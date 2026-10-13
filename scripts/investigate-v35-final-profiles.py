#!/usr/bin/env python3
"""Run the maintainer-approved 36-fork attribution follow-up without rescoring."""
import argparse
import importlib.util
import json
import os
from pathlib import Path
import shutil

ROOT = Path(__file__).resolve().parents[1]
PLAN = ROOT / 'roadmaps/v35/FINAL-ACCEPTANCE.md'


def load(name):
    spec = importlib.util.spec_from_file_location(name, ROOT / 'scripts' / (name + '.py'))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


allocation = load('investigate-v35-allocation')
review = load('review-v35-allocation')


def schedule():
    tracing = ['-XX:+UnlockDiagnosticVMOptions', '-XX:+LogCompilation',
               '-XX:LogFile=compilation-%p.xml', '-Xlog:class+load=info:file=classes-%p.log']
    return [(profile + '-' + kind + '-' + side, side, profile, forks, tracing + extra)
            for profile in ('AUTO_NO_REGISTRY', 'AUTO_REGISTRY', 'HOOK')
            for kind, forks, extra in [('trace', 4, []),
                ('dontinline', 2, ['-XX:CompileCommand=dontinline,' + allocation.TARGET])]
            for side in ('baseline', 'current')]


def summarize(directory, side, profile, forks, extra, pair):
    rows = json.loads((directory / 'results.json').read_text())
    if len(rows) != 1:
        raise ValueError('Unexpected result inventory')
    row = rows[0]
    if (not row['benchmark'].endswith('.V34DefaultPathBenchmark.defaultV34NoNetworkWarmPublisher')
            or row['params'] != {'profile': profile, 'scenario': 'GET'}
            or row['mode'] != 'avgt' or row['threads'] != 1 or row['forks'] != forks
            or row['warmupIterations'] != 5 or row['measurementIterations'] != 5
            or row['warmupTime'] != '1 s' or row['measurementTime'] != '1 s'
            or row['jvmArgs'] != allocation.FLAGS + extra
            or row['jdkVersion'] != '21.0.8' or row['jmhVersion'] != '1.37'):
        raise ValueError('Frozen diagnostic mismatch')
    result = review.metrics(row, forks)
    traces = review.diagnostic_traces(directory, row, pair / ('build-' + side) / 'benchmarks.jar')
    return {'metrics': result, 'traces': traces, 'forkAssociation': 'unverified', 'measurementInterval': None}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--pair', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if os.geteuid() == 0:
        parser.error('Run as the normal user, not sudo')
    os.umask(0o077)
    pair, out = args.pair.resolve(), args.output.resolve()
    out.mkdir(parents=True, exist_ok=False)
    allocation.save(out / 'input-audit.json', allocation.audit_pair(pair))
    allocation.save(out / 'schedule.json', schedule())
    preserved = [p for stage in ('primary', 'confirm', 'allocation') for p in (pair / stage).rglob('*')
                 if p.is_file()]
    hashes = {str(p.relative_to(pair)): allocation.digest(p) for p in preserved}
    allocation.save(out / 'prior-evidence-hashes.json', hashes)
    allocation.save(out / 'source.json', {'commit': allocation.git('rev-parse', 'HEAD'),
        'planSha256': allocation.digest(PLAN), 'runnerSha256': allocation.digest(Path(__file__)),
        'javaSha256': allocation.digest(allocation.JAVA),
        'approval': 'Maintainer explicitly approved 36 diagnostic forks on 2026-10-11'})
    shutil.copy2(PLAN, out / 'frozen-plan.md')
    shutil.copy2(__file__, out / 'runner.py')
    result = {}
    for name, side, profile, forks, extra in schedule():
        directory = out / name
        allocation.run(allocation.command(pair, side, profile, forks, extra, False, directory),
                       directory, timeout=300)
        result[name] = summarize(directory, side, profile, forks, extra, pair)
        allocation.save(out / 'review.json', result)
    if any(allocation.digest(pair / path) != value for path, value in hashes.items()):
        raise ValueError('Earlier evidence changed')
    allocation.save(out / 'summary.json', {'status': 'passed', 'forks': 36,
        'earlierEvidenceUnchanged': True, 'attribution': 'Requires review; completion is not acceptance'})


if __name__ == '__main__':
    main()
