<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Sumcheck/MLE Hardware Accelerator
Chisel hardware for Sumcheck round coefficients, multilinear folding and evaluation.
Repository: https://github.com/OpenRTL-Lab/sumcheck-mle-goldilocks
File: docs/performance.md

SPDX-FileCopyrightText: 2026 Goldilocks Sumcheck/MLE contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# 性能指标

[English](performance.en.md)

本文件定义任务延迟、连续任务吞吐和逐计算单元忙率，并给出理想原生接口下典型65,536点配置的实测结果：单轮系数延迟32,847 clk，完成间隔32,848 clk；主要系数乘法单元持续忙率99.756%，生成与随包电路的三次复测完全一致。

n 是变量数，N=2^n 为每张表的元素数，maxLogN 是 n 的上限；一个项是因子表的带权乘积，maxDegree 限制每项因子数。ROUND 输出轮系数，ROUND_FOLD 输出系数和一轮折叠帧，FOLD 只输出一轮折叠帧，EVAL 返回每个活动表在 n 次折叠后的值。本文 L 只表示任务延迟，不表示 maxLogN 或表长；II 是算术发射间隔。频率换算不是板级或硅后实测。

## 测试配置与方法

配置为 Goldilocks、maxLogN=16、maxDegree=2，四张表、两项二次、权重2和3。输入每拍包含四张表的相邻点对；原生接口理想供数、理想接收，挑战在最早合法时序给出，不设置外部带宽或延迟限制。内部同步存储、流水排空、挑战握手和任务切换的真实周期均计入。

[验证脚本](../tools/verify.py)生成45个回归任务和32个性能任务，四种mode各连续8个n=16任务。每个产品的理想性能完整运行3次；重新生成与随包SV分别编译、分别验证并要求结果一致。测试入口见 [快速复现](reproduction.md)。

C 参考用因子多项式卷积求系数、逐轮折叠求表值；独立 Python 参考通过采样/插值计算系数、通过布尔权重求和计算 MLE。测试包含一次/二次混合、单项/两项、权重0与p−1、挑战0/1与随机、n=0的MLE、全零/边界输入和非法帧。

## 延迟与吞吐定义

设 A 为 task 握手周期，F 为首输入握手周期，O 为末结果握手周期，C 为 completion 握手周期：

$$
L=C-A,\qquad L_{\rm output}=O-A.
$$

发行的任务延迟 L 包含输入、计算、输出及 completion；不是算术流水深度。每组连续8任务，去掉首任务预热后使用7次完成间隔：

$$
W=C_7-C_0,\qquad R_{\rm cycle}=\frac{7}{W},\qquad R_{\rm second}=f\frac{7}{W}.
$$

微架构内部有20拍模乘和5拍模加减流水，II=1；完整任务容量为1，不重叠。因此实测持续完成间隔比单任务延迟多1拍，两者仍独立测量。每一轮挑战到下一轮的真实排空也包含在窗口内。

## 四种模式实测

| 模式 | A | F | O | C | L（clk） | 连续完成间隔（clk） |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| ROUND | 4,363 | 4,364 | 37,209 | 37,210 | 32,847 | 32,848 |
| ROUND_FOLD | 267,147 | 267,148 | 332,795 | 332,797 | 65,650 | 65,651 |
| FOLD | 792,355 | 792,356 | 857,926 | 857,928 | 65,573 | 65,574 |
| EVAL | 1,316,947 | 1,316,948 | 1,415,780 | 1,415,781 | 98,834 | 98,835 |

以上时间戳为测试中的首个性能任务，全部七次完成间隔均等于表中值。EVAL 一次任务计算四张65,536点表的完整16轮MLE。

| 模式 | 稳态7次完成窗口（clk） | 任务/clk | 500 MHz 延迟（µs） | 500 MHz 任务/s |
| --- | ---: | ---: | ---: | ---: |
| ROUND | 229,936 | 0.000030443254 | 65.694 | 15,221.627 |
| ROUND_FOLD | 459,557 | 0.000015232060 | 131.300 | 7,616.030 |
| FOLD | 459,018 | 0.000015249947 | 131.146 | 7,624.973 |
| EVAL | 691,845 | 0.000010117873 | 197.668 | 5,058.937 |

clk 数来自 RTL 仿真；µs与任务/s按 f=500 MHz换算。该频率的DC条件见 [综合摘要](synthesis-summary.md)。

## 计算单元忙率

busy 表示该逻辑站点在本 clk 发射一项有效运算，idle=窗口周期−busy；20/5拍在途不重复算为20/5次busy。另记录 occupied，表示流水中至少有一项有效运算的周期比例。日志由验证层记录，不向产品加入性能计数器。

典型电路有14个模乘、33个模加、4个模减。下表按相同发射计数分组，数值是**组内每个实例**的 busy，不是将整个组相加。t=0,1；k=0,1,2；j按所列范围。四模式窗口依次为229,936、459,557、459,018、691,845 clk，因此所有 idle 都可以由窗口减 busy 精确得到。

| 实例组 | 实例数 | ROUND busy | ROUND_FOLD busy | FOLD busy | EVAL busy |
| --- | ---: | ---: | ---: | ---: | ---: |
| term{t}.first0/first1 模乘；table{0..3}.delta-sub 模减 | 8 | 229,376 | 458,752 | 229,376 | 458,745 |
| term{t}.first2/first3及lambda{0..2}模乘；quad-add模加；coefficient{k}.term-add/acc-add模加 | 18 | 229,376 | 229,376 | 0 | 0 |
| term{t}.fold-add{0..1} 模加 | 4 | 0 | 229,376 | 229,376 | 458,745 |
| coefficient{k}.tree1.{0..3}/tree2.{0..1}/tree3 模加 | 21 | 7 | 7 | 0 | 0 |

例如第一组各实例的 busy/idle：ROUND为229,376/560，ROUND_FOLD为458,752/805，FOLD为229,376/229,642，EVAL为458,745/233,100；忙率分别为99.756%、99.825%、49.971%、66.307%。

主要乘法器在长表系数/折叠计算阶段实现连续发射。EVAL首轮折叠的发射忙率99.899%，每轮扫描的有效发射数随表长度递减；完整任务指标覆盖装载和所有轮。重用乘法器与归并树的按阶段调度使不同模式有不同忙率，逐实例/逐轮全部值可在本次生成的 results.json 中查看。

## 验证结果

| 测试 | 每个产品任务数 | 核对结果字数 | mismatch | 结论 |
| --- | ---: | ---: | ---: | --- |
| 理想接口 | 77 | 2,098,497 | 0 | 通过；重复3次性能完全一致 |
| 协议暂停/背压 | 45 | 1,265 | 0 | 通过 |
| 在途复位恢复 | 45 | 1,265 | 0 | 通过，确认触发并重提 |
| 故意错误参考 | 45 | 1,265 | 1 | 按预期失败，证明检查器能发现错误 |

另通过n4/d2与n8/d3功能回归，以及将项目放到新目录后的典型配置双产品复测。三次配置属于生成覆盖，随包SV和本文性能表仍是n16/d2典型配置。
