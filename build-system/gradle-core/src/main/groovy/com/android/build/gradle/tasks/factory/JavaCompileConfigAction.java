package com.android.build.gradle.tasks.factory;

import static com.android.builder.core.VariantType.LIBRARY;
import static com.android.builder.core.VariantType.UNIT_TEST;
import static com.android.builder.model.AndroidProject.FD_INTERMEDIATES;

import com.android.build.gradle.internal.CompileOptions;
import com.android.build.gradle.internal.scope.ConventionMappingHelper;
import com.android.build.gradle.internal.scope.TaskConfigAction;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.AndroidVersion;
import com.google.common.base.Joiner;

import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.gradle.api.Action;
import org.gradle.api.JavaVersion;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.api.tasks.compile.JavaCompile;

import java.io.File;

import groovy.lang.Closure;

/**
 * Configuration Action for a JavaCompile task.
 */
public class JavaCompileConfigAction implements TaskConfigAction<JavaCompile> {

    private VariantScope scope;

    public JavaCompileConfigAction(VariantScope scope) {
        this.scope = scope;
    }

    @Override
    public String getName() {
        return scope.getTaskName("compile", "Java");
    }

    @Override
    public Class<JavaCompile> getType() {
        return JavaCompile.class;
    }

    @Override
    public void execute(final JavaCompile javaCompileTask) {
        final BaseVariantData testedVariantData = scope.getTestedVariantData();
        scope.getVariantData().javaCompileTask = javaCompileTask;

        javaCompileTask.setSource(scope.getVariantData().getJavaSources());

        ConventionMappingHelper
                .map(javaCompileTask, "classpath", new Closure<FileCollection>(this, this) {
                    public FileCollection doCall(Object it) {
                        FileCollection classpath = scope.getJavaClasspath();

                        if (testedVariantData != null) {
                            // For libraries, the classpath from androidBuilder includes the library output
                            // (bundle/classes.jar) as a normal dependency. In unit tests we don't want to package
                            // the jar at every run, so we use the *.class files instead.
                            if (!testedVariantData.getType().equals(LIBRARY) || scope
                                    .getVariantData().getType().equals(UNIT_TEST)) {
                                classpath = classpath
                                        .plus(testedVariantData.getScope().getJavaClasspath())
                                        .plus(scope.getGlobalScope().getProject().files(
                                                testedVariantData.getScope().getJavaOutputDir(),
                                                testedVariantData.getScope()
                                                        .getJavaDependencyCache()));
                            }

                            if (scope.getVariantData().getType().equals(UNIT_TEST)
                                    && testedVariantData.getType().equals(LIBRARY)) {
                                // The bundled classes.jar may exist, but it's probably old. Don't use it, we
                                // already have the *.class files in the classpath.
                                classpath = classpath.minus(scope.getGlobalScope().getProject()
                                        .files(testedVariantData.getVariantConfiguration()
                                                .getOutput().getJarFile()));
                            }

                        }

                        return classpath;
                    }

                    public FileCollection doCall() {
                        return doCall(null);
                    }

                });

        ConventionMappingHelper
                .map(javaCompileTask, "destinationDir", new Closure<File>(this, this) {
                    public File doCall(Object it) {
                        return scope.getJavaOutputDir();
                    }

                    public File doCall() {
                        return doCall(null);
                    }

                });
        ConventionMappingHelper
                .map(javaCompileTask, "dependencyCacheDir", new Closure<File>(this, this) {
                    public File doCall(Object it) {
                        return scope.getJavaDependencyCache();
                    }

                    public File doCall() {
                        return doCall(null);
                    }

                });

        configureLanguageLevel(javaCompileTask);
        javaCompileTask.getOptions().setEncoding(
                scope.getGlobalScope().getExtension().getCompileOptions().getEncoding());
        javaCompileTask.getOptions().setBootClasspath(
                Joiner.on(File.pathSeparator).join(
                        scope.getGlobalScope().getAndroidBuilder().getBootClasspathAsStrings()));
    }

    private void configureLanguageLevel(AbstractCompile compileTask) {
        final CompileOptions compileOptions = scope.getGlobalScope().getExtension()
                .getCompileOptions();
        JavaVersion javaVersionToUse;

        final AndroidVersion hash = AndroidTargetHash
                .getVersionFromHash(scope.getGlobalScope().getExtension().getCompileSdkVersion());
        Integer compileSdkLevel = (hash == null ? null : hash.getApiLevel());
        if (compileSdkLevel == null || (0 <= compileSdkLevel && compileSdkLevel <= 20)) {
            javaVersionToUse = JavaVersion.VERSION_1_6;
        } else {
            javaVersionToUse = JavaVersion.VERSION_1_7;
        }

        JavaVersion jdkVersion = JavaVersion
                .toVersion(System.getProperty("java.specification.version"));
        if (jdkVersion.compareTo(javaVersionToUse) < 0) {
//            logger.info(
//                    "Default language level for 'compileSdkVersion $compileSdkLevel' is " +
//                            "$javaVersionToUse, but the JDK used is $jdkVersion, so the JDK " +
//                            "language level will be used.")
            javaVersionToUse = jdkVersion;
        }

        compileOptions.setDefaultJavaVersion(javaVersionToUse);

        ConventionMappingHelper
                .map(compileTask, "sourceCompatibility", new Closure<String>(this, this) {
                    public String doCall(Object it) {
                        return compileOptions.getSourceCompatibility().toString();
                    }

                    public String doCall() {
                        return doCall(null);
                    }

                });
        ConventionMappingHelper
                .map(compileTask, "targetCompatibility", new Closure<String>(this, this) {
                    public String doCall(Object it) {
                        return compileOptions.getTargetCompatibility().toString();
                    }

                    public String doCall() {
                        return doCall(null);
                    }

                });
    }

    private static <Value extends String> Value setBootClasspath(
            org.gradle.api.tasks.compile.CompileOptions propOwner, Value bootClasspath) {
        propOwner.setBootClasspath(bootClasspath);
        return bootClasspath;
    }
}
