package com.android.build.gradle.internal;

import static com.android.SdkConstants.APPCOMPAT_LIB_ARTIFACT;
import static com.android.SdkConstants.SUPPORT_LIB_ARTIFACT;
import static java.io.File.separatorChar;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.tasks.Lint;
import com.android.builder.dependency.MavenCoordinatesImpl;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.ApiVersion;
import com.android.builder.model.BuildTypeContainer;
import com.android.builder.model.Dependencies;
import com.android.builder.model.JavaLibrary;
import com.android.builder.model.MavenCoordinates;
import com.android.builder.model.ProductFlavor;
import com.android.builder.model.ProductFlavorContainer;
import com.android.builder.model.SourceProvider;
import com.android.builder.model.SourceProviderContainer;
import com.android.builder.model.Variant;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.tools.lint.detector.api.Project;
import com.android.utils.Pair;
import com.android.utils.XmlUtils;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Files;

import org.gradle.api.Plugin;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencyArtifact;
import org.gradle.api.artifacts.ExternalDependency;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.plugins.PluginContainer;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.SourceSetOutput;
import org.gradle.tooling.provider.model.ToolingModelBuilder;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;
import org.w3c.dom.Document;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An implementation of Lint's {@link Project} class wrapping a Gradle model (project or
 * library)
 */
public class LintGradleProject extends Project {
    protected AndroidVersion mMinSdkVersion;
    protected AndroidVersion mTargetSdkVersion;

    private LintGradleProject(
            @NonNull LintGradleClient client,
            @NonNull File dir,
            @NonNull File referenceDir,
            @Nullable File manifest) {
        super(client, dir, referenceDir);
        mGradleProject = true;
        mMergeManifests = true;
        mDirectLibraries = Lists.newArrayList();
        if (manifest != null) {
            readManifest(manifest);
        }
    }

    /**
     * Creates a {@link com.android.build.gradle.internal.LintGradleProject} from
     * the given {@link com.android.builder.model.AndroidProject} definition for
     * a given {@link com.android.builder.model.Variant}, and returns it along with
     * a set of lint custom rule jars applicable for the given model project.
     *
     * @param client the client
     * @param project the model project
     * @param variant the variant
     * @param gradleProject the gradle project
     * @return a pair of new project and list of custom rule jars
     */
    @NonNull
    public static Pair<LintGradleProject, List<File>> create(
            @NonNull LintGradleClient client,
            @NonNull AndroidProject project,
            @NonNull Variant variant,
            @NonNull org.gradle.api.Project gradleProject) {
        assert !Lint.MODEL_LIBRARIES;

        File dir = gradleProject.getProjectDir();
        AppGradleProject lintProject = new AppGradleProject(client, dir,
                dir, project, variant);

        List<File> customRules = Lists.newArrayList();
        File appLintJar = new File(gradleProject.getBuildDir(),
                "lint" + separatorChar + "lint.jar");
        if (appLintJar.exists()) {
            customRules.add(appLintJar);
        }

        Set<AndroidLibrary> libraries = Sets.newHashSet();
        Dependencies dependencies = variant.getMainArtifact().getCompileDependencies();
        for (AndroidLibrary library : dependencies.getLibraries()) {
            lintProject.addDirectLibrary(createLibrary(client, library, libraries, customRules));
        }

        return Pair.of(lintProject, customRules);
    }

    @Override
    protected void initialize() {
        // Deliberately not calling super; that code is for ADT compatibility
    }

    protected void readManifest(File manifest) {
        if (manifest.exists()) {
            try {
                String xml = Files.toString(manifest, Charsets.UTF_8);
                Document document = XmlUtils.parseDocumentSilently(xml, true);
                if (document != null) {
                    readManifest(document);
                }
            } catch (IOException e) {
                mClient.log(e, "Could not read manifest %1$s", manifest);
            }
        }
    }

    @Override
    public boolean isGradleProject() {
        return true;
    }

    protected static boolean dependsOn(@NonNull Dependencies dependencies,
            @NonNull String artifact) {
        for (AndroidLibrary library : dependencies.getLibraries()) {
            if (dependsOn(library, artifact)) {
                return true;
            }
        }
        return false;
    }

