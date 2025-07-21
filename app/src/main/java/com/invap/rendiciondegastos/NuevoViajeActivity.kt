package com.invap.rendiciondegastos

import android.app.Activity
import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.invap.rendiciondegastos.databinding.ActivityNuevoViajeBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class NuevoViajeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNuevoViajeBinding
    private var idViajeAEditar: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNuevoViajeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cargarOpcionesMoneda()
        configurarCampoDeFecha()

        idViajeAEditar = intent.getStringExtra(EXTRA_VIAJE_ID)
        if (idViajeAEditar != null) {
            // Modo Edición
            binding.editTextNombreViaje.setText(intent.getStringExtra(EXTRA_VIAJE_NOMBRE))
            binding.editTextFechaViaje.setText(intent.getStringExtra(EXTRA_VIAJE_FECHA))
            binding.autoCompleteMonedaViaje.setText(intent.getStringExtra(EXTRA_VIAJE_MONEDA_DEFECTO), false)
            binding.buttonGuardar.text = "Actualizar Viaje"
        }

        binding.buttonGuardar.setOnClickListener {
            val nombreViaje = binding.editTextNombreViaje.text.toString()
            val fechaViaje = binding.editTextFechaViaje.text.toString()
            val monedaDefecto = binding.autoCompleteMonedaViaje.text.toString()

            if (nombreViaje.isNotEmpty() && fechaViaje.isNotEmpty() && monedaDefecto.isNotEmpty()) {
                val dataIntent = Intent()
                dataIntent.putExtra(EXTRA_VIAJE_NOMBRE, nombreViaje)
                dataIntent.putExtra(EXTRA_VIAJE_FECHA, fechaViaje)
                dataIntent.putExtra(EXTRA_VIAJE_MONEDA_DEFECTO, monedaDefecto)

                if (idViajeAEditar != null) {
                    dataIntent.putExtra(EXTRA_VIAJE_ID, idViajeAEditar)
                }
                setResult(Activity.RESULT_OK, dataIntent)
                finish()
            } else {
                Toast.makeText(this, "Por favor, completa todos los campos", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun configurarCampoDeFecha() {
        if (idViajeAEditar == null) {
            val calendario = Calendar.getInstance()
            val formatoFecha = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            binding.editTextFechaViaje.setText(formatoFecha.format(calendario.time))
        }
        binding.editTextFechaViaje.setOnClickListener {
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
            binding.editTextFechaViaje.setText(formatoFecha.format(fechaSeleccionada.time))
        }, anio, mes, dia)
        datePickerDialog.show()
    }

    private fun cargarOpcionesMoneda() {
        val userId = Firebase.auth.currentUser?.uid
        if (userId == null) {
            Log.e("ViajeDebug", "Error: No se encontró usuario al cargar opciones de moneda")
            return
        }

        val sharedPref = getSharedPreferences("UserPrefs_$userId", Context.MODE_PRIVATE)
        val monedasPorDefecto = setOf("Pesos", "Dólar")
        val monedas = sharedPref.getStringSet("MONEDAS", monedasPorDefecto)

        // --- MENSAJE DE DIAGNÓSTICO ---
        Log.d("ViajeDebug", "Monedas leídas para el desplegable: $monedas")

        val monedasAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, monedas?.toList() ?: monedasPorDefecto.toList())
        binding.autoCompleteMonedaViaje.setAdapter(monedasAdapter)
    }

    companion object {
        const val EXTRA_VIAJE_ID = "EXTRA_VIAJE_ID"
        const val EXTRA_VIAJE_NOMBRE = "EXTRA_VIAJE_NOMBRE"
        const val EXTRA_VIAJE_FECHA = "EXTRA_VIAJE_FECHA"
        const val EXTRA_VIAJE_MONEDA_DEFECTO = "EXTRA_VIAJE_MONEDA_DEFECTO"
    }
}