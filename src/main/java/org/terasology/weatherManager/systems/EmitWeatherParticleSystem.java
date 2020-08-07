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

import org.joml.Quaternionf;
import org.joml.Vector3f;
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
import org.terasology.logic.players.LocalPlayer;
import org.terasology.logic.players.event.OnPlayerRespawnedEvent;
import org.terasology.naming.Name;
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
import java.util.List;
import java.util.Random;

//TODO: destroy on contact with blocks (water)

@RegisterSystem(RegisterMode.CLIENT)
public class EmitWeatherParticleSystem extends BaseComponentSystem {

    private static final Name SUN = new Name("sunny");
    private static final Name SNOW = new Name("snow");
    private static final Name RAIN = new Name("rain");
    private static final Name HAIL = new Name("hail");

    private static final int PARTICLE_EMITTERS_COUNT = 500;
    private static final int PARTICLE_AREA_SIZE = 15;
    private static final int PARTICLE_AREA_HALF_SIZE = PARTICLE_AREA_SIZE / 2;
    private static final float PARTICLE_SPAWN_HEIGHT = 12;

    private static final Random random = new Random();

    private Name currentWeather = SUN;
    private final List<EntityRef> emitters = new ArrayList<>(PARTICLE_EMITTERS_COUNT);

    @In
    private LocalPlayer localPlayer;

    @In
    private EntityManager entityManager;

    @In
    private WeatherManagerSystem weatherManagerSystem;

    private int minDownfall;
    private int maxDownfall;

    @ReceiveEvent
    public void playerRespawned(OnPlayerRespawnedEvent event, EntityRef entity) {
        if (entityIsLocalPlayer(entity) && !currentWeather.equals(SUN)) {
            beginParticles();
        }
    }

    @ReceiveEvent
    public void playerDied(DeathEvent event, EntityRef entity) {
        if (entityIsLocalPlayer(entity)) {
            clearEmitters();
        }
    }

    private boolean entityIsLocalPlayer(EntityRef entity) {
        return entity.getId() == localPlayer.getCharacterEntity().getId();
    }

    /**
     * Begins the process of visual effects for rain.
     * @param event The StartRainEvent that was received.
     * @param worldEntity The entity that sent the event.
     */
    @ReceiveEvent
    public void onStartRainEvent(StartRainEvent event, EntityRef worldEntity) {
        changeWeather(RAIN);
    }

    /**
     * Begins the process of visual effects for snow.
     * @param event The StartSnowEvent that was received.
     * @param worldEntity The entity that sent the event.
     */
    @ReceiveEvent
    public void onStartSnowEvent(StartSnowEvent event, EntityRef worldEntity) {
        changeWeather(SNOW);
    }

    /**
     * Begins the process of visual effects for hail.
     * @param event The StartHailEvent that was recieved.
     * @param worldEntity The entity that sent the event.
     */
    @ReceiveEvent
    public void onStartHailEvent(StartHailEvent event, EntityRef worldEntity) {
        changeWeather(HAIL);
    }

    private void changeWeather(Name targetWeather) {
        clearEmitters();
        currentWeather = targetWeather;

        if (!currentWeather.equals(SUN)) {
            beginParticles();
        }
    }

    /**
     * Removes all particles for a sunny effect.
     * @param event The StartSunEvent that was received.
     * @param worldEntity The entity that sent the event.
     */
    @ReceiveEvent
    public void onStartSunEvent(StartSunEvent event, EntityRef worldEntity) {
        currentWeather = SUN;
        clearEmitters();
    }

    /**
     * Deletes the particle emitters of the specified entity.
     */
    private void clearEmitters() {
        emitters.forEach(EntityRef::destroy);
        emitters.clear();
    }

    /**
     * Determines the particle fall speed, depending on the current weather.
     */
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
     * Creates new particle emitters for the local player.
     */
    private void beginParticles() {
        prepareParticleProperties();
        LocationComponent location = localPlayer.getCharacterEntity().getComponent(LocationComponent.class);

        if (location != null && weatherManagerSystem.getCurrentWind() != null) {
            if (!weatherManagerSystem.getCurrentWeather().equals(DownfallCondition.DownfallType.NONE)) {
                float windXAbs = Math.abs(weatherManagerSystem.getCurrentWind().x * 10);
                float windYAbs = Math.abs(weatherManagerSystem.getCurrentWind().y * 10);
                Vector3f maxVelocity = new Vector3f(Math.min(1.5f, windXAbs), maxDownfall, Math.min(1.5f, windYAbs));
                if (weatherManagerSystem.getCurrentWind().x < 0) {
                    maxVelocity.x *= -1;
                }
                if (weatherManagerSystem.getCurrentWind().y < 0) {
                    maxVelocity.z *= -1;
                }
                Vector3f minVelocity = new Vector3f(maxVelocity.x / 2, minDownfall, maxVelocity.z / 2);

                emitters.clear();
                ParticlePool particlePool = null;

                for (int i = 0; i < PARTICLE_EMITTERS_COUNT; i++) {
                    EntityBuilder emitterBuilder = entityManager.newBuilder(currentWeather.toString());
                    emitterBuilder.getComponent(VelocityRangeGeneratorComponent.class)
                            .minVelocity.set(minVelocity.x, minVelocity.y, minVelocity.z);
                    emitterBuilder.getComponent(VelocityRangeGeneratorComponent.class)
                            .maxVelocity.set(maxVelocity.x, maxVelocity.y, maxVelocity.z);
                    emitterBuilder.setPersistent(false);

                    if (particlePool != null) {
                        emitterBuilder.getComponent(ParticleEmitterComponent.class).particlePool = particlePool;
                    }

                    EntityRef emitter = emitterBuilder.build();
                    if (particlePool == null) {
                        particlePool = emitter.getComponent(ParticleEmitterComponent.class).particlePool;
                    }

                    float relativeX = (float) random.nextGaussian() * PARTICLE_AREA_HALF_SIZE;
                    float relativeZ = (float) random.nextGaussian() * PARTICLE_AREA_HALF_SIZE + PARTICLE_AREA_SIZE / 3f;
                    Vector3f emitterPosition = new Vector3f(relativeX, PARTICLE_SPAWN_HEIGHT, relativeZ);

                    Location.attachChild(localPlayer.getCharacterEntity(), emitter, emitterPosition, new Quaternionf());
                    emitters.add(emitter);
                }
            }
        }
    }
}
