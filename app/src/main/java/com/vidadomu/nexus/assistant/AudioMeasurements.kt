package com.vidadomu.nexus.assistant

import kotlin.math.*

internal data class AudioMeasurements(val rmsDbfs:Double,val peakDbfs:Double,val nearFullScalePercent:Double,val quietPercent:Double,val bandsPercent:List<Double>) {
    companion object {
        val bands=listOf(20.0 to 250.0,250.0 to 2000.0,2000.0 to 6000.0,6000.0 to 8000.0)
        fun measure(samples:FloatArray):AudioMeasurements {
            require(samples.isNotEmpty() && samples.all {it.isFinite()})
            var sum=0.0;var peak=0.0;var clipped=0;var quiet=0
            samples.forEach {val v=abs(it.toDouble());sum+=v*v;peak=max(peak,v);if(v>=.999)clipped++;if(v<.001)quiet++}
            fun db(v:Double)=20*log10(max(v,1e-6))
            val size=2048;val energies=DoubleArray(bands.size)
            val frames=min(256,max(1,samples.size/size))
            for(frame in 0 until frames) {
                val offset=if(frames==1)0 else ((samples.size-size).coerceAtLeast(0).toLong()*frame/(frames-1)).toInt()
                val real=DoubleArray(size) {i -> (samples.getOrNull(offset+i) ?: 0f).toDouble()*(.5-.5*cos(2*PI*i/(size-1)))}
                val imag=DoubleArray(size)
                var j=0
                for(i in 1 until size) {var bit=size shr 1;while(j and bit!=0){j=j xor bit;bit=bit shr 1};j=j xor bit;if(i<j){val t=real[i];real[i]=real[j];real[j]=t}}
                var len=2
                while(len<=size) {
                    val angle=-2*PI/len
                    for(start in 0 until size step len) for(k in 0 until len/2) {
                        val a=start+k;val b=a+len/2;val c=cos(angle*k);val d=sin(angle*k)
                        val re=real[b]*c-imag[b]*d;val im=real[b]*d+imag[b]*c
                        real[b]=real[a]-re;imag[b]=imag[a]-im;real[a]+=re;imag[a]+=im
                    };len*=2
                }
                for(bin in 1 until size/2) {
                    val hz=bin*16000.0/size;val band=bands.indexOfFirst {hz>=it.first && hz<it.second}
                    if(band>=0) energies[band]+=real[bin]*real[bin]+imag[bin]*imag[bin]
                }
            }
            val total=energies.sum()
            return AudioMeasurements(db(sqrt(sum/samples.size)),db(peak),100.0*clipped/samples.size,100.0*quiet/samples.size,energies.map {if(total>0)100*it/total else 0.0})
        }
    }
}
