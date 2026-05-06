package io.github.takarakasai.misattitude.gl

import android.content.Context
import android.util.AttributeSet
import android.view.Choreographer
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.Surface
import android.view.SurfaceView
import io.github.takarakasai.misattitude.domain.BodyShape
import io.github.takarakasai.misattitude.domain.Quaternion
import io.github.takarakasai.misattitude.domain.WorldConvention
import com.google.android.filament.SwapChain
import com.google.android.filament.android.UiHelper
import kotlin.math.PI

/**
 * SurfaceView that hosts a Filament-rendered AttitudeScene. The scene's body / step / ghost
 * orientations are driven by the public setters below, called from the ViewModel.
 */
class FilamentSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : SurfaceView(context, attrs) {

    private val attitudeScene = AttitudeScene(context)
    private var swapChain: SwapChain? = null

    private val uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK).apply {
        renderCallback = object : UiHelper.RendererCallback {
            override fun onNativeWindowChanged(surface: Surface) {
                swapChain?.let { attitudeScene.engine.destroySwapChain(it) }
                swapChain = attitudeScene.engine.createSwapChain(surface)
            }
            override fun onDetachedFromSurface() {
                swapChain?.let {
                    attitudeScene.engine.destroySwapChain(it)
                    attitudeScene.engine.flushAndWait()
                    swapChain = null
                }
            }
            override fun onResized(width: Int, height: Int) {
                attitudeScene.resize(width, height)
            }
        }
    }

    private val choreographer = Choreographer.getInstance()
    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            choreographer.postFrameCallback(this)
            val sc = swapChain ?: return
            if (attitudeScene.renderer.beginFrame(sc, frameTimeNanos)) {
                attitudeScene.renderer.render(attitudeScene.view)
                attitudeScene.renderer.endFrame()
            }
        }
    }

    // -----------------------------------------------------------------
    // Touch-driven camera control: drag = orbit (azimuth/elevation), pinch = zoom.
    // The mappings below are calibrated for portrait phones — a full sweep across
    // the canvas rotates ~180° (PI), which feels natural without overshooting.
    // -----------------------------------------------------------------
    private val dragGesture = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            val w = width.coerceAtLeast(1).toDouble()
            val h = height.coerceAtLeast(1).toDouble()
            // Drag-right (distanceX < 0): the body should appear to follow the finger,
            // i.e., the body's right face rolls into view. That requires the camera to
            // orbit CW around worldUp from above. With distanceX < 0 we want a negative
            // angle (CW), so we use distanceX/w directly (no sign flip).
            attitudeScene.orbitAzimuth(distanceX / w * PI)
            // Drag-up (positive distanceY) → eye tilts up.
            attitudeScene.orbitElevation(distanceY / h * PI)
            return true
        }
        override fun onDown(e: MotionEvent): Boolean = true
        override fun onDoubleTap(e: MotionEvent): Boolean {
            // Double-tap to reset the camera to the convention's default vantage.
            attitudeScene.resetCamera()
            return true
        }
    })

    private val pinchGesture = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            // scaleFactor > 1 (pinch-out) means "zoom in" → reduce camera radius.
            attitudeScene.zoom(1.0 / detector.scaleFactor.toDouble())
            return true
        }
    })

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Always feed both detectors; ScaleGestureDetector ignores single-pointer events
        // and GestureDetector ignores multi-pointer scrolls — they coexist cleanly.
        pinchGesture.onTouchEvent(event)
        if (!pinchGesture.isInProgress) dragGesture.onTouchEvent(event)
        return true
    }

    init {
        uiHelper.attachTo(this)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        choreographer.postFrameCallback(frameCallback)
    }

    override fun onDetachedFromWindow() {
        choreographer.removeFrameCallback(frameCallback)
        super.onDetachedFromWindow()
    }

    fun shutdown() {
        choreographer.removeFrameCallback(frameCallback)
        uiHelper.detach()
        attitudeScene.destroy()
    }

    // --- public attitude setters (driven by the ViewModel) ---

    fun setBodyAttitude(q: Quaternion) = attitudeScene.setBodyAttitude(q)

    fun setStepAttitudes(q1: Quaternion?, q2: Quaternion?) =
        attitudeScene.setStepAttitudes(q1, q2)

    fun setGhostAttitude(q: Quaternion?) = attitudeScene.setGhostAttitude(q)

    fun setWorldConvention(c: WorldConvention) = attitudeScene.setConvention(c)

    fun setBodyShape(shape: BodyShape) = attitudeScene.setBodyShape(shape)
}
