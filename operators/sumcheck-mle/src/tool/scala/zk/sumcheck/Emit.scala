/*
 * OpenRTL-Header-Begin
 * OpenRTL — Open-source hardware accelerators.
 * Organization: https://github.com/OpenRTL-Lab
 * Website: https://openrtl.top/
 *
 * Project: Goldilocks Sumcheck/MLE Hardware Accelerator
 * Chisel hardware for Sumcheck round coefficients, multilinear folding and evaluation.
 * Repository: https://github.com/OpenRTL-Lab/sumcheck-mle-goldilocks
 * File: operators/sumcheck-mle/src/tool/scala/zk/sumcheck/Emit.scala
 *
 * SPDX-FileCopyrightText: 2026 Goldilocks Sumcheck/MLE contributors
 * Copyright applies to original project contributions only.
 * SPDX-License-Identifier: MIT
 * See LICENSE and applicable third-party notices.
 * OpenRTL-Header-End
 */

package zk.sumcheck

import circt.stage.ChiselStage
import java.nio.file.{Files,Paths}

object Emit extends App {
  require(args.length==4,"Emit build/ID/rtl maxLogN maxDegree vectorDirectory")
  val target=Paths.get(args(0)).normalize
  require(!target.isAbsolute && target.startsWith(Paths.get("build")) && target.getFileName.toString=="rtl" && !Files.exists(target))
  val c=Config(args(1).toInt,args(2).toInt)
  Files.createDirectories(target)
  ChiselStage.emitSystemVerilog(new SumcheckMleSimTop(c,args(3)),
    firtoolOpts=Array("-O=release","-disable-all-randomization","--lowering-options=disallowLocalVariables","-split-verilog",s"-o=$target"))
}

