/*
 * OpenRTL-Header-Begin
 * OpenRTL — Open-source hardware accelerators.
 * Organization: https://github.com/OpenRTL-Lab
 * Website: https://openrtl.top/
 *
 * Project: Goldilocks Sumcheck/MLE Hardware Accelerator
 * Chisel hardware for Sumcheck round coefficients, multilinear folding and evaluation.
 * Repository: https://github.com/OpenRTL-Lab/sumcheck-mle-goldilocks
 * File: operators/sumcheck-mle/src/main/scala/zk/sumcheck/Native.scala
 *
 * SPDX-FileCopyrightText: 2026 Goldilocks Sumcheck/MLE contributors
 * Copyright applies to original project contributions only.
 * SPDX-License-Identifier: MIT
 * See LICENSE and applicable third-party notices.
 * OpenRTL-Header-End
 */

package zk.sumcheck

import chisel3._
import ntt.asic.AsicPrefixArithmetic
import ntt.GoldilocksReference

case class Config(maxLogN: Int = 16, maxDegree: Int = 2) {
  require(maxLogN >= 1 && maxLogN <= 16 && (maxDegree == 2 || maxDegree == 3))
  val slots = 2 * maxDegree
  val width = slots
  val depth = 1 << (maxLogN - 1)
  val addressBits = math.max(1, maxLogN - 1)
  val mulsPerTerm = 4 + (if (maxDegree == 3) 6 else 0) + maxDegree + 1
  val qLatency = if (maxDegree == 2) 61 else 86
  val foldLatency = 31
}
object Field {
  val p = GoldilocksReference.Modulus
  def add(a: UInt, b: UInt): UInt = {
    val x = AsicPrefixArithmetic.add(a, b, 65)
    Mux(x >= p.U(65.W), AsicPrefixArithmetic.subtract(x, p.U(65.W), 65), x)(63, 0)
  }
  def sub(a: UInt, b: UInt): UInt = {
    val x = AsicPrefixArithmetic.subtract(AsicPrefixArithmetic.add(a, p.U(65.W), 65), b, 65)
    Mux(x >= p.U(65.W), AsicPrefixArithmetic.subtract(x, p.U(65.W), 65), x)(63, 0)
  }
}
class TaskData(c: Config) extends Bundle {
  val tag = UInt(32.W); val mode = UInt(2.W); val n = UInt(5.W); val terms = UInt(2.W)
  val degree = Vec(2, UInt(2.W)); val lambda = Vec(2, UInt(64.W))
}
class PairData(c: Config) extends Bundle {
  val tag = UInt(32.W); val index = UInt(17.W); val keep = UInt(c.slots.W); val last = Bool()
  val a = Vec(c.slots, UInt(64.W)); val b = Vec(c.slots, UInt(64.W))
}
class Challenge extends Bundle { val tag = UInt(32.W); val round = UInt(5.W); val r = UInt(64.W) }
class ResultData(c: Config) extends Bundle {
  val tag = UInt(32.W); val kind = UInt(2.W); val index = UInt(17.W)
  val keep = UInt(c.width.W); val last = Bool(); val words = Vec(c.width, UInt(64.W))
}
class Completion extends Bundle { val tag = UInt(32.W); val status = UInt(3.W) }

