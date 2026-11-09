package com.moos2333.stressball;

import com.moos2333.stressball.items.PressureBallItem;
import com.moos2333.stressball.items.StressBallItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, StressBall.MODID);

    public static final RegistryObject<Item> STRESS_BALL = ITEMS.register("stressball",
            () -> new StressBallItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> PRESSURE_BALL = ITEMS.register("pressure_ball",
            () -> new PressureBallItem(new Item.Properties().stacksTo(1)));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
        eventBus.addListener(ModItems::addCreative);
    }

    private static void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(STRESS_BALL);
            event.accept(PRESSURE_BALL);
        }
    }
}