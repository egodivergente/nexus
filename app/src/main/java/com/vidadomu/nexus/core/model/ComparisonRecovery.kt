package com.vidadomu.nexus.core.model

/** A request persisted before a process death may have reached the provider. Never retry it. */
fun GenerationJob.recoverInterruptedComparison(): GenerationJob =
    if((parameters.containsKey("__comparison_id") || parameters.containsKey("__assistant_plan")) && providerJobId.isNullOrBlank() && status == GenerationStatus.QUEUED)
        copy(status = GenerationStatus.FAILED,
            error = "Envío interrumpido. Comprueba el proveedor antes de repetir; podría haberse iniciado.",
            parameters = parameters + ("__submission_uncertain" to "true"))
    else this
