package com.npstra.stressball.network;

import com.npstra.stressball.capability.GuiStateCapability;
import com.npstra.stressball.capability.IGuiState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class GuiStateHandler implements IMessageHandler<GuiStateMessage, IMessage> {
    @Override
    public IMessage onMessage(GuiStateMessage message, MessageContext ctx) {
        EntityPlayerMP player = ctx.getServerHandler().player;
        player.getServer().addScheduledTask(() -> {
            IGuiState guiState = GuiStateCapability.get(player);
            if (guiState != null) {
                guiState.setGuiOpen(message.guiOpen);
            }
        });
        return null;
    }
}