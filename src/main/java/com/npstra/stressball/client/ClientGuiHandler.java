package com.npstra.stressball.client;

import com.npstra.stressball.StressBall;
import com.npstra.stressball.network.GuiStateMessage;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class ClientGuiHandler {
    private static boolean lastGuiState = false;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            boolean currentGuiOpen = Minecraft.getMinecraft().currentScreen != null;
            if (currentGuiOpen != lastGuiState) {
                lastGuiState = currentGuiOpen;
                StressBall.NETWORK.sendToServer(new GuiStateMessage(currentGuiOpen));
            }
        }
    }
}