package com.moos2333.stressball.capability;

import net.minecraft.core.Direction;
import net.minecraft.nbt.ByteTag;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GuiStateCapability {

    public static final Capability<IGuiState> GUI_STATE =
            CapabilityManager.get(new CapabilityToken<>() {});

    public static class Implementation implements IGuiState {
        private boolean guiOpen = false;
        @Override public boolean isGuiOpen() { return guiOpen; }
        @Override public void setGuiOpen(boolean open) { guiOpen = open; }
    }

    public static class Provider implements ICapabilitySerializable<ByteTag> {
        private final IGuiState instance = new Implementation();
        private final LazyOptional<IGuiState> optional = LazyOptional.of(() -> instance);

        @Override
        public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
            return GUI_STATE.orEmpty(cap, optional);
        }

        @Override
        public ByteTag serializeNBT() {
            return ByteTag.valueOf(instance.isGuiOpen());
        }

        @Override
        public void deserializeNBT(ByteTag nbt) {
            instance.setGuiOpen(nbt.getAsByte() == 1);
        }
    }

    public static IGuiState get(Player player) {
        return player.getCapability(GUI_STATE).orElse(null);
    }
}