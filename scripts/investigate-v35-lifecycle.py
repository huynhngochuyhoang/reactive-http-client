#!/usr/bin/env python3
"""Attribute C005 lifecycle work; reuse explicitly identified unchanged P7 scores."""
import argparse
import collections
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess

SPEC = importlib.util.spec_from_file_location('optional', Path(__file__).with_name('investigate-v35-optional.py'))
P = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(P)
RUN, ROOT = P.RUN, P.ROOT
PLAN = ROOT / 'roadmaps/v35/CONSTRUCTION-LIFECYCLE.md'
PRIOR = ROOT / 'target/release-evidence/v35/priority7'
PRIOR_HASH = 'dfd7e7a7d990a1b28e4873873410204309f5b21b8726481cbef1031574aa6ced'
PACKAGE = 'io.github.huynhngochuyhoang.httpstarter.benchmarks.'


def stages():
    return [(phase, profile) for phase in ('ContextAndProxy', 'FirstPublisher', 'FirstCall', 'WarmSubscription')
            for profile in ('AUTO_NO_REGISTRY', 'AUTO_REGISTRY')]


def command(jar, directory, phase, profile):
    warm = phase == 'WarmSubscription'
    benchmark = PACKAGE + ('V34DefaultPathBenchmark.defaultV34NoNetworkWarmSubscription' if warm
                           else 'V34ConstructionBenchmark.defaultV34Construction' + phase)
    return [str(RUN.JAVA), *RUN.FLAGS, '-jar', str(jar), benchmark.replace('.', r'\.') + '$',
            '-p', 'profile=' + profile, *(['-p', 'scenario=GET'] if warm else []),
            '-bm', 'avgt', '-tu', 'ns', '-t', '1', '-wi', '5', '-w', '1s', '-i', '5', '-r', '1s',
            '-f', '1', '-prof', 'gc', '-prof', 'jfr', '-foe', 'true',
            '-jvmArgs', ' '.join(RUN.FLAGS + ['-XX:FlightRecorderOptions=stackdepth=128']),
            '-rf', 'json', '-rff', str(directory / 'results.json')]


def classify(event):
    if event['type'] != 'jdk.ObjectAllocationSample':
        raise ValueError('Non-allocation event in exported evidence')
    value = event['values']
    frames = [f['method']['type']['name'] + '.' + f['method']['name']
              for f in (value.get('stackTrace') or {}).get('frames', [])]
    production = next((f for f in frames if f.startswith('io/github/huynhngochuyhoang/httpstarter/')
                       and '/benchmarks/' not in f), None)
    return value['objectClass']['name'], production, frames


def summarize(events):
    counts = collections.Counter()
    weights = collections.Counter()
    for event in events:
        name, production, frames = classify(event)
        key = (name, production, tuple(frames))
        counts[key] += 1
        weights[key] += event['values'].get('weight', 0)
    return [dict(allocatedClass=name, nearestStarterFrame=production, frames=frames,
                 samples=count, sampledWeight=weights[(name, production, frames)])
            for (name, production, frames), count in counts.most_common()]


def audit(out):
    source = json.loads((out / 'source.json').read_text())
    for name, path in [('runnerSha256', Path(__file__)), ('supportRunnerSha256', Path(P.__file__)),
                       ('javaSha256', RUN.JAVA)]:
        if source[name] != RUN.digest(path):
            raise ValueError('Changed frozen input: ' + name)
    if RUN.digest(out / 'frozen-plan.md') != source['planSha256'] or not PLAN.read_bytes().startswith((out / 'frozen-plan.md').read_bytes()):
        raise ValueError('Changed plan')
    inputs = json.loads((out / 'baseline/inputs.json').read_text())
    if inputs['shadedSha256'] != RUN.digest(out / 'baseline/benchmarks.jar'):
        raise ValueError('Changed baseline')
    for name, expected in inputs['sourceFiles'].items():
        if RUN.digest(ROOT / name) != expected:
            raise ValueError('Changed harness ' + name)
    if RUN.digest(PRIOR / 'SHA256SUMS') != PRIOR_HASH:
        raise ValueError('Changed P7 inventory')


