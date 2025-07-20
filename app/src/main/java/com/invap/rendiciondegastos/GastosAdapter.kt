package com.invap.rendiciondegastos

import Gasto
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.NumberFormat
import java.util.Locale

class GastosAdapter(private val listaDeGastos: List<Gasto>) :
    RecyclerView.Adapter<GastosAdapter.GastoViewHolder>() {

    inner class GastoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val descripcion: TextView = itemView.findViewById(R.id.textViewDescripcionGasto)
        val fecha: TextView = itemView.findViewById(R.id.textViewFechaGasto)
        val monto: TextView = itemView.findViewById(R.id.textViewMontoGasto)
        // 1. Añadimos la referencia al nuevo ImageView
        val iconoFoto: ImageView = itemView.findViewById(R.id.iconoFotoRecibo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GastoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_gasto, parent, false)
        return GastoViewHolder(view)
    }

    override fun onBindViewHolder(holder: GastoViewHolder, position: Int) {
        val gasto = listaDeGastos[position]
        holder.descripcion.text = gasto.descripcion
        holder.fecha.text = gasto.fecha

        val format: NumberFormat = NumberFormat.getCurrencyInstance(Locale("es", "AR"))
        holder.monto.text = format.format(gasto.monto)

        // 2. Lógica para mostrar/ocultar el ícono
        if (gasto.urlFotoRecibo.isNotEmpty()) {
            holder.iconoFoto.visibility = View.VISIBLE
        } else {
            holder.iconoFoto.visibility = View.GONE
        }

        // 3. Lógica para abrir la foto al hacer clic en la fila
        holder.itemView.setOnClickListener {
            if (gasto.urlFotoRecibo.isNotEmpty()) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(gasto.urlFotoRecibo))
                holder.itemView.context.startActivity(intent)
            }
        }
    }

    override fun getItemCount() = listaDeGastos.size
}