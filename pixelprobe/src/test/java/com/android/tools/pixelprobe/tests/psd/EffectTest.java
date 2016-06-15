/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.tools.pixelprobe.tests.psd;

import com.android.tools.pixelprobe.*;
import com.android.tools.pixelprobe.Image;
import com.android.tools.pixelprobe.effect.Shadow;
import com.android.tools.pixelprobe.tests.ImageUtils;
import org.junit.Assert;
import org.junit.Test;

import java.awt.*;
import java.io.IOException;
import java.util.List;

public class EffectTest {
    @Test
    public void singleShadow() throws IOException {
        Image image = ImageUtils.loadImage("layer_effect_single_shadow.psd");
        List<Layer> layers = image.getLayers();

        Layer layer = layers.get(0);
        Assert.assertEquals("Drop", layer.getName());
        Effects effects = layer.getEffects();
        Assert.assertEquals(0, effects.getInnerShadows().size());
        Assert.assertEquals(1, effects.getOuterShadows().size());

        Shadow shadow = effects.getOuterShadows().get(0);
        Assert.assertEquals(Shadow.Type.OUTER, shadow.getType());
        Assert.assertEquals(90.0f, shadow.getAngle(), 0.01f);
        Assert.assertEquals(BlendMode.MULTIPLY, shadow.getBlendMode());
        Assert.assertEquals(15.0f, shadow.getBlur(), 0.01f);
        Assert.assertEquals(Color.BLACK, shadow.getColor());
        Assert.assertEquals(5.0f, shadow.getDistance(), 0.01f);
        Assert.assertEquals(0.35f, shadow.getOpacity(), 0.01f);

        layer = layers.get(1);
        Assert.assertEquals("Inner", layer.getName());
        effects = layer.getEffects();
        Assert.assertEquals(1, effects.getInnerShadows().size());
        Assert.assertEquals(0, effects.getOuterShadows().size());

        shadow = effects.getInnerShadows().get(0);
        Assert.assertEquals(Shadow.Type.INNER, shadow.getType());
        Assert.assertEquals(47.0f, shadow.getAngle(), 0.01f);
        Assert.assertEquals(BlendMode.DARKEN, shadow.getBlendMode());
        Assert.assertEquals(7.0f, shadow.getBlur(), 0.01f);
        Assert.assertEquals(Color.GREEN, shadow.getColor());
        Assert.assertEquals(3.0f, shadow.getDistance(), 0.01f);
        Assert.assertEquals(0.55f, shadow.getOpacity(), 0.01f);
    }

    @Test
    public void multiShadow() throws IOException {
        Image image = ImageUtils.loadImage("layer_effect_multi_shadow.psd");
        List<Layer> layers = image.getLayers();

        Layer layer = layers.get(0);
        Assert.assertEquals("Multi", layer.getName());
        Effects effects = layer.getEffects();
        Assert.assertEquals(3, effects.getInnerShadows().size());
        Assert.assertEquals(3, effects.getOuterShadows().size());
    }
}
