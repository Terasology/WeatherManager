// Copyright 2021 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0
package org.terasology.weatherManager.weather;

/**
 * Simple data class which contains the WeatherCondition and its effective duration
 *
 * @see WeatherCondition Created by Linus on 8-11-2014.
 */
public final class ConditionAndDuration {
    public final WeatherCondition condition;
    public final float duration;

    public ConditionAndDuration(final WeatherCondition condition, final float duration) {
        this.condition = condition;
        this.duration = duration;
    }

    public String toString() {
        return condition.toString() + " (" + duration + ")";
    }
}
