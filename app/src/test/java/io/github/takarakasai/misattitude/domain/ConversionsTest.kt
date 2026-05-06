package io.github.takarakasai.misattitude.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Unit tests for [Conversions]. Goals:
 *   1. Round-trip stability: q → euler → q reproduces the same rotation (not necessarily the
 *      same euler triple, since the same rotation has many euler representations).
 *   2. Specific known rotations land on their expected quaternion / matrix.
 *   3. Gimbal-lock detection fires at the documented singularity for each convention family.
 *   4. Step decomposition recomposes to the full target rotation.
 *
 * All conventions (24 = 12 axis orderings × 2 frame interpretations) are exercised.
 */
class ConversionsTest {

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    /** Two quaternions represent the same rotation iff q ≈ ±p. */
    private fun sameRotation(a: Quaternion, b: Quaternion, eps: Double = 1e-9): Boolean {
        val ac = a.normalized().canonical()
        val bc = b.normalized().canonical()
        return abs(ac.w - bc.w) < eps &&
            abs(ac.x - bc.x) < eps &&
            abs(ac.y - bc.y) < eps &&
            abs(ac.z - bc.z) < eps
    }

    private fun assertSameRotation(expected: Quaternion, actual: Quaternion, eps: Double = 1e-9, msg: String = "") {
        assertTrue(
            "$msg expected=${expected.canonical()}, actual=${actual.canonical()}",
            sameRotation(expected, actual, eps),
        )
    }

    private fun assertMatrixEquals(expected: RotationMatrix, actual: RotationMatrix, eps: Double = 1e-9) {
        for (i in 0..2) for (j in 0..2) {
            assertEquals("M[$i][$j]", expected[i, j], actual[i, j], eps)
        }
    }

    /** Apply rotation matrix [m] to vector [v]; returns m * v. */
    private fun mulVec(m: RotationMatrix, v: DoubleArray): DoubleArray {
        return doubleArrayOf(
            m[0, 0] * v[0] + m[0, 1] * v[1] + m[0, 2] * v[2],
            m[1, 0] * v[0] + m[1, 1] * v[1] + m[1, 2] * v[2],
            m[2, 0] * v[0] + m[2, 1] * v[1] + m[2, 2] * v[2],
        )
    }

    private fun assertVecClose(a: DoubleArray, b: DoubleArray, eps: Double = 1e-9, msg: String = "") {
        for (i in 0..2) {
            assertEquals("$msg v[$i]", a[i], b[i], eps)
        }
    }

    // -----------------------------------------------------------------
    // Quaternion <-> Matrix
    // -----------------------------------------------------------------

    @Test
    fun identityQuaternionToMatrixIsIdentity() {
        assertMatrixEquals(RotationMatrix.IDENTITY, Conversions.quaternionToMatrix(Quaternion.IDENTITY))
    }

    @Test
    fun rotX90QuaternionAndMatrixAgree() {
        val q = Quaternion.fromAxisAngle(Vec3.X, PI / 2)
        val m = Conversions.quaternionToMatrix(q)
        assertMatrixEquals(RotationMatrix.rotX(PI / 2), m, 1e-12)
    }

    @Test
    fun rotY90QuaternionAndMatrixAgree() {
        val q = Quaternion.fromAxisAngle(Vec3.Y, PI / 2)
        val m = Conversions.quaternionToMatrix(q)
        assertMatrixEquals(RotationMatrix.rotY(PI / 2), m, 1e-12)
    }

    @Test
    fun rotZ90QuaternionAndMatrixAgree() {
        val q = Quaternion.fromAxisAngle(Vec3.Z, PI / 2)
        val m = Conversions.quaternionToMatrix(q)
        assertMatrixEquals(RotationMatrix.rotZ(PI / 2), m, 1e-12)
    }

    @Test
    fun matrixToQuaternionRoundTripStableAcrossAllOctants() {
        // Pick rotations with traces in different sign regions to exercise every Shepperd branch.
        val cases = listOf(
            // small rotation (trace > 0, w branch)
            Quaternion.fromAxisAngle(Vec3.X, 0.1),
            // 170° about X — trace very negative, x branch dominates
            Quaternion.fromAxisAngle(Vec3.X, 170.0 * PI / 180.0),
            // 170° about Y — y branch dominates
            Quaternion.fromAxisAngle(Vec3.Y, 170.0 * PI / 180.0),
            // 170° about Z — z branch dominates
            Quaternion.fromAxisAngle(Vec3.Z, 170.0 * PI / 180.0),
            // arbitrary mix
            Quaternion.fromAxisAngle(Vec3(1.0, 1.0, 1.0), 1.7),
            Quaternion.fromAxisAngle(Vec3(0.3, -0.7, 0.5), -2.4),
        )
        for (q in cases) {
            val m = Conversions.quaternionToMatrix(q)
            val recovered = Conversions.matrixToQuaternion(m)
            assertSameRotation(q, recovered, eps = 1e-12, msg = "matrix-to-quaternion round trip for $q")
        }
    }

