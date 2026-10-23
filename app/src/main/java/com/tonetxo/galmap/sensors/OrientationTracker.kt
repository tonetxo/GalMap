package com.tonetxo.galmap.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlin.math.sqrt

class DeviceOrientation(
    /** Row-major device→world rotation; world is east/north(magnetic)/up. */
    val rotation: FloatArray,
    val compassUnreliable: Boolean,
)

/**
 * Smoothed device orientation from TYPE_ROTATION_VECTOR. The low-pass filter runs on the
 * quaternion, which avoids the 359°→0° jump an angle filter would produce.
 */
class OrientationTracker(context: Context) {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    fun orientation(): Flow<DeviceOrientation> = callbackFlow {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (sensor == null) {
            close(IllegalStateException("No rotation vector sensor"))
            return@callbackFlow
        }

        // Rotation vector layout expected by SensorManager: x, y, z, w.
        val filtered = FloatArray(4)
        var hasValue = false
        var accuracyStatus = SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val q = toQuaternion(event.values)
                if (!hasValue) {
                    q.copyInto(filtered)
                    hasValue = true
                } else {
                    smooth(filtered, q)
                }
                val rotation = FloatArray(9)
                SensorManager.getRotationMatrixFromVector(rotation, filtered)
                // values[4] is the estimated heading accuracy in radians, when the sensor reports it.
                val headingAccuracyRad = event.values.getOrNull(4) ?: -1f
                val unreliable = accuracyStatus <= SensorManager.SENSOR_STATUS_ACCURACY_LOW ||
                    headingAccuracyRad > MAX_HEADING_ERROR_RAD
                trySend(DeviceOrientation(rotation, unreliable))
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                accuracyStatus = accuracy
            }
        }
        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        awaitClose { sensorManager.unregisterListener(listener) }
    }.conflate()

    private fun toQuaternion(values: FloatArray): FloatArray {
        val x = values[0]
        val y = values[1]
        val z = values[2]
        val w = if (values.size >= 4) values[3] else sqrt((1 - x * x - y * y - z * z).coerceAtLeast(0f))
        return floatArrayOf(x, y, z, w)
    }

    /** Normalised linear interpolation towards [target], taking the short way round. */
    private fun smooth(current: FloatArray, target: FloatArray) {
        val dot = current[0] * target[0] + current[1] * target[1] + current[2] * target[2] + current[3] * target[3]
        val sign = if (dot < 0f) -1f else 1f
        var norm = 0f
        for (i in 0..3) {
            current[i] += SMOOTHING * (sign * target[i] - current[i])
            norm += current[i] * current[i]
        }
        norm = sqrt(norm)
        for (i in 0..3) current[i] /= norm
        // SensorManager expects w >= 0 for the 4-component form.
        if (current[3] < 0f) for (i in 0..3) current[i] = -current[i]
    }

    private companion object {
        const val SMOOTHING = 0.15f
        const val MAX_HEADING_ERROR_RAD = 0.35f // ~20°
    }
}
