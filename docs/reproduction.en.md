<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Sumcheck/MLE Hardware Accelerator
Chisel hardware for Sumcheck round coefficients, multilinear folding and evaluation.
Repository: https://github.com/OpenRTL-Lab/sumcheck-mle-goldilocks
File: docs/reproduction.en.md

SPDX-FileCopyrightText: 2026 Goldilocks Sumcheck/MLE contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# Reproduction

[简体中文](reproduction.md)

This document explains independent circuit generation, C/Python reference checking, verification of regenerated and bundled SV, and measurement of latency, sustained throughput, and arithmetic-unit utilization across all four modes; the default entry point runs the complete typical-configuration test.

## Obtain the release snapshot

[Release v1.1.0](https://github.com/OpenRTL-Lab/sumcheck-mle-goldilocks/releases/tag/v1.1.0)

```bash
git clone --branch v1.1.0 --depth 1 https://github.com/OpenRTL-Lab/sumcheck-mle-goldilocks.git
cd sumcheck-mle-goldilocks
```

The version tag identifies the current source tree, including source notices and documentation. Release downloads provide hardware sources and bundled RTL for this version. Archive annotations and documentation can differ from the online tree. For RTL integration, use the product file list rather than verification environments; use the verification and synthesis documents for supported configurations and measurement conditions.

## 1. Prepare the environment

Install the public tools and versions in [Toolchain](toolchain.en.md) and ensure build tools, compilers, and the simulator are callable. The following commands only inspect versions; run them from the repository root:

```bash
java -version
mill --version
python3 --version
verilator --version
cc --version
g++ --version
```

All later commands also run from the repository root. build.mill pins source dependencies, and the first build resolves public artifacts. Optional launcher and cache settings are described in [Toolchain](toolchain.en.md).

Choose a fresh relative output directory under `build/` for each command. `--max-logn h` sets maximum table length 2^h, not table length h; `--degree D` sets maximum factors per weighted term. Thus n16/d2 denotes capacity for 65,536-entry tables and degree two per term. `--jobs` controls compilation parallelism; `--no-bench` skips long-table performance tasks, not the functional tests.

## 2. Complete typical-configuration verification

The following command builds the C reference, generates45 regression tasks plus 32 long-table performance tasks, cross-checks with independent Python, generates SV, lints the product, and builds simulations for both regenerated and bundled products:

```bash
python3 tools/verify.py --out build/verify --jobs 8
```

Both products run ideal-interface, protocol pause/backpressure, in-flight reset recovery, and deliberately incorrect-reference tests. The default 77 tasks include eight n= 16 benchmark tasks per mode. Protocol, reset, and negative-reference tests each run45 regression tasks. Ideal tests run three times per product, requiring identical performance measurements.

Performance uses ideal external interfaces rather than bandwidth, latency, or shared-port sweeps. Challenges are offered at their earliest legal opportunity, input is continuously supplied, and outputs are continuously accepted. Pause/backpressure testing validates functionality, not release performance.

A `status:0` result means the entire flow passed. The negative-reference test intentionally flips one bit of the first expected result; exactly one mismatch and a failing return code are required, confirming checker effectiveness. Reset logs must contain RESET_RECOVERY and demonstrate successful task resubmission.

## 3. Inspect results

`build/verify/results.json` contains configuration, test outcomes, per-task A/F/O/C timestamps, latency, completion intervals, per-unit valid issues/busy/idle cycles, and per-round measurements. Definitions and published values are in [Performance](performance.en.md). Stage logs share the output directory. `rtl/product.f` lists only product modules; `rtl/simulation.f` includes the verification environment.

Existing output directories are never overwritten. The following command performs another independent run in a new directory:

```bash
python3 tools/verify.py --out build/verify-again --jobs 8
```

## 4. Generate without simulation

The following command generates typical SV, reference vectors, and product/simulation lists without building or running Verilator:

```bash
python3 tools/emit_sv.py --out build/emit --max-logn 16 --degree 2
```

max-logn selects maximum table capacity; degree selects maximum degree per term. Public verification entry points support max-logn4 through 16 and degree2 or3. The underlying Chisel Config supports maxLogN1 through 16. Bundled SV and the synthesis summary describe only the typical n16/d2 configuration.

The following command verifies a generated n8/d3 configuration, including all four functional-test categories, without long-table benchmarks or comparison against the typical bundled SV:

```bash
python3 tools/verify.py --out build/verify-cubic --max-logn 8 --degree 3 --no-bench --jobs 8
```

## 5. Lint the bundled product directly

The following command only lints the bundled product; file-list paths are relative to the repository root:

```bash
verilator --lint-only --assert --top-module GoldilocksSumcheckMle -f rtl/v1.1.0/gl64-n16-t2-d2/files.f
```

Simulation may generate runtime paths for the current machine inside build. These are local run outputs, not distributed files. Public sources, bundled SV, and archives contain no original development-machine paths, credentials, or internal logs.
