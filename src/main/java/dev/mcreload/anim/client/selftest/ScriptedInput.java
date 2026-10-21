package dev.mcreload.anim.client.selftest;

import net.minecraft.client.player.Input;

/** Movement input driven by the self-test instead of the keyboard. */
final class ScriptedInput extends Input {
    boolean forward, back, sneak, jump;

    @Override
    public void tick(boolean slow, float slowFactor) {
        up = forward;
        down = back;
        left = false;
        right = false;
        forwardImpulse = forward ? 1f : (back ? -1f : 0f);
        leftImpulse = 0f;
        jumping = jump;
        shiftKeyDown = sneak;
        if (slow) {
            forwardImpulse *= slowFactor;
            leftImpulse *= slowFactor;
        }
    }
}
