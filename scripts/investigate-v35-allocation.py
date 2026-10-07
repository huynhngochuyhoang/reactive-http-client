#!/usr/bin/env python3
"""Bounded V35 investigation using the unchanged saved V34 JMH artifacts."""
import argparse
import datetime
import hashlib
import json
import os
from pathlib import Path
import signal
import subprocess
import time
import zipfile

ROOT = Path(__file__).resolve().parents[1]
JAVA = Path('/usr/lib/jvm/jdk-21.0.8-oracle-x64/bin/java')
BENCHMARK = '.*V34DefaultPathBenchmark.defaultV34NoNetworkWarmPublisher$'
FLAGS = ['-Xms512m', '-Xmx512m', '-XX:ActiveProcessorCount=2',
         '-Dorg.slf4j.simpleLogger.defaultLogLevel=warn']
PROFILES = 'MINIMAL,AUTO_REGISTRY,RESILIENCE_ENABLED_ONLY'
TARGET = 'io.github.huynhngochuyhoang.httpstarter.core.EffectiveCachePolicy::resolveSelection'
PLAN = ROOT / 'roadmaps/v35/ALLOCATION-INVESTIGATION.md'


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def save(path, value):
    path.write_text(json.dumps(value, indent=2) + '\n')


def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT).decode().strip()


def snapshot():
    data = {'time': datetime.datetime.now(datetime.timezone.utc).isoformat(),
            'affinity': sorted(os.sched_getaffinity(0)), 'cpuCount': os.cpu_count(),
            'processVisibility': 'This process namespace only; not proof of host exclusivity'}
    for filename in ('/proc/cpuinfo', '/proc/meminfo', '/proc/loadavg', '/proc/self/cgroup',
                     '/proc/pressure/cpu', '/proc/pressure/memory', '/proc/pressure/io',
                     '/sys/fs/cgroup/cpu.max', '/sys/fs/cgroup/cpu.stat',
                     '/sys/fs/cgroup/memory.max', '/sys/fs/cgroup/memory.current',
                     '/sys/fs/cgroup/cpuset.cpus.effective'):
        path = Path(filename)
        data[filename] = path.read_text() if path.exists() else 'unavailable'
    data['processes'] = subprocess.check_output(['ps', '-eo', 'pid,comm,pcpu,rss']).decode()
    return data


def run(command, directory, timeout=900, allow_failure=False):
    directory.mkdir(parents=True, exist_ok=False)
    environment = os.environ.copy()
    # Inherited VM injection would invalidate the frozen comparison.
    for key in ('JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS'):
        if environment.get(key):
            raise ValueError(f'Unexpected {key}; remove it explicitly before measurement')
    record = {'argv': list(map(str, command)), 'cwd': str(directory), 'startedAt': time.time()}
    save(directory / 'command.json', record)
    before = snapshot()
    save(directory / 'host-before.json', before)
    available = next(int(line.split()[1]) for line in before['/proc/meminfo'].splitlines()
                     if line.startswith('MemAvailable:'))
    if available < 2 * 1024 * 1024:
        record['exit'] = 77
        record['reason'] = 'Less than 2 GiB available; stage not started'
    else:
        with (directory / 'run.log').open('w') as log:
            process = subprocess.Popen(command, cwd=directory, env=environment, stdout=log,
                                       stderr=subprocess.STDOUT, start_new_session=True)
            try:
                record['exit'] = process.wait(timeout=timeout)
            except (subprocess.TimeoutExpired, KeyboardInterrupt) as error:
                # The launcher and its JMH workers share this exclusively owned group.
                os.killpg(process.pid, signal.SIGKILL)
                process.wait()
                record['exit'] = 124 if isinstance(error, subprocess.TimeoutExpired) else 130
    record['endedAt'] = time.time()
    save(directory / 'command.json', record)
    save(directory / 'host-after.json', snapshot())
    print(directory.name, 'exit', record['exit'], flush=True)
    if record['exit'] and not allow_failure:
        raise RuntimeError(f'Failed stage {directory}; retain outputs, do not silently retry')
    return record['exit']


def stages(kind):
    if kind == 'scored':
        return [(f'{order}-{side}', side, PROFILES, 2, [], False)
                for order, sides in [('forward', ('baseline', 'current')), ('reverse', ('current', 'baseline'))]
                for side in sides]
    tracing = ['-XX:+UnlockDiagnosticVMOptions', '-XX:+LogCompilation', '-XX:LogFile=compilation-%p.xml',
               '-Xlog:class+load=info:file=classes-%p.log']
    variants = [('trace', 4, [], False), ('no-ea', 2, ['-XX:-DoEscapeAnalysis'], False),
                ('dontinline', 2, [f'-XX:CompileCommand=dontinline,{TARGET}'], False),
                ('inline', 2, [f'-XX:CompileCommand=inline,{TARGET}'], False),
                ('jfr', 1, ['-XX:FlightRecorderOptions=stackdepth=128'], True)]
    return [(f'{name}-{side}', side, 'RESILIENCE_ENABLED_ONLY', forks, tracing + extra, jfr)
            for name, forks, extra, jfr in variants for side in ('baseline', 'current')]


