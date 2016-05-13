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

package com.android.build.gradle.internal;

import static com.android.SdkConstants.DOT_JAR;
import static com.android.SdkConstants.EXT_ANDROID_PACKAGE;
import static com.android.SdkConstants.EXT_JAR;
import static com.android.build.gradle.internal.dependency.DependencyChecker.computeVersionLessCoordinateKey;
import static com.android.builder.core.BuilderConstants.EXT_LIB_ARCHIVE;
import static com.android.builder.core.ErrorReporter.EvaluationMode.STANDARD;
import static com.android.builder.model.AndroidProject.FD_INTERMEDIATES;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.internal.scope.AndroidTask;
import com.android.builder.dependency.DependencyContainerImpl;
import com.android.builder.dependency.LibraryDependency;
import com.android.build.gradle.internal.dependency.VariantDependencies;
import com.android.build.gradle.internal.tasks.PrepareDependenciesTask;
import com.android.build.gradle.internal.tasks.PrepareLibraryTask;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.build.gradle.internal.variant.BaseVariantOutputData;
import com.android.builder.dependency.DependencyContainer;
import com.android.builder.dependency.JarDependency;
import com.android.builder.dependency.MavenCoordinatesImpl;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.JavaLibrary;
import com.android.builder.model.MavenCoordinates;
import com.android.builder.model.SyncIssue;
import com.android.builder.sdk.SdkLibData;
import com.android.utils.ILogger;
import com.google.common.base.Joiner;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

import org.gradle.api.CircularReferenceException;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.UnknownProjectException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.SelfResolvingDependency;
import org.gradle.api.artifacts.UnresolvedDependency;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.artifacts.result.UnresolvedDependencyResult;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.specs.Specs;
import org.gradle.util.GUtil;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;


/**
 * A manager to resolve configuration dependencies.
 */
public class DependencyManager {

    private static final boolean DEBUG_DEPENDENCY = false;
    private final Project project;
    private final ExtraModelInfo extraModelInfo;
    private final ILogger logger;
    private final SdkHandler sdkHandler;
    private SdkLibData sdkLibData =  SdkLibData.dontDownload();
    private boolean repositoriesUpdated = false;

    private final Map<String, PrepareLibraryTask> prepareLibTaskMap = Maps.newHashMap();

    public DependencyManager(
            @NonNull Project project,
            @NonNull ExtraModelInfo extraModelInfo,
            @NonNull SdkHandler sdkHandler) {
        this.project = project;
        this.extraModelInfo = extraModelInfo;
        this.sdkHandler = sdkHandler;
        logger = new LoggerWrapper(Logging.getLogger(DependencyManager.class));
    }

    public void addDependenciesToPrepareTask(
            @NonNull TaskFactory tasks,
            @NonNull BaseVariantData<? extends BaseVariantOutputData> variantData,
            @NonNull AndroidTask<PrepareDependenciesTask> prepareDependenciesTask) {
        VariantDependencies variantDeps = variantData.getVariantDependency();
        for (AndroidLibrary androidLibrary : variantDeps.getCompileDependencies().getAndroidDependencies()) {
            addDependencyToPrepareTask(tasks, androidLibrary, prepareDependenciesTask,
                    variantData.getScope().getPreBuildTask());
        }

        for (AndroidLibrary androidLibrary : variantDeps.getPackageDependencies().getAndroidDependencies()) {
            addDependencyToPrepareTask(tasks, androidLibrary, prepareDependenciesTask,
                    variantData.getScope().getPreBuildTask());
        }
    }

    private void addDependencyToPrepareTask(
            @NonNull TaskFactory tasks,
            @NonNull AndroidLibrary lib,
            @NonNull AndroidTask<PrepareDependenciesTask> prepareDependenciesTask,
            @NonNull AndroidTask<DefaultTask> preBuildTask) {
        PrepareLibraryTask prepareLibTask = prepareLibTaskMap.get(lib.getResolvedCoordinates().toString());
        if (prepareLibTask != null) {
            prepareDependenciesTask.dependsOn(tasks, prepareLibTask);
            prepareLibTask.dependsOn(preBuildTask.getName());
        }

        for (AndroidLibrary childLib : lib.getLibraryDependencies()) {
            addDependencyToPrepareTask(tasks, childLib, prepareDependenciesTask, preBuildTask);
        }
    }

    public void resolveDependencies(
            @NonNull VariantDependencies variantDeps,
            @Nullable VariantDependencies testedVariantDeps,
            @Nullable String testedProjectPath) {
        Multimap<AndroidLibrary, Configuration> reverseLibMap = ArrayListMultimap.create();

        resolveDependencyForConfig(variantDeps, testedVariantDeps, testedProjectPath, reverseLibMap);

        processLibraries(reverseLibMap);
    }

