/*
 * OpenRTL-Header-Begin
 * OpenRTL — Open-source hardware accelerators.
 * Organization: https://github.com/OpenRTL-Lab
 * Website: https://openrtl.top/
 *
 * Project: Goldilocks Sumcheck/MLE Hardware Accelerator
 * Chisel hardware for Sumcheck round coefficients, multilinear folding and evaluation.
 * Repository: https://github.com/OpenRTL-Lab/sumcheck-mle-goldilocks
 * File: operators/sumcheck-mle/src/main/scala/zk/sumcheck/Environment.scala
 *
 * SPDX-FileCopyrightText: 2026 Goldilocks Sumcheck/MLE contributors
 * Copyright applies to original project contributions only.
 * SPDX-License-Identifier: MIT
 * See LICENSE and applicable third-party notices.
 * OpenRTL-Header-End
 */

package zk.sumcheck

import chisel3._
import chisel3.util._
import chisel3.util.experimental.loadMemoryFromFileInline
import java.nio.file.{Files,Paths}
import scala.jdk.CollectionConverters._

case class Row(mode:Int,n:Int,terms:Int,d0:Int,d1:Int,l0:BigInt,l1:BigInt,
  policy:Int,ib:Int,pairs:Int,eb:Int,outputs:Int,rb:Int,status:Int)
