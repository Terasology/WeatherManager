/*
 * Copyright 2014 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.terasology.weatherManager.clouds;

import org.terasology.math.geom.Rect2i;
import org.terasology.registry.CoreRegistry;
import org.terasology.utilities.procedural.BrownianNoise3D;
import org.terasology.utilities.procedural.PerlinNoise;
import org.terasology.world.block.BlockAreac;
import org.terasology.world.generation.Border3D;
import org.terasology.world.generation.FacetProviderPlugin;
import org.terasology.world.generation.GeneratingRegion;
import org.terasology.world.generation.Produces;
import org.terasology.world.generator.plugin.RegisterPlugin;

/**
 * A facet provider for a single layer of clouds
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

        BlockAreac reg = facet.getWorldRegion();

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
