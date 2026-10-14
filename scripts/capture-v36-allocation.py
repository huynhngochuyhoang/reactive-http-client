#!/usr/bin/env python3
"""V36-P3-1: prepare and capture the 40 approved O/M/T workers, without retries."""
import argparse
import json
import os
from pathlib import Path
import re
import shutil
import signal
import subprocess
import time
import uuid
import zipfile

import importlib.util

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('review', ROOT / 'scripts/review-v35-allocation.py')
review = importlib.util.module_from_spec(spec)
spec.loader.exec_module(review)
base = review.RUNNER
JAVA = base.JAVA
PLAN = ROOT / 'roadmaps/v36/INVESTIGATION-PLAN.md'
PROFILER = 'evidence.v36.BoundaryRecorder'
MAIN = 'io.github.huynhngochuyhoang.httpstarter.benchmarks.BenchmarkRunner'
GIB = 1024 ** 3


def schedule():
    stages = []
    for lane, sides in [('O', 'BCCBBBCC'), ('M', 'BCCB'), ('T', 'BCCBBBCC'), ('I', 'BCCB')]:
        for slot, side in enumerate(sides):
            profiles = ('AUTO_REGISTRY', 'HOOK') if slot % 2 == 0 else ('HOOK', 'AUTO_REGISTRY')
            for profile in profiles:
                stages.append({'id': len(stages) + 1, 'lane': lane, 'profile': profile,
                               'side': 'baseline' if side == 'B' else 'current'})
    return stages


def group_processes(group):
    records = []
    for path in Path('/proc').glob('[0-9]*'):
        try:
            fields = (path / 'stat').read_text().rsplit(')', 1)[1].split()
            if int(fields[2]) != group or fields[0] == 'Z':
                continue
            argv = [arg.decode() for arg in (path / 'cmdline').read_bytes().split(b'\0') if arg]
            records.append({'pid': int(path.name), 'ppid': int(fields[1]), 'pgid': int(fields[2]),
                            'startTicks': int(fields[19]), 'rss': int(fields[21]) * os.sysconf('SC_PAGE_SIZE'),
                            'argv': argv})
        except (FileNotFoundError, ProcessLookupError):
            continue
    return records


def host_limits(out):
    snapshot = base.snapshot()
    relative = next(line[3:] for line in Path('/proc/self/cgroup').read_text().splitlines() if line.startswith('0::'))
    root = Path('/sys/fs/cgroup')
    current = root / relative.lstrip('/')
    if not current.is_dir():
        raise ValueError('Cannot establish cgroup ownership')
    limits = []
    while True:
        entry = {'path': str(current)}
        for name in ('cpu.max', 'memory.max', 'memory.current', 'cpu.stat', 'memory.events'):
            path = current / name
            entry[name] = path.read_text().strip() if path.exists() else None
        limits.append(entry)
        if current == root:
            break
        current = current.parent
    snapshot['ancestralCgroupLimits'] = limits
    snapshot['freeDiskBytes'] = shutil.disk_usage(out).free
    validate_host(snapshot)
    return snapshot


def validate_host(snapshot):
    available = next(int(line.split()[1]) * 1024 for line in snapshot['/proc/meminfo'].splitlines()
                     if line.startswith('MemAvailable:'))
    if available < 3 * GIB or snapshot['freeDiskBytes'] < 16 * GIB or len(snapshot['affinity']) < 2:
        raise ValueError('Host below approved RAM/disk/CPU bounds')
    for limit in snapshot['ancestralCgroupLimits']:
        cpu, memory = limit['cpu.max'], limit['memory.max']
        if cpu and cpu.split()[0] != 'max' and int(cpu.split()[0]) / int(cpu.split()[1]) < 2:
            raise ValueError('Cgroup CPU quota below two CPUs')
        if memory and memory != 'max' and int(memory) - int(limit['memory.current']) < 3 * GIB:
            raise ValueError('Cgroup memory headroom below 3 GiB')


