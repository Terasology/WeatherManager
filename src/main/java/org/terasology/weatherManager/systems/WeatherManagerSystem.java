// Copyright 2021 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.weatherManager.systems;
import com.google.common.math.DoubleMath;
import com.google.common.base.Function;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector3i;
import org.joml.Vector3ic;
import java.util.List;
import java.util.ArrayList;
import org.slf4j.LoggerFactory;
import org.terasology.engine.context.Context;
import org.terasology.engine.network.NetworkSystem;
import org.terasology.engine.entitySystem.entity.EntityManager;
import org.terasology.engine.entitySystem.entity.EntityRef;
import org.terasology.engine.logic.delay.PeriodicActionTriggeredEvent;
import org.terasology.engine.entitySystem.systems.BaseComponentSystem;
import org.terasology.engine.entitySystem.systems.RegisterMode;
import org.terasology.engine.entitySystem.systems.RegisterSystem;
import org.terasology.engine.logic.console.commandSystem.annotations.Command;
import org.terasology.engine.logic.console.commandSystem.annotations.CommandParam;
import org.terasology.engine.logic.delay.DelayManager;
import org.terasology.engine.network.Client;
import org.terasology.engine.logic.location.LocationComponent;
import org.terasology.engine.logic.delay.DelayedActionTriggeredEvent;
import org.terasology.engine.logic.players.event.LocalPlayerInitializedEvent;
import org.terasology.engine.registry.In;
import org.terasology.engine.registry.Share;
import org.terasology.engine.world.time.WorldTime;
import org.terasology.gestalt.entitysystem.event.ReceiveEvent;
import org.terasology.weatherManager.components.WeatherBase;
import org.terasology.weatherManager.events.StartHailEvent;
import org.terasology.weatherManager.events.StartRainEvent;
import org.terasology.weatherManager.events.StartSnowEvent;
import org.terasology.weatherManager.events.StartSunEvent;
import org.terasology.weatherManager.events.TemperatureIncreaseEvent;
import org.terasology.weatherManager.weather.ConditionAndDuration;
import org.terasology.weatherManager.weather.DownfallCondition;
import org.terasology.weatherManager.weather.Severity;
import org.terasology.weatherManager.weather.WeatherCondition;

import java.math.RoundingMode;
import java.util.Iterator;
import java.util.Random;

import org.terasology.climateConditions.ClimateConditionsSystem;
import org.terasology.climateConditions.ClimateConditionsSystem;


@RegisterSystem(RegisterMode.AUTHORITY)
@Share(WeatherManagerSystem.class)
public class WeatherManagerSystem extends BaseComponentSystem {

    public static final String PLACE_SNOW = "placeSnow";
    public static final String MELT_SNOW = "meltSnow";
    public static final String EVAPORATE_WATER = "evaporateWater";
    public static final String PLACE_WATER = "placeWater";
    public static final String TEMPERATURE_INCREASE = "temperatureIncrease";

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(WeatherManagerSystem.class);

    private Vector2f currentWind = new Vector2f();
    private Severity severity;

    private DownfallCondition.DownfallType currentWeather;

    private WeatherConditionProvider weatherConditionProvider;
    private float currentTemperature;

    private ConditionAndDuration current;
    private EntityRef weatherEntity;

    @In
    private EntityManager entityManager;

    @In
    private DelayManager delayManager;

    @In
    private WorldTime worldTime;

    @In
    private ClimateConditionsSystem climateConditionsSystem;

    @In
    private Context context;
    private int countTempAug = 0;
    private NetworkSystem networkSystem;


    @Command(shortDescription = "Print Massage", helpText = "Equivalent to a println but in the chat")
    public String printMessage(@CommandParam(value = "text") String text){
        return this.countTempAug+"";
    }

    @Command(shortDescription = "Make it rain", helpText = "Changes the weather to raining for some time")
    public String makeRain(@CommandParam(value = "time") int time) {
        float windX = randomWindSpeed();
        float windY = randomWindSpeed();

        DownfallCondition condition = DownfallCondition.get(Severity.MODERATE, DownfallCondition.DownfallType.RAIN, false);
        WeatherCondition weatherCondition = new WeatherCondition(Severity.MODERATE, condition, new Vector2f(windX, windY));
        ConditionAndDuration conditionAndDuration = new ConditionAndDuration(weatherCondition, time);
        changeWeather(conditionAndDuration);
        return "It is now raining.";
    }

