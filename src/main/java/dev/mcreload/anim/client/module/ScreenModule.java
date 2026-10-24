package dev.mcreload.anim.client.module;

import dev.mcreload.anim.client.math.AngleMath;
import dev.mcreload.anim.client.pose.PartPose;
import dev.mcreload.anim.client.pose.PoseFrame;
import dev.mcreload.anim.client.pose.Rigging;
import dev.mcreload.anim.client.pose.Signs;
import dev.mcreload.anim.client.rig.MotionState;
import dev.mcreload.anim.client.rig.PlayerRig;
import dev.mcreload.anim.config.Cfg;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * What the player does while their menu is open (see ScreenPoses), for everyone to see:
 * <ul>
 *   <li>inventory, crafting table, pause menu: thinking it over, a hand on the chin, the other arm
 *       folded under it, head tipped;</li>
 *   <li>chests, barrels, furnaces, any other container: rummaging in it with both hands, looking down;</li>
 *   <li>trading: a tap on the villager's sleeve, then talking: hands spread, pointing, explaining.</li>
 * </ul>
 * The poses were fitted on a box model of the player so that no hand goes through the head or body.
 */
final class ScreenModule implements AnimModule {
    @Override
    public boolean enabled() {
        return Cfg.idle;
    }

    @Override
    public void apply(PlayerRig rig, MotionState m, PoseFrame f, Signs s, float pt) {
        float gate = m.gate.v * m.groundW.v * m.armsFree.v;
        if (gate <= 0.001f) return;
        float hd = freeSide(rig.player, m);
        float t = m.since(m.screenStart);
        boolean chinFree = handItem(rig.player, m, hd).isEmpty(), holdFree = handItem(rig.player, m, -hd).isEmpty();
        think(m, f, s, m.thinkW.v * gate, hd, t, chinFree, holdFree);
        rummage(m, f, s, m.rummageW.v * gate, t);
        trade(f, s, m.tradeW.v * gate, hd, t);
    }

    /** The hand that does the gesture: the main one, unless it holds something and the other is empty. 1 = right. */
    private static float freeSide(Player p, MotionState m) {
        boolean main = p.getMainHandItem().isEmpty() || !p.getOffhandItem().isEmpty();
        return main == m.mainRight ? 1f : -1f;
    }

    /** What the hand on that side holds (1 = right). */
    private static net.minecraft.world.item.ItemStack handItem(Player p, MotionState m, float side) {
        return (side > 0) == m.mainRight ? p.getMainHandItem() : p.getOffhandItem();
    }

    private static float smoother(float x) {
        x = AngleMath.clamp(x, 0f, 1f);
        return x * x * x * (x * (x * 6f - 15f) + 10f);
    }

    /**
     * Thinking: the hand at the chin (elbow in front of the chest, the shoulder a little forward),
     * the other arm folded across under it, the head tipped down and to the side; now and then
     * the fingers tap the chin, and the head drifts as the thought goes on. An arm with something
     * in its hand (a sword would cross the face) just hangs relaxed.
     */
    private static void think(MotionState m, PoseFrame f, Signs s, float w, float hd, float t, boolean chinFree, boolean holdFree) {
        if (w <= 0.001f) return;
        PartPose chin = hd > 0 ? f.rightArm() : f.leftArm();
        PartPose hold = hd > 0 ? f.leftArm() : f.rightArm();
        float hmm = (float) Math.pow(Math.max(0f, Mth.sin(t * 0.9f + 1.2f)), 6f);
        float tap = hmm * Mth.sin(t * 14f) * 0.05f;
        if (chinFree) {
            chin.override(-0.81f, hd * -0.60f, hd * 0.24f, w);
            chin.overrideBend(s.elbow() * (1.37f + tap), w);
            chin.overrideBendAxis(hd * -0.50f, w);
            chin.pz -= 1.5f * w;
        }
        if (holdFree && chinFree) {
            hold.override(-1.21f, hd * 0.74f, hd * -0.36f, w);
            hold.overrideBend(s.elbow() * 0.52f, w);
            hold.overrideBendAxis(hd * 1.27f, w);
        } else {
            hold.override(-0.08f, 0f, hd * -0.08f, w);
            hold.overrideBend(s.elbow() * 0.20f, w);
        }
        f.head().override(0.38f + 0.04f * Mth.sin(t * 0.7f + m.seed), hd * (-0.12f + 0.08f * Mth.sin(t * 0.37f)), hd * 0.14f, w);
    }

