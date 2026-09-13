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

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

@Mod(modid = StressBall.MODID, name = StressBall.NAME, version = StressBall.VERSION, dependencies = "required-after:baubles")
public class StressBall {
    public static final String MODID = Tags.MOD_ID;
    public static final String NAME = Tags.MOD_NAME;
    public static final String VERSION = Tags.VERSION;

    public static SimpleNetworkWrapper NETWORK;
    public static final Map<UUID, Integer> LAST_ATTACK_TICK = new WeakHashMap<>();

    @SidedProxy(clientSide = "com.npstra.stressball.proxy.ClientProxy", serverSide = "com.npstra.stressball.proxy.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        ConfigHandler.load(event.getModConfigurationDirectory());

        NETWORK = NetworkRegistry.INSTANCE.newSimpleChannel(MODID);
        NETWORK.registerMessage(GuiStateHandler.class, GuiStateMessage.class, 0, Side.SERVER);

        CapabilityManager.INSTANCE.register(IGuiState.class, new GuiStateCapability.Storage(), GuiStateCapability.Implementation::new);
        MinecraftForge.EVENT_BUS.register(new CapabilityHandler());

        if (event.getSide().isClient()) {
            MinecraftForge.EVENT_BUS.register(new ClientGuiHandler());
        }

        proxy.preInit(event);
    }

    @Mod.EventBusSubscriber(modid = StressBall.MODID)
    public static class EventHandler {
        @SubscribeEvent
        public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
            LAST_ATTACK_TICK.remove(event.player.getUniqueID());
        }
    }
}