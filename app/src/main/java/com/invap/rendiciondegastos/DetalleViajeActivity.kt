package com.invap.rendiciondegastos

import Gasto
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.invap.rendiciondegastos.databinding.ActivityDetalleViajeBinding

class DetalleViajeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetalleViajeBinding
    private var viajeId: String? = null

    // 1. Añadimos las variables para la lista y el adaptador de gastos
    private val db = Firebase.firestore
    private val listaDeGastos = mutableListOf<Gasto>()
    private lateinit var adapter: GastosAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetalleViajeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viajeId = intent.getStringExtra("EXTRA_VIAJE_ID")
        val nombreViaje = intent.getStringExtra("EXTRA_VIAJE_NOMBRE")
        binding.textViewNombreViajeDetalle.text = nombreViaje

        // 2. Configuramos el RecyclerView de gastos
        adapter = GastosAdapter(listaDeGastos)
        binding.recyclerViewGastos.adapter = adapter
        binding.recyclerViewGastos.layoutManager = LinearLayoutManager(this)

        binding.fabAgregarGasto.setOnClickListener {
            val intent = Intent(this, NuevoGastoActivity::class.java)
            intent.putExtra("EXTRA_VIAJE_ID", viajeId)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        // 3. Cargamos los gastos cada vez que la pantalla es visible
        if (viajeId != null) {
            cargarGastos()
        }
    }

    private fun cargarGastos() {
        db.collection("gastos")
            // 4. Esta es la consulta clave: filtramos por el ID del viaje actual
            .whereEqualTo("viajeId", viajeId)
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