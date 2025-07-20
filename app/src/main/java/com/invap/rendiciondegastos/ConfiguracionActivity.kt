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

    // 1. Añadimos variables para la nueva lista
    private val formasPagoList = mutableListOf<String>()
    private lateinit var formasPagoAdapter: ConfiguracionAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConfiguracionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerViews()
        cargarConfiguracion()
        setupButtons()
    }

    private fun setupRecyclerViews() {
        // Configurar RecyclerView de Monedas
        monedasAdapter = ConfiguracionAdapter(monedasList) { monedaAEliminar ->
            val position = monedasList.indexOf(monedaAEliminar)
            if (position != -1) {
                monedasList.removeAt(position)
                monedasAdapter.notifyItemRemoved(position)
            }
        }
        binding.recyclerViewMonedas.adapter = monedasAdapter
        binding.recyclerViewMonedas.layoutManager = LinearLayoutManager(this)

        // Configurar RecyclerView de Tipos de Gasto
        tiposGastoAdapter = ConfiguracionAdapter(tiposGastoList) { tipoGastoAEliminar ->
            val position = tiposGastoList.indexOf(tipoGastoAEliminar)
            if (position != -1) {
                tiposGastoList.removeAt(position)
                tiposGastoAdapter.notifyItemRemoved(position)
            }
        }
        binding.recyclerViewTiposGasto.adapter = tiposGastoAdapter
        binding.recyclerViewTiposGasto.layoutManager = LinearLayoutManager(this)

        // 2. Configuramos el nuevo RecyclerView
        formasPagoAdapter = ConfiguracionAdapter(formasPagoList) { formaPagoAEliminar ->
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

        // 3. Añadimos lógica para el nuevo botón
        binding.buttonAnadirFormaPago.setOnClickListener {
            val nuevaFormaPago = binding.editTextNuevaFormaPago.text.toString().trim()
            if (nuevaFormaPago.isNotEmpty() && !formasPagoList.contains(nuevaFormaPago)) {
                formasPagoList.add(nuevaFormaPago)
                formasPagoAdapter.notifyItemInserted(formasPagoList.size - 1)
                binding.editTextNuevaFormaPago.text?.clear()
            }
        }

        binding.buttonGuardarConfig.setOnClickListener {
            guardarConfiguracion()
        }
    }

    private fun cargarConfiguracion() {
        val sharedPref = getSharedPreferences("RendicionDeGastosPrefs", Context.MODE_PRIVATE)
        binding.editTextNombrePersona.setText(sharedPref.getString("NOMBRE_PERSONA", ""))
        binding.editTextLegajo.setText(sharedPref.getString("LEGAJO", ""))
        binding.editTextCentroCostos.setText(sharedPref.getString("CENTRO_COSTOS", ""))

        val monedasGuardadas = sharedPref.getStringSet("MONEDAS", setOf("Pesos", "Dólar"))
        monedasList.clear()
        monedasList.addAll(monedasGuardadas ?: emptySet())
        monedasAdapter.notifyDataSetChanged()

        val tiposGastoGuardados = sharedPref.getStringSet("TIPOS_GASTO", setOf("Transporte", "Comida", "Alojamiento"))
        tiposGastoList.clear()
        tiposGastoList.addAll(tiposGastoGuardados ?: emptySet())
        tiposGastoAdapter.notifyDataSetChanged()

        // 4. Cargamos la nueva lista
        val formasPagoGuardadas = sharedPref.getStringSet("FORMAS_PAGO", setOf("Tarjeta de Crédito", "Efectivo"))
        formasPagoList.clear()
        formasPagoList.addAll(formasPagoGuardadas ?: emptySet())
        formasPagoAdapter.notifyDataSetChanged()
    }

    private fun guardarConfiguracion() {
        val sharedPref = getSharedPreferences("RendicionDeGastosPrefs", Context.MODE_PRIVATE)

        with(sharedPref.edit()) {
            putString("NOMBRE_PERSONA", binding.editTextNombrePersona.text.toString())
            putString("LEGAJO", binding.editTextLegajo.text.toString())
            putString("CENTRO_COSTOS", binding.editTextCentroCostos.text.toString())

            putStringSet("MONEDAS", monedasList.toSet())
            putStringSet("TIPOS_GASTO", tiposGastoList.toSet())
            // 5. Guardamos la nueva lista
            putStringSet("FORMAS_PAGO", formasPagoList.toSet())

            apply()
        }
        Toast.makeText(this, "Configuración guardada", Toast.LENGTH_SHORT).show()
        finish()
    }
}