package com.invap.rendiciondegastos

import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/**
 * Genera el Excel (.xlsx) de la rendición: una hoja por forma de pago.
 * Columnas: Comprobante | Detalle | un importe por tipo de gasto | Moneda | Pesos | Dólares | Tasa de cambio | CC | PT | WP.
 * Los anchos se calculan a partir del contenido (lo justo para que se vea todo).
 *
 * Importe en dólares de gastos en otra moneda: si es estimado, la celda se ve celeste; al escribir el valor real
 * (p. ej. del resumen de la tarjeta) deja de verse celeste. Para eso la estimación original se guarda en una
 * columna auxiliar oculta (a la derecha de WP) y un formato condicional compara la celda con ese valor.
 */
object ExcelRendicion {

    private const val ALTO_LINEA = 13.5          // puntos por línea de texto
    private const val ANCHO_MIN_COLUMNA = 6
    private const val ANCHO_MAX_DETALLE = 38
    private const val ANCHO_MIN_TABLA = 70

    private const val COLOR_ESTIMADO = "99CCFF"
    private const val COLOR_ENTRADA = "DDEBF7"
    private const val COLOR_ENCABEZADO = "D9D9D9"

    fun generar(file: File, viaje: Viaje, gastos: List<Gasto>, tiposConfigurados: List<String>, logoPng: ByteArray? = null) {
        val libro = XlsxLibro()
        if (logoPng != null) libro.logoPng(logoPng)
        val estilos = Estilos(libro)
        val tipos = tiposConfigurados.sorted()
        gastos.groupBy { it.formaDePago }.toSortedMap(String.CASE_INSENSITIVE_ORDER).forEach { (forma, lista) ->
            crearHoja(libro, estilos, viaje, forma, lista, tipos, logoPng)
        }
        FileOutputStream(file).use { libro.guardar(it) }
    }

    // ---------------------------------------------------------------- estilos

    private class Estilos(l: XlsxLibro) {
        private fun e() = XlsxLibro.Estilo()
        val dxfEstimado = l.dxfRelleno(COLOR_ESTIMADO)

        val titulo = l.estilo(e().tam(20).negrita().colorTexto("00B050").centrado())
        val negrita14 = l.estilo(e().tam(14).negrita())
        val negrita = l.estilo(e().negrita())
        val centroNegrita = l.estilo(e().negrita().centrado())
        val normal = l.estilo(e())
        val nota = l.estilo(e().negrita().ajustar().vArriba())

        val encabezado = l.estilo(e().negrita().centrado().vCentro().ajustar().relleno(COLOR_ENCABEZADO).borde(2))
        val texto = l.estilo(e().vCentro().borde(1))
        val centro = l.estilo(e().centrado().vCentro().borde(1))
        val detalle = l.estilo(e().ajustar().vCentro().borde(1))
        val numero = l.estilo(e().vCentro().borde(1).formatoNumero("#,##0.00"))
        val pesos = l.estilo(e().vCentro().borde(1).formatoNumero("\$ #,##0.00"))
        val dolares = l.estilo(e().vCentro().borde(1).formatoNumero("#,##0.00"))
        val tasa = l.estilo(e().vCentro().borde(1).formatoNumero("#,##0.00"))
        val auxiliar = l.estilo(e().formatoNumero("#,##0.00"))
        val totalEtiqueta = l.estilo(e().negrita().vCentro().borde(2))
        val totalPesos = l.estilo(e().negrita().vCentro().borde(2).formatoNumero("\$ #,##0.00"))
        val totalDolares = l.estilo(e().negrita().vCentro().borde(2).formatoNumero("#,##0.00"))
        val entradaPesos = l.estilo(e().vCentro().borde(2).relleno(COLOR_ENTRADA).formatoNumero("\$ #,##0.00"))
        val entradaDolares = l.estilo(e().vCentro().borde(2).relleno(COLOR_ENTRADA).formatoNumero("#,##0.00"))
        val cajaFirma = l.estilo(e().borde(1))
    }

