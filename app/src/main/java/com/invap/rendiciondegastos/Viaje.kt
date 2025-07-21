package com.invap.rendiciondegastos

data class Viaje(
    var id: String = "",
    val nombre: String = "",
    val fecha: String = "",
    val monedaPorDefecto: String = "",
    // --- Nuevos campos ---
    val imputacionPorDefectoPT: String = "",
    val imputacionPorDefectoWP: String = "",
    // --- Campos de configuración ---
    val nombrePersona: String = "",
    val legajo: String = "",
    val centroCostos: String = ""
)