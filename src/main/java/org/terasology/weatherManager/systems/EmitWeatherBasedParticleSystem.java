package org.terasology.weatherManager.systems;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.entitySystem.entity.EntityBuilder;
import org.terasology.entitySystem.entity.EntityManager;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.entity.internal.EntityInfoComponent;
import org.terasology.entitySystem.event.ReceiveEvent;
import org.terasology.entitySystem.prefab.Prefab;
import org.terasology.entitySystem.systems.BaseComponentSystem;
import org.terasology.entitySystem.systems.RegisterSystem;
import org.terasology.logic.common.ActivateEvent;
import org.terasology.logic.delay.DelayedActionTriggeredEvent;
import org.terasology.logic.location.LocationComponent;
import org.terasology.logic.players.event.OnPlayerRespawnedEvent;
import org.terasology.logic.players.event.OnPlayerSpawnedEvent;
import org.terasology.math.geom.Vector2f;
import org.terasology.math.geom.Vector3f;
import org.terasology.particles.components.ParticleEmitterComponent;
import org.terasology.physics.events.MovedEvent;
import org.terasology.registry.In;
import org.terasology.weatherManager.components.EmitWeatherBasedParticleComponent;
import org.terasology.weatherManager.events.StartRainEvent;
import org.terasology.weatherManager.weather.DownfallCondition;
import org.terasology.world.WorldProvider;
import org.terasology.world.block.BlockComponent;
import org.terasology.world.block.BlockManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

//TODO: implement for things other than rain
//TODO: make particles affected by wind, severity
//TODO: possibly change distance between particles
//TODO: destroy on contact with blocks?

@RegisterSystem
public class EmitWeatherBasedParticleSystem extends BaseComponentSystem {

    private EntityRef player;

    private final int SIZE_OF_RAIN_AREA = 24;

   // private Vector2f center;
    private Vector3f center;

    private Map<Vector2f, EntityRef> particleSpawners;

    private Map<Vector2f, EntityBuilder> builders;

    @In
    private EntityManager entityManager;

    @In
    private WorldProvider worldProvider;

    @In
    private BlockManager blockManager;

    Logger logger = LoggerFactory.getLogger(EmitWeatherBasedParticleSystem.class);

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
    public void startRainEvent(StartRainEvent event, EntityRef worldEntity) {

        for (EntityRef val : particleSpawners.values()) {
            val.destroy();
        }

        logger.info("event: "+player.getComponent(LocationComponent.class).getWorldPosition());

        if (player != null) {
            DownfallCondition.DownfallType weather = WeatherManagerSystem.getCurrentWeather();
            logger.info("weather: "+weather);

            if (weather.equals(DownfallCondition.DownfallType.RAIN)) {
                logger.info("RAIN");
                Vector3f baseLoc = round(player.getComponent(LocationComponent.class).getWorldPosition());

                //center = new Vector2f(baseLoc.x, baseLoc.z);
                center = baseLoc;

                for (int i = -SIZE_OF_RAIN_AREA / 2; i < SIZE_OF_RAIN_AREA / 2; i++) {
                    for (int j = -SIZE_OF_RAIN_AREA / 2; j < SIZE_OF_RAIN_AREA / 2; j++) {
                        Vector3f loc_emitter = new Vector3f(baseLoc.x, baseLoc.y, baseLoc.z);
                        loc_emitter.add(i, (float) SIZE_OF_RAIN_AREA / 3, j);

                        EntityBuilder builder = entityManager.newBuilder("WeatherManager:rain");
                        builder.getComponent(LocationComponent.class).setWorldPosition(loc_emitter);
                        EntityRef ref = builder.build();
                        particleSpawners.put(new Vector2f(loc_emitter.x, loc_emitter.z), ref);
                        builders.put(new Vector2f(loc_emitter.x, loc_emitter.z), builder);
                    }
                }
            }
        }
    }

