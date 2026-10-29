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

/** Calidad JPEG de las fotos incrustadas en el PDF, según el tamaño de impresión. */
private fun pdfJpegCalidad(tamano: TamanoRecibo): Int = when (tamano) {
    TamanoRecibo.CHICO -> 80
    TamanoRecibo.MEDIANO -> 82
    TamanoRecibo.GRANDE -> 85
}

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

    // Excel y PDF de recibos: "guardar en..." con el selector del sistema
    private val guardarExcelLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        if (uri != null) exportarAExcel(uri)
    }
    private val guardarRecibosLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) exportarRecibosAPDF(uri)
    }

    // Informe interno: "guardar en..." con el selector del sistema
    private val guardarInformeLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) generarInformeInterno(uri)
    }

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
                exportarAExcel(null)
                true
            }
            R.id.action_export_excel_guardar -> {
                guardarExcelLauncher.launch("INVAP - Rendicion_${nombreArchivoSeguro(nombreViaje)}.xlsx")
                true
            }
            R.id.action_completar_dolares -> {
                completarDolaresFaltantes()
                true
            }
            R.id.action_informe_compartir -> {
                generarInformeInterno(null)
                true
            }
            R.id.action_informe_guardar -> {
                guardarInformeLauncher.launch("Informe interno - ${nombreArchivoSeguro(nombreViaje)}.pdf")
                true
            }
            R.id.action_export_pdf -> {
                exportarRecibosAPDF(null)
                true
            }
            R.id.action_export_pdf_guardar -> {
                guardarRecibosLauncher.launch("Recibos_${nombreArchivoSeguro(nombreViaje)}.pdf")
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

    /** Genera el Excel; con [destino] lo guarda ahí y sin destino lo comparte. */
    private fun exportarAExcel(destino: Uri?) {
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
            if (destino == null) {
                compartirArchivoExcel(file)
            } else {
                val salida = contentResolver.openOutputStream(destino) ?: throw java.io.IOException("No se pudo abrir el destino elegido")
                salida.use { out -> file.inputStream().use { it.copyTo(out) } }
                Toast.makeText(this, "Excel guardado", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e("ExportarExcel", "Error al generar el archivo Excel", e)
            Toast.makeText(this, "Error al generar el archivo Excel: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /** Genera el informe interno; con [destino] lo guarda ahí y sin destino lo comparte. */
    private fun generarInformeInterno(destino: Uri?) {
        val viaje = viajeActual
        if (viaje == null || listaDeGastos.isEmpty()) {
            Toast.makeText(this, "No hay gastos para el informe", Toast.LENGTH_SHORT).show()
            return
        }
        val gastos = listaDeGastos.toList()
        val tasas = Monedas.tasasReferencia(this)
        binding.progressBarPDF.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val file = File(externalCacheDir, "INVAP - Informe interno_${nombreArchivoSeguro(nombreViaje)}.pdf")
                InformeInterno.generar(file, viaje, gastos, tasas)
                if (destino != null) {
                    val salida = contentResolver.openOutputStream(destino) ?: throw java.io.IOException("No se pudo abrir el destino elegido")
                    salida.use { out -> file.inputStream().use { it.copyTo(out) } }
                }
                withContext(Dispatchers.Main) {
                    if (destino == null) compartirInforme(file)
                    else Toast.makeText(this@DetalleViajeActivity, "Informe guardado", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e("InformeInterno", "Error al generar el informe", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@DetalleViajeActivity, "Error al generar el informe: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                withContext(NonCancellable + Dispatchers.Main) { binding.progressBarPDF.visibility = View.GONE }
            }
        }
    }

    private fun compartirInforme(file: File) {
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Informe interno del viaje: $nombreViaje")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Compartir informe interno"))
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

    /** Genera el PDF de recibos; con [destino] lo guarda ahí y sin destino lo comparte. */
    private fun exportarRecibosAPDF(destino: Uri?) {
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
                if (destino != null) {
                    val salida = contentResolver.openOutputStream(destino) ?: throw java.io.IOException("No se pudo abrir el destino elegido")
                    salida.use { out -> file.inputStream().use { it.copyTo(out) } }
                }
                withContext(Dispatchers.Main) {
                    if (destino == null) compartirArchivoPDF(file)
                    else Toast.makeText(this@DetalleViajeActivity, "PDF de recibos guardado", Toast.LENGTH_LONG).show()
                }
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
     * Las fotos se cargan de a una, reducidas, y se incrustan como JPEG recomprimido (el PDF pesa
     * poco más que los JPEG). El PDF se escribe con [PdfSimple], sin pasar por el Canvas de Android.
     */
    private fun crearDocumentoPDF(gastos: List<Gasto>): File {
        val pdf = PdfSimple()
        val pageWidth = 842.0
        val pageHeight = 595.0
        val margin = 30.0
        val separacion = 20.0
        val anchoContenido = pageWidth - 2 * margin
        val altoContenidoRecibo = pageHeight - 2 * margin - 20.0
        val xMax = margin + anchoContenido
        val colorTexto = 0x444444
        val colorSello = 0xFF0000
        val colorSelloNormal = 0x666666

        fun anchoSlot(t: TamanoRecibo): Double = when (t) {
            TamanoRecibo.CHICO -> (anchoContenido - 2 * separacion) / 3
            TamanoRecibo.MEDIANO -> (anchoContenido - separacion) / 2
            TamanoRecibo.GRANDE -> anchoContenido
        }

        // Dibuja un gasto (TAG, datos y foto o sello) en su espacio de la página
        fun dibujarGasto(page: PdfSimple.Pagina, gasto: Gasto, x: Double, ancho: Double) {
            val tamano = gasto.tamano()
            val grande = tamano == TamanoRecibo.GRANDE

            // 1. TAG
            val yPosTag = margin + 30.0
            page.texto(x, yPosTag, gasto.tagGasto, PdfSimple.HELVETICA_NEGRITA, 30.0, 0x000000)

            // 2. Datos del gasto (en Grande, al costado del TAG para dejar más alto a la imagen)
            val montoTexto = NumberFormat.getCurrencyInstance(Locale("es", "AR")).format(gasto.monto)
                .replace(' ', ' ').replace(' ', ' ')
            val textoGasto = "${gasto.descripcion}\n${gasto.formaDePago}\n${gasto.fecha}\n$montoTexto (${gasto.moneda})"
            val desplazamientoTexto = if (grande) 150.0 else 0.0
            val lineas = PdfSimple.envolver(textoGasto, PdfSimple.HELVETICA, 16.0, ancho - desplazamientoTexto)
            val altoLinea = 19.0
            val yPosTexto = if (grande) margin else yPosTag + 8.0
            lineas.forEachIndexed { i, linea ->
                page.texto(x + desplazamientoTexto, yPosTexto + 15.0 + i * altoLinea, linea, PdfSimple.HELVETICA, 16.0, colorTexto)
            }
            val altoTexto = lineas.size * altoLinea

            val yInicioContenido = if (grande) maxOf(yPosTag + 10.0, yPosTexto + altoTexto + 10.0)
                                   else yPosTexto + altoTexto + 10.0
            val altoDisponible = altoContenidoRecibo - (yInicioContenido - margin)

            // 3. Foto (cargada solo ahora, reducida) o sello
            val maxLado = pdfMaxLadoPx(tamano)
            val archivoFoto = if (gasto.urlFotoRecibo.isNotEmpty()) File(gasto.urlFotoRecibo) else null
            val target = if (archivoFoto != null && archivoFoto.exists()) {
                Glide.with(applicationContext).asBitmap()
                    .load(archivoFoto)
                    .signature(ObjectKey(archivoFoto.lastModified())) // si la foto se giró, no usar la versión en caché
                    .disallowHardwareConfig()
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
                val ratio = visibleAncho.toDouble() / visibleAlto.toDouble()
                val safeAlto = if (altoDisponible > 0) altoDisponible else 1.0

                val anchoFinal: Double
                val altoFinal: Double
                if (ratio > ancho / safeAlto) {
                    anchoFinal = ancho
                    altoFinal = ancho / ratio
                } else {
                    altoFinal = safeAlto
                    anchoFinal = safeAlto * ratio
                }
                val xImagen = x + (ancho - anchoFinal) / 2

                // Se incrusta el JPEG reducido y recomprimido (no el original ni un bitmap sin comprimir)
                val salida = java.io.ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, pdfJpegCalidad(tamano), salida)
                val indice = pdf.agregarJpeg(salida.toByteArray(), bitmap.width, bitmap.height)
                page.imagen(indice, xImagen, yInicioContenido, anchoFinal, altoFinal, rotar)
            } else {
                // Sin foto: sello. Si no está marcado "sin comprobante" es porque el archivo no está disponible.
                val textoSello = if (gasto.sinComprobante) "SIN COMPROBANTE" else "RECIBO NO DISPONIBLE"
                val centerY = yInicioContenido + (altoDisponible / 2)
                // "Sin comprobante" es un caso normal (gris); en rojo solo cuando falta un archivo que debería estar
                val colorEstampa = if (gasto.sinComprobante) colorSelloNormal else colorSello
                page.rectBorde(x + 20, centerY - 25, ancho - 40, 50.0, 10.0, 2.0, colorEstampa)
                page.textoCentrado(x + ancho / 2, centerY + 5.5, textoSello, PdfSimple.HELVETICA_NEGRITA, 16.0, colorEstampa)
            }

            // Liberar la foto de memoria antes de pasar a la siguiente
            target?.let { Glide.with(applicationContext).clear(it) }
        }

        // Cada forma de pago se entrega e imprime por separado (junto a su planilla Excel):
        // sus recibos van en páginas consecutivas y nunca se mezclan con los de otra forma de pago.
        val grupos = gastos.groupBy { it.formaDePago }.toSortedMap(String.CASE_INSENSITIVE_ORDER)

        for ((formaDePago, gastosGrupo) in grupos) {
            // Acomodar en páginas, en orden de carga: (gasto, posición x) por página
            val paginas = mutableListOf<MutableList<Pair<Gasto, Double>>>()
            var paginaActual = mutableListOf<Pair<Gasto, Double>>()
            var cursorX = margin
            for (gasto in gastosGrupo) {
                val ancho = anchoSlot(gasto.tamano())
                if (paginaActual.isNotEmpty() && cursorX + ancho > xMax + 0.5) {
                    paginas.add(paginaActual)
                    paginaActual = mutableListOf()
                    cursorX = margin
                }
                paginaActual.add(gasto to cursorX)
                cursorX += ancho + separacion
            }
            if (paginaActual.isNotEmpty()) paginas.add(paginaActual)

            paginas.forEachIndexed { indice, itemsPagina ->
                val page = pdf.nuevaPagina(pageWidth, pageHeight)
                for ((gasto, x) in itemsPagina) {
                    dibujarGasto(page, gasto, x, anchoSlot(gasto.tamano()))
                }
                dibujarPieDePagina(page, pageWidth, pageHeight, formaDePago, indice + 1, paginas.size)
            }
        }

        val file = File(externalCacheDir, "Recibos_${nombreArchivoSeguro(nombreViaje)}.pdf")
        FileOutputStream(file).use { pdf.guardar(it) }
        return file
    }

    private fun dibujarPieDePagina(page: PdfSimple.Pagina, pageWidth: Double, pageHeight: Double, formaDePago: String, paginaActual: Int, totalPaginas: Int) {
        val primerGasto = listaDeGastos.firstOrNull()
        val nombrePersona = primerGasto?.nombrePersona ?: ""
        val legajo = primerGasto?.legajo ?: ""

        val fechaCreacion = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
        val textoPie = "INVAP - Rendición de Viajes | $nombrePersona (Legajo: $legajo) | Viaje: $nombreViaje | Creado: $fechaCreacion"
        val textoPagina = "$formaDePago  |  Página $paginaActual de $totalPaginas"
        val gris = 0x888888
        page.texto(40.0, pageHeight - 20.0, textoPie, PdfSimple.HELVETICA, 8.0, gris)
        page.textoDerecha(pageWidth - 40.0, pageHeight - 20.0, textoPagina, PdfSimple.HELVETICA, 8.0, gris)
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