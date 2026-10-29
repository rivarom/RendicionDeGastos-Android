package com.invap.rendiciondegastos

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "viajes")
data class Viaje(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0, // Modificado: de String a Long autogenerado
    val nombre: String = "",
    val fecha: String = "",
    val monedaPorDefecto: String = "",
    // --- Nuevos campos ---
    val imputacionPorDefectoPT: String = "",
    val imputacionPorDefectoWP: String = "",
    // --- Campos de configuración ---
    val nombrePersona: String = "",
    val legajo: String = "",
    val centroCostos: String = "",
    // Fecha de fin del viaje (opcional, dd/MM/yyyy); se usa para los promedios por día del informe
    val fechaFin: String = ""
)