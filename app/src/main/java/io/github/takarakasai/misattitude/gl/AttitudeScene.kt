package io.github.takarakasai.misattitude.gl

import android.content.Context
import io.github.takarakasai.misattitude.domain.BodyShape
import io.github.takarakasai.misattitude.domain.Handedness
import io.github.takarakasai.misattitude.domain.Quaternion
import io.github.takarakasai.misattitude.domain.UpAxis
import io.github.takarakasai.misattitude.domain.WorldConvention
import com.google.android.filament.Camera
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.Material
import com.google.android.filament.MaterialInstance
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.Skybox
import com.google.android.filament.View
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Owns the Filament Engine, Scene, View, Camera, and all scene meshes.
 * Drives one frame per Choreographer tick from the FilamentSurfaceView.
 *
 * Rendering layout
 * ----------------
 *   - Solid cube + solid axis arrows -> opaque material (writes depth)
 *   - Ghost wireframe + faded step axes + axis labels -> transparent material
 *
 * Color palette is intentionally muted ("monolithic") so the body cube reads as a
 * neutral object and the axes are the focal element. Colors are RGB only, alpha
 * is supplied separately when meshes are built.
 */
class AttitudeScene(context: Context) {

    val engine: Engine = Engine.create()
    val renderer: Renderer = engine.createRenderer()
    val scene: Scene = engine.createScene()
    val view: View = engine.createView()
    val cameraEntity: Int = EntityManager.get().create()
    val camera: Camera = engine.createCamera(cameraEntity)

    private val opaqueMaterial: Material = loadMaterial(context, "vertex_color_opaque")
    private val transparentMaterial: Material = loadMaterial(context, "vertex_color")
    private val opaqueInstance: MaterialInstance = opaqueMaterial.createInstance()
    private val ghostInstance: MaterialInstance = transparentMaterial.createInstance()
    private val stepInstance: MaterialInstance = transparentMaterial.createInstance()
    private val labelInstance: MaterialInstance = transparentMaterial.createInstance()
    // Cube body uses a separate transparent instance (alpha 0.30) so the origin
    // and the body axes passing through it are clearly visible.
    private val cubeInstance: MaterialInstance = transparentMaterial.createInstance()

    // Body axes: muted but clearly distinguishable hues. Saturation kept low so they
    // sit calmly on the dark background instead of vibrating against it.
    private val bodyAxisColors = arrayOf(
        floatArrayOf(0.82f, 0.42f, 0.34f),  // X: terracotta
        floatArrayOf(0.55f, 0.72f, 0.45f),  // Y: sage green
        floatArrayOf(0.42f, 0.58f, 0.82f),  // Z: dusty steel blue
    )
    // World axes: same hues but desaturated toward neutral and dimmer, so they
    // read as a "reference frame" while the body axes dominate visually.
    private val worldAxisColors = arrayOf(
        floatArrayOf(0.62f, 0.50f, 0.46f),
        floatArrayOf(0.52f, 0.60f, 0.50f),
        floatArrayOf(0.48f, 0.54f, 0.62f),
    )

    // Camera setup. Mutable because the up-axis / handedness toggle rewrites it.
    // Stored so labels can compute billboard orientation each frame.
    private val cameraPos = floatArrayOf(2.4f, 1.9f, 2.6f)
    private val worldUp = floatArrayOf(0f, 1f, 0f)
    private var currentConvention: WorldConvention = WorldConvention.GraphicsDefault
    /** Latest body attitude — kept so label transforms can be refreshed when the convention changes. */
    private var lastBodyAttitude: Quaternion = Quaternion.IDENTITY

    // Geometry lengths — the labels need to be placed slightly beyond each tip.
    private val bodyAxisLength = 1.05f
    private val worldAxisLength = 1.45f
    private val labelOffset = 0.10f  // pushed outward from the tip a bit so it sits clear of the cone

