<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Sumcheck/MLE Hardware Accelerator
Chisel hardware for Sumcheck round coefficients, multilinear folding and evaluation.
Repository: https://github.com/OpenRTL-Lab/sumcheck-mle-goldilocks
File: README.zh-CN.md

SPDX-FileCopyrightText: 2026 Goldilocks Sumcheck/MLE contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# sumcheck-mle-goldilocks 硬件加速器

[English](README.md)

本仓库提供使用 Chisel 开发的 Goldilocks Sumcheck/MLE 独立硬件加速器。它可通过原生接口集成到 ASIC 或 FPGA，执行零知识证明中的单轮 Sumcheck 系数计算、多线性表折叠和多线性扩展求值，适用于基于这些运算的证明器、承诺求值与计算验证链路。

采用 [MIT 许可证](LICENSE)，允许免费商用、修改和再分发，须保留版权及许可声明；第三方参考与工具说明见 [第三方声明](docs/third-party-notices.md)。

[Release v1.1.0](https://github.com/OpenRTL-Lab/sumcheck-mle-goldilocks/releases/tag/v1.1.0) · [原生接口](docs/interface.md) · [快速复现](docs/reproduction.md) · [性能指标](docs/performance.md) · [综合摘要](docs/synthesis-summary.md)

## 计算与任务单位

n 个布尔变量对应每张输入表的 N = 2^n 个表项。“项”是一个带权的表因子乘积，不是一个表项或一个变量。最多两项，第 t 项的次数 d_t 是该项的因子数。因此，随包二次电路最多有四张活动因子表，每张 N 个元素。系数和表值均是 Goldilocks 素域 p = 2^64 - 2^32 + 1 下的无符号 64 位规范值，满足 0 ≤ x < p。

| 模式 | 一个任务返回的内容 |
| --- | --- |
| ROUND | 一轮多项式按次数递增排列的全部系数，不只是它在 0 和 1 处的值 |
| ROUND_FOLD | 先返回这些系数，接受一个挑战后，再返回全部活动表的一次折叠结果 |
| FOLD | 用一个挑战将全部活动表各折叠一次，不返回轮系数 |
| EVAL | 使用调用方提供的 n 个挑战，分别返回各活动表的 MLE 值，不自动计算其加权乘积或和 |

变量顺序按表索引的最低位优先。折叠输入配对表项 2i 和 2i+1，对挑战 r 返回 A[2i] + r * (A[2i+1] - A[2i])。一个任务只执行选定模式；EVAL 消去全部 n 个变量，而 ROUND_FOLD 只执行一个 Sumcheck 轮。“结果帧”是该阶段完整的输出组：ROUND_FOLD 的系数帧必须完成握手后才接受挑战。

## 配置速览

| 集成关注点 | 已发布硬件 |
| --- | --- |
| 计算 | Sumcheck 轮系数、挑战驱动的表折叠及多线性扩展（MLE）求值 |
| 随包配置 | 最多 16 个变量 / 65,536 个表点；2 项；每项次数 2 |
| 有限域及产品顶层 | Goldilocks p = 2^64 - 2^32 + 1；`GoldilocksSumcheckMle` |
| 模式 | ROUND、ROUND_FOLD、FOLD 和 EVAL；完整系数帧或表/求值帧 |
| 原生边界 | 任务、带索引的表输入、有序挑战、结果及完成 ready/valid 通道 |

## 快速开始

从 [Release v1.1.0](https://github.com/OpenRTL-Lab/sumcheck-mle-goldilocks/releases/tag/v1.1.0) 下载源码包，或克隆当前发布快照：

```bash
git clone --branch v1.1.0 --depth 1 https://github.com/OpenRTL-Lab/sumcheck-mle-goldilocks.git
cd sumcheck-mle-goldilocks
```

按 [工具链](docs/toolchain.md) 安装指定版本的依赖，然后在仓库根目录使用新的输出目录运行：

```bash
python3 tools/emit_sv.py --out build/emit --max-logn 16 --degree 2
```

此生成命令不是完整 RTL 回归。文档规定的验证入口为：

```bash
python3 tools/verify.py --out build/verify --jobs 8
```

预期结果、负控含义及短功能测试与完整性能测试的区别见 [快速复现](docs/reproduction.md)。

## 验证与集成边界

典型流程生成 45 个回归任务和 32 个长表性能任务，使用独立 C/Python 参考核对重新生成与随包产品，并分别运行理想接口、协议/背压、复位恢复及故意错误参考模式。生成三次配置有文档规定的单独命令，不继承随包二次配置的综合结果。

本模块不是完整交互式 Sumcheck 证明器或转录器。调用方提供声明、权重、正确排序的表和挑战。ROUND_FOLD 的系数帧接受完毕后才允许挑战握手。完整任务串行；仅交付系数帧不代表任务成功。固定 Goldilocks 域不能与 BN254 或其他证明系统的域互换。

### 正确理解性能

理想接口下，65,536 点表的 ROUND 任务延迟为 32,847 周期，稳态完成间隔为 32,848 周期。主要系数模乘单元的 99.756% 发射忙率按每实例 229,376 个有效发射周期除以 229,936 个窗口周期计算；不是多个实例求和，也不是有效流水级计数。ROUND_FOLD、FOLD、EVAL 的任务输出和时序不同，速率不能互换。

上述周期数来自理想原生接口下的 RTL 测量，按 MHz 换算的时间和速率不是板级或硅后实测。Design Compiler 结果只适用于文档所列配置、库角和约束，不代表布局布线签核或系统吞吐率；条件见 [性能指标](docs/performance.md) 和 [综合摘要](docs/synthesis-summary.md)。

## 仓库内容

- [Chisel 设计](operators/sumcheck-mle/src/main/scala/zk/sumcheck/)：原生接口、系数计算与折叠电路、验证环境。
- [算术公共函数](common/arithmetic/src/main/scala/)：局部前缀进位与 Goldilocks 域常量。
- [生成好的 SV](rtl/v1.1.0/gl64-n16-t2-d2/)：支持最多 65,536 点、两项、每项最多二次的典型电路；顶层为 `GoldilocksSumcheckMle`。
- [生成脚本](tools/emit_sv.py)与[验证脚本](tools/verify.py)：便携式电路生成及完整复测入口。
- [C 参考模型](operators/sumcheck-mle/cmodel/reference.c)、[独立大整数参考](operators/sumcheck-mle/tools/check_reference.py)和[仿真驱动](operators/sumcheck-mle/sim/driver.cpp)：逐字段核对结果，测量真实握手周期。
- [微架构](docs/architecture.md)、[原生接口](docs/interface.md)及[工具链](docs/toolchain.md)：结构、集成和复现所需的信息。

## 应用场景

本模块可用于 Goldilocks 域上的二次 Sumcheck 单轮生成、挑战驱动的表折叠、多表 MLE 求值及相关证明计算；例如将内积式声明转化为逐轮多项式，并在指定挑战点计算多线性表值，详见 [数学模型](docs/mathematical-model.md) 和 [原生接口](docs/interface.md)。

Sumcheck 与 MLE 是 Spartan 类证明计算中的基础构件，可参考 [Spartan 原理说明](https://microsoft.github.io/vega-prover/building-blocks/spartan.html)。这些是算法结构层面的集成示例，不限定应用系统；集成时由调用方选择 Goldilocks 域、表布局、声明、挑战和协议封装。随包电路每项最多二次；源代码另支持生成三次配置。

## 性能指标

理想接口下，65,536 点单轮系数任务延迟为 32,847 clk、连续任务完成间隔为 32,848 clk；按 500 MHz 换算为 65.694 µs 和约 15,222 任务/s；定义、测试方法及四种模式结果见 [性能指标](docs/performance.md)。

## 数学模型

电路计算模数为 `2^64−2^32+1` 的加权因子乘积之单轮完整系数、挑战折叠表或各表 MLE 值，严格公式、索引顺序和已知答案见 [数学模型](docs/mathematical-model.md)。

## 快速复现

使用公开工具可重新生成 SV，以独立 C/Python 参考验证生成电路和随包电路，并复测功能、协议、复位与性能，操作步骤见 [快速复现](docs/reproduction.md)。

## 综合结果

典型电路在 TSMC 28 nm HPC+、SSG 0.81 V −40 °C、500 MHz、10% setup 时钟不确定度下通过 DC setup 检查，121,483 条端点最差路径的最大逻辑级数为 39，条件、面积与统计口径见 [综合摘要](docs/synthesis-summary.md)。

## 公开边界

仓库提供独立加速器及可复现的 RTL/仿真链路，不包含 SoC、板级工程或完整证明协议。抱歉，由于第三方授权与保密约束，无法提供工艺库、存储宏实现、DC 原始脚本、报告或综合快照；综合信息以 [综合摘要](docs/synthesis-summary.md) 中的条件与结果转述提供。

## OpenRTL与定制集成

通过[OpenRTL组织主页](https://github.com/OpenRTL-Lab)、[ZK加速器集合](https://github.com/OpenRTL-Lab/zk-accelerators)及[OpenRTL网站](https://openrtl.top/)查找相关模块与合作联系入口。需要定制数据通路、接口或目标平台适配时可联系维护者；MIT商用权不附加购买服务的要求，不要在公开Issue提交保密规格。

## 发布源码与集成

版本标签标识包含来源声明和文档的当前源码树；Release 下载提供本版本硬件源码与随包 RTL。源码包与在线目录的注释及文档可能不同。RTL 集成使用产品文件清单，不把验证环境作为产品电路；支持配置、验证范围和测量条件见验证及综合文档。