    protected static boolean dependsOn(@NonNull AndroidLibrary library, @NonNull String artifact) {
        if (SUPPORT_LIB_ARTIFACT.equals(artifact)) {
            if (library.getJarFile().getName().startsWith("support-v4-")) {
                return true;
            }

        } else if (APPCOMPAT_LIB_ARTIFACT.equals(artifact)) {
            File bundle = library.getBundle();
            if (bundle.getName().startsWith("appcompat-v7-")) {
                return true;
            }
        }

        for (AndroidLibrary dependency : library.getLibraryDependencies()) {
            if (dependsOn(dependency, artifact)) {
                return true;
            }
        }

        return false;
    }

    void addDirectLibrary(@NonNull Project project) {
        mDirectLibraries.add(project);
    }

    @NonNull
    private static LibraryProject createLibrary(@NonNull LintGradleClient client,
            @NonNull AndroidLibrary library,
            @NonNull Set<AndroidLibrary> seen, List<File> customRules) {
        seen.add(library);
        File dir = library.getFolder();
        LibraryProject project = new LibraryProject(client, dir, dir, library);

        File ruleJar = library.getLintJar();
        if (ruleJar.exists()) {
            customRules.add(ruleJar);
        }

        for (AndroidLibrary dependent : library.getLibraryDependencies()) {
            if (!seen.contains(dependent)) {
                project.addDirectLibrary(createLibrary(client, dependent, seen, customRules));
            }
        }

        return project;
    }

    // TODO: Rename: this isn't really an "App" project (it could be a library) too; it's a "project"
    // (e.g. not a remote artifact) - LocalGradleProject
    private static class AppGradleProject extends LintGradleProject {
        private AndroidProject mProject;
        private Variant mVariant;
        private List<SourceProvider> mProviders;
        private List<SourceProvider> mTestProviders;

        private AppGradleProject(
                @NonNull LintGradleClient client,
                @NonNull File dir,
                @NonNull File referenceDir,
                @NonNull AndroidProject project,
                @NonNull Variant variant) {
            //TODO FIXME: handle multi-apk
            super(client, dir, referenceDir,
                    variant.getMainArtifact().getOutputs().iterator().next().getGeneratedManifest());

            mProject = project;
            mVariant = variant;
        }

        @Override
        public boolean isLibrary() {
            return mProject.isLibrary();
        }

        @Override
        public AndroidProject getGradleProjectModel() {
            return mProject;
        }

        @Override
        public Variant getCurrentVariant() {
            return mVariant;
        }

        private List<SourceProvider> getSourceProviders() {
            if (mProviders == null) {
                List<SourceProvider> providers = Lists.newArrayList();
                AndroidArtifact mainArtifact = mVariant.getMainArtifact();

                providers.add(mProject.getDefaultConfig().getSourceProvider());

                for (String flavorName : mVariant.getProductFlavors()) {
                    for (ProductFlavorContainer flavor : mProject.getProductFlavors()) {
                        if (flavorName.equals(flavor.getProductFlavor().getName())) {
                            providers.add(flavor.getSourceProvider());
                            break;
                        }
                    }
                }

                SourceProvider multiProvider = mainArtifact.getMultiFlavorSourceProvider();
                if (multiProvider != null) {
                    providers.add(multiProvider);
                }

                String buildTypeName = mVariant.getBuildType();
                for (BuildTypeContainer buildType : mProject.getBuildTypes()) {
                    if (buildTypeName.equals(buildType.getBuildType().getName())) {
                        providers.add(buildType.getSourceProvider());
                        break;
                    }
                }

                SourceProvider variantProvider = mainArtifact.getVariantSourceProvider();
                if (variantProvider != null) {
                    providers.add(variantProvider);
                }

                mProviders = providers;
            }

            return mProviders;
        }

        private List<SourceProvider> getTestSourceProviders() {
            if (mTestProviders == null) {
                List<SourceProvider> providers = Lists.newArrayList();

                ProductFlavorContainer defaultConfig = mProject.getDefaultConfig();
                for (SourceProviderContainer extra : defaultConfig.getExtraSourceProviders()) {
                    String artifactName = extra.getArtifactName();
                    if (AndroidProject.ARTIFACT_ANDROID_TEST.equals(artifactName)) {
                        providers.add(extra.getSourceProvider());
                    }
                }

                for (String flavorName : mVariant.getProductFlavors()) {
                    for (ProductFlavorContainer flavor : mProject.getProductFlavors()) {
                        if (flavorName.equals(flavor.getProductFlavor().getName())) {
                            for (SourceProviderContainer extra : flavor.getExtraSourceProviders()) {
                                String artifactName = extra.getArtifactName();
                                if (AndroidProject.ARTIFACT_ANDROID_TEST.equals(artifactName)) {
                                    providers.add(extra.getSourceProvider());
                                }
                            }
                        }
                    }
                }

                String buildTypeName = mVariant.getBuildType();
                for (BuildTypeContainer buildType : mProject.getBuildTypes()) {
                    if (buildTypeName.equals(buildType.getBuildType().getName())) {
                        for (SourceProviderContainer extra : buildType.getExtraSourceProviders()) {
                            String artifactName = extra.getArtifactName();
                            if (AndroidProject.ARTIFACT_ANDROID_TEST.equals(artifactName)) {
                                providers.add(extra.getSourceProvider());
                            }
                        }
                    }
                }

                mTestProviders = providers;
            }

            return mTestProviders;
        }

