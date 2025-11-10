package com.invap.rendiciondegastos

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "gastos",
    foreignKeys = [ForeignKey(
        entity = Viaje::class,
        parentColumns = ["id"],
        childColumns = ["viajeId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["viajeId"])] // Mejora el rendimiento de las consultas por viajeId
)
data class Gasto(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0, // Modificado: de String a Long
    val viajeId: Long = 0, // Modificado: de String a Long (debe coincidir con Viaje.id)
    val descripcion: String = "",
    val monto: Double = 0.0,
    val fecha: String = "",
    val urlFotoRecibo: String = "", // Almacenará el path local (sigue siendo String)
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
    val centroCostos: String = "",
    // --- Campo para ordenar ---
    val timestamp: Long = 0
)