    // World-frame reference axes (drawn at identity, slightly larger than body axes).
    private val worldAxes = Meshes.buildAxisArrows(
        engine, opaqueInstance,
        length = worldAxisLength,
        shaftRadius = 0.012f, tipRadius = 0.040f, tipLength = 0.16f,
        colors = worldAxisColors, alpha = 1.0f,
    )

    // Body shapes — only one is in the scene at a time. All use the body's quaternion
    // for their transform, so swapping is just an addEntity / removeEntity dance.
    private val bodyCube = Meshes.buildColoredCube(engine, cubeInstance, size = 0.55f, alpha = 0.30f)
    private val bodyCapsule = Meshes.buildCapsule(engine, opaqueInstance)
    private val bodyTeapot = Meshes.buildTeapot(engine, opaqueInstance, scale = 1.0f)
    // Spot loads from assets (OBJ + PNG); construction happens once at scene
    // init, costs ~50-100ms. Texture is freed (recycle()) inside buildSpot.
    private val bodySpot = Meshes.buildSpot(engine, opaqueInstance, context.assets, scale = 1.0f)
    private var currentBody: BodyShape = BodyShape.Cube

    private fun bodyMeshFor(shape: BodyShape): Mesh = when (shape) {
        BodyShape.Cube -> bodyCube
        BodyShape.Capsule -> bodyCapsule
        BodyShape.Teapot -> bodyTeapot
        BodyShape.Spot -> bodySpot
    }

    /**
     * "Stand-up" rotation that maps a Y-up-designed body mesh into a Z-up world.
     * Sends mesh-local +Y → world +Z (mesh's "up" goes to world up) and
     * mesh-local +Z → world +X (mesh's "front" becomes the robotics +X "forward"),
     * which is exactly a 120° rotation about the (1,1,1)/√3 axis →
     * quaternion (0.5, 0.5, 0.5, 0.5).
     *
     * Applied only to body shapes that have an obvious "up" / "front" (Capsule,
     * Teapot, Spot); the cube is rotationally featureless so it doesn't get
     * this offset.
     */
    private val zUpStandOffset = Quaternion(0.5, 0.5, 0.5, 0.5)

    /** Pose offset to apply to the body MESH only (not the body axes / labels) so that
     *  visually-meaningful shapes like Spot sit upright in the active world convention. */
    private fun bodyShapeOffset(): Quaternion {
        // All Y-up-designed shapes (Capsule, Teapot, Spot) need the Z-up
        // standup rotation when the active world convention is Z-up. The cube
        // has no intrinsic up direction so it doesn't get this offset.
        val needsOffset = (currentBody == BodyShape.Capsule ||
            currentBody == BodyShape.Teapot ||
            currentBody == BodyShape.Spot) &&
            currentConvention.upAxis == UpAxis.Z
        return if (needsOffset) zUpStandOffset else Quaternion.IDENTITY
    }

    // Body-attached axis arrows (shared across body shapes).
    private val bodyAxes = Meshes.buildAxisArrows(
        engine, opaqueInstance,
        length = bodyAxisLength,
        shaftRadius = 0.018f, tipRadius = 0.060f, tipLength = 0.18f,
        colors = bodyAxisColors, alpha = 1.0f,
    )

    // Faded body axes shown after step 1 / step 2 of the Euler decomposition.
    private val step1Axes = Meshes.buildAxisArrows(
        engine, stepInstance,
        length = 0.95f,
        shaftRadius = 0.012f, tipRadius = 0.040f, tipLength = 0.13f,
        colors = bodyAxisColors, alpha = 0.40f,
    )
    private val step2Axes = Meshes.buildAxisArrows(
        engine, stepInstance,
        length = 0.95f,
        shaftRadius = 0.012f, tipRadius = 0.040f, tipLength = 0.13f,
        colors = bodyAxisColors, alpha = 0.40f,
    )