        @NonNull
        @Override
        public List<File> getManifestFiles() {
            if (mManifestFiles == null) {
                mManifestFiles = Lists.newArrayList();
                for (SourceProvider provider : getSourceProviders()) {
                    File manifestFile = provider.getManifestFile();
                    if (manifestFile.exists()) { // model returns path whether or not it exists
                        mManifestFiles.add(manifestFile);
                    }
                }
            }

            return mManifestFiles;
        }

        @NonNull
        @Override
        public List<File> getProguardFiles() {
            if (mProguardFiles == null) {
                ProductFlavor flavor = mProject.getDefaultConfig().getProductFlavor();
                mProguardFiles = flavor.getProguardFiles().stream()
                        .filter(File::exists)
                        .collect(Collectors.toList());
                try {
                    mProguardFiles.addAll(
                            flavor.getConsumerProguardFiles().stream()
                                    .filter(File::exists)
                                    .collect(Collectors.toList()));
                } catch (Throwable t) {
                    // On some models, this threw
                    //   org.gradle.tooling.model.UnsupportedMethodException:
                    //    Unsupported method: BaseConfig.getConsumerProguardFiles().
                    // Playing it safe for a while.
                }
            }

            return mProguardFiles;
        }

        @NonNull
        @Override
        public List<File> getResourceFolders() {
            if (mResourceFolders == null) {
                mResourceFolders = Lists.newArrayList();
                for (SourceProvider provider : getSourceProviders()) {
                    Collection<File> resDirs = provider.getResDirectories();
                    // model returns path whether or not it exists
                    mResourceFolders.addAll(resDirs.stream()
                            .filter(File::exists)
                            .collect(Collectors.toList()));
                }

                mResourceFolders.addAll(
                        mVariant.getMainArtifact().getGeneratedResourceFolders().stream()
                                .filter(File::exists)
                                .collect(Collectors.toList()));
            }

            return mResourceFolders;
        }

        @NonNull
        @Override
        public List<File> getAssetFolders() {
            if (mAssetFolders == null) {
                mAssetFolders = Lists.newArrayList();
                for (SourceProvider provider : getSourceProviders()) {
                    Collection<File> dirs = provider.getAssetsDirectories();
                    // model returns path whether or not it exists
                    mAssetFolders.addAll(dirs.stream()
                            .filter(File::exists)
                            .collect(Collectors.toList()));
                }
            }

            return mAssetFolders;
        }

        @NonNull
        @Override
        public List<File> getJavaSourceFolders() {
            if (mJavaSourceFolders == null) {
                mJavaSourceFolders = Lists.newArrayList();
                for (SourceProvider provider : getSourceProviders()) {
                    Collection<File> srcDirs = provider.getJavaDirectories();
                    // model returns path whether or not it exists
                    mJavaSourceFolders.addAll(srcDirs.stream()
                            .filter(File::exists)
                            .collect(Collectors.toList()));
                }

                mJavaSourceFolders.addAll(
                        mVariant.getMainArtifact().getGeneratedSourceFolders().stream()
                                .filter(File::exists)
                                .collect(Collectors.toList()));
            }

            return mJavaSourceFolders;
        }

        @NonNull
        @Override
        public List<File> getTestSourceFolders() {
            if (mTestSourceFolders == null) {
                mTestSourceFolders = Lists.newArrayList();
                for (SourceProvider provider : getTestSourceProviders()) {
                    // model returns path whether or not it exists
                    mTestSourceFolders.addAll(provider.getJavaDirectories().stream()
                            .filter(File::exists)
                            .collect(Collectors.toList()));
                }
            }

            return mTestSourceFolders;
        }

