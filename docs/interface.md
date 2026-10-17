<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Sumcheck/MLE Hardware Accelerator
Chisel hardware for Sumcheck round coefficients, multilinear folding and evaluation.
Repository: https://github.com/OpenRTL-Lab/sumcheck-mle-goldilocks
File: docs/interface.md

SPDX-FileCopyrightText: 2026 Goldilocks Sumcheck/MLE contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# 原生接口

[English](interface.en.md)

本文件给出独立加速器的五个 ready/valid 通道、载荷编码、帧长度、挑战顺序、错误处理和复位约定，调用方可据此直接连接控制面与数据面。

任务含 n 个变量，每张因子表有 N=2^n 个元素。T=terms（1..2）是带权因子乘积的项数；degree[t]=d_t（1..D）是该项因子数，不是变量数。生成参数 D=maxDegree 随包为2，也可生成3。槽 s=t×D+j 保存第 t 项第 j 个因子，活动范围为 0 ≤ t < T、0 ≤ j < d_t；keep 是活动槽位掩码。令 d=max_t d_t 为轮多项式次数。模式0..2要求 1 ≤ n ≤ maxLogN，EVAL 另允许 n=0；随包 maxLogN=16。活动表值、lambda 权重和 r 挑战均为无符号规范整数 0 ≤ x < p，p = 2^64 - 2^32 + 1。

## 顶层与通道

顶层为 `GoldilocksSumcheckMle`，一个 `clock`，同步高有效 `reset`。所有通道仅在 valid && ready 时接受载荷；发送方阻塞时保持 valid 和全部字段稳定。支持一个在途完整任务，整体保序；tag 为调用方指定的 32 位任务标识并原样返回。

| 通道 | 方向 | 典型载荷位宽 |
| --- | --- | --- |
| task | 输入控制 | tag:32；mode:2；n:5；terms:2；degree[2]:各2；lambda[2]:各64 |
| input | 输入数据 | tag:32；index:17；keep:4；last:1；a[4]、b[4]:各64 |
| challenge | 输入控制 | tag:32；round:5；r:64 |
| result | 输出数据 | tag:32；kind:2；index:17；keep:4；last:1；words[4]:各64 |
| completion | 输出控制 | tag:32；status:3 |

SV 字段按 Chisel 展开，例如 `io_task_bits_tag`、`io_input_bits_a_0`，见 [顶层 SV](../rtl/v1.1.0/gl64-n16-t2-d2/GoldilocksSumcheckMle.sv)。生成三次配置时，槽数、keep 和 words 宽度变为 6，其他字段位宽不变。

## 数据布局与顺序

槽 s=tD+j；keep 的最低位对应槽0。活动槽由 terms、degree 决定，非活动槽 a/b 必须为零，输出非活动 words 也为零。所有活动域字、权重、挑战规范小于 p；严格数学定义见 [数学模型](mathematical-model.md)。

n>0 的输入每拍携带所有活动表的相邻两点，index 从0连续至 2^(n−1)−1；真实末拍 last=1。EVAL n=0 时一拍携带唯一 a 值，b 全零，index=0,last=1，无挑战。

| mode | 输入拍数 | 挑战顺序 | 结果 |
| --- | ---: | --- | --- |
| 0 ROUND | 2^(n−1) | 无 | kind0 一拍，index0，words 从常数到最高次；keep 低 d+1 位，last1 |
| 1 ROUND_FOLD | 2^(n−1) | kind0 结果握手之后接受 round0 | kind0 一帧，再 kind1 一帧共 2^(n−1) 拍 |
| 2 FOLD | 2^(n−1) | 装载完成后接受 round0 | kind1 共 2^(n−1) 拍 |
| 3 EVAL | n>0: 2^(n−1)；n=0: 1 | round0 至 round(n−1)，每轮 ready 后握手 | kind2 一拍，所有活动表的最终值 |

kind1 的 index 按折叠点从0递增，keep 为活动槽掩码，末拍 last=1。kind2 的 index=0,keep 为活动槽掩码,last=1。mode1 的两帧分别有 last；所有成功结果握手之后才给 completion。挑战可以提前保持 valid，但 ROUND_FOLD 不会在系数帧握手之前拉高 challenge.ready。

## 状态、错误与复位

| status | 含义 |
| --- | --- |
| 0 | OK，全部结果已接受 |
| 1 | BAD_PARAM，非法 n、terms、degree 或权重等；不接收输入帧 |
| 2 | BAD_FRAME，tag、index、keep、last、域表示、零填充或挑战字段错误 |

早到 last 不截短约定长度，电路仍排空应有输入拍数和在途计算，保证后续任务不被污染。错误挑战终止任务；已交付系数不代表该任务最终成功。复位清控制状态、FIFO 和流水有效位，不清表 SRAM；在途任务被丢弃，不返回其旧 completion。

表存储是有限深度同步 1R1W，输出有64拍 FIFO 和在途信用控制；背压通过 ready 传递，不丢结果。完整任务串行，算术内部 II=1 流水；详见 [微架构](architecture.md)。
