package dev.mcreload.moves.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.kosmx.playerAnim.impl.IAnimatedPlayer;
import dev.mcreload.moves.Move;
import dev.mcreload.moves.Skill;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * The menu's preview of a skill (author, 2026-11-16: "previews with the animations, so every move is seen"): a copy of
 * the player (skin, armour) that is not in the world, the skill's weapon in hand, playing its moves over and over.
 */
final class SkillPreview {
    private final RemotePlayer dummy;
    private Skill skill;
    private Move[] seq;
    private int index, t;

    SkillPreview() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer me = mc.player;
        dummy = new RemotePlayer(mc.level, me.getGameProfile());
        dummy.setMainArm(me.getMainArm());
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            dummy.setItemSlot(slot, me.getItemBySlot(slot).copy());
        }
    }

    /** Its moves, one after another, as they go in the game. */
    static Move[] moves(Skill k) {
        return switch (k) {
            case DASH -> new Move[]{Move.DASH};
            case SWORD_SPIN -> new Move[]{Move.SWORD_SPIN};
            case AIR_SPIN -> new Move[]{Move.AIR_SPIN};
            case CHAIN -> new Move[]{Move.CHAIN_LEAP, Move.CHAIN_HOP, Move.CHAIN_HOP_STAB, Move.CHAIN_FLIP};
            case THROW -> new Move[]{Move.THROW_AIM, Move.THROW};
            case WHIRL -> new Move[]{Move.WHIRL};
            case AXE_COMBO -> new Move[]{Move.AXE_TRIPLE, Move.AXE_COMBO_CHOP, Move.AXE_COMBO_2, Move.AXE_COMBO_DIAG, Move.AXE_COMBO_END};
            case VOLLEY -> new Move[]{Move.BARRAGE};
            case HAIL -> new Move[]{Move.HAIL};
            case MARKS -> new Move[]{Move.MARKED_SPIN_LEFT, Move.MARKED_CROUCH, Move.MARKED_SPIN_RIGHT};
            case LEGOLAS -> new Move[]{Move.LEGOLAS_LEFT, Move.LEGOLAS_RIGHT};
            case SLIDE_SHOT -> new Move[]{Move.SLIDE_SHOT};
            case XBOW_G -> new Move[]{Move.XBOW_VOLLEY, Move.XBOW_SPIN};
            case XBOW_FAN -> new Move[]{Move.XBOW_FAN, Move.XBOW_FAN_SHOT};
            case XBOW_ROLLS -> new Move[]{Move.XBOW_ROLL_BACK, Move.XBOW_ROLL_LEFT};
            case DIVE -> new Move[]{Move.DIVE};
            case GUARD_KICK -> new Move[]{Move.GUARD_KICK};
            case TIME_STOP -> new Move[]{Move.TS_STANCE, Move.TS_SLASH, Move.TS_THRUST, Move.TS_BACK, Move.TS_BELLY, Move.TS_YAWN};
            case GUARD_COUNTER -> new Move[]{Move.GUARD_SHOVE, Move.GUARD_GRAB, Move.HOSTAGE_HOLD, Move.HOSTAGE_KICK};
            case GUARD_FINISH -> new Move[]{Move.GUARD_FINISH};
        };
    }

    /** What the preview holds for a skill made with this weapon (nothing for one made with any). */
    static ItemStack weapon(Skill.Weapon w) {
        return new ItemStack(switch (w) {
            case SWORD, BLADE -> Items.IRON_SWORD;
            case AXE -> Items.IRON_AXE;
            case BOW -> Items.BOW;
            case CROSSBOW -> Items.CROSSBOW;
            case ANY -> Items.AIR;
        });
    }

    /** Its icon in the lists and on the wheel (a feather for a move made with anything in hand). */
    static ItemStack icon(Skill.Weapon w) {
        return w == Skill.Weapon.ANY ? new ItemStack(Items.FEATHER) : weapon(w);
    }

    void show(Skill k) {
        if (k == skill) return;
        skill = k;
        seq = k == null ? null : moves(k);
        dummy.setItemInHand(InteractionHand.MAIN_HAND, k == null ? ItemStack.EMPTY : weapon(k.weapon));
        index = 0;
        t = 0;
        if (seq != null) MoveAnimator.play(dummy, seq[0]);
        else MoveAnimator.stop(dummy, 3);
    }

    /** How long a move of it is shown (looped ones a while). */
    private static int span(Move m) {
        return m.length >= 1000 ? 30 : m.length == Move.THROW_AIM.length ? 16 : m.length;
    }

    /** Every client tick while shown. */
    void tick() {
        dummy.tickCount++;
        ((IAnimatedPlayer) dummy).playerAnimator_getAnimation().tick();
        if (seq == null) return;
        if (++t < (index < 0 ? 12 : span(seq[Math.max(0, index)]))) return;
        t = 0;
        if (index < 0) {
            // the pause over: from the start again
            index = 0;
            MoveAnimator.play(dummy, seq[0]);
        } else if (++index < seq.length) {
            MoveAnimator.play(dummy, seq[index], seq[index - 1]);
        } else {
            index = -1;
            MoveAnimator.stop(dummy, 4);
        }
    }

    /** Drawn with its feet at (x, y), {@code size} pixels tall, turned {@code yaw} degrees from the front. */
    void render(GuiGraphics g, int x, int y, int size, float yaw, float pt) {
        Quaternionf pose = new Quaternionf().rotateZ(Mth.PI).rotateX(-12f * Mth.DEG_TO_RAD).rotateY((yaw + dummy.yBodyRot + 180f) * Mth.DEG_TO_RAD);
        float scale = size / 1.9f;
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(x, y, 150);
        ps.mulPoseMatrix(new Matrix4f().scaling(scale, scale, -scale));
        ps.mulPose(pose);
        Lighting.setupForEntityInInventory();
        Minecraft mc = Minecraft.getInstance();
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        dispatcher.setRenderShadow(false);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        boolean hideGui = mc.options.hideGui;
        mc.options.hideGui = true;     // no name tag over it
        try {
            RenderSystem.runAsFancy(() -> dispatcher.render(dummy, 0, 0, 0, 0f, pt, ps, buffers, 0xF000F0));
        } finally {
            mc.options.hideGui = hideGui;
        }
        buffers.endBatch();
        dispatcher.setRenderShadow(true);
        ps.popPose();
        Lighting.setupFor3DItems();
    }
}
