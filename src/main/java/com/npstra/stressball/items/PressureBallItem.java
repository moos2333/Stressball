package com.npstra.stressball.items;

import baubles.api.BaubleType;
import baubles.api.BaublesApi;
import baubles.api.IBauble;
import baubles.api.cap.IBaublesItemHandler;
import com.npstra.stressball.StressBall;
import com.npstra.stressball.config.ConfigHandler;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IEntityOwnable;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.entity.item.EntityPainting;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.entity.passive.EntityOcelot;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.entity.passive.EntityWolf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

@Mod.EventBusSubscriber(modid = StressBall.MODID)
public class PressureBallItem extends Item implements IBauble {
    @GameRegistry.ObjectHolder(StressBall.MODID + ":pressure_ball")
    public static final PressureBallItem PRESSURE_BALL = null;

    private static final double ATTACK_RANGE = 3.0;
    private static final int RIGHT_CLICK_INTERVAL = 2;
    private static final int MOVEMENT_COOLDOWN = 10;
    private static final double MOVEMENT_THRESHOLD = 0.001;

    private static final Map<UUID, Integer> LAST_RIGHT_CLICK_TIME = new WeakHashMap<>();
    private static final Map<UUID, Vec3d> LAST_POSITION = new WeakHashMap<>();
    private static final Map<UUID, Integer> MOVEMENT_TIMER = new WeakHashMap<>();
    private static final Map<UUID, MiningProgress> MINING_PROGRESS = new WeakHashMap<>();

    public PressureBallItem() {
        setMaxStackSize(1);
        setCreativeTab(CreativeTabs.TOOLS);
        setTranslationKey(StressBall.MODID + ".pressure_ball");
        setRegistryName("pressure_ball");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        String full = I18n.format(StressBall.MODID + ".tooltip.pressure_ball");
        for (String line : full.split("\\\\n")) {
            tooltip.add(TextFormatting.GRAY + line);
        }
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        event.getRegistry().register(new PressureBallItem());
    }

    @Override
    public BaubleType getBaubleType(ItemStack itemstack) {
        return BaubleType.TRINKET;
    }

    @Override
    public void onWornTick(ItemStack itemstack, EntityLivingBase player) {
        if (player.world.isRemote) return;
        if (!(player instanceof EntityPlayer)) return;
        EntityPlayer entityPlayer = (EntityPlayer) player;
        UUID uuid = entityPlayer.getUniqueID();

        if (entityPlayer.isSpectator() || entityPlayer.isPlayerSleeping()) {
            MINING_PROGRESS.remove(uuid);
            return;
        }

        ItemStack mainhand = entityPlayer.getHeldItemMainhand();
        ResourceLocation id = mainhand.isEmpty() ? null : mainhand.getItem().getRegistryName();
        String regName = id == null ? "" : id.toString();

        if (!mainhand.isEmpty() && id != null && ConfigHandler.isItemBlacklisted(regName)) {
            MINING_PROGRESS.remove(uuid);
            return;
        }

        Vec3d currentPos = entityPlayer.getPositionVector();
        Vec3d lastPos = LAST_POSITION.get(uuid);
        boolean moved = false;
        if (lastPos != null) {
            double dx = currentPos.x - lastPos.x;
            double dy = currentPos.y - lastPos.y;
            double dz = currentPos.z - lastPos.z;
            if (dx * dx + dy * dy + dz * dz > MOVEMENT_THRESHOLD) {
                moved = true;
            }
        }
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

        if (entityPlayer.isHandActive()) {
            MINING_PROGRESS.remove(uuid);
            return;
        }

        if (!mainhand.isEmpty() && ConfigHandler.isRightClickItem(regName)) {
            int lastTick = LAST_RIGHT_CLICK_TIME.getOrDefault(uuid, 0);
            if (entityPlayer.ticksExisted - lastTick >= RIGHT_CLICK_INTERVAL
                    && !entityPlayer.getCooldownTracker().hasCooldown(mainhand.getItem())) {
                processRightClickAttack(entityPlayer);
                LAST_RIGHT_CLICK_TIME.put(uuid, entityPlayer.ticksExisted);
            }
            return;
        }

        if (!entityPlayer.onGround) {
            MINING_PROGRESS.remove(uuid);
            return;
        }

        boolean canAttack = entityPlayer.getCooledAttackStrength(0.5F) >= 1.0F;
        boolean canMine = !mainhand.isEmpty() && id != null && ConfigHandler.isAutoMineItem(regName);
        if (!canAttack && !canMine) {
            MINING_PROGRESS.remove(uuid);
            return;
        }

        Vec3d eyePos = entityPlayer.getPositionEyes(1.0F);
        Vec3d lookVec = entityPlayer.getLook(1.0F);
        Vec3d endPos = eyePos.add(lookVec.scale(ATTACK_RANGE));
        RayTraceResult blockResult = entityPlayer.world.rayTraceBlocks(eyePos, endPos, false, true, false);

        if (canAttack && performAutoAttack(entityPlayer, eyePos, lookVec, endPos, blockResult)) {
            return;
        }
        tryMine(entityPlayer, eyePos, endPos, blockResult);
    }

