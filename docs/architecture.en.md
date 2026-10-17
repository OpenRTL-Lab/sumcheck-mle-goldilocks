<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Sumcheck/MLE Hardware Accelerator
Chisel hardware for Sumcheck round coefficients, multilinear folding and evaluation.
Repository: https://github.com/OpenRTL-Lab/sumcheck-mle-goldilocks
File: docs/architecture.en.md

SPDX-FileCopyrightText: 2026 Goldilocks Sumcheck/MLE contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# Architecture

[简体中文](architecture.md)

This document describes the coefficient, storage, challenge-folding, and multi-round MLE datapaths: internal arithmetic uses II=1 pipelines, coefficient feedback uses eight interleaved accumulators, folding reuses multipliers, and the typical circuit has eight synchronous 1R1W table banks with finite output-credit control.

Here n is the number of Boolean variables and each table has N=2^n entries. A term is a weighted product of factor tables; the bundled maximum is two terms with two factors each. ROUND returns one round polynomial, ROUND_FOLD also folds each table once, FOLD only folds once, and EVAL folds all n variables to one value per active table. All arithmetic is modulo p = 2^64 - 2^32 + 1. II=1 means one arithmetic launch per cycle; 1R1W means one read port and one write port per bank, not two task engines.

## Datapath

Input payloads are registered after handshake, then checked and used for arithmetic or memory writes. ROUND/ROUND_FOLD compute coefficients from the input stream. FOLD/EVAL load tables before scanning under caller-supplied challenges. Each term generates coefficients, applies its weight, merges with the other term, and accumulates across point pairs.

| Unit | Typical instances | Latency | Initiation interval |
| --- | ---: | ---: | ---: |
| Goldilocks modular multiplier | 14 | 20 cycles | 1 cycle |
| Goldilocks modular adder | 33 | 5 cycles | 1 cycle |
| Goldilocks modular subtractor | 4 | 5 cycles | 1 cycle |

Each term has four first-layer multipliers and three weight multipliers. Folding reuses the first two first-layer multipliers per term rather than allocating a second multiplier array. A modular multiplier uses four 32×32 products with pipelined reduction. These counts describe logical arithmetic sites, not DC's final multiplier-cell count.

Wide addition uses 16-bit local computation followed by cross-block carry selection, avoiding a single-cycle path through wide addition, reduction and result correction. The full path from coefficient-input handshake to accumulator writeback takes 61 cycles. Within it, the accumulator addition itself has a five-cycle feedback dependency, hidden by eight interleaved accumulator slots per coefficient. After the last feedback returns, a balanced three-level, 15-cycle tree merges the partial sums. Generated degree-three configurations use an 86-cycle coefficient-feedback latency.

## Storage and folding

The typical circuit holds four 65,536-entry tables. Each table splits even/odd addresses across two 32768×64 banks, giving eight banks and 2 MiB. Each bank has one synchronous read port with one-cycle latency and one write port. Loading and folding writes are selected before the unique write port. Public SV defines generic memory behavior; technology SRAM mapping is performed by the integration flow.

Folding scans pairs in ascending order, with the two banks supplying adjacent points in the same cycle. Its read-to-result latency is 32 cycles: memory read1, operand register1, subtraction5, multiplication20, addition5. Compacted writes target already-scanned storage. Each round drains before the next begins. EVAL actually scans 2^(n−1)+…+1 pairs and returns each table's final value.

## Pipelines and scheduling

Task capacity is one: complete tasks do not overlap. Coefficient input and folding issue can each accept one pair per cycle, with all active tables represented in that pair. Task latency and sustained throughput are measured separately from handshake timestamps; see [Performance](performance.en.md).

Folded outputs use a 64-beat FIFO. Read issue reserves credit for in-flight results and pauses when credit is exhausted, without withdrawing committed outputs. The FIFO's generic asynchronous-read behavior is distinct from synchronous table-memory behavior and must be mapped accordingly.

## Verification/product separation

The verification top drives the native interface directly; C++ only supplies clock, reset, and test mode. Verification ROMs, logs, and utilization tracking are excluded from the independent product file list. Product RTL contains no measurement probes or FPGA primitives. C computes coefficients by polynomial convolution; Python uses interpolation and Boolean-weight MLE evaluation for independent cross-checking.
