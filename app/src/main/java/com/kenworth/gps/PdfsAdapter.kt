package com.kenworth.gps

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class PdfItem(val id: Int, val titulo: String, val url: String)

class PdfsAdapter(
    private val items: List<PdfItem>,
    private val onClick: (PdfItem) -> Unit
) : RecyclerView.Adapter<PdfsAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitulo: TextView = view.findViewById(R.id.tvTituloPdf)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_pdf, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val p = items[position]
        holder.tvTitulo.text = p.titulo
        holder.itemView.setOnClickListener { onClick(p) }
    }
}