    // Ghost wireframe used for the Slerp-vs-EulerLERP comparison.
    private val ghostCube = Meshes.buildCubeWireframe(
        engine, ghostInstance, size = 0.55f,
        r = 0.92f, g = 0.92f, b = 0.95f, alpha = 0.55f,
    )

    // Axis-tip labels. Body labels use the body axis colors (saturated for readability);
    // world labels use a uniform soft white so they read as "reference" markers.
    private val bodyLabels = arrayOf(
        Meshes.buildLetter(engine, labelInstance, Meshes.LETTER_X, bodyAxisColors[0], alpha = 0.95f),
        Meshes.buildLetter(engine, labelInstance, Meshes.LETTER_Y, bodyAxisColors[1], alpha = 0.95f),
        Meshes.buildLetter(engine, labelInstance, Meshes.LETTER_Z, bodyAxisColors[2], alpha = 0.95f),
    )
    private val worldLabels = arrayOf(
        Meshes.buildLetter(engine, labelInstance, Meshes.LETTER_X, worldAxisColors[0], alpha = 0.80f),
        Meshes.buildLetter(engine, labelInstance, Meshes.LETTER_Y, worldAxisColors[1], alpha = 0.80f),
        Meshes.buildLetter(engine, labelInstance, Meshes.LETTER_Z, worldAxisColors[2], alpha = 0.80f),
    )

    private var stepsVisible = false
    private var ghostVisible = false

    init {
        view.scene = scene
        view.camera = camera
        view.blendMode = View.BlendMode.OPAQUE
        // Unlit rendering doesn't benefit from tone-mapping; disabling post keeps
        // colors literal and avoids the HDR→LDR gamut squash.
        view.isPostProcessingEnabled = false

        // Charcoal background. Slightly cool, low luminance, gives a "monolithic" feel.
        scene.skybox = Skybox.Builder()
            .color(0.07f, 0.08f, 0.10f, 1.0f)
            .build(engine)

        scene.addEntity(worldAxes.entity)
        scene.addEntity(bodyMeshFor(currentBody).entity)
        scene.addEntity(bodyAxes.entity)
        for (m in bodyLabels) scene.addEntity(m.entity)
        for (m in worldLabels) scene.addEntity(m.entity)

        applyCurrentConvention()
        pushCamera()
    }

    /**
     * Push the current `cameraPos` / `worldUp` to the Filament camera and refresh
     * any view-dependent geometry (label billboards). Call after any camera edit.
     */
    private fun pushCamera() {
        camera.lookAt(
            cameraPos[0].toDouble(), cameraPos[1].toDouble(), cameraPos[2].toDouble(),
            0.0, 0.0, 0.0,
            worldUp[0].toDouble(), worldUp[1].toDouble(), worldUp[2].toDouble(),
        )
        // Labels billboard against camera position, so they need re-orienting.
        updateWorldLabels()
        updateBodyLabels(lastBodyAttitude)
    }

    /** Switch between graphics (Y-up) / robotics (Z-up) and right- vs left-handed visualization.
     *  Resets any user orbit/zoom — gestures are relative to the convention's default vantage. */
    fun setConvention(c: WorldConvention) {
        if (c == currentConvention) return
        currentConvention = c
        applyCurrentConvention()
        pushCamera()
        // Body shape offset depends on the new convention — re-apply.
        applyTransform(
            bodyMeshFor(currentBody).entity,
            quaternionToColumnMajor4x4(lastBodyAttitude * bodyShapeOffset()),
        )
    }

    // -----------------------------------------------------------------
    // Touch-driven camera orbit / zoom
    //
    // The Filament camera always looks at the origin and uses [worldUp]; a gesture
    // simply repositions the eye on the sphere of radius |eye|. Drag rotates the
    // eye around worldUp (azimuth) and around the camera-right axis (elevation).
    // Pinch scales |eye| (zoom). The convention's preferred eye is restored on
    // setConvention.
    // -----------------------------------------------------------------

