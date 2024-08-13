package net.ahmirza.MODS;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.client.KeyMapping;

import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Mod(highhelper.MOD_ID)
@OnlyIn(Dist.CLIENT)
public class highhelper {
    public static final String MOD_ID = "highhelper";
    private static final double DETECTION_RADIUS = 10.0;
    private static final double ATTACK_DISTANCE = 3.5;
    private static final float MIN_SMOOTH_FACTOR = 0.1f;
    private static final float MAX_SMOOTH_FACTOR = 0.3f;
    private static final int MIN_ATTACK_DELAY_TICKS = 2;
    private static final int MAX_ATTACK_DELAY_TICKS = 5;
    private static final int MAX_EXTRA_PAUSE_TICKS = 10;
    private static final int TARGET_SELECTION_RANDOMNESS = 3;

    private LivingEntity currentTarget = null;
    private boolean isActive = false;
    private final Random random = new Random();
    private long lastAttackTime = 0;
    private int attackDelayTicks = 0;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public highhelper() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.Key event) {
        if (event.getKey() == 72 && event.getAction() == 1) { // 72 is 'H' key
            toggleMod();
        }
    }

    private void toggleMod() {
        isActive = !isActive;
        Minecraft.getInstance().player.displayClientMessage(
                Component.literal("HighHelper " + (isActive ? "activated" : "deactivated")), false);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !isActive) return;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !isHoldingWeapon(player)) return;

        handleCombat(player);
    }

    private void handleCombat(LocalPlayer player) {
        if (currentTarget == null || !currentTarget.isAlive() || !isTargetInRange(player, currentTarget)) {
            findNewTarget(player);
        } else {
            rotateTowardsTarget(player);
            attackTarget(player);
        }
    }

    private void findNewTarget(LocalPlayer player) {
        AABB searchBox = player.getBoundingBox().inflate(DETECTION_RADIUS);
        List<Entity> nearbyEntities = player.level().getEntities(player, searchBox);

        // Randomly choose one of the closest entities, not always the absolute closest
        currentTarget = nearbyEntities.stream()
                .filter(e -> e instanceof LivingEntity && !(e instanceof Player) && e.isAlive())
                .map(e -> (LivingEntity) e)
                .sorted(Comparator.comparingDouble(player::distanceToSqr))
                .skip(random.nextInt(TARGET_SELECTION_RANDOMNESS))
                .findFirst()
                .orElse(null);
    }

    private void rotateTowardsTarget(LocalPlayer player) {
        if (currentTarget == null) return;

        Vec3 toTarget = currentTarget.position().add(0, currentTarget.getBbHeight() * 0.5, 0)
                .subtract(player.getEyePosition());

        double horizontalDistance = Math.sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z);
        float targetYaw = (float) Math.toDegrees(Math.atan2(-toTarget.x, toTarget.z));
        float targetPitch = (float) -Math.toDegrees(Math.atan2(toTarget.y, horizontalDistance));

        // Increase randomness in rotation
        targetYaw += (random.nextFloat() - 0.5f) * 5f;
        targetPitch += (random.nextFloat() - 0.5f) * 3f;

        player.setYRot(smoothRotation(player.getYRot(), targetYaw));
        player.setXRot(smoothRotation(player.getXRot(), targetPitch));
    }

    private float smoothRotation(float current, float target) {
        float difference = wrapAngleTo180(target - current);
        // Randomize the smooth factor for each rotation
        float smoothFactor = MIN_SMOOTH_FACTOR + random.nextFloat() * (MAX_SMOOTH_FACTOR - MIN_SMOOTH_FACTOR);
        return current + difference * smoothFactor;
    }

    private float wrapAngleTo180(float angle) {
        angle %= 360.0F;
        if (angle >= 180.0F) {
            angle -= 360.0F;
        } else if (angle < -180.0F) {
            angle += 360.0F;
        }
        return angle;
    }

    private void attackTarget(LocalPlayer player) {
        if (canAttack(player) && isTargetInRange(player, currentTarget)) {
            if (attackDelayTicks <= 0) {
                // Randomly decide whether to attack or wait
                if (random.nextInt(10) > 2) {
                    simulateAttack(player);
                    lastAttackTime = System.currentTimeMillis();
                    attackDelayTicks = random.nextInt(MAX_ATTACK_DELAY_TICKS + 1);
                } else {
                    attackDelayTicks = random.nextInt(MAX_EXTRA_PAUSE_TICKS);
                }
            } else {
                attackDelayTicks--;
            }
        }
    }

    private void simulateAttack(LocalPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        KeyMapping attackKey = mc.options.keyAttack;

        int attackDelay = 50 + random.nextInt(150); // Random delay between 50ms and 200ms
        scheduler.schedule(() -> {
            mc.execute(() -> {
                KeyMapping.click(attackKey.getKey());

                try {
                    Thread.sleep(20 + random.nextInt(50)); // Hold for 20-70ms
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                KeyMapping.set(attackKey.getKey(), false);
            });
        }, attackDelay, TimeUnit.MILLISECONDS);
    }

    private boolean canAttack(LocalPlayer player) {
        long currentTime = System.currentTimeMillis();
        float attackSpeed = (float) player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED);
        long attackCooldown = (long) (1000 / attackSpeed);
        return currentTime - lastAttackTime >= attackCooldown;
    }

    private boolean isTargetInRange(LocalPlayer player, LivingEntity target) {
        return player.distanceTo(target) <= ATTACK_DISTANCE;
    }

    private boolean isHoldingWeapon(Player player) {
        Item heldItem = player.getMainHandItem().getItem();
        return heldItem instanceof SwordItem || heldItem instanceof AxeItem;
    }

    public void cleanup() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(800, TimeUnit.MILLISECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
    }
}
