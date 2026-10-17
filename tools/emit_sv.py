#!/usr/bin/env python3
# OpenRTL-Header-Begin
# OpenRTL — Open-source hardware accelerators.
# Organization: https://github.com/OpenRTL-Lab
# Website: https://openrtl.top/
#
# Project: Goldilocks Sumcheck/MLE Hardware Accelerator
# Chisel hardware for Sumcheck round coefficients, multilinear folding and evaluation.
# Repository: https://github.com/OpenRTL-Lab/sumcheck-mle-goldilocks
# File: tools/emit_sv.py
#
# SPDX-FileCopyrightText: 2026 Goldilocks Sumcheck/MLE contributors
# Copyright applies to original project contributions only.
# SPDX-License-Identifier: MIT
# See LICENSE and applicable third-party notices.
# OpenRTL-Header-End

"""Generate independent RTL, reference vectors and product/simulation file lists."""
from source_headers import stamp_generated
import argparse
import json
import os
from pathlib import Path
import re
import shlex
import subprocess
import sys
from measurement import make_plan

ROOT = Path(__file__).resolve().parents[1]
OP = Path('operators/sumcheck-mle')
TOP = 'GoldilocksSumcheckMle'
BUNDLED = Path('rtl/v1.1.0/gl64-n16-t2-d2')


def run(command, output, label, expected=0):
    with (output / (label + '.log')).open('x') as log:
        result = subprocess.run([str(x) for x in command], cwd=ROOT,
                                stdout=log, stderr=subprocess.STDOUT)
    if result.returncode != expected:
        raise RuntimeError(f'{label}: exit={result.returncode}, expected={expected}; see its log')


def code(text):
    return re.sub(r'/\*.*?\*/|//[^\n]*', '', text, flags=re.S)


def product_closure(rtl):
    stamp_generated(rtl)
    definitions, texts, dependencies = {}, {}, {}
    for path in sorted(rtl.glob('*.sv')):
        text = re.sub(r'[ \t]*//[^\n]*\.scala:\d+:\d+[^\n]*', '', path.read_text())
        path.write_text(text)
        body = code(text)
        names = re.findall(r'\bmodule\s+(\w+)', body)
        assert len(names) == 1, path.name
        name = names[0]
        assert name not in definitions
        definitions[name] = path
        texts[name] = body
        dependencies[name] = set(re.findall(
            r'^[ \t]*(\w+)\b[ \t]+(?:#\s*\([^;]*?\)\s*)?\w+\s*\(', body,
            re.M | re.S)) - {'module', 'else'}
    seen, pending = set(), [TOP]
    while pending:
        name = pending.pop()
        if name in seen:
            continue
        assert name in definitions, f'Unresolved module: {name}'
        seen.add(name)
        pending.extend(dependencies[name])
    for name in seen:
        assert not re.search(r'\$readmem|\$display|\$write|\$finish|%|\b(?:DSP48\w*|RAMB\w*|URAM\w*|BUFG\w*|MMCME\w*)\b', texts[name])
    return [definitions[name] for name in sorted(seen)]


def memory_structure(product, n, degree):
    memory = next(p for p in product if p.stem == f'mem_{2**(n-1)}x64')
    text = code(memory.read_text())
    assert set(re.findall(r'\b(R\d+)_addr\b', text)) == {'R0'}
    assert set(re.findall(r'\b(W\d+)_addr\b', text)) == {'W0'}
    assert len(re.findall(r'Memory\[W0_addr\]\s*<=\s*W0_data', text)) == 1
    top = next(p for p in product if p.stem == TOP).read_text()
    banks = len(re.findall(r'\b'+re.escape(memory.stem)+r'\s+\w+\s*\(', top))
    assert banks == 4 * degree
    return {'ports': '1R1W', 'banks': banks, 'depth': 2**(n-1), 'width': 64,
            'logical_bytes': banks * 2**(n-1) * 8}


def simulation_list(rtl, output, bundled=False):
    sources = sorted(rtl.rglob('*.sv'))
    if bundled:
        generated = set(product_closure(rtl))
        sources = [p for p in sources if p not in generated] + sorted(BUNDLED.glob('*.sv'))
    includes = sorted({p.parent for p in sources})
    output.write_text(''.join('+incdir+'+str(p)+'\n' for p in includes)
                      + ''.join(str(p)+'\n' for p in sources))


def generate(output, max_logn=16, degree=2, bench=True):
    os.chdir(ROOT)
    output = Path(output)
    if output.is_absolute() or '..' in output.parts or not output.parts or output.parts[0] != 'build':
        raise ValueError('Use a new relative directory under build')
    if output.exists():
        raise ValueError('Output already exists; choose a new directory')
    if not 4 <= max_logn <= 16 or degree not in (2, 3):
        raise ValueError('The verification runner supports max-logn 4..16 and degree 2 or 3')
    output.mkdir(parents=True)
    vectors = output / 'vectors'
    vectors.mkdir()
    rows, count, groups = make_plan(max_logn, degree, bench)
    (vectors / 'plan.txt').write_text(''.join(' '.join(map(str, row))+'\n' for row in rows))
    (vectors / 'regression-count.txt').write_text(str(count)+'\n')
    run([os.environ.get('CC', 'cc'), '-std=c11', '-O2', '-Wall', '-Wextra', '-Werror',
         OP / 'cmodel/reference.c', '-o', output / 'reference'], output, 'c-compile')
    run([output / 'reference', vectors, vectors / 'plan.txt', degree], output, 'c-reference')
    run([sys.executable, OP / 'tools/check_reference.py', vectors, degree], output, 'python-reference')
    rtl = output / 'rtl'
    run(shlex.split(os.environ.get('MILL', 'mill')) + ['-i', 'sumcheckMle.tool.runMain',
         'zk.sumcheck.Emit', rtl, max_logn, degree, vectors], output, 'emit')
    product = product_closure(rtl)
    (rtl / 'product.f').write_text(''.join(str(p)+'\n' for p in product))
    simulation_list(rtl, rtl / 'simulation.f')
    configuration = {'version': '1.1.0', 'max_log_n': max_logn, 'max_degree': degree,
                     'tasks': len(rows), 'regression_tasks': count,
                     'memory': memory_structure(product, max_logn, degree)}
    (output / 'configuration.json').write_text(json.dumps(configuration, indent=2)+'\n')
    return output, rtl, rows, count, groups, product


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', default='build/emit')
    parser.add_argument('--max-logn', type=int, default=16)
    parser.add_argument('--degree', type=int, default=2)
    parser.add_argument('--no-bench', action='store_true')
    args = parser.parse_args()
    generate(args.out, args.max_logn, args.degree, not args.no_bench)
    print('Generation passed: product RTL is listed in rtl/product.f under the output directory')
