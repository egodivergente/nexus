package com.vidadomu.nexus.core.model

/** A provider contract check failed because of the prompt itself: the fix is in the prompt box, not in the parameters. */
class PromptContractViolation(message: String) : IllegalArgumentException(message)
