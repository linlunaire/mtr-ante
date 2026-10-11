package cn.zbx1425.mtrsteamloco.mixin;

import cn.zbx1425.mtrsteamloco.data.Rolling;
import cn.zbx1425.sowcer.math.Vector3f;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow protected abstract void setPosition(Vec3 position);
    @Shadow public abstract Vec3 position();
    @Shadow private float eyeHeight;
    @Shadow private float eyeHeightOld;
    @Shadow @Final private Quaternionf rotation;

    @Inject(method = "alignWithEntity", at = {
            @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setPosition(DDD)V", ordinal = 0, shift = At.Shift.AFTER),
            @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setPosition(Lnet/minecraft/world/phys/Vec3;)V", ordinal = 0, shift = At.Shift.AFTER)
    })
    private void rollPosition(float tickDelta, CallbackInfo ci) {
        float height = Mth.lerp(tickDelta, eyeHeightOld, eyeHeight);
        setPosition(Rolling.applyRolling(new Vector3f(position()), height).toVec3());
    }

    @Inject(method = "setRotation*", at = @At(value = "INVOKE",
            target = "Lorg/joml/Quaternionf;rotationYXZ(FFF)Lorg/joml/Quaternionf;", shift = At.Shift.AFTER), require = 1, allow = 1)
    private void rollRotation(CallbackInfo ci) {
        // NeoForge moves rotationYXZ from (FF)V into its (FFF)V overload. Scan
        // every overload without capturing their different argument lists.
        // Apply before vanilla derives forward/up/left and invalidates its view-matrix cache.
        rotation.mul(Rolling.getRollQuaternion().asMoj());
    }
}
