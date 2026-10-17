<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Sumcheck/MLE Hardware Accelerator
Chisel hardware for Sumcheck round coefficients, multilinear folding and evaluation.
Repository: https://github.com/OpenRTL-Lab/sumcheck-mle-goldilocks
File: docs/publication-checklist.md

SPDX-FileCopyrightText: 2026 Goldilocks Sumcheck/MLE contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# 发行内容说明

[English](publication-checklist.en.md)

本文件汇总仓库交付的设计、验证、指标和许可内容及检查口径，帮助使用者识别独立可复现部分与以摘要形式提供的综合信息。

## 设计与生成

- Chisel设计、必要公共算术、生成入口及固定构建依赖完整提供。
- 随包典型产品的8个SV模块与文件清单完整，顶层及端口见 [原生接口](interface.md)。
- 生成尺寸可配置，数学身份见 [数学模型](mathematical-model.md)，结构见 [微架构](architecture.md)。
- 产品闭包不含验证ROM、性能追踪层或FPGA专用原语；存储保留通用同步1R1W行为。

## 功能与性能

- C参考和独立Python参考交叉核对系数、折叠和完整MLE。
- 生成/随包产品分别通过功能、协议暂停、背压与在途复位恢复。
- 故意错误参考能触发明确失败；生成产品与随包产品逐模块一致。
- 理想接口性能重复3次一致，任务延迟、连续任务吞吐及逐单元忙闲都有定义；见 [性能指标](performance.md)。
- 独立复现通过，使用方法见 [快速复现](reproduction.md)。

## 文件与许可

- 本项目 Markdown 的中英文版本分别成文件；默认 README.md 为英文，README.zh-CN.md 为完整中文，其他文档也提供独立译文和双向语言链接；正文索引对应同语言文档。
- 索引使用相对链接，同一语言中的同一目标文档使用一致名称。
- 发行载荷排除原开发机器路径、密钥、内部身份、签名、内部哈希/提交追溯值、缓存和构建日志。
- Git自身与托管平台正常生成的提交标识及下载完整性元数据是平台元数据，不是原开发机器或凭证信息。
- 使用 [MIT 许可证](../LICENSE)，第三方来源见 [第三方声明](third-party-notices.md)。
- 综合只提供 [综合摘要](synthesis-summary.md)，不分发原始DC资料。
- Release附件为完整仓库源码包，不含Git目录、缓存、构建产物或内部过程文件。
