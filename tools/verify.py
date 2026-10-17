#!/usr/bin/env python3
# OpenRTL-Header-Begin
# OpenRTL — Open-source hardware accelerators.
# Organization: https://github.com/OpenRTL-Lab
# Website: https://openrtl.top/
#
# Project: Goldilocks Sumcheck/MLE Hardware Accelerator
# Chisel hardware for Sumcheck round coefficients, multilinear folding and evaluation.
# Repository: https://github.com/OpenRTL-Lab/sumcheck-mle-goldilocks
# File: tools/verify.py
#
# SPDX-FileCopyrightText: 2026 Goldilocks Sumcheck/MLE contributors
# Copyright applies to original project contributions only.
# SPDX-License-Identifier: MIT
# See LICENSE and applicable third-party notices.
# OpenRTL-Header-End

"""Verify C/Python references, regenerated/bundled RTL, protocol, reset and performance."""
import argparse
import json
import os
from pathlib import Path
import re
import sys
from emit_sv import generate, run, simulation_list, BUNDLED, ROOT, OP, TOP, code
from measurement import metrics


def result(path, tasks, negative=False):
    text = path.read_text()
    match = re.search(r'RESULT (.+)', text)
    assert match, 'Missing simulation result'
    values = {k: int(v) for k, v in re.findall(r'(\w+)=(\d+)', match[1])}
    assert values['done'] == 1 and values['tasks'] == tasks
    assert values['mismatches'] == int(negative)
    assert values['pass'] == int(not negative)
    return values


def verify(args):
    os.environ['CCACHE_DISABLE'] = '1'
    out, rtl, rows, count, groups, product = generate(
        args.out, args.max_logn, args.degree, not args.no_bench)
    run(['verilator', '--lint-only', '--assert', '--top-module', TOP,
         '-f', rtl / 'product.f'], out, 'product-lint')
    bundled = args.max_logn == 16 and args.degree == 2
    if bundled:
        existing = {p.name: code(p.read_text()).split() for p in BUNDLED.glob('*.sv')}
        regenerated = {p.name: code(p.read_text()).split() for p in product}
        assert regenerated == existing, 'Bundled product differs from regenerated RTL'
        simulation_list(rtl, rtl / 'bundled-simulation.f', True)
    report = {'configuration': json.loads((out / 'configuration.json').read_text()),
              'ideal_interfaces': True, 'tests': {}, 'bundled_rtl_compared': bundled}
    for label in ['generated'] + (['bundled'] if bundled else []):
        obj = out / ('obj-' + label)
        filelist = rtl / ('simulation.f' if label == 'generated' else 'bundled-simulation.f')
        run(['verilator', '--cc', '--exe', '--build', '-j', args.jobs, '--assert',
             '-DENABLE_INITIAL_MEM_', '--top-module', 'SumcheckMleSimTop', '-f', filelist,
             '--Mdir', obj.resolve(), '-CFLAGS', '-O2', (ROOT / OP / 'sim/driver.cpp')],
            out, label + '-compile')
        binary = obj / 'VSumcheckMleSimTop'
        tests = {}
        for mode, name in enumerate(['ideal', 'protocol', 'reset', 'negative']):
            log_name = label + '-' + name
            run([binary, mode], out, log_name, 1 if mode == 3 else 0)
            log = out / (log_name + '.log')
            tests[name] = result(log, len(rows) if mode == 0 else count, mode == 3)
            if mode == 2:
                assert 'RESET_RECOVERY' in log.read_text()
        performance = metrics(out / (label + '-ideal.log'), rows, groups, args.degree)
        repeats = 1
        if groups:
            for repeat in (2, 3):
                log_name = label + '-ideal-repeat' + str(repeat)
                run([binary, 0], out, log_name)
                result(out / (log_name + '.log'), len(rows))
                assert metrics(out / (log_name + '.log'), rows, groups, args.degree) == performance
                repeats += 1
        if label == 'bundled':
            assert tests == report['tests']['generated']
            assert performance == report['performance']
        else:
            report['performance'] = performance
        report['tests'][label] = tests
        report['performance_repeats_per_product'] = repeats
    report['status'] = 0
    (out / 'results.json').write_text(json.dumps(report, indent=2)+'\n')
    print(json.dumps({'status': 0, 'tasks': len(rows), 'regression_tasks': count,
                      'bundled_verified': bundled, 'performance_repeats': repeats}))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', default='build/verify')
    parser.add_argument('--max-logn', type=int, default=16)
    parser.add_argument('--degree', type=int, default=2)
    parser.add_argument('--jobs', type=int, default=8)
    parser.add_argument('--no-bench', action='store_true')
    args = parser.parse_args()
    if args.jobs < 1:
        parser.error('jobs must be positive')
    verify(args)
