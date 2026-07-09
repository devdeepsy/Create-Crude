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

        // BIG puffs — this is the "quite large particle" you wanted
        this.quadSize = 1.2F + this.random.nextFloat() * 0.8F; // ~1.2 - 2.0 blocks wide

        this.lifetime = 60 + this.random.nextInt(40); // 3-5 sec per particle, independent of block LIFETIME

        // Default color — overridden per-variant via setColor() from the provider/spawn call
        this.rCol = 0.8F;
        this.gCol = 0.85F;
        this.bCol = 0.2F;
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

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                        double x, double y, double z,
                                        double xd, double yd, double zd) {
            GasCloudParticle particle = new GasCloudParticle(level, x, y, z, xd, yd, zd, this.sprites);
            particle.pickSprite(this.sprites);
            return particle;
        }
    }
}