package com.github.voxxin.minecartflipping.mixin;

import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.MinecartBehavior;
import net.minecraft.world.entity.vehicle.NewMinecartBehavior;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Mixin(NewMinecartBehavior.class)
public abstract class MinecartBehaviorMixin extends MinecartBehavior {
    @Shadow
    public NewMinecartBehavior.MinecartStep oldLerp;
    @Shadow
    @Final
    public List<NewMinecartBehavior.MinecartStep> lerpSteps;
    @Unique
    private float previousAngle = 0.0f;
    @Unique
    private final int rotateMax = new Random().nextInt(25 - 18 + 1) + 18;

    @Unique
    private final List<NewMinecartBehavior.MinecartStep> oldLerpSteps = new ArrayList<>();

    protected MinecartBehaviorMixin(AbstractMinecart abstractMinecart) {
        super(abstractMinecart);
    }

    @Unique
    private float mf$calculateAngle(Vec3 deltaMovement, boolean isForwards) {
        if (this.posBreak()) {
            this.previousAngle = 0.0F;
            return 0.0F;
        }

        float newAngle = (float) (Math.atan2(deltaMovement.horizontalDistance(), deltaMovement.y) * this.rotateMax / Math.PI);
        this.previousAngle += (this.previousAngle + newAngle) * 0.060325F;
        this.previousAngle = (this.previousAngle) % 360.0F;
        this.previousAngle *= 1.05F;

        return isForwards ? this.previousAngle : -this.previousAngle;
    }

    @Unique
    private boolean mf$onGround() {
        AABB hitbox = this.minecart.getBoundingBox();
        hitbox.deflate(0.75f);

        Vec3[] pointsToCheck = new Vec3[]{
                new Vec3((hitbox.minX + hitbox.maxX) / 2, hitbox.minY, (hitbox.minZ + hitbox.maxZ) / 2),
                new Vec3(hitbox.minX, hitbox.minY, hitbox.minZ),
                new Vec3(hitbox.maxX, hitbox.minY, hitbox.minZ),
                new Vec3(hitbox.minX, hitbox.minY, hitbox.maxZ),
                new Vec3(hitbox.maxX, hitbox.minY, hitbox.maxZ)
        };



        for (Vec3 point : pointsToCheck) {
            if (this.minecart.level().clip(new ClipContext(point, point.add(0, -0.002, 0),
                    ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, this.minecart)).getType() == HitResult.Type.BLOCK ||
                    this.minecart.level().getEntities(this.minecart, hitbox.expandTowards(0, -1, 0).deflate(0.245)).stream()
                            .anyMatch(entity -> entity != this.minecart.getFirstPassenger())) {
                return true;
            }
        }

        return false;
    }


    @Inject(at = @At(value = "TAIL"), method = "setOldLerpValues")
    private void oldLerp(CallbackInfo ci) {
        if (!this.posBreak()) {
            this.oldLerp = new NewMinecartBehavior.MinecartStep(this.oldLerp.position(), this.oldLerp.movement(), this.oldLerp.yRot(), mf$calculateAngle(this.oldLerp.movement(), this.oldLerp.movement().x != 0 ? this.oldLerp.movement().x > 0 : this.oldLerp.movement().z > 0), this.oldLerp.weight());
        }
    }


    @Inject(at = @At(value = "INVOKE_ASSIGN", target = "Ljava/util/List;addAll(Ljava/util/Collection;)Z", shift = At.Shift.BEFORE), method = "lerpClientPositionAndRotation")
    private void mf$onLerpClientPositionAndRotation(CallbackInfo ci) {
        for (int i = 0; i < this.lerpSteps.size(); ++i) {
            NewMinecartBehavior.MinecartStep step = this.lerpSteps.get(i);
            boolean isForwards = step.movement().x != 0 ? step.movement().x > 0 : step.movement().z > 0;
            if (this.posBreak()) {
                this.previousAngle = isForwards ?step.xRot()/2:-step.xRot()/2;
                continue;
            }
            if (this.oldLerpSteps.contains(step)) continue;
            this.oldLerpSteps.add(step);

            step = new NewMinecartBehavior.MinecartStep(step.position(), step.movement(), step.yRot(), mf$calculateAngle(step.position(), isForwards), step.weight());
            this.lerpSteps.set(i, step);
            this.oldLerpSteps.add(step);
        }
    }

    @Inject(at = @At(value = "INVOKE", target = "Ljava/util/List;clear()V"), method = "lerpClientPositionAndRotation")
    private void clearOldLerp(CallbackInfo ci) {
        this.oldLerpSteps.clear();
    }

    @Unique
    private boolean posBreak() {
        return mf$onGround() || this.minecart.isOnRails();
    }
}
