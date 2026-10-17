/*
 * OpenRTL-Header-Begin
 * OpenRTL — Open-source hardware accelerators.
 * Organization: https://github.com/OpenRTL-Lab
 * Website: https://openrtl.top/
 *
 * Project: Goldilocks Sumcheck/MLE Hardware Accelerator
 * Chisel hardware for Sumcheck round coefficients, multilinear folding and evaluation.
 * Repository: https://github.com/OpenRTL-Lab/sumcheck-mle-goldilocks
 * File: operators/sumcheck-mle/cmodel/reference.c
 *
 * SPDX-FileCopyrightText: 2026 Goldilocks Sumcheck/MLE contributors
 * Copyright applies to original project contributions only.
 * SPDX-License-Identifier: MIT
 * See LICENSE and applicable third-party notices.
 * OpenRTL-Header-End
 */

/* 独立离线C金标准：通用多项式卷积、布尔点枚举与原地逐轮折叠；不驱动DUT。 */
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <inttypes.h>
#include <assert.h>
static const uint64_t P = UINT64_C(18446744069414584321);
static uint64_t add(uint64_t a,uint64_t b){return (uint64_t)(((__uint128_t)a+b)%P);}
static uint64_t sub(uint64_t a,uint64_t b){return (uint64_t)(((__uint128_t)a+P-b)%P);}
static uint64_t mul(uint64_t a,uint64_t b){return (uint64_t)((__uint128_t)a*b%P);}
static uint64_t rng(uint64_t*s){*s^=*s<<13;*s^=*s>>7;*s^=*s<<17;return *s%P;}
static uint64_t tables[6][65536];
static FILE *in,*exp,*em,*ch,*meta;
static uint64_t inpos,expos,rpos;
static void word(FILE*f,uint64_t a){assert(fprintf(f,"%016" PRIx64 "\n",a)>0);}
static void record(int kind,unsigned idx,unsigned keep,int last,uint64_t*v,int width){
 word(em,(uint64_t)kind|((uint64_t)keep<<2)|((uint64_t)idx<<8)|((uint64_t)last<<25));
 for(int i=0;i<width;i++){word(exp,v[i]);}
 expos++;
}
static void qcoeff(int n,int T,int D,int*deg,uint64_t*lam,uint64_t*q){
 for(int k=0;k<=D;k++)q[k]=0;
 for(int i=0;i<(1<<(n-1));i++)for(int t=0;t<T;t++){
  uint64_t c[4]={1,0,0,0};int d=0;
  for(int j=0;j<deg[t];j++){
   uint64_t a=tables[t*D+j][2*i],b=sub(tables[t*D+j][2*i+1],a),next[4]={0,0,0,0};
   for(int k=0;k<=d;k++){next[k]=add(next[k],mul(c[k],a));next[k+1]=add(next[k+1],mul(c[k],b));}
   memcpy(c,next,sizeof(c));d++;
  }
  for(int k=0;k<=d;k++)q[k]=add(q[k],mul(lam[t],c[k]));
 }
 uint64_t qs=add(q[0],q[0]),bs=0;
 for(int k=1;k<=D;k++)qs=add(qs,q[k]);
 for(int i=0;i<(1<<n);i++)for(int t=0;t<T;t++){
  uint64_t v=lam[t];for(int j=0;j<deg[t];j++)v=mul(v,tables[t*D+j][i]);bs=add(bs,v);
 }
 assert(qs==bs);
}
static void fold(int length,int slots,unsigned mask,uint64_t r){
 for(int s=0;s<slots;s++)if(mask&(1u<<s))for(int i=0;i<length/2;i++)
  tables[s][i]=add(tables[s][2*i],mul(r,sub(tables[s][2*i+1],tables[s][2*i])));
}
static void kat(void){
 uint64_t a[4][2]={{1,3},{2,5},{4,7},{6,8}},q[4],l[2]={2,3};int d[2]={2,2};
 for(int s=0;s<4;s++){memcpy(tables[s],a[s],sizeof(a[s]));}
 qcoeff(1,2,2,d,l,q);
 assert(q[0]==76&&q[1]==92&&q[2]==30);fold(2,4,15,2);
 assert(tables[0][0]==5&&tables[1][0]==8&&tables[2][0]==10&&tables[3][0]==10);
 assert(add(mul(2,mul(5,8)),mul(3,mul(10,10)))==380);
 uint64_t z[8]={0,0,0,1,0,1,0,2};memcpy(tables[0],z,sizeof(z));
 for(int len=8;len>1;len/=2){fold(len,1,1,1);}
 assert(tables[0][0]==2);
 for(int i=0;i<8;i++)tables[0][i]=5;
 for(int len=8;len>1;len/=2){fold(len,1,1,1);}
 assert(tables[0][0]==5);
 puts("REFERENCE_KAT pass=1 nova=2 analytic=1");
}
static FILE*openfile(const char*dir,const char*name){char p[4096];snprintf(p,sizeof(p),"%s/%s",dir,name);FILE*f=fopen(p,"wx");assert(f);return f;}
int main(int argc,char**argv){
 kat();if(argc==1)return 0;assert(argc==4);int D=atoi(argv[3]);assert(D==2||D==3);
 int slots=2*D,width=slots>D+1?slots:D+1;
 FILE*plan=fopen(argv[2],"r");assert(plan);
 in=openfile(argv[1],"input.hex");exp=openfile(argv[1],"expected.hex");em=openfile(argv[1],"expected-meta.hex");
 ch=openfile(argv[1],"challenges.hex");meta=openfile(argv[1],"meta.tsv");
 int mode,n,T,deg[2],pattern,policy;uint64_t lam[2],seed;unsigned task=0;
 while(fscanf(plan,"%d %d %d %d %d %" SCNu64 " %" SCNu64 " %d %" SCNu64 " %d",&mode,&n,&T,&deg[0],&deg[1],&lam[0],&lam[1],&pattern,&seed,&policy)==10){
  int badparam=policy==1;
  unsigned mask=0;for(int t=0;t<T;t++)for(int j=0;j<deg[t];j++)mask|=1u<<(t*D+j);
  int pairs=badparam?0:(n?1<<(n-1):1),N=badparam?0:1<<n;
  uint64_t ib=inpos,eb=expos,rb=rpos;
  if(!badparam){
   for(int s=0;s<slots;s++)for(int i=0;i<N;i++){
    uint64_t v=rng(&seed);if(pattern==1)v=0;if(pattern==2)v=P-1;
    if(pattern==3)v=(s==0&&n==3)?(uint64_t[]){0,0,0,1,0,1,0,2}[i]:5;
    if(pattern==4)v=(uint64_t)(i+s+1);
    if(pattern==5&&n==1&&D==2)v=(uint64_t[4][2]){{1,3},{2,5},{4,7},{6,8}}[s][i];
    tables[s][i]=(mask&(1u<<s))?v:0;
   }
   for(int i=0;i<pairs;i++)for(int s=0;s<slots;s++){
    word(in,tables[s][n?2*i:0]);word(in,n?tables[s][2*i+1]:0);inpos+=2;
   }
   int rounds=mode==3?n:((mode==1||mode==2)?1:0);uint64_t rs[16];
   for(int k=0;k<rounds;k++){rs[k]=rng(&seed);if(pattern==3||pattern==6)rs[k]=1;if(pattern==7)rs[k]=0;
    if(pattern==5){rs[k]=2;}
    word(ch,rs[k]);rpos++;
   }
   if(policy==0||policy==5){
    uint64_t values[6]={0,0,0,0,0,0};
    if(mode==0||mode==1){qcoeff(n,T,D,deg,lam,values);int degree=deg[0];if(T==2&&deg[1]>degree)degree=deg[1];
     record(0,0,(1u<<(degree+1))-1,1,values,width);
    }
    if(policy!=5&&mode==3){for(int k=0,len=N;k<n;k++,len/=2)fold(len,slots,mask,rs[k]);
     for(int s=0;s<slots;s++){values[s]=tables[s][0];}
     record(2,0,mask,1,values,width);
    }else if(policy!=5&&(mode==1||mode==2)){
     fold(N,slots,mask,rs[0]);for(int i=0;i<pairs;i++){
      for(int s=0;s<slots;s++){values[s]=tables[s][i];}
      record(1,(unsigned)i,mask,i==pairs-1,values,width);
     }
    }
   }
  }
  int status=badparam?1:(policy?2:0);
  fprintf(meta,"%u\t%d\t%d\t%d\t%d\t%d\t%016" PRIx64 "\t%016" PRIx64 "\t%d\t%" PRIu64 "\t%d\t%" PRIu64 "\t%" PRIu64 "\t%" PRIu64 "\t%d\n",task,mode,n,T,deg[0],deg[1],lam[0],lam[1],policy,ib,pairs,eb,expos-eb,rb,status);
  task++;
 }
 fclose(plan);fclose(in);fclose(exp);fclose(em);fclose(ch);fclose(meta);
 printf("REFERENCE tasks=%u input_words=%" PRIu64 " expected_records=%" PRIu64 " pass=1\n",task,inpos,expos);return 0;
}

