package org.AtomLink.radlink;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public class CustomBlock extends Block {
    public CustomBlock(String name, float hardness, float resistance) {
        super(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(hardness, resistance));
    }
}
