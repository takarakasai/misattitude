package io.github.takarakasai.misattitude.domain

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 3x3 rotation matrix in row-major storage. m[r][c] is row r, column c.
 * Acts on column vectors: v' = M * v.
 */
data class RotationMatrix(val m: Array<DoubleArray>) {

    init {
        require(m.size == 3 && m.all { it.size == 3 }) { "RotationMatrix must be 3x3" }
    }

    operator fun get(r: Int, c: Int): Double = m[r][c]

    operator fun times(o: RotationMatrix): RotationMatrix {
        val r = Array(3) { DoubleArray(3) }
        for (i in 0..2) for (j in 0..2) {
            var s = 0.0
            for (k in 0..2) s += m[i][k] * o.m[k][j]
            r[i][j] = s
        }
        return RotationMatrix(r)
    }

    fun transpose(): RotationMatrix {
        val r = Array(3) { DoubleArray(3) }
        for (i in 0..2) for (j in 0..2) r[i][j] = m[j][i]
        return RotationMatrix(r)
    }

    /** Project the (possibly non-orthogonal) matrix to the nearest rotation via SVD-free iteration. */
    fun orthonormalized(): RotationMatrix {
        // Gram-Schmidt on columns is sufficient for small numerical drift.
        val c0 = doubleArrayOf(m[0][0], m[1][0], m[2][0]).normalize()
        var c1 = doubleArrayOf(m[0][1], m[1][1], m[2][1])
        val d = c0[0] * c1[0] + c0[1] * c1[1] + c0[2] * c1[2]
        c1 = doubleArrayOf(c1[0] - d * c0[0], c1[1] - d * c0[1], c1[2] - d * c0[2]).normalize()
        val c2 = doubleArrayOf(
            c0[1] * c1[2] - c0[2] * c1[1],
            c0[2] * c1[0] - c0[0] * c1[2],
            c0[0] * c1[1] - c0[1] * c1[0],
        )
        return RotationMatrix(
            arrayOf(
                doubleArrayOf(c0[0], c1[0], c2[0]),
                doubleArrayOf(c0[1], c1[1], c2[1]),
                doubleArrayOf(c0[2], c1[2], c2[2]),
            )
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RotationMatrix) return false
        for (i in 0..2) for (j in 0..2) if (m[i][j] != other.m[i][j]) return false
        return true
    }

    override fun hashCode(): Int {
        var h = 0
        for (i in 0..2) for (j in 0..2) h = 31 * h + m[i][j].hashCode()
        return h
    }

    companion object {
        val IDENTITY = RotationMatrix(
            arrayOf(
                doubleArrayOf(1.0, 0.0, 0.0),
                doubleArrayOf(0.0, 1.0, 0.0),
                doubleArrayOf(0.0, 0.0, 1.0),
            )
        )

        fun rotX(a: Double): RotationMatrix {
            val c = cos(a); val s = sin(a)
            return RotationMatrix(
                arrayOf(
                    doubleArrayOf(1.0, 0.0, 0.0),
                    doubleArrayOf(0.0, c, -s),
                    doubleArrayOf(0.0, s, c),
                )
            )
        }

        fun rotY(a: Double): RotationMatrix {
            val c = cos(a); val s = sin(a)
            return RotationMatrix(
                arrayOf(
                    doubleArrayOf(c, 0.0, s),
                    doubleArrayOf(0.0, 1.0, 0.0),
                    doubleArrayOf(-s, 0.0, c),
                )
            )
        }

        fun rotZ(a: Double): RotationMatrix {
            val c = cos(a); val s = sin(a)
            return RotationMatrix(
                arrayOf(
                    doubleArrayOf(c, -s, 0.0),
                    doubleArrayOf(s, c, 0.0),
                    doubleArrayOf(0.0, 0.0, 1.0),
                )
            )
        }

        fun rotAxis(axis: Axis, a: Double): RotationMatrix = when (axis) {
            Axis.X -> rotX(a)
            Axis.Y -> rotY(a)
            Axis.Z -> rotZ(a)
        }
    }
}

private fun DoubleArray.normalize(): DoubleArray {
    val n = sqrt(this[0] * this[0] + this[1] * this[1] + this[2] * this[2])
    if (n == 0.0) return this
    return doubleArrayOf(this[0] / n, this[1] / n, this[2] / n)
}
