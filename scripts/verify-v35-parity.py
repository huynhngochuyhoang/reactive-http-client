#!/usr/bin/env python3
"""Regenerate V35 parity evidence in disposable checkouts, never in the user's reactor."""
import argparse
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
MODULES = tuple('reactive-http-client-' + name for name in ('starter', 'test', 'otel', 'benchmarks'))
spec = importlib.util.spec_from_file_location('native_runner', Path(__file__).with_name('verify-v33-native.py'))
native = importlib.util.module_from_spec(spec)
spec.loader.exec_module(native)


def safe_work(work, root=ROOT):
    work, root = work.resolve(), root.resolve()
    if work == root or work in root.parents or root in work.parents or 'target' in work.parts:
        raise ValueError('Work must be outside the original reactor and all target directories')
    return work


def safe_layout(source, evidence, repository):
    paths = [p.resolve() for p in (source, evidence, repository)]
    for index, path in enumerate(paths):
        if 'target' in path.parts:
            raise ValueError('Verification roots must not be inside target directories')
        for other in paths[index + 1:]:
            if path == other or path in other.parents or other in path.parents:
                raise ValueError('Source, evidence and repository must be separate siblings')


def collect_reports(source, out, started, modules=MODULES, folder='surefire-reports'):
    results = {}
    for module in modules:
        rows = []
        target = out / module / folder
        target.mkdir(parents=True)
        for report in sorted((source / module / 'target' / folder).glob('TEST-*.xml')):
            if report.stat().st_mtime < started:
                continue
            shutil.copy2(report, target)
            node = ET.parse(report).getroot()
            rows.append({'name': node.get('name'), **{k: int(node.get(k, 0))
                         for k in ('tests', 'failures', 'errors', 'skipped')}})
        results[module] = rows
    native.write_json(out / 'reports.json', results)
    totals = {module: {k: sum(row[k] for row in rows) for k in ('tests', 'failures', 'errors', 'skipped')}
              for module, rows in results.items()}
    native.write_json(out / 'totals.json', totals)
    return totals


