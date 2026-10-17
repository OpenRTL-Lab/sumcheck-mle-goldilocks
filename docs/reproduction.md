<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Sumcheck/MLE Hardware Accelerator
Chisel hardware for Sumcheck round coefficients, multilinear folding and evaluation.
Repository: https://github.com/OpenRTL-Lab/sumcheck-mle-goldilocks
File: docs/reproduction.md

SPDX-FileCopyrightText: 2026 Goldilocks Sumcheck/MLE contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# 快速复现

[English](reproduction.en.md)

本文件说明如何从本仓库独立生成电路、核对 C/Python 参考、验证生成与随包 SV，以及测量四种模式的延迟、吞吐和计算单元忙率；默认入口运行典型配置的完整测试。

## 获取版本快照

[Release v1.1.0](https://github.com/OpenRTL-Lab/sumcheck-mle-goldilocks/releases/tag/v1.1.0)

```bash
git clone --branch v1.1.0 --depth 1 https://github.com/OpenRTL-Lab/sumcheck-mle-goldilocks.git
cd sumcheck-mle-goldilocks
```

版本标签标识包含来源声明和文档的当前源码树；Release 下载提供本版本硬件源码与随包 RTL。源码包与在线目录的注释及文档可能不同。RTL 集成使用产品文件清单，不把验证环境作为产品电路；支持配置、验证范围和测量条件见验证及综合文档。

## 1. 环境准备

先按 [工具链](toolchain.md) 安装所列公开工具及版本，确认编译器、构建器和仿真器可用。以下命令仅检查版本，不执行验证；在仓库根目录运行：

```bash
java -version
mill --version
python3 --version
verilator --version
cc --version
g++ --version
```

后续命令也从仓库根目录执行。源码参数由 build.mill 固定，首次构建解析公开依赖；可选启动命令和缓存设置见 [工具链](toolchain.md)。

每条生成或验证命令使用 `build/` 下新的相对输出目录。`--max-logn h` 设置最大表长 2^h，不是 h；`--degree D` 设置每个带权项最多的因子数，因此 n16/d2 表示最多65,536点、每项最多二次的容量。`--jobs` 只控制编译并行度，`--no-bench` 跳过长表性能任务而不跳过功能测试。

## 2. 典型配置完整复测

下面的命令先构建 C 参考并生成45个回归任务及32个长表性能任务，再用独立 Python 参考核对结果，随后生成 SV、lint 产品并分别构建生成产品和随包产品的仿真：

```bash
python3 tools/verify.py --out build/verify --jobs 8
```

两个产品均执行理想接口、协议暂停/背压、在途复位恢复和故意错误参考测试。默认77任务中，性能任务为四种 mode 各8个，n=16；协议/复位/负参考各运行45个回归任务。每个产品的理想测试重复3次，要求性能测量完全一致。

理想性能测试不扫描外部带宽、延迟或共享端口；挑战在合法时序最早提供，发送方持续供数，接收方持续接受。协议测试的暂停与背压只验证功能可靠性，不用来定义发行性能。

输出 `status:0` 表示整条测试通过。负参考测试故意翻转第一个期望结果的一位，必须恰好报1次 mismatch 并返回失败；流程将这一预期失败作为检查器有效的证据。复位日志必须出现 RESET_RECOVERY，且重新提交的任务正常完成。

## 3. 查看结果

`build/verify/results.json` 包含配置、逐测试结果、逐任务 A/F/O/C 时间戳、延迟、连续完成间隔、逐单元有效发射/忙闲周期和每轮数据；定义与发行值见 [性能指标](performance.md)。各阶段日志位于同一输出目录，`rtl/product.f` 只列产品闭包，`rtl/simulation.f` 包含验证环境。

脚本不会覆盖已有结果目录。重复验证请使用新目录，例如以下命令执行第二次独立复测：

```bash
python3 tools/verify.py --out build/verify-again --jobs 8
```

## 4. 仅生成电路

下面命令生成典型 SV、参考向量以及产品/仿真清单，不编译或运行 Verilator：

```bash
python3 tools/emit_sv.py --out build/emit --max-logn 16 --degree 2
```

生成参数 max-logn 决定最大表容量，degree 决定每项最大次数。公开验证入口支持 max-logn 4至16、degree 2或3；底层 Chisel Config 支持 maxLogN 1至16。随包 SV 和 DC 摘要只对应典型 n16/d2 配置。

下面命令验证另生成的 n8/d3 配置，包括四类功能测试，不运行长表性能组或对照典型随包 SV：

```bash
python3 tools/verify.py --out build/verify-cubic --max-logn 8 --degree 3 --no-bench --jobs 8
```

## 5. 直接检查随包电路

以下命令仅 lint 随包产品，清单路径相对于仓库根目录：

```bash
verilator --lint-only --assert --top-module GoldilocksSumcheckMle -f rtl/v1.1.0/gl64-n16-t2-d2/files.f
```

仿真构建可能在 build 内生成当前机器的运行期文件路径，它们属于本次本地输出，不是发行内容。公开源码、随包 SV 和压缩包不包含原开发机器路径、凭证或内部日志。
