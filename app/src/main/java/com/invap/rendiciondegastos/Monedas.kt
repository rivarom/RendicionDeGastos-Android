package com.invap.rendiciondegastos

import android.content.Context
import java.text.Normalizer
import java.util.Locale

/** Reconocimiento de monedas y tasas de cambio de referencia (uso interno, no contable). */
object Monedas {

    const val PREFS = "UserPrefs_local"
    const val CLAVE_TASAS = "TASAS"

    fun normalizar(moneda: String): String =
        Normalizer.normalize(moneda.trim(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.ROOT)

    fun esPesos(moneda: String): Boolean =
        normalizar(moneda) in setOf("peso", "pesos", "ars", "\$", "\$ars")

    fun esDolar(moneda: String): Boolean =
        normalizar(moneda) in setOf("dolar", "dolares", "usd", "us\$", "u\$s", "u\$d")

    /** Moneda que no es pesos ni dólares (EGP, UGX, Euro, ...). */
    fun esExtranjera(moneda: String): Boolean = !esPesos(moneda) && !esDolar(moneda)

    /** Tasas de referencia configuradas: moneda (normalizada) -> unidades de esa moneda por 1 USD. */
    fun tasasReferencia(context: Context): Map<String, Double> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val resultado = mutableMapOf<String, Double>()
        prefs.getStringSet(CLAVE_TASAS, emptySet())?.forEach { item ->
            val partes = item.split("::")
            val tasa = partes.getOrNull(1)?.toDoubleOrNull()
            if (partes.size == 2 && tasa != null && tasa > 0) resultado[normalizar(partes[0])] = tasa
        }
        return resultado
    }

    fun tasaDe(tasas: Map<String, Double>, moneda: String): Double? = tasas[normalizar(moneda)]

    /** Importe estimado en dólares; null si no hay tasa de referencia para la moneda. */
    fun estimarDolares(monto: Double, moneda: String, tasas: Map<String, Double>): Double? {
        if (esDolar(moneda)) return monto
        val tasa = tasas[normalizar(moneda)] ?: return null
        return monto / tasa
    }
}

/** Importe del gasto en dólares: el monto si ya está en dólares; si no, el cargado o estimado (puede ser null). */
fun Gasto.dolares(): Double? = if (Monedas.esDolar(moneda)) monto else importeDolares
