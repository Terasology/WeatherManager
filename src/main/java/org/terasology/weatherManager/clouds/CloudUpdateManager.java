// Copyright 2021 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.weatherManager.clouds;

import org.joml.Vector3i;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.engine.entitySystem.entity.EntityRef;
import org.terasology.engine.entitySystem.systems.BaseComponentSystem;
import org.terasology.engine.entitySystem.systems.RegisterMode;
import org.terasology.engine.entitySystem.systems.RegisterSystem;
import org.terasology.engine.registry.In;
import org.terasology.engine.registry.Share;
import org.terasology.engine.world.WorldProvider;
import org.terasology.engine.world.block.Block;
import org.terasology.engine.world.block.BlockRegionc;
import org.terasology.engine.world.time.WorldTimeEvent;
import org.terasology.gestalt.entitysystem.event.ReceiveEvent;

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
        for (BlockRegionc reg : worldProvider.getRelevantRegions()) {
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
