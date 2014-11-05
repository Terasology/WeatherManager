package org.terasology.weatherManager.systems;

import org.slf4j.LoggerFactory;
import org.terasology.entitySystem.systems.BaseComponentSystem;
import org.terasology.entitySystem.systems.RegisterMode;
import org.terasology.entitySystem.systems.RegisterSystem;
import org.terasology.entitySystem.systems.UpdateSubscriberSystem;

@RegisterSystem(RegisterMode.AUTHORITY)
public class WeatherManagerSystem extends BaseComponentSystem implements UpdateSubscriberSystem {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(WeatherManagerSystem.class);;

    @Override
    public void initialise() {
        logger.info("Initializing WeatherManSystem");
    }

    @Override
    public void update(float delta) {

    }
}
