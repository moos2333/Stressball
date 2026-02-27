package com.npstra.stressball.capability;

import com.npstra.stressball.StressBall;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class CapabilityHandler {
    public static final ResourceLocation GUI_STATE_CAP = new ResourceLocation(StressBall.MODID, "gui_state");

    @SubscribeEvent
    public void attachCapability(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof EntityPlayer) {
            event.addCapability(GUI_STATE_CAP, new GuiStateCapability.Provider());
        }
    }
}