package dev.mcreload.moves.client;

import dev.mcreload.moves.Bow;
import dev.mcreload.moves.MovesConfig;
import dev.mcreload.moves.Stamina;
import dev.mcreload.moves.compat.KinetrixBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * After the volley, with the right button still held, the player stays on one knee and aims
 * (docs/combat-spec.md, 6.2): a new arrow is drawn at once and they move at a crawl. The left
 * button looses the arrow early, with whatever draw it has, and the next one is drawn straight
 * after (6.3). Letting the button go shoots as usual and stands them up; so does putting the bow
 * away. The flow's own poses and steps (6.3) wait for their animations: until then every shot is
 * from the knee.
 */
public final class BowStance {
    /** The volley's tick by which the player is down on one knee. */
    private static final int KNELT = 10;
    /** Ticks a kneeling player may be seen without a drawn bow (between two arrows, lag) before they stand up. */
    private static final int GRACE = 5;
    /** Kneeling players: ticks of grace left without a drawn bow. */
    private static final Map<AbstractClientPlayer, int[]> KNEELING = new WeakHashMap<>();

    public static boolean kneeling(Player p) {
        return KNEELING.containsKey(p);
    }

    /**
     * The volley ended, having got as far as {@code tick}: with the right button still held the
     * player stays down. Other players' buttons are not known here: they stay down while they draw.
     * Returns whether the stance took over the animation.
     */
    static boolean volleyEnded(AbstractClientPlayer p, int tick) {
        Minecraft mc = Minecraft.getInstance();
        boolean held = p != mc.player || mc.options.keyUse.isDown();
        if (tick < KNELT || !held || !Bow.isBow(p.getMainHandItem()) || !p.onGround()) return false;
        KNEELING.put(p, new int[]{GRACE});
        MoveAnimator.playKneel(p);
        return true;
    }

    /** Every client tick, for every player: up again once the bow is no longer drawn. */
    static void tick(AbstractClientPlayer p) {
        int[] grace = KNEELING.get(p);
        if (grace == null) return;
        Minecraft mc = Minecraft.getInstance();
        boolean local = p == mc.player;
        if (p.isUsingItem() && Bow.isBow(p.getUseItem())) grace[0] = GRACE;
        else grace[0]--;
        if (grace[0] < 0 || !Bow.isBow(p.getMainHandItem()) || !p.onGround() || Stamina.of(p).move != null
                || (local && !mc.options.keyUse.isDown())) {
            KNEELING.remove(p);
            // A new move (a dodge...) has its own animation already.
            if (Stamina.of(p).move == null) MoveAnimator.stop(p, 4);
            return;
        }
        KinetrixBridge.wholeBody(p.getUUID(), 200);
        if (local) p.setSprinting(false);
    }

    /**
     * The left button while kneeling: the arrow leaves now, with the draw it has, and the next one
     * is drawn at once (the right button is still held). Returns whether the click was taken.
     */
    static boolean shoot(LocalPlayer p) {
        if (!kneeling(p)) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode != null && p.isUsingItem() && Bow.isBow(p.getUseItem())) {
            mc.gameMode.releaseUsingItem(p);
            mc.gameMode.useItem(p, InteractionHand.MAIN_HAND);
        }
        return true;
    }

    /** On one knee the player creeps, as when sneaking with a drawn bow. */
    static void movementInput(Player p, Input in) {
        if (!kneeling(p)) return;
        float k = MovesConfig.kneelMoveFactor();
        in.forwardImpulse *= k;
        in.leftImpulse *= k;
        in.jumping = false;
    }

    private BowStance() {}
}
