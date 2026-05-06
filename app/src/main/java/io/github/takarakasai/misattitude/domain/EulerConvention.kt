package io.github.takarakasai.misattitude.domain

enum class Axis(val unit: Vec3) {
    X(Vec3.X),
    Y(Vec3.Y),
    Z(Vec3.Z);

    val index: Int get() = ordinal
}

enum class FrameKind { Intrinsic, Extrinsic }

/**
 * One of the 12 Euler-angle orderings, paired with a frame interpretation.
 *
 * Tait-Bryan orderings use three distinct axes (XYZ, XZY, YXZ, YZX, ZXY, ZYX);
 * proper Euler orderings repeat the first axis as the third (XYX, XZX, YXY, YZY, ZXZ, ZYZ).
 *
 * Intrinsic: each rotation is about the *current* (rotated) body axis. The rotation matrix is
 *   R = R(axis1, a1) * R(axis2, a2) * R(axis3, a3).
 * Extrinsic: each rotation is about a *fixed* world axis. The rotation matrix is
 *   R = R(axis3, a3) * R(axis2, a2) * R(axis1, a1).
 */
data class EulerConvention(
    val axis1: Axis,
    val axis2: Axis,
    val axis3: Axis,
    val frame: FrameKind,
) {
    init {
        require(axis1 != axis2 && axis2 != axis3) {
            "Adjacent axes must differ: $axis1-$axis2-$axis3"
        }
    }

    val isProperEuler: Boolean = axis1 == axis3
    val isTaitBryan: Boolean = !isProperEuler

    /** A short label like "XYZ (intrinsic)". */
    val label: String = "${axis1.name}${axis2.name}${axis3.name} (${frame.name.lowercase()})"

    companion object {
        // 6 Tait-Bryan + 6 proper Euler orderings.
        private val taitBryan = listOf(
            Triple(Axis.X, Axis.Y, Axis.Z),
            Triple(Axis.X, Axis.Z, Axis.Y),
            Triple(Axis.Y, Axis.X, Axis.Z),
            Triple(Axis.Y, Axis.Z, Axis.X),
            Triple(Axis.Z, Axis.X, Axis.Y),
            Triple(Axis.Z, Axis.Y, Axis.X),
        )
        private val properEuler = listOf(
            Triple(Axis.X, Axis.Y, Axis.X),
            Triple(Axis.X, Axis.Z, Axis.X),
            Triple(Axis.Y, Axis.X, Axis.Y),
            Triple(Axis.Y, Axis.Z, Axis.Y),
            Triple(Axis.Z, Axis.X, Axis.Z),
            Triple(Axis.Z, Axis.Y, Axis.Z),
        )

        val ALL: List<EulerConvention> = buildList {
            for ((a, b, c) in taitBryan + properEuler) {
                add(EulerConvention(a, b, c, FrameKind.Intrinsic))
                add(EulerConvention(a, b, c, FrameKind.Extrinsic))
            }
        }

        val DEFAULT = EulerConvention(Axis.Z, Axis.Y, Axis.X, FrameKind.Intrinsic)
    }
}
