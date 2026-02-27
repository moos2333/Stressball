package com.npstra.stressball.capability;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagByte;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import javax.annotation.Nullable;

public class GuiStateCapability {
    @CapabilityInject(IGuiState.class)
    public static final Capability<IGuiState> GUI_STATE = null;

    public static class Implementation implements IGuiState {
        private boolean guiOpen = false;
        @Override public boolean isGuiOpen() { return guiOpen; }
        @Override public void setGuiOpen(boolean open) { guiOpen = open; }
    }

    public static class Storage implements Capability.IStorage<IGuiState> {
        @Override public NBTBase writeNBT(Capability<IGuiState> capability, IGuiState instance, EnumFacing side) {
            return new NBTTagByte((byte) (instance.isGuiOpen() ? 1 : 0));
        }
        @Override public void readNBT(Capability<IGuiState> capability, IGuiState instance, EnumFacing side, NBTBase nbt) {
            instance.setGuiOpen(((NBTTagByte) nbt).getByte() == 1);
        }
    }

    public static class Provider implements ICapabilitySerializable<NBTTagByte> {
        private final IGuiState instance = new Implementation();
        @Override public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
            return capability == GUI_STATE;
        }
        @Override public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
            return capability == GUI_STATE ? GUI_STATE.cast(instance) : null;
        }
        @Override public NBTTagByte serializeNBT() {
            return (NBTTagByte) GUI_STATE.getStorage().writeNBT(GUI_STATE, instance, null);
        }
        @Override public void deserializeNBT(NBTTagByte nbt) {
            GUI_STATE.getStorage().readNBT(GUI_STATE, instance, null, nbt);
        }
    }

    public static IGuiState get(EntityPlayer player) {
        if (player == null || GUI_STATE == null) return null;
        return player.getCapability(GUI_STATE, null);
    }
}