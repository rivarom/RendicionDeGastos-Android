package com.invap.rendiciondegastos

import com.invap.rendiciondegastos.Gasto
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

// 1. Añadimos el nuevo parámetro para el clic largo
class GastosAdapter(
    private val listaDeGastos: List<Gasto>,
    private val onItemLongClicked: (Gasto) -> Unit
) : RecyclerView.Adapter<GastosAdapter.GastoViewHolder>() {

    inner class GastoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val descripcion: TextView = itemView.findViewById(R.id.textViewDescripcionGasto)
        val fecha: TextView = itemView.findViewById(R.id.textViewFechaGasto)
        val monto: TextView = itemView.findViewById(R.id.textViewMontoGasto)
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

        if (gasto.urlFotoRecibo.isNotEmpty()) {
            holder.iconoFoto.visibility = View.VISIBLE
        } else {
            holder.iconoFoto.visibility = View.GONE
        }

        // Clic normal para ver la foto
        holder.itemView.setOnClickListener {
            if (gasto.urlFotoRecibo.isNotEmpty()) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(gasto.urlFotoRecibo))
                holder.itemView.context.startActivity(intent)
            }
        }

        // 2. Configuración del clic largo para eliminar
        holder.itemView.setOnLongClickListener {
            onItemLongClicked(gasto)
            true
        }
    }

    override fun getItemCount() = listaDeGastos.size
}