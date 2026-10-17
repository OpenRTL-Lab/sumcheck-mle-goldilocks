<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Sumcheck/MLE Hardware Accelerator
Chisel hardware for Sumcheck round coefficients, multilinear folding and evaluation.
Repository: https://github.com/OpenRTL-Lab/sumcheck-mle-goldilocks
File: docs/third-party-notices.en.md

SPDX-FileCopyrightText: 2026 Goldilocks Sumcheck/MLE contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# Third-party Notices

[简体中文](third-party-notices.md)

This document explains project licensing, mathematical references, and public-tool dependencies: repository design and verification code use MIT, known answers retain their reference source, and build tools and process information remain subject to their respective licenses.

## Project license

Design sources, generated SV, generation/verification scripts, and project documentation are provided under the [MIT License](../LICENSE), allowing free commercial use, modification, and redistribution while retaining copyright and license notices.

## Mathematical references and known answers

Sumcheck and multilinear extensions follow publicly described mathematical methods. This project's native encoding, LSB-first variable order, and Goldilocks configuration are specified by [Mathematical model](mathematical-model.en.md).

Two small MLE examples in [Known answers](../operators/sumcheck-mle/reference/known-answers.json) refer to [Nova multilinear tests](https://github.com/microsoft/Nova/blob/main/src/spartan/polys/multilinear.rs). Nova is developed by Microsoft and distributed under the [Nova MIT License](https://github.com/microsoft/Nova/blob/main/LICENSE), with Copyright (c) Microsoft Corporation. This repository retains attribution for those mathematical answers without importing Nova source code or runtime dependencies. Bundled C/Python models perform all reference computations. The four quadratic-round tables and coefficients 76,92,30 are the project's hand-checkable example.

Shared mathematical components do not automatically imply compatibility in protocol, scalar field, table ordering, or byte encoding. Readers can connect their proving flows using [Native interface](interface.en.md).

## Tools and process information

Chisel, Scala, Mill, CIRCT, Verilator, GCC, and Python are used through public distributions under their own licenses; see [Toolchain](toolchain.en.md) for versions and installation resources. This project's MIT license does not relicense third-party tools or process information.

We apologize that licensing and confidentiality restrictions prevent bundling TSMC process libraries, SRAM macros, or proprietary Synopsys tools. Restated synthesis conditions and results are provided in [Synthesis summary](synthesis-summary.en.md).
