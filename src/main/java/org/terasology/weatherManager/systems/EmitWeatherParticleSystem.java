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
import org.terasology.math.geom.Vector3f;
import org.terasology.network.ClientComponent;
import org.terasology.network.events.ConnectedEvent;
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

//TODO: destroy on contact with blocks (water)

@RegisterSystem(RegisterMode.CLIENT)
public class EmitWeatherParticleSystem extends BaseComponentSystem {

    private static final String SUN = "sunny";
    private static final String SNOW = "snow";
    private static final String RAIN = "rain";
    private static final String HAIL = "hail";

    private static final int PARTICLE_AREA_SIZE = 20;
    private static final int PARTICLE_AREA_HALF_SIZE = PARTICLE_AREA_SIZE / 2;
    private static final float PARTICLE_SPAWN_HEIGHT = PARTICLE_AREA_SIZE / 3f;
    private static final int BUFFER_AMOUNT = 5;
    private static final int CLOUD_HEIGHT = 127;

    private String prefabName = SUN;

    private final Map<EntityRef, List<EntityRef>> emittersByParentEntity = new HashMap<>();

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

    @ReceiveEvent(components = ClientComponent.class)
    public void onConnect(ConnectedEvent connected, EntityRef entity) {
        begin(entity);
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
     * Ends particle effects around a given player.
     * @param player The player whose effects must be removed.
     */
    private void end(EntityRef player) {
        if (emittersByParentEntity.containsKey(player)) {
            clearEmitters(player);
            emittersByParentEntity.remove(player);
        }
    }

    /**
     * Begins particle effects around a given player.
     * @param player The player whose effects must begin
     */
    private void begin(EntityRef player) {
        if (!prefabName.equals(SUN)) {
            prepareParticleProperties();
            beginParticles(player);
        }
    }

    /**
     * Begins the process of visual effects for rain.
     * @param event The StartRainEvent that was received.
     * @param worldEntity The entity that sent the event.
     */
    @ReceiveEvent
    public void onStartRainEvent(StartRainEvent event, EntityRef worldEntity) {
        if (!prefabName.equals(RAIN))
            changeWeather(RAIN);
    }

    /**
     * Begins the process of visual effects for snow.
     * @param event The StartSnowEvent that was received.
     * @param worldEntity The entity that sent the event.
     */
    @ReceiveEvent
    public void onStartSnowEvent(StartSnowEvent event, EntityRef worldEntity) {
        if (!prefabName.equals(SNOW))
            changeWeather(SNOW);
    }

    /**
     * Begins the process of visual effects for hail.
     * @param event The StartHailEvent that was recieved.
     * @param worldEntity The entity that sent the event.
     */
    @ReceiveEvent
    public void onStartHailEvent(StartHailEvent event, EntityRef worldEntity) {
        if (!prefabName.equals(HAIL))
            changeWeather(HAIL);
    }

    private void changeWeather(String weatherPrefab) {
        clearAllEmitters();
        prefabName = weatherPrefab;
        prepareParticleProperties();
        beginParticlesForAllEntities();
    }

    private void beginParticlesForAllEntities() {
        emittersByParentEntity.keySet().forEach(this::beginParticles);
    }

    /**
     * Removes all particles for a sunny effect.
     * @param event The StartSunEvent that was received.
     * @param worldEntity The entity that sent the event.
     */
    @ReceiveEvent
    public void onStartSunEvent(StartSunEvent event, EntityRef worldEntity) {
        prefabName = SUN;
        clearAllEmitters();
    }

    /**
     * Deletes the particle emitters of all known entities.
     */
    private void clearAllEmitters() {
        for (EntityRef entity : emittersByParentEntity.keySet()) {
            clearEmitters(entity);
        }
    }

    /**
     * Deletes the particle emitters of the specified entity.
     */
    private void clearEmitters(EntityRef entity) {
        Collection<EntityRef> emitters = emittersByParentEntity.get(entity);
        emitters.forEach(EntityRef::destroy);
        emitters.clear();
    }

    private void prepareParticleProperties() {
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
        }
    }

    /**
     * Creates new particle emitters based on the location of the entity.
     */
    private void beginParticles(EntityRef entity) {
        LocationComponent location = entity.getComponent(LocationComponent.class);

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

                List<EntityRef> emitters;
                if (emittersByParentEntity.containsKey(entity)) {
                    emitters = emittersByParentEntity.get(entity);
                    emitters.clear();
                } else {
                    emitters = new ArrayList<>(PARTICLE_AREA_SIZE * PARTICLE_AREA_SIZE);
                    emittersByParentEntity.put(entity, emitters);
                }

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
                        emitterBuilder.setPersistent(false);

                        if (particlePool != null)
                            emitterBuilder.getComponent(ParticleEmitterComponent.class).particlePool = particlePool;

                        EntityRef emitter = emitterBuilder.build();
                        if (particlePool == null)
                            particlePool = emitter.getComponent(ParticleEmitterComponent.class).particlePool;

                        Location.attachChild(entity, emitter);
                        emitters.add(emitter);
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
}
