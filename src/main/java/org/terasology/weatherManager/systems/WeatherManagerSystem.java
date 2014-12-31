/*
 * Copyright 2014 MovingBlocks
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

import java.math.RoundingMode;

import org.slf4j.LoggerFactory;
import org.terasology.entitySystem.entity.EntityManager;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.event.ReceiveEvent;
import org.terasology.entitySystem.systems.BaseComponentSystem;
import org.terasology.entitySystem.systems.RegisterMode;
import org.terasology.entitySystem.systems.RegisterSystem;
import org.terasology.logic.delay.DelayManager;
import org.terasology.logic.delay.DelayedActionTriggeredEvent;
import org.terasology.registry.In;
import org.terasology.weatherManager.weather.ConditionAndDuration;
import org.terasology.world.time.WorldTime;

import com.google.common.math.DoubleMath;

@RegisterSystem(RegisterMode.AUTHORITY)
public class WeatherManagerSystem extends BaseComponentSystem {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(WeatherManagerSystem.class);

    private WeatherConditionProvider weatherConditionProvider;

    private ConditionAndDuration current;

    @In
    private EntityManager entityManager;

    @In
    private DelayManager delayManager;

    @In
    private WorldTime worldTime;


    @Override
    public void postBegin() {
        logger.info("Initializing WeatherManSystem");

        float avglength = WorldTime.DAY_LENGTH / 48.0f; // worldTime.getTimeRate(); -- not available for modules
        weatherConditionProvider = new MarkovChainWeatherGenerator(12354, avglength);
        current = weatherConditionProvider.getNext();

        EntityRef weatherEntity = entityManager.create();

        long length = DoubleMath.roundToLong(current.duration, RoundingMode.HALF_UP);
        delayManager.addDelayedAction(weatherEntity, "Weather", length);

        logger.info("Current weather: " + current.condition + " (" + current.duration + ")");
    }

//    private void makeClientsSimulationCarriers() {
//        Iterable<EntityRef> entities = entityManager.getEntitiesWith(ClientComponent.class);
//
//        for(EntityRef entity: entities) {
//            entity.addComponent(new WeatherSensor(true, false));
//        }
//    }

    @ReceiveEvent
    public void onTimeEvent(DelayedActionTriggeredEvent event, EntityRef worldEntity) {

        current = weatherConditionProvider.getNext();

        logger.info("WEATHER CHANGED: " + current.condition + "(" + current.duration + ")");

    }
}
