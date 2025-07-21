package com.invap.rendiciondegastos

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.invap.rendiciondegastos.databinding.ActivityDetalleViajeBinding
import java.io.File
import java.io.FileOutputStream


class DetalleViajeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetalleViajeBinding
    private var viajeId: String? = null
    private var nombreViaje: String? = null
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
        setSupportActionBar(binding.toolbarDetalle)

        viajeId = intent.getStringExtra("EXTRA_VIAJE_ID")
        nombreViaje = intent.getStringExtra("EXTRA_VIAJE_NOMBRE")
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

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.detalle_viaje_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_export_excel -> {
                exportarACSV()
                true
            }
            R.id.action_export_pdf -> {
                exportarRecibosAPDF() // Llamamos a la nueva función
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun exportarACSV() {
        if (listaDeGastos.isEmpty()) {
            Toast.makeText(this, "No hay gastos para exportar", Toast.LENGTH_SHORT).show()
            return
        }

        val csvBuilder = StringBuilder()
        // Encabezados
        csvBuilder.append("TAG,Fecha,Descripción,Tipo de Gasto,Forma de Pago,Moneda,Monto,PT,WP,Persona,Legajo,Centro de Costos\n")

        // Datos
        for (gasto in listaDeGastos) {
            // Escapar comillas dobles dentro de los campos de texto
            val descripcionLimpia = gasto.descripcion.replace("\"", "\"\"")

            csvBuilder.append("\"${gasto.tagGasto}\",")
            csvBuilder.append("\"${gasto.fecha}\",")
            csvBuilder.append("\"$descripcionLimpia\",")
            csvBuilder.append("\"${gasto.tipoGasto}\",")
            csvBuilder.append("\"${gasto.formaDePago}\",")
            csvBuilder.append("\"${gasto.moneda}\",")
            csvBuilder.append("${gasto.monto},") // Los números no necesitan comillas
            csvBuilder.append("\"${gasto.imputacionPT}\",")
            csvBuilder.append("\"${gasto.imputacionWP}\",")
            csvBuilder.append("\"${gasto.nombrePersona}\",")
            csvBuilder.append("\"${gasto.legajo}\",")
            csvBuilder.append("\"${gasto.centroCostos}\"\n")
        }

        try {
            val fileName = "Rendicion_${nombreViaje?.replace(" ", "_")}.csv"
            val file = File(externalCacheDir, fileName)
            FileOutputStream(file).use {
                it.write(csvBuilder.toString().toByteArray())
            }
            compartirArchivoCSV(file)
        } catch (e: Exception) {
            Log.e("ExportarCSV", "Error al generar el archivo CSV", e)
            Toast.makeText(this, "Error al generar el archivo CSV", Toast.LENGTH_LONG).show()
        }
    }

    private fun compartirArchivoCSV(file: File) {
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Rendición de Viaje: $nombreViaje")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Compartir Rendición CSV"))
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

    // Dentro de la clase DetalleViajeActivity

    private fun exportarRecibosAPDF() {
        val gastosConRecibo = listaDeGastos.filter { it.urlFotoRecibo.isNotEmpty() }

        if (gastosConRecibo.isEmpty()) {
            Toast.makeText(this, "No hay recibos para exportar", Toast.LENGTH_SHORT).show()
            return
        }

        // Mostramos el ProgressBar para indicar que el proceso comenzó
        // binding.progressBarPDF.visibility = View.VISIBLE
        Toast.makeText(this, "Iniciando descarga de recibos...", Toast.LENGTH_SHORT).show()

        // La lógica para descargar las imágenes y crear el PDF irá aquí en el siguiente paso
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