    private void processLibraries(@NonNull Multimap<AndroidLibrary, Configuration> reverseMap) {
        for (AndroidLibrary lib : reverseMap.keySet()) {
            setupPrepareLibraryTask(lib, reverseMap.get(lib));
        }
    }

    private void setupPrepareLibraryTask(
            @NonNull AndroidLibrary androidLibrary,
            @Nullable Collection<Configuration> configurationList) {
        Task task = maybeCreatePrepareLibraryTask(androidLibrary, project);

        // Use the reverse map to find all the configurations that included this android
        // library so that we can make sure they are built.
        // TODO fix, this is not optimum as we bring in more dependencies than we should.
        if (configurationList != null && !configurationList.isEmpty()) {
            for (Configuration configuration: configurationList) {
                task.dependsOn(configuration.getBuildDependencies());
            }
        }

        // check if this library is created by a parent (this is based on the
        // output file.
        // TODO Fix this as it's fragile
            /*
            This is a somewhat better way but it doesn't work in some project with
            weird setups...
            Project parentProject = DependenciesImpl.getProject(library.getBundle(), projects)
            if (parentProject != null) {
                String configName = library.getProjectVariant()
                if (configName == null) {
                    configName = "default"
                }

                prepareLibraryTask.dependsOn parentProject.getPath() + ":assemble${configName.capitalize()}"
            }
*/

    }

    /**
     * Handles the library and returns a task to "prepare" the library (ie unarchive it). The task
     * will be reused for all projects using the same library.
     *
     * @param library the library.
     * @param project the project
     * @return the prepare task.
     */
    private PrepareLibraryTask maybeCreatePrepareLibraryTask(
            @NonNull AndroidLibrary library,
            @NonNull Project project) {
        LibraryDependency lib = (LibraryDependency) library;

        // create proper key for the map. library here contains all the dependencies which
        // are not relevant for the task (since the task only extract the aar which does not
        // include the dependencies.
        // However there is a possible case of a rewritten dependencies (with resolution strategy)
        // where the aar here could have different dependencies, in which case we would still
        // need the same task.
        // So we extract a AbstractBundleDependency (no dependencies) from the LibraryDependency to
        // make the map key that doesn't take into account the dependencies.
        String key = library.getResolvedCoordinates().toString();

        PrepareLibraryTask prepareLibraryTask = prepareLibTaskMap.get(key);

        if (prepareLibraryTask == null) {
            String bundleName = GUtil.toCamelCase(lib.getName().replaceAll("\\:", " "));

            prepareLibraryTask = project.getTasks().create(
                    "prepare" + bundleName + "Library", PrepareLibraryTask.class);

            prepareLibraryTask.setDescription("Prepare " + lib.getName());
            prepareLibraryTask.setBundle(lib.getBundle());
            prepareLibraryTask.setExplodedDir(lib.getFolder());
            prepareLibraryTask.setVariantName("");

            prepareLibTaskMap.put(key, prepareLibraryTask);
        }

        return prepareLibraryTask;
    }

