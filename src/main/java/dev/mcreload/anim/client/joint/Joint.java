package dev.mcreload.anim.client.joint;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.mcreload.anim.mixin.CubeAccessor;
import dev.mcreload.anim.mixin.ModelPartAccessor;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * A hinge (elbow, knee or waist) shared by a body part and its overlay layer (sleeve, trousers,
 * jacket). The faces of the part's cubes are cut along the joint line once; while the joint is
 * bent, the model draws the part itself through {@link #draw}: the half that stays, then the half
 * that moves, rotated about the outer edge of the joint so no gap opens on the outside of the bend
 * (like Mo'Bends' limbs). The model parts themselves are never changed (no extra children, no
 * hidden cubes), so renderers that cache model parts (Oculus, Sodium ports, EMF...) cannot lose
 * anything: a straight limb is simply drawn the vanilla way.
 */
public final class Joint {
    /** Joint line (model pixels, part space), centre and half size of the limb at the joint. */
    final float jy, cx, cz, hx, hz;
    /** True if the half ABOVE the joint moves (waist), false if the half below does (elbow, knee). */
    final boolean upperMoves;
    /** For every part cut by this joint: its faces, the half that stays and the half that moves. */
    private final Map<ModelPart, Halves> halves = new IdentityHashMap<>();

    public float axis, bend;
    private boolean bent;
    private final float[] pivot = new float[3];
    private final Quaternionf rot = new Quaternionf();

    private Joint(float jy, float cx, float cz, float hx, float hz, boolean upperMoves) {
        this.jy = jy;
        this.cx = cx;
        this.cz = cz;
        this.hx = hx;
        this.hz = hz;
        this.upperMoves = upperMoves;
    }

    /**
     * Cuts the faces of the given parts at {@code jy}. The first part defines the joint geometry
     * (its first cube is the plain, un-inflated limb for player models). Returns null if nothing
     * could be cut.
     */
    static Joint build(float jy, boolean upperMoves, ModelPart... parts) {
        if (parts.length == 0 || parts[0] == null) return null;
        List<ModelPart.Cube> base = ((ModelPartAccessor) (Object) parts[0]).mcreload$cubes();
        if (base.isEmpty()) return null;
        ModelPart.Cube c0 = base.get(0);
        // Limbs and torso are 4 pixels deep; anything more is inflation (armour, overlay).
        float grow = Math.max(0f, ((c0.maxZ - c0.minZ) - 4f) / 2f);
        Joint j = new Joint(jy, (c0.minX + c0.maxX) / 2f, (c0.minZ + c0.maxZ) / 2f,
                (c0.maxX - c0.minX) / 2f - grow, (c0.maxZ - c0.minZ) / 2f - grow, upperMoves);
        for (ModelPart p : parts) {
            if (p != null) j.cut(p);
        }
        return j.halves.isEmpty() ? null : j;
    }

    private void cut(ModelPart part) {
        List<ModelPart.Cube> cubes = ((ModelPartAccessor) (Object) part).mcreload$cubes();
        if (cubes.isEmpty()) return;
        List<float[][]> above = new ArrayList<>(), below = new ArrayList<>();
        for (ModelPart.Cube cube : cubes) {
            for (ModelPart.Polygon poly : ((CubeAccessor) (Object) cube).mcreload$polygons()) {
                Clip.split(poly, jy, above, below);
            }
        }
        halves.put(part, new Halves(upperMoves ? below : above, upperMoves ? above : below));
    }

    /** This joint cuts the given part. */
    public boolean owns(ModelPart part) {
        return halves.containsKey(part);
    }

    /**
     * Draws a part cut by this joint, bent (the model calls this instead of {@code part.render}).
     * Returns false if the part is not one of ours.
     */
    public boolean draw(ModelPart part, PoseStack ps, VertexConsumer vc, int light, int overlay,
                        float r, float g, float b, float a) {
        Halves h = halves.get(part);
        if (h == null) return false;
        if (!part.visible) return true;
        ps.pushPose();
        part.translateAndRotate(ps);
        if (!part.skipDraw) {
            h.still.emit(ps.last(), vc, light, overlay, r, g, b, a);
            ps.pushPose();
            apply(ps);
            h.moving.emit(ps.last(), vc, light, overlay, r, g, b, a);
            ps.popPose();
        }
        for (ModelPart child : ((ModelPartAccessor) (Object) part).mcreload$children().values()) {
            child.render(ps, vc, light, overlay, r, g, b, a);
        }
        ps.popPose();
        return true;
    }

    /** The cut faces of one part: quads as 4 x (x, y, z, u, v) in model pixels plus a normal. */
    private record Halves(Quads still, Quads moving) {
        Halves(List<float[][]> still, List<float[][]> moving) {
            this(new Quads(still), new Quads(moving));
        }
    }

    private static final class Quads {
        final float[] verts, normals;
        final int count;
        private final Vector3f nv = new Vector3f();
        private final Vector4f pv = new Vector4f();

        Quads(List<float[][]> polys) {
            count = polys.size();
            verts = new float[count * 20];
            normals = new float[count * 3];
            for (int i = 0; i < count; i++) {
                float[][] q = polys.get(i);
                for (int k = 0; k < 4; k++) System.arraycopy(q[k], 0, verts, i * 20 + k * 5, 5);
                System.arraycopy(q[4], 0, normals, i * 3, 3);
            }
        }

        void emit(PoseStack.Pose pose, VertexConsumer vc, int light, int overlay, float r, float g, float b, float a) {
            Matrix4f m = pose.pose();
            Matrix3f n = pose.normal();
            for (int i = 0; i < count; i++) {
                n.transform(nv.set(normals[i * 3], normals[i * 3 + 1], normals[i * 3 + 2]));
                for (int k = 0; k < 4; k++) {
                    int o = i * 20 + k * 5;
                    m.transform(pv.set(verts[o] / 16f, verts[o + 1] / 16f, verts[o + 2] / 16f, 1f));
                    vc.vertex(pv.x(), pv.y(), pv.z(), r, g, b, a, verts[o + 3], verts[o + 4], overlay, light,
                            nv.x(), nv.y(), nv.z());
                }
            }
        }
    }

    /** Sets the bend (playerAnimator convention: axis angle around Y, bend angle). */
    public void set(float axis, float bend) {
        this.axis = axis;
        this.bend = bend;
        bent = Math.abs(bend) > 1e-4f;
        if (bent) hinge(axis, bend, pivot, rot);
    }

    public boolean bent() {
        return bent;
    }

    /** Joint line (part space, pixels). */
    public float jointY() {
        return jy;
    }

    public boolean upperMoves() {
        return upperMoves;
    }

    /** Hinge pivot (part space, pixels); valid while {@link #bent()}. */
    public float[] pivot() {
        return pivot;
    }

    /** Hinge rotation; valid while {@link #bent()}. */
    public Quaternionf rotation() {
        return rot;
    }

    /** The hinge transform in part space. */
    public void apply(PoseStack ps) {
        if (!bent) return;
        ps.translate(pivot[0] / 16f, pivot[1] / 16f, pivot[2] / 16f);
        ps.mulPose(rot);
        ps.translate(-pivot[0] / 16f, -pivot[1] / 16f, -pivot[2] / 16f);
    }

    private void hinge(float axis, float bend, float[] pivotOut, Quaternionf q) {
        hinge(jy, cx, cz, hx, hz, upperMoves, axis, bend, pivotOut, q);
    }

    /**
     * Pivot on the outer edge of the joint and the rotation about the bend axis. Rotation axis as
     * playerAnimator defines it: (cos(-axis), 0, sin(-axis)).
     */
    public static void hinge(float jy, float cx, float cz, float hx, float hz, boolean upperMoves,
                             float axis, float bend, float[] pivotOut, Quaternionf q) {
        float ax = (float) Math.cos(-axis), az = (float) Math.sin(-axis);
        // Where the moving end travels for a positive bend: A x (0, s, 0).
        float s = upperMoves ? -1f : 1f;
        float dx = -az * s, dz = ax * s;
        if (bend < 0f) { dx = -dx; dz = -dz; }
        pivotOut[0] = cx - hx * dx;
        pivotOut[1] = jy;
        pivotOut[2] = cz - hz * dz;
        q.identity().rotateAxis(bend, ax, 0f, az);
    }

    /** Same hinge as a matrix (for code that rebuilds the render transform, e.g. weapon trails). */
    public static void hinge(Matrix4f m, float jy, float cx, float cz, float hx, float hz, boolean upperMoves,
                             float axis, float bend) {
        if (Math.abs(bend) <= 1e-4f) return;
        float[] p = new float[3];
        Quaternionf q = new Quaternionf();
        hinge(jy, cx, cz, hx, hz, upperMoves, axis, bend, p, q);
        m.translate(p[0] / 16f, p[1] / 16f, p[2] / 16f);
        m.rotate(q);
        m.translate(-p[0] / 16f, -p[1] / 16f, -p[2] / 16f);
    }
}
