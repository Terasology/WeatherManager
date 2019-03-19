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

import org.terasology.entitySystem.entity.EntityBuilder;
import org.terasology.entitySystem.entity.EntityManager;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.entity.lifecycleEvents.OnActivatedComponent;
import org.terasology.entitySystem.event.ReceiveEvent;
import org.terasology.entitySystem.systems.BaseComponentSystem;
import org.terasology.entitySystem.systems.RegisterSystem;
import org.terasology.logic.delay.PeriodicActionTriggeredEvent;
import org.terasology.logic.location.LocationComponent;
import org.terasology.logic.players.LocalPlayer;
import org.terasology.math.geom.Vector2f;
import org.terasology.math.geom.Vector3f;
import org.terasology.math.geom.Vector3i;
import org.terasology.particles.components.ParticleEmitterComponent;
import org.terasology.particles.components.generators.VelocityRangeGeneratorComponent;
import org.terasology.physics.events.MovedEvent;
import org.terasology.registry.In;
import org.terasology.weatherManager.events.StartWeatherEvent;
import org.terasology.weatherManager.weather.DownfallCondition;
import org.terasology.world.WorldProvider;
import org.terasology.world.block.Block;
import org.terasology.world.block.BlockManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

//TODO: destroy on contact with blocks (water)

@RegisterSystem
public class EmitWeatherParticleSystem extends BaseComponentSystem {

    private static final String IS_SUNNY = "sunny";
    private static final int SIZE_OF_PARTICLE_AREA = 24;
    private static final int BUFFER_AMOUNT = 5;
    private static final int CLOUD_HEIGHT = 127;
    private static final int SNOW_BLOCK_RANGE = 40;

    private String prefabName;

    private Vector3f center;

    private Map<Vector2f, EntityRef> particleSpawners;

    private Map<Vector2f, EntityBuilder> builders;

    private boolean particlesMade;

    @In
    private EntityManager entityManager;

    @In
    private LocalPlayer localPlayer;

    @In
    private WeatherManagerSystem weatherManagerSystem;

    @In
    private WorldProvider worldProvider;

    @In
    private BlockManager blockManager;

    private int minDownfall;
    private int maxDownfall;
    private Block air;
    private Block snow;

    @Override
    public void postBegin() {
        air = blockManager.getBlock("engine:air");
        snow = blockManager.getBlock("WeatherManager:snow");

        particlesMade = false;

        particleSpawners = new HashMap<>();
        builders = new HashMap<>();
        setPrefabName();
    }

    /**
     * Begins making the process of visual effects for weather.
     * @param event The StartWeatherEvent that was recieved.
     * @param worldEntity The entity that sent the event.
     */
    @ReceiveEvent
    public void onStartWeatherEvent(StartWeatherEvent event, EntityRef worldEntity) {

        if (localPlayer.getPosition() != null) {
            clearEmitters();
            setPrefabName();
        }
    }

    @ReceiveEvent
    public void onCharacterActivation(OnActivatedComponent event, EntityRef characterEntity) {
        if (!particlesMade) {
            setPrefabName();
        }
    }

     /**
     * Moves the visual effects with the player.
     * @param event The MovedEvent.
     * @param character The entity that sent the MoveEvent.
     */
    @ReceiveEvent
    public void onCharacterMoved(MovedEvent event, EntityRef character) {

        if (center != null) {
            Vector3f pos = round(new Vector3f(event.getPosition()));

            if (!pos.equals(center)) {

                Vector3f dif = new Vector3f(center).sub(pos);

                center = new Vector3f(pos);

                if (dif.x != 0) {
                    if (dif.x > 0) {
                        refreshParticles(true, false, true);
                    } else {
                        refreshParticles(true, false, false);
                    }
                }
                if (dif.z != 0) {
                    if (dif.z > 0) {
                        refreshParticles(false, true, true);
                    } else {
                        refreshParticles(false, true, false);
                    }
                }
                if (dif.y != 0) {
                    if (dif.y > 0) {
                        refreshParticles(false, false, false);
                    } else {
                        refreshParticles(false, true, false);
                    }
                }
            }
        }
    }

    /**
     * Rounds a vector.
     * @param original The original vector.
     * @return The original vector rounded to be whole numbers.
     */
    private Vector3f round(Vector3f original) {
        original.x = Math.round(original.x);
        original.y = Math.round(original.y);
        original.z = Math.round(original.z);

        return original;
    }

