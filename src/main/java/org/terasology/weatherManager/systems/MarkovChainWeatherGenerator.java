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

import org.terasology.markovChains.RawMarkovChain;
import org.terasology.markovChains.dataStructures.TransitionMatrix;
import org.terasology.math.TeraMath;
import org.terasology.math.geom.Vector2f;
import org.terasology.utilities.random.FastRandom;
import org.terasology.utilities.random.Random;
import org.terasology.weatherManager.weather.ConditionAndDuration;
import org.terasology.weatherManager.weather.DownfallCondition;
import org.terasology.weatherManager.weather.Severity;
import org.terasology.weatherManager.weather.WeatherCondition;

import java.util.LinkedList;
import java.util.List;

/**
 * WeatherCondition provider using Markov Chain. Currently the only implementation for {@link WeatherConditionProvider}
 * Created by Linus on 5-11-2014.
 */
public class MarkovChainWeatherGenerator implements WeatherConditionProvider {

    private static final List<Severity> SEVERITIES = new LinkedList<Severity>() { {
        addLast(Severity.NONE);
        addLast(Severity.LIGHT);
        addLast(Severity.MODERATE);
        addLast(Severity.HEAVY);
    }};

    private static final TransitionMatrix CLOUDINESS_TRANSITION_MATRIX =
            new TransitionMatrix(2, Severity.values().length) {

                private float priorLikelyHood(int state) {
                    switch(state)
                    {
                        case 0: return 0.4f;
                        case 1: return 1.0f;
                        case 2: return 1.0f;
                        default:
                        case 3: return 0.6f;
                    }
                }

                @Override
                public float get(int... states) {
                    checkInputStates(false, 0, states);

                    int previous = states[0];
                    int current = states[1];
                    int next = states[2];

                    int diff = TeraMath.fastAbs(next - current);

                    return priorLikelyHood(next)
                            *  TeraMath.pow(0.75f, TeraMath.fastAbs(diff))       // diff of 0 is most likely
                            * (isMonotonic(previous, current, next) ? 1 : 0.25f); // prefer consistent changes
                }
            };

    private static final TransitionMatrix PRECIPITATION_TRANSITION_MATRIX =
            new TransitionMatrix(4, Severity.values().length) {

                private float priorLikelyHood(int state) {
                    switch(state) {
                        case 0: return 1.0f;
                        case 1: return 0.5f;
                        case 2: return 0.7f;
                        default:
                        case 3: return 0.3f;
                    }
                }

                private float priorLikelyHood(int state, int currentCloudCondition) {
                    if(currentCloudCondition == 0 && state != 0) {
                        //no downfall on a clear sky
                        return 0.0f;
                    }
                    else {
                        //make it unlikely that the downfall conditions are more severe than the cloudiness
                        int exponent = Math.max(0, currentCloudCondition - state);

                        return TeraMath.pow(0.8f, exponent);
                    }
                }

                @Override
                public float get(int... states) {
                    checkInputStates(false, 0, states);

                    int cloudPrevious = states[0];
                    int cloudCurrent = states[1];
                    int previous = states[2];
                    int current = states[3];
                    int next = states[4];

                    int diff = TeraMath.fastAbs(next - current);

                    return priorLikelyHood(next) * priorLikelyHood(next, cloudCurrent)
                           * TeraMath.pow(0.66f, TeraMath.fastAbs(diff - 1))      // diff of 1 is most likely
                           * (isMonotonic(previous, current, next) ? 1 : 0.5f)    // prefer consistent changes
                           * (follows(cloudPrevious, cloudCurrent, previous, current) ? 2 : 1) // rain follows cloud progression
                           * ((current == 0 && next != 0) ? 0.2f : 1.0f) // we don't want it to start raining too often
                           * ((previous != 0 && current != 0 && next < current) ? 1.5f : 1); // prefer short rain
                }
            };

    private final RawMarkovChain cloudinessGenerator    =
            new RawMarkovChain(CLOUDINESS_TRANSITION_MATRIX);
    private final RawMarkovChain precipitationGenerator =
            new RawMarkovChain(PRECIPITATION_TRANSITION_MATRIX);

