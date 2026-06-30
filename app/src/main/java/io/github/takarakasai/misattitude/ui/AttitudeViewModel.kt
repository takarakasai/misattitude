package io.github.takarakasai.misattitude.ui

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.takarakasai.misattitude.MisattitudeApplication
import io.github.takarakasai.misattitude.domain.BodyShape
import io.github.takarakasai.misattitude.domain.Conversions
import io.github.takarakasai.misattitude.domain.EulerAngles
import io.github.takarakasai.misattitude.domain.EulerConvention
import io.github.takarakasai.misattitude.domain.Handedness
import io.github.takarakasai.misattitude.domain.Quaternion
import io.github.takarakasai.misattitude.domain.RotationMatrix
import io.github.takarakasai.misattitude.domain.UpAxis
import io.github.takarakasai.misattitude.domain.WorldConvention
import io.github.takarakasai.misattitude.domain.approxEquals
import io.github.takarakasai.misattitude.domain.isFinite
import io.github.takarakasai.misattitude.sensor.DeviceAttitudeSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PlaybackMode {
    /** Geodesic on the unit-quaternion sphere — the "natural" attitude path. */
    Slerp,

    /** Linear interpolation in Euler-angle space; differs from Slerp because Euler-LERP
     *  is not a geodesic on SO(3). Useful for showing how badly Euler-LERP diverges. */
    EulerLerp,

    /** Demonstrates the three-axis decomposition of the END attitude:
     *  start = identity, then rotate a1 about axis1, then a2 about axis2, then a3 about axis3. */
    Step,
}

class AttitudeViewModel(application: Application) : AndroidViewModel(application) {

    // Access the process-wide BillingRepository so the ViewModel can:
    //   - observe entitlement changes and mirror them into UiState.proActive
    //   - forward "Buy Pro" / "Restore purchases" intents from the UI
    // Using getApplication() (provided by AndroidViewModel) is the standard
    // way to reach process-scoped singletons without DI infrastructure.
    private val billing = (application as MisattitudeApplication).billingRepository

    data class UiState(
        val canonical: Quaternion = Quaternion.IDENTITY,
        val convention: EulerConvention = EulerConvention.DEFAULT,
        val worldConvention: WorldConvention = WorldConvention.GraphicsDefault,
        val bodyShape: BodyShape = BodyShape.Spot,
        val start: Quaternion = Quaternion.IDENTITY,
        val end: Quaternion = Quaternion.IDENTITY,
        val playbackMode: PlaybackMode = PlaybackMode.Slerp,
        val playbackT: Double = 0.0,
        val isPlaying: Boolean = false,
        /** t per second (1.0 = traverse start→end in one second) */
        val playbackSpeed: Double = 0.4,
        val showSteps: Boolean = false,
        val showComparison: Boolean = false,
        /**
         * Whether the user has purchased the "Remove ads" Pro upgrade. When
         * true, [MainScreen] hides the AdMob banner entirely (no network
         * requests, the AdView is disposed). Wired to actual purchase state
         * by the Play Billing integration in a follow-up change; for now it
         * stays false so the banner is always shown in test builds.
         */
        val proActive: Boolean = false,
        /**
         * Whether "Live (sensor)" mode is active. While true, [canonical] is
         * driven continuously by the device's rotation-vector sensor (see
         * [DeviceAttitudeSource]) and the manual attitude editors are inert —
         * the body mirrors how the user physically tilts the phone, relative to
         * the last re-center. A Pro-gated feature.
         */
        val sensorActive: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState(proActive = billing.proPurchased.value))
    val state: StateFlow<UiState> = _state.asStateFlow()

    // Device-orientation source for "Live (sensor)" mode. Constructed eagerly
    // (cheap — just resolves the SensorManager and the default sensor) but does
    // not register a listener until setSensorActive(true). Its callback fires on
    // the main thread, so updating the StateFlow here is Compose-safe.
    private val deviceAttitude = DeviceAttitudeSource(application) { q ->
        // Only honour samples while live mode is on. start()/stop() bracket this,
        // but a final in-flight event can arrive just after stop(); the guard
        // makes that a harmless no-op.
        if (_state.value.sensorActive) {
            _state.update { it.copy(canonical = q) }
        }
    }

    /** Whether this device has a usable rotation-vector sensor. Constant for the
     *  process lifetime, so it's fine to read directly from composition. */
    val sensorAvailable: Boolean get() = deviceAttitude.isAvailable

