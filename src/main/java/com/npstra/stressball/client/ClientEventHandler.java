package com.npstra.stressball.client;

import com.npstra.stressball.StressBall;
import com.npstra.stressball.items.PressureBallItem;
import com.npstra.stressball.items.StressBallItem;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(modid = StressBall.MODID, value = Side.CLIENT)
public class ClientEventHandler {
    @SubscribeEvent
    public static void onModelRegistry(ModelRegistryEvent event) {
        ModelLoader.setCustomModelResourceLocation(StressBallItem.STRESS_BALL, 0,
                new ModelResourceLocation(StressBall.MODID + ":stressball", "inventory"));
        ModelLoader.setCustomModelResourceLocation(PressureBallItem.PRESSURE_BALL, 0,
                new ModelResourceLocation(StressBall.MODID + ":pressure_ball", "inventory"));
    }
}