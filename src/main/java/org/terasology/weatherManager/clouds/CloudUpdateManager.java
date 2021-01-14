/*
 * Copyright 2015 MovingBlocks
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

import org.joml.Vector3i;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.event.ReceiveEvent;
import org.terasology.entitySystem.systems.BaseComponentSystem;
import org.terasology.entitySystem.systems.RegisterMode;
import org.terasology.entitySystem.systems.RegisterSystem;
import org.terasology.registry.In;
import org.terasology.registry.Share;
import org.terasology.world.WorldProvider;
import org.terasology.world.block.Block;
import org.terasology.world.block.BlockRegion;
import org.terasology.world.time.WorldTimeEvent;

@RegisterSystem(RegisterMode.AUTHORITY)
@Share(CloudUpdateManager.class)
public class CloudUpdateManager extends BaseComponentSystem {

    private static final Logger logger = LoggerFactory.getLogger(CloudUpdateManager.class);

    private final int height = 127;

    @In
    private WorldProvider worldProvider;

    private CloudProvider cloudProvider = new CloudProvider();
    private CloudRasterizer cloudRasterizer = new CloudRasterizer();

    private float anim;

    @Override
    public void preBegin() {
        String seed = worldProvider.getSeed();
        cloudProvider.setSeed(seed.hashCode());
        cloudRasterizer.initialize();
    }

    @ReceiveEvent
    public void onTimeEvent(WorldTimeEvent event, EntityRef worldEntity) {

        logger.debug("Cloud layer update started...");

        int cnt = 0;
        for (BlockRegion reg : worldProvider.getRelevantRegions()) {
            if (reg.minY() <= height && height <= reg.maxY()) {
                for (int z = reg.minZ(); z <= reg.maxZ(); z++) {
                    for (int x = reg.minX(); x <= reg.maxX(); x++) {
                        boolean isClouded = cloudProvider.isClouded(x, z, anim);
                        Block oldBlock = worldProvider.getBlock(x, height, z);
                        Block block = cloudRasterizer.getBlock(isClouded, oldBlock);
                        if (!block.equals(oldBlock)) {
                            worldProvider.setBlock(new Vector3i(x, height, z), block);
                        }
                    }
                }
                cnt++;
            }
        }

        anim += 0.01;

        logger.debug("Cloud layer updated - {} chunks", cnt);
    }

    /**
     * @return current animation frame (cloud noise)
     */
    public float getAnimFrame() {
        return anim;
    }

    /**
     * @return the height of the cloud layer
     */
    public int getCloudHeight() {
        return height;
    }
}