    private void resolveDependencyForConfig(
            @NonNull final VariantDependencies variantDeps,
            @Nullable VariantDependencies testedVariantDeps,
            @Nullable String testedProjectPath,
            @NonNull Multimap<AndroidLibrary, Configuration> reverseLibMap) {

        Configuration compileClasspath = variantDeps.getCompileConfiguration();
        Configuration packageClasspath = variantDeps.getPackageConfiguration();

        // TODO - shouldn't need to do this - fix this in Gradle
        ensureConfigured(compileClasspath);
        ensureConfigured(packageClasspath);

        if (DEBUG_DEPENDENCY) {
            System.out.println(">>>>>>>>>>");
            System.out.println(
                    project.getName() + ":" +
                            compileClasspath.getName() + "/" +
                            packageClasspath.getName());
        }

        Set<String> currentUnresolvedDependencies = Sets.newHashSet();

        // records the artifact we find during package, to detect provided only dependencies.
        Set<String> artifactSet = Sets.newHashSet();

        // start with package dependencies, record the artifacts
        DependencyContainer packagedDependencies = gatherDependencies(
                packageClasspath,
                variantDeps,
                reverseLibMap,
                currentUnresolvedDependencies,
                testedProjectPath,
                artifactSet,
                true /*recordArtifactSet*/);

        // then the compile dependencies, comparing against the record package dependencies
        // to set the provided flag.
        DependencyContainer compileDependencies = gatherDependencies(
                compileClasspath,
                variantDeps,
                reverseLibMap,
                currentUnresolvedDependencies,
                testedProjectPath,
                artifactSet,
                false /*recordArtifactSet*/);

        if (extraModelInfo.getMode() != STANDARD &&
                compileClasspath.getResolvedConfiguration().hasError()) {
            for (String dependency : currentUnresolvedDependencies) {
                extraModelInfo.handleSyncError(
                        dependency,
                        SyncIssue.TYPE_UNRESOLVED_DEPENDENCY,
                        String.format(
                                "Unable to resolve dependency '%s'",
                                dependency));
            }
        }

        // validate the dependencies.
        variantDeps.getChecker().validate(compileDependencies, packagedDependencies, testedVariantDeps);

        if (DEBUG_DEPENDENCY) {
            System.out.println("*** COMPILE DEPS ***");
            for (AndroidLibrary lib : compileDependencies.getAndroidDependencies()) {
                System.out.println("LIB: " + lib);
            }
            for (JavaLibrary jar : compileDependencies.getJarDependencies()) {
                System.out.println("JAR: " + jar);
            }
            for (JavaLibrary jar : compileDependencies.getLocalDependencies()) {
                System.out.println("LOCAL-JAR: " + jar);
            }
            System.out.println("*** PACKAGE DEPS ***");
            for (AndroidLibrary lib : packagedDependencies.getAndroidDependencies()) {
                System.out.println("LIB: " + lib);
            }
            for (JavaLibrary jar : packagedDependencies.getJarDependencies()) {
                System.out.println("JAR: " + jar);
            }
            for (JavaLibrary jar : packagedDependencies.getLocalDependencies()) {
                System.out.println("LOCAL-JAR: " + jar);
            }
            System.out.println("***");
        }

        variantDeps.setDependencies(compileDependencies, packagedDependencies);

        configureBuild(variantDeps);

        if (DEBUG_DEPENDENCY) {
            System.out.println(project.getName() + ":" + compileClasspath.getName() + "/" +packageClasspath.getName());
            System.out.println("<<<<<<<<<<");
        }
    }

    @NonNull
    private DependencyContainer gatherDependencies(
            @NonNull Configuration configuration,
            @NonNull final VariantDependencies variantDeps,
            @NonNull Multimap<AndroidLibrary, Configuration> reverseLibMap,
            @NonNull Set<String> currentUnresolvedDependencies,
            @Nullable String testedProjectPath,
            @NonNull Set<String> artifactSet,
            boolean recordArtifactSet) {

        // collect the artifacts first.
        Map<ModuleVersionIdentifier, List<ResolvedArtifact>> artifacts = Maps.newHashMap();
        configuration = collectArtifacts(configuration, artifacts);

        // keep a map of modules already processed so that we don't go through sections of the
        // graph that have been seen elsewhere.
        Map<ModuleVersionIdentifier, List<LibraryDependency>> foundLibraries = Maps.newHashMap();
        Map<ModuleVersionIdentifier, List<JarDependency>> foundJars = Maps.newHashMap();

        // get the graph for the Android and Jar dependencies. This does not include
        // local jars.
        List<LibraryDependency> libraryDependencies = Lists.newArrayList();
        List<JarDependency> jarDependencies = Lists.newArrayList();

        Set<? extends DependencyResult> dependencyResultSet = configuration.getIncoming()
                .getResolutionResult().getRoot().getDependencies();

        for (DependencyResult dependencyResult : dependencyResultSet) {
            if (dependencyResult instanceof ResolvedDependencyResult) {
                addDependency(
                        ((ResolvedDependencyResult) dependencyResult).getSelected(),
                        variantDeps,
                        configuration,
                        libraryDependencies,
                        jarDependencies,
                        foundLibraries,
                        foundJars,
                        artifacts,
                        reverseLibMap,
                        currentUnresolvedDependencies,
                        testedProjectPath,
                        Collections.emptyList(),
                        artifactSet,
                        recordArtifactSet,
                        false, /*forceProvided*/
                        0);
            } else if (dependencyResult instanceof UnresolvedDependencyResult) {
                ComponentSelector attempted = ((UnresolvedDependencyResult) dependencyResult).getAttempted();
                if (attempted != null) {
                    currentUnresolvedDependencies.add(attempted.toString());
                }
            }
        }

        // also need to process local jar files, as they are not processed by the
        // resolvedConfiguration result. This only includes the local jar files for this project.
        List<JarDependency> localJars = Lists.newArrayList();
        for (Dependency dependency : configuration.getAllDependencies()) {
            if (dependency instanceof SelfResolvingDependency &&
                    !(dependency instanceof ProjectDependency)) {
                Set<File> files = ((SelfResolvingDependency) dependency).resolve();
                for (File localJarFile : files) {
                    if (DEBUG_DEPENDENCY) {
                        System.out.println("LOCAL " + configuration.getName() + ": " + localJarFile.getName());
                    }
                    // only accept local jar, no other types.
                    if (!localJarFile.getName().toLowerCase(Locale.getDefault()).endsWith(DOT_JAR)) {
                        variantDeps.getChecker().handleIssue(
                                localJarFile.getAbsolutePath(),
                                SyncIssue.TYPE_NON_JAR_LOCAL_DEP,
                                SyncIssue.SEVERITY_ERROR,
                                String.format(
                                        "Project %s: Only Jar-type local dependencies are supported. Cannot handle: %s",
                                        project.getName(), localJarFile.getAbsolutePath()));
                    } else {
                        JarDependency localJar;
                        if (recordArtifactSet) {
                            localJar = new JarDependency(localJarFile);
                            artifactSet.add(
                                    computeVersionLessCoordinateKey(localJar.getResolvedCoordinates()));
                        } else {
                            MavenCoordinates coord = JarDependency.getCoordForLocalJar(localJarFile);
                            boolean provided = !artifactSet
                                    .contains(computeVersionLessCoordinateKey(coord));

                            localJar = new JarDependency(
                                    localJarFile,
                                    ImmutableList.of(),
                                    coord,
                                    null,
                                    provided);
                        }
                        localJars.add(localJar);
                    }
                }
            }
        }

        return new DependencyContainerImpl(libraryDependencies, jarDependencies, localJars);
    }

