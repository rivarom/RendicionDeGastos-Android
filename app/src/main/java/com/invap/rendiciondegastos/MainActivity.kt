package com.invap.rendiciondegastos

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.invap.rendiciondegastos.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val listaDeViajes: MutableList<Viaje> = mutableListOf()
    private lateinit var adapter: ViajesAdapter
    private val db = Firebase.firestore

    private val nuevoViajeResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data: Intent? = result.data
            val nombre = data?.getStringExtra(NuevoViajeActivity.EXTRA_VIAJE_NOMBRE)
            val fecha = data?.getStringExtra(NuevoViajeActivity.EXTRA_VIAJE_FECHA)
            val monedaDefecto = data?.getStringExtra(NuevoViajeActivity.EXTRA_VIAJE_MONEDA_DEFECTO)
            val imputacionPT = data?.getStringExtra(NuevoViajeActivity.EXTRA_VIAJE_IMPUTACION_PT)
            val imputacionWP = data?.getStringExtra(NuevoViajeActivity.EXTRA_VIAJE_IMPUTACION_WP)
            val id = data?.getStringExtra(NuevoViajeActivity.EXTRA_VIAJE_ID)

            if (nombre != null && fecha != null && monedaDefecto != null && imputacionPT != null && imputacionWP != null) {
                if (id == null) {
                    guardarNuevoViaje(nombre, fecha, monedaDefecto, imputacionPT, imputacionWP)
                } else {
                    actualizarViaje(id, nombre, fecha, monedaDefecto, imputacionPT, imputacionWP)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Firebase.auth.currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        adapter = ViajesAdapter(
            listaDeViajes,
            onItemClicked = { viaje: Viaje ->
                val intent = Intent(this, DetalleViajeActivity::class.java)
                intent.putExtra("EXTRA_VIAJE_ID", viaje.id)
                intent.putExtra("EXTRA_VIAJE_NOMBRE", viaje.nombre)
                intent.putExtra("EXTRA_VIAJE_MONEDA_DEFECTO", viaje.monedaPorDefecto)
                intent.putExtra("EXTRA_VIAJE_IMPUTACION_PT", viaje.imputacionPorDefectoPT)
                intent.putExtra("EXTRA_VIAJE_IMPUTACION_WP", viaje.imputacionPorDefectoWP)
                startActivity(intent)
            },
            onItemLongClicked = { viaje: Viaje ->
                mostrarDialogoDeAcciones(viaje)
            }
        )

        binding.recyclerViewViajes.adapter = adapter
        binding.recyclerViewViajes.layoutManager = LinearLayoutManager(this)

        binding.fabAgregarViaje.setOnClickListener {
            val intent = Intent(this, NuevoViajeActivity::class.java)
            nuevoViajeResultLauncher.launch(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        if (Firebase.auth.currentUser != null) {
            verificarConfiguracionYContinuar()
        }
    }

    private fun verificarConfiguracionYContinuar() {
        val userId = Firebase.auth.currentUser?.uid ?: return
        val userPrefs = getSharedPreferences("UserPrefs_$userId", Context.MODE_PRIVATE)
        val configuracionCompleta = userPrefs.getBoolean("CONFIGURACION_COMPLETA", false)

        if (!configuracionCompleta) {
            // Si la configuración no está completa, forzamos al usuario a ir a la pantalla de Configuración.
            binding.fabAgregarViaje.visibility = View.GONE // Ocultamos el botón de añadir
            AlertDialog.Builder(this)
                .setTitle("Configuración Requerida")
                .setMessage("Antes de crear tu primer viaje, por favor completa tu configuración.")
                .setPositiveButton("Ir a Configuración") { _, _ ->
                    startActivity(Intent(this, ConfiguracionActivity::class.java))
                }
                .setCancelable(false)
                .show()
        } else {
            // Si la configuración está completa, cargamos los viajes y mostramos el botón.
            binding.fabAgregarViaje.visibility = View.VISIBLE
            cargarViajesDesdeFirestore()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, ConfiguracionActivity::class.java))
                true
            }
            R.id.action_logout -> {
                Firebase.auth.signOut()
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun mostrarDialogoDeAcciones(viaje: Viaje) {
        val opciones = arrayOf("Ver Gastos", "Editar Viaje", "Eliminar Viaje")

        AlertDialog.Builder(this)
            .setTitle(viaje.nombre)
            .setItems(opciones) { _, which ->
                when (which) {
                    0 -> { // Ver Gastos
                        val intent = Intent(this, DetalleViajeActivity::class.java)
                        intent.putExtra("EXTRA_VIAJE_ID", viaje.id)
                        intent.putExtra("EXTRA_VIAJE_NOMBRE", viaje.nombre)
                        intent.putExtra("EXTRA_VIAJE_MONEDA_DEFECTO", viaje.monedaPorDefecto)
                        intent.putExtra("EXTRA_VIAJE_IMPUTACION_PT", viaje.imputacionPorDefectoPT)
                        intent.putExtra("EXTRA_VIAJE_IMPUTACION_WP", viaje.imputacionPorDefectoWP)
                        startActivity(intent)
                    }
                    1 -> { // Editar Viaje
                        val intent = Intent(this, NuevoViajeActivity::class.java)
                        intent.putExtra(NuevoViajeActivity.EXTRA_VIAJE_ID, viaje.id)
                        intent.putExtra(NuevoViajeActivity.EXTRA_VIAJE_NOMBRE, viaje.nombre)
                        intent.putExtra(NuevoViajeActivity.EXTRA_VIAJE_FECHA, viaje.fecha)
                        intent.putExtra(NuevoViajeActivity.EXTRA_VIAJE_MONEDA_DEFECTO, viaje.monedaPorDefecto)
                        intent.putExtra(NuevoViajeActivity.EXTRA_VIAJE_IMPUTACION_PT, viaje.imputacionPorDefectoPT)
                        intent.putExtra(NuevoViajeActivity.EXTRA_VIAJE_IMPUTACION_WP, viaje.imputacionPorDefectoWP)
                        nuevoViajeResultLauncher.launch(intent)
                    }
                    2 -> { // Eliminar Viaje
                        mostrarDialogoDeConfirmacion(viaje)
                    }
                }
            }
            .show()
    }

    private fun mostrarDialogoDeConfirmacion(viaje: Viaje) {
        AlertDialog.Builder(this)
            .setTitle("Confirmar Eliminación")
            .setMessage("¿Estás seguro de que quieres eliminar el viaje '${viaje.nombre}'?")
            .setPositiveButton("Eliminar") { _, _ ->
                eliminarViaje(viaje)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun eliminarViaje(viaje: Viaje) {
        if (viaje.id.isEmpty()) {
            Toast.makeText(this, "Error: ID del viaje no encontrado", Toast.LENGTH_SHORT).show()
            return
        }
        db.collection("viajes").document(viaje.id)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Viaje eliminado", Toast.LENGTH_SHORT).show()
                cargarViajesDesdeFirestore()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error al eliminar el viaje", Toast.LENGTH_SHORT).show()
            }
    }

    private fun cargarViajesDesdeFirestore() {
        val userId = Firebase.auth.currentUser?.uid ?: return
        db.collection("viajes")
            .whereEqualTo("userId", userId)
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
                Toast.makeText(this, "Error al cargar los viajes.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun guardarNuevoViaje(nombre: String, fecha: String, monedaDefecto: String, imputacionPT: String, imputacionWP: String) {
        val userId = Firebase.auth.currentUser?.uid ?: return
        val sharedPref = getSharedPreferences("UserPrefs_$userId", Context.MODE_PRIVATE)
        val nombrePersona = sharedPref.getString("NOMBRE_PERSONA", "") ?: ""
        val legajo = sharedPref.getString("LEGAJO", "") ?: ""
        val centroCostos = sharedPref.getString("CENTRO_COSTOS", "") ?: ""

        val nuevoViaje = hashMapOf(
            "nombre" to nombre, "fecha" to fecha, "monedaPorDefecto" to monedaDefecto,
            "imputacionPorDefectoPT" to imputacionPT, "imputacionPorDefectoWP" to imputacionWP,
            "nombrePersona" to nombrePersona, "legajo" to legajo, "centroCostos" to centroCostos,
            "userId" to userId
        )
        db.collection("viajes").add(nuevoViaje)
            .addOnSuccessListener {
                Toast.makeText(this, "Viaje guardado", Toast.LENGTH_SHORT).show()
                // No es necesario llamar a cargarViajes aquí, onResume lo hará
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error al guardar el viaje", Toast.LENGTH_SHORT).show()
            }
    }

    private fun actualizarViaje(id: String, nombre: String, fecha: String, monedaDefecto: String, imputacionPT: String, imputacionWP: String) {
        val userId = Firebase.auth.currentUser?.uid ?: return
        val sharedPref = getSharedPreferences("UserPrefs_$userId", Context.MODE_PRIVATE)
        val nombrePersona = sharedPref.getString("NOMBRE_PERSONA", "") ?: ""
        val legajo = sharedPref.getString("LEGAJO", "") ?: ""
        val centroCostos = sharedPref.getString("CENTRO_COSTOS", "") ?: ""

        val viajeActualizado = hashMapOf(
            "nombre" to nombre, "fecha" to fecha, "monedaPorDefecto" to monedaDefecto,
            "imputacionPorDefectoPT" to imputacionPT, "imputacionPorDefectoWP" to imputacionWP,
            "nombrePersona" to nombrePersona, "legajo" to legajo, "centroCostos" to centroCostos,
            "userId" to userId
        )
        db.collection("viajes").document(id).set(viajeActualizado)
            .addOnSuccessListener {
                Toast.makeText(this, "Viaje actualizado", Toast.LENGTH_SHORT).show()
                // No es necesario llamar a cargarViajes aquí, onResume lo hará
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error al actualizar el viaje", Toast.LENGTH_SHORT).show()
            }
    }
}