    /** Rotate the camera around the world-up axis by [deltaRad] (positive = CCW from above). */
    fun orbitAzimuth(deltaRad: Double) {
        val ux = worldUp[0].toDouble(); val uy = worldUp[1].toDouble(); val uz = worldUp[2].toDouble()
        val rotated = rotateAroundAxis(
            cameraPos[0].toDouble(), cameraPos[1].toDouble(), cameraPos[2].toDouble(),
            ux, uy, uz, deltaRad,
        )
        cameraPos[0] = rotated[0].toFloat()
        cameraPos[1] = rotated[1].toFloat()
        cameraPos[2] = rotated[2].toFloat()
        pushCamera()
    }

    /** Tilt the camera up/down by [deltaRad]. Clamped near the poles to avoid flipping. */
    fun orbitElevation(deltaRad: Double) {
        // Right axis = normalize(worldUp × eye)
        val ux = worldUp[0].toDouble(); val uy = worldUp[1].toDouble(); val uz = worldUp[2].toDouble()
        val ex = cameraPos[0].toDouble(); val ey = cameraPos[1].toDouble(); val ez = cameraPos[2].toDouble()
        val rx0 = uy * ez - uz * ey
        val ry0 = uz * ex - ux * ez
        val rz0 = ux * ey - uy * ex
        val rlen = sqrt(rx0 * rx0 + ry0 * ry0 + rz0 * rz0)
        if (rlen < 1e-6) return  // already at pole — bail out cleanly

        val rx = rx0 / rlen; val ry = ry0 / rlen; val rz = rz0 / rlen
        val rotated = rotateAroundAxis(ex, ey, ez, rx, ry, rz, deltaRad)

        // Clamp: never let elevation get within ~5° of the up/down poles, where
        // the camera frame degenerates and labels start spinning.
        val newR = sqrt(rotated[0] * rotated[0] + rotated[1] * rotated[1] + rotated[2] * rotated[2])
        val cosToUp = (rotated[0] * ux + rotated[1] * uy + rotated[2] * uz) / newR
        if (abs(cosToUp) > 0.985) return

        cameraPos[0] = rotated[0].toFloat()
        cameraPos[1] = rotated[1].toFloat()
        cameraPos[2] = rotated[2].toFloat()
        pushCamera()
    }

    /** Multiply camera distance by [scale]. Clamps so the eye never enters the body
     *  bounding box and never flies arbitrarily far away. */
    fun zoom(scale: Double) {
        val ex = cameraPos[0].toDouble(); val ey = cameraPos[1].toDouble(); val ez = cameraPos[2].toDouble()
        val r = sqrt(ex * ex + ey * ey + ez * ez)
        val newR = (r * scale).coerceIn(1.5, 12.0)
        if (r < 1e-6) return
        val k = newR / r
        cameraPos[0] = (ex * k).toFloat()
        cameraPos[1] = (ey * k).toFloat()
        cameraPos[2] = (ez * k).toFloat()
        pushCamera()
    }

    /** Reset the camera to the convention's default vantage. */
    fun resetCamera() {
        applyCurrentConvention()
        pushCamera()
    }

    /** Rodrigues rotation: rotate (vx,vy,vz) by [angle] around unit axis (ax,ay,az). */
    private fun rotateAroundAxis(
        vx: Double, vy: Double, vz: Double,
        ax: Double, ay: Double, az: Double,
        angle: Double,
    ): DoubleArray {
        val c = cos(angle)
        val s = sin(angle)
        val dot = ax * vx + ay * vy + az * vz
        val cx = ay * vz - az * vy
        val cy = az * vx - ax * vz
        val cz = ax * vy - ay * vx
        return doubleArrayOf(
            vx * c + cx * s + ax * dot * (1 - c),
            vy * c + cy * s + ay * dot * (1 - c),
            vz * c + cz * s + az * dot * (1 - c),
        )
    }