    private void ensureConfigured(Configuration config) {
        for (Dependency dependency : config.getAllDependencies()) {
            if (dependency instanceof ProjectDependency) {
                ProjectDependency projectDependency = (ProjectDependency) dependency;
                project.evaluationDependsOn(projectDependency.getDependencyProject().getPath());
                try {
                    ensureConfigured(projectDependency.getProjectConfiguration());
                } catch (Throwable e) {
                    throw new UnknownProjectException(String.format(
                            "Cannot evaluate module %s : %s",
                            projectDependency.getName(), e.getMessage()),
                            e);
                }
            }
        }
    }



    /**
     * Collects the resolved artifacts and returns a configuration which contains them. If the
     * configuration has unresolved dependencies we check that we have the latest version of the
     * Google repository and the Android Support repository and we install them if not. After this,
     * the resolution is retried with a fresh copy of the configuration, that will contain the newly
     * updated repositories. If this passes, we return the correct configuration and we fill the
     * artifacts map.
     * @param configuration the configuration from which we get the artifacts
     * @param artifacts the map of artifacts that are being collected
     * @return a valid configuration that has only resolved dependencies.
     */
    private Configuration collectArtifacts(
            Configuration configuration,
            Map<ModuleVersionIdentifier,
            List<ResolvedArtifact>> artifacts) {

        Set<ResolvedArtifact> allArtifacts;
        // Make a copy because Gradle keeps a per configuration state of resolution that we
        // need to reset.
        Configuration configurationCopy = configuration.copyRecursive();

        Set<UnresolvedDependency> unresolvedDependencies =
                configuration
                        .getResolvedConfiguration()
                        .getLenientConfiguration()
                        .getUnresolvedModuleDependencies();

        if (unresolvedDependencies.isEmpty()) {
            allArtifacts = configuration.getResolvedConfiguration().getResolvedArtifacts();
        } else {
            if (!repositoriesUpdated && sdkLibData.useSdkDownload()) {
                for (UnresolvedDependency dependency : unresolvedDependencies) {
                    if (isGoogleOwnedDependency(dependency.getSelector())) {
                        List<File> updatedRepositories = sdkHandler.getSdkLoader()
                                .updateRepositories(sdkLibData, logger);
                        // Adding the updated local maven repositories to the project in order to
                        // bypass the fact that the old repositories contain the unresolved
                        // resolution result.
                        for (File updatedRepository : updatedRepositories) {
                            project.getRepositories().maven(mavenArtifactRepository -> {
                                mavenArtifactRepository.setUrl(updatedRepository.toURI());
                            });
                        }
                        repositoriesUpdated = true;
                        break;
                    }
                }
            }
            if (extraModelInfo.getMode() != STANDARD) {
                allArtifacts = configurationCopy.getResolvedConfiguration()
                        .getLenientConfiguration()
                        .getArtifacts(Specs.satisfyAll());
            } else {
                allArtifacts = configurationCopy.getResolvedConfiguration()
                        .getResolvedArtifacts();

            }
            // Modify the configuration to the one that passed.
            configuration = configurationCopy;
        }

        for (ResolvedArtifact artifact : allArtifacts) {
            ModuleVersionIdentifier id = artifact.getModuleVersion().getId();
            List<ResolvedArtifact> moduleArtifacts = artifacts.get(id);

            if (moduleArtifacts == null) {
                moduleArtifacts = Lists.newArrayList();
                artifacts.put(id, moduleArtifacts);
            }

            if (!moduleArtifacts.contains(artifact)) {
                moduleArtifacts.add(artifact);
            }
        }
        return configuration;
    }

