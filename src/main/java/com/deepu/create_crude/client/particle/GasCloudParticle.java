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

        this.friction = 0.98F;
        this.gravity = 0.0F;

        // Base size for world gas clouds
        this.quadSize = 1.2F + this.random.nextFloat() * 0.8F;
        this.lifetime = 60 + this.random.nextInt(40);

        this.rCol = 1.0F;
        this.gCol = 1.0F;
        this.bCol = 1.0F;
        this.alpha = 0.0F;

        this.hasPhysics = false;
        this.setSpriteFromAge(sprites);
    }

    public void setScale(float scale) {
        this.quadSize = scale;
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
            xd += (random.nextFloat() - 0.5F) * 0.001;
            zd += (random.nextFloat() - 0.5F) * 0.001;
            yd *= 0.98;

            move(xd, yd, zd);
            xd *= friction;
            yd *= friction;
            zd *= friction;

            setSpriteFromAge(sprites);

            float lifeRatio = (float) age / lifetime;
            if (lifeRatio < 0.2F) {
                this.alpha = (lifeRatio / 0.2F) * 0.4F; // Max opacity lowered to 0.4 for basin visibility
            } else if (lifeRatio > 0.7F) {
                this.alpha = (1.0F - (lifeRatio - 0.7F) / 0.3F) * 0.4F;
            } else {
                this.alpha = 0.4F;
            }
        }
    }

    public void setGasColor(float r, float g, float b) {
        this.rCol = r;
        this.gCol = g;
        this.bCol = b;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        private final float r, g, b;

        public Provider(SpriteSet sprites, float r, float g, float b) {
            this.sprites = sprites;
            this.r = r;
            this.g = g;
            this.b = b;
        }

        public Provider(SpriteSet sprites) {
            this(sprites, 1.0F, 1.0F, 1.0F);
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                        double x, double y, double z,
                                        double xd, double yd, double zd) {
            GasCloudParticle particle = new GasCloudParticle(level, x, y, z, xd, yd, zd, this.sprites);
            particle.pickSprite(this.sprites);
            particle.setGasColor(this.r, this.g, this.b);
            
            // Scaled down if spawned with minimal motion (basin internal particle)
            if (Math.abs(xd) < 0.005 && Math.abs(zd) < 0.005) {
                particle.setScale(0.25F + level.random.nextFloat() * 0.15F);
            }
            return particle;
        }
    }
}