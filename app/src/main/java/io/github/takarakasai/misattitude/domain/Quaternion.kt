package io.github.takarakasai.misattitude.domain

import kotlin.math.absoluteValue
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Unit quaternion in Hamilton convention: q = w + x*i + y*j + z*k.
 * Stored as (w, x, y, z). Multiplication composes rotations such that
 * (q1 * q2) applied to a vector first applies q2 then q1, matching the
 * matrix product R(q1) * R(q2).
 */
data class Quaternion(val w: Double, val x: Double, val y: Double, val z: Double) {

    operator fun times(o: Quaternion): Quaternion = Quaternion(
        w = w * o.w - x * o.x - y * o.y - z * o.z,
        x = w * o.x + x * o.w + y * o.z - z * o.y,
        y = w * o.y - x * o.z + y * o.w + z * o.x,
        z = w * o.z + x * o.y - y * o.x + z * o.w,
    )

    operator fun unaryMinus() = Quaternion(-w, -x, -y, -z)

    fun conjugate() = Quaternion(w, -x, -y, -z)

    fun norm() = sqrt(w * w + x * x + y * y + z * z)

    fun normalized(): Quaternion {
        val n = norm()
        if (n == 0.0) return IDENTITY
        return Quaternion(w / n, x / n, y / n, z / n)
    }

    /** Hemispherical canonical form: enforce w >= 0 to avoid double-cover ambiguity. */
    fun canonical(): Quaternion = if (w < 0.0) -this else this

    fun dot(o: Quaternion) = w * o.w + x * o.x + y * o.y + z * o.z

    companion object {
        val IDENTITY = Quaternion(1.0, 0.0, 0.0, 0.0)

        /** Quaternion from rotation about a unit axis by [angleRad]. */
        fun fromAxisAngle(axis: Vec3, angleRad: Double): Quaternion {
            val a = axis.normalized()
            val h = angleRad * 0.5
            val s = sin(h)
            return Quaternion(cos(h), a.x * s, a.y * s, a.z * s)
        }

        /** Spherical linear interpolation between [a] and [b] at parameter [t] in [0,1]. */
        fun slerp(a: Quaternion, b: Quaternion, t: Double): Quaternion {
            val an = a.normalized()
            var bn = b.normalized()
            var cosTheta = an.dot(bn)
            if (cosTheta < 0.0) {
                bn = -bn
                cosTheta = -cosTheta
            }
            // Fall back to LERP for nearly-parallel quaternions to avoid div-by-zero.
            if (cosTheta > 0.9995) {
                val r = Quaternion(
                    an.w + t * (bn.w - an.w),
                    an.x + t * (bn.x - an.x),
                    an.y + t * (bn.y - an.y),
                    an.z + t * (bn.z - an.z),
                )
                return r.normalized()
            }
            val theta = acos(cosTheta.coerceIn(-1.0, 1.0))
            val sinTheta = sin(theta)
            val wa = sin((1.0 - t) * theta) / sinTheta
            val wb = sin(t * theta) / sinTheta
            return Quaternion(
                wa * an.w + wb * bn.w,
                wa * an.x + wb * bn.x,
                wa * an.y + wb * bn.y,
                wa * an.z + wb * bn.z,
            )
        }
    }
}

fun Quaternion.isFinite() =
    w.isFinite() && x.isFinite() && y.isFinite() && z.isFinite()

fun Quaternion.approxEquals(o: Quaternion, eps: Double = 1e-9): Boolean {
    val a = canonical()
    val b = o.canonical()
    return (a.w - b.w).absoluteValue < eps &&
        (a.x - b.x).absoluteValue < eps &&
        (a.y - b.y).absoluteValue < eps &&
        (a.z - b.z).absoluteValue < eps
}