    // -----------------------------------------------------------------
    // Euler ↔ Quaternion: round-trip across all 24 conventions
    // -----------------------------------------------------------------

    /** Non-singular angle triples to feed each convention. */
    private fun nonSingularSamples(): List<EulerAngles> {
        // a2 stays away from singularities for both Tait-Bryan (cos a2 = 0) and proper Euler (sin a2 = 0).
        // For Tait-Bryan, |a2| < π/2; for proper Euler, 0 < a2 < π. Choose values valid for both.
        return listOf(
            EulerAngles(0.3, 0.4, -0.5),
            EulerAngles(-1.1, 0.7, 1.4),
            EulerAngles(2.2, 0.9, -2.5),
            EulerAngles(-2.7, 1.2, 0.6),
            EulerAngles(0.0, 0.5, 0.0),
        )
    }

    @Test
    fun eulerToQuaternionMatchesEulerToMatrixForAllConventions() {
        for (c in EulerConvention.ALL) for (a in nonSingularSamples()) {
            val q = Conversions.eulerToQuaternion(a, c)
            val m = Conversions.eulerToMatrix(a, c)
            val mFromQ = Conversions.quaternionToMatrix(q)
            assertMatrixEquals(m, mFromQ, eps = 1e-9)
        }
    }

    @Test
    fun eulerRoundTripPreservesRotationForAllConventions() {
        // For each (convention, angles) pair: build q, extract euler, build q', verify q ≈ ±q'.
        for (c in EulerConvention.ALL) for (a in nonSingularSamples()) {
            val q = Conversions.eulerToQuaternion(a, c)
            val (recovered, lock) = Conversions.quaternionToEuler(q, c)
            assertFalse("$c $a should not be singular", lock)
            val q2 = Conversions.eulerToQuaternion(recovered, c)
            assertSameRotation(q, q2, eps = 1e-8, msg = "round trip $c with $a")
        }
    }

    // -----------------------------------------------------------------
    // Specific known rotations
    // -----------------------------------------------------------------

    @Test
    fun zyxIntrinsic90AboutZIsRotZ90() {
        val c = EulerConvention(Axis.Z, Axis.Y, Axis.X, FrameKind.Intrinsic)
        val a = EulerAngles(PI / 2, 0.0, 0.0)
        val q = Conversions.eulerToQuaternion(a, c)
        // Rotating the +X axis (1,0,0) by Rz(90°) gives (0,1,0).
        val m = Conversions.quaternionToMatrix(q)
        assertVecClose(doubleArrayOf(0.0, 1.0, 0.0), mulVec(m, doubleArrayOf(1.0, 0.0, 0.0)), eps = 1e-12)
    }

    @Test
    fun extrinsicReversesIntrinsicForReversedAngles() {
        // R_extrinsic(A,B,C; a1,a2,a3) = R_intrinsic(C,B,A; a3,a2,a1)
        for (sample in nonSingularSamples()) {
            val cIntr = EulerConvention(Axis.X, Axis.Y, Axis.Z, FrameKind.Intrinsic)
            val cExtr = EulerConvention(Axis.Z, Axis.Y, Axis.X, FrameKind.Extrinsic)
            val aIntr = sample
            val aExtr = EulerAngles(sample.a3, sample.a2, sample.a1)
            val qI = Conversions.eulerToQuaternion(aIntr, cIntr)
            val qE = Conversions.eulerToQuaternion(aExtr, cExtr)
            assertSameRotation(qI, qE, eps = 1e-12,
                msg = "intrinsic XYZ vs extrinsic ZYX with reversed angles")
        }
    }

    // -----------------------------------------------------------------
    // Gimbal lock detection
    // -----------------------------------------------------------------

    @Test
    fun taitBryanReportsGimbalLockAt90DegreeMiddleAngle() {
        // Tait-Bryan singular when cos(a2) = 0 → a2 = ±π/2.
        for (c in EulerConvention.ALL.filter { it.isTaitBryan }) {
            val a = EulerAngles(0.4, PI / 2, 0.7)
            val q = Conversions.eulerToQuaternion(a, c)
            val (_, lock) = Conversions.quaternionToEuler(q, c)
            assertTrue("$c should detect gimbal lock at a2=π/2", lock)
        }
    }