    private boolean isGoogleOwnedDependency(ModuleVersionSelector selector) {
        return selector.getGroup().startsWith("com.google.android")
                ||  selector.getGroup().startsWith("com.android");
    }

    private static void printIndent(int indent, @NonNull String message) {
        for (int i = 0 ; i < indent ; i++) {
            System.out.print("\t");
        }

        System.out.println(message);
    }

    private void addDependency(
            @NonNull ResolvedComponentResult resolvedComponentResult,
            @NonNull VariantDependencies configDependencies,
            @NonNull Configuration configuration,
            @NonNull Collection<LibraryDependency> outLibraries,
            @NonNull List<JarDependency> outJars,
            @NonNull Map<ModuleVersionIdentifier, List<LibraryDependency>> alreadyFoundLibraries,
            @NonNull Map<ModuleVersionIdentifier, List<JarDependency>> alreadyFoundJars,
            @NonNull Map<ModuleVersionIdentifier, List<ResolvedArtifact>> artifacts,
            @NonNull Multimap<AndroidLibrary, Configuration> reverseLibMap,
            @NonNull Set<String> currentUnresolvedDependencies,
            @Nullable String testedProjectPath,
            @NonNull List<String> projectChain,
            @NonNull Set<String> artifactSet,
            boolean recordArtifactSet,
            boolean forceProvided,
            int indent) {

        ModuleVersionIdentifier moduleVersion = resolvedComponentResult.getModuleVersion();
        if (configDependencies.getChecker().checkForExclusion(moduleVersion)) {
            return;
        }

        if (moduleVersion.getName().equals("support-annotations") &&
                moduleVersion.getGroup().equals("com.android.support")) {
            configDependencies.setAnnotationsPresent(true);
        }

        List<LibraryDependency> libsForThisModule = alreadyFoundLibraries.get(moduleVersion);
        List<JarDependency> jarsForThisModule = alreadyFoundJars.get(moduleVersion);

        if (libsForThisModule != null) {
            if (DEBUG_DEPENDENCY) {
                printIndent(indent, "FOUND LIB: " + moduleVersion.getName());
            }
            outLibraries.addAll(libsForThisModule);

            for (AndroidLibrary lib : libsForThisModule) {
                reverseLibMap.put(lib, configuration);
            }

        } else if (jarsForThisModule != null) {
            if (DEBUG_DEPENDENCY) {
                printIndent(indent, "FOUND JAR: " + moduleVersion.getName());
            }
            outJars.addAll(jarsForThisModule);
        } else {
            if (DEBUG_DEPENDENCY) {
                printIndent(indent, "NOT FOUND: " + moduleVersion.getName());
            }
            // new module! Might be a jar or a library

            // get the associated gradlepath
            ComponentIdentifier id = resolvedComponentResult.getId();
            String gradlePath = (id instanceof ProjectComponentIdentifier) ?
                    ((ProjectComponentIdentifier) id).getProjectPath() : null;

            // check if this is a tested app project (via a separate test module).
            // In which case, all the dependencies must become provided.
            boolean childForceProvided = forceProvided;
            if (!recordArtifactSet && testedProjectPath != null && testedProjectPath.equals(gradlePath)) {
                childForceProvided = true;
            }

            // get the nested components first.
            List<LibraryDependency> nestedLibraries = Lists.newArrayList();
            List<JarDependency> nestedJars = Lists.newArrayList();

            Set<? extends DependencyResult> dependencies = resolvedComponentResult.getDependencies();
            for (DependencyResult dependencyResult : dependencies) {
                if (dependencyResult instanceof ResolvedDependencyResult) {
                    ResolvedComponentResult selected =
                            ((ResolvedDependencyResult) dependencyResult).getSelected();

                    List<String> newProjectChain = projectChain;

                    ComponentIdentifier identifier = selected.getId();
                    if (identifier instanceof ProjectComponentIdentifier) {
                        String projectPath =
                                ((ProjectComponentIdentifier) identifier).getProjectPath();

                        int index = projectChain.indexOf(projectPath);
                        if (index != -1) {
                            projectChain.add(projectPath);
                            String path = Joiner
                                    .on(" -> ")
                                    .join(projectChain.subList(index, projectChain.size()));

                            throw new CircularReferenceException(
                                    "Circular reference between projects: " + path);
                        }

                        newProjectChain = Lists.newArrayList();
                        newProjectChain.addAll(projectChain);
                        newProjectChain.add(projectPath);
                    }

                    addDependency(
                            selected,
                            configDependencies,
                            configuration,
                            nestedLibraries,
                            nestedJars,
                            alreadyFoundLibraries,
                            alreadyFoundJars,
                            artifacts,
                            reverseLibMap,
                            currentUnresolvedDependencies,
                            testedProjectPath,
                            newProjectChain,
                            artifactSet,
                            recordArtifactSet,
                            childForceProvided,
                            indent + 1);
                } else if (dependencyResult instanceof UnresolvedDependencyResult) {
                    ComponentSelector attempted = ((UnresolvedDependencyResult) dependencyResult).getAttempted();
                    if (attempted != null) {
                        currentUnresolvedDependencies.add(attempted.toString());
                    }
                }
            }

            if (DEBUG_DEPENDENCY) {
                printIndent(indent, "BACK2: " + moduleVersion.getName());
                printIndent(indent, "NESTED LIBS: " + nestedLibraries.size());
                printIndent(indent, "NESTED JARS: " + nestedJars.size());
            }

            // now loop on all the artifact for this modules.
            List<ResolvedArtifact> moduleArtifacts = artifacts.get(moduleVersion);

            if (moduleArtifacts != null) {
                for (ResolvedArtifact artifact : moduleArtifacts) {
                    MavenCoordinates mavenCoordinates = createMavenCoordinates(artifact);
                    boolean provided = forceProvided;
                    String coordKey = computeVersionLessCoordinateKey(mavenCoordinates);
                    if (recordArtifactSet) {
                        artifactSet.add(coordKey);
                    } else {
                        provided |= !artifactSet.contains(coordKey);
                    }

                    if (EXT_LIB_ARCHIVE.equals(artifact.getExtension())) {
                        if (DEBUG_DEPENDENCY) {
                            printIndent(indent, "TYPE: AAR");
                        }
                        if (libsForThisModule == null) {
                            libsForThisModule = Lists.newArrayList();
                            alreadyFoundLibraries.put(moduleVersion, libsForThisModule);
                        }

                        String path = computeArtifactPath(moduleVersion, artifact);
                        String name = computeArtifactName(moduleVersion, artifact);

                        if (DEBUG_DEPENDENCY) {
                            printIndent(indent, "NAME: " + name);
                            printIndent(indent, "PATH: " + path);
                        }

                        File explodedDir = project.file(project.getBuildDir() + "/" + FD_INTERMEDIATES + "/exploded-aar/" + path);

                        @SuppressWarnings("unchecked")
                        LibraryDependency LibraryDependency = new LibraryDependency(
                                artifact.getFile(),
                                explodedDir,
                                nestedLibraries,
                                nestedJars,
                                name,
                                artifact.getClassifier(),
                                gradlePath,
                                null /*requestedCoordinates*/,
                                mavenCoordinates,
                                provided);

                        libsForThisModule.add(LibraryDependency);
                        outLibraries.add(LibraryDependency);
                        reverseLibMap.put(LibraryDependency, configuration);

                    } else if (EXT_JAR.equals(artifact.getExtension())) {
                        if (DEBUG_DEPENDENCY) {
                            printIndent(indent, "TYPE: JAR");
                        }
                        // check this jar does not have a dependency on an library, as this would not work.
                        if (!nestedLibraries.isEmpty()) {
                            // there is one case where it's ok to have a jar depend on aars:
                            // when a test project tests a separate app project, the code of the
                            // app is published as a jar, but it brings in the dependencies
                            // of the app (which can be aars).
                            // we know we're in that case if testedProjectPath is non null, so we
                            // can detect this an accept it.
                            if (testedProjectPath != null && testedProjectPath.equals(gradlePath)) {
                                // for now we can only add them as out libraries for the current
                                // artifact (rather than the actual jar that is the tested code).
                                // But we nee to mark them as skipped since we don't want to
                                // include them.
                                // TODO: find a way to add them as children of the jar instead.
                                // TODO: we should take the jar only (of the aars). The rest doesn't matter.

                                // if this is a package scope, then skip the dependencies.
                                if (recordArtifactSet) {
                                    recursiveLibSkip(nestedLibraries);
                                } else {
                                    // if it's compile scope, make it optional.
                                    provided = true;
                                }

                                outLibraries.addAll(nestedLibraries);

                            } else {
                                configDependencies.getChecker()
                                        .handleIssue(
                                                createMavenCoordinates(artifact).toString(),
                                                SyncIssue.TYPE_JAR_DEPEND_ON_AAR,
                                                SyncIssue.SEVERITY_ERROR,
                                                String.format(
                                                        "Module '%s' depends on one or more Android Libraries but is a jar",
                                                        moduleVersion));
                            }
                        }

                        if (jarsForThisModule == null) {
                            jarsForThisModule = Lists.newArrayList();
                            alreadyFoundJars.put(moduleVersion, jarsForThisModule);
                        }

                        JarDependency jarDependency = new JarDependency(
                                artifact.getFile(),
                                nestedJars,
                                mavenCoordinates,
                                gradlePath,
                                provided);

                        // if package scope and the jar (and its dependencies) is from a tested
                        // app module then skip it.
                        if (recordArtifactSet && testedProjectPath != null && testedProjectPath.equals(gradlePath)) {
                            jarDependency.skip();

                            //noinspection unchecked
                            recursiveJavaSkip((List<JarDependency>) jarDependency.getDependencies());
                        }

                        if (DEBUG_DEPENDENCY) {
                            printIndent(indent, "JAR-INFO: " + jarDependency.toString());
                        }

                        jarsForThisModule.add(jarDependency);
                        outJars.add(jarDependency);

                    } else if (EXT_ANDROID_PACKAGE.equals(artifact.getExtension())) {
                        String name = computeArtifactName(moduleVersion, artifact);

                        configDependencies.getChecker().handleIssue(
                                name,
                                SyncIssue.TYPE_DEPENDENCY_IS_APK,
                                SyncIssue.SEVERITY_ERROR,
                                String.format(
                                        "Dependency %s on project %s resolves to an APK archive " +
                                        "which is not supported as a compilation dependency. File: %s",
                                        name, project.getName(), artifact.getFile()));
                    } else if ("apklib".equals(artifact.getExtension())) {
                        String name = computeArtifactName(moduleVersion, artifact);

                        configDependencies.getChecker().handleIssue(
                                name,
                                SyncIssue.TYPE_DEPENDENCY_IS_APKLIB,
                                SyncIssue.SEVERITY_ERROR,
                                String.format(
                                        "Packaging for dependency %s is 'apklib' and is not supported. " +
                                        "Only 'aar' libraries are supported.", name));
                    } else {
                        String name = computeArtifactName(moduleVersion, artifact);

                        logger.warning(String.format(
                                        "Unrecognized dependency: '%s' (type: '%s', extension: '%s')",
                                        name, artifact.getType(), artifact.getExtension()));
                    }
                }
            }

            if (DEBUG_DEPENDENCY) {
                printIndent(indent, "DONE: " + moduleVersion.getName());
            }
        }
    }