    @Command(shortDescription = "Make it snow", helpText = "Changes the weather to snowing for some time")
    public String makeSnow(@CommandParam(value = "time") int time) {
        float windX = randomWindSpeed();
        float windY = randomWindSpeed();

        DownfallCondition condition = DownfallCondition.get(Severity.MODERATE, DownfallCondition.DownfallType.SNOW, false);
        WeatherCondition weatherCondition = new WeatherCondition(Severity.MODERATE, condition, new Vector2f(windX, windY));
        ConditionAndDuration conditionAndDuration = new ConditionAndDuration(weatherCondition, time);
        changeWeather(conditionAndDuration);
        this.changeTemperaturePlayers();
        delayManager.addPeriodicAction(weatherEntity, TEMPERATURE_INCREASE, 10, 100);
        return "It is now snowing." + this.currentTemperature;
    }

    @Command(shortDescription = "Make it hail", helpText = "Changes the weather to hailing for some time")
    public String makeHail(@CommandParam(value = "time") int time) {
        float windX = randomWindSpeed();
        float windY = randomWindSpeed();
        DownfallCondition condition = DownfallCondition.get(Severity.MODERATE, DownfallCondition.DownfallType.HAIL, false);
        WeatherCondition weatherCondition = new WeatherCondition(Severity.MODERATE, condition, new Vector2f(windX, windY));
        ConditionAndDuration conditionAndDuration = new ConditionAndDuration(weatherCondition, time);
        changeWeather(conditionAndDuration);
        return "It is now hailing.";
    }

    @Command(shortDescription = "Make it sunny", helpText = "Changes the weather to sunny for some time")
    public String makeSunny(@CommandParam(value = "time") int time) {
        DownfallCondition condition = DownfallCondition.get(Severity.NONE, DownfallCondition.DownfallType.NONE, false);
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

    @ReceiveEvent
    public void changeTemperatureEvent(PeriodicActionTriggeredEvent event, EntityRef worldEntity) {
        if (event.getActionId().equals(TEMPERATURE_INCREASE)) {
            //we create a temperature event to set the temperature of the environment and we set the temperature
            new TemperatureIncreaseEvent();
            this.changeTemperaturePlayers();
        }
    }

    @ReceiveEvent
    public void reduceTemperature(StartSnowEvent event, EntityRef worldEntity) {
//        Function<Float, Float> function = (Float number) -> {
//            return (float) (number - 0.5 * Math.random());
//        };
//        this.climateConditionsSystem.configureTemperature(0, 100, 0, function,
//                -10, 30);
    }

    @ReceiveEvent
    public void increaseTemperature(StartSnowEvent event, EntityRef worldEntity) {
        float temperature = this.currentTemperature +0.1f;
        Function<Float, Float> function = (Float number) -> {
            return (float) (temperature);
        };
        this.climateConditionsSystem.configureTemperature(0, 200, 0, function,
                -10, 30);
        this.changeTemperaturePlayers();

    }

    @Override
    public void postBegin() {
        networkSystem = context.get(NetworkSystem.class);
        float avglength = WorldTime.DAY_LENGTH / 480.0f; // / 48.0f; // worldTime.getTimeRate(); -- not available for modules
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
     *
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
                this.changeTemperaturePlayers();
            }

            if (currentWeather.equals(DownfallCondition.DownfallType.NONE)) {
                delayManager.addPeriodicAction(weatherEntity, MELT_SNOW, 10, 10);
                delayManager.addPeriodicAction(weatherEntity, EVAPORATE_WATER, 10, 10);
            }

            if (currentWeather.equals(DownfallCondition.DownfallType.RAIN)) {
                delayManager.addPeriodicAction(weatherEntity, MELT_SNOW, 150, 300);
                delayManager.addPeriodicAction(weatherEntity, PLACE_WATER, 1000, 10000);
            }
        }

