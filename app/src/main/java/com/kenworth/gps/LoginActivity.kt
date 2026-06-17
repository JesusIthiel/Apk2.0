package com.kenworth.gps

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class LoginActivity : AppCompatActivity() {

    private lateinit var etUsuario:   EditText
    private lateinit var etPassword:  EditText
    private lateinit var btnLogin:    Button
    private lateinit var tvError:     TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var prefs:       SharedPreferences
    private val httpClient = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences(Config.PREFS_NAME, MODE_PRIVATE)

        // Si ya está logueado ir directo al principal
        if (prefs.getBoolean(Config.KEY_LOGGED_IN, false)) {
            irAMain()
            return
        }

        setContentView(R.layout.activity_login)

        etUsuario   = findViewById(R.id.etUsuario)
        etPassword  = findViewById(R.id.etPassword)
        btnLogin    = findViewById(R.id.btnLogin)
        tvError     = findViewById(R.id.tvError)
        progressBar = findViewById(R.id.progressBar)

        btnLogin.setOnClickListener { intentarLogin() }
    }

    private fun intentarLogin() {
        val usuario  = etUsuario.text.toString().trim()
        val password = etPassword.text.toString().trim()

        if (usuario.isEmpty() || password.isEmpty()) {
            mostrarError("Ingresa usuario y contraseña")
            return
        }

        btnLogin.isEnabled       = false
        progressBar.visibility   = View.VISIBLE
        tvError.visibility       = View.GONE

        val json = JSONObject().apply {
            put("usuario",  usuario)
            put("password", password)
        }
        val body = json.toString().toRequestBody("application/json".toMediaType())
        val req  = Request.Builder()
            .url(Config.LOGIN_URL)
            .post(body)
            .build()

        httpClient.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    btnLogin.isEnabled     = true
                    mostrarError("Sin conexión al servidor")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val texto = response.body?.string() ?: ""
                response.close()

                runOnUiThread {
                    progressBar.visibility = View.GONE
                    btnLogin.isEnabled     = true
                    try {
                        val obj = JSONObject(texto)
                        if (obj.getString("status") == "success") {
                            val nombre = obj.optString("nombre", usuario)
                            prefs.edit {
                                putBoolean(Config.KEY_LOGGED_IN, true)
                                putString(Config.KEY_USUARIO,   usuario)
                                putString(Config.KEY_NOMBRE,    nombre)
                            }
                            irAMain()
                        } else {
                            mostrarError("Usuario o contraseña incorrectos")
                        }
                    } catch (_: Exception) {
                        mostrarError("Error de servidor. Intenta de nuevo.")
                    }
                }
            }
        })
    }

    private fun mostrarError(msg: String) {
        tvError.text       = msg
        tvError.visibility = View.VISIBLE
    }

    private fun irAMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        httpClient.dispatcher.executorService.shutdown()
    }
}
