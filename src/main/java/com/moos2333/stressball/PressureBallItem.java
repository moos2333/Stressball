package com.moos2333.stressball.items;

import com.moos2333.stressball.config.ConfigHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;

public class PressureBallItem extends Item implements ICurioItem {

    private static final double ATTACK_RANGE = 3.0;
    private static final int RIGHT_CLICK_INTERVAL = 2;
    private static final int MOVEMENT_COOLDOWN = 10;
    private static final double MOVEMENT_THRESHOLD = 0.001;

    private static final Map<UUID, Integer> LAST_RIGHT_CLICK_TIME = new WeakHashMap<>();
    private static final Map<UUID, Vec3> LAST_POSITION = new WeakHashMap<>();
    private static final Map<UUID, Integer> MOVEMENT_TIMER = new WeakHashMap<>();
    private static final Map<UUID, MiningProgress> MINING_PROGRESS = new WeakHashMap<>();

    public PressureBallItem(Properties properties) {
        super(properties);
    }

    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
        ICurioItem.super.curioTick(slotContext, stack);
        LivingEntity entity = slotContext.entity();
        if (!(entity instanceof Player player)) return;
        if (player.level().isClientSide()) return;

        UUID uuid = player.getUUID();

        if (player.isSpectator() || player.isSleeping()) {
            MINING_PROGRESS.remove(uuid);
            return;
        }

        ItemStack mainhand = player.getMainHandItem();
        ResourceLocation id = mainhand.isEmpty() ? null
                : BuiltInRegistries.ITEM.getKey(mainhand.getItem());

        if (ConfigHandler.isItemBlacklisted(id)) {
            MINING_PROGRESS.remove(uuid);
            return;
        }

        Vec3 currentPos = player.position();
        Vec3 lastPos = LAST_POSITION.get(uuid);
        boolean moved = lastPos != null && currentPos.distanceToSqr(lastPos) > MOVEMENT_THRESHOLD;
        LAST_POSITION.put(uuid, currentPos);

        if (moved) {
            MOVEMENT_TIMER.put(uuid, MOVEMENT_COOLDOWN);
            MINING_PROGRESS.remove(uuid);
            return;
        }

        Integer timer = MOVEMENT_TIMER.get(uuid);
        if (timer != null) {
            if (timer > 0) {
                MOVEMENT_TIMER.put(uuid, timer - 1);
                MINING_PROGRESS.remove(uuid);
                return;
            } else {
                MOVEMENT_TIMER.remove(uuid);
            }
        }

        if (player.isUsingItem()) {
            MINING_PROGRESS.remove(uuid);
            return;
        }

        if (ConfigHandler.isRightClickItem(id)) {
            int lastTick = LAST_RIGHT_CLICK_TIME.getOrDefault(uuid, 0);
            if (player.tickCount - lastTick >= RIGHT_CLICK_INTERVAL
                    && !player.getCooldowns().isOnCooldown(mainhand.getItem())) {
                mainhand.use(player.level(), player, InteractionHand.MAIN_HAND);
                LAST_RIGHT_CLICK_TIME.put(uuid, player.tickCount);
            }
            return;
        }

        if (!player.onGround()) {
            MINING_PROGRESS.remove(uuid);
            return;
        }

        boolean canAttack = player.getAttackStrengthScale(0.5F) >= 1.0F;
        boolean canMine = ConfigHandler.isAutoMineItem(id);
        if (!canAttack && !canMine) {
            MINING_PROGRESS.remove(uuid);
            return;
        }

        Vec3 eyePos = player.getEyePosition(1.0F);
        Vec3 lookVec = player.getViewVector(1.0F);
        Vec3 endPos = eyePos.add(lookVec.scale(ATTACK_RANGE));
        HitResult blockResult = player.level().clip(new ClipContext(eyePos, endPos,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));

