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
import org.joml.Vector2f;
import org.slf4j.LoggerFactory;
import org.terasology.entitySystem.entity.EntityManager;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.event.ReceiveEvent;
import org.terasology.entitySystem.systems.BaseComponentSystem;
import org.terasology.entitySystem.systems.RegisterMode;
import org.terasology.entitySystem.systems.RegisterSystem;
import org.terasology.logic.console.commandSystem.annotations.Command;
import org.terasology.logic.console.commandSystem.annotations.CommandParam;
import org.terasology.logic.delay.DelayManager;
import org.terasology.logic.delay.DelayedActionTriggeredEvent;
import org.terasology.logic.players.event.LocalPlayerInitializedEvent;
import org.terasology.registry.In;
import org.terasology.registry.Share;
import org.terasology.weatherManager.components.WeatherBase;
import org.terasology.weatherManager.events.StartHailEvent;
import org.terasology.weatherManager.events.StartRainEvent;
import org.terasology.weatherManager.events.StartSnowEvent;
import org.terasology.weatherManager.events.StartSunEvent;
import org.terasology.weatherManager.weather.ConditionAndDuration;
import org.terasology.weatherManager.weather.DownfallCondition;
import org.terasology.weatherManager.weather.Severity;
import org.terasology.weatherManager.weather.WeatherCondition;
import org.terasology.world.time.WorldTime;

import java.math.RoundingMode;
import java.util.Iterator;
import java.util.Random;

@RegisterSystem(RegisterMode.AUTHORITY)
@Share(WeatherManagerSystem.class)
public class WeatherManagerSystem extends BaseComponentSystem {

    public static final String PLACE_SNOW = "placeSnow";
    public static final String MELT_SNOW = "meltSnow";
    public static final String EVAPORATE_WATER = "evaporateWater";
    public static final String PLACE_WATER = "placeWater";

    private Vector2f currentWind = new Vector2f();
    private Severity severity;

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(WeatherManagerSystem.class);

    private DownfallCondition.DownfallType currentWeather;

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
        float windX = randomWindSpeed();
        float windY = randomWindSpeed();

