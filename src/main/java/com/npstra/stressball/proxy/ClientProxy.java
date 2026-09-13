package com.npstra.stressball.proxy;

import com.npstra.stressball.StressBall;
import com.npstra.stressball.items.PressureBallItem;
import com.npstra.stressball.items.StressBallItem;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(modid = StressBall.MODID, value = Side.CLIENT)
public class ClientProxy extends CommonProxy {
    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
    }

    @SubscribeEvent
    public static void onModelRegistry(ModelRegistryEvent event) {
        registerItemModel(StressBallItem.STRESS_BALL);
        registerItemModel(PressureBallItem.PRESSURE_BALL);
    }

    private static void registerItemModel(Item item) {
        if (item != null && item.getRegistryName() != null) {
            ModelLoader.setCustomModelResourceLocation(item, 0,
                    new ModelResourceLocation(item.getRegistryName(), "inventory"));
        }
    }
}