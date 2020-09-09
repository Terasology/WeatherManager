// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.weatherManager.clouds;

import org.terasology.engine.registry.CoreRegistry;
import org.terasology.engine.world.block.Block;
import org.terasology.engine.world.block.BlockManager;
import org.terasology.engine.world.chunks.CoreChunk;
import org.terasology.engine.world.generation.Region;
import org.terasology.engine.world.generation.WorldRasterizerPlugin;
import org.terasology.engine.world.generator.plugin.RegisterPlugin;

/**
 * Rasterizes {@link CloudFacet} data onto a given chunk
 */
@RegisterPlugin
public class CloudRasterizer implements WorldRasterizerPlugin {

    // TODO: As of 2016-05-31 this fails to result in anything but null. Other places work. Related to this being 
    //  used in world gen maybe?
    //@In // Commented out in favor of the CoreRegistry usage below, which works fine
    BlockManager blockManager;

    private Block cloudBlock;

    @Override
    public void initialize() {
        // TODO: Remove / harden initialization when somebody has time to clean this up
        blockManager = CoreRegistry.get(BlockManager.class);
        cloudBlock = blockManager.getBlock("WeatherManager:Cloud");
    }

    @Override
    public void generateChunk(CoreChunk chunk, Region chunkRegion) {

        CloudFacet facet = chunkRegion.getFacet(CloudFacet.class);

        if (facet == null) {
            return;
        }

        int relHeight = facet.getHeight() - chunk.getChunkWorldOffsetY();

        if (relHeight >= 0 && relHeight < chunk.getChunkSizeY()) {
            for (int z = 0; z < chunk.getChunkSizeZ(); z++) {
                for (int x = 0; x < chunk.getChunkSizeX(); x++) {
                    boolean isClouded = facet.get(x, z);
                    Block oldBlock = chunk.getBlock(x, relHeight, z);
                    Block block = getBlock(isClouded, oldBlock);
                    if (!block.equals(oldBlock)) {
                        chunk.setBlock(x, relHeight, z, cloudBlock);
                    }
                }
            }
        }
    }

    /**
     * @param isClouded true if it should be a cloud block
     * @param oldBlock the current block
     * @return the new block - possibly still the one one, never <code>null</code>.
     */
    public Block getBlock(boolean isClouded, Block oldBlock) {

        if (isClouded && oldBlock.equals(blockManager.getBlock(BlockManager.AIR_ID))) {
            return cloudBlock;
        }

        if (!isClouded && oldBlock.equals(cloudBlock)) {
            return blockManager.getBlock(BlockManager.AIR_ID);
        }

        return oldBlock;
    }
}
