package com.deepu.create_crude.gases;

public record GasProperties(
    int maxRadius,
    int maxLifetime,
    int expansionInterval,   // not yet used but kept for future
    int tickInterval,
    int explosionRadius,
    int tintColor            // 0xFFRRGGBB
) {}
