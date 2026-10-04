package com.jarvis.assistant.commands

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Free weather lookup via Open-Meteo (no API key required).
 * Pass in device coordinates (from FusedLocationProvider / LocationManager in MainActivity).
 */
object WeatherService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun getCurrentWeather(lat: Double, lon: Double): String = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,weather_code,wind_speed_10m&timezone=auto"
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext "Hava durumu alınamadı."
                val json = JSONObject(response.body?.string().orEmpty())
                val current = json.getJSONObject("current")
                val temp = current.getDouble("temperature_2m")
                val code = current.getInt("weather_code")
                val wind = current.getDouble("wind_speed_10m")
                "Şu anda hava ${temp.toInt()} derece, ${describeWeatherCode(code)}. Rüzgar hızı saatte ${wind.toInt()} km."
            }
        } catch (e: Exception) {
            "Hava durumu servisine ulaşılamadı: ${e.message}"
        }
    }

    // WMO weather codes -> short Turkish description
    private fun describeWeatherCode(code: Int): String = when (code) {
        0 -> "açık"
        1, 2 -> "parçalı bulutlu"
        3 -> "kapalı"
        45, 48 -> "sisli"
        51, 53, 55 -> "çiseleyen yağmurlu"
        61, 63, 65 -> "yağmurlu"
        71, 73, 75 -> "karlı"
        80, 81, 82 -> "sağanak yağışlı"
        95, 96, 99 -> "fırtınalı"
        else -> "değişken"
    }
}
