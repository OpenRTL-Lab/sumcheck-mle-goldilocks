<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Sumcheck/MLE Hardware Accelerator
Chisel hardware for Sumcheck round coefficients, multilinear folding and evaluation.
Repository: https://github.com/OpenRTL-Lab/sumcheck-mle-goldilocks
File: docs/performance.en.md

SPDX-FileCopyrightText: 2026 Goldilocks Sumcheck/MLE contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# Performance

[简体中文](performance.md)

This document defines task latency, sustained task throughput, and per-unit utilization and reports ideal-native-interface results for the typical 65,536-point circuit: ROUND latency 32,847 cycles, completion interval 32,848 cycles, and 99.756% sustained issue utilization in its main coefficient multipliers, with three identical runs for both regenerated and bundled products.

n counts variables, N=2^n is entries per table, and maxLogN bounds n. A term is a weighted product of factor tables; maxDegree bounds factors per term. ROUND outputs round coefficients; ROUND_FOLD outputs coefficients and one folded frame; FOLD outputs one folded frame; EVAL returns each active table's value after n folds. In this document L denotes task latency, not maxLogN or table length. II is arithmetic initiation interval. Frequency conversions are not board or silicon measurements.

## Configuration and methodology

The configuration is Goldilocks, maxLogN=16, maxDegree=2, four tables, two quadratic terms, and weights2 and 3. Each input beat contains adjacent entries from all four tables. Supply and acceptance are ideal, with challenges offered at their earliest legal time and no external bandwidth/latency limits. Actual synchronous-memory, pipeline-drain, challenge-handshake, and task-transition cycles remain included.

[Verification script](../tools/verify.py) creates45 regression tasks plus 32 performance tasks: eight consecutive n=16 tasks per mode. Ideal performance runs three times per product. Regenerated and bundled SV are separately compiled and checked for identical results. The entry point is described in [Reproduction](reproduction.en.md).

C uses polynomial convolution for coefficients and iterative folding for tables. Independent Python uses sampling/interpolation for coefficients and Boolean-weight summation for MLE. Coverage includes mixed linear/quadratic terms, one/two terms, weights0 and p−1, challenges0/1/random, n=0 MLE, zero/boundary data, and invalid frames.

## Latency and throughput definitions

A is the task-handshake cycle, F the first input handshake, O the last result handshake, and C the completion handshake:

$$
L=C-A,\qquad L_{\rm output}=O-A.
$$

Published task latency L includes input, computation, output, and completion, rather than only arithmetic-pipeline depth. For each eight-task group, seven completion intervals are measured after the first completion:

$$
W=C_7-C_0,\qquad R_{\rm cycle}=\frac{7}{W},\qquad R_{\rm second}=f\frac{7}{W}.
$$

Modular multiplication/addition/subtraction use20/5-cycle pipelines with II=1. Complete-task capacity is one, without overlapping tasks. Measured completion interval is therefore one cycle longer than task latency, but the two metrics are independently measured. Actual challenge and round-drain costs are included.

## Results by mode

| Mode | A | F | O | C | L (cycles) | Completion interval (cycles) |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| ROUND | 4,363 | 4,364 | 37,209 | 37,210 | 32,847 | 32,848 |
| ROUND_FOLD | 267,147 | 267,148 | 332,795 | 332,797 | 65,650 | 65,651 |
| FOLD | 792,355 | 792,356 | 857,926 | 857,928 | 65,573 | 65,574 |
| EVAL | 1,316,947 | 1,316,948 | 1,415,780 | 1,415,781 | 98,834 | 98,835 |

Timestamps identify each group's first performance task. All seven measured intervals equal the corresponding value. One EVAL task evaluates all four 65,536-entry tables through 16 folding rounds.

| Mode | Seven-completion window (cycles) | Tasks/cycle | Latency at500 MHz (µs) | Tasks/s at500 MHz |
| --- | ---: | ---: | ---: | ---: |
| ROUND | 229,936 | 0.000030443254 | 65.694 | 15,221.627 |
| ROUND_FOLD | 459,557 | 0.000015232060 | 131.300 | 7,616.030 |
| FOLD | 459,018 | 0.000015249947 | 131.146 | 7,624.973 |
| EVAL | 691,845 | 0.000010117873 | 197.668 | 5,058.937 |

Cycle counts are measured in RTL simulation; time and tasks/s are calculated using f=500 MHz. DC conditions for that frequency are in [Synthesis summary](synthesis-summary.en.md).

## Arithmetic-unit utilization

busy means a logical site issues one valid operation in that cycle; idle equals window length minus busy. A20/5-cycle operation in flight is not counted as20/5 busy issues. occupied separately measures cycles with at least one valid operation in the pipeline. Verification-layer logs provide these measurements without product performance counters.

The typical circuit contains 14 modular multipliers,33 modular adders, and 4 modular subtractors. Units with identical issue counts are grouped below; values are busy cycles **per instance**, not the sum over the group. t=0,1; k=0,1,2; j uses the indicated range. Mode windows are 229,936 /459,557 /459,018 /691,845 cycles, so every idle count is obtained exactly by subtracting busy.

| Instance group | Count | ROUND busy | ROUND_FOLD busy | FOLD busy | EVAL busy |
| --- | ---: | ---: | ---: | ---: | ---: |
| term{t}.first0/first1 multipliers; table{0..3}.delta-sub subtractors | 8 | 229,376 | 458,752 | 229,376 | 458,745 |
| term{t}.first2/first3 and lambda{0..2} multipliers; quad-add; coefficient{k}.term-add/acc-add | 18 | 229,376 | 229,376 | 0 | 0 |
| term{t}.fold-add{0..1} adders | 4 | 0 | 229,376 | 229,376 | 458,745 |
| coefficient{k}.tree1.{0..3}/tree2.{0..1}/tree3 adders | 21 | 7 | 7 | 0 | 0 |

For each instance in the first group, busy/idle is229,376/560 for ROUND,458,752/805 for ROUND_FOLD,229,376/229,642 for FOLD, and 458,745/233,100 for EVAL, giving 99.756%,99.825%,49.971%, and 66.307% respectively.

Main multipliers issue continuously during long-table coefficient/folding phases. EVAL's first folding round has99.899% issue utilization, with issue counts decreasing as tables shrink. Whole-task figures include loading and all rounds. Multiplier reuse and phase-specific merge scheduling produce mode-dependent utilization; results.json records every instance and round.

## Verification results

| Test | Tasks per product | Checked result words | Mismatches | Result |
| --- | ---: | ---: | ---: | --- |
| Ideal interface | 77 | 2,098,497 | 0 | Pass; 3 identical performance runs |
| Protocol pause/backpressure | 45 | 1,265 | 0 | Pass |
| In-flight reset recovery | 45 | 1,265 | 0 | Pass; trigger and resubmission confirmed |
| Deliberately incorrect reference | 45 | 1,265 | 1 | Expected failure, proving checker effectiveness |

Functional regressions also pass for n4/d2 and n8/d3, together with typical-configuration verification of regenerated and bundled SV from an independent checkout location. Degree-three testing covers an alternative generated circuit; bundled SV and the performance tables remain the typical n16/d2 configuration.