    init {
        // Mirror entitlement changes from BillingRepository into UiState so
        // anything observing UiState (MainScreen → AdBanner show/hide,
        // Settings sheet About → button text) updates reactively without
        // each call site needing its own subscription.
        //
        // When entitlement transitions Pro → Free (refund / billing-hold),
        // also snap any Pro-only selections back to a free default so the
        // UI doesn't keep editing locked content under a free user.
        viewModelScope.launch {
            billing.proPurchased.collect { pro ->
                // Live (sensor) mode is Pro-gated; tear down the listener if the
                // entitlement is lost so a downgraded user can't keep streaming.
                if (!pro) deviceAttitude.stop()
                _state.update { s ->
                    val nextShape = if (!pro && !s.bodyShape.isFree) BodyShape.Cube else s.bodyShape
                    val nextConv = if (!pro && !s.convention.isFree) EulerConvention.DEFAULT else s.convention
                    val nextSensor = if (!pro) false else s.sensorActive
                    s.copy(proActive = pro, bodyShape = nextShape, convention = nextConv, sensorActive = nextSensor)
                }
            }
        }
    }

    // ─── Billing UI hooks ───────────────────────────────────────────────────

    /** Launch the Google Play purchase sheet for the Pro upgrade. Result lands
     *  via the entitlement flow above; nothing else needed in the UI. */
    fun launchProPurchase(activity: Activity) = billing.launchPurchaseFlow(activity)

    /** Re-query Google for purchases — used by the "Restore purchases" button
     *  for users who reinstall or switch devices. */
    fun restorePurchases() = billing.restorePurchases()

    /** Localised price string for the Pro SKU, e.g. "¥800" or "$5.00". May be
     *  empty briefly at app start before ProductDetails has loaded. */
    val proPriceFormatted: String get() = billing.proPriceFormatted

    // ─── Live (sensor) mode ─────────────────────────────────────────────────

    /**
     * Turn "Live (sensor)" mode on or off. Pro-gated: the UI routes free users
     * to the purchase flow, but we also refuse here so no path enables it for
     * free. Enabling re-centers (so the current pose becomes identity) and
     * stops any running playback so the two don't fight over [UiState.canonical].
     */
    fun setSensorActive(active: Boolean) {
        if (active) {
            if (!_state.value.proActive || !deviceAttitude.isAvailable) return
            deviceAttitude.recenter()
            deviceAttitude.start()
            _state.update { it.copy(sensorActive = true, isPlaying = false) }
        } else {
            deviceAttitude.stop()
            _state.update { it.copy(sensorActive = false) }
        }
    }

    /** Re-zero live mode: the phone's current pose becomes the identity attitude. */
    fun recenterSensor() = deviceAttitude.recenter()

    /** Drop the sensor registration to save battery while backgrounded, without
     *  clearing the user's "live mode" intent. Call from the UI on ON_PAUSE. */
    fun pauseSensor() = deviceAttitude.stop()

    /** Re-register the sensor on return to the foreground, but only if live mode
     *  is still on. Call from the UI on ON_RESUME. */
    fun resumeSensor() {
        if (_state.value.sensorActive) deviceAttitude.start()
    }

    override fun onCleared() {
        deviceAttitude.stop()
        super.onCleared()
    }

    // --- direct attitude edits ---

    fun setEuler(angles: EulerAngles) {
        // Live (sensor) mode owns `canonical`; ignore manual edits so a stray
        // slider drag doesn't fight the ~50 Hz sensor stream.
        if (_state.value.sensorActive) return
        val q = Conversions.eulerToQuaternion(angles, _state.value.convention).canonical()
        _state.update { it.copy(canonical = q) }
    }

    fun setQuaternion(q: Quaternion) {
        if (_state.value.sensorActive) return
        if (!q.isFinite() || q.norm() < 1e-9) return
        _state.update { it.copy(canonical = q.normalized().canonical()) }
    }

    fun setMatrix(m: RotationMatrix) {
        if (_state.value.sensorActive) return
        val q = Conversions.matrixToQuaternion(m.orthonormalized()).canonical()
        _state.update { it.copy(canonical = q) }
    }

    fun setConvention(c: EulerConvention) {
        // Defensive gate: even if the UI somehow surfaces a Pro-only convention
        // to a free user (e.g. stale state, deep link), refuse to switch to it.
        // This keeps the entitlement check authoritative in one place.
        if (!c.isFree && !_state.value.proActive) return
        _state.update { it.copy(convention = c) }
    }

    fun setWorldConvention(c: WorldConvention) {
        _state.update { it.copy(worldConvention = c) }
    }

    fun setUpAxis(axis: UpAxis) {
        _state.update { it.copy(worldConvention = it.worldConvention.copy(upAxis = axis)) }
    }

    fun setHandedness(h: Handedness) {
        _state.update { it.copy(worldConvention = it.worldConvention.copy(handedness = h)) }
    }

    fun applyRoboticsPreset() = setWorldConvention(WorldConvention.Robotics)

    fun applyGraphicsPreset() = setWorldConvention(WorldConvention.GraphicsDefault)

