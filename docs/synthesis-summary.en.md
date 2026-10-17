<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Sumcheck/MLE Hardware Accelerator
Chisel hardware for Sumcheck round coefficients, multilinear folding and evaluation.
Repository: https://github.com/OpenRTL-Lab/sumcheck-mle-goldilocks
File: docs/synthesis-summary.en.md

SPDX-FileCopyrightText: 2026 Goldilocks Sumcheck/MLE contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# Synthesis Summary

[简体中文](synthesis-summary.md)

This document restates DC synthesis results for the typical Sumcheck/MLE circuit using TSMC28 nm HPC+, the specified worst process corner,500 MHz, and 10% setup clock uncertainty: setup passes; the121,483 constrained endpoint-worst paths have a maximum depth of 39; total mapped cell area is9.732878 mm², including 9.423642 mm² of SRAM macros.

GL64 denotes canonical 64-bit Goldilocks arithmetic. maxLogN=16 bounds table length at 2^16 entries; two degree-two terms need four factor tables. 1R1W means one read port and one write port per bank. WNS is minimum setup slack; TNS sums negative setup slacks.

## Conditions

| Item | Condition |
| --- | --- |
| Circuit | GoldilocksSumcheckMle; GL64; maxLogN16; two terms of degree up to two |
| Synthesis tool | Synopsys Design Compiler NXT X-2025.06-SP3, linux64 |
| Standard-cell technology | TSMC28 nm HPC+ |
| Process corner | SSG,0.81 V,−40 °C |
| Clock | One clock,500 MHz,2.000 ns period |
| Setup uncertainty | 0.200 ns,10% of period; used as the clock-jitter budget in this flow |
| Clock transition | 0.100 ns |
| Maximum input/output delays | 1.000 ns each, referenced to the same clock |
| Input transition | 0.125 ns |
| Maximum transition | 0.250 ns |
| Output pin load | 0.020 pF |
| Reset | Synchronous, active high |
| Table-memory mapping | 8 logical 32768×64, 1R1W banks; 256 physical SRAM macros |

Public SV retains generic memory behavior. The reported mapping uses validated1R1W SRAM implementations. Logical table capacity is 2 MiB, with a separately implemented output FIFO. Area is DC mapped-cell area, not die area.

## Setup and logic depth

| Metric | Result |
| --- | ---: |
| Setup WNS | +0.000020504 ns |
| Setup TNS | 0 ns |
| Violating endpoints | 0 |
| Constrained setup endpoints | 121,483 |
| Maximum logic depth | 39 |
| Strictly below 50 levels | 121,483 /121,483 (100%) |
| Greater than 30 levels | 168 (0.1383%) |
| Greater than 40 levels | 0 |
| Greater than 50 levels | 0 |

Statistics use the worst max data path for each constrained setup endpoint:118,078 internal-to-internal,3,054 input-to-internal, and 351 internal-to-output paths. Each data-path output pin whose leaf name is exactly `Z` or `ZN` counts as one logic level. Report-line counts, other pins, nets, clock-to-Q, and clock-tree points do not count. Full-path pin sequences and timing points were cross-checked. Threshold counts are cumulative and must not be added together.

## Area

| Disjoint category | Area (µm²) | Area (mm²) | Share |
| --- | ---: | ---: | ---: |
| Combinational logic, excluding buffers/inverters | 137,236.946 | 0.137237 | 1.4100% |
| Buffers/inverters | 6,860.588 | 0.006861 | 0.0705% |
| Noncombinational logic | 165,138.429 | 0.165138 | 1.6967% |
| SRAM macros | 9,423,642.000 | 9.423642 | 96.8228% |
| Total cell area | 9,732,877.963 | 9.732878 | 100% |

Standard cells total 309,235.963 µm² (0.309236 mm²). Buffers/inverters are separated from the original combinational-area total to avoid double counting.

## Availability

This document provides synthesis conditions, definitions, and restated results. We apologize that third-party licensing and confidentiality restrictions prevent publishing process libraries, SRAM-macro implementations, original DC Tcl, reports, or netlist snapshots. Readers can reproduce RTL and simulation using the distributed circuit and [Reproduction](reproduction.en.md), then use their own synthesis environment. Cycle measurements and 500 MHz conversions appear in [Performance](performance.en.md).
