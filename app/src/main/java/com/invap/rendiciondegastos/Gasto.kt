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
    indices = [Index(value = ["viajeId"])]
)
data class Gasto(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0,
    val viajeId: Long = 0,
    val descripcion: String = "",
    val monto: Double = 0.0,
    val fecha: String = "",
    val urlFotoRecibo: String = "",
    val moneda: String = "",
    val tipoGasto: String = "",
    val formaDePago: String = "",
    val tagGasto: String = "",
    val imputacionPT: String = "",
    val imputacionWP: String = "",
    val nombrePersona: String = "",
    val legajo: String = "",
    val centroCostos: String = "",
    val timestamp: Long = 0,
    // El comentario se movió aquí, fuera de los parámetros
    val sinComprobante: Boolean = false
)