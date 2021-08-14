// Copyright 2021 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0
package org.terasology.weatherManager.components;

import org.terasology.gestalt.entitysystem.component.Component;

/**
 * Created by Linus on 30-10-2014.
 */
public class WeatherSensor implements Component<WeatherSensor> {

    protected boolean canCarryWeather;
    protected boolean isStatic;

    public WeatherSensor(final boolean canCarryWeather, final boolean isStatic) {
        this.canCarryWeather = canCarryWeather;
        this.isStatic = isStatic;
    }

    public boolean getIsCanCarryWeather() {
        return canCarryWeather;
    }

    public boolean getIsStatic() {
        return this.isStatic;
    }

    @Override
    public void copyFrom(WeatherSensor other) {
        this.canCarryWeather = other.canCarryWeather;
        this.isStatic = other.isStatic;
    }
}
