package pl.holovis.mixin;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.holovis.HoloConfig;

/**
 * Custom Sky. Zwracany typ koloru zależy od wersji (int ARGB albo Vec3d),
 * więc obsługujemy oba - dzięki temu mixin jest odporny na różnice w mappingach.
 */
@Mixin(ClientWorld.class)
public abstract class ClientWorldMixin {

    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true, require = 0)
    private void holovis$skyColor(Vec3d cameraPos, float tickDelta, CallbackInfoReturnable<Object> cir) {
        HoloConfig cfg = HoloConfig.get();
        if (!cfg.skyEnabled) return;
        int rgb = holovis$sky(cfg, tickDelta);
        Object orig = cir.getReturnValue();
        if (orig instanceof Integer) {
            cir.setReturnValue(0xFF000000 | rgb);
        } else if (orig instanceof Vec3d) {
            cir.setReturnValue(new Vec3d(((rgb >> 16) & 255) / 255.0, ((rgb >> 8) & 255) / 255.0, (rgb & 255) / 255.0));
        }
    }

    @Inject(method = "getCloudsColor", at = @At("RETURN"), cancellable = true, require = 0)
    private void holovis$cloudsColor(float tickDelta, CallbackInfoReturnable<Object> cir) {
        HoloConfig cfg = HoloConfig.get();
        if (!cfg.skyEnabled || !cfg.skyClouds) return;
        int sky = holovis$sky(cfg, tickDelta);
        Object orig = cir.getReturnValue();
        if (orig instanceof Integer i) {
            int o = i & 0xFFFFFF;
            cir.setReturnValue(0xFF000000 | holovis$mix(o, sky, 0.5f));
        } else if (orig instanceof Vec3d v) {
            int o = ((int) (v.x * 255) << 16) | ((int) (v.y * 255) << 8) | (int) (v.z * 255);
            int m = holovis$mix(o, sky, 0.5f);
            cir.setReturnValue(new Vec3d(((m >> 16) & 255) / 255.0, ((m >> 8) & 255) / 255.0, (m & 255) / 255.0));
        }
    }

    @Unique
    private int holovis$sky(HoloConfig cfg, float tickDelta) {
        int rgb = cfg.skyColor.rgb();
        if (!cfg.skyDaylight) return rgb;
        ClientWorld w = (ClientWorld) (Object) this;
        float f = MathHelper.clamp(MathHelper.cos(w.getSkyAngle(tickDelta) * ((float) Math.PI * 2f)) * 2f + 0.5f, 0f, 1f);
        float k = 0.12f + 0.88f * f;
        int r = (int) (((rgb >> 16) & 255) * k), g = (int) (((rgb >> 8) & 255) * k), b = (int) ((rgb & 255) * k);
        return (r << 16) | (g << 8) | b;
    }

    @Unique
    private static int holovis$mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }
}
