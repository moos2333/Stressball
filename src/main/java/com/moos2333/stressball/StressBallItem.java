package com.moos2333.stressball.items;

import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;

public class StressBallItem extends Item implements ICurioItem {

    private static final double ATTACK_RANGE = 3.0;
    private static final double MOVEMENT_THRESHOLD = 0.001;
    private static final int STAND_DELAY = 2;

    private static final Map<UUID, Vec3> LAST_POSITION = new WeakHashMap<>();
    private static final Map<UUID, Integer> STAND_TIMER = new WeakHashMap<>();

    public StressBallItem(Properties properties) {
        super(properties);
    }

    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
        ICurioItem.super.curioTick(slotContext, stack);
        LivingEntity entity = slotContext.entity();
        if (!(entity instanceof Player player)) return;
        if (player.level().isClientSide()) return;

        UUID uuid = player.getUUID();
        Vec3 currentPos = player.position();
        Vec3 lastPos = LAST_POSITION.get(uuid);
        boolean moved = lastPos != null && currentPos.distanceToSqr(lastPos) > MOVEMENT_THRESHOLD;
        LAST_POSITION.put(uuid, currentPos);

        if (moved) {
            STAND_TIMER.put(uuid, 0);
            return;
        }

        int timer = STAND_TIMER.getOrDefault(uuid, 0);
        if (timer < STAND_DELAY) {
            STAND_TIMER.put(uuid, timer + 1);
            return;
        }

        if (!shouldAttack(player)) return;
        if (player.getAttackStrengthScale(0.5F) < 1.0F) return;

        Entity target = findTarget(player);
        if (target == null) return;
        if (target instanceof LivingEntity living && living.hurtTime > living.hurtDuration / 2) return;

        player.attack(target);
        swingArm(player);
    }

    private boolean shouldAttack(Player player) {
        if (player.isSpectator()) return false;
        if (!player.onGround()) return false;
        if (player.isUsingItem()) return false;
        if (player.isSleeping()) return false;
        return true;
    }

    private Entity findTarget(Player player) {
        Vec3 eyePos = player.getEyePosition(1.0F);
        Vec3 lookVec = player.getViewVector(1.0F);
        Vec3 endPos = eyePos.add(lookVec.scale(ATTACK_RANGE));

        HitResult blockHit = player.level().clip(new ClipContext(eyePos, endPos,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double blockDist = blockHit.getType() == HitResult.Type.BLOCK
                ? eyePos.distanceTo(blockHit.getLocation()) : ATTACK_RANGE;

        AABB searchBox = player.getBoundingBox()
                .expandTowards(lookVec.scale(ATTACK_RANGE)).inflate(1.0);

        List<Entity> entities = player.level().getEntities(player, searchBox);
        Entity target = null;
        double closest = blockDist;

        for (Entity entity : entities) {
            if (!entity.isPickable() && !entity.canBeHitByProjectile()) continue;
            if (entity instanceof Boat || entity instanceof ItemFrame || entity instanceof Painting
                    || entity instanceof ArmorStand) continue;
            if (entity instanceof TamableAnimal tamable && tamable.isTame()) continue;

            AABB entityBB = entity.getBoundingBox().inflate(entity.getPickRadius());
            Optional<Vec3> result = entityBB.clip(eyePos, endPos);
            if (result.isPresent()) {
                double dist = eyePos.distanceTo(result.get());
                if (dist < closest) {
                    closest = dist;
                    target = entity;
                }
            }
        }
        return target;
    }

    static void swingArm(Player player) {
        player.swing(InteractionHand.MAIN_HAND, true);
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.connection.send(new ClientboundAnimatePacket(player, 0));
        }
    }

    public static void cleanup(UUID uuid) {
        LAST_POSITION.remove(uuid);
        STAND_TIMER.remove(uuid);
    }

    @Override
    public void onEquip(SlotContext slotContext, ItemStack prevStack, ItemStack stack) {
        if (slotContext.entity() instanceof Player player && !player.level().isClientSide()) {
            player.playSound(SoundEvents.ARMOR_EQUIP_DIAMOND, 0.75F, 1.9F);
        }
    }

    @Override
    public void onUnequip(SlotContext slotContext, ItemStack newStack, ItemStack stack) {
        if (slotContext.entity() instanceof Player player && !player.level().isClientSide()) {
            player.playSound(SoundEvents.ARMOR_EQUIP_DIAMOND, 0.75F, 2.0F);
            cleanup(player.getUUID());
        }
    }
}