package io.github.takarakasai.misattitude.domain

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Conversions between Quaternion / RotationMatrix / EulerAngles for any of the 12 Euler
 * orderings, in either intrinsic (body-frame) or extrinsic (world-frame) form.
 *
 * Internal canonical form is the unit quaternion (Hamilton convention).
 *
 * Implementation note. For Euler extraction we treat extrinsic (A,B,C) as intrinsic (C,B,A)
 * with the angle order reversed — that identity follows directly from
 *   R_extrinsic(A,B,C; a1,a2,a3) = R_C(a3) R_B(a2) R_A(a1)
 *   = R_intrinsic(C,B,A; a3,a2,a1).
 */
object Conversions {

    private const val GIMBAL_EPS = 1e-6

    // ---------------------------------------------------------------------
    // Quaternion <-> RotationMatrix
    // ---------------------------------------------------------------------

    fun quaternionToMatrix(q: Quaternion): RotationMatrix {
        val n = q.normalized()
        val w = n.w; val x = n.x; val y = n.y; val z = n.z
        val xx = x * x; val yy = y * y; val zz = z * z
        val xy = x * y; val xz = x * z; val yz = y * z
        val wx = w * x; val wy = w * y; val wz = w * z
        return RotationMatrix(
            arrayOf(
                doubleArrayOf(1 - 2 * (yy + zz), 2 * (xy - wz),     2 * (xz + wy)),
                doubleArrayOf(2 * (xy + wz),     1 - 2 * (xx + zz), 2 * (yz - wx)),
                doubleArrayOf(2 * (xz - wy),     2 * (yz + wx),     1 - 2 * (xx + yy)),
            )
        )
    }

    /** Shepperd's method — numerically stable across the full SO(3). */
    fun matrixToQuaternion(m: RotationMatrix): Quaternion {
        val trace = m[0, 0] + m[1, 1] + m[2, 2]
        return if (trace > 0.0) {
            val s = sqrt(trace + 1.0) * 2.0    // s = 4*w
            Quaternion(
                w = 0.25 * s,
                x = (m[2, 1] - m[1, 2]) / s,
                y = (m[0, 2] - m[2, 0]) / s,
                z = (m[1, 0] - m[0, 1]) / s,
            ).normalized()
        } else if (m[0, 0] > m[1, 1] && m[0, 0] > m[2, 2]) {
            val s = sqrt(1.0 + m[0, 0] - m[1, 1] - m[2, 2]) * 2.0   // s = 4*x
            Quaternion(
                w = (m[2, 1] - m[1, 2]) / s,
                x = 0.25 * s,
                y = (m[0, 1] + m[1, 0]) / s,
                z = (m[0, 2] + m[2, 0]) / s,
            ).normalized()
        } else if (m[1, 1] > m[2, 2]) {
            val s = sqrt(1.0 + m[1, 1] - m[0, 0] - m[2, 2]) * 2.0   // s = 4*y
            Quaternion(
                w = (m[0, 2] - m[2, 0]) / s,
                x = (m[0, 1] + m[1, 0]) / s,
                y = 0.25 * s,
                z = (m[1, 2] + m[2, 1]) / s,
            ).normalized()
        } else {
            val s = sqrt(1.0 + m[2, 2] - m[0, 0] - m[1, 1]) * 2.0   // s = 4*z
            Quaternion(
                w = (m[1, 0] - m[0, 1]) / s,
                x = (m[0, 2] + m[2, 0]) / s,
                y = (m[1, 2] + m[2, 1]) / s,
                z = 0.25 * s,
            ).normalized()
        }
    }

    // ---------------------------------------------------------------------
    // EulerAngles -> Quaternion (always exact, for any convention)
    // ---------------------------------------------------------------------

    fun eulerToQuaternion(angles: EulerAngles, c: EulerConvention): Quaternion {
        val q1 = Quaternion.fromAxisAngle(c.axis1.unit, angles.a1)
        val q2 = Quaternion.fromAxisAngle(c.axis2.unit, angles.a2)
        val q3 = Quaternion.fromAxisAngle(c.axis3.unit, angles.a3)
        // Hamilton multiplication: (qa * qb) applies qb first, then qa.
        // Intrinsic (rotating frame): R = R(axis1,a1) * R(axis2,a2) * R(axis3,a3)
        //   so the FIRST operation in time corresponds to the LEFTMOST quat.
        // Extrinsic (fixed frame): R = R(axis3,a3) * R(axis2,a2) * R(axis1,a1).
        return if (c.frame == FrameKind.Intrinsic) q1 * q2 * q3 else q3 * q2 * q1
    }

