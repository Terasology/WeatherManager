package org.terasology.weatherManager.systems;

import org.slf4j.LoggerFactory;
import org.terasology.engine.Time;
import org.terasology.entitySystem.entity.EntityManager;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.systems.BaseComponentSystem;
import org.terasology.entitySystem.systems.RegisterMode;
import org.terasology.entitySystem.systems.RegisterSystem;
import org.terasology.entitySystem.systems.UpdateSubscriberSystem;
import org.terasology.network.ClientComponent;
import org.terasology.registry.In;
import org.terasology.weatherManager.components.WeatherSensor;

import org.terasology.weatherManager.weather.ConditionAndDuration;
import org.terasology.world.time.WorldTime;

@RegisterSystem(RegisterMode.AUTHORITY)
public class WeatherManagerSystem extends BaseComponentSystem implements UpdateSubscriberSystem {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(WeatherManagerSystem.class);

    private WeatherConditionProvider weatherConditionProvider = new MarkovChainWeatherGenerator(12354, WorldTime.DAY_LENGTH);

    private ConditionAndDuration current;
    private float timeOnCurrent;

    @In
    Time time;

    @In WorldTime worldTime;

    @In
    EntityManager entityManager;

    @Override
    public void initialise() {
        logger.info("Initializing WeatherManSystem");

        current = weatherConditionProvider.getNext();
        logger.info("Current weather :" + current.condition + " (" + current.duration + ")");
    }

    private void makeClientsSimulationCarriers() {
        Iterable<EntityRef> entities = entityManager.getEntitiesWith(ClientComponent.class);

        for(EntityRef entity: entities) {
            entity.addComponent(new WeatherSensor(true, false));
        }
    }

    float ds = 0.0f;

    @Override
    public void update(float delta) {
        timeOnCurrent += delta;
        ds+=delta;

        while(timeOnCurrent >= current.duration) {
            timeOnCurrent -= current.duration / WorldTime.DAY_LENGTH / 1000;
            current = weatherConditionProvider.getNext();
            logger.info("WEATHER CHANGED: " + current.condition + "(" + current.duration + ")");
        }

        if(ds > 2.0f) {
            ds = 0;
            logger.info("timeUntil next change: " + (current.duration - timeOnCurrent));
        }
    }
}
