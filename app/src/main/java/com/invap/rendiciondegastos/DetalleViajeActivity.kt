package com.invap.rendiciondegastos

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.invap.rendiciondegastos.databinding.ActivityDetalleViajeBinding
import jxl.CellView
import jxl.SheetSettings
import jxl.Workbook
import jxl.format.Alignment
import jxl.format.Border
import jxl.format.BorderLineStyle
import jxl.format.Colour
import jxl.format.VerticalAlignment
import jxl.write.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class DetalleViajeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetalleViajeBinding
    private var viajeId: String? = null
    private var nombreViaje: String? = null
    private var monedaPorDefecto: String? = null
    private var imputacionPtPorDefecto: String? = null
    private var imputacionWpPorDefecto: String? = null

    private val db = Firebase.firestore
    private val listaDeGastos = mutableListOf<Gasto>()
    private val listaDeViajes = mutableListOf<Viaje>()
    private lateinit var adapter: GastosAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetalleViajeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbarDetalle)

        viajeId = intent.getStringExtra("EXTRA_VIAJE_ID")
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
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(gasto.urlFotoRecibo)))
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
        if (viajeId != null) {
            cargarDatos()
        }
    }

    private fun cargarDatos() {
        cargarViajeActual()
        cargarGastos()
    }

    private fun cargarViajeActual() {
        val userId = Firebase.auth.currentUser?.uid ?: return
        db.collection("viajes")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { result ->
                listaDeViajes.clear()
                for (document in result) {
                    val viaje = document.toObject(Viaje::class.java)
                    viaje.id = document.id
                    listaDeViajes.add(viaje)
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
            R.id.action_export_pdf -> {
                exportarRecibosAPDF()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun exportarAExcel() {
        if (listaDeGastos.isEmpty()) {
            Toast.makeText(this, "No hay gastos para exportar", Toast.LENGTH_SHORT).show()
            return
        }

        val fileName = "Rendicion_${nombreViaje?.replace(" ", "_")}.xls"
        val file = File(externalCacheDir, fileName)

        try {
            val userId = Firebase.auth.currentUser?.uid ?: return
            val userPrefs = getSharedPreferences("UserPrefs_$userId", Context.MODE_PRIVATE)
            val tiposDeGastoConfigurados = userPrefs.getStringSet("TIPOS_GASTO", emptySet())?.toList()?.sorted() ?: emptyList()

            val gastosAgrupados = listaDeGastos.groupBy { it.formaDePago }
            val workbook: WritableWorkbook = Workbook.createWorkbook(file)

            // --- Definición de Formatos ---
            val tituloFont = WritableFont(WritableFont.ARIAL, 28, WritableFont.BOLD)
            tituloFont.setColour(Colour.GREEN)
            val tituloFormat = WritableCellFormat(tituloFont)

            val boldFont12 = WritableFont(WritableFont.ARIAL, 12, WritableFont.BOLD)
            val boldFormat12 = WritableCellFormat(boldFont12)

            val boldFont = WritableFont(WritableFont.ARIAL, 10, WritableFont.BOLD)
            val boldFormat = WritableCellFormat(boldFont)

            val centerBoldUppercaseFormat = WritableCellFormat(boldFont).apply {
                setAlignment(Alignment.CENTRE)
            }

            val normalFormat = WritableCellFormat()

            val thickHeaderFormat = WritableCellFormat(boldFont).apply {
                setBackground(Colour.GRAY_25)
                setBorder(Border.ALL, BorderLineStyle.MEDIUM)
                setAlignment(Alignment.CENTRE)
                setVerticalAlignment(VerticalAlignment.CENTRE)
            }
            val tableCellFormat = WritableCellFormat().apply {
                setBorder(Border.ALL, BorderLineStyle.THIN)
            }
            val tableCellCenterFormat = WritableCellFormat().apply {
                setBorder(Border.ALL, BorderLineStyle.THIN)
                setAlignment(Alignment.CENTRE)
            }
            val accountingFormat = jxl.write.NumberFormat("#,##0.00")
            val tableNumberCellFormat = WritableCellFormat(accountingFormat).apply {
                setBorder(Border.ALL, BorderLineStyle.THIN)
            }
            val totalFormat = WritableCellFormat(boldFont, accountingFormat).apply {
                setBorder(Border.ALL, BorderLineStyle.MEDIUM)
            }
            val totalLabelFormat = WritableCellFormat(boldFont).apply {
                setBorder(Border.ALL, BorderLineStyle.MEDIUM)
            }
            val signatureBoxFormat = WritableCellFormat().apply {
                setBorder(Border.ALL, BorderLineStyle.THIN)
            }

            gastosAgrupados.forEach { (formaDePago, gastosDelGrupo) ->
                val nombreHoja = formaDePago.replace(Regex("[^A-Za-z0-9]"), "").take(30)
                val sheet = workbook.createSheet(nombreHoja, workbook.numberOfSheets)

                val settings: SheetSettings = sheet.settings
                settings.setPaperSize(jxl.format.PaperSize.A4)
                settings.setOrientation(jxl.format.PageOrientation.LANDSCAPE)
                settings.setFitWidth(1)

                // --- Cabecera de la Planilla ---
                val primerGasto = gastosDelGrupo.first()
                val viajeActual = listaDeViajes.find { it.id == viajeId }

                sheet.setRowView(6, 900) // Fila 7
                sheet.addCell(Label(1, 6, "INVAP - Rendición de Viajes", tituloFormat))

                sheet.addCell(Label(1, 1, "Plazos para la rendición:", boldFormat))
                sheet.addCell(Label(1, 2, "Los anticipos deberán ser rendidos dentro de la semana de haber concluido el viaje."))
                sheet.addCell(Label(1, 3, "En caso de tener que realizar un nuevo desplazamiento..."))
                sheet.addCell(Label(1, 4, "Los anticipos tendrán vencimiento a los 30 días..."))

                sheet.addCell(Label(5, 7, "RENDICIÓN DE GASTOS REALIZADOS CON", centerBoldUppercaseFormat))
                sheet.addCell(Label(5, 8, formaDePago.uppercase(Locale.getDefault()), centerBoldUppercaseFormat))

                sheet.addCell(Label(6, 10, "RENDICIÓN DE GASTOS DE:", normalFormat))
                sheet.addCell(Label(6, 11, primerGasto.nombrePersona, boldFormat))

                sheet.addCell(Label(1, 12, "Viaje:", normalFormat))
                sheet.addCell(Label(2, 12, nombreViaje, boldFormat))

                sheet.addCell(Label(1, 13, "Fecha:", normalFormat))
                sheet.addCell(Label(2, 13, viajeActual?.fecha, boldFormat))

                sheet.addCell(Label(6, 12, "N° LEGAJO: ${primerGasto.legajo}", boldFormat))
                sheet.addCell(Label(6, 13, "CC ${primerGasto.centroCostos} - PT ${viajeActual?.imputacionPorDefectoPT}", boldFormat))

                // --- Tabla de Gastos ---
                val filaInicioTabla = 16
                val colOffset = 1
                sheet.setRowView(filaInicioTabla, 500, false)

                val headers = mutableListOf("Comprobante")
                headers.addAll(tiposDeGastoConfigurados)
                headers.addAll(listOf("Total", "CC", "PT", "WP"))

                headers.forEachIndexed { index, header ->
                    sheet.addCell(Label(index + colOffset, filaInicioTabla, header, thickHeaderFormat))
                }

                val totalesPorColumna = DoubleArray(tiposDeGastoConfigurados.size) { 0.0 }

                gastosDelGrupo.forEachIndexed { rowIndex, gasto ->
                    val row = filaInicioTabla + 1 + rowIndex
                    sheet.addCell(Label(0 + colOffset, row, gasto.tagGasto, tableCellCenterFormat))

                    val columnaGastoIndex = tiposDeGastoConfigurados.indexOf(gasto.tipoGasto)
                    if (columnaGastoIndex != -1) {
                        sheet.addCell(Number(columnaGastoIndex + 1 + colOffset, row, gasto.monto, tableNumberCellFormat))
                        totalesPorColumna[columnaGastoIndex] += gasto.monto
                    }

                    (1..tiposDeGastoConfigurados.size).forEach { colIdx ->
                        if (colIdx != columnaGastoIndex + 1) {
                            sheet.addCell(Label(colIdx + colOffset, row, "", tableCellFormat))
                        }
                    }

                    sheet.addCell(Number(headers.indexOf("Total") + colOffset, row, gasto.monto, tableNumberCellFormat))
                    sheet.addCell(Label(headers.indexOf("CC") + colOffset, row, gasto.centroCostos, tableCellCenterFormat))
                    sheet.addCell(Label(headers.indexOf("PT") + colOffset, row, gasto.imputacionPT, tableCellCenterFormat))
                    sheet.addCell(Label(headers.indexOf("WP") + colOffset, row, gasto.imputacionWP, tableCellCenterFormat))
                }

                // Fila de Totales
                val totalRowIndex = filaInicioTabla + 1 + gastosDelGrupo.size
                sheet.addCell(Label(0 + colOffset, totalRowIndex, "TOTALES", totalLabelFormat))
                var totalGeneral = 0.0
                totalesPorColumna.forEachIndexed { index, total ->
                    if (total > 0) {
                        sheet.addCell(Number(index + 1 + colOffset, totalRowIndex, total, totalFormat))
                    } else {
                        sheet.addCell(Label(index + 1 + colOffset, totalRowIndex, "", totalFormat))
                    }
                    totalGeneral += total
                }
                sheet.addCell(Number(headers.indexOf("Total") + colOffset, totalRowIndex, totalGeneral, totalFormat))
                (headers.indexOf("Total") + 1 until headers.size).forEach { index ->
                    sheet.addCell(Label(index + colOffset, totalRowIndex, "", totalLabelFormat))
                }

                // --- Pie de la Planilla ---
                val filaPie = totalRowIndex + 4
                sheet.setRowView(filaPie, 500)
                sheet.setRowView(filaPie + 2, 500)

                sheet.addCell(Label(1, filaPie, "Autorizó"))
                sheet.mergeCells(2, filaPie, 3, filaPie)
                sheet.addCell(Label(2, filaPie, "", signatureBoxFormat))

                sheet.addCell(Label(1, filaPie + 2, "Reviso"))
                sheet.mergeCells(2, filaPie + 2, 3, filaPie + 2)
                sheet.addCell(Label(2, filaPie + 2, "", signatureBoxFormat))

                sheet.mergeCells(7, filaPie, 8, filaPie)
                sheet.addCell(Label(7, filaPie, "", signatureBoxFormat))
                sheet.addCell(Label(7, filaPie + 1, "Firma y Aclaración del titular de la rendición"))

                val filaLeyendas = filaPie + 5
                sheet.addCell(Label(1, filaLeyendas, "LOS GASTOS SIN COMPROBANTE DEBEN SER DETALLADOS."))
                sheet.addCell(Label(1, filaLeyendas + 1, "LAS FACTURAS DE RESTAURANTE CUANDO SON VARIOS COMENSALES..."))

                // Ancho de Columnas
                sheet.setColumnView(0, 6) // Columna A
                (1 until headers.size + colOffset).forEach { col ->
                    sheet.setColumnView(col, 21) // Columnas de la tabla
                }
                sheet.setColumnView(headers.size + colOffset, 6)
            }

            workbook.write()
            workbook.close()
            compartirArchivoExcel(file)

        } catch (e: Exception) {
            Log.e("ExportarExcel", "Error al generar el archivo Excel", e)
            Toast.makeText(this, "Error al generar el archivo Excel: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun compartirArchivoExcel(file: File) {
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.ms-excel"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Rendición de Viaje: $nombreViaje")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Compartir Rendición"))
    }

    private fun exportarRecibosAPDF() {
        if (listaDeGastos.isEmpty()) {
            Toast.makeText(this, "No hay gastos para exportar", Toast.LENGTH_SHORT).show()
            return
        }
        binding.progressBarPDF.visibility = View.VISIBLE
        Toast.makeText(this, "Preparando PDF...", Toast.LENGTH_SHORT).show()

        val gastosOrdenados = listaDeGastos.sortedBy { it.formaDePago }
        val gastosConReciboUrl = gastosOrdenados.filter { it.urlFotoRecibo.isNotEmpty() }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val bitmapsDescargados = mutableMapOf<Gasto, Bitmap>()
                for (gasto in gastosConReciboUrl) {
                    val bitmap = Glide.with(this@DetalleViajeActivity).asBitmap().load(gasto.urlFotoRecibo).submit().get()
                    bitmapsDescargados[gasto] = bitmap
                }
                withContext(Dispatchers.Main) {
                    crearDocumentoPDF(gastosOrdenados, bitmapsDescargados)
                }
            } catch (e: Exception) {
                Log.e("ExportarPDF", "Error al descargar imágenes", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@DetalleViajeActivity, "Error al descargar recibos", Toast.LENGTH_LONG).show()
                    binding.progressBarPDF.visibility = View.GONE
                }
            }
        }
    }

    private fun crearDocumentoPDF(gastos: List<Gasto>, bitmaps: Map<Gasto, Bitmap>) {
        val pdfDocument = PdfDocument()
        val pageWidth = 842
        val pageHeight = 595
        val margin = 40f
        val recibosPorPagina = 3
        val espacioEntreRecibos = 20f

        val anchoContenido = pageWidth - 2 * margin
        val anchoRecibo = (anchoContenido - (recibosPorPagina - 1) * espacioEntreRecibos) / recibosPorPagina
        val altoRecibo = pageHeight - 2 * margin - 60f

        val paintTag = TextPaint().apply {
            color = android.graphics.Color.BLACK; textSize = 30f; isFakeBoldText = true
        }
        val paintTextoGasto = TextPaint().apply {
            color = android.graphics.Color.DKGRAY; textSize = 20f
        }
        val paintSello = Paint().apply {
            color = android.graphics.Color.RED; style = Paint.Style.STROKE; strokeWidth = 2f
        }
        val paintTextoSello = Paint().apply {
            color = android.graphics.Color.RED; textSize = 24f; isFakeBoldText = true; textAlign = Paint.Align.CENTER
        }

        val totalGastos = gastos.size
        var gastoIndex = 0

        while (gastoIndex < totalGastos) {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, (gastoIndex / recibosPorPagina) + 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            for (i in 0 until recibosPorPagina) {
                if (gastoIndex >= totalGastos) break
                val gasto = gastos[gastoIndex]
                val xOffset = margin + i * (anchoRecibo + espacioEntreRecibos)
                val yPosTag = margin + 30f
                canvas.drawText(gasto.tagGasto, xOffset, yPosTag, paintTag)

                val bitmap = bitmaps[gasto]
                if (bitmap != null) {
                    val scale = anchoRecibo / bitmap.width.toFloat()
                    val nuevoAlto = bitmap.height * scale
                    val scaledBitmap = Bitmap.createScaledBitmap(bitmap, anchoRecibo.toInt(), nuevoAlto.toInt(), true)
                    canvas.drawBitmap(scaledBitmap, xOffset, yPosTag + 10f, null)
                } else {
                    val textoSinRecibo = "${gasto.descripcion}\n${gasto.formaDePago}\n${gasto.fecha}\n${
                        NumberFormat.getCurrencyInstance(Locale("es", "AR")).format(gasto.monto)
                    } (${gasto.moneda})"
                    val textLayout = StaticLayout.Builder.obtain(textoSinRecibo, 0, textoSinRecibo.length, paintTextoGasto, anchoRecibo.toInt()).build()
                    canvas.save()
                    canvas.translate(xOffset, yPosTag + 60f)
                    textLayout.draw(canvas)
                    canvas.restore()
                    val rectSello = RectF(xOffset + 20, yPosTag + altoRecibo - 80f, xOffset + anchoRecibo - 20, yPosTag + altoRecibo - 30f)
                    canvas.drawRoundRect(rectSello, 10f, 10f, paintSello)
                    canvas.drawText("SIN RECIBO", rectSello.centerX(), rectSello.centerY() + 8, paintTextoSello)
                }
                gastoIndex++
            }
            dibujarPieDePagina(page, (gastoIndex - 1) / recibosPorPagina + 1, (totalGastos + recibosPorPagina - 1) / recibosPorPagina)
            pdfDocument.finishPage(page)
        }
        try {
            val fileName = "Recibos_${nombreViaje?.replace(" ", "_")}.pdf"
            val file = File(externalCacheDir, fileName)
            FileOutputStream(file).use { pdfDocument.writeTo(it) }
            pdfDocument.close()
            compartirArchivoPDF(file)
        } catch (e: Exception) {
            Log.e("ExportarPDF", "Error al guardar el PDF", e)
            Toast.makeText(this, "Error al guardar el PDF", Toast.LENGTH_LONG).show()
        } finally {
            binding.progressBarPDF.visibility = View.GONE
        }
    }

    private fun dibujarPieDePagina(page: PdfDocument.Page, paginaActual: Int, totalPaginas: Int) {
        val canvas = page.canvas
        val paint = Paint().apply {
            color = android.graphics.Color.GRAY; textSize = 10f
        }

        val primerGasto = listaDeGastos.firstOrNull()
        val nombrePersona = primerGasto?.nombrePersona ?: ""
        val legajo = primerGasto?.legajo ?: ""

        val fechaCreacion = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
        val textoPie = "INVAP - Rendición de Viajes | $nombrePersona (Legajo: $legajo) | Viaje: $nombreViaje | Creado: $fechaCreacion"
        val textoPagina = "Página $paginaActual de $totalPaginas"
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
            .setTitle(gasto.descripcion)
            .setItems(opciones) { _, which ->
                when (which) {
                    0 -> {
                        if (gasto.urlFotoRecibo.isNotEmpty()) {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(gasto.urlFotoRecibo)))
                        } else {
                            Toast.makeText(this, "Este gasto no tiene un recibo adjunto", Toast.LENGTH_SHORT).show()
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
        if (gasto.id.isEmpty()) {
            Toast.makeText(this, "Error: ID del gasto no encontrado", Toast.LENGTH_SHORT).show()
            return
        }
        db.collection("gastos").document(gasto.id)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Gasto eliminado", Toast.LENGTH_SHORT).show()
                cargarGastos()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error al eliminar el gasto", Toast.LENGTH_SHORT).show()
            }
    }

    private fun cargarGastos() {
        val userId = Firebase.auth.currentUser?.uid
        if (userId == null || viajeId == null) return
        db.collection("gastos")
            .whereEqualTo("viajeId", viajeId)
            .whereEqualTo("userId", userId)
            .orderBy("timestamp")
            .get()
            .addOnSuccessListener { result ->
                listaDeGastos.clear()
                for (document in result) {
                    val gasto = document.toObject(Gasto::class.java)
                    gasto.id = document.id
                    listaDeGastos.add(gasto)
                }
                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener { exception ->
                Log.w("DetalleViajeActivity", "Error al cargar gastos.", exception)
                Toast.makeText(this, "Error al cargar gastos.", Toast.LENGTH_SHORT).show()
            }
    }
}