    fun setBodyShape(shape: BodyShape) {
        // Same defensive gate as setConvention: Pro-only body shapes (Teapot,
        // Spot) require an active Pro entitlement. UI chips guard against this
        // already; this catches any path that bypasses the UI.
        if (!shape.isFree && !_state.value.proActive) return
        _state.update { it.copy(bodyShape = shape) }
    }

    fun resetToIdentity() {
        // In live mode the sensor equivalent of "reset" is "re-center", which is
        // a separate control; leave `canonical` to the sensor here.
        if (_state.value.sensorActive) return
        _state.update { it.copy(canonical = Quaternion.IDENTITY) }
    }

    // --- playback ---

    fun captureStart() = _state.update { it.copy(start = it.canonical) }

    fun captureEnd() = _state.update { it.copy(end = it.canonical) }

    fun setPlaybackMode(m: PlaybackMode) = _state.update { it.copy(playbackMode = m) }

    fun setPlaybackT(t: Double) {
        if (_state.value.sensorActive) return
        _state.update { s ->
            val tt = t.coerceIn(0.0, 1.0)
            s.copy(playbackT = tt, canonical = trajectory(s, tt))
        }
    }

    fun togglePlaying() {
        if (_state.value.sensorActive) return
        _state.update { s ->
            val resumeFromStart = s.playbackT >= 1.0
            val newT = if (resumeFromStart) 0.0 else s.playbackT
            s.copy(
                isPlaying = !s.isPlaying,
                playbackT = newT,
                canonical = trajectory(s, newT),
            )
        }
    }

    fun resetPlayback() {
        if (_state.value.sensorActive) return
        _state.update { it.copy(playbackT = 0.0, isPlaying = false, canonical = trajectory(it, 0.0)) }
    }

    /** Called from a Compose LaunchedEffect on every frame while [UiState.isPlaying] is true. */
    fun advance(deltaSeconds: Double) {
        _state.update { s ->
            if (!s.isPlaying) return@update s
            val newT = (s.playbackT + deltaSeconds * s.playbackSpeed).coerceIn(0.0, 1.0)
            val playing = newT < 1.0
            s.copy(
                playbackT = newT,
                isPlaying = playing,
                canonical = trajectory(s, newT),
            )
        }
    }

    fun toggleSteps() = _state.update { it.copy(showSteps = !it.showSteps) }

    fun toggleComparison() = _state.update { it.copy(showComparison = !it.showComparison) }

    // --- derived helpers ---

    /** What the currently-selected playback trajectory yields at t. */
    private fun trajectory(s: UiState, t: Double): Quaternion = when (s.playbackMode) {
        PlaybackMode.Slerp -> Quaternion.slerp(s.start, s.end, t).canonical()
        PlaybackMode.EulerLerp -> {
            val sa = Conversions.quaternionToEuler(s.start, s.convention).angles
            val ea = Conversions.quaternionToEuler(s.end, s.convention).angles
            Conversions.eulerLerp(sa, ea, s.convention, t).canonical()
        }
        PlaybackMode.Step -> {
            val ea = Conversions.quaternionToEuler(s.end, s.convention).angles
            Conversions.stepInterpolated(ea, s.convention, t).canonical()
        }
    }

    /** Comparison ghost: when comparison is on AND mode is Slerp, render Euler-LERP at same t.
     *  When comparison is on AND mode is Euler-LERP, render Slerp at same t. */
    fun ghostQuaternion(s: UiState): Quaternion? {
        if (!s.showComparison) return null
        return when (s.playbackMode) {
            PlaybackMode.Slerp -> {
                val sa = Conversions.quaternionToEuler(s.start, s.convention).angles
                val ea = Conversions.quaternionToEuler(s.end, s.convention).angles
                Conversions.eulerLerp(sa, ea, s.convention, s.playbackT).canonical()
            }
            PlaybackMode.EulerLerp -> Quaternion.slerp(s.start, s.end, s.playbackT).canonical()
            PlaybackMode.Step -> Quaternion.slerp(Quaternion.IDENTITY, s.end, s.playbackT).canonical()
        }
    }

    /** Step-axes positions for the live (currently-displayed) Euler decomposition. */
    fun stepFrames(s: UiState): Pair<Quaternion, Quaternion>? {
        if (!s.showSteps) return null
        val angles = Conversions.quaternionToEuler(s.canonical, s.convention).angles
        val frames = Conversions.stepOrientations(angles, s.convention)
        // frames[0] = identity (= world axes already shown), frames[1] = after step 1, frames[2] = after step 2.
        // We surface the two intermediate frames; if either is essentially identity or essentially the body
        // attitude, the renderer will still draw it but it overlaps — that's fine pedagogically.
        return frames[1] to frames[2]
    }

    fun isCanonicalIdentity(): Boolean = _state.value.canonical.approxEquals(Quaternion.IDENTITY)
}
