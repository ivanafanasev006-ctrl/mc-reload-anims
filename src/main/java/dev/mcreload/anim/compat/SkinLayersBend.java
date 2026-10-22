package dev.mcreload.anim.compat;

import dev.mcreload.anim.client.joint.Clip;
import dev.mcreload.anim.client.joint.Joint;
import dev.mcreload.anim.client.joint.Joints;
import dev.mcreload.anim.client.joint.PartBinding;
import dev.mcreload.anim.config.Cfg;
import dev.mcreload.anim.mixin.CubeAccessor;
import dev.tr7zw.skinlayers.api.LayerFeatureTransformerAPI;
import dev.tr7zw.skinlayers.api.MeshTransformer;
import dev.tr7zw.skinlayers.api.MeshTransformerProvider;
import dev.tr7zw.skinlayers.api.SkinLayersAPI;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The Skin Layers 3D side of {@link SkinLayersCompat}: a mesh transformer that bends voxels with
 * our hinges, and a layer transformer that moves head and arm layers with a bent waist.
 *
 * <p>Skin Layers 3D draws a part's voxels in "mesh space": the part's transform, then a scale
 * (voxel size) and a small offset, so the mapping to the part space our joints live in is
 * {@code part = S * (16 * mesh + t)} (pixels). Each vertex on the moving side of the joint line
 * is turned about the hinge there and mapped back.</p>
 */
final class SkinLayersBend implements MeshTransformerProvider {
    private final MeshTransformerProvider previous;
    private final Bender bender = new Bender();

    private SkinLayersBend(MeshTransformerProvider previous) {
        this.previous = previous != null ? previous : MeshTransformerProvider.EMPTY_PROVIDER;
    }

    static void install() {
        SkinLayersAPI.setupMeshTransformerProvider(new SkinLayersBend(SkinLayersAPI.getMeshTransformerProvider()));
        LayerFeatureTransformerAPI.LayerTransformer prev = LayerFeatureTransformerAPI.getTransformer();
        LayerFeatureTransformerAPI.setLayerTransformer((player, stack, part) -> {
            if (prev != null) prev.transform(player, stack, part);
            if (!Cfg.enabled || !Cfg.bendSkinLayers || Joints.firstPerson) return;
            PartBinding b = PartBinding.of(part);
            if (b == null || !b.upper()) return;
            if (b.kind == PartBinding.HEAD) b.rig.applyNeck(stack, b.body, part);
            else b.rig.applyUpperBody(stack, b.body);
        });
    }

    @Override
    public MeshTransformer prepareTransformer(ModelPart vanillaModel) {
        PartBinding b = PartBinding.of(vanillaModel);
        if (b == null) return previous.prepareTransformer(vanillaModel);
        bender.set(b);
        return bender;
    }

    // ------------------------------------------------------------------ voxel sizes

    private static Field configField, baseField, bodyField;
    private static boolean lookedUp;

    /** {base voxel size, body voxel width} from the Skin Layers config (defaults if unreadable). */
    private static void voxelSizes(float[] out) {
        out[0] = 1.15f;
        out[1] = 1.05f;
        try {
            if (!lookedUp) {
                lookedUp = true;
                configField = Class.forName("dev.tr7zw.skinlayers.versionless.ModBase").getField("config");
            }
            if (configField == null) return;
            Object cfg = configField.get(null);
            if (cfg == null) return;
            if (baseField == null) {
                baseField = cfg.getClass().getField("baseVoxelSize");
                bodyField = cfg.getClass().getField("bodyVoxelWidthSize");
            }
            out[0] = baseField.getFloat(cfg);
            out[1] = bodyField.getFloat(cfg);
        } catch (Throwable t) {
            configField = null;
        }
    }

    // ------------------------------------------------------------------ the transformer

    private static final class Bender implements MeshTransformer {
        boolean active;
        boolean upperMoves;
        float jy, cut;
        float sx, sy, sz, tx, ty;
        final float[] pivot = new float[3];
        final Quaternionf rot = new Quaternionf(), half = new Quaternionf();
        private final float[] sizes = new float[2];
        private final Vector3f tmp = new Vector3f();

        void set(PartBinding b) {
            Joint j = b.joint;
            active = j != null && j.bent() && Cfg.enabled && Cfg.bendSkinLayers && !Joints.firstPerson;
            voxelSizes(sizes);
            float base = sizes[0];
            sy = 1.035f;
            sz = base;
            switch (b.kind) {
                case PartBinding.BODY -> { sx = sizes[1]; tx = 0f; ty = -0.2f; }
                case PartBinding.RIGHT_ARM -> { sx = base; tx = b.slim ? -0.499f : -0.998f; ty = -0.1f; }
                case PartBinding.LEFT_ARM -> { sx = base; tx = b.slim ? 0.499f : 0.998f; ty = -0.1f; }
                case PartBinding.RIGHT_LEG, PartBinding.LEFT_LEG -> { sx = base; tx = 0f; ty = -0.2f; }
                default -> { sx = base; tx = 0f; ty = 0f; active = false; }
            }
            if (j == null) return;
            jy = j.jointY();
            cut = jy / sy - ty;
            upperMoves = j.upperMoves();
            if (active) {
                float[] p = j.pivot();
                System.arraycopy(p, 0, pivot, 0, 3);
                rot.set(j.rotation());
                half.identity().slerp(rot, 0.5f);
            }
        }

        boolean moving(float partY) {
            return upperMoves ? partY < jy : partY > jy;
        }