    /**
     * Set [cameraPos] and [worldUp] for the active [currentConvention] and push them
     * to the Filament camera. The body / world axis geometry itself is unchanged —
     * +X always means +X — only the camera's vantage point and screen up vary.
     *
     *   Y-up RH (graphics):  eye at front-right-up, +Y on screen up, +Z toward viewer.
     *   Y-up LH (DirectX-ish): mirror Z so +Z points into the screen.
     *   Z-up RH (robotics):  eye placed so the world appears as if rotated −90° about
     *                        Z relative to the graphics view — i.e. world +X projects
     *                        to screen-LEFT and world +Y to screen-RIGHT, giving the
     *                        canonical "robot from front-left" view used in ROS docs.
     *                        Equivalent to rotating the original (X-forward) Z-up
     *                        camera by +90° about world Z.
     *   Z-up LH:             mirror across the Z axis (camera at -X side).
     */
    private fun applyCurrentConvention() {
        when (currentConvention.upAxis) {
            UpAxis.Y -> {
                worldUp[0] = 0f; worldUp[1] = 1f; worldUp[2] = 0f
                cameraPos[0] = 2.4f
                cameraPos[1] = 1.9f
                cameraPos[2] = if (currentConvention.handedness == Handedness.RightHanded) 2.6f else -2.6f
            }
            UpAxis.Z -> {
                worldUp[0] = 0f; worldUp[1] = 0f; worldUp[2] = 1f
                // RH: camera at +X +Y +Z. World +X is left of screen, +Y is right —
                // matches "view of a robot rotated −90° about Z" relative to the
                // X-forward camera. LH: mirror to -X side.
                cameraPos[0] = if (currentConvention.handedness == Handedness.RightHanded) 2.4f else -2.4f
                cameraPos[1] = 2.6f
                cameraPos[2] = 1.9f
            }
        }
        // Caller is expected to follow up with pushCamera() (or rely on it being
        // called by setConvention / init).
    }

    fun resize(width: Int, height: Int) {
        view.viewport = com.google.android.filament.Viewport(0, 0, width, height)
        val aspect = if (height == 0) 1.0 else width.toDouble() / height
        camera.setProjection(45.0, aspect, 0.05, 100.0, Camera.Fov.VERTICAL)
    }

    /** Update the orientation of the active body shape + body axes + body labels. */
    fun setBodyAttitude(q: Quaternion) {
        lastBodyAttitude = q
        val axisXform = quaternionToColumnMajor4x4(q)
        applyTransform(bodyAxes.entity, axisXform)
        // Body MESH gets an additional shape-offset so e.g. Spot stands upright in
        // Z-up worlds. (q * offset) means: rotate the mesh by `offset` in body
        // local coords first, then apply the user's attitude. Body axes and labels
        // do NOT get the offset — at q=identity body axes still align with world.
        applyTransform(bodyMeshFor(currentBody).entity, quaternionToColumnMajor4x4(q * bodyShapeOffset()))
        updateBodyLabels(q)
    }

    /** Swap which body shape is currently rendered (cube / capsule / teapot / spot). */
    fun setBodyShape(shape: BodyShape) {
        if (shape == currentBody) return
        scene.removeEntity(bodyMeshFor(currentBody).entity)
        currentBody = shape
        val next = bodyMeshFor(currentBody)
        scene.addEntity(next.entity)
        // Re-apply the current attitude (with the new shape's offset) to the new body.
        applyTransform(next.entity, quaternionToColumnMajor4x4(lastBodyAttitude * bodyShapeOffset()))
    }

