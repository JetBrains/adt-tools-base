/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.build.gradle.model;

import static com.android.builder.core.BuilderConstants.DEBUG;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Dependencies;
import com.android.builder.model.JavaLibrary;
import com.android.builder.model.ProductFlavorContainer;
import com.android.builder.model.Variant;
import com.google.common.collect.Maps;

import junit.framework.TestCase;

import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.UnknownModelException;
import org.gradle.tooling.model.GradleProject;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.CodeSource;
import java.util.Collection;
import java.util.Map;

public class AndroidProjectTest extends TestCase {

    private static final String FOLDER_TEST_SAMPLE = "samples";
    private static final String FOLDER_TEST_PROJECT = "test-projects";

    private static final String MODEL_VERSION = "1.0.0";

    private static final class ProjectData {
        AndroidProject model;
        File projectDir;

        static ProjectData create(File projectDir, AndroidProject model) {
            ProjectData projectData = new ProjectData();
            projectData.model = model;
            projectData.projectDir = projectDir;

            return projectData;
        }
    }

    private Map<String, ProjectData> getModelForMultiProject(String testFolder, String projectName)
            throws Exception {
        // Configure the connector and create the connection
        GradleConnector connector = GradleConnector.newConnector();

        File projectDir = new File(getTestDir(testFolder), projectName);
        connector.forProjectDirectory(projectDir);

        Map<String, ProjectData> map = Maps.newHashMap();

        ProjectConnection connection = connector.connect();

        try {
            // Query the default Gradle Model.
            GradleProject model = connection.getModel(GradleProject.class);
            assertNotNull("Model Object null-check", model);

            // Now get the children projects, recursively.
            for (GradleProject child : model.getChildren()) {
                String path = child.getPath();
                String name = path.substring(1);
                File childDir = new File(projectDir, name);

                GradleConnector childConnector = GradleConnector.newConnector();

                childConnector.forProjectDirectory(childDir);

                ProjectConnection childConnection = childConnector.connect();
                try {
                    AndroidProject androidProject = childConnection.getModel(AndroidProject.class);

                    assertNotNull("Model Object null-check for " + path, androidProject);
                    assertEquals("Model Name for " + path, name, androidProject.getName());
                    assertEquals("Model version", MODEL_VERSION, androidProject.getModelVersion());

                    map.put(path, ProjectData.create(childDir, androidProject));

                } catch (UnknownModelException e) {
                    // probably a Java-only project, ignore.
                } finally {
                    childConnection.close();
                }
            }
        } finally {
            connection.close();
        }

        return map;
    }

    public void testTicTacToe() throws Exception {
        Map<String, ProjectData> map = getModelForMultiProject(FOLDER_TEST_SAMPLE, "tictactoe");

        ProjectData libModelData = map.get(":lib");
        assertNotNull("lib module model null-check", libModelData);
        assertTrue("lib module library flag", libModelData.model.isLibrary());

        ProjectData appModelData = map.get(":app");
        assertNotNull("app module model null-check", appModelData);

        Collection<Variant> variants = appModelData.model.getVariants();
        Variant debugVariant = getVariant(variants, DEBUG);
        assertNotNull("debug variant null-check", debugVariant);

        Dependencies dependencies = debugVariant.getMainArtifact().getDependencies();
        assertNotNull(dependencies);

        Collection<AndroidLibrary> libs = dependencies.getLibraries();
        assertNotNull(libs);
        assertEquals(1, libs.size());

        AndroidLibrary androidLibrary = libs.iterator().next();
        assertNotNull(androidLibrary);

        assertEquals("Dependency project path", ":lib", androidLibrary.getProject());

        // TODO: right now we can only test the folder name efficiently
        assertTrue(androidLibrary.getFolder().getPath().endsWith("/tictactoe/lib/unspecified"));
    }

