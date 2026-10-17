/*
 * OpenRTL-Header-Begin
 * OpenRTL — Open-source hardware accelerators.
 * Organization: https://github.com/OpenRTL-Lab
 * Website: https://openrtl.top/
 *
 * Project: Goldilocks Sumcheck/MLE Hardware Accelerator
 * Chisel hardware for Sumcheck round coefficients, multilinear folding and evaluation.
 * Repository: https://github.com/OpenRTL-Lab/sumcheck-mle-goldilocks
 * File: operators/sumcheck-mle/sim/driver.cpp
 *
 * SPDX-FileCopyrightText: 2026 Goldilocks Sumcheck/MLE contributors
 * Copyright applies to original project contributions only.
 * SPDX-License-Identifier: MIT
 * See LICENSE and applicable third-party notices.
 * OpenRTL-Header-End
 */

#include "VSumcheckMleSimTop.h"
#include "verilated.h"
#include <iostream>
#include <cstdlib>
// 仅时钟/复位/触发/模式及结果读取；不接触原生接口，不做参考运算。
int main(int argc,char**argv){
  VerilatedContext ctx;ctx.commandArgs(argc,argv);VSumcheckMleSimTop top{&ctx};
  auto tick=[&](){top.clock=0;top.eval();ctx.timeInc(1);top.clock=1;top.eval();ctx.timeInc(1);};
  top.reset=1;top.trigger=0;top.testMode=argc>1?std::atoi(argv[1]):0;
  for(int i=0;i<8;i++)tick();top.reset=0;tick();top.trigger=1;tick();top.trigger=0;
  unsigned long long limit=50000000;
  while(!top.done && !ctx.gotFinish() && limit--)tick();
  bool ok=top.done&&top.pass&&top.mismatches==0;
  std::cout<<"RESULT pass="<<ok<<" done="<<unsigned(top.done)<<" mismatches="<<top.mismatches
    <<" checkedWords="<<top.checkedWords<<" tasks="<<top.tasksCompleted<<" cycles="<<top.cycles<<'\n';
  top.final();return ok?0:1;
}

