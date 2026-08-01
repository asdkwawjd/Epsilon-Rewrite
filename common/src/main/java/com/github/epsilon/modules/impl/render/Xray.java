package com.github.epsilon.modules.impl.render;

import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.SettingGroup;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.IntSetting;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Set;

public class Xray extends Module {

    public static final Xray INSTANCE = new Xray();

    private Xray() {
        super("Xray", Category.RENDER);
    }

    private enum Mode {
        SIMPLE,
        FULL
    }

    private final SettingGroup sgGeneral = settingGroup("General");

    private final IntSetting opacity = intSetting("BG Opacity", 0, 0, 255, 1, _ -> rreload()).group(sgGeneral);

    private final BoolSetting coalOre = boolSetting("Coal Ore", true, _ -> rreload()).group(sgGeneral);
    private final BoolSetting copperOre = boolSetting("Copper Ore", true, _ -> rreload()).group(sgGeneral);
    private final BoolSetting diamondOre = boolSetting("Diamond Ore", true, _ -> rreload()).group(sgGeneral);
    private final BoolSetting emeraldOre = boolSetting("Emerald Ore", true, _ -> rreload()).group(sgGeneral);
    private final BoolSetting goldOre = boolSetting("Gold Ore", true, _ -> rreload()).group(sgGeneral);
    private final BoolSetting ironOre = boolSetting("Iron Ore", true, _ -> rreload()).group(sgGeneral);
    private final BoolSetting lapisOre = boolSetting("Lapis Ore", true, _ -> rreload()).group(sgGeneral);
    private final BoolSetting redstoneOre = boolSetting("Redstone Ore", true, _ -> rreload()).group(sgGeneral);
    private final BoolSetting ancientDebris = boolSetting("Ancient Debris", true, _ -> rreload()).group(sgGeneral);
    private final BoolSetting netherGold = boolSetting("Nether Gold Ore", true, _ -> rreload()).group(sgGeneral);
    private final BoolSetting netherQuartz = boolSetting("Nether Quartz Ore", true, _ -> rreload()).group(sgGeneral);

    private final BoolSetting chests = boolSetting("Chests", true, _ -> rreload()).group(sgGeneral);
    private final BoolSetting spawners = boolSetting("Spawners", true, _ -> rreload()).group(sgGeneral);
    private final BoolSetting lava = boolSetting("Lava", true, _ -> rreload()).group(sgGeneral);
    private final BoolSetting water = boolSetting("Water", false, _ -> rreload()).group(sgGeneral);
    private final BoolSetting portals = boolSetting("Portals", false, _ -> rreload()).group(sgGeneral);
    private final BoolSetting mineralBlocks = boolSetting("Mineral Blocks", false, _ -> rreload()).group(sgGeneral);

    private final Set<Block> xrayBlocks = new HashSet<>();

    @Override
    protected void onEnable() {
        rebuildBlockSet();
        mc.levelRenderer.allChanged();
    }

    @Override
    protected void onDisable() {
        xrayBlocks.clear();
        mc.levelRenderer.allChanged();
    }

    private void rreload() {
        if (!isEnabled()) return;
        rebuildBlockSet();
        mc.levelRenderer.allChanged();
    }

    private void rebuildBlockSet() {
        xrayBlocks.clear();

        if (coalOre.getValue()) {
            xrayBlocks.add(Blocks.COAL_ORE);
            xrayBlocks.add(Blocks.DEEPSLATE_COAL_ORE);
        }
        if (copperOre.getValue()) {
            xrayBlocks.add(Blocks.COPPER_ORE);
            xrayBlocks.add(Blocks.DEEPSLATE_COPPER_ORE);
        }
        if (diamondOre.getValue()) {
            xrayBlocks.add(Blocks.DIAMOND_ORE);
            xrayBlocks.add(Blocks.DEEPSLATE_DIAMOND_ORE);
        }
        if (emeraldOre.getValue()) {
            xrayBlocks.add(Blocks.EMERALD_ORE);
            xrayBlocks.add(Blocks.DEEPSLATE_EMERALD_ORE);
        }
        if (goldOre.getValue()) {
            xrayBlocks.add(Blocks.GOLD_ORE);
            xrayBlocks.add(Blocks.DEEPSLATE_GOLD_ORE);
        }
        if (ironOre.getValue()) {
            xrayBlocks.add(Blocks.IRON_ORE);
            xrayBlocks.add(Blocks.DEEPSLATE_IRON_ORE);
        }
        if (lapisOre.getValue()) {
            xrayBlocks.add(Blocks.LAPIS_ORE);
            xrayBlocks.add(Blocks.DEEPSLATE_LAPIS_ORE);
        }
        if (redstoneOre.getValue()) {
            xrayBlocks.add(Blocks.REDSTONE_ORE);
            xrayBlocks.add(Blocks.DEEPSLATE_REDSTONE_ORE);
        }
        if (ancientDebris.getValue()) {
            xrayBlocks.add(Blocks.ANCIENT_DEBRIS);
        }
        if (netherGold.getValue()) {
            xrayBlocks.add(Blocks.NETHER_GOLD_ORE);
        }
        if (netherQuartz.getValue()) {
            xrayBlocks.add(Blocks.NETHER_QUARTZ_ORE);
        }

        if (chests.getValue()) {
            xrayBlocks.add(Blocks.CHEST);
            xrayBlocks.add(Blocks.TRAPPED_CHEST);
            xrayBlocks.add(Blocks.ENDER_CHEST);
            xrayBlocks.add(Blocks.BARREL);
            xrayBlocks.add(Blocks.SHULKER_BOX);
        }

        if (spawners.getValue()) {
            xrayBlocks.add(Blocks.SPAWNER);
        }

        if (lava.getValue()) {
            xrayBlocks.add(Blocks.LAVA);
        }

        if (water.getValue()) {
            xrayBlocks.add(Blocks.WATER);
        }

        if (portals.getValue()) {
            xrayBlocks.add(Blocks.NETHER_PORTAL);
            xrayBlocks.add(Blocks.END_PORTAL);
            xrayBlocks.add(Blocks.END_PORTAL_FRAME);
        }

        if (mineralBlocks.getValue()) {
            xrayBlocks.add(Blocks.DIAMOND_BLOCK);
            xrayBlocks.add(Blocks.EMERALD_BLOCK);
            xrayBlocks.add(Blocks.GOLD_BLOCK);
            xrayBlocks.add(Blocks.IRON_BLOCK);
            xrayBlocks.add(Blocks.COPPER_BLOCK);
            xrayBlocks.add(Blocks.NETHERITE_BLOCK);
            xrayBlocks.add(Blocks.RAW_GOLD_BLOCK);
            xrayBlocks.add(Blocks.RAW_IRON_BLOCK);
            xrayBlocks.add(Blocks.RAW_COPPER_BLOCK);
        }
    }

    public boolean isXrayBlock(BlockState state) {
        return xrayBlocks.contains(state.getBlock());
    }

    public int getBackgroundOpacity() {
        return opacity.getValue();
    }
}
