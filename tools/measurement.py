# OpenRTL-Header-Begin
# OpenRTL — Open-source hardware accelerators.
# Organization: https://github.com/OpenRTL-Lab
# Website: https://openrtl.top/
#
# Project: Goldilocks Sumcheck/MLE Hardware Accelerator
# Chisel hardware for Sumcheck round coefficients, multilinear folding and evaluation.
# Repository: https://github.com/OpenRTL-Lab/sumcheck-mle-goldilocks
# File: tools/measurement.py
#
# SPDX-FileCopyrightText: 2026 Goldilocks Sumcheck/MLE contributors
# Copyright applies to original project contributions only.
# SPDX-License-Identifier: MIT
# See LICENSE and applicable third-party notices.
# OpenRTL-Header-End

"""Test plans and cycle-based measurements, independent of any private workspace."""
import re

def make_plan(n,D,bench):
    rows=[]
    def add(mode,N,terms=2,degrees=None,weights=(2,3),pattern=0,seed=None,policy=0):
        d=degrees or (D,D);rows.append([mode,N,terms,*d,*weights,pattern,seed or (9137+97*len(rows)),policy])
    # 首任务用于手算KAT与负参考控制；三次配置首任务为独立随机三次。
    add(1,1,pattern=5 if D==2 else 0)
    add(3,0);add(3,3,terms=1,degrees=(1,1),pattern=3);add(3,3,pattern=1)
    for mode in range(4):
        for N in sorted(set([1,3,min(n,8)])):
            add(mode,N)
        add(mode,min(n,4),degrees=(1,D),weights=(0,FieldP-1),pattern=2)
        add(mode,min(n,4),terms=1,degrees=(1,1),pattern=6)
        add(mode,min(n,4),pattern=7)
    add(0,0,policy=1);add(3,n+1,policy=1)
    for mode in range(4):
        for policy in (2,3,4):add(mode,3,policy=policy)
    for mode in (1,2,3):add(mode,3,policy=5)
    count=len(rows);groups=[]
    if bench:
        for mode in range(4):
            first=len(rows)
            for i in range(8):add(mode,n)
            groups.append({'mode':mode,'first':first,'count':8,'n':n})
    return rows,count,groups
