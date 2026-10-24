package dev.mcreload.anim.client.gallery;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.kosmx.playerAnim.impl.IAnimatedPlayer;
import dev.mcreload.anim.client.rig.MotionState;
import dev.mcreload.anim.client.rig.PlayerRig;
import dev.mcreload.anim.client.rig.RigRegistry;
import dev.mcreload.anim.mixin.PlayerAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * The gallery's dummy: a copy of you (skin, skin layers, main hand, armour) that is not in the
 * world, driven by a demo script and animated by the very same rig as any player you see. It
 * behaves like another player whose client tells us what it does (keys, menus, emotes...), while
 * its body (position, speed, ground, pose) is moved by a little physics of its own: it runs
 * round a wide circle so the motion is real, jumps and falls with gravity.
 */
public final class DemoStage {
    /** Radius of the circle it walks and runs round (blocks): wide enough not to lean into it. */
    private static final double R = 24.0;

    private final RemotePlayer dummy;
    private final PlayerRig rig;
    private final double cx, cy, cz;
    private Demo demo;
    private int t;

    // what the script asks for this tick
    private double speed;
    private boolean sprint, crouch, crawl, fly, glide, sleep;
    // the body's own state
    private double angle, y, vy;
    private boolean ground = true;
    private double pushX, pushZ;

