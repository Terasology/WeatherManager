// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0
package org.terasology.weatherManager;

import org.junit.Test;
import org.terasology.weatherManager.systems.MarkovChainWeatherGenerator;

/**
 * Created by Linus on 5-11-2014.
 */
public class WeatherConditionTest {

    @Test
    public void printNrOfDownfallConditions() {
        MarkovChainWeatherGenerator markovChainWeatherGenerator = new MarkovChainWeatherGenerator(3456, 6.0f);

        //warm up
        for (int i = 0; i < 100; i++) {
            System.out.println(i + ": " + markovChainWeatherGenerator.getNext());
        }
        /*
        System.out.println( WeatherCondition.values().length + " conditions:");
        for (WeatherCondition condition: WeatherCondition.values()) {
            System.out.println("\t" + condition + "(" + condition.likelihood() + ")");
        }
        */
    }
}