def size(directory):
    return sum(path.stat().st_size for path in directory.rglob('*') if path.is_file())


def execute(argv, directory, stage_id, timeout, started, worker_required, raw_root):
    directory.mkdir(parents=True, exist_ok=False)
    record = {'argv': list(map(str, argv)), 'stageId': stage_id, 'cwd': str(directory), 'startedAt': time.time()}
    base.save(directory / 'command.json', record)
    workers = {}
    process = None
    try:
        base.save(directory / 'host-before.json', host_limits(raw_root))
        for key in ('JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS'):
            if os.environ.get(key):
                raise ValueError('Inherited VM injection: ' + key)
        with (directory / 'run.log').open('w') as output:
            process = subprocess.Popen(argv, cwd=directory, stdout=output, stderr=subprocess.STDOUT, start_new_session=True)
            record['launcherPid'] = process.pid
            base.save(directory / 'command.json', record)
            deadline = time.monotonic() + timeout
            while process.poll() is None:
                members = group_processes(process.pid)
                for member in members:
                    if 'org.openjdk.jmh.runner.ForkedMain' in member['argv']:
                        workers[(member['pid'], member['startTicks'])] = member
                if len(workers) > int(worker_required):
                    raise ValueError('Unexpected/duplicate worker process')
                if (time.monotonic() > deadline or time.monotonic() - started > 7200
                        or sum(p['rss'] for p in members) > 2 * GIB
                        or size(directory) > 256 * 1024 ** 2 or size(raw_root) > 8 * GIB):
                    raise ValueError('Approved time/RSS/output bound exceeded')
                time.sleep(1)
            record['exit'] = process.wait()
            if record['exit'] or len(workers) != int(worker_required) or group_processes(process.pid):
                raise ValueError('Failed stage, missing worker, or surviving process group')
    except BaseException as error:
        record['failure'] = type(error).__name__ + ': ' + str(error)
        if process is not None:
            try:
                os.killpg(process.pid, signal.SIGKILL)
            except ProcessLookupError:
                pass
            record['exit'] = process.wait()
        record.setdefault('exit', 77)
        # Even a successful launcher is not an accepted capture if validation failed.
        if record['exit'] == 0:
            record['exit'] = 65
        raise
    finally:
        record['endedAt'] = time.time()
        base.save(directory / 'command.json', record)
        base.save(directory / 'workers.json', list(workers.values()))
        base.save(directory / 'host-after.json', base.snapshot())


def stage_spec(stage, pair, out, recorder, seal):
    stage_id = f'{stage["id"]:02}-{stage["lane"]}-{stage["profile"]}-{stage["side"]}-{uuid.uuid4().hex}'
    directory = out / 'stages' / stage_id
    jar = pair / ('build-' + stage['side']) / 'benchmarks.jar'
    inputs = json.loads((jar.parent / 'inputs.json').read_text())
    flags = base.FLAGS + [f'-Dv36.stage={stage_id}', f'-Dv36.seal={seal}', f'-Dv36.boundaries={directory / "boundaries.xml"}']
    if stage['lane'] in ('T', 'I'):
        flags += ['-XX:+UnlockDiagnosticVMOptions', '-XX:+LogCompilation', '-XX:LogFile=compilation-%p.xml',
                  '-Xlog:class+load=info:file=classes-%p.log']
    if stage['lane'] == 'I':
        flags += ['-XX:CompileCommand=dontinline,' + base.TARGET]
    argv = [str(JAVA), *base.FLAGS, '-Dbenchmark.project.version=4.5.0-SNAPSHOT',
            '-Dbenchmark.starter.version=' + inputs['starter']['version'],
            '-Dbenchmark.api.compatibility.baseline.version=4.4.2', '-Dbenchmark.spring-boot.version=4.0.0',
            '-Dbenchmark.commit=' + inputs['sourceCommit'], '-Dbenchmark.stack.context=V36-P3-1',
            '-cp', str(jar) + ':' + str(recorder), MAIN, base.BENCHMARK,
            '-p', 'profile=' + stage['profile'], '-p', 'scenario=GET', '-f', '1', '-wf', '0',
            '-bm', 'avgt', '-tu', 'ns', '-t', '1', '-wi', '5', '-w', '1s', '-i', '5', '-r', '1s',
            '-prof', 'gc', '-foe', 'true', '-jvmArgs', ' '.join(flags), '-rf', 'json', '-rff', str(directory / 'results.json')]
    if stage['lane'] != 'O':
        argv += ['-prof', PROFILER]
    return dict(stage, stageId=stage_id, directory=directory, jar=jar, recorder=recorder, seal=seal,
                jvmArgs=flags, argv=argv)


