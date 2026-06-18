package com.kenworth.gps

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class Modulo(val id: Int, val nombre: String, val totalPdfs: Int)

class ModulosAdapter(
    private val items: List<Modulo>,
    private val onClick: (Modulo) -> Unit
) : RecyclerView.Adapter<ModulosAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvNombre: TextView = view.findViewById(R.id.tvNombreModulo)
        val tvTotal:  TextView = view.findViewById(R.id.tvTotalPdfs)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_modulo, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val m = items[position]
        holder.tvNombre.text = m.nombre
        holder.tvTotal.text  = "${m.totalPdfs} documento${if (m.totalPdfs != 1) "s" else ""}"
        holder.itemView.setOnClickListener { onClick(m) }
    }
}