    public DemoStage() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer me = mc.player;
        Object irons = dev.mcreload.anim.compat.IronsSpellsCompat.adjustment();
        dummy = new RemotePlayer(mc.level, me.getGameProfile());
        dev.mcreload.anim.compat.IronsSpellsCompat.restoreAdjustment(irons);
        dummy.setMainArm(me.getMainArm());
        dummy.getEntityData().set(PlayerAccessor.kinetrix$modelParts(), me.getEntityData().get(PlayerAccessor.kinetrix$modelParts()));
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            dummy.setItemSlot(slot, me.getItemBySlot(slot).copy());
        }
        cx = me.getX();
        cy = me.getY();
        cz = me.getZ();
        rig = RigRegistry.get(dummy);
        if (rig != null) rig.preview = true;
        place(0.0, cy, true);
    }

    public boolean ready() {
        return rig != null;
    }

    public Demo demo() {
        return demo;
    }

    public MotionState motion() {
        return rig.motion;
    }

    /** Start showing a demo from the beginning, standing still and empty-handed. */
    public void play(Demo next) {
        demo = next;
        t = 0;
        speed = 0;
        sprint = crouch = crawl = fly = glide = sleep = false;
        vy = 0;
        ground = true;
        place(angle, cy, true);
        dummy.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        dummy.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        dummy.stopFallFlying();
        dummy.getAbilities().flying = false;
        dummy.stopSleeping();
        dummy.setPose(Pose.STANDING);
        dummy.swinging = false;
        dummy.hurtTime = 0;
        if (rig != null) {
            MotionState m = rig.motion;
            m.onRemote(dev.mcreload.anim.net.AnimPacket.STATE, 0);
            m.onRemote(dev.mcreload.anim.net.AnimPacket.EMOTE, -1);
            m.onRemote(dev.mcreload.anim.net.AnimPacket.IDLE, -1);
            m.onRemote(dev.mcreload.anim.net.AnimPacket.SIT, 0);
            m.activeAt = m.time;
        }
    }

    // ------------------------------------------------------------------ what a script can do

    public int tick() {
        return t;
    }

    public void speed(double blocksPerTick) {
        speed = blocksPerTick;
    }

    public void sprint(boolean on) {
        sprint = on;
    }

    public void crouch(boolean on) {
        crouch = on;
    }

    public void crawl(boolean on) {
        crawl = on;
    }

    public void fly(boolean on) {
        fly = on;
    }

    public void glide(boolean on) {
        glide = on;
    }

    public void sleep(boolean on) {
        sleep = on;
    }

    public boolean onGround() {
        return ground;
    }

    /** Height above the ground it stands on (blocks). */
    public double height() {
        return y - cy;
    }

    public boolean falling() {
        return !ground && vy < 0;
    }

    public void jump() {
        if (ground) {
            vy = 0.42;
            ground = false;
        }
    }

    /** Drop from this high up (straight away, from rest). */
    public void dropFrom(double h) {
        place(angle, cy + h, false);
        vy = 0;
    }

    public void hold(ItemStack main, ItemStack off) {
        if (!ItemStack.matches(dummy.getMainHandItem(), main)) dummy.setItemSlot(EquipmentSlot.MAINHAND, main.copy());
        if (!ItemStack.matches(dummy.getOffhandItem(), off)) dummy.setItemSlot(EquipmentSlot.OFFHAND, off.copy());
    }

    public void swing(InteractionHand hand) {
        dummy.swinging = true;
        dummy.swingTime = -1;
        dummy.swingingArm = hand;
    }

    /** Hit: from the front pushes it back, from behind pushes it forward. */
    public void hurt(boolean front) {
        dummy.hurtTime = 10;
        dummy.hurtDuration = 10;
        float yaw = (float) angle;
        double f = front ? -0.35 : 0.35;
        pushX = -Mth.sin(yaw) * f;
        pushZ = Mth.cos(yaw) * f;
    }

    /** As if its own client had sent this (keys / menu held, emote, fidget, sitting, landing, sleep style). */
    public void remote(byte type, int value) {
        rig.motion.onRemote(type, value);
    }

    // ------------------------------------------------------------------ every client tick

    public void update() {
        if (rig == null || demo == null) return;
        if (demo.script() != null) demo.script().tick(this, t);
        t++;
        if (demo.loopTicks() > 0 && t >= demo.loopTicks()) play(demo);

        // Body: round the circle at the asked speed, gravity in the air.
        double oldX = dummy.getX(), oldY = dummy.getY(), oldZ = dummy.getZ();
        angle += speed / R;
        if (glide) {
            y -= 0.06;
        } else if (fly) {
            y = cy + 1.5 + 0.1 * Math.sin(t * 0.1);
        } else if (!ground) {
            vy = (vy - 0.08) * 0.98;
            vy = Math.max(vy, -2.9);    // faster reads as a teleport
            y += vy;
            if (y <= cy) {
                y = cy;
                vy = 0;
                ground = true;
            }
        }
        double x = cx + R * Math.cos(angle) - R + pushX, z = cz + R * Math.sin(angle) + pushZ;
        pushX *= 0.6;
        pushZ *= 0.6;
        dummy.xo = oldX;
        dummy.yo = oldY;
        dummy.zo = oldZ;
        dummy.setPos(x, y, z);
        dummy.setOnGround(ground && !fly && !glide);
        float yaw = (float) Math.toDegrees(angle);
        dummy.yBodyRotO = dummy.yBodyRot;
        dummy.yHeadRotO = dummy.yHeadRot;
        dummy.yRotO = dummy.getYRot();
        dummy.yBodyRot = yaw;
        dummy.yHeadRot = yaw;
        dummy.setYRot(yaw);
        dummy.setXRot(0f);

        dummy.setSprinting(sprint);
        dummy.getAbilities().flying = fly;
        if (glide && !dummy.isFallFlying()) dummy.startFallFlying();
        if (!glide && dummy.isFallFlying()) dummy.stopFallFlying();
        if (sleep && !dummy.isSleeping()) {
            dummy.setSleepingPos(BlockPos.containing(x, y, z));
            dummy.setPose(Pose.SLEEPING);
        } else if (!sleep) {
            if (dummy.isSleeping()) dummy.stopSleeping();
            dummy.setPose(crawl ? Pose.SWIMMING : crouch ? Pose.CROUCHING : glide ? Pose.FALL_FLYING : Pose.STANDING);
        }

        float moved = (float) Math.sqrt((x - oldX) * (x - oldX) + (z - oldZ) * (z - oldZ));
        dummy.walkAnimation.update(fly || glide ? 0f : Math.min(moved * 4f, 1f), 0.4f);
        if (dummy.hurtTime > 0) dummy.hurtTime--;
        if (dummy.swinging && ++dummy.swingTime >= 6) {
            dummy.swinging = false;
            dummy.swingTime = 0;
        }
        dummy.tickCount++;
        ((IAnimatedPlayer) dummy).playerAnimator_getAnimation().tick();
    }

    private void place(double a, double atY, boolean onGround) {
        angle = a;
        y = atY;
        ground = onGround;
        double x = cx + R * Math.cos(a) - R, z = cz + R * Math.sin(a);
        dummy.setPos(x, atY, z);
        dummy.xo = x;
        dummy.yo = atY;
        dummy.zo = z;
    }

    // ------------------------------------------------------------------ drawing

    /**
     * Draw it centred at (x, y = feet), {@code size} pixels tall, seen from {@code viewYaw}
     * degrees round from the front and {@code viewPitch} degrees from above. Lying down (asleep),
     * it is seen from straight above, lying across the stage, centred.
     */
    public void render(GuiGraphics g, int x, int y, int size, float viewYaw, float viewPitch, float pt) {
        if (rig == null) return;
        float bodyYaw = Mth.rotLerp(pt, dummy.yBodyRotO, dummy.yBodyRot);
        boolean lying = dummy.isSleeping();
        // Standing, the renderer turns the model by -bodyYaw (we add it back); lying (no bed), by +bodyYaw.
        Quaternionf pose = lying
                ? new Quaternionf().rotateZ(Mth.PI).rotateX(-Mth.HALF_PI).rotateY((viewYaw - 35f - bodyYaw) * Mth.DEG_TO_RAD)
                : new Quaternionf().rotateZ(Mth.PI).rotateX(viewPitch * Mth.DEG_TO_RAD).rotateY((viewYaw + bodyYaw + 180f) * Mth.DEG_TO_RAD);
        float scale = size / 1.9f;
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(x, lying ? y - size * 0.45f : y, 150);
        ps.mulPoseMatrix(new Matrix4f().scaling(scale, scale, -scale));
        ps.mulPose(pose);
        if (lying) {
            // It lies from the feet (the origin) along the body's facing turned a quarter: back it up half a body.
            float a = bodyYaw * Mth.DEG_TO_RAD;
            ps.translate(Mth.cos(a) * 0.85f, 0f, -Mth.sin(a) * 0.85f);
        }
        Lighting.setupForEntityInInventory();
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        dispatcher.setRenderShadow(false);
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        // No name tag over it (names are drawn only while the HUD is shown).
        Minecraft mc = Minecraft.getInstance();
        boolean hideGui = mc.options.hideGui;
        mc.options.hideGui = true;
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

    /** Dozing or asleep: the "Z z z" belong over its head. */
    public boolean napping() {
        return rig != null && (dummy.isSleeping() || rig.motion.dozing);
    }

    public float time() {
        return rig.motion.time;
    }
}