        @NonNull
        @Override
        public List<File> getJavaClassFolders() {
            if (mJavaClassFolders == null) {
                mJavaClassFolders = new ArrayList<>(1);
                File outputClassFolder = mVariant.getMainArtifact().getClassesFolder();
                if (outputClassFolder.exists()) {
                    mJavaClassFolders.add(outputClassFolder);
                }
            }

            return mJavaClassFolders;
        }

        private static boolean sProvidedAvailable = true;

        @NonNull
        @Override
        public List<File> getJavaLibraries(boolean includeProvided) {
            if (includeProvided) {
                if (mJavaLibraries == null) {
                    Dependencies dependencies = mVariant.getMainArtifact().getCompileDependencies();
                    Collection<JavaLibrary> libs = dependencies.getJavaLibraries();
                    mJavaLibraries = Lists.newArrayListWithExpectedSize(libs.size());
                    for (JavaLibrary lib : libs) {
                        File jar = lib.getJarFile();
                        if (jar.exists()) {
                            mJavaLibraries.add(jar);
                        }
                    }
                }
                return mJavaLibraries;
            } else {
                // Skip provided libraries?
                if (mNonProvidedJavaLibraries == null) {
                    Dependencies dependencies = mVariant.getMainArtifact().getCompileDependencies();
                    Collection<JavaLibrary> libs = dependencies.getJavaLibraries();
                    mNonProvidedJavaLibraries = Lists.newArrayListWithExpectedSize(libs.size());
                    for (JavaLibrary lib : libs) {
                        File jar = lib.getJarFile();
                        if (jar.exists()) {
                            if (sProvidedAvailable) {
                                // Method added in 1.4-rc1; gracefully handle running with
                                // older plugins
                                try {
                                    if (lib.isProvided()) {
                                        continue;
                                    }
                                } catch (Throwable t) {
                                    //noinspection AssignmentToStaticFieldFromInstanceMethod
                                    sProvidedAvailable = false; // don't try again
                                }
                            }

                            mNonProvidedJavaLibraries.add(jar);
                        }
                    }
                }
                return mNonProvidedJavaLibraries;
            }
        }

        @Nullable
        @Override
        public String getPackage() {
            // For now, lint only needs the manifest package; not the potentially variant specific
            // package. As part of the Gradle work on the Lint API we should make two separate
            // package lookup methods -- one for the manifest package, one for the build package
            if (mPackage == null) { // only used as a fallback in case manifest somehow is null
                String packageName = mProject.getDefaultConfig().getProductFlavor()
                        .getApplicationId();
                if (packageName != null) {
                    return packageName;
                }
            }

            return mPackage; // from manifest
        }

        @Override
        @NonNull
        public AndroidVersion getMinSdkVersion() {
            if (mMinSdkVersion == null) {
                ApiVersion minSdk = mVariant.getMergedFlavor().getMinSdkVersion();
                if (minSdk == null) {
                    ProductFlavor flavor = mProject.getDefaultConfig().getProductFlavor();
                    minSdk = flavor.getMinSdkVersion();
                }
                if (minSdk != null) {
                    mMinSdkVersion = LintUtils.convertVersion(minSdk, mClient.getTargets());
                } else {
                    mMinSdkVersion = super.getMinSdkVersion(); // from manifest
                }
            }

            return mMinSdkVersion;
        }

        @Override
        @NonNull
        public AndroidVersion getTargetSdkVersion() {
            if (mTargetSdkVersion == null) {
                ApiVersion targetSdk = mVariant.getMergedFlavor().getTargetSdkVersion();
                if (targetSdk == null) {
                    ProductFlavor flavor = mProject.getDefaultConfig().getProductFlavor();
                    targetSdk = flavor.getTargetSdkVersion();
                }
                if (targetSdk != null) {
                    mTargetSdkVersion = LintUtils.convertVersion(targetSdk, mClient.getTargets());
                } else {
                    mTargetSdkVersion = super.getTargetSdkVersion(); // from manifest
                }
            }

            return mTargetSdkVersion;
        }

        @Override
        public int getBuildSdk() {
            String compileTarget = mProject.getCompileTarget();
            AndroidVersion version = AndroidTargetHash.getPlatformVersion(compileTarget);
            if (version != null) {
                return version.getFeatureLevel();
            }

            return super.getBuildSdk();
        }