    // ---------------------------------------------------------------- utilidades

    private fun colLetra(col: Int): String = XlsxLibro.letra(col)

    private fun direccion(col: Int, fila: Int) = "${colLetra(col)}${fila + 1}"

    private fun fmt(v: Double): String = String.format(Locale.US, "%,.2f", v)

    /** Cantidad de líneas que ocupa [texto] en una celda de [ancho] caracteres, cortando por palabras. */
    private fun lineasPara(texto: String, ancho: Int): Int {
        if (texto.isEmpty() || ancho <= 0) return 1
        var lineas = 1
        var actual = 0
        for (palabra in texto.split(' ')) {
            val l = palabra.length
            if (actual == 0) {
                actual = l
            } else if (actual + 1 + l <= ancho) {
                actual += 1 + l
            } else {
                lineas++
                actual = l
            }
            while (actual > ancho) {
                lineas++
                actual -= ancho
            }
        }
        return lineas
    }

    private fun palabraMasLarga(texto: String): Int = texto.split(' ').maxOfOrNull { it.length } ?: 0

    private fun esCredito(forma: String): Boolean =
        forma.contains("Crédito", ignoreCase = true) || forma.contains("Credito", ignoreCase = true)

    /** Texto en una fila, combinado a lo ancho de la tabla y con salto de línea automático. */
    private fun notaCombinada(h: XlsxLibro.Hoja, fila: Int, colDesde: Int, colHasta: Int, anchoTotal: Int, texto: String, estilo: Int) {
        h.combinar(fila, colDesde, fila, colHasta)
        h.texto(fila, colDesde, texto, estilo)
        // El texto en negrita ocupa ~15% más que el normal
        val lineas = lineasPara(texto, (anchoTotal * 0.87).toInt())
        h.altoFila(fila, ALTO_LINEA * lineas + 2)
    }

    // ---------------------------------------------------------------- hoja

