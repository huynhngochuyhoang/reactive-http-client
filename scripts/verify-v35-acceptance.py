#!/usr/bin/env python3
"""Rebuild V35 matched inputs and preserve final cost/API evidence outside build output."""
import argparse
import importlib.util
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[1]
GROUP = Path('io/github/huynhngochuyhoang')
MODULE = 'reactive-http-client-benchmarks'
MODULES = ['reactive-http-client-' + name for name in ('starter', 'test', 'otel')]
PLAN = ROOT / 'roadmaps/v35/FINAL-ACCEPTANCE.md'


def load(name):
    spec = importlib.util.spec_from_file_location(name.replace('-', '_'), ROOT / 'scripts' / (name + '.py'))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


native = load('verify-v33-native')
parity = load('verify-v35-parity')
inputs = load('verify-v34-benchmark-inputs')
reviewer = load('review-v34-benchmark-results')
allocation = load('investigate-v35-allocation')


def seed_third_party(seed, target):
    def ignore(directory, names):
        return ['huynhngochuyhoang'] if Path(directory).relative_to(seed) == Path('io/github') else []
    shutil.copytree(seed, target, ignore=ignore, copy_function=shutil.copyfile)
    if (target / GROUP).exists():
        raise ValueError('Seed copied project artifacts')


def confirmation_selection(rows):
    historical = ('io.github.huynhngochuyhoang.httpstarter.benchmarks.'
                  'V34DefaultPathBenchmark.defaultV34NoNetworkWarmPublisher',
                  (('profile', 'RESILIENCE_ENABLED_ONLY'), ('scenario', 'GET')))
    keys = {reviewer.identity(row) for row in rows if row['latencyReview'] or row['allocationReview']}
    keys.add(historical)
    return [{'benchmark': name, 'params': dict(params)} for name, params in sorted(keys)]


