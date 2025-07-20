package com.invap.rendiciondegastos

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.recyclerview.widget.LinearLayoutManager
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
            val nombre = data?.getStringExtra(NuevoViajeActivity.EXTRA_NOMBRE)
            val fecha = data?.getStringExtra(NuevoViajeActivity.EXTRA_FECHA)

            if (nombre != null && fecha != null) {
                guardarNuevoViaje(nombre, fecha)
            }
        }
    }
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                // Abrimos la pantalla de configuración
                startActivity(Intent(this, ConfiguracionActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        // La llamada a installSplashScreen() debe ser lo primero
        installSplashScreen()

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // 2. Establecemos la Toolbar como la barra de acción de la actividad
        setSupportActionBar(binding.toolbar)

        // 1. Actualizamos la creación del adaptador para incluir la nueva función de clic largo
        adapter = ViajesAdapter(
            listaDeViajes,
            onItemClicked = { viaje ->
                val intent = Intent(this, DetalleViajeActivity::class.java)
                intent.putExtra("EXTRA_VIAJE_ID", viaje.id)
                intent.putExtra("EXTRA_VIAJE_NOMBRE", viaje.nombre)
                startActivity(intent)
            },
            onItemLongClicked = { viaje ->
                mostrarDialogoDeConfirmacion(viaje)
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
        cargarViajesDesdeFirestore()
    }

    // 2. Nueva función para mostrar un diálogo de alerta
    private fun mostrarDialogoDeConfirmacion(viaje: Viaje) {
        AlertDialog.Builder(this)
            .setTitle("Confirmar Eliminación")
            .setMessage("¿Estás seguro de que quieres eliminar el viaje '${viaje.nombre}'? Esta acción no se puede deshacer.")
            .setPositiveButton("Eliminar") { _, _ ->
                eliminarViaje(viaje)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // 3. Nueva función para eliminar el viaje de Firestore
    private fun eliminarViaje(viaje: Viaje) {
        if (viaje.id.isEmpty()) {
            Toast.makeText(this, "Error: ID del viaje no encontrado", Toast.LENGTH_SHORT).show()
            return
        }
        db.collection("viajes").document(viaje.id)
            .delete()
            .addOnSuccessListener {
                Log.d("MainActivity", "Viaje eliminado con éxito")
                Toast.makeText(this, "Viaje eliminado", Toast.LENGTH_SHORT).show()
                cargarViajesDesdeFirestore() // Recargamos la lista para que desaparezca el elemento
            }
            .addOnFailureListener { e ->
                Log.w("MainActivity", "Error al eliminar el viaje", e)
                Toast.makeText(this, "Error al eliminar el viaje", Toast.LENGTH_SHORT).show()
            }
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
        // 1. Accedemos a la configuración guardada
        val sharedPref = getSharedPreferences("RendicionDeGastosPrefs", Context.MODE_PRIVATE)
        val nombrePersona = sharedPref.getString("NOMBRE_PERSONA", "") ?: ""
        val legajo = sharedPref.getString("LEGAJO", "") ?: ""
        val centroCostos = sharedPref.getString("CENTRO_COSTOS", "") ?: ""

        // 2. Creamos el objeto del nuevo viaje incluyendo todos los datos
        val nuevoViaje = hashMapOf(
            "nombre" to nombre,
            "fecha" to fecha,
            "nombrePersona" to nombrePersona,
            "legajo" to legajo,
            "centroCostos" to centroCostos
        )

        // 3. Guardamos el objeto completo en Firestore
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