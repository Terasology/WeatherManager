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
import org.terasology.logic.location.LocationComponent;
import org.terasology.logic.players.event.OnPlayerSpawnedEvent;
import org.terasology.math.JomlUtil;
import org.terasology.math.geom.Vector2f;
import org.terasology.math.geom.Vector3f;
import org.terasology.network.events.DisconnectedEvent;
import org.terasology.particles.components.ParticleEmitterComponent;
import org.terasology.particles.components.generators.VelocityRangeGeneratorComponent;
import org.terasology.physics.events.MovedEvent;
import org.terasology.registry.In;
import org.terasology.weatherManager.events.StartHailEvent;
import org.terasology.weatherManager.events.StartRainEvent;
import org.terasology.weatherManager.events.StartSnowEvent;
import org.terasology.weatherManager.events.StartSunEvent;
import org.terasology.weatherManager.weather.DownfallCondition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

//TODO: destroy on contact with blocks (water)

@RegisterSystem(RegisterMode.CLIENT)
public class EmitWeatherParticleSystem extends BaseComponentSystem {

    private static final String SUN = "sunny";
    private static final String SNOW = "snow";
    private static final String RAIN = "rain";
    private static final String HAIL = "hail";

    private static final int SIZE_OF_PARTICLE_AREA = 24;
    private static final int BUFFER_AMOUNT = 5;
    private static final int CLOUD_HEIGHT = 127;

    private String prefabName = SUN;

    private Map<Vector2f, EntityRef> particleSpawners;

    private Map<Vector2f, EntityBuilder> builders;

    private Map<EntityRef, Vector3f> previousLocations;

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

        particleSpawners = new HashMap<>();
        builders = new HashMap<>();
        previousLocations = new HashMap<>();
    }

    @ReceiveEvent
    public void playerSpawned(OnPlayerSpawnedEvent event, EntityRef player) {
        begin(player);
    }

    @ReceiveEvent
    public void playerRespawned(OnPlayerSpawnedEvent event, EntityRef player) {
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

            for (EntityRef ref : previousLocations.keySet()) {
                makeNewParticleEmitters(ref);
            }
        } //TODO: test the refector, multiplayer
    }

    /**
     * Begins particle effects around a given player.
     * @param player The player whose effects must begin
     */
    private void begin(EntityRef player) {
        LocationComponent loc = player.getComponent(LocationComponent.class);

        if (loc != null) {
            previousLocations.put(player, new Vector3f(loc.getWorldPosition()));
            if (!prefabName.equals(SUN)) {
                makeNewParticleEmitters(player);
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
     * Moves the visual effects with the player.
     * @param event The MovedEvent.
     * @param character The entity that sent the MoveEvent.
     */
    @ReceiveEvent
    public void onCharacterMoved(MovedEvent event, EntityRef character) {

        if (previousLocations.containsKey(character)) {
            Vector3f center = previousLocations.get(character);
            Vector3f pos = round(new Vector3f(event.getPosition()));

            if (center != null && !pos.equals(center)) {

                Vector3f dif = new Vector3f(center).sub(pos);

                center = new Vector3f(pos);
                previousLocations.replace(character, center);

                if (dif.x != 0) {
                    if (dif.x > 0) {
                        refreshParticles(true, false, true, center);
                    } else {
                        refreshParticles(true, false, false, center);
                    }
                }
                if (dif.z != 0) {
                    if (dif.z > 0) {
                        refreshParticles(false, true, true, center);
                    } else {
                        refreshParticles(false, true, false, center);
                    }
                }
                if (dif.y != 0) {
                    if (dif.y > 0) {
                        refreshParticles(false, false, false, center);
                    } else {
                        refreshParticles(false, true, false, center);
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
    private void refreshParticles(boolean x, boolean z, boolean positive, Vector3f center) {

        if (!prefabName.equals(SUN)) {
            ArrayList<Vector2f> remove = new ArrayList<>();

            for (Vector2f vect : particleSpawners.keySet()) {
                float distX = vect.distance(new Vector2f(center.x, vect.y));
                float distZ = vect.distance(new Vector2f(vect.x, center.z));
                if (distX > SIZE_OF_PARTICLE_AREA / 2 + BUFFER_AMOUNT || distZ > SIZE_OF_PARTICLE_AREA / 2 + BUFFER_AMOUNT) {
                    if (!inRange(vect)) {
                        remove.add(vect);
                        if (builders.containsKey(vect)) {
                            particleSpawners.get(vect).destroy();
                            builders.remove(vect);
                        }
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
                        builder.getComponent(VelocityRangeGeneratorComponent.class).minVelocity.set(JomlUtil.from(minVelocity));
                        builder.getComponent(VelocityRangeGeneratorComponent.class).maxVelocity.set(JomlUtil.from(maxVelocity));
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
     * Resets builders and particleSpawners, and destroys current entities (within range of player).
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
    private void makeNewParticleEmitters(EntityRef player) {
        LocationComponent loc = player.getComponent(LocationComponent.class);

        if (loc != null && weatherManagerSystem.getCurrentWind() != null) {
            particlesMade = true;

            if (!weatherManagerSystem.getCurrentWeather().equals(DownfallCondition.DownfallType.NONE)) {
                Vector3f baseLoc = round(loc.getWorldPosition());

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

                        builder.getComponent(VelocityRangeGeneratorComponent.class).minVelocity.set(JomlUtil.from(minVelocity));
                        builder.getComponent(VelocityRangeGeneratorComponent.class).maxVelocity.set(JomlUtil.from(maxVelocity));
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
     * @param emitterLoc The center location of the range.
     */
    private boolean inRange(Vector2f emitterLoc) {

        for (Vector3f other : previousLocations.values()) {
            Vector2f withoutY = new Vector2f(other.x, other.z);

            if (withoutY.distance(emitterLoc) < SIZE_OF_PARTICLE_AREA / 2 + BUFFER_AMOUNT) {
                return true;
            }
        }
        return false;
    }
}
