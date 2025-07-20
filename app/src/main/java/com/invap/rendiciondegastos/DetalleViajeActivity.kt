package com.invap.rendiciondegastos

import android.content.Intent
import android.net.Uri
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
        val tituloFormateado = getString(R.string.titulo_detalle_viaje, nombreViaje)
        binding.textViewNombreViajeDetalle.text = tituloFormateado

        // 1. Actualizamos la creación del adaptador para manejar ambos clics
        adapter = GastosAdapter(
            listaDeGastos,
            onItemClicked = { gasto ->
                // CLIC CORTO: Si hay foto, la muestra. Si no, no hace nada.
                if (gasto.urlFotoRecibo.isNotEmpty()) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(gasto.urlFotoRecibo))
                    startActivity(intent)
                }
            },
            onItemLongClicked = { gasto ->
                // CLIC LARGO: Muestra el menú de acciones.
                mostrarDialogoDeAcciones(gasto)
            }
        )
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

    // 2. Nueva función para el diálogo de acciones del gasto
    private fun mostrarDialogoDeAcciones(gasto: Gasto) {
        val opciones = arrayOf("Ver Recibo", "Editar Gasto", "Eliminar Gasto")

        AlertDialog.Builder(this)
            .setTitle(gasto.descripcion)
            .setItems(opciones) { _, which ->
                when (which) {
                    0 -> { // Ver Recibo
                        if (gasto.urlFotoRecibo.isNotEmpty()) {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(gasto.urlFotoRecibo))
                            startActivity(intent)
                        } else {
                            Toast.makeText(this, "Este gasto no tiene un recibo adjunto", Toast.LENGTH_SHORT).show()
                        }
                    }
                    1 -> { // El usuario eligió "Editar Gasto"
                        val intent = Intent(this, NuevoGastoActivity::class.java)
                        // Pasamos todos los datos del gasto para la edición
                        intent.putExtra(NuevoGastoActivity.EXTRA_VIAJE_ID, viajeId)
                        intent.putExtra(NuevoGastoActivity.EXTRA_GASTO_ID, gasto.id)
                        intent.putExtra(NuevoGastoActivity.EXTRA_GASTO_DESCRIPCION, gasto.descripcion)
                        intent.putExtra(NuevoGastoActivity.EXTRA_GASTO_MONTO, gasto.monto)
                        intent.putExtra(NuevoGastoActivity.EXTRA_GASTO_FECHA, gasto.fecha)
                        intent.putExtra(NuevoGastoActivity.EXTRA_GASTO_TIPO, gasto.tipoGasto)
                        intent.putExtra(NuevoGastoActivity.EXTRA_GASTO_MONEDA, gasto.moneda)
                        intent.putExtra(NuevoGastoActivity.EXTRA_GASTO_URL_FOTO, gasto.urlFotoRecibo)
                        startActivity(intent)
                    }
                    2 -> { // Eliminar Gasto
                        mostrarDialogoDeConfirmacion(gasto)
                    }
                }
            }
            .show()
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
        if (gasto.id.isEmpty()) {
            Toast.makeText(this, "Error: ID del gasto no encontrado", Toast.LENGTH_SHORT).show()
            return
        }
        db.collection("gastos").document(gasto.id)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Gasto eliminado", Toast.LENGTH_SHORT).show()
                cargarGastos()
            }
            .addOnFailureListener { e ->
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
                Toast.makeText(this, "Error al cargar gastos.", Toast.LENGTH_SHORT).show()
            }
    }
}