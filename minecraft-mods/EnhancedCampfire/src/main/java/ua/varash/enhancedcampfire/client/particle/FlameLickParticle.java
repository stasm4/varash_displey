package ua.varash.enhancedcampfire.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.particles.SimpleParticleType;
import org.jetbrains.annotations.Nullable;
import ua.varash.enhancedcampfire.client.fx.ParticleBudget;

/** A short-lived animated little flame licking out between the logs. */
public class FlameLickParticle extends BudgetParticle {
    private final SpriteSet sprites;
    private final float startSize;

    protected FlameLickParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z, vx, vy, vz);
        this.sprites = sprites;
        this.lifetime = 8 + this.random.nextInt(8);
        this.gravity = -0.02F;
        this.friction = 0.9F;
        this.hasPhysics = false;
        this.startSize = 0.09F + this.random.nextFloat() * 0.08F;
        this.quadSize = this.startSize;
        this.alpha = 0.9F;
        this.setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.removed) {
            return;
        }
        this.setSpriteFromAge(this.sprites);
        float life = (float) this.age / this.lifetime;
        this.quadSize = this.startSize * (1.0F - life * 0.6F);
        this.alpha = 0.9F * (1.0F - life * life);
    }

    @Override
    public int getLightColor(float partialTick) {
        return LightTexture.FULL_BRIGHT;
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Nullable
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double vx, double vy, double vz) {
            return ParticleBudget.tryAcquire() ? new FlameLickParticle(level, x, y, z, vx, vy, vz, sprites) : null;
        }
    }
}
