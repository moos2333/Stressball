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
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
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

@Mod.EventBusSubscriber
public class PressureBallItem extends Item implements IBauble {
    @GameRegistry.ObjectHolder(StressBall.MODID + ":pressure_ball")
    public static final PressureBallItem PRESSURE_BALL = null;

    private static final double ATTACK_RANGE = 3.0;
    private static final int ATTACK_INTERVAL = 10;
    private static final int MOVEMENT_COOLDOWN = 10;
    private static final double MOVEMENT_THRESHOLD = 0.001;

    private static final Map<UUID, Integer> LAST_ATTACK_TIME = new WeakHashMap<>();
    private static final Map<UUID, BlockPos> LAST_POSITION = new WeakHashMap<>();
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

        BlockPos currentPos = entityPlayer.getPosition();
        BlockPos lastPos = LAST_POSITION.get(uuid);
        if (lastPos != null) {
            double dx = currentPos.getX() - lastPos.getX();
            double dy = currentPos.getY() - lastPos.getY();
            double dz = currentPos.getZ() - lastPos.getZ();
            if (dx * dx + dy * dy + dz * dz > MOVEMENT_THRESHOLD) {
                MOVEMENT_TIMER.put(uuid, MOVEMENT_COOLDOWN);
            }
        }
        LAST_POSITION.put(uuid, currentPos);

        Integer timer = MOVEMENT_TIMER.get(uuid);
        if (timer != null) {
            if (timer > 0) {
                MOVEMENT_TIMER.put(uuid, timer - 1);
                return;
            } else {
                MOVEMENT_TIMER.remove(uuid);
            }
        }