def retain_artifacts(source, destination, modules):
    for module in modules:
        dest = destination / module
        dest.mkdir(parents=True)
        shutil.copy2(source / module / 'pom.xml', dest)
        artifacts = list((source / module / 'target').glob('*.jar'))
        if not artifacts:
            raise ValueError('Missing built artifacts: ' + module)
        for artifact in artifacts:
            shutil.copy2(artifact, dest)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('stage', choices=('prepare', 'api', 'primary', 'confirm', 'allocation'))
    parser.add_argument('--work', type=Path, required=True)
    parser.add_argument('--ref', default='HEAD')
    parser.add_argument('--seed-repository', type=Path)
    args = parser.parse_args()
    if os.geteuid() == 0:
        parser.error('Run as the normal user, not sudo')
    work = parity.safe_work(args.work)
    out = work / 'evidence'
    java_home = allocation.JAVA.parent.parent
    env = dict(os.environ, JAVA_HOME=str(java_home), MAVEN_OPTS='-Xmx512m -XX:ActiveProcessorCount=2')
    for key in ('JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS'):
        if env.get(key):
            raise ValueError('Remove inherited VM injection: ' + key)
    env['PATH'] = str(java_home / 'bin') + os.pathsep + env['PATH']
    os.umask(0o077)
    if args.stage == 'prepare':
        work.mkdir(parents=True, exist_ok=False)
        out.mkdir()
        commit = subprocess.check_output(['git', 'rev-parse', args.ref + '^{commit}'], cwd=ROOT, text=True).strip()
        native.write_json(out / 'source.json', {'commit': commit,
            'tree': subprocess.check_output(['git', 'rev-parse', commit + '^{tree}'], cwd=ROOT, text=True).strip(),
            'invokingStatus': subprocess.check_output(['git', 'status', '--short'], cwd=ROOT, text=True),
            'planSha256': native.checksum(PLAN), 'runnerSha256': native.checksum(Path(__file__))})
        shutil.copy2(PLAN, out / 'frozen-plan.md')
        shutil.copy2(__file__, out / 'runner.py')
    else:
        source = json.loads((out / 'source.json').read_text())
        commit = source['commit']
        if native.checksum(PLAN) != source['planSha256'] or native.checksum(Path(__file__)) != source['runnerSha256']:
            raise ValueError('Frozen plan/runner changed; preserve this attempt')
    stage = out / args.stage
    stage.mkdir(exist_ok=False)
    native.write_json(stage / 'environment.json', {k: env[k] for k in ('JAVA_HOME', 'MAVEN_OPTS')})

    def run(name, command, cwd, timeout=2400):
        native.run(stage, name, list(map(str, command)), cwd, env, timeout)

    def clone(name):
        source = work / ('source-' + name)
        run('clone-' + name, ['git', 'clone', '--local', '--no-hardlinks', '--no-checkout', ROOT, source], ROOT)
        run('checkout-' + name, ['git', 'checkout', '--detach', commit], source)
        return source

    def maven(source, repo):
        return ['mvn', '-B', '-ntp', '-s', str(source / '.mvn/maven-central-settings.xml'),
                '-Dmaven.repo.local=' + str(repo)]

    def provenance(source, repo, name, artifacts, destination):
        lane = 'v35-p10-' + name
        link = source / 'target/published-baseline-repositories' / (lane + '-4.4.2')
        link.parent.mkdir(parents=True, exist_ok=True)
        link.symlink_to(repo, target_is_directory=True)
        evidence = source / 'target' / ('provenance-' + name)
        run('provenance-' + name, ['bash', source / 'scripts/verify-published-baseline-provenance.sh',
            lane, '4.4.2', evidence, *artifacts], source)
        shutil.copytree(evidence, destination)
        shutil.copytree(repo / GROUP, destination / 'resolved-project')

    def benchmark_command(side, directory, selection, params=()):
        version = '4.4.2' if side == 'baseline' else '4.5.0-SNAPSHOT'
        return [str(allocation.JAVA), *allocation.FLAGS,
            '-Dbenchmark.project.version=4.5.0-SNAPSHOT', '-Dbenchmark.starter.version=' + version,
            '-Dbenchmark.api.compatibility.baseline.version=4.4.2', '-Dbenchmark.spring-boot.version=4.0.0',
            '-Dbenchmark.commit=' + commit, '-Dbenchmark.stack.context=V35 final matched acceptance',
            '-jar', str(out / ('build-' + side) / 'benchmarks.jar'), selection,
            *[item for key, value in params for item in ('-p', key + '=' + value)],
            '-bm', 'avgt', '-tu', 'ns', '-t', '1', '-wi', '5', '-w', '1s', '-i', '5', '-r', '1s',
            '-f', '2', '-prof', 'gc', '-foe', 'true', '-jvmArgs', ' '.join(allocation.FLAGS),
            '-rf', 'json', '-rff', str(directory / 'results.json')]

    status = {'status': 'failed'}
    try:
        if args.stage == 'prepare':
            if args.seed_repository is None:
                raise ValueError('A read-only third-party seed repository is required')
            run('java', ['java', '-version'], ROOT)
            run('maven', ['mvn', '-version'], ROOT)
            for name in ('current', 'baseline'):
                source = clone(name)
                repo = work / ('repository-' + name)
                parity.safe_layout(source, out, repo)
                seed_third_party(args.seed_repository.resolve(), repo)
                mvn = maven(source, repo)
                if name == 'current':
                    run('install-current', mvn + ['-DskipTests', 'install'], source)
                    run('packaging-40', ['bash', 'scripts/verify-generation-packaging.sh', '4.5.0-SNAPSHOT'], source)
                    shutil.copytree(source / 'target/generation-packaging', stage / 'packaging-40')
                    retain_artifacts(source, stage / 'artifacts', MODULES)
                build = out / ('build-' + name)
                build.mkdir()
                version = '4.4.2' if name == 'baseline' else '4.5.0-SNAPSHOT'
                command = mvn + ['-f', MODULE + '/pom.xml', '-Dbenchmark.starter.version=' + version]
                started = time.time()
                try:
                    run('benchmark-' + name, command + ['-DargLine=-XX:+DisableExplicitGC -XX:ActiveProcessorCount=2',
                                                       'package'], source)
                finally:
                    totals = parity.collect_reports(source, build, started, [MODULE])
                parity.require_pass(totals)
                shutil.copy2(source / MODULE / 'target/benchmarks.jar', build / 'benchmarks.jar')
                for label, goal, prop in [('classpath', 'dependency:build-classpath', 'mdep.outputFile'),
                        ('dependencies', 'dependency:tree', 'outputFile'), ('effective-pom', 'help:effective-pom', 'output')]:
                    run(name + '-' + label, command + [goal, '-D' + prop + '=' + str(build / (label + '.txt'))], source)
                inputs.ROOT, inputs.MODULE = source, source / MODULE
                starter, dependencies = inputs.classpath_inputs((build / 'classpath.txt').read_text(), version, name == 'baseline')
                native.write_json(build / 'inputs.json', {'starter': starter, 'dependencies': dependencies,
                    'published': name == 'baseline', 'sourceFiles': inputs.source_hashes(),
                    'sourceCommit': commit, 'shadedSha256': inputs.digest(build / 'benchmarks.jar'),
                    'verifiedShadedStarterClasses': inputs.verify_shaded(build / 'benchmarks.jar', starter),
                    'worktree': subprocess.check_output(['git', 'status', '--porcelain'], cwd=source, text=True)})
                if name == 'baseline':
                    provenance(source, repo, name, ['reactive-http-client-starter'], stage / 'provenance-baseline')
            pair = [json.loads((out / ('build-' + side) / 'inputs.json').read_text()) for side in ('baseline', 'current')]
            native.write_json(stage / 'matched-inputs.json', inputs.compare(*pair))
            native.write_json(stage / 'byte-audit.json', allocation.audit_pair(out))
        elif args.stage == 'api':
            failures = []
            for kind in ('root', 'starter'):
                source = clone('api-' + kind)
                repo = work / ('repository-api-' + kind)
                seed_third_party((work / 'repository-baseline'), repo)
                try:
                    command = maven(source, repo)
                    selection = [] if kind == 'root' else ['-pl', MODULES[0]]
                    run('strict-' + kind, command + selection + ['-Papi-compatibility', '-DskipTests', 'verify'], source)
                    modules = MODULES if kind == 'root' else MODULES[:1]
                    retain_artifacts(source, stage / ('artifacts-' + kind), modules)
                    provenance(source, repo, 'api-' + kind, modules, stage / ('provenance-' + kind))
                    for module in modules:
                        shutil.copytree(source / module / 'target/japicmp', stage / kind / module / 'japicmp')
                        run(kind + '-effective-' + module, command + ['-f', module + '/pom.xml', '-Papi-compatibility',
                            'help:effective-pom', '-Doutput=' + str(stage / kind / module / 'effective-pom.xml')], source)
                    if kind == 'root':
                        run('packaging-api-40', ['bash', 'scripts/verify-generation-packaging.sh', '4.5.0-SNAPSHOT'], source)
                        run('module-clean-41', command + ['-pl', ','.join(MODULES), 'clean'], source)
                        run('package-41', command + ['-Dspring-boot.version=4.1.0', '-DskipTests', 'verify'], source)
                        run('packaging-41', ['bash', 'scripts/verify-generation-packaging.sh', '4.5.0-SNAPSHOT'], source)
                        shutil.copytree(source / 'target/generation-packaging', stage / 'packaging-41')
                        retain_artifacts(source, stage / 'artifacts-41', MODULES)
                except (OSError, RuntimeError, subprocess.SubprocessError) as error:
                    failures.append({'lane': kind, 'error': str(error)})
                finally:
                    for module in MODULES:
                        report = source / module / 'target/japicmp'
                        dest = stage / ('retained-' + kind) / module
                        if report.exists():
                            shutil.copytree(report, dest)
            native.write_json(stage / 'failures.json', failures)
            if failures:
                raise RuntimeError('API/packaging failure; both independent lanes attempted')
            source = work / 'source-current'
            env['MAVEN_OPTS'] += ' -Dmaven.repo.local=' + str(work / 'repository-current')
            for name in ('api-compatibility-fixtures', 'published-baseline-fixtures'):
                run(name, ['bash', 'scripts/verify-' + name + '.sh'], source)
            for name in ('api-compatibility-fixtures', 'published-baseline-provenance-fixtures'):
                shutil.copytree(source / 'target' / name, stage / name)
        elif args.stage in ('primary', 'confirm'):
            allocation.audit_pair(out)
            if args.stage == 'primary':
                rows = {}
                for side in ('baseline', 'current'):
                    directory = stage / side
                    selection = '.*(V34DefaultPathBenchmark|V34ConstructionBenchmark|V33PlanningCostBenchmark.metadataCold|StarterInvocationInternalsBenchmark).*'
                    allocation.run(benchmark_command(side, directory, selection), directory, timeout=3600)
                    rows[side] = json.loads((directory / 'results.json').read_text())
                    reviewer.validate(rows[side])
                reviewed = reviewer.review(rows['baseline'], rows['current'])
                native.write_json(stage / 'review.json', reviewed)
                native.write_json(stage / 'confirmation-selection.json', confirmation_selection(reviewed))
            else:
                selection = json.loads((out / 'primary/confirmation-selection.json').read_text())
                native.write_json(stage / 'selection.json', selection)
                rows = {'baseline': [], 'current': []}
                for side in ('current', 'baseline'):
                    for index, row in enumerate(selection):
                        directory = stage / (side + '-' + str(index))
                        allocation.run(benchmark_command(side, directory, '^' + re.escape(row['benchmark']) + '$',
                            sorted(row['params'].items())), directory, timeout=300)
                        values = json.loads((directory / 'results.json').read_text())
                        if len(values) != 1 or reviewer.identity(values[0]) != reviewer.identity(row):
                            raise ValueError('Wrong confirmation identity')
                        rows[side].extend(values)
                native.write_json(stage / 'review.json', reviewer.review(rows['baseline'], rows['current'], complete=False))
        else:
            # Separate, preselected attribution schedule, never a replacement score.
            for phase in ('prepare', 'scored', 'diagnostic'):
                run('allocation-' + phase, [sys.executable, '-B', ROOT / 'scripts/investigate-v35-allocation.py',
                    phase, '--pair', out, '--output', stage / 'run'], ROOT, timeout=2400)
            originals = work / 'private-jfr'
            originals.mkdir(mode=0o700)
            recordings = []
            for index, recording in enumerate(sorted((stage / 'run').rglob('*.jfr'))):
                original = originals / (str(index) + '.jfr')
                shutil.move(recording, original)
                run('scrub-' + str(index), [java_home / 'bin/jfr', 'scrub', '--include-events',
                    'jdk.ObjectAllocationSample', original, recording], ROOT)
                recordings.append({'originalSha256': native.checksum(original),
                    'exportedSha256': native.checksum(recording), 'path': str(recording.relative_to(out))})
            native.write_json(stage / 'recordings.json', recordings)
            run('review', [sys.executable, '-B', ROOT / 'scripts/review-v35-allocation.py',
                stage / 'run', '--output', stage / 'review'], ROOT)
        status['status'] = 'passed'
    finally:
        native.write_json(stage / 'summary.json', status)
    print('Completed ' + args.stage, flush=True)


if __name__ == '__main__':
    main()
