#!/usr/bin/env python3
"""Run C003's frozen comparison using existing JMH workloads and review rules."""
import argparse
import importlib.util
import json
import os
from pathlib import Path
import shutil
import statistics
import subprocess

SPEC = importlib.util.spec_from_file_location('discovery', Path(__file__).with_name('investigate-v35-discovery.py'))
DISCOVERY = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(DISCOVERY)
RUN, REVIEW = DISCOVERY.RUN, DISCOVERY.REVIEW
ROOT = RUN.ROOT
PLAN = ROOT / 'roadmaps/v35/STATIC-PLANNING-PROJECTION.md'
BASELINE = ROOT / 'target/release-evidence/v35/priority5/baseline'
LOGGING = DISCOVERY.PACKAGE + 'StarterDiagnosticsOverheadBenchmark.metadataOnlyExchangeLoggingGetNoBody'
CONTROLS = REVIEW.HELPERS | {LOGGING}
REVIEW.HELPERS = CONTROLS
FIRST = {(DISCOVERY.PACKAGE + 'V34ConstructionBenchmark.defaultV34Construction' + phase,
          (('profile', profile),))
         for phase in ('FirstPublisher', 'FirstCall') for profile in ('AUTO_NO_REGISTRY', 'AUTO_REGISTRY')}


def stages():
    return [(order, side, kind) for order, sides in (
        ('forward', ('baseline', 'candidate')), ('reverse', ('candidate', 'baseline')))
        for side in sides for kind in ('warm', 'controls', 'first')]


def command(jar, directory, kind, diagnostic=None):
    params = []
    if kind == 'warm':
        selection = '.*V34DefaultPathBenchmark.defaultV34NoNetworkWarm(Publisher|Subscription)$'
        params = ['-p', 'profile=' + (diagnostic or DISCOVERY.PROFILES),
                  '-p', 'scenario=GET' if diagnostic else 'scenario=GET,TARGET']
        if diagnostic:
            selection = '.*V34DefaultPathBenchmark.defaultV34NoNetworkWarmPublisher$'
    elif kind == 'first':
        selection = '.*V34ConstructionBenchmark.defaultV34ConstructionFirst(Publisher|Call)$'
        params = ['-p', 'profile=AUTO_NO_REGISTRY,AUTO_REGISTRY']
    else:
        selection = '(' + '|'.join(sorted(name.replace('.', r'\.') for name in CONTROLS)) + ')$'
    flags = RUN.FLAGS + (['-XX:FlightRecorderOptions=stackdepth=128'] if diagnostic else [])
    return [str(RUN.JAVA), *RUN.FLAGS, '-jar', str(jar), selection, *params,
            '-bm', 'avgt', '-tu', 'ns', '-t', '1', '-wi', '5', '-w', '1s', '-i', '5', '-r', '1s',
            '-f', '1' if diagnostic else '2', '-prof', 'gc', '-foe', 'true',
            '-jvmArgs', ' '.join(flags), '-rf', 'json', '-rff', str(directory / 'results.json'),
            *(['-prof', 'jfr'] if diagnostic else [])]


def validate_rows(rows, kind):
    indexed = REVIEW.validate(rows, complete=False)
    expected = DISCOVERY.WARM if kind == 'warm' else FIRST if kind == 'first' else {(name, ()) for name in CONTROLS}
    if indexed.keys() != expected:
        raise ValueError('Incomplete/unexpected C003 rows')
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
        subprocess.run(['git', 'diff', '--exit-code', 'HEAD', '--', 'reactive-http-client-starter/src/main'], cwd=ROOT, check=True)
        for name in ('benchmarks.jar', 'inputs.json', 'classpath.txt', 'dependency-tree.txt', 'effective-pom.xml'):
            shutil.copy2(BASELINE / name, baseline / name)
        (out / 'frozen-plan.md').write_bytes(PLAN.read_bytes())
        (out / 'runner.py').write_bytes(Path(__file__).read_bytes())
        RUN.save(out / 'source.json', {'commit': RUN.git('rev-parse', 'HEAD'), 'tree': RUN.git('rev-parse', 'HEAD^{tree}'),
                 'status': RUN.git('status', '--short'), 'runnerSha256': RUN.digest(Path(__file__)),
                 'supportRunnerSha256': RUN.digest(Path(DISCOVERY.__file__)),
                 'planSha256': RUN.digest(PLAN), 'javaSha256': RUN.digest(RUN.JAVA)})
        private = ROOT / 'target/v35-private-planning'
        private.mkdir(mode=0o700, exist_ok=False)
        for profile in ('MINIMAL', 'AUTO_REGISTRY'):
            directory = out / 'attribution' / profile
            RUN.run(command(baseline / 'benchmarks.jar', directory, 'warm', profile), directory, timeout=180)
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
        for key, path in [('planSha256', PLAN), ('runnerSha256', Path(__file__)),
                          ('supportRunnerSha256', Path(DISCOVERY.__file__)), ('javaSha256', RUN.JAVA)]:
            if source[key] != RUN.digest(path):
                raise ValueError('Frozen inputs changed: ' + key)
        DISCOVERY.audit(out)
    if args.stage == 'score':
        for order, side, kind in stages():
            directory = out / 'scored' / f'{order}-{side}-{kind}'
            RUN.run(command(out / side / 'benchmarks.jar', directory, kind), directory, timeout=1500)
            validate_rows(json.loads((directory / 'results.json').read_text()), kind)
    elif args.stage == 'review':
        destination = out / 'review'
        destination.mkdir(exist_ok=False)
        reports = {}
        for order in ('forward', 'reverse'):
            by_side = {}
            for side in ('baseline', 'candidate'):
                rows = []
                for kind in ('warm', 'controls', 'first'):
                    directory = out / 'scored' / f'{order}-{side}-{kind}'
                    if json.loads((directory / 'command.json').read_text())['exit'] != 0:
                        raise ValueError('Incomplete/failed stage')
                    selected = json.loads((directory / 'results.json').read_text())
                    validate_rows(selected, kind)
                    rows += selected
                by_side[side] = rows
            reports[order] = REVIEW.review(by_side['baseline'], by_side['candidate'], complete=False)
            indexed = {side: {REVIEW.identity(row): row for row in rows} for side, rows in by_side.items()}
            for row in reports[order]:
                for side in by_side:
                    original = indexed[side][REVIEW.identity(row)]
                    row[side + 'ForkBytes'] = [statistics.mean(f) for f in original['secondaryMetrics']['gc.alloc.rate.norm']['rawData']]
        RUN.save(destination / 'review.json', reports)
        print(json.dumps({order: {'rows': len(rows), 'latencyFlags': sum(r['latencyReview'] for r in rows),
                         'allocationFlags': sum(r['allocationReview'] for r in rows)} for order, rows in reports.items()}))


if __name__ == '__main__':
    main()
