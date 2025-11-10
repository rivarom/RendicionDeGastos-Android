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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
// Se eliminan todas las importaciones de Firebase (excepto las que se eliminarán más adelante)
// import com.google.firebase.firestore.ktx.firestore // Eliminado
// import com.google.firebase.ktx.Firebase // Eliminado
import com.invap.rendiciondegastos.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val listaDeViajes: MutableList<Viaje> = mutableListOf()
    private lateinit var adapter: ViajesAdapter
    // private val db = Firebase.firestore // Reemplazado por Room
    private lateinit var db: AppDatabase // Instancia de la base de datos Room

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

            // Modificado: El ID ahora es Long. 0L significa que es un viaje nuevo.
            val id = data?.getLongExtra(NuevoViajeActivity.EXTRA_VIAJE_ID, 0L)

            if (nombre != null && fecha != null && monedaDefecto != null && imputacionPT != null && imputacionWP != null) {
                if (id == 0L) { // Modificado: se comprueba 0L en lugar de null
                    guardarNuevoViaje(nombre, fecha, monedaDefecto, imputacionPT, imputacionWP)
                } else {
                    actualizarViaje(id!!, nombre, fecha, monedaDefecto, imputacionPT, imputacionWP)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Lógica de autenticación ya eliminada en el paso 5

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        // Añadido: Inicializar la base de datos Room
        db = AppDatabase.getInstance(applicationContext)

        // Habilita el modo Edge-to-Edge
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Aplica el relleno para las barras del sistema
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        adapter = ViajesAdapter(
            listaDeViajes,
            onItemClicked = { viaje: Viaje ->
                val intent = Intent(this, DetalleViajeActivity::class.java)
                // Modificado: Se pasa el ID como Long
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
        // Lógica de autenticación ya eliminada
        verificarConfiguracionYContinuar()
    }

    private fun verificarConfiguracionYContinuar() {
        val userPrefs = getSharedPreferences("UserPrefs_local", Context.MODE_PRIVATE)
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
            cargarViajesLocales() // Modificado: Carga desde Room
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
            // Lógica de Logout ya eliminada
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
                        intent.putExtra("EXTRA_VIAJE_ID", viaje.id) // Pasa Long
                        intent.putExtra("EXTRA_VIAJE_NOMBRE", viaje.nombre)
                        intent.putExtra("EXTRA_VIAJE_MONEDA_DEFECTO", viaje.monedaPorDefecto)
                        intent.putExtra("EXTRA_VIAJE_IMPUTACION_PT", viaje.imputacionPorDefectoPT)
                        intent.putExtra("EXTRA_VIAJE_IMPUTACION_WP", viaje.imputacionPorDefectoWP)
                        startActivity(intent)
                    }
                    1 -> { // Editar Viaje
                        val intent = Intent(this, NuevoViajeActivity::class.java)
                        intent.putExtra(NuevoViajeActivity.EXTRA_VIAJE_ID, viaje.id) // Pasa Long
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
        // Modificado: Lógica de Room en una coroutine
        lifecycleScope.launch {
            try {
                db.viajeDao().deleteViaje(viaje)
                Toast.makeText(this@MainActivity, "Viaje eliminado", Toast.LENGTH_SHORT).show()
                cargarViajesLocales() // Recarga la lista
            } catch (e: Exception) {
                Log.e("MainActivity", "Error al eliminar el viaje", e)
                Toast.makeText(this@MainActivity, "Error al eliminar el viaje", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun cargarViajesLocales() {
        // Modificado: Lógica de Room en una coroutine
        lifecycleScope.launch {
            try {
                val viajes = db.viajeDao().getAllViajes()
                listaDeViajes.clear()
                listaDeViajes.addAll(viajes)
                adapter.notifyDataSetChanged()
            } catch (e: Exception) {
                Log.e("MainActivity", "Error al cargar viajes locales", e)
                Toast.makeText(this@MainActivity, "Error al cargar los viajes.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun guardarNuevoViaje(nombre: String, fecha: String, monedaDefecto: String, imputacionPT: String, imputacionWP: String) {
        val sharedPref = getSharedPreferences("UserPrefs_local", Context.MODE_PRIVATE)
        val nombrePersona = sharedPref.getString("NOMBRE_PERSONA", "") ?: ""
        val legajo = sharedPref.getString("LEGAJO", "") ?: ""
        val centroCostos = sharedPref.getString("CENTRO_COSTOS", "") ?: ""

        // Modificado: Se crea un objeto Viaje
        val nuevoViaje = Viaje(
            nombre = nombre,
            fecha = fecha,
            monedaPorDefecto = monedaDefecto,
            imputacionPorDefectoPT = imputacionPT,
            imputacionPorDefectoWP = imputacionWP,
            nombrePersona = nombrePersona,
            legajo = legajo,
            centroCostos = centroCostos
            // El ID es 0 por defecto, Room lo autogenerará
        )

        // Modificado: Lógica de Room en una coroutine
        lifecycleScope.launch {
            try {
                db.viajeDao().insertViaje(nuevoViaje)
                Toast.makeText(this@MainActivity, "Viaje guardado", Toast.LENGTH_SHORT).show()
                cargarViajesLocales() // Recarga la lista
            } catch (e: Exception) {
                Log.e("MainActivity", "Error al guardar el viaje", e)
                Toast.makeText(this@MainActivity, "Error al guardar el viaje", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun actualizarViaje(id: Long, nombre: String, fecha: String, monedaDefecto: String, imputacionPT: String, imputacionWP: String) {
        val sharedPref = getSharedPreferences("UserPrefs_local", Context.MODE_PRIVATE)
        val nombrePersona = sharedPref.getString("NOMBRE_PERSONA", "") ?: ""
        val legajo = sharedPref.getString("LEGAJO", "") ?: ""
        val centroCostos = sharedPref.getString("CENTRO_COSTOS", "") ?: ""

        // Modificado: Se crea un objeto Viaje con el ID existente
        val viajeActualizado = Viaje(
            id = id, // Se pasa el ID para la actualización
            nombre = nombre,
            fecha = fecha,
            monedaPorDefecto = monedaDefecto,
            imputacionPorDefectoPT = imputacionPT,
            imputacionPorDefectoWP = imputacionWP,
            nombrePersona = nombrePersona,
            legajo = legajo,
            centroCostos = centroCostos
        )

        // Modificado: Lógica de Room en una coroutine
        lifecycleScope.launch {
            try {
                db.viajeDao().updateViaje(viajeActualizado)
                Toast.makeText(this@MainActivity, "Viaje actualizado", Toast.LENGTH_SHORT).show()
                cargarViajesLocales() // Recarga la lista
            } catch (e: Exception) {
                Log.e("MainActivity", "Error al actualizar el viaje", e)
                Toast.makeText(this@MainActivity, "Error al actualizar el viaje", Toast.LENGTH_SHORT).show()
            }
        }
    }
}