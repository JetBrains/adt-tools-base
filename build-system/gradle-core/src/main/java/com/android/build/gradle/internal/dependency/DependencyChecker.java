/*
 * Copyright (C) 2012 The Android Open Source Project
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
package com.android.build.gradle.internal.dependency;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.core.SyncIssueHandler;
import com.android.builder.core.VariantType;
import com.android.builder.dependency.DependencyContainer;
import com.android.builder.dependency.MavenCoordinatesImpl;
import com.android.builder.dependency.SkippableLibrary;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.JavaLibrary;
import com.android.builder.model.MavenCoordinates;
import com.android.builder.model.SyncIssue;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import org.gradle.api.artifacts.ModuleVersionIdentifier;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Checks for dependencies to ensure Android compatibility
 */
public class DependencyChecker implements SyncIssueHandler {

    @NonNull
    private final String projectName;
    @NonNull
    private final String variantName;
    @NonNull
    private SyncIssueHandler syncIssueHandler;
    @NonNull
    private VariantType variantType;
    @Nullable
    private final VariantType testedVariantType;

    private final List<SyncIssue> syncIssues = Lists.newArrayList();

    /**
     * Contains API levels obtained from dependencies on the legacy com.google.android:android
     * artifact. Keys are specific versions of the artifact, values are the corresponding API
     * levels.
     */
    @NonNull
    private final Map<ModuleVersionIdentifier, Integer> legacyApiLevels = Maps.newHashMap();

    public DependencyChecker(
            @NonNull String projectName,
            @NonNull String variantName,
            @NonNull SyncIssueHandler syncIssueHandler,
            @NonNull VariantType variantType,
            @Nullable VariantType testedVariantType) {
        this.projectName = projectName;
        this.variantName = variantName;
        this.syncIssueHandler = syncIssueHandler;
        this.variantType = variantType;
        this.testedVariantType = testedVariantType;
    }

    @NonNull
    public Map<ModuleVersionIdentifier, Integer> getLegacyApiLevels() {
        return legacyApiLevels;
    }

    @NonNull
    public List<SyncIssue> getSyncIssues() {
        return syncIssues;
    }

    @NonNull
    public String getProjectName() {
        return projectName;
    }

    @NonNull
    public String getVariantName() {
        return variantName;
    }

    /**
     * Validate the dependencies after they have been fully resolved.
     *
     * This will compare the compile/package graphs, as well as the graphs of an optional
     * tested variant.
     *
     *
     * @param compileDependencies the compile dependency graph
     * @param packagedDependencies the packaged dependency graph
     * @param testedVariantDeps an optional tested dependencies.
     */
    public void validate(
            @NonNull DependencyContainer compileDependencies,
            @NonNull DependencyContainer packagedDependencies,
            @Nullable VariantDependencies testedVariantDeps) {
        // tested map if applicable.
        Map<String, String> testedMap = computeTestedDependencyMap(testedVariantDeps);

        compareAndroidDependencies(
                compileDependencies.getAndroidDependencies(),
                packagedDependencies.getAndroidDependencies(),
                testedMap);

        compareJavaDependencies(
                compileDependencies.getJarDependencies(),
                packagedDependencies.getJarDependencies(),
                compileDependencies.getAndroidDependencies(),
                packagedDependencies.getAndroidDependencies(),
                testedMap);
    }


    /**
     * Checks if a given module should just be excluded from the dependency graph.
     *
     * @param id the module coordinates.
     *
     * @return true if the module should be excluded.
     */
    public boolean checkForExclusion(@NonNull ModuleVersionIdentifier id) {
        String group = id.getGroup();
        String name = id.getName();
        String version = id.getVersion();

        if ("com.google.android".equals(group) && "android".equals(name)) {
            int moduleLevel = getApiLevelFromMavenArtifact(version);
            legacyApiLevels.put(id, moduleLevel);

            handleIssue(
                    id.toString(),
                    SyncIssue.TYPE_DEPENDENCY_MAVEN_ANDROID,
                    SyncIssue.SEVERITY_WARNING,
                    String.format("Ignoring Android API artifact %s for %s", id, variantName));
            return true;
        }

        if (variantType == VariantType.UNIT_TEST) {
            return false;
        }

        if (("org.apache.httpcomponents".equals(group) && "httpclient".equals(name)) ||
                ("xpp3".equals(group) && name.equals("xpp3")) ||
                ("commons-logging".equals(group) && "commons-logging".equals(name)) ||
                ("xerces".equals(group) && "xmlParserAPIs".equals(name)) ||
                ("org.json".equals(group) && "json".equals(name)) ||
                ("org.khronos".equals(group) && "opengl-api".equals(name))) {

            handleIssue(
                    id.toString(),
                    SyncIssue.TYPE_DEPENDENCY_INTERNAL_CONFLICT,
                    SyncIssue.SEVERITY_WARNING,
                    String.format(
                            "WARNING: Dependency %s is ignored for %s as it may be conflicting with the internal version provided by Android.\n"
                                    +
                                    "         In case of problem, please repackage it with jarjar to change the class packages",
                            id, variantName));
            return true;
        }

        return false;
    }

