package ru.weathericon.app

import android.content.Context
import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.pm.ShortcutManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.util.concurrent.TimeUnit

data class City(val name: String, val admin: String, val country: String, val lat: Double, val lon: Double) {
    val title get() = listOf(name, admin, country).filter { it.isNotBlank() }.distinct().joinToString(", ")
}
data class Weather(val temp: Int, val precipType: Int, val observed: String, val emoji: String = "☀️", val description: String = "")

class MainActivity : ComponentActivity() {
    private val prefs by lazy { getSharedPreferences("weather", MODE_PRIVATE) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scheduleBackgroundRefresh(this)
        getSystemService(ShortcutManager::class.java)?.removeDynamicShortcuts(listOf("current-weather"))
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF2563EB), background = Color(0xFFF8FAFC))) {
                var city by remember { mutableStateOf(readCity()) }
                var weather by remember { mutableStateOf(readWeather()) }
                var query by remember { mutableStateOf("") }
                var results by remember { mutableStateOf<List<City>>(emptyList()) }
                var loading by remember { mutableStateOf(false) }
                var error by remember { mutableStateOf("") }
                var choosing by remember { mutableStateOf(city == null) }
                val scope = rememberCoroutineScope()
                fun refresh(selected: City) {
                    scope.launch {
                        loading = true
                        try {
                            val fresh = withContext(Dispatchers.IO) { WeatherApi.current(selected) }
                            weather = fresh; saveWeather(fresh); updateLauncherIcon(this@MainActivity, fresh)
                            error = ""
                        } catch (e: Exception) { Log.e("WeatherIcon", "Weather refresh failed", e); error = e.message ?: "Не удалось получить погоду. Проверьте сеть." }
                        loading = false
                    }
                }
                LaunchedEffect(city?.title) {
                    val selected = city ?: return@LaunchedEffect
                    weather?.let { updateLauncherIcon(this@MainActivity, it) }
                    refresh(selected)
                    while (true) { delay(TimeUnit.MINUTES.toMillis(10)); refresh(selected) }
                }
                Surface(Modifier.fillMaxSize(), color = Color(0xFFF8FAFC)) {
                    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 28.dp)) {
                        Text(if (choosing) "Выберите город" else "Погода", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
                        Spacer(Modifier.height(8.dp))
                        Text("Текущая погода и температура на ярлыке приложения", color = Color(0xFF64748B))
                        Spacer(Modifier.height(24.dp))
                        if (!choosing && city != null) {
                            Text(city!!.title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(14.dp))
                            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                Column(Modifier.fillMaxWidth().padding(24.dp)) {
                                    Text(weather?.let { "${if (it.temp > 0) "+" else ""}${it.temp}°" } ?: "—°", fontSize = 58.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D4ED8))
                                    Text(weather?.let { "${it.emoji} ${it.description}" } ?: if (loading) "Загружаем погоду…" else "Погода не загружена", fontSize = 18.sp)
                                    Spacer(Modifier.height(8.dp))
                                    Text(weather?.let { "Обновлено: ${it.observed.replace('T', ' ')}" } ?: "", color = Color(0xFF64748B), fontSize = 13.sp)
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                            if (error.isNotEmpty()) Text(error, color = Color(0xFFB45309), modifier = Modifier.padding(top = 10.dp))
                            TextButton(onClick = { refresh(city!!) }) { Text("Обновить сейчас") }
                            TextButton(onClick = { choosing = true; query = ""; results = emptyList() }) { Text("Сменить город") }
                            Text("Обновление в фоне зависит от ограничений Android и может происходить реже 10 минут.", color = Color(0xFF64748B), fontSize = 12.sp, modifier = Modifier.padding(top = 16.dp))
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                BasicTextField(value = query, onValueChange = { query = it }, singleLine = true,
                                    modifier = Modifier.weight(1f).padding(14.dp), decorationBox = { inner ->
                                        if (query.isEmpty()) Text("Например, Казань", color = Color.Gray)
                                        inner()
                                    })
                                Button(onClick = {
                                    if (query.trim().length >= 2) scope.launch {
                                        loading = true; error = ""
                                        try { results = withContext(Dispatchers.IO) { WeatherApi.search(query.trim()) }; if (results.isEmpty()) error = "Город не найден" }
                        catch (e: Exception) { Log.e("WeatherIcon", "City search failed", e); error = "Не удалось выполнить поиск. Проверьте интернет и попробуйте снова." }
                                        loading = false
                                    }
                                }) { Text("Найти") }
                            }
                            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                            if (error.isNotEmpty()) Text(error, color = Color(0xFFB45309), modifier = Modifier.padding(vertical = 12.dp))
                            LazyColumn(Modifier.padding(top = 8.dp)) {
                                items(results) { item ->
                                    Text(item.title, Modifier.fillMaxWidth().clickable {
                                        city = item; prefs.edit().putString("name", item.name).putString("admin", item.admin).putString("country", item.country).putString("lat", item.lat.toString()).putString("lon", item.lon.toString()).apply()
                                        choosing = false; error = ""
                                    }.padding(vertical = 16.dp), fontSize = 17.sp)
                                    HorizontalDivider(color = Color(0xFFE2E8F0))
                                }
                            }
                        }
                        Spacer(Modifier.weight(1f, fill = false))
                        Text("Погода: WeatherAPI.com · поиск города: Open-Meteo", color = Color(0xFF64748B), fontSize = 12.sp, modifier = Modifier.padding(top = 16.dp))
                    }
                }
            }
        }
    }
    private fun readCity(): City? = try { City(prefs.getString("name", "")!!, prefs.getString("admin", "")!!, prefs.getString("country", "")!!, prefs.getString("lat", "")!!.toDouble(), prefs.getString("lon", "")!!.toDouble()) } catch (_: Exception) { null }
    private fun readWeather(): Weather? = try { Weather(prefs.getInt("temp", Int.MIN_VALUE), prefs.getInt("code", -1), prefs.getString("observed", "")!!, prefs.getString("emoji", "☀️")!!, prefs.getString("description", "")!!).takeIf { it.temp != Int.MIN_VALUE } } catch (_: Exception) { null }
    private fun saveWeather(w: Weather) { prefs.edit().putInt("temp", w.temp).putInt("code", w.precipType).putString("observed", w.observed).putString("emoji", w.emoji).putString("description", w.description).apply() }
}

