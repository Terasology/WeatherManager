/*
 * Copyright 2014 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.terasology.weatherManager.weather;

import org.terasology.math.geom.Vector2f;

/**
 * Simple data class which describes in game weather condition
 * Created by Linus on 5-11-2014.
 */
public final class WeatherCondition {

    public final Severity cloudiness;
    public final DownfallCondition downfallCondition;
    public final Vector2f wind;

    public WeatherCondition(final Severity cloudiness, final DownfallCondition downfallCondition, final Vector2f wind) {
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
