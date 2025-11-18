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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.invap.rendiciondegastos.databinding.ActivityNuevoGastoBinding
import kotlinx.coroutines.launch
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
                binding.imageViewFotoRecibo.setImageURI(fotoUri)
                binding.imageViewFotoRecibo.visibility = View.VISIBLE
                urlFotoExistente = null
            } else {
                pathFotoLocal = null
            }
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

        cargarOpcionesDesplegables()
        configurarCampoDeFecha()
        configurarValidacionEnTiempoReal()

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

        binding.buttonTomarFoto.setOnClickListener {
            when {
                ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> abrirCamara()
                else -> requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
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
            } else {
                // Si se desmarca, habilitar la cámara
                binding.buttonTomarFoto.isEnabled = true
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

        val formaDePagoSeleccionada = formasPagoList.find { it.nombre == formaDePagoNombre }
        val imputacionSeleccionada = imputacionesList.find { "PT: ${it.pt} / WP: ${it.wp}" == imputacionSeleccionadaStr }

        if (formaDePagoSeleccionada == null || imputacionSeleccionada == null) {
            Toast.makeText(this, "Por favor, selecciona valores válidos de las listas", Toast.LENGTH_SHORT).show()
            binding.buttonGuardarGasto.isEnabled = true
            return
        }

        val userPrefs = getSharedPreferences("UserPrefs_local", Context.MODE_PRIVATE)
        val nombrePersona = userPrefs.getString("NOMBRE_PERSONA", "") ?: ""
        val legajo = userPrefs.getString("LEGAJO", "") ?: ""
        val centroCostos = userPrefs.getString("CENTRO_COSTOS", "") ?: ""

        lifecycleScope.launch {
            try {
                val tag: String
                val urlFotoFinal: String

                if (idGastoAEditar != 0L) { // Editando
                    tag = intent.getStringExtra(EXTRA_GASTO_TAG) ?: ""
                    // MODIFICADO: Si está marcado, no guarda foto.
                    urlFotoFinal = if (esSinComprobante) "" else (pathFotoLocal ?: (urlFotoExistente ?: ""))
                } else { // Creando
                    val count = db.gastoDao().countGastosByFormaDePago(viajeId, formaDePagoNombre)
                    tag = "${formaDePagoSeleccionada.prefijo}${count + 1}"
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
                    timestamp = System.currentTimeMillis(),
                    sinComprobante = esSinComprobante // --- CAMPO NUEVO GUARDADO ---
                )

                guardarDatosEnRoom(gasto)

            } catch (e: Exception) {
                Log.e("NuevoGastoActivity", "Error al calcular TAG o guardar", e)
                Toast.makeText(this@NuevoGastoActivity, "Error al guardar: ${e.message}", Toast.LENGTH_LONG).show()
                binding.buttonGuardarGasto.isEnabled = true
            }
        }
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
        binding.editTextMontoGasto.setText(intent.getDoubleExtra(EXTRA_GASTO_MONTO, 0.0).toString())
        binding.editTextFechaGasto.setText(intent.getStringExtra(EXTRA_GASTO_FECHA))
        binding.autoCompleteTipoGasto.setText(intent.getStringExtra(EXTRA_GASTO_TIPO), false)
        binding.autoCompleteMoneda.setText(intent.getStringExtra(EXTRA_GASTO_MONEDA), false)
        binding.autoCompleteFormaPago.setText(intent.getStringExtra(EXTRA_GASTO_FORMA_PAGO), false)
        val pt = intent.getStringExtra(EXTRA_GASTO_IMPUTACION_PT)
        val wp = intent.getStringExtra(EXTRA_GASTO_IMPUTACION_WP)
        if (pt != null && wp != null) {
            binding.autoCompleteImputacion.setText("PT: $pt / WP: $wp", false)
        }

        // --- LÓGICA DE CHECKBOX AÑADIDA ---
        val sinComprobante = intent.getBooleanExtra(EXTRA_GASTO_SIN_COMPROBANTE, false)
        binding.checkboxSinComprobante.isChecked = sinComprobante
        if (sinComprobante) {
            binding.buttonTomarFoto.isEnabled = false
        }
        // --- FIN LÓGICA AÑADIDA ---

        urlFotoExistente = intent.getStringExtra(EXTRA_GASTO_URL_FOTO)
        // MODIFICADO: Solo mostrar la foto si hay URL Y no está marcado "Sin comprobante"
        if (!urlFotoExistente.isNullOrEmpty() && !sinComprobante) {
            binding.imageViewFotoRecibo.visibility = View.VISIBLE
            try {
                Glide.with(this).load(File(urlFotoExistente)).into(binding.imageViewFotoRecibo)
            } catch (e: Exception) {
                Log.e("NuevoGastoActivity", "Error al cargar imagen en modo edición", e)
                binding.imageViewFotoRecibo.visibility = View.GONE
                Toast.makeText(this, "No se pudo cargar la foto", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun cargarOpcionesDesplegables() {
        val userPrefs = getSharedPreferences("UserPrefs_local", Context.MODE_PRIVATE)

        val monedas = userPrefs.getStringSet("MONEDAS", setOf("Pesos", "Dólar"))?.toList() ?: listOf("Pesos", "Dólar")
        val monedasAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, monedas)
        binding.autoCompleteMoneda.setAdapter(monedasAdapter)

        val tiposGasto = userPrefs.getStringSet("TIPOS_GASTO", setOf("Transporte", "Comida"))?.toList() ?: listOf("Transporte", "Comida")
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
        val formasPagoNombres = formasPagoList.map { it.nombre }
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
        val imputacionesFormateadas = imputacionesList.map { "PT: ${it.pt} / WP: ${it.wp}" }
        val imputacionesAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, imputacionesFormateadas)
        binding.autoCompleteImputacion.setAdapter(imputacionesAdapter)
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
    }
}