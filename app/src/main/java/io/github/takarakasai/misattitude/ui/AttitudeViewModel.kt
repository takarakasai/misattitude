package io.github.takarakasai.misattitude.ui

import androidx.lifecycle.ViewModel
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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

class AttitudeViewModel : ViewModel() {

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
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    // --- direct attitude edits ---

    fun setEuler(angles: EulerAngles) {
        val q = Conversions.eulerToQuaternion(angles, _state.value.convention).canonical()
        _state.update { it.copy(canonical = q) }
    }

    fun setQuaternion(q: Quaternion) {
        if (!q.isFinite() || q.norm() < 1e-9) return
        _state.update { it.copy(canonical = q.normalized().canonical()) }
    }

    fun setMatrix(m: RotationMatrix) {
        val q = Conversions.matrixToQuaternion(m.orthonormalized()).canonical()
        _state.update { it.copy(canonical = q) }
    }

    fun setConvention(c: EulerConvention) {
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
        _state.update { it.copy(bodyShape = shape) }
    }

    fun resetToIdentity() {
        _state.update { it.copy(canonical = Quaternion.IDENTITY) }
    }

    // --- playback ---

    fun captureStart() = _state.update { it.copy(start = it.canonical) }

    fun captureEnd() = _state.update { it.copy(end = it.canonical) }

    fun setPlaybackMode(m: PlaybackMode) = _state.update { it.copy(playbackMode = m) }

    fun setPlaybackT(t: Double) {
        _state.update { s ->
            val tt = t.coerceIn(0.0, 1.0)
            s.copy(playbackT = tt, canonical = trajectory(s, tt))
        }
    }

    fun togglePlaying() {
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
