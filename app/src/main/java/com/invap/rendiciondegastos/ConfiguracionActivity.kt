package com.invap.rendiciondegastos

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.invap.rendiciondegastos.databinding.ActivityConfiguracionBinding

class ConfiguracionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConfiguracionBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConfiguracionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cargarConfiguracion()

        binding.buttonGuardarConfig.setOnClickListener {
            guardarConfiguracion()
        }
    }

    private fun cargarConfiguracion() {
        // Accedemos al archivo de preferencias compartidas
        val sharedPref = getSharedPreferences("RendicionDeGastosPrefs", Context.MODE_PRIVATE)
        // Leemos cada valor y lo ponemos en su campo de texto
        binding.editTextNombrePersona.setText(sharedPref.getString("NOMBRE_PERSONA", ""))
        binding.editTextLegajo.setText(sharedPref.getString("LEGAJO", ""))
        binding.editTextCentroCostos.setText(sharedPref.getString("CENTRO_COSTOS", ""))
    }

    private fun guardarConfiguracion() {
        val sharedPref = getSharedPreferences("RendicionDeGastosPrefs", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("NOMBRE_PERSONA", binding.editTextNombrePersona.text.toString())
            putString("LEGAJO", binding.editTextLegajo.text.toString())
            putString("CENTRO_COSTOS", binding.editTextCentroCostos.text.toString())
            apply() // Usamos apply() para guardar los cambios de forma asíncrona
        }
        Toast.makeText(this, "Configuración guardada", Toast.LENGTH_SHORT).show()
        finish() // Cerramos la pantalla al guardar
    }
}