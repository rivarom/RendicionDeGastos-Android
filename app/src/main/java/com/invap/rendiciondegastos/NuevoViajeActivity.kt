package com.invap.rendiciondegastos

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.invap.rendiciondegastos.databinding.ActivityNuevoViajeBinding

class NuevoViajeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNuevoViajeBinding
    private var idViajeAEditar: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNuevoViajeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        idViajeAEditar = intent.getStringExtra(EXTRA_VIAJE_ID)

        if (idViajeAEditar != null) {
            binding.editTextNombreViaje.setText(intent.getStringExtra(EXTRA_VIAJE_NOMBRE))
            binding.editTextFechaViaje.setText(intent.getStringExtra(EXTRA_VIAJE_FECHA))
            binding.buttonGuardar.text = "Actualizar Viaje"
        }

        binding.buttonGuardar.setOnClickListener {
            val nombreViaje = binding.editTextNombreViaje.text.toString()
            val fechaViaje = binding.editTextFechaViaje.text.toString()

            if (nombreViaje.isNotEmpty() && fechaViaje.isNotEmpty()) {
                val dataIntent = Intent()
                // CORRECCIÓN: Usamos los nombres de las constantes que definimos abajo
                dataIntent.putExtra(EXTRA_VIAJE_NOMBRE, nombreViaje)
                dataIntent.putExtra(EXTRA_VIAJE_FECHA, fechaViaje)

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

    companion object {
        const val EXTRA_VIAJE_ID = "EXTRA_VIAJE_ID"
        const val EXTRA_VIAJE_NOMBRE = "EXTRA_VIAJE_NOMBRE"
        const val EXTRA_VIAJE_FECHA = "EXTRA_VIAJE_FECHA"
    }
}