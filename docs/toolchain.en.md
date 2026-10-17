<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Sumcheck/MLE Hardware Accelerator
Chisel hardware for Sumcheck round coefficients, multilinear folding and evaluation.
Repository: https://github.com/OpenRTL-Lab/sumcheck-mle-goldilocks
File: docs/toolchain.en.md

SPDX-FileCopyrightText: 2026 Goldilocks Sumcheck/MLE contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# Toolchain

[简体中文](toolchain.md)

This document lists the public-tool versions used for generation, independent reference checking, and RTL functional/performance reproduction, including dependency resolution and optional environment variables.

| Tool | Version used | Purpose |
| --- | --- | --- |
| Mill | 1.1.7 | Build entry point |
| Scala | 2.13.18 | Chisel-source compilation |
| Chisel and plugin | 7.13.0 | Circuit generation |
| CIRCT firtool | 1.149.0 | SystemVerilog lowering |
| OpenJDK | 21.0.11 | JVM |
| Python | 3.12 | Flow and big-integer reference |
| Verilator | 5.050 | Lint and RTL simulation |
| GCC/G++ | 16.2.0 | C reference and C++ simulation builds |

On Linux, install public distributions of JDK, Mill, Python, Verilator, GCC/G++, and make so that `java`, `mill`, `python3`, `verilator`, `cc`, `g++`, and `make` are callable from PATH. Installation resources: [Mill](https://mill-build.org/), [Chisel](https://www.chisel-lang.org/), [Verilator](https://verilator.org/guide/latest/install.html), and [GCC](https://gcc.gnu.org/).

[Build definition](../build.mill) pins Scala, Chisel, and the compiler plugin using public Maven dependencies. Chisel resolves the corresponding public firtool distribution. The first run requires network access to dependency repositories or reader-provided matching caches. Tool binaries, machine-specific launchers, and caches are not bundled.

If the Mill launcher has another name, set `MILL='bash mill-launcher'`. `CC` selects the C compiler; cache locations are chosen by the user environment, and `TMPDIR` may select temporary storage. Simulation objects and results remain under the selected build output directory, without dependency on the original development workspace. See [Reproduction](reproduction.en.md) for the complete flow.
