package com.android.build.gradle.tasks.factory;

import static com.android.builder.core.VariantType.LIBRARY;
import static com.android.builder.core.VariantType.UNIT_TEST;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.gradle.AndroidGradleOptions;
import com.android.build.gradle.internal.CompileOptions;
import com.android.build.gradle.internal.LoggerWrapper;
import com.android.build.gradle.internal.scope.ConventionMappingHelper;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.builder.dependency.LibraryDependency;
import com.android.builder.model.SyncIssue;
import com.android.utils.FileUtils;
import com.android.utils.ILogger;
import com.google.common.base.Joiner;

import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.file.FileCollection;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Configuration Action for a JavaCompile task.
 */
public class JavaCompileConfigAction implements TaskConfigAction<AndroidJavaCompile> {
    private static final ILogger LOG = LoggerWrapper.getLogger(JavaCompileConfigAction.class);

    private VariantScope scope;

    public JavaCompileConfigAction(VariantScope scope) {
        this.scope = scope;
    }

    @NonNull
    @Override
    public String getName() {
        return scope.getTaskName("compile", "JavaWithJavac");
    }

    @NonNull
    @Override
    public Class<AndroidJavaCompile> getType() {
        return AndroidJavaCompile.class;
    }

    @Override
    public void execute(@NonNull final AndroidJavaCompile javacTask) {
        final BaseVariantData testedVariantData = scope.getTestedVariantData();
        scope.getVariantData().javacTask = javacTask;
        javacTask.compileSdkVersion = scope.getGlobalScope().getExtension().getCompileSdkVersion();
        javacTask.mBuildContext = scope.getInstantRunBuildContext();

        // We can't just pass the collection directly, as the instanceof check in the incremental
        // compile doesn't work recursively currently, so every ConfigurableFileTree needs to be
        // directly in the source array.
        for (ConfigurableFileTree fileTree: scope.getVariantData().getJavaSources()) {
            javacTask.source(fileTree);
        }

        // javac 1.8 may generate code that uses class not available in android.jar.  This is fine
        // if jack is used to compile code for the app and this compile task is created only for
        // unit test.  In which case, we want to keep the default bootstrap classpath.
        final boolean keepDefaultBootstrap =
                scope.getVariantConfiguration().getJackOptions().isEnabled()
                        && JavaVersion.current().isJava8Compatible();

        if (!keepDefaultBootstrap) {
            // Set boot classpath if we don't need to keep the default.  Otherwise, this is added as
            // normal classpath.
            javacTask.getOptions().setBootClasspath(
                    Joiner.on(File.pathSeparator).join(
                            scope.getGlobalScope().getAndroidBuilder()
                                    .getBootClasspathAsStrings(false)));
        }

        ConventionMappingHelper.map(javacTask, "classpath", new Callable<FileCollection>() {
            @Override
            public FileCollection call() {
                FileCollection classpath = scope.getJavaClasspath();
                Project project = scope.getGlobalScope().getProject();

                if (keepDefaultBootstrap) {
                    classpath = classpath.plus(project.files(
                            scope.getGlobalScope().getAndroidBuilder().getBootClasspath(false)));
                }

                if (testedVariantData != null) {
                    // For libraries, the classpath from androidBuilder includes the library
                    // output (bundle/classes.jar) as a normal dependency. In unit tests we
                    // don't want to package the jar at every run, so we use the *.class
                    // files instead.
                    if (!testedVariantData.getType().equals(LIBRARY)
                            || scope.getVariantData().getType().equals(UNIT_TEST)) {
                        classpath = classpath.plus(project.files(
                                        testedVariantData.getScope().getJavaClasspath(),
                                        testedVariantData.getScope().getJavaOutputDir(),
                                        testedVariantData.getScope().getJavaDependencyCache()));
                    }

                    if (scope.getVariantData().getType().equals(UNIT_TEST)
                            && testedVariantData.getType().equals(LIBRARY)) {
                        // The bundled classes.jar may exist, but it's probably old. Don't
                        // use it, we already have the *.class files in the classpath.
                        LibraryDependency libraryDependency =
                                testedVariantData.getVariantConfiguration().getOutput();
                        if (libraryDependency != null) {
                            File jarFile = libraryDependency.getJarFile();
                            classpath = classpath.minus(project.files(jarFile));
                        }
                    }
                }

                return classpath;
            }
        });

        javacTask.setDestinationDir(scope.getJavaOutputDir());

        javacTask.setDependencyCacheDir(scope.getJavaDependencyCache());

        CompileOptions compileOptions = scope.getGlobalScope().getExtension().getCompileOptions();

        AbstractCompilesUtil.configureLanguageLevel(
                javacTask,
                compileOptions,
                scope.getGlobalScope().getExtension().getCompileSdkVersion(),
                scope.getVariantConfiguration().getJackOptions().isEnabled());

        javacTask.getOptions().setEncoding(compileOptions.getEncoding());

        GlobalScope globalScope = scope.getGlobalScope();
        Project project = globalScope.getProject();

        boolean incremental;

        if (compileOptions.getIncremental() != null) {
            incremental = compileOptions.getIncremental();
            LOG.info("Incremental flag set to %1$b in DSL", incremental);
        } else {
            if (globalScope.getExtension().getDataBinding().isEnabled()
                    || project.getPlugins().hasPlugin("com.neenbedankt.android-apt")
                    || project.getPlugins().hasPlugin("me.tatarka.retrolambda")) {
                incremental = false;
                LOG.info("Incremental Java compilation disabled in variant %1$s "
                                + "as you are using an incompatible plugin",
                        scope.getVariantConfiguration().getFullName());
            } else {
                // For now, default to true, unless the use uses several source folders,
                // in that case, we cannot guarantee that the incremental java works fine.

                // some source folders may be configured but do not exist, in that case, don't
                // use as valid source folders to determine whether or not we should turn on
                // incremental compilation.
                List<File> sourceFolders = new ArrayList<File>();
                for (ConfigurableFileTree sourceFolder
                        : scope.getVariantData().getUserJavaSources()) {

                    if (sourceFolder.getDir().exists()) {
                        sourceFolders.add(sourceFolder.getDir());
                    }
                }
                incremental = sourceFolders.size() == 1;
                if (sourceFolders.size() > 1) {
                    LOG.info("Incremental Java compilation disabled in variant %1$s "
                            + "as you are using %2$d source folders : %3$s",
                            scope.getVariantConfiguration().getFullName(),
                            sourceFolders.size(), Joiner.on(',').join(sourceFolders));
                }
            }
        }

        if (AndroidGradleOptions.isJavaCompileIncrementalPropertySet(project)) {
            scope.getGlobalScope().getAndroidBuilder().getErrorReporter().handleSyncError(
                    null,
                    SyncIssue.TYPE_GENERIC,
                    String.format(
                            "The %s property has been replaced by a DSL property. Please add the "
                                    + "following to your build.gradle instead:\n"
                                    + "android {\n"
                                    + "  compileOptions.incremental = false\n"
                                    + "}",
                            AndroidGradleOptions.PROPERTY_INCREMENTAL_JAVA_COMPILE));
        }

        if (incremental) {
            LOG.info("Using incremental javac compilation.");
        } else {
            LOG.info("Not using incremental javac compilation.");
        }
        javacTask.getOptions().setIncremental(incremental);
    }
}
