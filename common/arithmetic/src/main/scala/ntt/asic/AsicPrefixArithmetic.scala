/*
 * OpenRTL-Header-Begin
 * OpenRTL — Open-source hardware accelerators.
 * Organization: https://github.com/OpenRTL-Lab
 * Website: https://openrtl.top/
 *
 * Project: Goldilocks Sumcheck/MLE Hardware Accelerator
 * Chisel hardware for Sumcheck round coefficients, multilinear folding and evaluation.
 * Repository: https://github.com/OpenRTL-Lab/sumcheck-mle-goldilocks
 * File: common/arithmetic/src/main/scala/ntt/asic/AsicPrefixArithmetic.scala
 *
 * SPDX-FileCopyrightText: 2026 Goldilocks Sumcheck/MLE contributors
 * Copyright applies to original project contributions only.
 * SPDX-License-Identifier: MIT
 * See LICENSE and applicable third-party notices.
 * OpenRTL-Header-End
 */

package ntt.asic

import chisel3._
import chisel3.util._

/** 可移植的倍增前缀进位；每一层合并2^k位，避免线性宽进位链。
  * 结果显式截断到 width 位；调用者为需要进位的运算多保留一位。
  */
object AsicPrefixArithmetic {
  def add(a: UInt, b: UInt, width: Int, carryIn: Bool = false.B): UInt = {
    require(width >= 2)
    val left = Wire(UInt(width.W)); left := a
    val right = Wire(UInt(width.W)); right := b
    val bits = left ^ right
    var propagate: UInt = bits
    var generate: UInt = left & right
    var distance = 1
    while (distance < width) {
      val shiftedGenerate = (generate << distance)(width - 1, 0)
      val shiftedPropagate = Cat(propagate(width - distance - 1, 0),
        ((BigInt(1) << distance) - 1).U(distance.W))
      val nextGenerate = generate | (propagate & shiftedGenerate)
      val nextPropagate = propagate & shiftedPropagate
      generate = nextGenerate
      propagate = nextPropagate
      distance *= 2
    }
    val carries = ((generate << 1) | ((propagate << 1) & Fill(width + 1, carryIn)) |
      carryIn.asUInt)(width - 1, 0)
    bits ^ carries
  }
  def subtract(a: UInt, b: UInt, width: Int): UInt = {
    val right = Wire(UInt(width.W)); right := b
    add(a, ~right, width, true.B)
  }
}