def require_pass(totals):
    if not totals or any(not row['tests'] or any(row[k] for k in ('failures', 'errors', 'skipped'))
                         for row in totals.values()):
        raise RuntimeError(f'Incomplete or unsuccessful reports: {totals}')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('stage', choices=['init', 'full40', 'full41', 'consumer40', 'consumer41',
                        'minimal40', 'minimal41', 'jvm40', 'jvm41', 'cache-reachability',
                        'handoff-reachability', 'benchmark-reachability'])
    parser.add_argument('--work', type=Path, required=True, help='fresh external workspace, e.g. /tmp/v35-parity-new')
    parser.add_argument('--ref', default='HEAD')
    parser.add_argument('--java-home', type=Path, default=os.environ.get('JAVA_HOME'))
    parser.add_argument('--seed-repository', type=Path)
    args = parser.parse_args()
    if os.geteuid() == 0:
        parser.error('Run as your normal user, not sudo')
    work = safe_work(args.work)
    source = work / ('source-41' if args.stage.endswith('41') else 'source-40')
    repository, evidence = work / 'repository', work / 'evidence'
    safe_layout(source, evidence, repository)
    if args.java_home is None:
        parser.error('Set JAVA_HOME to JDK 21 or pass --java-home')
    java_home = args.java_home.resolve()
    env = dict(os.environ, JAVA_HOME=str(java_home), MAVEN_OPTS='-Xmx512m -XX:ActiveProcessorCount=2',
               JAVA_TOOL_OPTIONS='-XX:ActiveProcessorCount=2', PYTHONDONTWRITEBYTECODE='1')
    env['PATH'] = str(java_home / 'bin') + os.pathsep + env['PATH']
    os.umask(0o077)
    if args.stage == 'init':
        work.mkdir(parents=True, exist_ok=False)
        evidence.mkdir()
        commit = subprocess.check_output(['git', 'rev-parse', '--verify', args.ref + '^{commit}'], cwd=ROOT, text=True).strip()
        native.write_json(work / 'workspace.json', {'commit': commit, 'original': str(ROOT), 'repository': str(repository)})
    else:
        if not (work / 'workspace.json').is_file():
            parser.error('Run init first')
        metadata = json.loads((work / 'workspace.json').read_text())
        if metadata['original'] != str(ROOT) or metadata['repository'] != str(repository):
            parser.error('Workspace ownership does not match this runner')
        commit = metadata['commit']
    out = evidence / args.stage
    out.mkdir(exist_ok=False)
    shutil.copy2(__file__, out / 'runner.py')
    native.write_json(out / 'environment.json', {key: env[key] for key in ('JAVA_HOME', 'MAVEN_OPTS', 'JAVA_TOOL_OPTIONS')})
    native.write_json(out / 'source.json', {'commit': commit, 'originalStatus': subprocess.check_output(
        ['git', 'status', '--short'], cwd=ROOT, text=True)})

    def run(name, command, cwd=source):
        native.run(out, name, command, cwd, env, timeout=2400)

    status = {'status': 'failed'}
    try:
        run('java', ['java', '-version'], ROOT)
        run('maven', ['mvn', '-version'], ROOT)
        if args.stage == 'init':
            repository.mkdir()
            if args.seed_repository:
                run('seed', ['cp', '-RL', '--reflink=auto', '--no-preserve=mode,ownership',
                             str(args.seed_repository.resolve()) + '/.', str(repository)], ROOT)
            for row in ('40', '41'):
                checkout = work / ('source-' + row)
                run('clone-' + row, ['git', 'clone', '--local', '--no-hardlinks', '--no-checkout', str(ROOT), str(checkout)], ROOT)
                run('checkout-' + row, ['git', 'checkout', '--detach', commit], checkout)
                if subprocess.check_output(['git', 'status', '--porcelain'], cwd=checkout).strip():
                    raise RuntimeError('Checkout not clean')
        else:
            actual = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=source, text=True).strip()
            source_status = subprocess.check_output(['git', 'status', '--porcelain'], cwd=source, text=True)
            native.write_json(out / 'checkout.json', {'commit': actual, 'status': source_status,
                'tree': subprocess.check_output(['git', 'rev-parse', 'HEAD^{tree}'], cwd=source, text=True).strip()})
            if actual != commit or source_status.strip():
                raise RuntimeError('Verification checkout changed')
            maven = ['mvn', '-B', '-ntp', '-s', str(source / '.mvn/maven-central-settings.xml'),
                     '-Dmaven.repo.local=' + str(repository)]
            if args.stage.startswith('full'):
                boot = '4.1.0' if args.stage.endswith('41') else '4.0.0'
                started = time.time()
                try:
                    # Fresh per-row checkouts need no clean; root target never holds evidence or dependencies.
                    run('suite', maven + ['-Dspring-boot.version=' + boot, '-Pbenchmarks',
                        '-DargLine=-XX:+DisableExplicitGC -XX:ActiveProcessorCount=2', '-Dmaven.javadoc.skip=true', 'install'])
                finally:
                    totals = collect_reports(source, out, started)
                require_pass(totals)
                (out / 'artifacts').mkdir()
                for module in MODULES:
                    command = maven + ['-Dspring-boot.version=' + boot, '-f', str(source / module / 'pom.xml')]
                    for label, goal, key in [('effective-pom', 'help:effective-pom', 'output'),
                                              ('dependencies', 'dependency:tree', 'outputFile'),
                                              ('classpath', 'dependency:build-classpath', 'mdep.outputFile')]:
                        run(module + '-' + label, command + [goal, '-D' + key + '=' + str(out / module / (label + '.txt'))])
                    if module != MODULES[-1]:
                        for artifact in (repository / 'io/github/huynhngochuyhoang' / module / '4.5.0-SNAPSHOT').glob('*'):
                            if artifact.suffix in ('.jar', '.pom'):
                                shutil.copy2(artifact, out / 'artifacts')
            elif args.stage.endswith('reachability'):
                benchmark = args.stage == 'benchmark-reachability'
                cache = args.stage == 'cache-reachability'
                module = MODULES[-1] if benchmark else MODULES[0]
                profile = 'v34-benchmark-reachability' if benchmark else (
                    'v32-cache-reachability' if cache else 'v31-handoff-reachability')
                folder = 'cache-reachability-reports' if benchmark or cache else 'surefire-reports'
                started = time.time()
                try:
                    run('suite', maven + ['-f', str(source / module / 'pom.xml'), '-P' + profile, 'test'])
                finally:
                    totals = collect_reports(source, out, started, [module], folder)
                require_pass(totals)
            else:
                env['JAVA_TOOL_OPTIONS'] += ' -XX:+DisableExplicitGC'
                native.write_json(out / 'environment.json', {key: env[key] for key in ('JAVA_HOME', 'MAVEN_OPTS', 'JAVA_TOOL_OPTIONS')})
                run('parity', [sys.executable, '-B', str(source / 'scripts/verify-v33-parity.py'), args.stage,
                              '--repository', str(repository), '--evidence', str(out)])
        status['status'] = 'passed'
    finally:
        native.write_json(out / 'summary.json', status)
        files = sorted(path for path in out.rglob('*') if path.is_file())
        (out / 'SHA256SUMS').write_text(''.join(f'{native.checksum(path)}  {path.relative_to(out)}\n' for path in files))
    print(f'Passed {args.stage}: {out}', flush=True)


if __name__ == '__main__':
    main()