    private void processRightClickAttack(EntityPlayer player) {
        ItemStack mainhand = player.getHeldItemMainhand();
        if (mainhand.isEmpty()) return;

        if (player.isCreative()) {
            int count = mainhand.getCount();
            mainhand.getItem().onItemRightClick(player.world, player, EnumHand.MAIN_HAND);
            mainhand.setCount(count);
        } else {
            ActionResult<ItemStack> result = mainhand.getItem().onItemRightClick(player.world, player, EnumHand.MAIN_HAND);
            ItemStack returned = result.getResult();
            if (returned != mainhand) {
                player.setHeldItem(EnumHand.MAIN_HAND, returned);
            }
        }
    }

    private boolean performAutoAttack(EntityPlayer player, Vec3d eyePos, Vec3d lookVec, Vec3d endPos, RayTraceResult blockResult) {
        double blockDist = blockResult != null && blockResult.typeOfHit == RayTraceResult.Type.BLOCK
                ? eyePos.distanceTo(blockResult.hitVec) : ATTACK_RANGE;

        AxisAlignedBB searchBox = player.getEntityBoundingBox()
                .expand(lookVec.x * ATTACK_RANGE, lookVec.y * ATTACK_RANGE, lookVec.z * ATTACK_RANGE)
                .grow(1.0);

        List<Entity> entities = player.world.getEntitiesWithinAABBExcludingEntity(player, searchBox);
        Entity targetEntity = null;
        double closest = blockDist;

        for (Entity entity : entities) {
            if (!entity.canBeCollidedWith() && !entity.canBeAttackedWithItem()) continue;
            if (isExcluded(entity)) continue;

            ResourceLocation entityId = EntityList.getKey(entity.getClass());
            if (entityId != null && ConfigHandler.isEntityBlacklisted(entityId.toString())) continue;

            if (entity instanceof IEntityOwnable) {
                IEntityOwnable ownable = (IEntityOwnable) entity;
                Entity owner = ownable.getOwner();
                if (owner instanceof EntityPlayer && owner.getUniqueID().equals(player.getUniqueID())) continue;
            }

            AxisAlignedBB entityBB = entity.getEntityBoundingBox().grow(entity.getCollisionBorderSize());
            RayTraceResult result = entityBB.calculateIntercept(eyePos, endPos);
            if (result != null) {
                double dist = eyePos.distanceTo(result.hitVec);
                if (dist < closest) {
                    closest = dist;
                    targetEntity = entity;
                }
            }
        }

        if (targetEntity == null) return false;
        if (targetEntity.hurtResistantTime > 0) return true;

        player.attackTargetEntityWithCurrentItem(targetEntity);
        StressBallItem.swingArm(player);
        MINING_PROGRESS.remove(player.getUniqueID());
        return true;
    }

