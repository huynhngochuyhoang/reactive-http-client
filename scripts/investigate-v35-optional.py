#!/usr/bin/env python3
"""Compare independent C004 candidates with the frozen existing JMH workloads."""
import argparse
import importlib.util
import json
import os
from pathlib import Path
import shutil
import statistics
import zipfile

SPEC = importlib.util.spec_from_file_location('discovery', Path(__file__).with_name('investigate-v35-discovery.py'))
D = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(D)
RUN, REVIEW = D.RUN, D.REVIEW
ROOT = RUN.ROOT
PLAN = ROOT / 'roadmaps/v35/OPTIONAL-PREPARATION.md'
SAVED = ROOT / 'target/release-evidence/v35/priority6/baseline'
CACHE_NAMES = {'io.github.huynhngochuyhoang.httpstarter.core.V30CacheWorkPerformanceBenchmark.cacheV30NoNetwork' + name
               for name in ('Hit', 'CallerRejection')}
REVIEW.HELPERS |= CACHE_NAMES
CONSTRUCTION = {(D.PACKAGE + 'V34ConstructionBenchmark.defaultV34Construction' + phase, (('profile', profile),))
                for phase in ('ContextAndProxy', 'FirstPublisher', 'FirstCall')
                for profile in ('AUTO_NO_REGISTRY', 'AUTO_REGISTRY')}
CACHE = {(name, (('metered', metered),)) for name in CACHE_NAMES for metered in ('false', 'true')}
SELECTIONS = {'warm': D.WARM, 'construction': CONSTRUCTION, 'cache': CACHE}


def stages():
    return [(order, side, kind) for order, sides in (
        ('forward', ('baseline', 'preparation', 'reuse')), ('reverse', ('reuse', 'preparation', 'baseline')))
        for side in sides for kind in SELECTIONS]


def command(jar, directory, kind):
    if kind == 'warm':
        selection = '.*V34DefaultPathBenchmark.defaultV34NoNetworkWarm(Publisher|Subscription)$'
        params = ['-p', 'profile=' + D.PROFILES, '-p', 'scenario=GET,TARGET']
    elif kind == 'construction':
        selection = '.*V34ConstructionBenchmark.defaultV34Construction(ContextAndProxy|FirstPublisher|FirstCall)$'
        params = ['-p', 'profile=AUTO_NO_REGISTRY,AUTO_REGISTRY']
    else:
        selection = '.*V30CacheWorkPerformanceBenchmark.cacheV30NoNetwork(Hit|CallerRejection)$'
        params = ['-p', 'metered=false,true']
    return [str(RUN.JAVA), *RUN.FLAGS, '-jar', str(jar), selection, *params,
            '-bm', 'avgt', '-tu', 'ns', '-t', '1', '-wi', '5', '-w', '1s', '-i', '5', '-r', '1s',
            '-f', '2', '-prof', 'gc', '-foe', 'true', '-jvmArgs', ' '.join(RUN.FLAGS),
            '-rf', 'json', '-rff', str(directory / 'results.json')]


def unparameterized_control(row):
    # The reused V34 metric validator accepts unparameterized external controls.
    return dict(row, params={}) if row['benchmark'] in CACHE_NAMES else row


def validate_rows(rows, kind):
    indexed = {REVIEW.identity(row): row for row in rows}
    if len(indexed) != len(rows) or indexed.keys() != SELECTIONS[kind]:
        raise ValueError('Incomplete/unexpected C004 rows')
    for row in rows:
        REVIEW.validate([unparameterized_control(row)], complete=False)
    return indexed


def compare(before, after):
    if before.keys() != after.keys():
        raise ValueError('Unmatched C004 rows')
    result = []
    for key, old in before.items():
        new = after[key]
        row, = REVIEW.review([unparameterized_control(old)], [unparameterized_control(new)], complete=False)
        row['params'] = old.get('params', {})
        for side, original in [('baseline', old), ('candidate', new)]:
            row[side + 'ForkBytes'] = [statistics.mean(fork) for fork in original['secondaryMetrics']['gc.alloc.rate.norm']['rawData']]
        result.append(row)
    return sorted(result, key=REVIEW.identity)