    @NonNull
    private static MavenCoordinates createMavenCoordinates(
            @NonNull ResolvedArtifact resolvedArtifact) {
        return new MavenCoordinatesImpl(
                resolvedArtifact.getModuleVersion().getId().getGroup(),
                resolvedArtifact.getModuleVersion().getId().getName(),
                resolvedArtifact.getModuleVersion().getId().getVersion(),
                resolvedArtifact.getExtension(),
                resolvedArtifact.getClassifier());
    }

    private static void recursiveLibSkip(@NonNull List<LibraryDependency> libs) {
        for (LibraryDependency lib : libs) {
            lib.skip();

            //noinspection unchecked
            recursiveLibSkip((List<LibraryDependency>) lib.getLibraryDependencies());
            //noinspection unchecked
            recursiveJavaSkip((List<JarDependency>) lib.getJavaDependencies());
        }
    }

    private static void recursiveJavaSkip(@NonNull List<JarDependency> libs) {
        for (JarDependency lib : libs) {
            lib.skip();

            //noinspection unchecked
            recursiveJavaSkip((List<JarDependency>) lib.getDependencies());
        }
    }

    @NonNull
    private String computeArtifactPath(
            @NonNull ModuleVersionIdentifier moduleVersion,
            @NonNull ResolvedArtifact artifact) {
        StringBuilder pathBuilder = new StringBuilder();

        pathBuilder.append(normalize(logger, moduleVersion, moduleVersion.getGroup()))
                .append('/')
                .append(normalize(logger, moduleVersion, moduleVersion.getName()))
                .append('/')
                .append(normalize(logger, moduleVersion,
                        moduleVersion.getVersion()));

        if (artifact.getClassifier() != null && !artifact.getClassifier().isEmpty()) {
            pathBuilder.append('/').append(normalize(logger, moduleVersion,
                    artifact.getClassifier()));
        }

        return pathBuilder.toString();
    }

