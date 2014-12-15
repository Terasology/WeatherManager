/*
 * Copyright 2014 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.terasology.weatherManager.clouds;

import org.terasology.registry.CoreRegistry;
import org.terasology.world.block.Block;
import org.terasology.world.block.BlockManager;
import org.terasology.world.chunks.CoreChunk;
import org.terasology.world.generation.Region;
import org.terasology.world.generation.WorldRasterizerPlugin;
import org.terasology.world.generator.plugin.RegisterPlugin;

/**
 * Rasterizes {@link CloudFacet} data onto a given chunk
 * @author Martin Steiger
 */
@RegisterPlugin
public class CloudRasterizer implements WorldRasterizerPlugin {

    private Block cloudBlock;

    @Override
    public void initialize() {
        BlockManager blockManager = CoreRegistry.get(BlockManager.class);
        cloudBlock = blockManager.getBlock("WeatherMan:Cloud");
    }

    @Override
    public void generateChunk(CoreChunk chunk, Region chunkRegion) {

        CloudFacet facet = chunkRegion.getFacet(CloudFacet.class);

        int relHeight = facet.getHeight() - chunk.getChunkWorldOffsetY();

        if (relHeight >= 0 && relHeight < chunk.getChunkSizeY()) {
            for (int z = 0; z < chunk.getChunkSizeZ(); z++) {
                for (int x = 0; x < chunk.getChunkSizeX(); x++) {
                    if (facet.get(x, z)) {
                        chunk.setBlock(x, relHeight, z, cloudBlock);
                    }
                }
            }
        }
    }
}
