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

/**
 * Created by Linus on 5-11-2014.
 */
//package private
abstract class ProbabilisticEvent {

    public static final float COMMON        = 1.0f;
    public static final float UNCOMMON      = 0.6f;
    public static final float VERY_UNCOMMON = 0.3f;
    public static final float RARE          = 0.1f;
    public static final float IMPOSSIBLE    = 0.0f;

    protected final float likelihood;

    protected ProbabilisticEvent(final float likelihood) {
        this.likelihood = likelihood;
    }

    protected final boolean isPossible() {
        return likelihood > 0f;
    }
}