    public void testFlavorLib() throws Exception {
        Map<String, ProjectData> map = getModelForMultiProject(FOLDER_TEST_SAMPLE, "flavorlib");

        ProjectData appModelData = map.get(":app");
        assertNotNull("Module app null-check", appModelData);
        AndroidProject model = appModelData.model;

        assertFalse("Library Project", model.isLibrary());

        Collection<Variant> variants = model.getVariants();
        Collection<ProductFlavorContainer> productFlavors = model.getProductFlavors();

        ProductFlavorContainer flavor1 = getProductFlavor(productFlavors, "flavor1");
        assertNotNull(flavor1);

        Variant flavor1Debug = getVariant(variants, "flavor1Debug");
        assertNotNull(flavor1Debug);

        Dependencies dependencies = flavor1Debug.getMainArtifact().getDependencies();
        assertNotNull(dependencies);
        Collection<AndroidLibrary> libs = dependencies.getLibraries();
        assertNotNull(libs);
        assertEquals(1, libs.size());
        AndroidLibrary androidLibrary = libs.iterator().next();
        assertNotNull(androidLibrary);
        assertEquals(":lib1", androidLibrary.getProject());
        // TODO: right now we can only test the folder name efficiently
        assertTrue(androidLibrary.getFolder().getPath(), androidLibrary.getFolder().getPath().endsWith("/flavorlib/lib1/unspecified"));

        ProductFlavorContainer flavor2 = getProductFlavor(productFlavors, "flavor2");
        assertNotNull(flavor2);

        Variant flavor2Debug = getVariant(variants, "flavor2Debug");
        assertNotNull(flavor2Debug);

        dependencies = flavor2Debug.getMainArtifact().getDependencies();
        assertNotNull(dependencies);
        libs = dependencies.getLibraries();
        assertNotNull(libs);
        assertEquals(1, libs.size());
        androidLibrary = libs.iterator().next();
        assertNotNull(androidLibrary);
        assertEquals(":lib2", androidLibrary.getProject());
        // TODO: right now we can only test the folder name efficiently
        assertTrue(androidLibrary.getFolder().getPath(), androidLibrary.getFolder().getPath().endsWith("/flavorlib/lib2/unspecified"));
    }

    public void testFlavoredLib() throws Exception {
        Map<String, ProjectData> map = getModelForMultiProject(FOLDER_TEST_SAMPLE, "flavoredlib");

        ProjectData appModelData = map.get(":app");
        assertNotNull("Module app null-check", appModelData);
        AndroidProject model = appModelData.model;

        assertFalse("Library Project", model.isLibrary());

        Collection<Variant> variants = model.getVariants();
        Collection<ProductFlavorContainer> productFlavors = model.getProductFlavors();

        ProductFlavorContainer flavor1 = getProductFlavor(productFlavors, "flavor1");
        assertNotNull(flavor1);

        Variant flavor1Debug = getVariant(variants, "flavor1Debug");
        assertNotNull(flavor1Debug);

        Dependencies dependencies = flavor1Debug.getMainArtifact().getDependencies();
        assertNotNull(dependencies);
        Collection<AndroidLibrary> libs = dependencies.getLibraries();
        assertNotNull(libs);
        assertEquals(1, libs.size());
        AndroidLibrary androidLibrary = libs.iterator().next();
        assertNotNull(androidLibrary);
        assertEquals(":lib", androidLibrary.getProject());
        assertEquals("flavor1Release", androidLibrary.getProjectVariant());
        // TODO: right now we can only test the folder name efficiently
        assertTrue(androidLibrary.getFolder().getPath(), androidLibrary.getFolder().getPath().endsWith("/flavoredlib/lib/unspecified/flavor1Release"));

        ProductFlavorContainer flavor2 = getProductFlavor(productFlavors, "flavor2");
        assertNotNull(flavor2);

        Variant flavor2Debug = getVariant(variants, "flavor2Debug");
        assertNotNull(flavor2Debug);

        dependencies = flavor2Debug.getMainArtifact().getDependencies();
        assertNotNull(dependencies);
        libs = dependencies.getLibraries();
        assertNotNull(libs);
        assertEquals(1, libs.size());
        androidLibrary = libs.iterator().next();
        assertNotNull(androidLibrary);
        assertEquals(":lib", androidLibrary.getProject());
        assertEquals("flavor2Release", androidLibrary.getProjectVariant());
        // TODO: right now we can only test the folder name efficiently
        assertTrue(androidLibrary.getFolder().getPath(), androidLibrary.getFolder().getPath().endsWith("/flavoredlib/lib/unspecified/flavor2Release"));
    }

    public void testMultiproject() throws Exception {
        Map<String, ProjectData> map = getModelForMultiProject(FOLDER_TEST_SAMPLE, "multiproject");

        ProjectData baseLibModelData = map.get(":baseLibrary");
        assertNotNull("Module app null-check", baseLibModelData);
        AndroidProject model = baseLibModelData.model;

        Collection<Variant> variants = model.getVariants();
        assertEquals("Variant count", 2, variants.size());

        Variant variant = getVariant(variants, "release");
        assertNotNull("release variant null-check", variant);

        AndroidArtifact mainInfo = variant.getMainArtifact();
        assertNotNull("Main Artifact null-check", mainInfo);

        Dependencies dependencies = mainInfo.getDependencies();
        assertNotNull("Dependencies null-check", dependencies);

        Collection<String> projects = dependencies.getProjects();
        assertNotNull("project dep list null-check", projects);
        assertEquals("project dep count", 1, projects.size());
        assertEquals("dep on :util check", ":util", projects.iterator().next());

        Collection<JavaLibrary> javaLibraries = dependencies.getJavaLibraries();
        assertNotNull("jar dep list null-check", javaLibraries);
        // TODO these are jars coming from ':util' They shouldn't be there.
        assertEquals("jar dep count", 2, javaLibraries.size());
    }


