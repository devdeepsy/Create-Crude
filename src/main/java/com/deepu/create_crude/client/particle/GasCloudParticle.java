package com.deepu.create_crude.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;

public class GasCloudParticle extends TextureSheetParticle {

    private final SpriteSet sprites;

    protected GasCloudParticle(ClientLevel level, double x, double y, double z,
                                double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;

        this.xd = xd;
        this.yd = yd;
        this.zd = zd;

        // Gas drifts lazily, barely rises, barely settles
        this.friction = 0.98F;
        this.gravity = 0.0F;

        // BIG puffs
        this.quadSize = 1.2F + this.random.nextFloat() * 0.8F; // ~1.2 - 2.0 blocks wide

        this.lifetime = 60 + this.random.nextInt(40); // 3-5 sec per particle

        // Default color — overridden per-variant via setGasColor()
        this.rCol = 1.0F;
        this.gCol = 1.0F;
        this.bCol = 1.0F;
        this.alpha = 0.0F; // start invisible, fade in

        this.hasPhysics = false;
        this.setSpriteFromAge(sprites);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public void tick() {
        xo = x; yo = y; zo = z;

        if (age++ >= lifetime) {
            remove();
        } else {
            // gentle random drift so it doesn't look like it's on rails
            xd += (random.nextFloat() - 0.5F) * 0.002;
            zd += (random.nextFloat() - 0.5F) * 0.002;
            yd *= 0.98; // slowly stop rising/falling

            move(xd, yd, zd);
            xd *= friction;
            yd *= friction;
            zd *= friction;

            setSpriteFromAge(sprites);

            // fade in over first 20% of life, fade out over last 30%
            float lifeRatio = (float) age / lifetime;
            if (lifeRatio < 0.2F) {
                this.alpha = (lifeRatio / 0.2F) * 0.85F;
            } else if (lifeRatio > 0.7F) {
                this.alpha = (1.0F - (lifeRatio - 0.7F) / 0.3F) * 0.85F;
            } else {
                this.alpha = 0.85F;
            }
        }
    }

    /** Call right after creation to recolor per gas variant */
    public void setGasColor(float r, float g, float b) {
        this.rCol = r;
        this.gCol = g;
        this.bCol = b;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        private final float r;
        private final float g;
        private final float b;

        // Constructor for colored gas variants
        public Provider(SpriteSet sprites, float r, float g, float b) {
            this.sprites = sprites;
            this.r = r;
            this.g = g;
            this.b = b;
        }

        // Fallback default constructor (makes it white if no color given)
        public Provider(SpriteSet sprites) {
            this(sprites, 1.0F, 1.0F, 1.0F);
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                        double x, double y, double z,
                                        double xd, double yd, double zd) {
            GasCloudParticle particle = new GasCloudParticle(level, x, y, z, xd, yd, zd, this.sprites);
            particle.pickSprite(this.sprites);
            particle.setGasColor(this.r, this.g, this.b); // Now properly references local fields!
            return particle;
        }
    }
}