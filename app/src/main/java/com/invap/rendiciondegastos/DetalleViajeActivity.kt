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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
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
        // Habilita el modo Edge-to-Edge
        WindowCompat.setDecorFitsSystemWindows(window, false)

// Aplica el relleno para las barras del sistema
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

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
    private fun getCellAddress(col: Int, row: Int): String {
        val colLetter = ('A' + col).toString()
        return "$colLetter${row + 1}"
    }

    private fun exportarAExcel() {
        if (listaDeGastos.isEmpty()) {
            Toast.makeText(this, "No hay gastos para exportar", Toast.LENGTH_SHORT).show()
            return
        }

        val fileName = "INVAP - Rendicion_${nombreViaje?.replace(" ", "_")}.xls"
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
            val boldFont14 = WritableFont(WritableFont.ARIAL, 14, WritableFont.BOLD)
            val boldFormat12 = WritableCellFormat(boldFont12)
            val boldFont = WritableFont(WritableFont.ARIAL, 10, WritableFont.BOLD)
            val boldFormat = WritableCellFormat(boldFont)
            val boldFormat14=WritableCellFormat(boldFont14)
            val centerBoldUppercaseFormat = WritableCellFormat(boldFont).apply { setAlignment(Alignment.CENTRE) }
            val normalFormat = WritableCellFormat()
            val thickHeaderFormat = WritableCellFormat(boldFont).apply {
                setBackground(Colour.GRAY_25)
                setBorder(Border.ALL, BorderLineStyle.MEDIUM)
                setAlignment(Alignment.CENTRE)
                setVerticalAlignment(VerticalAlignment.CENTRE)
                setWrap(true)
            }
            val tableCellFormat = WritableCellFormat().apply { setBorder(Border.ALL, BorderLineStyle.THIN) }
            val tableCellCenterFormat = WritableCellFormat().apply {
                setBorder(Border.ALL, BorderLineStyle.THIN)
                setAlignment(Alignment.CENTRE)
            }
            // Formatos de número específicos para cada moneda
            val pesosAccountingFormat = jxl.write.NumberFormat("$ #,##0.00")
            val dolaresAccountingFormat = jxl.write.NumberFormat(" #,##0.00")

// Formatos de celda para la tabla de
            val accountingFormat = jxl.write.NumberFormat("#,##0.00")
            val tableNumberCellFormat = WritableCellFormat(accountingFormat).apply {
                setBorder(Border.ALL, BorderLineStyle.THIN)
            }
            val tablePesosCellFormat = WritableCellFormat(pesosAccountingFormat).apply {
                setBorder(Border.ALL, BorderLineStyle.THIN)
            }
            val tableDolaresCellFormat = WritableCellFormat(dolaresAccountingFormat).apply {
                setBorder(Border.ALL, BorderLineStyle.THIN)
            }

// Formatos de celda para la fila de totales
            val totalPesosFormat = WritableCellFormat(boldFont, pesosAccountingFormat).apply {
                setBorder(Border.ALL, BorderLineStyle.MEDIUM)
            }
            val totalDolaresFormat = WritableCellFormat(boldFont, dolaresAccountingFormat).apply {
                setBorder(Border.ALL, BorderLineStyle.MEDIUM)
            }
            val totalLabelFormat = WritableCellFormat(boldFont).apply {
                setBorder(Border.ALL, BorderLineStyle.MEDIUM)
            }
            val signatureBoxFormat = WritableCellFormat().apply {
                setBorder(Border.ALL, BorderLineStyle.THIN)
            }
            val inputCellPesosFormat = WritableCellFormat(pesosAccountingFormat).apply {
                setBackground(Colour.ICE_BLUE)
                setBorder(Border.ALL, BorderLineStyle.MEDIUM)
            }
            val inputCellDolaresFormat = WritableCellFormat(dolaresAccountingFormat).apply {
                setBackground(Colour.ICE_BLUE)
                setBorder(Border.ALL, BorderLineStyle.MEDIUM)
            }
            val totalLabelBorderlessFormat = WritableCellFormat(boldFont)

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
                sheet.setRowView(5, 900)
                sheet.addCell(Label(1, 5, "INVAP - Rendición de Viajes", tituloFormat))
                sheet.addCell(Label(1, 1, "Plazos para la rendición:", boldFormat14))
                sheet.addCell(Label(1, 2, "Los anticipos deberán ser rendidos dentro de la semana de haber concluido el viaje.", boldFormat))
                sheet.addCell(Label(1, 3, "En caso de tener que realizar un nuevo desplazamiento, preovio a la solicitud de fondos debería proceder a la rendicion del anticipo pendiente", boldFormat))
                sheet.addCell(Label(1, 4, "Los anticipos tendrán vencimiento a los 30 días de la fecha de haberse hecho efectivos", boldFormat))
                sheet.addCell(Label(5, 7, "RENDICIÓN DE GASTOS REALIZADOS CON", centerBoldUppercaseFormat))
                sheet.addCell(Label(5, 8, formaDePago.uppercase(Locale.getDefault()), centerBoldUppercaseFormat))
                sheet.addCell(Label(6, 10, "Rendición de gastos de: ", normalFormat))
                sheet.addCell(Label(8, 10, primerGasto.nombrePersona, boldFormat12))
                sheet.addCell(Label(1, 11, "Viaje:", normalFormat))
                sheet.addCell(Label(2, 11, nombreViaje, boldFormat))
                sheet.addCell(Label(1, 12, "Fecha:", normalFormat))
                sheet.addCell(Label(2, 12, viajeActual?.fecha, boldFormat))
                sheet.addCell(Label(6, 11, "N° de Legajo:", normalFormat))
                sheet.addCell(Label(7, 11, "${primerGasto.legajo}", boldFormat))
                sheet.addCell(Label(6, 12, "CC:", normalFormat))
                sheet.addCell(Label(7, 12, "${primerGasto.centroCostos} ", boldFormat))

                // --- Tabla de Gastos ---
                val filaInicioTabla = 14
                val colOffset = 1
                sheet.setRowView(filaInicioTabla, 1050, false)

                val headers = mutableListOf("Comprobante")
                headers.addAll(tiposDeGastoConfigurados)
                headers.addAll(listOf("Moneda", "Importe en Pesos", "Importe en Dólares", "CC", "PT", "WP"))

                headers.forEachIndexed { index, header ->
                    sheet.addCell(Label(index + colOffset, filaInicioTabla, header, thickHeaderFormat))
                }


                val totalesPorTipoGasto = DoubleArray(tiposDeGastoConfigurados.size) { 0.0 }

                gastosDelGrupo.forEachIndexed { rowIndex, gasto ->
                    val row = filaInicioTabla + 1 + rowIndex
                    sheet.addCell(Label(0 + colOffset, row, gasto.tagGasto, tableCellCenterFormat))

                    val columnaGastoIndex = tiposDeGastoConfigurados.indexOf(gasto.tipoGasto)
                    if (columnaGastoIndex != -1) {
                        sheet.addCell(Number(columnaGastoIndex + 1 + colOffset, row, gasto.monto, tableNumberCellFormat))
                        totalesPorTipoGasto[columnaGastoIndex] += gasto.monto
                    }

                    (1..tiposDeGastoConfigurados.size).forEach { colIdx ->
                        if (colIdx != columnaGastoIndex + 1) {
                            sheet.addCell(Label(colIdx + colOffset, row, "", tableCellFormat))
                        }
                    }

                    val colMoneda = headers.indexOf("Moneda") + colOffset
                    val colPesos = headers.indexOf("Importe en Pesos") + colOffset
                    val colDolares = headers.indexOf("Importe en Dólares") + colOffset
                    sheet.addCell(Label(colMoneda, row, gasto.moneda, tableCellCenterFormat))
                    if (gasto.moneda.equals("Pesos", ignoreCase = true)) {
                        sheet.addCell(Number(colPesos, row, gasto.monto, totalPesosFormat))
                        sheet.addCell(Label(colDolares, row, "", tableCellFormat))
                    } else if (gasto.moneda.equals("Dólar", ignoreCase = true) || gasto.moneda.equals("USD", ignoreCase = true)) {
                        sheet.addCell(Label(colPesos, row, "", tableCellFormat))
                        sheet.addCell(Number(colDolares, row, gasto.monto, totalDolaresFormat))
                    } else {
                        sheet.addCell(Label(colPesos, row, "", tableCellFormat))
                        sheet.addCell(Label(colDolares, row, "", tableCellFormat))
                    }

                    sheet.addCell(Label(headers.indexOf("CC") + colOffset, row, gasto.centroCostos, tableCellCenterFormat))
                    sheet.addCell(Label(headers.indexOf("PT") + colOffset, row, gasto.imputacionPT, tableCellCenterFormat))
                    sheet.addCell(Label(headers.indexOf("WP") + colOffset, row, gasto.imputacionWP, tableCellCenterFormat))
                }

                // Fila de Totales
                val totalRowIndex = filaInicioTabla + 1 + gastosDelGrupo.size
// La celda "TOTALES" sí tiene borde
                sheet.addCell(Label(0 + colOffset, totalRowIndex, "TOTALES", totalLabelFormat))

// Las celdas vacías debajo de los tipos de gasto usan el nuevo formato SIN borde
                (1..tiposDeGastoConfigurados.size).forEach { colIdx ->
                    sheet.addCell(Label(colIdx + colOffset, totalRowIndex, "", totalLabelBorderlessFormat))
                }

                val colPesosIndex = headers.indexOf("Importe en Pesos") + colOffset
                val colDolaresIndex = headers.indexOf("Importe en Dólares") + colOffset
                val primeraFilaDatos = filaInicioTabla + 2
                val ultimaFilaDatos = totalRowIndex

// Las celdas con las fórmulas de suma SÍ tienen borde
                val colPesosLetra = ('A' + colPesosIndex)
                val formulaPesos = "SUMA(${colPesosLetra}$primeraFilaDatos:${colPesosLetra}$ultimaFilaDatos)"
                sheet.addCell(Formula(colPesosIndex, totalRowIndex, formulaPesos, totalPesosFormat))

                val colDolaresLetra = ('A' + colDolaresIndex)
                val formulaDolares = "SUMA(${colDolaresLetra}$primeraFilaDatos:${colDolaresLetra}$ultimaFilaDatos)"
                sheet.addCell(Formula(colDolaresIndex, totalRowIndex, formulaDolares, totalDolaresFormat))

// Las celdas vacías después de los totales usan el nuevo formato SIN borde
                (headers.indexOf("Importe en Dólares") + 1 until headers.size).forEach { index ->
                    sheet.addCell(Label(index + colOffset, totalRowIndex, "", totalLabelBorderlessFormat))
                }

                if (!formaDePago.contains("Crédito", ignoreCase = true) && !formaDePago.contains("Credito", ignoreCase = true)) {

                    //--- NUEVA SECCIÓN: ADELANTO, CONSUMOS, SALDO (CORREGIDA) ---
                    val filaAdelanto = totalRowIndex + 1 // Dejamos una fila en blanco
                    val colEtiqueta =
                        headers.indexOf("Importe en Pesos") - 1 // Alineado a la izquierda de los importes
// Fila 1: Recibido por Tesorería
                    sheet.addCell(
                        Label(
                            colEtiqueta,
                            filaAdelanto,
                            "Recibido por Tesorería: ",
                            boldFormat
                        )
                    )
                    sheet.addCell(
                        Label(
                            colEtiqueta + 4,
                            filaAdelanto,
                            " Completar con el monto de adelanto recibido:",
                            normalFormat
                        )
                    )
                    val recibidoPesosCell =
                        Number(colPesosIndex, filaAdelanto, 0.0, inputCellPesosFormat)
                    sheet.addCell(recibidoPesosCell)
                    val recibidoDolaresCell =
                        Number(colDolaresIndex, filaAdelanto, 0.0, inputCellDolaresFormat)
                    sheet.addCell(recibidoDolaresCell)

// Fila 2: Saldo del Anticipo
                    sheet.addCell(
                        Label(
                            colEtiqueta,
                            filaAdelanto + 1,
                            "Saldo del Anticipo:",
                            boldFormat
                        )
                    )
                    sheet.addCell(
                        Label(
                            colEtiqueta + 4,
                            filaAdelanto + 1,
                            " Completar con lo que te quedó del Anticipo",
                            normalFormat
                        )
                    )
                    val saldoPesosCell =
                        Number(colPesosIndex, filaAdelanto + 1, 0.0, inputCellPesosFormat)
                    sheet.addCell(saldoPesosCell)
                    val saldoDolaresCell =
                        Number(colDolaresIndex, filaAdelanto + 1, 0.0, inputCellDolaresFormat)
                    sheet.addCell(saldoDolaresCell)

// Fila 3: A DEVOLVER (con la fórmula que pediste)
                    sheet.addCell(Label(colEtiqueta, filaAdelanto + 2, "A DEVOLVER:", boldFormat))
// Obtenemos las direcciones de las celdas a sumar
                    val totalPesosAddress = getCellAddress(colPesosIndex, totalRowIndex)
                    val recibidoPesosAddress =
                        getCellAddress(recibidoPesosCell.column, recibidoPesosCell.row)
                    val saldoPesosAddress =
                        getCellAddress(saldoPesosCell.column, saldoPesosCell.row)
// Construimos la fórmula SIN el signo "="
                    val formulaDevolverPesos =
                        "0"//"Si((-${recibidoPesosAddress}+${totalPesosAddress}+${saldoPesosAddress})<0; -(-${recibidoPesosAddress}+${totalPesosAddress}+${saldoPesosAddress});0)"
                    sheet.addCell(
                        Formula(
                            colPesosIndex,
                            filaAdelanto + 2,
                            formulaDevolverPesos,
                            totalPesosFormat
                        )
                    )
// ... y para dólares
                    val totalDolaresAddress = getCellAddress(colDolaresIndex, totalRowIndex)
                    val recibidoDolaresAddress =
                        getCellAddress(recibidoDolaresCell.column, recibidoDolaresCell.row)
                    val saldoDolaresAddress =
                        getCellAddress(saldoDolaresCell.column, saldoDolaresCell.row)
                    val formulaDevolverDolares =
                        "0"//"${totalDolaresAddress}+${recibidoDolaresAddress}+${saldoDolaresAddress}"
                    sheet.addCell(
                        Formula(
                            colDolaresIndex,
                            filaAdelanto + 2,
                            formulaDevolverDolares,
                            totalDolaresFormat
                        )
                    )
// Fila 4: A RECUPERAR
                    val formulaRecuperarPesos =
                        "0"//"Si((-${recibidoPesosAddress}+${totalPesosAddress}+${saldoPesosAddress})<0; -(-${recibidoPesosAddress}+${totalPesosAddress}+${saldoPesosAddress});0)"
                    val formulaRecuperarDolares =
                        "0"//"${totalDolaresAddress}+${recibidoDolaresAddress}+${saldoDolaresAddress}"
                    sheet.addCell(Label(colEtiqueta, filaAdelanto + 3, "A RECUPERAR:", boldFormat))
                    sheet.addCell(
                        Formula(
                            colDolaresIndex,
                            filaAdelanto + 3,
                            formulaRecuperarDolares,
                            totalDolaresFormat
                        )
                    )
                    sheet.addCell(
                        Formula(
                            colPesosIndex,
                            filaAdelanto + 3,
                            formulaRecuperarPesos,
                            totalPesosFormat
                        )
                    )
                }
                // --- Pie de la Planilla ---
                val filaPie = totalRowIndex + 7
                sheet.setRowView(filaPie, 500); sheet.setRowView(filaPie + 2, 500)
                sheet.addCell(Label(1, filaPie, "Autorizó")); sheet.mergeCells(2, filaPie, 3, filaPie); sheet.addCell(Label(2, filaPie, "", signatureBoxFormat))
                sheet.addCell(Label(1, filaPie + 2, "Reviso")); sheet.mergeCells(2, filaPie + 2, 3, filaPie + 2); sheet.addCell(Label(2, filaPie + 2, "", signatureBoxFormat))
                sheet.mergeCells(7, filaPie, 8, filaPie); sheet.addCell(Label(7, filaPie, "", signatureBoxFormat)); sheet.addCell(Label(7, filaPie + 1, "Firma y Aclaración del titular"))
                val filaLeyendas = filaPie + 5
                sheet.addCell(Label(1, filaLeyendas, "Los gastos sin comprobante deben ser detallados",boldFormat))
                sheet.addCell(Label(1, filaLeyendas + 1, "En las facturas de restaurantes, cuando son varios comensales, debe colocarse el detalle de los mismos en el reverso",boldFormat))
                // Ancho de Columnas
                sheet.setColumnView(0, 6)
                (1 until headers.size + colOffset).forEach { col ->
                    sheet.setColumnView(col, 13)
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