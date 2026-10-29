package com.invap.rendiciondegastos

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import com.bumptech.glide.signature.ObjectKey
import com.invap.rendiciondegastos.databinding.ActivityDetalleViajeBinding
import jxl.CellView
import jxl.SheetSettings
import jxl.WorkbookSettings
import jxl.Workbook
import jxl.format.Alignment
import jxl.format.Border
import jxl.format.BorderLineStyle
import jxl.format.Colour
import jxl.format.VerticalAlignment
import jxl.write.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

/**
 * Lado mayor (px) al que se reducen las fotos de recibos dentro del PDF, según el tamaño de impresión.
 * Más alto = más nítido y PDF más pesado.
 */
private fun pdfMaxLadoPx(tamano: TamanoRecibo): Int = when (tamano) {
    TamanoRecibo.CHICO -> 1000
    TamanoRecibo.MEDIANO -> 1500
    TamanoRecibo.GRANDE -> 2000
}

/** En tamaño Grande, una foto vertical se gira 90° sola: los recibos grandes (A4/carta) se imprimen siempre apaisados. */
private const val ROTAR_GRANDE_VERTICAL = true

class DetalleViajeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetalleViajeBinding
    private var viajeId: Long = 0L
    private var nombreViaje: String? = null
    private var monedaPorDefecto: String? = null
    private var imputacionPtPorDefecto: String? = null
    private var imputacionWpPorDefecto: String? = null

    private lateinit var db: AppDatabase
    private val listaDeGastos = mutableListOf<Gasto>()
    private var viajeActual: Viaje? = null
    private lateinit var adapter: GastosAdapter

    // "Guardar en...": el usuario elige dónde (Descargas, Drive, etc.) con el selector del sistema
    private val guardarViajeLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) exportarViajeAUri(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityDetalleViajeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbarDetalle)

        db = AppDatabase.getInstance(applicationContext)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        viajeId = intent.getLongExtra("EXTRA_VIAJE_ID", 0L)
        if (viajeId == 0L) {
            Toast.makeText(this, "Error: ID de viaje no válido", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        nombreViaje = intent.getStringExtra("EXTRA_VIAJE_NOMBRE")
        monedaPorDefecto = intent.getStringExtra("EXTRA_VIAJE_MONEDA_DEFECTO")
        imputacionPtPorDefecto = intent.getStringExtra("EXTRA_VIAJE_IMPUTACION_PT")
        imputacionWpPorDefecto = intent.getStringExtra("EXTRA_VIAJE_IMPUTACION_WP")

        val tituloFormateado = getString(R.string.titulo_detalle_viaje, nombreViaje)
        binding.textViewNombreViajeDetalle.text = tituloFormateado

        adapter = GastosAdapter(
            listaDeGastos,
            onItemClicked = { gasto ->
                if (gasto.urlFotoRecibo.isNotEmpty()) {
                    try {
                        val file = File(gasto.urlFotoRecibo)
                        val uri = FileProvider.getUriForFile(this, "com.invap.rendiciondegastos.fileprovider", file)
                        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "image/jpeg")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivity(viewIntent)
                    } catch (e: Exception) {
                        Toast.makeText(this, "No se puede abrir el recibo", Toast.LENGTH_SHORT).show()
                        Log.e("DetalleViajeActivity", "Error al abrir foto local", e)
                    }
                } else {
                    Toast.makeText(this, "Este gasto no tiene recibo", Toast.LENGTH_SHORT).show()
                }
            },
            onItemLongClicked = { gasto ->
                mostrarDialogoDeAcciones(gasto)
            }
        )
        binding.recyclerViewGastos.adapter = adapter
        binding.recyclerViewGastos.layoutManager = LinearLayoutManager(this)

        binding.fabAgregarGasto.setOnClickListener {
            val intent = Intent(this, NuevoGastoActivity::class.java)
            intent.putExtra(NuevoGastoActivity.EXTRA_VIAJE_ID, viajeId)
            intent.putExtra(NuevoGastoActivity.EXTRA_VIAJE_MONEDA_DEFECTO, monedaPorDefecto)
            intent.putExtra(NuevoGastoActivity.EXTRA_VIAJE_IMPUTACION_PT, imputacionPtPorDefecto)
            intent.putExtra(NuevoGastoActivity.EXTRA_VIAJE_IMPUTACION_WP, imputacionWpPorDefecto)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        if (viajeId != 0L) {
            cargarDatos()
        }
    }

    private fun cargarDatos() {
        lifecycleScope.launch {
            try {
                viajeActual = db.viajeDao().getViajeById(viajeId)
                if (viajeActual == null) {
                    Toast.makeText(this@DetalleViajeActivity, "Error: No se encontró el viaje", Toast.LENGTH_SHORT).show()
                    finish()
                    return@launch
                }

                val gastos = db.gastoDao().getGastosByViajeId(viajeId)
                listaDeGastos.clear()
                listaDeGastos.addAll(gastos)
                adapter.notifyDataSetChanged()

            } catch (e: Exception) {
                Log.e("DetalleViajeActivity", "Error al cargar datos de Room", e)
                Toast.makeText(this@DetalleViajeActivity, "Error al cargar datos", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.detalle_viaje_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_export_excel -> {
                exportarAExcel()
                true
            }
            R.id.action_completar_dolares -> {
                completarDolaresFaltantes()
                true
            }
            R.id.action_export_pdf -> {
                exportarRecibosAPDF()
                true
            }
            R.id.action_export_viaje_guardar -> {
                guardarViajeLauncher.launch(ViajeBackup.nombreSugerido(nombreViaje ?: "viaje"))
                true
            }
            R.id.action_export_viaje_compartir -> {
                exportarViajeCompartiendo()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    private fun nombreArchivoSeguro(nombre: String?): String =
        (nombre ?: "viaje").trim().replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_").ifEmpty { "viaje" }

    private fun exportarAExcel() {
        if (listaDeGastos.isEmpty()) {
            Toast.makeText(this, "No hay gastos para exportar", Toast.LENGTH_SHORT).show()
            return
        }
        val viaje = viajeActual
        if (viaje == null) {
            Toast.makeText(this, "Error: Datos del viaje no cargados", Toast.LENGTH_SHORT).show()
            return
        }

        val file = File(externalCacheDir, "INVAP - Rendicion_${nombreArchivoSeguro(nombreViaje)}.xlsx")
        try {
            val userPrefs = getSharedPreferences("UserPrefs_local", Context.MODE_PRIVATE)
            val tipos = userPrefs.getStringSet("TIPOS_GASTO", emptySet())?.toList() ?: emptyList()
            val logo = runCatching { resources.openRawResource(R.drawable.invap_logoverde).use { it.readBytes() } }.getOrNull()
            ExcelRendicion.generar(file, viaje, listaDeGastos.toList(), tipos, logo)
            compartirArchivoExcel(file)
        } catch (e: Exception) {
            Log.e("ExportarExcel", "Error al generar el archivo Excel", e)
            Toast.makeText(this, "Error al generar el archivo Excel: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /** Completa el importe en dólares de los gastos que no lo tienen, con las tasas de referencia (queda marcado como estimado). */
    private fun completarDolaresFaltantes() {
        val tasas = Monedas.tasasReferencia(this)
        if (tasas.isEmpty()) {
            Toast.makeText(this, "Primero cargá las tasas de referencia en Configuración", Toast.LENGTH_LONG).show()
            return
        }
        val gastos = listaDeGastos.toList()
        lifecycleScope.launch {
            var completados = 0
            var sinTasa = 0
            withContext(Dispatchers.IO) {
                for (g in gastos) {
                    if (Monedas.esDolar(g.moneda) || g.importeDolares != null) continue
                    val usd = Monedas.estimarDolares(g.monto, g.moneda, tasas)
                    if (usd == null) sinTasa++ else {
                        db.gastoDao().setDolaresEstimados(g.id, usd)
                        completados++
                    }
                }
            }
            val msg = "Importes en dólares estimados: $completados" + if (sinTasa > 0) " (sin tasa de referencia: $sinTasa)" else ""
            Toast.makeText(this@DetalleViajeActivity, msg, Toast.LENGTH_LONG).show()
            cargarDatos()
        }
    }

    private fun compartirArchivoExcel(file: File) {
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Rendición de Viaje: $nombreViaje")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Compartir Rendición"))
    }

    private fun exportarViajeAUri(uri: Uri) {
        binding.progressBarPDF.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val salida = contentResolver.openOutputStream(uri)
                    ?: throw ViajeBackupException("No se pudo abrir el destino elegido.")
                val fotosFaltantes = ViajeBackup.exportar(db, viajeId, salida)
                val aviso = if (fotosFaltantes > 0) " ($fotosFaltantes foto(s) no se encontraron en el teléfono)" else ""
                Toast.makeText(this@DetalleViajeActivity, "Viaje guardado$aviso", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Log.e("ExportarViaje", "Error al exportar el viaje", e)
                Toast.makeText(this@DetalleViajeActivity, "No se pudo guardar el viaje: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.progressBarPDF.visibility = View.GONE
            }
        }
    }

    private fun exportarViajeCompartiendo() {
        binding.progressBarPDF.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val archivo = File(externalCacheDir, ViajeBackup.nombreSugerido(nombreViaje ?: "viaje"))
                ViajeBackup.exportar(db, viajeId, FileOutputStream(archivo))
                val uri = FileProvider.getUriForFile(this@DetalleViajeActivity, "${packageName}.fileprovider", archivo)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Viaje: $nombreViaje")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, "Compartir viaje"))
            } catch (e: Exception) {
                Log.e("ExportarViaje", "Error al exportar el viaje", e)
                Toast.makeText(this@DetalleViajeActivity, "No se pudo exportar el viaje: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.progressBarPDF.visibility = View.GONE
            }
        }
    }

    private fun exportarRecibosAPDF() {
        if (listaDeGastos.isEmpty()) {
            Toast.makeText(this, "No hay gastos para exportar", Toast.LENGTH_SHORT).show()
            return
        }
        binding.progressBarPDF.visibility = View.VISIBLE
        Toast.makeText(this, "Preparando PDF...", Toast.LENGTH_SHORT).show()

        val gastosOrdenados = listaDeGastos.sortedBy { it.formaDePago }

        // Todo el trabajo pesado (leer fotos, dibujar, escribir) fuera del hilo principal
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val file = crearDocumentoPDF(gastosOrdenados)
                withContext(Dispatchers.Main) { compartirArchivoPDF(file) }
            } catch (e: Exception) {
                Log.e("ExportarPDF", "Error al generar el PDF", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@DetalleViajeActivity, "Error al generar el PDF: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                withContext(NonCancellable + Dispatchers.Main) { binding.progressBarPDF.visibility = View.GONE }
            }
        }
    }

    /**
     * Genera el PDF de recibos (A4 apaisado). Cada forma de pago empieza en página nueva.
     * Cada recibo se imprime en el tamaño elegido al cargarlo: Chico (3 por página), Mediano (2 por página)
     * o Grande (1 por página). Los recibos de una misma forma de pago se acomodan en orden de carga,
     * combinando tamaños en una página mientras entren.
     * Las fotos se cargan de a una, reducidas, y se liberan apenas se dibujan.
     */
    private fun crearDocumentoPDF(gastos: List<Gasto>): File {
        val pdfDocument = PdfDocument()
        try {
            val pageWidth = 842
            val pageHeight = 595
            val margin = 30f
            val separacion = 20f
            val anchoContenido = pageWidth - 2 * margin
            val altoContenidoRecibo = pageHeight - 2 * margin - 20f
            val xMax = margin + anchoContenido

            fun anchoSlot(t: TamanoRecibo): Float = when (t) {
                TamanoRecibo.CHICO -> (anchoContenido - 2 * separacion) / 3
                TamanoRecibo.MEDIANO -> (anchoContenido - separacion) / 2
                TamanoRecibo.GRANDE -> anchoContenido
            }

            val paintTag = TextPaint().apply {
                color = android.graphics.Color.BLACK; textSize = 30f; isFakeBoldText = true
            }
            val paintTextoGasto = TextPaint().apply {
                color = android.graphics.Color.DKGRAY; textSize = 16f
            }
            val paintSello = Paint().apply {
                color = android.graphics.Color.RED; style = Paint.Style.STROKE; strokeWidth = 2f
            }
            val paintTextoSello = Paint().apply {
                color = android.graphics.Color.RED; textSize = 16f; isFakeBoldText = true; textAlign = Paint.Align.CENTER
            }
            val paintImagen = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

            // Dibuja un gasto (TAG, datos y foto o sello) en su espacio de la página
            fun dibujarGasto(canvas: Canvas, gasto: Gasto, x: Float, ancho: Float) {
                val tamano = gasto.tamano()
                val grande = tamano == TamanoRecibo.GRANDE

                // 1. TAG
                val yPosTag = margin + 30f
                canvas.drawText(gasto.tagGasto, x, yPosTag, paintTag)

                // 2. Datos del gasto (en Grande, al costado del TAG para dejar más alto a la imagen)
                val textoGasto = "${gasto.descripcion}\n${gasto.formaDePago}\n${gasto.fecha}\n${
                    NumberFormat.getCurrencyInstance(Locale("es", "AR")).format(gasto.monto)
                } (${gasto.moneda})"
                val desplazamientoTexto = if (grande) 150f else 0f
                val textLayout = StaticLayout.Builder.obtain(
                    textoGasto, 0, textoGasto.length, paintTextoGasto, (ancho - desplazamientoTexto).toInt()
                ).build()
                val yPosTexto = if (grande) margin else yPosTag + paintTag.fontMetrics.descent + 5f
                canvas.save()
                canvas.translate(x + desplazamientoTexto, yPosTexto)
                textLayout.draw(canvas)
                canvas.restore()

                val yInicioContenido = if (grande) maxOf(yPosTag + 10f, yPosTexto + textLayout.height + 10f)
                                       else yPosTexto + textLayout.height + 10f
                val altoDisponible = altoContenidoRecibo - (yInicioContenido - margin)

                // 3. Foto (cargada solo ahora, reducida) o sello
                val maxLado = pdfMaxLadoPx(tamano)
                val archivoFoto = if (gasto.urlFotoRecibo.isNotEmpty()) File(gasto.urlFotoRecibo) else null
                val target = if (archivoFoto != null && archivoFoto.exists()) {
                    Glide.with(applicationContext).asBitmap()
                        .load(archivoFoto)
                        .signature(ObjectKey(archivoFoto.lastModified())) // si la foto se giró, no usar la versión en caché
                        .disallowHardwareConfig() // el canvas del PDF es por software
                        .downsample(DownsampleStrategy.AT_MOST)
                        .submit(maxLado, maxLado)
                } else null

                val bitmap: Bitmap? = try {
                    target?.get()
                } catch (e: Exception) {
                    Log.w("ExportarPDF", "No se pudo leer la foto de ${gasto.tagGasto}", e)
                    null
                }

                if (bitmap != null) {
                    val rotar = grande && ROTAR_GRANDE_VERTICAL && bitmap.height > bitmap.width
                    val visibleAncho = if (rotar) bitmap.height else bitmap.width
                    val visibleAlto = if (rotar) bitmap.width else bitmap.height
                    val ratio = visibleAncho.toFloat() / visibleAlto.toFloat()
                    val safeAlto = if (altoDisponible > 0) altoDisponible else 1f

                    val anchoFinal: Float
                    val altoFinal: Float
                    if (ratio > ancho / safeAlto) {
                        anchoFinal = ancho
                        altoFinal = ancho / ratio
                    } else {
                        altoFinal = safeAlto
                        anchoFinal = safeAlto * ratio
                    }
                    val xImagen = x + (ancho - anchoFinal) / 2

                    // Se dibuja el bitmap reducido (no el original): el PDF conserva la resolución de maxLado
                    if (!rotar) {
                        canvas.drawBitmap(bitmap, null,
                            RectF(xImagen, yInicioContenido, xImagen + anchoFinal, yInicioContenido + altoFinal), paintImagen)
                    } else {
                        canvas.save()
                        canvas.translate(xImagen + anchoFinal, yInicioContenido)
                        canvas.rotate(90f) // giro horario: el borde superior de la foto queda a la derecha
                        canvas.drawBitmap(bitmap, null, RectF(0f, 0f, altoFinal, anchoFinal), paintImagen)
                        canvas.restore()
                    }
                } else {
                    // Sin foto: sello. Si no está marcado "sin comprobante" es porque el archivo no está disponible.
                    val textoSello = if (gasto.sinComprobante) "SIN COMPROBANTE" else "RECIBO NO DISPONIBLE"
                    val centerY = yInicioContenido + (altoDisponible / 2)
                    val rectSello = RectF(x + 20, centerY - 25, x + ancho - 20, centerY + 25)
                    canvas.drawRoundRect(rectSello, 10f, 10f, paintSello)
                    val textY = centerY - (paintTextoSello.descent() + paintTextoSello.ascent()) / 2
                    canvas.drawText(textoSello, rectSello.centerX(), textY, paintTextoSello)
                }

                // Liberar la foto de memoria antes de pasar a la siguiente
                target?.let { Glide.with(applicationContext).clear(it) }
            }

            // Cada forma de pago se entrega e imprime por separado (junto a su planilla Excel):
            // sus recibos van en páginas consecutivas y nunca se mezclan con los de otra forma de pago.
            val grupos = gastos.groupBy { it.formaDePago }.toSortedMap(String.CASE_INSENSITIVE_ORDER)
            var paginaGlobal = 0

            for ((formaDePago, gastosGrupo) in grupos) {
                // Acomodar en páginas, en orden de carga: (gasto, posición x) por página
                val paginas = mutableListOf<MutableList<Pair<Gasto, Float>>>()
                var paginaActual = mutableListOf<Pair<Gasto, Float>>()
                var cursorX = margin
                for (gasto in gastosGrupo) {
                    val ancho = anchoSlot(gasto.tamano())
                    if (paginaActual.isNotEmpty() && cursorX + ancho > xMax + 0.5f) {
                        paginas.add(paginaActual)
                        paginaActual = mutableListOf()
                        cursorX = margin
                    }
                    paginaActual.add(gasto to cursorX)
                    cursorX += ancho + separacion
                }
                if (paginaActual.isNotEmpty()) paginas.add(paginaActual)

                paginas.forEachIndexed { indice, itemsPagina ->
                    paginaGlobal++
                    val page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, paginaGlobal).create())
                    for ((gasto, x) in itemsPagina) {
                        dibujarGasto(page.canvas, gasto, x, anchoSlot(gasto.tamano()))
                    }
                    dibujarPieDePagina(page, formaDePago, indice + 1, paginas.size)
                    pdfDocument.finishPage(page)
                }
            }

            val file = File(externalCacheDir, "Recibos_${nombreArchivoSeguro(nombreViaje)}.pdf")
            FileOutputStream(file).use { pdfDocument.writeTo(it) }
            return file
        } finally {
            pdfDocument.close()
        }
    }

    private fun dibujarPieDePagina(page: PdfDocument.Page, formaDePago: String, paginaActual: Int, totalPaginas: Int) {
        val canvas = page.canvas
        val paint = Paint().apply {
            color = android.graphics.Color.GRAY; textSize = 10f
        }

        val primerGasto = listaDeGastos.firstOrNull()
        val nombrePersona = primerGasto?.nombrePersona ?: ""
        val legajo = primerGasto?.legajo ?: ""

        val fechaCreacion = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
        val textoPie = "INVAP - Rendición de Viajes | $nombrePersona (Legajo: $legajo) | Viaje: $nombreViaje | Creado: $fechaCreacion"
        val textoPagina = "$formaDePago  |  Página $paginaActual de $totalPaginas"
        canvas.drawText(textoPie, 40f, page.info.pageHeight - 20f, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(textoPagina, page.info.pageWidth - 40f, page.info.pageHeight - 20f, paint)
    }

    private fun compartirArchivoPDF(file: File) {
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Recibos del Viaje: $nombreViaje")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Compartir Recibos en PDF"))
    }

    private fun mostrarDialogoDeAcciones(gasto: Gasto) {
        val opciones = arrayOf("Ver Recibo", "Editar Gasto", "Eliminar Gasto")
        AlertDialog.Builder(this)
            .setTitle(gasto.descripcion.ifEmpty { "(Gasto sin descripción)" })
            .setItems(opciones) { _, which ->
                when (which) {
                    0 -> {
                        if (gasto.urlFotoRecibo.isNotEmpty()) {
                            try {
                                val file = File(gasto.urlFotoRecibo)
                                val uri = FileProvider.getUriForFile(this, "com.invap.rendiciondegastos.fileprovider", file)
                                val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, "image/jpeg")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                startActivity(viewIntent)
                            } catch (e: Exception) {
                                Toast.makeText(this, "No se puede abrir el recibo", Toast.LENGTH_SHORT).show()
                                Log.e("DetalleViajeActivity", "Error al abrir foto local", e)
                            }
                        } else {
                            val mensaje = if (gasto.sinComprobante) "Marcado 'Sin comprobante'" else "Este gasto no tiene recibo"
                            Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show()
                        }
                    }
                    1 -> {
                        val intent = Intent(this, NuevoGastoActivity::class.java)
                        intent.putExtra(NuevoGastoActivity.EXTRA_VIAJE_ID, viajeId)
                        intent.putExtra(NuevoGastoActivity.EXTRA_GASTO_ID, gasto.id)
                        intent.putExtra(NuevoGastoActivity.EXTRA_GASTO_DESCRIPCION, gasto.descripcion)
                        intent.putExtra(NuevoGastoActivity.EXTRA_GASTO_MONTO, gasto.monto)
                        intent.putExtra(NuevoGastoActivity.EXTRA_GASTO_FECHA, gasto.fecha)
                        intent.putExtra(NuevoGastoActivity.EXTRA_GASTO_TIPO, gasto.tipoGasto)
                        intent.putExtra(NuevoGastoActivity.EXTRA_GASTO_MONEDA, gasto.moneda)
                        intent.putExtra(NuevoGastoActivity.EXTRA_GASTO_FORMA_PAGO, gasto.formaDePago)
                        intent.putExtra(NuevoGastoActivity.EXTRA_GASTO_URL_FOTO, gasto.urlFotoRecibo)
                        intent.putExtra(NuevoGastoActivity.EXTRA_GASTO_TAG, gasto.tagGasto)
                        intent.putExtra(NuevoGastoActivity.EXTRA_GASTO_IMPUTACION_PT, gasto.imputacionPT)
                        intent.putExtra(NuevoGastoActivity.EXTRA_GASTO_IMPUTACION_WP, gasto.imputacionWP)

                        // --- LÍNEA AÑADIDA (PASO 4) ---
                        intent.putExtra(NuevoGastoActivity.EXTRA_GASTO_SIN_COMPROBANTE, gasto.sinComprobante)
                        intent.putExtra(NuevoGastoActivity.EXTRA_GASTO_TAMANO_RECIBO, gasto.tamanoRecibo)
                        gasto.importeDolares?.let {
                            intent.putExtra(NuevoGastoActivity.EXTRA_GASTO_IMPORTE_DOLARES, it)
                            intent.putExtra(NuevoGastoActivity.EXTRA_GASTO_DOLARES_ESTIMADO, gasto.dolaresEstimado)
                        }

                        startActivity(intent)
                    }
                    2 -> {
                        mostrarDialogoDeConfirmacion(gasto)
                    }
                }
            }
            .show()
    }

    private fun mostrarDialogoDeConfirmacion(gasto: Gasto) {
        AlertDialog.Builder(this)
            .setTitle("Confirmar Eliminación")
            .setMessage("¿Estás seguro de que quieres eliminar el gasto '${gasto.descripcion}'?")
            .setPositiveButton("Eliminar") { _, _ ->
                eliminarGasto(gasto)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun eliminarGasto(gasto: Gasto) {
        lifecycleScope.launch {
            try {
                // (Se quitó la lógica de File(gasto.urlFotoRecibo).delete() según su decisión)
                db.gastoDao().deleteGasto(gasto)
                Toast.makeText(this@DetalleViajeActivity, "Gasto eliminado", Toast.LENGTH_SHORT).show()
                cargarDatos()
            } catch (e: Exception) {
                Log.e("DetalleViajeActivity", "Error al eliminar el gasto de Room", e)
                Toast.makeText(this@DetalleViajeActivity, "Error al eliminar el gasto", Toast.LENGTH_SHORT).show()
            }
        }
    }
}