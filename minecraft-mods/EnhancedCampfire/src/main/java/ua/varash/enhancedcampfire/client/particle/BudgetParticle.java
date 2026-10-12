package ua.varash.enhancedcampfire.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import ua.varash.enhancedcampfire.client.fx.ParticleBudget;

/** Base for all campfire particles: gives the budget slot back when the particle dies. */
abstract class BudgetParticle extends TextureSheetParticle {
    protected BudgetParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
        super(level, x, y, z);
        // the 7-argument super constructor randomises the velocity; we want exactly what the spawner asked for
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
    }

    @Override
    public void remove() {
        if (!this.removed) {
            ParticleBudget.release();
        }
        super.remove();
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }
}
