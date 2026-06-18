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

class LocationService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val httpClient = OkHttpClient()
    private val handler = Handler(Looper.getMainLooper())

    // Mejor lectura precisa (≤100m) y cualquier lectura de respaldo
    @Volatile private var mejorLectura:     Location? = null
    @Volatile private var cualquierLectura: Location? = null

    private val androidId by lazy {
        Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: ""
    }
    private val modeloDispositivo = Build.MODEL          ?: ""
    private val marcaDispositivo  = Build.MANUFACTURER   ?: ""
    private val versionOS         = Build.VERSION.RELEASE ?: ""

    // Runnable que cada 60s toma la mejor lectura acumulada y la envía
    private val enviarRunnable = object : Runnable {
        override fun run() {
            val loc = mejorLectura ?: cualquierLectura
            mejorLectura    = null
            cualquierLectura = null
            if (loc != null) enviarUbicacion(loc)
            handler.postDelayed(this, Config.INTERVALO_MS)
        }
    }

    // Runnable que cada 10s verifica si el dispositivo fue eliminado o desvinculado
    private val pingRunnable = object : Runnable {
        override fun run() {
            verificarEstado()
            handler.postDelayed(this, Config.PING_INTERVALO_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        crearCanalNotificacion()
        iniciarComoForeground()
        iniciarRastreo()
        handler.postDelayed(enviarRunnable, Config.INTERVALO_MS)
        handler.postDelayed(pingRunnable,   Config.PING_INTERVALO_MS)
        return START_STICKY
    }

    private fun crearCanalNotificacion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(
                Config.CHANNEL_ID,
                "Kenworth del Este",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
        }
    }

    private fun buildNotif(texto: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, Config.CHANNEL_ID)
            .setContentTitle("Kenworth del Este")
            .setContentText(texto)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pi)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    private fun iniciarComoForeground() {
        val notif = buildNotif("●")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(Config.NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(Config.NOTIF_ID, notif)
        }
    }

    private fun iniciarRastreo() {
        // Pedir fix cada 15 segundos para acumular buenas lecturas
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            15_000L
        )
            .setMinUpdateIntervalMillis(10_000L)
            .setMaxUpdateDelayMillis(20_000L)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                for (loc in result.locations) {
                    // Siempre guardar la lectura más reciente como respaldo
                    val respaldo = cualquierLectura
                    if (respaldo == null || loc.accuracy < respaldo.accuracy) {
                        cualquierLectura = loc
                    }
                    // Guardar la más precisa que cumpla ≤100m
                    if (loc.accuracy <= 100f) {
                        val actual = mejorLectura
                        if (actual == null || loc.accuracy < actual.accuracy) {
                            mejorLectura = loc
                            Log.d("KW_GPS", "Fix preciso: ${loc.accuracy.toInt()}m")
                        }
                    }
                }
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
            }

            override fun onResponse(call: Call, response: Response) {
                val texto = response.body?.string() ?: ""
                response.close()
                try {
                    val obj    = JSONObject(texto)
                    val status = obj.optString("status", "")
                    when (status) {
                        "eliminated", "unlinked" -> cerrarSesion()
                        "error" -> Log.e("KW_GPS", "Error servidor: ${obj.optString("message", "")}")
                        "success" -> Log.d("KW_GPS", "OK [${location.latitude}, ${location.longitude}] acc:${location.accuracy.toInt()}m")
                    }
                } catch (e: Exception) {
                    Log.e("KW_GPS", "Parse error: ${e.message}")
                }
            }
        })
    }

    private fun cerrarSesion() {
        getSharedPreferences(Config.PREFS_NAME, MODE_PRIVATE).edit().clear().apply()
        startActivity(Intent(this, LoginActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        })
        stopSelf()
    }

    private fun verificarEstado() {
        val prefs  = getSharedPreferences(Config.PREFS_NAME, MODE_PRIVATE)
        val idTel  = prefs.getString(Config.KEY_TELEFONO, "") ?: ""
        if (idTel.isEmpty()) return

        val json = JSONObject().apply {
            put("android_id",  androidId)
            put("id_telefono", idTel)
        }
        val body = json.toString().toRequestBody("application/json".toMediaType())
        val req  = Request.Builder()
            .url(Config.PING_URL)
            .addHeader("X-API-Key", Config.API_KEY)
            .post(body)
            .build()

        httpClient.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                val texto = response.body?.string() ?: ""
                response.close()
                try {
                    val status = JSONObject(texto).optString("status", "")
                    if (status == "eliminated" || status == "unlinked") cerrarSesion()
                } catch (_: Exception) {}
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(enviarRunnable)
        handler.removeCallbacks(pingRunnable)
        if (::locationCallback.isInitialized)
            fusedLocationClient.removeLocationUpdates(locationCallback)
        httpClient.dispatcher.executorService.shutdown()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