    private static int getApiLevelFromMavenArtifact(@NonNull String version) {
        switch (version) {
            case "1.5_r3":
            case "1.5_r4":
                return 3;
            case "1.6_r2":
                return 4;
            case "2.1_r1":
            case "2.1.2":
                return 7;
            case "2.2.1":
                return 8;
            case "2.3.1":
                return 9;
            case "2.3.3":
                return 10;
            case "4.0.1.2":
                return 14;
            case "4.1.1.4":
                return 15;
        }

        return -1;
    }

    private void compareAndroidDependencies(
            @NonNull List<AndroidLibrary> compileLibs,
            @NonNull List<AndroidLibrary> packageLibs,
            @NonNull Map<String, String> testedMap) {
        // For Libraries:
        // Only library projects can support provided aar.
        // However, package(publish)-only are still not supported (they don't make sense).
        // For now, provided only dependencies will be kept normally in the compile-graph.
        // However we'll want to not include them in the resource merging.
        // For Applications (and testing variants
        // All Android libraries must be in both lists.

        Map<String, SkippableLibrary> compileMap = computeAndroidDependencyMap(compileLibs);
        Map<String, SkippableLibrary> packageMap = computeAndroidDependencyMap(packageLibs);

        // go through the list of keys on the compile map, comparing to the package and the tested
        // one.

        for (String coordinateKey : compileMap.keySet()) {
            SkippableLibrary compileLib = compileMap.get(coordinateKey);
            SkippableLibrary packageMatch = packageMap.get(coordinateKey);

            MavenCoordinates resolvedCoordinates = compileLib.getResolvedCoordinates();

            if (packageMatch != null) {
                // found a match. Compare to tested, and skip if needed.
                skipTestDependency(packageMatch, testedMap);

                // remove it from the list of package dependencies
                packageMap.remove(coordinateKey);

                // compare versions.
                if (!resolvedCoordinates.getVersion()
                        .equals(packageMatch.getResolvedCoordinates().getVersion())) {
                    // wrong version, handle the error.
                    handleIssue(
                            coordinateKey,
                            SyncIssue.TYPE_MISMATCH_DEP,
                            SyncIssue.SEVERITY_ERROR,
                            String.format(
                                    "Conflict with dependency '%s'. Resolved versions for"
                                            + " compilation (%s) and packaging (%s) differ. This can "
                                            + "generate runtime errors due to mismatched resources.",
                                    coordinateKey,
                                    resolvedCoordinates.getVersion(),
                                    packageMatch.getResolvedCoordinates().getVersion()));
                }
            } else {
                // no match, means this is a provided only dependency, which is only
                // possibly if the variant is a library.
                // However we also mark as skipped dependency coming from app module that are
                // tested with a separate module. So if the library is skipped, just ignore it.
                if (!compileLib.isSkipped() &&
                        (variantType != VariantType.LIBRARY && (testedVariantType != VariantType.LIBRARY
                                || !variantType.isForTesting()))) {
                    handleIssue(
                            resolvedCoordinates.toString(),
                            SyncIssue.TYPE_NON_JAR_PROVIDED_DEP,
                            SyncIssue.SEVERITY_ERROR,
                            String.format(
                                    "Project %s: Provided dependencies can only be jars. %s is an Android Library.",
                                    projectName,
                                    resolvedCoordinates.toString()));
                }
            }
        }

        // at this time, packageMap will only contain package-only dependencies.
        // which is not possible here, so flag them.
        for (SkippableLibrary packageOnlyLib : packageMap.values()) {
            MavenCoordinates packagedCoords = packageOnlyLib.getResolvedCoordinates();
            handleIssue(
                    packagedCoords.toString(),
                    SyncIssue.TYPE_NON_JAR_PACKAGE_DEP,
                    SyncIssue.SEVERITY_ERROR,
                    String.format(
                            "Project %s: apk-only dependencies can only be jars. %s is an Android Library.",
                            projectName, packagedCoords));
        }
    }

