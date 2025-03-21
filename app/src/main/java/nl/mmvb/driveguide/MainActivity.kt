package nl.mmvb.driveguide

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import nl.mmvb.driveguide.ui.theme.DriveGuideTheme
import nl.mmvb.driveguide.ui.theme.OverpassFamily
import nl.mmvb.driveguide.ui.theme.TrafficRed
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private const val TAG = "DriveGuide"

class MainActivity : ComponentActivity() {
    private val locationViewModel: LocationViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startLocationUpdates()
        }
    }

    private var first: Boolean = true
    private var lastLocation: Location? = null
    private var lastUpdateTime: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DriveGuideTheme {
                Surface {
                    LocationScreen(locationViewModel)
                }
            }
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                startLocationUpdates()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val locationListener = LocationListener { location ->
            locationViewModel.updateSpeed(location.speed * 3.6, location.speedAccuracyMetersPerSecond * 3.6)
            Log.d(TAG, "Speed: ${location.speed * 3.6}")
            Log.d(TAG, "Speed Accuracy: ${location.speedAccuracyMetersPerSecond * 3.6}")
            Log.d(TAG, "Location: ${location.latitude} ${location.longitude}")
            Log.d(TAG, "Horizontal Accuracy: ${location.accuracy}")

            val currentTime = System.currentTimeMillis()
            val distance = lastLocation?.distanceTo(location) ?: Float.MAX_VALUE
            val timeElapsed = currentTime - lastUpdateTime

            if (first || (distance > 10 && timeElapsed > 5000) || timeElapsed > 30000) {
                first = false
                lastLocation = location
                lastUpdateTime = currentTime

                locationViewModel.updateLocation(location.latitude, location.longitude, location.accuracy)
            }
        }

        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0f, locationListener)
    }
}

@Composable
fun LocationScreen(locationViewModel: LocationViewModel, modifier: Modifier = Modifier) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val speed by locationViewModel.speed.collectAsState()
    //val speedAccuracy by locationViewModel.speed.collectAsState()
    val maxSpeed by locationViewModel.maxSpeed.collectAsState()
    val road by locationViewModel.road.collectAsState()

    var color = Color.Unspecified

    if (speed != null && maxSpeed != null) {
        val currentSpeed = speed!!
        //val currentSpeedAccuracy = speedAccuracy!!
        val speedLimit = maxSpeed!!.toDouble()

        if (speed!! <= maxSpeed!!) {
            color = lerp(MaterialTheme.colorScheme.onBackground, Color.Green, scaleFactor(currentSpeed, speedLimit, 5.0))
        } else {
            var margin = 3.0

            if (currentSpeed >= 100.0) {
                margin = (currentSpeed * 0.03)
            }

            if (speedLimit < 130) {
                margin += 3.0
            }

            //margin -= min(currentSpeedAccuracy/2.0, margin)

            color = if (currentSpeed >= speedLimit + margin) {
                Color.Red
            } else {
                lerp(Color.Red, Color.Green, scaleFactor(currentSpeed, speedLimit, margin))
            }
        }
    }

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (isLandscape) {
            Row(
                modifier = modifier
                    .padding(vertical = 25.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Speed(
                    speed = speed,
                    modifier = Modifier
                        .weight(1f),
                    color = color
                )
                MaxSpeed(
                    maxSpeed = maxSpeed,
                    modifier = Modifier
                        .weight(1f)
                )
                Road(
                    road = road,
                    modifier = Modifier
                        .weight(1f)
                )
            }
        } else {
            Column(
                modifier = modifier
                    .safeDrawingPadding()
                    .padding(horizontal = 25.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Speed(
                    speed = speed,
                    modifier = Modifier
                        .weight(1f),
                    color = color
                )
                MaxSpeed(
                    maxSpeed = maxSpeed,
                    modifier = Modifier
                        .weight(1f)
                )
                Road(
                    road = road,
                    modifier = Modifier
                        .weight(1f),
                )
            }
        }
    }
}

private fun scaleFactor(input: Double, target: Double, margin: Double): Float {
    val difference = abs(input - target)
    return when {
        difference <= margin -> (1 - (difference / margin)).toFloat()
        else -> 0.0f
    }
}

@Preview(showBackground = true)
@Composable
fun LocationScreenPreview() {
    DriveGuideTheme(darkTheme = true) {
        Surface {
            LocationScreen(LocationViewModel())
        }
    }
}

