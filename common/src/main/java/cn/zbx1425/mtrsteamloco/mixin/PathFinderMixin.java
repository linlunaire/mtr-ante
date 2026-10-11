package cn.zbx1425.mtrsteamloco.mixin;

import cn.zbx1425.mtrsteamloco.path.BetterPathFinder;
import mtr.path.*;
import mtr.data.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.*;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PathFinder.class)
public abstract class PathFinderMixin {

    @Inject(method = "findPath", at = @At("HEAD"), cancellable = true, remap = false)
    private static void findPath(List<PathData> path, Map<BlockPos, Map<BlockPos, Rail>> rails, List<SavedRailBase> savedRailBases, int stopIndexOffset, int cruisingAltitude, boolean useFastSpeed, CallbackInfoReturnable<Integer> cir) {
        // System.out.println("Mixin findPath");
        cir.setReturnValue(BetterPathFinder.findPath(path, rails, savedRailBases, stopIndexOffset, cruisingAltitude, useFastSpeed));
        cir.cancel();
    }

    @Inject(method = "appendPath", at = @At("HEAD"), cancellable = true, remap = false)
    private static void appendPath(List<PathData> path, List<PathData> partialPath, CallbackInfo ci) {
        // System.out.println("Mixin appendPath");
        BetterPathFinder.appendPath(path, partialPath);
        ci.cancel();
    }
}