package com.jarvis.assistant.commands

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Generic smart-home automation layer.
 *
 * You don't have a specific platform picked yet, so this is built as a thin,
 * pluggable interface: each "device" is just a name mapped to a webhook URL.
 * This works out of the box with almost anything that exposes HTTP endpoints:
 *   - Home Assistant (via its REST API + long-lived access token)
 *   - Tuya/Smart Life (via IFTTT webhooks or a local Tuya-to-HTTP bridge)
 *   - Google Home (via IFTTT "Google Assistant" -> Webhooks applets)
 *   - Any DIY ESP32/Shelly/Tasmota device with an HTTP toggle endpoint
 *
 * To wire up a real device later: add an entry to `deviceRegistry` with the
 * device's name and the URL to call for "on" and "off", or replace this whole
 * class with a Home-Assistant-specific client if you settle on that platform.
 */
object SmartHomeController {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    data class Device(val name: String, val onUrl: String?, val offUrl: String?)

    // Empty for now — add devices here once a platform/hardware is chosen, e.g.:
    // Device("salon lambası", "http://homeassistant.local:8123/api/webhook/salon_on", ".../salon_off")
    private val deviceRegistry: MutableList<Device> = mutableListOf()

    fun registerDevice(device: Device) {
        deviceRegistry.removeAll { it.name.equals(device.name, ignoreCase = true) }
        deviceRegistry.add(device)
    }

    fun listDevices(): List<String> = deviceRegistry.map { it.name }

    suspend fun turnOn(deviceName: String): String = trigger(deviceName, turnOn = true)
    suspend fun turnOff(deviceName: String): String = trigger(deviceName, turnOn = false)

    private suspend fun trigger(deviceName: String, turnOn: Boolean): String = withContext(Dispatchers.IO) {
        val device = deviceRegistry.firstOrNull { it.name.equals(deviceName, ignoreCase = true) }
            ?: return@withContext "\"$deviceName\" adında kayıtlı bir cihaz bulamadım. " +
                "Henüz bir ev otomasyonu sistemi bağlanmadı — Home Assistant / Tuya / Google Home " +
                "seçince cihazları SmartHomeController'a ekleyebiliriz."

        val url = if (turnOn) device.onUrl else device.offUrl
        if (url.isNullOrBlank()) return@withContext "${device.name} için ${if (turnOn) "açma" else "kapama"} adresi tanımlı değil."

        try {
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    "${device.name} ${if (turnOn) "açıldı" else "kapatıldı"}."
                } else {
                    "${device.name} için komut gönderilemedi (${response.code})."
                }
            }
        } catch (e: Exception) {
            "Cihaza ulaşılamadı: ${e.message}"
        }
    }
}
