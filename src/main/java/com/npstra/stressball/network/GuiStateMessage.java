package com.npstra.stressball.network;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

public class GuiStateMessage implements IMessage {
    public boolean guiOpen;
    public GuiStateMessage() {}
    public GuiStateMessage(boolean guiOpen) { this.guiOpen = guiOpen; }
    @Override public void fromBytes(ByteBuf buf) { guiOpen = buf.readBoolean(); }
    @Override public void toBytes(ByteBuf buf) { buf.writeBoolean(guiOpen); }
}