    private void compareJavaDependencies(
            @NonNull List<JavaLibrary> compileJars,
            @NonNull List<JavaLibrary> packageJars,
            @NonNull List<AndroidLibrary> compileLibs,
            @NonNull List<AndroidLibrary> packageLibs,
            @NonNull Map<String, String> testedMap) {
        Map<String, SkippableLibrary> compileMap = computeJavaDependencyMap(compileJars, compileLibs);
        Map<String, SkippableLibrary> packageMap = computeJavaDependencyMap(packageJars, packageLibs);

        // go through the list of keys on the compile map, comparing to the package and the tested
        // one.

        for (String coordinateKey : compileMap.keySet()) {
            SkippableLibrary packageMatch = packageMap.get(coordinateKey);

            if (packageMatch != null) {
                // found a match. Compare to tested, and skip if needed.
                skipTestDependency(packageMatch, testedMap);

                // remove it from the list of package dependencies
                packageMap.remove(coordinateKey);
            }
        }
    }

    private void skipTestDependency(
            @NonNull SkippableLibrary library,
            @NonNull Map<String, String> testedMap) {
        if (testedMap.isEmpty()) {
            return;
        }

        MavenCoordinates coordinates = library.getResolvedCoordinates();
        String testedVersion = testedMap.get(computeVersionLessCoordinateKey(coordinates));

        // if there is no similar version in the test dependencies, nothing to do.
        if (testedVersion == null) {
            return;
        }

        // same artifact, skip packaging of the dependency in the test app
        // whether the version is a match or not.
        library.skip();

        // if the dependency is present in both tested and test artifact,
        // verify that they are the same version
        if (!testedVersion.equals(coordinates.getVersion())) {
            String artifactInfo =  coordinates.getGroupId() + ":" + coordinates.getArtifactId();
            handleIssue(
                    artifactInfo,
                    SyncIssue.TYPE_MISMATCH_DEP,
                    SyncIssue.SEVERITY_ERROR,
                    String.format(
                            "Conflict with dependency '%s'. Resolved versions for"
                                    + " app (%s) and test app (%s) differ. See"
                                    + " http://g.co/androidstudio/app-test-app-conflict"
                                    + " for details.",
                            artifactInfo,
                            testedVersion,
                            coordinates.getVersion()));
        }
    }
    /**
     * Returns a map representing the tested dependencies. This represents only the packaged
     * ones as they are the one that matters when figuring out what to skip in the test
     * graphs.
     *
     * The map represents (dependency key, version) where the key is basically
     * the coordinates minus the version.
     *
     * @return the map
     *
     * @see #computeVersionLessCoordinateKey(MavenCoordinates)
     */
    private static Map<String, String> computeTestedDependencyMap(
            @Nullable VariantDependencies testedVariantDeps) {
        if (testedVariantDeps == null) {
            return ImmutableMap.of();
        }

        DependencyContainer packageDependencies = testedVariantDeps.getPackageDependencies();

        List<JavaLibrary> testedJars = packageDependencies.getJarDependencies();
        List<AndroidLibrary> testedLibs = packageDependencies.getAndroidDependencies();

        Map<String, String> map = Maps.newHashMapWithExpectedSize(
                testedJars.size() + testedLibs.size());

        fillTestedDependencyMapWithJars(testedJars, map);
        fillTestedDependencyMapWithAars(testedLibs, map);

        return map;
    }

    private static void fillTestedDependencyMapWithJars(
            @NonNull Collection<? extends JavaLibrary> dependencies,
            @NonNull Map<String, String> map) {
        for (JavaLibrary javaLibrary : dependencies) {
            if (javaLibrary.getProject() == null) {
                MavenCoordinates coordinates = javaLibrary.getResolvedCoordinates();
                map.put(
                        computeVersionLessCoordinateKey(coordinates),
                        coordinates.getVersion());
            }

            // then the transitive dependencies.
            fillTestedDependencyMapWithJars(javaLibrary.getDependencies(), map);
        }
    }

    private static void fillTestedDependencyMapWithAars(
            @NonNull List<? extends AndroidLibrary> dependencies,
            @NonNull Map<String, String> map) {
        for (AndroidLibrary androidLibrary : dependencies) {
            if (androidLibrary.getProject() == null) {
                MavenCoordinates coordinates = androidLibrary.getResolvedCoordinates();

                map.put(
                        computeVersionLessCoordinateKey(coordinates),
                        coordinates.getVersion());
            }

            // then the transitive dependencies
            fillTestedDependencyMapWithAars(androidLibrary.getLibraryDependencies(), map);
            fillTestedDependencyMapWithJars(androidLibrary.getJavaDependencies(), map);
        }
    }