        @Nullable
        @Override
        public Boolean dependsOn(@NonNull String artifact) {
            if (SUPPORT_LIB_ARTIFACT.equals(artifact)) {
                if (mSupportLib == null) {
                    Dependencies dependencies = mVariant.getMainArtifact().getCompileDependencies();
                    mSupportLib = dependsOn(dependencies, artifact);
                }
                return mSupportLib;
            } else if (APPCOMPAT_LIB_ARTIFACT.equals(artifact)) {
                if (mAppCompat == null) {
                    Dependencies dependencies = mVariant.getMainArtifact().getCompileDependencies();
                    mAppCompat = dependsOn(dependencies, artifact);
                }
                return mAppCompat;
            } else {
                return super.dependsOn(artifact);
            }
        }
    }

    private static class LibraryProject extends LintGradleProject {
        private AndroidLibrary mLibrary;

        private LibraryProject(
                @NonNull LintGradleClient client,
                @NonNull File dir,
                @NonNull File referenceDir,
                @NonNull AndroidLibrary library) {
            super(client, dir, referenceDir, library.getManifest());
            mLibrary = library;

            // TODO: Make sure we don't use this project for any source library projects!
            mReportIssues = false;
        }

        @Override
        public boolean isLibrary() {
            return true;
        }

        @Override
        public AndroidLibrary getGradleLibraryModel() {
            return mLibrary;
        }

        @Override
        public Variant getCurrentVariant() {
            return null;
        }

        @NonNull
        @Override
        public List<File> getManifestFiles() {
            if (mManifestFiles == null) {
                File manifest = mLibrary.getManifest();
                if (manifest.exists()) {
                    mManifestFiles = Collections.singletonList(manifest);
                } else {
                    mManifestFiles = Collections.emptyList();
                }
            }

            return mManifestFiles;
        }

        @NonNull
        @Override
        public List<File> getProguardFiles() {
            if (mProguardFiles == null) {
                File proguardRules = mLibrary.getProguardRules();
                if (proguardRules.exists()) {
                    mProguardFiles = Collections.singletonList(proguardRules);
                } else {
                    mProguardFiles = Collections.emptyList();
                }
            }

            return mProguardFiles;
        }

        @NonNull
        @Override
        public List<File> getResourceFolders() {
            if (mResourceFolders == null) {
                File folder = mLibrary.getResFolder();
                if (folder.exists()) {
                    mResourceFolders = Collections.singletonList(folder);
                } else {
                    mResourceFolders = Collections.emptyList();
                }
            }

            return mResourceFolders;
        }

        @NonNull
        @Override
        public List<File> getAssetFolders() {
            if (mAssetFolders == null) {
                File folder = mLibrary.getAssetsFolder();
                if (folder.exists()) {
                    mAssetFolders = Collections.singletonList(folder);
                } else {
                    mAssetFolders = Collections.emptyList();
                }
            }

            return mAssetFolders;
        }

        @NonNull
        @Override
        public List<File> getJavaSourceFolders() {
            return Collections.emptyList();
        }

        @NonNull
        @Override
        public List<File> getTestSourceFolders() {
            return Collections.emptyList();
        }

        @NonNull
        @Override
        public List<File> getJavaClassFolders() {
            return Collections.emptyList();
        }

        @NonNull
        @Override
        public List<File> getJavaLibraries(boolean includeProvided) {
            if (!includeProvided && mLibrary.isProvided()) {
                return Collections.emptyList();
            }

            if (mJavaLibraries == null) {
                mJavaLibraries =
                        Stream.concat(
                                Stream.of(mLibrary.getJarFile()),
                                mLibrary.getLocalJars().stream())
                        .filter(File::exists)
                        .collect(Collectors.toList());
            }

            return mJavaLibraries;
        }

        @Nullable
        @Override
        public Boolean dependsOn(@NonNull String artifact) {
            if (SUPPORT_LIB_ARTIFACT.equals(artifact)) {
                if (mSupportLib == null) {
                    mSupportLib = dependsOn(mLibrary, artifact);
                }
                return mSupportLib;
            } else if (APPCOMPAT_LIB_ARTIFACT.equals(artifact)) {
                if (mAppCompat == null) {
                    mAppCompat = dependsOn(mLibrary, artifact);
                }
                return mAppCompat;
            } else {
                return super.dependsOn(artifact);
            }
        }
    }

