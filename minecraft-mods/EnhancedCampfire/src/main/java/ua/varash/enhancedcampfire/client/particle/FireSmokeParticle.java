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

/**
 * Campfire smoke.  Born small, dark and fairly dense right above the flames; while rising it slows down, grows,
 * turns light grey, becomes more transparent and drifts sideways with the wind (more the higher it gets).
 * The steam variant is white, quick and short-lived (rain hitting the fire, smouldering embers).
 */
public class FireSmokeParticle extends BudgetParticle {
    private final boolean steam;
    private final float startSize;
    private final float endSize;
    private final float maxAlpha;
    private final float darkShade;
    private final float lightShade;
    private final float rollSpeed;

    protected FireSmokeParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz,
                                SpriteSet sprites, boolean steam) {
        super(level, x, y, z, vx, vy, vz);
        this.steam = steam;
        this.pickSprite(sprites);
        this.hasPhysics = true;
        this.gravity = 0.0F;
        this.friction = 0.985F;
        if (steam) {
            this.lifetime = 22 + this.random.nextInt(18);
            this.startSize = 0.12F + this.random.nextFloat() * 0.08F;
            this.endSize = this.startSize * 2.6F;
            this.maxAlpha = 0.32F;
            this.darkShade = 0.85F;
            this.lightShade = 0.97F;
        } else {
            this.lifetime = 80 + this.random.nextInt(70);
            this.startSize = 0.26F + this.random.nextFloat() * 0.16F;
            this.endSize = this.startSize * (2.6F + this.random.nextFloat() * 0.9F);
            this.maxAlpha = 0.38F + this.random.nextFloat() * 0.12F;
            this.darkShade = 0.11F + this.random.nextFloat() * 0.06F;
            this.lightShade = 0.52F + this.random.nextFloat() * 0.14F;
        }
        this.quadSize = this.startSize;
        this.roll = this.random.nextFloat() * ((float) (Math.PI * 2.0D));
        this.oRoll = this.roll;
        this.rollSpeed = (this.random.nextFloat() - 0.5F) * 0.03F;
        this.alpha = 0.0F;
        this.rCol = this.gCol = this.bCol = this.darkShade;
    }

    @Override
    public void tick() {
        this.oRoll = this.roll;
        this.roll += this.rollSpeed;
        super.tick();
        if (this.removed) {
            return;
        }
        float life = (float) this.age / this.lifetime;
        // buoyancy that fades as the smoke cools, plus wind that grows with height above the fire
        this.yd += steam ? 0.002F : 0.0012F * (1.0F - life);
        float drift = steam ? 0.0007F : 0.0011F * (0.3F + life);
        this.xd += Wind.x() * drift + (this.random.nextFloat() - 0.5F) * 0.0025F;
        this.zd += Wind.z() * drift + (this.random.nextFloat() - 0.5F) * 0.0025F;

        float fadeIn = Mth.clamp(life / 0.12F, 0.0F, 1.0F);
        float fadeOut = 1.0F - Mth.clamp((life - 0.35F) / 0.65F, 0.0F, 1.0F);
        this.alpha = this.maxAlpha * fadeIn * fadeOut * fadeOut;
        float shade = Mth.lerp(Mth.clamp(life * 1.6F, 0.0F, 1.0F), this.darkShade, this.lightShade);
        this.rCol = shade;
        this.gCol = shade * 0.98F;
        this.bCol = shade * 0.96F;
        float grow = 1.0F - (1.0F - life) * (1.0F - life);
        this.quadSize = Mth.lerp(grow, this.startSize, this.endSize);
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
            return ParticleBudget.tryAcquire() ? new FireSmokeParticle(level, x, y, z, vx, vy, vz, sprites, false) : null;
        }
    }

    public static final class SteamProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public SteamProvider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Nullable
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double vx, double vy, double vz) {
            return ParticleBudget.tryAcquire() ? new FireSmokeParticle(level, x, y, z, vx, vy, vz, sprites, true) : null;
        }
    }
}
