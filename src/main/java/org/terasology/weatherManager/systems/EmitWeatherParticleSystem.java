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
import org.terasology.entitySystem.event.ReceiveEvent;
import org.terasology.entitySystem.systems.BaseComponentSystem;
import org.terasology.entitySystem.systems.RegisterMode;
import org.terasology.entitySystem.systems.RegisterSystem;
import org.terasology.logic.characters.events.DeathEvent;
import org.terasology.logic.location.Location;
import org.terasology.logic.location.LocationComponent;
import org.terasology.logic.players.event.OnPlayerRespawnedEvent;
import org.terasology.logic.players.event.OnPlayerSpawnedEvent;
import org.terasology.math.geom.Vector2f;
import org.terasology.math.geom.Vector3f;
import org.terasology.network.events.DisconnectedEvent;
import org.terasology.particles.ParticlePool;
import org.terasology.particles.components.ParticleEmitterComponent;
import org.terasology.particles.components.generators.VelocityRangeGeneratorComponent;
import org.terasology.registry.In;
import org.terasology.weatherManager.events.StartHailEvent;
import org.terasology.weatherManager.events.StartRainEvent;
import org.terasology.weatherManager.events.StartSnowEvent;
import org.terasology.weatherManager.events.StartSunEvent;
import org.terasology.weatherManager.weather.DownfallCondition;

import java.util.HashMap;
import java.util.Map;

//TODO: destroy on contact with blocks (water)

@RegisterSystem(RegisterMode.CLIENT)
public class EmitWeatherParticleSystem extends BaseComponentSystem {

    private static final String SUN = "sunny";
    private static final String SNOW = "snow";
    private static final String RAIN = "rain";
    private static final String HAIL = "hail";

    private static final int PARTICLE_AREA_SIZE = 24;
    private static final int PARTICLE_AREA_HALF_SIZE = PARTICLE_AREA_SIZE / 2;
    private static final float PARTICLE_SPAWN_HEIGHT = PARTICLE_AREA_SIZE / 3f;
    private static final int BUFFER_AMOUNT = 5;
    private static final int CLOUD_HEIGHT = 127;

    private String prefabName = SUN;

    private final Map<Vector2f, EntityRef> particleSpawners = new HashMap<>();
    private final Map<Vector2f, EntityBuilder> builders = new HashMap<>();
    private final Map<EntityRef, Vector3f> previousLocations = new HashMap<>();

    private boolean particlesMade;

    @In
    private EntityManager entityManager;

    @In
    private WeatherManagerSystem weatherManagerSystem;

    private int minDownfall;
    private int maxDownfall;

    @Override
    public void postBegin() {
        particlesMade = false;
    }

    @ReceiveEvent
    public void playerSpawned(OnPlayerSpawnedEvent event, EntityRef player) {
        begin(player);
    }

    @ReceiveEvent
    public void playerRespawned(OnPlayerRespawnedEvent event, EntityRef player) {
        begin(player);
    }

    @ReceiveEvent
    public void playerDied(DeathEvent event, EntityRef player) {
        end(player);
    }

    @ReceiveEvent
    public void playerLeft(DisconnectedEvent event, EntityRef player) {
        end(player);
    }

    /**
     * Ends particle effects around a given playerbegin.
     * @param player The player whose effects must be removed.
     */
    private void end(EntityRef player) {
        if (player.hasComponent(LocationComponent.class) && previousLocations.containsKey(player)) {
            clearEmitters();
            previousLocations.remove(player);

            for (EntityRef entity : previousLocations.keySet()) {
                makeNewParticleEmitters(entity);
            }
        } //TODO: test the refector, multiplayer
    }

    /**
     * Begins particle effects around a given player.
     * @param player The player whose effects must begin
     */
    private void begin(EntityRef player) {
        LocationComponent location = player.getComponent(LocationComponent.class);

        if (location != null) {
            previousLocations.put(player, new Vector3f(location.getWorldPosition()));
            if (!prefabName.equals(SUN)) {
                beginParticles();
            }
        }
    }

    /**
     * Begins the process of visual effects for rain.
     * @param event The StartRainEvent that was received.
     * @param worldEntity The entity that sent the event.
     */
    @ReceiveEvent
    public void onStartRainEvent(StartRainEvent event, EntityRef worldEntity) {
        clearEmitters();
        prefabName = RAIN;

        beginParticles();
    }

