<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Sumcheck/MLE Hardware Accelerator
Chisel hardware for Sumcheck round coefficients, multilinear folding and evaluation.
Repository: https://github.com/OpenRTL-Lab/sumcheck-mle-goldilocks
File: docs/interface.en.md

SPDX-FileCopyrightText: 2026 Goldilocks Sumcheck/MLE contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# Native Interface

[简体中文](interface.md)

This document specifies the accelerator's five ready/valid channels, payload encodings, frame lengths, challenge ordering, error handling, and reset behavior for direct control-plane and data-plane integration.

A task has n variables and N=2^n entries per factor table. T=terms (1..2) counts weighted products of factors; degree[t]=d_t (1..D) counts factors in term t, not variables. D=maxDegree is 2 in the bundled circuit (3 is also generatable). Slot s=t*D+j stores factor j of term t, for 0 <= t < T and 0 <= j < d_t; keep is the bit mask of active slots. Let d=max_t d_t, the round-polynomial degree. Modes 0..2 require 1 <= n <= maxLogN; EVAL also permits n=0. The bundled maxLogN is 16. All active elements, lambda weights and r challenges are canonical unsigned values 0 <= x < p, p = 2^64 - 2^32 + 1.

## Top module and channels

The top module is `GoldilocksSumcheckMle`, with one `clock` and synchronous active-high `reset`. A transfer occurs only when valid && ready. The sender keeps valid and every payload field stable while blocked. One complete task can be in flight, in order; the caller's 32-bit tag is returned unchanged.

| Channel | Direction | Typical payload widths |
| --- | --- | --- |
| task | Control input | tag:32; mode:2; n:5; terms:2; degree[2]:2 each; lambda[2]:64 each |
| input | Data input | tag:32; index:17; keep:4; last:1; a[4], b[4]:64 each |
| challenge | Control input | tag:32; round:5; r:64 |
| result | Data output | tag:32; kind:2; index:17; keep:4; last:1; words[4]:64 each |
| completion | Control output | tag:32; status:3 |

Chisel flattens fields into SV ports such as `io_task_bits_tag` and `io_input_bits_a_0`; see [Top-level SV](../rtl/v1.1.0/gl64-n16-t2-d2/GoldilocksSumcheckMle.sv). A generated degree-three configuration has six slots and six-bit keep, with six result words; other field widths remain unchanged.

## Data layout and ordering

Slot s=tD+j, with keep bit0 corresponding to slot0. terms and degree determine active slots. Inactive a/b inputs and inactive output words are zero. Active field words, weights, and challenges are canonical integers below p; see [Mathematical model](mathematical-model.en.md).

For n>0, each input beat contains adjacent entries of all active tables. index runs from0 through 2^(n−1)−1, with last=1 on the actual final beat. EVAL n=0 uses one beat with the sole a value, zero b, index=0,last=1, and no challenge.

| mode | Input beats | Challenge ordering | Result |
| --- | ---: | --- | --- |
| 0 ROUND | 2^(n−1) | None | One kind0 beat, index0, coefficients in increasing degree order; lowest d+1 keep bits, last1 |
| 1 ROUND_FOLD | 2^(n−1) | round0 after the kind0 handshake | One kind0 frame, then a kind1 frame of 2^(n−1) beats |
| 2 FOLD | 2^(n−1) | round0 after loading | 2^(n−1) kind1 beats |
| 3 EVAL | n>0: 2^(n−1); n=0: 1 | round0 through round(n−1), each accepted when ready | One kind2 beat containing all active table evaluations |

kind1 index increments from0, keep identifies active slots, and last=1 only on the final beat. kind2 uses index=0, active-slot keep, and last=1. Each mode1 frame has its own last marker. Successful completion follows all result handshakes. Challenges may be offered early, but ROUND_FOLD does not assert challenge.ready before the coefficient-frame handshake.

## Statuses, errors, and reset

| status | Meaning |
| --- | --- |
| 0 | OK; all results accepted |
| 1 | BAD_PARAM; invalid n, terms, degrees, weights, etc.; no input frame accepted |
| 2 | BAD_FRAME; invalid tag, index, keep, last, field encoding, zero padding, or challenge |

An early last does not shorten the specified input length. The circuit drains the expected beats and in-flight computation to protect subsequent tasks. An invalid challenge terminates the task; a previously delivered coefficient frame does not imply successful completion. Reset clears control, FIFO, and pipeline-valid state, not table SRAM. It discards an in-flight task without returning its stale completion.

Table storage is finite, synchronous 1R1W. A 64-beat output FIFO and in-flight credit reservation propagate backpressure through ready without losing results. Complete tasks are serialized, while internal arithmetic pipelines have II=1; see [Architecture](architecture.en.md).
