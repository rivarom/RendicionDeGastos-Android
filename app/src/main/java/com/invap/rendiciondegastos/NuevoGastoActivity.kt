package com.invap.rendiciondegastos

import android.Manifest
import android.app.DatePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.signature.ObjectKey
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.invap.rendiciondegastos.databinding.ActivityNuevoGastoBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class NuevoGastoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNuevoGastoBinding
    private lateinit var db: AppDatabase

    private var viajeId: Long = 0L
    private var idGastoAEditar: Long = 0L

    private var fotoUri: Uri? = null
    private var pathFotoLocal: String? = null
    private var urlFotoExistente: String? = null
    // Giro (horario, múltiplo de 90) elegido en el formulario; se aplica a la foto recién al guardar
    private var rotacionPendiente = 0

    private val formasPagoList = mutableListOf<FormaDePago>()
    private val imputacionesList = mutableListOf<Imputacion>()

    private val requestCameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) { abrirCamara() }
            else { Toast.makeText(this, "Permiso de cámara necesario", Toast.LENGTH_SHORT).show() }
        }

    private val cameraLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) {
                val ruta = pathFotoLocal
                lifecycleScope.launch {
                    // La foto de la cámara (12 MP) se reduce y se endereza según su EXIF antes de mostrarla
                    if (ruta != null) {
                        withContext(Dispatchers.IO) {
                            runCatching { FotoUtil.reducir(File(ruta)) }
                                .onFailure { Log.w("NuevoGastoActivity", "No se pudo reducir la foto", it) }
                        }
                    }
                    val archivo = if (ruta != null) File(ruta) else null
                    val glide = Glide.with(this@NuevoGastoActivity)
                    (if (archivo != null) glide.load(archivo).signature(ObjectKey(archivo.lastModified())) else glide.load(fotoUri))
                        .into(binding.imageViewFotoRecibo)
                    binding.imageViewFotoRecibo.visibility = View.VISIBLE
                    rotacionPendiente = 0
                    binding.imageViewFotoRecibo.rotation = 0f
                    binding.buttonGirarFoto.visibility = View.VISIBLE
                    urlFotoExistente = null
                }
            } else {
                pathFotoLocal = null
            }
        }

    // Resultado del escáner de documentos (una sola página, ya recortada, girada y corregida)
    private val escanerLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { resultado ->
        if (resultado.resultCode == android.app.Activity.RESULT_OK) {
            val uri = GmsDocumentScanningResult.fromActivityResultIntent(resultado.data)?.pages?.firstOrNull()?.imageUri
            if (uri != null) {
                guardarFotoEscaneada(uri)
            } else {
                Toast.makeText(this, "No se obtuvo la imagen escaneada", Toast.LENGTH_SHORT).show()
            }
        }
        // Si el usuario cancela el escáner no se cambia nada: se conserva la foto que hubiera
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityNuevoGastoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = AppDatabase.getInstance(applicationContext)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        viajeId = intent.getLongExtra(EXTRA_VIAJE_ID, 0L)
        idGastoAEditar = intent.getLongExtra(EXTRA_GASTO_ID, 0L)

        if (viajeId == 0L) {
            Toast.makeText(this, "Error: ID de viaje no válido", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        tasasReferencia = Monedas.tasasReferencia(this)
        cargarOpcionesDesplegables()
        configurarCampoDeFecha()
        configurarValidacionEnTiempoReal()
        configurarImporteDolares()

        if (idGastoAEditar == 0L) { // Modo Nuevo Gasto
            val monedaDefecto = intent.getStringExtra(EXTRA_VIAJE_MONEDA_DEFECTO)
            if (!monedaDefecto.isNullOrEmpty()) {
                binding.autoCompleteMoneda.setText(monedaDefecto, false)
            }
            val ptDefecto = intent.getStringExtra(EXTRA_VIAJE_IMPUTACION_PT)
            val wpDefecto = intent.getStringExtra(EXTRA_VIAJE_IMPUTACION_WP)
            if (!ptDefecto.isNullOrEmpty() && !wpDefecto.isNullOrEmpty()) {
                binding.autoCompleteImputacion.setText("PT: $ptDefecto / WP: $wpDefecto", false)
            }
        } else { // Modo Edición
            prepararModoEdicion()
        }

        binding.buttonGirarFoto.setOnClickListener {
            rotacionPendiente = (rotacionPendiente + 90) % 360
            binding.imageViewFotoRecibo.rotation = rotacionPendiente.toFloat()
        }

        binding.buttonTomarFoto.setOnClickListener {
            abrirEscaner()
        }

        // --- LÓGICA DE CHECKBOX AÑADIDA ---
        binding.checkboxSinComprobante.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Si se marca "Sin comprobante", deshabilitar la cámara y limpiar fotos
                binding.buttonTomarFoto.isEnabled = false
                binding.imageViewFotoRecibo.visibility = View.GONE
                fotoUri = null
                pathFotoLocal = null
                urlFotoExistente = null
                binding.imageViewFotoRecibo.setImageURI(null)
                rotacionPendiente = 0
                binding.imageViewFotoRecibo.rotation = 0f
                binding.buttonGirarFoto.visibility = View.GONE
                habilitarSelectorTamano(false)
            } else {
                // Si se desmarca, habilitar la cámara
                binding.buttonTomarFoto.isEnabled = true
                habilitarSelectorTamano(true)
            }
        }
        // --- FIN LÓGICA AÑADIDA ---

        binding.buttonGuardarGasto.setOnClickListener {
            if (validarCampos()) {
                guardarGasto()
            } else {
                Toast.makeText(this, "Por favor, completa los campos obligatorios", Toast.LENGTH_SHORT).show()
            }
        }

        validarCampos()
    }

    private fun tamanoSeleccionado(): TamanoRecibo = when (binding.radioGroupTamanoRecibo.checkedRadioButtonId) {
        R.id.radioTamanoMediano -> TamanoRecibo.MEDIANO
        R.id.radioTamanoGrande -> TamanoRecibo.GRANDE
        else -> TamanoRecibo.CHICO
    }

    private fun seleccionarTamano(tamano: TamanoRecibo) {
        binding.radioGroupTamanoRecibo.check(when (tamano) {
            TamanoRecibo.MEDIANO -> R.id.radioTamanoMediano
            TamanoRecibo.GRANDE -> R.id.radioTamanoGrande
            TamanoRecibo.CHICO -> R.id.radioTamanoChico
        })
    }

    /** Sin comprobante no hay foto que dimensionar: el selector se deshabilita y queda en Chico. */
    private fun habilitarSelectorTamano(habilitado: Boolean) {
        if (!habilitado) seleccionarTamano(TamanoRecibo.CHICO)
        for (i in 0 until binding.radioGroupTamanoRecibo.childCount) {
            binding.radioGroupTamanoRecibo.getChildAt(i).isEnabled = habilitado
        }
    }

    // --- Importe en dólares (referencia, opcional) ---
    private var tasasReferencia: Map<String, Double> = emptyMap()
    private var dolaresManual = false          // true si el usuario escribió el valor (no es estimado)
    private var actualizandoDolares = false    // evita tratar como "manual" un cambio hecho por el código

    private fun configurarImporteDolares() {
        val recalcular = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { actualizarCampoDolares() }
        }
        binding.autoCompleteMoneda.addTextChangedListener(recalcular)
        binding.editTextMontoGasto.addTextChangedListener(recalcular)
        binding.editTextImporteDolares.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (actualizandoDolares) return
                dolaresManual = !s.isNullOrBlank()
                if (!dolaresManual) actualizarCampoDolares() // vació el campo: vuelve a estimarse
                else aplicarEstiloDolares()
            }
        })
    }

    /** Muestra el campo solo para monedas distintas del dólar y, mientras no sea manual, lo estima con la tasa de referencia. */
    private fun actualizarCampoDolares() {
        val moneda = binding.autoCompleteMoneda.text?.toString()?.trim().orEmpty()
        val visible = moneda.isNotEmpty() && !Monedas.esDolar(moneda)
        binding.layoutImporteDolares.visibility = if (visible) View.VISIBLE else View.GONE
        if (!visible || dolaresManual) return
        val monto = binding.editTextMontoGasto.text?.toString()?.trim()?.toDoubleOrNull()
        val tasa = tasasReferencia[Monedas.normalizar(moneda)]
        val estimado = if (monto != null && tasa != null) monto / tasa else null
        actualizandoDolares = true
        binding.editTextImporteDolares.setText(estimado?.let { String.format(Locale.US, "%.2f", it) } ?: "")
        actualizandoDolares = false
        aplicarEstiloDolares()
    }

    private var colorFondoDolaresNormal: Int? = null

    /** Estimado: fondo celeste (igual que en el Excel). Ingresado a mano: aspecto normal. */
    private fun aplicarEstiloDolares() {
        val layout = binding.layoutImporteDolares
        if (colorFondoDolaresNormal == null) colorFondoDolaresNormal = layout.boxBackgroundColor
        val moneda = binding.autoCompleteMoneda.text?.toString()?.trim().orEmpty()
        val hayValor = !binding.editTextImporteDolares.text.isNullOrBlank()
        val estimado = hayValor && !dolaresManual
        layout.boxBackgroundColor = if (estimado) 0x663F8FD2 else (colorFondoDolaresNormal ?: layout.boxBackgroundColor)
        // Indicadores que no dependen del estilo del campo: etiqueta y color del texto
        layout.hint = when {
            estimado -> "Importe en dólares (ESTIMADO)"
            hayValor -> "Importe en dólares (verificado)"
            else -> "Importe en dólares (opcional)"
        }
        val colorTexto = if (estimado) android.graphics.Color.rgb(30, 110, 200) else null
        if (colorTexto != null) binding.editTextImporteDolares.setTextColor(colorTexto)
        else binding.editTextImporteDolares.setTextColor(binding.editTextDescripcionGasto.textColors)
        binding.editTextImporteDolares.setTypeface(null, if (estimado) android.graphics.Typeface.ITALIC else android.graphics.Typeface.NORMAL)
        layout.helperText = when {
            estimado -> "Estimado con la tasa de referencia; si lo editás pasa a valor verificado"
            hayValor -> "Valor ingresado (verificado)"
            Monedas.tasaDe(tasasReferencia, moneda) == null -> "Sin tasa de referencia para esta moneda (se carga en Configuración)"
            else -> null
        }
    }

    private fun configurarValidacionEnTiempoReal() {
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                validarCampos()
            }
        }

        binding.autoCompleteTipoGasto.addTextChangedListener(textWatcher)
        binding.autoCompleteImputacion.addTextChangedListener(textWatcher)
        binding.autoCompleteFormaPago.addTextChangedListener(textWatcher)
        binding.autoCompleteMoneda.addTextChangedListener(textWatcher)
        binding.editTextMontoGasto.addTextChangedListener(textWatcher)
    }

    private fun validarCampos(): Boolean {
        val tipoGastoValido = !(binding.autoCompleteTipoGasto.text?.toString().isNullOrEmpty())
        binding.autoCompleteTipoGasto.error = if (tipoGastoValido) null else "Campo obligatorio"
        val imputacionValida = !(binding.autoCompleteImputacion.text?.toString().isNullOrEmpty())
        binding.autoCompleteImputacion.error = if (imputacionValida) null else "Campo obligatorio"
        val formaPagoValida = !(binding.autoCompleteFormaPago.text?.toString().isNullOrEmpty())
        binding.autoCompleteFormaPago.error = if (formaPagoValida) null else "Campo obligatorio"
        val monedaValida = !(binding.autoCompleteMoneda.text?.toString().isNullOrEmpty())
        binding.autoCompleteMoneda.error = if (monedaValida) null else "Campo obligatorio"
        val montoValido = !(binding.editTextMontoGasto.text?.toString().isNullOrEmpty())
        binding.editTextMontoGasto.error = if (montoValido) null else "Campo obligatorio"
        val esValido = tipoGastoValido && imputacionValida && formaPagoValida && monedaValida && montoValido
        binding.buttonGuardarGasto.isEnabled = esValido
        return esValido
    }

    private fun guardarGasto() {
        binding.buttonGuardarGasto.isEnabled = false

        val descripcion = binding.editTextDescripcionGasto.text.toString().trim()
        val montoStr = binding.editTextMontoGasto.text.toString().trim()
        val fecha = binding.editTextFechaGasto.text.toString().trim()
        val tipoGasto = binding.autoCompleteTipoGasto.text.toString()
        val moneda = binding.autoCompleteMoneda.text.toString()
        val formaDePagoNombre = binding.autoCompleteFormaPago.text.toString()
        val imputacionSeleccionadaStr = binding.autoCompleteImputacion.text.toString()

        // --- LÓGICA DE CHECKBOX AÑADIDA ---
        val esSinComprobante = binding.checkboxSinComprobante.isChecked

        // Importe en dólares: solo aplica a monedas distintas del dólar
        val usdTexto = binding.editTextImporteDolares.text?.toString()?.trim()?.replace(',', '.')
        val importeDolares = if (Monedas.esDolar(moneda)) null else usdTexto?.toDoubleOrNull()
        val dolaresEstimado = importeDolares != null && !dolaresManual

        val formaDePagoSeleccionada = formasPagoList.find { it.nombre == formaDePagoNombre }
        val imputacionSeleccionada = imputacionesList.find { "PT: ${it.pt} / WP: ${it.wp}" == imputacionSeleccionadaStr }

        if (formaDePagoSeleccionada == null || imputacionSeleccionada == null) {
            Toast.makeText(this, "Por favor, selecciona valores válidos de las listas", Toast.LENGTH_SHORT).show()
            binding.buttonGuardarGasto.isEnabled = true
            return
        }

        // Según el manual: sin "Gasto sin comprobante" la foto del recibo es obligatoria
        val tieneFoto = pathFotoLocal != null || !urlFotoExistente.isNullOrEmpty()
        if (!esSinComprobante && !tieneFoto) {
            Toast.makeText(this, "Tomá la foto del recibo o marcá 'Gasto sin comprobante'", Toast.LENGTH_LONG).show()
            binding.buttonGuardarGasto.isEnabled = true
            return
        }

        val userPrefs = getSharedPreferences("UserPrefs_local", Context.MODE_PRIVATE)
        val nombrePersona = userPrefs.getString("NOMBRE_PERSONA", "") ?: ""
        val legajo = userPrefs.getString("LEGAJO", "") ?: ""
        val centroCostos = userPrefs.getString("CENTRO_COSTOS", "") ?: ""

        lifecycleScope.launch {
            try {
                // Aplicar el giro elegido a la foto recién ahora, para que cancelar el formulario no la modifique
                val rutaFotoAGirar = if (esSinComprobante) null else (pathFotoLocal ?: urlFotoExistente)
                if (rotacionPendiente != 0 && !rutaFotoAGirar.isNullOrEmpty()) {
                    withContext(Dispatchers.IO) { FotoUtil.girarJpeg(File(rutaFotoAGirar), rotacionPendiente) }
                    rotacionPendiente = 0
                }

                val tag: String
                val urlFotoFinal: String
                var timestampGasto = System.currentTimeMillis()

                if (idGastoAEditar != 0L) { // Editando
                    // Conservar el momento de creación original (define el orden en las listas)
                    db.gastoDao().getGastoById(idGastoAEditar)?.let { timestampGasto = it.timestamp }
                    val tagOriginal = intent.getStringExtra(EXTRA_GASTO_TAG) ?: ""
                    val formaOriginal = intent.getStringExtra(EXTRA_GASTO_FORMA_PAGO) ?: ""
                    // Si cambió la forma de pago, el TAG debe llevar el prefijo y numeración nuevos
                    tag = if (tagOriginal.isNotEmpty() && formaOriginal == formaDePagoNombre) {
                        tagOriginal
                    } else {
                        generarTag(formaDePagoSeleccionada.prefijo, formaDePagoNombre)
                    }
                    // MODIFICADO: Si está marcado, no guarda foto.
                    urlFotoFinal = if (esSinComprobante) "" else (pathFotoLocal ?: (urlFotoExistente ?: ""))
                } else { // Creando
                    tag = generarTag(formaDePagoSeleccionada.prefijo, formaDePagoNombre)
                    // MODIFICADO: Si está marcado, no guarda foto.
                    urlFotoFinal = if (esSinComprobante) "" else (pathFotoLocal ?: "")
                }

                val gasto = Gasto(
                    id = idGastoAEditar,
                    viajeId = viajeId,
                    descripcion = descripcion,
                    monto = montoStr.toDouble(),
                    fecha = fecha,
                    urlFotoRecibo = urlFotoFinal,
                    moneda = moneda,
                    tipoGasto = tipoGasto,
                    formaDePago = formaDePagoNombre,
                    tagGasto = tag,
                    imputacionPT = imputacionSeleccionada.pt,
                    imputacionWP = imputacionSeleccionada.wp,
                    nombrePersona = nombrePersona,
                    legajo = legajo,
                    centroCostos = centroCostos,
                    timestamp = timestampGasto,
                    sinComprobante = esSinComprobante, // --- CAMPO NUEVO GUARDADO ---
                    tamanoRecibo = if (esSinComprobante) TamanoRecibo.CHICO.name else tamanoSeleccionado().name,
                    importeDolares = importeDolares,
                    dolaresEstimado = dolaresEstimado
                )

                guardarDatosEnRoom(gasto)

            } catch (e: Exception) {
                Log.e("NuevoGastoActivity", "Error al calcular TAG o guardar", e)
                Toast.makeText(this@NuevoGastoActivity, "Error al guardar: ${e.message}", Toast.LENGTH_LONG).show()
                binding.buttonGuardarGasto.isEnabled = true
            }
        }
    }

    /**
     * Siguiente TAG libre para la forma de pago en este viaje: toma el mayor número ya usado + 1
     * (no cuenta filas, para no duplicar TAGs cuando se borró un gasto intermedio).
     */
    private suspend fun generarTag(prefijo: String, formaDePago: String): String {
        val existentes = db.gastoDao().getTagsByFormaDePago(viajeId, formaDePago).toSet()
        val maximo = existentes.maxOfOrNull { Regex("(\\d+)\$").find(it)?.value?.toIntOrNull() ?: 0 } ?: 0
        var n = maximo + 1
        while (existentes.contains("$prefijo$n")) n++
        return "$prefijo$n"
    }

    private fun guardarDatosEnRoom(gasto: Gasto) {
        lifecycleScope.launch {
            try {
                val mensaje: String
                if (gasto.id == 0L) {
                    db.gastoDao().insertGasto(gasto)
                    mensaje = "Gasto guardado"
                } else {
                    db.gastoDao().updateGasto(gasto)
                    mensaje = "Gasto actualizado"
                }
                Toast.makeText(this@NuevoGastoActivity, mensaje, Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                Log.e("NuevoGastoActivity", "Error al escribir en Room", e)
                Toast.makeText(this@NuevoGastoActivity, "Error al guardar en base de datos", Toast.LENGTH_SHORT).show()
                binding.buttonGuardarGasto.isEnabled = true
            }
        }
    }

    private fun configurarCampoDeFecha() {
        if (idGastoAEditar == 0L) {
            val calendario = Calendar.getInstance()
            val formatoFecha = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            binding.editTextFechaGasto.setText(formatoFecha.format(calendario.time))
        }
        binding.editTextFechaGasto.setOnClickListener {
            mostrarDatePickerDialog()
        }
    }

    private fun mostrarDatePickerDialog() {
        val calendario = Calendar.getInstance()
        val anio = calendario.get(Calendar.YEAR)
        val mes = calendario.get(Calendar.MONTH)
        val dia = calendario.get(Calendar.DAY_OF_MONTH)
        val datePickerDialog = DatePickerDialog(this, { _, year, month, dayOfMonth ->
            val fechaSeleccionada = Calendar.getInstance()
            fechaSeleccionada.set(year, month, dayOfMonth)
            val formatoFecha = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            binding.editTextFechaGasto.setText(formatoFecha.format(fechaSeleccionada.time))
        }, anio, mes, dia)
        datePickerDialog.show()
    }

    private fun prepararModoEdicion() {
        binding.buttonGuardarGasto.text = "Actualizar Gasto"
        binding.editTextDescripcionGasto.setText(intent.getStringExtra(EXTRA_GASTO_DESCRIPCION))
        binding.editTextMontoGasto.setText(java.math.BigDecimal.valueOf(intent.getDoubleExtra(EXTRA_GASTO_MONTO, 0.0)).stripTrailingZeros().toPlainString())
        binding.editTextFechaGasto.setText(intent.getStringExtra(EXTRA_GASTO_FECHA))
        binding.autoCompleteTipoGasto.setText(intent.getStringExtra(EXTRA_GASTO_TIPO), false)
        binding.autoCompleteMoneda.setText(intent.getStringExtra(EXTRA_GASTO_MONEDA), false)
        binding.autoCompleteFormaPago.setText(intent.getStringExtra(EXTRA_GASTO_FORMA_PAGO), false)
        val pt = intent.getStringExtra(EXTRA_GASTO_IMPUTACION_PT)
        val wp = intent.getStringExtra(EXTRA_GASTO_IMPUTACION_WP)
        if (pt != null && wp != null) {
            binding.autoCompleteImputacion.setText("PT: $pt / WP: $wp", false)
        }

        // Importe en dólares guardado (manual o estimado)
        if (intent.hasExtra(EXTRA_GASTO_IMPORTE_DOLARES)) {
            val usd = intent.getDoubleExtra(EXTRA_GASTO_IMPORTE_DOLARES, 0.0)
            dolaresManual = !intent.getBooleanExtra(EXTRA_GASTO_DOLARES_ESTIMADO, false)
            actualizandoDolares = true
            binding.editTextImporteDolares.setText(String.format(Locale.US, "%.2f", usd))
            actualizandoDolares = false
            binding.layoutImporteDolares.visibility = View.VISIBLE
            aplicarEstiloDolares()
        } else {
            dolaresManual = false
            actualizarCampoDolares()
        }

        // --- LÓGICA DE CHECKBOX AÑADIDA ---
        val sinComprobante = intent.getBooleanExtra(EXTRA_GASTO_SIN_COMPROBANTE, false)
        binding.checkboxSinComprobante.isChecked = sinComprobante
        if (sinComprobante) {
            binding.buttonTomarFoto.isEnabled = false
            habilitarSelectorTamano(false)
        } else {
            seleccionarTamano(TamanoRecibo.desde(intent.getStringExtra(EXTRA_GASTO_TAMANO_RECIBO)))
        }
        // --- FIN LÓGICA AÑADIDA ---

        urlFotoExistente = intent.getStringExtra(EXTRA_GASTO_URL_FOTO)
        // MODIFICADO: Solo mostrar la foto si hay URL Y no está marcado "Sin comprobante"
        if (!urlFotoExistente.isNullOrEmpty() && !sinComprobante) {
            binding.imageViewFotoRecibo.visibility = View.VISIBLE
            binding.buttonGirarFoto.visibility = View.VISIBLE
            try {
                val archivoExistente = File(urlFotoExistente.orEmpty())
                Glide.with(this).load(archivoExistente)
                    .signature(ObjectKey(archivoExistente.lastModified())) // si la foto se giró, no usar la versión en caché
                    .into(binding.imageViewFotoRecibo)
            } catch (e: Exception) {
                Log.e("NuevoGastoActivity", "Error al cargar imagen en modo edición", e)
                binding.imageViewFotoRecibo.visibility = View.GONE
                Toast.makeText(this, "No se pudo cargar la foto", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun cargarOpcionesDesplegables() {
        val userPrefs = getSharedPreferences("UserPrefs_local", Context.MODE_PRIVATE)

        val monedas = userPrefs.getStringSet("MONEDAS", setOf("Pesos", "Dólar"))?.toList()?.sorted() ?: listOf("Pesos", "Dólar")
        val monedasAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, monedas)
        binding.autoCompleteMoneda.setAdapter(monedasAdapter)

        val tiposGasto = userPrefs.getStringSet("TIPOS_GASTO", setOf("Transporte", "Comida"))?.toList()?.sorted() ?: listOf("Transporte", "Comida")
        val tiposGastoAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, tiposGasto)
        binding.autoCompleteTipoGasto.setAdapter(tiposGastoAdapter)

        val formasPagoGuardadas = userPrefs.getStringSet("FORMAS_PAGO", setOf("Tarjeta de Crédito::TC", "Efectivo::EFE"))
        formasPagoList.clear()
        (formasPagoGuardadas ?: emptySet()).forEach {
            val partes = it.split("::")
            if (partes.size == 2) {
                formasPagoList.add(FormaDePago(partes[0], partes[1]))
            }
        }
        val formasPagoNombres = formasPagoList.map { it.nombre }.sorted()
        val formasPagoAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, formasPagoNombres)
        binding.autoCompleteFormaPago.setAdapter(formasPagoAdapter)

        val imputacionesGuardadas = userPrefs.getStringSet("IMPUTACIONES", setOf("00::00"))
        imputacionesList.clear()
        (imputacionesGuardadas ?: emptySet()).forEach {
            val partes = it.split("::")
            if (partes.size == 2) {
                imputacionesList.add(Imputacion(partes[0], partes[1]))
            }
        }
        val imputacionesFormateadas = imputacionesList.map { "PT: ${it.pt} / WP: ${it.wp}" }.sorted()
        val imputacionesAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, imputacionesFormateadas)
        binding.autoCompleteImputacion.setAdapter(imputacionesAdapter)
    }

    /**
     * Abre el escáner de documentos de Google. Si no está disponible (sin Google Play services,
     * poca RAM, etc.) se usa la cámara común como respaldo.
     */
    private fun abrirEscaner() {
        val opciones = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(false)
            .setPageLimit(1)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(opciones)
            .getStartScanIntent(this)
            .addOnSuccessListener { intentSender ->
                escanerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener { e ->
                Log.w("NuevoGastoActivity", "Escáner no disponible, se usa la cámara común", e)
                Toast.makeText(this, "Escáner no disponible: se usa la cámara común", Toast.LENGTH_SHORT).show()
                abrirCamaraConPermiso()
            }
    }

    private fun abrirCamaraConPermiso() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> abrirCamara()
            else -> requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    /** Copia la imagen escaneada a la carpeta de fotos de la app y la deja como foto del gasto. */
    private fun guardarFotoEscaneada(origen: Uri) {
        lifecycleScope.launch {
            try {
                val destino = withContext(Dispatchers.IO) {
                    val archivo = crearArchivoDeImagen()
                    val entrada = contentResolver.openInputStream(origen)
                        ?: throw java.io.IOException("No se pudo leer la imagen escaneada")
                    entrada.use { input -> archivo.outputStream().use { salida -> input.copyTo(salida) } }
                    // Si la foto es muy grande, se reduce (ahorra espacio y hace más liviano el PDF)
                    runCatching { FotoUtil.reducir(archivo) }
                        .onFailure { Log.w("NuevoGastoActivity", "No se pudo reducir la foto escaneada", it) }
                    archivo
                }
                pathFotoLocal = destino.absolutePath
                urlFotoExistente = null
                rotacionPendiente = 0
                binding.imageViewFotoRecibo.rotation = 0f
                Glide.with(this@NuevoGastoActivity).load(destino)
                    .signature(ObjectKey(destino.lastModified()))
                    .into(binding.imageViewFotoRecibo)
                binding.imageViewFotoRecibo.visibility = View.VISIBLE
                binding.buttonGirarFoto.visibility = View.VISIBLE
            } catch (e: Exception) {
                Log.e("NuevoGastoActivity", "Error al guardar la imagen escaneada", e)
                Toast.makeText(this@NuevoGastoActivity, "No se pudo guardar la foto: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun abrirCamara() {
        try {
            val fotoArchivo = crearArchivoDeImagen()
            pathFotoLocal = fotoArchivo.absolutePath
            fotoUri = FileProvider.getUriForFile(this, "com.invap.rendiciondegastos.fileprovider", fotoArchivo)
            cameraLauncher.launch(fotoUri)
        } catch (e: Exception) {
            Log.e("NuevoGastoActivity", "Error al crear archivo de imagen", e)
            Toast.makeText(this, "Error al preparar la cámara", Toast.LENGTH_SHORT).show()
        }
    }

    private fun crearArchivoDeImagen(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File? = getExternalFilesDir("Pictures")
        if (storageDir != null && !storageDir.exists()) {
            storageDir.mkdirs()
        }
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
    }

    companion object {
        const val EXTRA_VIAJE_ID = "EXTRA_VIAJE_ID"
        const val EXTRA_VIAJE_MONEDA_DEFECTO = "EXTRA_VIAJE_MONEDA_DEFECTO"
        const val EXTRA_VIAJE_IMPUTACION_PT = "EXTRA_VIAJE_IMPUTACION_PT"
        const val EXTRA_VIAJE_IMPUTACION_WP = "EXTRA_VIAJE_IMPUTACION_WP"
        const val EXTRA_GASTO_ID = "EXTRA_GASTO_ID"
        const val EXTRA_GASTO_DESCRIPCION = "EXTRA_GASTO_DESCRIPCION"
        const val EXTRA_GASTO_MONTO = "EXTRA_GASTO_MONTO"
        const val EXTRA_GASTO_FECHA = "EXTRA_GASTO_FECHA"
        const val EXTRA_GASTO_TIPO = "EXTRA_GASTO_TIPO"
        const val EXTRA_GASTO_MONEDA = "EXTRA_GASTO_MONEDA"
        const val EXTRA_GASTO_FORMA_PAGO = "EXTRA_GASTO_FORMA_PAGO"
        const val EXTRA_GASTO_URL_FOTO = "EXTRA_GASTO_URL_FOTO"
        const val EXTRA_GASTO_TAG = "EXTRA_GASTO_TAG"
        const val EXTRA_GASTO_IMPUTACION_PT = "EXTRA_GASTO_IMPUTACION_PT"
        const val EXTRA_GASTO_IMPUTACION_WP = "EXTRA_GASTO_IMPUTACION_WP"

        // --- CONSTANTE AÑADIDA ---
        const val EXTRA_GASTO_SIN_COMPROBANTE = "EXTRA_GASTO_SIN_COMPROBANTE"
        const val EXTRA_GASTO_TAMANO_RECIBO = "EXTRA_GASTO_TAMANO_RECIBO"
        const val EXTRA_GASTO_IMPORTE_DOLARES = "EXTRA_GASTO_IMPORTE_DOLARES"
        const val EXTRA_GASTO_DOLARES_ESTIMADO = "EXTRA_GASTO_DOLARES_ESTIMADO"
    }
}