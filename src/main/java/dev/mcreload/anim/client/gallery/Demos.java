package dev.mcreload.anim.client.gallery;

import dev.mcreload.anim.client.rig.MotionState;
import dev.mcreload.anim.net.AnimPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** Everything the gallery shows, by category, with the script the dummy follows for each. */
public final class Demos {
    public static final String[] CATEGORIES = {"move", "landing", "combat", "idle", "emote", "menu", "rest", "reaction", "world"};
    public static final List<Demo> ALL = new ArrayList<>();

    private static final ItemStack NONE = ItemStack.EMPTY;

    static {
        // ---------------------------------------------------------------- moving
        add("stand", "move", 0, (s, t) -> {});
        add("walk", "move", 0, (s, t) -> s.speed(0.215));
        add("sprint", "move", 0, (s, t) -> { s.sprint(true); s.speed(0.28); });
        add("brake", "move", 90, (s, t) -> { boolean run = t < 50; s.sprint(run); s.speed(run ? 0.28 : 0); });
        add("sneak", "move", 0, (s, t) -> { s.crouch(true); s.speed(t % 80 < 50 ? 0.065 : 0); });
        add("crawl", "move", 0, (s, t) -> { s.crawl(true); s.speed(t % 100 < 70 ? 0.035 : 0); });
        add("jump", "move", 30, (s, t) -> { if (t == 2) s.jump(); });
        add("run_jump", "move", 30, (s, t) -> { s.sprint(true); s.speed(0.28); if (t == 4) s.jump(); });
        add("fall", "move", 60, (s, t) -> { if (t == 1) s.dropFrom(9); });
        add("skydive", "move", 50, (s, t) -> { if (t == 1) s.dropFrom(120); });
        add("elytra", "move", 200, (s, t) -> { s.glide(true); s.speed(1.2); });
        add("fly", "move", 0, (s, t) -> { s.fly(true); s.speed(t % 120 < 80 ? 0.5 : 0); });

        // ---------------------------------------------------------------- landings
        add("land_soft", "landing", 40, (s, t) -> { if (t == 1) s.dropFrom(2.2); });
        add("land_hard", "landing", 50, (s, t) -> { if (t == 1) s.dropFrom(6); });
        add("land_roll", "landing", 55, (s, t) -> {
            s.speed(0.22);
            if (t == 1) s.dropFrom(6);
            if (t == 8) s.remote(AnimPacket.LAND, MotionState.LAND_ROLL);
        });
        add("land_backroll", "landing", 55, (s, t) -> {
            s.speed(-0.12);
            if (t == 1) s.dropFrom(6);
            if (t == 8) s.remote(AnimPacket.LAND, MotionState.LAND_BACKROLL);
        });
        add("land_sword", "landing", 60, (s, t) -> {
            s.hold(new ItemStack(Items.DIAMOND_SWORD), NONE);
            if (t == 4) s.dropFrom(7);
            if (t == 12) s.remote(AnimPacket.LAND, MotionState.LAND_SWORD);
        });
        add("land_dual", "landing", 60, (s, t) -> {
            s.hold(new ItemStack(Items.DIAMOND_SWORD), new ItemStack(Items.IRON_SWORD));
            if (t == 4) s.dropFrom(7);
            if (t == 12) s.remote(AnimPacket.LAND, MotionState.LAND_SWORD);
        });

        // ---------------------------------------------------------------- weapons
        add("sword_draw", "combat", 90, (s, t) -> s.hold(t < 4 ? NONE : new ItemStack(Items.DIAMOND_SWORD), NONE));
        add("sword_draw_off", "combat", 90, (s, t) -> s.hold(new ItemStack(Items.IRON_SWORD), t < 4 ? NONE : new ItemStack(Items.DIAMOND_SWORD)));
        add("sword_guard", "combat", 80, (s, t) -> {
            s.hold(new ItemStack(Items.DIAMOND_SWORD), NONE);
            if (t == 20) s.swing(InteractionHand.MAIN_HAND);
            s.speed(t > 40 && t < 70 ? 0.2 : 0);
        });
        add("sword_combo", "combat", 70, (s, t) -> {
            s.hold(new ItemStack(Items.DIAMOND_SWORD), NONE);
            if (t == 6 || t == 18 || t == 30 || t == 42) s.swing(InteractionHand.MAIN_HAND);
        });
        add("sword_shield", "combat", 70, (s, t) -> {
            s.hold(new ItemStack(Items.IRON_SWORD), new ItemStack(Items.SHIELD));
            if (t == 6 || t == 40) s.swing(InteractionHand.MAIN_HAND);
        });
        add("sword_run", "combat", 0, (s, t) -> { s.hold(new ItemStack(Items.DIAMOND_SWORD), NONE); s.sprint(true); s.speed(0.28); });
        add("dual_run", "combat", 60, (s, t) -> {
            s.hold(new ItemStack(Items.DIAMOND_SWORD), new ItemStack(Items.IRON_SWORD));
            s.sprint(true);
            s.speed(0.28);
            if (t == 30) s.jump();
        });
        add("axe", "combat", 60, (s, t) -> {
            s.hold(new ItemStack(Items.IRON_AXE), NONE);
            if (t == 10 || t == 34) s.swing(InteractionHand.MAIN_HAND);
        });
        add("fists", "combat", 60, (s, t) -> { if (t == 6 || t == 14 || t == 22) s.swing(InteractionHand.MAIN_HAND); });

        // ---------------------------------------------------------------- idle
        idle("idle_stretch", MotionState.IDLE_STRETCH, NONE, NONE);
        idle("idle_look", MotionState.IDLE_LOOK, NONE, NONE);
        idle("idle_neck", MotionState.IDLE_NECK, NONE, NONE);
        idle("idle_kick", MotionState.IDLE_KICK, NONE, NONE);
        idle("idle_spin", MotionState.IDLE_ITEM_SPIN, new ItemStack(Items.DIAMOND_SWORD), NONE);
        idle("idle_inspect", MotionState.IDLE_ITEM_INSPECT, new ItemStack(Items.DIAMOND_SWORD), NONE);
        idle("idle_practice", MotionState.IDLE_ITEM_PRACTICE, new ItemStack(Items.IRON_SWORD), NONE);
        idle("idle_shoulder", MotionState.IDLE_ITEM_SHOULDER, new ItemStack(Items.IRON_PICKAXE), NONE);
        idle("idle_lean", MotionState.IDLE_ITEM_LEAN, new ItemStack(Items.IRON_SHOVEL), NONE);
        idle("idle_flint", MotionState.IDLE_FLINT, new ItemStack(Items.FLINT_AND_STEEL), NONE);
        idle("idle_bucket", MotionState.IDLE_BUCKET, new ItemStack(Items.WATER_BUCKET), NONE);
        idle("idle_rod", MotionState.IDLE_ROD, new ItemStack(Items.FISHING_ROD), NONE);
        idle("idle_lantern", MotionState.IDLE_LANTERN, new ItemStack(Items.LANTERN), NONE);
        idle("idle_block", MotionState.IDLE_BLOCK, new ItemStack(Items.OAK_LOG), NONE);
        idle("idle_dual_draw", MotionState.IDLE_DUAL_DRAW, new ItemStack(Items.DIAMOND_SWORD), new ItemStack(Items.IRON_SWORD));
        idle("idle_dual_spin", MotionState.IDLE_DUAL_SPIN, new ItemStack(Items.DIAMOND_SWORD), new ItemStack(Items.IRON_SWORD));
        ALL.add(new Demo("doze", "idle", 0, (s, t) -> { if (t == 2) s.motion().forceDoze(); }, MotionState::forceDoze));

        // ---------------------------------------------------------------- emotes
        for (int e = 0; e < MotionState.EMOTE_COUNT; e++) {
            final int emote = e;
            float d = MotionState.emoteDuration(e);
            int loop = d > 0f ? Math.round(d * 20f) + 30 : 0;
            ALL.add(new Demo("emote_" + MotionState.EMOTE_KEYS[e], "emote", loop,
                    (s, t) -> { if (t == 4) s.remote(AnimPacket.EMOTE, emote); },
                    m -> m.playEmote(emote)));
        }

        // ---------------------------------------------------------------- menus
        menu("menu_think", MotionState.SCREEN_THINK);
        menu("menu_rummage", MotionState.SCREEN_RUMMAGE);
        menu("menu_trade", MotionState.SCREEN_TRADE);

        // ---------------------------------------------------------------- sleeping and sitting
        sleep("sleep_starfish", MotionState.SLEEP_STARFISH);
        sleep("sleep_face_down", MotionState.SLEEP_FACE_DOWN);
        sleep("sleep_hands_behind", MotionState.SLEEP_HANDS_BEHIND);
        add("sit_back", "rest", 0, (s, t) -> { if (t == 2) s.remote(AnimPacket.SIT, 1); });
        add("sit_knee", "rest", 0, (s, t) -> { if (t == 2) s.remote(AnimPacket.SIT, 2); });

        // ---------------------------------------------------------------- reactions
        add("hit_front", "reaction", 40, (s, t) -> { if (t == 8) s.hurt(true); });
        add("hit_back", "reaction", 40, (s, t) -> { if (t == 8) s.hurt(false); });
        add("explosion", "reaction", 70, (s, t) -> { if (t == 8) s.motion().onExplosion(3.0); });
        add("explosion_sword", "reaction", 90, (s, t) -> {
            s.hold(new ItemStack(Items.DIAMOND_SWORD), NONE);
            if (t == 30) s.motion().onExplosion(3.0);
        });
        add("totem", "reaction", 80, (s, t) -> {
            s.hold(NONE, new ItemStack(Items.TOTEM_OF_UNDYING));
            if (t == 8) s.motion().onTotem();
        });

        // ---------------------------------------------------------------- only in the world
        for (String id : new String[]{"swim", "tread", "dive", "ladder", "boat", "minecart", "horse", "two_riders",
                "bow", "crossbow", "shield", "eat", "trident", "fishing", "mining", "place", "drop", "swap", "spell"}) {
            ALL.add(new Demo(id, "world", 0, null, null));
        }
    }

