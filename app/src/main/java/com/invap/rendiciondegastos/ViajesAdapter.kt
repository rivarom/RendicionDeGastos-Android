package com.invap.rendiciondegastos

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ViajesAdapter(
    private val listaDeViajes: List<Viaje>,
    private val onItemClicked: (Viaje) -> Unit
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

        holder.itemView.setOnClickListener {
            onItemClicked(viajeActual)
        }
    }

    override fun getItemCount(): Int {
        return listaDeViajes.size
    }
}