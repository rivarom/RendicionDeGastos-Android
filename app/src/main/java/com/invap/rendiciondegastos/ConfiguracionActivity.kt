package com.invap.rendiciondegastos

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.invap.rendiciondegastos.databinding.ActivityConfiguracionBinding

class ConfiguracionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConfiguracionBinding

    // Listas para los adaptadores
    private val monedasList = mutableListOf<String>()
    private lateinit var monedasAdapter: ConfiguracionAdapter
    private val tiposGastoList = mutableListOf<String>()
    private lateinit var tiposGastoAdapter: ConfiguracionAdapter
    private val formasPagoList = mutableListOf<FormaDePago>()
    private lateinit var formasPagoAdapter: ConfiguracionConPrefijoAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConfiguracionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerViews()
        cargarConfiguracion()
        setupButtons()
    }

    private fun setupRecyclerViews() {
        // La configuración de los RecyclerViews no cambia
        monedasAdapter = ConfiguracionAdapter(monedasList) { monedaAEliminar ->
            val position = monedasList.indexOf(monedaAEliminar)
            if (position != -1) {
                monedasList.removeAt(position)
                monedasAdapter.notifyItemRemoved(position)
            }
        }
        binding.recyclerViewMonedas.adapter = monedasAdapter
        binding.recyclerViewMonedas.layoutManager = LinearLayoutManager(this)

        tiposGastoAdapter = ConfiguracionAdapter(tiposGastoList) { tipoGastoAEliminar ->
            val position = tiposGastoList.indexOf(tipoGastoAEliminar)
            if (position != -1) {
                tiposGastoList.removeAt(position)
                tiposGastoAdapter.notifyItemRemoved(position)
            }
        }
        binding.recyclerViewTiposGasto.adapter = tiposGastoAdapter
        binding.recyclerViewTiposGasto.layoutManager = LinearLayoutManager(this)

        formasPagoAdapter = ConfiguracionConPrefijoAdapter(formasPagoList) { formaPagoAEliminar ->
            val position = formasPagoList.indexOf(formaPagoAEliminar)
            if (position != -1) {
                formasPagoList.removeAt(position)
                formasPagoAdapter.notifyItemRemoved(position)
            }
        }
        binding.recyclerViewFormasPago.adapter = formasPagoAdapter
        binding.recyclerViewFormasPago.layoutManager = LinearLayoutManager(this)
    }

    private fun setupButtons() {
        // La lógica de los botones de "Añadir" no cambia
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

        binding.buttonGuardarConfig.setOnClickListener {
            guardarConfiguracion()
        }
    }

    private fun cargarConfiguracion() {
        val userId = Firebase.auth.currentUser?.uid
        if (userId == null) {
            Toast.makeText(this, "Error: No se encontró usuario", Toast.LENGTH_SHORT).show()
            return
        }

        // 1. Usamos un solo archivo de preferencias, específico para el usuario
        val userPrefs = getSharedPreferences("UserPrefs_$userId", Context.MODE_PRIVATE)

        // Cargamos los datos personales
        binding.editTextNombrePersona.setText(userPrefs.getString("NOMBRE_PERSONA", ""))
        binding.editTextLegajo.setText(userPrefs.getString("LEGAJO", ""))
        binding.editTextCentroCostos.setText(userPrefs.getString("CENTRO_COSTOS", ""))

        // 2. Definimos los valores por defecto para un usuario nuevo
        val monedasPorDefecto = setOf("Pesos", "Dólar", "Euro")
        val tiposGastoPorDefecto = setOf("Transporte", "Comida", "Alojamiento")
        val formasPagoPorDefecto = setOf("Tarjeta de Débito Recargable::TD", "Efectivo USD::EFE")

        // 3. Cargamos las listas. Si no existen, usamos los valores por defecto
        val monedasGuardadas = userPrefs.getStringSet("MONEDAS", monedasPorDefecto)
        monedasList.clear()
        monedasList.addAll(monedasGuardadas ?: monedasPorDefecto)
        monedasAdapter.notifyDataSetChanged()

        val tiposGastoGuardados = userPrefs.getStringSet("TIPOS_GASTO", tiposGastoPorDefecto)
        tiposGastoList.clear()
        tiposGastoList.addAll(tiposGastoGuardados ?: tiposGastoPorDefecto)
        tiposGastoAdapter.notifyDataSetChanged()

        val formasPagoGuardadas = userPrefs.getStringSet("FORMAS_PAGO", formasPagoPorDefecto)
        formasPagoList.clear()
        (formasPagoGuardadas ?: formasPagoPorDefecto).forEach {
            val partes = it.split("::")
            if (partes.size == 2) {
                formasPagoList.add(FormaDePago(partes[0], partes[1]))
            }
        }
        formasPagoAdapter.notifyDataSetChanged()
    }

    private fun guardarConfiguracion() {
        val userId = Firebase.auth.currentUser?.uid
        if (userId == null) {
            Toast.makeText(this, "Error al guardar: No se encontró usuario", Toast.LENGTH_SHORT).show()
            return
        }

        // 1. Usamos un solo archivo de preferencias, específico para el usuario
        val userPrefs = getSharedPreferences("UserPrefs_$userId", Context.MODE_PRIVATE)
        val formasPagoAGuardar = formasPagoList.map { "${it.nombre}::${it.prefijo}" }.toSet()

        with(userPrefs.edit()) {
            // Guardamos los datos personales
            putString("NOMBRE_PERSONA", binding.editTextNombrePersona.text.toString())
            putString("LEGAJO", binding.editTextLegajo.text.toString())
            putString("CENTRO_COSTOS", binding.editTextCentroCostos.text.toString())

            // Guardamos las listas personales del usuario
            putStringSet("MONEDAS", monedasList.toSet())
            putStringSet("TIPOS_GASTO", tiposGastoList.toSet())
            putStringSet("FORMAS_PAGO", formasPagoAGuardar)

            apply()
        }

        Toast.makeText(this, "Configuración guardada", Toast.LENGTH_SHORT).show()
        finish()
    }
}