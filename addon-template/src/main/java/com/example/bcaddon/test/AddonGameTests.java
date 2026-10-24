package com.example.bcaddon.test;

import com.example.bcaddon.BcAddon;
import com.example.bcaddon.combat.BcAttacks;
import com.example.bcaddon.combat.Guard;
import com.example.bcaddon.combat.Heavy;
import net.bettercombat.api.AttackHand;
import net.bettercombat.logic.PlayerAttackHelper;
import net.bettercombat.logic.PlayerAttackProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Server-side tests of the addon with Better Combat loaded: {@code ./gradlew runGameTestServer}.
 * Forge runs them only when {@code forge.enabledGameTestNamespaces} lists the mod (the
 * gameTestServer run in build.gradle does); in a normal game they do nothing.
 */
@GameTestHolder(BcAddon.MODID)
@PrefixGameTestTemplate(false)
public final class AddonGameTests {
    /** data/bcaddon/structures/floor.nbt: 3x3 stone floor. */
    private static final String FLOOR = "floor";

    /** A player who just joined is not in the middle of a Better Combat swing (see BcAttacks.resetCombo). */
    @GameTest(template = FLOOR)
    public static void noSwingRightAfterJoining(GameTestHelper helper) {
        ServerPlayer player = swordsman(helper, 0f);
        helper.assertTrue(BcAttacks.current(player) == null, "a fresh player reads as swinging");
        // What Better Combat does on the server while it applies the second swing of a combo.
        ((PlayerAttackProperties) player).setComboCount(1);
        AttackHand hand = BcAttacks.current(player);
        helper.assertTrue(hand != null && hand.combo().current() == 2, "expected the second attack of the combo");
        BcAttacks.resetCombo(player);
        helper.succeed();
    }

    /** Holding the heavy attack key swaps the attack Better Combat performs, and slows the swings. */
    @GameTest(template = FLOOR)
    public static void heavyAttackReplacesTheSwing(GameTestHelper helper) {
        ServerPlayer player = swordsman(helper, 0f);
        AttackHand normal = PlayerAttackHelper.getCurrentAttack(player, 0);
        helper.assertTrue(normal != null, "the iron sword has no Better Combat attack");
        double speed = player.getAttributeValue(Attributes.ATTACK_SPEED);

        Heavy.setServer(player, true);
        AttackHand heavy = PlayerAttackHelper.getCurrentAttack(player, 0);
        helper.assertTrue(heavy != null && "bettercombat:two_handed_slash_vertical_right".equals(heavy.attack().animation()),
                "expected the heavy animation from weapon_extras/swords.json");
        helper.assertTrue(Math.abs(heavy.attack().damageMultiplier() - 1.6) < 1e-6, "expected damage multiplier 1.6");
        helper.assertTrue(player.getAttributeValue(Attributes.ATTACK_SPEED) < speed, "heavy swings must be slower");

        Heavy.setServer(player, false);
        AttackHand back = PlayerAttackHelper.getCurrentAttack(player, 0);
        helper.assertTrue(back != null && normal.attack().animation().equals(back.attack().animation()), "expected the normal attack again");
        helper.succeed();
    }

    /** A melee hit from the front inside the guard window is parried, once; from behind it is not. */
    @GameTest(template = FLOOR)
    public static void guardParriesFromTheFrontOnly(GameTestHelper helper) {
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 1, 2));

        ServerPlayer facing = swordsman(helper, 0f); // yaw 0 looks along +Z, at the zombie
        Guard.request(facing);
        helper.assertTrue(hit(facing, zombie).isCanceled(), "a hit from the front in the window must be parried");
        helper.assertTrue(zombie.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "the attacker must be staggered");
        helper.assertTrue(!hit(facing, zombie).isCanceled(), "only one parry per guard");

        ServerPlayer turned = swordsman(helper, 180f); // back to the zombie
        Guard.request(turned);
        helper.assertTrue(!hit(turned, zombie).isCanceled(), "a hit from behind must not be parried");
        helper.succeed();
    }

    private static LivingAttackEvent hit(ServerPlayer player, Zombie attacker) {
        LivingAttackEvent event = new LivingAttackEvent(player, player.damageSources().mobAttack(attacker), 4f);
        Guard.onAttacked(event);
        return event;
    }

    private static ServerPlayer swordsman(GameTestHelper helper, float yaw) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 1, 0.5));
        player.moveTo(at.x, at.y, at.z, yaw, 0f);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        return player;
    }

    private AddonGameTests() {}
}
