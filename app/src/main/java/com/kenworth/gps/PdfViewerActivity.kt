package com.kenworth.gps

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.*
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import okhttp3.*
import java.io.File
import java.io.IOException

class PdfViewerActivity : AppCompatActivity() {

    private val httpClient = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pdf_viewer)

        val pdfUrl = intent.getStringExtra("pdf_url")   ?: ""
        val titulo = intent.getStringExtra("pdf_titulo") ?: "Documento"

        findViewById<TextView>(R.id.tvTituloPdf).text = titulo
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        descargarYMostrar(pdfUrl)
    }

    private fun descargarYMostrar(url: String) {
        val tvCargando  = findViewById<TextView>(R.id.tvCargando)
        val layoutCarg  = findViewById<View>(R.id.layoutCargando)
        val rvPaginas   = findViewById<RecyclerView>(R.id.rvPaginas)
        val tvPaginas   = findViewById<TextView>(R.id.tvPaginas)

        tvCargando.text = "Descargando documento…"

        val req = Request.Builder().url(url).build()
        httpClient.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    tvCargando.text = "Error de conexión. Verifica tu internet."
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    runOnUiThread { tvCargando.text = "Error al obtener el archivo (${response.code})" }
                    response.close()
                    return
                }
                val bytes = response.body?.bytes()
                response.close()

                if (bytes == null || bytes.isEmpty()) {
                    runOnUiThread { tvCargando.text = "El archivo está vacío." }
                    return
                }

                val file = File(cacheDir, "pdf_viewer_tmp.pdf")
                file.writeBytes(bytes)

                runOnUiThread { tvCargando.text = "Procesando páginas…" }

                Thread {
                    val paginas = renderizarPaginas(file)
                    runOnUiThread {
                        if (paginas.isEmpty()) {
                            tvCargando.text = "No se pudo abrir el PDF."
                            return@runOnUiThread
                        }
                        tvPaginas.text = "${paginas.size} pág."
                        rvPaginas.layoutManager = LinearLayoutManager(this@PdfViewerActivity)
                        rvPaginas.adapter = PaginasAdapter(paginas)
                        layoutCarg.visibility = View.GONE
                        rvPaginas.visibility  = View.VISIBLE
                    }
                }.start()
            }
        })
    }

    private fun renderizarPaginas(file: File): List<Bitmap> {
        val paginas = mutableListOf<Bitmap>()
        try {
            val fd       = android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(fd)
            val ancho    = resources.displayMetrics.widthPixels - 64

            for (i in 0 until renderer.pageCount) {
                val page  = renderer.openPage(i)
                val escala = ancho.toFloat() / page.width
                val alto   = (page.height * escala).toInt()
                val bmp    = Bitmap.createBitmap(ancho, alto, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(android.graphics.Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                paginas.add(bmp)
            }
            renderer.close()
            fd.close()
        } catch (e: Exception) {
            android.util.Log.e("KW_PDF", "Error renderizando: ${e.message}")
        }
        return paginas
    }

    override fun onDestroy() {
        super.onDestroy()
        httpClient.dispatcher.executorService.shutdown()
    }
}

class PaginasAdapter(private val paginas: List<Bitmap>) :
    RecyclerView.Adapter<PaginasAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val iv: ImageView = view.findViewById(R.id.ivPagina)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pdf_page, parent, false)
        return VH(v)
    }

    override fun getItemCount() = paginas.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.iv.setImageBitmap(paginas[position])
    }
}
