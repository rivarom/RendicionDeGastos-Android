package com.invap.rendiciondegastos

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Informe interno de gastos del viaje (PDF, A4 vertical): totales, gráficos de torta por tipo de gasto,
 * por moneda y por forma de pago, e índices (promedios por día, etc.).
 * Todo en dólares de referencia (uso interno, no contable): los gastos en otra moneda usan el importe en
 * dólares cargado o estimado; si no lo tienen, se convierten con la tasa de referencia configurada.
 */
object InformeInterno {

    private const val ANCHO = 595
    private const val ALTO = 842
    private const val MARGEN = 40f
    private const val MS_DIA = 86_400_000L

    private val PALETA = intArrayOf(
        0xFF1F77B4.toInt(), 0xFFFF7F0E.toInt(), 0xFF2CA02C.toInt(), 0xFFD62728.toInt(),
        0xFF9467BD.toInt(), 0xFF8C564B.toInt(), 0xFFE377C2.toInt(), 0xFF17BECF.toInt()
    )
    private const val COLOR_OTROS = 0xFF9E9E9E.toInt()

    private class Item(val etiqueta: String, val valor: Double)

    /** Importe del gasto en dólares de referencia, o null si no se puede convertir. */
    private fun usd(g: Gasto, tasas: Map<String, Double>): Double? =
        if (Monedas.esDolar(g.moneda)) g.monto
        else g.importeDolares ?: Monedas.estimarDolares(g.monto, g.moneda, tasas)

    private fun dinero(v: Double): String = "US$ " + String.format(Locale.US, "%,.2f", v)

    private fun porcentaje(parte: Double, total: Double): String =
        if (total > 0) String.format(Locale.US, "%.1f%%", parte * 100.0 / total) else "-"

    private fun parseFecha(s: String): Long? {
        val t = s.trim()
        if (t.isEmpty()) return null
        for (patron in listOf("dd/MM/yyyy", "d/M/yyyy", "yyyy-MM-dd")) {
            try {
                val f = SimpleDateFormat(patron, Locale.US)
                f.isLenient = false
                f.timeZone = TimeZone.getTimeZone("UTC")
                val d = f.parse(t)
                if (d != null) return d.time
            } catch (e: Exception) {
            }
        }
        return null
    }

    /** Cantidad de días del viaje y de dónde se obtuvo. */
    private fun calcularDias(viaje: Viaje, gastos: List<Gasto>): Pair<Int, String> {
        val inicio = parseFecha(viaje.fecha)
        val fin = parseFecha(viaje.fechaFin)
        if (inicio != null && fin != null && fin >= inicio) {
            return Pair(((fin - inicio) / MS_DIA).toInt() + 1, "fechas del viaje")
        }
        val fechas = gastos.mapNotNull { parseFecha(it.fecha) }
        if (fechas.isNotEmpty()) {
            val n = ((fechas.maxOrNull()!! - fechas.minOrNull()!!) / MS_DIA).toInt() + 1
            return Pair(n, "del primer al último gasto (el viaje no tiene fecha de fin)")
        }
        return Pair(1, "sin fechas")
    }

    // ---------------------------------------------------------------- dibujo

    private class Pagina(private val doc: PdfDocument) {
        private var numero = 0
        private var pagina: PdfDocument.Page? = null
        lateinit var canvas: Canvas
        var y = MARGEN

