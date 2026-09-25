package cn.zbx1425.mtrsteamloco.render.train;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;
import org.jetbrains.annotations.Nullable;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.RandomSource;

public class SteamSmokeParticle extends SingleQuadParticle {

    SteamSmokeParticle(ClientLevel clientLevel, double d, double e, double f, double g, double h, double i, TextureAtlasSprite sprite) {
        super(clientLevel, d, e, f, sprite);
        this.scale(3.0f);
        this.setSize(0.25f, 0.25f);
        this.lifetime = this.random.nextInt(10) + 30;
        this.gravity = 3.0E-6f;
        this.hasPhysics = true;
        this.xd = g;
        this.yd = h + (double)(this.random.nextFloat() / 500.0f);
        this.zd = i;
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime || this.alpha <= 0.0f) {
            this.remove();
            return;
        }
        this.xd += (double)(this.random.nextFloat() / 5000.0f * (float)(this.random.nextBoolean() ? 1 : -1));
        this.zd += (double)(this.random.nextFloat() / 5000.0f * (float)(this.random.nextBoolean() ? 1 : -1));
        this.yd -= (double)this.gravity;
        this.move(this.xd, this.yd, this.zd);

        if (this.age < this.lifetime * 0.4) {
            this.quadSize += 0.2;
        } else {
            this.quadSize += 0.1;
        }
        if (this.y == this.yo) {
            this.remove();
        }
    }

    @Override
    protected Layer getLayer() {
        return Layer.OPAQUE;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {

        private final SpriteSet sprites;

        public Provider(SpriteSet spriteSet) {
            this.sprites = spriteSet;
        }

        @Nullable
        @Override
        public Particle createParticle(SimpleParticleType particleOptions, ClientLevel clientLevel, double d, double e, double f, double g, double h, double i, RandomSource random) {
            SteamSmokeParticle campfireSmokeParticle = new SteamSmokeParticle(clientLevel, d, e, f, g, h, i, sprites.get(random));
            campfireSmokeParticle.setAlpha(1f);
            return campfireSmokeParticle;
        }
    }
}