    /**
     * Refreshes the location of the particle emitting entities (to move if necessary).
     * @param x If the player moved on the x axis.
     * @param z If the player moved on the z axis.
     * @param positive If the player moved in a positive or negative direction.
     */
    private void refreshParticles(boolean x, boolean z, boolean positive) {

        if (!prefabName.equals(IS_SUNNY)) {
            ArrayList<Vector2f> remove = new ArrayList<>();

            for (Vector2f vect : particleSpawners.keySet()) {
                float distX = vect.distance(new Vector2f(center.x, vect.y));
                float distZ = vect.distance(new Vector2f(vect.x, center.z));
                if (distX > SIZE_OF_PARTICLE_AREA / 2 + BUFFER_AMOUNT || distZ > SIZE_OF_PARTICLE_AREA / 2 + BUFFER_AMOUNT) {
                    remove.add(vect);
                    if (builders.containsKey(vect)) {
                        particleSpawners.get(vect).destroy();
                        builders.remove(vect);
                    }
                } else {
                    Vector3f newPos = new Vector3f(vect.x, center.y + (float) SIZE_OF_PARTICLE_AREA / 3, vect.y);
                    LocationComponent loc = builders.get(vect).getComponent(LocationComponent.class);
                    if (!loc.getWorldPosition().equals(newPos)) {
                        builders.get(vect).getComponent(LocationComponent.class).setWorldPosition(newPos);
                    }
                }
            }

            for (Vector2f toRemove : remove) {
                particleSpawners.remove(toRemove);
            }

            float windXAbs = Math.abs(weatherManagerSystem.getCurrentWind().x * 10);
            float windYAbs = Math.abs(weatherManagerSystem.getCurrentWind().y * 10);
            Vector3f maxVelocity = new Vector3f(Math.min(1.5f, windXAbs), maxDownfall, Math.min(1.5f, windYAbs));
            if (weatherManagerSystem.getCurrentWind().x < 0) {
                maxVelocity.x *= -1;
            }
            if (weatherManagerSystem.getCurrentWind().y < 0) {
                maxVelocity.z *= -1;
            }
            Vector3f minVelocity = new Vector3f(maxVelocity.x, minDownfall, maxVelocity.z);

            for (int i = 0; i < BUFFER_AMOUNT; i++) {
                for (int j = -SIZE_OF_PARTICLE_AREA / 2; j < SIZE_OF_PARTICLE_AREA / 2; j++) {
                    Vector3f locEmitter = new Vector3f(center);

                    locEmitter.addY(Math.min(CLOUD_HEIGHT, (float) SIZE_OF_PARTICLE_AREA / 3));

                    if (x) {
                        if (positive) {
                            locEmitter.add(-Math.round((float) SIZE_OF_PARTICLE_AREA / 2) - i, 0, j);
                        } else {
                            locEmitter.add(Math.round((float) SIZE_OF_PARTICLE_AREA / 2) + i, 0, j);
                        }
                    } else if (z) {
                        if (positive) {
                            locEmitter.add(j, 0, -Math.round((float) SIZE_OF_PARTICLE_AREA / 2 - i));
                        } else {
                            locEmitter.add(j, 0, Math.round((float) SIZE_OF_PARTICLE_AREA / 2 + i));
                        }
                    }

                    if (!particleSpawners.containsKey(new Vector2f(locEmitter.x, locEmitter.z))) {
                        EntityBuilder builder = entityManager.newBuilder(prefabName);
                        builder.getComponent(LocationComponent.class).setWorldPosition(locEmitter);
                        builder.getComponent(VelocityRangeGeneratorComponent.class).minVelocity.set(minVelocity);
                        builder.getComponent(VelocityRangeGeneratorComponent.class).maxVelocity.set(maxVelocity);
                        builder.setPersistent(true);
                        EntityRef ref = builder.build();
                        particleSpawners.put(new Vector2f(locEmitter.x, locEmitter.z), ref);
                        builders.put(new Vector2f(locEmitter.x, locEmitter.z), builder);
                    }
                }
            }
        }
    }

    /**
     * Resets builders and particleSpawners, and destroys current entities.
     */
    private void clearEmitters() {
        if (entityManager != null) {
            for (EntityRef ref : entityManager.getEntitiesWith(ParticleEmitterComponent.class)) {
                if (ref.getParentPrefab() != null) {
                    boolean isRain = ref.getParentPrefab().getName().equalsIgnoreCase("WeatherManager:rain");
                    boolean isSnow = ref.getParentPrefab().getName().equalsIgnoreCase("WeatherManager:snow");
                    boolean isHail = ref.getParentPrefab().getName().equalsIgnoreCase("WeatherManager:hail");
                    if (isRain || isSnow || isHail) {
                        ref.destroy();
                    }
                }
            }
        }

        builders = new HashMap<>();
        particleSpawners = new HashMap<>();
    }