    /** Show or hide the two intermediate-frame axes during Euler step animation. */
    fun setStepAttitudes(q1: Quaternion?, q2: Quaternion?) {
        val want = q1 != null && q2 != null
        if (want != stepsVisible) {
            if (want) {
                scene.addEntity(step1Axes.entity)
                scene.addEntity(step2Axes.entity)
            } else {
                scene.removeEntity(step1Axes.entity)
                scene.removeEntity(step2Axes.entity)
            }
            stepsVisible = want
        }
        if (q1 != null && q2 != null) {
            applyTransform(step1Axes.entity, quaternionToColumnMajor4x4(q1))
            applyTransform(step2Axes.entity, quaternionToColumnMajor4x4(q2))
        }
    }

    /** Show or hide the ghost wireframe. Pass null to hide. */
    fun setGhostAttitude(q: Quaternion?) {
        val want = q != null
        if (want != ghostVisible) {
            if (want) scene.addEntity(ghostCube.entity) else scene.removeEntity(ghostCube.entity)
            ghostVisible = want
        }
        if (q != null) {
            applyTransform(ghostCube.entity, quaternionToColumnMajor4x4(q))
        }
    }

    private fun applyTransform(entity: Int, xform: FloatArray) {
        val tcm = engine.transformManager
        if (tcm.getInstance(entity) == 0) tcm.create(entity)
        tcm.setTransform(tcm.getInstance(entity), xform)
    }

    private fun updateWorldLabels() {
        val tips = arrayOf(
            floatArrayOf(worldAxisLength + labelOffset, 0f, 0f),
            floatArrayOf(0f, worldAxisLength + labelOffset, 0f),
            floatArrayOf(0f, 0f, worldAxisLength + labelOffset),
        )
        for (i in 0..2) {
            applyTransform(worldLabels[i].entity, billboardTransform(tips[i]))
        }
    }

    private fun updateBodyLabels(q: Quaternion) {
        val r = bodyAxisLength + labelOffset
        val tipsLocal = arrayOf(
            floatArrayOf(r, 0f, 0f),
            floatArrayOf(0f, r, 0f),
            floatArrayOf(0f, 0f, r),
        )
        for (i in 0..2) {
            val tipWorld = rotateVecByQuat(tipsLocal[i], q)
            applyTransform(bodyLabels[i].entity, billboardTransform(tipWorld))
        }
    }

    /**
     * Build a column-major 4x4 transform that places the label's local origin at
     * [tip] in world space and rotates it so the label's local +Z faces the camera.
     * Result: local +X = screen-right, +Y = screen-up. Letter geometry lives in the
     * XY plane so it appears upright and front-facing.
     */
    private fun billboardTransform(tip: FloatArray): FloatArray {
        var fx = cameraPos[0] - tip[0]
        var fy = cameraPos[1] - tip[1]
        var fz = cameraPos[2] - tip[2]
        val flen = sqrt(fx * fx + fy * fy + fz * fz)
        if (flen > 1e-6f) { fx /= flen; fy /= flen; fz /= flen }

        // If forward is nearly parallel to worldUp, fall back to a horizontal up.
        val ux: Float; val uy: Float; val uz: Float
        val parallel = abs(fx * worldUp[0] + fy * worldUp[1] + fz * worldUp[2]) > 0.999f
        if (parallel) {
            // Pick any perpendicular up. Use world +X if we were aligned with +Y, else world +Y.
            ux = if (worldUp[1] > 0.5f) 1f else 0f
            uy = if (worldUp[1] > 0.5f) 0f else 1f
            uz = 0f
        } else {
            ux = worldUp[0]; uy = worldUp[1]; uz = worldUp[2]
        }

        // right = normalize(cross(up, forward))
        var rx = uy * fz - uz * fy
        var ry = uz * fx - ux * fz
        var rz = ux * fy - uy * fx
        val rlen = sqrt(rx * rx + ry * ry + rz * rz)
        if (rlen > 1e-6f) { rx /= rlen; ry /= rlen; rz /= rlen }

        // newUp = cross(forward, right)
        val nx = fy * rz - fz * ry
        val ny = fz * rx - fx * rz
        val nz = fx * ry - fy * rx

        val out = FloatArray(16)
        // Column 0 = right
        out[0] = rx; out[1] = ry; out[2] = rz; out[3] = 0f
        // Column 1 = up
        out[4] = nx; out[5] = ny; out[6] = nz; out[7] = 0f
        // Column 2 = forward
        out[8] = fx; out[9] = fy; out[10] = fz; out[11] = 0f
        // Column 3 = translation
        out[12] = tip[0]; out[13] = tip[1]; out[14] = tip[2]; out[15] = 1f
        return out
    }

