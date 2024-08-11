package net.ahmirza.tutorialmod;


import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

import java.util.List;

@Mod("highhelper")
@EventBusSubscriber(modid = "highhelper", bus = Bus.FORGE)
public class highhelper {

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        Player player = event.player;
        var level = player.level();

        if (!level.isClientSide) {
            // Check if the player is holding a sword
            ItemStack heldItem = player.getMainHandItem();
            if (heldItem.getItem() instanceof SwordItem) {
                // Define a 5-block radius AABB around the player
                AABB boundingBox = new AABB(
                        player.getX() - 5, player.getY() - 5, player.getZ() - 5,
                        player.getX() + 5, player.getY() + 5, player.getZ() + 5
                );

                // Get all living entities within the bounding box (excluding the player)
                List<Entity> entities = level.getEntities(player, boundingBox, entity -> entity instanceof LivingEntity && !(entity instanceof Player));

                // Physically attack each entity
                for (Entity entity : entities) {
                    if (entity instanceof LivingEntity livingEntity) {
                        // Simulate the player hitting the entity
                        player.attack(livingEntity);
                        // Apply the sword's cooldown to prevent spamming
                        player.resetAttackStrengthTicker();
                    }
                }
            }
        }
    }
}