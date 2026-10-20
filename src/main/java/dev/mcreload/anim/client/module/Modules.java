package dev.mcreload.anim.client.module;

import java.util.List;

public final class Modules {
    public static final List<AnimModule> ALL = List.of(
            new LocomotionModule(),
            new IdleModule(),
            new AirModule(),
            new WaterModule(),
            new ClimbModule(),
            new CombatModule(),
            new ReactionModule(),
            new SpellcastModule()
    );

    private Modules() {}
}
