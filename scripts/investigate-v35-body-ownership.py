#!/usr/bin/env python3
"""Execute the frozen null-body ownership comparison using existing JMH workloads."""
import argparse
import json
import os
from pathlib import Path
import shutil
import statistics
import subprocess

import importlib.util

SPEC = importlib.util.spec_from_file_location('discovery', Path(__file__).with_name('investigate-v35-discovery.py'))
DISCOVERY = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(DISCOVERY)
RUN = DISCOVERY.RUN
REVIEW = DISCOVERY.REVIEW
ROOT = RUN.ROOT
PLAN = ROOT / 'roadmaps/v35/BODY-REPORTING-OWNERSHIP.md'
BASELINE = ROOT / 'target/release-evidence/v35/priority4/baseline'
PREFIX = DISCOVERY.PACKAGE + 'V34DefaultPathBenchmark.'
LOOPBACK = {(PREFIX + 'defaultV34LoopbackCall', (('profile', profile), ('scenario', scenario)))
            for profile in ('MINIMAL', 'AUTO_NO_REGISTRY', 'AUTO_REGISTRY')
            for scenario in ('GET', 'TARGET', 'STRING', 'JSON', 'ENTITY', 'EMPTY', 'ERROR4', 'ERROR5')}


def stages():
    return [(order, side, kind) for order, sides in (
        ('forward', ('baseline', 'candidate')), ('reverse', ('candidate', 'baseline')))
        for side in sides for kind in ('warm', 'loopback')]


def command(jar, directory, kind, diagnostic=None):
    warm = kind == 'warm'
    selection = PREFIX + ('defaultV34NoNetworkWarm(Publisher|Subscription)$' if warm else 'defaultV34LoopbackCall$')
    profiles = DISCOVERY.PROFILES if warm else 'MINIMAL,AUTO_NO_REGISTRY,AUTO_REGISTRY'
    scenarios = 'GET,TARGET' if warm else 'GET,TARGET,STRING,JSON,ENTITY,EMPTY,ERROR4,ERROR5'
    if diagnostic:
        profiles, phase = diagnostic
        selection = PREFIX + 'defaultV34NoNetworkWarm' + phase + '$'
        scenarios = 'GET'
    flags = RUN.FLAGS + (['-XX:FlightRecorderOptions=stackdepth=128'] if diagnostic else [])
    return [str(RUN.JAVA), *RUN.FLAGS, '-jar', str(jar), selection,
            '-p', 'profile=' + profiles, '-p', 'scenario=' + scenarios,
            '-bm', 'avgt', '-tu', 'ns', '-t', '1', '-wi', '5', '-w', '1s', '-i', '5', '-r', '1s',
            '-f', '1' if diagnostic else '2', '-prof', 'gc', '-foe', 'true',
            '-jvmArgs', ' '.join(flags), '-rf', 'json', '-rff', str(directory / 'results.json'),
            *(['-prof', 'jfr'] if diagnostic else [])]


def validate_rows(rows, kind):
    indexed = REVIEW.validate(rows, complete=False)
    if indexed.keys() != (DISCOVERY.WARM if kind == 'warm' else LOOPBACK):
        raise ValueError('Incomplete/unexpected C002 rows')
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
        private = ROOT / 'target/v35-private-body-ownership'
        private.mkdir(mode=0o700, exist_ok=False)
        for profile, phase in [('AUTO_NO_REGISTRY', 'Publisher'), ('AUTO_REGISTRY', 'Subscription')]:
            directory = out / 'attribution' / (profile + '-' + phase)
            RUN.run(command(baseline / 'benchmarks.jar', directory, 'warm', (profile, phase)), directory, timeout=180)
            recording, = directory.rglob('*.jfr')
            original = private / (directory.name + '.jfr')
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
        if any(source[key] != RUN.digest(path) for key, path in (
                ('planSha256', PLAN), ('runnerSha256', Path(__file__)),
                ('supportRunnerSha256', Path(DISCOVERY.__file__)), ('javaSha256', RUN.JAVA))):
            raise ValueError('Frozen inputs changed')
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
                for kind in ('warm', 'loopback'):
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
                    row[side + 'ForkBytes'] = [statistics.mean(f) for f in original['secondaryMetrics']['gc.alloc.rate.norm']['rawData']]
        RUN.save(destination / 'review.json', reports)
        print(json.dumps({order: {'rows': len(rows), 'latencyFlags': sum(r['latencyReview'] for r in rows),
                         'allocationFlags': sum(r['allocationReview'] for r in rows)} for order, rows in reports.items()}))


if __name__ == '__main__':
    main()