def prepare(pair, out):
    out.mkdir(parents=True, exist_ok=False)
    audit = base.audit_pair(pair)
    paths = [pair / ('build-' + side) / name for name in ('benchmarks.jar', 'inputs.json') for side in ('baseline', 'current')]
    paths += [JAVA.parent.parent / name for name in ('bin/java', 'release', 'lib/server/libjvm.so', 'lib/modules')]
    expected = re.findall(r'^\|.*`([0-9a-f]{64})`.*$', PLAN.read_text(), re.MULTILINE)
    if len(expected) != 8 or [base.digest(p) for p in paths] != expected:
        raise ValueError('Pinned artifact/JDK mismatch')
    orders = []
    for side, inputs in audit['inputs'].items():
        classpath = audit['orderedClasspaths'][side]
        for path in map(Path, classpath):
            wanted = inputs['starter']['sha256'] if str(path) == inputs['starter']['path'] else inputs['dependencies'][path.name]
            if base.digest(path) != wanted:
                raise ValueError('Dependency hash mismatch: ' + str(path))
            paths.append(path)
        orders.append(['STARTER' if p == inputs['starter']['path'] else Path(p).name for p in classpath])
    if orders[0] != orders[1]:
        raise ValueError('Classpath order mismatch')
    base.save(out / 'input-audit.json', audit)
    base.save(out / 'schedule.json', schedule())
    source = ROOT / '.github/v36-boundary-recorder'
    classes = out / 'recorder-classes'
    classes.mkdir()
    jar = pair / 'build-baseline/benchmarks.jar'
    commands = [
        [str(JAVA.with_name('javac')), '-proc:none', '-cp', str(jar), '-d', str(classes),
         str(source / 'BoundaryRecorder.java'), str(source / 'BoundaryRecorderTest.java')],
        [str(JAVA), '-XX:+DisableExplicitGC', '-cp', str(classes) + ':' + str(jar), 'evidence.v36.BoundaryRecorderTest']]
    for index, command in enumerate(commands):
        with (out / f'recorder-check-{index}.log').open('w') as log:
            result = subprocess.run(command, stdout=log, stderr=subprocess.STDOUT, timeout=120)
        base.save(out / f'recorder-check-{index}.json', {'argv': command, 'exit': result.returncode,
                                                     'deterministicNotBenchmark': True})
        if result.returncode:
            raise ValueError('Recorder deterministic check failed')
    recorder = out / 'boundary-recorder.jar'
    with zipfile.ZipFile(recorder, 'w') as archive:
        archive.write(classes / 'evidence/v36/BoundaryRecorder.class', 'evidence/v36/BoundaryRecorder.class')
    paths += [recorder, PLAN, Path(__file__), ROOT / 'scripts/review-v35-allocation.py',
              ROOT / 'scripts/investigate-v35-allocation.py', ROOT / 'scripts/review-v34-benchmark-results.py',
              source / 'BoundaryRecorder.java', source / 'BoundaryRecorderTest.java']
    paths += [ROOT / name for name in audit['inputs']['current']['sourceFiles']]
    hashes = {str(path): base.digest(path) for path in paths}
    base.save(out / 'sealed-inputs.json', hashes)
    shutil.copy2(PLAN, out / 'frozen-plan.md')
    base.save(out / 'source.json', {'commit': base.git('rev-parse', 'HEAD'), 'tree': base.git('rev-parse', 'HEAD^{tree}'),
                                  'status': base.git('status', '--short'), 'approval': 'V36-P3-1; 2026-10-13'})
    patch = subprocess.check_output(['git', 'diff', '--binary'], cwd=ROOT)
    for name in subprocess.check_output(['git', 'ls-files', '--others', '--exclude-standard'], cwd=ROOT, text=True).splitlines():
        diff = subprocess.run(['git', 'diff', '--no-index', '--binary', '--', '/dev/null', name], cwd=ROOT, stdout=subprocess.PIPE)
        if diff.returncode != 1:
            raise ValueError('Cannot preserve source addition')
        patch += diff.stdout
    (out / 'source.patch').write_bytes(patch)
    print('Prepared sealed inputs and deterministic recorder test; zero experiment forks', flush=True)


