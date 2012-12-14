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

import com.android.resources.Density;
import com.google.common.io.Closeables;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.imageio.ImageIO;

/**
 * The base Generator class.
 */
public abstract class GraphicGenerator {
    /**
     * Options used for all generators.
     */
    public static class Options {
        /** Minimum version (API level) of the SDK to generate icons for */
        public int minSdk = 1;

        /** Source image to use as a basis for the icon */
        public BufferedImage sourceImage;

        /** The density to generate the icon with */
        public Density density = Density.XHIGH;
    }

    /** Shapes that can be used for icon backgrounds */
    public static enum Shape {
        /** No background */
        NONE("none"),
        /** Circular background */
        CIRCLE("circle"),
        /** Square background */
        SQUARE("square");

        /** Id, used in filenames to identify associated stencils */
        public final String id;

        Shape(String id) {
            this.id = id;
        }
    }

    /** Foreground effects styles */
    public static enum Style {
        /** No effects */
        SIMPLE("fore1");

        /** Id, used in filenames to identify associated stencils */
        public final String id;

        Style(String id) {
            this.id = id;
        }
    }

    /**
     * Generate a single icon using the given options
     *
     * @param context render context to use for looking up resources etc
     * @param options options controlling the appearance of the icon
     * @return a {@link BufferedImage} with the generated icon
     */
    public abstract BufferedImage generate(GraphicGeneratorContext context, Options options);

    /**
     * Computes the target filename (relative to the Android project folder)
     * where an icon rendered with the given options should be stored. This is
     * also used as the map keys in the result map used by
     * {@link #generate(String, Map, GraphicGeneratorContext, Options, String)}.
     *
     * @param options the options object used by the generator for the current
     *            image
     * @param name the base name to use when creating the path
     * @return a path relative to the res/ folder where the image should be
     *         stored (will always use / as a path separator, not \ on Windows)
     */
    protected String getIconPath(Options options, String name) {
        return getIconFolder(options) + '/' + getIconName(options, name);
    }

    /**
     * Gets name of the file itself. It is sometimes modified by options, for
     * example in unselected tabs we change foo.png to foo-unselected.png
     */
    protected String getIconName(Options options, String name) {
        return name + ".png"; //$NON-NLS-1$
    }

    /**
     * Gets name of the folder to contain the resource. It usually includes the
     * density, but is also sometimes modified by options. For example, in some
     * notification icons we add in -v9 or -v11.
     */
    protected String getIconFolder(Options options) {
        return "res/drawable-" + options.density.getResourceValue(); //$NON-NLS-1$
    }

    /**
     * Generates a full set of icons into the given map. The values in the map
     * will be the generated images, and each value is keyed by the
     * corresponding relative path of the image, which is determined by the
     * {@link #getIconPath(Options, String)} method.
     *
     * @param category the current category to place images into (if null the
     *            density name will be used)
     * @param categoryMap the map to put images into, should not be null. The
     *            map is a map from a category name, to a map from file path to
     *            image.
     * @param context a generator context which for example can load resources
     * @param options options to apply to this generator
     * @param name the base name of the icons to generate
     */
    public void generate(String category, Map<String, Map<String, BufferedImage>> categoryMap,
            GraphicGeneratorContext context, Options options, String name) {
        Density[] densityValues = Density.values();
        // Sort density values into ascending order
        Arrays.sort(densityValues, new Comparator<Density>() {
            @Override
            public int compare(Density d1, Density d2) {
                return d1.getDpiValue() - d2.getDpiValue();
            }
        });

        for (Density density : densityValues) {
            if (!density.isValidValueForDevice()) {
                continue;
            }
            if (density == Density.TV || density == Density.XXHIGH) {
                // TODO don't manually check and instead gracefully handle missing stencils.
                // Not yet supported -- missing stencil image
                continue;
            }
            options.density = density;
            BufferedImage image = generate(context, options);
            if (image != null) {
                String mapCategory = category;
                if (mapCategory == null) {
                    mapCategory = options.density.getResourceValue();
                }
                Map<String, BufferedImage> imageMap = categoryMap.get(mapCategory);
                if (imageMap == null) {
                    imageMap = new LinkedHashMap<String, BufferedImage>();
                    categoryMap.put(mapCategory, imageMap);
                }
                imageMap.put(getIconPath(options, name), image);
            }
        }
    }

