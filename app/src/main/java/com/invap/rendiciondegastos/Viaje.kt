package com.invap.rendiciondegastos

data class Viaje(
    var id: String = "",
    val nombre: String = "",
    val fecha: String = "",
    // Nuevos campos de configuración
    val nombrePersona: String = "",
    val legajo: String = "",
    val centroCostos: String = ""
)