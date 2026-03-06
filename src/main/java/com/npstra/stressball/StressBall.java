package com.npstra.stressball;

import com.npstra.stressball.capability.CapabilityHandler;
import com.npstra.stressball.capability.GuiStateCapability;
import com.npstra.stressball.capability.IGuiState;
import com.npstra.stressball.client.ClientGuiHandler;
import com.npstra.stressball.config.ConfigHandler;
import com.npstra.stressball.network.GuiStateHandler;
import com.npstra.stressball.network.GuiStateMessage;
import com.npstra.stressball.proxy.CommonProxy;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod(modid = StressBall.MODID, name = StressBall.NAME, version = StressBall.VERSION, dependencies = "required-after:baubles")
public class StressBall {
    public static final String MODID = "stressball";
    public static final String NAME = "Stress Ball";
    public static final String VERSION = "0.0.2";

    public static SimpleNetworkWrapper NETWORK;
    public static final Map<UUID, Integer> LAST_ATTACK_TICK = new HashMap<>();
    public static final Map<UUID, Integer> STAND_STILL_TIMER = new HashMap<>();

    @SidedProxy(clientSide = "com.npstra.stressball.proxy.ClientProxy", serverSide = "com.npstra.stressball.proxy.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        NETWORK = NetworkRegistry.INSTANCE.newSimpleChannel(MODID);
        NETWORK.registerMessage(GuiStateHandler.class, GuiStateMessage.class, 0, Side.SERVER);

        if (event.getSide().isClient()) {
            MinecraftForge.EVENT_BUS.register(new ClientGuiHandler());
        }

        CapabilityManager.INSTANCE.register(IGuiState.class, new GuiStateCapability.Storage(), GuiStateCapability.Implementation::new);
        MinecraftForge.EVENT_BUS.register(new CapabilityHandler());
        MinecraftForge.EVENT_BUS.register(EventHandler.class);

        ConfigHandler.load(event.getModConfigurationDirectory());

        proxy.preInit(event);
    }

    @Mod.EventBusSubscriber
    public static class EventHandler {
        @SubscribeEvent
        public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
            UUID uuid = event.player.getUniqueID();
            LAST_ATTACK_TICK.remove(uuid);
            STAND_STILL_TIMER.remove(uuid);
        }
    }
}