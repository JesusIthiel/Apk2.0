package com.kenworth.gps

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import okhttp3.*
import org.json.JSONObject
import java.io.File
import java.io.IOException

class ModuloActivity : AppCompatActivity() {

    private val httpClient = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_modulo)

        val moduloId   = intent.getIntExtra("modulo_id", 0)
        val moduloNombre = intent.getStringExtra("modulo_nombre") ?: ""

        findViewById<TextView>(R.id.tvTituloModulo).text = moduloNombre
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        val rv          = findViewById<RecyclerView>(R.id.rvPdfs)
        val progress    = findViewById<ProgressBar>(R.id.progressBar)
        val tvVacio     = findViewById<TextView>(R.id.tvVacio)

        rv.layoutManager = LinearLayoutManager(this)

        progress.visibility = View.VISIBLE

        val req = Request.Builder()
            .url("${Config.CONTENIDO_API}?accion=contenido&modulo_id=$moduloId")
            .addHeader("X-API-Key", Config.API_KEY)
            .build()

        httpClient.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    progress.visibility = View.GONE
                    tvVacio.text = "Error de conexión"
                    tvVacio.visibility = View.VISIBLE
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: ""
                response.close()
                runOnUiThread {
                    progress.visibility = View.GONE
                    try {
                        val json  = JSONObject(body)
                        val array = json.getJSONArray("data")
                        val lista = mutableListOf<PdfItem>()
                        for (i in 0 until array.length()) {
                            val obj = array.getJSONObject(i)
                            lista.add(PdfItem(
                                id     = obj.getInt("id"),
                                titulo = obj.getString("titulo"),
                                url    = obj.getString("url")
                            ))
                        }
                        if (lista.isEmpty()) {
                            tvVacio.visibility = View.VISIBLE
                        } else {
                            rv.adapter = PdfsAdapter(lista) { pdf ->
                                    startActivity(Intent(this@ModuloActivity, PdfViewerActivity::class.java).apply {
                                        putExtra("pdf_url",    pdf.url)
                                        putExtra("pdf_titulo", pdf.titulo)
                                    })
                                }
                        }
                    } catch (e: Exception) {
                        tvVacio.text = "Error al cargar documentos"
                        tvVacio.visibility = View.VISIBLE
                    }
                }
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        httpClient.dispatcher.executorService.shutdown()
    }
}
