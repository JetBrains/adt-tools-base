/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.assetstudiolib;

import com.android.assetstudiolib.Util.Effect;
import com.android.assetstudiolib.Util.FillEffect;
import com.android.assetstudiolib.Util.ShadowEffect;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

/**
 * A {@link GraphicGenerator} that generates Android "menu" icons.
 */
public class MenuIconGenerator extends GraphicGenerator {
    /** Creates a menu icon generator */
    public MenuIconGenerator() {
    }

    @Override
    public BufferedImage generate(GraphicGeneratorContext context, Options options) {
        Rectangle imageSizeHdpi = new Rectangle(0, 0, 48, 48);
        Rectangle targetRectHdpi = new Rectangle(8, 8, 32, 32);
        float scaleFactor = GraphicGenerator.getMdpiScaleFactor(options.density);
        Rectangle imageRect = Util.scaleRectangle(imageSizeHdpi, scaleFactor);
        Rectangle targetRect = Util.scaleRectangle(targetRectHdpi, scaleFactor);

        BufferedImage outImage = Util.newArgbBufferedImage(imageRect.width, imageRect.height);
        Graphics2D g = (Graphics2D) outImage.getGraphics();

        BufferedImage tempImage = Util.newArgbBufferedImage(
                imageRect.width, imageRect.height);
        Graphics2D g2 = (Graphics2D) tempImage.getGraphics();
        Util.drawCenterInside(g2, options.sourceImage, targetRect);

        Util.drawEffects(g, tempImage, 0, 0, new Effect[] {
                new FillEffect(
                        new GradientPaint(
                                0, 0,
                                new Color(0xa3a3a3),
                                0, imageRect.height,
                                new Color(0x787878))),
                new ShadowEffect(
                        0,
                        2 * scaleFactor,
                        2 * scaleFactor,
                        Color.BLACK,
                        0.2,
                        true),
                new ShadowEffect(
                        0,
                        1,
                        0,
                        Color.BLACK,
                        0.35,
                        true),
                new ShadowEffect(
                        0,
                        -1,
                        0,
                        Color.WHITE,
                        0.35,
                        true),
        });

        g.dispose();
        g2.dispose();

        return outImage;
    }
}
