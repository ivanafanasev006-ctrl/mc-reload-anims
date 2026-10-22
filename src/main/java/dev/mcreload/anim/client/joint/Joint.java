package dev.mcreload.anim.client.joint;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.mcreload.anim.mixin.CubeAccessor;
import dev.mcreload.anim.mixin.ModelPartAccessor;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A hinge (elbow, knee or waist) shared by a body part and its overlay layer (sleeve, trousers,
 * jacket). The part's own cubes are no longer drawn; instead two {@link JointPart} children draw
 * the half above and the half below the joint line, and the moving half rotates about the outer
 * edge of the joint so no gap opens on the outside of the bend (like Mo'Bends' limbs).
 */
public final class Joint {
    /** Joint line (model pixels, part space), centre and half size of the limb at the joint. */
    final float jy, cx, cz, hx, hz;
    /** True if the half ABOVE the joint moves (waist), false if the half below does (elbow, knee). */
    final boolean upperMoves;
    private final List<JointPart> moving = new ArrayList<>();
    private final List<ModelPart> owners = new ArrayList<>();

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
     * Cuts the given parts at {@code jy}. The first part defines the joint geometry (its first cube
     * is the plain, un-inflated limb for player models). Returns null if nothing could be cut.
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
        return j.owners.isEmpty() ? null : j;
    }

    private void cut(ModelPart part) {
        ModelPartAccessor acc = (ModelPartAccessor) (Object) part;
        List<ModelPart.Cube> cubes = acc.mcreload$cubes();
        if (cubes.isEmpty()) return;
        List<float[][]> above = new ArrayList<>(), below = new ArrayList<>();
        for (ModelPart.Cube cube : cubes) {
            for (ModelPart.Polygon poly : ((CubeAccessor) (Object) cube).mcreload$polygons()) {
                Clip.split(poly, jy, above, below);
            }
        }
        JointPart top = new JointPart(above), bottom = new JointPart(below);
        Map<String, ModelPart> children = new HashMap<>(acc.mcreload$children());
        children.put("mcreload_upper", top);
        children.put("mcreload_lower", bottom);
        acc.mcreload$setChildren(children);
        // Keep the cubes (other code picks random cubes, e.g. stuck arrows) but don't draw them.
        part.skipDraw = true;
        moving.add(upperMoves ? top : bottom);
        owners.add(part);
    }

    /** Sets the bend (playerAnimator convention: axis angle around Y, bend angle). */
    public void set(float axis, float bend) {
        this.axis = axis;
        this.bend = bend;
        bent = Math.abs(bend) > 1e-4f;
        if (bent) hinge(axis, bend, pivot, rot);
        for (JointPart p : moving) p.setHinge(bent, pivot[0], pivot[1], pivot[2], rot);
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