    /**
     * Class which creates a lint project hierarchy based on a corresponding
     * Gradle project hierarchy, looking up project dependencies by name, creating
     * wrapper projects for Java libraries, looking up tooling models for each project,
     * etc.
     */
    static class ProjectSearch {
        public final Map<AndroidProject,Project> appProjects = Maps.newHashMap();
        public final Map<AndroidLibrary,Project> libraryProjects = Maps.newHashMap();
        public final Map<MavenCoordinates,Project> libraryProjectsByCoordinate = Maps.newHashMap();
        public final Map<String,Project> namedProjects = Maps.newHashMap();
        public final Map<JavaLibrary,Project> javaLibraryProjects = Maps.newHashMap();
        public final Map<MavenCoordinates,Project> javaLibraryProjectsByCoordinate = Maps.newHashMap();
        public final Map<org.gradle.api.Project,AndroidProject> gradleProjects = Maps.newHashMap();
        public final List<File> customViewRuleJars = Lists.newArrayList();
        private Set<Object> mSeen = Sets.newHashSet();

        public ProjectSearch() {
            assert Lint.MODEL_LIBRARIES;
        }

        @Nullable
        private static AndroidProject createAndroidProject(@NonNull LintGradleClient client,
                @NonNull org.gradle.api.Project gradleProject) {
            PluginContainer pluginContainer = gradleProject.getPlugins();
            for (Plugin p : pluginContainer) {
                if (p instanceof ToolingRegistryProvider) {
                    ToolingModelBuilderRegistry registry;
                    registry = ((ToolingRegistryProvider) p).getModelBuilderRegistry();
                    String modelName = AndroidProject.class.getName();
                    ToolingModelBuilder builder = registry.getBuilder(modelName);
                    assert builder.canBuild(modelName) : modelName;
                    return (AndroidProject) builder.buildAll(modelName, gradleProject);
                }
            }

            return null;
        }

        @Nullable
        private AndroidProject getAndroidProject(@NonNull LintGradleClient client,
                @NonNull org.gradle.api.Project gradleProject) {
            AndroidProject androidProject = gradleProjects.get(gradleProject);
            if (androidProject == null) {
                androidProject = createAndroidProject(client, gradleProject);
                if (androidProject != null) {
                    gradleProjects.put(gradleProject, androidProject);
                }
            }
            return androidProject;
        }

        public Project getProject(
                @NonNull LintGradleClient client,
                @NonNull org.gradle.api.Project gradleProject,
                @NonNull String variantName) {
            AndroidProject androidProject = getAndroidProject(client, gradleProject);
            if (androidProject != null) {
                for (Variant variant : androidProject.getVariants()) {
                    if (variantName.equals(variant.getName())) {
                        return getProject(client, androidProject, variant, gradleProject);
                    }
                }
                // This shouldn't happen; we didn't get an AndroidProject for an expected
                // variant name
                assert false : variantName;
            }

            // Make plain vanilla project; this is what happens for Java projects (which
            // don't have a Gradle model)

            JavaPluginConvention convention = gradleProject.getConvention()
                    .getPlugin(JavaPluginConvention.class);
            if (convention == null) {
                return null;
            }

            // Language level: Currently not needed. The way to get it is via
            //   convention.getSourceCompatibility()

            // Sources
            SourceSetContainer sourceSets = convention.getSourceSets();
            if (sourceSets != null) {
                final List<File> sources = Lists.newArrayList();
                final List<File> classes = Lists.newArrayList();
                final List<File> libs = Lists.newArrayList();
                final List<File> tests = Lists.newArrayList();
                for (SourceSet sourceSet : sourceSets) {
                    if (sourceSet.getName().equals(SourceSet.TEST_SOURCE_SET_NAME)) {
                        // We don't model the full test source set yet (e.g. its dependencies),
                        // only its source files
                        SourceDirectorySet javaSrc = sourceSet.getJava();
                        if (javaSrc != null) {
                            tests.addAll(javaSrc.getSrcDirs());
                        }
                        continue;
                    }

                    SourceDirectorySet javaSrc = sourceSet.getJava();
                    if (javaSrc != null) {
                        // There are also resource directories, in case we want to
                        // model those here eventually
                        sources.addAll(javaSrc.getSrcDirs());
                    }
                    SourceSetOutput output = sourceSet.getOutput();
                    if (output != null) {
                        classes.add(output.getClassesDir());
                    }

                    libs.addAll(sourceSet.getCompileClasspath().getFiles());

                    // TODO: Boot classpath? We don't have access to that here, so for
                    // now EcjParser just falls back to the running Gradle JVM and looks
                    // up its class path.
                }

                File dir = gradleProject.getProjectDir();
                final List<Project> directLibraries = Lists.newArrayList();
                Project project = new Project(client, dir, dir) {
                    @Override
                    protected void initialize() {
                        // Deliberately not calling super; that code is for ADT compatibility

                        mGradleProject = true;
                        mMergeManifests = true;
                        mDirectLibraries = directLibraries;
                        mJavaSourceFolders = sources;
                        mJavaClassFolders = classes;
                        mJavaLibraries = libs;
                        mTestSourceFolders = tests;
                    }

                    @Override
                    public boolean isGradleProject() {
                        return true;
                    }

                    @Override
                    public boolean isAndroidProject() {
                        return false;
                    }

                    @Nullable
                    @Override
                    public IAndroidTarget getBuildTarget() {
                        return null;
                    }
                };

                // Dependencies
                ConfigurationContainer configurations = gradleProject.getConfigurations();
                Configuration compile = configurations.getByName(JavaPlugin.COMPILE_CONFIGURATION_NAME);
                if (compile != null) {
                    for (Dependency dependency : compile.getDependencies()) {
                        if (dependency instanceof ProjectDependency) {
                            org.gradle.api.Project p =
                                    ((ProjectDependency) dependency).getDependencyProject();
                            if (p != null) {
                                Project lintProject = getProject(client, p.getName(), p,
                                        variantName);
                                if (lintProject != null) {
                                    directLibraries.add(lintProject);
                                }
                            }
                        } else if (dependency instanceof ExternalDependency) {
                            String group = dependency.getGroup();
                            String name = dependency.getName();
                            String version = dependency.getVersion();
                            MavenCoordinatesImpl coordinates = new MavenCoordinatesImpl(group,
                                    name, version);
                            Project javaLib = javaLibraryProjectsByCoordinate.get(coordinates);
                            //noinspection StatementWithEmptyBody
                            if (javaLib != null) {
                                directLibraries.add(javaLib);
                            } else {
                                // Else: Create wrapper here. Unfortunately, we don't have a
                                // pointer to the actual .jar file to add (getArtifacts()
                                // typically returns an empty set), so we can't create
                                // a real artifact (and creating a fake one and placing it here
                                // is dangerous; it would mean putting one into the
                                // map that would prevent a real definition from being inserted.
                                // finding the actual content to add
                            }
                        }
                    }
                }

                return project;
            }

            return null;
        }