        if (currentWeather.equals(DownfallCondition.DownfallType.SNOW)) {
            weatherEntity.send(new StartSnowEvent());
        }

        if (currentWeather.equals(DownfallCondition.DownfallType.NONE)) {
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

    public void changeTemperaturePlayers() {
        this.countTempAug=100;
        List<Vector3fc> playerPos = this.getPlayersPosition();
        float currentTemp = 0;
        for(Vector3fc players : playerPos){
            currentTemp += this.climateConditionsSystem.getTemperature(players.x(), players.y(), players.z());
        }
        this.currentTemperature = currentTemp/playerPos.size();
    }

    public float randomWindSpeed() {
        Random rand = new Random();
        return (float) Math.random() / (rand.nextInt(21) - 10);
    }

    public List<Vector3fc> getPlayersPosition() {
        final Vector3f position = new Vector3f();
        final Vector3f playerPos = new Vector3f();
        List<Vector3fc> listPlayerPos = new ArrayList<Vector3fc>();

        for (Client currentPlayer : networkSystem.getPlayers()) {
            LocationComponent locComp = currentPlayer.getEntity().getComponent(LocationComponent.class);
            playerPos.set(locComp.getWorldPosition(position));
            listPlayerPos.add(playerPos);
        }
        return listPlayerPos;
    }
    public void currentWeather(){
        float currentHumidityDegree = this.climateConditionsSystem.getHumidity(this.getPlayersPosition()) / this.climateConditionsSystem.humidityMaximum ;
        Random rand = new Random();
        boolean withThunder = rand.nextInt(2)==0?false:true ;
        Severity severity = withThunder==false?Severity.MODERATE: Severity.HEAVY;

        if(currentHumidityDegree>0.7){
            if(this.currentTemperature>0){
                DownfallCondition condition = DownfallCondition.get(severity, DownfallCondition.DownfallType.RAIN, withThunder);
                WeatherCondition weatherCondition = new WeatherCondition(severity, condition, new Vector2f(windX, windY));
                this.current.condition = weatherCondition ;
            }
            else if(this.currentTemperature>-10 && this.currentTemperature<=0){
                DownfallCondition condition = DownfallCondition.get(severity, DownfallCondition.DownfallType.SNOW, withThunder);
                WeatherCondition weatherCondition = new WeatherCondition(severity, condition, new Vector2f(windX, windY));
                this.current.condition = weatherCondition ;

            }
            else{
                DownfallCondition condition = DownfallCondition.get(severity, DownfallCondition.DownfallType.HAIL, withThunder);
                WeatherCondition weatherCondition = new WeatherCondition(severity, condition, new Vector2f(windX, windY));
                this.current.condition = weatherCondition ;
            }
        }
        if(currentHumidityDegree<=0.7 && currentHumidityDegree>0.5 ){
            if(this.currentTemperature>0){
                DownfallCondition condition = DownfallCondition.get(Severity.LIGHT, DownfallCondition.DownfallType.RAIN, false);
                WeatherCondition weatherCondition = new WeatherCondition(Severity.LIGHT, condition, new Vector2f(windX, windY));
                this.current.condition = weatherCondition ;
            }
            else if(this.currentTemperature>-10 && this.currentTemperature<=0){
                DownfallCondition condition = DownfallCondition.get(Severity.LIGHT, DownfallCondition.DownfallType.SNOW, false);
                WeatherCondition weatherCondition = new WeatherCondition(Severity.LIGHT, condition, new Vector2f(windX, windY));
                this.current.condition = weatherCondition ;
            }
            else{
                DownfallCondition condition = DownfallCondition.get(Severity.LIGHT, DownfallCondition.DownfallType.HAIL, false);
                WeatherCondition weatherCondition = new WeatherCondition(Severity.LIGHT, condition, new Vector2f(windX, windY));
                this.current.condition = weatherCondition ;
            }
        }
        if(currentHumidityDegree<=0.5){
            DownfallCondition condition = DownfallCondition.get(Severity.NONE, DownfallCondition.DownfallType.NONE, false);
            WeatherCondition weatherCondition = new WeatherCondition(Severity.NONE, condition, new Vector2f(windX, windY));
            this.current.condition = weatherCondition ;
        }

    }
}