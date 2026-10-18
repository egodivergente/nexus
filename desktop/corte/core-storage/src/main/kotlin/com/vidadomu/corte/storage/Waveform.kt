package com.vidadomu.corte.storage

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException

/**
 * Multi-resolution min/max peaks of an audio stream (all channels folded together). Level 0 has one
 * peak pair per [BASE_BUCKET] frames; each next level merges 4 buckets. Drawing picks the coarsest
 * level that still has at least one bucket per pixel, so any zoom is cheap.
 */
class Waveform(val sampleRate: Int, val totalFrames: Long, val levels: List<ShortArray>) {
    init {
        require(sampleRate > 0)
    }

    val durationUs: Long get() = totalFrames * 1_000_000L / sampleRate

    fun bucketFrames(level: Int): Long = BASE_BUCKET.toLong() shl (2 * level)

    /** Coarsest level whose bucket is not larger than [framesPerPixel]. */
    fun levelFor(framesPerPixel: Double): Int {
        var l = 0
        while (l + 1 < levels.size && bucketFrames(l + 1) <= framesPerPixel) l++
        return l
    }

    /**
     * Writes min/max (normalised -1..1) for [pixels] columns covering source frames
     * [startFrame, endFrame) into [outMin]/[outMax]. Columns without data get 0.
     */
    fun fill(startFrame: Long, endFrame: Long, pixels: Int, outMin: FloatArray, outMax: FloatArray) {
        if (pixels <= 0 || endFrame <= startFrame) return
        val fpp = (endFrame - startFrame).toDouble() / pixels
        // Use a level with ~8 buckets per pixel and only buckets fully inside the column, so a loud
        // passage never bleeds into the neighbouring column because of bucket alignment.
        val level = levelFor(fpp / 8.0)
        val data = levels[level]
        val bucket = bucketFrames(level)
        val buckets = data.size / 2
        for (px in 0 until pixels) {
            val f0 = startFrame + (px * fpp).toLong()
            val f1 = maxOf(f0 + 1, startFrame + ((px + 1) * fpp).toLong())
            var b0 = ((f0 + bucket - 1) / bucket).toInt()
            var b1 = (f1 / bucket).toInt() - 1
            if (b0 > b1) { b0 = (f0 / bucket).toInt(); b1 = ((f1 - 1) / bucket).toInt() }
            if (b0 >= buckets || b1 < 0) { outMin[px] = 0f; outMax[px] = 0f; continue }
            b0 = b0.coerceAtLeast(0); b1 = b1.coerceAtMost(buckets - 1)
            var mn = Short.MAX_VALUE.toInt()
            var mx = Short.MIN_VALUE.toInt()
            for (b in b0..b1) {
                mn = minOf(mn, data[2 * b].toInt()); mx = maxOf(mx, data[2 * b + 1].toInt())
            }
            outMin[px] = mn / 32768f
            outMax[px] = mx / 32767f
        }
    }

    fun writeTo(file: File) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        DataOutputStream(tmp.outputStream().buffered()).use { out ->
            out.writeInt(MAGIC)
            out.writeInt(sampleRate)
            out.writeLong(totalFrames)
            out.writeInt(levels.size)
            for (l in levels) {
                out.writeInt(l.size)
                for (v in l) out.writeShort(v.toInt())
            }
        }
        if (!tmp.renameTo(file)) {
            file.delete()
            if (!tmp.renameTo(file)) throw IOException("cannot write ${file.name}")
        }
    }

    companion object {
        const val BASE_BUCKET = 256
        private const val MAGIC = 0x43574631 // "CWF1"

        fun readFrom(file: File): Waveform = DataInputStream(file.inputStream().buffered()).use { inp ->
            if (inp.readInt() != MAGIC) throw IOException("bad waveform cache")
            val sr = inp.readInt()
            val frames = inp.readLong()
            val n = inp.readInt()
            if (n !in 1..32) throw IOException("bad waveform cache")
            val levels = List(n) {
                val size = inp.readInt()
                if (size < 0 || size > 200_000_000) throw IOException("bad waveform cache")
                ShortArray(size) { inp.readShort() }
            }
            Waveform(sr, frames, levels)
        }
    }
}

/** Streaming builder: feed interleaved 16-bit PCM as it is decoded, never holding the whole file. */
class WaveformBuilder(private val sampleRate: Int, private val channels: Int) {
    private val base = ShortArrayBuilder()
    private var frameInBucket = 0
    private var curMin = Short.MAX_VALUE.toInt()
    private var curMax = Short.MIN_VALUE.toInt()
    private var frames = 0L
    private var channelPos = 0

    init {
        require(sampleRate > 0 && channels > 0)
    }

    fun feed(samples: ShortArray, count: Int = samples.size) {
        for (i in 0 until count) {
            val v = samples[i].toInt()
            if (v < curMin) curMin = v
            if (v > curMax) curMax = v
            if (++channelPos == channels) {
                channelPos = 0
                frames++
                if (++frameInBucket == Waveform.BASE_BUCKET) flushBucket()
            }
        }
    }

    private fun flushBucket() {
        base.add(curMin.toShort()); base.add(curMax.toShort())
        frameInBucket = 0
        curMin = Short.MAX_VALUE.toInt(); curMax = Short.MIN_VALUE.toInt()
    }

    fun build(): Waveform {
        if (frameInBucket > 0) flushBucket()
        val levels = ArrayList<ShortArray>()
        var cur = base.toArray()
        levels += cur
        while (cur.size / 2 > 4 && levels.size < 12) {
            val n = (cur.size / 2 + 3) / 4
            val next = ShortArray(n * 2)
            for (i in 0 until n) {
                var mn = Short.MAX_VALUE.toInt(); var mx = Short.MIN_VALUE.toInt()
                for (j in 4 * i until minOf(4 * i + 4, cur.size / 2)) {
                    mn = minOf(mn, cur[2 * j].toInt()); mx = maxOf(mx, cur[2 * j + 1].toInt())
                }
                next[2 * i] = mn.toShort(); next[2 * i + 1] = mx.toShort()
            }
            levels += next
            cur = next
        }
        return Waveform(sampleRate, frames, levels)
    }

    private class ShortArrayBuilder {
        private var data = ShortArray(4096)
        private var size = 0
        fun add(v: Short) {
            if (size == data.size) data = data.copyOf(data.size * 2)
            data[size++] = v
        }
        fun toArray(): ShortArray = data.copyOf(size)
    }
}