    @NonNull
    private static String computeArtifactName(
            @NonNull ModuleVersionIdentifier moduleVersion,
            @NonNull ResolvedArtifact artifact) {
        StringBuilder nameBuilder = new StringBuilder();

        nameBuilder.append(moduleVersion.getGroup())
                .append(':')
                .append(moduleVersion.getName())
                .append(':')
                .append(moduleVersion.getVersion());

        if (artifact.getClassifier() != null && !artifact.getClassifier().isEmpty()) {
            nameBuilder.append(':').append(artifact.getClassifier());
        }

        return nameBuilder.toString();
    }

    /**
     * Normalize a path to remove all illegal characters for all supported operating systems.
     * {@see http://en.wikipedia.org/wiki/Filename#Comparison%5Fof%5Ffile%5Fname%5Flimitations}
     *
     * @param id the module coordinates that generated this path
     * @param path the proposed path name
     * @return the normalized path name
     */
    static String normalize(ILogger logger, ModuleVersionIdentifier id, String path) {
        if (path == null || path.isEmpty()) {
            logger.info(String.format(
                    "When unzipping library '%s:%s:%s, either group, name or version is empty",
                    id.getGroup(), id.getName(), id.getVersion()));
            return path;
        }
        // list of illegal characters
        String normalizedPath = path.replaceAll("[%<>:\"/?*\\\\]", "@");
        if (normalizedPath == null || normalizedPath.isEmpty()) {
            // if the path normalization failed, return the original path.
            logger.info(String.format(
                    "When unzipping library '%s:%s:%s, the normalized '%s' is empty",
                    id.getGroup(), id.getName(), id.getVersion(), path));
            return path;
        }
        try {
            int pathPointer = normalizedPath.length() - 1;
            // do not end your path with either a dot or a space.
            String suffix = "";
            while (pathPointer >= 0 && (normalizedPath.charAt(pathPointer) == '.'
                    || normalizedPath.charAt(pathPointer) == ' ')) {
                pathPointer--;
                suffix += "@";
            }
            if (pathPointer < 0) {
                throw new RuntimeException(String.format(
                        "When unzipping library '%s:%s:%s, " +
                        "the path '%s' cannot be transformed into a valid directory name",
                        id.getGroup(), id.getName(), id.getVersion(), path));
            }
            return normalizedPath.substring(0, pathPointer + 1) + suffix;
        } catch (Exception e) {
            logger.error(e, String.format(
                    "When unzipping library '%s:%s:%s', " +
                    "Path normalization failed for input %s",
                    id.getGroup(), id.getName(), id.getVersion(), path));
            return path;
        }
    }