        if (canAttack && performAutoAttack(player, uuid, eyePos, lookVec, endPos, blockResult)) {
            return;
        }
        if (canMine) {
            tryMine(player, uuid, mainhand, blockResult);
        }
    }

    private boolean performAutoAttack(Player player, UUID uuid, Vec3 eyePos, Vec3 lookVec,
                                      Vec3 endPos, HitResult blockResult) {
        double blockDist = blockResult.getType() == HitResult.Type.BLOCK
                ? eyePos.distanceTo(blockResult.getLocation()) : ATTACK_RANGE;

        AABB searchBox = player.getBoundingBox()
                .expandTowards(lookVec.scale(ATTACK_RANGE)).inflate(1.0);

        List<Entity> entities = player.level().getEntities(player, searchBox);
        Entity target = null;
        double closest = blockDist;

        for (Entity entity : entities) {
            if (!entity.isPickable() && !entity.canBeHitByProjectile()) continue;
            if (isExcluded(entity)) continue;

            ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
            if (ConfigHandler.isEntityBlacklisted(entityId)) continue;

            if (entity instanceof OwnableEntity ownable) {
                if (ownable.getOwnerUUID() != null && ownable.getOwnerUUID().equals(uuid)) continue;
            }

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

        if (target == null) return false;
        if (target instanceof LivingEntity living && living.hurtTime > living.hurtDuration / 2) return true;

        player.attack(target);
        StressBallItem.swingArm(player);
        MINING_PROGRESS.remove(uuid);
        return true;
    }

    private void tryMine(Player player, UUID uuid, ItemStack mainhand, HitResult blockResult) {
        if (blockResult.getType() != HitResult.Type.BLOCK) {
            MINING_PROGRESS.remove(uuid);
            return;
        }

        if (player.isCreative()) return;

        BlockPos pos = ((BlockHitResult) blockResult).getBlockPos();
        BlockState state = player.level().getBlockState(pos);
        if (!mainhand.isCorrectToolForDrops(state) && mainhand.getDestroySpeed(state) <= 1.0F) return;

        MiningProgress progress = MINING_PROGRESS.get(uuid);
        if (progress == null || !progress.matches(player.level(), pos, state, mainhand)) {
            progress = new MiningProgress(player.level(), pos, state, mainhand);
            MINING_PROGRESS.put(uuid, progress);
        }

        progress.addTicks(1);

        if (progress.getCurrentTicks() >= progress.getRequiredTicks()) {
            if (player.level().destroyBlock(pos, true)) {
                mainhand.mineBlock(player.level(), state, pos, player);
                StressBallItem.swingArm(player);
            }
            MINING_PROGRESS.remove(uuid);
        }
    }

    private boolean isExcluded(Entity entity) {
        if (entity instanceof Boat || entity instanceof ItemFrame || entity instanceof Painting
                || entity instanceof ArmorStand) return true;
        if (entity instanceof TamableAnimal tamable && tamable.isTame()) return true;
        return false;
    }

    public static void cleanup(UUID uuid) {
        LAST_RIGHT_CLICK_TIME.remove(uuid);
        LAST_POSITION.remove(uuid);
        MOVEMENT_TIMER.remove(uuid);
        MINING_PROGRESS.remove(uuid);
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

    private static class MiningProgress {
        private final Level level;
        private final BlockPos pos;
        private final BlockState state;
        private final ItemStack tool;
        private int currentTicks;
        private final int requiredTicks;

        MiningProgress(Level level, BlockPos pos, BlockState state, ItemStack tool) {
            this.level = level;
            this.pos = pos;
            this.state = state;
            this.tool = tool.copy();
            this.currentTicks = 0;
            this.requiredTicks = calculateRequiredTicks(tool, state);
        }

        private int calculateRequiredTicks(ItemStack tool, BlockState state) {
            float hardness = state.getDestroySpeed(level, pos);
            if (hardness < 0) return Integer.MAX_VALUE;
            float speed = tool.getDestroySpeed(state);
            if (speed <= 0) return Integer.MAX_VALUE;
            boolean canHarvest = tool.isCorrectToolForDrops(state);
            int factor = canHarvest ? 30 : 100;
            int ticks = (int) Math.ceil((double) hardness * factor / speed);
            return Math.max(1, ticks);
        }

        boolean matches(Level level, BlockPos pos, BlockState state, ItemStack tool) {
            return this.level == level && this.pos.equals(pos) && this.state == state
                    && ItemStack.matches(this.tool, tool);
        }

        void addTicks(int ticks) { currentTicks += ticks; }
        int getCurrentTicks() { return currentTicks; }
        int getRequiredTicks() { return requiredTicks; }
    }
}