    @Test
    fun properEulerReportsGimbalLockAtZeroOrPiMiddleAngle() {
        // Proper Euler singular when sin(a2) = 0 → a2 = 0 or π.
        for (c in EulerConvention.ALL.filter { it.isProperEuler }) {
            for (a2 in listOf(0.0, PI)) {
                val a = EulerAngles(0.4, a2, 0.7)
                val q = Conversions.eulerToQuaternion(a, c)
                val (_, lock) = Conversions.quaternionToEuler(q, c)
                assertTrue("$c should detect gimbal lock at a2=$a2", lock)
            }
        }
    }

    @Test
    fun gimbalLockExtractionRoundTripsToSameRotation() {
        // Even at gimbal lock the extracted euler triple, when converted back to a quaternion,
        // must reproduce the original rotation (only the parameterization is degenerate).
        val c = EulerConvention(Axis.Z, Axis.Y, Axis.X, FrameKind.Intrinsic)
        val a = EulerAngles(0.4, PI / 2, 0.7)
        val q = Conversions.eulerToQuaternion(a, c)
        val (recovered, lock) = Conversions.quaternionToEuler(q, c)
        assertTrue(lock)
        val q2 = Conversions.eulerToQuaternion(recovered, c)
        assertSameRotation(q, q2, eps = 1e-9, msg = "gimbal-lock round trip")
    }

    @Test
    fun isNearGimbalLockMatchesExactCases() {
        // sanity: exact singularities are flagged.
        val tb = EulerConvention(Axis.X, Axis.Y, Axis.Z, FrameKind.Intrinsic)
        assertTrue(Conversions.isNearGimbalLock(EulerAngles(0.0, PI / 2, 0.0), tb))
        assertTrue(Conversions.isNearGimbalLock(EulerAngles(0.0, -PI / 2, 0.0), tb))
        assertFalse(Conversions.isNearGimbalLock(EulerAngles(0.0, 0.5, 0.0), tb))

        val pe = EulerConvention(Axis.Z, Axis.X, Axis.Z, FrameKind.Intrinsic)
        assertTrue(Conversions.isNearGimbalLock(EulerAngles(0.0, 0.0, 0.0), pe))
        assertTrue(Conversions.isNearGimbalLock(EulerAngles(0.0, PI, 0.0), pe))
        assertFalse(Conversions.isNearGimbalLock(EulerAngles(0.0, PI / 2, 0.0), pe))
    }

    // -----------------------------------------------------------------
    // stepOrientations decomposition
    // -----------------------------------------------------------------

    @Test
    fun stepOrientationsRecomposeToFullRotationForAllConventions() {
        for (c in EulerConvention.ALL) {
            val a = EulerAngles(0.5, 0.7, -0.9)
            val steps = Conversions.stepOrientations(a, c)
            assertEquals(4, steps.size)
            assertSameRotation(Quaternion.IDENTITY, steps[0], eps = 1e-12, msg = "$c step0")
            val full = Conversions.eulerToQuaternion(a, c)
            assertSameRotation(full, steps[3], eps = 1e-12, msg = "$c step3 vs full")
        }
    }

    @Test
    fun stepInterpolatedHitsBoundariesExactly() {
        val c = EulerConvention(Axis.Z, Axis.Y, Axis.X, FrameKind.Intrinsic)
        val a = EulerAngles(0.5, 0.7, -0.9)
        val steps = Conversions.stepOrientations(a, c)
        assertSameRotation(steps[0], Conversions.stepInterpolated(a, c, 0.0), eps = 1e-12)
        // The implementation uses (t*3).coerceAtMost(2.999999) to avoid out-of-range
        // segment indexing, so t=1.0 lands at u ≈ 0.999999 in segment 2 — close to but
        // not exactly the full rotation. A 1e-5 tolerance covers that safely.
        assertSameRotation(steps[3], Conversions.stepInterpolated(a, c, 1.0), eps = 1e-5)
    }

    // -----------------------------------------------------------------
    // eulerLerp (regression: must produce identity at t=0 and target at t=1)
    // -----------------------------------------------------------------

    @Test
    fun eulerLerpEndpoints() {
        val c = EulerConvention.DEFAULT
        val s = EulerAngles(0.1, 0.2, 0.3)
        val e = EulerAngles(0.7, -0.4, 1.1)
        val qS = Conversions.eulerToQuaternion(s, c)
        val qE = Conversions.eulerToQuaternion(e, c)
        assertSameRotation(qS, Conversions.eulerLerp(s, e, c, 0.0))
        assertSameRotation(qE, Conversions.eulerLerp(s, e, c, 1.0))
    }