    fun eulerToMatrix(angles: EulerAngles, c: EulerConvention): RotationMatrix {
        val r1 = RotationMatrix.rotAxis(c.axis1, angles.a1)
        val r2 = RotationMatrix.rotAxis(c.axis2, angles.a2)
        val r3 = RotationMatrix.rotAxis(c.axis3, angles.a3)
        return if (c.frame == FrameKind.Intrinsic) r1 * r2 * r3 else r3 * r2 * r1
    }

    // ---------------------------------------------------------------------
    // Quaternion -> EulerAngles (per convention; flags gimbal lock)
    // ---------------------------------------------------------------------

    data class EulerExtraction(val angles: EulerAngles, val gimbalLock: Boolean)

    fun quaternionToEuler(q: Quaternion, c: EulerConvention): EulerExtraction {
        val m = quaternionToMatrix(q)
        return matrixToEuler(m, c)
    }

    fun matrixToEuler(m: RotationMatrix, c: EulerConvention): EulerExtraction {
        // Reduce extrinsic (A,B,C; a1,a2,a3) to intrinsic (C,B,A; a3,a2,a1).
        if (c.frame == FrameKind.Extrinsic) {
            val intrinsicConv = EulerConvention(c.axis3, c.axis2, c.axis1, FrameKind.Intrinsic)
            val (e, lock) = extractIntrinsic(m, intrinsicConv)
            return EulerExtraction(EulerAngles(e.a3, e.a2, e.a1), lock)
        }
        return extractIntrinsic(m, c)
    }

    private fun extractIntrinsic(M: RotationMatrix, c: EulerConvention): EulerExtraction {
        val i = c.axis1.index
        val j = c.axis2.index
        return if (c.isProperEuler) {
            val k = thirdAxis(i, j)
            val sigma = parity(i, j, k)
            extractProperEuler(M, i, j, k, sigma)
        } else {
            val k = c.axis3.index
            val sigma = parity(i, j, k)
            extractTaitBryan(M, i, j, k, sigma)
        }
    }

    private fun extractTaitBryan(M: RotationMatrix, i: Int, j: Int, k: Int, sigma: Int): EulerExtraction {
        // M = R_i(a1) * R_j(a2) * R_k(a3) decomposes as:
        //   M[i][k] =  sigma * sin(a2)
        //   M[j][k] = -sigma * cos(a2) * sin(a1)
        //   M[k][k] =                 cos(a2) * cos(a1)
        //   M[i][i] =  cos(a2) * cos(a3)
        //   M[i][j] = -sigma * cos(a2) * sin(a3)
        val s2 = (sigma * M[i, k]).coerceIn(-1.0, 1.0)
        val a2 = asin(s2)
        val cosA2 = sqrt(max(0.0, 1.0 - s2 * s2))
        return if (cosA2 > GIMBAL_EPS) {
            val a1 = atan2(-sigma * M[j, k], M[k, k])
            val a3 = atan2(-sigma * M[i, j], M[i, i])
            EulerExtraction(EulerAngles(a1, a2, a3), false)
        } else {
            // Gimbal lock: cos(a2) ≈ 0. Only (a1 ± a3) is observable, where the sign
            // depends on sign(s2). Pin a3 = 0 and recover the combined angle as a1.
            val s2Sign = if (s2 >= 0.0) 1.0 else -1.0
            val a1 = atan2(s2Sign * M[j, i], M[j, j])
            val a3 = 0.0
            EulerExtraction(EulerAngles(a1, a2, a3), true)
        }
    }

    private fun extractProperEuler(M: RotationMatrix, i: Int, j: Int, k: Int, sigma: Int): EulerExtraction {
        // M = R_i(a1) * R_j(a2) * R_i(a3) with k the third basis axis (3 - i - j):
        //   M[i][i] =  cos(a2)
        //   M[i][j] =  sin(a2) * sin(a3)
        //   M[i][k] =  sigma * sin(a2) * cos(a3)
        //   M[j][i] =  sin(a1) * sin(a2)
        //   M[k][i] = -sigma * cos(a1) * sin(a2)
        val cosA2 = M[i, i].coerceIn(-1.0, 1.0)
        val a2 = acos(cosA2)
        val sinA2 = sqrt(max(0.0, 1.0 - cosA2 * cosA2))
        return if (sinA2 > GIMBAL_EPS) {
            val a1 = atan2(M[j, i], -sigma * M[k, i])
            val a3 = atan2(M[i, j], sigma * M[i, k])
            EulerExtraction(EulerAngles(a1, a2, a3), false)
        } else {
            // Gimbal lock: sin(a2) ≈ 0, a2 ≈ 0 or π. Only (a1 ± a3) is observable,
            // where the sign depends on sign(c2). Pin a3 = 0 and recover the combined angle as a1.
            val c2Sign = if (M[i, i] >= 0.0) 1.0 else -1.0
            val a1 = atan2(-sigma * c2Sign * M[j, k], M[j, j])
            val a3 = 0.0
            EulerExtraction(EulerAngles(a1, a2, a3), true)
        }
    }

