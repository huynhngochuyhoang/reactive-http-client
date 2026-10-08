#!/usr/bin/env python3
"""Run the frozen C001 comparison with the existing JMH harness, not a timing engine."""
import argparse
import importlib.util
import json
import os
from pathlib import Path
import shutil
import statistics
import subprocess
import zipfile


def module(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


RUN = module('allocation_runner', 'investigate-v35-allocation.py')
REVIEW = module('cost_review', 'review-v34-benchmark-results.py')
ROOT = RUN.ROOT
PLAN = ROOT / 'roadmaps/v35/DISCOVERY-COMPOSITION.md'
SAVED = ROOT / 'target/release-evidence/v34/priority10'
PACKAGE = 'io.github.huynhngochuyhoang.httpstarter.benchmarks.'
PROFILES = 'MINIMAL,AUTO_NO_REGISTRY,AUTO_REGISTRY,RESILIENCE_ENABLED_ONLY,OBSERVER,HOOK'
COMPOSITION = {PACKAGE + 'StarterDiagnosticsOverheadBenchmark.diagnosticsNoNetwork' + name + 'GetNoBody'
               for name in ('OneObserver', 'MultipleObservers', 'OneLifecycleHook', 'MultipleLifecycleHooks')}
WARM = {(PACKAGE + 'V34DefaultPathBenchmark.defaultV34NoNetwork' + phase,
         (('profile', profile), ('scenario', scenario)))
        for phase in ('WarmPublisher', 'WarmSubscription')
        for profile in PROFILES.split(',') for scenario in ('GET', 'TARGET')}


def stages():
    return [(order, side, kind) for order, sides in (
        ('forward', ('baseline', 'candidate')), ('reverse', ('candidate', 'baseline')))
        for side in sides for kind in ('warm', 'composition')]


def command(jar, directory, kind, profile=None):
    selection = ('.*V34DefaultPathBenchmark.defaultV34NoNetworkWarm(Publisher|Subscription)$' if kind == 'warm'
                 else '.*StarterDiagnosticsOverheadBenchmark.diagnosticsNoNetwork(OneObserver|MultipleObservers|OneLifecycleHook|MultipleLifecycleHooks)GetNoBody$')
    if kind == 'attribution':
        selection = '.*V34DefaultPathBenchmark.defaultV34NoNetworkWarmPublisher$'
    return [str(RUN.JAVA), *RUN.FLAGS, '-jar', str(jar), selection,
            *(['-p', f'profile={profile or PROFILES}', '-p', 'scenario=GET' if profile else 'scenario=GET,TARGET']
              if kind != 'composition' else []),
            '-bm', 'avgt', '-tu', 'ns', '-t', '1', '-wi', '5', '-w', '1s', '-i', '5', '-r', '1s',
            '-f', '1' if kind == 'attribution' else '2', '-prof', 'gc', '-foe', 'true',
            '-jvmArgs', ' '.join(RUN.FLAGS), '-rf', 'json', '-rff', str(directory / 'results.json'),
            *(['-prof', 'jfr'] if kind == 'attribution' else [])]


def audit(out):
    before = json.loads((out / 'baseline/inputs.json').read_text())
    after = json.loads((out / 'candidate/inputs.json').read_text())
    if before['sourceFiles'] != after['sourceFiles'] or before['dependencies'] != after['dependencies']:
        raise ValueError('Harness or non-starter inputs changed')
    for name, expected in before['sourceFiles'].items():
        if RUN.digest(ROOT / name) != expected:
            raise ValueError('Harness source changed: ' + name)
    for side, inputs in [('baseline', before), ('candidate', after)]:
        if RUN.digest(out / side / 'benchmarks.jar') != inputs['shadedSha256']:
            raise ValueError('Shaded artifact changed')
    classes = {}
    with zipfile.ZipFile(out / 'baseline/benchmarks.jar') as first, zipfile.ZipFile(out / 'candidate/benchmarks.jar') as second:
        if set(first.namelist()) != set(second.namelist()):
            raise ValueError('Shaded entry set changed')
        differences = [name for name in first.namelist() if first.read(name) != second.read(name)]
        classes = [name for name in differences if name.endswith('.class')]
        handler = 'io/github/huynhngochuyhoang/httpstarter/core/ReactiveClientInvocationHandler'
        if not classes or any(not (name == handler + '.class' or name.startswith(handler + '$')) for name in classes):
            raise ValueError('Unexpected executable changes: ' + str(classes))
        if any(not name.endswith('.class') and not name.startswith('META-INF/maven/io.github.huynhngochuyhoang/reactive-http-client-starter/')
               for name in differences):
            raise ValueError('Unexpected resource changes')
    old_order = [Path(p).name for p in (out / 'baseline/classpath.txt').read_text().strip().split(os.pathsep)]
    new_order = [Path(p).name for p in (out / 'candidate/classpath.txt').read_text().strip().split(os.pathsep)]
    if old_order != new_order:
        raise ValueError('Ordered classpath differs')
    result = {'changedClasses': classes, 'changedEntries': differences,
              'matchedHarnessFiles': len(before['sourceFiles']), 'matchedDependencies': len(before['dependencies']),
              'orderedClasspathMatches': True, 'baselineSha256': before['shadedSha256'], 'candidateSha256': after['shadedSha256']}
    RUN.save(out / 'pair-audit.json', result)
    return result


def validate_rows(rows, kind):
    REVIEW.HELPERS = COMPOSITION
    indexed = REVIEW.validate(rows, complete=False)
    expected = WARM if kind == 'warm' else {(name, ()) for name in COMPOSITION}
    if indexed.keys() != expected:
        raise ValueError('Incomplete/unexpected C001 rows')
    return indexed


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('stage', choices=('attribution', 'audit', 'score', 'review'))
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    out = args.output.resolve()
    os.umask(0o077)
    if args.stage == 'attribution':
        out.mkdir(parents=True, exist_ok=True)
        baseline = out / 'baseline'
        baseline.mkdir(exist_ok=False)
        RUN.audit_pair(SAVED)
        subprocess.run(['git', 'diff', '--exit-code', 'HEAD', '--', 'reactive-http-client-starter/src/main'], cwd=ROOT, check=True)
        for name in ('benchmarks.jar', 'inputs.json', 'classpath.txt', 'dependency-tree.txt', 'effective-pom.xml'):
            shutil.copy2(SAVED / 'build-current' / name, baseline / name)
        (out / 'frozen-plan.md').write_bytes(PLAN.read_bytes())
        (out / 'runner.py').write_bytes(Path(__file__).read_bytes())
        RUN.save(out / 'source.json', {'commit': RUN.git('rev-parse', 'HEAD'), 'tree': RUN.git('rev-parse', 'HEAD^{tree}'),
                 'status': RUN.git('status', '--short'), 'runnerSha256': RUN.digest(Path(__file__)),
                 'planSha256': RUN.digest(PLAN), 'javaSha256': RUN.digest(RUN.JAVA)})
        private = ROOT / 'target/v35-private-discovery'
        private.mkdir(mode=0o700, exist_ok=False)
        for profile in ('AUTO_NO_REGISTRY', 'HOOK'):
            directory = out / 'attribution' / profile
            RUN.run(command(baseline / 'benchmarks.jar', directory, 'attribution', profile), directory, timeout=180)
            recording, = directory.rglob('*.jfr')
            original = private / (profile + '.jfr')
            shutil.move(recording, original)
            original.chmod(0o600)
            jfr = RUN.JAVA.with_name('jfr')
            scrub = [str(jfr), 'scrub', '--include-events', 'jdk.ObjectAllocationSample', str(original), str(recording)]
            with (directory / 'scrub.log').open('w') as log:
                subprocess.run(scrub, stdout=log, stderr=subprocess.STDOUT, check=True)
            with (directory / 'allocations.json').open('w') as log:
                subprocess.run([str(jfr), 'print', '--json', '--events', 'jdk.ObjectAllocationSample', str(recording)], stdout=log, check=True)
            RUN.save(directory / 'privacy.json', {'scrubCommand': scrub, 'originalSha256': RUN.digest(original),
                     'exportedSha256': RUN.digest(recording), 'privateOriginalExcluded': True})
        return
    if args.stage in ('audit', 'score'):
        source = json.loads((out / 'source.json').read_text())
        if source['planSha256'] != RUN.digest(PLAN) or source['runnerSha256'] != RUN.digest(Path(__file__)) or source['javaSha256'] != RUN.digest(RUN.JAVA):
            raise ValueError('Frozen inputs changed')
        audit(out)
    if args.stage == 'score':
        for order, side, kind in stages():
            directory = out / 'scored' / f'{order}-{side}-{kind}'
            RUN.run(command(out / side / 'benchmarks.jar', directory, kind), directory, timeout=1200 if kind == 'warm' else 300)
            validate_rows(json.loads((directory / 'results.json').read_text()), kind)
    elif args.stage == 'review':
        destination = out / 'review'
        destination.mkdir(exist_ok=False)
        reports = {}
        for order in ('forward', 'reverse'):
            by_side = {}
            for side in ('baseline', 'candidate'):
                rows = []
                for kind in ('warm', 'composition'):
                    directory = out / 'scored' / f'{order}-{side}-{kind}'
                    if json.loads((directory / 'command.json').read_text())['exit'] != 0:
                        raise ValueError('Incomplete/failed stage')
                    selected = json.loads((directory / 'results.json').read_text())
                    validate_rows(selected, kind)
                    rows += selected
                by_side[side] = rows
            reports[order] = REVIEW.review(by_side['baseline'], by_side['candidate'], complete=False)
            indexed = {side: {REVIEW.identity(r): r for r in rows} for side, rows in by_side.items()}
            for row in reports[order]:
                for side in by_side:
                    original = indexed[side][REVIEW.identity(row)]
                    row[side + 'ForkBytes'] = [statistics.mean(fork) for fork in original['secondaryMetrics']['gc.alloc.rate.norm']['rawData']]
        RUN.save(destination / 'review.json', reports)
        print(json.dumps({order: {'rows': len(rows), 'latencyFlags': sum(r['latencyReview'] for r in rows),
                         'allocationFlags': sum(r['allocationReview'] for r in rows)} for order, rows in reports.items()}))


if __name__ == '__main__':
    main()