    /**
     * Begins the process of visual effects for snow.
     * @param event The StartSnowEvent that was received.
     * @param worldEntity The entity that sent the event.
     */
    @ReceiveEvent
    public void onStartSnowEvent(StartSnowEvent event, EntityRef worldEntity) {
        clearEmitters();
        prefabName = SNOW;

        beginParticles();
    }

    /**
     * Begins the process of visual effects for hail.
     * @param event The StartHailEvent that was recieved.
     * @param worldEntity The entity that sent the event.
     */
    @ReceiveEvent
    public void onStartHailEvent(StartHailEvent event, EntityRef worldEntity) {
        clearEmitters();
        prefabName = HAIL;

        beginParticles();
    }

    /**
     * Removes all particles for a sunny effect.
     * @param event The StartSunEvent that was received.
     * @param worldEntity The entity that sent the event.
     */
    @ReceiveEvent
    public void onStartSunEvent(StartSunEvent event, EntityRef worldEntity) {
        prefabName = SUN;

        clearEmitters();
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
     * Resets builders and particleSpawners, and destroys current entities (within range of player).
     */
    private void clearEmitters() {
        if (entityManager != null) {
            for (EntityRef entity : entityManager.getEntitiesWith(ParticleEmitterComponent.class)) {
                if (entity.getParentPrefab() != null) {
                    boolean isRain = entity.getParentPrefab().getName().equalsIgnoreCase("WeatherManager:rain");
                    boolean isSnow = entity.getParentPrefab().getName().equalsIgnoreCase("WeatherManager:snow");
                    boolean isHail = entity.getParentPrefab().getName().equalsIgnoreCase("WeatherManager:hail");
                    if (isRain || isSnow || isHail) {
                        entity.destroy();
                    }
                }
            }
        }

        builders.clear();
        particleSpawners.clear();
    }

    /**
     * Creates new particle emitters based on the location of the player.
     */
    private void makeNewParticleEmitters(EntityRef player) {
        LocationComponent location = player.getComponent(LocationComponent.class);

        if (location != null && weatherManagerSystem.getCurrentWind() != null) {
            particlesMade = true;

            if (!weatherManagerSystem.getCurrentWeather().equals(DownfallCondition.DownfallType.NONE)) {
                Vector3f worldPosition = round(location.getWorldPosition());

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

                ParticlePool particlePool = null;
                float zOffset = PARTICLE_AREA_HALF_SIZE / 2f;

                for (int i = -PARTICLE_AREA_HALF_SIZE; i < PARTICLE_AREA_HALF_SIZE; i++) {
                    for (int j = -PARTICLE_AREA_HALF_SIZE; j < PARTICLE_AREA_HALF_SIZE; j++) {
                        Vector3f emitterPosition = new Vector3f(worldPosition);
                        emitterPosition.add(i, PARTICLE_SPAWN_HEIGHT, j + zOffset);

                        EntityBuilder emitterBuilder = entityManager.newBuilder(prefabName);

                        emitterBuilder.getComponent(VelocityRangeGeneratorComponent.class).minVelocity.set(minVelocity);
                        emitterBuilder.getComponent(VelocityRangeGeneratorComponent.class).maxVelocity.set(maxVelocity);
                        emitterBuilder.getComponent(LocationComponent.class).setWorldPosition(emitterPosition);
                        emitterBuilder.setPersistent(true);

                        if (particlePool != null)
                            emitterBuilder.getComponent(ParticleEmitterComponent.class).particlePool = particlePool;

                        EntityRef emitter = emitterBuilder.build();
                        if (particlePool == null)
                            particlePool = emitter.getComponent(ParticleEmitterComponent.class).particlePool;

                        Location.attachChild(player, emitter);

                        particleSpawners.put(new Vector2f(emitterPosition.x, emitterPosition.z), emitter);
                        builders.put(new Vector2f(emitterPosition.x, emitterPosition.z), emitterBuilder);
                    }
                }
            }
        }
    }

    private void beginParticles() {
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

            for (EntityRef ref : previousLocations.keySet()) {
                makeNewParticleEmitters(ref);
            }
        }
    }

    /**
     * Finds if another player is within particle range of the given Vector2f.
     * Intended to be used when clearing off particles.
     * @param emitterPosition The center location of the range.
     */
    private boolean inRange(Vector2f emitterPosition) {

        for (Vector3f entityPosition : previousLocations.values()) {
            Vector2f horizontalPosition = new Vector2f(entityPosition.x, entityPosition.z);

            if (horizontalPosition.distance(emitterPosition) < PARTICLE_AREA_HALF_SIZE + BUFFER_AMOUNT) {
                return true;
            }
        }
        return false;
    }
}
