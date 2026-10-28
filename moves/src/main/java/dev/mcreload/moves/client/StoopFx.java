package dev.mcreload.moves.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.common.MinecraftForge;

import java.util.HashMap;
import java.util.Map;

/**
 * The enemy a player hops over stoops forward as if their blow had missed (docs/combat-spec.md, 5):
 * the whole body leans forward from the feet, eased in and out. Players keep their own animations.
 */
public final class StoopFx {
    /** How far forward the enemy leans at most (degrees). */
    private static final float LEAN = 32f;
    private static final int EASE_IN = 3, EASE_OUT = 5;
    /** Entity id -> {start, end} in client game ticks. */
    private static final Map<Integer, long[]> STOOPING = new HashMap<>();

    static void init() {
        MinecraftForge.EVENT_BUS.addListener(StoopFx::pre);
        MinecraftForge.EVENT_BUS.addListener(StoopFx::post);
    }

    public static void stoop(int entity, int delay, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long now = mc.level.getGameTime();
        STOOPING.put(entity, new long[]{now + delay, now + delay + ticks});
        STOOPING.values().removeIf(t -> t[1] < now - 20);
    }

    /** How much the entity leans now, 0..1. */
    private static float amount(LivingEntity e, float partialTick) {
        long[] t = STOOPING.get(e.getId());
        if (t == null) return 0f;
        float now = e.level().getGameTime() + partialTick;
        if (now < t[0] || now > t[1]) return 0f;
        float in = Mth.clamp((now - t[0]) / EASE_IN, 0f, 1f), out = Mth.clamp((t[1] - now) / EASE_OUT, 0f, 1f);
        float k = Math.min(in, out);
        return k * k * (3 - 2 * k);
    }

    private static final Map<LivingEntity, Boolean> PUSHED = new java.util.WeakHashMap<>();

    private static void pre(RenderLivingEvent.Pre<?, ?> event) {
        LivingEntity e = event.getEntity();
        if (e instanceof Player || STOOPING.isEmpty()) return;
        float k = amount(e, event.getPartialTick());
        if (k <= 0f) return;
        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        // Lean forward from the feet, along the way the body faces.
        float yaw = Mth.rotLerp(event.getPartialTick(), e.yBodyRotO, e.yBodyRot);
        ps.mulPose(Axis.YP.rotationDegrees(-yaw));
        ps.mulPose(Axis.XP.rotationDegrees(LEAN * k));
        ps.mulPose(Axis.YP.rotationDegrees(yaw));
        PUSHED.put(e, Boolean.TRUE);
    }

    private static void post(RenderLivingEvent.Post<?, ?> event) {
        if (PUSHED.remove(event.getEntity()) != null) event.getPoseStack().popPose();
    }

    private StoopFx() {}
}
