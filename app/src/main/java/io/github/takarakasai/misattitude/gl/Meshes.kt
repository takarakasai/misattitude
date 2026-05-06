package io.github.takarakasai.misattitude.gl

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.android.filament.Box
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.IndexBuffer
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import com.google.android.filament.VertexBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Owns GPU buffers + an entity for one drawable. Call destroy() before tearing down the engine. */
class Mesh(
    val entity: Int,
    private val vertexBuffer: VertexBuffer,
    private val indexBuffer: IndexBuffer,
) {
    fun destroy(engine: Engine) {
        engine.destroyEntity(entity)
        EntityManager.get().destroy(entity)
        engine.destroyVertexBuffer(vertexBuffer)
        engine.destroyIndexBuffer(indexBuffer)
    }
}

object Meshes {

    private const val POS_BYTES = 12      // float3 position
    private const val COL_BYTES = 16      // float4 color
    private const val STRIDE = POS_BYTES + COL_BYTES

    /**
     * Build three solid 3-D axis arrows (X, Y, Z) sharing a single mesh.
     * Each axis has a square-base shaft and a circular cone tip — far more readable than
     * 1-pixel LINES and gives the viewer an unambiguous +direction.
     */
    fun buildAxisArrows(
        engine: Engine,
        material: MaterialInstance,
        length: Float = 1.0f,
        shaftRadius: Float = 0.014f,
        tipRadius: Float = 0.045f,
        tipLength: Float = 0.14f,
        colors: Array<FloatArray>,                        // [colorX, colorY, colorZ], each rgb
        alpha: Float = 1.0f,
        shaftSegments: Int = 8,
        coneSegments: Int = 12,
    ): Mesh {
        val verts = ArrayList<Float>()
        val indices = ArrayList<Short>()

        // For each axis (X, Y, Z), pick two perpendiculars forming a right-handed basis.
        // Pairing is cyclic so that perp1 × perp2 = axis dir.
        val basis = arrayOf(
            Triple(floatArrayOf(1f, 0f, 0f), floatArrayOf(0f, 1f, 0f), floatArrayOf(0f, 0f, 1f)),
            Triple(floatArrayOf(0f, 1f, 0f), floatArrayOf(0f, 0f, 1f), floatArrayOf(1f, 0f, 0f)),
            Triple(floatArrayOf(0f, 0f, 1f), floatArrayOf(1f, 0f, 0f), floatArrayOf(0f, 1f, 0f)),
        )

        fun pushVertex(p: FloatArray, c: FloatArray) {
            verts.add(p[0]); verts.add(p[1]); verts.add(p[2])
            verts.add(c[0]); verts.add(c[1]); verts.add(c[2]); verts.add(alpha)
        }

        val shaftLen = length - tipLength
        val twoPi = 2.0 * Math.PI

        for (axisIdx in 0..2) {
            val (dir, e1, e2) = basis[axisIdx]
            val color = colors[axisIdx]
            val shaftBase = (verts.size / 7).toShort()

            // Shaft ring at start (origin)
            for (i in 0 until shaftSegments) {
                val theta = (twoPi * i / shaftSegments).toFloat()
                val cs = kotlin.math.cos(theta)
                val sn = kotlin.math.sin(theta)
                pushVertex(
                    floatArrayOf(
                        shaftRadius * (cs * e1[0] + sn * e2[0]),
                        shaftRadius * (cs * e1[1] + sn * e2[1]),
                        shaftRadius * (cs * e1[2] + sn * e2[2]),
                    ),
                    color,
                )
            }
            // Shaft ring at end
            for (i in 0 until shaftSegments) {
                val theta = (twoPi * i / shaftSegments).toFloat()
                val cs = kotlin.math.cos(theta)
                val sn = kotlin.math.sin(theta)
                pushVertex(
                    floatArrayOf(
                        shaftLen * dir[0] + shaftRadius * (cs * e1[0] + sn * e2[0]),
                        shaftLen * dir[1] + shaftRadius * (cs * e1[1] + sn * e2[1]),
                        shaftLen * dir[2] + shaftRadius * (cs * e1[2] + sn * e2[2]),
                    ),
                    color,
                )
            }
            // Shaft side triangles (doubleSided material so winding doesn't matter visually)
            for (i in 0 until shaftSegments) {
                val a = (shaftBase + i).toShort()
                val b = (shaftBase + (i + 1) % shaftSegments).toShort()
                val c = (shaftBase + shaftSegments + i).toShort()
                val d = (shaftBase + shaftSegments + (i + 1) % shaftSegments).toShort()
                indices.add(a); indices.add(b); indices.add(d)
                indices.add(a); indices.add(d); indices.add(c)
            }

            // Cone (arrowhead): tip + base center + ring
            val coneBase = (verts.size / 7).toShort()
            pushVertex(floatArrayOf(length * dir[0], length * dir[1], length * dir[2]), color)
            pushVertex(floatArrayOf(shaftLen * dir[0], shaftLen * dir[1], shaftLen * dir[2]), color)
            for (j in 0 until coneSegments) {
                val theta = (twoPi * j / coneSegments).toFloat()
                val cs = kotlin.math.cos(theta)
                val sn = kotlin.math.sin(theta)
                pushVertex(
                    floatArrayOf(
                        shaftLen * dir[0] + tipRadius * (cs * e1[0] + sn * e2[0]),
                        shaftLen * dir[1] + tipRadius * (cs * e1[1] + sn * e2[1]),
                        shaftLen * dir[2] + tipRadius * (cs * e1[2] + sn * e2[2]),
                    ),
                    color,
                )
            }
            val tipIdx = coneBase
            val baseCenterIdx = (coneBase + 1).toShort()
            for (j in 0 until coneSegments) {
                val r0 = (coneBase + 2 + j).toShort()
                val r1 = (coneBase + 2 + (j + 1) % coneSegments).toShort()
                indices.add(tipIdx); indices.add(r0); indices.add(r1)
                indices.add(baseCenterIdx); indices.add(r1); indices.add(r0)
            }
        }

        val nVerts = verts.size / 7
        val vbBytes = ByteBuffer.allocateDirect(nVerts * STRIDE).order(ByteOrder.nativeOrder())
        for (f in verts) vbBytes.putFloat(f)
        vbBytes.flip()

        val vb = VertexBuffer.Builder()
            .vertexCount(nVerts)
            .bufferCount(1)
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, STRIDE)
            .attribute(VertexBuffer.VertexAttribute.COLOR, 0, VertexBuffer.AttributeType.FLOAT4, POS_BYTES, STRIDE)
            .build(engine)
        vb.setBufferAt(engine, 0, vbBytes)

