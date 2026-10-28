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
     * Hop over a zombie set on the player: its blow in the wind-up is parried, it cannot hurt the
     * player until the landing, a later blow lands; Speed I at the end; 25 energy.
     */
    @GameTest(template = FLOOR, timeoutTicks = 240)
    public static void hopOverZombie(GameTestHelper h) {
        Script t = new Script(h).settle();
        ServerPlayer p = t.player();
        Zombie zombie = h.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 2, 5);
        Stamina s = Stamina.of(p);
        t.at(2, () -> {
            zombie.setTarget(p);
            zombie.setAggressive(true);
            zombie.setYRot(180f);
            zombie.setYHeadRot(180f);
            s.energy = 100f;
            t.check(MoveServer.request(p, Move.HOP, false, zombie.getId()), "hop refused against a zombie at point blank");
            t.close(s.energy, 100f - MovesConfig.costHop(), "energy after a hop");
        });
        t.at(5, () -> {
            p.hurt(p.damageSources().mobAttack(zombie), 6f);
            t.close(p.getHealth(), 20f, "health after the zombie's blow in the wind-up");
        });
        t.at(20, () -> {
            p.invulnerableTime = 0;
            p.hurt(p.damageSources().mobAttack(zombie), 6f);
            t.close(p.getHealth(), 20f, "health after the zombie's blow during the flip");
        });
        t.at(2 + Move.HOP.length, () -> {
            t.check(p.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED), "no Speed after the hop");
            p.invulnerableTime = 0;
            p.hurt(p.damageSources().mobAttack(zombie), 6f);
            t.check(p.getHealth() < 20f, "the zombie still cannot hurt the player after the hop");
        }).run(3 + Move.HOP.length);
    }

    /** No hop without an enemy set on the player (a calm zombie, a cow): R + W rolls instead. */
    @GameTest(template = FLOOR, timeoutTicks = 200)
    public static void hopNeedsAnEnemy(GameTestHelper h) {
        Script t = new Script(h).settle();
        ServerPlayer p = t.player();
        Zombie calm = h.spawnWithNoFreeWill(EntityType.ZOMBIE, 3, 2, 5);
        net.minecraft.world.entity.animal.Cow cow = h.spawnWithNoFreeWill(EntityType.COW, 4, 2, 5);
        Stamina s = Stamina.of(p);
        // Truly calm: a test mob keeps choosing targets unless its AI is off.
        calm.setNoAi(true);
        t.at(2, () -> {
            calm.setTarget(null);
            calm.setAggressive(false);
            t.check(!MoveServer.request(p, Move.HOP, false, calm.getId()), "hop over a zombie that is not after the player");
            t.check(!MoveServer.request(p, Move.HOP, false, cow.getId()), "hop over a cow");
            t.check(!MoveServer.request(p, Move.HOP, false, -1), "hop over nobody");
            t.check(s.move == null, "a refused hop left a move running");
        }).run(3);
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
            p.hurt(p.damageSources().mobAttack(zombie), 4f);
            t.close(p.getHealth(), 20f, "health after a blow in the Legolas dodge");
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
