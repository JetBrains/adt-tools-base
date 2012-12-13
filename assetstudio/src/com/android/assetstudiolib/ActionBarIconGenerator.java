/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.eclipse.org/org/documents/epl-v10.php
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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

/**
 * Generate icons for the action bar
 */
public class ActionBarIconGenerator extends GraphicGenerator {

    /** Creates a new {@link ActionBarIconGenerator} */
    public ActionBarIconGenerator() {
    }

    @Override
    public BufferedImage generate(GraphicGeneratorContext context, Options options) {
        ActionBarOptions actionBarOptions = (ActionBarOptions) options;
        Rectangle iconSizeMdpi = new Rectangle(0, 0, 32, 32);
        Rectangle targetRectMdpi = actionBarOptions.sourceIsClipart
                ? new Rectangle(0, 0, 32, 32)
                : new Rectangle(4, 4, 24, 24);
        final float scaleFactor = GraphicGenerator.getMdpiScaleFactor(options.density);
        Rectangle imageRect = Util.scaleRectangle(iconSizeMdpi, scaleFactor);
        Rectangle targetRect = Util.scaleRectangle(targetRectMdpi, scaleFactor);
        BufferedImage outImage = Util.newArgbBufferedImage(imageRect.width, imageRect.height);
        Graphics2D g = (Graphics2D) outImage.getGraphics();

        BufferedImage tempImage = Util.newArgbBufferedImage(
                imageRect.width, imageRect.height);
        Graphics2D g2 = (Graphics2D) tempImage.getGraphics();
        Util.drawCenterInside(g2, options.sourceImage, targetRect);

        if (actionBarOptions.theme == Theme.HOLO_LIGHT) {
            Util.drawEffects(g, tempImage, 0, 0, new Effect[] {
                    new FillEffect(new Color(0x333333), 0.6),
            });
        } else {
            assert actionBarOptions.theme == Theme.HOLO_DARK;
            Util.drawEffects(g, tempImage, 0, 0, new Effect[] {
                    new FillEffect(new Color(0xFFFFFF), 0.8)
            });
        }

        g.dispose();
        g2.dispose();

        return outImage;
    }

    /** Options specific to generating action bar icons */
    public static class ActionBarOptions extends GraphicGenerator.Options {
        /** The theme to generate icons for */
        public Theme theme = Theme.HOLO_LIGHT;

        /** Whether or not the source image is a clipart source */
        public boolean sourceIsClipart = false;
    }

    /** The themes to generate action bar icons for */
    public enum Theme {
        /** Theme.Holo - a dark (and default) version of the Honeycomb theme */
        HOLO_DARK,

        /** Theme.HoloLight - a light version of the Honeycomb theme */
        HOLO_LIGHT;
    }
}
