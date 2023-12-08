// Copyright 2021 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0
package org.terasology.weatherManager.weather;
import java.util.Random;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

import java.util.HashMap;
import java.util.Map;

/**
 * Pseudo enum class to describe different downfall conditions of the weather.
 * <p>
 * Can be compared with "==", since only one instance will be created for each combination of values.
 */
public final class DownfallCondition {
    private static final Map<DownfallValues, DownfallCondition> INSTANCES = new HashMap<>();

    // this needs to be executed after creating INSTANCES, otherwise we'll run into an NPE in get()
    @SuppressWarnings("checkstyle:DeclarationOrder")
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
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DownfallCondition that = (DownfallCondition) o;
        return Objects.equal(downfallValues, that.downfallValues);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(downfallValues);
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

        if (INSTANCES.containsKey(values)) {
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
    public static DownfallCondition currentDownfall(float temperature, Severity humidity){
        Random rand = new Random();
        boolean withThunder = rand.nextInt(2)==0?false:true ;
        // if the severity is none, there is no downfall regardless of the temparature. Also, there is no thunder
        // because it would be an illegal combination as stated in the get method of this class
        if(humidity.toString().equals("none")){
            return DownfallCondition.get(humidity,DownfallCondition.DownfallType.NONE,false);
        }
        // it is raining with a severity non equal to none if the temperature is greater than 0
        else if(temperature>0){
            return DownfallCondition.get(humidity,DownfallCondition.DownfallType.RAIN,withThunder);
        }
        // it is hailing with a severity non equal to none if the temperature greater than -2 and less than 0
        else if(temperature>-2){
            return DownfallCondition.get(humidity,DownfallCondition.DownfallType.HAIL,withThunder);
        }
        // it is snowing if there is no match with the precedent cases
        else {
            return DownfallCondition.get(humidity,DownfallCondition.DownfallType.SNOW,withThunder);
        }
    }

    public DownfallValues getDownfallValues() {
        return downfallValues;
    }

    public enum DownfallType {
        NONE("none"),
        RAIN("rain"),
        HAIL("hail"),
        SNOW("snow");

        private final String string;

        DownfallType(String string) {
            this.string = string;
        }

        @Override
        public String toString() {
            return string;
        }
    }

    public static final class DownfallValues {
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
            if (other != null && other instanceof DownfallValues) {
                DownfallValues otherDownfallValues = (DownfallValues) other;

                return otherDownfallValues.type == this.type
                        && otherDownfallValues.amount == this.amount
                        && otherDownfallValues.withThunder == this.withThunder;
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