@Composable
fun Speed(speed: Double?, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    BoxWithConstraints(
        modifier = modifier
    ) {
        val speedText = speed?.let { "%.0f".format(it) } ?: "-"
        var fontSize = (maxWidth.value / (2 / 1.2)).sp
        var fontWeight = FontWeight.Bold
        if (speedText.length > 2) {
            fontSize = (maxWidth.value / (speedText.length / 1.5)).sp
            fontWeight = FontWeight.SemiBold
        }
        Text(
            text = speedText,
            style = TextStyle(
                fontSize = fontSize,
                fontWeight = fontWeight,
                color = color
            ),
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Preview(showBackground = false)
@Composable
fun SpeedPreview() {
    DriveGuideTheme(darkTheme = true) {
        Surface {
            Speed(130.0)
        }
    }
}

@Composable
fun MaxSpeed(maxSpeed: Int?, modifier: Modifier = Modifier) {
    val maxSpeedText = maxSpeed?.toString() ?: "-"
    BoxWithConstraints(
        modifier = modifier
    ) {
        val border = (min(maxHeight.value, maxWidth.value) / 11).dp
        BoxWithConstraints(
            modifier = Modifier
                .align(Alignment.Center)
                .clip(CircleShape)
                .background(Color.White)
                .aspectRatio(1f)
                .border(border, TrafficRed, CircleShape)
        ) {
            var fontSize = (maxWidth.value / (max(maxSpeedText.length, 2) / 1.25)).sp
            var fontWeight = FontWeight.Bold
            var letterSpacing = (-10).sp
            if (maxSpeedText.length > 2) {
                fontSize = (maxWidth.value / (maxSpeedText.length / 1.5)).sp
                fontWeight = FontWeight.SemiBold
                letterSpacing = (-10).sp
            }
            Text(
                text = maxSpeedText,
                style = TextStyle(
                    fontFamily = OverpassFamily,
                    fontSize = fontSize,
                    fontWeight = fontWeight,
                    letterSpacing = letterSpacing,
                    color = Color.Black,
                ),
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Preview(showBackground = false)
@Composable
fun MaxSpeedPreview() {
    DriveGuideTheme(darkTheme = true) {
        Surface {
            MaxSpeed(100)
        }
    }
}

@Preview(showBackground = false)
@Composable
fun MaxSpeedPreview2() {
    DriveGuideTheme(darkTheme = true) {
        Surface {
            MaxSpeed(80)
        }
    }
}

@Composable
fun RoadNumberSign(text: String, size: TextUnit, color: Color, background: Color, border: Boolean, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        val shape = RoundedCornerShape((size.value / 6).dp)

        val baseModifier = Modifier
            .clip(shape)
            .background(background)
            .padding((size.value / 4).dp, 0.dp)

        val conditionalModifier = if (border) {
            Modifier
                .border((size.value / 20).dp, Color.White, shape)
                .then(baseModifier)
        } else {
            baseModifier
        }

        Box(
            modifier = conditionalModifier
        ) {
            Text(
                text = text,
                style = TextStyle(
                    fontFamily = OverpassFamily,
                    fontSize = size,
                    fontWeight = FontWeight.SemiBold,
                    color = color
                )
            )
        }
    }
}

@Composable
fun Road(road: Road?, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly) {
        if (road != null) {
            BoxWithConstraints(modifier = Modifier.padding(vertical = 5.dp)) {
                val text = road.official_name ?: road.name ?: road.ref ?:  road.int_ref ?: "Unknown Road"
                val fontSize = (maxWidth.value / (max(text.length, 2) / 1.6)).sp
                Text(
                    text = text,
                    style = TextStyle(
                        fontSize = fontSize,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            Row(modifier = Modifier.padding(vertical = 5.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                if (road.ref != null && road.ref.matches("""A\s?\d+""".toRegex())) {
                    RoadNumberSign(
                        text = road.ref.replace(" ", ""),
                        size = 56.sp,
                        color = Color.White,
                        background = Color(0xFFBB1E10),
                        border = true
                    )
                } else if (road.ref != null && road.ref.matches("""N\s?\d+""".toRegex())) {
                    RoadNumberSign(
                        text = road.ref.replace(" ", ""),
                        size = 56.sp,
                        color = Color.Black,
                        background = Color(0xFFFFC800),
                        border = false
                    )
                }

                if (road.int_ref != null && road.int_ref.matches("""E\s?\d+""".toRegex())) {
                    RoadNumberSign(
                        text = road.int_ref.replace(" ", ""),
                        size = 56.sp,
                        color = Color.White,
                        background = Color(0xFF10BB19),
                        border = true
                    )
                }
            }
        }
    }
}

@Preview(showBackground = false)
@Composable
fun RoadPreview() {
    DriveGuideTheme(darkTheme = true) {
        Surface {
            Road(road = Road(ref = "A1", int_ref = "E231"))
        }
    }
}

@Preview(showBackground = false)
@Composable
fun Road2Preview() {
    DriveGuideTheme(darkTheme = true) {
        Surface {
            Road(road = Road(ref = "N223", int_ref = "E30"))
        }
    }
}

@Preview(showBackground = false)
@Composable
fun Road3Preview() {
    DriveGuideTheme(darkTheme = true) {
        Surface {
            Road(road = Road(name = "Rijksweg A1", int_ref = "E231"))
        }
    }
}

@Preview(showBackground = false)
@Composable
fun Road4Preview() {
    DriveGuideTheme(darkTheme = true) {
        Surface {
            Road(road = Road(name = "Kerkstraat"))
        }
    }
}