    /**
     * Returns the scale factor to apply for a given MDPI density to compute the
     * absolute pixel count to use to draw an icon of the given target density
     *
     * @param density the density
     * @return a factor to multiple mdpi distances with to compute the target density
     */
    public static float getMdpiScaleFactor(Density density) {
        return density.getDpiValue() / (float) Density.MEDIUM.getDpiValue();
    }

    /**
     * Returns one of the built in stencil images, or null
     *
     * @param relativePath stencil path such as "launcher-stencil/square/web/back.png"
     * @return the image, or null
     * @throws IOException if an unexpected I/O error occurs
     */
    @SuppressWarnings("resource") // Eclipse doesn't know about Closeables#closeQuietly yet
    public static BufferedImage getStencilImage(String relativePath) throws IOException {
        InputStream is = GraphicGenerator.class.getResourceAsStream(relativePath);
        try {
            return ImageIO.read(is);
        } finally {
            Closeables.closeQuietly(is);
        }
    }

    /**
     * Returns the icon (32x32) for a given clip art image.
     *
     * @param name the name of the image to be loaded (which can be looked up via
     *            {@link #getClipartNames()})
     * @return the icon image
     * @throws IOException if the image cannot be loaded
     */
    @SuppressWarnings("resource") // Eclipse doesn't know about Closeables#closeQuietly yet
    public static BufferedImage getClipartIcon(String name) throws IOException {
        InputStream is = GraphicGenerator.class.getResourceAsStream(
                "/images/clipart/small/" + name);
        try {
            return ImageIO.read(is);
        } finally {
            Closeables.closeQuietly(is);
        }
    }

    /**
     * Returns the full size clip art image for a given image name.
     *
     * @param name the name of the image to be loaded (which can be looked up via
     *            {@link #getClipartNames()})
     * @return the clip art image
     * @throws IOException if the image cannot be loaded
     */
    @SuppressWarnings("resource") // Eclipse doesn't know about Closeables#closeQuietly yet
    public static BufferedImage getClipartImage(String name) throws IOException {
        InputStream is = GraphicGenerator.class.getResourceAsStream(
                "/images/clipart/big/" + name);
        try {
            return ImageIO.read(is);
        } finally {
            Closeables.closeQuietly(is);
        }
    }

    /**
     * Returns the names of available clip art images which can be obtained by passing the
     * name to {@link #getClipartIcon(String)} or
     * {@link GraphicGenerator#getClipartImage(String)}
     *
     * @return an iterator for the available image names
     */
    public static Iterator<String> getClipartNames() {
        List<String> names = new ArrayList<String>(80);
        try {
            String pathPrefix = "images/clipart/big/"; //$NON-NLS-1$
            ProtectionDomain protectionDomain = GraphicGenerator.class.getProtectionDomain();
            URL url = protectionDomain.getCodeSource().getLocation();
            File file;
            try {
                file = new File(url.toURI());
            } catch (URISyntaxException e) {
                file = new File(url.getPath());
            }
            final ZipFile zipFile = new JarFile(file);
            Enumeration<? extends ZipEntry> enumeration = zipFile.entries();
            while (enumeration.hasMoreElements()) {
                ZipEntry zipEntry = enumeration.nextElement();
                String name = zipEntry.getName();
                if (!name.startsWith(pathPrefix) || !name.endsWith(".png")) { //$NON-NLS-1$
                    continue;
                }

                int lastSlash = name.lastIndexOf('/');
                if (lastSlash != -1) {
                    name = name.substring(lastSlash + 1);
                }
                names.add(name);
            }
        } catch (final Exception e) {
            e.printStackTrace();
        }

        return names.iterator();
    }
}
