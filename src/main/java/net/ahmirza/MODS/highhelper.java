package net.ahmirza.MODS;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

@Mod(highhelper.MOD_ID)
public class highhelper {
    public static final String MOD_ID = "highhelper";
    private static final double DETECTION_RADIUS = 8.0;
    private static final double ATTACK_RANGE = 3.0;
    private static final float ROTATION_SPEED = 0.4f;
    private static final float KILL_ROTATION_SPEED = 0.4f;
    private static final int MIN_JUMP_COOLDOWN = 10;
    private static final int MAX_JUMP_COOLDOWN = 20;
    private static final int BASE_AUTOCLICKER_COOLDOWN = 10;
    private static final int POST_KILL_DELAY = 5;
    private static final double FRONT_FOV_ANGLE = Math.toRadians(60);
    private static final double REAR_FOV_ANGLE = Math.toRadians(120);

    private static final Random random = new Random();
    private static int jumpCooldownCounter = 0;
    private static LivingEntity currentTarget = null;
    private static int autoClickerCooldown = 0;
    private static boolean isPanningAfterKill = false;
    private static int postKillDelayCounter = 0;
    private static Vec3 targetPoint;
    private static Vec3 killedMobCenter;
    private static boolean checkingRearMobs = false;

    public highhelper() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void updateTargetPoint(Entity target) {
        AABB boundingBox = target.getBoundingBox();
        double randomX = boundingBox.minX + (boundingBox.maxX - boundingBox.minX) * (0.4 + random.nextDouble() * 0.2);
        double randomY = boundingBox.minY + (boundingBox.maxY - boundingBox.minY) * (0.4 + random.nextDouble() * 0.2);
        double randomZ = boundingBox.minZ + (boundingBox.maxZ - boundingBox.minZ) * (0.4 + random.nextDouble() * 0.2);
        targetPoint = new Vec3(randomX, randomY, randomZ);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        if (player == null || mc.level == null) return;

        // Update cooldowns
        if (jumpCooldownCounter > 0) {
            jumpCooldownCounter--;
        }
        if (autoClickerCooldown > 0) {
            autoClickerCooldown--;
        }

        if (isPanningAfterKill) {
            if (panCameraToKilledMob(player)) {
                isPanningAfterKill = false;
                killedMobCenter = null;
                postKillDelayCounter = POST_KILL_DELAY;
            }
            return; // Skip other processing while panning
        }

        if (postKillDelayCounter > 0) {
            postKillDelayCounter--;
            return; // Skip processing during post-kill delay
        }

        if (isHoldingWeapon(player)) {
            List<LivingEntity> nearbyMobs;
            if (!checkingRearMobs) {
                nearbyMobs = findNearbyMobsInFOV(player, FRONT_FOV_ANGLE);
                if (nearbyMobs.isEmpty()) {
                    checkingRearMobs = true;
                }
            } else {
                nearbyMobs = findNearbyMobsInFOV(player, REAR_FOV_ANGLE);
                if (nearbyMobs.isEmpty()) {
                    checkingRearMobs = false;
                }
            }

            if (!nearbyMobs.isEmpty()) {
                currentTarget = selectClosestTarget(player, nearbyMobs);
                updateTargetPoint(currentTarget);
                rotateTowardsTargetPoint(player, ROTATION_SPEED);
                moveTowardsTarget(player, currentTarget);

                // Attack if in range
                if (player.distanceTo(currentTarget) <= ATTACK_RANGE) {
                    autoClicker(mc, player, nearbyMobs.size());
                }
            } else {
                currentTarget = null;
                player.setSprinting(false);
            }
        }
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() == currentTarget) {
            isPanningAfterKill = true;
            AABB boundingBox = currentTarget.getBoundingBox();
            killedMobCenter = new Vec3(
                    (boundingBox.minX + boundingBox.maxX) / 2,
                    (boundingBox.minY + boundingBox.maxY) / 2,
                    (boundingBox.minZ + boundingBox.maxZ) / 2
            );
            currentTarget = null;
        }
    }

    private boolean panCameraToKilledMob(LocalPlayer player) {
        if (killedMobCenter == null) return true;

        Vec3 playerEyes = player.getEyePosition();
        Vec3 toTarget = killedMobCenter.subtract(playerEyes);

        double horizontalDistance = Math.sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z);
        float targetYaw = (float) Math.toDegrees(Math.atan2(-toTarget.x, toTarget.z));
        float targetPitch = (float) -Math.toDegrees(Math.atan2(toTarget.y, horizontalDistance));

        // Prevent camera from pointing downwards
        targetPitch = Math.min(targetPitch, 0);

        float yawDifference = targetYaw - player.getYRot();
        float pitchDifference = targetPitch - player.getXRot();

        while (yawDifference < -180) yawDifference += 360;
        while (yawDifference > 180) yawDifference -= 360;

        player.setYRot(player.getYRot() + yawDifference * KILL_ROTATION_SPEED);
        player.setXRot(player.getXRot() + pitchDifference * KILL_ROTATION_SPEED);

        // Check if the camera is close enough to the target rotation
        return Math.abs(yawDifference) < 1 && Math.abs(pitchDifference) < 1;
    }

    private boolean isHoldingWeapon(LocalPlayer player) {
        Item heldItem = player.getMainHandItem().getItem();
        return heldItem instanceof SwordItem || heldItem instanceof AxeItem || heldItem instanceof BowItem || heldItem instanceof CrossbowItem;
    }

    private List<LivingEntity> findNearbyMobsInFOV(LocalPlayer player, double fovAngle) {
        List<LivingEntity> nearbyMobs = new ArrayList<>();
        Vec3 playerLook = player.getLookAngle();

        for (Entity entity : player.level().getEntities(player, player.getBoundingBox().inflate(DETECTION_RADIUS))) {
            if (entity instanceof LivingEntity && !(entity instanceof LocalPlayer) && entity.isAlive()) {
                Vec3 toEntity = entity.position().subtract(player.position()).normalize();
                double angle = Math.acos(playerLook.dot(toEntity));

                if (angle <= fovAngle) {
                    nearbyMobs.add((LivingEntity) entity);
                }
            }
        }
        return nearbyMobs;
    }

    private LivingEntity selectClosestTarget(LocalPlayer player, List<LivingEntity> nearbyMobs) {
        return nearbyMobs.stream()
                .min(Comparator.comparingDouble(player::distanceToSqr))
                .orElse(null);
    }

    private void rotateTowardsTargetPoint(LocalPlayer player, float rotationSpeed) {
        if (targetPoint == null) return;

        Vec3 playerEyes = player.getEyePosition();
        Vec3 toTarget = targetPoint.subtract(playerEyes);

        double horizontalDistance = Math.sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z);
        float targetYaw = (float) Math.toDegrees(Math.atan2(-toTarget.x, toTarget.z));
        float targetPitch = (float) -Math.toDegrees(Math.atan2(toTarget.y, horizontalDistance));

        // Prevent camera from pointing downwards
        targetPitch = Math.min(targetPitch, 0);

        float yawDifference = targetYaw - player.getYRot();
        float pitchDifference = targetPitch - player.getXRot();

        while (yawDifference < -180) yawDifference += 360;
        while (yawDifference > 180) yawDifference -= 360;

        player.setYRot(player.getYRot() + yawDifference * rotationSpeed);
        player.setXRot(player.getXRot() + pitchDifference * rotationSpeed);
    }

    private void moveTowardsTarget(LocalPlayer player, Entity target) {
        Vec3 playerPos = player.position();
        Vec3 targetPos = target.position();
        Vec3 movement = targetPos.subtract(playerPos).normalize();

        player.setDeltaMovement(movement.x, player.getDeltaMovement().y, movement.z);
        player.setSprinting(true);

        // Jump if there's a block in front of the player
        if (jumpCooldownCounter <= 0 && isBlockInFront(player)) {
            player.jumpFromGround();
            jumpCooldownCounter = MIN_JUMP_COOLDOWN + random.nextInt(MAX_JUMP_COOLDOWN - MIN_JUMP_COOLDOWN + 1);
        }
    }

    private boolean isBlockInFront(LocalPlayer player) {
        Vec3 playerPos = player.position();
        Vec3 lookVec = player.getLookAngle();
        Vec3 checkPos = playerPos.add(lookVec.x, 0, lookVec.z);
        return !player.level().getBlockState(new BlockPos((int) checkPos.x, (int) playerPos.y, (int) checkPos.z)).isAir();
    }

    private void autoClicker(Minecraft mc, LocalPlayer player, int mobCount) {
        if (autoClickerCooldown <= 0) {
            mc.gameMode.attack(player, currentTarget);
            player.swing(InteractionHand.MAIN_HAND);
            autoClickerCooldown = Math.max(1, BASE_AUTOCLICKER_COOLDOWN - mobCount);
        }
    }
}