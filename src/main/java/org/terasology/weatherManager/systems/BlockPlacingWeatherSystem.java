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
import org.terasology.world.chunks.ChunkConstants;

import static org.terasology.weatherManager.systems.WeatherManagerSystem.EVAPORATE_WATER;
import static org.terasology.weatherManager.systems.WeatherManagerSystem.MELT_SNOW;
import static org.terasology.weatherManager.systems.WeatherManagerSystem.PLACE_SNOW;
import static org.terasology.weatherManager.systems.WeatherManagerSystem.PLACE_WATER;

@RegisterSystem(RegisterMode.AUTHORITY)
public class BlockPlacingWeatherSystem extends BaseComponentSystem {
    private static final int SNOW_BLOCK_RANGE = 40;
    private Block air;
    private Block snow;
    private Block water;
    private FastRandom rand = new FastRandom();

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
        water = blockManager.getBlock("CoreAssets:water");
        networkSystem = context.get(NetworkSystem.class);
    }

    /**
     * Places snow blocks on the ground when it is snowing.
     * The event with the id PLACE_SNOW will only be created when it is snowing
     * and the other events also correspond with the appropriate weather condition.
     * @param event The event that means it is time to place snow
     * @param worldEntity The entity that sent the event (assumed to be the player)
     */
    @ReceiveEvent
    public void onPlaceEvent(PeriodicActionTriggeredEvent event, EntityRef worldEntity) {
        if (event.getActionId().equals(PLACE_SNOW)) {
            for(Client currentPlayer : networkSystem.getPlayers()) {
                LocationComponent locComp = currentPlayer.getEntity().getComponent(LocationComponent.class);
                Vector3i playerPos = new Vector3i(locComp.getWorldPosition());

                placeSnow(playerPos);
            }
        } else if (event.getActionId().equals(MELT_SNOW)) {
            for(Client currentPlayer : networkSystem.getPlayers()) {
                LocationComponent locComp = currentPlayer.getEntity().getComponent(LocationComponent.class);
                Vector3i playerPos = new Vector3i(locComp.getWorldPosition());

                meltSnow(playerPos);
            }
        }  else if (event.getActionId().equals(PLACE_WATER)) {
            for(Client currentPlayer : networkSystem.getPlayers()) {
                LocationComponent locComp = currentPlayer.getEntity().getComponent(LocationComponent.class);
                Vector3i playerPos = new Vector3i(locComp.getWorldPosition());

                placeWater(playerPos);
            }
        }  else if (event.getActionId().equals(EVAPORATE_WATER)) {
            for(Client currentPlayer : networkSystem.getPlayers()) {
                LocationComponent locComp = currentPlayer.getEntity().getComponent(LocationComponent.class);
                Vector3i playerPos = new Vector3i(locComp.getWorldPosition());

                evaporateWater(playerPos);
            }
        }
    }

    /**
     * Finds a spot to place a block.
     * @param toCheck the block type that we should be looking for.
     * @param x the x position that the blocks
     * @return a vector with the height where the block should be placed, null if no block should be placed.
     */
    private Vector3i findSpot(Block toCheck, int x, int z, int initialY) {
        int currentY = initialY + SNOW_BLOCK_RANGE;
        int iter = 0;
        while (iter < SNOW_BLOCK_RANGE * 2 && worldProvider.getBlock(x, currentY, z).equals(air)) {
            iter++;
            currentY--;
        }
        while (iter < SNOW_BLOCK_RANGE * 2 && !worldProvider.getBlock(x, currentY, z).equals(air)) {
            iter++;
            currentY++;
        }
        if (iter >= SNOW_BLOCK_RANGE * 2) {
            return null;
        }

        if (worldProvider.getSunlight(x, currentY, z) != ChunkConstants.MAX_SUNLIGHT) {
            // The block isn't actually exposed to the weather.
            return null;
        }
        Block ground = worldProvider.getBlock(x, currentY-1, z);
        if (ground.equals(toCheck)) {
            return new Vector3i(x, currentY-1, z);
        } else if (toCheck.equals(air) && !ground.isPenetrable() && ground.isAttachmentAllowed()) {
            return new Vector3i(x, currentY, z);
        } else {
            return null;
        }
    }

    private void placeSnow(Vector3i playerPos) {
        int x = getValueToPlaceBlock(playerPos.x);
        int z = getValueToPlaceBlock(playerPos.z);
        Vector3i spotToPlace = findSpot(air, x, z, playerPos.y);
        if (spotToPlace != null) {
            worldProvider.setBlock(spotToPlace, snow);
        }
    }

    private void meltSnow(Vector3i playerPos) {
        int x = getValueToPlaceBlock(playerPos.x);
        int z = getValueToPlaceBlock(playerPos.z);
        Vector3i spotToPlace = findSpot(snow, x, z, playerPos.y);
        if (spotToPlace != null) {
            worldProvider.setBlock(spotToPlace, water);
        }
    }

    private void placeWater(Vector3i playerPos) {
        int x = getValueToPlaceBlock(playerPos.x);
        int z = getValueToPlaceBlock(playerPos.z);
        Vector3i spotToPlace = findSpot(air, x, z, playerPos.y);
        if (spotToPlace != null) {
            worldProvider.setBlock(spotToPlace, water);
        }
    }

    private void evaporateWater(Vector3i playerPos) {
        int x = getValueToPlaceBlock(playerPos.x);
        int z = getValueToPlaceBlock(playerPos.z);
        Vector3i spotToPlace = findSpot(water, x, z, playerPos.y);
        if (spotToPlace != null) {
            worldProvider.setBlock(spotToPlace, air);
        }
    }

    private int getValueToPlaceBlock(int initial) {
        return initial + rand.nextInt(SNOW_BLOCK_RANGE * 2) - SNOW_BLOCK_RANGE;
    }
}