        public Project getProject(
                @NonNull LintGradleClient client,
                @NonNull AndroidProject project,
                @NonNull Variant variant,
                @NonNull org.gradle.api.Project gradleProject) {
            Project cached = appProjects.get(project);
            if (cached != null) {
                return cached;
            }
            mSeen.add(project);
            File dir = gradleProject.getProjectDir();
            AppGradleProject lintProject = new AppGradleProject(client, dir, dir, project, variant);
            appProjects.put(project, lintProject);

            File appLintJar = new File(gradleProject.getBuildDir(),
                    "lint" + separatorChar + "lint.jar");
            if (appLintJar.exists()) {
                customViewRuleJars.add(appLintJar);
            }

            // DELIBERATELY calling getDependencies here (and Dependencies#getProjects() below) :
            // the new hierarchical model is not working yet.
            //noinspection deprecation
            Dependencies dependencies = variant.getMainArtifact().getDependencies();
            for (AndroidLibrary library : dependencies.getLibraries()) {
                if (library.getProject() != null
                        && gradleProject.findProject(library.getProject()) == gradleProject) {
                    // Don't know why the dependencies for the model sometimes points to self...
                    // Ignore these
                    continue;
                }
                lintProject.addDirectLibrary(getLibrary(client, library, gradleProject, variant));
            }

            List<String> processedProjects = null;
            //noinspection deprecation
            for (String projectName : dependencies.getProjects()) {
                // At some point (2.2 according to the docs, but still not happening;
                // possibly tied to a flag) the model will stop providing the projects
                // here and will return them as part of getJavaLibraries. Therefore,
                // we look in both places, but record them here such that we don't double
                // count them.
                if (processedProjects == null) {
                    processedProjects = Lists.newArrayList();
                }
                processedProjects.add(projectName);
                Project libLintProject = getProject(client, projectName, gradleProject,
                        variant.getName());
                if (libLintProject != null) {
                    lintProject.addDirectLibrary(libLintProject);
                }
            }

            for (JavaLibrary library : dependencies.getJavaLibraries()) {
                String projectName = library.getProject();
                if (projectName != null) {
                    if (processedProjects != null && processedProjects.contains(projectName)) {
                        continue;
                    }
                    Project libLintProject = getProject(client, projectName, gradleProject,
                            variant.getName());
                    if (libLintProject != null) {
                        lintProject.addDirectLibrary(libLintProject);
                        continue;
                    }
                }
                lintProject.addDirectLibrary(getLibrary(client, library));
            }

            //noinspection deprecation
            assert dependencies.getProjects().isEmpty(); // should have been handled above

            return lintProject;
        }

