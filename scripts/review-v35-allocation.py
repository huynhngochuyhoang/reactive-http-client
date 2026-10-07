#!/usr/bin/env python3
"""Validate the frozen V35 subset and summarize raw forks and compiler evidence."""
import argparse
from collections import Counter
import importlib.util
import json
import math
from pathlib import Path
import re
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
        if calls or any('httpstarter' in e['class'] for e in eliminated):
            tasks.append({'compileId': task.get('compile_id'), 'method': task.get('method'),
                          'stamp': task.get('stamp'), 'eliminations': eliminated, 'policyInlining': calls})
    return {'file': path.name, 'timeMillis': int(root.get('time_ms')), 'process': root.get('process'),
            'tasks': tasks, 'nmethods': [dict(e.attrib) for e in root.iter('nmethod')
                                         if 'CacheWorkPolicy' in e.get('method', '')
                                         or 'ReactiveClientInvocationHandler invoke ' in e.get('method', '')],
            'deoptimizations': [dict(e.attrib) for e in root.iter('make_not_entrant')]}


def loaded_classes(path, expected_jar):
    selected = []
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
        if name.endswith(('EffectiveCachePolicy$Selection', 'ReactiveHttpClientProperties$CachePolicyConfig')):
            selected.append({'class': name, 'seconds': float(timestamp), 'source': source})
    if not from_jar or len(selected) != 2:
        raise ValueError('Missing loaded-class evidence')
    return {'starterAndHarnessClassesFromSavedJar': from_jar, 'selected': selected}


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
                traces = sorted((compiler(p) for p in directory.glob('compilation-*.xml')), key=lambda x: x['timeMillis'])
                if len(traces) != forks:
                    raise ValueError('Missing compiler traces')
                if len({t['timeMillis'] for t in traces}) != forks:
                    raise ValueError('Ambiguous compiler fork ordering')
                jar = Path(command['argv'][command['argv'].index('-jar') + 1])
                for i, trace in enumerate(traces):
                    trace['fork'] = i + 1
                    trace['allocationBytesPerOp'] = summaries[0]['allocation']['forkMeans'][i]
                    trace['loadedClasses'] = loaded_classes(directory / f'classes-{trace["process"]}.log', jar)
                report[kind][name] = {'metrics': summaries, 'traces': traces,
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
