package com.invap.rendiciondegastos

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

// Un data class simple para manejar el par de datos (Nombre y Prefijo)
data class FormaDePago(val nombre: String, val prefijo: String)

class ConfiguracionConPrefijoAdapter(
    private val items: MutableList<FormaDePago>,
    private val onItemDeleted: (FormaDePago) -> Unit
) : RecyclerView.Adapter<ConfiguracionConPrefijoAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nombreItem: TextView = itemView.findViewById(R.id.textViewNombreItem)
        val prefijoItem: TextView = itemView.findViewById(R.id.textViewPrefijoItem)
        val botonEliminar: ImageButton = itemView.findViewById(R.id.buttonEliminarItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // Le decimos que use el nuevo layout que creamos
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_configuracion_con_prefijo, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.nombreItem.text = item.nombre
        holder.prefijoItem.text = "Prefijo: ${item.prefijo}"

        holder.botonEliminar.setOnClickListener {
            onItemDeleted(item)
        }
    }

    override fun getItemCount(): Int {
        return items.size
    }
}