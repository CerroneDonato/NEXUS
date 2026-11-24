package it.cerrone.dailyhub

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/** Open-Meteo, called by the phone itself. The shape matches what the dashboard already parses. */
object WeatherClient {
    private val http = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()

    private val codes = mapOf(
        0 to ("Sereno" to "clear"),
        1 to ("Prevalentemente sereno" to "clear"),
        2 to ("Parzialmente nuvoloso" to "partly-cloudy"),
        3 to ("Coperto" to "cloudy"),
        45 to ("Nebbia" to "fog"),
        48 to ("Nebbia con brina" to "fog"),
        51 to ("Pioviggine leggera" to "drizzle"),
        53 to ("Pioviggine" to "drizzle"),
        55 to ("Pioviggine intensa" to "drizzle"),
        61 to ("Pioggia leggera" to "rain"),
        63 to ("Pioggia" to "rain"),
        65 to ("Pioggia forte" to "rain"),
        71 to ("Neve leggera" to "snow"),
        73 to ("Neve" to "snow"),
        75 to ("Neve forte" to "snow"),
        80 to ("Rovesci leggeri" to "rain"),
        81 to ("Rovesci" to "rain"),
        82 to ("Rovesci violenti" to "rain"),
        85 to ("Rovesci di neve" to "snow"),
        95 to ("Temporale" to "storm"),
        96 to ("Temporale con grandine" to "storm"),
        99 to ("Temporale con grandine forte" to "storm"),
    )

    private val english = mapOf(
        0 to "Clear", 1 to "Mostly clear", 2 to "Partly cloudy", 3 to "Overcast", 45 to "Fog", 48 to "Freezing fog",
        51 to "Light drizzle", 53 to "Drizzle", 55 to "Heavy drizzle", 61 to "Light rain", 63 to "Rain", 65 to "Heavy rain",
        71 to "Light snow", 73 to "Snow", 75 to "Heavy snow", 80 to "Light showers", 81 to "Showers", 82 to "Violent showers",
        85 to "Snow showers", 95 to "Thunderstorm", 96 to "Thunderstorm with hail", 99 to "Thunderstorm with heavy hail",
    )

    fun describe(code: Int?): Pair<String, String> {
        val known = code?.let { codes[it] } ?: return tr("N/D", "N/A") to "unknown"
        return (if (Lang.isEnglish) english[code ?: -1] ?: known.first else known.first) to known.second
    }

    suspend fun fetch(prefs: Prefs): JSONObject = withContext(Dispatchers.IO) { load(prefs) }

    data class Place(val name: String, val region: String, val latitude: Double, val longitude: Double)

    /** City name to coordinates with Open-Meteo's free geocoding. Null when nothing matches. */
    suspend fun geocode(name: String): Place? = withContext(Dispatchers.IO) {
        val url = "https://geocoding-api.open-meteo.com/v1/search".toHttpUrl().newBuilder()
            .addQueryParameter("name", name)
            .addQueryParameter("count", "1")
            .addQueryParameter("language", if (Lang.isEnglish) "en" else "it")
            .build()
        http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw HubException(tr("Ricerca della città non disponibile", "City search not available"))
            val hit = JSONObject(response.body?.string().orEmpty()).optJSONArray("results")?.optJSONObject(0)
            hit?.let { Place(it.getString("name"), it.optString("admin1"), it.getDouble("latitude"), it.getDouble("longitude")) }
        }
    }

    fun load(prefs: Prefs): JSONObject {
        val url = "https://api.open-meteo.com/v1/forecast".toHttpUrl().newBuilder()
            .addQueryParameter("latitude", prefs.latitude)
            .addQueryParameter("longitude", prefs.longitude)
            .addQueryParameter("timezone", ZoneId.systemDefault().id)
            .addQueryParameter("forecast_days", "7")
            .addQueryParameter("current", "temperature_2m,apparent_temperature,weather_code,wind_speed_10m,is_day")
            .addQueryParameter("hourly", "temperature_2m,precipitation_probability,weather_code")
            .addQueryParameter("daily", "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset")
            .build()
        return http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw HubException(tr("Meteo non disponibile", "Weather not available"))
            parse(JSONObject(response.body?.string().orEmpty()), prefs.city)
        }
    }

    fun parse(payload: JSONObject, city: String): JSONObject {
        val current = payload.optJSONObject("current") ?: JSONObject()
        val daily = payload.optJSONObject("daily") ?: JSONObject()
        val hourly = payload.optJSONObject("hourly") ?: JSONObject()
        val currentCode = if (current.isNull("weather_code")) null else current.optInt("weather_code")
        val (currentDesc, currentIcon) = describe(currentCode)
        val dailyCodes = daily.optJSONArray("weather_code")
        val firstCode = dailyCodes?.let { if (it.length() > 0 && !it.isNull(0)) it.optInt(0) else null }
        val (dailyDesc, dailyIcon) = describe(firstCode)
        val times = hourly.optJSONArray("time") ?: org.json.JSONArray()
        val nowHour = current.optString("time").take(13)
        val hours = org.json.JSONArray()
        for (index in 0 until times.length()) {
            val time = times.optString(index)
            if (nowHour.isNotEmpty() && time.take(13) < nowHour) continue
            val code = hourly.optJSONArray("weather_code")?.optInt(index)
            val (desc, icon) = describe(code)
            if (hours.length() >= 12) break
            hours.put(
                JSONObject()
                    .put("time", time)
                    .put("temperature", hourly.optJSONArray("temperature_2m")?.opt(index) ?: JSONObject.NULL)
                    .put("precipitationProbability", hourly.optJSONArray("precipitation_probability")?.opt(index) ?: JSONObject.NULL)
                    .put("description", desc)
                    .put("icon", icon)
            )
        }
        val days = org.json.JSONArray()
        val dayTimes = daily.optJSONArray("time") ?: org.json.JSONArray()
        for (index in 0 until dayTimes.length()) {
            val code = dailyCodes?.let { if (index < it.length() && !it.isNull(index)) it.optInt(index) else null }
            val (desc, icon) = describe(code)
            days.put(
                JSONObject()
                    .put("date", dayTimes.optString(index))
                    .put("min", numberAt(daily, "temperature_2m_min", index))
                    .put("max", numberAt(daily, "temperature_2m_max", index))
                    .put("precipitationProbability", numberAt(daily, "precipitation_probability_max", index))
                    .put("description", desc)
                    .put("icon", icon)
            )
        }
        val today = days.optJSONObject(0) ?: JSONObject()
        return JSONObject()
            .put("city", city)
            .put(
                "current",
                JSONObject()
                    .put("time", current.optString("time"))
                    .put("temperature", if (current.isNull("temperature_2m")) JSONObject.NULL else current.get("temperature_2m"))
                    .put("isDay", current.optInt("is_day", 1) == 1)
                    .put("description", currentDesc)
                    .put("icon", currentIcon)
            )
            .put(
                "today",
                JSONObject()
                    .put("min", today.opt("min") ?: JSONObject.NULL)
                    .put("max", today.opt("max") ?: JSONObject.NULL)
                    .put("precipitationProbability", today.opt("precipitationProbability") ?: JSONObject.NULL)
                    .put("description", dailyDesc)
                    .put("icon", dailyIcon)
            )
            .put("hourly", hours)
            .put("daily", days)
    }

    private fun numberAt(daily: JSONObject, key: String, index: Int): Any {
        val values = daily.optJSONArray(key) ?: return JSONObject.NULL
        if (index >= values.length() || values.isNull(index)) return JSONObject.NULL
        return values.get(index)
    }
}
