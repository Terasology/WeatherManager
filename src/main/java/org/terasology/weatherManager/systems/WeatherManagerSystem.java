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
import org.terasology.weatherManager.events.TemperatureDecreaseEvent;
import org.terasology.weatherManager.events.TemperatureStagnateEvent;
import org.terasology.weatherManager.weather.ConditionAndDuration;
import org.terasology.weatherManager.weather.DownfallCondition;
import org.terasology.weatherManager.weather.Severity;
import org.terasology.weatherManager.weather.WeatherCondition;
import org.terasology.engine.network.Client;
import org.terasology.engine.logic.location.LocationComponent;



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
    public static final String TEMPERATURE_DECREASE = "temperatureDecrease";
    public static final String TEMPERATURE_STAGNATE = "temperatureStagnate";
    public static final String DELAYED_TEMPERATURE_CHOICE = "delayTemp";

    public static final String HUMIDITY_INCREASE = "HumidityIncrease";

    public static final float TMAX = 50f;
    public static final float TMIN = -10f;
    public static final float TFREEZE = 0f;
    public static final float TEBULLITION = 20f;
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(WeatherManagerSystem.class);

    private Vector2f currentWind = new Vector2f();
    private Severity severity;

    private DownfallCondition.DownfallType currentWeather;

    private WeatherConditionProvider weatherConditionProvider;
    private float currentTemperature;
    private float currentHumidity;

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
    private double countTempAug = 0;
    private NetworkSystem networkSystem;

    @In
    private WorldProvider worldProvider;

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
        return "It is now snowing.";
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
        delayManager.addDelayedAction(weatherEntity, DELAYED_TEMPERATURE_CHOICE, 100);
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
        //Each time a delayedActionTriggeredEvent is trigger, we change the way the temperature change
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

            if (currentWeather.equals(DownfallCondition.DownfallType.NONE)) {
                delayManager.addPeriodicAction(weatherEntity, MELT_SNOW, 100, 100);
                delayManager.addPeriodicAction(weatherEntity, EVAPORATE_WATER, 100, 100);
            }

            if (currentWeather.equals(DownfallCondition.DownfallType.RAIN)) {
                delayManager.addPeriodicAction(weatherEntity, MELT_SNOW, 150, 300);
                delayManager.addPeriodicAction(weatherEntity, PLACE_WATER, 1000, 10000);
                increaseHumidity();
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

    public float randomWindSpeed() {
        Random rand = new Random();
        return (float) Math.random() / (rand.nextInt(21) - 10);
    }

    /**
     * The temperature is equal of the average temperature between the players
     */
    public void changeTemperaturePlayers() {
        List<Vector3fc> playerPos = this.getPlayersPosition();
        float currentTemp = 0;

        for (Vector3fc players : playerPos) {
            currentTemp += this.climateConditionsSystem.getTemperature(players.x(), players.y(), players.z());

        }
        this.currentTemperature = currentTemp / playerPos.size();
    }

    /**
     * Get a list of the position of all the players
     *
     * @return List<Vector3fc> listPlayers
     */
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

    public void curWeather() {
        Random rand = new Random();
        float windX = randomWindSpeed();
        float windY = randomWindSpeed();
        boolean withThunder = rand.nextInt(2) == 0 ? false : true;
        this.severity = withThunder == false ? Severity.MODERATE : Severity.HEAVY;
        for (Vector3fc position : this.getPlayersPosition()) {
            float currentHumidityDegree = this.climateConditionsSystem.getHumidity(position) / 100 ;
            if (currentHumidityDegree > 0.7) {
                if (this.currentTemperature > 0) {
                    DownfallCondition condition = DownfallCondition.get(this.severity, DownfallCondition.DownfallType.RAIN, withThunder);
                    WeatherCondition weatherCondition = new WeatherCondition(this.severity, condition, new Vector2f(windX, windY));
                    float time = this.current.duration ;
                    this.current = new ConditionAndDuration(weatherCondition,time);
                } else if (this.currentTemperature > -10 && this.currentTemperature <= 0) {
                    DownfallCondition condition = DownfallCondition.get(this.severity, DownfallCondition.DownfallType.SNOW, withThunder);
                    WeatherCondition weatherCondition = new WeatherCondition(this.severity, condition, new Vector2f(windX, windY));
                    float time = this.current.duration ;
                    this.current = new ConditionAndDuration(weatherCondition,time);

                } else {
                    DownfallCondition condition = DownfallCondition.get(this.severity, DownfallCondition.DownfallType.HAIL, withThunder);
                    WeatherCondition weatherCondition = new WeatherCondition(this.severity, condition, new Vector2f(windX, windY));
                    float time = this.current.duration ;
                    this.current = new ConditionAndDuration(weatherCondition,time);
                }
            }
            if (currentHumidityDegree <= 0.7 && currentHumidityDegree > 0.5) {
                this.severity = Severity.LIGHT ;
                if (this.currentTemperature > 0) {
                    DownfallCondition condition = DownfallCondition.get(this.severity, DownfallCondition.DownfallType.RAIN, false);
                    WeatherCondition weatherCondition = new WeatherCondition(this.severity, condition, new Vector2f(windX, windY));
                    float time = this.current.duration ;
                    this.current = new ConditionAndDuration(weatherCondition,time);
                } else if (this.currentTemperature > -10 && this.currentTemperature <= 0) {
                    DownfallCondition condition = DownfallCondition.get(this.severity, DownfallCondition.DownfallType.SNOW, false);
                    WeatherCondition weatherCondition = new WeatherCondition(this.severity, condition, new Vector2f(windX, windY));
                    float time = this.current.duration ;
                    this.current = new ConditionAndDuration(weatherCondition,time);
                } else {
                    DownfallCondition condition = DownfallCondition.get(this.severity, DownfallCondition.DownfallType.HAIL, false);
                    WeatherCondition weatherCondition = new WeatherCondition(this.severity, condition, new Vector2f(windX, windY));
                    float time = this.current.duration ;
                    this.current = new ConditionAndDuration(weatherCondition,time);
                }
            }
            if (currentHumidityDegree <= 0.5) {
                this.severity = Severity.NONE ;
                DownfallCondition condition = DownfallCondition.get(this.severity, DownfallCondition.DownfallType.NONE, false);
                WeatherCondition weatherCondition = new WeatherCondition(Severity.NONE, condition, new Vector2f(0, 0));
                float time = this.current.duration ;
                this.current = new ConditionAndDuration(weatherCondition,time);
            }
        }
    }

    @Command(shortDescription = "Print Message", helpText = "Equivalent to a println but in the chat")
    public String printMessage(@CommandParam(value = "text") String text) {
        return "this.currentTemperature = " + this.currentTemperature + "\n" + "Nombre de fois triggerEvent called : " + this.countTempAug
                + "\n" + "HasDelayedAction ? " + delayManager.hasDelayedAction(weatherEntity, "Weather");
    }


    /**
     * Don't change the current Temperature
     *
     * @param event
     * @param worldEntity
     */
    @ReceiveEvent
    public void stagnateTemperature(TemperatureStagnateEvent event, EntityRef worldEntity) {
        float temperature = this.currentTemperature;
        //T = (TMAX - TMIN)value + TMIN => value = (T - TMIN)/(TMAX -TMIN)
        float value = (temperature - TMIN) / (TMAX - TMIN);
        Function<Float, Float> function = (Float number) -> {
            return (float) (value);
        };
        //I fixed the seaLevel = maxLevel to be sure that the value given if temperatureBase ==> The variation
        //Of temperature can be negligee
        this.climateConditionsSystem.configureTemperature(200, 200, 0, function,
                TMIN, TMAX);
        this.changeTemperaturePlayers();
    }

    /**
     * Decrease the current Temperature by a number between 0 and 0.01
     *
     * @param event
     * @param worldEntity
     */
    @ReceiveEvent
    public void reduceTemperature(TemperatureDecreaseEvent event, EntityRef worldEntity) {
        float temperature = this.currentTemperature;
        //T = (TMAX - TMIN)value + TMIN => value = (T - TMIN)/(TMAX -TMIN)
        float randNbr = (float) (Math.random() * 0.01);
        float value = (temperature - TMIN - randNbr) / (TMAX - TMIN);
        Function<Float, Float> function = (Float number) -> {
            return (float) (value);
        };
        //I fixed the seaLevel = maxLevel to be sure that the value given if temperatureBase ==> The variation
        //Of temperature due the height can be negligee
        this.climateConditionsSystem.configureTemperature(200, 200, 0, function,
                TMIN, TMAX);
        this.changeTemperaturePlayers();
    }

    /**
     * Increase the current Temperature by a number between 0 and 0.01
     *
     * @param event
     * @param worldEntity
     */
    @ReceiveEvent
    public void increaseTemperature(TemperatureIncreaseEvent event, EntityRef worldEntity) {
        float temperature = this.currentTemperature;
        //T = (TMAX - TMIN)value + TMIN => value = (T - TMIN)/(TMAX -TMIN)
        float randNbr = (float) (Math.random() * 0.01);
        float value = (temperature - TMIN + randNbr) / (TMAX - TMIN);
        Function<Float, Float> function = (Float number) -> {
            return (float) (value);
        };
        //I fixed the seaLevel = maxLevel to be sure that the value given if temperatureBase ==> The variation
        //Of temperature can be negligee
        this.climateConditionsSystem.configureTemperature(200, 200, 0, function,
                TMIN, TMAX);
        this.changeTemperaturePlayers();

    }

    /**
     * This function create a Temperature Event each period.
     *
     * @param event
     * @param weatherEntity
     */
    @ReceiveEvent
    public void chooseTemperatureVariable(PeriodicActionTriggeredEvent event, EntityRef weatherEntity) {
        switch (event.getActionId()) {
            case TEMPERATURE_INCREASE:
                weatherEntity.send(new TemperatureIncreaseEvent());
                break;
            case TEMPERATURE_DECREASE:
                weatherEntity.send(new TemperatureDecreaseEvent());
                break;
            case TEMPERATURE_STAGNATE:
                weatherEntity.send(new TemperatureStagnateEvent());
                break;
        }
    }

    /**
     * This class will create a periodicAction which will either increase, decrease the temperature or make it stagnate
     * This class will create a periodicAction which will either increase, decrease the temperature or
     * make it stagnate
     *
     * @param event
     * @param weatherEntity
     */
    @ReceiveEvent
    public void chooseTemperature(DelayedActionTriggeredEvent event, EntityRef weatherEntity) {
        if (event.getActionId().equals(DELAYED_TEMPERATURE_CHOICE)) {
            //We cancel all periodic action
            if (delayManager.hasPeriodicAction(weatherEntity, TEMPERATURE_INCREASE)) {
                delayManager.cancelPeriodicAction(weatherEntity, TEMPERATURE_INCREASE);
            }
            if (delayManager.hasPeriodicAction(weatherEntity, TEMPERATURE_DECREASE)) {
                delayManager.cancelPeriodicAction(weatherEntity, TEMPERATURE_DECREASE);
            }
            if (delayManager.hasPeriodicAction(weatherEntity, TEMPERATURE_STAGNATE)) {
                delayManager.cancelPeriodicAction(weatherEntity, TEMPERATURE_STAGNATE);
            }
            this.countTempAug++;
            String[] tempChoices = {TEMPERATURE_STAGNATE, TEMPERATURE_INCREASE, TEMPERATURE_DECREASE};
            int indexChoice = (int) (Math.random() * 3);
            delayManager.addPeriodicAction(weatherEntity, tempChoices[indexChoice], 0, 100);
            //We add another delayed action to do this action again, over and over
            delayManager.addDelayedAction(weatherEntity, DELAYED_TEMPERATURE_CHOICE, 100000);
        }


      

    }

    /**
     * Having the snow melt when the temperature reaches 0 degree
     */
    public void startMelting(EntityRef weatherEntity) {
        if (this.currentTemperature >= 0.0) {
            delayManager.cancelPeriodicAction(weatherEntity, MELT_SNOW);
            delayManager.addPeriodicAction(weatherEntity, MELT_SNOW, 100, 100);
        }
        for (Client currentPlayer : networkSystem.getPlayers()) {
            Vector3i playerPos = new Vector3i();
            LocationComponent locComp = currentPlayer.getEntity().getComponent(LocationComponent.class);
            playerPos.set(locComp.getWorldPosition(position), RoundingMode.FLOOR);
            meltSnow(playerPos);
        }
    }

    private Vector3i findSpot(Block toCheck, int x, int z, int initialY) {
        int currentY = initialY + SNOW_BLOCK_RANGE;
        int iter = 0;
        while (iter < SNOW_BLOCK_RANGE * 2 && worldProvider.getBlock(x, currentY, z).equals(air)) {
            iter++;
            currentY--;
        }
        while (iter < SNOW_BLOCK_RANGE * 2 && !worldProvider.getBlock(x, currentY, z).equals(air)) {
            iter++;
            currentY++;
        }
        if (iter >= SNOW_BLOCK_RANGE * 2) {
            return null;
        }

        if (worldProvider.getSunlight(x, currentY, z) != Chunks.MAX_SUNLIGHT) {
            // The block isn't actually exposed to the weather.
            return null;
        }
        Block ground = worldProvider.getBlock(x, currentY - 1, z);
        if (ground.equals(toCheck)) {
            return new Vector3i(x, currentY - 1, z);
        } else if (toCheck.equals(air) && !ground.isPenetrable() && ground.isAttachmentAllowed()) {
            return new Vector3i(x, currentY, z);
        } else {
            return null;
        }
    }
    private void meltSnow(Vector3ic playerPos) {
        int x = getValueToPlaceBlock(playerPos.x());
        int z = getValueToPlaceBlock(playerPos.z());
        Vector3i spotToPlace = findSpot(snow, x, z, playerPos.y());
        if (spotToPlace != null) {
            worldProvider.setBlock(spotToPlace, water);
        }
    }
    public void increaseHumidity() {
        float humidity = this.currentHumidity;
        float humidityMin = climateConditionsSystem.getHumidityMinimum();
        float humidityMax = climateConditionsSystem.getHumidityMaximum();
        float randNbr = (float) (Math.random() * 0.01);
        float value = (humidity - humidityMin + randNbr) / (humidityMax - humidityMin);
        Function<Float, Float> function = (Float number) -> {
            return (float) (value);
        };

        this.climateConditionsSystem.configureHumidity(200, 200, 0, function,
                humidityMin, humidityMax);
    }
}

