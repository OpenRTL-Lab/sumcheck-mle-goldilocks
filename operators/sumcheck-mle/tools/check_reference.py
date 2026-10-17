#!/usr/bin/env python3
# OpenRTL-Header-Begin
# OpenRTL — Open-source hardware accelerators.
# Organization: https://github.com/OpenRTL-Lab
# Website: https://openrtl.top/
#
# Project: Goldilocks Sumcheck/MLE Hardware Accelerator
# Chisel hardware for Sumcheck round coefficients, multilinear folding and evaluation.
# Repository: https://github.com/OpenRTL-Lab/sumcheck-mle-goldilocks
# File: operators/sumcheck-mle/tools/check_reference.py
#
# SPDX-FileCopyrightText: 2026 Goldilocks Sumcheck/MLE contributors
# Copyright applies to original project contributions only.
# SPDX-License-Identifier: MIT
# See LICENSE and applicable third-party notices.
# OpenRTL-Header-End

"""大整数独立参考：q用小点求值/拉格朗日插值，MLE用布尔权重定义，不复刻RTL。"""
import json
from pathlib import Path
import sys
P=2**64-2**32+1
def mle(table,point):
    ans=0
    for i,a in enumerate(table):
        w=1
        for k,r in enumerate(point):w=w*(r if i>>k&1 else 1-r)%P
        ans=(ans+a*w)%P
    return ans
def coefficients(tables,degree,weights,n,D):
    vals=[]
    for x in range(max(degree)+1):
        v=0
        for i in range(1<<(n-1)):
            for t,d in enumerate(degree):
                term=weights[t]
                for j in range(d):
                    a,b=tables[t*D+j][2*i:2*i+2];term=term*(a+x*(b-a))%P
                v=(v+term)%P
        vals.append(v)
    c=[0]*len(vals)
    for x,y in enumerate(vals):
        poly=[1];den=1
        for j in range(len(vals)):
            if j==x:continue
            nxt=[0]*(len(poly)+1)
            for k,a in enumerate(poly):nxt[k]=(nxt[k]-j*a)%P;nxt[k+1]=(nxt[k+1]+a)%P
            poly=nxt;den=den*(x-j)%P
        scale=y*pow(den,-1,P)%P
        for k,a in enumerate(poly):c[k]=(c[k]+scale*a)%P
    return c
OP=Path(__file__).resolve().parents[1]
kat=json.loads((OP/'reference/known-answers.json').read_text())
for name in ['nova_mle','nova_constant']:
    k=kat[name];assert mle(k['table'],k['point_lsb_first'])==k['expected']
k=kat['analytic_round'];assert coefficients(k['tables'],k['degrees'],k['lambda'],1,2)==k['coefficients']
if len(sys.argv)>1:
    v=Path(sys.argv[1]);D=int(sys.argv[2]);S=2*D;W=max(S,D+1)
    read=lambda n:[int(x,16) for x in (v/n).read_text().split()]
    data=read('input.hex');out=read('expected.hex');rs=read('challenges.hex');records=0
    for line in (v/'meta.tsv').read_text().splitlines():
        cols=line.split();mode,n,T,d0,d1=[int(x) for x in cols[1:6]]
        degree=[d0,d1][:T];lam=[int(cols[6],16),int(cols[7],16)]
        policy,base,pairs,eb,count,rb,status=[int(x) for x in cols[8:]]
        if policy:continue
        tables=[[0]*(1<<n) for _ in range(S)]
        for i in range(pairs):
            for s in range(S):
                a,b=data[base+(i*S+s)*2:base+(i*S+s)*2+2]
                tables[s][2*i if n else 0]=a
                if n:tables[s][2*i+1]=b
        expected=[]
        if mode in [0,1]:
            c=coefficients(tables,degree,lam,n,D);expected.append(c+[0]*(W-len(c)))
        if mode in [1,2]:
            r=rs[rb]
            expected += [[(a[2*i]+r*(a[2*i+1]-a[2*i]))%P for a in tables] for i in range(pairs)]
        if mode==3:expected.append([mle(a,rs[rb:rb+n]) for a in tables])
        assert len(expected)==count
        for i,values in enumerate(expected):assert out[(eb+i)*W:(eb+i+1)*W]==values,(cols[0],i)
        records+=count
    print(f'PYTHON_REFERENCE pass=1 records={records} KAT=3')
else:print('PYTHON_KAT pass=1 KAT=3')

