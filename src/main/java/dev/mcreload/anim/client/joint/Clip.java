package dev.mcreload.anim.client.joint;

import net.minecraft.client.model.geom.ModelPart;

import java.util.ArrayList;
import java.util.List;

/** Cuts a model quad by the horizontal plane y = jy into the part above and the part below. */
final class Clip {
    private static final float EPS = 1e-4f;

    static void split(ModelPart.Polygon poly, float jy, List<float[][]> above, List<float[][]> below) {
        ModelPart.Vertex[] vs = poly.vertices;
        if (vs.length < 3) return;
        List<float[]> pts = new ArrayList<>(vs.length);
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (ModelPart.Vertex v : vs) {
            pts.add(new float[]{v.pos.x(), v.pos.y(), v.pos.z(), v.u, v.v});
            minY = Math.min(minY, v.pos.y());
            maxY = Math.max(maxY, v.pos.y());
        }
        float[] normal = {poly.normal.x(), poly.normal.y(), poly.normal.z()};
        // Model space is y-down: "above" the joint means smaller y.
        if (maxY <= jy + EPS) { add(above, pts, normal); return; }
        if (minY >= jy - EPS) { add(below, pts, normal); return; }
        add(above, clip(pts, jy, true), normal);
        add(below, clip(pts, jy, false), normal);
    }

    private static List<float[]> clip(List<float[]> in, float jy, boolean keepAbove) {
        List<float[]> out = new ArrayList<>();
        int n = in.size();
        for (int i = 0; i < n; i++) {
            float[] cur = in.get(i), prev = in.get((i + n - 1) % n);
            boolean cIn = inside(cur, jy, keepAbove), pIn = inside(prev, jy, keepAbove);
            if (cIn) {
                if (!pIn) out.add(cross(prev, cur, jy));
                out.add(cur);
            } else if (pIn) {
                out.add(cross(prev, cur, jy));
            }
        }
        return out;
    }

    private static boolean inside(float[] p, float jy, boolean keepAbove) {
        return keepAbove ? p[1] <= jy + EPS : p[1] >= jy - EPS;
    }

    private static float[] cross(float[] a, float[] b, float jy) {
        float t = (jy - a[1]) / (b[1] - a[1]);
        float[] r = new float[5];
        for (int i = 0; i < 5; i++) r[i] = a[i] + (b[i] - a[i]) * t;
        r[1] = jy;
        return r;
    }

    /** Stores a polygon as quads (triangles repeat their last vertex). */
    private static void add(List<float[][]> out, List<float[]> pts, float[] normal) {
        int n = pts.size();
        if (n < 3) return;
        // Fan: (0, i, i+1, i+2) quads.
        for (int i = 1; i + 1 < n; i += 2) {
            float[] a = pts.get(0), b = pts.get(i), c = pts.get(i + 1);
            float[] d = i + 2 < n ? pts.get(i + 2) : c;
            out.add(new float[][]{a.clone(), b.clone(), c.clone(), d.clone(), normal});
        }
    }

    private Clip() {}
}
