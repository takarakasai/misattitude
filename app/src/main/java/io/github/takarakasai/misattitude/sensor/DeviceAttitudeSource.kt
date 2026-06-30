package io.github.takarakasai.misattitude.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import io.github.takarakasai.misattitude.domain.Quaternion

/**
 * Streams the physical device orientation as an internal [Quaternion] for the
 * "Live (sensor)" mode, where the on-screen body mirrors how the user tilts the
 * phone.
 *
 * Sensor choice
 * -------------
 * Prefers `TYPE_GAME_ROTATION_VECTOR` — a fused gyroscope+accelerometer estimate
 * that (unlike `TYPE_ROTATION_VECTOR`) does **not** reference the magnetometer.
 * For a relative "tilt from here" demo that is exactly what we want: no compass
 * means no sudden yaw jumps when the user passes a magnet/laptop, and no
 * "the body points North" surprise. Falls back to `TYPE_ROTATION_VECTOR` on
 * devices that lack the game variant.
 *
 * Neither sensor requires a runtime permission or a manifest `<uses-permission>`,
 * so enabling this feature adds nothing to the Data-safety / advertising-ID
 * declarations.
 *
 * Relative reference (re-center)
 * ------------------------------
 * The raw sensor quaternion `qDev` maps device→world (East-North-Up). Showing
 * that absolute pose is confusing, so instead we capture a reference pose
 * `qRef` — the device pose the moment the user enables the mode or taps
 * "Re-center" — and emit the delta
 *
 *     qRel = qRef⁻¹ ⊗ qDev
 *
 * expressed in the reference device frame. At the reference instant `qRel` is
 * identity; tilting the phone from there rotates the body the same way the user
 * perceives the phone moving (the device frame — X right, Y up, Z out of the
 * screen toward the face — lines up with the app's graphics frame when the
 * phone is held facing the camera).
 *
 * **Threading**: `registerListener` without a `Handler` delivers events on the
 * main thread, so [onAttitude] is invoked on the main thread and is safe to use
 * to drive a StateFlow that Compose collects.
 */
class DeviceAttitudeSource(
    context: Context,
    private val onAttitude: (Quaternion) -> Unit,
) : SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val sensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    /** True iff the device exposes a rotation-vector sensor we can use. */
    val isAvailable: Boolean get() = sensor != null

    // Reused across events — onSensorChanged fires ~50×/s, so we don't allocate
    // a fresh array per sample.
    private val rotation = FloatArray(4)

    /** Reference pose (device→world) captured on the first sample after a
     *  re-center request. Null until that first sample arrives. */
    private var reference: Quaternion? = null

    /** When true, the next sample (re)defines [reference]. Armed initially and
     *  by [recenter]; deliberately NOT armed by [start] so that resuming after
     *  an app-background keeps the same zero pose. */
    private var pendingRecenter = true
    private var listening = false

    /** Begin (or resume) delivering samples. Idempotent. Keeps any existing
     *  [reference] so a pause/resume cycle does not silently re-zero. */
    fun start() {
        val s = sensor ?: return
        if (listening) return
        listening = sensorManager.registerListener(this, s, SensorManager.SENSOR_DELAY_GAME)
    }

    /** Stop delivering samples. Idempotent. Retains [reference]. */
    fun stop() {
        if (!listening) return
        sensorManager.unregisterListener(this)
        listening = false
    }

    /** Re-zero: the next sample becomes the new identity reference. */
    fun recenter() {
        pendingRecenter = true
    }

    override fun onSensorChanged(event: SensorEvent) {
        // getQuaternionFromVector accepts a length-4 or length-5 rotation vector
        // and writes [w, x, y, z] into `rotation`.
        SensorManager.getQuaternionFromVector(rotation, event.values)
        val qDev = Quaternion(
            rotation[0].toDouble(),
            rotation[1].toDouble(),
            rotation[2].toDouble(),
            rotation[3].toDouble(),
        ).normalized()

        if (pendingRecenter || reference == null) {
            reference = qDev
            pendingRecenter = false
        }
        val ref = reference ?: qDev
        // qRel = ref⁻¹ ⊗ qDev (conjugate == inverse for a unit quaternion),
        // i.e. the rotation since the reference, in the reference device frame.
        val qRel = (ref.conjugate() * qDev).canonical()
        onAttitude(qRel)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { /* no-op */ }
}
