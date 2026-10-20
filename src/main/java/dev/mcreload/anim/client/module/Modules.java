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
            new FistModule(),
            new GestureModule(),
            new EmoteModule(),
            new RideModule(),
            new FlightModule(),
            new ReactionModule(),
            new SleepModule(),
            new SpellcastModule(),
            new PhysicsModule()
    );

    private Modules() {}
}