    /**
     * Rummaging: leaning in, looking down, both hands in front digging about, each on its own
     * rhythm so it never looks like a loop.
     */
    private static void rummage(MotionState m, PoseFrame f, Signs s, float w, float t) {
        if (w <= 0.001f) return;
        float ph = t * 4.2f;
        f.rightArm().override(-1.10f + 0.16f * Mth.sin(ph), -0.18f + 0.15f * Mth.sin(ph * 1.7f + 1.3f), 0.06f, w);
        f.rightArm().overrideBend(s.elbow() * (0.60f + 0.18f * Mth.sin(ph * 1.3f + 0.5f)), w);
        f.leftArm().override(-1.10f + 0.16f * Mth.sin(ph + 2.4f), 0.18f - 0.15f * Mth.sin(ph * 1.7f + 3.9f), -0.06f, w);
        f.leftArm().overrideBend(s.elbow() * (0.60f + 0.18f * Mth.sin(ph * 1.3f + 2.9f)), w);
        Rigging.leanTorso(f, s, 0.24f * w);
        f.head().override(0.55f + 0.05f * Mth.sin(t * 1.1f), 0.15f * Mth.sin(t * 0.6f + m.seed), 0f, w);
    }

    /**
     * Trading: first a hand reaches out and taps the villager's sleeve twice (as if to get their
     * attention), then the talk: hands spread, a point at something, one hand explaining, one
     * after the other, the head nodding along. The head keeps looking at the villager.
     */
    private static void trade(PoseFrame f, Signs s, float w, float hd, float t) {
        if (w <= 0.001f) return;
        PartPose a = hd > 0 ? f.rightArm() : f.leftArm();
        PartPose b = hd > 0 ? f.leftArm() : f.rightArm();

        float reach = smoother(t / 0.25f) * (1f - smoother((t - 0.85f) / 0.30f));
        if (reach > 0.001f) {
            float tap = t > 0.25f && t < 0.75f ? Math.abs(Mth.sin(Mth.PI * (t - 0.25f) / 0.25f)) : 0f;
            a.override(-1.30f, hd * 0.10f, hd * 0.05f, w * reach);
            a.overrideBend(s.elbow() * (0.25f + 0.30f * tap), w * reach);
        }

        float talk = smoother((t - 0.85f) / 0.40f) * w;
        if (talk <= 0.001f) return;
        float u = (t - 0.85f) / 2.2f;
        float palms = gesture(u, 0), point = gesture(u, 1), explain = gesture(u, 2);
        if (palms > 0.001f) {
            float o = 0.10f * Mth.sin(t * 3.2f);
            a.override(-0.60f, hd * -0.25f, hd * (0.55f + o), talk * palms);
            a.overrideBend(s.elbow() * 0.85f, talk * palms);
            b.override(-0.60f, hd * 0.25f, hd * -(0.55f + 0.10f * Mth.sin(t * 3.2f + 0.6f)), talk * palms);
            b.overrideBend(s.elbow() * 0.85f, talk * palms);
        }
        if (point > 0.001f) {
            a.override(-1.45f, hd * -0.12f, 0f, talk * point);
            a.overrideBend(s.elbow() * 0.05f, talk * point);
        }
        if (explain > 0.001f) {
            float k = Mth.sin(t * 4.5f);
            a.override(-0.85f + 0.18f * k, hd * 0.12f, hd * 0.10f, talk * explain);
            a.overrideBend(s.elbow() * (0.95f + 0.15f * Mth.sin(t * 4.5f + 1f)), talk * explain);
        }
        f.head().rx += 0.07f * Mth.sin(t * 2.9f) * talk;
        f.head().rz += 0.05f * Mth.sin(t * 1.3f) * talk;
        Rigging.twistUpper(f, 0.06f * Mth.sin(t * 1.1f) * talk);
    }

    /** Weight of talking gesture {@code i} (of three, taking turns) at {@code u} gesture-lengths in. */
    private static float gesture(float u, int i) {
        float x = u - i;
        x -= 3f * (float) Math.floor(x / 3f);
        return smoother(x / 0.25f) * (1f - smoother((x - 0.85f) / 0.25f));
    }
}