    private void tryMine(EntityPlayer player, Vec3d eyePos, Vec3d endPos, RayTraceResult blockResult) {
        if (blockResult == null || blockResult.typeOfHit != RayTraceResult.Type.BLOCK) {
            MINING_PROGRESS.remove(player.getUniqueID());
            return;
        }

        if (player.isCreative()) return;

        ItemStack mainhand = player.getHeldItemMainhand();
        if (mainhand.isEmpty()) return;
        ResourceLocation id = mainhand.getItem().getRegistryName();
        if (id == null) return;
        if (!ConfigHandler.isAutoMineItem(id.toString())) return;

        BlockPos pos = blockResult.getBlockPos();
        IBlockState state = player.world.getBlockState(pos);
        if (!mainhand.canHarvestBlock(state) && mainhand.getDestroySpeed(state) <= 1.0F) return;

        UUID uuid = player.getUniqueID();
        MiningProgress progress = MINING_PROGRESS.get(uuid);
        if (progress == null || !progress.matches(player.world, pos, state, mainhand)) {
            progress = new MiningProgress(player.world, pos, state, mainhand);
            MINING_PROGRESS.put(uuid, progress);
        }

        progress.addTicks(1);

        if (progress.getCurrentTicks() >= progress.getRequiredTicks()) {
            if (player.world.destroyBlock(pos, true)) {
                mainhand.onBlockDestroyed(player.world, state, pos, player);
                StressBallItem.swingArm(player);
            }
            MINING_PROGRESS.remove(uuid);
        }
    }

    private boolean isExcluded(Entity entity) {
        if (entity instanceof EntityBoat) return true;
        if (entity instanceof EntityItemFrame) return true;
        if (entity instanceof EntityPainting) return true;
        if (entity instanceof EntityArmorStand) return true;
        if (entity instanceof EntityTameable && ((EntityTameable) entity).isTamed()) return true;
        if (entity instanceof EntityWolf && ((EntityWolf) entity).isTamed()) return true;
        if (entity instanceof EntityOcelot && ((EntityOcelot) entity).isTamed()) return true;
        if (entity instanceof EntityHorse && ((EntityHorse) entity).isTame()) return true;
        return false;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        if (!world.isRemote) {
            IBaublesItemHandler baubles = BaublesApi.getBaublesHandler(player);
            ItemStack held = player.getHeldItem(hand);
            for (int i = 0; i < baubles.getSlots(); i++) {
                if (baubles.getStackInSlot(i).isEmpty() && baubles.isItemValidForSlot(i, held, player)) {
                    baubles.setStackInSlot(i, held.copy());
                    held.setCount(0);
                    break;
                }
            }
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, player.getHeldItem(hand));
    }

    @Override
    public void onEquipped(ItemStack itemstack, EntityLivingBase player) {
        player.playSound(SoundEvents.ITEM_ARMOR_EQUIP_DIAMOND, 0.75F, 1.9F);
    }

    @Override
    public void onUnequipped(ItemStack itemstack, EntityLivingBase player) {
        player.playSound(SoundEvents.ITEM_ARMOR_EQUIP_DIAMOND, 0.75F, 2.0F);
        UUID uuid = player.getUniqueID();
        LAST_RIGHT_CLICK_TIME.remove(uuid);
        LAST_POSITION.remove(uuid);
        MOVEMENT_TIMER.remove(uuid);
        MINING_PROGRESS.remove(uuid);
    }

    @Override
    public EnumRarity getRarity(ItemStack stack) {
        return EnumRarity.RARE;
    }

    @Override
    public boolean hasEffect(ItemStack stack) {
        return true;
    }

    private static class MiningProgress {
        private final World world;
        private final BlockPos pos;
        private final IBlockState state;
        private final ItemStack tool;
        private int currentTicks;
        private final int requiredTicks;

        public MiningProgress(World world, BlockPos pos, IBlockState state, ItemStack tool) {
            this.world = world;
            this.pos = pos;
            this.state = state;
            this.tool = tool.copy();
            this.currentTicks = 0;
            this.requiredTicks = calculateRequiredTicks(tool, state);
        }

        private int calculateRequiredTicks(ItemStack tool, IBlockState state) {
            float hardness = state.getBlockHardness(world, pos);
            if (hardness < 0) return Integer.MAX_VALUE;
            float speed = tool.getDestroySpeed(state);
            if (speed <= 0) return Integer.MAX_VALUE;
            boolean canHarvest = tool.canHarvestBlock(state);
            int factor = canHarvest ? 30 : 100;
            int ticks = (int) Math.ceil((double) hardness * factor / speed);
            return Math.max(1, ticks);
        }

        public boolean matches(World world, BlockPos pos, IBlockState state, ItemStack tool) {
            return this.world == world && this.pos.equals(pos) && this.state == state
                    && ItemStack.areItemStacksEqual(this.tool, tool);
        }

        public void addTicks(int ticks) {
            currentTicks += ticks;
        }

        public int getCurrentTicks() {
            return currentTicks;
        }

        public int getRequiredTicks() {
            return requiredTicks;
        }
    }
}