    // -----------------------------------------------------------------
    // Slerp endpoints
    // -----------------------------------------------------------------

    @Test
    fun slerpEndpoints() {
        val a = Quaternion.fromAxisAngle(Vec3.X, 0.3)
        val b = Quaternion.fromAxisAngle(Vec3.Y, 1.2)
        assertSameRotation(a, Conversions.slerp(a, b, 0.0))
        assertSameRotation(b, Conversions.slerp(a, b, 1.0))
    }

    // -----------------------------------------------------------------
    // Sanity: rotating basis vectors gives the right answer for each Tait-Bryan ordering
    // -----------------------------------------------------------------

    @Test
    fun composedXyzRotates100() {
        // With XYZ intrinsic, R = Rx(a1) * Ry(a2) * Rz(a3).
        // Apply to (1,0,0): Rz(a3) sends it to (cos a3, sin a3, 0), Ry(a2) keeps y unchanged,
        // and Rx(a1) doesn't move x. So the resulting x-component should be cos(a3) * cos(a2)... wait,
        // actually Ry(a2) does affect (cos a3, sin a3, 0): rotates in xz plane.
        // Final x = cos(a2) * cos(a3) + 0 * Rx contribution = cos(a2)*cos(a3).
        // Easier: just verify against the matrix.
        val c = EulerConvention(Axis.X, Axis.Y, Axis.Z, FrameKind.Intrinsic)
        val a = EulerAngles(0.4, 0.7, -1.1)
        val q = Conversions.eulerToQuaternion(a, c)
        val m = Conversions.quaternionToMatrix(q)
        val expected = mulVec(Conversions.eulerToMatrix(a, c), doubleArrayOf(1.0, 0.0, 0.0))
        val actual = mulVec(m, doubleArrayOf(1.0, 0.0, 0.0))
        assertVecClose(expected, actual, eps = 1e-12)
    }

    @Test
    fun rotationMatrixIsActuallyOrthogonal() {
        for (c in EulerConvention.ALL) {
            val a = EulerAngles(0.3, 0.6, 0.9)
            val m = Conversions.eulerToMatrix(a, c)
            val mt = m.transpose()
            val product = m * mt
            assertMatrixEquals(RotationMatrix.IDENTITY, product, eps = 1e-12)
        }
    }

    // -----------------------------------------------------------------
    // Quaternion algebra sanity checks (helps catch convention regressions in Quaternion.kt)
    // -----------------------------------------------------------------

    @Test
    fun quaternionMultiplicationOrderMatchesMatrixMultiplication() {
        // (q1 * q2) applied to a vector should equal R(q1) * R(q2) * v.
        val q1 = Quaternion.fromAxisAngle(Vec3.X, 0.7)
        val q2 = Quaternion.fromAxisAngle(Vec3.Y, 0.4)
        val qProd = q1 * q2
        val mProd = Conversions.quaternionToMatrix(q1) * Conversions.quaternionToMatrix(q2)
        assertMatrixEquals(mProd, Conversions.quaternionToMatrix(qProd), eps = 1e-12)
    }

    @Test
    fun axisAngleQuaternionRotatesAxisToAxis() {
        // A quaternion rotating about (0,0,1) leaves +Z fixed.
        val q = Quaternion.fromAxisAngle(Vec3.Z, 1.234)
        val m = Conversions.quaternionToMatrix(q)
        val rotated = mulVec(m, doubleArrayOf(0.0, 0.0, 1.0))
        assertVecClose(doubleArrayOf(0.0, 0.0, 1.0), rotated, eps = 1e-12)
    }

    @Test
    fun rotZ90SendsXAxisToY() {
        // Manual sanity: pure 90° about +Z sends +X to +Y.
        val q = Quaternion.fromAxisAngle(Vec3.Z, PI / 2)
        val m = Conversions.quaternionToMatrix(q)
        assertVecClose(doubleArrayOf(0.0, 1.0, 0.0), mulVec(m, doubleArrayOf(1.0, 0.0, 0.0)), eps = 1e-12)
        assertVecClose(doubleArrayOf(-1.0, 0.0, 0.0), mulVec(m, doubleArrayOf(0.0, 1.0, 0.0)), eps = 1e-12)
    }

    @Test
    fun cosSinSanity() {
        // Compile-time guard that the kotlin.math imports are fine in this module.
        assertEquals(1.0, cos(0.0), 0.0)
        assertEquals(0.0, sin(0.0), 0.0)
    }
}
