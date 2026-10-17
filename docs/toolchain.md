<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Sumcheck/MLE Hardware Accelerator
Chisel hardware for Sumcheck round coefficients, multilinear folding and evaluation.
Repository: https://github.com/OpenRTL-Lab/sumcheck-mle-goldilocks
File: docs/toolchain.md

SPDX-FileCopyrightText: 2026 Goldilocks Sumcheck/MLE contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# 工具链

[English](toolchain.en.md)

本文件列出本项目电路生成、独立参考和 RTL 功能/性能复测使用的公开工具版本，说明依赖解析和可选环境变量，帮助读者准备一致的复现环境。

| 工具 | 已使用版本 | 用途 |
| --- | --- | --- |
| Mill | 1.1.7 | 构建入口 |
| Scala | 2.13.18 | Chisel 源码编译 |
| Chisel 与插件 | 7.13.0 | 电路生成 |
| CIRCT firtool | 1.149.0 | SystemVerilog 降级 |
| OpenJDK | 21.0.11 | JVM |
| Python | 3.12 | 流程与大整数参考 |
| Verilator | 5.050 | lint 与 RTL 仿真 |
| GCC/G++ | 16.2.0 | C 参考和 C++ 仿真构建 |

在 Linux 上安装公开发行的 JDK、Mill、Python、Verilator、GCC/G++ 和 make，使 `java`、`mill`、`python3`、`verilator`、`cc`、`g++`、`make` 可从 PATH 调用。工具入口：[Mill](https://mill-build.org/)、[Chisel](https://www.chisel-lang.org/)、[Verilator](https://verilator.org/guide/latest/install.html)、[GCC](https://gcc.gnu.org/)。

Scala/Chisel 与插件由 [构建定义](../build.mill) 固定并通过公开 Maven 依赖解析，firtool 由 Chisel 对应的公开解析流程获取。首次运行需要可访问依赖源的网络，或读者准备的相同版本缓存。项目不携带工具二进制、机器专用启动器或缓存。

若 Mill 启动器名称不同，可设置 `MILL='bash mill-launcher'`；`CC` 可指定 C 编译器，缓存位置可通过用户环境设置，临时目录可用 `TMPDIR`。验证对象目录与结果留在所选 build 输出目录；没有绑定原开发机器目录。完整步骤见 [快速复现](reproduction.md)。
