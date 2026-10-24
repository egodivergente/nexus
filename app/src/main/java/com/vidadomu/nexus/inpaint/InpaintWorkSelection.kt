package com.vidadomu.nexus.inpaint

/** WorkManager does not promise chronological ordering for unique-work history. */
internal fun <T> selectInpaintWork(
    works: List<T>, trackedId: String?, id: (T) -> String, finished: (T) -> Boolean
): T? = works.singleOrNull { !finished(it) }
    ?: trackedId?.let { tracked -> works.singleOrNull { id(it) == tracked } }
    ?: works.singleOrNull().takeIf { trackedId == null }