        DownfallCondition condition = DownfallCondition.get(Severity.MODERATE,  DownfallCondition.DownfallType.RAIN, false);
        WeatherCondition weatherCondition = new WeatherCondition(Severity.MODERATE, condition, new Vector2f(windX, windY));
        ConditionAndDuration conditionAndDuration = new ConditionAndDuration(weatherCondition, time);
        changeWeather(conditionAndDuration);
        return "It is now raining.";
    }

    @Command(shortDescription = "Make it snow", helpText = "Changes the weather to snowing for some time")
    public String makeSnow(@CommandParam(value = "time") int time) {
        float windX = randomWindSpeed();
        float windY = randomWindSpeed();

        DownfallCondition condition = DownfallCondition.get(Severity.MODERATE,  DownfallCondition.DownfallType.SNOW, false);
        WeatherCondition weatherCondition = new WeatherCondition(Severity.MODERATE, condition, new Vector2f(windX, windY));
        ConditionAndDuration conditionAndDuration = new ConditionAndDuration(weatherCondition, time);
        changeWeather(conditionAndDuration);
        return "It is now snowing.";
    }

    @Command(shortDescription = "Make it hail", helpText = "Changes the weather to hailing for some time")
    public String makeHail(@CommandParam(value = "time") int time) {
        float windX = randomWindSpeed();
        float windY = randomWindSpeed();

        DownfallCondition condition = DownfallCondition.get(Severity.MODERATE,  DownfallCondition.DownfallType.HAIL, false);
        WeatherCondition weatherCondition = new WeatherCondition(Severity.MODERATE, condition, new Vector2f(windX, windY));
        ConditionAndDuration conditionAndDuration = new ConditionAndDuration(weatherCondition, time);
        changeWeather(conditionAndDuration);
        return "It is now hailing.";
    }

    @Command(shortDescription = "Make it sunny", helpText = "Changes the weather to sunny for some time")
    public String makeSunny(@CommandParam(value = "time") int time) {
        DownfallCondition condition = DownfallCondition.get(Severity.NONE,  DownfallCondition.DownfallType.NONE, false);
        WeatherCondition weatherCondition = new WeatherCondition(Severity.NONE, condition, new Vector2f(0, 0));
        ConditionAndDuration conditionAndDuration = new ConditionAndDuration(weatherCondition, time);
        changeWeather(conditionAndDuration);
        return "It is now sunny.";
    }

    @ReceiveEvent
    public void onLocalPlayerReady(LocalPlayerInitializedEvent event, EntityRef entity) {
        long length = DoubleMath.roundToLong(current.duration, RoundingMode.HALF_UP);
        delayManager.addDelayedAction(weatherEntity, "RandomWeather", length);
        changeWeather(current);
    }

    @Override
    public void postBegin() {
        float avglength = WorldTime.DAY_LENGTH / 480.0f;// / 48.0f; // worldTime.getTimeRate(); -- not available for modules
        weatherConditionProvider = new MarkovChainWeatherGenerator(12354, avglength);
        current = weatherConditionProvider.getNext();

        boolean weatherEntityFound = false;

        Iterator weatherEntityIter = entityManager.getEntitiesWith(WeatherBase.class).iterator();
        if (weatherEntityIter.hasNext()) {
            weatherEntity = (EntityRef) weatherEntityIter.next();
        } else {
            weatherEntity = entityManager.create();
            weatherEntity.addComponent(new WeatherBase());
        }

        changeWeather(current);
    }

    /**
     * For changing weather on command.
     * @param conditionAndDuration The ConditionAndDuration for the new weather.
     */
    private void changeWeather(ConditionAndDuration conditionAndDuration) {
        current = conditionAndDuration;
        long length = DoubleMath.roundToLong(current.duration, RoundingMode.HALF_UP);

        if (delayManager != null && weatherEntity != null && !delayManager.hasDelayedAction(weatherEntity, "Weather")) {
            delayManager.addDelayedAction(weatherEntity, "Weather", length);
        }

        currentWeather = current.condition.downfallCondition.getDownfallValues().type;
        severity = current.condition.downfallCondition.getDownfallValues().amount;
        currentWind.set(current.condition.wind);
        triggerEvents();
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
        if (event.getActionId().equals("RandomWeather")) {
            current = weatherConditionProvider.getNext();
            currentWeather = current.condition.downfallCondition.getDownfallValues().type;
            severity = current.condition.downfallCondition.getDownfallValues().amount;
            currentWind.set(current.condition.wind);
            triggerEvents();
            logger.debug("WEATHER CHANGED: " + current.condition + "(" + current.duration + ")");
        }
    }

    /**
     * Adds/removes periodic actions and sends events based on the type of weather it currently is.
     */
    private void triggerEvents() {
        if (delayManager != null && weatherEntity != null) {
            if (delayManager.hasPeriodicAction(weatherEntity, MELT_SNOW)) {
                delayManager.cancelPeriodicAction(weatherEntity, MELT_SNOW);
            }
            if (delayManager.hasPeriodicAction(weatherEntity, EVAPORATE_WATER)) {
                delayManager.cancelPeriodicAction(weatherEntity, EVAPORATE_WATER);
            }
            if (delayManager.hasPeriodicAction(weatherEntity, PLACE_SNOW)) {
                delayManager.cancelPeriodicAction(weatherEntity, PLACE_SNOW);
            }
            if (delayManager.hasPeriodicAction(weatherEntity, PLACE_WATER)) {
                delayManager.cancelPeriodicAction(weatherEntity, PLACE_WATER);
            }

            if (currentWeather.equals(DownfallCondition.DownfallType.SNOW)) {
                delayManager.addPeriodicAction(weatherEntity, PLACE_SNOW, 200, 400);
            }

            if (currentWeather.equals(DownfallCondition.DownfallType.NONE)){
                delayManager.addPeriodicAction(weatherEntity, MELT_SNOW, 150, 300);
                delayManager.addPeriodicAction(weatherEntity, EVAPORATE_WATER, 150, 300);
            }

            if (currentWeather.equals(DownfallCondition.DownfallType.RAIN)) {
                delayManager.addPeriodicAction(weatherEntity, MELT_SNOW, 150, 300);
                delayManager.addPeriodicAction(weatherEntity, PLACE_WATER, 1000, 10000);
            }
        }

        if (currentWeather.equals(DownfallCondition.DownfallType.SNOW)) {
            weatherEntity.send(new StartSnowEvent());
        }

        if (currentWeather.equals(DownfallCondition.DownfallType.NONE)){
            weatherEntity.send(new StartSunEvent());
        }

        if (currentWeather.equals(DownfallCondition.DownfallType.RAIN)) {
            weatherEntity.send(new StartRainEvent());
        }

        if (currentWeather.equals(DownfallCondition.DownfallType.HAIL)) {
            weatherEntity.send(new StartHailEvent());
        }
    }

    /**
     * Removes the periodic actions before saving, in case the game will be closed out.
     */
    @Override
    public void preSave() {
        if (delayManager.hasPeriodicAction(weatherEntity, PLACE_SNOW)) {
            delayManager.cancelPeriodicAction(weatherEntity, PLACE_SNOW);
        }

        if (delayManager.hasPeriodicAction(weatherEntity, MELT_SNOW)) {
            delayManager.cancelPeriodicAction(weatherEntity, MELT_SNOW);
        }

        if (delayManager.hasPeriodicAction(weatherEntity, PLACE_WATER)) {
            delayManager.cancelPeriodicAction(weatherEntity, PLACE_WATER);
        }

        if (delayManager.hasPeriodicAction(weatherEntity, EVAPORATE_WATER)) {
            delayManager.cancelPeriodicAction(weatherEntity, EVAPORATE_WATER);
        }
    }

    @Override
    public void postSave() {
        triggerEvents();
    }

    public DownfallCondition.DownfallType getCurrentWeather() {
        return currentWeather;
    }
    public Vector2f getCurrentWind() {
        return currentWind;
    }
    public Severity getCurrentSeverity() {
        return severity;
    }

    public float randomWindSpeed() {
        Random rand = new Random();
        return (float) Math.random() / (rand.nextInt(21) - 10);
    }
}
