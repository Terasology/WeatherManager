// Copyright 2021 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0
package org.terasology.weatherManager.weather;

/**
 * Describes the severity/concentration of the weather Created by Linus on 15-2-2015.
 */
public enum Severity {
    NONE("none", 0.00f),
    LIGHT("light", 0.33f),
    MODERATE("moderate", 0.66f),
    HEAVY("heavy", 1.00f);

    private final String string;
    private final float numericValue;

    Severity(String string, float numericValue) {
        this.string = string;
        this.numericValue = numericValue;
    }

    @Override
    public String toString() {
        return string;
    }
}
