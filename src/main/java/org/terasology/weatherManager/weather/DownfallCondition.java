/*
 * Copyright 2015 MovingBlocks
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

import com.google.common.base.Preconditions;

import java.util.HashMap;
import java.util.Map;

/**
 * Pseudo enum class to describe different downfall conditions of the weather.
 *
 * Can be compared with "==", since only one instance will be created for each combination of values.
 */
public final class DownfallCondition {
    private static final Map<DownfallValues, DownfallCondition> INSTANCES = new HashMap<>();

    public static final DownfallCondition NO_DOWNFALL = get(Severity.NONE, DownfallType.NONE, false);
    
    private final DownfallValues downfallValues;

    private DownfallCondition(final DownfallValues values) {
        this.downfallValues = values;
    }

    @Override
    public String toString() {
        return downfallValues.toString();
    }

    @Override
    public int hashCode() {
        return downfallValues.hashCode();
    }

    public static DownfallCondition get(final Severity amount, final DownfallType type, final boolean withThunder) {
        // check arguments
        Preconditions.checkArgument(!withThunder || (amount == Severity.NONE),
                "Severity == NONE and withThunder == true is an illegal combination."
        );

        Preconditions.checkArgument(amount != Severity.NONE || (type == DownfallType.NONE),
                "Severity == NONE and type == %s is a an illegal combination.",
                type
        );

        Preconditions.checkArgument(amount != null && type != null,
                "Function does not accept null arguments."
        );

        // body
        final DownfallValues values = new DownfallValues(amount, type, withThunder);

        if(INSTANCES.containsKey(values)) {
            return INSTANCES.get(values);
        } else {
            DownfallCondition ret = new DownfallCondition(values);
            INSTANCES.put(values, ret);
            return ret;
        }

        /* cooler Java8 version that is not supported yet
        return INSTANCES.computeIfAbsent(values, (DownfallValues v) -> new DownfallCondition(v) );
        */
    }

    public static enum DownfallType {
        NONE("none"),
        RAIN("rain"),
        HAIL("hail"),
        SNOW("snow");

        private final String string;

        private DownfallType(String string) {
            this.string = string;
        }

        @Override
        public String toString() {
            return string;
        }
    }

    private static final class DownfallValues {
        public final DownfallType type;
        public final Severity amount;
        public final boolean withThunder;

        public DownfallValues(final Severity amount, final DownfallType type, final boolean withThunder) {
            this.amount = amount;
            this.type = type;
            this.withThunder = withThunder;
        }

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

        @Override
        public boolean equals(Object other) {
            if(other != null && other instanceof DownfallValues) {
                DownfallValues otherDownfallValues = (DownfallValues)other;

                return otherDownfallValues.type == this.type &&
                       otherDownfallValues.amount == this.amount &&
                       otherDownfallValues.withThunder == this.withThunder;
            }

            return false;
        }

        @Override
        public int hashCode() {
            return type.ordinal() +
                   DownfallType.values().length * amount.ordinal() +
                    DownfallType.values().length * Severity.values().length * (withThunder ? 1 : 0);
        }
    }
}
