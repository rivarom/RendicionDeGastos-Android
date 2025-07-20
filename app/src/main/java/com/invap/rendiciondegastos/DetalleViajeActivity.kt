package com.invap.rendiciondegastos

import Gasto
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.invap.rendiciondegastos.databinding.ActivityDetalleViajeBinding

class DetalleViajeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetalleViajeBinding
    private var viajeId: String? = null

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

        adapter = GastosAdapter(listaDeGastos) { gasto : Gasto ->
            mostrarDialogoDeConfirmacion(gasto)
        }
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
        if (viajeId != null) {
            cargarGastos()
        }
    }

    private fun mostrarDialogoDeConfirmacion(gasto: Gasto) {
        AlertDialog.Builder(this)
            .setTitle("Confirmar Eliminación")
            .setMessage("¿Estás seguro de que quieres eliminar el gasto '${gasto.descripcion}'?")
            .setPositiveButton("Eliminar") { _, _ ->
                eliminarGasto(gasto)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun eliminarGasto(gasto: Gasto) {
        // Asegúrate que el gasto tenga un ID antes de intentar borrarlo.
        if (gasto.id.isEmpty()) {
            Toast.makeText(this, "Error: ID del gasto no encontrado", Toast.LENGTH_SHORT).show()
            return
        }
        db.collection("gastos").document(gasto.id)
            .delete()
            .addOnSuccessListener {
                Log.d("DetalleViajeActivity", "Gasto eliminado con éxito")
                Toast.makeText(this, "Gasto eliminado", Toast.LENGTH_SHORT).show()
                cargarGastos() // Recargamos la lista
            }
            .addOnFailureListener { e ->
                Log.w("DetalleViajeActivity", "Error al eliminar el gasto", e)
                Toast.makeText(this, "Error al eliminar el gasto", Toast.LENGTH_SHORT).show()
            }
    }

    private fun cargarGastos() {
        db.collection("gastos")
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