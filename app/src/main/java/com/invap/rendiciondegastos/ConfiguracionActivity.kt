package com.invap.rendiciondegastos

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.invap.rendiciondegastos.databinding.ActivityConfiguracionBinding

class ConfiguracionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConfiguracionBinding

    private val monedasList = mutableListOf<String>()
    private lateinit var monedasAdapter: ConfiguracionAdapter

    private val tiposGastoList = mutableListOf<String>()
    private lateinit var tiposGastoAdapter: ConfiguracionAdapter

    // Variables para la lista de Formas de Pago
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
        // Configuración de Monedas
        monedasAdapter = ConfiguracionAdapter(monedasList) { monedaAEliminar ->
            val position = monedasList.indexOf(monedaAEliminar)
            if (position != -1) {
                monedasList.removeAt(position)
                monedasAdapter.notifyItemRemoved(position)
            }
        }
        binding.recyclerViewMonedas.adapter = monedasAdapter
        binding.recyclerViewMonedas.layoutManager = LinearLayoutManager(this)

        // Configuración de Tipos de Gasto
        tiposGastoAdapter = ConfiguracionAdapter(tiposGastoList) { tipoGastoAEliminar ->
            val position = tiposGastoList.indexOf(tipoGastoAEliminar)
            if (position != -1) {
                tiposGastoList.removeAt(position)
                tiposGastoAdapter.notifyItemRemoved(position)
            }
        }
        binding.recyclerViewTiposGasto.adapter = tiposGastoAdapter
        binding.recyclerViewTiposGasto.layoutManager = LinearLayoutManager(this)

        // Configuración de Formas de Pago con el nuevo adaptador
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
        // Botón "Añadir Moneda"
        binding.buttonAnadirMoneda.setOnClickListener {
            val nuevaMoneda = binding.editTextNuevaMoneda.text.toString().trim()
            if (nuevaMoneda.isNotEmpty() && !monedasList.contains(nuevaMoneda)) {
                monedasList.add(nuevaMoneda)
                monedasAdapter.notifyItemInserted(monedasList.size - 1)
                binding.editTextNuevaMoneda.text?.clear()
            }
        }

        // Botón "Añadir Tipo de Gasto"
        binding.buttonAnadirTipoGasto.setOnClickListener {
            val nuevoTipoGasto = binding.editTextNuevoTipoGasto.text.toString().trim()
            if (nuevoTipoGasto.isNotEmpty() && !tiposGastoList.contains(nuevoTipoGasto)) {
                tiposGastoList.add(nuevoTipoGasto)
                tiposGastoAdapter.notifyItemInserted(tiposGastoList.size - 1)
                binding.editTextNuevoTipoGasto.text?.clear()
            }
        }

        // Botón "Añadir Forma de Pago"
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

        // Botón "Guardar Configuración"
        binding.buttonGuardarConfig.setOnClickListener {
            guardarConfiguracion()
        }
    }

    private fun cargarConfiguracion() {
        val sharedPref = getSharedPreferences("RendicionDeGastosPrefs", Context.MODE_PRIVATE)
        binding.editTextNombrePersona.setText(sharedPref.getString("NOMBRE_PERSONA", ""))
        binding.editTextLegajo.setText(sharedPref.getString("LEGAJO", ""))
        binding.editTextCentroCostos.setText(sharedPref.getString("CENTRO_COSTOS", ""))

        // Cargar monedas
        val monedasGuardadas = sharedPref.getStringSet("MONEDAS", setOf("Pesos", "Dólar"))
        monedasList.clear()
        monedasList.addAll(monedasGuardadas ?: emptySet())
        monedasAdapter.notifyDataSetChanged()

        // Cargar tipos de gasto
        val tiposGastoGuardados = sharedPref.getStringSet("TIPOS_GASTO", setOf("Transporte", "Comida", "Alojamiento"))
        tiposGastoList.clear()
        tiposGastoList.addAll(tiposGastoGuardados ?: emptySet())
        tiposGastoAdapter.notifyDataSetChanged()

        // Cargar formas de pago
        val formasPagoGuardadas = sharedPref.getStringSet("FORMAS_PAGO", setOf("Tarjeta de Crédito::TC", "Efectivo::EFE"))
        formasPagoList.clear()
        formasPagoGuardadas?.forEach {
            val partes = it.split("::")
            if (partes.size == 2) {
                formasPagoList.add(FormaDePago(partes[0], partes[1]))
            }
        }
        formasPagoAdapter.notifyDataSetChanged()
    }

    private fun guardarConfiguracion() {
        val sharedPref = getSharedPreferences("RendicionDeGastosPrefs", Context.MODE_PRIVATE)

        // Convertimos la lista de objetos FormaDePago a un Set de Strings para guardarla
        val formasPagoAGuardar = formasPagoList.map { "${it.nombre}::${it.prefijo}" }.toSet()

        with(sharedPref.edit()) {
            putString("NOMBRE_PERSONA", binding.editTextNombrePersona.text.toString())
            putString("LEGAJO", binding.editTextLegajo.text.toString())
            putString("CENTRO_COSTOS", binding.editTextCentroCostos.text.toString())

            putStringSet("MONEDAS", monedasList.toSet())
            putStringSet("TIPOS_GASTO", tiposGastoList.toSet())
            putStringSet("FORMAS_PAGO", formasPagoAGuardar)

            apply()
        }
        Toast.makeText(this, "Configuración guardada", Toast.LENGTH_SHORT).show()
        finish()
    }
}