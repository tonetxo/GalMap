package com.tonetxo.galmap.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.tonetxo.galmap.ar.GeoArMath
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.sqrt

/** What the geomagnetic model predicts at the user's position. */
data class ExpectedField(val strengthUt: Float, val inclinationDeg: Float)

class DeviceOrientation(
    /** Row-major device→world rotation; world is east/north(magnetic)/up. */
    val rotation: FloatArray,
    /** The magnetometer is disturbed, so heading is being held by the gyroscope alone. */
    val magneticInterference: Boolean,
)

/**
 * Gyro-first orientation. Attitude comes from TYPE_GAME_ROTATION_VECTOR (gyroscope +
 * accelerometer), which is smooth and immune to magnets but has an arbitrary north. The yaw
 * between that frame and magnetic north is learnt from TYPE_ROTATION_VECTOR, slowly, and only
 * while the measured field strength matches the geomagnetic model; otherwise it is frozen so
 * nearby metal or a magnetic case cannot drag the labels around.
 *
 * Devices without a game rotation vector fall back to the plain rotation vector.
 */
class OrientationTracker(context: Context) {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    /** [expectedField] gives the geomagnetic model at the user's position, when known. */
    fun orientation(expectedField: () -> ExpectedField?): Flow<DeviceOrientation> = callbackFlow {
        val rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (rotationVector == null) {
            close(IllegalStateException("No rotation vector sensor"))
            return@callbackFlow
        }
        val gameVector = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        val fused = gameVector != null

        // Attitude quaternion (x, y, z, w) of the frame we emit from, smoothed.
        val attitude = FloatArray(4)
        var hasAttitude = false
        val attitudeMatrix = FloatArray(9)
        val magneticMatrix = FloatArray(9)

        var yawOffsetDeg: Double? = if (fused) null else 0.0
        var fieldUt = Float.NaN
        var inclinationDeg = Float.NaN
        var magAccuracy = SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM

        // Metal and magnets nearby usually change the field's strength, its dip, or both.
        fun fieldTrusted(): Boolean {
            if (magAccuracy < SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM) return false
            if (fieldUt.isNaN() || inclinationDeg.isNaN()) return false
            val expected = expectedField() ?: DEFAULT_FIELD
            return abs(fieldUt - expected.strengthUt) <= expected.strengthUt * FIELD_STRENGTH_TOLERANCE &&
                abs(inclinationDeg - expected.inclinationDeg) <= INCLINATION_TOLERANCE_DEG
        }

        fun onMagneticField(values: FloatArray) {
            val (x, y, z) = values
            val magnitude = sqrt(x * x + y * y + z * z)
            if (magnitude < 1f || !hasAttitude) return
            // World "up" in device coordinates is the last row of the device→world matrix;
            // the tilt of the gyro frame is exact, so the dip can be measured against it.
            val up = x * attitudeMatrix[6] + y * attitudeMatrix[7] + z * attitudeMatrix[8]
            val dip = Math.toDegrees(asin((-up / magnitude).toDouble().coerceIn(-1.0, 1.0))).toFloat()
            fieldUt = if (fieldUt.isNaN()) magnitude else fieldUt + FIELD_SMOOTHING * (magnitude - fieldUt)
            inclinationDeg = if (inclinationDeg.isNaN()) dip else inclinationDeg + FIELD_SMOOTHING * (dip - inclinationDeg)
        }

        fun emit() {
            val yaw = yawOffsetDeg ?: return
            val rotation = GeoArMath.rotateYaw(attitudeMatrix, yaw)
            trySend(DeviceOrientation(rotation, magneticInterference = fused && !fieldTrusted()))
        }

        fun onAttitude(values: FloatArray, smoothing: Float) {
            val q = toQuaternion(values)
            if (!hasAttitude) {
                q.copyInto(attitude)
                hasAttitude = true
            } else {
                smooth(attitude, q, smoothing)
            }
            SensorManager.getRotationMatrixFromVector(attitudeMatrix, attitude)
            emit()
        }

        /** Learns the yaw between the gyro frame and magnetic north from the 9-axis fusion. */
        fun onMagneticAttitude(values: FloatArray) {
            if (!hasAttitude) return
            SensorManager.getRotationMatrixFromVector(magneticMatrix, toQuaternion(values))
            // Heading is ill-defined when the camera points almost straight up or down.
            if (abs(GeoArMath.cameraPitchDegrees(magneticMatrix)) > MAX_PITCH_FOR_YAW_DEG) return
            val target = GeoArMath.signedDeltaDegrees(
                GeoArMath.cameraHeadingDegrees(attitudeMatrix),
                GeoArMath.cameraHeadingDegrees(magneticMatrix),
            )
            val current = yawOffsetDeg
            yawOffsetDeg = when {
                current == null -> target
                fieldTrusted() -> current + YAW_GAIN * GeoArMath.signedDeltaDegrees(current, target)
                else -> current
            }
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_GAME_ROTATION_VECTOR -> onAttitude(event.values, GAME_SMOOTHING)
                    Sensor.TYPE_ROTATION_VECTOR ->
                        if (fused) onMagneticAttitude(event.values) else onAttitude(event.values, MAG_SMOOTHING)
                    Sensor.TYPE_MAGNETIC_FIELD -> onMagneticField(event.values)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                if (sensor.type == Sensor.TYPE_MAGNETIC_FIELD) magAccuracy = accuracy
            }
        }

        for (sensor in listOfNotNull(rotationVector, gameVector, magnetometer)) {
            sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
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
    private fun smooth(current: FloatArray, target: FloatArray, factor: Float) {
        val dot = current[0] * target[0] + current[1] * target[1] + current[2] * target[2] + current[3] * target[3]
        val sign = if (dot < 0f) -1f else 1f
        var norm = 0f
        for (i in 0..3) {
            current[i] += factor * (sign * target[i] - current[i])
            norm += current[i] * current[i]
        }
        norm = sqrt(norm)
        for (i in 0..3) current[i] /= norm
        // SensorManager expects w >= 0 for the 4-component form.
        if (current[3] < 0f) for (i in 0..3) current[i] = -current[i]
    }

    private companion object {
        /** The gyro frame is already clean; light smoothing just removes hand tremor. */
        const val GAME_SMOOTHING = 0.3f
        const val MAG_SMOOTHING = 0.15f
        const val FIELD_SMOOTHING = 0.1f

        /** Per 9-axis sample (~50 Hz): a time constant of about 5 s. */
        const val YAW_GAIN = 0.004

        const val MAX_PITCH_FOR_YAW_DEG = 70.0

        /** Accepted deviation of the measured field from the geomagnetic model. */
        const val FIELD_STRENGTH_TOLERANCE = 0.15f
        const val INCLINATION_TOLERANCE_DEG = 8f

        /** Galicia, used until the first location fix. */
        val DEFAULT_FIELD = ExpectedField(strengthUt = 44.5f, inclinationDeg = 57f)
    }
}
