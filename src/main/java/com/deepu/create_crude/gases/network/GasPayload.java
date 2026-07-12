package com.deepu.create_crude.gases.network;

import net.minecraft.resources.ResourceLocation;

public record GasPayload(ResourceLocation gasBlockId, int radius) {
}