    @ReceiveEvent
    public void onCharacterMoved(MovedEvent event, EntityRef character) {
        //player = character;

        if (center != null) { //TODO: check if raining before entering
            Vector3f pos = round(new Vector3f(event.getPosition()));

            Vector2f posAsVect2 = new Vector2f(pos.x, pos.z);

            if (!pos.equals(center)) {
                //logger.info("dif: " + dif);

                /*
                if (dif.x != 0) {
                    if (dif.x < ) {
                        makeNewEmitters(pos, true, false);
                    } else {
                        makeNewEmitters(pos, true, true);
                    }
                }
                if (dif.y != 0) {
                    if (dif.y < 0) {
                        makeNewEmitters(pos, false, false);
                    } else {
                        makeNewEmitters(pos, false, true);
                    }
                }*/

                //center = posAsVect2

                Vector3f dif = new Vector3f(center).sub(pos);

                center = new Vector3f(pos);
                logger.info("center: "+center);

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
        /*
        if (particleSpawners.containsKey(posAsVect2)) {
            logger.info("pos: "+pos);
        }*/
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
            if (distX > SIZE_OF_RAIN_AREA / 2 || distY > SIZE_OF_RAIN_AREA / 2) { //TODO: figure out why all are getting destroyed
                remove.add(vect);
                if (builders.containsKey(vect)) {
                    particleSpawners.get(vect).destroy();
                    builders.remove(vect);
                }
            } else {
                Vector3f newPos = new Vector3f(vect.x, center.y + (float) SIZE_OF_RAIN_AREA / 3, vect.y);
                LocationComponent loc = builders.get(vect).getComponent(LocationComponent.class);
                if (!loc.getWorldPosition().equals(newPos)) {
                    builders.get(vect).getComponent(LocationComponent.class).setWorldPosition(newPos);
                    //builders.put(new Vector2f(newPos.x, newPos.z), builders.get(vect));
                    //builders.remove(vect);
                }
            }
        }

        for (Vector2f toRemove : remove) {
            particleSpawners.remove(toRemove);
        }

        for (int j = -SIZE_OF_RAIN_AREA / 2; j < SIZE_OF_RAIN_AREA / 2; j++) {
            Vector3f loc_emitter = new Vector3f(center);

            if (x) {
                if (positive) {
                    loc_emitter.add(-Math.round((float) SIZE_OF_RAIN_AREA / 2), (float) SIZE_OF_RAIN_AREA / 3, j);
                } else {
                    loc_emitter.add(Math.round((float) SIZE_OF_RAIN_AREA / 2), (float) SIZE_OF_RAIN_AREA / 3, j);
                }
            } else {
                if (positive) {
                    loc_emitter.add(j, (float) SIZE_OF_RAIN_AREA / 3, -Math.round((float) SIZE_OF_RAIN_AREA / 2));
                } else {
                    loc_emitter.add(j, (float) SIZE_OF_RAIN_AREA / 3, Math.round((float) SIZE_OF_RAIN_AREA / 2));
                }
            }

            if (!particleSpawners.containsKey(new Vector2f(loc_emitter.x, loc_emitter.z))) {
                //logger.info("building at: "+loc_emitter);
                EntityBuilder builder = entityManager.newBuilder("WeatherManager:rain");
                builder.getComponent(LocationComponent.class).setWorldPosition(loc_emitter);
                EntityRef ref = builder.build();
                particleSpawners.put(new Vector2f(loc_emitter.x, loc_emitter.z), ref);
                builders.put(new Vector2f(loc_emitter.x, loc_emitter.z), builder);
            }
        }
    }

    /*
    private void makeNewEmitters(Vector3f pos, boolean x, boolean positive) {

        for (int j = -SIZE_OF_RAIN_AREA / 2; j < SIZE_OF_RAIN_AREA / 2; j++) {
            Vector3f loc_emitter = new Vector3f(pos.x, pos.y, pos.z);

            if (x) {
                loc_emitter.add(j, (float) SIZE_OF_RAIN_AREA / 2, Math.round((float) SIZE_OF_RAIN_AREA / 2));
            } else {
                loc_emitter.add(Math.round((float) SIZE_OF_RAIN_AREA / 2), (float) SIZE_OF_RAIN_AREA / 2, j);
            }

            if (!particleSpawners.containsKey(new Vector2f(loc_emitter.x, loc_emitter.z))) {
                EntityBuilder builder = entityManager.newBuilder("WeatherManager:rain");
                builder.getComponent(LocationComponent.class).setWorldPosition(loc_emitter);
                EntityRef ref = builder.build();
                particleSpawners.put(new Vector2f(loc_emitter.x, loc_emitter.z), ref); // Spawn the particle emitter
            }
        }

        for (int j = -SIZE_OF_RAIN_AREA / 2; j < SIZE_OF_RAIN_AREA / 2; j++) {
            Vector3f loc_emitter = new Vector3f(pos.x, pos.y, pos.z);

            if (x) {
                if (positive) {
                    loc_emitter.add((float) -SIZE_OF_RAIN_AREA / 2, 0, j);
                } else {
                    loc_emitter.add((float) SIZE_OF_RAIN_AREA / 2, 0, j);
                }
            } else {
                if (positive) {
                    loc_emitter.add(j, 0, (float) -SIZE_OF_RAIN_AREA / 2);
                } else {
                    loc_emitter.add(j, 0, (float) SIZE_OF_RAIN_AREA / 2);
                }
            }

            Vector2f emitterAsVect2 = new Vector2f(loc_emitter.x, loc_emitter.z);

            if (particleSpawners.containsKey(emitterAsVect2)) {
                logger.info("destroying");
                particleSpawners.get(emitterAsVect2).destroy();
            }
        }
    }*/
}