    // ---------------------------------------------------------------------
    // Step-by-step decomposition for animation/visualization.
    //
    // Returns four orientations (q0, q1, q2, q3) where:
    //   q0 = identity
    //   q1 = body orientation after applying ONLY the first elementary rotation
    //   q2 = body orientation after the first two elementary rotations
    //   q3 = full target orientation (= eulerToQuaternion(angles, c))
    //
    // For both intrinsic and extrinsic, the "first" rotation is angles.a1 about axis1,
    // matching how the user reads the convention. The body axes drawn at q1, q2 show
    // the frame in which the next rotation is applied.
    // ---------------------------------------------------------------------

    fun stepOrientations(angles: EulerAngles, c: EulerConvention): List<Quaternion> {
        val q1 = Quaternion.fromAxisAngle(c.axis1.unit, angles.a1)
        val q2 = Quaternion.fromAxisAngle(c.axis2.unit, angles.a2)
        val q3 = Quaternion.fromAxisAngle(c.axis3.unit, angles.a3)
        return if (c.frame == FrameKind.Intrinsic) {
            // body-frame: each subsequent rotation is composed on the right.
            listOf(Quaternion.IDENTITY, q1, q1 * q2, q1 * q2 * q3)
        } else {
            // world-frame: each subsequent rotation pre-multiplies in the world frame.
            listOf(Quaternion.IDENTITY, q1, q2 * q1, q3 * q2 * q1)
        }
    }

    /** Same thing but a continuous interpolation for smooth playback. t in [0,1]. */
    fun stepInterpolated(angles: EulerAngles, c: EulerConvention, t: Double): Quaternion {
        val tt = t.coerceIn(0.0, 1.0)
        val seg = (tt * 3.0).coerceAtMost(2.999999)
        val idx = seg.toInt()                          // 0, 1, or 2
        val u = seg - idx                               // [0,1) within segment
        val partialAngle = when (idx) {
            0 -> u * angles.a1
            1 -> u * angles.a2
            else -> u * angles.a3
        }
        val partial = when (idx) {
            0 -> Quaternion.fromAxisAngle(c.axis1.unit, partialAngle)
            1 -> Quaternion.fromAxisAngle(c.axis2.unit, partialAngle)
            else -> Quaternion.fromAxisAngle(c.axis3.unit, partialAngle)
        }
        val q1 = Quaternion.fromAxisAngle(c.axis1.unit, angles.a1)
        val q2 = Quaternion.fromAxisAngle(c.axis2.unit, angles.a2)
        return if (c.frame == FrameKind.Intrinsic) {
            when (idx) {
                0 -> partial
                1 -> q1 * partial
                else -> q1 * q2 * partial
            }
        } else {
            when (idx) {
                0 -> partial
                1 -> partial * q1
                else -> partial * q2 * q1
            }
        }
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private fun thirdAxis(i: Int, j: Int): Int = 3 - i - j

    /** +1 if (i,j,k) is an even permutation of (0,1,2), -1 if odd. Requires distinct values. */
    private fun parity(i: Int, j: Int, k: Int): Int {
        val v = (i - j) * (j - k) * (k - i) / 2
        return if (v > 0) 1 else -1
    }

    /** True if [a2] is close to a Euler-extraction singularity for convention [c]. */
    fun isNearGimbalLock(angles: EulerAngles, c: EulerConvention, eps: Double = 1e-3): Boolean {
        return if (c.isProperEuler) {
            // singular at a2 = 0 or π (sin(a2) = 0)
            abs(kotlin.math.sin(angles.a2)) < eps
        } else {
            // singular at a2 = ±π/2 (cos(a2) = 0)
            abs(kotlin.math.cos(angles.a2)) < eps
        }
    }

    /** Slerp on quaternions, exposed at object level for callers that don't import the companion. */
    fun slerp(a: Quaternion, b: Quaternion, t: Double): Quaternion = Quaternion.slerp(a, b, t)

    /** Linear-in-Euler interpolation for the comparison demo (NOT a geodesic on SO(3)). */
    fun eulerLerp(start: EulerAngles, end: EulerAngles, c: EulerConvention, t: Double): Quaternion {
        val u = t.coerceIn(0.0, 1.0)
        val angles = EulerAngles(
            start.a1 + (end.a1 - start.a1) * u,
            start.a2 + (end.a2 - start.a2) * u,
            start.a3 + (end.a3 - start.a3) * u,
        )
        return eulerToQuaternion(angles, c)
    }

    @Suppress("unused")
    private val twoPi = 2.0 * PI
}
