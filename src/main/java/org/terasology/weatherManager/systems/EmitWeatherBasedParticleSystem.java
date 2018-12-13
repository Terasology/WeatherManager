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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.entitySystem.entity.EntityBuilder;
import org.terasology.entitySystem.entity.EntityManager;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.event.ReceiveEvent;
import org.terasology.entitySystem.systems.BaseComponentSystem;
import org.terasology.entitySystem.systems.RegisterSystem;
import org.terasology.logic.location.LocationComponent;
import org.terasology.logic.players.event.OnPlayerRespawnedEvent;
import org.terasology.logic.players.event.OnPlayerSpawnedEvent;
import org.terasology.math.geom.Vector2f;
import org.terasology.math.geom.Vector3f;
import org.terasology.particles.components.ParticleEmitterComponent;
import org.terasology.particles.components.generators.VelocityRangeGeneratorComponent;
import org.terasology.physics.events.MovedEvent;
import org.terasology.registry.In;
import org.terasology.weatherManager.events.StartWeatherEvent;
import org.terasology.weatherManager.weather.DownfallCondition;
import org.terasology.weatherManager.weather.Severity;
import org.terasology.world.WorldProvider;
import org.terasology.world.block.BlockManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

//TODO: possibly change distance between particles
//TODO: destroy on contact with blocks?

@RegisterSystem
public class EmitWeatherBasedParticleSystem extends BaseComponentSystem {

    private EntityRef player;

    private final int SIZE_OF_PARTICLE_AREA = 24;
    private final int BUFFER_AMOUNT = 5;

    private String prefabName;

    private Vector3f center;

    private Map<Vector2f, EntityRef> particleSpawners;

    private Map<Vector2f, EntityBuilder> builders;

    @In
    private EntityManager entityManager;

    private Logger logger = LoggerFactory.getLogger(EmitWeatherBasedParticleSystem.class);

    private int minDownfall;
    private int maxDownfall;

    @ReceiveEvent
    public void onPlayerSpawn(OnPlayerSpawnedEvent event, EntityRef character) {
        particleSpawners = new HashMap<>();
        builders = new HashMap<>();
        player = character;
    }

    @ReceiveEvent
    public void onPlayerRespawn(OnPlayerRespawnedEvent event, EntityRef character) {
        player = character;
    }

    @ReceiveEvent
    public void onStartWeatherEvent(StartWeatherEvent event, EntityRef worldEntity) {

        maxDownfall = -7;
        minDownfall = -4;
        if (WeatherManagerSystem.getCurrentSeverity().equals(Severity.HEAVY)) {
            maxDownfall = -10;
            minDownfall = -7;
        } else if (WeatherManagerSystem.getCurrentSeverity().equals(Severity.MODERATE)) {
            maxDownfall = -8;
            minDownfall = -6;
        }

        for (EntityRef val : particleSpawners.values()) {
            val.destroy();
        }

        if (player != null) {
            DownfallCondition.DownfallType weather = WeatherManagerSystem.getCurrentWeather();
            logger.info("weather: "+weather);

            if (weather.equals(DownfallCondition.DownfallType.RAIN)) {
                prefabName = "WeatherManager:rain";
                makeNewParticleEmitters();
            } else if (weather.equals(DownfallCondition.DownfallType.HAIL)) {
                prefabName = "WeatherManager:hail";
                makeNewParticleEmitters();
            } else if (weather.equals(DownfallCondition.DownfallType.SNOW)) {
                prefabName = "WeatherManager:snow";
                makeNewParticleEmitters();
            } else if (weather.equals(DownfallCondition.DownfallType.NONE)) {
                clearEmitters();
            }
        }
    }

    @ReceiveEvent
    public void onCharacterMoved(MovedEvent event, EntityRef character) {

        if (center != null) {
            Vector3f pos = round(new Vector3f(event.getPosition()));

            if (!pos.equals(center)) {

                Vector3f dif = new Vector3f(center).sub(pos);

                center = new Vector3f(pos);

                if (dif.x != 0) {
                    if (dif.x > 0) {
                        refreshParticles(true, true);
                    } else {
                        refreshParticles(true, false);
                    }
                }
                if (dif.z != 0) {
                    if (dif.z > 0) {
                        refreshParticles(false, true);
                    } else {
                        refreshParticles(false, false);
                    }
                }
            }
        }
    }

    private Vector3f round (Vector3f original) {
        original.x = Math.round(original.x);
        original.y = Math.round(original.y);
        original.z = Math.round(original.z);

        return original;
    }