    /** Rotate vector v by unit quaternion q. */
    private fun rotateVecByQuat(v: FloatArray, q: Quaternion): FloatArray {
        val n = q.normalized()
        val qw = n.w.toFloat(); val qx = n.x.toFloat(); val qy = n.y.toFloat(); val qz = n.z.toFloat()
        // v + 2 * cross(q.xyz, cross(q.xyz, v) + q.w * v)
        val tx = 2f * (qy * v[2] - qz * v[1])
        val ty = 2f * (qz * v[0] - qx * v[2])
        val tz = 2f * (qx * v[1] - qy * v[0])
        return floatArrayOf(
            v[0] + qw * tx + (qy * tz - qz * ty),
            v[1] + qw * ty + (qz * tx - qx * tz),
            v[2] + qw * tz + (qx * ty - qy * tx),
        )
    }

    fun destroy() {
        scene.skybox?.let { engine.destroySkybox(it) }
        val all = mutableListOf(
            worldAxes, bodyCube, bodyCapsule, bodyTeapot, bodySpot,
            bodyAxes, step1Axes, step2Axes, ghostCube,
        )
        all.addAll(bodyLabels)
        all.addAll(worldLabels)
        all.forEach { it.destroy(engine) }
        engine.destroyMaterialInstance(opaqueInstance)
        engine.destroyMaterialInstance(ghostInstance)
        engine.destroyMaterialInstance(stepInstance)
        engine.destroyMaterialInstance(labelInstance)
        engine.destroyMaterialInstance(cubeInstance)
        engine.destroyMaterial(opaqueMaterial)
        engine.destroyMaterial(transparentMaterial)
        engine.destroyCameraComponent(cameraEntity)
        EntityManager.get().destroy(cameraEntity)
        engine.destroyView(view)
        engine.destroyScene(scene)
        engine.destroyRenderer(renderer)
        engine.destroy()
    }

    private fun loadMaterial(context: Context, baseName: String): Material {
        val bytes = context.assets.open("$baseName.filamat").use { it.readBytes() }
        val buf = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
        buf.put(bytes); buf.flip()
        return Material.Builder().payload(buf, buf.remaining()).build(engine)
    }
}

/** Convert a unit quaternion to a column-major 4x4 transform (Filament's expected layout). */
fun quaternionToColumnMajor4x4(q: Quaternion): FloatArray {
    val n = q.normalized()
    val w = n.w.toFloat(); val x = n.x.toFloat(); val y = n.y.toFloat(); val z = n.z.toFloat()
    val xx = x * x; val yy = y * y; val zz = z * z
    val xy = x * y; val xz = x * z; val yz = y * z
    val wx = w * x; val wy = w * y; val wz = w * z
    val out = FloatArray(16)
    // Column 0
    out[0] = 1f - 2f * (yy + zz); out[1] = 2f * (xy + wz); out[2] = 2f * (xz - wy); out[3] = 0f
    // Column 1
    out[4] = 2f * (xy - wz);      out[5] = 1f - 2f * (xx + zz); out[6] = 2f * (yz + wx); out[7] = 0f
    // Column 2
    out[8] = 2f * (xz + wy);      out[9] = 2f * (yz - wx);      out[10] = 1f - 2f * (xx + yy); out[11] = 0f
    // Column 3
    out[12] = 0f; out[13] = 0f; out[14] = 0f; out[15] = 1f
    return out
}
