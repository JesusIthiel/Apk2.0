package com.kenworth.gps

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.*
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var etTelefono:      EditText
    private lateinit var btnIniciar:      Button
    private lateinit var cardNumero:      View
    private lateinit var tvModulosTitulo: TextView
    private lateinit var rvModulos:       RecyclerView
    private lateinit var progressModulos: ProgressBar
    private lateinit var prefs:           SharedPreferences

    private val httpClient = OkHttpClient()

    private val launUbicacion = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms[Manifest.permission.ACCESS_FINE_LOCATION] == true) pedirBackground()
        else toast("Se necesita permiso de ubicación")
    }

    private val launBackground = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) pedirNotificacion()
        else toast("Elige 'Permitir siempre' para rastreo en segundo plano")
    }

    private val launNotificacion = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { iniciarServicio() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences(Config.PREFS_NAME, MODE_PRIVATE)

        if (!prefs.getBoolean(Config.KEY_LOGGED_IN, false)) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        etTelefono      = findViewById(R.id.etTelefono)
        btnIniciar      = findViewById(R.id.btnIniciar)
        cardNumero      = findViewById(R.id.cardNumero)
        tvModulosTitulo = findViewById(R.id.tvModulosTitulo)
        rvModulos       = findViewById(R.id.rvModulos)
        progressModulos = findViewById(R.id.progressModulos)

        rvModulos.layoutManager = LinearLayoutManager(this)

        btnIniciar.setOnClickListener { verificarYArrancar() }

        val numero = prefs.getString(Config.KEY_TELEFONO, "") ?: ""
        if (numero.isNotEmpty()) {
            mostrarModulos()
            prefs.edit { putBoolean(Config.KEY_ACTIVO, true) }
            arrancarServicio()
        } else {
            cardNumero.visibility = View.VISIBLE
        }
    }

    override fun onResume() {
        super.onResume()
        val numero = prefs.getString(Config.KEY_TELEFONO, "") ?: ""
        if (numero.isNotEmpty()) cargarModulos()
    }

    private fun mostrarModulos() {
        cardNumero.visibility      = View.GONE
        tvModulosTitulo.visibility = View.VISIBLE
        rvModulos.visibility       = View.VISIBLE
        cargarModulos()
    }

    private fun cargarModulos() {
        progressModulos.visibility = View.VISIBLE

        val req = Request.Builder()
            .url("${Config.CONTENIDO_API}?accion=modulos")
            .addHeader("X-API-Key", Config.API_KEY)
            .build()

        httpClient.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { progressModulos.visibility = View.GONE }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: ""
                response.close()
                runOnUiThread {
                    progressModulos.visibility = View.GONE
                    try {
                        val json  = JSONObject(body)
                        val array = json.optJSONArray("data")
                        val lista = mutableListOf<Modulo>()
                        if (array != null) {
                            for (i in 0 until array.length()) {
                                val obj = array.getJSONObject(i)
                                lista.add(Modulo(
                                    id        = obj.getInt("id"),
                                    nombre    = obj.getString("nombre"),
                                    totalPdfs = obj.optInt("total_pdfs", 0)
                                ))
                            }
                        }
                        if (lista.isEmpty()) {
                            tvModulosTitulo.text = "Sin módulos disponibles"
                        } else {
                            tvModulosTitulo.text = "MÓDULOS"
                            rvModulos.adapter = ModulosAdapter(lista) { modulo ->
                                val intent = Intent(this@MainActivity, ModuloActivity::class.java).apply {
                                    putExtra("modulo_id",     modulo.id)
                                    putExtra("modulo_nombre", modulo.nombre)
                                }
                                startActivity(intent)
                            }
                        }
                    } catch (e: Exception) {
                        tvModulosTitulo.text = "Error al cargar módulos"
                        android.util.Log.e("KW_MOD", "body=$body err=${e.message}")
                    }
                }
            }
        })
    }

    private fun verificarYArrancar() {
        val numero = etTelefono.text.toString().trim()
        if (numero.isEmpty()) { toast("Ingresa el número de línea"); return }
        prefs.edit { putString(Config.KEY_TELEFONO, numero) }

        if (!tienePermiso(Manifest.permission.ACCESS_FINE_LOCATION)) {
            launUbicacion.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        } else pedirBackground()
    }

    private fun pedirBackground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            !tienePermiso(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        ) {
            toast("En la siguiente pantalla elige 'Permitir siempre'")
            launBackground.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else pedirNotificacion()
    }

    private fun pedirNotificacion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !tienePermiso(Manifest.permission.POST_NOTIFICATIONS)
        ) {
            launNotificacion.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else iniciarServicio()
    }

    private fun iniciarServicio() {
        prefs.edit { putBoolean(Config.KEY_ACTIVO, true) }
        arrancarServicio()
        mostrarModulos()
    }

    private fun arrancarServicio() {
        val intent = Intent(this, LocationService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
        else startService(intent)
    }

    private fun tienePermiso(p: String) =
        ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    override fun onDestroy() {
        super.onDestroy()
        httpClient.dispatcher.executorService.shutdown()
    }
}
