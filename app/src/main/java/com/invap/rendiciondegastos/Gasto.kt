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
    val sinComprobante: Boolean = false,
    // Tamaño en que se imprime el recibo en el PDF (nombre de TamanoRecibo)
    val tamanoRecibo: String = TamanoRecibo.CHICO.name,
    // Importe en dólares de gastos en otra moneda (null = sin dato). Si [dolaresEstimado], se calculó con
    // la tasa de referencia al cargar el gasto; si no, lo ingresó el usuario (p. ej. del resumen de la tarjeta).
    val importeDolares: Double? = null,
    val dolaresEstimado: Boolean = false
)

/** Tamaño de impresión del recibo en la hoja A4 apaisada del PDF. */
enum class TamanoRecibo(val etiqueta: String) {
    CHICO("Chico (1/3 de página)"),
    MEDIANO("Mediano (1/2 página)"),
    GRANDE("Grande (página completa, apaisado)");

    companion object {
        fun desde(valor: String?): TamanoRecibo = values().firstOrNull { it.name == valor } ?: CHICO
    }
}

fun Gasto.tamano(): TamanoRecibo = TamanoRecibo.desde(tamanoRecibo)