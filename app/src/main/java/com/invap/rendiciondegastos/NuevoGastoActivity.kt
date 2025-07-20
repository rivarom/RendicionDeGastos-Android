package com.invap.rendiciondegastos

import android.Manifest
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
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.invap.rendiciondegastos.databinding.ActivityNuevoGastoBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class NuevoGastoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNuevoGastoBinding
    private val db = Firebase.firestore
    private val storage = Firebase.storage
    private var viajeId: String? = null
    private var fotoUri: Uri? = null

    // Variable para saber si estamos editando
    private var idGastoAEditar: String? = null
    private var urlFotoExistente: String? = null

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
                urlFotoExistente = null // Si se toma una nueva foto, la anterior ya no cuenta
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNuevoGastoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viajeId = intent.getStringExtra(EXTRA_VIAJE_ID)
        idGastoAEditar = intent.getStringExtra(EXTRA_GASTO_ID)

        cargarOpcionesDesplegables()

        if (idGastoAEditar != null) {
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

    private fun prepararModoEdicion() {
        binding.buttonGuardarGasto.text = "Actualizar Gasto"

        binding.editTextDescripcionGasto.setText(intent.getStringExtra(EXTRA_GASTO_DESCRIPCION))
        binding.editTextMontoGasto.setText(intent.getDoubleExtra(EXTRA_GASTO_MONTO, 0.0).toString())
        binding.editTextFechaGasto.setText(intent.getStringExtra(EXTRA_GASTO_FECHA))
        binding.autoCompleteTipoGasto.setText(intent.getStringExtra(EXTRA_GASTO_TIPO), false)
        binding.autoCompleteMoneda.setText(intent.getStringExtra(EXTRA_GASTO_MONEDA), false)

        urlFotoExistente = intent.getStringExtra(EXTRA_GASTO_URL_FOTO)
        if (!urlFotoExistente.isNullOrEmpty()) {
            binding.imageViewFotoRecibo.visibility = View.VISIBLE
            Glide.with(this).load(urlFotoExistente).into(binding.imageViewFotoRecibo)
        }
    }

    private fun cargarOpcionesDesplegables() {
        val sharedPref = getSharedPreferences("RendicionDeGastosPrefs", Context.MODE_PRIVATE)
        val monedas = sharedPref.getStringSet("MONEDAS", emptySet())?.toList() ?: emptyList()
        val monedasAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, monedas)
        binding.autoCompleteMoneda.setAdapter(monedasAdapter)

        val tiposGasto = sharedPref.getStringSet("TIPOS_GASTO", emptySet())?.toList() ?: emptyList()
        val tiposGastoAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, tiposGasto)
        binding.autoCompleteTipoGasto.setAdapter(tiposGastoAdapter)
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

    private fun guardarGasto() {
        binding.buttonGuardarGasto.isEnabled = false
        val descripcion = binding.editTextDescripcionGasto.text.toString().trim()
        val montoStr = binding.editTextMontoGasto.text.toString().trim()
        val fecha = binding.editTextFechaGasto.text.toString().trim()
        val tipoGasto = binding.autoCompleteTipoGasto.text.toString()
        val moneda = binding.autoCompleteMoneda.text.toString()

        if (descripcion.isEmpty() || montoStr.isEmpty() || fecha.isEmpty() || viajeId == null || tipoGasto.isEmpty() || moneda.isEmpty()) {
            Toast.makeText(this, "Por favor, completa todos los campos", Toast.LENGTH_SHORT).show()
            binding.buttonGuardarGasto.isEnabled = true
            return
        }

        if (fotoUri != null) { // Si se tomó una nueva foto, subirla
            subirFotoYGuardarDatos(fotoUri!!, descripcion, montoStr.toDouble(), fecha, viajeId!!, tipoGasto, moneda)
        } else { // Si no se tomó una nueva foto, guardar/actualizar los datos directamente
            guardarDatosEnFirestore(descripcion, montoStr.toDouble(), fecha, viajeId!!, tipoGasto, moneda, urlFotoExistente ?: "")
        }
    }

    private fun subirFotoYGuardarDatos(uri: Uri, descripcion: String, monto: Double, fecha: String, viajeId: String, tipoGasto: String, moneda: String) {
        val fotoRef = storage.reference.child("recibos/${UUID.randomUUID()}.jpg")
        Toast.makeText(this, "Subiendo foto...", Toast.LENGTH_SHORT).show()
        fotoRef.putFile(uri)
            .continueWithTask { task ->
                if (!task.isSuccessful) { task.exception?.let { throw it } }
                fotoRef.downloadUrl
            }
            .addOnSuccessListener { downloadUrl ->
                guardarDatosEnFirestore(descripcion, monto, fecha, viajeId, tipoGasto, moneda, downloadUrl.toString())
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al subir la foto", Toast.LENGTH_SHORT).show()
                binding.buttonGuardarGasto.isEnabled = true
            }
    }

    private fun guardarDatosEnFirestore(descripcion: String, monto: Double, fecha: String, viajeId: String, tipoGasto: String, moneda: String, urlFoto: String) {
        val sharedPref = getSharedPreferences("RendicionDeGastosPrefs", Context.MODE_PRIVATE)
        val nombrePersona = sharedPref.getString("NOMBRE_PERSONA", "") ?: ""
        val legajo = sharedPref.getString("LEGAJO", "") ?: ""
        val centroCostos = sharedPref.getString("CENTRO_COSTOS", "") ?: ""

        val gastoMap = hashMapOf(
            "viajeId" to viajeId,
            "descripcion" to descripcion,
            "monto" to monto,
            "fecha" to fecha,
            "urlFotoRecibo" to urlFoto,
            "moneda" to moneda,
            "tipoGasto" to tipoGasto,
            "nombrePersona" to nombrePersona,
            "legajo" to legajo,
            "centroCostos" to centroCostos
        )

        val task = if (idGastoAEditar == null) {
            // CREAR: Añade un nuevo documento
            db.collection("gastos").add(gastoMap)
        } else {
            // ACTUALIZAR: Actualiza el documento existente
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
        const val EXTRA_GASTO_ID = "EXTRA_GASTO_ID"
        const val EXTRA_GASTO_DESCRIPCION = "EXTRA_GASTO_DESCRIPCION"
        const val EXTRA_GASTO_MONTO = "EXTRA_GASTO_MONTO"
        const val EXTRA_GASTO_FECHA = "EXTRA_GASTO_FECHA"
        const val EXTRA_GASTO_TIPO = "EXTRA_GASTO_TIPO"
        const val EXTRA_GASTO_MONEDA = "EXTRA_GASTO_MONEDA"
        const val EXTRA_GASTO_URL_FOTO = "EXTRA_GASTO_URL_FOTO"
    }
}