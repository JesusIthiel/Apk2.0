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
import androidx.drawerlayout.widget.DrawerLayout

class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout:   DrawerLayout
    private lateinit var etTelefono:     EditText
    private lateinit var btnIniciar:     Button
    private lateinit var tvEstado:       TextView
    private lateinit var tvInfo:         TextView
    private lateinit var tvUsuario:      TextView
    private lateinit var tvDrawerNombre: TextView
    private lateinit var prefs:          SharedPreferences

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

        drawerLayout   = findViewById(R.id.drawerLayout)
        etTelefono     = findViewById(R.id.etTelefono)
        btnIniciar     = findViewById(R.id.btnIniciar)
        tvEstado       = findViewById(R.id.tvEstado)
        tvInfo         = findViewById(R.id.tvInfo)
        tvUsuario      = findViewById(R.id.tvUsuario)
        tvDrawerNombre = findViewById(R.id.tvDrawerNombre)

        val nombre = prefs.getString(Config.KEY_NOMBRE, "") ?: ""
        tvUsuario.text      = nombre.ifEmpty { "Empleado" }
        tvDrawerNombre.text = nombre.ifEmpty { "Empleado" }

        etTelefono.setText(prefs.getString(Config.KEY_TELEFONO, ""))

        if (prefs.getBoolean(Config.KEY_ACTIVO, false)) mostrarActivo()
        else mostrarDetenido()

        findViewById<ImageButton>(R.id.btnMenu).setOnClickListener {
            drawerLayout.openDrawer(androidx.core.view.GravityCompat.START)
        }

        btnIniciar.setOnClickListener { verificarYArrancar() }

        findViewById<Button>(R.id.btnCerrarSesion).setOnClickListener {
            cerrarSesion()
        }
    }

    private fun cerrarSesion() {
        stopService(Intent(this, LocationService::class.java))
        prefs.edit {
            putBoolean(Config.KEY_LOGGED_IN, false)
            putBoolean(Config.KEY_ACTIVO, false)
            remove(Config.KEY_TELEFONO)
            remove(Config.KEY_USUARIO)
            remove(Config.KEY_NOMBRE)
        }
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
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
        val intent = Intent(this, LocationService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
        else startService(intent)
        prefs.edit { putBoolean(Config.KEY_ACTIVO, true) }
        mostrarActivo()
    }

    fun onDesvinculado() {
        prefs.edit {
            putBoolean(Config.KEY_ACTIVO, false)
            remove(Config.KEY_TELEFONO)
        }
        mostrarDetenido()
        etTelefono.setText("")
        toast("Dispositivo desvinculado por el administrador")
    }

    private fun mostrarActivo() {
        btnIniciar.visibility = View.GONE
        tvEstado.text = "●  Rastreo activo"
        tvEstado.setTextColor(getColor(R.color.verde))
        tvInfo.text = "Enviando ubicación cada 3 minutos.\nEl rastreo continúa aunque cierres la app."
        etTelefono.isEnabled = false
    }

    private fun mostrarDetenido() {
        btnIniciar.visibility = View.VISIBLE
        tvEstado.text = "○  Servicio detenido"
        tvEstado.setTextColor(getColor(R.color.gris))
        tvInfo.text = "Ingresa el número de línea y presiona Iniciar"
        etTelefono.isEnabled = true
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(androidx.core.view.GravityCompat.START)) {
            drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    private fun tienePermiso(p: String) =
        ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
