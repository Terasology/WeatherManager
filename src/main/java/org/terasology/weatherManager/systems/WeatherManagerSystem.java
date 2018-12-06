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

import com.google.common.math.DoubleMath;
import org.slf4j.LoggerFactory;
import org.terasology.entitySystem.entity.EntityManager;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.event.ReceiveEvent;
import org.terasology.entitySystem.systems.BaseComponentSystem;
import org.terasology.entitySystem.systems.RegisterSystem;
import org.terasology.logic.console.commandSystem.annotations.Command;
import org.terasology.logic.console.commandSystem.annotations.CommandParam;
import org.terasology.logic.delay.DelayManager;
import org.terasology.logic.delay.DelayedActionTriggeredEvent;
import org.terasology.math.geom.Vector2f;
import org.terasology.registry.In;
import org.terasology.utilities.random.Random;
import org.terasology.weatherManager.weather.ConditionAndDuration;
import org.terasology.weatherManager.weather.DownfallCondition;
import org.terasology.weatherManager.weather.Severity;
import org.terasology.weatherManager.weather.WeatherCondition;
import org.terasology.world.time.WorldTime;

import java.math.RoundingMode;

@RegisterSystem
public class WeatherManagerSystem extends BaseComponentSystem {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(WeatherManagerSystem.class);

    private WeatherConditionProvider weatherConditionProvider;

    private ConditionAndDuration current;
    private EntityRef weatherEntity;

    @In
    private EntityManager entityManager;

    @In
    private DelayManager delayManager;

    @In
    private WorldTime worldTime;

    @Command(shortDescription = "Make it rain", helpText = "Changes the weather to raining for some time")
    public String makeRain(@CommandParam(value = "time") int time) {
        float windX = (float) Math.random() / (int)(Math.random() * 10);
        float windY = (float) Math.random() / (int)(Math.random() * 10);
        if (Math.random() > .5) {
            windX *= -1;
        }
        if (Math.random() > .5) {
            windY *= -1;
        }
        DownfallCondition condition = DownfallCondition.get(Severity.MODERATE,  DownfallCondition.DownfallType.RAIN, false);
        WeatherCondition weatherCondition = new WeatherCondition(Severity.MODERATE, condition, new Vector2f(windX, windY));
        ConditionAndDuration conditionAndDuration = new ConditionAndDuration(weatherCondition, time);
        changeWeather(conditionAndDuration);
        return "It is now raining.";
    }

    @Command(shortDescription = "Make it snow", helpText = "Changes the weather to snowing for some time")
    public String makeSnow(@CommandParam(value = "time") int time) {
        float windX = (float) Math.random() / (int)(Math.random() * 10);
        float windY = (float) Math.random() / (int)(Math.random() * 10);
        if (Math.random() > .5) {
            windX *= -1;
        }
        if (Math.random() > .5) {
            windY *= -1;
        }
        DownfallCondition condition = DownfallCondition.get(Severity.MODERATE,  DownfallCondition.DownfallType.SNOW, false);
        WeatherCondition weatherCondition = new WeatherCondition(Severity.MODERATE, condition, new Vector2f(windX, windY));
        ConditionAndDuration conditionAndDuration = new ConditionAndDuration(weatherCondition, time);
        changeWeather(conditionAndDuration);
        return "It is now snowing.";
    }

    @Command(shortDescription = "Make it hail", helpText = "Changes the weather to hailing for some time")
    public String makeHail(@CommandParam(value = "time") int time) {
        float windX = (float) Math.random() / (int)(Math.random() * 10);
        float windY = (float) Math.random() / (int)(Math.random() * 10);
        if (Math.random() > .5) {
            windX *= -1;
        }
        if (Math.random() > .5) {
            windY *= -1;
        }
        DownfallCondition condition = DownfallCondition.get(Severity.MODERATE,  DownfallCondition.DownfallType.HAIL, false);
        WeatherCondition weatherCondition = new WeatherCondition(Severity.MODERATE, condition, new Vector2f(windX, windY));
        ConditionAndDuration conditionAndDuration = new ConditionAndDuration(weatherCondition, time);
        changeWeather(conditionAndDuration);
        return "It is now hailing.";
    }

    @Command(shortDescription = "Make it sunny", helpText = "Changes the weather to sunny for some time")
    public String makeSunny(@CommandParam(value = "time") int time) {
        DownfallCondition condition = DownfallCondition.get(Severity.NONE,  DownfallCondition.DownfallType.NONE, false);
        WeatherCondition weatherCondition = new WeatherCondition(Severity.NONE, condition, Vector2f.zero());
        ConditionAndDuration conditionAndDuration = new ConditionAndDuration(weatherCondition, time);
        changeWeather(conditionAndDuration);
        return "It is now sunny.";
    }

    @Override
    public void postBegin() {

        float avglength = WorldTime.DAY_LENGTH / 480.0f;// / 48.0f; // worldTime.getTimeRate(); -- not available for modules
        weatherConditionProvider = new MarkovChainWeatherGenerator(12354, avglength);
        current = weatherConditionProvider.getNext();

        weatherEntity = entityManager.create();

        long length = DoubleMath.roundToLong(current.duration, RoundingMode.HALF_UP);
        delayManager.addDelayedAction(weatherEntity, "Weather", length);
    }

    /**
     * For changing weather on command.
     * @param conditionAndDuration The ConditionAndDuration for the new weather.
     */
    public void changeWeather(ConditionAndDuration conditionAndDuration) {
        current = conditionAndDuration;
        long length = DoubleMath.roundToLong(current.duration, RoundingMode.HALF_UP);
        delayManager.addDelayedAction(weatherEntity, "Weather", length);
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
