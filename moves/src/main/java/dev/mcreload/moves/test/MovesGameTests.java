package dev.mcreload.moves.test;

import com.mojang.authlib.GameProfile;
import dev.mcreload.moves.Guard;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
            // Against the blow itself, not vanilla's moment of invulnerability after a hit.
            float before = p.getHealth();
            p.invulnerableTime = 0;
            p.hurt(p.damageSources().mobAttack(zombie), 4f);
            t.close(p.getHealth(), before, "health after a blow in the dodge frames");
        });
        t.at(14, () -> {
            t.check(!s.untouchable(), "still untouchable at dodge tick " + s.moveTick);
            float before = p.getHealth();
            p.invulnerableTime = 0;
            p.hurt(p.damageSources().mobAttack(zombie), 4f);
            t.check(p.getHealth() < before, "a blow after the dodge frames did not land");
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

    /** Cooked food gives back a little energy (author: 25 of 100), raw food none; a golden apple frees moves for 15 s. */
    @GameTest(template = FLOOR)
    public static void cookedFoodGivesEnergy(GameTestHelper h) {
        Script t = new Script(h);
        ServerPlayer p = t.player();
        Stamina s = Stamina.of(p);
        t.at(3, () -> {
            s.energy = 10f;
            eat(p, new ItemStack(Items.BEEF));
            t.close(s.energy, 10f, "energy after raw beef");
            eat(p, new ItemStack(Items.COOKED_BEEF));
            t.close(s.energy, 10f + MovesConfig.cookedFoodEnergy(), "energy after a steak");
            eat(p, new ItemStack(Items.RABBIT_STEW));
            t.close(s.energy, 10f + 2 * MovesConfig.cookedFoodEnergy(), "energy after a rabbit stew");
            s.energy = MovesConfig.maxEnergy() - 5f;
            eat(p, new ItemStack(Items.BAKED_POTATO));
            t.close(s.energy, MovesConfig.maxEnergy(), "energy over the top of the bar");
            t.check(MovesConfig.appleFreeTicks() == 10 * 20, "golden apple free time " + MovesConfig.appleFreeTicks());
        }).run(5);
    }

    private static void eat(ServerPlayer p, ItemStack food) {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                new net.minecraftforge.event.entity.living.LivingEntityUseItemEvent.Finish(p, food, 0, food.copy()));
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

    // ------------------------------------------------------------------ the sword block

    /** A sword held across: a blow from the front loses 75 %, the sword 1 durability, the player 1 shield. */
    @GameTest(template = FLOOR, timeoutTicks = 200)
    public static void guardOneSword(GameTestHelper h) {
        Script t = new Script(h).settle();
        ServerPlayer p = t.player();
        Zombie zombie = t.zombieInFront();
        Stamina s = Stamina.of(p);
        t.at(1, () -> p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD)));
        t.at(3, () -> Guard.request(p, true));
        t.at(12, () -> {
            t.check(s.guard == Guard.Kind.SINGLE, "guard not up: " + s.guard);
            p.hurt(p.damageSources().mobAttack(zombie), 8f);
            t.close(p.getHealth(), 18f, "health after a blocked blow of 8");
            t.close(s.shields, 9f, "shields after a blocked blow");
            t.check(p.getMainHandItem().getDamageValue() == 1, "sword wear: " + p.getMainHandItem().getDamageValue());
        }).run(13);
    }

    /** Two swords crossed: 90 % held, each sword 1 durability, 1.5 shields. */
    @GameTest(template = FLOOR, timeoutTicks = 200)
    public static void guardTwoSwords(GameTestHelper h) {
        Script t = new Script(h).settle();
        ServerPlayer p = t.player();
        Zombie zombie = t.zombieInFront();
        Stamina s = Stamina.of(p);
        t.at(1, () -> {
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
            p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.IRON_SWORD));
        });
        t.at(3, () -> Guard.request(p, true));
        t.at(13, () -> {
            t.check(s.guard == Guard.Kind.DOUBLE, "double guard not up: " + s.guard);
            p.hurt(p.damageSources().mobAttack(zombie), 10f);
            t.close(p.getHealth(), 19f, "health after a blow of 10 on two swords");
            t.close(s.shields, 8.5f, "shields after a blow on two swords");
            t.check(p.getMainHandItem().getDamageValue() == 1 && p.getOffhandItem().getDamageValue() == 1,
                    "sword wear: " + p.getMainHandItem().getDamageValue() + ", " + p.getOffhandItem().getDamageValue());
        }).run(14);
    }

    /** A blow right after the blade is up does not land; nothing is spent. */
    @GameTest(template = FLOOR, timeoutTicks = 200)
    public static void guardPerfectParry(GameTestHelper h) {
        Script t = new Script(h).settle();
        ServerPlayer p = t.player();
        Zombie zombie = t.zombieInFront();
        Stamina s = Stamina.of(p);
        t.at(1, () -> p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD)));
        t.at(3, () -> Guard.request(p, true));
        t.at(9, () -> {
            t.check(Guard.inParryWindow(s), "not in the parry window at guard tick " + s.guardTick);
            p.hurt(p.damageSources().mobAttack(zombie), 8f);
            t.close(p.getHealth(), 20f, "health after a parried blow");
            t.close(s.shields, 10f, "shields after a parried blow");
            t.check(p.getMainHandItem().getDamageValue() == 0, "a parry wore the sword");
        }).run(10);
    }

    /** Tapping the button: no new parry window until the guard has been down a moment. */
    @GameTest(template = FLOOR, timeoutTicks = 200)
    public static void guardTapNoParry(GameTestHelper h) {
        Script t = new Script(h).settle();
        ServerPlayer p = t.player();
        Zombie zombie = t.zombieInFront();
        Stamina s = Stamina.of(p);
        t.at(1, () -> p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD)));
        t.at(3, () -> Guard.request(p, true));
        t.at(4, () -> Guard.request(p, false));
        t.at(5, () -> Guard.request(p, true));
        t.at(11, () -> {
            t.check(!Guard.inParryWindow(s), "a tapped guard parried");
            p.hurt(p.damageSources().mobAttack(zombie), 8f);
            t.close(p.getHealth(), 18f, "health after a blow on a tapped guard");
        }).run(12);
    }

    /** Blows from behind are not blocked. */
    @GameTest(template = FLOOR, timeoutTicks = 200)
    public static void guardNotFromBehind(GameTestHelper h) {
        Script t = new Script(h).settle();
        ServerPlayer p = t.player();
        Zombie zombie = h.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 2, 1);
        Stamina s = Stamina.of(p);
        t.at(1, () -> p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD)));
        t.at(3, () -> Guard.request(p, true));
        t.at(12, () -> {
            p.hurt(p.damageSources().mobAttack(zombie), 8f);
            t.close(p.getHealth(), 12f, "health after a blow from behind");
            t.close(s.shields, 10f, "shields after a blow from behind");
        }).run(13);
    }

    /** No guard with a shield in the other hand, nor without shields; the last shield drops it. */
    @GameTest(template = FLOOR, timeoutTicks = 200)
    public static void guardNeedsShieldsAndNoShield(GameTestHelper h) {
        Script t = new Script(h).settle();
        ServerPlayer p = t.player();
        Zombie zombie = t.zombieInFront();
        Stamina s = Stamina.of(p);
        t.at(1, () -> {
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
            p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
            Guard.request(p, true);
            t.check(s.guard == Guard.Kind.NONE, "guard with a shield in the other hand");
            p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            s.shields = 0f;
            Guard.request(p, true);
            t.check(s.guard == Guard.Kind.NONE, "guard without shields");
            s.shields = 1f;
            Guard.request(p, true);
            t.check(s.guard == Guard.Kind.SINGLE, "guard refused with a shield left");
        });
        t.at(12, () -> {
            p.hurt(p.damageSources().mobAttack(zombie), 8f);
            t.close(s.shields, 0f, "shields after the last one was spent");
            t.check(s.guard == Guard.Kind.NONE, "guard still up without shields");
        }).run(13);
    }

    // ------------------------------------------------------------------ the hop and the deflect

    /**
     * Vault over a zombie at point blank: its blow in the crouch is parried, it cannot hurt the
     * player until the landing, a later blow lands; it is shoved back 1-2 blocks; Speed I at the
     * end; the vault's energy.
     */
    @GameTest(template = FLOOR, timeoutTicks = 240)
    public static void hopOverZombie(GameTestHelper h) {
        Script t = new Script(h).settle();
        ServerPlayer p = t.player();
        Zombie zombie = h.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 2, 5);
        Stamina s = Stamina.of(p);
        t.at(2, () -> {
            // a mob that resists knockback is shoved as far (author, 2026-10-29)
            zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
            zombie.setTarget(p);
            zombie.setAggressive(true);
            zombie.setYRot(180f);
            zombie.setYHeadRot(180f);
            s.energy = 100f;
            t.check(MoveServer.request(p, Move.HOP, false, zombie.getId()), "vault refused over a zombie at point blank");
            t.close(s.energy, 100f - MovesConfig.costHop(), "energy after a vault");
        });
        t.at(5, () -> {
            p.hurt(p.damageSources().mobAttack(zombie), 6f);
            t.close(p.getHealth(), 20f, "health after the zombie's blow in the crouch");
        });
        // The test player does not jump: put them where the vault lands, beyond the zombie, before the
        // shove, so the zombie flies back the way they came instead of into them.
        t.at(Move.HOP_SHOVE, () -> {
            Vec3 beyond = h.absoluteVec(new Vec3(3.5, 2.0, 7.3));
            p.teleportTo(beyond.x, beyond.y, beyond.z);
        });
        t.at(Move.HOP_LAND, () -> {
            p.invulnerableTime = 0;
            p.hurt(p.damageSources().mobAttack(zombie), 6f);
            t.close(p.getHealth(), 20f, "health after the zombie's blow during the vault");
        });
        t.at(12 + Move.HOP_SHOVE, () -> {
            // Shoved back the way the player came from: here, where the test player stands.
            double moved = zombie.position().subtract(h.absoluteVec(new Vec3(3.5, 2.0, 5.5))).horizontalDistance();
            t.check(moved >= 1.8 && moved <= 3.5, "zombie shoved " + moved + " blocks (want about 2.3)");
        });
        t.at(2 + Move.HOP.length, () -> {
            t.check(p.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED), "no Speed after the vault");
            p.invulnerableTime = 0;
            p.hurt(p.damageSources().mobAttack(zombie), 6f);
            t.check(p.getHealth() < 20f, "the zombie still cannot hurt the player after the vault");
        }).run(3 + Move.HOP.length);
    }

    /**
     * Who can be vaulted (author): a zombie (even a calm one), a pig, a sheep; not a chicken, a baby
     * zombie (too small), an iron golem (too tall), nobody. R + W rolls instead.
     */
    @GameTest(template = FLOOR, timeoutTicks = 200)
    public static void vaultNeedsAMobOfSomeSize(GameTestHelper h) {
        Script t = new Script(h).settle();
        ServerPlayer p = t.player();
        Zombie calm = h.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 2, 5);
        net.minecraft.world.entity.animal.Pig pig = h.spawnWithNoFreeWill(EntityType.PIG, 2, 2, 5);
        net.minecraft.world.entity.animal.Sheep sheep = h.spawnWithNoFreeWill(EntityType.SHEEP, 4, 2, 5);
        net.minecraft.world.entity.animal.Chicken chicken = h.spawnWithNoFreeWill(EntityType.CHICKEN, 3, 2, 4);
        Zombie baby = h.spawnWithNoFreeWill(EntityType.ZOMBIE, 2, 2, 4);
        baby.setBaby(true);
        net.minecraft.world.entity.animal.IronGolem golem = h.spawnWithNoFreeWill(EntityType.IRON_GOLEM, 4, 2, 6);
        calm.setNoAi(true);
        t.at(2, () -> {
            t.check(dev.mcreload.moves.Hop.valid(p, calm), "a calm zombie cannot be vaulted");
            t.check(dev.mcreload.moves.Hop.valid(p, pig), "a pig cannot be vaulted");
            t.check(dev.mcreload.moves.Hop.valid(p, sheep), "a sheep cannot be vaulted");
            t.check(!dev.mcreload.moves.Hop.valid(p, chicken), "a chicken can be vaulted");
            t.check(!dev.mcreload.moves.Hop.valid(p, baby), "a baby zombie can be vaulted");
            t.check(!dev.mcreload.moves.Hop.valid(p, golem), "an iron golem can be vaulted");
            t.check(!MoveServer.request(p, Move.HOP, false, chicken.getId()), "vault over a chicken");
            t.check(!MoveServer.request(p, Move.HOP, false, -1), "vault over nobody");
            t.check(s(p).move == null, "a refused vault left a move running");
            t.check(MoveServer.request(p, Move.HOP, false, pig.getId()), "vault over a pig refused");
        }).run(3);
    }

    private static Stamina s(ServerPlayer p) {
        return Stamina.of(p);
    }

    /** Zombies that do not burn in the test's daylight (their health is measured). */
    private static void noSun(net.minecraft.world.entity.LivingEntity... mobs) {
        for (var m : mobs) m.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE, 20 * 60, 0, false, false));
    }

    // ------------------------------------------------------------------ the sword strikes

    /**
     * Finisher: a zombie at a fifth of its health, a sword, point blank. It is held (its blows do
     * not land, nor anyone else's: the player stands still, so they are untouchable), hurt but
     * alive after the stab, dead when the blade comes out; it costs its energy.
     */
    @GameTest(template = FLOOR, timeoutTicks = 200)
    public static void finisherKillsAWeakMob(GameTestHelper h) {
        Script t = new Script(h).settle();
        ServerPlayer p = t.player();
        Zombie zombie = h.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 2, 4);
        Zombie other = h.spawnWithNoFreeWill(EntityType.ZOMBIE, 2, 2, 3);
        Stamina s = Stamina.of(p);
        t.at(2, () -> {
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
            zombie.setHealth(zombie.getMaxHealth() * 0.2f);
            s.energy = 100f;
            t.check(MoveServer.request(p, Move.FINISHER, false, zombie.getId()), "finisher refused on a weak zombie at point blank");
            t.close(s.energy, 100f - MovesConfig.costFinisher(), "energy after a finisher");
        });
        t.at(6, () -> {
            p.hurt(p.damageSources().mobAttack(zombie), 6f);
            t.close(p.getHealth(), 20f, "health after the held zombie's blow");
            p.invulnerableTime = 0;
            p.hurt(p.damageSources().mobAttack(other), 6f);
            t.close(p.getHealth(), 20f, "health after another zombie's blow during the finisher");
        });
        t.at(3 + dev.mcreload.moves.Strikes.FINISH_STAB, () -> t.check(zombie.isAlive() && zombie.getHealth() < 4f,
                "after the stab the zombie should be hurt and alive: " + zombie.getHealth()));
        t.at(3 + dev.mcreload.moves.Strikes.FINISH_KILL, () -> t.check(!zombie.isAlive(), "the zombie survived the finisher"))
                .run(4 + dev.mcreload.moves.Strikes.FINISH_KILL);
    }

    /** The finisher with an axe (author, 2026-10-28): it kills a weak mob as the sword does; a pickaxe makes none. */
    @GameTest(template = FLOOR, timeoutTicks = 200)
    public static void finisherWithAnAxe(GameTestHelper h) {
        Script t = new Script(h).settle();
        ServerPlayer p = t.player();
        Zombie zombie = h.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 2, 4);
        t.at(2, () -> {
            zombie.setHealth(3f);
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
            t.check(!MoveServer.request(p, Move.FINISHER, false, zombie.getId()), "finisher with a pickaxe");
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
            t.check(MoveServer.request(p, Move.FINISHER, false, zombie.getId()), "finisher refused with an axe");
            t.check(!MoveServer.request(p, Move.DASH, false), "the dash strike is a sword's: refused with an axe");
        });
        t.at(3 + dev.mcreload.moves.Strikes.FINISH_KILL, () -> t.check(!zombie.isAlive(), "the zombie survived the axe's finisher"))
                .run(4 + dev.mcreload.moves.Strikes.FINISH_KILL);
    }

    /** No finisher on a healthy mob, without a sword, on a boss (by the tag or by its health), too far. */
    @GameTest(template = FLOOR, timeoutTicks = 200)
    public static void finisherRules(GameTestHelper h) {
        Script t = new Script(h).settle();
        ServerPlayer p = t.player();
        Zombie healthy = h.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 2, 4);
        Zombie big = h.spawnWithNoFreeWill(EntityType.ZOMBIE, 4, 2, 4);
        Zombie far = h.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 2, 7);
        Zombie near = h.spawnWithNoFreeWill(EntityType.ZOMBIE, 2, 2, 5);
        t.at(2, () -> {
            big.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(300.0);
            big.setHealth(20f);
            far.setHealth(2f);
            healthy.setHealth(healthy.getMaxHealth());
            t.check(!MoveServer.request(p, Move.FINISHER, false, healthy.getId()), "finisher without a sword");
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
            t.check(!MoveServer.request(p, Move.FINISHER, false, healthy.getId()), "finisher on a zombie at full health");
            t.check(!MoveServer.request(p, Move.FINISHER, false, big.getId()), "finisher on a boss (300 health)");
            t.check(!MoveServer.request(p, Move.FINISHER, false, far.getId()), "finisher 3.5 blocks away");
            // Point blank only (author): 1.8 blocks away (a step short of touching) the client offers none.
            near.setHealth(2f);
            t.check(!dev.mcreload.moves.Strikes.finishable(p, near, true), "finisher offered 1.8 blocks away");
            t.check(dev.mcreload.moves.Strikes.boss(big), "300 health is not a boss");
            t.check(!dev.mcreload.moves.Strikes.boss(healthy), "a zombie is a boss");
            t.check(s(p).move == null, "a refused finisher left a move running");
        }).run(3);
    }

    /**
     * Dash strike: sprinting with a sword, the blade finds the zombie in front as the lunge starts
     * (the hitbox moves with the player from 0.25 s), a crit; no dash walking.
     */
    @GameTest(template = FLOOR, timeoutTicks = 200)
    public static void dashStrikeCrits(GameTestHelper h) {
        Script t = new Script(h).settle();
        ServerPlayer p = t.player();
        Zombie zombie = h.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 2, 5);
        noSun(zombie);
        Stamina s = Stamina.of(p);
        t.at(2, () -> {
            // author, 2026-10-29, fifth message: S and the left button held, the right one: no run needed
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
            t.check(!MoveServer.request(p, Move.DASH, false), "dash strike with an axe");
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
            p.setSprinting(false);
            s.energy = 100f;
            t.check(MoveServer.request(p, Move.DASH, false), "dash strike refused standing with a sword");
            t.close(s.energy, 100f - MovesConfig.costDash(), "energy after a dash strike");
        });
        t.at(2 + dev.mcreload.moves.Strikes.DASH_REACH_FROM - 1, () -> t.close(zombie.getHealth(), 20f, "zombie hurt before the lunge"));
        t.at(3 + dev.mcreload.moves.Strikes.DASH_REACH_FROM, () -> {
            // An iron sword hits for 6; a crit for 9 (a zombie's 2 armour takes a little off).
            float lost = 20f - zombie.getHealth();
            t.check(lost >= 6f * 1.4f && lost <= 6f * 1.5f + 0.01f, "dash strike took " + lost + " health (a crit of 6 is 9)");
        });
        // Then 30 s before the next one (author); once it has passed, again.
        t.at(4 + Move.DASH.length, () -> {
            p.setSprinting(true);
            s.energy = 100f;
            t.check(!MoveServer.request(p, Move.DASH, false), "a second dash strike right after the first");
            t.check(s.dashCooldown > 20 * 25, "dash cooldown " + s.dashCooldown + " ticks (want about 30 s)");
            s.dashCooldown = 0;
            t.check(MoveServer.request(p, Move.DASH, false), "dash strike refused once the cooldown is over");
        }).run(5 + Move.DASH.length);
    }

    /** The dash's blade reaches a mob a block to the side of its line (author: a bigger hitbox), not one two blocks off. */
    @GameTest(template = FLOOR, timeoutTicks = 200)
    public static void dashStrikeWideHitbox(GameTestHelper h) {
        Script t = new Script(h).settle();
        ServerPlayer p = t.player();
        Zombie aside = h.spawnWithNoFreeWill(EntityType.ZOMBIE, 4, 2, 5);
        Zombie far = h.spawnWithNoFreeWill(EntityType.ZOMBIE, 6, 2, 5);
        noSun(aside, far);
        t.at(2, () -> {
            // 1.25 blocks to the side of the line: its side 0.95 from it (the old hitbox reached 0.7)
            aside.teleportTo(aside.getX() + 0.25, aside.getY(), aside.getZ());
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
            p.setSprinting(true);
            t.check(MoveServer.request(p, Move.DASH, false), "dash strike refused");
        });
        t.at(3 + dev.mcreload.moves.Strikes.DASH_REACH_TO, () -> {
            t.check(aside.getHealth() < 20f, "the dash missed a zombie 1.25 blocks to the side of its line");
            t.close(far.getHealth(), 20f, "a zombie 3 blocks to the side was hit");
        }).run(4 + dev.mcreload.moves.Strikes.DASH_REACH_TO);
    }

    // ------------------------------------------------------------------ the author's second message of 2026-10-29

    /**
     * The vault with a kick: over the zombie, its blows do not land, the kick takes 1 heart and flings
     * it back the way the player came, even though it resists knockback; its energy.
     */
    @GameTest(template = FLOOR, timeoutTicks = 240)
    public static void kickFlingsTheMob(GameTestHelper h) {
        Script t = new Script(h).settle();
        ServerPlayer p = t.player();
        Zombie zombie = h.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 2, 5);
        noSun(zombie);
        Stamina s = Stamina.of(p);
        t.at(2, () -> {
            zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
            s.energy = 100f;
            t.check(MoveServer.request(p, Move.KICK, false, zombie.getId()), "kick refused at a zombie in front");
            t.close(s.energy, 100f - MovesConfig.costKick(), "energy after a kick");
        });
        t.at(10, () -> {
            p.hurt(p.damageSources().mobAttack(zombie), 6f);
            t.close(p.getHealth(), 20f, "health after the zombie's blow during the kick");
            // the test player does not fly: put them where the kick is made from, beyond the zombie
            Vec3 beyond = h.absoluteVec(new Vec3(3.5, 2.0, 7.0));
            p.teleportTo(beyond.x, beyond.y, beyond.z);
        });
        // The kick lands in the player's tick of move tick HIT (script tick HIT + 2): its own damage,
        // measured across that tick (the tests run side by side, a stray arrow may find the zombie).
        float[] before = new float[1];
        t.at(1 + dev.mcreload.moves.Kick.HIT, () -> before[0] = zombie.getHealth());
        t.at(2 + dev.mcreload.moves.Kick.HIT, () -> {
            float lost = before[0] - zombie.getHealth();
            t.check(lost >= 1.8f && lost <= 2.01f, "the kick took " + lost + " health (want 1 heart)");
        });
        t.at(16 + dev.mcreload.moves.Kick.HIT, () -> {
            double moved = h.absoluteVec(new Vec3(3.5, 2.0, 5.5)).z - zombie.getZ();
            t.check(moved >= 2.0, "the kicked zombie went " + moved + " blocks back (want 2 or more)");
        }).run(17 + dev.mcreload.moves.Kick.HIT);
    }

    /**
     * The attack out of a slide: only while sliding, with a sword or an axe; a half turn, so the
     * zombie behind (the one slid past) takes a crit, the one ahead nothing; then 15 s (author: half
     * the dash's 30).
     */
    @GameTest(template = FLOOR, timeoutTicks = 200)
    public static void slideAttackCrits(GameTestHelper h) {
        Script t = new Script(h).settle();
        ServerPlayer p = t.player();
        Zombie behind = h.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 2, 1);
        Zombie ahead = h.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 2, 6);
        noSun(behind, ahead);
        Stamina s = Stamina.of(p);
        t.at(2, () -> {
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
            t.check(!MoveServer.request(p, Move.SLIDE_ATTACK, false), "attack out of a slide without a slide");
            p.setSprinting(true);
            t.check(MoveServer.request(p, Move.SLIDE, false), "slide refused");
            s.energy = 100f;
            t.check(MoveServer.request(p, Move.SLIDE_ATTACK, false), "attack out of the slide refused");
            t.close(s.energy, 100f - MovesConfig.costSlideAttack(), "energy after the attack");
        });
        t.at(3 + dev.mcreload.moves.Strikes.SLIDE_HIT, () -> {
            float lost = 20f - behind.getHealth();
            t.check(lost >= 8f, "the zombie slid past took " + lost + " (a crit of an iron sword is 9)");
            t.close(ahead.getHealth(), 20f, "the zombie behind the turned player was hit");
        });
        t.at(4 + Move.SLIDE_ATTACK.length, () -> {
            p.setSprinting(true);
            t.check(MoveServer.request(p, Move.SLIDE, false), "second slide refused");
            t.check(!MoveServer.request(p, Move.SLIDE_ATTACK, false), "a second attack out of a slide within 15 s");
            int left = s.slideAttackCooldown;
            t.check(left > 20 * 12 && left <= 20 * 15, "cooldown " + left + " ticks (want about 15 s)");
        }).run(5 + Move.SLIDE_ATTACK.length);
    }

    /** R held in a fall: tucked, the landing hurts 70 % less (another player's the same fall does not); then the roll. */
    @GameTest(template = FLOOR, timeoutTicks = 200)
    public static void fallRollCutsDamage(GameTestHelper h) {
        Script t = new Script(h).settle();
        ServerPlayer p = t.player();
        ServerPlayer other = t.player();
        Stamina s = Stamina.of(p);
        t.at(2, () -> {
            p.fallDistance = 1f;
            t.check(!MoveServer.request(p, Move.FALL_TUCK, false), "tucked after a hop of a block");
            p.fallDistance = 10f;
            t.check(MoveServer.request(p, Move.FALL_TUCK, false), "tuck refused in a 10-block fall");
            p.causeFallDamage(10f, 1f, p.damageSources().fall());
            other.causeFallDamage(10f, 1f, other.damageSources().fall());
            t.close(other.getHealth(), 13f, "health after a 10-block fall untucked");
            t.close(p.getHealth(), 17f, "health after a 10-block fall tucked (7 damage less 70 %, rounded up)");
            s.energy = 100f;
            t.check(MoveServer.request(p, Move.FALL_ROLL, false), "roll out of the tuck refused");
            t.check(s.move == Move.FALL_ROLL, "not rolling");
            t.close(s.energy, 100f - MovesConfig.costFallRoll(), "energy after the roll (every move eats energy)");
            // up half again as quickly as before (author: 50 % faster): the stun ends at the push up
            t.check(Move.FALL_ROLL.recoverFrom() <= 21, "the stun lasts " + Move.FALL_ROLL.recoverFrom() + " ticks (was 30)");
        }).run(3);
    }

    /**
     * W + Space + R (author, 2026-10-29, fourth message): the kick and the deep jump start from the
     * ground or a jump already begun, not out of a fall; the deep jump's energy, its low untouchable roll.
     */
    @GameTest(template = FLOOR, timeoutTicks = 200)
    public static void kickAndDiveFromAJump(GameTestHelper h) {
        Script t = new Script(h).settle();
        ServerPlayer p = t.player();
        Zombie zombie = t.zombieInFront();
        noSun(zombie);
        Stamina s = Stamina.of(p);
        t.at(2, () -> {
            p.setOnGround(true);
            MoveServer.seen(p);
            s.energy = 100f;
            t.check(MoveServer.request(p, Move.DIVE, false), "the deep jump refused from the ground");
            t.close(s.energy, 100f - MovesConfig.costDive(), "energy after the deep jump");
        });
        t.at(2 + Move.DIVE_TOUCH + 1, () -> t.check(s.untouchable() && s.low(), "the deep jump's roll is not low and untouchable"));
        t.at(4 + Move.DIVE.length, () -> {
            // a jump already begun: off the ground a few ticks, rising
            p.setOnGround(true);
            MoveServer.seen(p);
            p.setOnGround(false);
            p.fallDistance = 0f;
            t.check(MoveServer.request(p, Move.KICK, false, zombie.getId()), "the kick refused in a jump");
            MoveServer.cancel(p, Move.KICK);
            p.fallDistance = 4f;
            t.check(!MoveServer.request(p, Move.DIVE, false), "the deep jump out of a fall");
            p.fallDistance = 0f;
        }).run(5 + Move.DIVE.length);
    }

    /**
     * The edge just gone over (author, 2026-10-29: sliding off a drop the grab did not work): the
     * player slid off the top of a two-block wall, two blocks out and below its top, catches the edge
     * behind them; it costs energy, so do the hops along it, the hang after them does not; too far out, nothing.
     */
    @GameTest(template = FLOOR, timeoutTicks = 200)
    public static void ledgeCaughtAfterSlidingOff(GameTestHelper h) {
        Script t = new Script(h).settle();
        ServerPlayer p = t.player();
        Stamina s = Stamina.of(p);
        int south = dev.mcreload.moves.Ledge.id(net.minecraft.core.Direction.SOUTH);
        t.at(2, () -> {
            for (int x = 1; x <= 5; x++) {
                for (int z = 5; z <= 6; z++) {
                    for (int y = 2; y <= 3; y++) h.setBlock(x, y, z, net.minecraft.world.level.block.Blocks.STONE);
                }
            }
            // on the top at its north edge, then slid off it northwards
            Vec3 top = h.absoluteVec(new Vec3(3.5, 4.0, 5.3));
            p.teleportTo(h.getLevel(), top.x, top.y, top.z, 180f, 0f);
            p.setOnGround(true);
            MoveServer.seen(p);
            Vec3 far = h.absoluteVec(new Vec3(3.5, 3.0, 0.5));
            p.teleportTo(far.x, far.y, far.z);
            p.setOnGround(false);
            t.check(!MoveServer.request(p, Move.LEDGE_HANG, false, south), "caught an edge 4 blocks away");
            Vec3 off = h.absoluteVec(new Vec3(3.5, 3.5, 3.0));
            p.teleportTo(off.x, off.y, off.z);
            s.energy = 100f;
            t.check(MoveServer.request(p, Move.LEDGE_HANG, false, south), "the edge just slid off was not caught");
            t.check(Math.abs(s.ledgeTop - top.y) < 0.01, "edge top " + s.ledgeTop + " (want " + top.y + ")");
            t.close(s.energy, 100f - MovesConfig.costLedgeGrab(), "energy after catching the edge");
            int along = dev.mcreload.moves.Ledge.hopTarget(net.minecraft.core.Direction.SOUTH, 0);
            t.check(MoveServer.request(p, Move.LEDGE_HOP_LEFT, false, along), "a hop along the edge refused");
            t.check(MoveServer.request(p, Move.LEDGE_HOP_LEFT, false, along), "the next hop refused while the first plays");
            t.close(s.energy, 100f - MovesConfig.costLedgeGrab() - 2 * MovesConfig.costLedgeHop(), "energy after two hops");
            t.check(MoveServer.request(p, Move.LEDGE_HANG, false, south), "back to the hang refused");
            t.close(s.energy, 100f - MovesConfig.costLedgeGrab() - 2 * MovesConfig.costLedgeHop(), "energy after hanging again (free)");
        }).run(3);
    }

    /**
     * The fifth message's hops along a ledge (author: on to the next blocks, every side of a block):
     * round the end of a one-block pillar to its side, into the wall of an inside corner, onto the
     * next block a step up; the server takes the new edge from the hop's request.
     */
    @GameTest(template = FLOOR, timeoutTicks = 200)
    public static void ledgeCornersAndSteps(GameTestHelper h) {
        Script t = new Script(h).settle();
        ServerPlayer p = t.player();
        Stamina s = Stamina.of(p);
        net.minecraft.core.Direction south = net.minecraft.core.Direction.SOUTH;
        t.at(2, () -> {
            net.minecraft.world.level.block.state.BlockState stone = net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
            // a pillar three high at x 2, z 5; a wall to its east two higher at x 3 (a step up), and at x 5 a
            // wall coming out towards the player (an inside corner with the wall along z 5)
            for (int y = 2; y <= 4; y++) h.setBlock(2, y, 5, stone);
            for (int y = 2; y <= 5; y++) h.setBlock(3, y, 5, stone);
            for (int y = 2; y <= 5; y++) h.setBlock(4, y, 5, stone);
            for (int y = 2; y <= 6; y++) h.setBlock(5, y, 4, stone);
            for (int y = 2; y <= 6; y++) h.setBlock(5, y, 5, stone);
            double top = h.absoluteVec(new Vec3(0, 5.0, 0)).y;
            Vec3 hang = h.absoluteVec(new Vec3(2.5, 5.0 - dev.mcreload.moves.Ledge.HANG, 5.0 - 0.301));
            p.teleportTo(hang.x, hang.y, hang.z);
            // hanging on the pillar's front (north face, the wall to the south): to the west, round its corner
            dev.mcreload.moves.Ledge.Step round = dev.mcreload.moves.Ledge.next(p, p.position(), south, top, false, true);
            t.check(round != null && round.via() != null && round.spot().dir() == net.minecraft.core.Direction.EAST,
                    "round the pillar's west corner: " + round);
            // to the east, the next block a step up
            dev.mcreload.moves.Ledge.Step up = dev.mcreload.moves.Ledge.next(p, p.position(), south, top, true, true);
            t.check(up != null && up.via() == null && Math.abs(up.spot().top() - (top + 1)) < 0.01, "a step up onto the next block: " + up);
            // the server: hanging, then the hop to the step up (one block higher, the same way)
            p.fallDistance = 4f;
            t.check(MoveServer.request(p, Move.LEDGE_HANG, false, dev.mcreload.moves.Ledge.id(south)), "the pillar's edge not caught");
            t.check(MoveServer.request(p, Move.LEDGE_HOP_LEFT, false, dev.mcreload.moves.Ledge.hopTarget(south, 1.0)), "the hop up refused");
            t.check(Math.abs(s.ledgeTop - (top + 1)) < 0.01 && s.ledgeDir == dev.mcreload.moves.Ledge.id(south),
                    "the server's edge after the hop up: top " + s.ledgeTop + ", way " + s.ledgeDir);
            t.check(!MoveServer.request(p, Move.LEDGE_HOP_LEFT, false, dev.mcreload.moves.Ledge.id(south) | 9 << 2), "a hop of four blocks up");
            // on the higher wall at x 4, to the east: the wall at x 5 comes out at the side (an inside corner)
            Vec3 high = h.absoluteVec(new Vec3(4.5, 6.0 - dev.mcreload.moves.Ledge.HANG, 5.0 - 0.301));
            p.teleportTo(high.x, high.y, high.z);
            dev.mcreload.moves.Ledge.Step in = dev.mcreload.moves.Ledge.next(p, p.position(), south, top + 1, true, true);
            t.check(in != null && in.spot().dir() == net.minecraft.core.Direction.EAST && in.via() == null,
                    "into the inside corner, facing the wall at the side: " + in);
        }).run(3);
    }

    /** Round a one-block pillar by its corners: four hops to the right go round it and back to the face it started on. */
    @GameTest(template = FLOOR, timeoutTicks = 200)
    public static void ledgeRoundAPillar(GameTestHelper h) {
        Script t = new Script(h).settle();
        ServerPlayer p = t.player();
        t.at(2, () -> {
            for (int y = 2; y <= 4; y++) h.setBlock(3, y, 4, net.minecraft.world.level.block.Blocks.STONE);
            double top = h.absoluteVec(new Vec3(0, 5.0, 0)).y;
            Vec3 start = h.absoluteVec(new Vec3(3.5, 5.0 - dev.mcreload.moves.Ledge.HANG, 4.0 - 0.301));
            p.teleportTo(start.x, start.y, start.z);
            net.minecraft.core.Direction d = net.minecraft.core.Direction.SOUTH;
            StringBuilder way = new StringBuilder(d.toString());
            for (int i = 0; i < 4; i++) {
                dev.mcreload.moves.Ledge.Step st = dev.mcreload.moves.Ledge.next(p, p.position(), d, top, false, true);
                if (st == null) {
                    t.check(false, "hop " + (i + 1) + " round the pillar found nowhere to go, after " + way);
                    return;
                }
                d = st.spot().dir();
                Vec3 at = st.spot().at();
                p.teleportTo(at.x, at.y, at.z);
                way.append(" -> ").append(d).append(String.format(java.util.Locale.ROOT, " (%.2f, %.2f)",
                        at.x - h.absoluteVec(Vec3.ZERO).x, at.z - h.absoluteVec(Vec3.ZERO).z));
            }
            t.check(d == net.minecraft.core.Direction.SOUTH && p.position().distanceTo(start) < 0.7, "round the pillar: " + way);
        }).run(3);
    }

    /** The celebrate emote: free, 8 s, nothing else starts over it; moving (a cancel) ends it early. */
    @GameTest(template = FLOOR, timeoutTicks = 200)
    public static void celebrateEmote(GameTestHelper h) {
        Script t = new Script(h).settle();
        ServerPlayer p = t.player();
        Stamina s = Stamina.of(p);
        t.at(2, () -> {
            p.setOnGround(true);
            MoveServer.seen(p);
            s.energy = 10f;
            t.check(MoveServer.request(p, Move.CELEBRATE, false), "the emote refused on the ground");
            t.close(s.energy, 10f, "energy after the emote (it is free)");
            t.check(Move.CELEBRATE.length >= 160, "the emote lasts " + Move.CELEBRATE.length + " ticks (author: 8 s)");
            t.check(!MoveServer.request(p, Move.DODGE_LEFT, false), "a dodge over the emote (it must be broken off first)");
        });
        t.at(20, () -> {
            t.check(s.move == Move.CELEBRATE, "the emote ended before its time");
            MoveServer.cancel(p, Move.CELEBRATE);
            t.check(s.move == null, "moving did not end the emote");
        }).run(21);
    }

    /**
     * The fast run (author, 2026-10-29, fifth message): Ctrl held while sprinting, 7 blocks a second
     * (the speed attribute), a hundredth of the bar a second, no refill meanwhile; without the key, the
     * vanilla sprint and the bar refills.
     */
    @GameTest(template = FLOOR, timeoutTicks = 200)
    public static void fastRun(GameTestHelper h) {
        Script t = new Script(h).settle();
        ServerPlayer p = t.player();
        Stamina s = Stamina.of(p);
        float[] e = new float[1];
        t.at(2, () -> {
            s.energy = 50f;
            s.runKey = true;
            p.setSprinting(true);
        });
        t.at(4, () -> {
            double speed = p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
            double bps = speed / 0.13 * dev.mcreload.moves.FastRun.VANILLA_SPRINT;
            t.check(Math.abs(bps - 7.0) < 0.05, "the fast run goes " + bps + " blocks a second (want 7)");
            e[0] = s.energy;
        });
        t.at(24, () -> {
            float spent = e[0] - s.energy;
            t.check(Math.abs(spent - 1f) < 0.06, "the fast run ate " + spent + " energy in a second (want 1 of 100)");
            s.runKey = false;
        });
        t.at(26, () -> {
            double speed = p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
            t.check(Math.abs(speed - 0.13) < 1.0E-3, "the sprint without the key: speed " + speed);
            e[0] = s.energy;
        });
        t.at(46, () -> t.check(s.energy > e[0], "the bar does not refill after the fast run")).run(47);
    }

    /**
     * The plunge (author, 2026-10-29, fifth message): falling with a sword or an axe, the blade held
     * ready; the landing hurts 20 % less, drives it in (energy), a short stun; bare-handed or in a
     * short drop, nothing.
     */
    @GameTest(template = FLOOR, timeoutTicks = 200)
    public static void plungeCutsDamage(GameTestHelper h) {
        Script t = new Script(h).settle();
        ServerPlayer p = t.player();
        ServerPlayer other = t.player();
        Stamina s = Stamina.of(p);
        t.at(2, () -> {
            p.setOnGround(false);
            p.fallDistance = 10f;
            t.check(!MoveServer.request(p, Move.PLUNGE_FALL, false), "the blade held ready bare-handed");
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
            p.fallDistance = 2f;
            t.check(!MoveServer.request(p, Move.PLUNGE_FALL, false), "the blade held ready in a drop that does not hurt");
            p.fallDistance = 10f;
            t.check(MoveServer.request(p, Move.PLUNGE_FALL, false), "the blade not held ready in a 10-block fall with an axe");
            p.causeFallDamage(10f, 1f, p.damageSources().fall());
            other.causeFallDamage(10f, 1f, other.damageSources().fall());
            t.close(other.getHealth(), 13f, "health after a 10-block fall bare-handed");
            t.close(p.getHealth(), 14f, "health after it with the blade (7 damage less 20 %, rounded up)");
            s.energy = 100f;
            t.check(MoveServer.request(p, Move.PLUNGE, false), "the plunge on landing refused");
            t.close(s.energy, 100f - MovesConfig.costPlunge(), "energy after the plunge");
            t.check(Move.PLUNGE.recoverFrom() == dev.mcreload.moves.Plunge.STAND && Move.PLUNGE.recoverFrom() <= 15,
                    "the stun lasts " + Move.PLUNGE.recoverFrom() + " ticks (a short one)");
        }).run(3);
    }

    /**
     * The axe whirlwind (author, 2026-10-29, fifth message): an axe only; three turns, each hitting
     * every mob round the player once (the one behind too), not the one out of reach; then 45 s.
     */
    @GameTest(template = FLOOR, timeoutTicks = 240)
    public static void whirlwindHitsAllRound(GameTestHelper h) {
        Script t = new Script(h).settle();
        ServerPlayer p = t.player();
        Zombie front = h.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 2, 5);
        Zombie back = h.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 2, 1);
        Zombie far = h.spawnWithNoFreeWill(EntityType.ZOMBIE, 8, 2, 3);
        noSun(front, back, far);
        Stamina s = Stamina.of(p);
        int[] hits = new int[2];
        float[] last = new float[2];
        t.at(2, () -> {
            for (Zombie z : new Zombie[]{front, back, far}) {
                z.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(200);
                z.setHealth(200);
                z.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
            }
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
            t.check(!MoveServer.request(p, Move.WHIRL, false), "the whirlwind with a sword");
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
            s.energy = 100f;
            t.check(MoveServer.request(p, Move.WHIRL, false), "the whirlwind refused with an axe");
            t.close(s.energy, 100f - MovesConfig.costWhirl(), "energy after the whirlwind");
            last[0] = front.getHealth();
            last[1] = back.getHealth();
        });
        for (int i = 3; i < 4 + Move.WHIRL.length; i++) {
            t.at(i, () -> {
                // the zombies are held where they stood, so each turn finds them again
                Vec3 fp = h.absoluteVec(new Vec3(3.5, 2, 5.5)), bp = h.absoluteVec(new Vec3(3.5, 2, 1.5));
                if (front.getHealth() < last[0]) hits[0]++;
                if (back.getHealth() < last[1]) hits[1]++;
                last[0] = front.getHealth();
                last[1] = back.getHealth();
                front.teleportTo(fp.x, fp.y, fp.z);
                back.teleportTo(bp.x, bp.y, bp.z);
                Vec3 me = h.absoluteVec(new Vec3(3.5, 2, 3.5));
                p.teleportTo(me.x, me.y, me.z);
            });
        }
        t.at(5 + Move.WHIRL.length, () -> {
            t.check(hits[0] == 3 && hits[1] == 3, "hits a turn: in front " + hits[0] + ", behind " + hits[1] + " (want 3 each)");
            t.close(far.getHealth(), 200f, "the zombie out of reach was hit");
            s.energy = 100f;
            t.check(!MoveServer.request(p, Move.WHIRL, false), "a second whirlwind right after the first");
            t.check(s.whirlCooldown > 20 * 40, "whirlwind cooldown " + s.whirlCooldown + " ticks (want 45 s)");
        }).run(6 + Move.WHIRL.length);
    }

    /** The roll kick (Ctrl, W, Space, R at a mob): its energy, the kick into the mob at RK_HIT, nothing hurts the player until the landing. */
    @GameTest(template = FLOOR, timeoutTicks = 240)
    public static void rollKickFlingsTheMob(GameTestHelper h) {
        Script t = new Script(h).settle();
        ServerPlayer p = t.player();
        Zombie zombie = h.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 2, 5);
        noSun(zombie);
        Stamina s = Stamina.of(p);
        t.at(2, () -> {
            p.setOnGround(true);
            MoveServer.seen(p);
            s.energy = 100f;
            t.check(MoveServer.request(p, Move.ROLL_KICK, false, zombie.getId()), "the roll kick refused at a zombie in front");
            t.close(s.energy, 100f - MovesConfig.costRollKick(), "energy after the roll kick");
        });
        t.at(8, () -> {
            p.hurt(p.damageSources().mobAttack(zombie), 6f);
            t.close(p.getHealth(), 20f, "health after the zombie's blow in the flip");
            Vec3 beyond = h.absoluteVec(new Vec3(3.5, 2.0, 7.0));
            p.teleportTo(beyond.x, beyond.y, beyond.z);
        });
        float[] before = new float[1];
        t.at(1 + dev.mcreload.moves.Kick.RK_HIT, () -> before[0] = zombie.getHealth());
        t.at(2 + dev.mcreload.moves.Kick.RK_HIT, () -> {
            float lost = before[0] - zombie.getHealth();
            t.check(lost >= 1.8f && lost <= 2.01f, "the roll kick took " + lost + " health (want 1 heart)");
        }).run(3 + dev.mcreload.moves.Kick.RK_HIT);
    }

    /**
     * The ledge: a two-block wall in front; caught (the edge's top remembered), hand over hand along
     * it, up onto it, the climb passing through the edge's corner; nothing to catch in the open.
     */
    @GameTest(template = FLOOR, timeoutTicks = 200)
    public static void ledgeCatchAndClimb(GameTestHelper h) {
        Script t = new Script(h).settle();
        ServerPlayer p = t.player();
        Stamina s = Stamina.of(p);
        int south = dev.mcreload.moves.Ledge.id(net.minecraft.core.Direction.SOUTH);
        t.at(1, () -> t.check(!MoveServer.request(p, Move.LEDGE_HANG, false, south), "caught an edge in the open"));
        t.at(2, () -> {
            for (int x = 1; x <= 5; x++) {
                for (int y = 2; y <= 3; y++) h.setBlock(x, y, 5, net.minecraft.world.level.block.Blocks.STONE);
            }
            Vec3 hang = h.absoluteVec(new Vec3(3.5, 4.0 - dev.mcreload.moves.Ledge.HANG, 5.0 - 0.301));
            p.teleportTo(hang.x, hang.y, hang.z);
            p.fallDistance = 6f;
            t.check(MoveServer.request(p, Move.LEDGE_HANG, false, south), "the edge of a two-block wall was not caught");
            t.check(Math.abs(s.ledgeTop - h.absoluteVec(new Vec3(0, 4.0, 0)).y) < 0.01, "edge top " + s.ledgeTop);
            t.check(p.fallDistance == 0f, "the fall before the catch still counts");
            t.check(MoveServer.request(p, Move.LEDGE_SHIMMY_LEFT, false, south), "hand over hand refused");
            t.check(MoveServer.request(p, Move.LEDGE_HANG, false, south), "back to the hang refused");
            t.check(!MoveServer.request(p, Move.DODGE_LEFT, false), "a dodge while hanging");
            s.energy = 100f;
            t.check(MoveServer.request(p, Move.LEDGE_CLIMB, false, south), "the climb refused");
            t.close(s.energy, 100f - MovesConfig.costLedgeClimb(), "energy after the climb");
        });
        t.at(4, () -> t.check(p.noPhysics, "the climb does not pass through the edge's corner"));
        t.at(4 + Move.LEDGE_CLIMB.length, () -> t.check(s.move == null && !p.noPhysics, "the climb did not end")).run(5 + Move.LEDGE_CLIMB.length);
    }

    /**
     * The hand reaches only the edge right in front (author, 2026-10-29: from the far side of a block
     * it does not reach): 0.2 block from the wall, caught; 0.6 away, not; the automatic hang only
     * with the toes at the drop.
     */
    @GameTest(template = FLOOR, timeoutTicks = 200)
    public static void ledgeOnlyAtTheEdge(GameTestHelper h) {
        Script t = new Script(h).settle();
        ServerPlayer p = t.player();
        t.at(2, () -> {
            for (int x = 1; x <= 5; x++) {
                for (int z = 5; z <= 6; z++) {
                    for (int y = 2; y <= 3; y++) h.setBlock(x, y, z, net.minecraft.world.level.block.Blocks.STONE);
                }
            }
            double y = h.absoluteVec(new Vec3(0, 4.0 - dev.mcreload.moves.Ledge.HANG, 0)).y;
            Vec3 near = h.absoluteVec(new Vec3(3.5, 0, 5.0 - 0.3 - 0.2));
            p.teleportTo(near.x, y, near.z);
            t.check(dev.mcreload.moves.Ledge.find(p, y, y, false) != null, "the edge 0.2 block in front was not caught");
            Vec3 far = h.absoluteVec(new Vec3(3.5, 0, 5.0 - 0.3 - 0.6));
            p.teleportTo(far.x, y, far.z);
            t.check(dev.mcreload.moves.Ledge.find(p, y, y, true) == null, "an edge 0.6 block away was caught");
            // on the top, facing the drop (north): the toes at the edge, then a step back from it
            Vec3 edge = h.absoluteVec(new Vec3(3.5, 4.0, 5.4));
            p.teleportTo(h.getLevel(), edge.x, edge.y, edge.z, 180f, 0f);
            t.check(dev.mcreload.moves.Ledge.dropAhead(p) != null, "no automatic hang with the toes at the edge");
            Vec3 back = h.absoluteVec(new Vec3(3.5, 4.0, 6.0));
            p.teleportTo(h.getLevel(), back.x, back.y, back.z, 180f, 0f);
            t.check(dev.mcreload.moves.Ledge.dropAhead(p) == null, "an automatic hang 0.7 block from the edge");
        }).run(3);
    }

    /** A friend on the top pulls up a player hanging below: both moves start; nobody else (on the floor below) can. */
    @GameTest(template = FLOOR, timeoutTicks = 200)
    public static void ledgeFriendPullsUp(GameTestHelper h) {
        Script t = new Script(h).settle();
        ServerPlayer hanging = t.player();
        ServerPlayer friend = t.player();
        ServerPlayer below = t.player();
        int south = dev.mcreload.moves.Ledge.id(net.minecraft.core.Direction.SOUTH);
        t.at(2, () -> {
            for (int x = 1; x <= 5; x++) {
                for (int z = 5; z <= 6; z++) {
                    for (int y = 2; y <= 3; y++) h.setBlock(x, y, z, net.minecraft.world.level.block.Blocks.STONE);
                }
            }
            Vec3 hang = h.absoluteVec(new Vec3(3.5, 4.0 - dev.mcreload.moves.Ledge.HANG, 5.0 - 0.301));
            hanging.teleportTo(hang.x, hang.y, hang.z);
            t.check(MoveServer.request(hanging, Move.LEDGE_HANG, false, south), "the edge was not caught");
            Vec3 top = dev.mcreload.moves.Ledge.friendAt(hanging.position(), net.minecraft.core.Direction.SOUTH, Stamina.of(hanging).ledgeTop);
            friend.teleportTo(top.x, top.y, top.z);
            Vec3 floor = h.absoluteVec(new Vec3(2.5, 2.0, 3.5));
            below.teleportTo(floor.x, floor.y, floor.z);
        });
        t.at(4, () -> {
            t.check(!MoveServer.request(below, Move.LEDGE_HELP, false, hanging.getId()), "pulled up from below the edge");
            t.check(MoveServer.request(friend, Move.LEDGE_HELP, false, hanging.getId()), "the friend's hand refused");
            t.check(Stamina.of(hanging).move == Move.LEDGE_PULLED, "the hanging player is not pulled up");
            t.check(!MoveServer.request(hanging, Move.LEDGE_PULLED, false, south), "a client asked to be pulled up itself");
        }).run(5);
    }

    /** A side dodge with a sword knocks an arrow off to the sword's side, for 1.5 shields. */
    @GameTest(template = FLOOR, timeoutTicks = 200)
    public static void deflectArrowWithSword(GameTestHelper h) {
        Script t = new Script(h).settle();
        ServerPlayer p = t.player();
        Stamina s = Stamina.of(p);
        Arrow[] arrow = new Arrow[1];
        t.at(1, () -> p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD)));
        t.at(3, () -> {
            MoveServer.request(p, Move.DODGE_LEFT, false);
            // Straight at the player from in front (they face +Z).
            arrow[0] = new Arrow(h.getLevel(), p.getX(), p.getY() + 1.2, p.getZ() + 2.5);
            arrow[0].shoot(0, 0, -1, 1.2f, 0f);
            h.getLevel().addFreshEntity(arrow[0]);
        });
        // Hold the dodge in its deflect frames while the arrow arrives.
        for (int i = 4; i < 10; i++) t.at(i, () -> s.moveTick = Move.DEFLECT_TO - 1);    // deflect frames and untouchable both
        t.at(10, () -> {
            t.close(p.getHealth(), 20f, "health after a deflected arrow");
            t.close(s.shields, 10f - MovesConfig.deflectShields(), "shields after a deflected arrow");
            t.check(arrow[0].getDeltaMovement().x < -0.05, "the arrow did not go off to the right (sword hand): " + arrow[0].getDeltaMovement());
        }).run(11);
    }

    /** Without a sword or a bow the dodge only lets the arrow fly past (no shields spent). */
    @GameTest(template = FLOOR, timeoutTicks = 200)
    public static void noDeflectBareHanded(GameTestHelper h) {
        Script t = new Script(h).settle();
        ServerPlayer p = t.player();
        Stamina s = Stamina.of(p);
        t.at(3, () -> {
            MoveServer.request(p, Move.DODGE_LEFT, false);
            Arrow arrow = new Arrow(h.getLevel(), p.getX(), p.getY() + 1.2, p.getZ() + 2.5);
            arrow.shoot(0, 0, -1, 1.2f, 0f);
            h.getLevel().addFreshEntity(arrow);
        });
        for (int i = 4; i < 10; i++) t.at(i, () -> s.moveTick = Move.DEFLECT_TO - 1);    // deflect frames and untouchable both
        t.at(10, () -> {
            t.close(p.getHealth(), 20f, "health after an arrow through the dodge");
            t.close(s.shields, 10f, "shields spent without a weapon");
        }).run(11);
    }

    // ------------------------------------------------------------------ the bow

    /** The bow lasts half as long again, and hits like a sword in the hand: 4 damage, 1.6 a second. */
    @GameTest(template = FLOOR)
    public static void bowAsWeapon(GameTestHelper h) {
        Script t = new Script(h);
        ServerPlayer p = t.player();
        t.at(1, () -> p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOW)));
        t.at(4, () -> {
            t.check(Items.BOW.getMaxDamage() == 576, "bow durability " + Items.BOW.getMaxDamage());
            t.close((float) p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE), 4f, "bow attack damage");
            t.close((float) p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED), 1.6f, "bow attack speed");
        }).run(5);
    }

    /** A full-strength bow blow on the ground sweeps whoever stands by the target. */
    @GameTest(template = FLOOR, timeoutTicks = 200)
    public static void bowSweeps(GameTestHelper h) {
        Script t = new Script(h).settle();
        ServerPlayer p = t.player();
        // Husks: zombies would burn in the test world's daylight, and a burning mob shrugs off a
        // weaker hit for a moment after each burn.
        Zombie target = h.spawnWithNoFreeWill(EntityType.HUSK, 3, 2, 5);
        Zombie beside = h.spawnWithNoFreeWill(EntityType.HUSK, 4, 2, 5);
        t.at(1, () -> p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOW)));
        t.at(30, () -> {
            target.invulnerableTime = 0;
            beside.invulnerableTime = 0;
            float before = beside.getHealth();
            p.attack(target);
            t.check(target.getHealth() <= target.getMaxHealth() - 3.9f, "the target took " + (target.getMaxHealth() - target.getHealth()));
            t.check(beside.getHealth() < before, "the zombie beside was not swept");
        }).run(31);
    }

    /**
     * The volley: from a full draw, three arrows at the animation's releases, as many as there are
     * (two here), each wearing the bow; 20 energy.
     */
    @GameTest(template = FLOOR, timeoutTicks = 200)
    public static void bowVolley(GameTestHelper h) {
        Script t = new Script(h);
        ServerPlayer p = t.player();
        Stamina s = Stamina.of(p);
        t.at(1, () -> {
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOW));
            p.getInventory().add(new ItemStack(Items.ARROW, 2));
            p.startUsingItem(InteractionHand.MAIN_HAND);
        });
        t.at(8, () -> t.check(!MoveServer.request(p, Move.BARRAGE, false), "volley from a half draw"));
        t.at(24, () -> {
            s.energy = 100f;
            t.check(MoveServer.request(p, Move.BARRAGE, false), "volley refused from a full draw");
            t.check(!p.isUsingItem(), "the bow is still drawn after the volley started");
            t.close(s.energy, 100f - MovesConfig.costBarrage(), "energy after a volley");
        });
        t.at(24 + Move.BARRAGE.length, () -> {
            // Only this player's: the other tests of the batch shoot arrows nearby too.
            int arrows = h.getLevel().getEntitiesOfClass(Arrow.class, p.getBoundingBox().inflate(64), a -> a.getOwner() == p).size();
            t.check(arrows == 2, "arrows flying: " + arrows);
            t.check(p.getInventory().countItem(Items.ARROW) == 0, "arrows left: " + p.getInventory().countItem(Items.ARROW));
            t.check(p.getMainHandItem().getDamageValue() == 2, "bow wear: " + p.getMainHandItem().getDamageValue());
        }).run(25 + Move.BARRAGE.length);
    }

    /**
     * The flow's moves are made with the bow drawn, not without it. A dodge costs its energy, the
     * pose after it nothing; the pose may follow a little before the dodge has played out (the
     * client's request comes that much early); a pose cannot be cut short before its arrow has left
     * and the hand followed through.
     */
    @GameTest(template = FLOOR, timeoutTicks = 200)
    public static void bowFlowNeedsADrawnBow(GameTestHelper h) {
        Script t = new Script(h).settle();
        ServerPlayer p = t.player();
        Stamina s = Stamina.of(p);
        t.at(1, () -> {
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOW));
            p.getInventory().add(new ItemStack(Items.ARROW, 8));
        });
        t.at(3, () -> {
            s.energy = 100f;
            t.check(!MoveServer.request(p, Move.FLOW_SPIN_LEFT, false), "a flow dodge without a drawn bow");
            t.check(!MoveServer.request(p, Move.LEGOLAS_LEFT, false), "a Legolas dodge without a drawn bow");
            p.startUsingItem(InteractionHand.MAIN_HAND);
        });
        t.at(5, () -> {
            t.check(MoveServer.request(p, Move.FLOW_SPIN_LEFT, false), "a flow dodge refused with the bow drawn");
            t.close(s.energy, 100f - MovesConfig.costBowDodge(), "energy after a flow dodge");
            t.check(!MoveServer.request(p, Move.FLOW_CROUCH, false), "a pose at the start of the dodge");
        });
        t.at(5 + Move.FLOW_SPIN_LEFT.freeFrom(), () -> {
            float before = s.energy;
            t.check(MoveServer.request(p, Move.FLOW_CROUCH, false), "the pose refused at the end of the dodge");
            t.close(s.energy, before, "energy after a pose");
            t.check(!MoveServer.request(p, Move.FLOW_ROLL_LEFT, false), "a dodge cut into a pose before its arrow left");
        });
        t.at(5 + Move.FLOW_SPIN_LEFT.freeFrom() + Move.FLOW_CROUCH.freeFrom(), () -> {
            s.energy = 100f;
            t.check(MoveServer.request(p, Move.FLOW_ROLL_LEFT, false), "a roll refused once the pose's arrow had left");
            t.close(s.energy, 100f - MovesConfig.costBowDodge(), "energy after a roll in the flow");
        }).run(5 + Move.FLOW_SPIN_LEFT.freeFrom() + Move.FLOW_CROUCH.freeFrom() + 1);
    }

    /** A Legolas dodge: the bow drawn, its energy, untouchable like a side dodge. */
    @GameTest(template = FLOOR, timeoutTicks = 200)
    public static void legolasDodge(GameTestHelper h) {
        Script t = new Script(h).settle();
        ServerPlayer p = t.player();
        Stamina s = Stamina.of(p);
        Zombie zombie = h.spawnWithNoFreeWill(EntityType.HUSK, 5, 2, 3);
        t.at(1, () -> {
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOW));
            p.getInventory().add(new ItemStack(Items.ARROW, 8));
            p.startUsingItem(InteractionHand.MAIN_HAND);
        });
        t.at(4, () -> {
            s.energy = 100f;
            t.check(MoveServer.request(p, Move.LEGOLAS_LEFT, false), "a Legolas dodge refused with the bow drawn");
            t.close(s.energy, 100f - MovesConfig.costBowDodge(), "energy after a Legolas dodge");
        });
        t.at(7, () -> {
            t.check(s.untouchable(), "not untouchable at Legolas tick " + s.moveTick);
            // Against the blow itself (other tests' arrows may have grazed the player before), not
            // vanilla's moment of invulnerability after a hit.
            float before = p.getHealth();
            p.invulnerableTime = 0;
            p.hurt(p.damageSources().mobAttack(zombie), 4f);
            t.close(p.getHealth(), before, "health after a blow in the Legolas dodge");
        });
        t.at(4 + 18, () -> {
            t.check(MoveServer.request(p, Move.LEGOLAS_RIGHT, false), "the next Legolas dodge refused at the end of the spin");
        }).run(4 + 19);
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
            // Blows hurt as much as they say (difficulty scales mob blows).
            h.getLevel().getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
            return this;
        }

        /** A zombie that does nothing, 2.5 blocks in front of the player (who faces +Z). */
        Zombie zombieInFront() {
            return h.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 2, 6);
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
