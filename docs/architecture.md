<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Sumcheck/MLE Hardware Accelerator
Chisel hardware for Sumcheck round coefficients, multilinear folding and evaluation.
Repository: https://github.com/OpenRTL-Lab/sumcheck-mle-goldilocks
File: docs/architecture.md

SPDX-FileCopyrightText: 2026 Goldilocks Sumcheck/MLE contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# 微架构

[English](architecture.en.md)

本文件介绍系数计算、表存储、挑战折叠与多轮 MLE 调度的协同结构：内部算术采用 II=1 流水，系数反馈采用八槽交织，折叠复用乘法器，典型配置使用八个同步 1R1W 表银行和有限输出信用控制。

n 为布尔变量数，每张表含 N=2^n 个元素；一个“项”是带权的因子表乘积，随包最多两项、每项两个因子。ROUND 返回一轮多项式，ROUND_FOLD 还将各表折叠一次，FOLD 只折叠一次，EVAL 消去全部 n 个变量、分别得到各活动表的一个值。全部算术模 p = 2^64 - 2^32 + 1。II=1 指每拍可发射一次算术，1R1W 指每个 bank 各有一个读口和一个写口，不是两套任务引擎。

## 数据路径

输入握手后先寄存载荷，再检查字段并驱动计算或表写入。ROUND/ROUND_FOLD 在输入流上生成各项系数；FOLD/EVAL 装载表后按挑战顺序扫描。两项分别生成系数，乘以权重后合并，再对全部点对累加。

| 单元 | 典型实例数 | 延迟 | 发射间隔 |
| --- | ---: | ---: | ---: |
| Goldilocks 模乘 | 14 | 20 clk | 1 clk |
| Goldilocks 模加 | 33 | 5 clk | 1 clk |
| Goldilocks 模减 | 4 | 5 clk | 1 clk |

每项有4个第一层模乘和3个权重模乘。折叠复用每项前2个第一层模乘，不重复配置专用乘法阵列。每个模乘使用4个32×32乘积并流水化归约；上述是逻辑算术站点数，不是 DC 最终乘法 cell 数。

宽加法采用16位局部计算与跨块进位选择，避免把宽加法、归约和结果修正全部放在一拍。从系数输入握手到累加槽写回的完整路径为61 clk，其中累加加法自身的反馈依赖为5拍；每个系数使用8槽交织以覆盖这5拍等待，最后反馈返回后通过15拍三级平衡树合并。源码三次配置的系数反馈延迟为86 clk。

## 存储与折叠

典型容量为4张65,536点表，每张表的偶/奇地址各用一个32768×64银行，共8银行、2 MiB。每银行同步读1拍、单读单写，装载与折叠写回先选择后进入唯一写口。公开 SV 提供通用存储行为；工艺 SRAM 的映射由集成流程完成。

折叠扫描从0递增，两个银行同拍提供相邻点；读1拍、操作数寄存1拍、减法5拍、乘法20拍、加法5拍，合计32拍。写回紧缩表的地址落在已扫描区，先排空当前轮再开始下一轮。EVAL 实际扫描 2^(n−1)+…+1 个点对，末轮输出各表最终值。

## 流水与调度

任务级容量是1，并非多个完整任务之间的流水线。系数阶段可以每拍接受一个点对，折叠阶段可以每拍发射一个点对；每个点对同时覆盖全部活动表。延迟和长期任务吞吐分别使用握手时间戳测量，见 [性能指标](performance.md)。

折叠输出使用64拍 FIFO。读发射前为在途结果预留信用，信用不足时停止发射；背压不撤销已经承诺的输出。FIFO 通用异步读行为与表存储同步读行为分开定义，不应不加区分地映射成同一种 SRAM。

## 验证与产品隔离

验证顶层直接驱动原生接口，C++ 仅驱动时钟、复位和测试模式。ROM、日志和忙率追踪用于验证，不列入独立产品文件清单；发行电路没有统计探针或 FPGA 原语。C 参考使用因子多项式卷积，Python 使用采样插值与布尔权重求值，形成独立核对链路。
