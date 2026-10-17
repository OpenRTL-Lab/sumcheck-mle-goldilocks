<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Sumcheck/MLE Hardware Accelerator
Chisel hardware for Sumcheck round coefficients, multilinear folding and evaluation.
Repository: https://github.com/OpenRTL-Lab/sumcheck-mle-goldilocks
File: docs/third-party-notices.md

SPDX-FileCopyrightText: 2026 Goldilocks Sumcheck/MLE contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# 第三方声明

[English](third-party-notices.en.md)

本文件说明项目许可、数学参考与公开工具依赖：本仓库设计和验证代码使用MIT许可，已知答案保留参考来源，构建工具与综合工艺资料分别遵守其自身许可。

## 项目许可

设计源码、生成SV、生成/验证脚本和本项目文档按 [MIT 许可证](../LICENSE) 提供，允许免费商用、修改与再分发，须保留版权及许可声明。

## 数学参考和已知答案

Sumcheck与多线性扩展的定义参照公开数学方法；本项目的原生编码、LSB优先变量顺序及Goldilocks配置以 [数学模型](mathematical-model.md) 为准。

[已知答案](../operators/sumcheck-mle/reference/known-answers.json)中的两组小型MLE表值参考 [Nova 多线性测试](https://github.com/microsoft/Nova/blob/main/src/spartan/polys/multilinear.rs)。Nova由Microsoft开发并按 [Nova MIT 许可证](https://github.com/microsoft/Nova/blob/main/LICENSE) 发布，版权声明为 Copyright (c) Microsoft Corporation. 本仓库保留这两组数学答案的来源，不引入Nova源码或运行时依赖；全部参考计算由随包C/Python模型执行。二次轮的四张表与76、92、30系数为本项目可手算样例。

采用相同数学构件不意味着协议、标量域、表顺序或字节编码自动兼容；读者可按 [原生接口](interface.md) 对接自己的证明流程。

## 工具与工艺资料

Chisel、Scala、Mill、CIRCT、Verilator、GCC及Python通过公开发行版使用，各工具遵守自己的许可，版本与安装入口见 [工具链](toolchain.md)。本项目MIT许可不重新授权第三方工具或工艺资料。

抱歉，由于授权与保密限制，TSMC工艺库、SRAM宏和Synopsys专有工具无法随包提供；综合条件与结果转述见 [综合摘要](synthesis-summary.md)。