    private static void add(String id, String category, int loop, Demo.Script script) {
        ALL.add(new Demo(id, category, loop, script, null));
    }

    private static void idle(String id, int action, ItemStack main, ItemStack off) {
        int loop = Math.round(MotionState.idleDuration(action) * 20f) + 30;
        ALL.add(new Demo(id, "idle", loop, (s, t) -> {
            s.hold(main, off);
            if (t == 10) s.remote(AnimPacket.IDLE, action);
        }, main.isEmpty() && off.isEmpty() ? m -> m.forceIdle(action) : null));
    }

    private static void menu(String id, int pose) {
        // The trade starts with a tap on the sleeve: open it again now and then to see it.
        ALL.add(new Demo(id, "menu", pose == MotionState.SCREEN_TRADE ? 200 : 0, (s, t) -> {
            if (t == 4) s.remote(AnimPacket.STATE, pose << AnimPacket.SCREEN_SHIFT);
        }, null));
    }

    private static void sleep(String id, int style) {
        ALL.add(new Demo(id, "rest", 0, (s, t) -> {
            if (t == 1) s.remote(AnimPacket.SLEEP, style);
            s.sleep(t >= 2);
        }, null));
    }

    public static List<Demo> in(String category) {
        List<Demo> out = new ArrayList<>();
        for (Demo d : ALL) if (d.category().equals(category)) out.add(d);
        return out;
    }

    public static Demo byId(String id) {
        for (Demo d : ALL) if (d.id().equals(id)) return d;
        return null;
    }

    private Demos() {}
}
