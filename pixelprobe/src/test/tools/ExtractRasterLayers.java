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

import com.android.tools.pixelprobe.Image;
import com.android.tools.pixelprobe.Layer;
import com.android.tools.pixelprobe.PixelProbe;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.*;
import java.util.stream.Collectors;

@SuppressWarnings("UseOfSystemOutOrSystemErr")
public class ExtractRasterLayers {
    private static Path outputDir;

    public static void main(String[] args) {
        checkArguments(args);

        Path inputDir = Paths.get(args[0]).toAbsolutePath().normalize();
        if (Files.notExists(inputDir)) {
            printHelp();
            System.exit(1);
        }

        outputDir = Paths.get(args[1]).toAbsolutePath().normalize();
        if (Files.notExists(outputDir)) {
            try {
                Files.createDirectories(outputDir);
            } catch (IOException e1) {
                System.err.println("Could not create output directory " + outputDir);
                System.exit(1);
            }
        }

        try {
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:**/*.psd");
            Files.list(inputDir)
                .filter(matcher::matches)
                .collect(Collectors.toList())
                .parallelStream()
                .forEach(ExtractRasterLayers::extractLayers);
        } catch (IOException e) {
            System.err.println("An error occurred while reading the input directory.");
        }
    }

    private static void extractLayers(Path path) {
        File file = path.toFile();
        try (FileInputStream in = new FileInputStream(file)) {
            Image image = PixelProbe.probe(in);
            image.getLayers()
                .parallelStream()
                .filter(layer -> layer.getType() == Layer.Type.IMAGE)
                .forEach(layer -> save(layer, file.getName()));
        } catch (IOException e) {
            System.err.println("An error occurred while parsing " + path);
        }
    }

    private static void save(Layer layer, String name) {
        BufferedImage image = layer.getImage();
        if (image == null) return;

        Path path = Paths.get(outputDir.toAbsolutePath().toString(), name);
        try {
            if (Files.notExists(path)) Files.createDirectories(path);
            path = path.resolve(layer.getName() + ".png");

            System.out.println("Writing " + outputDir.relativize(path));

            ImageIO.write(toSRGB(image), "PNG", path.toFile());
        } catch (IOException e) {
            System.err.println("An error occurred while writing " + path);
        }
    }

    private static BufferedImage toSRGB(BufferedImage bufferedImage) throws IOException {
        if (bufferedImage.getColorModel().getColorSpace().isCS_sRGB()) return bufferedImage;
        ColorConvertOp op = new ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_sRGB),
                new RenderingHints(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY));
        return op.filter(bufferedImage, null);
    }

    private static void checkArguments(String[] args) {
        if (args.length < 2) {
            printHelp();
            System.exit(1);
        }
    }

    private static void printHelp() {
        System.out.println("java ExtractRasterLayers <input dir> <output dir>\n");
        System.out.println("Finds all .psd files in <input dir> and extract the content ");
        System.out.println("of their raster layers in <output dir> as PNG files.");
    }
}
