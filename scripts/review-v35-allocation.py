#!/usr/bin/env python3
"""Validate the frozen V35 subset and summarize raw forks and compiler evidence."""
import argparse
from collections import Counter
import importlib.util
import json
import math
from pathlib import Path
import re
import shlex
import statistics
import subprocess
from urllib.parse import unquote, urlparse
import xml.etree.ElementTree as ET


def module(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


RUNNER = module('v35runner', 'investigate-v35-allocation.py')
REVIEW = module('v34review', 'review-v34-benchmark-results.py')


def metrics(row, forks):
    result = {}
    for name, metric, unit in [('latency', row['primaryMetric'], 'ns/op'),
                               ('allocation', row['secondaryMetrics']['gc.alloc.rate.norm'], 'B/op')]:
        data = metric['rawData']
        if (metric['scoreUnit'] != unit or len(data) != forks or any(len(f) != 5 for f in data)
                or any(not math.isfinite(x) or x < 0 for f in data for x in f)
                or not math.isfinite(metric['score']) or not math.isfinite(metric['scoreError'])):
            raise ValueError('Incomplete or invalid metric')
        result[name] = {'mean': metric['score'], 'error': metric['scoreError'], 'unit': unit,
                        'forkMeans': [statistics.mean(f) for f in data], 'rawData': data}
    return result


def compiler(path):
    root = ET.parse(path).getroot()
    if (root.tag != 'hotspot_log' or not (root.get('process') or '').isdigit()
            or not (root.get('time_ms') or '').isdigit() or root.find('hotspot_log_done') is None):
        raise ValueError('Incomplete compiler process evidence')
    relevant = ('EffectiveCachePolicy', 'CacheWorkPolicy', 'ReactiveClientInvocationHandler',
                'V34DefaultPathBenchmark', 'V34WorkloadFixture')
    nmethods = [dict(e.attrib) for e in root.iter('nmethod')]
    invalidations = [dict(e.attrib) for e in root.iter('make_not_entrant')]
    tasks = []
    for task in root.iter('task'):
        classes = {e.get('id'): e.get('name') for e in task.iter('klass')}
        methods = {e.get('id'): classes.get(e.get('holder'), e.get('holder', '')) + '::' + e.get('name', '')
                   for e in task.iter('method')}
        eliminated = []
        for event in task.iter('eliminate_allocation'):
            name = classes.get(event.get('type'), event.get('type'))
            frames = [{'method': methods.get(f.get('method'), f.get('method')), 'bci': f.get('bci')}
                      for f in event.iter('jvms')]
            eliminated.append({'class': name, 'frames': frames})
        calls = []
        for parse in task.iter('parse'):
            last = None
            for node in parse:
                if node.tag == 'call':
                    last = methods.get(node.get('method'), node.get('method'))
                elif node.tag in ('inline_success', 'inline_fail') and last and 'EffectiveCachePolicy' in last:
                    calls.append({'caller': methods.get(parse.get('method'), parse.get('method')),
                                  'callee': last, 'decision': node.tag, 'reason': node.get('reason')})
        if (calls or any('httpstarter' in e['class'] for e in eliminated)
                or any(name in task.get('method', '') for name in relevant)):
            done = task.find('task_done')
            tasks.append({'compileId': task.get('compile_id'), 'method': task.get('method'),
                          'stamp': task.get('stamp'), 'eliminations': eliminated, 'policyInlining': calls,
                          'completion': dict(done.attrib) if done is not None else None,
                          'nmethods': [e for e in nmethods if e.get('compile_id') == task.get('compile_id')],
                          'invalidations': [e for e in invalidations if e.get('compile_id') == task.get('compile_id')]})
    ids = {t['compileId'] for t in tasks}
    return {'file': path.name, 'timeMillis': int(root.get('time_ms')), 'process': root.get('process'),
            'vmArguments': shlex.split(root.findtext('vm_arguments/args', '')),
            'classpaths': [line.partition('=')[2] for line in root.findtext('vm_arguments/properties', '').splitlines()
                           if line.startswith('java.class.path=')],
            'logDone': dict(root.find('hotspot_log_done').attrib), 'measurementInterval': None,
            'tasks': tasks, 'nmethods': [e for e in nmethods if e.get('compile_id') in ids
                                        or any(name in e.get('method', '') for name in relevant)],
            'deoptimizations': invalidations}


def loaded_classes(path, expected_jar):
    selected = []
    required = {'io.github.huynhngochuyhoang.httpstarter.core.EffectiveCachePolicy$Selection',
                'io.github.huynhngochuyhoang.httpstarter.config.ReactiveHttpClientProperties$CachePolicyConfig'}
    from_jar = 0
    for line in path.read_text().splitlines():
        match = re.match(r'\[([0-9.]+)s\]\[info\]\[class,load\] (\S+) source: (.*)', line)
        if not match:
            continue
        timestamp, name, source = match.groups()
        if name.startswith('io.github.huynhngochuyhoang.httpstarter.') and source.startswith('file:'):
            location = urlparse(source)
            if location.netloc or Path(unquote(location.path)) != expected_jar:
                raise ValueError('Starter/harness class loaded outside the saved JAR')
            from_jar += 1
        if name in required:
            if not source.startswith('file:'):
                raise ValueError('Missing selected-class artifact origin')
            selected.append({'class': name, 'seconds': float(timestamp), 'source': source})
    if not from_jar or Counter(e['class'] for e in selected) != Counter(required):
        raise ValueError('Missing loaded-class evidence')
    return {'starterAndHarnessClassesFromSavedJar': from_jar, 'selected': selected}


def diagnostic_traces(directory, row, expected_jar):
    command = json.loads((directory / 'command.json').read_text())
    argv = command['argv']
    def option(flag):
        if argv.count(flag) != 1 or argv.index(flag) + 1 >= len(argv):
            raise ValueError('Missing/duplicate diagnostic command option: ' + flag)
        return argv[argv.index(flag) + 1]
    parameters = [argv[i + 1] for i, arg in enumerate(argv[:-1]) if arg == '-p']
    expected = {'-f': str(row['forks']), '-bm': 'avgt', '-tu': 'ns', '-t': '1',
                '-wi': '5', '-w': '1s', '-i': '5', '-r': '1s', '-rf': 'json'}
    if (command.get('exit') != 0 or option('-jar') != str(expected_jar)
            or parameters != [f'profile={row["params"]["profile"]}', 'scenario=GET']
            or any(option(flag) != value for flag, value in expected.items())
            or shlex.split(option('-jvmArgs')) != row['jvmArgs']
            or argv[argv.index('-jar') + 2] != RUNNER.BENCHMARK):
        raise ValueError('Failed or mismatched diagnostic command')
    # Legacy results have no worker PID or iteration timestamps. File order is not fork identity.
    traces = [compiler(path) for path in sorted(directory.glob('compilation-*.xml'))]
    if len(traces) != row['forks'] or len({t['process'] for t in traces}) != row['forks']:
        raise ValueError('Missing/duplicate compiler process evidence')
    expected_logs = {f'classes-{t["process"]}.log' for t in traces}
    if {p.name for p in directory.glob('classes-*.log')} != expected_logs:
        raise ValueError('Mismatched class-load inventory')
    for trace in traces:
        if trace['file'] != f'compilation-pid{trace["process"]}.xml':
            raise ValueError('Compiler filename/process mismatch')
        if trace['classpaths'] != [str(expected_jar)]:
            raise ValueError('Compiler classpath differs from recorded artifact')
        if trace['vmArguments'][:len(row['jvmArgs'])] != row['jvmArgs']:
            raise ValueError('Compiler VM arguments differ from recorded workload')
        trace['loadedClasses'] = loaded_classes(directory / f'classes-{trace["process"]}.log', expected_jar)
    return traces


def validate_jfr_summary(summary):
    counts = {name: int(count) for name, count in re.findall(r'^\s*(jdk\.\S+)\s+(\d+)\s+\d+\s*$', summary, re.MULTILINE)}
    allowed = {'jdk.ObjectAllocationSample', 'jdk.Checkpoint', 'jdk.Metadata'}
    if not counts.get('jdk.ObjectAllocationSample') or any(count and name not in allowed for name, count in counts.items()):
        raise ValueError('Scrub JFR to allocation samples before including it in evidence')


def summarize(root):
    report = {'scored': {}, 'diagnostic': {}, 'reviews': {},
              'limit': 'Diagnostic timings are not scored benefit; summaries alone do not assert a cause.'}
    audit = json.loads((root / 'input-audit.json').read_text())
    orders = {side: ['STARTER' if Path(p).name == f'reactive-http-client-starter-{audit["inputs"][side]["starter"]["version"]}.jar'
                     else Path(p).name for p in paths] for side, paths in audit['orderedClasspaths'].items()}
    if orders['baseline'] != orders['current']:
        raise ValueError('Dependency classpath order differs')
    report['orderedClasspathMatchesApartFromStarterCoordinate'] = True
    raw = {}
    for kind in ('scored', 'diagnostic'):
        for name, side, profiles, forks, extra, jfr in RUNNER.stages(kind):
            directory = root / kind / name
            command = json.loads((directory / 'command.json').read_text())
            if command.get('exit') != 0:
                raise ValueError(f'Failed or incomplete stage: {directory}')
            rows = json.loads((directory / 'results.json').read_text())
            if Counter(r['params']['profile'] for r in rows) != Counter(profiles.split(',')):
                raise ValueError('Missing/duplicate profile row')
            summaries = []
            for row in rows:
                if (row['benchmark'] != RUNNER.BENCHMARK.removeprefix('.*').removesuffix('$')
                        and not row['benchmark'].endswith('.' + RUNNER.BENCHMARK[2:-1])):
                    raise ValueError('Unexpected benchmark')
                if (row['params'] != {'profile': row['params']['profile'], 'scenario': 'GET'}
                        or row['mode'] != 'avgt' or row['threads'] != 1 or row['forks'] != forks
                        or row['warmupIterations'] != 5 or row['measurementIterations'] != 5
                        or row['warmupTime'] != '1 s' or row['measurementTime'] != '1 s'
                        or row['jvmArgs'] != RUNNER.FLAGS + extra
                        or row['jdkVersion'] != '21.0.8' or row['jmhVersion'] != '1.37'):
                    raise ValueError('Frozen workload/VM mismatch')
                summaries.append({'profile': row['params']['profile'], **metrics(row, forks)})
            report[kind][name] = summaries
            if kind == 'scored':
                raw[name] = rows
            else:
                jar = Path(command['argv'][command['argv'].index('-jar') + 1])
                if len(rows) != 1:
                    raise ValueError('Ambiguous multi-profile compiler evidence')
                traces = diagnostic_traces(directory, rows[0], jar)
                report[kind][name] = {'metrics': summaries, 'traces': traces,
                                    'forkAssociation': 'unverified', 'measurementInterval': None,
                                    'jfrFiles': [str(p.relative_to(root)) for p in directory.rglob('*.jfr')]}
                if jfr and not report[kind][name]['jfrFiles']:
                    raise ValueError('Missing JFR recording')
                for recording in report[kind][name]['jfrFiles']:
                    summary = subprocess.check_output([str(RUNNER.JAVA.with_name('jfr')), 'summary', str(root / recording)], text=True)
                    validate_jfr_summary(summary)
    for order in ('forward', 'reverse'):
        report['reviews'][order] = REVIEW.review(raw[f'{order}-baseline'], raw[f'{order}-current'], complete=False)
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('evidence', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=False)
    report = summarize(args.evidence)
    (args.output / 'review.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps({kind: len(report[kind]) for kind in ('scored', 'diagnostic')}))


if __name__ == '__main__':
    main()
