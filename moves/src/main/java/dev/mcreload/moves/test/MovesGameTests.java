package dev.mcreload.moves.test;

import com.mojang.authlib.GameProfile;
import dev.mcreload.moves.KinetrixMoves;
import dev.mcreload.moves.Move;
import dev.mcreload.moves.MoveServer;
import dev.mcreload.moves.MovesConfig;
import dev.mcreload.moves.Stamina;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The server's rules, checked on a real server: ./gradlew -p moves runGameTestServer.
 *
 * <p>Each test puts a survival player on a stone floor and ticks them like a connected player
 * (the client's part, the push and the animation, is the client self-test's).</p>
 */
@GameTestHolder(KinetrixMoves.MODID)
@PrefixGameTestTemplate(false)
public final class MovesGameTests {
    private static final String FLOOR = "floor";

    /** A dodge costs its energy; a second one during the first is refused and costs nothing. */
    @GameTest(template = FLOOR)
    public static void dodgeCostsEnergy(GameTestHelper h) {
        Script t = new Script(h);
        ServerPlayer p = t.player();
        Stamina s = Stamina.of(p);
        t.at(3, () -> {
            s.energy = 100f;
            t.check(MoveServer.request(p, Move.DODGE_LEFT, false), "dodge refused on the ground with full energy");
            t.close(s.energy, 100f - MovesConfig.costDodge(), "energy after a dodge");
            t.check(!MoveServer.request(p, Move.DODGE_RIGHT, false), "second dodge during the first was allowed");
            t.close(s.energy, 100f - MovesConfig.costDodge(), "energy after a refused dodge");
        }).run(5);
    }

    /** Not enough energy: no move, nothing spent. */
    @GameTest(template = FLOOR)
    public static void noEnergyNoMove(GameTestHelper h) {
        Script t = new Script(h);
        ServerPlayer p = t.player();
        Stamina s = Stamina.of(p);
        t.at(3, () -> {
            s.energy = MovesConfig.costRoll() - 1f;
            t.check(!MoveServer.request(p, Move.DODGE_BACK, false), "roll allowed without the energy for it");
            t.check(s.move == null, "a refused roll left a move running");
            t.close(s.energy, MovesConfig.costRoll() - 1f, "energy after a refused roll");
        }).run(5);
    }

    /** The slide needs a sprint. */
    @GameTest(template = FLOOR)
    public static void slideNeedsSprint(GameTestHelper h) {
        Script t = new Script(h);
        ServerPlayer p = t.player();
        t.at(3, () -> {
            t.check(!MoveServer.request(p, Move.SLIDE, false), "slide allowed while walking");
            p.setSprinting(true);
            t.check(MoveServer.request(p, Move.SLIDE, false), "slide refused while sprinting");
        }).run(5);
    }

    /** A blow in the dodge's untouchable frames misses; one after them lands. */
    @GameTest(template = FLOOR, timeoutTicks = 200)
    public static void dodgeFramesMissBlows(GameTestHelper h) {
        Script t = new Script(h).settle();
        ServerPlayer p = t.player();
        Zombie zombie = h.spawnWithNoFreeWill(EntityType.ZOMBIE, 5, 2, 3);
        Stamina s = Stamina.of(p);
        t.at(3, () -> MoveServer.request(p, Move.DODGE_LEFT, false));
        t.at(6, () -> {
            t.check(s.untouchable(), "not untouchable at dodge tick " + s.moveTick);
            p.hurt(p.damageSources().mobAttack(zombie), 4f);
            t.close(p.getHealth(), 20f, "health after a blow in the dodge frames");
        });
        t.at(14, () -> {
            t.check(!s.untouchable(), "still untouchable at dodge tick " + s.moveTick);
            p.invulnerableTime = 0;
            p.hurt(p.damageSources().mobAttack(zombie), 4f);
            t.check(p.getHealth() < 20f, "a blow after the dodge frames did not land");
        }).run(15);
    }

    /** An arrow flies through a player in their untouchable frames. */
    @GameTest(template = FLOOR, timeoutTicks = 200)
    public static void arrowsFlyThroughDodge(GameTestHelper h) {
        Script t = new Script(h).settle();
        ServerPlayer p = t.player();
        Stamina s = Stamina.of(p);
        t.at(3, () -> {
            MoveServer.request(p, Move.DODGE_LEFT, false);
            Arrow arrow = new Arrow(h.getLevel(), p.getX() - 2.5, p.getY() + 1.0, p.getZ());
            arrow.setBaseDamage(4.0);
            arrow.shoot(1, 0, 0, 1.2f, 0f);
            h.getLevel().addFreshEntity(arrow);
        });
        // Hold the dodge in its untouchable frames while the arrow passes.
        for (int i = 4; i < 14; i++) t.at(i, () -> s.moveTick = Move.DODGE_LEFT.iFrom + 1);
        t.at(14, () -> t.close(p.getHealth(), 20f, "health after an arrow through the dodge")).run(15);
    }

    /** A roll is 0.6 block high while tucked, then the player stands again. */
    @GameTest(template = FLOOR)
    public static void rollIsLow(GameTestHelper h) {
        Script t = new Script(h);
        ServerPlayer p = t.player();
        Stamina s = Stamina.of(p);
        int after = 3 + Move.DODGE_BACK.length + 1;
        t.at(3, () -> MoveServer.request(p, Move.DODGE_BACK, false));
        t.at(9, () -> t.close(p.getBbHeight(), 0.6f, "hitbox height mid-roll (tick " + s.moveTick + ")"));
        t.at(after, () -> {
            t.check(s.move == null, "the roll did not end");
            t.close(p.getBbHeight(), 1.8f, "hitbox height after the roll");
        }).run(after + 1);
    }

