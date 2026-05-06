package io.github.takarakasai.misattitude.domain

/** Angles in radians, in the order dictated by the paired [EulerConvention]. */
data class EulerAngles(val a1: Double, val a2: Double, val a3: Double) {
    companion object {
        val ZERO = EulerAngles(0.0, 0.0, 0.0)
    }
}
