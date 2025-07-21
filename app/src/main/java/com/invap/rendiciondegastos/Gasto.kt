package com.invap.rendiciondegastos

data class Gasto(
    var id: String = "",
    val viajeId: String = "",
    val descripcion: String = "",
    val monto: Double = 0.0,
    val fecha: String = "",
    val urlFotoRecibo: String = "",
    val moneda: String = "",
    val tipoGasto: String = "",
    val formaDePago: String = "",
    val tagGasto: String = "",
    // --- Nuevos campos ---
    val imputacionPT: String = "",
    val imputacionWP: String = "",
    // --- Campos de configuración ---
    val nombrePersona: String = "",
    val legajo: String = "",
    val centroCostos: String = ""
)