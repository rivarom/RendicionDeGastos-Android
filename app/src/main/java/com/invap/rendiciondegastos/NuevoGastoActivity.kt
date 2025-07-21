package com.invap.rendiciondegastos

import android.Manifest
import android.app.DatePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.invap.rendiciondegastos.databinding.ActivityNuevoGastoBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

class NuevoGastoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNuevoGastoBinding
    private val db = Firebase.firestore
    private val storage = Firebase.storage
    private var viajeId: String? = null
    private var fotoUri: Uri? = null
    private var idGastoAEditar: String? = null
    private var urlFotoExistente: String? = null
    private val formasPagoList = mutableListOf<FormaDePago>()

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
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNuevoGastoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viajeId = intent.getStringExtra(EXTRA_VIAJE_ID)
        idGastoAEditar = intent.getStringExtra(EXTRA_GASTO_ID)

        cargarOpcionesDesplegables()
        configurarCampoDeFecha()

        if (idGastoAEditar == null) {
            val monedaDefecto = intent.getStringExtra(EXTRA_VIAJE_MONEDA_DEFECTO)
            if (!monedaDefecto.isNullOrEmpty()) {
                binding.autoCompleteMoneda.setText(monedaDefecto, false)
            }
        } else {
            prepararModoEdicion()
        }

        binding.buttonTomarFoto.setOnClickListener {
            when {
                ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> abrirCamara()
                else -> requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        binding.buttonGuardarGasto.setOnClickListener {
            guardarGasto()
        }
    }

    private fun guardarGasto() {
        Log.d("GuardarGasto", "Iniciando proceso de guardado...")
        binding.buttonGuardarGasto.isEnabled = false

        val descripcion = binding.editTextDescripcionGasto.text.toString().trim()
        val montoStr = binding.editTextMontoGasto.text.toString().trim()
        val fecha = binding.editTextFechaGasto.text.toString().trim()
        val tipoGasto = binding.autoCompleteTipoGasto.text.toString()
        val moneda = binding.autoCompleteMoneda.text.toString()
        val formaDePagoNombre = binding.autoCompleteFormaPago.text.toString()

        if (montoStr.isEmpty() || fecha.isEmpty() || viajeId == null || tipoGasto.isEmpty() || moneda.isEmpty() || formaDePagoNombre.isEmpty()) {
            Toast.makeText(this, "Por favor, completa todos los campos", Toast.LENGTH_SHORT).show()
            binding.buttonGuardarGasto.isEnabled = true
            return
        }

        val formaDePagoSeleccionada = formasPagoList.find { it.nombre == formaDePagoNombre }
        if (formaDePagoSeleccionada == null) {
            Toast.makeText(this, "Forma de pago no válida", Toast.LENGTH_SHORT).show()
            binding.buttonGuardarGasto.isEnabled = true
            return
        }
        val prefijo = formaDePagoSeleccionada.prefijo

        if (idGastoAEditar != null) {
            Log.d("GuardarGasto", "Modo Edición detectado.")
            val tagExistente = intent.getStringExtra(EXTRA_GASTO_TAG) ?: ""
            if (fotoUri != null) {
                subirFotoYGuardarDatos(fotoUri!!, descripcion, montoStr.toDouble(), fecha, viajeId!!, tipoGasto, moneda, formaDePagoNombre, tagExistente)
            } else {
                guardarDatosEnFirestore(descripcion, montoStr.toDouble(), fecha, viajeId!!, tipoGasto, moneda, formaDePagoNombre, urlFotoExistente ?: "", tagExistente)
            }
            return
        }

        Log.d("GuardarGasto", "Modo Creación. Calculando nuevo TAG...")
        db.collection("gastos")
            .whereEqualTo("viajeId", viajeId)
            .whereEqualTo("formaDePago", formaDePagoNombre)
            .get()
            .addOnSuccessListener { documents ->
                val nuevoNumeroSecuencial = documents.size() + 1
                val nuevoTag = "$prefijo$nuevoNumeroSecuencial"
                Log.d("GuardarGasto", "Nuevo TAG calculado: $nuevoTag")

                if (fotoUri != null) {
                    subirFotoYGuardarDatos(fotoUri!!, descripcion, montoStr.toDouble(), fecha, viajeId!!, tipoGasto, moneda, formaDePagoNombre, nuevoTag)
                } else {
                    guardarDatosEnFirestore(descripcion, montoStr.toDouble(), fecha, viajeId!!, tipoGasto, moneda, formaDePagoNombre, "", nuevoTag)
                }
            }
            .addOnFailureListener { e ->
                Log.e("GuardarGasto", "Error al calcular el TAG", e)
                Toast.makeText(this, "Error al calcular el TAG: ${e.message}", Toast.LENGTH_LONG).show()
                binding.buttonGuardarGasto.isEnabled = true
            }
    }

    // El resto de las funciones (subirFoto, guardarDatos, etc.) no cambian.
    // Pega el resto de tus funciones aquí.
    private fun configurarCampoDeFecha() {
        if (idGastoAEditar == null) {
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
        urlFotoExistente = intent.getStringExtra(EXTRA_GASTO_URL_FOTO)
        if (!urlFotoExistente.isNullOrEmpty()) {
            binding.imageViewFotoRecibo.visibility = View.VISIBLE
            Glide.with(this).load(urlFotoExistente).into(binding.imageViewFotoRecibo)
        }
    }

    // Dentro de NuevoGastoActivity.kt, reemplaza la función cargarOpcionesDesplegables

    private fun cargarOpcionesDesplegables() {
        val userId = Firebase.auth.currentUser?.uid
        if (userId == null) {
            Log.e("GastoDebug", "Error: No se encontró usuario al cargar opciones")
            return
        }

        val sharedPref = getSharedPreferences("UserPrefs_$userId", Context.MODE_PRIVATE)

        val monedas = sharedPref.getStringSet("MONEDAS", emptySet())?.toList() ?: emptyList()
        // --- MENSAJE DE DIAGNÓSTICO ---
        Log.d("GastoDebug", "Monedas para el desplegable: $monedas")
        val monedasAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, monedas)
        binding.autoCompleteMoneda.setAdapter(monedasAdapter)

        val tiposGasto = sharedPref.getStringSet("TIPOS_GASTO", emptySet())?.toList() ?: emptyList()
        // --- MENSAJE DE DIAGNÓSTICO ---
        Log.d("GastoDebug", "Tipos de Gasto para el desplegable: $tiposGasto")
        val tiposGastoAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, tiposGasto)
        binding.autoCompleteTipoGasto.setAdapter(tiposGastoAdapter)

        val formasPagoGuardadas = sharedPref.getStringSet("FORMAS_PAGO", emptySet())
        formasPagoList.clear()
        formasPagoGuardadas?.forEach {
            val partes = it.split("::")
            if (partes.size == 2) {
                formasPagoList.add(FormaDePago(partes[0], partes[1]))
            }
        }
        val formasPagoNombres = formasPagoList.map { it.nombre }
        // --- MENSAJE DE DIAGNÓSTICO ---
        Log.d("GastoDebug", "Formas de Pago para el desplegable: $formasPagoNombres")
        val formasPagoAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, formasPagoNombres)
        binding.autoCompleteFormaPago.setAdapter(formasPagoAdapter)
    }

    private fun abrirCamara() {
        val fotoArchivo = crearArchivoDeImagen()
        fotoUri = FileProvider.getUriForFile(this, "com.invap.rendiciondegastos.fileprovider", fotoArchivo)
        cameraLauncher.launch(fotoUri)
    }

    private fun crearArchivoDeImagen(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File? = getExternalFilesDir("Pictures")
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
    }

    private fun subirFotoYGuardarDatos(uri: Uri, descripcion: String, monto: Double, fecha: String, viajeId: String, tipoGasto: String, moneda: String, formaDePago: String, tag: String) {
        val fotoRef = storage.reference.child("recibos/${UUID.randomUUID()}.jpg")
        Toast.makeText(this, "Subiendo foto...", Toast.LENGTH_SHORT).show()
        fotoRef.putFile(uri)
            .continueWithTask { task ->
                if (!task.isSuccessful) { task.exception?.let { throw it } }
                fotoRef.downloadUrl
            }
            .addOnSuccessListener { downloadUrl ->
                guardarDatosEnFirestore(descripcion, monto, fecha, viajeId, tipoGasto, moneda, formaDePago, downloadUrl.toString(), tag)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al subir la foto", Toast.LENGTH_SHORT).show()
                binding.buttonGuardarGasto.isEnabled = true
            }
    }

    private fun guardarDatosEnFirestore(descripcion: String, monto: Double, fecha: String, viajeId: String, tipoGasto: String, moneda: String, formaDePago: String, urlFoto: String, tag: String) {
        val userId = Firebase.auth.currentUser?.uid
        if (userId == null) {
            Toast.makeText(this, "Error: No se pudo identificar al usuario.", Toast.LENGTH_SHORT).show()
            binding.buttonGuardarGasto.isEnabled = true
            return
        }

        // CORRECCIÓN: Leemos desde el archivo de preferencias del usuario actual
        val userPrefs = getSharedPreferences("UserPrefs_$userId", Context.MODE_PRIVATE)
        val nombrePersona = userPrefs.getString("NOMBRE_PERSONA", "") ?: ""
        val legajo = userPrefs.getString("LEGAJO", "") ?: ""
        val centroCostos = userPrefs.getString("CENTRO_COSTOS", "") ?: ""

        val gastoMap = hashMapOf(
            "viajeId" to viajeId,
            "descripcion" to descripcion,
            "monto" to monto,
            "fecha" to fecha,
            "urlFotoRecibo" to urlFoto,
            "moneda" to moneda,
            "tipoGasto" to tipoGasto,
            "formaDePago" to formaDePago,
            "tagGasto" to tag,
            "nombrePersona" to nombrePersona,
            "legajo" to legajo,
            "centroCostos" to centroCostos,
            "timestamp" to System.currentTimeMillis(),
            "userId" to userId
        )

        val task = if (idGastoAEditar == null) {
            db.collection("gastos").add(gastoMap)
        } else {
            db.collection("gastos").document(idGastoAEditar!!).set(gastoMap)
        }

        task.addOnSuccessListener {
            val mensaje = if (idGastoAEditar == null) "Gasto guardado" else "Gasto actualizado"
            Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show()
            finish()
        }.addOnFailureListener {
            Toast.makeText(this, "Error al guardar los datos", Toast.LENGTH_SHORT).show()
            binding.buttonGuardarGasto.isEnabled = true
        }
    }

    companion object {
        const val EXTRA_VIAJE_ID = "EXTRA_VIAJE_ID"
        const val EXTRA_VIAJE_MONEDA_DEFECTO = "EXTRA_VIAJE_MONEDA_DEFECTO"
        const val EXTRA_GASTO_ID = "EXTRA_GASTO_ID"
        const val EXTRA_GASTO_DESCRIPCION = "EXTRA_GASTO_DESCRIPCION"
        const val EXTRA_GASTO_MONTO = "EXTRA_GASTO_MONTO"
        const val EXTRA_GASTO_FECHA = "EXTRA_GASTO_FECHA"
        const val EXTRA_GASTO_TIPO = "EXTRA_GASTO_TIPO"
        const val EXTRA_GASTO_MONEDA = "EXTRA_GASTO_MONEDA"
        const val EXTRA_GASTO_FORMA_PAGO = "EXTRA_GASTO_FORMA_PAGO"
        const val EXTRA_GASTO_URL_FOTO = "EXTRA_GASTO_URL_FOTO"
        const val EXTRA_GASTO_TAG = "EXTRA_GASTO_TAG"
    }
}