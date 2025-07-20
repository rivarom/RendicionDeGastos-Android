package com.invap.rendiciondegastos

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ConfiguracionAdapter(
    private val items: MutableList<String>,
    private val onItemDeleted: (String) -> Unit
) : RecyclerView.Adapter<ConfiguracionAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nombreItem: TextView = itemView.findViewById(R.id.textViewNombreItem)
        val botonEliminar: ImageButton = itemView.findViewById(R.id.buttonEliminarItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_configuracion, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.nombreItem.text = item

        holder.botonEliminar.setOnClickListener {
            onItemDeleted(item)
        }
    }

    override fun getItemCount(): Int {
        return items.size
    }
}