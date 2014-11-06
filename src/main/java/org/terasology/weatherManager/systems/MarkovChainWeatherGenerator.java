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
package org.terasology.weatherManager.systems;

import org.terasology.math.Vector2i;
import org.terasology.rendering.nui.properties.OneOf;
import org.terasology.weatherManager.MarkovChain;
import org.terasology.weatherManager.weather.WeatherCondition;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by Linus on 5-11-2014.
 */
public class MarkovChainWeatherGenerator implements WeatherConditionProvider {

    private final MarkovChain<WeatherCondition> weatherMarkovChain;

    private static final float[][][] FIRST_ORDER_CONDITION_TRANSITION_MATRIX =
            generateFirstOrderConditionTransitionMatrix();

    private static float[][][] generateFirstOrderConditionTransitionMatrix() {
        final WeatherCondition[] stateList = WeatherCondition.values();
        final int nrOfStates = WeatherCondition.values().length;


        float[][][] transitionMatrix = new float[nrOfStates][nrOfStates][nrOfStates];


        for (int previous = 0; previous < nrOfStates; previous++) {
            final WeatherCondition previousState = stateList[previous];
            for (int current = 0; current < nrOfStates; current++) {
                final WeatherCondition currentState = stateList[current];
                for (int next = 0; next < nrOfStates; next++) {
                    final WeatherCondition nextState = stateList[next];
                    switch (Math.abs(transitionDelta(currentState, nextState))) {
                        case 1:
                            transitionMatrix[previous][current][next] = 1.0f * nextState.likelihood();
                            break;

                        case 2:
                            transitionMatrix[previous][current][next] = 0.6f * nextState.likelihood();
                            break;

                        case 3:
                            transitionMatrix[previous][current][next] = 0.3f * nextState.likelihood();
                            break;
                    }

                    if(!isMonotonic(previousState, currentState, nextState)) {
                        transitionMatrix[previous][current][next] *= 0.3f;
                    }

                    if(currentState.downfallCondition.type == WeatherCondition.DownfallCondition.Type.NONE &&
                       nextState.downfallCondition.type != WeatherCondition.DownfallCondition.Type.NONE
                    ) {
                        transitionMatrix[previous][current][next] *= 0.3f; //Because we don't like constant rain
                    }
                    if(toInt(currentState.downfallCondition.amount) < toInt(nextState.downfallCondition.amount)) {
                        transitionMatrix[previous][current][next] *= 1.8f; //Because we don't like constant rain
                    }
                }
            }
        }

        return transitionMatrix;
    }

    private static boolean isMonotonic(final WeatherCondition previous, final WeatherCondition current, final WeatherCondition next) {
        return isMonotonic(previous.downfallCondition.amount, current.downfallCondition.amount, next.downfallCondition.amount) &&
               isMonotonic(previous.cloudiness, current.cloudiness, next.cloudiness) &&
               !(previous.downfallCondition.withThunder && !current.downfallCondition.withThunder && next.downfallCondition.withThunder);
    }

    private static boolean isMonotonic(final WeatherCondition.Severity previous, final WeatherCondition.Severity current, final WeatherCondition.Severity next) {
        return toInt(previous) <= toInt(current) && toInt(current) <= toInt(next) ||
               toInt(previous) >= toInt(current) && toInt(current) >= toInt(next);
    }

    private static int transitionDelta(final WeatherCondition from, final WeatherCondition to) {
        return  Math.abs(difference(from.cloudiness, to.cloudiness)) +
                Math.abs(difference(from.downfallCondition.amount, to.downfallCondition.amount)) +
                ((from.downfallCondition.type != to.downfallCondition.type &&
                        from.downfallCondition.type != WeatherCondition.DownfallCondition.Type.NONE &&
                        to.downfallCondition.type != WeatherCondition.DownfallCondition.Type.NONE) ? 2 : 0) +
                ((from.downfallCondition.withThunder != to.downfallCondition.withThunder) ? 2 : 0);

    }

    private static int transitionDelta(final WeatherCondition.DownfallCondition from, final WeatherCondition.DownfallCondition to) {
        int delta = difference(from.amount, to.amount);

        if(from.amount != WeatherCondition.Severity.NONE && to.amount != WeatherCondition.Severity.NONE &&
                from.type != to.type) {
            delta += 2;
        }

        return delta;
    }

    private static int difference(final WeatherCondition.Severity left, final WeatherCondition.Severity right) {
        return toInt(left) - toInt(right);
    }

    private static int toInt(final WeatherCondition.Severity severity) {
        switch (severity) {
            case NONE:
                return 0;
            case LIGHT:
                return 1;
            case MODERATE:
                return 2;
            case HEAVY:
                return 3;
            default:
                throw new IllegalArgumentException("No case for " + severity);
        }
    }

    public MarkovChainWeatherGenerator() {
        weatherMarkovChain = new MarkovChain<WeatherCondition>(Arrays.asList(WeatherCondition.values()), FIRST_ORDER_CONDITION_TRANSITION_MATRIX);

        // warm up: produce a believable initial history using the transition matrix;
        for (int i = 0; i < weatherMarkovChain.order * 2; i++) {
            weatherMarkovChain.next();
        }
    }

    @Override
    public String toDisplayString() {
        return "Markov Chain Weather Generator";
    }

    @Override
    public WeatherCondition getNext() {
        return weatherMarkovChain.next();
    }
}