FieldP=2**64-2**32+1
def metrics(path,rows,groups,D):
    data={}; currentPhase={}; muls=2*(4+(6 if D==3 else 0)+D+1)
    names=[]
    for t in range(2):
        names += [f'term{t}.first{k}' for k in range(4)]
        if D==3:names += [f'term{t}.cubic{k}' for k in range(6)]
        names += [f'term{t}.lambda{k}' for k in range(D+1)]
    for t in range(2):
        names += [f'term{t}.quad-add']
        names += [f'term{t}.fold-add{k}' for k in range(D)]
        if D==3:names += [f'term{t}.cubic-add{k}' for k in range(2)]
    for k in range(D+1):
        names += [f'coefficient{k}.term-add',f'coefficient{k}.acc-add']
        names += [f'coefficient{k}.tree1.{j}' for j in range(4)]
        names += [f'coefficient{k}.tree2.{j}' for j in range(2)]
        names += [f'coefficient{k}.tree3']
    adds=len(names)-muls; names += [f'table{s}.delta-sub' for s in range(2*D)]
    depths=[20]*muls+[5]*(len(names)-muls);total=len(names)
    for line in path.read_text().splitlines():
        label=line.split(' ',1)[0]
        f={k:int(v) for k,v in re.findall(r'(\w+)=\s*(\d+)',line)}
        if 'tag' not in f:continue
        tag=f['tag'];a=data.setdefault(tag,{'work':[0]*total,'occupied':[0]*total,'last_issue':[-100]*total,
          'work_clocks':0,'round_work':{},'round_occupied':{},'events':[]})
        if label=='WORK':
            bits=f['mask'] | (f['add'] << muls) | (f['sub'] << (muls+adds))
            clk=a['work_clocks'];a['work_clocks']+=1
            for b in range(total):
                issue=(bits>>b)&1;a['work'][b]+=issue
                if issue:a['last_issue'][b]=clk
                a['occupied'][b]+=int(clk-a['last_issue'][b]<depths[b])
            ph=currentPhase.get(tag,'q');counts=a['round_work'].setdefault(ph,[0]*total)
            occupied=a['round_occupied'].setdefault(ph,[0]*total)
            for b in range(total):counts[b]+=(bits>>b)&1;occupied[b]+=int(clk-a['last_issue'][b]<depths[b])
        elif label=='SCAN_BEGIN':currentPhase[tag]='fold'+str(f['round'])
        elif label in ('ACCEPT','FIRST','OUTPUT','CHAL','COMPLETE'):
            a['events'].append({'type':label,**f})
    assert len(data)==len(rows)
    tasks=[]
    for tag,a in sorted(data.items()):
        ev=a['events'];A=next(e['cycle'] for e in ev if e['type']=='ACCEPT')
        C=next(e['cycle'] for e in ev if e['type']=='COMPLETE')
        F=next((e['cycle'] for e in ev if e['type']=='FIRST'),None)
        O=max((e['cycle'] for e in ev if e['type']=='OUTPUT'),default=None)
        q=next((e['cycle'] for e in ev if e['type']=='OUTPUT' and e['kind']==0),None)
        cs=[e for e in ev if e['type']=='CHAL'];phases=[]
        if q is not None:phases.append({'phase':'q','cycles':q-F+1,'first':F,'last':q})
        for k,e in enumerate(cs):
            if rows[tag][-1]!=0:continue
            end=cs[k+1]['cycle']-1 if k+1<len(cs) else O
            phases.append({'phase':'fold'+str(e['round']),'cycles':end-e['cycle']+1,'first':e['cycle'],'last':end})
        for ph in phases:
            counts=a['round_work'].get(ph['phase'],[0]*total)
            occupied=a['round_occupied'].get(ph['phase'],[0]*total)
            ph['units']=[{'name':names[b],'pipeline_depth':depths[b],'ii':1,'issues':counts[b],
              'busy':counts[b],'idle':ph['cycles']-counts[b],'busy_pct':100*counts[b]/ph['cycles'],
              'occupied':occupied[b],'occupied_pct':100*occupied[b]/ph['cycles']} for b in range(total)]
            assert all(u['idle']>=0 for u in ph['units']),ph
        L=C-A
        assert a['work_clocks']==L,(tag,a['work_clocks'],L)
        units=[{'name':names[b],'pipeline_depth':depths[b],'ii':1,'issues':a['work'][b],
          'busy':a['work'][b],'idle':L-a['work'][b],'busy_pct':100*a['work'][b]/L,
          'occupied':a['occupied'][b],'occupied_pct':100*a['occupied'][b]/L} for b in range(total)]
        assert all(u['idle']>=0 for u in units)
        tasks.append({'tag':tag,'mode':rows[tag][0],'n':rows[tag][1],'policy':rows[tag][-1],
          'A':A,'F':F,'O':O,'C':C,'latency_cycles':L,'units':units,'phases':phases})
    throughput=[]
    for g in groups:
        v=tasks[g['first']:g['first']+g['count']];assert len(v)==8
        span=v[-1]['C']-v[0]['C'];intervals=[v[i+1]['C']-v[i]['C'] for i in range(7)]
        throughput.append({**g,'steady_tasks':7,'steady_window_cycles':span,'tasks_per_cycle':7/span,
          'measured_completion_intervals':intervals,'window_tasks':8,'window_cycles':v[-1]['C']-v[0]['A'],
          'single_task_latency_cycles':v[0]['latency_cycles'],'task_pipeline':False,
          'units':[{'name':names[b],'busy':sum(x['units'][b]['busy'] for x in v[1:]),
            'idle':span-sum(x['units'][b]['busy'] for x in v[1:]),
            'busy_pct':100*sum(x['units'][b]['busy'] for x in v[1:])/span,
            'occupied':sum(x['units'][b]['occupied'] for x in v[1:]),
            'occupied_pct':100*sum(x['units'][b]['occupied'] for x in v[1:])/span} for b in range(total)]})
    return {'tasks':tasks,'throughput':throughput,'multiplier_instances':names[:muls],'arithmetic_instances':names,
      'busy_definition':'该逻辑算术站点在本clk进行一项有效运算/发射；20拍模乘/5拍模加减在途不额外重复算busy；非成功帧排空数据不用于发布性能',
      'phase_definition':'q为首输入至q握手含端点；fold为挑战握手至下一挑战前一clk或末输出含端点',
      'latency_definition':'completion握手周期减task握手周期；吞吐独立测7次连续完成间隔'}

