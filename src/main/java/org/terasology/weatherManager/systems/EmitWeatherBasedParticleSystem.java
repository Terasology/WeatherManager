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

@RegisterSystem
public class EmitWeatherBasedParticleSystem extends BaseComponentSystem {

    private EntityRef player;

    private final int SIZE_OF_RAIN_AREA = 20;

    private Vector2f center;

    private Map<Vector2f, EntityRef> particleSpawners;

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
        player = character;
        LoggerFactory.getLogger("").info("player spawned");
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

                center = new Vector2f(baseLoc.x, baseLoc.z);

                for (int i = -SIZE_OF_RAIN_AREA / 2; i < SIZE_OF_RAIN_AREA / 2; i++) {
                    for (int j = -SIZE_OF_RAIN_AREA / 2; j < SIZE_OF_RAIN_AREA / 2; j++) {
                        Vector3f loc_emitter = new Vector3f(baseLoc.x, baseLoc.y, baseLoc.z);
                        loc_emitter.add(i, (float) SIZE_OF_RAIN_AREA / 2, j);

                        EntityBuilder builder = entityManager.newBuilder("WeatherManager:rain");
                        builder.getComponent(LocationComponent.class).setWorldPosition(loc_emitter);
                        EntityRef ref = builder.build();
                        particleSpawners.put(new Vector2f(loc_emitter.x, loc_emitter.z), ref); // Spawn the particle emitter
                        logger.info("loc_emitter: "+loc_emitter);
                    }
                }
            }
        }
    }

    @ReceiveEvent
    public void onCharacterMoved(MovedEvent event, EntityRef character) {
        //player = character;

        Vector3f pos = round(new Vector3f(event.getPosition()));

        Vector2f posAsVect2 = new Vector2f(pos.x, pos.z);

        if (!posAsVect2.equals(center)) {
            Vector2f dif = center.sub(posAsVect2);
            logger.info("dif: "+dif);

            if (dif.x != 0) {
                makeNewEmitters(pos, true);
            } //TODO: test
            if (dif.y != 0) {
                makeNewEmitters(pos, false);
            }

            center = posAsVect2;
        }
        /*
        if (particleSpawners.containsKey(posAsVect2)) {
            logger.info("pos: "+pos);
        }*/

        //TODO: handle movement
    }

    private Vector3f round (Vector3f original) {
        original.x = Math.round(original.x);
        original.y = Math.round(original.y);
        original.z = Math.round(original.z);

        return original;
    }

    private void makeNewEmitters(Vector3f pos, boolean x) {

        for (int j = -SIZE_OF_RAIN_AREA / 2; j < SIZE_OF_RAIN_AREA / 2; j++) {
            Vector3f loc_emitter = new Vector3f(pos.x, pos.y, pos.z);
            if (x) {
                loc_emitter.add(Math.round((float) SIZE_OF_RAIN_AREA / 2), (float) SIZE_OF_RAIN_AREA / 2, j);
            } else {
                loc_emitter.add(j, (float) SIZE_OF_RAIN_AREA / 2, Math.round((float) SIZE_OF_RAIN_AREA / 2));
            }

            if (!particleSpawners.containsKey(new Vector2f(loc_emitter.x, loc_emitter.z))) {
                EntityBuilder builder = entityManager.newBuilder("WeatherManager:rain");
                builder.getComponent(LocationComponent.class).setWorldPosition(loc_emitter);
                EntityRef ref = builder.build();
                particleSpawners.put(new Vector2f(loc_emitter.x, loc_emitter.z), ref); // Spawn the particle emitter
            }
        }

        for (int j = -SIZE_OF_RAIN_AREA / 2; j < SIZE_OF_RAIN_AREA / 2; j++) {
            Vector2f loc_emitter = new Vector2f(pos.x, pos.z);
            if (x) {
                loc_emitter.add(j, -SIZE_OF_RAIN_AREA);
            } else {
                loc_emitter.add(-SIZE_OF_RAIN_AREA, j);
            }

            if (particleSpawners.containsKey(loc_emitter)) {
                logger.info("destroying");
                particleSpawners.get(loc_emitter).destroy();
            }
            //TODO: fix destruction
        }
    }
}
