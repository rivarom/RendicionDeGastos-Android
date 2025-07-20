package com.invap.rendiciondegastos

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.invap.rendiciondegastos.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val listaDeViajes = mutableListOf<Viaje>()
    private lateinit var adapter: ViajesAdapter
    private val db = Firebase.firestore

    private val nuevoViajeResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data: Intent? = result.data
            val nombre = data?.getStringExtra(NuevoViajeActivity.EXTRA_NOMBRE)
            val fecha = data?.getStringExtra(NuevoViajeActivity.EXTRA_FECHA)

            if (nombre != null && fecha != null) {
                guardarNuevoViaje(nombre, fecha)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = ViajesAdapter(listaDeViajes) { viaje ->
            val intent = Intent(this, DetalleViajeActivity::class.java)
            intent.putExtra("EXTRA_VIAJE_ID", viaje.id)
            intent.putExtra("EXTRA_VIAJE_NOMBRE", viaje.nombre)
            startActivity(intent)
        }

        binding.recyclerViewViajes.adapter = adapter
        binding.recyclerViewViajes.layoutManager = LinearLayoutManager(this)

        binding.fabAgregarViaje.setOnClickListener {
            val intent = Intent(this, NuevoViajeActivity::class.java)
            nuevoViajeResultLauncher.launch(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        cargarViajesDesdeFirestore()
    }

    private fun cargarViajesDesdeFirestore() {
        db.collection("viajes")
            .get()
            .addOnSuccessListener { result ->
                listaDeViajes.clear()
                for (document in result) {
                    val viaje = document.toObject(Viaje::class.java)
                    viaje.id = document.id
                    listaDeViajes.add(viaje)
                }
                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener { exception ->
                Log.w("MainActivity", "Error al obtener documentos.", exception)
                Toast.makeText(this, "Error al cargar los viajes.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun guardarNuevoViaje(nombre: String, fecha: String) {
        val nuevoViaje = hashMapOf(
            "nombre" to nombre,
            "fecha" to fecha
        )

        db.collection("viajes")
            .add(nuevoViaje)
            .addOnSuccessListener {
                Log.d("MainActivity", "Documento añadido con ID: ${it.id}")
                cargarViajesDesdeFirestore()
            }
            .addOnFailureListener { e ->
                Log.w("MainActivity", "Error al añadir documento", e)
                Toast.makeText(this, "Error al guardar el viaje.", Toast.LENGTH_SHORT).show()
            }
    }
}