package ua.varash.enhancedcampfire.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import ua.varash.enhancedcampfire.client.fx.ParticleBudget;
import ua.varash.enhancedcampfire.client.fx.Wind;

/**
 * A glowing spark: shoots up, is slowed down by the air, flutters in the hot updraft, cools from
 * yellow-white through orange to dull red and blinks out.  Big sparks live longer and burn brighter.
 */
public class SparkParticle extends BudgetParticle {
    private final boolean big;
    private final float flickerPhase;
    private final float startSize;

    protected SparkParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz,
                            SpriteSet sprites, boolean big) {
        super(level, x, y, z, vx, vy, vz);
        this.big = big;
        this.pickSprite(sprites);
        this.lifetime = big ? 45 + this.random.nextInt(35) : 16 + this.random.nextInt(26);
        this.gravity = big ? -0.05F : -0.12F; // negative gravity = buoyant
        this.friction = 0.93F;
        this.hasPhysics = true;
        this.startSize = (big ? 0.075F : 0.032F) + this.random.nextFloat() * (big ? 0.03F : 0.018F);
        this.quadSize = this.startSize;
        this.flickerPhase = this.random.nextFloat() * 10.0F;
        this.rCol = 1.0F;
        this.gCol = 0.9F;
        this.bCol = 0.6F;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.removed) {
            return;
        }
        float life = (float) this.age / this.lifetime;
        // updraft turbulence and a bit of wind
        this.xd += (this.random.nextFloat() - 0.5F) * 0.012F + Wind.x() * 0.0006F;
        this.zd += (this.random.nextFloat() - 0.5F) * 0.012F + Wind.z() * 0.0006F;
        // cooling: white-yellow -> orange -> deep red
        this.rCol = 1.0F;
        this.gCol = Mth.clamp(0.9F - life * 1.1F, 0.12F, 0.9F);
        this.bCol = Mth.clamp(0.55F - life * 1.6F, 0.02F, 0.6F);
        float flicker = 0.75F + 0.25F * Mth.sin(this.age * (big ? 0.9F : 1.7F) + this.flickerPhase);
        this.alpha = Mth.clamp((1.0F - life * life) * flicker, 0.0F, 1.0F);
        this.quadSize = this.startSize * (1.0F - life * 0.45F);
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
            return ParticleBudget.tryAcquire() ? new SparkParticle(level, x, y, z, vx, vy, vz, sprites, false) : null;
        }
    }

    public static final class BigProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public BigProvider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Nullable
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double vx, double vy, double vz) {
            return ParticleBudget.tryAcquire() ? new SparkParticle(level, x, y, z, vx, vy, vz, sprites, true) : null;
        }
    }
}
