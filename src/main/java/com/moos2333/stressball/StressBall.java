package com.moos2333.stressball;

import com.mojang.logging.LogUtils;
import com.moos2333.stressball.config.ConfigHandler;
import com.moos2333.stressball.items.PressureBallItem;
import com.moos2333.stressball.items.StressBallItem;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.util.UUID;

@Mod(StressBall.MODID)
public class StressBall {
    public static final String MODID = "stressball";
    public static final Logger LOGGER = LogUtils.getLogger();

    public StressBall(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        ModItems.register(modEventBus);
        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> ConfigHandler.load(FMLPaths.CONFIGDIR.get().toFile()));
    }

    @Mod.EventBusSubscriber(modid = MODID)
    public static class EventHandler {
        @SubscribeEvent
        public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
            UUID uuid = event.getEntity().getUUID();
            StressBallItem.cleanup(uuid);
            PressureBallItem.cleanup(uuid);
        }
    }
}