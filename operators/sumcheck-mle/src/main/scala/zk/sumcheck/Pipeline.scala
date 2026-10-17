/*
 * OpenRTL-Header-Begin
 * OpenRTL — Open-source hardware accelerators.
 * Organization: https://github.com/OpenRTL-Lab
 * Website: https://openrtl.top/
 *
 * Project: Goldilocks Sumcheck/MLE Hardware Accelerator
 * Chisel hardware for Sumcheck round coefficients, multilinear folding and evaluation.
 * Repository: https://github.com/OpenRTL-Lab/sumcheck-mle-goldilocks
 * File: operators/sumcheck-mle/src/main/scala/zk/sumcheck/Pipeline.scala
 *
 * SPDX-FileCopyrightText: 2026 Goldilocks Sumcheck/MLE contributors
 * Copyright applies to original project contributions only.
 * SPDX-License-Identifier: MIT
 * See LICENSE and applicable third-party notices.
 * OpenRTL-Header-End
 */

package zk.sumcheck

import chisel3._
import chisel3.util.{Pipe => _, _}
import ntt.asic.AsicPrefixArithmetic

/** 两拍分块进位选择：每块最多16位；跨块进位仅在第二拍。 */
class SumcheckSegmentedAdd(width: Int) extends Module {
  val io = IO(new Bundle {
    val a = Input(UInt(width.W)); val b = Input(UInt(width.W))
    val carry = Input(Bool()); val sum = Output(UInt(width.W))
  })
  val cin = RegNext(io.carry)
  var carry: Bool = cin
  val chunks = (0 until width by 16).map { low =>
    val n = math.min(16, width-low)
    val a = io.a(low+n-1,low); val b = io.b(low+n-1,low)
    val sum0 = RegNext(AsicPrefixArithmetic.add(a,b,n+1))
    val sum1 = RegNext(AsicPrefixArithmetic.add(a,b,n+1,true.B))
    val propagate = RegNext((a ^ b).andR)
    val selected = Mux(carry,sum1(n-1,0),sum0(n-1,0))
    carry = sum0(n) || (propagate && carry)
    selected
  }
  io.sum := RegNext(Cat(chunks.reverse))
}

object Pipe {
  val MulLatency = 20
  val ModLatency = 5
  def add(a: UInt,b: UInt,width: Int,carry: Bool = false.B): UInt = {
    val m = Module(new SumcheckSegmentedAdd(width))
    m.io.a := a; m.io.b := b; m.io.carry := carry; m.io.sum
  }
  def sub(a: UInt,b: UInt,width: Int): UInt = {
    val right = Wire(UInt(width.W)); right := b
    add(a,~right,width,true.B)
  }
  // x非负且小于3p；一遍规范调整固定3拍。
  def correct(x: UInt,width: Int): UInt = {
    val difference = sub(x,Field.p.U(width.W),width)
    val original = ShiftRegister(x,2)
    RegNext(Mux(difference(width-1),original,difference))
  }
  def modAdd(a: UInt,b: UInt): UInt = correct(add(a,b,65),65)(63,0)
  def modSub(a: UInt,b: UInt): UInt = {
    val raw = sub(a,b,65)
    val adjusted = add(raw,Field.p.U(65.W),65)
    val result = RegNext(Mux(ShiftRegister(raw(64),2),adjusted,ShiftRegister(raw,2)))
    result(63,0)
  }
}

/** 二十拍II1的标准单元模乘；每个宽重组/约减均有局部/块间两道寄存。 */
class SumcheckGoldilocksMul extends Module {
  val io = IO(new Bundle {
    val a = Input(UInt(64.W)); val b = Input(UInt(64.W)); val product = Output(UInt(64.W))
  })
  val a = RegNext(io.a); val b = RegNext(io.b)
  val p0 = RegNext(a(31,0)*b(31,0)); val p1 = RegNext(a(31,0)*b(63,32))
  val p2 = RegNext(a(63,32)*b(31,0)); val p3 = RegNext(a(63,32)*b(63,32))
  val cross = Pipe.add(p1,p2,65)
  val outerLow = ShiftRegister(p0,2); val outerHigh = ShiftRegister(p3,2)
  val low = Pipe.add(outerLow,Cat(cross(31,0),0.U(32.W)),65)
  val crossHigh = ShiftRegister(cross(64,32),2); val highDelayed = ShiftRegister(outerHigh,2)
  val productHigh = Pipe.add(highDelayed,crossHigh,65,low(64))
  val productLow = ShiftRegister(low(63,0),2)
  val highSum = Pipe.add(productLow(63,32),productHigh(31,0),33)
  val subtrahend = Pipe.add(productHigh(31,0),productHigh(63,32),33)
  val t0 = ShiftRegister(productLow(31,0),2)
  val positive = Pipe.add(Cat(0.U(1.W),highSum,t0),Field.p.U(66.W),66)
  val candidate = Pipe.sub(positive,Cat(0.U(33.W),ShiftRegister(subtrahend,2)),66)
  io.product := Pipe.correct(Pipe.correct(candidate,66),66)(63,0)
}

