// Copyright 2021 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.weatherManager.clouds;

import org.terasology.engine.world.block.BlockRegion;
import org.terasology.engine.world.generation.Border3D;
import org.terasology.engine.world.generation.facets.base.BaseBooleanFieldFacet2D;

/**
 * Defines a 2D layer of clouds.
 */
public class CloudFacet extends BaseBooleanFieldFacet2D {

    private final int height;

    public CloudFacet(int height, BlockRegion targetRegion, Border3D border) {
        super(targetRegion, border);

        this.height = height;
    }

    /**
     * @return the height of this cloud layer
     */
    public int getHeight() {
        return height;
    }
}