        if (shouldAttack(entityPlayer) && entityPlayer.getCooledAttackStrength(0.5F) >= 1.0F) {
            int lastTick = LAST_ATTACK_TIME.getOrDefault(uuid, 0);
            int currentTick = entityPlayer.ticksExisted;
            if (currentTick - lastTick >= ATTACK_INTERVAL) {
                ItemStack mainhand = entityPlayer.getHeldItemMainhand();
                String regName = mainhand.isEmpty() ? "" : mainhand.getItem().getRegistryName().toString();

                if (!mainhand.isEmpty() && ConfigHandler.isRightClickItem(regName)) {
                    processRightClickAttack(entityPlayer);
                } else {
                    performAutoAttack(entityPlayer);
                }
                LAST_ATTACK_TIME.put(uuid, currentTick);
            }
        }
    }

    private boolean shouldAttack(EntityPlayer player) {
        ItemStack mainhand = player.getHeldItemMainhand();
        if (!mainhand.isEmpty()) {
            String regName = mainhand.getItem().getRegistryName().toString();
            if (ConfigHandler.isItemBlacklisted(regName)) return false;
        }

        if (player.moveForward != 0 || player.moveStrafing != 0) return false;
        if (!player.onGround) return false;
        if (player.isHandActive()) return false;
        if (player.isPlayerSleeping()) return false;
        return true;
    }

    private void processRightClickAttack(EntityPlayer player) {
        ItemStack mainhand = player.getHeldItemMainhand();
        if (mainhand.isEmpty()) return;

        if (player.isCreative()) {
            ItemStack copy = mainhand.copy();
            mainhand.getItem().onItemRightClick(player.world, player, EnumHand.MAIN_HAND);
            player.setHeldItem(EnumHand.MAIN_HAND, copy);
        } else {
            mainhand.getItem().onItemRightClick(player.world, player, EnumHand.MAIN_HAND);
        }
    }

    private void performAutoAttack(EntityPlayer player) {
        Vec3d eyePos = player.getPositionEyes(1.0F);
        Vec3d lookVec = player.getLook(1.0F);
        Vec3d endPos = eyePos.add(lookVec.scale(ATTACK_RANGE));

        AxisAlignedBB searchBox = player.getEntityBoundingBox().grow(ATTACK_RANGE);
        List<Entity> entities = player.world.getEntitiesWithinAABBExcludingEntity(player, searchBox);
        Entity targetEntity = null;
        double closest = Double.MAX_VALUE;

        for (Entity entity : entities) {
            if (!entity.canBeCollidedWith() && !entity.canBeAttackedWithItem()) continue;
            AxisAlignedBB entityBB = entity.getEntityBoundingBox().grow(0.3);
            RayTraceResult result = entityBB.calculateIntercept(eyePos, endPos);
            if (result != null) {
                double dist = eyePos.distanceTo(result.hitVec);
                if (dist < closest) {
                    closest = dist;
                    targetEntity = entity;
                }
            }
        }

        if (targetEntity != null) {
            ResourceLocation entityId = EntityList.getKey(targetEntity.getClass());
            if (entityId != null && ConfigHandler.isEntityBlacklisted(entityId.toString())) return;

            if (targetEntity instanceof IEntityOwnable) {
                IEntityOwnable ownable = (IEntityOwnable) targetEntity;
                Entity owner = ownable.getOwner();
                if (owner instanceof EntityPlayer && owner.getUniqueID().equals(player.getUniqueID())) return;
            }

            player.attackTargetEntityWithCurrentItem(targetEntity);
            return;
        }

        RayTraceResult blockResult = player.world.rayTraceBlocks(eyePos, endPos, false, true, true);
        if (blockResult != null && blockResult.typeOfHit == RayTraceResult.Type.BLOCK) {
            if (player.isCreative()) return;
            ItemStack mainhand = player.getHeldItemMainhand();
            if (mainhand.isEmpty()) return;
            String regName = mainhand.getItem().getRegistryName().toString();
            if (!ConfigHandler.isAutoMineItem(regName)) return;

            BlockPos pos = blockResult.getBlockPos();
            IBlockState state = player.world.getBlockState(pos);
            if (!mainhand.canHarvestBlock(state) && mainhand.getDestroySpeed(state) <= 1.0F) return;

            UUID uuid = player.getUniqueID();
            MiningProgress progress = MINING_PROGRESS.get(uuid);
            if (progress == null || !progress.matches(pos, state, mainhand)) {
                progress = new MiningProgress(pos, state, mainhand);
                MINING_PROGRESS.put(uuid, progress);
            }

            int requiredTicks = progress.getRequiredTicks();
            progress.addTicks(ATTACK_INTERVAL);

            if (progress.getCurrentTicks() >= requiredTicks) {
                if (player.world.destroyBlock(pos, true)) {
                    mainhand.onBlockDestroyed(player.world, state, pos, player);
                }
                MINING_PROGRESS.remove(uuid);
            }
        } else {
            MINING_PROGRESS.remove(player.getUniqueID());
        }
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
        LAST_ATTACK_TIME.remove(uuid);
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
        private final BlockPos pos;
        private final IBlockState state;
        private final ItemStack tool;
        private int currentTicks;
        private final int requiredTicks;

        public MiningProgress(BlockPos pos, IBlockState state, ItemStack tool) {
            this.pos = pos;
            this.state = state;
            this.tool = tool.copy();
            this.currentTicks = 0;
            this.requiredTicks = calculateRequiredTicks(tool, state);
        }

        private int calculateRequiredTicks(ItemStack tool, IBlockState state) {
            float hardness = state.getBlockHardness(null, null);
            if (hardness < 0) return Integer.MAX_VALUE;
            float speed = tool.getDestroySpeed(state);
            if (speed <= 0) return Integer.MAX_VALUE;
            int ticks = (int) Math.ceil((hardness / speed) * 20);
            return Math.max(1, ticks);
        }

        public boolean matches(BlockPos pos, IBlockState state, ItemStack tool) {
            return this.pos.equals(pos) && this.state == state && ItemStack.areItemStacksEqual(this.tool, tool);
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