def verify_seal(out):
    for path, expected in json.loads((out / 'sealed-inputs.json').read_text()).items():
        if base.digest(Path(path)) != expected:
            raise ValueError('Sealed input changed: ' + path)


def ordinary(pair, out):
    # Exclusive creation makes interruption/failure non-retryable, including preflight.
    with (out / 'execution-ledger.json').open('x') as stream:
        stream.write('{}\n')
    started = time.monotonic()
    ledger = {'state': 'running', 'attemptedStageIds': [], 'completedStageIds': [], 'preflightAttempts': 0,
              'workerCeiling': 48, 'activeSeconds': 0, 'approval': 'V36-P3-1'}
    try:
        verify_seal(out)
        for index, argv in enumerate(([str(JAVA), '-version'], [str(JAVA), *base.FLAGS, '-XX:+PrintFlagsFinal', '-version'])):
            ledger['preflightAttempts'] += 1
            base.save(out / 'execution-ledger.json', ledger)
            execute(argv, out / f'preflight-{index}', f'preflight-{index}', 30, started, False, out)
        results = {}
        seal = base.digest(out / 'sealed-inputs.json')
        for stage in schedule()[:40]:
            verify_seal(out)
            expected = stage_spec(stage, pair, out, out / 'boundary-recorder.jar', seal)
            ledger['attemptedStageIds'].append(stage['id'])
            base.save(out / 'execution-ledger.json', ledger)
            base.save(out / f'stage-{stage["id"]:02}.json', {k: str(v) if isinstance(v, Path) else v for k, v in expected.items()})
            execute(expected['argv'], expected['directory'], expected['stageId'], 120, started, True, out)
            result = review.v36_stage(expected['directory'], expected)
            base.save(expected['directory'] / 'review.json', result)
            results[expected['stageId']] = {k: result[k] for k in ('metrics', 'allocationMode', 'forkAssociation')}
            base.save(out / 'summary.json', results)
            ledger['completedStageIds'].append(stage['id'])
            ledger['activeSeconds'] = time.monotonic() - started
            base.save(out / 'execution-ledger.json', ledger)
            print(expected['stageId'], result['allocationMode'], result['metrics']['allocation']['mean'], flush=True)
        ledger['state'] = 'ordinary-complete; intervention unexecuted'
    except BaseException as error:
        ledger['state'] = 'aborted; new approval required before resumption'
        ledger['failure'] = type(error).__name__ + ': ' + str(error)
        raise
    finally:
        ledger['activeSeconds'] = time.monotonic() - started
        ledger['unattemptedStageIds'] = [s['id'] for s in schedule() if s['id'] not in ledger['attemptedStageIds']]
        base.save(out / 'execution-ledger.json', ledger)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('phase', choices=('prepare', 'ordinary'))
    parser.add_argument('--pair', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if os.geteuid() == 0:
        parser.error('Use the normal user, not sudo')
    os.umask(0o077)
    {'prepare': prepare, 'ordinary': ordinary}[args.phase](args.pair.resolve(), args.output.resolve())


if __name__ == '__main__':
    main()
