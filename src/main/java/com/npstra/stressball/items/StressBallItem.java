package com.npstra.stressball.items;

import baubles.api.BaubleType;
import baubles.api.BaublesApi;
import baubles.api.IBauble;
import baubles.api.cap.IBaublesItemHandler;
import com.npstra.stressball.StressBall;
import com.npstra.stressball.capability.GuiStateCapability;
import com.npstra.stressball.capability.IGuiState;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
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
import net.minecraft.world.World;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;

import java.util.UUID;

@Mod.EventBusSubscriber
public class StressBallItem extends Item implements IBauble {
    @GameRegistry.ObjectHolder(StressBall.MODID + ":stressball")
    public static final StressBallItem STRESS_BALL = null;

    private static final int STAND_STILL_DELAY = 20;
    private static final double ATTACK_RANGE = 3.0;

    public StressBallItem() {
        setMaxStackSize(1);
        setCreativeTab(CreativeTabs.TOOLS);
        setTranslationKey(StressBall.MODID + ".stressball");
        setRegistryName("stressball");
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

        if (isPlayerStable(entityPlayer)) {
            int timer = StressBall.STAND_STILL_TIMER.getOrDefault(uuid, 0);
            if (timer < STAND_STILL_DELAY) {
                StressBall.STAND_STILL_TIMER.put(uuid, timer + 1);
            }
            if (timer >= STAND_STILL_DELAY - 1) {
                if (entityPlayer.getCooledAttackStrength(0.5F) >= 1.0F) {
                    int lastTick = StressBall.LAST_ATTACK_TICK.getOrDefault(uuid, 0);
                    int currentTick = entityPlayer.ticksExisted;
                    if (currentTick - lastTick >= 10) {
                        performAutoAttack(entityPlayer);
                        StressBall.LAST_ATTACK_TICK.put(uuid, currentTick);
                    }
                }
            }
        } else {
            StressBall.STAND_STILL_TIMER.remove(uuid);
        }
    }

    private boolean isPlayerStable(EntityPlayer player) {
        double motionX = player.motionX;
        double motionZ = player.motionZ;
        if (motionX * motionX + motionZ * motionZ > 0.001) return false;
        if (!player.onGround) return false;

        IGuiState guiState = GuiStateCapability.get(player);
        if (guiState == null || guiState.isGuiOpen()) return false;
        return !player.isHandActive() && !player.isPlayerSleeping();
    }

    private void performAutoAttack(EntityPlayer player) {
        Vec3d eyePos = player.getPositionEyes(1.0F);
        Vec3d lookVec = player.getLook(1.0F);
        Vec3d endPos = eyePos.add(lookVec.scale(ATTACK_RANGE));

        AxisAlignedBB searchBox = player.getEntityBoundingBox().grow(ATTACK_RANGE);
        java.util.List<Entity> entities = player.world.getEntitiesWithinAABBExcludingEntity(player, searchBox);
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
            player.resetCooldown();
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