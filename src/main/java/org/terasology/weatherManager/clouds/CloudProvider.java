// Copyright 2020 The Terasology Foundation
// SPDX-License-Identifier: Apache-2.0

package org.terasology.weatherManager.clouds;

import org.terasology.engine.registry.CoreRegistry;
import org.terasology.engine.utilities.procedural.BrownianNoise3D;
import org.terasology.engine.utilities.procedural.PerlinNoise;
import org.terasology.engine.world.generation.Border3D;
import org.terasology.engine.world.generation.FacetProviderPlugin;
import org.terasology.engine.world.generation.GeneratingRegion;
import org.terasology.engine.world.generation.Produces;
import org.terasology.engine.world.generator.plugin.RegisterPlugin;
import org.terasology.math.geom.Rect2i;

/**
 * A facet provider for a single layer of clouds
 *
 * @author Martin Steiger
 */
@RegisterPlugin
@Produces(CloudFacet.class)
public class CloudProvider implements FacetProviderPlugin {

    private BrownianNoise3D noise;

    @Override
    public void setSeed(long seed) {
        noise = new BrownianNoise3D(new PerlinNoise(seed), 4);
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

        Rect2i reg = facet.getWorldRegion();

        for (int y = reg.minY(); y <= reg.maxY(); y++) {
            for (int x = reg.minX(); x <= reg.maxX(); x++) {
                if (isClouded(x, y, anim)) {
                    facet.setWorld(x, y, true);
                }
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
