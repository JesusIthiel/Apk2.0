package com.kenworth.gps

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.*
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class LocationService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val httpClient = OkHttpClient()

    // Info del dispositivo (se lee una vez al iniciar)
    private val androidId by lazy {
        Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: ""
    }
    private val modeloDispositivo  = Build.MODEL          ?: ""
    private val marcaDispositivo   = Build.MANUFACTURER   ?: ""
    private val versionOS          = Build.VERSION.RELEASE ?: ""

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        crearCanalNotificacion()
        iniciarComoForeground()
        iniciarRastreo()
        return START_STICKY
    }

    private fun crearCanalNotificacion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(
                Config.CHANNEL_ID,
                "Rastreo GPS Kenworth",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Servicio de ubicación activo"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
        }
    }

    private fun buildNotif(texto: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, Config.CHANNEL_ID)
            .setContentTitle("Kenworth GPS")
            .setContentText(texto)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun iniciarComoForeground() {
        val notif = buildNotif("Rastreo activo — enviando cada 1 min")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(Config.NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(Config.NOTIF_ID, notif)
        }
    }

    private fun actualizarNotif(texto: String) {
        getSystemService(NotificationManager::class.java)
            .notify(Config.NOTIF_ID, buildNotif(texto))
    }

    private fun iniciarRastreo() {
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            Config.INTERVALO_MS
        )
            .setMinUpdateIntervalMillis(Config.INTERVALO_MS / 2)
            .setMaxUpdateDelayMillis(Config.INTERVALO_MS + 30_000L)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { enviarUbicacion(it) }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                request,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            Log.e("KW_GPS", "Sin permiso: ${e.message}")
            stopSelf()
        }
    }

    private fun enviarUbicacion(location: Location) {
        val prefs = getSharedPreferences(Config.PREFS_NAME, MODE_PRIVATE)
        val idTel = prefs.getString(Config.KEY_TELEFONO, "") ?: ""
        if (idTel.isEmpty()) return

        val json = JSONObject().apply {
            put("id_telefono",     idTel)
            put("android_id",      androidId)
            put("lat",             location.latitude)
            put("lng",             location.longitude)
            put("precision",       location.accuracy.toInt())
            put("modelo",          modeloDispositivo)
            put("marca",           marcaDispositivo)
            put("version_android", versionOS)
        }

        val body = json.toString().toRequestBody("application/json".toMediaType())
        val req  = Request.Builder()
            .url(Config.API_URL)
            .addHeader("X-API-Key", Config.API_KEY)
            .post(body)
            .build()

        httpClient.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("KW_GPS", "Error al enviar: ${e.message}")
                actualizarNotif("⚠ Sin conexión — reintentando en 3 min")
            }

            override fun onResponse(call: Call, response: Response) {
                val texto = response.body?.string() ?: ""
                response.close()

                try {
                    val obj    = JSONObject(texto)
                    val status = obj.optString("status", "")

                    when (status) {
                        "eliminated" -> {
                            // El admin eliminó este dispositivo — limpiar número pero mantener sesión
                            getSharedPreferences(Config.PREFS_NAME, MODE_PRIVATE).edit().apply {
                                putBoolean(Config.KEY_ACTIVO, false)
                                remove(Config.KEY_TELEFONO)
                                apply()
                            }
                            actualizarNotif("Dispositivo eliminado — ingresa un nuevo número")
                            stopSelf()
                        }
                        "unlinked" -> {
                            getSharedPreferences(Config.PREFS_NAME, MODE_PRIVATE).edit().apply {
                                putBoolean(Config.KEY_ACTIVO, false)
                                remove(Config.KEY_TELEFONO)
                                apply()
                            }
                            actualizarNotif("Dispositivo desvinculado por administrador")
                            stopSelf()
                        }
                        "error" -> {
                            val msg = obj.optString("message", "")
                            if (msg == "duplicate") {
                                actualizarNotif("⚠ Número ya activo en otro dispositivo")
                            } else {
                                Log.e("KW_GPS", "Error servidor: $msg")
                            }
                        }
                        "success" -> {
                            val hora = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                            actualizarNotif("✓ Enviado $hora · ${String.format("%.4f", location.latitude)}, ${String.format("%.4f", location.longitude)}")
                            Log.d("KW_GPS", "OK [${location.latitude}, ${location.longitude}]")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("KW_GPS", "Parse error: ${e.message}")
                }
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::locationCallback.isInitialized)
            fusedLocationClient.removeLocationUpdates(locationCallback)
        httpClient.dispatcher.executorService.shutdown()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
