package com.vidadomu.nexus.core.model

import java.security.MessageDigest

/** A provider job can have multiple independent files, including files with identical extensions. */
object OutputFiles {
    fun token(output: GenerationOutput): String = MessageDigest.getInstance("SHA-256")
        .digest("${output.jobId}\n${output.id}\n${output.url}".toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }.take(24)

    fun conflictingLocalUris(outputs: List<GenerationOutput>): Set<String> = outputs
        .filter { !it.localUri.isNullOrBlank() }
        .groupBy { it.localUri!! }
        .filterValues { group -> group.map { it.url }.distinct().size > 1 }
        .keys

    fun trustedLocalUri(output: GenerationOutput, conflicts: Set<String>): String? =
        output.localUri?.takeUnless { it.isBlank() || it in conflicts }
}
