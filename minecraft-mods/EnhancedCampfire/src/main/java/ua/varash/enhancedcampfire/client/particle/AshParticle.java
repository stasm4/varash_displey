package ua.varash.enhancedcampfire.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import ua.varash.enhancedcampfire.client.fx.ParticleBudget;
import ua.varash.enhancedcampfire.client.fx.Wind;

/** A dark flake of ash: lifted by the heat, then slowly see-saws down, tumbling, and settles on the ground. */
public class AshParticle extends BudgetParticle {
    private final float swayPhase;
    private final float rollSpeed;

    protected AshParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z, vx, vy, vz);
        this.pickSprite(sprites);
        this.lifetime = 70 + this.random.nextInt(60);
        this.gravity = 0.035F;
        this.friction = 0.95F;
        this.hasPhysics = true;
        this.quadSize = 0.035F + this.random.nextFloat() * 0.03F;
        float shade = 0.12F + this.random.nextFloat() * 0.18F;
        this.rCol = shade;
        this.gCol = shade * 0.97F;
        this.bCol = shade * 0.94F;
        this.alpha = 0.95F;
        this.swayPhase = this.random.nextFloat() * ((float) (Math.PI * 2.0D));
        this.rollSpeed = (this.random.nextFloat() - 0.5F) * 0.2F;
        this.roll = this.random.nextFloat() * ((float) (Math.PI * 2.0D));
        this.oRoll = this.roll;
    }

    @Override
    public void tick() {
        this.oRoll = this.roll;
        if (!this.onGround) {
            this.roll += this.rollSpeed;
        }
        super.tick();
        if (this.removed) {
            return;
        }
        if (!this.onGround) {
            float sway = Mth.sin(this.age * 0.25F + this.swayPhase) * 0.004F;
            this.xd += sway + Wind.x() * 0.0006F;
            this.zd += Mth.cos(this.age * 0.21F + this.swayPhase) * 0.004F + Wind.z() * 0.0006F;
        }
        float life = (float) this.age / this.lifetime;
        this.alpha = life > 0.8F ? (1.0F - life) / 0.2F * 0.95F : 0.95F;
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
            return ParticleBudget.tryAcquire() ? new AshParticle(level, x, y, z, vx, vy, vz, sprites) : null;
        }
    }
}
