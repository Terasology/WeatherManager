// Copyright 2021 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0
package org.terasology.weatherManager.weather;

import org.joml.Vector2f;
import org.joml.Vector2fc;

/**
 * Simple data class which describes in game weather condition
 */
public final class WeatherCondition {

    public final Severity cloudiness;
    public final DownfallCondition downfallCondition;
    public final Vector2fc wind;

    public WeatherCondition(final Severity cloudiness, final DownfallCondition downfallCondition, final Vector2fc wind) {
        this.cloudiness = cloudiness;
        this.downfallCondition = downfallCondition;
        this.wind = new Vector2f(wind);
    }

    @Override
    public String toString() {
        String string = cloudinessString();

        if (downfallCondition != DownfallCondition.NO_DOWNFALL) {
            string += " and " + downfallCondition.toString();
        }

        string += " with wind " + wind.toString();

        return string;
    }

    private String cloudinessString() {
        switch (cloudiness) {
            case NONE:
                return "Clear sky";

            case LIGHT:
                return "Lightly clouded";

            case MODERATE:
                return "Moderately clouded";

            case HEAVY:
                return "Densely clouded";

            default:
                throw new IllegalArgumentException("No case for " + cloudiness);
        }
    }

}
