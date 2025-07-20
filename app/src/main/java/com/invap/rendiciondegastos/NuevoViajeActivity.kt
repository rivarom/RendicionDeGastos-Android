package com.invap.rendiciondegastos

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.invap.rendiciondegastos.databinding.ActivityNuevoViajeBinding

class NuevoViajeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNuevoViajeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNuevoViajeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // --- LÓGICA DEL BOTÓN GUARDAR ---
        binding.buttonGuardar.setOnClickListener {
            // 1. Obtenemos el texto de los campos del formulario.
            val nombreViaje = binding.editTextNombreViaje.text.toString()
            val fechaViaje = binding.editTextFechaViaje.text.toString()

            // 2. Verificamos que los campos no estén vacíos.
            if (nombreViaje.isNotEmpty() && fechaViaje.isNotEmpty()) {
                // 3. Creamos un Intent para devolver los datos.
                val dataIntent = Intent()
                // "Empaquetamos" los datos usando un sistema de clave-valor.
                dataIntent.putExtra(EXTRA_NOMBRE, nombreViaje)
                dataIntent.putExtra(EXTRA_FECHA, fechaViaje)

                // 4. Establecemos el resultado como "OK" y adjuntamos el intent con datos.
                setResult(Activity.RESULT_OK, dataIntent)

                // 5. Cerramos esta actividad para volver a la pantalla principal.
                finish()
            } else {
                // Si algún campo está vacío, mostramos un mensaje.
                Toast.makeText(this, "Por favor, completa todos los campos", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Es una buena práctica definir las "claves" para los datos en un companion object.
    // Esto evita errores de tipeo al usarlas en otra actividad.
    companion object {
        const val EXTRA_NOMBRE = "com.invap.rendiciondegastos.EXTRA_NOMBRE"
        const val EXTRA_FECHA = "com.invap.rendiciondegastos.EXTRA_FECHA"
    }
}