    public void testCustomArtifact() throws Exception {
        // Load the custom model for the projects
        Map<String, ProjectData> map = getModelForMultiProject(FOLDER_TEST_PROJECT,
                "customArtifactDep");

        ProjectData appModelData = map.get(":app");
        assertNotNull("Module app null-check", appModelData);
        AndroidProject model = appModelData.model;

        Collection<Variant> variants = model.getVariants();
        assertEquals("Variant count", 2, variants.size());

        Variant variant = getVariant(variants, "release");
        assertNotNull("release variant null-check", variant);

        AndroidArtifact mainInfo = variant.getMainArtifact();
        assertNotNull("Main Artifact null-check", mainInfo);

        Dependencies dependencies = mainInfo.getDependencies();
        assertNotNull("Dependencies null-check", dependencies);

        Collection<String> projects = dependencies.getProjects();
        assertNotNull("project dep list null-check", projects);
        assertTrue("project dep empty check", projects.isEmpty());

        Collection<JavaLibrary> javaLibraries = dependencies.getJavaLibraries();
        assertNotNull("jar dep list null-check", javaLibraries);
        assertEquals("jar dep count", 1, javaLibraries.size());
    }

    public void testLocalJarInLib() throws Exception {
        Map<String, ProjectData> map = getModelForMultiProject(FOLDER_TEST_SAMPLE, "localJars");

        ProjectData libModelData = map.get(":baseLibrary");
        assertNotNull("Module app null-check", libModelData);
        AndroidProject model = libModelData.model;

        Collection<Variant> variants = model.getVariants();

        Variant releaseVariant = getVariant(variants, "release");
        assertNotNull(releaseVariant);

        Dependencies dependencies = releaseVariant.getMainArtifact().getDependencies();
        assertNotNull(dependencies);

        Collection<JavaLibrary> javaLibraries = dependencies.getJavaLibraries();
        assertNotNull(javaLibraries);

        //  com.google.guava:guava:11.0.2
        //  \--- com.google.code.findbugs:jsr305:1.3.9
        //  + the local jar
        assertEquals(3, javaLibraries.size());
    }


    /**
     * Returns the root dir for the gradle plugin project
     */
    private File getRootDir() {
        CodeSource source = getClass().getProtectionDomain().getCodeSource();
        if (source != null) {
            URL location = source.getLocation();
            try {
                File dir = new File(location.toURI());
                assertTrue(dir.getPath(), dir.exists());

                File f;
                if (System.getenv("IDE_MODE") != null) {
                    f = dir.getParentFile().getParentFile().getParentFile();
                    f = new File(f, "build-system");
                } else {
                    f = dir.getParentFile().getParentFile().getParentFile().getParentFile().getParentFile().getParentFile().getParentFile();
                    f = new File(f, "tools" + File.separator + "base" + File.separator + "build-system");
                }
                return f;
            } catch (URISyntaxException e) {
                fail(e.getLocalizedMessage());
            }
        }

        fail("Fail to get the tools/build folder");
        return null;
    }

    /**
     * Returns the root folder for the tests projects.
     */
    private File getTestDir(@NonNull String testFolder) {
        File rootDir = getRootDir();
        return new File(new File(rootDir, "integration-test"), testFolder);
    }

    @Nullable
    private static Variant getVariant(
            @NonNull Collection<Variant> items,
            @NonNull String name) {
        for (Variant item : items) {
            if (name.equals(item.getName())) {
                return item;
            }
        }

        return null;
    }

    @Nullable
    private static ProductFlavorContainer getProductFlavor(
            @NonNull Collection<ProductFlavorContainer> items,
            @NonNull String name) {
        for (ProductFlavorContainer item : items) {
            assertNotNull("ProductFlavorContainer list item null-check:" + name, item);
            assertNotNull("ProductFlavorContainer.getProductFlavor() list item null-check: " + name, item.getProductFlavor());
            assertNotNull("ProductFlavorContainer.getProductFlavor().getName() list item null-check: " + name, item.getProductFlavor().getName());
            if (name.equals(item.getProductFlavor().getName())) {
                return item;
            }
        }

        return null;
    }
}
