package dev.mcreload.anim.client.joint;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * One half of a limb (or of the torso): the original cube's faces cut at the joint, drawn with an
 * optional hinge rotation about a pivot on the joint line. Lives as a child of the original
 * ModelPart, so it follows every transform the game and other mods put on that part.
 */
public final class JointPart extends ModelPart {
    /** Per quad: 4 vertices x (x, y, z, u, v), in model pixels. */
    private final float[] verts;
    /** Per quad: normal (x, y, z). */
    private final float[] normals;
    private final int quads;

    // Hinge: rotate about (px, py, pz) [pixels].
    private boolean bent;
    private float px, py, pz;
    private final Quaternionf rot = new Quaternionf();

    JointPart(List<float[][]> polys) {
        super(new ArrayList<>(), new HashMap<>());
        quads = polys.size();
        verts = new float[quads * 20];
        normals = new float[quads * 3];
        for (int i = 0; i < quads; i++) {
            float[][] q = polys.get(i);
            // q[0..3] = vertices (x, y, z, u, v), q[4] = normal
            for (int k = 0; k < 4; k++) System.arraycopy(q[k], 0, verts, i * 20 + k * 5, 5);
            System.arraycopy(q[4], 0, normals, i * 3, 3);
        }
    }

    void setHinge(boolean bent, float px, float py, float pz, Quaternionf q) {
        this.bent = bent;
        this.px = px;
        this.py = py;
        this.pz = pz;
        this.rot.set(q);
    }

    @Override
    public void translateAndRotate(PoseStack ps) {
        if (!bent) return;
        ps.translate(px / 16f, py / 16f, pz / 16f);
        ps.mulPose(rot);
        ps.translate(-px / 16f, -py / 16f, -pz / 16f);
    }

    @Override
    public void render(PoseStack ps, VertexConsumer vc, int light, int overlay, float r, float g, float b, float a) {
        if (!this.visible || quads == 0) return;
        ps.pushPose();
        translateAndRotate(ps);
        if (!this.skipDraw) {
            PoseStack.Pose pose = ps.last();
            Matrix4f m = pose.pose();
            Matrix3f n = pose.normal();
            Vector3f nv = new Vector3f();
            Vector4f pv = new Vector4f();
            for (int i = 0; i < quads; i++) {
                n.transform(nv.set(normals[i * 3], normals[i * 3 + 1], normals[i * 3 + 2]));
                for (int k = 0; k < 4; k++) {
                    int o = i * 20 + k * 5;
                    m.transform(pv.set(verts[o] / 16f, verts[o + 1] / 16f, verts[o + 2] / 16f, 1f));
                    vc.vertex(pv.x(), pv.y(), pv.z(), r, g, b, a, verts[o + 3], verts[o + 4], overlay, light,
                            nv.x(), nv.y(), nv.z());
                }
            }
        }
        ps.popPose();
    }
}