def audit(out):
    before = json.loads((out / 'baseline/inputs.json').read_text())
    source = json.loads((out / 'source.json').read_text())
    for key, path in [('runnerSha256', Path(__file__)), ('supportRunnerSha256', Path(D.__file__)), ('javaSha256', RUN.JAVA)]:
        if source[key] != RUN.digest(path):
            raise ValueError('Changed frozen input: ' + key)
    if RUN.digest(out / 'frozen-plan.md') != source['planSha256'] or not PLAN.read_bytes().startswith((out / 'frozen-plan.md').read_bytes()):
        raise ValueError('Changed frozen plan')
    for name, expected in before['sourceFiles'].items():
        if RUN.digest(ROOT / name) != expected:
            raise ValueError('Changed harness: ' + name)
    result = {}
    for side, owners in [('preparation', ('ReactiveClientInvocationHandler', 'LocalResponseCacheManager')),
                         ('reuse', ('EffectiveCachePolicy',))]:
        after = json.loads((out / side / 'inputs.json').read_text())
        if before['sourceFiles'] != after['sourceFiles'] or before['dependencies'] != after['dependencies']:
            raise ValueError('Harness/dependencies differ')
        for name, data in [('baseline', before), (side, after)]:
            if RUN.digest(out / name / 'benchmarks.jar') != data['shadedSha256']:
                raise ValueError('Artifact differs')
        order = lambda name: [Path(p).name for p in (out / name / 'classpath.txt').read_text().strip().split(os.pathsep)]
        if order('baseline') != order(side):
            raise ValueError('Classpath order differs')
        prefixes = ['io/github/huynhngochuyhoang/httpstarter/core/' + owner for owner in owners]
        with zipfile.ZipFile(out / 'baseline/benchmarks.jar') as old, zipfile.ZipFile(out / side / 'benchmarks.jar') as new:
            if set(old.namelist()) != set(new.namelist()):
                raise ValueError('Entry set differs')
            changed = [name for name in old.namelist() if old.read(name) != new.read(name)]
            if not changed or any(not any(name == p + '.class' or name.startswith(p + '$') for p in prefixes) for name in changed):
                raise ValueError('Unexpected changed entries: ' + str(changed))
        result[side] = {'changedEntries': changed, 'baselineSha256': before['shadedSha256'],
                        'candidateSha256': after['shadedSha256'], 'harnessFiles': len(before['sourceFiles']),
                        'nonStarterDependencies': len(before['dependencies']), 'orderedClasspathMatches': True}
    RUN.save(out / 'pair-audit.json', result)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('stage', choices=('freeze', 'audit', 'score', 'review'))
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    out = args.output.resolve()
    os.umask(0o077)
    if args.stage == 'freeze':
        out.mkdir(parents=True, exist_ok=True)
        baseline = out / 'baseline'
        baseline.mkdir(exist_ok=False)
        for name in ('benchmarks.jar', 'inputs.json', 'classpath.txt', 'dependency-tree.txt', 'effective-pom.xml'):
            shutil.copy2(SAVED / name, baseline / name)
        (out / 'frozen-plan.md').write_bytes(PLAN.read_bytes())
        (out / 'runner.py').write_bytes(Path(__file__).read_bytes())
        RUN.save(out / 'source.json', {'commit': RUN.git('rev-parse', 'HEAD'), 'tree': RUN.git('rev-parse', 'HEAD^{tree}'),
            'status': RUN.git('status', '--short'), 'runnerSha256': RUN.digest(Path(__file__)),
            'supportRunnerSha256': RUN.digest(Path(D.__file__)), 'planSha256': RUN.digest(PLAN), 'javaSha256': RUN.digest(RUN.JAVA)})
        return
    audit(out)
    if args.stage == 'score':
        for order, side, kind in stages():
            directory = out / 'scored' / f'{order}-{side}-{kind}'
            RUN.run(command(out / side / 'benchmarks.jar', directory, kind), directory, timeout=1500)
            validate_rows(json.loads((directory / 'results.json').read_text()), kind)
    elif args.stage == 'review':
        destination = out / 'review'
        destination.mkdir(exist_ok=False)
        reviews = {}
        for order in ('forward', 'reverse'):
            indexed = {}
            for side in ('baseline', 'preparation', 'reuse'):
                indexed[side] = {}
                for kind in SELECTIONS:
                    directory = out / 'scored' / f'{order}-{side}-{kind}'
                    if json.loads((directory / 'command.json').read_text())['exit'] != 0:
                        raise ValueError('Failed scored stage')
                    indexed[side].update(validate_rows(json.loads((directory / 'results.json').read_text()), kind))
            reviews[order] = {side: compare(indexed['baseline'], indexed[side]) for side in ('preparation', 'reuse')}
        RUN.save(destination / 'review.json', reviews)
        print(json.dumps({order: {side: {'rows': len(rows), 'latencyFlags': sum(r['latencyReview'] for r in rows),
              'allocationFlags': sum(r['allocationReview'] for r in rows)} for side, rows in sides.items()}
              for order, sides in reviews.items()}))


if __name__ == '__main__':
    main()
