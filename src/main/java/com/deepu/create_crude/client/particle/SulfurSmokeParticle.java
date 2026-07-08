package com.deepu.create_crude.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

public class SulfurSmokeParticle extends TextureSheetParticle {

    private final SpriteSet sprites;
    protected SulfurSmokeParticle(ClientLevel level, double x, double y, double z,
                                   double xd, double yd, double zd,SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites; // will be set in the provider
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.friction = 0.96F;
        this.gravity = -0.1F; // slight upward drift, like smoke
        this.quadSize = 0.25F + this.random.nextFloat() * 0.1F;
        this.lifetime = 40 + this.random.nextInt(20);

        // Yellow sulfur tint
        this.rCol = 1.0F;
        this.gCol = 0.82F;
        this.bCol = 0.15F;
        this.alpha = 1.0F;

        this.hasPhysics = false;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public void tick() {
        super.tick();
        this.setSpriteFromAge(this.sprites);
        // fade out over lifetime
        this.alpha = 0.85F * (1.0F - (float) this.age / this.lifetime);
    }

    public static class Provider implements ParticleProvider<SimpleParticleType>{
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                        double x, double y, double z,
                                        double xd, double yd, double zd) {
            SulfurSmokeParticle particle = new SulfurSmokeParticle(level, x, y, z, xd, yd, zd,this.sprites);
            particle.pickSprite(this.sprites);
            return particle;
        }
    }
}