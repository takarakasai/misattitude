package io.github.takarakasai.misattitude.domain

/**
 * Which world axis points "up" on screen.
 *
 *   Y — graphics convention (OpenGL, Unity-ish). Y is up.
 *   Z — robotics / engineering convention (ROS, aerospace). Z is up, X is "forward".
 */
enum class UpAxis { Y, Z }

/**
 * Coordinate-system handedness used for visualization.
 *
 *   RightHanded — standard math / physics / OpenGL: +X × +Y = +Z.
 *   LeftHanded — DirectX / Unreal / Unity (visual): +X × +Y = -Z (one axis flipped).
 *
 * The underlying rotation math (quaternion / rotation-matrix) is unchanged.
 * Handedness only affects how the world frame is oriented in the camera view —
 * specifically, whether the un-displayed axis points toward or away from the
 * viewer. Educational benefit: students see the same numerical attitude
 * rendered in either visual convention.
 */
enum class Handedness { RightHanded, LeftHanded }

/**
 * Visualization convention for the world frame in the 3-D view.
 *
 * The body cube's quaternion is independent of this — only the camera placement
 * and (transitively) the up-vector for label billboarding change.
 */
data class WorldConvention(
    val upAxis: UpAxis = UpAxis.Y,
    val handedness: Handedness = Handedness.RightHanded,
) {
    companion object {
        /** OpenGL-ish: Y up, right-handed. The default for graphics tools. */
        val GraphicsDefault = WorldConvention(UpAxis.Y, Handedness.RightHanded)

        /** ROS-style robotics: Z up, X forward, Y left, right-handed. */
        val Robotics = WorldConvention(UpAxis.Z, Handedness.RightHanded)
    }
}
