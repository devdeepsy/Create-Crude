package com.deepu.create_crude;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModParticles {
    public static final String MOD_ID = CreateCrude.MODID;

    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
        DeferredRegister.create(BuiltInRegistries.PARTICLE_TYPE, MOD_ID);

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SULFUR_SMOKE =
        PARTICLE_TYPES.register("sulfur_smoke", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> GAS_CLOUD =
        PARTICLE_TYPES.register("gas_cloud", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> LPG_CLOUDS =
        PARTICLE_TYPES.register("lpg_clouds", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> METHANE_CLOUDS =
        PARTICLE_TYPES.register("methane_clouds", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> ETHANE_CLOUDS =
        PARTICLE_TYPES.register("ethane_clouds", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> PROPANE_CLOUDS =
        PARTICLE_TYPES.register("propane_clouds", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BUTANE_CLOUDS =
        PARTICLE_TYPES.register("butane_clouds", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> HYDROGEN_CLOUDS =
        PARTICLE_TYPES.register("hydrogen_clouds", () -> new SimpleParticleType(false));
    

    public static void register(IEventBus modEventBus) {
        PARTICLE_TYPES.register(modEventBus);
    }
}