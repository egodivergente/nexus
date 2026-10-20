package com.vidadomu.nexus.ui.models

/** Pluraliza "modelo"/"modelos" según el conteo (ej. "1 modelo", "3 modelos"). */
fun pluralModelos(count: Int): String = if (count == 1) "1 modelo" else "$count modelos"

/** Pluraliza "proveedor"/"proveedores" según el conteo. */
fun pluralProveedores(count: Int): String = if (count == 1) "1 proveedor" else "$count proveedores"