object Rows {
  def read(dir:String):Seq[Row] = Files.readAllLines(Paths.get(dir,"meta.tsv")).asScala.toSeq.map { l =>
    val x=l.split("\\s+")
    Row(x(1).toInt,x(2).toInt,x(3).toInt,x(4).toInt,x(5).toInt,BigInt(x(6),16),BigInt(x(7),16),
      x(8).toInt,x(9).toInt,x(10).toInt,x(11).toInt,x(12).toInt,x(13).toInt,x(14).toInt)
  }
}
/** 直接Chisel原生接口测试环境；ROM和计数仅在ENV，未列入产品闭包。 */
class SumcheckMleSimTop(c:Config,dir:String) extends Module {
  val trigger=IO(Input(Bool())); val testMode=IO(Input(UInt(2.W)))
  val done=IO(Output(Bool())); val pass=IO(Output(Bool()))
  val mismatches=IO(Output(UInt(32.W))); val checkedWords=IO(Output(UInt(64.W)))
  val tasksCompleted=IO(Output(UInt(32.W))); val cycles=IO(Output(UInt(64.W)))
  val rows=Rows.read(dir); require(rows.nonEmpty)
  val regressionCount=new String(Files.readAllBytes(Paths.get(dir,"regression-count.txt"))).trim.toInt
  val selectedCount=Mux(testMode===0.U,rows.size.U,regressionCount.U)
  def rom(file:String):Mem[UInt]={
    val path=Paths.get(dir,file).toAbsolutePath.toString
    val depth=math.max(1,Files.readAllLines(Paths.get(path)).size)
    val m=Mem(depth,UInt(64.W)); loadMemoryFromFileInline(m,path); m
  }
  val inputs=rom("input.hex"); val outputs=rom("expected.hex")
  val metas=rom("expected-meta.hex"); val challenges=rom("challenges.hex")
  val running=RegInit(false.B); val index=RegInit(0.U(log2Ceil(rows.size+1).W))
  def field(f:Row=>BigInt,w:Int):UInt=VecInit(rows.map(x=>f(x).U(w.W)))(index)
  val mode=field(x=>BigInt(x.mode),2); val n=field(x=>BigInt(x.n),5)
  val terms=field(x=>BigInt(x.terms),2); val d0=field(x=>BigInt(x.d0),2);val d1=field(x=>BigInt(x.d1),2)
  val policy=field(x=>BigInt(x.policy),3); val ib=field(x=>BigInt(x.ib),32)
  val inputPairs=field(x=>BigInt(x.pairs),17);val eb=field(x=>BigInt(x.eb),32)
  val outputCount=field(x=>BigInt(x.outputs),32);val rb=field(x=>BigInt(x.rb),32)
  val expectedStatus=field(x=>BigInt(x.status),3)
  val accepted=RegInit(false.B);val inputIndex=RegInit(0.U(17.W));val outputIndex=RegInit(0.U(32.W))
  val challengeIndex=RegInit(0.U(5.W));val qSeen=RegInit(false.B)
  val resetTested=RegInit(false.B);val abort=WireDefault(false.B)
  val dut=withReset(reset.asBool || abort){Module(new GoldilocksSumcheckMle(c))}
  val time=RegInit(0.U(64.W));val start=Reg(UInt(64.W));val errors=RegInit(0.U(32.W))
  val checked=RegInit(0.U(64.W));val complete=RegInit(0.U(32.W))
  val active=running && index < selectedCount
  val allow= testMode =/= 1.U || time(2,0)=/=3.U
  val mask=VecInit((0 until c.slots).map(s=>terms>(s/c.maxDegree).U &&
    Mux((s/c.maxDegree).U===0.U,d0,d1)>(s%c.maxDegree).U)).asUInt
  dut.io.task.valid:=active && !accepted && allow
  dut.io.task.bits.tag:=index;dut.io.task.bits.mode:=mode;dut.io.task.bits.n:=n
  dut.io.task.bits.terms:=terms;dut.io.task.bits.degree:=VecInit(Seq(d0,d1))
  dut.io.task.bits.lambda:=VecInit(Seq(field(_.l0,64),field(_.l1,64)))
  dut.io.input.valid:=active && accepted && inputIndex<inputPairs && allow
  dut.io.input.bits.tag:=index;dut.io.input.bits.index:=inputIndex+Mux(policy===3.U && inputIndex===0.U,1.U,0.U)
  dut.io.input.bits.keep:=mask
  dut.io.input.bits.last:=inputIndex===inputPairs-1.U || (policy===2.U && inputIndex===0.U)
  for(s<-0 until c.slots){
    val address=ib+inputIndex*(2*c.slots).U+(2*s).U
    dut.io.input.bits.a(s):=Mux(policy===4.U && inputIndex===0.U && (s==0).B,Field.p.U,inputs(address))
    dut.io.input.bits.b(s):=inputs(address+1.U)
  }
  // 错误挑战测试只提交第一拍：DUT会终止任务，不能再挂起后续MLE轮挑战。
  val challengeCount=Mux(policy===5.U,1.U,Mux(mode===3.U,n,Mux(mode===1.U || mode===2.U,1.U,0.U)))
  // 挑战可以早到，但一旦valid必须一直保持到ready，不随功能暂停节拍撤回。
  dut.io.challenge.valid:=active && accepted && (policy===0.U || policy===5.U) && inputIndex===inputPairs && challengeIndex<challengeCount
  dut.io.challenge.bits.tag:=index;dut.io.challenge.bits.round:=challengeIndex
  dut.io.challenge.bits.r:=Mux(policy===5.U,Field.p.U,challenges(rb+challengeIndex))
  dut.io.result.ready:=active && accepted && (testMode=/=1.U || time(4,0)>=17.U)
  dut.io.completion.ready:=active && accepted && allow
  // Exercise in-flight reset for both short and large verification configurations.
  val abortIndex=Mux(inputPairs>8.U,8.U,inputPairs>>1)
  abort:=testMode===2.U && !resetTested && dut.io.input.fire && inputPairs>1.U && inputIndex===abortIndex && policy===0.U
  when(running){time:=time+1.U}
  when(trigger && !running){running:=true.B}
  when(dut.io.task.fire){accepted:=true.B;start:=time
    printf(p"ACCEPT tag=$index mode=$mode n=$n cycle=$time\n")}
  when(dut.io.input.fire){inputIndex:=inputIndex+1.U
    when(inputIndex===0.U){printf(p"FIRST tag=$index cycle=$time\n")}}
  when(dut.io.challenge.fire){challengeIndex:=challengeIndex+1.U
    when(mode===1.U){assert(qSeen,"challenge accepted before polynomial result handshake")}
    printf(p"CHAL tag=$index round=$challengeIndex cycle=$time\n")}
  val meta=metas(eb+outputIndex)
  val badResult=WireDefault(false.B)
  val mismatchedWords=(0 until c.width).map { s =>
    val expected=outputs((eb+outputIndex)*c.width.U+s.U)
    val corrupted=Mux(testMode===3.U && index===0.U && outputIndex===0.U && (s==0).B,expected^1.U,expected)
    dut.io.result.bits.words(s)=/=corrupted
  }.reduce(_||_)
  // 整个64位元数据均参加比较，避免firtool压缩ROM未使用的中间位后hex布局改变。
  val actualMeta=Wire(UInt(64.W))
  actualMeta:=dut.io.result.bits.kind | (dut.io.result.bits.keep << 2) |
    (dut.io.result.bits.index << 8) | (dut.io.result.bits.last.asUInt << 25)
  badResult:=outputIndex>=outputCount || dut.io.result.bits.tag=/=index || actualMeta=/=meta || mismatchedWords
  when(dut.io.result.fire){
    outputIndex:=outputIndex+1.U;checked:=checked+PopCount(dut.io.result.bits.keep)
    when(badResult){errors:=errors+1.U
      printf(p"MISMATCH tag=$index record=$outputIndex meta=$actualMeta expectedMeta=$meta word0=${dut.io.result.bits.words(0)} expected0=${outputs((eb+outputIndex)*c.width.U)}\n")}
    when(dut.io.result.bits.kind===0.U){qSeen:=true.B}
    when(dut.io.result.bits.last){printf(p"OUTPUT tag=$index kind=${dut.io.result.bits.kind} cycle=$time\n")}
  }
  when(dut.io.completion.fire){
    complete:=complete+1.U
    when(dut.io.completion.bits.tag=/=index || dut.io.completion.bits.status=/=expectedStatus || outputIndex=/=outputCount || inputIndex=/=inputPairs){
      errors:=errors+1.U;printf(p"BAD_COMPLETION tag=$index status=${dut.io.completion.bits.status} outputs=$outputIndex inputs=$inputIndex\n")}
    printf(p"COMPLETE tag=$index status=${dut.io.completion.bits.status} cycle=$time latency=${time-start}\n")
    index:=index+1.U;accepted:=false.B;inputIndex:=0.U;outputIndex:=0.U;challengeIndex:=0.U;qSeen:=false.B
  }
  val held=RegNext(dut.io.result.valid && !dut.io.result.ready,false.B)
  val prior=RegEnable(dut.io.result.bits,dut.io.result.valid)
  when(held && !abort && !RegNext(abort,false.B)){assert(dut.io.result.valid && dut.io.result.bits.asUInt===prior.asUInt,"result changed while stalled")}
  for((channel,name)<-Seq(dut.io.task,dut.io.input,dut.io.challenge).zip(Seq("task","input","challenge"))){
    val blocked=RegNext(channel.valid && !channel.ready,false.B)
    val payload=RegEnable(channel.bits.asUInt,channel.valid)
    when(blocked && !abort && !RegNext(abort,false.B)){
      when(!channel.valid || channel.bits.asUInt=/=payload){
        printf(p"PRODUCER_BAD channel=${name} cycle=$time valid=${channel.valid} ready=${channel.ready} tag=$index accepted=$accepted input=$inputIndex pairs=$inputPairs old=$payload now=${channel.bits.asUInt}\n")
      }
      assert(channel.valid && channel.bits.asUInt===payload,s"ENV $name producer changed while stalled")
    }
  }
  when(abort){resetTested:=true.B;accepted:=false.B;inputIndex:=0.U;outputIndex:=0.U;challengeIndex:=0.U;qSeen:=false.B
    printf(p"RESET_RECOVERY tag=$index cycle=$time\n")}
  done:=running && index===selectedCount;pass:=done && errors===0.U && complete===selectedCount
  mismatches:=errors;checkedWords:=checked;tasksCompleted:=complete;cycles:=time
}