    private void configureBuild(VariantDependencies configurationDependencies) {
        addDependsOnTaskInOtherProjects(
                project.getTasks().getByName(JavaBasePlugin.BUILD_NEEDED_TASK_NAME), true,
                JavaBasePlugin.BUILD_NEEDED_TASK_NAME, "compile");
        addDependsOnTaskInOtherProjects(
                project.getTasks().getByName(JavaBasePlugin.BUILD_DEPENDENTS_TASK_NAME), false,
                JavaBasePlugin.BUILD_DEPENDENTS_TASK_NAME, "compile");
    }

    /**
     * Adds a dependency on tasks with the specified name in other projects.  The other projects
     * are determined from project lib dependencies using the specified configuration name.
     * These may be projects this project depends on or projects that depend on this project
     * based on the useDependOn argument.
     *
     * @param task Task to add dependencies to
     * @param useDependedOn if true, add tasks from projects this project depends on, otherwise
     * use projects that depend on this one.
     * @param otherProjectTaskName name of task in other projects
     * @param configurationName name of configuration to use to find the other projects
     */
    private static void addDependsOnTaskInOtherProjects(final Task task, boolean useDependedOn,
            String otherProjectTaskName,
            String configurationName) {
        Project project = task.getProject();
        final Configuration configuration = project.getConfigurations().getByName(
                configurationName);
        task.dependsOn(configuration.getTaskDependencyFromProjectDependency(
                useDependedOn, otherProjectTaskName));
    }

    public void setSdkLibData(@NonNull SdkLibData sdkLibData) {
        this.sdkLibData = sdkLibData;
    }
}