    private void refreshParticles(boolean x, boolean positive) {

        ArrayList<Vector2f> remove = new ArrayList<>();

        for(Vector2f vect : particleSpawners.keySet()) {
            float distX = vect.distance(new Vector2f(center.x, vect.y));
            float distY = vect.distance(new Vector2f(vect.x, center.z));
            if (distX > SIZE_OF_PARTICLE_AREA / 2 + BUFFER_AMOUNT || distY > SIZE_OF_PARTICLE_AREA / 2 + BUFFER_AMOUNT) {
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

        Vector3f maxVelocity = new Vector3f(Math.min(.25f, WeatherManagerSystem.getCurrentWind().x * 10), maxDownfall, Math.min(.25f, WeatherManagerSystem.getCurrentWind().x * 10));
        Vector3f minVelocity = new Vector3f(maxVelocity.x, minDownfall, maxVelocity.z);

        for (int i = 0; i < BUFFER_AMOUNT; i++) {
            for (int j = -SIZE_OF_PARTICLE_AREA / 2; j < SIZE_OF_PARTICLE_AREA / 2; j++) {
                Vector3f loc_emitter = new Vector3f(center);

                if (x) {
                    if (positive) {
                        loc_emitter.add(-Math.round((float) SIZE_OF_PARTICLE_AREA / 2) - i, (float) SIZE_OF_PARTICLE_AREA / 3, j);
                    } else {
                        loc_emitter.add(Math.round((float) SIZE_OF_PARTICLE_AREA / 2) + i, (float) SIZE_OF_PARTICLE_AREA / 3, j);
                    }
                } else {
                    if (positive) {
                        loc_emitter.add(j, (float) SIZE_OF_PARTICLE_AREA / 3, -Math.round((float) SIZE_OF_PARTICLE_AREA / 2 - i));
                    } else {
                        loc_emitter.add(j, (float) SIZE_OF_PARTICLE_AREA / 3, Math.round((float) SIZE_OF_PARTICLE_AREA / 2 + i));
                    }
                }

                if (!particleSpawners.containsKey(new Vector2f(loc_emitter.x, loc_emitter.z))) {
                    EntityBuilder builder = entityManager.newBuilder(prefabName);
                    builder.getComponent(LocationComponent.class).setWorldPosition(loc_emitter);
                    builder.getComponent(VelocityRangeGeneratorComponent.class).minVelocity.set(minVelocity);
                    builder.getComponent(VelocityRangeGeneratorComponent.class).maxVelocity.set(maxVelocity);
                    EntityRef ref = builder.build();
                    particleSpawners.put(new Vector2f(loc_emitter.x, loc_emitter.z), ref);
                    builders.put(new Vector2f(loc_emitter.x, loc_emitter.z), builder);
                }
            }
        }
    }

    private void clearEmitters() {
        for (EntityRef ref : particleSpawners.values()) {
            ref.destroy();
        }

        builders = new HashMap<>();
        particleSpawners = new HashMap<>();
    }

    private void makeNewParticleEmitters() {

        clearEmitters();

        Vector3f baseLoc = round(player.getComponent(LocationComponent.class).getWorldPosition());

        center = baseLoc;

        Vector3f maxVelocity = new Vector3f(Math.min(.25f, WeatherManagerSystem.getCurrentWind().x * 10), maxDownfall, Math.min(.25f, WeatherManagerSystem.getCurrentWind().x * 10));
        Vector3f minVelocity = new Vector3f(maxVelocity.x, minDownfall, maxVelocity.z);

        for (int i = -SIZE_OF_PARTICLE_AREA / 2; i < SIZE_OF_PARTICLE_AREA / 2; i++) {
            for (int j = -SIZE_OF_PARTICLE_AREA / 2; j < SIZE_OF_PARTICLE_AREA / 2; j++) {
                Vector3f loc_emitter = new Vector3f(baseLoc.x, baseLoc.y, baseLoc.z);
                loc_emitter.add(i, (float) SIZE_OF_PARTICLE_AREA / 3, j);

                EntityBuilder builder = entityManager.newBuilder(prefabName);

                builder.getComponent(VelocityRangeGeneratorComponent.class).minVelocity.set(minVelocity);
                builder.getComponent(VelocityRangeGeneratorComponent.class).maxVelocity.set(maxVelocity);
                builder.getComponent(LocationComponent.class).setWorldPosition(loc_emitter);
                EntityRef ref = builder.build();
                particleSpawners.put(new Vector2f(loc_emitter.x, loc_emitter.z), ref);
                builders.put(new Vector2f(loc_emitter.x, loc_emitter.z), builder);
            }
        }
    }
}
