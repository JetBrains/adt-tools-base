/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.builder.png;

import static java.nio.charset.Charset.defaultCharset;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.resources.Density;
import com.android.utils.FileUtils;
import com.android.utils.NullLogger;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * Unit tests for {@link VectorDrawableRenderer}.
 */
public class VectorDrawableRendererTest {
    @Rule
    public TemporaryFolder tmpFolder = new TemporaryFolder();

    private VectorDrawableRenderer mRenderer;

    private File mRes;

    private File mOutput;

    private Set<Density> mDensities;

    @Before
    public void setUp() throws Exception {
        mDensities = ImmutableSet.of(Density.HIGH, Density.MEDIUM, Density.LOW);
        mOutput = new File("output");
        mRenderer = new VectorDrawableRenderer(19, mOutput, mDensities, new NullLogger());
        mRes = tmpFolder.newFolder("app", "src", "main", "res");
    }

    @Test
    public void commonCase() throws Exception {
        File drawable = new File(mRes, "drawable");
        File input = new File(drawable, "icon.xml");

        Files.createParentDirs(input);
        Files.write("<vector></vector>", input, defaultCharset());

        Collection<File> result = mRenderer.getFilesToBeGenerated(input);

        Assert.assertEquals(
                ImmutableSet.of(
                        FileUtils.join(mOutput, "drawable-hdpi", "icon.png"),
                        FileUtils.join(mOutput, "drawable-mdpi", "icon.png"),
                        FileUtils.join(mOutput, "drawable-ldpi", "icon.png"),
                        FileUtils.join(mOutput, "drawable-anydpi-v21", "icon.xml")),
                ImmutableSet.copyOf(result));
    }

    @Test
    public void noDensities() throws Exception {
        mRenderer = new VectorDrawableRenderer(
                19, mOutput, Collections.<Density>emptySet(), new NullLogger());
        File drawable = new File(mRes, "drawable");
        File input = new File(drawable, "icon.xml");

        Files.createParentDirs(input);
        Files.write("<vector></vector>", input, defaultCharset());

        Collection<File> result = mRenderer.getFilesToBeGenerated(input);

        Assert.assertEquals(
                ImmutableSet.of(
                        FileUtils.join(mOutput, "drawable-anydpi-v21", "icon.xml")),
                ImmutableSet.copyOf(result));
    }

    @Test
    public void languageQualifier() throws Exception {
        File drawable = new File(mRes, "drawable-fr");
        File input = new File(drawable, "icon.xml");

        Files.createParentDirs(input);
        Files.write("<vector></vector>", input, defaultCharset());

        Collection<File> result = mRenderer.getFilesToBeGenerated(input);

        Assert.assertEquals(
                ImmutableSet.of(
                        FileUtils.join(mOutput, "drawable-fr-hdpi", "icon.png"),
                        FileUtils.join(mOutput, "drawable-fr-mdpi", "icon.png"),
                        FileUtils.join(mOutput, "drawable-fr-ldpi", "icon.png"),
                        FileUtils.join(mOutput, "drawable-fr-anydpi-v21", "icon.xml")),
                ImmutableSet.copyOf(result));
    }

    @Test
    public void versionQualifier() throws Exception {
        File drawable = new File(mRes, "drawable-v16");
        File input = new File(drawable, "icon.xml");

        Files.createParentDirs(input);
        Files.write("<vector></vector>", input, defaultCharset());

        Collection<File> result = mRenderer.getFilesToBeGenerated(input);

        Assert.assertEquals(
                ImmutableSet.of(
                        FileUtils.join(mOutput, "drawable-hdpi-v16", "icon.png"),
                        FileUtils.join(mOutput, "drawable-mdpi-v16", "icon.png"),
                        FileUtils.join(mOutput, "drawable-ldpi-v16", "icon.png"),
                        FileUtils.join(mOutput, "drawable-anydpi-v21", "icon.xml")),
                ImmutableSet.copyOf(result));
    }

    @Test
    public void densityQualifier() throws Exception {
        File drawable = new File(mRes, "drawable-hdpi");
        File input = new File(drawable, "icon.xml");

        Files.createParentDirs(input);
        Files.write("<vector></vector>", input, defaultCharset());

        Collection<File> result = mRenderer.getFilesToBeGenerated(input);

        Assert.assertEquals(
                ImmutableSet.of(
                        FileUtils.join(mOutput, "drawable-hdpi", "icon.png"),
                        FileUtils.join(mOutput, "drawable-hdpi-v21", "icon.xml")),
                ImmutableSet.copyOf(result));
    }