    /**
     * Computes a map of (key, library) for all android libraries (direct and transitives).
     *
     * The key is either the gradle project path or the maven coordinates. The format of each
     * makes it impossible to have collisions.
     *
     * @param androidDependencies the dependencies
     * @return the map.
     */
    private static Map<String, SkippableLibrary> computeAndroidDependencyMap(
            @NonNull List<AndroidLibrary> androidDependencies) {

        Map<String, SkippableLibrary> map = Maps.newHashMapWithExpectedSize(
                androidDependencies.size());

        fillDependencyMapWithAars(androidDependencies, map, true, false);

        return map;
    }

    /**
     * Computes a map of (key, library) for all java libraries (direct and transitives).
     *
     * The key is either the gradle project path or the maven coordinates. The format of each
     * makes it impossible to have collisions.
     *
     * This receives both Java dependencies and Android dependencies since the latter can
     * have transitive java dependencies.
     *
     * @param javaDependencies the java dependencies
     * @param androidDependencies the android dependencies
     * @return the map.
     */
    private static Map<String, SkippableLibrary> computeJavaDependencyMap(
            @NonNull List<JavaLibrary> javaDependencies,
            @NonNull List<AndroidLibrary> androidDependencies) {

        Map<String, SkippableLibrary> map = Maps.newHashMapWithExpectedSize(
                javaDependencies.size() + androidDependencies.size());

        fillDependencyMapWithJars(javaDependencies, map);
        fillDependencyMapWithAars(androidDependencies, map, false, true);

        return map;
    }

    private static void fillDependencyMapWithJars(
            @NonNull Collection<? extends JavaLibrary> dependencies,
            @NonNull Map<String, SkippableLibrary> map) {
        for (JavaLibrary javaLibrary : dependencies) {
            if (javaLibrary.getProject() != null) {
                map.put(javaLibrary.getProject(), (SkippableLibrary) javaLibrary);
            } else {
                MavenCoordinates coordinates = javaLibrary.getResolvedCoordinates();
                map.put(
                        computeVersionLessCoordinateKey(coordinates),
                        (SkippableLibrary) javaLibrary);

            }

            // then the transitive dependencies.
            fillDependencyMapWithJars(javaLibrary.getDependencies(), map);
        }
    }

    private static void fillDependencyMapWithAars(
            @NonNull List<? extends AndroidLibrary> dependencies,
            @NonNull Map<String, SkippableLibrary> map,
            boolean includeAars,
            boolean includeJars) {
        for (AndroidLibrary androidLibrary : dependencies) {
            if (includeAars) {
                if (androidLibrary.getProject() != null) {
                    map.put(androidLibrary.getProject(), (SkippableLibrary) androidLibrary);
                } else {
                    MavenCoordinates coordinates = androidLibrary.getResolvedCoordinates();
                    map.put(
                            computeVersionLessCoordinateKey(coordinates),
                            (SkippableLibrary) androidLibrary);
                }
            }

            // then the transitive dependencies.
            if (includeAars) {
                fillDependencyMapWithAars(androidLibrary.getLibraryDependencies(), map,
                        true, includeJars);
            }
            if (includeJars) {
                fillDependencyMapWithJars(androidLibrary.getJavaDependencies(), map);
            }

        }
    }


    /**
     * Compute a version-less key representing the given coordinates.
     * @param coordinates the coordinates
     * @return the key.
     */
    @NonNull
    public static String computeVersionLessCoordinateKey(@NonNull MavenCoordinates coordinates) {
        if (coordinates instanceof MavenCoordinatesImpl) {
            return ((MavenCoordinatesImpl) coordinates).getVersionLessId();
        }
        StringBuilder sb = new StringBuilder(coordinates.getGroupId());
        sb.append(':').append(coordinates.getArtifactId());
        if (coordinates.getClassifier() != null) {
            sb.append(':').append(coordinates.getClassifier());
        }
        return sb.toString();
    }


    @NonNull
    @Override
    public SyncIssue handleIssue(@Nullable String data, int type, int severity,
            @NonNull String msg) {
        SyncIssue issue = syncIssueHandler.handleIssue(data, type, severity, msg);
        syncIssues.add(issue);
        return issue;
    }
}
