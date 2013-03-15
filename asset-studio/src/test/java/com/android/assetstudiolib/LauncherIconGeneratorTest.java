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

import com.android.assetstudiolib.LauncherIconGenerator.LauncherOptions;

import java.io.IOException;

@SuppressWarnings("javadoc")
public class LauncherIconGeneratorTest extends GeneratorTest {
    private void checkGraphic(String baseName,
            GraphicGenerator.Shape shape, GraphicGenerator.Style style,
            boolean crop, int background, boolean isWebGraphic) throws IOException {
        LauncherOptions options = new LauncherOptions();
        options.shape = shape;
        options.crop = crop;
        options.style = style;
        options.backgroundColor = background;
        options.isWebGraphic = isWebGraphic;

        LauncherIconGenerator generator = new LauncherIconGenerator();
        checkGraphic(4 + (isWebGraphic ? 1 : 0), "launcher", baseName, generator, options);
    }

    public void testLauncher_simpleCircle() throws Exception {
        checkGraphic("red_simple_circle", GraphicGenerator.Shape.CIRCLE,
                GraphicGenerator.Style.SIMPLE, true, 0xFF0000, true);
    }

    // The glossy rendering type is no longer included since it doesn't match the
    // style guide.
    //public void testLauncher_glossySquare() throws Exception {
    //    checkGraphic("blue_glossy_square", GraphicGenerator.Shape.SQUARE,
    //            GraphicGenerator.Style.GLOSSY, true, 0x0040FF, true);
    //}
}
