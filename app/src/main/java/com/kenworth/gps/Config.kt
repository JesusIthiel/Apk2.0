package com.kenworth.gps

object Config {
    const val API_URL   = "https://kweste.com.mx/Telefonia/php/ubicaciones_api.php?accion=registrar"
    const val PING_URL  = "https://kweste.com.mx/Telefonia/php/ubicaciones_api.php?accion=ping"
    const val LOGIN_URL = "https://kweste.com.mx/Telefonia/php/ubicaciones_api.php?accion=login_conductor"
    const val API_KEY   = "kw-gps-5e47b359a5d8d39e7df630b0"

    const val INTERVALO_MS      = 1 * 60 * 1000L
    const val PING_INTERVALO_MS = 10 * 1000L   // verificar estado cada 10 seg
    const val CHANNEL_ID    = "kw_gps_channel"
    const val NOTIF_ID      = 1001
    const val PREFS_NAME    = "kw_gps_prefs"
    const val KEY_TELEFONO  = "id_telefono"
    const val KEY_ACTIVO    = "servicio_activo"
    const val KEY_LOGGED_IN = "logged_in"
    const val KEY_USUARIO   = "usuario"
    const val KEY_NOMBRE    = "nombre"
}
