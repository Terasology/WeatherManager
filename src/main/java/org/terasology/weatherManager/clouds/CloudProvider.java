// Copyright 2021 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.weatherManager.clouds;

import org.joml.Vector2ic;
import org.terasology.engine.registry.CoreRegistry;
import org.terasology.engine.utilities.procedural.BrownianNoise;
import org.terasology.engine.utilities.procedural.SimplexNoise;
import org.terasology.engine.world.generation.Border3D;
import org.terasology.engine.world.generation.FacetProviderPlugin;
import org.terasology.engine.world.generation.GeneratingRegion;
import org.terasology.engine.world.generation.Produces;
import org.terasology.engine.world.generator.plugin.RegisterPlugin;

/**
 * A facet provider for a single layer of clouds
 */
@RegisterPlugin
@Produces(CloudFacet.class)
public class CloudProvider implements FacetProviderPlugin {

    private BrownianNoise noise;

    @Override
    public void setSeed(long seed) {
        noise = new BrownianNoise(new SimplexNoise(seed), 4);
    }

    @Override
    public void process(GeneratingRegion region) {

        CloudUpdateManager cloudManager = CoreRegistry.get(CloudUpdateManager.class);

        // cloudManager is available only if run with AUTHORITY flag
        if (cloudManager == null) {
            return;
        }

        Border3D border = region.getBorderForFacet(CloudFacet.class);
        CloudFacet facet = new CloudFacet(cloudManager.getCloudHeight(), region.getRegion(), border);

        float anim = cloudManager.getAnimFrame();

        for (Vector2ic pos: facet.getRelativeArea()) {
            if (isClouded(pos.x(), pos.y(), anim)) {
                facet.setWorld(pos, true);
            }
        }

        region.setRegionFacet(CloudFacet.class, facet);
    }

    public boolean isClouded(int wx, int wz, float anim) {
        float nx = wx * 0.01f;
        float nz = wz * 0.01f;
        return (noise.noise(nx, anim, nz) < 0);
    }

}
