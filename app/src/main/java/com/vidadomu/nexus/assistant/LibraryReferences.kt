package com.vidadomu.nexus.assistant

import com.vidadomu.nexus.agent.core.tools.Attachment

internal fun mergeReferenceAttachments(current: List<Attachment>, added: Attachment): List<Attachment> =
    if (current.any { it.id == added.id || it.uri == added.uri }) current else current + added
