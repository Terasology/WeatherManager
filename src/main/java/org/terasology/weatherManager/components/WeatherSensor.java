// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0
package org.terasology.weatherManager.components;

import org.terasology.engine.entitySystem.Component;

/**
 * Created by Linus on 30-10-2014.
 */
public class WeatherSensor implements Component {

    public final boolean canCarryWeather;
    public final boolean isStatic;
    public WeatherSensor(final boolean canCarryWeather, final boolean isStatic) {
        this.canCarryWeather = canCarryWeather;
        this.isStatic = isStatic;
    }
}