        val pTitulo = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 20f; typeface = Typeface.DEFAULT_BOLD; color = 0xFF1B5E20.toInt() }
        val pSeccion = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 13f; typeface = Typeface.DEFAULT_BOLD; color = 0xFF000000.toInt() }
        val pTexto = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10.5f; color = 0xFF222222.toInt() }
        val pNegrita = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10.5f; typeface = Typeface.DEFAULT_BOLD; color = 0xFF000000.toInt() }
        val pChico = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8.5f; color = 0xFF666666.toInt() }
        val pLinea = Paint().apply { color = 0xFFBBBBBB.toInt(); strokeWidth = 0.8f }

        init { nueva() }

        private fun nueva() {
            numero++
            val info = PdfDocument.PageInfo.Builder(ANCHO, ALTO, numero).create()
            val p = doc.startPage(info)
            pagina = p
            canvas = p.canvas
            y = MARGEN
        }

        private fun cerrarActual() {
            val p = pagina ?: return
            canvas.drawText("Informe interno de gastos - uso interno, no contable   |   Página $numero", MARGEN, ALTO - 20f, pChico)
            doc.finishPage(p)
            pagina = null
        }

        fun asegurar(alto: Float) {
            if (y + alto > ALTO - MARGEN - 10f) {
                cerrarActual()
                nueva()
            }
        }

        fun terminar() = cerrarActual()

        fun espacio(alto: Float) { y += alto }

        fun texto(t: String, paint: Paint = pTexto, x: Float = MARGEN) {
            asegurar(paint.textSize + 6f)
            y += paint.textSize
            canvas.drawText(t, x, y, paint)
            y += 5f
        }

        fun seccion(t: String) {
            asegurar(40f)
            y += 10f
            texto(t, pSeccion)
            canvas.drawLine(MARGEN, y - 2f, ANCHO - MARGEN, y - 2f, pLinea)
            y += 4f
        }

        /** Fila de tabla: la etiqueta va a la izquierda y cada valor alineado a la derecha en su columna. */
        fun fila(etiqueta: String, valores: List<String>, xsDerecha: List<Float>, paint: Paint = pTexto) {
            asegurar(paint.textSize + 6f)
            y += paint.textSize
            canvas.drawText(etiqueta, MARGEN, y, paint)
            valores.forEachIndexed { i, v ->
                val ancho = paint.measureText(v)
                canvas.drawText(v, xsDerecha[i] - ancho, y, paint)
            }
            y += 5f
        }
    }

    // ---------------------------------------------------------------- informe

    fun generar(file: File, viaje: Viaje, gastos: List<Gasto>, tasas: Map<String, Double>) {
        val doc = PdfDocument()
        try {
            val pg = Pagina(doc)

            val conUsd = gastos.mapNotNull { g -> usd(g, tasas)?.let { Pair(g, it) } }
            val sinUsd = gastos.filter { g -> usd(g, tasas) == null }
            val totalUsd = conUsd.sumOf { it.second }
            val (dias, origenDias) = calcularDias(viaje, gastos)

            // ---- encabezado
            pg.texto("INVAP - Informe interno de gastos", pg.pTitulo)
            pg.espacio(4f)
            val persona = gastos.firstOrNull()?.nombrePersona?.ifBlank { null } ?: viaje.nombrePersona
            pg.texto("Viaje: ${viaje.nombre}", pg.pNegrita)
            if (persona.isNotBlank()) pg.texto("Rendición de: $persona")
            val fechas = if (viaje.fechaFin.isNotBlank()) "${viaje.fecha} al ${viaje.fechaFin}" else viaje.fecha
            pg.texto("Fecha: $fechas")
            pg.texto("Generado: " + SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date()), pg.pChico)
            pg.texto("Todos los importes están expresados en dólares de referencia (uso interno, no contable).", pg.pChico)

            // ---- totales e índices
            pg.seccion("Resumen e índices")
            val col = ANCHO - MARGEN
            pg.fila("Gasto total", listOf(dinero(totalUsd)), listOf(col), pg.pNegrita)
            pg.fila("Duración considerada", listOf("$dias día(s)"), listOf(col))
            pg.texto("   Fuente: $origenDias", pg.pChico)
            pg.fila("Gasto total promedio por día", listOf(dinero(totalUsd / dias)), listOf(col))
            pg.fila("Cantidad de gastos", listOf(gastos.size.toString()), listOf(col))
            if (conUsd.isNotEmpty()) {
                pg.fila("Gasto promedio por comprobante", listOf(dinero(totalUsd / conUsd.size)), listOf(col))
                val mayor = conUsd.maxByOrNull { it.second }!!
                val desc = mayor.first.descripcion.trim().ifEmpty { mayor.first.tipoGasto }
                pg.fila("Gasto máximo (${mayor.first.tagGasto} - ${desc.take(28)})", listOf(dinero(mayor.second)), listOf(col))
            }
            val sinComprobante = gastos.count { it.sinComprobante }
            pg.fila("Gastos sin comprobante", listOf("$sinComprobante (${porcentaje(sinComprobante.toDouble(), gastos.size.toDouble())})"), listOf(col))

            // ---- por tipo de gasto (tabla con promedio por día)
            val porTipo = conUsd.groupBy { it.first.tipoGasto.ifBlank { "(sin tipo)" } }
                .map { (k, v) -> Item(k, v.sumOf { it.second }) }
                .sortedByDescending { it.valor }
            pg.seccion("Gasto por tipo")
            val x1 = 340f
            val x2 = 430f
            val x3 = col
            pg.fila("Tipo", listOf("Total", "% del total", "Promedio/día"), listOf(x1, x2, x3), pg.pNegrita)
            porTipo.forEach {
                pg.fila(it.etiqueta, listOf(dinero(it.valor), porcentaje(it.valor, totalUsd), dinero(it.valor / dias)), listOf(x1, x2, x3))
            }

            // ---- tortas
            torta(pg, "Gasto por tipo de gasto", porTipo)
            val porMoneda = conUsd.groupBy { it.first.moneda.ifBlank { "(sin moneda)" } }
                .map { (k, v) -> Item(k, v.sumOf { it.second }) }
                .sortedByDescending { it.valor }
            torta(pg, "Gasto por moneda de la transacción", porMoneda)
            val porForma = conUsd.groupBy { it.first.formaDePago.ifBlank { "(sin forma de pago)" } }
                .map { (k, v) -> Item(k, v.sumOf { it.second }) }
                .sortedByDescending { it.valor }
            torta(pg, "Gasto por forma de pago", porForma)

            // ---- monedas en su importe original
            pg.seccion("Importes en la moneda original")
            gastos.groupBy { it.moneda.ifBlank { "(sin moneda)" } }.toSortedMap(String.CASE_INSENSITIVE_ORDER).forEach { (m, lista) ->
                val total = lista.sumOf { it.monto }
                pg.fila("$m (${lista.size} gasto(s))", listOf(String.format(Locale.US, "%,.2f", total)), listOf(col))
            }

            // ---- avisos
            if (sinUsd.isNotEmpty()) {
                pg.seccion("Gastos sin equivalente en dólares (no incluidos en este informe)")
                pg.texto("Cargá la tasa de referencia en Configuración o el importe en dólares del gasto.", pg.pChico)
                sinUsd.take(15).forEach {
                    val d = it.descripcion.trim().take(30)
                    pg.texto("${it.tagGasto}  ${d}  -  ${String.format(Locale.US, "%,.2f", it.monto)} ${it.moneda}")
                }
                if (sinUsd.size > 15) pg.texto("... y ${sinUsd.size - 15} más", pg.pChico)
            }

            pg.terminar()
            FileOutputStream(file).use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
    }

    /** Gráfico de torta con leyenda (hasta 8 porciones; el resto se agrupa en "Otros"). */
    private fun torta(pg: Pagina, titulo: String, itemsOrdenados: List<Item>) {
        pg.seccion(titulo)
        val total = itemsOrdenados.sumOf { it.valor }
        if (total <= 0.0) {
            pg.texto("Sin datos para graficar.", pg.pChico)
            return
        }
        val maxPorciones = PALETA.size
        val items = if (itemsOrdenados.size > maxPorciones) {
            itemsOrdenados.take(maxPorciones - 1) + Item("Otros", itemsOrdenados.drop(maxPorciones - 1).sumOf { it.valor })
        } else itemsOrdenados

        val diametro = 120f
        val altoLeyenda = items.size * 15f
        val alto = maxOf(diametro, altoLeyenda) + 10f
        pg.asegurar(alto)

        val oval = RectF(MARGEN, pg.y + 4f, MARGEN + diametro, pg.y + 4f + diametro)
        val pincel = Paint(Paint.ANTI_ALIAS_FLAG)
        var inicio = -90f
        items.forEachIndexed { i, it ->
            val barrido = (it.valor / total * 360.0).toFloat()
            pincel.style = Paint.Style.FILL
            pincel.color = if (it.etiqueta == "Otros" && itemsOrdenados.size > maxPorciones && i == items.size - 1) COLOR_OTROS else PALETA[i % PALETA.size]
            pg.canvas.drawArc(oval, inicio, barrido, true, pincel)
            pincel.style = Paint.Style.STROKE
            pincel.strokeWidth = 1f
            pincel.color = 0xFFFFFFFF.toInt()
            pg.canvas.drawArc(oval, inicio, barrido, true, pincel)
            inicio += barrido
        }

        val xLeyenda = MARGEN + diametro + 24f
        var yLeyenda = pg.y + 14f
        items.forEachIndexed { i, it ->
            pincel.style = Paint.Style.FILL
            pincel.color = if (it.etiqueta == "Otros" && itemsOrdenados.size > maxPorciones && i == items.size - 1) COLOR_OTROS else PALETA[i % PALETA.size]
            pg.canvas.drawRect(xLeyenda, yLeyenda - 8f, xLeyenda + 9f, yLeyenda + 1f, pincel)
            val etiqueta = if (it.etiqueta.length > 26) it.etiqueta.take(25) + "…" else it.etiqueta
            pg.canvas.drawText("$etiqueta", xLeyenda + 15f, yLeyenda, pg.pTexto)
            val valor = "${dinero(it.valor)}  (${porcentaje(it.valor, total)})"
            val ancho = pg.pTexto.measureText(valor)
            pg.canvas.drawText(valor, ANCHO - MARGEN - ancho, yLeyenda, pg.pTexto)
            yLeyenda += 15f
        }
        pg.espacio(alto)
    }
}
