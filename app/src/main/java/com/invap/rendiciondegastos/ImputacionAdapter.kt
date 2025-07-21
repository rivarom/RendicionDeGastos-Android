package com.invap.rendiciondegastos

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

// Clase de datos para manejar el par PT y WP
data class Imputacion(val pt: String, val wp: String)

class ImputacionAdapter(
    private val items: MutableList<Imputacion>,
    private val onItemDeleted: (Imputacion) -> Unit
) : RecyclerView.Adapter<ImputacionAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textViewPT: TextView = itemView.findViewById(R.id.textViewPT)
        val textViewWP: TextView = itemView.findViewById(R.id.textViewWP)
        val botonEliminar: ImageButton = itemView.findViewById(R.id.buttonEliminarItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_configuracion_imputacion, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.textViewPT.text = "PT: ${item.pt}"
        holder.textViewWP.text = "WP: ${item.wp}"

        holder.botonEliminar.setOnClickListener {
            onItemDeleted(item)
        }
    }

    override fun getItemCount(): Int {
        return items.size
    }
}