    private int[] cloudinessHistory = new int[2];
    private int[] precipitationHistory = new int[2];
    private Vector2f previousWind;

    private final Random randomNumberGenerator;

    //Default mean duration of each generated weatherCondition.
    private final float meanDuration;

    private static final Vector2f ANGLE_REFERENCE_VECTOR = new Vector2f(1,0);

    private static boolean isMonotonic(int first, int second, int third) {
        return (first <= second && second <= third) ||
               (first >= second && second >= third);
    }

    private static boolean follows(int firstA, int secondA, int firstB, int secondB ) {
        return (firstA > secondA && firstB > secondB) || (firstA < secondA && firstB < secondB);
    }


    /**
     * Generates the next random WindCondition using Markov Chain
     * @return a random WindCondition represented as {@link Vector2f}
     */
    public Vector2f nextWindCondition() {
        float expectedMagnitude = ((cloudinessHistory[1] + precipitationHistory[1]) / 8.0f)  * 0.75f + previousWind.length() * 0.25f;
        float stdDev = (cloudinessHistory[1] / 8.0f);

        float nextMagnitude = Math.max((float)TeraMath.fastAbs(TeraMath.fastAbs(randomNumberGenerator.nextGaussian(expectedMagnitude, stdDev))), 0.001f);
        float newAngle = (float)randomNumberGenerator.nextGaussian(0.0, TeraMath.PI * 0.25f)
                       + previousWind.angle(ANGLE_REFERENCE_VECTOR);

        return new Vector2f((float)Math.cos(newAngle) * nextMagnitude,
                            (float)Math.sin(newAngle) * nextMagnitude
        );
    }

    /**
     * Creates a new Weather Generator which generates random weather using Markov Chain
     * @param seed the seed to be used for {@link FastRandom}
     * @param meanDuration Default mean duration of each generated weatherCondition
     */
    public MarkovChainWeatherGenerator(final long seed, final float meanDuration) {
        this.meanDuration = meanDuration;
        this.randomNumberGenerator = new FastRandom(seed);
        //weatherMarkovChain = new MarkovChain<WeatherCondition>(Arrays.asList(WeatherCondition.values()), FIRST_ORDER_CONDITION_TRANSITION_MATRIX, randomNumberGenerator);

        previousWind = new Vector2f(1.0f, 0.0f);

        // warm up: produce a believable initial history using the transition matrix;
        for (int i = 0; i < 8; i++) {
            getNext();
        }
    }

    @Override
    public String toDisplayString() {
        return "Markov Chain Weather Generator";
    }

    private float randomDuration(WeatherCondition condition) {
        double duration = Math.abs(randomNumberGenerator.nextGaussian(meanDuration, meanDuration / 2));

        return (float) duration;
    }

    @Override
    public ConditionAndDuration getNext() {
        // update cloud chain
        int nextCloud =
                cloudinessGenerator.getNext(randomNumberGenerator.nextFloat(), cloudinessHistory[0], cloudinessHistory[1]);

        cloudinessHistory[0] = cloudinessHistory[1];
        cloudinessHistory[1] = nextCloud;


        // update precipitation chain
        int nextPrecipitation =
                precipitationGenerator.getNext(randomNumberGenerator.nextFloat(), cloudinessHistory[0], cloudinessHistory[1], precipitationHistory[0], precipitationHistory[1]);

        precipitationHistory[0] = precipitationHistory[1];
        precipitationHistory[1] = nextPrecipitation;


        // update wind state
        previousWind = nextWindCondition();

        // now put generated values into a WeatherCondition object
        DownfallCondition downfallCondition =
                nextPrecipitation == 0
                        ? DownfallCondition.NO_DOWNFALL
                        : DownfallCondition.get(SEVERITIES.get(nextPrecipitation), DownfallCondition.DownfallType.RAIN, false);

        final WeatherCondition condition = new WeatherCondition(Severity.values()[nextCloud], downfallCondition, previousWind);

        return new ConditionAndDuration(
                condition,
                randomDuration(condition)
        );
    }
}