object WeatherApi {
    private val API_KEY get() = BuildConfig.WEATHER_API_KEY
    fun search(q: String): List<City> {
        val encoded = URLEncoder.encode(q, "UTF-8")
        val json = get("https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=12&language=ru&format=json")
        val arr = JSONObject(json).optJSONArray("results") ?: return emptyList()
        return (0 until arr.length()).map { i -> arr.getJSONObject(i).let { City(it.optString("name"), it.optString("admin1"), it.optString("country"), it.getDouble("latitude"), it.getDouble("longitude")) } }
    }
    fun current(city: City): Weather {
        val url = "https://api.weatherapi.com/v1/current.json?key=$API_KEY&q=${city.lat},${city.lon}&aqi=no&lang=ru"
        val json = JSONObject(get(url))
        val current = json.getJSONObject("current")
        val condition = current.getJSONObject("condition")
        val code = condition.optInt("code", -1)
        val description = condition.optString("text", "Погода")
        val precipType = precipitationType(description, current.optDouble("precip_mm", 0.0))
        val emoji = weatherEmoji(code, precipType, current.optInt("is_day", 1) == 1)
        return Weather(kotlin.math.round(current.getDouble("temp_c")).toInt(), precipType,
            current.optString("last_updated"), emoji, description)
    }
    private fun get(address: String): String {
        val c = URL(address).openConnection() as HttpURLConnection
        c.connectTimeout = 10000; c.readTimeout = 10000; c.requestMethod = "GET"
        try {
            val status = c.responseCode
            if (status !in 200..299) {
                val detail = (c.errorStream ?: c.inputStream).bufferedReader().use { it.readText() }
                throw WeatherApiException(status, detail)
            }
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }
}

private fun precipitationType(text: String, amount: Double): Int {
    val t = text.lowercase()
    val rain = listOf("rain", "drizzle", "дожд", "морос").any(t::contains)
    val snow = listOf("snow", "sleet", "ледян", "снег").any(t::contains)
    return when { rain && snow -> 3; snow -> 2; rain || amount > 0.0 -> 1; else -> 0 }
}

private fun weatherEmoji(code: Int, precipitation: Int, isDay: Boolean): String = when {
    code in setOf(1087, 1273, 1276, 1279, 1282) -> "⛈️"
    precipitation == 1 -> "🌧️"
    precipitation == 2 -> "❄️"
    precipitation == 3 -> "🌨️"
    code == 1000 && isDay -> "☀️"
    code == 1000 -> "🌙"
    code in setOf(1003, 1006, 1009) -> "☁️"
    code in setOf(1030, 1135, 1147) -> "🌫️"
    else -> "🌤️"
}

private fun apiErrorMessage(status: Int, body: String): String = try {
    JSONObject(body).optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
} catch (_: Exception) { null } ?: "WeatherAPI вернул HTTP $status."

class WeatherApiException(val status: Int, val responseBody: String) : Exception(apiErrorMessage(status, responseBody))

private fun scheduleBackgroundRefresh(context: Context) {
    val request = PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork("weather-refresh", ExistingPeriodicWorkPolicy.UPDATE, request)
}

class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val p = applicationContext.getSharedPreferences("weather", Context.MODE_PRIVATE)
        val city = try { City(p.getString("name", "")!!, p.getString("admin", "")!!, p.getString("country", "")!!, p.getString("lat", "")!!.toDouble(), p.getString("lon", "")!!.toDouble()) } catch (_: Exception) { return@withContext Result.success() }
        try {
            val w = WeatherApi.current(city)
            p.edit().putInt("temp", w.temp).putInt("code", w.precipType).putString("observed", w.observed).putString("emoji", w.emoji).putString("description", w.description).apply()
            updateLauncherIcon(applicationContext, w)
            Result.success()
        } catch (e: WeatherApiException) { Log.e("WeatherIcon", "WeatherAPI rejected the request", e); Result.failure() }
        catch (e: Exception) { Log.e("WeatherIcon", "Weather worker failed", e); Result.retry() }
    }
}

