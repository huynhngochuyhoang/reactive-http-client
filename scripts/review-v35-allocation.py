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


def v36_stage(directory, expected):
    """One owned worker per stage; never infer identity from timestamp ordering."""
    command = json.loads((directory / 'command.json').read_text())
    if command.get('exit') != 0 or command['argv'] != expected['argv'] or command['stageId'] != expected['stageId']:
        raise ValueError('Failed or mismatched V36 command')
    workers = json.loads((directory / 'workers.json').read_text())
    if len(workers) != 1:
        raise ValueError('Expected exactly one owned worker')
    worker = workers[0]
    cp = str(expected['jar']) + ':' + str(expected['recorder'])
    argv = worker['argv']
    if (worker['pid'] == command['launcherPid'] or worker['pgid'] != command['launcherPid']
            or worker['ppid'] != command['launcherPid'] or worker['startTicks'] <= 0
            or argv.count('org.openjdk.jmh.runner.ForkedMain') != 1 or argv.count('-cp') != 1
            or argv[argv.index('-cp') + 1] != cp or argv[0] != str(RUNNER.JAVA)
            or argv[1:1 + len(expected['jvmArgs'])] != expected['jvmArgs']):
        raise ValueError('Worker identity/arguments mismatch')
    rows = json.loads((directory / 'results.json').read_text())
    if len(rows) != 1:
        raise ValueError('Expected one result row')
    row = rows[0]
    if (row['benchmark'] != 'io.github.huynhngochuyhoang.httpstarter.benchmarks.V34DefaultPathBenchmark.defaultV34NoNetworkWarmPublisher'
            or row['params'] != {'profile': expected['profile'], 'scenario': 'GET'}
            or row['mode'] != 'avgt' or row['threads'] != 1 or row['forks'] != 1
            or row['warmupIterations'] != 5 or row['measurementIterations'] != 5
            or row['warmupTime'] != '1 s' or row['measurementTime'] != '1 s'
            or row['jvmArgs'] != expected['jvmArgs'] or row['jdkVersion'] != '21.0.8'
            or row['jmhVersion'] != '1.37'):
        raise ValueError('V36 workload/result mismatch')
    result = {'metrics': metrics(row, 1), 'worker': worker, 'forkAssociation': 'single-owned-worker',
              'measurementInterval': None, 'executingCodeIdentity': 'unverified'}
    samples = result['metrics']['allocation']['rawData'][0]
    modes = ['low' if abs(x - 1744) <= 32 else 'high' if abs(x - 2000) <= 32 else 'other' for x in samples]
    result['allocationMode'] = modes[0] if len(set(modes)) == 1 else 'mixed'
    boundary = directory / 'boundaries.xml'
    envelopes = []
    if expected['lane'] == 'O':
        if boundary.exists():
            raise ValueError('Ordinary lane unexpectedly recorded boundaries')
    else:
        entries = ET.parse(boundary).getroot().findall('entry')
        record = {e.get('key'): e.text for e in entries}
        if len(record) != len(entries):
            raise ValueError('Duplicate boundary fields')
        for key, value in {'schema': 'v36-1', 'complete': 'true', 'stage': expected['stageId'],
                           'seal': expected['seal'], 'pid': str(worker['pid']), 'classpath': cp,
                           'benchmark': row['benchmark'], 'profile': expected['profile'], 'scenario': 'GET'}.items():
            if record.get(key) != value:
                raise ValueError('Boundary identity mismatch: ' + key)
        for key, path in [('recorderOrigin', expected['recorder']), ('jmhOrigin', expected['jar'])]:
            location = urlparse(record[key])
            if (location.scheme != 'file' or location.netloc or location.query or location.fragment
                    or Path(unquote(location.path)) != path):
                raise ValueError('Boundary artifact origin mismatch')
        actual_args = [record['argument.' + str(i)] for i in range(int(record['argumentCount']))]
        if actual_args != argv[1:argv.index('-cp')]:
            raise ValueError('Boundary VM arguments mismatch')
        if {k for k in record if k.startswith('iteration.')} != {
                f'iteration.{i}.{field}' for i in range(10)
                for field in ('type', 'before.nano', 'after.nano', 'before.nanoAfter', 'after.nanoAfter',
                              'before.uptimeMs', 'after.uptimeMs')}:
            raise ValueError('Incomplete/extra iteration fields')
        previous_nano, previous_uptime = None, 0
        for i in range(10):
            prefix = f'iteration.{i}.'
            phase = 'WARMUP' if i < 5 else 'MEASUREMENT'
            if record[prefix + 'type'] != phase:
                raise ValueError('Iteration phase mismatch')
            start, end = [int(record[prefix + when + '.nano']) for when in ('before', 'after')]
            start_after, end_after = [int(record[prefix + when + '.nanoAfter']) for when in ('before', 'after')]
            begin_ms, end_ms = [int(record[prefix + when + '.uptimeMs']) for when in ('before', 'after')]
            if (not start <= start_after <= end <= end_after
                    or (previous_nano is not None and start < previous_nano)
                    or not previous_uptime <= begin_ms <= end_ms):
                raise ValueError('Non-monotonic/overlapping boundary clocks')
            envelopes.append({'index': i, 'type': phase, 'beforeUptimeMs': begin_ms, 'afterUptimeMs': end_ms,
                              'beforeNano': start, 'afterNano': end,
                              'precisionMarginMs': 2 + math.ceil(max(start_after - start, end_after - end) / 1e6)})
            previous_nano, previous_uptime = end_after, end_ms
        result['iterationEnvelopes'] = envelopes
    traces = list(directory.glob('compilation-*.xml'))
    logs = list(directory.glob('classes-*.log'))
    if expected['lane'] not in ('T', 'I'):
        if traces or logs:
            raise ValueError('Unexpected tracing in non-trace lane')
        return result
    pid = worker['pid']
    if {p.name for p in traces} != {f'compilation-pid{pid}.xml'} or {p.name for p in logs} != {f'classes-{pid}.log'}:
        raise ValueError('V36 compiler/class-log inventory mismatch')
    trace = compiler(traces[0])
    if (trace['process'] != str(pid) or trace['classpaths'] != [cp]
            or trace['vmArguments'] != argv[1:argv.index('-cp')]):
        raise ValueError('V36 compiler worker mismatch')
    trace['loadedClasses'] = loaded_classes(logs[0], expected['jar'])
    result['trace'] = trace
    if expected['lane'] == 'I':
        directive = '-XX:CompileCommand=dontinline,' + RUNNER.TARGET
        if expected['jvmArgs'].count(directive) != 1:
            raise ValueError('Missing/duplicate intervention directive')
        calls = [{'compileId': task['compileId'], 'taskMethod': task['method'], **call}
                 for task in trace['tasks'] for call in task['policyInlining']
                 if call['callee'] == RUNNER.TARGET]
        result['intervention'] = {
            'directive': directive,
            'resolverInlining': calls,
            'nonInliningObserved': any(c['decision'] == 'inline_fail' and 'CompileCommand' in (c['reason'] or '') for c in calls),
            'inliningAlsoObserved': any(c['decision'] == 'inline_success' for c in calls),
            'ordinaryHighWitness': False}
    # Envelopes bound the iteration, not the timed loop or the code actually executed.
    result['compilerEnvelopeEvents'] = [
        {'iteration': e['index'], 'events': [
            {'kind': kind, **event, 'position': envelope_position(event.get('stamp'), e)}
            for kind, events in [('installation', trace['nmethods']), ('invalidation', trace['deoptimizations'])]
            for event in events]}
        for e in envelopes if e['type'] == 'MEASUREMENT']
    return result


def envelope_position(stamp, envelope):
    if stamp is None or not math.isfinite(float(stamp)):
        return 'unknown'
    millis = float(stamp) * 1000
    start, end = envelope['beforeUptimeMs'], envelope['afterUptimeMs']
    margin = envelope.get('precisionMarginMs', 2)
    if abs(millis - start) <= margin or abs(millis - end) <= margin:
        return 'boundary-ambiguous'
    return 'before' if millis < start else 'after' if millis > end else 'inside-envelope'


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
