package com.example.weather

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import android.view.LayoutInflater
import android.view.ViewGroup

data class WeatherResponse(
    val name: String,
    val main: Main,
    val weather: List<Weather>,
    val timezone: Int
)

data class Main(
    val temp: Float,
    val humidity: Int
)

data class Weather(
    val description: String,
    val icon: String
)

data class ForecastResponse(
    val list: List<ForecastItem>
)

data class ForecastItem(
    val dt: Long,
    val main: Main,
    val weather: List<Weather>
)

data class InterpolatedForecastItem(
    val time: String,
    val temp: Float,
    val description: String,
    val icon: String
)

interface WeatherApiService {
    @GET("weather")
    suspend fun getWeather(
        @Query("q") city: String,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric",
        @Query("lang") lang: String = "ru"
    ): WeatherResponse

    @GET("forecast")
    suspend fun getForecast(
        @Query("q") city: String,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric",
        @Query("lang") lang: String = "ru"
    ): ForecastResponse
}

class ForecastAdapter(private val forecastItems: List<InterpolatedForecastItem>) :
    RecyclerView.Adapter<ForecastAdapter.ForecastViewHolder>() {

    class ForecastViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val timeTextView: TextView = itemView.findViewById(R.id.timeTextView)
        val tempTextView: TextView = itemView.findViewById(R.id.tempTextView)
        val iconImageView: ImageView = itemView.findViewById(R.id.iconImageView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ForecastViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.forecast_item, parent, false)
        return ForecastViewHolder(view)
    }

    override fun onBindViewHolder(holder: ForecastViewHolder, position: Int) {
        val item = forecastItems[position]
        holder.timeTextView.text = item.time
        holder.tempTextView.text = "${item.temp.toInt()}°C"
        Picasso.get().load("https://openweathermap.org/img/wn/${item.icon}.png")
            .error(android.R.drawable.ic_menu_close_clear_cancel)
            .into(holder.iconImageView)
    }

    override fun getItemCount(): Int = forecastItems.size
}

class MainActivity : AppCompatActivity() {
    private val apiKey = "f84abe653e22fb8183eebdc890000405"
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.openweathermap.org/data/2.5/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    private val weatherService = retrofit.create(WeatherApiService::class.java)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val cityEditText: EditText = findViewById(R.id.cityEditText)
        val cityTextView: TextView = findViewById(R.id.cityTextView)
        val temperatureTextView: TextView = findViewById(R.id.temperatureTextView)
        val humidityTextView: TextView = findViewById(R.id.humidityTextView)
        val conditionTextView: TextView = findViewById(R.id.conditionTextView)
        val lastUpdatedTextView: TextView = findViewById(R.id.lastUpdatedTextView)
        val weatherIcon: ImageView = findViewById(R.id.weatherIcon)
        val refreshButton: Button = findViewById(R.id.refreshButton)
        val progressBar: ProgressBar = findViewById(R.id.progressBar)
        val forecastRecyclerView: RecyclerView = findViewById(R.id.forecastRecyclerView)

        forecastRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        cityEditText.setOnEditorActionListener { _, actionId, event ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                val city = cityEditText.text.toString().trim()
                if (city.isNotEmpty()) {
                    fetchWeatherData(cityTextView, temperatureTextView, humidityTextView, conditionTextView, lastUpdatedTextView, weatherIcon, progressBar, forecastRecyclerView, city)
                    true
                } else {
                    Toast.makeText(this, "Введите город", Toast.LENGTH_SHORT).show()
                    false
                }
            } else {
                false
            }
        }

        refreshButton.setOnClickListener {
            val city = cityEditText.text.toString().trim()
            if (city.isNotEmpty()) {
                fetchWeatherData(cityTextView, temperatureTextView, humidityTextView, conditionTextView, lastUpdatedTextView, weatherIcon, progressBar, forecastRecyclerView, city)
            } else {
                Toast.makeText(this, "Введите город", Toast.LENGTH_SHORT).show()
            }
        }

        fetchWeatherData(cityTextView, temperatureTextView, humidityTextView, conditionTextView, lastUpdatedTextView, weatherIcon, progressBar, forecastRecyclerView, "Moscow")
    }

    private fun interpolateForecast(forecastItems: List<ForecastItem>, timezoneOffset: Int): List<InterpolatedForecastItem> {
        val result = mutableListOf<InterpolatedForecastItem>()
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.add(Calendar.SECOND, timezoneOffset)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startTime = calendar.timeInMillis / 1000

        for (hour in 0 until 12) {
            val targetTime = startTime + hour * 3600
            val nextItems = forecastItems.filter { it.dt >= targetTime }.take(2)
            if (nextItems.size < 2) {
                if (nextItems.isNotEmpty()) {
                    val item = nextItems[0]
                    calendar.timeInMillis = targetTime * 1000
                    result.add(
                        InterpolatedForecastItem(
                            time = sdf.format(calendar.time),
                            temp = item.main.temp,
                            description = item.weather.getOrNull(0)?.description ?: "Нет данных",
                            icon = item.weather.getOrNull(0)?.icon ?: "01d"
                        )
                    )
                }
                continue
            }
            val (prev, next) = nextItems
            val timeDiff = (next.dt - prev.dt).toFloat()
            val weight = if (timeDiff > 0) ((targetTime - prev.dt) / timeDiff) else 0f
            val interpolatedTemp = prev.main.temp + (next.main.temp - prev.main.temp) * weight
            calendar.timeInMillis = targetTime * 1000
            result.add(
                InterpolatedForecastItem(
                    time = sdf.format(calendar.time),
                    temp = interpolatedTemp,
                    description = prev.weather.getOrNull(0)?.description ?: "Нет данных",
                    icon = prev.weather.getOrNull(0)?.icon ?: "01d"
                )
            )
        }
        return result
    }

    private fun fetchWeatherData(
        cityTextView: TextView,
        temperatureTextView: TextView,
        humidityTextView: TextView,
        conditionTextView: TextView,
        lastUpdatedTextView: TextView,
        weatherIcon: ImageView,
        progressBar: ProgressBar,
        forecastRecyclerView: RecyclerView,
        city: String
    ) {
        progressBar.visibility = View.VISIBLE
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val weatherResponse = weatherService.getWeather(city, apiKey)
                val forecastResponse = try {
                    weatherService.getForecast(city, apiKey)
                } catch (e: Exception) {
                    ForecastResponse(emptyList())
                }
                cityTextView.text = weatherResponse.name
                temperatureTextView.text = "${weatherResponse.main.temp.toInt()}°C"
                humidityTextView.text = "Влажность: ${weatherResponse.main.humidity}%"
                conditionTextView.text = weatherResponse.weather.getOrNull(0)?.description?.capitalize() ?: "Нет данных"
                val localTime = Calendar.getInstance().apply {
                    add(Calendar.SECOND, weatherResponse.timezone)
                    timeZone = TimeZone.getTimeZone("UTC")
                }.time
                lastUpdatedTextView.text = "Обновлено: ${
                    SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(localTime)
                }"
                Picasso.get().load("https://openweathermap.org/img/wn/${weatherResponse.weather.getOrNull(0)?.icon ?: "01d"}@2x.png")
                    .error(android.R.drawable.ic_menu_close_clear_cancel)
                    .into(weatherIcon)
                val interpolatedForecast = interpolateForecast(forecastResponse.list, weatherResponse.timezone)
                forecastRecyclerView.adapter = ForecastAdapter(interpolatedForecast)
            } catch (e: HttpException) {
                val errorMessage = when (e.code()) {
                    404 -> "Город не найден"
                    401 -> "Неверный API-ключ"
                    else -> "Ошибка сервера: ${e.code()}"
                }
                Toast.makeText(this@MainActivity, errorMessage, Toast.LENGTH_LONG).show()
                cityTextView.text = "Ошибка"
                temperatureTextView.text = "--°C"
                humidityTextView.text = "Влажность: --%"
                conditionTextView.text = "Не удалось загрузить"
                lastUpdatedTextView.text = "Обновлено: --"
                forecastRecyclerView.adapter = ForecastAdapter(emptyList())
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                cityTextView.text = "Ошибка"
                temperatureTextView.text = "--°C"
                humidityTextView.text = "Влажность: --%"
                conditionTextView.text = "Не удалось загрузить"
                lastUpdatedTextView.text = "Обновлено: --"
                forecastRecyclerView.adapter = ForecastAdapter(emptyList())
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }
}