    /** Energy refills from food; with an empty hunger bar it does not. */
    @GameTest(template = FLOOR)
    public static void energyRefillsFromFood(GameTestHelper h) {
        Script t = new Script(h);
        ServerPlayer fed = t.player();
        ServerPlayer starving = t.player();
        t.at(3, () -> {
            Stamina.of(fed).energy = 50f;
            Stamina.of(starving).energy = 50f;
            starving.getFoodData().setFoodLevel(0);
        });
        t.at(43, () -> {
            // 2 s standing still, well fed: 4 x 2 (still) x 1.5 (fed) = 12 a second.
            t.check(Stamina.of(fed).energy > 60f, "energy after 2 s fed: " + Stamina.of(fed).energy);
            t.check(fed.getFoodData().getExhaustionLevel() > 0f || fed.getFoodData().getSaturationLevel() < 5f,
                    "refilling energy used no food");
            t.close(Stamina.of(starving).energy, 50f, "energy refilled while starving");
        }).run(44);
    }

    /** Golden apple: moves are free for a while; enchanted: full bar too. */
    @GameTest(template = FLOOR)
    public static void goldenApples(GameTestHelper h) {
        Script t = new Script(h);
        ServerPlayer p = t.player();
        Stamina s = Stamina.of(p);
        t.at(3, () -> {
            s.energy = 50f;
            s.ateGoldenApple(false);
            t.check(MoveServer.request(p, Move.DODGE_LEFT, false), "dodge refused after a golden apple");
            t.close(s.energy, 50f, "energy spent during golden apple time");
            s.energy = 10f;
            s.ateGoldenApple(true);
            t.close(s.energy, MovesConfig.maxEnergy(), "energy after an enchanted golden apple");
            t.check(s.freeTicks == MovesConfig.enchantedFreeTicks(), "free time after an enchanted apple: " + s.freeTicks);
        }).run(5);
    }

    /** Shields refill half a shield at a time, paid with energy. */
    @GameTest(template = FLOOR, timeoutTicks = 200)
    public static void shieldsRefill(GameTestHelper h) {
        Script t = new Script(h);
        ServerPlayer p = t.player();
        Stamina s = Stamina.of(p);
        int after = 3 + MovesConfig.shieldRegenTicks();
        t.at(3, () -> {
            s.shields = 5f;
            s.shieldTimer = 0;
        });
        t.at(after, () -> t.close(s.shields, 5f + MovesConfig.shieldRegen(), "shields after one refill")).run(after + 1);
    }

    // ------------------------------------------------------------------ helpers

    /**
     * A test as a list of steps by tick. Every tick first ticks the test's players like connected
     * players (their own tick, where the moves and the bars run), then runs that tick's steps.
     */
    private static final class Script {
        private final GameTestHelper h;
        private final List<ServerPlayer> players = new ArrayList<>();
        private final Map<Integer, List<Runnable>> steps = new HashMap<>();
        private int tick, base;

        Script(GameTestHelper h) {
            this.h = h;
        }

        /**
         * Steps start after the 3 seconds a newly joined player cannot be hurt (vanilla), so that
         * a blow that misses misses because of the move.
         */
        Script settle() {
            base = 62;
            return this;
        }

        /** A survival player on the floor (relative y 1 is the floor itself: the test block sits under it). */
        ServerPlayer player() {
            ServerPlayer p = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), new GameProfile(UUID.randomUUID(), "moves-test")) {
                @Override
                public boolean isSpectator() {
                    return false;
                }

                @Override
                public boolean isCreative() {
                    return false;
                }
            };
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            new EmbeddedChannel(connection);
            h.getLevel().getServer().getPlayerList().placeNewPlayer(connection, p);
            p.setGameMode(GameType.SURVIVAL);
            Vec3 at = h.absoluteVec(new Vec3(3.5, 2.0, 3.5));
            p.teleportTo(h.getLevel(), at.x, at.y, at.z, 0f, 0f);
            p.getFoodData().setFoodLevel(20);
            p.getFoodData().setSaturation(5f);
            players.add(p);
            return p;
        }

        Script at(int t, Runnable step) {
            steps.computeIfAbsent(base + t, k -> new ArrayList<>()).add(step);
            return this;
        }

        /** Runs the steps, passes at tick {@code end}; the players leave either way. */
        void run(int lastStep) {
            int end = base + lastStep;
            h.onEachTick(() -> {
                if (tick > end) return;
                tick++;
                try {
                    for (ServerPlayer p : players) p.doTick();
                    for (Runnable step : steps.getOrDefault(tick, List.of())) step.run();
                } catch (RuntimeException | AssertionError e) {
                    leave();
                    tick = end + 1;
                    throw e;
                }
                if (tick == end) {
                    leave();
                    h.succeed();
                }
            });
        }

        private void leave() {
            for (ServerPlayer p : players) h.getLevel().getServer().getPlayerList().remove(p);
        }

        void check(boolean ok, String what) {
            if (!ok) h.fail(what);
        }

        void close(float got, float want, String what) {
            if (Math.abs(got - want) > 0.01f) h.fail(what + ": " + got + ", expected " + want);
        }
    }

    private MovesGameTests() {}
}
