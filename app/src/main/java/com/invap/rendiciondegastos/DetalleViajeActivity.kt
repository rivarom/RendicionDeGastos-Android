package com.invap.rendiciondegastos

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.invap.rendiciondegastos.databinding.ActivityDetalleViajeBinding

class DetalleViajeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetalleViajeBinding
    private var viajeId: String? = null
    private var monedaPorDefecto: String? = null
    private var imputacionPtPorDefecto: String? = null
    private var imputacionWpPorDefecto: String? = null

    private val db = Firebase.firestore
    private val listaDeGastos = mutableListOf<Gasto>()
    private lateinit var adapter: GastosAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetalleViajeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viajeId = intent.getStringExtra("EXTRA_VIAJE_ID")
        val nombreViaje = intent.getStringExtra("EXTRA_VIAJE_NOMBRE")
        monedaPorDefecto = intent.getStringExtra("EXTRA_VIAJE_MONEDA_DEFECTO")
        imputacionPtPorDefecto = intent.getStringExtra("EXTRA_VIAJE_IMPUTACION_PT")
        imputacionWpPorDefecto = intent.getStringExtra("EXTRA_VIAJE_IMPUTACION_WP")

        val tituloFormateado = getString(R.string.titulo_detalle_viaje, nombreViaje)
        binding.textViewNombreViajeDetalle.text = tituloFormateado

        adapter = GastosAdapter(
            listaDeGastos,
            onItemClicked = { gasto ->
                if (gasto.urlFotoRecibo.isNotEmpty()) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(gasto.urlFotoRecibo)))
                }
            },
            onItemLongClicked = { gasto ->
                mostrarDialogoDeAcciones(gasto)
            }
        )
        binding.recyclerViewGastos.adapter = adapter
        binding.recyclerViewGastos.layoutManager = LinearLayoutManager(this)

        binding.fabAgregarGasto.setOnClickListener {
            val intent = Intent(this, NuevoGastoActivity::class.java)
            intent.putExtra(NuevoGastoActivity.EXTRA_VIAJE_ID, viajeId)
            intent.putExtra(NuevoGastoActivity.EXTRA_VIAJE_MONEDA_DEFECTO, monedaPorDefecto)
            intent.putExtra(NuevoGastoActivity.EXTRA_VIAJE_IMPUTACION_PT, imputacionPtPorDefecto)
            intent.putExtra(NuevoGastoActivity.EXTRA_VIAJE_IMPUTACION_WP, imputacionWpPorDefecto)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        if (viajeId != null) {
            cargarGastos()
        }
    }

    private fun mostrarDialogoDeAcciones(gasto: Gasto) {
        val opciones = arrayOf("Ver Recibo", "Editar Gasto", "Eliminar Gasto")
        AlertDialog.Builder(this)
            .setTitle(gasto.descripcion)
            .setItems(opciones) { _, which ->
                when (which) {
                    0 -> { // Ver Recibo
                        if (gasto.urlFotoRecibo.isNotEmpty()) {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(gasto.urlFotoRecibo)))
                        } else {
                            Toast.makeText(this, "Este gasto no tiene un recibo adjunto", Toast.LENGTH_SHORT).show()
                        }
                    }
                    1 -> { // Editar Gasto
                        val intent = Intent(this, NuevoGastoActivity::class.java)
                        intent.putExtra(NuevoGastoActivity.EXTRA_VIAJE_ID, viajeId)
                        intent.putExtra(NuevoGastoActivity.EXTRA_GASTO_ID, gasto.id)
                        intent.putExtra(NuevoGastoActivity.EXTRA_GASTO_DESCRIPCION, gasto.descripcion)
                        intent.putExtra(NuevoGastoActivity.EXTRA_GASTO_MONTO, gasto.monto)
                        intent.putExtra(NuevoGastoActivity.EXTRA_GASTO_FECHA, gasto.fecha)
                        intent.putExtra(NuevoGastoActivity.EXTRA_GASTO_TIPO, gasto.tipoGasto)
                        intent.putExtra(NuevoGastoActivity.EXTRA_GASTO_MONEDA, gasto.moneda)
                        intent.putExtra(NuevoGastoActivity.EXTRA_GASTO_FORMA_PAGO, gasto.formaDePago)
                        intent.putExtra(NuevoGastoActivity.EXTRA_GASTO_URL_FOTO, gasto.urlFotoRecibo)
                        intent.putExtra(NuevoGastoActivity.EXTRA_GASTO_TAG, gasto.tagGasto)
                        intent.putExtra(NuevoGastoActivity.EXTRA_GASTO_IMPUTACION_PT, gasto.imputacionPT)
                        intent.putExtra(NuevoGastoActivity.EXTRA_GASTO_IMPUTACION_WP, gasto.imputacionWP)
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
        val userId = Firebase.auth.currentUser?.uid
        if (userId == null || viajeId == null) return

        db.collection("gastos")
            .whereEqualTo("viajeId", viajeId)
            .whereEqualTo("userId", userId)
            .orderBy("timestamp")
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