package nl.mmvb.driveguide

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class LocationViewModel : ViewModel() {
    private val _speed = MutableStateFlow<Double?>(null)
    val speed = _speed.asStateFlow()

    private val _maxSpeed = MutableStateFlow<Int?>(null)
    val maxSpeed = _maxSpeed.asStateFlow()

    private val _road = MutableStateFlow<Road?>(null)
    val road = _road.asStateFlow()

    fun updateSpeed(newSpeed: Double) {
        _speed.value = newSpeed
    }

    fun updateLocation(latitude: Double, longitude: Double) {
        viewModelScope.launch {
            try {
                val query = """
                    [out:json];
                    (
                        way(around:50,$latitude,$longitude)["maxspeed"];
                        way(around:50,$latitude,$longitude)["maxspeed:conditional"];
                    );
                    out;
                """.trimIndent()
                val response = RetrofitClient.instance.getSpeedLimit(query)
                val tags = response.elements.firstOrNull()?.tags
                val maxSpeed = tags?.maxspeed
                val maxSpeedConditional = tags?.maxspeed_conditional
                _maxSpeed.value = determineMaxSpeed(maxSpeed, maxSpeedConditional)?.toIntOrNull()
                _road.value = tags?.toRoad()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun determineMaxSpeed(maxSpeed: String?, maxSpeedConditional: String?): String? {
        return if (maxSpeedConditional != null) {
            parseConditionalSpeed(maxSpeedConditional) ?: maxSpeed
        } else {
            maxSpeed
        }
    }

    private fun parseConditionalSpeed(conditional: String): String? {
        val conditions = conditional.split(';')
        val currentTime = LocalTime.now()
        val timeRangePattern =
            """(?<start>\d{1,2}:\d{2}(?:\s?[AP]M)?)\s?-\s?(?<end>\d{1,2}:\d{2}(?:\s?[AP]M)?)""".toRegex()

        for (condition in conditions) {
            val parts = condition.split('@')

            val speedLimit = parts.firstOrNull()?.trim() ?: continue
            val timeRange = parts.lastOrNull()?.trim() ?: continue

            val timeRangeResult = timeRangePattern.find(timeRange)

            if (timeRangeResult != null) {
                val formatter = DateTimeFormatter.ofPattern("[HH:mm][h:mm a]")
                val startTime = LocalTime.parse(timeRangeResult.groups["start"]?.value, formatter)
                val endTime = LocalTime.parse(timeRangeResult.groups["end"]?.value, formatter)

                if (isTimeWithinRange(currentTime, startTime, endTime)) {
                    return speedLimit
                }
            }
        }
        return null
    }

    private fun isTimeWithinRange(compareTime: LocalTime, startTime: LocalTime, endTime: LocalTime): Boolean {
        return if (startTime.isBefore(endTime)) {
            compareTime in startTime..endTime
        } else {
            compareTime >= startTime || compareTime <= endTime
        }
    }
}

data class Road(
    val name: String? = null,
    val official_name: String? = null,
    val ref: String? = null,
    val int_ref: String? = null,
    val maxspeed: String? = null
) {
    companion object
}

fun Tags.toRoad(): Road {
    return Road(
        name = this.name,
        official_name = this.official_name,
        int_ref = this.int_name,
        ref = this.ref,
        maxspeed = this.maxspeed
    )
}