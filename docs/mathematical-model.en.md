<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Sumcheck/MLE Hardware Accelerator
Chisel hardware for Sumcheck round coefficients, multilinear folding and evaluation.
Repository: https://github.com/OpenRTL-Lab/sumcheck-mle-goldilocks
File: docs/mathematical-model.en.md

SPDX-FileCopyrightText: 2026 Goldilocks Sumcheck/MLE contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# Circuit Function and Mathematical Model

[简体中文](mathematical-model.md)

This document precisely specifies the Goldilocks field, table layout, complete single-round polynomial, challenge folding, and multilinear-extension evaluation implemented by the circuit, including its four modes and hand-checkable known answers.

## Field and configuration

All arithmetic is in $\mathbb F_p$, where:

$$
p=2^{64}-2^{32}+1=18446744069414584321.
$$

Field elements use canonical 64-bit integers $0\le x<p$, not Montgomery encoding. Generation parameters are $L=\mathrm{maxLogN}$ and $D=\mathrm{maxDegree}$. The source supports $1\le L\le16$ and $D\in\{2,3\}$; the bundled circuit uses $L=16,D=2$. There are $S=2D$ table slots.

At runtime, the number of variables is $n$, table length is $N=2^n$, and $1\le T\le2$ terms are active. Term $t$ has degree $1\le d_t\le D$ and weight $\lambda_t\in\mathbb F_p$. Slot $s=tD+j$ carries table $A_{t,j}$; only $0\le t<T,\ 0\le j<d_t$ are active. Index bits define variable ordering from least significant to most significant, without bit reversal.

## Complete single-round polynomial

For $n\ge1$, input beat $i$ carries $a_{i,t,j}=A_{t,j}[2i]$ and $b_{i,t,j}=A_{t,j}[2i+1]$, where $0\le i<2^{n-1}$. Define:

$$
q(X)=\sum_{i=0}^{2^{n-1}-1}\sum_{t=0}^{T-1}
\lambda_t\prod_{j=0}^{d_t-1}
\left(a_{i,t,j}+X(b_{i,t,j}-a_{i,t,j})\right)
=\sum_{k=0}^{d}c_kX^k,\qquad d=\max_{t<T}d_t.
$$

The circuit returns every coefficient $c_0,\ldots,c_d$ in increasing degree order, rather than only $q(0),q(1)$. The Boolean-sum identity is:

$$
q(0)+q(1)=\sum_{u=0}^{2^n-1}\sum_{t=0}^{T-1}
\lambda_t\prod_{j=0}^{d_t-1}A_{t,j}[u].
$$

The caller can use the coefficients as Sumcheck round messages and for claim checks; challenges remain caller-supplied.

## Challenge folding and full MLE evaluation

For one challenge $r\in\mathbb F_p$, folding is:

$$
B_{t,j}[i]=A_{t,j}[2i]+r\left(A_{t,j}[2i+1]-A_{t,j}[2i]\right).
$$

Full MLE evaluation eliminates variables in least-significant-bit order using $r_0,\ldots,r_{n-1}$. Let $u_k$ be bit $k$ of index $u$, and define $\chi_0(r)=1-r,\ \chi_1(r)=r$. The final value of each active table is:

$$
\widetilde A_{t,j}(r_0,\ldots,r_{n-1})
=\sum_{u=0}^{2^n-1}A_{t,j}[u]\prod_{k=0}^{n-1}\chi_{u_k}(r_k).
$$

Full MLE returns each active table's evaluation; it does not automatically combine evaluations into a weighted sum of products. For $n=0$, the table has one entry, the empty product is 1, no challenge is needed, and input a contains that entry while b is zero.

## Mode mapping

| mode | Name | Mathematical output |
| --- | --- | --- |
| 0 | ROUND | One beat containing complete coefficients $c_0,\ldots,c_d$ |
| 1 | ROUND_FOLD | Coefficients first; after their handshake, accept $r$ and return every $B_{t,j}[i]$ |
| 2 | FOLD | Only the single-round folded entries $B_{t,j}[i]$ |
| 3 | EVAL | Accept all $n$ challenges and return final MLE values for all active slots |

Modes 0–2 require $1\le n\le L$; EVAL accepts $0\le n\le L$. ROUND_FOLD implements one Sumcheck round and leaves later-round scheduling to the caller. EVAL executes all MLE folding rounds within one task.

## Known answers

For $n=1,T=2,d_0=d_1=2,\lambda=(2,3)$, let the four tables be $(1,3),(2,5),(4,7),(6,8)$:

$$
q(X)=2(1+2X)(2+3X)+3(4+3X)(6+2X)=76+92X+30X^2.
$$

Thus $q(0)+q(1)=274$. With $r=2$, folded values are $(5,8,10,10)$ and $q(2)=380=2\cdot5\cdot8+3\cdot10\cdot10$.

Independent MLE examples include table $(0,0,0,1,0,1,0,2)$ evaluated at $(1,1,1)$ giving 2, and the constant table 5 giving 5 at that point. See [Known answers](../operators/sumcheck-mle/reference/known-answers.json) and [Third-party notices](third-party-notices.en.md). All-one coordinates make these examples independent of variable ordering; random non-Boolean challenges are also checked using independent big-integer Boolean-weight evaluation.

Port encodings, statuses, and handshake ordering are specified in [Native interface](interface.en.md).
