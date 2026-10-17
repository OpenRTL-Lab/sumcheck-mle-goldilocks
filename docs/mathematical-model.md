<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Sumcheck/MLE Hardware Accelerator
Chisel hardware for Sumcheck round coefficients, multilinear folding and evaluation.
Repository: https://github.com/OpenRTL-Lab/sumcheck-mle-goldilocks
File: docs/mathematical-model.md

SPDX-FileCopyrightText: 2026 Goldilocks Sumcheck/MLE contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# 电路功能与数学模型

[English](mathematical-model.en.md)

本文件严格定义 Goldilocks Sumcheck/MLE 电路的域、输入布局、单轮完整多项式、挑战折叠与多线性扩展求值，给出四种运行模式及可手算的已知答案，以便调用方按相同数学语义集成和验证。

## 域与配置

所有运算在 $\mathbb F_p$ 中进行，其中：

$$
p=2^{64}-2^{32}+1=18446744069414584321.
$$

端口域元素采用 64 位规范表示 $0\le x<p$，不是 Montgomery 表示。定义生成参数 $L=\mathrm{maxLogN}$、$D=\mathrm{maxDegree}$。源码支持 $1\le L\le16$、$D\in\{2,3\}$；随包电路为 $L=16,D=2$。表槽数为 $S=2D$。

运行时变量数为 $n$，表长 $N=2^n$，项数 $1\le T\le2$。每项次数 $1\le d_t\le D$，权重为 $\lambda_t\in\mathbb F_p$。槽号 $s=tD+j$ 对应第 $t$ 项第 $j$ 张表 $A_{t,j}$，只有 $0\le t<T,\ 0\le j<d_t$ 的槽活动。表的变量顺序是整数索引的最低位到最高位，不需要位反转。

## 单轮完整多项式

$n\ge1$ 时，第 $i$ 拍输入 $a_{i,t,j}=A_{t,j}[2i]$、$b_{i,t,j}=A_{t,j}[2i+1]$，$0\le i<2^{n-1}$。定义：

$$
q(X)=\sum_{i=0}^{2^{n-1}-1}\sum_{t=0}^{T-1}
\lambda_t\prod_{j=0}^{d_t-1}
\left(a_{i,t,j}+X(b_{i,t,j}-a_{i,t,j})\right)
=\sum_{k=0}^{d}c_kX^k,\qquad d=\max_{t<T}d_t.
$$

电路输出所有 $c_0,\ldots,c_d$，顺序为常数项到最高次项；这不是只输出 $q(0),q(1)$ 的接口。其布尔求和关系为：

$$
q(0)+q(1)=\sum_{u=0}^{2^n-1}\sum_{t=0}^{T-1}
\lambda_t\prod_{j=0}^{d_t-1}A_{t,j}[u].
$$

调用方可以将返回的系数用于 Sumcheck 轮消息及声明检查，挑战由调用方提供。

## 挑战折叠与完整 MLE

单轮挑战 $r\in\mathbb F_p$ 的折叠定义为：

$$
B_{t,j}[i]=A_{t,j}[2i]+r\left(A_{t,j}[2i+1]-A_{t,j}[2i]\right).
$$

完整 MLE 模式按 $r_0,\ldots,r_{n-1}$ 依次消去最低位变量。设 $u_k$ 是索引 $u$ 的第 $k$ 位，定义 $\chi_0(r)=1-r,\ \chi_1(r)=r$，则每个活动槽的最终值为：

$$
\widetilde A_{t,j}(r_0,\ldots,r_{n-1})
=\sum_{u=0}^{2^n-1}A_{t,j}[u]\prod_{k=0}^{n-1}\chi_{u_k}(r_k).
$$

完整 MLE 返回每张活动表的值，不自动计算这些值的加权乘积之和。$n=0$ 时表只有一个值，空乘积为 1，没有挑战；输入 a 为表值、b 为零。

## 模式对应关系

| mode | 名称 | 数学输出 |
| --- | --- | --- |
| 0 | ROUND | 一拍完整系数 $c_0,\ldots,c_d$ |
| 1 | ROUND_FOLD | 先完整系数，再在该系数帧握手后接收 $r$，输出全部 $B_{t,j}[i]$ |
| 2 | FOLD | 只输出单轮 $B_{t,j}[i]$ |
| 3 | EVAL | 接收全部 $n$ 个挑战，输出所有活动槽的最终 MLE 值 |

前三种模式要求 $1\le n\le L$；EVAL 要求 $0\le n\le L$。ROUND_FOLD 只计算一个 Sumcheck 轮，后续轮由调用方调度；EVAL 则在单个任务中实际执行全部 MLE 折叠轮。

## 已知答案

令 $n=1,T=2,d_0=d_1=2,\lambda=(2,3)$，四张表依次为 $(1,3),(2,5),(4,7),(6,8)$：

$$
q(X)=2(1+2X)(2+3X)+3(4+3X)(6+2X)=76+92X+30X^2.
$$

因此 $q(0)+q(1)=274$；$r=2$ 时折叠值为 $(5,8,10,10)$，且 $q(2)=380=2\cdot5\cdot8+3\cdot10\cdot10$。

独立 MLE 已知答案还包括表 $(0,0,0,1,0,1,0,2)$ 在 $(1,1,1)$ 处为 2，以及常数表 5 在同一点为 5。数据见 [已知答案](../operators/sumcheck-mle/reference/known-answers.json)，来源说明见 [第三方声明](third-party-notices.md)。这些全一坐标答案不依赖变量顺序；随机非布尔挑战另由独立大整数布尔权重公式交叉验证。

端口编码、状态与握手顺序见 [原生接口](interface.md)。
