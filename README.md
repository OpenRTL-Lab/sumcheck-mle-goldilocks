<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Sumcheck/MLE Hardware Accelerator
Chisel hardware for Sumcheck round coefficients, multilinear folding and evaluation.
Repository: https://github.com/OpenRTL-Lab/sumcheck-mle-goldilocks
File: README.md

SPDX-FileCopyrightText: 2026 Goldilocks Sumcheck/MLE contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# Goldilocks Sumcheck and Multilinear Extension Hardware Accelerator

[简体中文](README.zh-CN.md)

This repository provides a standalone Goldilocks Sumcheck/MLE hardware accelerator developed in Chisel. Through native interfaces, it can be integrated into ASIC or FPGA designs for single-round Sumcheck coefficient generation, multilinear table folding, and multilinear-extension evaluation used in zero-knowledge provers, commitment evaluation, and computation-verification pipelines.

It is licensed under the [MIT License](LICENSE), permitting free commercial use, modification, and redistribution, provided the copyright and license notices are retained; see [Third-party notices](docs/third-party-notices.en.md) for reference and tool information.

[Release v1.1.0](https://github.com/OpenRTL-Lab/sumcheck-mle-goldilocks/releases/tag/v1.1.0) · [Native interface](docs/interface.en.md) · [Reproduction](docs/reproduction.en.md) · [Performance](docs/performance.en.md) · [Synthesis summary](docs/synthesis-summary.en.md)

## Computation and task units

The input tables have N = 2^n entries for n Boolean variables. A term means a weighted product of table factors, not one table entry or one variable. There are up to two terms; degree d_t is the number of factors in term t. The bundled degree-two circuit therefore has up to four active factor tables, each with N entries. Coefficients and table values are canonical unsigned 64-bit Goldilocks elements, 0 <= x < p for p = 2^64 - 2^32 + 1.

| Mode | One task returns |
| --- | --- |
| ROUND | All coefficients of one round polynomial, in increasing degree order; not just its values at 0 and 1 |
| ROUND_FOLD | Those coefficients first, then every active table folded once after accepting one challenge |
| FOLD | Every active table folded once using one challenge, without round coefficients |
| EVAL | Each active table's MLE at n caller-supplied challenges; not their weighted product or sum |

Table-index bits select variables least-significant first. A folding input pairs entries 2i and 2i+1, and returns A[2i] + r * (A[2i+1] - A[2i]) for challenge r. One task covers one selected mode; EVAL folds all n variables, whereas ROUND_FOLD covers only one Sumcheck round. A result frame is the complete output group for that stage: ROUND_FOLD's coefficient frame must handshake before the challenge is accepted.

## At a glance

| Integration question | Released hardware |
| --- | --- |
| Computation | Sumcheck round coefficients, challenge-driven table folding and multilinear extension (MLE) evaluation |
| Bundled configuration | Up to 16 variables / 65,536 table points; 2 terms; degree 2 per term |
| Field and product top | Goldilocks p = 2^64 - 2^32 + 1; `GoldilocksSumcheckMle` |
| Modes | ROUND, ROUND_FOLD, FOLD and EVAL; complete coefficients or table/evaluation frames |
| Native boundary | Task, indexed table input, ordered challenge, result and completion ready/valid channels |

## Quick start

Download the source archive from [Release v1.1.0](https://github.com/OpenRTL-Lab/sumcheck-mle-goldilocks/releases/tag/v1.1.0), or clone the current release snapshot:

```bash
git clone --branch v1.1.0 --depth 1 https://github.com/OpenRTL-Lab/sumcheck-mle-goldilocks.git
cd sumcheck-mle-goldilocks
```

Install the exact prerequisites in [Toolchain](docs/toolchain.en.md), then run from the repository root with a fresh output directory:

```bash
python3 tools/emit_sv.py --out build/emit --max-logn 16 --degree 2
```

This emission command is not a complete RTL regression. The documented verification entry is:

```bash
python3 tools/verify.py --out build/verify --jobs 8
```

Follow [Reproduction](docs/reproduction.en.md) for expected results, negative-control interpretation and the distinction between short functional and full performance runs.

## Verification and integration boundary

The typical workflow generates 45 regression tasks and 32 long-table benchmark tasks, checks independent C/Python references and both regenerated/bundled products, and separates ideal, protocol/backpressure, reset-recovery and deliberately incorrect-reference modes. Degree-three generated configurations have a separate documented command and do not inherit the bundled degree-two synthesis results.

This is not a complete interactive Sumcheck prover or transcript. The caller supplies claims, weights, correctly ordered tables and challenges. In ROUND_FOLD, the coefficient frame must be accepted before the challenge handshake. Complete tasks are serialized; a delivered coefficient frame alone is not a successful task. The fixed Goldilocks field is not interchangeable with BN254 or a different proof system's field.

### Reading performance correctly

For ideal-interface ROUND tasks with 65,536-point tables, latency is 32,847 cycles and the steady completion interval is 32,848 cycles. The reported 99.756% main coefficient-multiplier issue utilization counts 229,376 valid issue cycles per instance / 229,936 measured window cycles. It is neither a sum over all multiplier instances nor a count of occupied pipeline stages. ROUND_FOLD, FOLD and EVAL have different task outputs and timing; their rates are not interchangeable.

These are documented RTL cycle measurements under ideal native interfaces. MHz-based time/rate conversions are not board or silicon measurements. The reported Design Compiler results apply only to the documented configuration, library corner and constraints; they do not establish place-and-route signoff or system throughput. See [Performance](docs/performance.en.md) and [Synthesis summary](docs/synthesis-summary.en.md) for conditions.

## Repository contents

- [Chisel design](operators/sumcheck-mle/src/main/scala/zk/sumcheck/): native interfaces, coefficient/folding circuits, and verification environment.
- [Common arithmetic](common/arithmetic/src/main/scala/): local prefix carry functions and the Goldilocks field constant.
- [Generated SV](rtl/v1.1.0/gl64-n16-t2-d2/): the typical circuit supports up to 65,536 points, two terms, and degree two per term; its top module is `GoldilocksSumcheckMle`.
- [Generation script](tools/emit_sv.py) and [Verification script](tools/verify.py): portable generation and end-to-end verification entry points.
- [C reference model](operators/sumcheck-mle/cmodel/reference.c), [Independent big-integer reference](operators/sumcheck-mle/tools/check_reference.py), and [Simulation driver](operators/sumcheck-mle/sim/driver.cpp): field-by-field checking and handshake-cycle measurements.
- [Architecture](docs/architecture.en.md), [Native interface](docs/interface.en.md), and [Toolchain](docs/toolchain.en.md): implementation, integration, and reproduction details.

## Applications

The module can accelerate quadratic Sumcheck round generation, challenge-driven table folding, multiple-table MLE evaluation, and related proof computations over Goldilocks. Examples include converting inner-product claims into round polynomials and evaluating multilinear tables at specified challenge points; see [Mathematical model](docs/mathematical-model.en.md) and [Native interface](docs/interface.en.md).

Sumcheck and MLE are foundational components of Spartan-style proving; see the [Spartan overview](https://microsoft.github.io/vega-prover/building-blocks/spartan.html). These illustrate algorithmic integration points rather than limiting eligible systems. The caller selects the Goldilocks field, table layout, claims, challenges, and protocol encoding. The bundled circuit supports degree two per term; the source also supports generating degree-three configurations.

## Performance

Under ideal interfaces, a 65,536-point single-round coefficient task takes 32,847 cycles, with a continuous-task completion interval of 32,848 cycles: 65.694 µs and approximately 15,222 tasks/s at 500 MHz. Definitions, methodology, and all four modes are provided in [Performance](docs/performance.en.md).

## Mathematical model

The circuit computes complete round coefficients for weighted products of linear factors, challenge-folded tables, or individual-table MLE values over modulus `2^64−2^32+1`. Exact formulas, index ordering, and known answers are specified in [Mathematical model](docs/mathematical-model.en.md).

## Quick reproduction

Public tools can regenerate SV, verify both regenerated and bundled circuits against independent C/Python references, and reproduce functional, protocol, reset, and performance tests; see [Reproduction](docs/reproduction.en.md).

## Synthesis results

The typical circuit passes DC setup checks using TSMC 28 nm HPC+, SSG 0.81 V −40 °C, 500 MHz, and 10% setup clock uncertainty; the maximum depth across 121,483 endpoint-worst paths is 39 logic levels. Conditions, area, and counting methodology are presented in [Synthesis summary](docs/synthesis-summary.en.md).

## Publication scope

The repository provides an independent accelerator and a reproducible RTL/simulation flow, rather than a SoC, board project, or complete proof protocol. We apologize that third-party licensing and confidentiality restrictions prevent distributing process libraries, memory-macro implementations, original DC scripts, reports, or synthesis snapshots. The available conditions and results are restated in [Synthesis summary](docs/synthesis-summary.en.md).

## OpenRTL and custom integration

Browse [OpenRTL](https://github.com/OpenRTL-Lab), the [ZK accelerator catalogue](https://github.com/OpenRTL-Lab/zk-accelerators) and [OpenRTL website](https://openrtl.top/) for related modules and the cooperation/contact entry. Custom datapaths, interfaces and target-platform adaptation are optional services; MIT commercial use does not require buying a service. Do not submit confidential specifications in public issues.

## Release sources and integration

The version tag identifies the current source tree, including source notices and documentation. Release downloads provide hardware sources and bundled RTL for this version. Archive annotations and documentation can differ from the online tree. For RTL integration, use the product file list rather than verification environments; use the verification and synthesis documents for supported configurations and measurement conditions.
