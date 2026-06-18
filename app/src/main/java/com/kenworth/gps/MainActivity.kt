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

class MainActivity : AppCompatActivity() {

    private lateinit var etTelefono: EditText
    private lateinit var btnIniciar: Button
    private lateinit var cardNumero: View
    private lateinit var prefs:      SharedPreferences

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

        etTelefono = findViewById(R.id.etTelefono)
        btnIniciar = findViewById(R.id.btnIniciar)
        cardNumero = findViewById(R.id.cardNumero)

        btnIniciar.setOnClickListener { verificarYArrancar() }

        val numero = prefs.getString(Config.KEY_TELEFONO, "") ?: ""
        if (numero.isNotEmpty()) {
            cardNumero.visibility = View.GONE
            // Siempre arrancar el servicio al abrir la app — Android puede haberlo matado
            prefs.edit { putBoolean(Config.KEY_ACTIVO, true) }
            arrancarServicio()
        } else {
            cardNumero.visibility = View.VISIBLE
        }
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
        cardNumero.visibility = View.GONE
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
}