        /** Hinges a point given in mesh pixels (in place); returns whether it moved. */
        boolean hinge(Vector3f m) {
            float x = sx * (m.x + tx), y = sy * (m.y + ty), z = sz * m.z;
            if (!moving(y)) return false;
            rotate(m, x, y, z);
            return true;
        }

        private void rotate(Vector3f m, float x, float y, float z) {
            tmp.set(x - pivot[0], y - pivot[1], z - pivot[2]);
            rot.transform(tmp);
            m.set((tmp.x + pivot[0]) / sx - tx, (tmp.y + pivot[1]) / sy - ty, (tmp.z + pivot[2]) / sz);
        }

        private final Vector3f vp = new Vector3f();

        /** Voxel faces: positions in blocks, mesh space. Each corner bends on its own side. */
        @Override
        public void transform(Vector3f normal, Vector4f[] vertices) {
            if (!active) return;
            int moved = 0;
            for (Vector4f v : vertices) {
                vp.set(v.x * 16f, v.y * 16f, v.z * 16f);
                if (hinge(vp)) {
                    moved++;
                    v.set(vp.x / 16f, vp.y / 16f, vp.z / 16f, v.w);
                }
            }
            if (moved == vertices.length) rot.transform(normal);
            else if (moved > 0) half.transform(normal);
        }

        /** The big flat box (fast render mode): cut once at the joint line, then hinge every frame. */
        @Override
        public void transform(ModelPart.Cube cube) {
            CubeState st = CUBES.get(cube);
            if (!active) {
                if (st != null) st.apply(null);
                return;
            }
            if (st == null || st.cut != cut) {
                if (st != null) st.unsplit(cube);
                st = CubeState.split(cube, cut);
                CUBES.put(cube, st);
            }
            st.apply(this);
        }
    }

    private static final Map<ModelPart.Cube, CubeState> CUBES = Collections.synchronizedMap(new WeakHashMap<>());

    /** A cube whose faces were cut at the joint line, with the unbent corner positions. */
    private static final class CubeState {
        final float cut;
        final ModelPart.Polygon[] original;
        final ModelPart.Polygon[] polys;
        final boolean[] upper;
        final float[] pos, normals;

        private CubeState(float cut, ModelPart.Polygon[] original, List<ModelPart.Polygon> polys, List<Boolean> upper) {
            this.cut = cut;
            this.original = original;
            this.polys = polys.toArray(new ModelPart.Polygon[0]);
            this.upper = new boolean[this.polys.length];
            this.pos = new float[this.polys.length * 12];
            this.normals = new float[this.polys.length * 3];
            for (int i = 0; i < this.polys.length; i++) {
                this.upper[i] = upper.get(i);
                ModelPart.Polygon p = this.polys[i];
                for (int k = 0; k < 4; k++) {
                    Vector3f v = p.vertices[k].pos;
                    pos[i * 12 + k * 3] = v.x;
                    pos[i * 12 + k * 3 + 1] = v.y;
                    pos[i * 12 + k * 3 + 2] = v.z;
                }
                normals[i * 3] = p.normal.x;
                normals[i * 3 + 1] = p.normal.y;
                normals[i * 3 + 2] = p.normal.z;
            }
        }

        static CubeState split(ModelPart.Cube cube, float cut) {
            CubeAccessor acc = (CubeAccessor) (Object) cube;
            ModelPart.Polygon[] old = acc.mcreload$polygons();
            List<ModelPart.Polygon> out = new ArrayList<>();
            List<Boolean> side = new ArrayList<>();
            for (ModelPart.Polygon p : old) {
                List<float[][]> above = new ArrayList<>(), below = new ArrayList<>();
                Clip.split(p, cut, above, below);
                for (float[][] q : above) { out.add(polygon(q)); side.add(true); }
                for (float[][] q : below) { out.add(polygon(q)); side.add(false); }
            }
            CubeState st = new CubeState(cut, old, out, side);
            acc.kinetrix$setPolygons(st.polys);
            return st;
        }

        void unsplit(ModelPart.Cube cube) {
            ((CubeAccessor) (Object) cube).kinetrix$setPolygons(original);
        }

        private static ModelPart.Polygon polygon(float[][] q) {
            ModelPart.Vertex[] vs = new ModelPart.Vertex[4];
            for (int k = 0; k < 4; k++) vs[k] = new ModelPart.Vertex(q[k][0], q[k][1], q[k][2], q[k][3], q[k][4]);
            ModelPart.Polygon p = new ModelPart.Polygon(vs.clone(), 0f, 0f, 1f, 1f, 1f, 1f, false, Direction.UP);
            for (int k = 0; k < 4; k++) p.vertices[k] = vs[k];
            p.normal.set(q[4][0], q[4][1], q[4][2]);
            return p;
        }

        /** Restores the straight corners, then hinges the moving side (bender null = straight). */
        void apply(Bender b) {
            for (int i = 0; i < polys.length; i++) {
                ModelPart.Polygon p = polys[i];
                boolean move = b != null && (b.upperMoves == upper[i]);
                for (int k = 0; k < 4; k++) {
                    int o = i * 12 + k * 3;
                    Vector3f v = p.vertices[k].pos;
                    if (move) {
                        b.rotate(v, b.sx * (pos[o] + b.tx), b.sy * (pos[o + 1] + b.ty), b.sz * pos[o + 2]);
                    } else {
                        v.set(pos[o], pos[o + 1], pos[o + 2]);
                    }
                }
                p.normal.set(normals[i * 3], normals[i * 3 + 1], normals[i * 3 + 2]);
                if (move) b.rot.transform(p.normal);
            }
        }
    }
}
