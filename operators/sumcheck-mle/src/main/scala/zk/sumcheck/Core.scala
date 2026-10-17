/*
 * OpenRTL-Header-Begin
 * OpenRTL — Open-source hardware accelerators.
 * Organization: https://github.com/OpenRTL-Lab
 * Website: https://openrtl.top/
 *
 * Project: Goldilocks Sumcheck/MLE Hardware Accelerator
 * Chisel hardware for Sumcheck round coefficients, multilinear folding and evaluation.
 * Repository: https://github.com/OpenRTL-Lab/sumcheck-mle-goldilocks
 * File: operators/sumcheck-mle/src/main/scala/zk/sumcheck/Core.scala
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

/** 单任务扫描核；算术II1，8槽交织反馈，真实单读单写存储。 */
class GoldilocksSumcheckMle(c: Config) extends Module {
  val io = IO(new Bundle {
    val task = Flipped(Decoupled(new TaskData(c)))
    val input = Flipped(Decoupled(new PairData(c)))
    val challenge = Flipped(Decoupled(new Challenge))
    val result = Decoupled(new ResultData(c))
    val completion = Decoupled(new Completion)
  })
  val idle :: input :: inputDrain :: qDrain :: qReduce :: qOut :: challenge :: scan :: drain :: finalOut :: done :: Nil = Enum(11)
  val state = RegInit(idle)
  val task = Reg(new TaskData(c)); val mask = Reg(UInt(c.slots.W)); val degree = Reg(UInt(2.W))
  val status = RegInit(0.U(3.W)); val inputBad = RegInit(false.B)
  val count = RegInit(0.U(17.W)); val pairs = Reg(UInt(17.W)); val remaining = Reg(UInt(5.W))
  val round = RegInit(0.U(5.W)); val r = Reg(UInt(64.W))
  val issue = RegInit(0.U(17.W)); val qCount = RegInit(0.U(17.W))
  val credit = RegInit(0.U(7.W)); val outputLastSeen = RegInit(false.B)
  val stripes = RegInit(VecInit(Seq.fill(c.maxDegree+1)(VecInit(Seq.fill(8)(0.U(64.W))))))
  val accum = RegInit(VecInit(Seq.fill(c.maxDegree+1)(0.U(64.W))))
  val finals = Reg(Vec(c.slots, UInt(64.W)))
  val even = Seq.fill(c.slots)(SyncReadMem(c.depth, UInt(64.W)))
  val odd = Seq.fill(c.slots)(SyncReadMem(c.depth, UInt(64.W)))
  val fifo = Module(new Queue(new ResultData(c),64))
  val hasQ = task.mode <= 1.U; val emitsFold = task.mode === 1.U || task.mode === 2.U
  val captured = RegEnable(io.input.bits,io.input.fire)
  val capturedIndex = RegEnable(count,io.input.fire)
  val capturedValid = RegNext(io.input.fire,false.B)
  val memIssue = state === scan && issue < pairs && (!emitsFold || credit < 64.U)
  val memValid = RegNext(memIssue,false.B)
  val memIndex = RegEnable(issue,memIssue)
  val ma = even.map(_.read(issue(c.addressBits-1,0),memIssue))
  val mb = odd.map(_.read(issue(c.addressBits-1,0),memIssue))
  val qLaunch = io.input.fire && hasQ
  val foldLaunch = memValid
  val qPrep = ShiftRegister(qLaunch,6,false.B,true.B)
  val fPrep = ShiftRegister(foldLaunch,6,false.B,true.B)
  val baseLatency = 31
  val polyLatency = if(c.maxDegree==3) 56 else 31
  val weightLaunch = ShiftRegister(qLaunch,polyLatency,false.B,true.B)
  val mergeLaunch = ShiftRegister(qLaunch,polyLatency+20,false.B,true.B)
  val feedbackLaunch = ShiftRegister(qLaunch,c.qLatency-5,false.B,true.B)
  val qValid = ShiftRegister(qLaunch,c.qLatency,false.B,true.B)
  val feedbackBank = ShiftRegister(count(2,0),c.qLatency-5)
  val returnBank = ShiftRegister(feedbackBank,5)
  val foldIndex = ShiftRegister(memIndex,c.foldLatency)
  val foldValid = ShiftRegister(foldLaunch,c.foldLatency,false.B,true.B)
  val reduceLaunch = state === qReduce
  val reduceValid = ShiftRegister(reduceLaunch,15,false.B,true.B)
  val workBits = collection.mutable.ArrayBuffer[Bool]()
  val addBits = collection.mutable.ArrayBuffer[Bool]()
  val subBits = collection.mutable.ArrayBuffer[Bool]()
  val weighted = collection.mutable.ArrayBuffer[Seq[UInt]]()
  val folded = Wire(Vec(c.slots,UInt(64.W)))
  val rawA = (0 until c.slots).map(s=>RegNext(Mux(foldLaunch,ma(s),io.input.bits.a(s))))
  val rawB = (0 until c.slots).map(s=>RegNext(Mux(foldLaunch,mb(s),io.input.bits.b(s))))
  val delta = (0 until c.slots).map(s=>Pipe.modSub(rawB(s),rawA(s)))
  val aPrepared = rawA.map(x=>ShiftRegister(x,5))
  for(t<-0 until 2) {
    val a = (0 until c.maxDegree).map(j=>aPrepared(t*c.maxDegree+j))
    val d = (0 until c.maxDegree).map(j=>delta(t*c.maxDegree+j))
    val operands = Seq((a(0),a(1)),(a(0),d(1)),(d(0),a(1)),(d(0),d(1)))
    val first = (0 until 4).map { k=>
      val m = Module(new SumcheckGoldilocksMul)
      val qUse = qPrep && task.terms>t.U && task.degree(t)>=2.U
      val fUse = fPrep && (if(k<c.maxDegree) mask(t*c.maxDegree+k) else false.B)
      m.io.a := Mux(fUse,r,Mux(qUse,operands(k)._1,0.U))
      m.io.b := Mux(fUse,d(math.min(k,c.maxDegree-1)),Mux(qUse,operands(k)._2,0.U))
      workBits += (qUse || fUse); m.io.product
    }
    val q26 = ShiftRegister(qLaunch,26,false.B,true.B)
    val f26 = ShiftRegister(foldLaunch,26,false.B,true.B)
    val quad = Seq(ShiftRegister(first(0),5),Pipe.modAdd(first(1),first(2)),ShiftRegister(first(3),5))
    addBits += (q26 && task.terms>t.U && task.degree(t)>=2.U)
    for(j<-0 until c.maxDegree) {
      folded(t*c.maxDegree+j) := Mux(mask(t*c.maxDegree+j),Pipe.modAdd(ShiftRegister(a(j),20),first(j)),0.U)
      addBits += (f26 && mask(t*c.maxDegree+j))
    }
    val linear = Seq(ShiftRegister(a(0),25),ShiftRegister(d(0),25),0.U(64.W))
    val base = (0 until 3).map(k=>Mux(task.degree(t)===1.U,linear(k),quad(k)))
    val poly = if(c.maxDegree==3) {
      val a2 = ShiftRegister(a(2),25); val d2 = ShiftRegister(d(2),25)
      val op = Seq((base(0),a2),(base(0),d2),(base(1),a2),(base(1),d2),(base(2),a2),(base(2),d2))
      val six = op.map { case(x,y)=>
        val m = Module(new SumcheckGoldilocksMul)
        val use = ShiftRegister(qLaunch,baseLatency,false.B,true.B) && task.terms>t.U && task.degree(t)===3.U
        m.io.a := Mux(use,x,0.U); m.io.b := Mux(use,y,0.U); workBits += use; m.io.product
      }
      val cubic = Seq(ShiftRegister(six(0),5),Pipe.modAdd(six(1),six(2)),Pipe.modAdd(six(3),six(4)),ShiftRegister(six(5),5))
      for(k<-0 until 2){addBits += (ShiftRegister(qLaunch,51,false.B,true.B) && task.terms>t.U && task.degree(t)===3.U)}
      (0 until 4).map(k=>Mux(task.degree(t)===3.U,cubic(k),if(k<3) ShiftRegister(base(k),25) else 0.U(64.W)))
    } else base
    weighted += (0 to c.maxDegree).map { k=>
      val m = Module(new SumcheckGoldilocksMul)
      val use = weightLaunch && task.terms>t.U && task.degree(t)>=k.U
      m.io.a := Mux(use,poly(k),0.U); m.io.b := Mux(use,task.lambda(t),0.U)
      workBits += use; m.io.product
    }
  }
  for(k<-0 to c.maxDegree) {
    val merged = Pipe.modAdd(weighted(0)(k),weighted(1)(k))
    val feedback = Pipe.modAdd(stripes(k)(feedbackBank),merged)
    when(qValid){stripes(k)(returnBank):=feedback}
    addBits += (mergeLaunch && degree>=k.U)
    addBits += (feedbackLaunch && degree>=k.U)
    val level1 = (0 until 4).map(j=>Pipe.modAdd(stripes(k)(2*j),stripes(k)(2*j+1)))
    val level2 = (0 until 2).map(j=>Pipe.modAdd(level1(2*j),level1(2*j+1)))
    val result = Pipe.modAdd(level2(0),level2(1))
    when(reduceValid){accum(k):=result}
    for(j<-0 until 4){addBits += (reduceLaunch && degree>=k.U)}
    for(j<-0 until 2){addBits += (ShiftRegister(reduceLaunch,5,false.B,true.B) && degree>=k.U)}
    addBits += (ShiftRegister(reduceLaunch,10,false.B,true.B) && degree>=k.U)
  }
  for(d<-1 to 4){
    val previousValid = ShiftRegister(feedbackLaunch,d,false.B,true.B)
    val previousBank = ShiftRegister(feedbackBank,d)
    when(feedbackLaunch && previousValid){assert(feedbackBank =/= previousBank,"accumulator feedback hazard")}
  }
  when(qValid) {
    qCount := qCount+1.U
    when(qCount===pairs-1.U && state===qDrain){state:=Mux(inputBad,done,qReduce)}
  }
  when(state===qReduce){state:=qDrain}
  when(reduceValid){state:=qOut}
  chisel3.layer.block(chisel3.layers.Verification) {
    for(s<-0 until c.slots){subBits += (ShiftRegister(qLaunch,1,false.B,true.B) && mask(s)) || (ShiftRegister(foldLaunch,1,false.B,true.B) && mask(s))}
    val work = VecInit(workBits.toSeq).asUInt
    val addWork = VecInit(addBits.toSeq).asUInt; val subWork = VecInit(subBits.toSeq).asUInt
    when(state=/=idle){printf(p"WORK tag=${task.tag} round=$round mask=$work add=$addWork sub=$subWork\n")}
  }
  io.task.ready := state===idle; io.input.ready := state===input; io.challenge.ready := state===challenge
  io.completion.valid := state===done; io.completion.bits.tag:=task.tag; io.completion.bits.status:=status
  fifo.io.enq.valid:=foldValid && emitsFold; fifo.io.enq.bits:=0.U.asTypeOf(new ResultData(c))
  fifo.io.enq.bits.tag:=task.tag; fifo.io.enq.bits.kind:=1.U; fifo.io.enq.bits.index:=foldIndex
  fifo.io.enq.bits.keep:=mask; fifo.io.enq.bits.last:=foldIndex===pairs-1.U; fifo.io.enq.bits.words:=folded
  fifo.io.deq.ready:=io.result.ready && state=/=qOut && state=/=finalOut
  io.result.valid:=fifo.io.deq.valid; io.result.bits:=fifo.io.deq.bits
  when(state===qOut || state===finalOut) {
    io.result.valid:=true.B; io.result.bits:=0.U.asTypeOf(new ResultData(c))
    io.result.bits.tag:=task.tag; io.result.bits.last:=true.B; io.result.bits.kind:=Mux(state===qOut,0.U,2.U)
    io.result.bits.keep:=Mux(state===qOut,((1.U(c.width.W)<<(degree+&1.U))-1.U)(c.width-1,0),mask)
    for(k<-0 until c.width){io.result.bits.words(k):=Mux(state===qOut,if(k<=c.maxDegree) accum(k) else 0.U,finals(k))}
  }
  when(memIssue){issue:=issue+1.U;when(issue===pairs-1.U){state:=drain}}
  when((memIssue && emitsFold)=/=fifo.io.deq.fire){credit:=Mux(memIssue && emitsFold,credit+1.U,credit-1.U)}
  when(fifo.io.enq.valid){assert(fifo.io.enq.ready,"reserved output credit exhausted")}
  when(fifo.io.deq.fire && fifo.io.deq.bits.last){outputLastSeen:=true.B}
  for(s<-0 until c.slots) {
    val load = capturedValid && task.mode=/=0.U && mask(s)
    val fold = foldValid && mask(s)
    assert(!(load && fold),"load/fold memory write collision")
    val address = Mux(load,capturedIndex(c.addressBits-1,0),(foldIndex>>1)(c.addressBits-1,0))
    when(load || (fold && !foldIndex(0))){even(s).write(address,Mux(load,captured.a(s),folded(s)))}
    when((load && task.n=/=0.U) || (fold && foldIndex(0))){odd(s).write(address,Mux(load,captured.b(s),folded(s)))}
    when(fold){finals(s):=folded(s)}
  }
  when(foldValid && foldIndex===pairs-1.U) {
    printf(p"SCAN_END tag=${task.tag} round=$round\n")
    when(task.mode===3.U){remaining:=remaining-1.U;round:=round+1.U
      when(remaining===1.U){state:=finalOut}.otherwise{pairs:=pairs>>1;state:=challenge}}
  }
  when(state===drain && emitsFold && outputLastSeen){state:=done}
  when(io.task.fire) {
    task:=io.task.bits;status:=0.U;inputBad:=false.B;count:=0.U;qCount:=0.U
    issue:=0.U;credit:=0.U;outputLastSeen:=false.B;round:=0.U;remaining:=io.task.bits.n
    pairs:=Mux(io.task.bits.n===0.U,1.U,1.U(17.W)<<(io.task.bits.n-1.U))
    val active = (0 until c.slots).map(s=>io.task.bits.terms>(s/c.maxDegree).U && io.task.bits.degree(s/c.maxDegree)>(s%c.maxDegree).U)
    mask:=VecInit(active).asUInt
    degree:=Mux(io.task.bits.terms===2.U && io.task.bits.degree(1)>io.task.bits.degree(0),io.task.bits.degree(1),io.task.bits.degree(0))
    val bad = io.task.bits.n>c.maxLogN.U || (io.task.bits.n===0.U && io.task.bits.mode=/=3.U) || io.task.bits.terms<1.U || io.task.bits.terms>2.U ||
      (0 until 2).map(t=>io.task.bits.terms>t.U && (io.task.bits.degree(t)<1.U || io.task.bits.degree(t)>c.maxDegree.U || io.task.bits.lambda(t)>=Field.p.U)).reduce(_||_)
    state:=Mux(bad,done,input);when(bad){status:=1.U}
    for(k<-0 to c.maxDegree){accum(k):=0.U;for(b<-0 until 8){stripes(k)(b):=0.U}}
    for(s<-0 until c.slots){finals(s):=0.U}
  }
  when(io.input.fire){count:=count+1.U;when(count===pairs-1.U){state:=inputDrain}}
  when(capturedValid) {
    val bad = captured.tag=/=task.tag || captured.index=/=capturedIndex || captured.keep=/=mask || captured.last=/=(capturedIndex===pairs-1.U) ||
      (0 until c.slots).map(s=>Mux(mask(s),captured.a(s)>=Field.p.U || captured.b(s)>=Field.p.U || (task.n===0.U && captured.b(s)=/=0.U),captured.a(s)=/=0.U || captured.b(s)=/=0.U)).reduce(_||_)
    inputBad:=inputBad || bad
    when(task.n===0.U){for(s<-0 until c.slots){finals(s):=captured.a(s)}}
    when(capturedIndex===pairs-1.U) {
      when(inputBad || bad){status:=2.U;state:=Mux(hasQ,qDrain,done)}
        .elsewhen(hasQ){state:=qDrain}.elsewhen(task.n===0.U){state:=finalOut}.otherwise{state:=challenge}
    }
  }
  when(state===qOut && io.result.fire){state:=Mux(task.mode===0.U,done,challenge)}
  when(state===finalOut && io.result.fire){state:=done}
  when(io.challenge.fire) {
    when(io.challenge.bits.tag=/=task.tag || io.challenge.bits.round=/=round || io.challenge.bits.r>=Field.p.U){status:=2.U;state:=done}
      .otherwise{r:=io.challenge.bits.r;issue:=0.U;outputLastSeen:=false.B;state:=scan;printf(p"SCAN_BEGIN tag=${task.tag} round=$round\n")}
  }
  when(io.completion.fire){state:=idle}
}

