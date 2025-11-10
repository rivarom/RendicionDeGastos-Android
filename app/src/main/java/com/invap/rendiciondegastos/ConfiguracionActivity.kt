package com.invap.rendiciondegastos

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
// import com.google.firebase.auth.ktx.auth // Eliminado
// import com.google.firebase.ktx.Firebase // Eliminado
import com.invap.rendiciondegastos.databinding.ActivityConfiguracionBinding
import com.invap.rendiciondegastos.Imputacion

class ConfiguracionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConfiguracionBinding

    // Variables para las listas
    private val monedasList = mutableListOf<String>()
    private lateinit var monedasAdapter: ConfiguracionAdapter
    private val tiposGastoList = mutableListOf<String>()
    private lateinit var tiposGastoAdapter: ConfiguracionAdapter
    private val formasPagoList = mutableListOf<FormaDePago>()
    private lateinit var formasPagoAdapter: ConfiguracionConPrefijoAdapter
    private val imputacionesList = mutableListOf<Imputacion>()
    private lateinit var imputacionesAdapter: ImputacionAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConfiguracionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // Habilita el modo Edge-to-Edge
        WindowCompat.setDecorFitsSystemWindows(window, false)

// Aplica el relleno para las barras del sistema
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupRecyclerViews()
        cargarConfiguracion()
        setupButtons()
    }

    private fun setupRecyclerViews() {
        // Monedas
        monedasAdapter = ConfiguracionAdapter(monedasList) { item ->
            monedasList.remove(item)
            monedasAdapter.notifyDataSetChanged()
        }
        binding.recyclerViewMonedas.adapter = monedasAdapter
        binding.recyclerViewMonedas.layoutManager = LinearLayoutManager(this)

        // Tipos de Gasto
        tiposGastoAdapter = ConfiguracionAdapter(tiposGastoList) { item ->
            tiposGastoList.remove(item)
            tiposGastoAdapter.notifyDataSetChanged()
        }
        binding.recyclerViewTiposGasto.adapter = tiposGastoAdapter
        binding.recyclerViewTiposGasto.layoutManager = LinearLayoutManager(this)

        // Formas de Pago
        formasPagoAdapter = ConfiguracionConPrefijoAdapter(formasPagoList) { item ->
            formasPagoList.remove(item)
            formasPagoAdapter.notifyDataSetChanged()
        }
        binding.recyclerViewFormasPago.adapter = formasPagoAdapter
        binding.recyclerViewFormasPago.layoutManager = LinearLayoutManager(this)

        // Imputaciones
        imputacionesAdapter = ImputacionAdapter(imputacionesList) { item ->
            imputacionesList.remove(item)
            imputacionesAdapter.notifyDataSetChanged()
        }
        binding.recyclerViewImputaciones.adapter = imputacionesAdapter
        binding.recyclerViewImputaciones.layoutManager = LinearLayoutManager(this)
    }

    private fun setupButtons() {
        // ... (botones de Moneda, Tipo de Gasto y Forma de Pago sin cambios) ...
        binding.buttonAnadirMoneda.setOnClickListener {
            val nuevaMoneda = binding.editTextNuevaMoneda.text.toString().trim()
            if (nuevaMoneda.isNotEmpty() && !monedasList.contains(nuevaMoneda)) {
                monedasList.add(nuevaMoneda)
                monedasAdapter.notifyItemInserted(monedasList.size - 1)
                binding.editTextNuevaMoneda.text?.clear()
            }
        }

        binding.buttonAnadirTipoGasto.setOnClickListener {
            val nuevoTipoGasto = binding.editTextNuevoTipoGasto.text.toString().trim()
            if (nuevoTipoGasto.isNotEmpty() && !tiposGastoList.contains(nuevoTipoGasto)) {
                tiposGastoList.add(nuevoTipoGasto)
                tiposGastoAdapter.notifyItemInserted(tiposGastoList.size - 1)
                binding.editTextNuevoTipoGasto.text?.clear()
            }
        }

        binding.buttonAnadirFormaPago.setOnClickListener {
            val nombre = binding.editTextNuevaFormaPagoNombre.text.toString().trim()
            val prefijo = binding.editTextNuevaFormaPagoPrefijo.text.toString().trim()
            if (nombre.isNotEmpty() && prefijo.isNotEmpty()) {
                val nuevaFormaPago = FormaDePago(nombre, prefijo)
                if (!formasPagoList.contains(nuevaFormaPago)) {
                    formasPagoList.add(nuevaFormaPago)
                    formasPagoAdapter.notifyItemInserted(formasPagoList.size - 1)
                    binding.editTextNuevaFormaPagoNombre.text?.clear()
                    binding.editTextNuevaFormaPagoPrefijo.text?.clear()
                }
            }
        }

        // Botón "Añadir Imputación"
        binding.buttonAnadirImputacion.setOnClickListener {
            val pt = binding.editTextNuevaImputacionPT.text.toString().trim()
            val wp = binding.editTextNuevaImputacionWP.text.toString().trim()
            if (pt.isNotEmpty() && wp.isNotEmpty()) {
                val nuevaImputacion = Imputacion(pt, wp)
                if (!imputacionesList.contains(nuevaImputacion)) {
                    imputacionesList.add(nuevaImputacion)
                    imputacionesAdapter.notifyItemInserted(imputacionesList.size - 1)
                    binding.editTextNuevaImputacionPT.text?.clear()
                    binding.editTextNuevaImputacionWP.text?.clear()
                }
            }
        }

        binding.buttonGuardarConfig.setOnClickListener {
            guardarConfiguracion()
        }
    }

    private fun cargarConfiguracion() {
        // val userId = Firebase.auth.currentUser?.uid ?: return // Eliminado
        val userPrefs = getSharedPreferences("UserPrefs_local", Context.MODE_PRIVATE) // Modificado

        binding.editTextNombrePersona.setText(userPrefs.getString("NOMBRE_PERSONA", ""))
        binding.editTextLegajo.setText(userPrefs.getString("LEGAJO", ""))
        binding.editTextCentroCostos.setText(userPrefs.getString("CENTRO_COSTOS", ""))

        // Valores por defecto para un usuario nuevo
        val monedasPorDefecto = setOf("Pesos", "Dólar")
        val tiposGastoPorDefecto = setOf("Transporte", "Comida", "Alojamiento")
        // --- CAMBIOS AQUÍ ---
        val formasPagoPorDefecto = setOf("Tarjeta de Débito Recargable::TDR", "Efectivo::EFE","Tarjeta de Crédito::TC")
        val imputacionesPorDefecto = setOf("00::00")

        // Cargar Monedas
        val monedasGuardadas = userPrefs.getStringSet("MONEDAS", monedasPorDefecto)
        monedasList.clear()
        monedasList.addAll(monedasGuardadas ?: monedasPorDefecto)
        monedasAdapter.notifyDataSetChanged()

        // Cargar Tipos de Gasto
        val tiposGastoGuardados = userPrefs.getStringSet("TIPOS_GASTO", tiposGastoPorDefecto)
        tiposGastoList.clear()
        tiposGastoList.addAll(tiposGastoGuardados ?: tiposGastoPorDefecto)
        tiposGastoAdapter.notifyDataSetChanged()

        // Cargar Formas de Pago
        val formasPagoGuardadas = userPrefs.getStringSet("FORMAS_PAGO", formasPagoPorDefecto)
        formasPagoList.clear()
        (formasPagoGuardadas ?: formasPagoPorDefecto).forEach {
            val partes = it.split("::")
            if (partes.size == 2) {
                formasPagoList.add(FormaDePago(partes[0], partes[1]))
            }
        }
        formasPagoAdapter.notifyDataSetChanged()

        // Cargar Imputaciones
        val imputacionesGuardadas = userPrefs.getStringSet("IMPUTACIONES", imputacionesPorDefecto)
        imputacionesList.clear()
        (imputacionesGuardadas ?: imputacionesPorDefecto).forEach {
            val partes = it.split("::")
            if (partes.size == 2) {
                imputacionesList.add(Imputacion(partes[0], partes[1]))
            }
        }
        imputacionesAdapter.notifyDataSetChanged()
    }

    private fun guardarConfiguracion() {
        // val userId = Firebase.auth.currentUser?.uid ?: return // Eliminado
        val userPrefs = getSharedPreferences("UserPrefs_local", Context.MODE_PRIVATE) // Modificado

        val formasPagoAGuardar = formasPagoList.map { "${it.nombre}::${it.prefijo}" }.toSet()
        val imputacionesAGuardar = imputacionesList.map { "${it.pt}::${it.wp}" }.toSet()

        with(userPrefs.edit()) {
            putString("NOMBRE_PERSONA", binding.editTextNombrePersona.text.toString())
            putString("LEGAJO", binding.editTextLegajo.text.toString())
            putString("CENTRO_COSTOS", binding.editTextCentroCostos.text.toString())

            putStringSet("MONEDAS", monedasList.toSet())
            putStringSet("TIPOS_GASTO", tiposGastoList.toSet())
            putStringSet("FORMAS_PAGO", formasPagoAGuardar)
            putStringSet("IMPUTACIONES", imputacionesAGuardar)
            // --- LÍNEA NUEVA ---
            putBoolean("CONFIGURACION_COMPLETA", true) // Marcamos que la configuración se guardó
            apply()
        }
        Toast.makeText(this, "Configuración guardada", Toast.LENGTH_SHORT).show()
        finish()
    }
}