    @Test
    public void anyDpi() throws Exception {
        File drawable = new File(mRes, "drawable-anydpi");
        File input = new File(drawable, "icon.xml");

        Files.createParentDirs(input);
        Files.write("<vector></vector>", input, defaultCharset());

        Collection<File> result = mRenderer.getFilesToBeGenerated(input);

        Assert.assertEquals(
                ImmutableSet.of(
                        FileUtils.join(mOutput, "drawable-ldpi", "icon.png"),
                        FileUtils.join(mOutput, "drawable-mdpi", "icon.png"),
                        FileUtils.join(mOutput, "drawable-hdpi", "icon.png"),
                        FileUtils.join(mOutput, "drawable-anydpi-v21", "icon.xml")),
                ImmutableSet.copyOf(result));
    }

    @Test
    public void anyDpi_version() throws Exception {
        File drawable = new File(mRes, "drawable-anydpi-v16");
        File input = new File(drawable, "icon.xml");

        Files.createParentDirs(input);
        Files.write("<vector></vector>", input, defaultCharset());

        Collection<File> result = mRenderer.getFilesToBeGenerated(input);

        Assert.assertEquals(
                ImmutableSet.of(
                        FileUtils.join(mOutput, "drawable-ldpi-v16", "icon.png"),
                        FileUtils.join(mOutput, "drawable-mdpi-v16", "icon.png"),
                        FileUtils.join(mOutput, "drawable-hdpi-v16", "icon.png"),
                        FileUtils.join(mOutput, "drawable-anydpi-v21", "icon.xml")),
                ImmutableSet.copyOf(result));
    }

    @Test
    public void noDpi() throws Exception {
        File drawable = new File(mRes, "drawable-nodpi");
        File input = new File(drawable, "icon.xml");

        Files.createParentDirs(input);
        Files.write("<vector></vector>", input, defaultCharset());

        Collection<File> result = mRenderer.getFilesToBeGenerated(input);

        Assert.assertEquals(
                ImmutableSet.of(
                        FileUtils.join(mOutput, "drawable-nodpi", "icon.xml")),
                ImmutableSet.copyOf(result));
    }

    @Test
    public void noDpi_version() throws Exception {
        File drawable = new File(mRes, "drawable-nodpi-v16");
        File input = new File(drawable, "icon.xml");

        Files.createParentDirs(input);
        Files.write("<vector></vector>", input, defaultCharset());

        Collection<File> result = mRenderer.getFilesToBeGenerated(input);

        Assert.assertEquals(
                ImmutableSet.of(
                        FileUtils.join(mOutput, "drawable-nodpi-v16", "icon.xml")),
                ImmutableSet.copyOf(result));
    }

    @Test
    public void needsPreprocessing() throws Exception {
        File drawable = new File(mRes, "drawable");
        File input = new File(drawable, "icon.xml");

        Files.createParentDirs(input);
        Files.write("<vector></vector>", input, defaultCharset());

        assertTrue(mRenderer.needsPreprocessing(input));
    }

    @Test
    public void needsPreprocessing_v21() throws Exception {
        File drawableV21 = new File(mRes, "drawable-v21");
        File input = new File(drawableV21, "icon.xml");

        Files.createParentDirs(input);
        Files.write("<vector></vector>", input, defaultCharset());

        assertFalse(mRenderer.needsPreprocessing(input));
    }

    @Test
    public void needsPreprocessing_anydpi_v21() throws Exception {
        File drawableV21 = new File(mRes, "drawable-anydpi-v21");
        File input = new File(drawableV21, "icon.xml");

        Files.createParentDirs(input);
        Files.write("<vector></vector>", input, defaultCharset());

        assertFalse(mRenderer.needsPreprocessing(input));
    }

    @Test
    public void needsPreprocessing_v16() throws Exception {
        File drawableV16 = new File(mRes, "drawable-v16");
        File input = new File(drawableV16, "icon.xml");

        Files.createParentDirs(input);
        Files.write("<vector></vector>", input, defaultCharset());

        assertTrue(mRenderer.needsPreprocessing(input));
    }

    @Test
    public void needsPreprocessing_nonVector() throws Exception {
        File drawable = new File(mRes, "drawable");
        File input = new File(drawable, "icon.xml");

        Files.createParentDirs(input);
        Files.write("<bitmap android:src=\"@drawable/icon\" />", input, defaultCharset());

        assertFalse(mRenderer.needsPreprocessing(input));
    }

    @Test
    public void needsPreprocessing_notDrawable() throws Exception {
        File values = new File(mRes, "values");
        File input = new File(values, "strings.xml");

        Files.createParentDirs(input);
        Files.write("<resources></resources>", input, defaultCharset());

        assertFalse(mRenderer.needsPreprocessing(input));
    }

    @Test
    public void needsPreprocessing_minSdk() throws Exception {
        mRenderer = new VectorDrawableRenderer(21, mOutput, mDensities, new NullLogger());
        File drawable = new File(mRes, "drawable");
        File input = new File(drawable, "icon.xml");

        Files.createParentDirs(input);
        Files.write("<vector></vector>", input, defaultCharset());

        assertFalse(mRenderer.needsPreprocessing(input));
    }
}