        val ibBytes = ByteBuffer.allocateDirect(indices.size * 2).order(ByteOrder.nativeOrder())
        for (i in indices) ibBytes.putShort(i)
        ibBytes.flip()
        val ib = IndexBuffer.Builder()
            .indexCount(indices.size)
            .bufferType(IndexBuffer.Builder.IndexType.USHORT)
            .build(engine)
        ib.setBuffer(engine, ibBytes)

        val entity = EntityManager.get().create()
        RenderableManager.Builder(1)
            .boundingBox(Box(0f, 0f, 0f, length, length, length))
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vb, ib)
            .material(0, material)
            .culling(false)
            .priority(if (alpha < 1f) 6 else 4)
            .build(engine, entity)

        return Mesh(entity, vb, ib)
    }

    /**
     * Three orthogonal axes drawn as line segments from the origin to ±length along X/Y/Z.
     * Positive direction in saturated R/G/B; negative direction in a darker tint.
     */
    fun buildAxes(
        engine: Engine,
        material: MaterialInstance,
        length: Float = 1.0f,
        alpha: Float = 1.0f,
    ): Mesh {
        // 6 segments (positive and negative for each axis) = 12 vertices.
        data class V(val px: Float, val py: Float, val pz: Float, val r: Float, val g: Float, val b: Float)
        val verts = listOf(
            // +X red, -X dark red
            V(0f, 0f, 0f, 1f, 0f, 0f), V(length, 0f, 0f, 1f, 0f, 0f),
            V(0f, 0f, 0f, 0.3f, 0f, 0f), V(-length, 0f, 0f, 0.3f, 0f, 0f),
            // +Y green, -Y dark green
            V(0f, 0f, 0f, 0f, 1f, 0f), V(0f, length, 0f, 0f, 1f, 0f),
            V(0f, 0f, 0f, 0f, 0.3f, 0f), V(0f, -length, 0f, 0f, 0.3f, 0f),
            // +Z blue, -Z dark blue
            V(0f, 0f, 0f, 0.2f, 0.4f, 1f), V(0f, 0f, length, 0.2f, 0.4f, 1f),
            V(0f, 0f, 0f, 0.05f, 0.1f, 0.3f), V(0f, 0f, -length, 0.05f, 0.1f, 0.3f),
        )
        val vbBytes = ByteBuffer.allocateDirect(verts.size * STRIDE).order(ByteOrder.nativeOrder())
        for (v in verts) {
            vbBytes.putFloat(v.px); vbBytes.putFloat(v.py); vbBytes.putFloat(v.pz)
            vbBytes.putFloat(v.r); vbBytes.putFloat(v.g); vbBytes.putFloat(v.b); vbBytes.putFloat(alpha)
        }
        vbBytes.flip()

        val vb = VertexBuffer.Builder()
            .vertexCount(verts.size)
            .bufferCount(1)
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, STRIDE)
            .attribute(VertexBuffer.VertexAttribute.COLOR, 0, VertexBuffer.AttributeType.FLOAT4, POS_BYTES, STRIDE)
            .build(engine)
        vb.setBufferAt(engine, 0, vbBytes)

        val indices = ShortArray(verts.size) { it.toShort() }
        val ibBytes = ByteBuffer.allocateDirect(indices.size * 2).order(ByteOrder.nativeOrder())
        for (i in indices) ibBytes.putShort(i)
        ibBytes.flip()
        val ib = IndexBuffer.Builder()
            .indexCount(indices.size)
            .bufferType(IndexBuffer.Builder.IndexType.USHORT)
            .build(engine)
        ib.setBuffer(engine, ibBytes)

        val entity = EntityManager.get().create()
        RenderableManager.Builder(1)
            .boundingBox(Box(0f, 0f, 0f, length, length, length))
            .geometry(0, RenderableManager.PrimitiveType.LINES, vb, ib)
            .material(0, material)
            .culling(false)
            .priority(7)
            .build(engine, entity)

        return Mesh(entity, vb, ib)
    }

    /**
     * Per-face shaded cube. Faces share a single warm-neutral hue but each face has a
     * slightly different luminance, so the cube reads as a 3-D solid (corners visible)
     * while staying chromatically muted. Pairs of opposite faces are within ~10% L*
     * of each other — orientation is still readable but the cube no longer dominates
     * the scene against the colored axes.
     *
     * Face → tint:
     *   +X bright,  -X dim
     *   +Y mid-bright (top), -Y mid-dim (bottom)
     *   +Z mid, -Z dimmer-mid
     */
    fun buildColoredCube(
        engine: Engine,
        material: MaterialInstance,
        size: Float = 0.5f,
        alpha: Float = 1.0f,
    ): Mesh {
        val s = size
        // 6 faces × 4 vertices = 24 vertices, 6 × 6 = 36 indices.
        data class V(val px: Float, val py: Float, val pz: Float, val r: Float, val g: Float, val b: Float)

        // Warm neutral base, varied by face. R/G/B chosen so the cube has a slight
        // beige-gray tint rather than pure gray (more pleasant against charcoal bg).
        val faceColors = arrayOf(
            floatArrayOf(0.66f, 0.62f, 0.56f),  // +X
            floatArrayOf(0.46f, 0.43f, 0.39f),  // -X
            floatArrayOf(0.60f, 0.58f, 0.53f),  // +Y (top)
            floatArrayOf(0.40f, 0.38f, 0.34f),  // -Y (bottom)
            floatArrayOf(0.54f, 0.52f, 0.48f),  // +Z
            floatArrayOf(0.43f, 0.41f, 0.37f),  // -Z
        )

        val faces: List<Pair<List<V>, ShortArray>> = run {
            val faceList = mutableListOf<Pair<List<V>, ShortArray>>()
            val cpx = faceColors[0]; val cnx = faceColors[1]
            val cpy = faceColors[2]; val cny = faceColors[3]
            val cpz = faceColors[4]; val cnz = faceColors[5]
            // +X face
            faceList += listOf(
                V(s, -s, -s, cpx[0], cpx[1], cpx[2]), V(s,  s, -s, cpx[0], cpx[1], cpx[2]),
                V(s,  s,  s, cpx[0], cpx[1], cpx[2]), V(s, -s,  s, cpx[0], cpx[1], cpx[2]),
            ) to shortArrayOf(0, 1, 2,  0, 2, 3)
            // -X face
            faceList += listOf(
                V(-s, -s,  s, cnx[0], cnx[1], cnx[2]), V(-s,  s,  s, cnx[0], cnx[1], cnx[2]),
                V(-s,  s, -s, cnx[0], cnx[1], cnx[2]), V(-s, -s, -s, cnx[0], cnx[1], cnx[2]),
            ) to shortArrayOf(0, 1, 2,  0, 2, 3)
            // +Y face
            faceList += listOf(
                V(-s, s, -s, cpy[0], cpy[1], cpy[2]), V(-s, s,  s, cpy[0], cpy[1], cpy[2]),
                V( s, s,  s, cpy[0], cpy[1], cpy[2]), V( s, s, -s, cpy[0], cpy[1], cpy[2]),
            ) to shortArrayOf(0, 1, 2,  0, 2, 3)
            // -Y face
            faceList += listOf(
                V(-s, -s,  s, cny[0], cny[1], cny[2]), V(-s, -s, -s, cny[0], cny[1], cny[2]),
                V( s, -s, -s, cny[0], cny[1], cny[2]), V( s, -s,  s, cny[0], cny[1], cny[2]),
            ) to shortArrayOf(0, 1, 2,  0, 2, 3)
            // +Z face
            faceList += listOf(
                V(-s, -s, s, cpz[0], cpz[1], cpz[2]), V( s, -s, s, cpz[0], cpz[1], cpz[2]),
                V( s,  s, s, cpz[0], cpz[1], cpz[2]), V(-s,  s, s, cpz[0], cpz[1], cpz[2]),
            ) to shortArrayOf(0, 1, 2,  0, 2, 3)
            // -Z face
            faceList += listOf(
                V( s, -s, -s, cnz[0], cnz[1], cnz[2]), V(-s, -s, -s, cnz[0], cnz[1], cnz[2]),
                V(-s,  s, -s, cnz[0], cnz[1], cnz[2]), V( s,  s, -s, cnz[0], cnz[1], cnz[2]),
            ) to shortArrayOf(0, 1, 2,  0, 2, 3)
            faceList
        }

        val totalVerts = faces.sumOf { it.first.size }
        val totalIdx = faces.sumOf { it.second.size }

        val vbBytes = ByteBuffer.allocateDirect(totalVerts * STRIDE).order(ByteOrder.nativeOrder())
        val ibBytes = ByteBuffer.allocateDirect(totalIdx * 2).order(ByteOrder.nativeOrder())
        var baseIdx = 0
        for ((vs, ix) in faces) {
            for (v in vs) {
                vbBytes.putFloat(v.px); vbBytes.putFloat(v.py); vbBytes.putFloat(v.pz)
                vbBytes.putFloat(v.r); vbBytes.putFloat(v.g); vbBytes.putFloat(v.b); vbBytes.putFloat(alpha)
            }
            for (i in ix) ibBytes.putShort((i + baseIdx).toShort())
            baseIdx += vs.size
        }
        vbBytes.flip(); ibBytes.flip()

        val vb = VertexBuffer.Builder()
            .vertexCount(totalVerts)
            .bufferCount(1)
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, STRIDE)
            .attribute(VertexBuffer.VertexAttribute.COLOR, 0, VertexBuffer.AttributeType.FLOAT4, POS_BYTES, STRIDE)
            .build(engine)
        vb.setBufferAt(engine, 0, vbBytes)

        val ib = IndexBuffer.Builder()
            .indexCount(totalIdx)
            .bufferType(IndexBuffer.Builder.IndexType.USHORT)
            .build(engine)
        ib.setBuffer(engine, ibBytes)

        val entity = EntityManager.get().create()
        RenderableManager.Builder(1)
            .boundingBox(Box(0f, 0f, 0f, s, s, s))
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vb, ib)
            .material(0, material)
            // When the cube is translucent we want both faces visible so the
            // axes / origin show through the back face as well.
            .culling(alpha >= 1f)
            .priority(if (alpha < 1f) 6 else 4)
            .build(engine, entity)

        return Mesh(entity, vb, ib)
    }

    /**
     * Wireframe cube outline (12 line segments). Useful as a "ghost" overlay for the
     * Euler-LERP comparison view, since drawing a fully transparent solid cube can
     * obscure the primary cube due to depth-sort artifacts.
     */
    fun buildCubeWireframe(
        engine: Engine,
        material: MaterialInstance,
        size: Float = 0.5f,
        r: Float = 1f, g: Float = 1f, b: Float = 1f, alpha: Float = 0.6f,
    ): Mesh {
        val s = size
        val corners = floatArrayOf(
            -s, -s, -s,   s, -s, -s,    s,  s, -s,   -s,  s, -s,
            -s, -s,  s,   s, -s,  s,    s,  s,  s,   -s,  s,  s,
        )
        val edges = shortArrayOf(
            0, 1, 1, 2, 2, 3, 3, 0,   // back
            4, 5, 5, 6, 6, 7, 7, 4,   // front
            0, 4, 1, 5, 2, 6, 3, 7,   // verticals
        )
        val n = corners.size / 3
        val vbBytes = ByteBuffer.allocateDirect(n * STRIDE).order(ByteOrder.nativeOrder())
        for (i in 0 until n) {
            vbBytes.putFloat(corners[3 * i]); vbBytes.putFloat(corners[3 * i + 1]); vbBytes.putFloat(corners[3 * i + 2])
            vbBytes.putFloat(r); vbBytes.putFloat(g); vbBytes.putFloat(b); vbBytes.putFloat(alpha)
        }
        vbBytes.flip()
        val ibBytes = ByteBuffer.allocateDirect(edges.size * 2).order(ByteOrder.nativeOrder())
        for (e in edges) ibBytes.putShort(e)
        ibBytes.flip()

        val vb = VertexBuffer.Builder()
            .vertexCount(n)
            .bufferCount(1)
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, STRIDE)
            .attribute(VertexBuffer.VertexAttribute.COLOR, 0, VertexBuffer.AttributeType.FLOAT4, POS_BYTES, STRIDE)
            .build(engine)
        vb.setBufferAt(engine, 0, vbBytes)

        val ib = IndexBuffer.Builder()
            .indexCount(edges.size)
            .bufferType(IndexBuffer.Builder.IndexType.USHORT)
            .build(engine)
        ib.setBuffer(engine, ibBytes)

        val entity = EntityManager.get().create()
        RenderableManager.Builder(1)
            .boundingBox(Box(0f, 0f, 0f, s, s, s))
            .geometry(0, RenderableManager.PrimitiveType.LINES, vb, ib)
            .material(0, material)
            .culling(false)
            .priority(7)
            .build(engine, entity)

        return Mesh(entity, vb, ib)
    }

    // -----------------------------------------------------------------
    // Axis-tip letter labels (X / Y / Z)
    //
    // Each letter is built as a flat 2D mesh in the XY plane (z = 0) made of thin
    // quads representing the strokes. The label entity's transform is responsible
    // for positioning the letter at the axis tip and rotating it to face the
    // camera (billboarding).
    // -----------------------------------------------------------------

    /** A single straight stroke segment in the XY plane. */
    data class Stroke(val x1: Float, val y1: Float, val x2: Float, val y2: Float)

    /** Letter outlines, sized to fit roughly within ±0.12 in Y (so total height = 0.24). */
    private val LETTER_HALF_W = 0.09f
    private val LETTER_HALF_H = 0.12f

    val LETTER_X = listOf(
        Stroke(-LETTER_HALF_W, -LETTER_HALF_H,  LETTER_HALF_W,  LETTER_HALF_H),
        Stroke(-LETTER_HALF_W,  LETTER_HALF_H,  LETTER_HALF_W, -LETTER_HALF_H),
    )
    val LETTER_Y = listOf(
        Stroke(-LETTER_HALF_W,  LETTER_HALF_H, 0f, 0f),
        Stroke( LETTER_HALF_W,  LETTER_HALF_H, 0f, 0f),
        Stroke(0f, 0f, 0f, -LETTER_HALF_H),
    )
    val LETTER_Z = listOf(
        Stroke(-LETTER_HALF_W,  LETTER_HALF_H,  LETTER_HALF_W,  LETTER_HALF_H),
        Stroke( LETTER_HALF_W,  LETTER_HALF_H, -LETTER_HALF_W, -LETTER_HALF_H),
        Stroke(-LETTER_HALF_W, -LETTER_HALF_H,  LETTER_HALF_W, -LETTER_HALF_H),
    )

    /**
     * Build a 2-D letter mesh from a list of stroke segments.
     * The mesh sits in the XY plane (z = 0) centered at origin.
     * Caller transforms it (translate to tip + face camera) each frame.
     */
    fun buildLetter(
        engine: Engine,
        material: MaterialInstance,
        strokes: List<Stroke>,
        rgb: FloatArray,
        alpha: Float = 1.0f,
        strokeHalfWidth: Float = 0.024f,
    ): Mesh {
        val verts = ArrayList<Float>()
        val indices = ArrayList<Short>()

        fun pushVert(x: Float, y: Float) {
            verts.add(x); verts.add(y); verts.add(0f)
            verts.add(rgb[0]); verts.add(rgb[1]); verts.add(rgb[2]); verts.add(alpha)
        }

        for (s in strokes) {
            val dx = s.x2 - s.x1
            val dy = s.y2 - s.y1
            val len = kotlin.math.sqrt(dx * dx + dy * dy)
            // Perpendicular (rotated 90° CCW): (-dy, dx) / len, scaled to half-width.
            val nx = if (len > 1e-6f) -dy / len * strokeHalfWidth else 0f
            val ny = if (len > 1e-6f)  dx / len * strokeHalfWidth else 0f

            val baseIdx = (verts.size / 7).toShort()
            pushVert(s.x1 + nx, s.y1 + ny)   // 0: start +n
            pushVert(s.x1 - nx, s.y1 - ny)   // 1: start -n
            pushVert(s.x2 + nx, s.y2 + ny)   // 2: end +n
            pushVert(s.x2 - nx, s.y2 - ny)   // 3: end -n
            indices.add(baseIdx)
            indices.add((baseIdx + 1).toShort())
            indices.add((baseIdx + 2).toShort())
            indices.add((baseIdx + 1).toShort())
            indices.add((baseIdx + 3).toShort())
            indices.add((baseIdx + 2).toShort())
        }

        val nVerts = verts.size / 7
        val vbBytes = ByteBuffer.allocateDirect(nVerts * STRIDE).order(ByteOrder.nativeOrder())
        for (f in verts) vbBytes.putFloat(f)
        vbBytes.flip()

        val vb = VertexBuffer.Builder()
            .vertexCount(nVerts)
            .bufferCount(1)
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, STRIDE)
            .attribute(VertexBuffer.VertexAttribute.COLOR, 0, VertexBuffer.AttributeType.FLOAT4, POS_BYTES, STRIDE)
            .build(engine)
        vb.setBufferAt(engine, 0, vbBytes)

        val ibBytes = ByteBuffer.allocateDirect(indices.size * 2).order(ByteOrder.nativeOrder())
        for (i in indices) ibBytes.putShort(i)
        ibBytes.flip()
        val ib = IndexBuffer.Builder()
            .indexCount(indices.size)
            .bufferType(IndexBuffer.Builder.IndexType.USHORT)
            .build(engine)
        ib.setBuffer(engine, ibBytes)

        val entity = EntityManager.get().create()
        RenderableManager.Builder(1)
            .boundingBox(Box(0f, 0f, 0f, LETTER_HALF_W * 2, LETTER_HALF_H * 2, 0.05f))
            // Drawn last so labels sit on top of opaque cube/axes. Transparent
            // material doesn't write depth, so labels never occlude geometry.
            .priority(7)
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vb, ib)
            .material(0, material)
            .culling(false)
            .build(engine, entity)

        return Mesh(entity, vb, ib)
    }

    // -----------------------------------------------------------------
    // Generic primitive helpers — used by composite meshes (Teapot, etc.)
    // They append vertices/indices into shared buffers so all primitives can be
    // packed into a single mesh / single draw call.
    // -----------------------------------------------------------------

    private fun pushVert(
        verts: ArrayList<Float>,
        x: Float, y: Float, z: Float,
        rgb: FloatArray, alpha: Float,
    ) {
        verts.add(x); verts.add(y); verts.add(z)
        verts.add(rgb[0]); verts.add(rgb[1]); verts.add(rgb[2]); verts.add(alpha)
    }

    /**
     * Append a UV-sphere (or ellipsoid) to [verts] / [indices].
     * [colorFn] is called per vertex with (lx, ly, lz) = local offset from center,
     * letting callers do simple two-tone shading (e.g. "white belly / black back").
     */
    fun appendEllipsoid(
        verts: ArrayList<Float>,
        indices: ArrayList<Short>,
        cx: Float, cy: Float, cz: Float,
        rx: Float, ry: Float, rz: Float,
        colorFn: (lx: Float, ly: Float, lz: Float) -> FloatArray,
        rings: Int = 12,
        sectors: Int = 16,
        alpha: Float = 1.0f,
    ) {
        val baseIdx = (verts.size / 7).toShort()
        // Latitude i ∈ [0..rings] from south pole to north pole.
        // Longitude j ∈ [0..sectors] (closing seam duplicates first column).
        for (i in 0..rings) {
            val phi = (Math.PI * i / rings - Math.PI / 2).toFloat()  // -π/2 to π/2
            val sinPhi = kotlin.math.sin(phi)
            val cosPhi = kotlin.math.cos(phi)
            for (j in 0..sectors) {
                val theta = (2.0 * Math.PI * j / sectors).toFloat()
                val cosT = kotlin.math.cos(theta)
                val sinT = kotlin.math.sin(theta)
                val lx = rx * cosPhi * cosT
                val ly = ry * sinPhi
                val lz = rz * cosPhi * sinT
                val color = colorFn(lx, ly, lz)
                pushVert(verts, cx + lx, cy + ly, cz + lz, color, alpha)
            }
        }
        // Two triangles per (i, j) cell.
        val cols = sectors + 1
        for (i in 0 until rings) {
            for (j in 0 until sectors) {
                val a = (baseIdx + i * cols + j).toShort()
                val b = (baseIdx + i * cols + j + 1).toShort()
                val c = (baseIdx + (i + 1) * cols + j).toShort()
                val d = (baseIdx + (i + 1) * cols + j + 1).toShort()
                indices.add(a); indices.add(c); indices.add(b)
                indices.add(b); indices.add(c); indices.add(d)
            }
        }
    }

    /**
     * Append a cone whose [apex] is the tip and whose flat circular base is centered
     * at [base] with the given [radius]. Side faces and base disc are both emitted.
     */
    fun appendCone(
        verts: ArrayList<Float>,
        indices: ArrayList<Short>,
        ax: Float, ay: Float, az: Float,
        bx: Float, by: Float, bz: Float,
        radius: Float,
        rgb: FloatArray,
        segments: Int = 14,
        alpha: Float = 1.0f,
    ) {
        // Direction apex → base.
        val dx = bx - ax; val dy = by - ay; val dz = bz - az
        val len = kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
        if (len < 1e-6f) return
        val ndx = dx / len; val ndy = dy / len; val ndz = dz / len

        // Build an orthonormal basis perpendicular to the cone axis.
        val helper = if (kotlin.math.abs(ndy) < 0.9f) floatArrayOf(0f, 1f, 0f) else floatArrayOf(1f, 0f, 0f)
        var e1x = helper[1] * ndz - helper[2] * ndy
        var e1y = helper[2] * ndx - helper[0] * ndz
        var e1z = helper[0] * ndy - helper[1] * ndx
        val e1len = kotlin.math.sqrt(e1x * e1x + e1y * e1y + e1z * e1z)
        e1x /= e1len; e1y /= e1len; e1z /= e1len
        val e2x = ndy * e1z - ndz * e1y
        val e2y = ndz * e1x - ndx * e1z
        val e2z = ndx * e1y - ndy * e1x

        val baseIdx = (verts.size / 7).toShort()
        pushVert(verts, ax, ay, az, rgb, alpha)            // 0: apex
        pushVert(verts, bx, by, bz, rgb, alpha)            // 1: base center
        for (i in 0 until segments) {                       // 2..2+segments-1: ring
            val theta = (2.0 * Math.PI * i / segments).toFloat()
            val c = kotlin.math.cos(theta)
            val s = kotlin.math.sin(theta)
            val rxv = bx + radius * (c * e1x + s * e2x)
            val ryv = by + radius * (c * e1y + s * e2y)
            val rzv = bz + radius * (c * e1z + s * e2z)
            pushVert(verts, rxv, ryv, rzv, rgb, alpha)
        }
        for (i in 0 until segments) {
            val r0 = (baseIdx + 2 + i).toShort()
            val r1 = (baseIdx + 2 + (i + 1) % segments).toShort()
            // Side face (apex, r0, r1)
            indices.add(baseIdx); indices.add(r0); indices.add(r1)
            // Base disc (center, r1, r0)
            indices.add((baseIdx + 1).toShort()); indices.add(r1); indices.add(r0)
        }
    }

    /**
     * Append a torus (or torus segment) to [verts] / [indices].
     *
     *   Geometry: each major-segment angle θ ∈ [startAngle, endAngle] places a
     *   small minor ring of radius [minorRadius] around a center that orbits the
     *   torus center [cx, cy, cz] at radius [majorRadius] in the plane spanned
     *   by [refDir] (= θ=0 direction) and [axis] × [refDir]. The ring's normal
     *   is [axis], so the torus is "lying on" the plane perpendicular to [axis].
     *
     *   For an open torus (endAngle - startAngle < 2π), the start and end caps
     *   are NOT closed — caller is expected to abut the openings against other
     *   geometry (e.g. the teapot body where the handle attaches). This keeps
     *   the helper simple and avoids visible cap polygons inside the body.
     */
    fun appendTorus(
        verts: ArrayList<Float>,
        indices: ArrayList<Short>,
        cx: Float, cy: Float, cz: Float,
        axis: FloatArray,         // unit vector perpendicular to the ring plane
        refDir: FloatArray,       // unit vector in the ring plane (θ = 0 direction)
        majorRadius: Float,
        minorRadius: Float,
        rgb: FloatArray,
        startAngle: Float = 0f,
        endAngle: Float = (2.0 * Math.PI).toFloat(),
        majorSeg: Int = 18,
        minorSeg: Int = 10,
        alpha: Float = 1.0f,
    ) {
        // Third basis vector (ring plane) = axis × refDir.
        val sx = axis[1] * refDir[2] - axis[2] * refDir[1]
        val sy = axis[2] * refDir[0] - axis[0] * refDir[2]
        val sz = axis[0] * refDir[1] - axis[1] * refDir[0]

        val baseIdx = (verts.size / 7).toShort()
        val twoPi = (2.0 * Math.PI).toFloat()
        val cols = minorSeg  // minor angle wraps fully — column 0 ≡ column minorSeg, no extra column

        for (i in 0..majorSeg) {
            val t = i.toFloat() / majorSeg
            val theta = startAngle + (endAngle - startAngle) * t
            val cT = kotlin.math.cos(theta); val sT = kotlin.math.sin(theta)
            // Center of this minor ring.
            val mcx = cx + majorRadius * (cT * refDir[0] + sT * sx)
            val mcy = cy + majorRadius * (cT * refDir[1] + sT * sy)
            val mcz = cz + majorRadius * (cT * refDir[2] + sT * sz)
            // Minor-ring local basis: outward radial = (cT*refDir + sT*(axis×refDir)),
            // and "up" = axis. The minor ring lies in the plane spanned by these two.
            val rx = cT * refDir[0] + sT * sx
            val ry = cT * refDir[1] + sT * sy
            val rz = cT * refDir[2] + sT * sz
            for (j in 0 until minorSeg) {
                val phi = twoPi * j / minorSeg
                val cP = kotlin.math.cos(phi); val sP = kotlin.math.sin(phi)
                val px = mcx + minorRadius * (cP * rx + sP * axis[0])
                val py = mcy + minorRadius * (cP * ry + sP * axis[1])
                val pz = mcz + minorRadius * (cP * rz + sP * axis[2])
                pushVert(verts, px, py, pz, rgb, alpha)
            }
        }
        // Triangulate the strip between consecutive major rings.
        for (i in 0 until majorSeg) {
            for (j in 0 until minorSeg) {
                val jn = (j + 1) % minorSeg
                val a = (baseIdx + i * cols + j).toShort()
                val b = (baseIdx + i * cols + jn).toShort()
                val c = (baseIdx + (i + 1) * cols + j).toShort()
                val d = (baseIdx + (i + 1) * cols + jn).toShort()
                indices.add(a); indices.add(c); indices.add(b)
                indices.add(b); indices.add(c); indices.add(d)
            }
        }
    }

    /**
     * Wrap up [verts] / [indices] (interleaved float3 position + float4 color
     * vertex format) into a renderable Mesh entity using [material].
     */
    private fun finalizeMesh(
        engine: Engine,
        material: MaterialInstance,
        verts: ArrayList<Float>,
        indices: ArrayList<Short>,
        bbox: Box,
        opaque: Boolean,
    ): Mesh {
        val nVerts = verts.size / 7
        val vbBytes = ByteBuffer.allocateDirect(nVerts * STRIDE).order(ByteOrder.nativeOrder())
        for (f in verts) vbBytes.putFloat(f)
        vbBytes.flip()
        val vb = VertexBuffer.Builder()
            .vertexCount(nVerts)
            .bufferCount(1)
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, STRIDE)
            .attribute(VertexBuffer.VertexAttribute.COLOR, 0, VertexBuffer.AttributeType.FLOAT4, POS_BYTES, STRIDE)
            .build(engine)
        vb.setBufferAt(engine, 0, vbBytes)

        val ibBytes = ByteBuffer.allocateDirect(indices.size * 2).order(ByteOrder.nativeOrder())
        for (i in indices) ibBytes.putShort(i)
        ibBytes.flip()
        val ib = IndexBuffer.Builder()
            .indexCount(indices.size)
            .bufferType(IndexBuffer.Builder.IndexType.USHORT)
            .build(engine)
        ib.setBuffer(engine, ibBytes)

        val entity = EntityManager.get().create()
        RenderableManager.Builder(1)
            .boundingBox(bbox)
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vb, ib)
            .material(0, material)
            .culling(opaque)
            .priority(if (opaque) 4 else 6)
            .build(engine, entity)
        return Mesh(entity, vb, ib)
    }

    /**
     * Generic capsule (pill shape): cylinder body + two hemispheres at the ends.
     * Used as a "robot" stand-in body. Aligned along +Y by default.
     */
    fun buildCapsule(
        engine: Engine,
        material: MaterialInstance,
        radius: Float = 0.28f,
        cylinderHalfHeight: Float = 0.30f,
        bodyColor: FloatArray = floatArrayOf(0.55f, 0.58f, 0.65f),
        capColor: FloatArray = floatArrayOf(0.80f, 0.45f, 0.30f),
        alpha: Float = 1.0f,
    ): Mesh {
        val verts = ArrayList<Float>()
        val indices = ArrayList<Short>()

        // Cylinder side: ring at +y and ring at -y, then triangle strip between them.
        val segments = 18
        val baseIdx = (verts.size / 7).toShort()
        for (i in 0..segments) {
            val theta = (2.0 * Math.PI * i / segments).toFloat()
            val cs = kotlin.math.cos(theta) * radius
            val sn = kotlin.math.sin(theta) * radius
            pushVert(verts, cs,  cylinderHalfHeight, sn, bodyColor, alpha)  // top ring
        }
        for (i in 0..segments) {
            val theta = (2.0 * Math.PI * i / segments).toFloat()
            val cs = kotlin.math.cos(theta) * radius
            val sn = kotlin.math.sin(theta) * radius
            pushVert(verts, cs, -cylinderHalfHeight, sn, bodyColor, alpha)  // bottom ring
        }
        for (i in 0 until segments) {
            val a = (baseIdx + i).toShort()
            val b = (baseIdx + i + 1).toShort()
            val c = (baseIdx + (segments + 1) + i).toShort()
            val d = (baseIdx + (segments + 1) + i + 1).toShort()
            indices.add(a); indices.add(c); indices.add(b)
            indices.add(b); indices.add(c); indices.add(d)
        }

        // Top hemisphere (full sphere — cheaper than half, the bottom half is hidden inside the cylinder).
        appendEllipsoid(
            verts, indices,
            cx = 0f, cy = cylinderHalfHeight, cz = 0f,
            rx = radius, ry = radius, rz = radius,
            colorFn = { _, _, _ -> capColor },
            rings = 10, sectors = segments, alpha = alpha,
        )
        appendEllipsoid(
            verts, indices,
            cx = 0f, cy = -cylinderHalfHeight, cz = 0f,
            rx = radius, ry = radius, rz = radius,
            colorFn = { _, _, _ -> capColor },
            rings = 10, sectors = segments, alpha = alpha,
        )

        val totalH = cylinderHalfHeight + radius
        return finalizeMesh(
            engine, material, verts, indices,
            bbox = Box(0f, 0f, 0f, radius, totalH, radius),
            opaque = alpha >= 1f,
        )
    }

    /**
     * Stylized Utah-style teapot, built from primitives.
     *
     * Local frame:
     *   +Y up  (lid + knob direction)
     *   +Z forward (spout direction — body's "front")
     *   -Z back   (handle direction)
     *   ±X is to the teapot's right / left.
     *
     * Anatomy:
     *   - Body  : a fat-bottomed ellipsoid (rx=rz > ry) that reads as a "pot"
     *   - Lid   : a short flat ellipsoid sitting on top, slightly wider than the
     *             pot opening so the seam is visible
     *   - Knob  : a small sphere on the lid
     *   - Spout : a slightly tilted cone protruding forward + up (apex outside,
     *             base inside the body so the join is hidden)
     *   - Handle: a half-torus on the back, lying in the YZ plane, opening +Z
     *             (toward the body) so the curve bulges in -Z (away from body)
     *
     * Coloring: warm ivory body with a slightly darker bottom shade, so the
     * teapot reads as a 3-D solid even under the unlit material. Spout / handle
     * / lid / knob inherit the ivory base color so they merge into a single
     * cohesive object — directional information comes from the silhouette
     * (spout vs. handle), not from color.
     */
    fun buildTeapot(
        engine: Engine,
        material: MaterialInstance,
        scale: Float = 1.0f,
        alpha: Float = 1.0f,
    ): Mesh {
        val verts = ArrayList<Float>()
        val indices = ArrayList<Short>()

        // Warm ivory ceramic. A subtle "shaded bottom" via colorFn makes the
        // pot read as 3D against the dark scene background.
        val ivoryTop    = floatArrayOf(0.86f, 0.80f, 0.70f)
        val ivoryBottom = floatArrayOf(0.62f, 0.57f, 0.49f)
        val ivory       = floatArrayOf(0.82f, 0.76f, 0.66f)
        // Lid + knob are slightly cooler so the silhouette reads as separate parts.
        val lidColor    = floatArrayOf(0.74f, 0.69f, 0.61f)

        // Body: pot (wider than tall)
        appendEllipsoid(
            verts, indices,
            cx = 0f, cy = 0f, cz = 0f,
            rx = 0.42f * scale, ry = 0.30f * scale, rz = 0.42f * scale,
            colorFn = { _, ly, _ ->
                // Lerp between bottom and top tint by Y.
                val t = ((ly / (0.30f * scale)) + 1f) * 0.5f  // -ry -> 0, +ry -> 1
                val tt = t.coerceIn(0f, 1f)
                floatArrayOf(
                    ivoryBottom[0] + (ivoryTop[0] - ivoryBottom[0]) * tt,
                    ivoryBottom[1] + (ivoryTop[1] - ivoryBottom[1]) * tt,
                    ivoryBottom[2] + (ivoryTop[2] - ivoryBottom[2]) * tt,
                )
            },
            rings = 14, sectors = 20,
            alpha = alpha,
        )
        // Lid: short flat disc (an ellipsoid with tiny ry), sitting just above
        // the body's top. Wider than the body's top opening for a visible rim.
        appendEllipsoid(
            verts, indices,
            cx = 0f, cy = 0.32f * scale, cz = 0f,
            rx = 0.22f * scale, ry = 0.05f * scale, rz = 0.22f * scale,
            colorFn = { _, _, _ -> lidColor },
            rings = 8, sectors = 18,
            alpha = alpha,
        )
        // Knob: small sphere on the lid.
        appendEllipsoid(
            verts, indices,
            cx = 0f, cy = 0.40f * scale, cz = 0f,
            rx = 0.06f * scale, ry = 0.06f * scale, rz = 0.06f * scale,
            colorFn = { _, _, _ -> lidColor },
            rings = 8, sectors = 12,
            alpha = alpha,
        )
        // Spout: cone tilted forward and slightly upward.
        // Apex sits well outside the body in +Z; base buried inside body
        // so the cone's base disc is hidden.
        appendCone(
            verts, indices,
            ax = 0f, ay = 0.18f * scale, az = 0.62f * scale,   // apex (front + up)
            bx = 0f, by = 0.05f * scale, bz = 0.30f * scale,   // base center inside body
            radius = 0.09f * scale,
            rgb = ivory, segments = 14, alpha = alpha,
        )
        // Handle: half-torus on -Z side. Axis = -X so that θ=π/2 lies in -Z
        // (curve bulges away from body); refDir = +Y so θ=0 is the upper attach
        // point and θ=π is the lower attach point.
        appendTorus(
            verts, indices,
            cx = 0f, cy = 0.0f, cz = -0.34f * scale,
            axis = floatArrayOf(-1f, 0f, 0f),
            refDir = floatArrayOf(0f, 1f, 0f),
            majorRadius = 0.20f * scale,
            minorRadius = 0.04f * scale,
            rgb = ivory,
            startAngle = 0f,
            endAngle = Math.PI.toFloat(),
            majorSeg = 16,
            minorSeg = 10,
            alpha = alpha,
        )

        return finalizeMesh(
            engine, material, verts, indices,
            bbox = Box(
                0f, 0.05f * scale, 0.0f,
                0.45f * scale, 0.45f * scale, 0.65f * scale,
            ),
            opaque = alpha >= 1f,
        )
    }

    // -----------------------------------------------------------------
    // OBJ-based meshes (Keenan Crane's "Spot" cow, etc.)
    //
    // The vertex_color material doesn't sample textures, so we bake the texture
    // into per-vertex colors at load time: for each unique (position-index,
    // uv-index) pair we look up the corresponding pixel in the bound texture
    // PNG and store its RGB on the vertex. This loses the texture's smooth
    // interpolation across triangles, but at Spot's vertex density (~3225
    // unique vertices) the iconic black-and-white cow pattern still reads
    // clearly without needing a textured material variant.
    // -----------------------------------------------------------------

    /** Sample [bitmap] at OBJ-style UV ([0,1] x [0,1], origin = lower-left). */
    private fun sampleColor(bitmap: Bitmap, u: Float, v: Float): FloatArray {
        val w = bitmap.width
        val h = bitmap.height
        val px = (u * w).toInt().coerceIn(0, w - 1)
        // OBJ texture coords: V=0 at bottom; PNG pixel rows: y=0 at top → flip V.
        val py = ((1f - v) * h).toInt().coerceIn(0, h - 1)
        val argb = bitmap.getPixel(px, py)
        return floatArrayOf(
            ((argb shr 16) and 0xFF) / 255f,
            ((argb shr 8) and 0xFF) / 255f,
            (argb and 0xFF) / 255f,
        )
    }

    /**
     * Spot the cow — Keenan Crane's CG-classic mascot, public domain.
     * <https://www.cs.cmu.edu/~kmcrane/Projects/ModelRepository/>
     *
     * Loads `spot_triangulated.obj` + `spot_texture.png` from the app assets,
     * parses the OBJ (only `v`, `vt`, `f` lines are needed — Spot ships without
     * normals), bakes each unique (position, uv) pair's texture color into a
     * vertex color, and centers + uniformly scales the model so its largest
     * dimension equals 0.9 × [scale] (similar visual footprint to Teapot).
     *
     * Spot's local frame is Y-up with +Z forward (head direction), matching our
     * other body shapes — so it gets the same Z-up standup offset in the
     * Robotics convention.
     */
    fun buildSpot(
        engine: Engine,
        material: MaterialInstance,
        assetManager: AssetManager,
        scale: Float = 1.0f,
        alpha: Float = 1.0f,
    ): Mesh {
        // ---- Parse the OBJ ----
        val positions = ArrayList<Float>()  // xyz xyz xyz ...
        val uvs = ArrayList<Float>()        // uv uv uv ...
        // Each triangle: 6 ints = (p0,u0, p1,u1, p2,u2). 0-based indices.
        val tris = ArrayList<IntArray>()

        assetManager.open("spot_triangulated.obj").bufferedReader().use { br ->
            br.lineSequence().forEach { rawLine ->
                if (rawLine.length < 2) return@forEach
                val ch0 = rawLine[0]
                val ch1 = rawLine[1]
                when {
                    ch0 == 'v' && ch1 == ' ' -> {
                        val toks = rawLine.split(' ', '\t').filter { it.isNotEmpty() }
                        positions.add(toks[1].toFloat())
                        positions.add(toks[2].toFloat())
                        positions.add(toks[3].toFloat())
                    }
                    ch0 == 'v' && ch1 == 't' -> {
                        val toks = rawLine.split(' ', '\t').filter { it.isNotEmpty() }
                        uvs.add(toks[1].toFloat())
                        uvs.add(toks[2].toFloat())
                    }
                    ch0 == 'f' && ch1 == ' ' -> {
                        val toks = rawLine.split(' ', '\t').filter { it.isNotEmpty() }
                        // Triangulated mesh → exactly 3 vertices per face.
                        // Each vertex token: "pos[/uv[/normal]]" (1-based indices).
                        val fa = IntArray(6)
                        for (i in 0..2) {
                            val parts = toks[i + 1].split('/')
                            fa[i * 2] = parts[0].toInt() - 1
                            fa[i * 2 + 1] =
                                if (parts.size > 1 && parts[1].isNotEmpty()) parts[1].toInt() - 1 else 0
                        }
                        tris.add(fa)
                    }
                    // Anything else (#, vn, mtllib, usemtl, o, g, s) is ignored.
                }
            }
        }

        // ---- Compute bounds for centering + uniform scale ----
        var minX = Float.POSITIVE_INFINITY; var maxX = Float.NEGATIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY; var maxY = Float.NEGATIVE_INFINITY
        var minZ = Float.POSITIVE_INFINITY; var maxZ = Float.NEGATIVE_INFINITY
        var i = 0
        while (i < positions.size) {
            val x = positions[i]; val y = positions[i + 1]; val z = positions[i + 2]
            if (x < minX) minX = x; if (x > maxX) maxX = x
            if (y < minY) minY = y; if (y > maxY) maxY = y
            if (z < minZ) minZ = z; if (z > maxZ) maxZ = z
            i += 3
        }
        val cx = (minX + maxX) * 0.5f
        val cy = (minY + maxY) * 0.5f
        val cz = (minZ + maxZ) * 0.5f
        val maxDim = maxOf(maxX - minX, maxY - minY, maxZ - minZ).coerceAtLeast(1e-6f)
        // 0.9 × scale matches the Teapot footprint; legs/head fit nicely inside
        // the same bounding box used for body axes.
        val s = (0.9f * scale) / maxDim

        // ---- Bake texture color into each unique (pos, uv) vertex ----
        val bitmap = BitmapFactory.decodeStream(assetManager.open("spot_texture.png"))

        val vertMap = HashMap<Long, Int>()
        val verts = ArrayList<Float>()
        val indices = ArrayList<Short>()

        fun addVert(pi: Int, ui: Int): Short {
            val key = (pi.toLong() shl 32) or (ui.toLong() and 0xFFFFFFFFL)
            vertMap[key]?.let { return it.toShort() }
            val u = uvs[ui * 2]
            val v = uvs[ui * 2 + 1]
            val rgb = sampleColor(bitmap, u, v)
            // Spot's OBJ has the cow facing -Z. Our convention (matching the
            // Teapot's spout) is +Z forward, so apply a 180° rotation
            // about the Y axis: (x, y, z) → (-x, y, -z). This preserves
            // handedness (winding stays CCW), unlike a single-axis flip.
            val px = -(positions[pi * 3] - cx) * s
            val py = (positions[pi * 3 + 1] - cy) * s
            val pz = -(positions[pi * 3 + 2] - cz) * s
            val newIdx = verts.size / 7
            pushVert(verts, px, py, pz, rgb, alpha)
            vertMap[key] = newIdx
            return newIdx.toShort()
        }

        for (tri in tris) {
            indices.add(addVert(tri[0], tri[1]))
            indices.add(addVert(tri[2], tri[3]))
            indices.add(addVert(tri[4], tri[5]))
        }

        bitmap.recycle()

        val halfDim = (maxDim * s) * 0.5f
        return finalizeMesh(
            engine, material, verts, indices,
            bbox = Box(0f, 0f, 0f, halfDim, halfDim, halfDim),
            opaque = alpha >= 1f,
        )
    }
}
