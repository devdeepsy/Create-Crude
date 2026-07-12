package com.deepu.create_crude.client;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.resources.ResourceLocation;

public class CreateCrudePartialModels {
    public static final PartialModel STEEL_PUMP_COG = PartialModel.of(
        ResourceLocation.fromNamespaceAndPath("createcrude", "block/steel_pump/cog")
    );

    public static void init() {
        // Trigger class loading during early client initialization
    }
}