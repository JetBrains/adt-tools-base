package com.android.build.gradle.tasks.factory;

import static com.android.builder.core.VariantType.LIBRARY;
import static com.android.builder.core.VariantType.UNIT_TEST;

import com.android.annotations.NonNull;
import com.android.build.gradle.AndroidGradleOptions;
import com.android.build.gradle.OptionalCompilationStep;
import com.android.build.gradle.internal.CompileOptions;
import com.android.build.gradle.internal.scope.ConventionMappingHelper;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.builder.dependency.LibraryDependency;
import com.google.common.base.Joiner;

import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.file.FileCollection;

import java.io.File;
import java.util.concurrent.Callable;

/**
 * Configuration Action for a JavaCompile task.
 */
public class JavaCompileConfigAction implements TaskConfigAction<AndroidJavaCompile> {

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
        javacTask.mBuildContext = scope.getInstantRunBuildContext();

        // We can't just pass the collection directly, as the instanceof check in the incremental
        // compile doesn't work recursively currently, so every ConfigurableFileTree needs to be
        // directly in the source array.
        for (ConfigurableFileTree fileTree: scope.getVariantData().getJavaSources()) {
            javacTask.source(fileTree);
        }

        ConventionMappingHelper.map(javacTask, "classpath", new Callable<FileCollection>() {
            @Override
            public FileCollection call() {
                FileCollection classpath = scope.getJavaClasspath();
                Project project = scope.getGlobalScope().getProject();

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
                scope.getGlobalScope().getExtension().getCompileSdkVersion()
        );

        javacTask.getOptions().setEncoding(compileOptions.getEncoding());

        javacTask.getOptions().setBootClasspath(
                Joiner.on(File.pathSeparator).join(
                        scope.getGlobalScope().getAndroidBuilder().getBootClasspathAsStrings(false)));

        GlobalScope globalScope = scope.getGlobalScope();
        Project project = globalScope.getProject();
        if (AndroidGradleOptions.isJavaCompileIncremental(project) ||
                (globalScope.isActive(OptionalCompilationStep.INSTANT_DEV) &&
                        AndroidGradleOptions.isInstantRunJavaCompileIncremental(project))) {
            // TODO(http://b.android.com/200043): Find out why the annotation processors seem to be
            // incompatible with incremental java compilation.
            if (globalScope.getExtension().getDataBinding().isEnabled()
                    || project.getPlugins().hasPlugin("com.neenbedankt.android-apt")) {
                javacTask.getOptions().setIncremental(false);
            } else {
                javacTask.getOptions().setIncremental(true);
            }
        }
    }
}
