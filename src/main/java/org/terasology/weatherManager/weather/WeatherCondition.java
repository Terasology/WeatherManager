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

import java.util.LinkedList;
import java.util.List;

/**
 * Created by Linus on 5-11-2014.
 */
public final class WeatherCondition extends ProbabilisticEvent {

    private static final WeatherCondition[] VALUES = generateInstances();

    public final Severity cloudiness;
    public final DownfallCondition downfallCondition;

    private WeatherCondition(final Severity cloudiness, final DownfallCondition downfallCondition) {
        super(likelihood(cloudiness, downfallCondition));

        this.cloudiness = cloudiness;
        this.downfallCondition = downfallCondition;
    }

    public float likelihood() {
        return likelihood;
    }

    public String toString() {
        String string = cloudinessString();

        if (downfallCondition.amount != Severity.NONE) {
            string += " and " + downfallCondition.toString();
        }

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

    public static WeatherCondition[] values() {
        return VALUES;
    }

    private static float likelihood(final Severity clouds, final DownfallCondition downfall) {
        final float likelihood;

        switch(clouds) {
            case NONE:
                likelihood = (downfall.amount == Severity.NONE)
                    ? COMMON
                    : IMPOSSIBLE;
                break;

            case LIGHT:
                likelihood = (downfall.amount == Severity.HEAVY)
                        ? RARE
                        : (downfall.amount == Severity.MODERATE)
                        ? VERY_UNCOMMON
                        : COMMON;
                break;

            case MODERATE:
                likelihood = (downfall.amount == Severity.HEAVY)
                        ? (downfall.withThunder) ? VERY_UNCOMMON : UNCOMMON
                        : COMMON;
                break;

            case HEAVY:
                likelihood = COMMON;
                break;

            default:
                throw new IllegalArgumentException("No case for " + clouds);
        }

        return likelihood * downfall.likelihood * likelihood(clouds);
    }

    private static float likelihood(final Severity clouds) {
        switch (clouds) {
            case NONE:
                return UNCOMMON;
            case LIGHT:
                return COMMON;
            case MODERATE:
                return COMMON;
            case HEAVY:
                return UNCOMMON;
            default:
                throw new IllegalArgumentException("No case for Severity " + clouds);
        }
    }

    private static WeatherCondition[] generateInstances() {
        List<WeatherCondition> possibleConditions = new LinkedList<>();

        for (Severity cloudiness : Severity.values()) {
            for (DownfallCondition downfall: DownfallCondition.values()) {
                WeatherCondition condition = new WeatherCondition(cloudiness, downfall);
                if (condition.isPossible()) {
                    possibleConditions.add(condition);
                }
            }
        }

        WeatherCondition[] array = {};
        return possibleConditions.toArray(array);
    }

    public static enum Severity {
        NONE("none"),
        LIGHT("light"),
        MODERATE("moderate"),
        HEAVY("heavy");

        private final String string;

        private Severity(String string) {
            this.string = string;
        }

        @Override
        public String toString() {
            return string;
        }
    }

    public static final class DownfallCondition extends ProbabilisticEvent {

        private static final DownfallCondition[] VALUES = generateInstances();

        public static enum Type {
            NONE("none"),
            RAIN("rain"),
            HAIL("hail"),
            SNOW("snow");

            private final String string;

            private Type(String string) {
                this.string = string;
            }

            @Override
            public String toString() {
                return string;
            }
        }

        public final Type type;
        public final Severity amount;
        public final boolean withThunder;

        private DownfallCondition(final Severity amount, final Type type, final boolean withThunder) {
            super(likelihood(amount, type, withThunder));

            this.amount = amount;
            this.type = type;
            this.withThunder = withThunder;
        }

        public static DownfallCondition[] values() {
            return VALUES;
        }

        @Override
        public String toString() {
            if (amount == Severity.NONE) {
                return "no downfall";
            }

            String string = amount + " " + type;

            if (withThunder) {
                string += " with thunder";
            }

            return string;
        }

        private static float likelihood(final Severity heaviness, final Type type, final boolean withThunder) {
            if (type == Type.NONE || heaviness == Severity.NONE) {
                return (type == Type.NONE && heaviness == Severity.NONE && !withThunder)
                        ? COMMON
                        : IMPOSSIBLE;
            } else {
                return likelihood(type, withThunder);
            }
        }

        private static float likelihood(final Type type, final boolean withThunder) {
            switch(type) {
                case RAIN:
                    return withThunder ? UNCOMMON : COMMON;
                case SNOW:
                    return withThunder ? RARE : COMMON;
                case HAIL:
                    return withThunder ? RARE : VERY_UNCOMMON;
                default:
                    throw new IllegalArgumentException("No case for type " + type.toString());
            }
        }

        private static DownfallCondition[] generateInstances() {
            List<DownfallCondition> possibleConditions = new LinkedList<DownfallCondition>();

            for (Severity heaviness : Severity.values()) {
                for (Type type: Type.values()) {
                    //if (type == Type.HAIL || type == type.SNOW)
                    //    continue;

                    final DownfallCondition withThunder = new DownfallCondition(heaviness, type, true);
                    final DownfallCondition withoutThunder = new DownfallCondition(heaviness, type, false);

                    if (withThunder.isPossible()) {
                        possibleConditions.add(withThunder);
                    }
                    if (withoutThunder.isPossible()) {
                        possibleConditions.add(withoutThunder);
                    }
                }
            }

            DownfallCondition[] array = {};
            return possibleConditions.toArray(array);
        }
    }
}
