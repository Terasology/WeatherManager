/*
 * Copyright 2018 MovingBlocks
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

package org.terasology.weatherManager.systems;

import org.terasology.context.Context;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.event.ReceiveEvent;
import org.terasology.entitySystem.systems.BaseComponentSystem;
import org.terasology.entitySystem.systems.RegisterMode;
import org.terasology.entitySystem.systems.RegisterSystem;
import org.terasology.logic.delay.PeriodicActionTriggeredEvent;
import org.terasology.logic.location.LocationComponent;
import org.terasology.math.geom.Vector3i;
import org.terasology.network.Client;
import org.terasology.network.NetworkSystem;
import org.terasology.registry.In;
import org.terasology.utilities.random.FastRandom;
import org.terasology.world.WorldProvider;
import org.terasology.world.block.Block;
import org.terasology.world.block.BlockManager;

@RegisterSystem(RegisterMode.AUTHORITY)
public class BlockPlacingWeatherSystem extends BaseComponentSystem {
    private static final int SNOW_BLOCK_RANGE = 40;
    private Block air;
    private Block snow;
    private Block water;

    @In
    private WorldProvider worldProvider;

    @In
    private BlockManager blockManager;

    @In
    private Context context;

    private NetworkSystem networkSystem;

    @Override
    public void postBegin() {
        air = blockManager.getBlock("engine:air");
        snow = blockManager.getBlock("WeatherManager:snow");
        water = blockManager.getBlock("CoreAssets:water:engine:eighthBlock");
        networkSystem = context.get(NetworkSystem.class);
    }

    /**
     * Places snow blocks on the ground when it is snowing.
     * The event with the id "placeSnow" will only be created when it is snowing.
     * @param event The event that means it is time to place snow
     * @param worldEntity The entity that sent the event (assumed to be the player)
     */
    @ReceiveEvent
    public void onPlaceEvent(PeriodicActionTriggeredEvent event, EntityRef worldEntity) {
        if (event.getActionId().equals("placeSnow")) {
            for(Client currentPlayer : networkSystem.getPlayers()) {
                LocationComponent locComp = currentPlayer.getEntity().getComponent(LocationComponent.class);
                Vector3i playerPos = new Vector3i(locComp.getWorldPosition());

                placeSnow(playerPos);
            }
        } else if (event.getActionId().equals("meltSnow")) {
            for(Client currentPlayer : networkSystem.getPlayers()) {
                LocationComponent locComp = currentPlayer.getEntity().getComponent(LocationComponent.class);
                Vector3i playerPos = new Vector3i(locComp.getWorldPosition());

                meltSnow(playerPos);
            }
        }  else if (event.getActionId().equals("placeWater")) {
            for(Client currentPlayer : networkSystem.getPlayers()) {
                LocationComponent locComp = currentPlayer.getEntity().getComponent(LocationComponent.class);
                Vector3i playerPos = new Vector3i(locComp.getWorldPosition());

                placeWater(playerPos);
            }
        }  else if (event.getActionId().equals("evaporateWater")) {
            for(Client currentPlayer : networkSystem.getPlayers()) {
                LocationComponent locComp = currentPlayer.getEntity().getComponent(LocationComponent.class);
                Vector3i playerPos = new Vector3i(locComp.getWorldPosition());

                evaporateWater(playerPos);
            }
        }
    }

    /**
     * Finds a spot to
     * @param initialPos The position that the blocks should be centered around.
     * @param toCheck The block type that we should be looking for.
     * @return A vector with the height where the block should be placed.
     * If no block should be placed, the x and z of the vector will differ from initialPos
     */
    private Vector3i findSpot(Vector3i initialPos, Block toCheck) {
        FastRandom rand = new FastRandom();
        int x = (int) initialPos.x + rand.nextInt(SNOW_BLOCK_RANGE * 2) - SNOW_BLOCK_RANGE;
        int z = (int) initialPos.z + rand.nextInt(SNOW_BLOCK_RANGE * 2) - SNOW_BLOCK_RANGE;
        int currentY = (int) initialPos.y + SNOW_BLOCK_RANGE;
        int iter = 0;
        boolean lastGround = false;
        while (iter < SNOW_BLOCK_RANGE * 2) {
            Block current = worldProvider.getBlock(x, currentY, z);
            if (current.equals(toCheck) && lastGround) {
                return new Vector3i(x, currentY, z);
            } else if (current.equals(air)) {
                currentY--;
                lastGround = false;
            } else if (current.isPenetrable() || !current.isAttachmentAllowed()) {
                return new Vector3i(x - 1, currentY, z - 1);
            } else if (!current.equals(snow)) {
                lastGround = true;
                currentY++;
            } else {
                return new Vector3i(x - 1, currentY, z - 1); //break out to avoid double-placing snow
            }
            iter++;
        }
        return new Vector3i(x - 1, currentY, z - 1);
    }

    private void placeSnow(Vector3i playerPos) {
        Vector3i spotToPlace = findSpot(playerPos, air);
        if (spotToPlace.x == playerPos.x && spotToPlace.y == playerPos.y) {
            worldProvider.setBlock(spotToPlace, snow);
        }
    }

    private void meltSnow(Vector3i playerPos) {
        Vector3i spotToPlace = findSpot(playerPos, snow);
        if (spotToPlace.x == playerPos.x && spotToPlace.y == playerPos.y) {
            worldProvider.setBlock(spotToPlace, water);
        }
    }

    private void placeWater(Vector3i playerPos) {
        Vector3i spotToPlace = findSpot(playerPos, air);
        if (spotToPlace.x == playerPos.x && spotToPlace.y == playerPos.y) {
            worldProvider.setBlock(spotToPlace, water);
        }
    }

    private void evaporateWater(Vector3i playerPos) {
        Vector3i spotToPlace = findSpot(playerPos, water);
        if (spotToPlace.x == playerPos.x && spotToPlace.y == playerPos.y) {
            worldProvider.setBlock(spotToPlace, air);
        }
    }
}
