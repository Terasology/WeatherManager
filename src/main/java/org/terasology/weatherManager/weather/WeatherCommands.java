package org.terasology.weatherManager.weather;

import org.terasology.entitySystem.systems.RegisterSystem;
import org.terasology.logic.console.commandSystem.annotations.Command;
import org.terasology.math.geom.Vector2f;
import org.terasology.weatherManager.systems.WeatherManagerSystem;
import org.terasology.weatherManager.weather.DownfallCondition.DownfallType;

@RegisterSystem
public class WeatherCommands {
    @Command(shortDescription = "Make it rain", helpText = "Changes the weather to raining for some time")
    public String makeRain() {
        //probably change this stuff later once I get the command itself to show up - I don't know if this will work as it
        WeatherCondition weatherCondition = new WeatherCondition(Severity.MODERATE, DownfallCondition.get(Severity.MODERATE,  DownfallType.RAIN, false),new Vector2f(0, 0));
        ConditionAndDuration conditionAndDuration = new ConditionAndDuration(weatherCondition, 10000);
        new WeatherManagerSystem().postBegin();
        return "it is now raining.";
    }
}
