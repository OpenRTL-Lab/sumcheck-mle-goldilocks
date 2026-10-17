<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Sumcheck/MLE Hardware Accelerator
Chisel hardware for Sumcheck round coefficients, multilinear folding and evaluation.
Repository: https://github.com/OpenRTL-Lab/sumcheck-mle-goldilocks
File: docs/synthesis-summary.md

SPDX-FileCopyrightText: 2026 Goldilocks Sumcheck/MLE contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# 综合摘要

[English](synthesis-summary.en.md)

本文件转述典型 Sumcheck/MLE 电路在 TSMC 28 nm HPC+ 最差指定工艺角、500 MHz 与10% setup时钟不确定度下的 DC 综合结果：setup通过，121,483个已约束端点的最差路径最大39级，总映射cell面积9.732878 mm²，其中SRAM宏面积9.423642 mm²。

GL64 指64位规范 Goldilocks 域算术；maxLogN=16 表示表长上限 2^16，两项二次乘积需要四张因子表。1R1W 指每个 bank 各有一个读口和一个写口。WNS 是最差 setup 裕量，TNS 是负 setup 裕量之和。

## 综合条件

| 项目 | 条件 |
| --- | --- |
| 电路 | GoldilocksSumcheckMle，GL64，maxLogN16，两项、每项最多二次 |
| 综合软件 | Synopsys Design Compiler NXT X-2025.06-SP3，linux64 |
| 标准单元工艺 | TSMC 28 nm HPC+ |
| 工艺角 | SSG，0.81 V，−40 °C |
| 时钟 | 单时钟，500 MHz，周期2.000 ns |
| setup不确定度 | 0.200 ns，即周期的10%；本流程以该值表达时钟抖动预算 |
| 时钟转换时间 | 0.100 ns |
| 输入/输出最大延迟 | 各1.000 ns，相对于同一时钟 |
| 输入转换时间 | 0.125 ns |
| 最大转换时间 | 0.250 ns |
| 输出pin负载 | 0.020 pF |
| 复位 | 同步高有效 |
| 表存储映射 | 逻辑8个32768×64、1R1W银行；物理256颗SRAM宏 |

公开SV保留通用存储行为；该配置映射使用已验证的1R1W SRAM实现。逻辑表容量2 MiB，输出FIFO单独实现。面积为DC映射cell面积，不是芯片die面积。

## Setup与逻辑级数

| 指标 | 结果 |
| --- | ---: |
| Setup WNS | +0.000020504 ns |
| Setup TNS | 0 ns |
| 违例端点数 | 0 |
| 已约束setup端点数 | 121,483 |
| 最大逻辑级数 | 39 |
| 严格小于50级 | 121,483 / 121,483（100%） |
| 大于30级 | 168（0.1383%） |
| 大于40级 | 0 |
| 大于50级 | 0 |

按每个已约束setup端点最差的一条max数据路径统计，包含118,078条内部到内部、3,054条输入到内部、351条内部到输出路径。逻辑级数仅按数据路径中叶名严格为 `Z` 或 `ZN` 的输出引脚计数，每个计一级；不按报告行数计数，不计其他引脚、net行、clock-to-Q或时钟树。完整路径引脚序列与时序点交叉核对一致。各阈值是累计口径，不能相加。

## 面积

| 分类（互不重叠） | 面积（µm²） | 面积（mm²） | 占比 |
| --- | ---: | ---: | ---: |
| 组合逻辑（不含Buf/Inv） | 137,236.946 | 0.137237 | 1.4100% |
| Buf/Inv | 6,860.588 | 0.006861 | 0.0705% |
| 非组合逻辑 | 165,138.429 | 0.165138 | 1.6967% |
| SRAM宏 | 9,423,642.000 | 9.423642 | 96.8228% |
| 总cell面积 | 9,732,877.963 | 9.732878 | 100% |

标准单元部分合计309,235.963 µm²（0.309236 mm²）。分组已将原始组合面积中包含的Buf/Inv拆开，避免重复求和。

## 提供方式

本文件提供综合条件、指标、定义与结果转述。抱歉，由于第三方许可与保密约束，无法公开工艺库、SRAM宏实现、DC原始Tcl、报告或网表快照。读者可以使用仓库提供的电路与 [快速复现](reproduction.md) 重现RTL和仿真链路，再对接自己的综合环境。周期性能及500 MHz换算见 [性能指标](performance.md)。
