package com.kenworth.gps

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

// Se ejecuta al encender el teléfono — reinicia el servicio si estaba activo
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val prefs       = context.getSharedPreferences(Config.PREFS_NAME, Context.MODE_PRIVATE)
        val estabaActivo = prefs.getBoolean(Config.KEY_ACTIVO, false)
        val telefono     = prefs.getString(Config.KEY_TELEFONO, "")

        if (estabaActivo && !telefono.isNullOrEmpty()) {
            val si = Intent(context, LocationService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                context.startForegroundService(si)
            else
                context.startService(si)
        }
    }
}
