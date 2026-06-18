package com.kenworth.gps

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.*
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import okhttp3.*
import java.io.File
import java.io.IOException

class PdfViewerActivity : AppCompatActivity() {

    private val httpClient = OkHttpClient()
    private var pdfUrl   = ""
    private var titulo   = ""

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pdf_viewer)

        pdfUrl = intent.getStringExtra("pdf_url")   ?: ""
        titulo = intent.getStringExtra("pdf_titulo") ?: "Documento"

        findViewById<TextView>(R.id.tvTituloPdf).text = titulo
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnDescargar).setOnClickListener { descargarPdf() }

        val webView  = findViewById<WebView>(R.id.webView)
        val progress = findViewById<ProgressBar>(R.id.progressWeb)

        webView.settings.apply {
            javaScriptEnabled  = true
            domStorageEnabled  = true
            loadWithOverviewMode = true
            useWideViewPort    = true
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                progress.visibility = View.GONE
            }
            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                progress.visibility = View.GONE
            }
        }

        val encoded = Uri.encode(pdfUrl)
        webView.loadUrl("https://docs.google.com/gviewer?embedded=true&url=$encoded")
    }

    private fun descargarPdf() {
        Toast.makeText(this, "Descargando…", Toast.LENGTH_SHORT).show()
        val req = Request.Builder().url(pdfUrl).build()
        httpClient.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { Toast.makeText(this@PdfViewerActivity, "Error al descargar", Toast.LENGTH_LONG).show() }
            }
            override fun onResponse(call: Call, response: Response) {
                val bytes = response.body?.bytes()
                response.close()
                if (bytes == null) return
                val file = File(cacheDir, "doc_${System.currentTimeMillis()}.pdf")
                file.writeBytes(bytes)
                runOnUiThread {
                    try {
                        val uri = FileProvider.getUriForFile(this@PdfViewerActivity, "$packageName.fileprovider", file)
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "application/pdf")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivity(Intent.createChooser(intent, "Abrir con…"))
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(this@PdfViewerActivity, "Instala un visor de PDF", Toast.LENGTH_LONG).show()
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
