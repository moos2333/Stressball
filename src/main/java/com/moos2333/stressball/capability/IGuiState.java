package com.moos2333.stressball.capability;

import net.minecraftforge.common.capabilities.AutoRegisterCapability;

@AutoRegisterCapability
public interface IGuiState {
    boolean isGuiOpen();
    void setGuiOpen(boolean open);
}