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

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * A {@link GraphicGenerator} that generates Android "launcher" icons.
 */
public class LauncherIconGenerator extends GraphicGenerator {
    private static final Rectangle IMAGE_SIZE_MDPI = new Rectangle(0, 0, 48, 48);
    private static final Rectangle TARGET_RECT_MDPI = new Rectangle(2, 2, 44, 44);

    @Override
    public BufferedImage generate(GraphicGeneratorContext context, Options options) {
        LauncherOptions launcherOptions = (LauncherOptions) options;

        String density;
        if (launcherOptions.isWebGraphic) {
            density = "web";
        } else {
            density = launcherOptions.density.getResourceValue();
        }
        String shape = launcherOptions.shape.id;
        BufferedImage mBackImage = null;
        BufferedImage mForeImage = null;
        BufferedImage mMaskImage = null;
        if (launcherOptions.shape != Shape.NONE) {
            mBackImage = context.loadImageResource("/images/launcher_stencil/"
                + shape + "/" + density + "/back.png");
            mForeImage = context.loadImageResource("/images/launcher_stencil/"
                + shape + "/" + density + "/" + launcherOptions.style.id + ".png");
            mMaskImage = context.loadImageResource("/images/launcher_stencil/"
                + shape + "/" + density + "/mask.png");
        }

        float scaleFactor = GraphicGenerator.getMdpiScaleFactor(launcherOptions.density);
        if (launcherOptions.isWebGraphic) {
            // Target size for the web graphic is 512
            scaleFactor = 512 / (float) IMAGE_SIZE_MDPI.height;
        }
        Rectangle imageRect = Util.scaleRectangle(IMAGE_SIZE_MDPI, scaleFactor);
        Rectangle targetRect = Util.scaleRectangle(TARGET_RECT_MDPI, scaleFactor);

        BufferedImage outImage = Util.newArgbBufferedImage(imageRect.width, imageRect.height);
        Graphics2D g = (Graphics2D) outImage.getGraphics();
        if (mBackImage != null) {
            g.drawImage(mBackImage, 0, 0, null);
        }

        BufferedImage tempImage = Util.newArgbBufferedImage(imageRect.width, imageRect.height);
        Graphics2D g2 = (Graphics2D) tempImage.getGraphics();
        if (mMaskImage != null) {
            g2.drawImage(mMaskImage, 0, 0, null);
            g2.setComposite(AlphaComposite.SrcAtop);
            g2.setPaint(new Color(launcherOptions.backgroundColor));
            g2.fillRect(0, 0, imageRect.width, imageRect.height);
        }

        if (launcherOptions.crop) {
            Util.drawCenterCrop(g2, launcherOptions.sourceImage, targetRect);
        } else {
            Util.drawCenterInside(g2, launcherOptions.sourceImage, targetRect);
        }

        g.drawImage(tempImage, 0, 0, null);
        if (mForeImage != null) {
            g.drawImage(mForeImage, 0, 0, null);
        }

        g.dispose();
        g2.dispose();

        return outImage;
    }

    @Override
    public void generate(String category, Map<String, Map<String, BufferedImage>> categoryMap,
            GraphicGeneratorContext context, Options options, String name) {
        LauncherOptions launcherOptions = (LauncherOptions) options;
        boolean generateWebImage = launcherOptions.isWebGraphic;
        launcherOptions.isWebGraphic = false;
        super.generate(category, categoryMap, context, options, name);

        if (generateWebImage) {
            launcherOptions.isWebGraphic = true;
            BufferedImage image = generate(context, options);
            if (image != null) {
                Map<String, BufferedImage> imageMap = new HashMap<String, BufferedImage>();
                categoryMap.put("Web", imageMap);
                imageMap.put(getIconPath(options, name), image);
            }
        }
    }

    @Override
    protected String getIconPath(Options options, String name) {
        if (((LauncherOptions) options).isWebGraphic) {
            return name + "-web.png"; // Store at the root of the project
        }

        return super.getIconPath(options, name);
    }

    /** Options specific to generating launcher icons */
    public static class LauncherOptions extends GraphicGenerator.Options {
        /** Background color, as an RRGGBB packed integer */
        public int backgroundColor = 0;

        /** Whether the image should be cropped or not */
        public boolean crop = true;

        /** The shape to use for the background */
        public Shape shape = Shape.SQUARE;

        /** The effects to apply to the foreground */
        public Style style = Style.SIMPLE;

        /**
         * Whether a web graphic should be generated (will ignore normal density
         * setting). The {@link #generate(GraphicGeneratorContext, Options)}
         * method will use this to decide whether to generate a normal density
         * icon or a high res web image. The
         * {@link GraphicGenerator#generate(String, Map, GraphicGeneratorContext, Options, String)}
         * method will use this flag to determine whether it should include a
         * web graphic in its iteration.
         */
        public boolean isWebGraphic;
    }
}
