package com.vidadomu.nexus.core.network

import okhttp3.RequestBody
import okio.*
class ProgressRequestBody(private val delegate:RequestBody,private val progress:(Long,Long)->Unit):RequestBody() {
    override fun contentType()=delegate.contentType()
    override fun contentLength()=delegate.contentLength()
    override fun isOneShot()=true
    override fun writeTo(sink:BufferedSink) {
        val total=contentLength(); var sent=0L;var last=0L
        val forwarding=object:ForwardingSink(sink) {
            override fun write(source:Buffer,byteCount:Long) {
                super.write(source,byteCount);sent+=byteCount
                if(sent-last>=65536 || sent==total) {last=sent;progress(sent,total)}
            }
        }.buffer()
        delegate.writeTo(forwarding);forwarding.flush()
    }
}