def freeze(out):
    out.mkdir(parents=True, exist_ok=True)
    baseline = out / 'baseline'
    baseline.mkdir(exist_ok=False)
    subprocess.run(['git', 'diff', '--exit-code', 'HEAD', '--', 'reactive-http-client-starter/src/main'], cwd=ROOT, check=True)
    if RUN.digest(PRIOR / 'SHA256SUMS') != PRIOR_HASH:
        raise ValueError('Changed prior evidence')
    subprocess.run(['sha256sum', '--quiet', '-c', 'SHA256SUMS'], cwd=PRIOR, check=True)
    for name in ('benchmarks.jar', 'inputs.json', 'classpath.txt', 'dependency-tree.txt', 'effective-pom.xml'):
        shutil.copy2(PRIOR / 'baseline' / name, baseline / name)
    shutil.copy2(PLAN, out / 'frozen-plan.md')
    shutil.copy2(Path(__file__), out / 'runner.py')
    RUN.save(out / 'source.json', dict(commit=RUN.git('rev-parse', 'HEAD'), tree=RUN.git('rev-parse', 'HEAD^{tree}'),
        status=RUN.git('status', '--short'), planSha256=RUN.digest(PLAN), runnerSha256=RUN.digest(Path(__file__)),
        supportRunnerSha256=RUN.digest(Path(P.__file__)), javaSha256=RUN.digest(RUN.JAVA), priorInventorySha256=PRIOR_HASH))
    reused = {}
    for order in ('forward', 'reverse'):
        rows = []
        for kind in ('construction', 'warm'):
            source = PRIOR / 'scored' / f'{order}-baseline-{kind}'
            directory = out / 'reused' / source.name
            shutil.copytree(source, directory)
            if json.loads((directory / 'command.json').read_text())['exit'] != 0:
                raise ValueError('Failed prior stage')
            selected = json.loads((directory / 'results.json').read_text())
            P.validate_rows(selected, kind)
            rows += selected
        reused[order] = rows
    RUN.save(out / 'reused-results.json', reused)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('stage', choices=('freeze', 'attribute', 'summarize', 'audit'))
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    out = args.output.resolve()
    os.umask(0o077)
    if args.stage == 'freeze':
        freeze(out)
        return
    audit(out)
    if args.stage == 'attribute':
        private = ROOT / 'target/v35-private-lifecycle'
        private.mkdir(mode=0o700, exist_ok=False)
        for phase, profile in stages():
            name = phase + '-' + profile
            directory = out / 'attribution' / name
            RUN.run(command(out / 'baseline/benchmarks.jar', directory, phase, profile), directory, timeout=180)
            recording, = directory.rglob('*.jfr')
            original = private / (name + '.jfr')
            shutil.move(recording, original)
            original.chmod(0o600)
            jfr = RUN.JAVA.with_name('jfr')
            scrub = [str(jfr), 'scrub', '--include-events', 'jdk.ObjectAllocationSample', str(original), str(recording)]
            with (directory / 'scrub.log').open('w') as log:
                subprocess.run(scrub, stdout=log, stderr=subprocess.STDOUT, check=True)
            with (directory / 'allocations.json').open('w') as log:
                subprocess.run([str(jfr), 'print', '--json', '--events', 'jdk.ObjectAllocationSample', str(recording)], stdout=log, check=True)
            RUN.save(directory / 'privacy.json', dict(scrubCommand=scrub, originalSha256=RUN.digest(original),
                     exportedSha256=RUN.digest(recording), privateOriginalExcluded=True))
    elif args.stage == 'summarize':
        result = {}
        for phase, profile in stages():
            name = phase + '-' + profile
            directory = out / 'attribution' / name
            if json.loads((directory / 'command.json').read_text())['exit'] != 0:
                raise ValueError('Failed diagnostic')
            result[name] = summarize(json.loads((directory / 'allocations.json').read_text())['recording']['events'])
        RUN.save(out / 'attribution-summary.json', result)
        print(json.dumps({name: sum(row['samples'] for row in rows) for name, rows in result.items()}))


if __name__ == '__main__':
    main()
