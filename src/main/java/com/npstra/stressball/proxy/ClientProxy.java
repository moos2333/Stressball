package com.npstra.stressball.proxy;

import com.npstra.stressball.items.PressureBallItem;
import com.npstra.stressball.items.StressBallItem;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

public class ClientProxy extends CommonProxy {
    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
    }

    private void registerItemModel(Item item, int meta, String variant) {
        if (item != null) {
            ModelLoader.setCustomModelResourceLocation(item, meta,
                    new ModelResourceLocation(item.getRegistryName(), variant));
        }
    }
}