def command(pair, side, profiles, forks, extra, jfr, directory):
    inputs = json.loads((pair / f'build-{side}/inputs.json').read_text())
    return [str(JAVA), *FLAGS, '-Dbenchmark.project.version=4.5.0-SNAPSHOT',
            f'-Dbenchmark.starter.version={inputs["starter"]["version"]}',
            '-Dbenchmark.api.compatibility.baseline.version=4.4.2', '-Dbenchmark.spring-boot.version=4.0.0',
            f'-Dbenchmark.commit={inputs["sourceCommit"]}',
            '-Dbenchmark.stack.context=V35 allocation attribution',
            '-jar', str(pair / f'build-{side}/benchmarks.jar'), BENCHMARK,
            '-p', f'profile={profiles}', '-p', 'scenario=GET', '-bm', 'avgt', '-tu', 'ns', '-t', '1',
            '-wi', '5', '-w', '1s', '-i', '5', '-r', '1s', '-f', str(forks), '-prof', 'gc', '-foe', 'true',
            '-jvmArgs', ' '.join(FLAGS + extra), '-rf', 'json', '-rff', str(directory / 'results.json'),
            *(['-prof', 'jfr'] if jfr else [])]


def audit_pair(pair):
    inputs = {side: json.loads((pair / f'build-{side}/inputs.json').read_text()) for side in ('baseline', 'current')}
    baseline, current = inputs['baseline'], inputs['current']
    if (baseline['dependencies'] != current['dependencies'] or baseline['sourceFiles'] != current['sourceFiles']
            or baseline['starter']['version'] != '4.4.2' or not baseline['published']
            or current['starter']['version'] != '4.5.0-SNAPSHOT' or current['published']):
        raise ValueError('Unmatched saved inputs')
    for name, expected in current['sourceFiles'].items():
        if digest(ROOT / name) != expected:
            raise ValueError(f'Changed harness input: {name}')
    jars = {}
    for side, metadata in inputs.items():
        path = pair / f'build-{side}/benchmarks.jar'
        if digest(path) != metadata['shadedSha256']:
            raise ValueError(f'Changed saved JAR: {side}')
        with zipfile.ZipFile(path) as jar:
            jars[side] = {name: hashlib.sha256(jar.read(name)).hexdigest()
                          for name in jar.namelist() if not name.endswith('/')}
    differences = [name for name in sorted(jars['baseline'].keys() | jars['current'].keys())
                   if jars['baseline'].get(name) != jars['current'].get(name)]
    if any(name.endswith('.class') for name in differences):
        raise ValueError('Saved executable class bytes differ')
    return {'inputs': inputs, 'differentResources': differences,
            'allShadedClassesIdentical': True,
            'shadedClassCount': sum(name.endswith('.class') for name in jars['current']),
            'orderedClasspaths': {s: (pair / f'build-{s}/classpath.txt').read_text().strip().split(os.pathsep)
                                  for s in inputs},
            'entryHashes': jars}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('stage', choices=('prepare', 'scored', 'diagnostic'))
    parser.add_argument('--pair', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    pair, out = args.pair.resolve(), args.output.resolve()
    if args.stage == 'prepare':
        out.mkdir(parents=True, exist_ok=False)
        audit = audit_pair(pair)
        save(out / 'input-audit.json', audit)
        save(out / 'source.json', {'commit': git('rev-parse', 'HEAD'), 'tree': git('rev-parse', 'HEAD^{tree}'),
                                   'workingTree': git('status', '--short'), 'planSha256': digest(PLAN),
                                   'runnerSha256': digest(Path(__file__)), 'javaSha256': digest(JAVA)})
        (out / 'frozen-plan.md').write_bytes(PLAN.read_bytes())
        (out / 'source.patch').write_bytes(subprocess.check_output(['git', 'diff', '--binary'], cwd=ROOT))
        (out / 'runner.py').write_bytes(Path(__file__).read_bytes())
        save(out / 'schedule.json', {kind: stages(kind) for kind in ('scored', 'diagnostic')})
        run([str(JAVA), '-version'], out / 'java')
        run([str(JAVA), *FLAGS, '-XX:+PrintFlagsFinal', '-version'], out / 'vm-flags')
        run([str(JAVA), '-XX:+UnlockDiagnosticVMOptions', '-XX:+PrintEscapeAnalysis', '-version'],
            out / 'escape-analysis-availability', allow_failure=True)
        return
    source = json.loads((out / 'source.json').read_text())
    if (digest(Path(__file__)) != source['runnerSha256'] or digest(JAVA) != source['javaSha256']
            or digest(PLAN) != source['planSha256']):
        raise ValueError('Runner/toolchain/plan differs from frozen preparation')
    audit_pair(pair)
    if args.stage == 'diagnostic':
        for name, *_ in stages('scored'):
            if json.loads((out / 'scored' / name / 'command.json').read_text())['exit'] != 0:
                raise ValueError('Scored stage incomplete or failed')
    for name, side, profiles, forks, extra, jfr in stages(args.stage):
        directory = out / args.stage / name
        run(command(pair, side, profiles, forks, extra, jfr, directory), directory,
            timeout=900 if args.stage == 'scored' else 600)


if __name__ == '__main__':
    main()