private fun updateLauncherIcon(context: Context, weather: Weather) {
    val iconType = when {
        weather.emoji.contains("⛈") -> "storm"
        weather.emoji.contains("🌧") -> "rain"
        weather.emoji.contains("❄") -> "snow"
        weather.emoji.contains("🌨") -> "mixed"
        weather.emoji.contains("🌫") -> "fog"
        else -> "clear"
    }
    val t = weather.temp.coerceIn(-99, 99)
    val tempPart = if (t < 0) "m${-t}" else "p$t"
    val aliasName = ".Icon_t${tempPart}_${iconType}"
    val packageName = context.packageName
    val next = ComponentName(context, packageName + aliasName)
    val prefs = context.getSharedPreferences("weather", Context.MODE_PRIVATE)
    val previousName = prefs.getString("active_launcher_icon", ".Icon_default") ?: ".Icon_default"
    try {
        if (previousName == aliasName) {
            context.packageManager.setComponentEnabledSetting(next, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            context.packageManager.setComponentEnabledSetting(next, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            return
        }
        context.packageManager.setComponentEnabledSetting(next, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        context.packageManager.setComponentEnabledSetting(ComponentName(context, packageName + previousName), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        prefs.edit().putString("active_launcher_icon", aliasName).apply()
        Log.i("WeatherIcon", "Launcher icon changed to ${weather.temp} degrees, $iconType")
    } catch (e: Exception) { Log.e("WeatherIcon", "Unable to update launcher icon", e) }
}
