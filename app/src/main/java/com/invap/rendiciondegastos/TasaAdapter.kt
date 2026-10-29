package com.invap.rendiciondegastos

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.math.BigDecimal

/** Tasa de cambio de referencia: unidades de [moneda] por 1 USD. */
data class TasaCambio(val moneda: String, val tasa: Double)

class TasaAdapter(
    private val items: MutableList<TasaCambio>,
    private val onItemDeleted: (TasaCambio) -> Unit
) : RecyclerView.Adapter<TasaAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textoMoneda: TextView = itemView.findViewById(R.id.textViewPT)
        val textoTasa: TextView = itemView.findViewById(R.id.textViewWP)
        val botonEliminar: ImageButton = itemView.findViewById(R.id.buttonEliminarItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // Se reutiliza el diseño de fila de las imputaciones (dos textos y un botón de borrar)
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_configuracion_imputacion, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.textoMoneda.text = item.moneda
        holder.textoTasa.text = "1 USD = ${BigDecimal.valueOf(item.tasa).stripTrailingZeros().toPlainString()} ${item.moneda}"
        holder.botonEliminar.setOnClickListener { onItemDeleted(item) }
    }

    override fun getItemCount(): Int = items.size
}