    private fun crearHoja(
        libro: XlsxLibro,
        s: Estilos,
        viaje: Viaje,
        forma: String,
        lista: List<Gasto>,
        tiposConfigurados: List<String>,
        logoPng: ByteArray?
    ) {
        val baseHoja = forma.replace(Regex("[\\[\\]:*?/\\\\]"), "").trim().take(28).ifEmpty { "Hoja" }
        var nombreHoja = baseHoja
        var sufijo = 2
        while (libro.existeHoja(nombreHoja)) {
            nombreHoja = "${baseHoja}_$sufijo"
            sufijo++
        }
        val sheet = libro.hoja(nombreHoja)

        val credito = esCredito(forma)
        val primerGasto = lista.first()

        // ---- columnas
        val tipos = tiposConfigurados.toMutableList()
        val hayOtros = lista.any { g -> tipos.none { it == g.tipoGasto } }
        if (hayOtros) tipos.add("Otros")
        val c0 = 1
        val colComprobante = c0
        val colDetalle = c0 + 1
        val colTipo0 = c0 + 2
        val colMoneda = colTipo0 + tipos.size
        val colPesos = colMoneda + 1
        val colDolares = colMoneda + 2
        val colTasa = colMoneda + 3
        val colCC = colMoneda + 4
        val colPT = colMoneda + 5
        val colWP = colMoneda + 6
        val ultimaCol = colWP
        val colAuxiliar = ultimaCol + 1   // estimación original de los dólares (oculta)

        val encabezados = mutableListOf("Comprobante", "Detalle")
        encabezados.addAll(tipos)
        encabezados.addAll(listOf("Moneda", "Importe en Pesos", "Importe en Dólares", "Tasa de cambio", "CC", "PT", "WP"))

        fun indiceTipo(g: Gasto): Int {
            val i = tiposConfigurados.indexOf(g.tipoGasto)
            return if (i >= 0) i else tipos.size - 1 // "Otros" es la última
        }
        fun textoDetalle(g: Gasto): String = g.descripcion.trim()

        // ---- anchos según contenido
        val anchos = IntArray(ultimaCol + 1)
        encabezados.forEachIndexed { i, h -> anchos[c0 + i] = palabraMasLarga(h) + 2 }
        val sumaPesos = lista.filter { Monedas.esPesos(it.moneda) }.sumOf { it.monto }
        val sumaDolares = lista.sumOf { g ->
            if (Monedas.esDolar(g.moneda)) g.monto else if (Monedas.esExtranjera(g.moneda)) (g.importeDolares ?: 0.0) else 0.0
        }
        anchos[colComprobante] = maxOf(anchos[colComprobante], "TOTALES".length + 2)
        var maxDetalle = 0
        for (g in lista) {
            anchos[colComprobante] = maxOf(anchos[colComprobante], g.tagGasto.length + 2)
            maxDetalle = maxOf(maxDetalle, textoDetalle(g).length)
            val ci = colTipo0 + indiceTipo(g)
            anchos[ci] = maxOf(anchos[ci], fmt(g.monto).length + 2)
            anchos[colMoneda] = maxOf(anchos[colMoneda], g.moneda.length + 2)
            if (Monedas.esPesos(g.moneda)) anchos[colPesos] = maxOf(anchos[colPesos], fmt(g.monto).length + 4)
            val usd = g.dolares()
            if (usd != null && !Monedas.esPesos(g.moneda)) anchos[colDolares] = maxOf(anchos[colDolares], fmt(usd).length + 2)
            if (Monedas.esExtranjera(g.moneda) && usd != null && usd > 0) {
                anchos[colTasa] = maxOf(anchos[colTasa], fmt(g.monto / usd).length + 2)
            }
            anchos[colCC] = maxOf(anchos[colCC], g.centroCostos.length + 2)
            anchos[colPT] = maxOf(anchos[colPT], g.imputacionPT.length + 2)
            anchos[colWP] = maxOf(anchos[colWP], g.imputacionWP.length + 2)
        }
        anchos[colPesos] = maxOf(anchos[colPesos], fmt(sumaPesos).length + 4, 12)
        anchos[colDolares] = maxOf(anchos[colDolares], fmt(sumaDolares).length + 2, 12)
        anchos[colDetalle] = (maxDetalle + 2).coerceIn(14, ANCHO_MAX_DETALLE)
        for (c in c0..ultimaCol) anchos[c] = maxOf(anchos[c], ANCHO_MIN_COLUMNA)
        var anchoTotal = (c0..ultimaCol).sumOf { anchos[it] }
        if (anchoTotal < ANCHO_MIN_TABLA) {
            anchos[colDetalle] += ANCHO_MIN_TABLA - anchoTotal
            anchoTotal = ANCHO_MIN_TABLA
        }
        sheet.anchoColumna(0, 2.0)
        for (c in c0..ultimaCol) sheet.anchoColumna(c, anchos[c].toDouble())
        sheet.ocultarColumna(colAuxiliar, 12.0)

        // ---- encabezado de la hoja
        sheet.texto(1, c0, "Plazos para la rendición:", s.negrita14)
        notaCombinada(sheet, 2, c0, ultimaCol, anchoTotal, "Los anticipos deberán ser rendidos dentro de la semana de haber concluido el viaje.", s.nota)
        notaCombinada(sheet, 3, c0, ultimaCol, anchoTotal, "En caso de tener que realizar un nuevo desplazamiento, previo a la solicitud de fondos debería proceder a la rendición del anticipo pendiente.", s.nota)
        notaCombinada(sheet, 4, c0, ultimaCol, anchoTotal, "Los anticipos tendrán vencimiento a los 30 días de la fecha de haberse hecho efectivos.", s.nota)
        // Logo de INVAP centrado y, debajo, el título de la planilla
        sheet.altoFila(5, 54.0)
        val conLogo = logoPng != null
        if (conLogo) sheet.logoCentrado(5, c0, ultimaCol, 220, 67)
        sheet.altoFila(6, 30.0)
        sheet.combinar(6, c0, 6, ultimaCol)
        sheet.texto(6, c0, if (conLogo) "Rendición de Viajes" else "INVAP - Rendición de Viajes", s.titulo)
        sheet.combinar(7, c0, 7, ultimaCol)
        sheet.texto(7, c0, "RENDICIÓN DE GASTOS REALIZADOS CON", s.centroNegrita)
        sheet.combinar(8, c0, 8, ultimaCol)
        sheet.texto(8, c0, forma.uppercase(Locale.getDefault()), s.centroNegrita)

        val persona = primerGasto.nombrePersona.ifBlank { viaje.nombrePersona }
        val legajo = primerGasto.legajo.ifBlank { viaje.legajo }
        val cc = primerGasto.centroCostos.ifBlank { viaje.centroCostos }
        val fechas = if (viaje.fechaFin.isNotBlank()) "${viaje.fecha} al ${viaje.fechaFin}" else viaje.fecha
        sheet.texto(10, c0, "Rendición de gastos de:  $persona", s.negrita)
        sheet.texto(11, c0, "Viaje:  ${viaje.nombre}", s.negrita)
        sheet.texto(12, c0, "Fecha:  $fechas", s.negrita)
        sheet.texto(13, c0, "N° de Legajo:  $legajo        CC:  $cc", s.negrita)

        // ---- tabla
        val filaEncabezado = 15
        val lineasEncabezado = encabezados.indices.maxOf { lineasPara(encabezados[it], anchos[c0 + it] - 1) }
        sheet.altoFila(filaEncabezado, ALTO_LINEA * lineasEncabezado + 6)
        encabezados.forEachIndexed { i, h -> sheet.texto(filaEncabezado, c0 + i, h, s.encabezado) }

        lista.forEachIndexed { idx, g ->
            val fila = filaEncabezado + 1 + idx
            val extranjera = Monedas.esExtranjera(g.moneda)   // ni pesos ni dólares
            val esPesos = Monedas.esPesos(g.moneda)
            val esDolar = Monedas.esDolar(g.moneda)

            sheet.texto(fila, colComprobante, g.tagGasto, s.centro)
            val txt = textoDetalle(g)
            sheet.texto(fila, colDetalle, txt, s.detalle)
            val lineas = lineasPara(txt, anchos[colDetalle] - 1)
            if (lineas > 1) sheet.altoFila(fila, ALTO_LINEA * lineas + 2)

            val colImporte = colTipo0 + indiceTipo(g)
            for (c in colTipo0 until colMoneda) {
                if (c == colImporte) sheet.numero(fila, c, g.monto, s.numero)
                else sheet.vacio(fila, c, s.texto)
            }
            sheet.texto(fila, colMoneda, g.moneda, s.centro)

            if (esPesos) sheet.numero(fila, colPesos, g.monto, s.pesos)
            else sheet.vacio(fila, colPesos, s.texto)

            when {
                esDolar -> sheet.numero(fila, colDolares, g.monto, s.dolares)
                esPesos -> sheet.vacio(fila, colDolares, s.texto)
                else -> {
                    val usd = g.importeDolares
                    if (usd != null) {
                        sheet.numero(fila, colDolares, usd, s.dolares)
                        // Estimación original: mientras la celda siga igual, se ve celeste
                        if (g.dolaresEstimado) sheet.numero(fila, colAuxiliar, usd, s.auxiliar)
                    } else {
                        sheet.vacio(fila, colDolares, s.dolares)
                    }
                    val d = direccion(colDolares, fila)
                    val a = direccion(colAuxiliar, fila)
                    // Celeste si la celda está vacía (completar a mano) o si todavía vale lo estimado
                    sheet.formatoCondicional(d, "OR(NOT(ISNUMBER($d)),AND(ISNUMBER($a),$d=$a))", s.dxfEstimado)
                }
            }

            if (extranjera) {
                val d = direccion(colDolares, fila)
                val m = direccion(colImporte, fila)
                sheet.formula(fila, colTasa, "IF($d>0,$m/$d,\"\")", s.tasa)
            } else {
                sheet.vacio(fila, colTasa, s.texto)
            }

            sheet.texto(fila, colCC, g.centroCostos, s.centro)
            sheet.texto(fila, colPT, g.imputacionPT, s.centro)
            sheet.texto(fila, colWP, g.imputacionWP, s.centro)
        }

        // ---- totales
        val filaTotal = filaEncabezado + 1 + lista.size
        sheet.texto(filaTotal, colComprobante, "TOTALES", s.totalEtiqueta)
        val primera = filaEncabezado + 2   // numeración de Excel (base 1)
        val ultima = filaTotal              // última fila de datos, base 1
        sheet.formula(filaTotal, colPesos, "SUM(${colLetra(colPesos)}$primera:${colLetra(colPesos)}$ultima)", s.totalPesos)
        sheet.formula(filaTotal, colDolares, "SUM(${colLetra(colDolares)}$primera:${colLetra(colDolares)}$ultima)", s.totalDolares)

        var fila = filaTotal + 1
        if (!credito) {
            val filaRecibido = fila
            val filaSaldo = fila + 1
            val filaDevolver = fila + 2
            fun etiqueta(r: Int, texto: String) {
                sheet.combinar(r, colComprobante, r, colMoneda)
                sheet.texto(r, colComprobante, texto, s.negrita)
            }
            etiqueta(filaRecibido, "Recibido por Tesorería:")
            sheet.numero(filaRecibido, colPesos, 0.0, s.entradaPesos)
            sheet.numero(filaRecibido, colDolares, 0.0, s.entradaDolares)
            sheet.texto(filaRecibido, colTasa, " Completar con el monto de adelanto recibido", s.normal)
            etiqueta(filaSaldo, "Saldo del Anticipo:")
            sheet.numero(filaSaldo, colPesos, 0.0, s.entradaPesos)
            sheet.numero(filaSaldo, colDolares, 0.0, s.entradaDolares)
            sheet.texto(filaSaldo, colTasa, " Completar con lo que te quedó del anticipo", s.normal)
            etiqueta(filaDevolver, "A DEVOLVER/RECUPERAR:")
            sheet.formula(filaDevolver, colPesos, "-${direccion(colPesos, filaRecibido)}+${direccion(colPesos, filaTotal)}+${direccion(colPesos, filaSaldo)}", s.totalPesos)
            sheet.formula(filaDevolver, colDolares, "-${direccion(colDolares, filaRecibido)}+${direccion(colDolares, filaTotal)}+${direccion(colDolares, filaSaldo)}", s.totalDolares)
            sheet.texto(filaDevolver, colTasa, " Valor Negativo->Devolver/Positivo->Recuperar", s.normal)
            fila += 3
        }

        // ---- firmas
        val filaPie = fila + 1
        sheet.altoFila(filaPie, 25.0)
        sheet.altoFila(filaPie + 2, 25.0)
        sheet.texto(filaPie, colComprobante, "Autorizó", s.normal)
        sheet.vacio(filaPie, colDetalle, s.cajaFirma)
        sheet.texto(filaPie + 2, colComprobante, "Reviso", s.normal)
        sheet.vacio(filaPie + 2, colDetalle, s.cajaFirma)
        val colFirmaDesde = ultimaCol - 2
        sheet.combinar(filaPie, colFirmaDesde, filaPie, ultimaCol)
        for (c in colFirmaDesde..ultimaCol) sheet.vacio(filaPie, c, s.cajaFirma)
        sheet.texto(filaPie + 1, colFirmaDesde, "Firma y Aclaración del titular", s.normal)

        // ---- leyendas
        val filaLeyendas = filaPie + 4
        notaCombinada(sheet, filaLeyendas, c0, ultimaCol, anchoTotal, "Los gastos sin comprobante deben ser detallados.", s.nota)
        notaCombinada(sheet, filaLeyendas + 1, c0, ultimaCol, anchoTotal, "En las facturas de restaurantes, cuando son varios comensales, debe colocarse el detalle de los mismos en el reverso.", s.nota)
    }
}
