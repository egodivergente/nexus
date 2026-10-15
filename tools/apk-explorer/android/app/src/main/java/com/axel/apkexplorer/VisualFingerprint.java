package com.axel.apkexplorer;

/** Conservative comparison of sampled RGB frames; ignores tiny rendering noise only. */
public final class VisualFingerprint {
    public static boolean same(int[] a,int[] b) {
        if(a.length!=b.length || a.length==0)return false;
        long difference=0;int changed=0;
        for(int i=0;i<a.length;i++){
            int r=Math.abs(((a[i]>>16)&255)-((b[i]>>16)&255));
            int g=Math.abs(((a[i]>>8)&255)-((b[i]>>8)&255));
            int blue=Math.abs((a[i]&255)-(b[i]&255));
            difference+=r+g+blue;
            if(Math.max(r,Math.max(g,blue))>16)changed++;
        }
        return difference/(double)(a.length*3)<=1.0 && changed/(double)a.length<=.005;
    }
}