    /**
     * Creates new particle emitters based on the location of the player.
     */
    private void makeNewParticleEmitters() {

        if (localPlayer.getPosition() != null && weatherManagerSystem.getCurrentWind() != null) {
            particlesMade = true;
            clearEmitters();
            if (!weatherManagerSystem.getCurrentWeather().equals(DownfallCondition.DownfallType.NONE)) {
                Vector3f baseLoc = round(localPlayer.getPosition());

                center = baseLoc;

                float windXAbs = Math.abs(weatherManagerSystem.getCurrentWind().x * 10);
                float windYAbs = Math.abs(weatherManagerSystem.getCurrentWind().y * 10);
                Vector3f maxVelocity = new Vector3f(Math.min(1.5f, windXAbs), maxDownfall, Math.min(1.5f, windYAbs));
                if (weatherManagerSystem.getCurrentWind().x < 0) {
                    maxVelocity.x *= -1;
                }
                if (weatherManagerSystem.getCurrentWind().y < 0) {
                    maxVelocity.z *= -1;
                }
                Vector3f minVelocity = new Vector3f(maxVelocity.x, minDownfall, maxVelocity.z);

                for (int i = -SIZE_OF_PARTICLE_AREA / 2; i < SIZE_OF_PARTICLE_AREA / 2; i++) {
                    for (int j = -SIZE_OF_PARTICLE_AREA / 2; j < SIZE_OF_PARTICLE_AREA / 2; j++) {
                        Vector3f locEmitter = new Vector3f(baseLoc.x, baseLoc.y, baseLoc.z);
                        locEmitter.add(i, (float) SIZE_OF_PARTICLE_AREA / 3, j);

                        EntityBuilder builder = entityManager.newBuilder(prefabName);

                        builder.getComponent(VelocityRangeGeneratorComponent.class).minVelocity.set(minVelocity);
                        builder.getComponent(VelocityRangeGeneratorComponent.class).maxVelocity.set(maxVelocity);
                        builder.getComponent(LocationComponent.class).setWorldPosition(locEmitter);
                        builder.setPersistent(true);

                        EntityRef ref = builder.build();
                        particleSpawners.put(new Vector2f(locEmitter.x, locEmitter.z), ref);
                        builders.put(new Vector2f(locEmitter.x, locEmitter.z), builder);
                    }
                }
            }
        }
    }

    private void setPrefabName() {
        DownfallCondition.DownfallType weather = weatherManagerSystem.getCurrentWeather();

        if (weather != null && weatherManagerSystem.getCurrentSeverity() != null) {
            maxDownfall = -7;
            minDownfall = -4;
            switch (weatherManagerSystem.getCurrentSeverity()) {
                case HEAVY:
                    maxDownfall = -10;
                    minDownfall = -7;
                    break;
                case MODERATE:
                    maxDownfall = -9;
                    minDownfall = -7;
                    break;
                case LIGHT:
                    maxDownfall = -8;
                    minDownfall = -6;
                    break;
            }

            if (localPlayer.getPosition() != null) {
                switch (weather) {
                    case RAIN:
                        prefabName = "WeatherManager:rain";
                        makeNewParticleEmitters();
                        break;
                    case HAIL:
                        prefabName = "WeatherManager:hail";
                        makeNewParticleEmitters();
                        break;
                    case SNOW:
                        maxDownfall--; //makes snow fall slower
                        minDownfall--;
                        prefabName = "WeatherManager:snow";
                        makeNewParticleEmitters();
                        break;
                    case NONE:
                        prefabName = IS_SUNNY;
                        clearEmitters();
                        break;
                }
            }
        }
    }

    /**
     * Places snow blocks on the ground when it is snowing.
     * The event with the id "placeSnow" will only be created when it is snowing.
     * @param event The event that means it is time to place snow
     * @param worldEntity The entity that recieved the event
     */
    @ReceiveEvent
    public void onPlaceEvent(PeriodicActionTriggeredEvent event, EntityRef worldEntity) {
        if (event.getActionId().equals("placeSnow")) {
            Random rand = new Random();
            int x = (int) localPlayer.getPosition().x + rand.nextInt(SNOW_BLOCK_RANGE * 2) - SNOW_BLOCK_RANGE;
            int z = (int) localPlayer.getPosition().z + rand.nextInt(SNOW_BLOCK_RANGE * 2) - SNOW_BLOCK_RANGE;
            int currentY = (int) localPlayer.getPosition().y + SNOW_BLOCK_RANGE;
            int iter = 0;
            boolean lastGround = false;
            boolean placed = false;
            while(!placed || iter < SNOW_BLOCK_RANGE * 2) {
                Block current = worldProvider.getBlock(x, currentY, z);
                if (current.equals(air) && lastGround) {
                    worldProvider.setBlock(new Vector3i(x, currentY, z), snow);
                    placed = true;
                } else if (current.equals(air)){
                    currentY--;
                    lastGround = false;
                } else if (!current.equals(snow)){
                    lastGround = true;
                    currentY++;
                } else {
                    placed = true; //break out to avoid double-placing snow
                }
                iter++;
            }
        }
    }
}
