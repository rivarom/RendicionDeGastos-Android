package com.invap.rendiciondegastos

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

// 1. Añadimos el nuevo parámetro para el clic largo
class ViajesAdapter(
    private val listaDeViajes: List<Viaje>,
    private val onItemClicked: (Viaje) -> Unit,
    private val onItemLongClicked: (Viaje) -> Unit
) : RecyclerView.Adapter<ViajesAdapter.ViajeViewHolder>() {

    inner class ViajeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nombreViaje: TextView = itemView.findViewById(R.id.textViewNombreViaje)
        val fechaViaje: TextView = itemView.findViewById(R.id.textViewFechaViaje)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViajeViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_viaje, parent, false)
        return ViajeViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViajeViewHolder, position: Int) {
        val viajeActual = listaDeViajes[position]
        holder.nombreViaje.text = viajeActual.nombre
        holder.fechaViaje.text = viajeActual.fecha

        // Configuración del clic normal para ver detalles
        holder.itemView.setOnClickListener {
            onItemClicked(viajeActual)
        }

        // 2. Configuración del clic largo para eliminar
        holder.itemView.setOnLongClickListener {
            onItemLongClicked(viajeActual)
            true // Devolvemos 'true' para indicar que el evento fue manejado.
        }
    }

    override fun getItemCount(): Int {
        return listaDeViajes.size
    }
}