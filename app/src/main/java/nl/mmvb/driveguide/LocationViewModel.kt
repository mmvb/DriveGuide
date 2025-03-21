package nl.mmvb.driveguide

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.max

class LocationViewModel : ViewModel() {
    private val _speed = MutableStateFlow<Double?>(null)
    val speed = _speed.asStateFlow()

    private val _speedAccuracy = MutableStateFlow<Double?>(null)
    val speedAccuracy = _speedAccuracy.asStateFlow()

    private val _maxSpeed = MutableStateFlow<Int?>(null)
    val maxSpeed = _maxSpeed.asStateFlow()

    private val _road = MutableStateFlow<Road?>(null)
    val road = _road.asStateFlow()

    fun updateSpeed(newSpeed: Double, newSpeedAccuracy: Double) {
        _speed.value = newSpeed
        _speedAccuracy.value = newSpeedAccuracy
    }

    fun updateLocation(latitude: Double, longitude: Double, accuracy: Float) {
        viewModelScope.launch {
            try {
                val range = max(accuracy, 6.0f)
                val query = """
                    [out:json];
                    (
                        way(around:$range,$latitude,$longitude)["highway"="motorway"]["maxspeed"];
                        way(around:$range,$latitude,$longitude)["highway"="motorway"]["maxspeed_conditional"];
                        way(around:$range,$latitude,$longitude)["highway"="motorway_link"]["maxspeed"];
                        way(around:$range,$latitude,$longitude)["highway"="motorway_link"]["maxspeed_conditional"];
                        way(around:$range,$latitude,$longitude)["highway"="trunk"]["maxspeed"];
                        way(around:$range,$latitude,$longitude)["highway"="trunk"]["maxspeed_conditional"];
                        way(around:$range,$latitude,$longitude)["highway"="trunk_link"]["maxspeed"];
                        way(around:$range,$latitude,$longitude)["highway"="trunk_link"]["maxspeed_conditional"];
                        way(around:$range,$latitude,$longitude)["highway"="primary"]["maxspeed"];
                        way(around:$range,$latitude,$longitude)["highway"="primary"]["maxspeed_conditional"];
                        way(around:$range,$latitude,$longitude)["highway"="primary_link"]["maxspeed"];
                        way(around:$range,$latitude,$longitude)["highway"="primary_link"]["maxspeed_conditional"];
                        way(around:$range,$latitude,$longitude)["highway"="secondary"]["maxspeed"];
                        way(around:$range,$latitude,$longitude)["highway"="secondary"]["maxspeed_conditional"];
                        way(around:$range,$latitude,$longitude)["highway"="secondary_link"]["maxspeed"];
                        way(around:$range,$latitude,$longitude)["highway"="secondary_link"]["maxspeed_conditional"];
                        way(around:$range,$latitude,$longitude)["highway"="tertiary"]["maxspeed"];
                        way(around:$range,$latitude,$longitude)["highway"="tertiary"]["maxspeed_conditional"];
                        way(around:$range,$latitude,$longitude)["highway"="tertiary_link"]["maxspeed"];
                        way(around:$range,$latitude,$longitude)["highway"="tertiary_link"]["maxspeed_conditional"];
                        way(around:$range,$latitude,$longitude)["highway"="unclassified"]["maxspeed"];
                        way(around:$range,$latitude,$longitude)["highway"="unclassified"]["maxspeed_conditional"];
                        way(around:$range,$latitude,$longitude)["highway"="unclassified_link"]["maxspeed"];
                        way(around:$range,$latitude,$longitude)["highway"="unclassified_link"]["maxspeed_conditional"];
                        way(around:$range,$latitude,$longitude)["highway"="residential"]["maxspeed"];
                        way(around:$range,$latitude,$longitude)["highway"="residential"]["maxspeed_conditional"];
                        way(around:$range,$latitude,$longitude)["highway"="residential_link"]["maxspeed"];
                        way(around:$range,$latitude,$longitude)["highway"="residential_link"]["maxspeed_conditional"];
                        way(around:$range,$latitude,$longitude)["highway"="service"]["maxspeed"];
                        way(around:$range,$latitude,$longitude)["highway"="service"]["maxspeed_conditional"];
                        way(around:$range,$latitude,$longitude)["highway"="service_link"]["maxspeed"];
                        way(around:$range,$latitude,$longitude)["highway"="service_link"]["maxspeed_conditional"];
                        way(around:$range,$latitude,$longitude)["highway"="living_street"]["maxspeed"];
                        way(around:$range,$latitude,$longitude)["highway"="living_street"]["maxspeed_conditional"];
                        way(around:$range,$latitude,$longitude)["highway"="track"]["maxspeed"];
                        way(around:$range,$latitude,$longitude)["highway"="track"]["maxspeed_conditional"];
                        way(around:$range,$latitude,$longitude)["highway"="path"]["motor_vehicle"="yes"]["maxspeed"];
                        way(around:$range,$latitude,$longitude)["highway"="path"]["motor_vehicle"="yes"]["maxspeed_conditional"];
                        way(around:$range,$latitude,$longitude)["highway"="path"]["motorcar"="yes"]["maxspeed"];
                        way(around:$range,$latitude,$longitude)["highway"="path"]["motorcar"="yes"]["maxspeed_conditional"];
                        way(around:$range,$latitude,$longitude)["highway"="road"]["maxspeed"];
                        way(around:$range,$latitude,$longitude)["highway"="road"]["maxspeed_conditional"];
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