        @Nullable
        private Project getProject(
                @NonNull LintGradleClient client,
                @NonNull String name,
                @NonNull org.gradle.api.Project gradleProject,
                @NonNull String variantName) {
            Project cached = namedProjects.get(name);
            if (cached != null) {
                // TODO: Are names unique across siblings?
                return cached;
            }
            org.gradle.api.Project namedProject = gradleProject.findProject(name);
            if (namedProject != null) {
                Project project = getProject(client, namedProject, variantName);
                if (project != null) {
                    namedProjects.put(name, project);
                    return project;
                }
            }

            return null;
        }

        @NonNull
        private Project getLibrary(
                @NonNull LintGradleClient client,
                @NonNull AndroidLibrary library,
                @NonNull org.gradle.api.Project gradleProject,
                @NonNull Variant variant) {
            Project cached = libraryProjects.get(library);
            if (cached != null) {
                return cached;
            }

            MavenCoordinates coordinates = library.getResolvedCoordinates();
            cached = libraryProjectsByCoordinate.get(coordinates);
            if (cached != null) {
                return cached;
            }

            if (library.getProject() != null) {
                Project project = getProject(client, library.getProject(), gradleProject,
                        variant.getName());
                if (project != null) {
                    libraryProjects.put(library, project);
                    return project;
                }
            }

            mSeen.add(library);
            File dir = library.getFolder();
            LibraryProject project = new LibraryProject(client, dir, dir, library);
            libraryProjects.put(library, project);
            libraryProjectsByCoordinate.put(coordinates, project);

            File ruleJar = library.getLintJar();
            if (ruleJar.exists()) {
                customViewRuleJars.add(ruleJar);
            }

            for (AndroidLibrary dependent : library.getLibraryDependencies()) {
                project.addDirectLibrary(getLibrary(client, dependent, gradleProject, variant));
            }

            return project;
        }

        @NonNull
        private Project getLibrary(
                @NonNull LintGradleClient client,
                @NonNull JavaLibrary library) {
            Project cached = javaLibraryProjects.get(library);
            if (cached != null) {
                return cached;
            }

            MavenCoordinates coordinates = library.getResolvedCoordinates();
            cached = javaLibraryProjectsByCoordinate.get(coordinates);
            if (cached != null) {
                return cached;
            }

            mSeen.add(library);
            File dir = library.getJarFile();
            JavaLibraryProject project = new JavaLibraryProject(client, dir, dir, library);
            javaLibraryProjects.put(library, project);
            javaLibraryProjectsByCoordinate.put(coordinates, project);

            for (JavaLibrary dependent : library.getDependencies()) {
                project.addDirectLibrary(getLibrary(client, dependent));
            }

            return project;
        }
    }

    private static class JavaLibraryProject extends LintGradleProject {
        private JavaLibrary mLibrary;

        private JavaLibraryProject(
                @NonNull LintGradleClient client,
                @NonNull File dir,
                @NonNull File referenceDir,
                @NonNull JavaLibrary library) {
            super(client, dir, referenceDir, null);
            mLibrary = library;
            mReportIssues = false;
        }

        @Override
        public boolean isLibrary() {
            return true;
        }

        @NonNull
        @Override
        public List<File> getManifestFiles() {
            return Collections.emptyList();
        }

        @NonNull
        @Override
        public List<File> getProguardFiles() {
            return Collections.emptyList();
        }

        @NonNull
        @Override
        public List<File> getResourceFolders() {
            return Collections.emptyList();
        }

        @NonNull
        @Override
        public List<File> getAssetFolders() {
            return Collections.emptyList();
        }

        @NonNull
        @Override
        public List<File> getJavaSourceFolders() {
            return Collections.emptyList();
        }

        @NonNull
        @Override
        public List<File> getTestSourceFolders() {
            return Collections.emptyList();
        }

        @NonNull
        @Override
        public List<File> getJavaClassFolders() {
            return Collections.emptyList();
        }

        @NonNull
        @Override
        public List<File> getJavaLibraries(boolean includeProvided) {
            if (!includeProvided && mLibrary.isProvided()) {
                return Collections.emptyList();
            }

            if (mJavaLibraries == null) {
                mJavaLibraries = Collections.singletonList(mLibrary.getJarFile());
            }

            return mJavaLibraries;
        }
    }
}
