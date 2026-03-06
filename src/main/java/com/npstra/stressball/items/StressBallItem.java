package com.npstra.stressball.items;

import baubles.api.BaubleType;
import baubles.api.BaublesApi;
import baubles.api.IBauble;
import baubles.api.cap.IBaublesItemHandler;
import com.npstra.stressball.StressBall;
import com.npstra.stressball.capability.GuiStateCapability;
import com.npstra.stressball.capability.IGuiState;
import com.npstra.stressball.config.ConfigHandler;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
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
public class StressBallItem extends Item implements IBauble {
    @GameRegistry.ObjectHolder(StressBall.MODID + ":stressball")
    public static final StressBallItem STRESS_BALL = null;

    private static final double ATTACK_RANGE = 3.0;
    private static final double MOVEMENT_THRESHOLD = 0.001;
    private static final int STAND_DELAY = 2;

    private static final Map<UUID, Vec3d> LAST_POSITION = new WeakHashMap<>();
    private static final Map<UUID, Integer> STAND_TIMER = new WeakHashMap<>();

    public StressBallItem() {
        setMaxStackSize(1);
        setCreativeTab(CreativeTabs.TOOLS);
        setTranslationKey(StressBall.MODID + ".stressball");
        setRegistryName("stressball");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        String full = net.minecraft.client.resources.I18n.format(StressBall.MODID + ".tooltip.stressball");
        for (String line : full.split("\\\\n")) {
            tooltip.add(TextFormatting.GRAY + line);
        }
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        event.getRegistry().register(new StressBallItem());
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

        IGuiState guiState = GuiStateCapability.get(entityPlayer);
        boolean guiOpen = guiState != null && guiState.isGuiOpen();

        if (moved || guiOpen) {
            STAND_TIMER.put(uuid, 0);
            return;
        }

        int timer = STAND_TIMER.getOrDefault(uuid, 0);
        if (timer < STAND_DELAY) {
            STAND_TIMER.put(uuid, timer + 1);
            return;
        }

        if (shouldAttack(entityPlayer) && entityPlayer.getCooledAttackStrength(0.5F) >= 1.0F) {
            int lastTick = StressBall.LAST_ATTACK_TICK.getOrDefault(uuid, 0);
            int currentTick = entityPlayer.ticksExisted;
            if (currentTick - lastTick >= 10) {
                performAutoAttack(entityPlayer);
                StressBall.LAST_ATTACK_TICK.put(uuid, currentTick);
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
            player.attackTargetEntityWithCurrentItem(targetEntity);
            return;
        }

        RayTraceResult blockResult = player.world.rayTraceBlocks(eyePos, endPos, false, true, true);
        if (blockResult != null && blockResult.typeOfHit == RayTraceResult.Type.BLOCK) {
            if (player instanceof EntityPlayerMP) {
                EntityPlayerMP mp = (EntityPlayerMP) player;
                mp.interactionManager.onBlockClicked(blockResult.getBlockPos(), blockResult.sideHit);
            }
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
        StressBall.LAST_ATTACK_TICK.remove(uuid);
        LAST_POSITION.remove(uuid);
        STAND_TIMER.remove(uuid);
    }

    @Override
    public EnumRarity getRarity(ItemStack stack) {
        return EnumRarity.RARE;
    }

    @Override
    public boolean hasEffect(ItemStack stack) {
        return true;
    }
}