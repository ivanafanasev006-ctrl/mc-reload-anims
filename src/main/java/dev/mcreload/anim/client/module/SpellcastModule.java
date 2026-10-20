package dev.mcreload.anim.client.module;

import dev.mcreload.anim.client.pose.PoseFrame;
import dev.mcreload.anim.client.pose.Rigging;
import dev.mcreload.anim.client.pose.Signs;
import dev.mcreload.anim.client.rig.MotionState;
import dev.mcreload.anim.client.rig.PlayerRig;
import dev.mcreload.anim.compat.IronsSpellsCompat;
import dev.mcreload.anim.config.Cfg;
import net.minecraft.util.Mth;

/**
 * Iron's Spells 'n Spellbooks: Iron's animates the arms for each spell; this module gives the
 * rest of the body something to do. A grounded casting stance, a posture flavoured by the spell
 * school, a tremble while channelling, and a recoil when the spell is released.
 * Arms are deliberately left to Iron's.
 */
final class SpellcastModule implements AnimModule {
    @Override
    public boolean enabled() {
        return Cfg.ironsSpells && IronsSpellsCompat.isReady();
    }

    @Override
    public void apply(PlayerRig rig, MotionState m, PoseFrame f, Signs s, float pt) {
        float gate = m.gate.v;
        float c = m.castW.v * gate;

        if (c > 0.001f) {
            // Stance: soft knees and a slightly wider base, less while walking.
            float still = 1f - 0.6f * m.moveF();
            Rigging.crouch(f, s, 0.12f * c * still);
            Rigging.legsOut(f, 0.07f * c * still);

            float t = m.time;
            switch (m.cast.style()) {
                case AGGRESSIVE -> {
                    Rigging.leanTorso(f, s, 0.12f * c);
                    f.head().rx -= 0.10f * c;
                }
                case ASCENDANT -> {
                    Rigging.leanTorso(f, s, -0.06f * c);
                    f.head().rx -= 0.15f * c;
                    f.body().py += 0.015f * c;
                }
                case FLOWING -> {
                    Rigging.leanTorso(f, s, 0.03f * c);
                    f.body().rz += 0.03f * Mth.sin(t * 2f) * c;
                    f.body().py += (0.02f + 0.015f * Mth.sin(t * 2f)) * c;
                }
                default -> Rigging.leanTorso(f, s, 0.05f * c);
            }

            if (m.cast.continuous()) {
                f.torso().rz += (Mth.sin(t * 41f) * 0.008f + Mth.sin(t * 29f) * 0.006f) * c;
            }
        }

        // Release recoil once the cast ends.
        float r = m.release.x * gate;
        if (Math.abs(r) > 0.001f) {
            Rigging.leanTorso(f, s, -0.16f * r);
            f.head().rx -= 0.12f * r;
            f.body().py += 0.02f * r;
            Rigging.armsOut(f, 0.10f * r);
        }
    }
}
