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

package com.android.tools.gradle.eclipse;

import static com.android.SdkConstants.ANDROID_MANIFEST_XML;
import static com.android.SdkConstants.DOT_JAVA;
import static com.android.SdkConstants.FN_LOCAL_PROPERTIES;
import static com.android.tools.gradle.eclipse.GradleImport.ANDROID_GRADLE_PLUGIN;
import static com.android.tools.gradle.eclipse.GradleImport.DECLARE_GLOBAL_REPOSITORIES;
import static com.android.tools.gradle.eclipse.GradleImport.IMPORT_SUMMARY_TXT;
import static com.android.tools.gradle.eclipse.GradleImport.MAVEN_REPOSITORY;
import static com.android.tools.gradle.eclipse.GradleImport.NL;
import static com.android.tools.gradle.eclipse.ImportSummary.MSG_FOLDER_STRUCTURE;
import static com.android.tools.gradle.eclipse.ImportSummary.MSG_FOOTER;
import static com.android.tools.gradle.eclipse.ImportSummary.MSG_GUESSED_VERSIONS;
import static com.android.tools.gradle.eclipse.ImportSummary.MSG_HEADER;
import static com.android.tools.gradle.eclipse.ImportSummary.MSG_MANIFEST;
import static com.android.tools.gradle.eclipse.ImportSummary.MSG_MISSING_GOOGLE_REPOSITORY_1;
import static com.android.tools.gradle.eclipse.ImportSummary.MSG_MISSING_GOOGLE_REPOSITORY_2;
import static com.android.tools.gradle.eclipse.ImportSummary.MSG_MISSING_REPO_1;
import static com.android.tools.gradle.eclipse.ImportSummary.MSG_MISSING_REPO_2;
import static com.android.tools.gradle.eclipse.ImportSummary.MSG_REPLACED_JARS;
import static com.android.tools.gradle.eclipse.ImportSummary.MSG_REPLACED_LIBS;
import static com.android.tools.gradle.eclipse.ImportSummary.MSG_UNHANDLED;
import static com.google.common.base.Charsets.UTF_8;
import static java.io.File.separator;
import static java.io.File.separatorChar;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.SdkManager;
import com.android.testutils.TestUtils;
import com.android.utils.ILogger;
import com.android.utils.Pair;
import com.android.utils.SdkUtils;
import com.android.utils.StdLogger;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

import junit.framework.TestCase;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

// TODO:
// -- Test what happens if we're missing a project.properties file
// -- Test what happens when we have a broken AndroidManifest file
// -- Test resolving classpath variables
// -- Test resolving workspace paths
// -- Test resolving compiler options (1.6 vs 1.7)
// -- Test what happens when you have jars in libs as well as in .classpath
// -- Test what happens with absolute paths in .classpath references
// -- Test proguard
// -- Test whether we can depend on a Java library which depends on another
//    Java library (e.g. through only .classpath dependency, no project.properties file)
// -- Resolve the gradle wrapper location issue, and hook up gradle-building these projects
// -- Test version extraction for libraries like joda-time, guava-11.0.1.jar, etc
// -- Test what happens if you depend on both play services and contain gcm.jar; should
//    not repeat play services dependency

public class GradleImportTest extends TestCase {
    private static File createProject(String name, String pkg) throws IOException {
        File dir = Files.createTempDir();
        return createProject(dir, name, pkg);
    }

    private static File createProject(File dir, String name, String pkg) throws IOException {
        createDotProject(dir, name, true);
        File src = new File("src");
        File gen = new File("gen");

        createSampleJavaSource(dir, "src", pkg, "MyActivity");
        createSampleJavaSource(dir, "gen", pkg, "R");

        createClassPath(dir,
                new File("bin", "classes"),
                Arrays.asList(src, gen),
                Collections.<File>emptyList());
        createProjectProperties(dir, "android-17", null, null, null,
                Collections.<File>emptyList());
        createAndroidManifest(dir, pkg, 8, 16, null);

        createDefaultStrings(dir);
        createDefaultIcon(dir);

        return dir;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void testResolveExpressions() throws Exception {
        File root = Files.createTempDir();
        File projectDir = new File(root, "dir1" + separator + "dir2" + separator + "dir3" +
                separator + "dir4" + separator + "prj");
        projectDir.mkdirs();
        createProject(projectDir, "test1", "test.pkg");
        File var1 = new File(root, "sub1" + separator + "sub2" + separator + "sub3");
        var1.mkdirs();
        File var4 = new File(projectDir.getParentFile(), "var4");
        var4.mkdirs();
        File tpl = new File(projectDir.getParentFile().getParentFile().getParentFile(), "TARGET" +
                separator + "android" + separator + "third-party");
        tpl.mkdirs();
        File supportLib = new File(tpl, "android-support-v4.r19.jar");
        supportLib.createNewFile();

        Files.write(""
                + "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<projectDescription>\n"
                + "\t<name>UnitTest</name>\n"
                + "\t<comment></comment>\n"
                + "\t<projects>\n"
                + "\t</projects>\n"
                + "\t<buildSpec>\n"
                + "\t\t<buildCommand>\n"
                + "\t\t\t<name>org.eclipse.jdt.core.javabuilder</name>\n"
                + "\t\t\t<arguments>\n"
                + "\t\t\t</arguments>\n"
                + "\t\t</buildCommand>\n"
                + "\t</buildSpec>\n"
                + "\t<natures>\n"
                + "\t\t<nature>org.eclipse.jdt.core.javanature</nature>\n"
                + "\t</natures>\n"
                + "\t<linkedResources>\n"
                + "\t\t<link>\n"
                + "\t\t\t<name>MYLIBS</name>\n"
                + "\t\t\t<type>2</type>\n"
                + "\t\t\t<locationURI>MYLIBS</locationURI>\n"
                + "\t\t</link>\n"
                + "\t\t<link>\n"
                + "\t\t\t<name>3rd_java_libs</name>\n"
                + "\t\t\t<type>2</type>\n"
                + "\t\t\t<locationURI>PARENT-3-PROJECT_LOC/TARGET/android/third-party</locationURI>\n"
                + "\t\t</link>\n"
                + "\t\t<link>\n"
                + "\t\t\t<name>jnilibs</name>\n"
                + "\t\t\t<type>2</type>\n"
                + "\t\t\t<locationURI>virtual:/virtual</locationURI>\n"
                + "\t\t</link>\n"
                + "\t</linkedResources>\n"
                + "\t<variableList>\n"
                + "\t\t<variable>\n"
                + "\t\t\t<name>MY_VAR_1</name>\n"
                + "\t\t\t<value>" + SdkUtils.fileToUrl(var1) + "</value>\n"
                + "\t\t</variable>\n"
                + "\t\t<variable>\n"
                + "\t\t\t<name>MY_VAR_2</name>\n"
                + "\t\t\t<value>$%7BMY_VAR_1%7D</value>\n"
                + "\t\t</variable>\n"
                + "\t\t<variable>\n"
                + "\t\t\t<name>MY_VAR_3</name>\n"
                + "\t\t\t<value>$%7BPROJECT_LOC%7D/src</value>\n"
                + "\t\t</variable>\n"
                + "\t\t<variable>\n"
                + "\t\t\t<name>MY_VAR_4</name>\n"
                + "\t\t\t<value>$%7BPARENT-1-PROJECT_LOC%7D/var4</value>\n"
                + "\t\t</variable>\n"
                + "\t\t<variable>\n"
                + "\t\t\t<name>MY_VAR_5</name>\n"
                + "\t\t\t<value>$%7BPARENT_LOC%7D/var4</value>\n"
                + "\t\t</variable>\n"
                + "\t</variableList>\n"
                + "</projectDescription>",
                new File(projectDir, ".project"), UTF_8);

        GradleImport importer = new GradleImport();
        EclipseProject project = EclipseProject.getProject(importer, projectDir);
        importer.getPathMap();

        // Test absolute paths
        assertEquals(var1, project.resolveVariableExpression(var1.getPath()));
        assertEquals(var1, project.resolveVariableExpression(var1.getAbsolutePath()));
        assertEquals(var1.getCanonicalFile(),
                project.resolveVariableExpression(var1.getCanonicalPath()));
        assertEquals(var1, project.resolveVariableExpression(var1.getPath().replace('/',
                separatorChar))); // on Windows, make sure we handle workspace files with forwards


        // Test project relative paths
        String relative = "src" + separator + "test" + separator + "pkg" + separator
                + "MyActivity.java";
        assertEquals(new File(projectDir, relative), project.resolveVariableExpression(relative));
        assertEquals(new File(projectDir, relative), project.resolveVariableExpression(
                relative.replace('/', separatorChar)));

        // Test workspace paths
        // This is handled by testLibraries2

        // Test path variables
        assertEquals(var1, project.resolveVariableExpression("MY_VAR_1"));
        assertEquals(var1, project.resolveVariableExpression("MY_VAR_2"));
        assertEquals(new File(projectDir, "src"), project.resolveVariableExpression("MY_VAR_3"));
        assertEquals(var4, project.resolveVariableExpression("MY_VAR_4"));
        assertEquals(var4, project.resolveVariableExpression("MY_VAR_5"));

        // Test linked variables
        assertEquals(supportLib, project.resolveVariableExpression(
                "3rd_java_libs/android-support-v4.r19.jar"));

        // Test user-supplied values
        assertEquals(var1, project.resolveVariableExpression("MY_VAR_1"));
        importer.getPathMap().put("MY_VAR_1", projectDir);
        assertEquals(projectDir, project.resolveVariableExpression("MY_VAR_1"));
        importer.getPathMap().put("/some/unresolved/path", var4);
        assertEquals(var4, project.resolveVariableExpression("/some/unresolved/path"));

        // Setup for workspace tests

        assertNull(project.resolveVariableExpression("MY_GLOBAL_VAR"));
        final File workspace = new File(root, "workspace");
        workspace.mkdirs();
        File prefs = new File(workspace, ".metadata" + separator +
                ".plugins" + separator +
                "org.eclipse.core.runtime" + separator +
                ".settings" + separator +
                "org.eclipse.jdt.core.prefs");
        prefs.getParentFile().mkdirs();
        File global1 = var1.getParentFile();
        Files.write(""
                + "eclipse.preferences.version=1\n"
                + "org.eclipse.jdt.core.classpathVariable.MY_GLOBAL_VAR="
                + global1.getPath().replace(separatorChar,'/').replace(":","\\:") + "\n"
                + "org.eclipse.jdt.core.codeComplete.visibilityCheck=enabled\n"
                + "org.eclipse.jdt.core.compiler.codegen.inlineJsrBytecode=enabled\n"
                + "org.eclipse.jdt.core.compiler.codegen.targetPlatform=1.6\n"
                + "org.eclipse.jdt.core.compiler.compliance=1.6\n"
                + "org.eclipse.jdt.core.compiler.problem.assertIdentifier=error\n"
                + "org.eclipse.jdt.core.compiler.problem.enumIdentifier=error\n"
                + "org.eclipse.jdt.core.compiler.source=1.6\n"
                + "org.eclipse.jdt.core.formatter.tabulation.char=space", prefs, UTF_8);
        File global2 = var4.getParentFile();
        prefs = new File(workspace, ".metadata" + separator +
                ".plugins" + separator +
                "org.eclipse.core.runtime" + separator +
                ".settings" + separator +
                "org.eclipse.core.resources.prefs");
        prefs.getParentFile().mkdirs();
        Files.write(""
                + "eclipse.preferences.version=1\n"
                + "pathvariable.MY_GLOBAL_VAR_2="
                + global2.getPath().replace(separatorChar,'/').replace(":", "\\:") + "\n"
                + "version=1", prefs, UTF_8);

        importer.setEclipseWorkspace(workspace);

        // Test global path variables

        assertEquals(global1, project.resolveVariableExpression("MY_GLOBAL_VAR"));
        assertEquals(var1, project.resolveVariableExpression("MY_GLOBAL_VAR/sub3"));
        assertEquals(var1, project.resolveVariableExpression("MY_GLOBAL_VAR" + separator + "sub3"));

        // Test workspace linked resources
        assertEquals(global2, project.resolveVariableExpression("MY_GLOBAL_VAR_2"));

        deleteDir(projectDir);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void testBasic() throws Exception {
        File projectDir = createProject("test1", "test.pkg");

        // Add some files in there that we are ignoring
        new File(projectDir, "ic_launcher-web.png").createNewFile();
        new File(projectDir, "Android.mk").createNewFile();
        new File(projectDir, "build.properties").createNewFile();
        new File(projectDir, "local.properties").createNewFile();
        new File(projectDir, "src" + separator + ".git").mkdir();
        new File(projectDir, "src" + separator + ".svn").mkdir();

        // Project being imported
        assertEquals(""
                + ".classpath\n"
                + ".project\n"
                + "Android.mk\n"
                + "AndroidManifest.xml\n"
                + "build.properties\n"
                + "gen\n"
                + "  test\n"
                + "    pkg\n"
                + "      R.java\n"
                + "ic_launcher-web.png\n"
                + "local.properties\n"
                + "project.properties\n"
                + "res\n"
                + "  drawable\n"
                + "    ic_launcher.xml\n"
                + "  values\n"
                + "    strings.xml\n"
                + "src\n"
                + "  .git\n"
                + "  .svn\n"
                + "  test\n"
                + "    pkg\n"
                + "      MyActivity.java\n",
                fileTree(projectDir, true));

        File imported = checkProject(projectDir, ""
                + MSG_HEADER
                + MSG_UNHANDLED
                + "* Android.mk\n"
                + "* build.properties\n"
                + "* ic_launcher-web.png\n"
                + MSG_FOLDER_STRUCTURE
                + DEFAULT_MOVED
                + MSG_FOOTER,
                true /* checkBuild */);

        // Imported contents
        assertEquals(""
                + "app\n"
                + "  build.gradle\n"
                + "  src\n"
                + "    main\n"
                + "      AndroidManifest.xml\n"
                + "      java\n"
                + "        test\n"
                + "          pkg\n"
                + "            MyActivity.java\n"
                + "      res\n"
                + "        drawable\n"
                + "          ic_launcher.xml\n"
                + "        values\n"
                + "          strings.xml\n"
                + "build.gradle\n"
                + "import-summary.txt\n"
                + "local.properties\n"
                + "settings.gradle\n",
                fileTree(imported, true));

        deleteDir(projectDir);
        deleteDir(imported);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void testMoveRsAndAidl() throws Exception {
        File projectDir = createProject("test1", "test.pkg");
        createSampleAidlFile(projectDir, "src", "test.pkg");
        createSampleRsFile(projectDir, "src", "test.pkg");

        // Project being imported
        assertEquals(""
                + ".classpath\n"
                + ".project\n"
                + "AndroidManifest.xml\n"
                + "gen\n"
                + "  test\n"
                + "    pkg\n"
                + "      R.java\n"
                + "project.properties\n"
                + "res\n"
                + "  drawable\n"
                + "    ic_launcher.xml\n"
                + "  values\n"
                + "    strings.xml\n"
                + "src\n"
                + "  test\n"
                + "    pkg\n"
                + "      IHardwareService.aidl\n"
                + "      MyActivity.java\n"
                + "      latency.rs\n",
                fileTree(projectDir, true));

        File imported = checkProject(projectDir, ""
                + MSG_HEADER
                + MSG_FOLDER_STRUCTURE
                + DEFAULT_MOVED
                + "* src/test/pkg/IHardwareService.aidl => app/src/main/aidl/test/pkg/IHardwareService.aidl\n"
                + "* src/test/pkg/latency.rs => app/src/main/rs/latency.rs\n"
                + MSG_FOOTER,
                true /* checkBuild */);

        // Imported contents
        assertEquals(""
                + "app\n"
                + "  build.gradle\n"
                + "  src\n"
                + "    main\n"
                + "      AndroidManifest.xml\n"
                + "      aidl\n"
                + "        test\n"
                + "          pkg\n"
                + "            IHardwareService.aidl\n"
                + "      java\n"
                + "        test\n"
                + "          pkg\n"
                + "            MyActivity.java\n"
                + "      res\n"
                + "        drawable\n"
                + "          ic_launcher.xml\n"
                + "        values\n"
                + "          strings.xml\n"
                + "      rs\n"
                + "        latency.rs\n"
                + "build.gradle\n"
                + "import-summary.txt\n"
                + "local.properties\n"
                + "settings.gradle\n",
                fileTree(imported, true));

        deleteDir(projectDir);
        deleteDir(imported);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void testLibraries() throws Exception {
        File root = Files.createTempDir();
        File app = createLibrary(root, "test.lib2.pkg");

        // ADT Directory structure created by the above:
        assertEquals(""
                + "App\n"
                + "  .classpath\n"
                + "  .gitignore\n"
                + "  .project\n"
                + "  AndroidManifest.xml\n"
                + "  gen\n"
                + "    test\n"
                + "      pkg\n"
                + "        R.java\n"
                + "  project.properties\n"
                + "  res\n"
                + "    drawable\n"
                + "      ic_launcher.xml\n"
                + "    values\n"
                + "      strings.xml\n"
                + "  src\n"
                + "    test\n"
                + "      pkg\n"
                + "        MyActivity.java\n"
                + "Lib1\n"
                + "  .classpath\n"
                + "  .project\n"
                + "  AndroidManifest.xml\n"
                + "  gen\n"
                + "    test\n"
                + "      lib\n"
                + "        pkg\n"
                + "          R.java\n"
                + "  project.properties\n"
                + "  src\n"
                + "    test\n"
                + "      lib\n"
                + "        pkg\n"
                + "          MyLibActivity.java\n"
                + "Lib2\n"
                + "  .classpath\n"
                + "  .project\n"
                + "  AndroidManifest.xml\n"
                + "  gen\n"
                + "    test\n"
                + "      lib2\n"
                + "        pkg\n"
                + "          R.java\n"
                + "  project.properties\n"
                + "  src\n"
                + "    test\n"
                + "      lib2\n"
                + "        pkg\n"
                + "          MyLib2Activity.java\n"
                + "subdir1\n"
                + "  subdir2\n"
                + "    JavaLib\n"
                + "      .classpath\n"
                + "      .gitignore\n"
                + "      .project\n"
                + "      src\n"
                + "        test\n"
                + "          lib2\n"
                + "            pkg\n"
                + "              Utilities.java\n",
                fileTree(root, true));

        File imported = checkProject(app, ""
                + MSG_HEADER
                + MSG_MANIFEST
                + MSG_UNHANDLED
                + "From App:\n"
                + "* .gitignore\n"
                + "From JavaLib:\n"
                + "* .gitignore\n"
                + MSG_FOLDER_STRUCTURE
                + "In JavaLib:\n"
                + "* src/ => javaLib/src/main/java/\n"
                + "In Lib1:\n"
                + "* AndroidManifest.xml => lib1/src/main/AndroidManifest.xml\n"
                + "* src/ => lib1/src/main/java/\n"
                + "In Lib2:\n"
                + "* AndroidManifest.xml => lib2/src/main/AndroidManifest.xml\n"
                + "* src/ => lib2/src/main/java/\n"
                + "In App:\n"
                + "* AndroidManifest.xml => app/src/main/AndroidManifest.xml\n"
                + "* res/ => app/src/main/res/\n"
                + "* src/ => app/src/main/java/\n"
                + MSG_FOOTER,
                true /* checkBuild */);

        // Imported project
        assertEquals(""
                + "app\n"
                + "  build.gradle\n"
                + "  src\n"
                + "    main\n"
                + "      AndroidManifest.xml\n"
                + "      java\n"
                + "        test\n"
                + "          pkg\n"
                + "            MyActivity.java\n"
                + "      res\n"
                + "        drawable\n"
                + "          ic_launcher.xml\n"
                + "        values\n"
                + "          strings.xml\n"
                + "build.gradle\n"
                + "import-summary.txt\n"
                + "javaLib\n"
                + "  build.gradle\n"
                + "  src\n"
                + "    main\n"
                + "      java\n"
                + "        test\n"
                + "          lib2\n"
                + "            pkg\n"
                + "              Utilities.java\n"
                + "lib1\n"
                + "  build.gradle\n"
                + "  src\n"
                + "    main\n"
                + "      AndroidManifest.xml\n"
                + "      java\n"
                + "        test\n"
                + "          lib\n"
                + "            pkg\n"
                + "              MyLibActivity.java\n"
                + "lib2\n"
                + "  build.gradle\n"
                + "  src\n"
                + "    main\n"
                + "      AndroidManifest.xml\n"
                + "      java\n"
                + "        test\n"
                + "          lib2\n"
                + "            pkg\n"
                + "              MyLib2Activity.java\n"
                + "local.properties\n"
                + "settings.gradle\n",
                fileTree(imported, true));

        // Let's peek at some of the key files to make sure we codegen'ed the right thing
        //noinspection PointlessBooleanExpression,ConstantConditions
        assertEquals(""
                + (!DECLARE_GLOBAL_REPOSITORIES ?
                "buildscript {\n"
                + "    repositories {\n"
                + "        " + MAVEN_REPOSITORY + "\n"
                + "    }\n"
                + "}\n" : "")
                + "apply plugin: 'java'\n",
                Files.toString(new File(imported, "javaLib" + separator + "build.gradle"), UTF_8)
                        .replace(NL, "\n"));

        // Let's peek at some of the key files to make sure we codegen'ed the right thing
        //noinspection ConstantConditions
        assertEquals(""
                + "// Top-level build file where you can add configuration options common to all sub-projects/modules.\n"
                + (DECLARE_GLOBAL_REPOSITORIES ?
                    "buildscript {\n"
                    + "    repositories {\n"
                    + "        " + MAVEN_REPOSITORY + "\n"
                    + "    }\n"
                    + "    dependencies {\n"
                    + "        classpath '" + ANDROID_GRADLE_PLUGIN + "'\n"
                    + "    }\n"
                    + "}\n"
                    + "\n"
                    + "allprojects {\n"
                    + "    repositories {\n"
                    + "        " + MAVEN_REPOSITORY + "\n"
                    + "    }\n"
                    + "}\n" : ""),
                Files.toString(new File(imported, "build.gradle"), UTF_8)
                        .replace(NL, "\n"));

        //noinspection PointlessBooleanExpression,ConstantConditions
        assertEquals(""
                + (!DECLARE_GLOBAL_REPOSITORIES ?
                    "buildscript {\n"
                    + "    repositories {\n"
                    + "        " + MAVEN_REPOSITORY + "\n"
                    + "    }\n"
                    + "    dependencies {\n"
                    + "        classpath '" + ANDROID_GRADLE_PLUGIN + "'\n"
                    + "    }\n"
                    + "}\n" : "")
                + "apply plugin: 'android'\n"
                + (!DECLARE_GLOBAL_REPOSITORIES ?
                    "\n"
                    + "repositories {\n"
                    + "    " + MAVEN_REPOSITORY + "\n"
                    + "}\n" : "")
                + "\n"
                + "android {\n"
                + "    compileSdkVersion 17\n"
                + "    buildToolsVersion \"" + BUILD_TOOLS_VERSION + "\"\n"
                + "\n"
                + "    defaultConfig {\n"
                + "        minSdkVersion 8\n"
                + "        targetSdkVersion 16\n"
                + "    }\n"
                + "\n"
                + "    buildTypes {\n"
                + "        release {\n"
                + "            runProguard false\n"
                + "            proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.txt'\n"
                + "        }\n"
                + "    }\n"
                + "}\n"
                + "\n"
                + "dependencies {\n"
                + "    compile project(':lib1')\n"
                + "    compile project(':lib2')\n"
                + "    compile project(':javaLib')\n"
                + "}",
                Files.toString(new File(imported, "app" + separator + "build.gradle"), UTF_8)
                        .replace(NL,"\n"));
        //noinspection PointlessBooleanExpression,ConstantConditions
        assertEquals(""
                + (!DECLARE_GLOBAL_REPOSITORIES ?
                    "buildscript {\n"
                    + "    repositories {\n"
                    + "        " + MAVEN_REPOSITORY + "\n"
                    + "    }\n"
                    + "    dependencies {\n"
                    + "        classpath '" + ANDROID_GRADLE_PLUGIN + "'\n"
                    + "    }\n"
                    + "}\n" : "")
                + "apply plugin: 'android-library'\n"
                + (!DECLARE_GLOBAL_REPOSITORIES ?
                    "\n"
                    + "repositories {\n"
                    + "    " + MAVEN_REPOSITORY + "\n"
                    + "}\n" : "")
                + "\n"
                + "android {\n"
                + "    compileSdkVersion 18\n"
                + "    buildToolsVersion \"" + BUILD_TOOLS_VERSION + "\"\n"
                + "\n"
                + "    defaultConfig {\n"
                + "        minSdkVersion 8\n"
                + "        targetSdkVersion 8\n"
                + "    }\n"
                + "\n"
                + "    release {\n"
                + "        runProguard false\n"
                + "        proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.txt'\n"
                + "    }\n"
                + "}\n"
                + "\n"
                + "dependencies {\n"
                + "    compile project(':lib1')\n"
                + "}",
                Files.toString(new File(imported, "lib2" + separator + "build.gradle"), UTF_8)
                        .replace(NL, "\n"));
        assertEquals(""
                + "include ':javaLib'\n"
                + "include ':lib1'\n"
                + "include ':lib2'\n"
                + "include ':app'\n",
                Files.toString(new File(imported, "settings.gradle"), UTF_8)
                        .replace(NL, "\n"));

        deleteDir(root);
        deleteDir(imported);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static File createLibrary(File root, String lib2Pkg) throws IOException {
        // Plain Java library, used by Library 1 and App
        String javaLibName = "JavaLib";
        String javaLibRelative = "subdir1" + separator + "subdir2" + separator + javaLibName;
        File javaLib = new File(root, javaLibRelative);
        javaLib.mkdirs();
        String javaLibPkg = "test.lib2.pkg";
        createDotProject(javaLib, javaLibName, false);
        File javaLibSrc = new File("src");
        createSampleJavaSource(javaLib, "src", javaLibPkg, "Utilities");
        createClassPath(javaLib,
                new File("bin"),
                Collections.singletonList(javaLibSrc),
                Collections.<File>emptyList());

        // Make Android library 1

        String lib1Name = "Lib1";
        File lib1 = new File(root, lib1Name);
        lib1.mkdirs();
        String lib1Pkg = "test.lib.pkg";
        createDotProject(lib1, lib1Name, true);
        File lib1Src = new File("src");
        File lib1Gen = new File("gen");
        createSampleJavaSource(lib1, "src", lib1Pkg, "MyLibActivity");
        createSampleJavaSource(lib1, "gen", lib1Pkg, "R");
        createClassPath(lib1,
                new File("bin", "classes"),
                Arrays.asList(lib1Src, lib1Gen),
                Collections.<File>emptyList());
        createProjectProperties(lib1, "android-19", null, true, null,
                Collections.singletonList(new File(".." + separator + javaLibRelative)));
        createAndroidManifest(lib1, lib1Pkg, -1, -1, "<application/>");

        String lib2Name = "Lib2";
        File lib2 = new File(root, lib2Name);
        lib2.mkdirs();
        createDotProject(lib2, lib2Name, true);
        File lib2Src = new File("src");
        File lib2Gen = new File("gen");
        createSampleJavaSource(lib2, "src", lib2Pkg, "MyLib2Activity");
        createSampleJavaSource(lib2, "gen", lib2Pkg, "R");
        createClassPath(lib2,
                new File("bin", "classes"),
                Arrays.asList(lib2Src, lib2Gen),
                Collections.<File>emptyList());
        createProjectProperties(lib2, "android-18", null, true, null,
                Collections.singletonList(new File(".." + separator + lib1Name)));
        createAndroidManifest(lib2, lib2Pkg, 7, -1, "<application/>");

        // Main app project, depends on library1, library2 and java lib
        String appName = "App";
        File app = new File(root, appName);
        app.mkdirs();
        String appPkg = "test.pkg";
        createDotProject(app, appName, true);
        File appSrc = new File("src");
        File appGen = new File("gen");
        createSampleJavaSource(app, "src", appPkg, "MyActivity");
        createSampleJavaSource(app, "gen", appPkg, "R");
        createClassPath(app,
                new File("bin", "classes"),
                Arrays.asList(appSrc, appGen),
                Collections.<File>emptyList());
        createProjectProperties(app, "android-17", null, null, null,
                Arrays.asList(
                        new File(".." + separator + lib1Name),
                        new File(".." + separator + lib2Name),
                        new File(".." + separator + javaLibRelative)));
        createAndroidManifest(app, appPkg, 8, 16, null);
        createDefaultStrings(app);
        createDefaultIcon(app);

        // Add some files in there that we are ignoring
        new File(app, ".gitignore").createNewFile();
        new File(javaLib, ".gitignore").createNewFile();
        return app;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void testReplaceJar() throws Exception {
        // Add in some well known jars and make sure they get migrated as dependencies
        File projectDir = createProject("test1", "test.pkg");
        File libs = new File(projectDir, "libs");
        libs.mkdirs();
        new File(libs, "android-support-v4.jar").createNewFile();
        new File(libs, "android-support-v7-gridlayout.jar").createNewFile();
        new File(libs, "android-support-v7-appcompat.jar").createNewFile();

        File imported = checkProject(projectDir, ""
                + MSG_HEADER
                + MSG_REPLACED_JARS
                + "android-support-v4.jar => com.android.support:support-v4:+\n"
                + "android-support-v7-appcompat.jar => com.android.support:appcompat-v7:+\n"
                + "android-support-v7-gridlayout.jar => com.android.support:gridlayout-v7:+\n"
                + MSG_FOLDER_STRUCTURE
                + DEFAULT_MOVED
                + MSG_FOOTER,
                true /* checkBuild */);

        // Imported contents
        assertEquals(""
                + "app\n"
                + "  build.gradle\n"
                + "  src\n"
                + "    main\n"
                + "      AndroidManifest.xml\n"
                + "      java\n"
                + "        test\n"
                + "          pkg\n"
                + "            MyActivity.java\n"
                + "      res\n"
                + "        drawable\n"
                + "          ic_launcher.xml\n"
                + "        values\n"
                + "          strings.xml\n"
                + "build.gradle\n"
                + "import-summary.txt\n"
                + "local.properties\n"
                + "settings.gradle\n",
                fileTree(imported, true));

        //noinspection PointlessBooleanExpression,ConstantConditions
        assertEquals(""
                + (!DECLARE_GLOBAL_REPOSITORIES ?
                    "buildscript {\n"
                    + "    repositories {\n"
                    + "        " + MAVEN_REPOSITORY + "\n"
                    + "    }\n"
                    + "    dependencies {\n"
                    + "        classpath '" + ANDROID_GRADLE_PLUGIN + "'\n"
                    + "    }\n"
                    + "}\n" : "")
                + "apply plugin: 'android'\n"
                + (!DECLARE_GLOBAL_REPOSITORIES ?
                    "\n"
                    + "repositories {\n"
                    + "    " + MAVEN_REPOSITORY + "\n"
                    + "}\n" : "")
                + "\n"
                + "android {\n"
                + "    compileSdkVersion 17\n"
                + "    buildToolsVersion \"" + BUILD_TOOLS_VERSION + "\"\n"
                + "\n"
                + "    defaultConfig {\n"
                + "        minSdkVersion 8\n"
                + "        targetSdkVersion 16\n"
                + "    }\n"
                + "\n"
                + "    buildTypes {\n"
                + "        release {\n"
                + "            runProguard false\n"
                + "            proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.txt'\n"
                + "        }\n"
                + "    }\n"
                + "}\n"
                + "\n"
                + "dependencies {\n"
                + "    compile 'com.android.support:support-v4:+'\n"
                + "    compile 'com.android.support:appcompat-v7:+'\n"
                + "    compile 'com.android.support:gridlayout-v7:+'\n"
                + "}",
                Files.toString(new File(imported, "app" + separator + "build.gradle"), UTF_8)
                        .replace(NL, "\n"));

        deleteDir(projectDir);
        deleteDir(imported);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void testOptions() throws Exception {
        // Check options like turning off jar replacement and leaving module names capitalized
        File projectDir = createProject("Test1", "test.pkg");
        File libs = new File(projectDir, "libs");
        libs.mkdirs();
        new File(libs, "android-support-v4.jar").createNewFile();
        new File(libs, "android-support-v7-gridlayout.jar").createNewFile();
        new File(libs, "android-support-v7-appcompat.jar").createNewFile();
        new File(libs, "armeabi").mkdirs();

        File imported = checkProject(projectDir, ""
                + MSG_HEADER
                + MSG_FOLDER_STRUCTURE
                + "* AndroidManifest.xml => Test1/src/main/AndroidManifest.xml\n"
                + "* libs/android-support-v4.jar => Test1/libs/android-support-v4.jar\n"
                + "* libs/android-support-v7-appcompat.jar => Test1/libs/android-support-v7-appcompat.jar\n"
                + "* libs/android-support-v7-gridlayout.jar => Test1/libs/android-support-v7-gridlayout.jar\n"
                + "* res/ => Test1/src/main/res/\n"
                + "* src/ => Test1/src/main/java/\n"
                + MSG_FOOTER,
                false /* checkBuild */,
                new ImportCustomizer() {
                    @Override
                    public void customize(GradleImport importer) {
                        importer.setGradleNameStyle(false);
                        importer.setReplaceJars(false);
                        importer.setReplaceLibs(false);
                    }
                });

        // Imported contents
        assertEquals(""
                + "Test1\n"
                + "  build.gradle\n"
                + "  libs\n"
                + "    android-support-v4.jar\n"
                + "    android-support-v7-appcompat.jar\n"
                + "    android-support-v7-gridlayout.jar\n"
                + "  src\n"
                + "    main\n"
                + "      AndroidManifest.xml\n"
                + "      java\n"
                + "        test\n"
                + "          pkg\n"
                + "            MyActivity.java\n"
                + "      res\n"
                + "        drawable\n"
                + "          ic_launcher.xml\n"
                + "        values\n"
                + "          strings.xml\n"
                + "build.gradle\n"
                + "import-summary.txt\n"
                + "local.properties\n"
                + "settings.gradle\n",
                fileTree(imported, true));

        //noinspection PointlessBooleanExpression,ConstantConditions
        assertEquals(""
                + (!DECLARE_GLOBAL_REPOSITORIES ?
                    "buildscript {\n"
                    + "    repositories {\n"
                    + "        " + MAVEN_REPOSITORY + "\n"
                    + "    }\n"
                    + "    dependencies {\n"
                    + "        classpath '" + ANDROID_GRADLE_PLUGIN + "'\n"
                    + "    }\n"
                    + "}\n" : "")
                + "apply plugin: 'android'\n"
                + (!DECLARE_GLOBAL_REPOSITORIES ?
                    "\n"
                    + "repositories {\n"
                    + "    " + MAVEN_REPOSITORY + "\n"
                    + "}\n" : "")
                + "\n"
                + "android {\n"
                + "    compileSdkVersion 17\n"
                + "    buildToolsVersion \"" + BUILD_TOOLS_VERSION + "\"\n"
                + "\n"
                + "    defaultConfig {\n"
                + "        minSdkVersion 8\n"
                + "        targetSdkVersion 16\n"
                + "    }\n"
                + "\n"
                + "    buildTypes {\n"
                + "        release {\n"
                + "            runProguard false\n"
                + "            proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.txt'\n"
                + "        }\n"
                + "    }\n"
                + "}\n"
                + "\n"
                + "dependencies {\n"
                + "    compile files('libs/android-support-v4.jar')\n"
                + "    compile files('libs/android-support-v7-appcompat.jar')\n"
                + "    compile files('libs/android-support-v7-gridlayout.jar')\n"
                + "}",
                Files.toString(new File(imported, "test1" + separator + "build.gradle"), UTF_8)
                        .replace(NL, "\n"));

        deleteDir(projectDir);
        deleteDir(imported);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void testJni() throws Exception {
        File root = Files.createTempDir();
        final File sdkLocation = new File(root, "sdk");
        sdkLocation.mkdirs();
        final File ndkLocation = new File(root, "ndk");
        ndkLocation.mkdirs();
        File projectDir = new File(root, "project");
        projectDir.mkdirs();
        createProject(projectDir, "testJni", "test.pkg");
        createDotProject(projectDir, "testJni", true, true);
        File jni = new File(projectDir, "jni");
        jni.mkdirs();
        File makefile = new File(jni, "Android.mk");
        Files.write(""
                + "LOCAL_PATH := $(call my-dir)\n"
                + "\n"
                + "include $(CLEAR_VARS)\n"
                + "\n"
                + "LOCAL_MODULE    := hello-jni\n"
                + "LOCAL_SRC_FILES := hello-jni.c\n"
                + "\n"
                + "include $(BUILD_SHARED_LIBRARY)",
                makefile, UTF_8);
        new File(jni, "Application.mk").createNewFile();
        new File(jni, "HelloJni.cpp").createNewFile();
        new File(jni, "hello-jni.c").createNewFile();

        File libs = new File(projectDir, "libs");
        libs.mkdirs();
        File armeabi = new File(libs, "armeabi");
        armeabi.mkdirs();
        new File(armeabi, "libexternal.so").createNewFile();
        new File(armeabi, "libhello-jni.so").createNewFile();
        File mips = new File(libs, "mips");
        mips.mkdirs();
        new File(mips, "libexternal.so").createNewFile();
        new File(mips, "libhello-jni.so").createNewFile();

        Files.write(
                escapeProperty("sdk.dir", sdkLocation.getPath()) + "\n" +
                escapeProperty("ndk.dir", ndkLocation.getPath()) + "\n",
                new File(projectDir, FN_LOCAL_PROPERTIES), UTF_8);

        File imported = checkProject(projectDir, ""
                + MSG_HEADER
                + MSG_FOLDER_STRUCTURE
                + "* AndroidManifest.xml => testJni/src/main/AndroidManifest.xml\n"
                + "* jni/ => testJni/src/main/jni/\n"
                + "* libs/armeabi/libexternal.so => testJni/src/main/jniLibs/armeabi/libexternal.so\n"
                + "* libs/mips/libexternal.so => testJni/src/main/jniLibs/mips/libexternal.so\n"
                + "* res/ => testJni/src/main/res/\n"
                + "* src/ => testJni/src/main/java/\n"
                + MSG_FOOTER,
                false /* checkBuild */,
                new ImportCustomizer() {
                    @Override
                    public void customize(GradleImport importer) {
                        importer.setGradleNameStyle(false);
                        importer.setSdkLocation(null);
                        importer.setReplaceJars(false);
                        importer.setReplaceLibs(false);
                    }
                });

        // Imported contents
        assertEquals(""
                + "build.gradle\n"
                + "import-summary.txt\n"
                + "local.properties\n"
                + "settings.gradle\n"
                + "testJni\n"
                + "  build.gradle\n"
                + "  src\n"
                + "    main\n"
                + "      AndroidManifest.xml\n"
                + "      java\n"
                + "        test\n"
                + "          pkg\n"
                + "            MyActivity.java\n"
                + "      jni\n"
                + "        Android.mk\n"
                + "        Application.mk\n"
                + "        HelloJni.cpp\n"
                + "        hello-jni.c\n"
                + "      jniLibs\n"
                + "        armeabi\n"
                + "          libexternal.so\n"
                + "        mips\n"
                + "          libexternal.so\n"
                + "      res\n"
                + "        drawable\n"
                + "          ic_launcher.xml\n"
                + "        values\n"
                + "          strings.xml\n",
                fileTree(imported, true));

        //noinspection PointlessBooleanExpression,ConstantConditions
        assertEquals(""
                + (!DECLARE_GLOBAL_REPOSITORIES ?
                "buildscript {\n"
                + "    repositories {\n"
                + "        " + MAVEN_REPOSITORY + "\n"
                + "    }\n"
                + "    dependencies {\n"
                + "        classpath '" + ANDROID_GRADLE_PLUGIN + "'\n"
                + "    }\n"
                + "}\n" : "")
                + "apply plugin: 'android'\n"
                + (!DECLARE_GLOBAL_REPOSITORIES ?
                "\n"
                + "repositories {\n"
                + "    " + MAVEN_REPOSITORY + "\n"
                + "}\n" : "")
                + "\n"
                + "android {\n"
                + "    compileSdkVersion 17\n"
                + "    buildToolsVersion \"" + BUILD_TOOLS_VERSION + "\"\n"
                + "\n"
                + "    defaultConfig {\n"
                + "        minSdkVersion 8\n"
                + "        targetSdkVersion 16\n"
                + "\n"
                + "        ndk {\n"
                + "            moduleName \"hello-jni\"\n"
                + "        }\n"
                + "    }\n"
                + "\n"
                + "    buildTypes {\n"
                + "        release {\n"
                + "            runProguard false\n"
                + "            proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.txt'\n"
                + "        }\n"
                + "    }\n"
                + "}\n",
                Files.toString(new File(imported, "testJni" + separator + "build.gradle"), UTF_8)
                        .replace(NL, "\n"));

        assertEquals(sdkLocation.getPath(),
                GradleImport.getProperties(new File(imported, FN_LOCAL_PROPERTIES)).
                        getProperty("sdk.dir"));
        assertEquals(ndkLocation.getPath(),
                GradleImport.getProperties(new File(imported, FN_LOCAL_PROPERTIES)).
                        getProperty("ndk.dir"));

        deleteDir(root);
        deleteDir(imported);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void testJniLibs() throws Exception {
        // Check that ABI libs are copied to the right place
        File projectDir = createProject("Test1", "test.pkg");
        File libs = new File(projectDir, "libs");
        libs.mkdirs();
        new File(libs, "android-support-v4.jar").createNewFile();
        File armeabi = new File(libs, "armeabi");
        armeabi.mkdirs();
        new File(armeabi, "libfoo.so").createNewFile();

        File imported = checkProject(projectDir, ""
                + MSG_HEADER
                + MSG_FOLDER_STRUCTURE
                + "* AndroidManifest.xml => Test1/src/main/AndroidManifest.xml\n"
                + "* libs/android-support-v4.jar => Test1/libs/android-support-v4.jar\n"
                + "* libs/armeabi/libfoo.so => Test1/src/main/jniLibs/armeabi/libfoo.so\n"
                + "* res/ => Test1/src/main/res/\n"
                + "* src/ => Test1/src/main/java/\n"
                + MSG_FOOTER,
                false /* checkBuild */,
                new ImportCustomizer() {
                    @Override
                    public void customize(GradleImport importer) {
                        importer.setGradleNameStyle(false);
                        importer.setReplaceJars(false);
                        importer.setReplaceLibs(false);
                    }
                });

        // Imported contents
        assertEquals(""
                + "Test1\n"
                + "  build.gradle\n"
                + "  libs\n"
                + "    android-support-v4.jar\n"
                + "  src\n"
                + "    main\n"
                + "      AndroidManifest.xml\n"
                + "      java\n"
                + "        test\n"
                + "          pkg\n"
                + "            MyActivity.java\n"
                + "      jniLibs\n"
                + "        armeabi\n"
                + "          libfoo.so\n"
                + "      res\n"
                + "        drawable\n"
                + "          ic_launcher.xml\n"
                + "        values\n"
                + "          strings.xml\n"
                + "build.gradle\n"
                + "import-summary.txt\n"
                + "local.properties\n"
                + "settings.gradle\n",
                fileTree(imported, true));

        deleteDir(projectDir);
        deleteDir(imported);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void testReplaceSourceLibraryProject() throws Exception {
        // Make a library project which looks like it can just be replaced by a project

        File root = Files.createTempDir();
        // Pretend lib2 is ActionBarSherlock; it should then be stripped out and replaced
        // by a set of dependencies
        File app = createLibrary(root, "com.actionbarsherlock");

        File imported = checkProject(app, ""
                + MSG_HEADER
                + MSG_MANIFEST
                + MSG_UNHANDLED
                + "From App:\n"
                + "* .gitignore\n"
                + "From JavaLib:\n"
                + "* .gitignore\n"
                + MSG_REPLACED_LIBS
                + "Lib2 =>\n"
                + "    com.actionbarsherlock:actionbarsherlock:4.4.0@aar\n"
                + "    com.android.support:support-v4:+\n"
                + MSG_FOLDER_STRUCTURE
                // TODO: The summary should describe the library!!
                + "In JavaLib:\n"
                + "* src/ => javaLib/src/main/java/\n"
                + "In Lib1:\n"
                + "* AndroidManifest.xml => lib1/src/main/AndroidManifest.xml\n"
                + "* src/ => lib1/src/main/java/\n"
                + "In App:\n"
                + "* AndroidManifest.xml => app/src/main/AndroidManifest.xml\n"
                + "* res/ => app/src/main/res/\n"
                + "* src/ => app/src/main/java/\n"
                + MSG_FOOTER,
                false /* checkBuild */);

        // Imported project; note how lib2 is gone
        assertEquals(""
                + "app\n"
                + "  build.gradle\n"
                + "  src\n"
                + "    main\n"
                + "      AndroidManifest.xml\n"
                + "      java\n"
                + "        test\n"
                + "          pkg\n"
                + "            MyActivity.java\n"
                + "      res\n"
                + "        drawable\n"
                + "          ic_launcher.xml\n"
                + "        values\n"
                + "          strings.xml\n"
                + "build.gradle\n"
                + "import-summary.txt\n"
                + "javaLib\n"
                + "  build.gradle\n"
                + "  src\n"
                + "    main\n"
                + "      java\n"
                + "        test\n"
                + "          lib2\n"
                + "            pkg\n"
                + "              Utilities.java\n"
                + "lib1\n"
                + "  build.gradle\n"
                + "  src\n"
                + "    main\n"
                + "      AndroidManifest.xml\n"
                + "      java\n"
                + "        test\n"
                + "          lib\n"
                + "            pkg\n"
                + "              MyLibActivity.java\n"
                + "local.properties\n"
                + "settings.gradle\n",
                fileTree(imported, true));

        //noinspection PointlessBooleanExpression,ConstantConditions
        assertEquals(""
                + (!DECLARE_GLOBAL_REPOSITORIES ?
                    "buildscript {\n"
                    + "    repositories {\n"
                    + "        " + MAVEN_REPOSITORY + "\n"
                    + "    }\n"
                    + "    dependencies {\n"
                    + "        classpath '" + ANDROID_GRADLE_PLUGIN + "'\n"
                    + "    }\n"
                    + "}\n" : "")
                + "apply plugin: 'android'\n"
                + (!DECLARE_GLOBAL_REPOSITORIES ?
                    "\n"
                    + "repositories {\n"
                    + "    " + MAVEN_REPOSITORY + "\n"
                    + "}\n" : "")
                + "\n"
                + "android {\n"
                + "    compileSdkVersion 17\n"
                + "    buildToolsVersion \"" + BUILD_TOOLS_VERSION + "\"\n"
                + "\n"
                + "    defaultConfig {\n"
                + "        minSdkVersion 8\n"
                + "        targetSdkVersion 16\n"
                + "    }\n"
                + "\n"
                + "    buildTypes {\n"
                + "        release {\n"
                + "            runProguard false\n"
                + "            proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.txt'\n"
                + "        }\n"
                + "    }\n"
                + "}\n"
                + "\n"
                + "dependencies {\n"
                + "    compile project(':lib1')\n"
                + "    compile project(':javaLib')\n"
                + "    compile 'com.actionbarsherlock:actionbarsherlock:4.4.0@aar'\n"
                + "    compile 'com.android.support:support-v4:+'\n"
                + "}",
                Files.toString(new File(imported, "app" + separator + "build.gradle"), UTF_8)
                        .replace(NL, "\n"));

        deleteDir(root);
        deleteDir(imported);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void testMissingRepositories() throws Exception {
        File root = Files.createTempDir();
        final File sdkLocation = new File(root, "sdk");
        sdkLocation.mkdirs();
        File projectDir = new File(root, "project");
        projectDir.mkdirs();
        createProject(projectDir, "test1", "test.pkg");
        File libs = new File(projectDir, "libs");
        libs.mkdirs();
        new File(libs, "android-support-v4.jar").createNewFile();

        File imported = checkProject(projectDir, ""
                + MSG_HEADER
                + MSG_REPLACED_JARS
                + "android-support-v4.jar => com.android.support:support-v4:+\n"
                + MSG_FOLDER_STRUCTURE
                + DEFAULT_MOVED
                + MSG_MISSING_REPO_1
                + "$ROOT_PARENT/sdk\n"
                + MSG_MISSING_REPO_2
                + MSG_FOOTER,
                false /* checkBuild */, new ImportCustomizer() {
            @Override
            public void customize(GradleImport importer) {
                importer.setSdkLocation(sdkLocation);
            }
        });

        deleteDir(root);
        deleteDir(imported);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void testMissingPlayRepositories() throws Exception {
        File root = Files.createTempDir();
        final File sdkLocation = new File(root, "sdk");
        sdkLocation.mkdirs();
        File projectDir = new File(root, "project");
        projectDir.mkdirs();
        createProject(projectDir, "test1", "test.pkg");
        File libs = new File(projectDir, "libs");
        libs.mkdirs();
        new File(libs, "gcm.jar").createNewFile();

        File imported = checkProject(projectDir, ""
                + MSG_HEADER
                + MSG_REPLACED_JARS
                + "gcm.jar => com.google.android.gms:play-services:+\n"
                + MSG_FOLDER_STRUCTURE
                + DEFAULT_MOVED
                + MSG_MISSING_GOOGLE_REPOSITORY_1
                + "$ROOT_PARENT/sdk\n"
                + MSG_MISSING_GOOGLE_REPOSITORY_2
                + MSG_FOOTER,
                false /* checkBuild */, new ImportCustomizer() {
            @Override
            public void customize(GradleImport importer) {
                importer.setSdkLocation(sdkLocation);
            }
        });

        deleteDir(root);
        deleteDir(imported);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void testGuessedVersion() throws Exception {
        File root = Files.createTempDir();
        final File sdkLocation = new File(root, "sdk");
        sdkLocation.mkdirs();
        File projectDir = new File(root, "project");
        projectDir.mkdirs();
        createProject(projectDir, "test1", "test.pkg");
        File libs = new File(projectDir, "libs");
        libs.mkdirs();
        new File(libs, "guava-13.0.1.jar").createNewFile();

        File imported = checkProject(projectDir, ""
                + MSG_HEADER
                + MSG_REPLACED_JARS
                + "guava-13.0.1.jar => com.google.guava:guava:13.0.1\n"
                + MSG_GUESSED_VERSIONS
                + "guava-13.0.1.jar => version 13.0.1 in com.google.guava:guava:13.0.1\n"
                + MSG_FOLDER_STRUCTURE
                + DEFAULT_MOVED
                + MSG_FOOTER,
                false /* checkBuild */, new ImportCustomizer() {
            @Override
            public void customize(GradleImport importer) {
                importer.setSdkLocation(sdkLocation);
            }
        });

        deleteDir(root);
        deleteDir(imported);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void testClassPathFilePaths() throws Exception {
        // Test a project where the .classpath file contains additional
        // issues: workspace-local dependencies for projects,
        // absolute paths to the framework, etc.

        File root = Files.createTempDir();
        File projectDir = new File(root, "prj");
        projectDir.mkdirs();
        projectDir = createProject(projectDir, "1 Weird 'name' of project!", "test.pkg");
        File lib = new File(root, "android-support-v7-appcompat");
        lib.mkdirs();

        File classpath = new File(projectDir, ".classpath");
        assertTrue(classpath.exists());
        classpath.delete();
        //noinspection SpellCheckingInspection
        Files.write(""
                + "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<classpath>\n"
                + "\t<classpathentry kind=\"src\" path=\"src\"/>\n"
                + "\t<classpathentry kind=\"src\" path=\"gen\"/>\n"
                + "\t<classpathentry kind=\"con\" path=\"com.android.ide.eclipse.adt.ANDROID_FRAMEWORK\"/>\n"
                + "\t<classpathentry exported=\"true\" kind=\"con\" path=\"com.android.ide.eclipse.adt.DEPENDENCIES\"/>\n"
                + "\t<classpathentry kind=\"lib\" path=\"libs/basic-http-client-android-0.88.jar\"/>\n"
                + "\t<classpathentry kind=\"lib\" path=\"/opt/android-sdk/platforms/android-14/android.jar\">\n"
                + "\t\t<attributes>\n"
                + "\t\t\t<attribute name=\"javadoc_location\" value=\"file:/opt/android-sdk/docs/reference\"/>\n"
                + "\t\t</attributes>\n"
                + "\t\t<accessrules>\n"
                + "\t\t\t<accessrule kind=\"nonaccessible\" pattern=\"com/android/internal/**\"/>\n"
                + "\t\t</accessrules>\n"
                + "\t</classpathentry>\n"
                + "\t<classpathentry kind=\"lib\" path=\"libs/htmlcleaner-2.6.jar\"/>\n"
                + "\t<classpathentry exported=\"true\" kind=\"con\" path=\"com.android.ide.eclipse.adt.LIBRARIES\"/>\n"
                + "\t<classpathentry combineaccessrules=\"false\" kind=\"src\" path=\"/android-support-v7-appcompat\"/>\n"
                + "\t<classpathentry kind=\"output\" path=\"bin/classes\"/>\n"
                + "</classpath>",
                classpath, UTF_8);

        //noinspection SpellCheckingInspection
        File imported = checkProject(projectDir, ""
                + MSG_HEADER
                + MSG_FOLDER_STRUCTURE
                + "* $ROOT_PARENT/android-support-v7-appcompat/ => _1Weirdnameofproject/src/main/java/\n"
                + "* AndroidManifest.xml => _1Weirdnameofproject/src/main/AndroidManifest.xml\n"
                + "* res/ => _1Weirdnameofproject/src/main/res/\n"
                + "* src/ => _1Weirdnameofproject/src/main/java/\n"
                + MSG_FOOTER,
                false /* checkBuild */, new ImportCustomizer() {
            @Override
            public void customize(GradleImport importer) {
                importer.setGradleNameStyle(false);
            }
        });

        deleteDir(projectDir);
        deleteDir(imported);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static Pair<File,File> createLibrary2(File library1Dir) throws Exception {
        File root = Files.createTempDir();

        // Workspace Setup
        // /Library1, compiled with 1.7, and depends on an external jar outside the project (guava)
        // /Library2 (depends on /Library1)
        // /AndroidLibraryProject (depend on /Library1, /Library2)
        // /AndroidAppProject (depends on /AndroidLibraryProject)
        // In addition to make things complicated, /Library1 can live outside the workspace
        // (based on the path we pass in)
        // and /Library2 lives in a subdirectory of the workspace

        // Plain Java library, used by Library 1 and App
        // Make Java Library library 1
        String lib1Name = "Library1";
        File lib1 = library1Dir.isAbsolute() ? library1Dir :
                new File(root, library1Dir.getPath());
        lib1.mkdirs();
        String lib1Pkg = "test.lib1.pkg";
        createDotProject(lib1, lib1Name, false);
        createSampleJavaSource(lib1, "src", lib1Pkg, "Library1");
        File guavaPath = new File(root, "some" + separator + "path" + separator +
                "guava-13.0.1.jar");
        guavaPath.getParentFile().mkdirs();
        guavaPath.createNewFile();
        Files.write(""
                + "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<classpath>\n"
                + "\t<classpathentry kind=\"src\" path=\"src\"/>\n"
                + "\t<classpathentry kind=\"con\" path=\"org.eclipse.jdt.launching.JRE_CONTAINER/org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType/Java 7\"/>\n"
                + "\t<classpathentry exported=\"true\" kind=\"lib\" path=\"" + guavaPath.getAbsoluteFile().getCanonicalFile().getPath() + "\"/>\n"
                + "\t<classpathentry kind=\"output\" path=\"bin\"/>\n"
                + "</classpath>",
                new File(lib1, ".classpath"), UTF_8);
        createEclipseSettingsFile(lib1, "1.6");

        // Make Java Library 2
        String lib2Name = "Library2";
        File lib2 = new File(root, lib2Name);
        lib2.mkdirs();
        createDotProject(lib2, lib2Name, false);
        String lib2Pkg = "test.lib2.pkg";
        createSampleJavaSource(lib2, "src", lib2Pkg, "Library2");
        Files.write(""
                + "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<classpath>\n"
                + "\t<classpathentry kind=\"src\" path=\"src\"/>\n"
                + "\t<classpathentry kind=\"con\" path=\"org.eclipse.jdt.launching.JRE_CONTAINER/org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType/Java 7\"/>\n"
                + "\t<classpathentry combineaccessrules=\"false\" kind=\"src\" path=\"/Library1\"/>\n"
                + "\t<classpathentry kind=\"output\" path=\"bin\"/>\n"
                + "</classpath>",
                new File(lib2, ".classpath"), UTF_8);
        createEclipseSettingsFile(lib2, "1.7");

        // Make Android Library Project 1
        String androidLibName = "AndroidLibrary";
        File androidLib = new File(root, androidLibName);
        androidLib.mkdirs();
        createDotProject(androidLib, androidLibName, true);
        String androidLibPkg = "test.android.lib.pkg";
        createSampleJavaSource(androidLib, "src", androidLibPkg, "AndroidLibrary");
        createSampleJavaSource(androidLib, "gen", androidLibPkg, "R");
        Files.write(""
                + "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<classpath>\n"
                + "\t<classpathentry kind=\"src\" path=\"src\"/>\n"
                + "\t<classpathentry kind=\"src\" path=\"gen\"/>\n"
                + "\t<classpathentry kind=\"con\" path=\"com.android.ide.eclipse.adt.ANDROID_FRAMEWORK\"/>\n"
                + "\t<classpathentry exported=\"true\" kind=\"con\" path=\"com.android.ide.eclipse.adt.LIBRARIES\"/>\n"
                + "\t<classpathentry exported=\"true\" kind=\"con\" path=\"com.android.ide.eclipse.adt.DEPENDENCIES\"/>\n"
                + "\t<classpathentry combineaccessrules=\"false\" exported=\"true\" kind=\"src\" path=\"/Library1\"/>\n"
                + "\t<classpathentry combineaccessrules=\"false\" exported=\"true\" kind=\"src\" path=\"/Library2\"/>\n"
                + "\t<classpathentry kind=\"output\" path=\"bin/classes\"/>\n"
                + "</classpath>", new File(androidLib, ".classpath"), UTF_8);
        createProjectProperties(androidLib, "android-18", null, true, null,
                // Note how Android library projects don't point to non-Android projects
                // in the project.properties file; only via the .classpath file!
                Collections.<File>emptyList());
        createAndroidManifest(androidLib, androidLibPkg, 7, -1, "");

        // Main app project, depends on library project
        String appName = "AndroidApp";
        File app = new File(root, appName);
        app.mkdirs();
        String appPkg = "test.pkg";
        createDotProject(app, appName, true);
        File appSrc = new File("src");
        File appGen = new File("gen");
        createSampleJavaSource(app, "src", appPkg, "AppActivity");
        createSampleJavaSource(app, "gen", appPkg, "R");
        createClassPath(app,
                new File("bin", "classes"),
                Arrays.asList(appSrc, appGen),
                Collections.<File>emptyList());
        createProjectProperties(app, "android-17", null, null, null,
                Collections.singletonList(new File(".." + separator + androidLibName)));
        createAndroidManifest(app, appPkg, 8, 16, null);
        createDefaultStrings(app);
        createDefaultIcon(app);

        // Add some files in there that we are ignoring
        new File(app, ".gitignore").createNewFile();

        return Pair.of(root, app);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void testLibraries2() throws Exception {
        Pair<File,File> pair = createLibrary2(new File("Library1"));
        File root = pair.getFirst();
        File app = pair.getSecond();

        // ADT Directory structure created by the above:
        assertEquals(""
                + "AndroidApp\n"
                + "  .classpath\n"
                + "  .gitignore\n"
                + "  .project\n"
                + "  AndroidManifest.xml\n"
                + "  gen\n"
                + "    test\n"
                + "      pkg\n"
                + "        R.java\n"
                + "  project.properties\n"
                + "  res\n"
                + "    drawable\n"
                + "      ic_launcher.xml\n"
                + "    values\n"
                + "      strings.xml\n"
                + "  src\n"
                + "    test\n"
                + "      pkg\n"
                + "        AppActivity.java\n"
                + "AndroidLibrary\n"
                + "  .classpath\n"
                + "  .project\n"
                + "  AndroidManifest.xml\n"
                + "  gen\n"
                + "    test\n"
                + "      android\n"
                + "        lib\n"
                + "          pkg\n"
                + "            R.java\n"
                + "  project.properties\n"
                + "  src\n"
                + "    test\n"
                + "      android\n"
                + "        lib\n"
                + "          pkg\n"
                + "            AndroidLibrary.java\n"
                + "Library1\n"
                + "  .classpath\n"
                + "  .project\n"
                + "  .settings\n"
                + "    org.eclipse.jdt.core.prefs\n"
                + "  src\n"
                + "    test\n"
                + "      lib1\n"
                + "        pkg\n"
                + "          Library1.java\n"
                + "Library2\n"
                + "  .classpath\n"
                + "  .project\n"
                + "  .settings\n"
                + "    org.eclipse.jdt.core.prefs\n"
                + "  src\n"
                + "    test\n"
                + "      lib2\n"
                + "        pkg\n"
                + "          Library2.java\n"
                + "some\n"
                + "  path\n"
                + "    guava-13.0.1.jar\n",
                fileTree(root, true));

        final AtomicReference<GradleImport> importReference = new AtomicReference<GradleImport>();
        File imported = checkProject(app, ""
                + MSG_HEADER
                + MSG_MANIFEST
                + MSG_UNHANDLED
                + "* .gitignore\n"
                + MSG_REPLACED_JARS
                + "guava-13.0.1.jar => com.google.guava:guava:13.0.1\n"
                + MSG_GUESSED_VERSIONS
                + "guava-13.0.1.jar => version 13.0.1 in com.google.guava:guava:13.0.1\n"
                + MSG_FOLDER_STRUCTURE
                + "In Library1:\n"
                + "* src/ => library1/src/main/java/\n"
                + "In Library2:\n"
                + "* src/ => library2/src/main/java/\n"
                + "In AndroidLibrary:\n"
                + "* AndroidManifest.xml => androidLibrary/src/main/AndroidManifest.xml\n"
                + "* src/ => androidLibrary/src/main/java/\n"
                + "In AndroidApp:\n"
                + "* AndroidManifest.xml => androidApp/src/main/AndroidManifest.xml\n"
                + "* res/ => androidApp/src/main/res/\n"
                + "* src/ => androidApp/src/main/java/\n"
                + MSG_FOOTER,
                false /* checkBuild */, new ImportCustomizer() {
            @Override
            public void customize(GradleImport importer) {
                importReference.set(importer);
            }
        });
        assertEquals("{/Library1=" + new File(root, "Library1").getCanonicalPath() +
                ", /Library2=" + new File(root, "Library2").getCanonicalPath() +"}",
                describePathMap(importReference.get()));

        // Imported project
        assertEquals(""
                + "androidApp\n"
                + "  build.gradle\n"
                + "  src\n"
                + "    main\n"
                + "      AndroidManifest.xml\n"
                + "      java\n"
                + "        test\n"
                + "          pkg\n"
                + "            AppActivity.java\n"
                + "      res\n"
                + "        drawable\n"
                + "          ic_launcher.xml\n"
                + "        values\n"
                + "          strings.xml\n"
                + "androidLibrary\n"
                + "  build.gradle\n"
                + "  src\n"
                + "    main\n"
                + "      AndroidManifest.xml\n"
                + "      java\n"
                + "        test\n"
                + "          android\n"
                + "            lib\n"
                + "              pkg\n"
                + "                AndroidLibrary.java\n"
                + "build.gradle\n"
                + "import-summary.txt\n"
                + "library1\n"
                + "  build.gradle\n"
                + "  src\n"
                + "    main\n"
                + "      java\n"
                + "        test\n"
                + "          lib1\n"
                + "            pkg\n"
                + "              Library1.java\n"
                + "library2\n"
                + "  build.gradle\n"
                + "  src\n"
                + "    main\n"
                + "      java\n"
                + "        test\n"
                + "          lib2\n"
                + "            pkg\n"
                + "              Library2.java\n"
                + "local.properties\n"
                + "settings.gradle\n",
                fileTree(imported, true));

        // Let's peek at some of the key files to make sure we codegen'ed the right thing
        //noinspection PointlessBooleanExpression,ConstantConditions
        assertEquals(""
                + (!DECLARE_GLOBAL_REPOSITORIES ?
                    "buildscript {\n"
                    + "    repositories {\n"
                    + "        " + MAVEN_REPOSITORY + "\n"
                    + "    }\n"
                    + "}\n" : "")
                + "apply plugin: 'java'\n"
                + "\n"
                + "dependencies {\n"
                + "    compile 'com.google.guava:guava:13.0.1'\n"
                + "}",
                Files.toString(new File(imported, "library1" + separator + "build.gradle"), UTF_8)
                        .replace(NL, "\n"));
        //noinspection PointlessBooleanExpression,ConstantConditions
        assertEquals(""
                + (!DECLARE_GLOBAL_REPOSITORIES ?
                    "buildscript {\n"
                    + "    repositories {\n"
                    + "        " + MAVEN_REPOSITORY + "\n"
                    + "    }\n"
                    + "    dependencies {\n"
                    + "        classpath '" + ANDROID_GRADLE_PLUGIN + "'\n"
                    + "    }\n"
                    + "}\n" : "")
                + "apply plugin: 'android'\n"
                + (!DECLARE_GLOBAL_REPOSITORIES ?
                    "\n"
                    + "repositories {\n"
                    + "    " + MAVEN_REPOSITORY + "\n"
                    + "}\n" : "")
                + "\n"
                + "android {\n"
                + "    compileSdkVersion 17\n"
                + "    buildToolsVersion \"" + BUILD_TOOLS_VERSION + "\"\n"
                + "\n"
                + "    defaultConfig {\n"
                + "        minSdkVersion 8\n"
                + "        targetSdkVersion 16\n"
                + "    }\n"
                + "\n"
                + "    buildTypes {\n"
                + "        release {\n"
                + "            runProguard false\n"
                + "            proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.txt'\n"
                + "        }\n"
                + "    }\n"
                + "}\n"
                + "\n"
                + "dependencies {\n"
                + "    compile project(':androidLibrary')\n"
                + "}",
                Files.toString(new File(imported, "androidApp" + separator + "build.gradle"), UTF_8)
                        .replace(NL,"\n"));

        //noinspection PointlessBooleanExpression,ConstantConditions
        assertEquals(""
                + (!DECLARE_GLOBAL_REPOSITORIES ?
                    "buildscript {\n"
                    + "    repositories {\n"
                    + "        " + MAVEN_REPOSITORY + "\n"
                    + "    }\n"
                    + "    dependencies {\n"
                    + "        classpath '" + ANDROID_GRADLE_PLUGIN + "'\n"
                    + "    }\n"
                    + "}\n" : "")
                + "apply plugin: 'android-library'\n"
                + (!DECLARE_GLOBAL_REPOSITORIES ?
                    "\n"
                    + "repositories {\n"
                    + "    " + MAVEN_REPOSITORY + "\n"
                    + "}\n" : "")
                + "\n"
                + "android {\n"
                + "    compileSdkVersion 18\n"
                + "    buildToolsVersion \"" + BUILD_TOOLS_VERSION + "\"\n"
                + "\n"
                + "    defaultConfig {\n"
                + "        minSdkVersion 8\n"
                + "        targetSdkVersion 8\n"
                + "    }\n"
                + "\n"
                + "    release {\n"
                + "        runProguard false\n"
                + "        proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.txt'\n"
                + "    }\n"
                + "}\n"
                + "\n"
                + "dependencies {\n"
                + "    compile project(':library1')\n"
                + "    compile project(':library2')\n"
                + "}",
                Files.toString(new File(imported, "androidLibrary" + separator + "build.gradle"), UTF_8)
                        .replace(NL,"\n"));

        //noinspection PointlessBooleanExpression,ConstantConditions
        assertEquals(""
                + (!DECLARE_GLOBAL_REPOSITORIES ?
                    "buildscript {\n"
                    + "    repositories {\n"
                    + "        " + MAVEN_REPOSITORY + "\n"
                    + "    }\n"
                    + "}\n" : "")
                + "apply plugin: 'java'\n"
                + "\n"
                + "dependencies {\n"
                + "    compile 'com.google.guava:guava:13.0.1'\n"
                + "}",
                Files.toString(new File(imported, "library1" + separator + "build.gradle"), UTF_8)
                        .replace(NL, "\n"));
        //noinspection PointlessBooleanExpression,ConstantConditions
        assertEquals(""
                + (!DECLARE_GLOBAL_REPOSITORIES ?
                    "buildscript {\n"
                    + "    repositories {\n"
                    + "        " + MAVEN_REPOSITORY + "\n"
                    + "    }\n"
                    + "}\n" : "")
                + "apply plugin: 'java'\n"
                + "\n"
                + "sourceCompatibility = \"1.7\"\n"
                + "targetCompatibility = \"1.7\"\n"
                + "\n"
                + "dependencies {\n"
                + "    compile project(':library1')\n"
                + "}",
                Files.toString(new File(imported, "library2" + separator + "build.gradle"), UTF_8)
                        .replace(NL, "\n"));

        // TODO: Should this ONLY include the root module?
        assertEquals(""
                + "include ':library1'\n"
                + "include ':library2'\n"
                + "include ':androidLibrary'\n"
                + "include ':androidApp'\n",
                Files.toString(new File(imported, "settings.gradle"), UTF_8)
                        .replace(NL, "\n"));

        //noinspection ConstantConditions
        assertEquals(""
                + "// Top-level build file where you can add configuration options common to all sub-projects/modules.\n"
                + (DECLARE_GLOBAL_REPOSITORIES ?
                    "buildscript {\n"
                    + "    repositories {\n"
                    + "        " + MAVEN_REPOSITORY + "\n"
                    + "    }\n"
                    + "    dependencies {\n"
                    + "        classpath '" + ANDROID_GRADLE_PLUGIN + "'\n"
                    + "    }\n"
                    + "}\n"
                    + "\n"
                    + "allprojects {\n"
                    + "    repositories {\n"
                    + "        " + MAVEN_REPOSITORY + "\n"
                    + "    }\n"
                    + "}\n" : ""),
                Files.toString(new File(imported, "build.gradle"), UTF_8)
                        .replace(NL, "\n"));

        deleteDir(root);
        deleteDir(imported);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void testLibrariesWithWorkspaceMapping1() throws Exception {
        // Provide manually edited workspace mapping /Library1 = actual dir
        final String library1Path = "subdir1" + separator + "subdir2" + separator +
                "UnrelatedName";
        final File library1Dir = new File(library1Path);
        Pair<File,File> pair = createLibrary2(library1Dir);
        final File root = pair.getFirst();
        File app = pair.getSecond();

        final AtomicReference<GradleImport> importReference = new AtomicReference<GradleImport>();
        File imported = checkProject(app, ""
                + MSG_HEADER
                + MSG_MANIFEST
                + MSG_UNHANDLED
                + "* .gitignore\n"
                + MSG_REPLACED_JARS
                + "guava-13.0.1.jar => com.google.guava:guava:13.0.1\n"
                + MSG_GUESSED_VERSIONS
                + "guava-13.0.1.jar => version 13.0.1 in com.google.guava:guava:13.0.1\n"
                + MSG_FOLDER_STRUCTURE
                + "In Library1:\n"
                + "* src/ => library1/src/main/java/\n"
                + "In Library2:\n"
                + "* src/ => library2/src/main/java/\n"
                + "In AndroidLibrary:\n"
                + "* AndroidManifest.xml => androidLibrary/src/main/AndroidManifest.xml\n"
                + "* src/ => androidLibrary/src/main/java/\n"
                + "In AndroidApp:\n"
                + "* AndroidManifest.xml => androidApp/src/main/AndroidManifest.xml\n"
                + "* res/ => androidApp/src/main/res/\n"
                + "* src/ => androidApp/src/main/java/\n"
                + MSG_FOOTER,
                false /* checkBuild */, new ImportCustomizer() {
            @Override
            public void customize(GradleImport importer) {
                importReference.set(importer);
                importer.getPathMap().put("/Library1", new File(root, library1Path));
            }
        });
        assertEquals("{/Library1=" + new File(root, library1Path).getCanonicalPath()
                + ", /Library2=" + new File(root, "Library2").getCanonicalPath() + "}",
                describePathMap(importReference.get()));
        deleteDir(root);
        deleteDir(imported);
    }

    private static String describePathMap(GradleImport importer) throws IOException {
        Map<String, File> map = importer.getPathMap();
        List<String> keys = Lists.newArrayList(map.keySet());
        Collections.sort(keys);
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean first = true;
        for (String key : keys) {
            File file = map.get(key);
            if (file != null) {
                file = file.getCanonicalFile();
            }
            if (first) {
                first = false;
            } else {
                sb.append(", ");
            }
            sb.append(key);
            sb.append("=");
            sb.append(file);
        }
        sb.append("}");
        return sb.toString();
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void testLibrariesWithWorkspaceMapping2() throws Exception {
        // Provide manually edited workspace location; importer reads workspace data
        // to find it
        final String library1Path = "subdir1" + separator + "subdir2" + separator +
                "UnrelatedName";
        final File library1Dir = new File(library1Path);
        Pair<File,File> pair = createLibrary2(library1Dir);
        final File root = pair.getFirst();
        File app = pair.getSecond();
        final File library1AbsDir = new File(root, library1Path);

        final File workspace = new File(root, "workspace");
        workspace.mkdirs();
        File metadata = new File(workspace, ".metadata");
        metadata.mkdirs();
        new File(metadata, "version.ini").createNewFile();
        assertTrue(GradleImport.isEclipseWorkspaceDir(workspace));
        File projects = new File(metadata, ".plugins" + separator + "org.eclipse.core.resources" +
                separator + ".projects");
        projects.mkdirs();
        File library1 = new File(projects, "Library1");
        library1.mkdirs();
        File location = new File(library1, ".location");
        byte[] data = ("blahblahblahURI//" + SdkUtils.fileToUrl(library1AbsDir) +
                "\000blahblahblah").getBytes(Charsets.UTF_8);
        Files.write(data, location);

        final AtomicReference<GradleImport> importReference = new AtomicReference<GradleImport>();
        File imported = checkProject(app, ""
                + MSG_HEADER
                + MSG_MANIFEST
                + MSG_UNHANDLED
                + "* .gitignore\n"
                + MSG_REPLACED_JARS
                + "guava-13.0.1.jar => com.google.guava:guava:13.0.1\n"
                + MSG_GUESSED_VERSIONS
                + "guava-13.0.1.jar => version 13.0.1 in com.google.guava:guava:13.0.1\n"
                + MSG_FOLDER_STRUCTURE
                + "In Library1:\n"
                + "* src/ => library1/src/main/java/\n"
                + "In Library2:\n"
                + "* src/ => library2/src/main/java/\n"
                + "In AndroidLibrary:\n"
                + "* AndroidManifest.xml => androidLibrary/src/main/AndroidManifest.xml\n"
                + "* src/ => androidLibrary/src/main/java/\n"
                + "In AndroidApp:\n"
                + "* AndroidManifest.xml => androidApp/src/main/AndroidManifest.xml\n"
                + "* res/ => androidApp/src/main/res/\n"
                + "* src/ => androidApp/src/main/java/\n"
                + MSG_FOOTER,
                false /* checkBuild */, new ImportCustomizer() {
            @Override
            public void customize(GradleImport importer) {
                importReference.set(importer);
                importer.setEclipseWorkspace(workspace);
            }
        });
        assertEquals("{/Library1=" + new File(root, library1Path).getCanonicalPath()
                + ", /Library2=" + new File(root, "Library2").getCanonicalPath() + "}",
                describePathMap(importReference.get()));
        deleteDir(root);
        deleteDir(imported);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void testLibrariesWithWorkspacePathVars() throws Exception {
        // Provide manually edited workspace location which contains workspace locations
        final String library1Path = "subdir1" + separator + "subdir2" + separator +
                "UnrelatedName";
        final File library1Dir = new File(library1Path);
        Pair<File,File> pair = createLibrary2(library1Dir);
        final File root = pair.getFirst();
        File app = pair.getSecond();
        final File library1AbsDir = new File(root, library1Path);

        final File workspace = new File(root, "workspace");
        workspace.mkdirs();
        File metadata = new File(workspace, ".metadata");
        metadata.mkdirs();
        new File(metadata, "version.ini").createNewFile();
        assertTrue(GradleImport.isEclipseWorkspaceDir(workspace));
        File projects = new File(metadata, ".plugins" + separator + "org.eclipse.core.resources" +
                separator + ".projects");
        projects.mkdirs();
        File library1 = new File(projects, "Library1");
        library1.mkdirs();
        File location = new File(library1, ".location");
        byte[] data = ("blahblahblahURI//" + SdkUtils.fileToUrl(library1AbsDir) +
                "\000blahblahblah").getBytes(Charsets.UTF_8);
        Files.write(data, location);

        final AtomicReference<GradleImport> importReference = new AtomicReference<GradleImport>();
        File imported = checkProject(app, ""
                + MSG_HEADER
                + MSG_MANIFEST
                + MSG_UNHANDLED
                + "* .gitignore\n"
                + MSG_REPLACED_JARS
                + "guava-13.0.1.jar => com.google.guava:guava:13.0.1\n"
                + MSG_GUESSED_VERSIONS
                + "guava-13.0.1.jar => version 13.0.1 in com.google.guava:guava:13.0.1\n"
                + MSG_FOLDER_STRUCTURE
                + "In Library1:\n"
                + "* src/ => library1/src/main/java/\n"
                + "In Library2:\n"
                + "* src/ => library2/src/main/java/\n"
                + "In AndroidLibrary:\n"
                + "* AndroidManifest.xml => androidLibrary/src/main/AndroidManifest.xml\n"
                + "* src/ => androidLibrary/src/main/java/\n"
                + "In AndroidApp:\n"
                + "* AndroidManifest.xml => androidApp/src/main/AndroidManifest.xml\n"
                + "* res/ => androidApp/src/main/res/\n"
                + "* src/ => androidApp/src/main/java/\n"
                + MSG_FOOTER,
                false /* checkBuild */, new ImportCustomizer() {
            @Override
            public void customize(GradleImport importer) {
                importReference.set(importer);
                importer.setEclipseWorkspace(workspace);
            }
        });
        assertEquals("{/Library1=" + new File(root, library1Path).getCanonicalPath()
                + ", /Library2=" + new File(root, "Library2").getCanonicalPath() + "}",
                describePathMap(importReference.get()));
        deleteDir(root);
        deleteDir(imported);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void testErrorHandling() throws Exception {
        File projectDir = createProject("test1", "test.pkg");

        File classPath = new File(projectDir, ".classpath");
        Files.write(""
                + "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<classpath>\n"
                + "        <classpathentry kind=\"src\" path=\"src\"/\n" // <== XML error
                + "        <classpathentry kind=\"src\" path=\"gen\"/>\n"
                + "        <classpathentry kind=\"con\" path=\"com.android.ide.eclipse.adt.ANDROID_FRAMEWORK\"/>\n"
                + "        <classpathentry exported=\"true\" kind=\"con\" path=\"com.android.ide.eclipse.adt.LIBRARIES\"/>\n"
                + "        <classpathentry exported=\"true\" kind=\"con\" path=\"com.android.ide.eclipse.adt.DEPENDENCIES\"/>\n"
                + "        <classpathentry kind=\"output\" path=\"bin/classes\"/>\n"
                + "</classpath>", classPath, UTF_8);

        final AtomicReference<GradleImport> importReference = new AtomicReference<GradleImport>();
        File imported = checkProject(projectDir, ""
                + MSG_HEADER
                + "\n"
                + " * $ROOT/.classpath:\n"
                + "   Invalid XML file:\n"
                + "   $ROOT/.classpath:\n"
                + "   Element type \"classpathentry\" must be followed by either attribute\n"
                + "   specifications, \">\" or \"/>\".\n\n"
                + MSG_FOOTER,
                false /* checkBuild */, new ImportCustomizer() {
            @Override
            public void customize(GradleImport importer) {
                importReference.set(importer);
            }
        });

        assertEquals("[$CLASSPATH_FILE:\n"
                + "Invalid XML file: $CLASSPATH_FILE:\n"
                + "Element type \"classpathentry\" must be followed by either attribute "
                + "specifications, \">\" or \"/>\".]",
                importReference.get().getErrors().toString().replace(
                        classPath.getPath(), "$CLASSPATH_FILE").
                        replace(classPath.getCanonicalPath(), "$CLASSPATH_FILE"));

        deleteDir(projectDir);
        deleteDir(imported);
    }

    public void testSdkNdkSetters() {
        GradleImport importer = new GradleImport();
        File ndkLocation = new File("ndk");
        File sdkLocation = new File("sdk");
        importer.setNdkLocation(ndkLocation);
        importer.setSdkLocation(sdkLocation);
        assertSame(sdkLocation, importer.getSdkLocation());
        assertSame(ndkLocation, importer.getNdkLocation());
    }

    // --- Unit test infrastructure from this point on ----

    @SuppressWarnings({"ResultOfMethodCallIgnored", "SpellCheckingInspection"})
    private static void createEclipseSettingsFile(File prj, String languageLevel)
            throws IOException {
        File file = new File(prj, ".settings" + separator + "org.eclipse.jdt.core.prefs");
        file.getParentFile().mkdirs();
        Files.write("" +
                "eclipse.preferences.version=1\n" +
                "org.eclipse.jdt.core.compiler.codegen.inlineJsrBytecode=enabled\n" +
                "org.eclipse.jdt.core.compiler.codegen.targetPlatform=" +
                languageLevel +
                "\n" +
                "org.eclipse.jdt.core.compiler.codegen.unusedLocal=preserve\n" +
                "org.eclipse.jdt.core.compiler.compliance=" +
                languageLevel +
                "\n" +
                "org.eclipse.jdt.core.compiler.debug.lineNumber=generate\n" +
                "org.eclipse.jdt.core.compiler.debug.localVariable=generate\n" +
                "org.eclipse.jdt.core.compiler.debug.sourceFile=generate\n" +
                "org.eclipse.jdt.core.compiler.problem.assertIdentifier=error\n" +
                "org.eclipse.jdt.core.compiler.problem.enumIdentifier=error\n" +
                "org.eclipse.jdt.core.compiler.source=" +
                languageLevel, file, Charsets.UTF_8);
    }

    interface ImportCustomizer {
        void customize(GradleImport importer);
    }

    private static File checkProject(File adtProjectDir,
            String expectedSummary, boolean checkBuild) throws Exception {
        return checkProject(adtProjectDir, expectedSummary, checkBuild, null);
    }

    private static File checkProject(File adtProjectDir,
            String expectedSummary, boolean checkBuild,
            ImportCustomizer customizer) throws Exception {
        File destDir = Files.createTempDir();
        assertTrue(GradleImport.isAdtProjectDir(adtProjectDir));
        List<File> projects = Collections.singletonList(adtProjectDir);
        GradleImport importer = new GradleImport();

        String sdkPath = getTestSdkPath();
        if (sdkPath != null) {
            importer.setSdkLocation(new File(sdkPath));
        }

        File wrapper = findGradleWrapper();
        if (wrapper != null) {
            importer.setGradleWrapperLocation(wrapper);
        }

        if (customizer != null) {
            customizer.customize(importer);
        }
        importer.importProjects(projects);

        importer.exportProject(destDir, false);
        String summary = Files.toString(new File(destDir, IMPORT_SUMMARY_TXT), UTF_8);
        summary = summary.replace("\r", "");
        String testSdkPath = getTestSdkPath();
        if (testSdkPath != null) {
            summary = summary.replace(testSdkPath, "$ADT_TEST_SDK_PATH");
        }
        summary = summary.replace(separatorChar, '/');
        summary = summary.replace(adtProjectDir.getPath().replace(separatorChar,'/'), "$ROOT");
        File parentFile = adtProjectDir.getParentFile();
        if (parentFile != null) {
            summary = summary.replace(parentFile.getPath().replace(separatorChar,'/'),
                    "$ROOT_PARENT");
        }
        assertEquals(expectedSummary, summary);

        if (checkBuild) {
            assertBuildsCleanly(destDir, true);
        }

        return destDir;
    }

    @Nullable
    private static File findGradleWrapper() throws IOException {
        File root = TestUtils.getCanonicalRoot("resources", "baseMerge");
        // The TestUtils call returns results within sdk-common when run from within the IDE
        // so look relative to it to find the templates folder
        if (root != null) {
            File top = root
                    .getParentFile()
                    .getParentFile()
                    .getParentFile()
                    .getParentFile()
                    .getParentFile()
                    .getParentFile()
                    .getParentFile();
            File wrapper = new File(top, "templates" + separator + "gradle" + separator +
                    "wrapper");
            if (wrapper.exists()) {
                return wrapper;
            }
        }

        return null;
    }

    private static boolean isWindows() {
        return SdkUtils.startsWithIgnoreCase(System.getProperty("os.name"), "windows");
    }

    public static void assertBuildsCleanly(File base, boolean allowWarnings) throws Exception {
        File gradlew = new File(base, "gradlew" + (isWindows() ? ".bat" : ""));
        if (!gradlew.exists()) {
            // Not using a wrapper; can't easily test building (we don't have a gradle prebuilt)
            return;
        }
        File pwd = base.getAbsoluteFile();
        Process process = Runtime.getRuntime().exec(new String[]{gradlew.getPath(),
                "assembleDebug"}, null, pwd);
        int exitCode = process.waitFor();
        byte[] stdout = ByteStreams.toByteArray(process.getInputStream());
        byte[] stderr = ByteStreams.toByteArray(process.getErrorStream());
        String errors = new String(stderr, UTF_8);
        String output = new String(stdout, UTF_8);
        int expectedExitCode = 0;
        if (output.contains("BUILD FAILED") && errors.contains(
                "Could not find any version that matches com.android.tools.build:gradle:")) {
            // We ignore this assertion. We got here because we are using a version of the
            // Android Gradle plug-in that is not available in Maven Central yet.
            expectedExitCode = 1;
        } else {
            assertTrue(output + "\n" + errors, output.contains("BUILD SUCCESSFUL"));
            if (!allowWarnings) {
                assertEquals(output + "\n" + errors, "", errors);
            }
        }
        assertEquals(expectedExitCode, exitCode);
        System.out.println("Built project successfully; output was:\n" + output);
    }

    private static String fileTree(File file, boolean includeDirs) {
        StringBuilder sb = new StringBuilder(1000);
        appendFiles(sb, includeDirs, file, 0);
        return sb.toString();
    }

    private static void appendFiles(StringBuilder sb, boolean includeDirs, File file, int depth) {
        // Skip wrapper, since it may or may not be present for unit tests
        if (depth == 1) {
            String name = file.getName();
            if (name.equals(".gradle")
                    || name.equals("gradle")
                    || name.equals("gradlew")
                    || name.equals("gradlew.bat")) {
                return;
            }
        } else if (depth == 2 && file.getName().equals("build")) { // Skip output
            return;
        }

        boolean isDirectory = file.isDirectory();
        if (depth > 0 && (!isDirectory || includeDirs)) {
            for (int i = 0; i < depth - 1; i++) {
                sb.append("  ");
            }
            sb.append(file.getName());
            sb.append("\n");
        }

        if (isDirectory) {
            File[] children = file.listFiles();
            if (children != null) {
                Arrays.sort(children, new Comparator<File>() {
                    @Override
                    public int compare(File file, File file2) {
                        return file.getName().compareTo(file2.getName());
                    }
                });
                for (File child : children) {
                    appendFiles(sb, includeDirs, child, depth + 1);
                }
            }
        }
    }

    private static void createDotProject(
            @NonNull File projectDir,
            String name,
            boolean addAndroidNature) throws IOException {
        createDotProject(projectDir, name, addAndroidNature, addAndroidNature);
    }

    private static void createDotProject(
            @NonNull File projectDir,
            String name,
            boolean addAndroidNature, boolean addNdkNature) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<projectDescription>\n"
                + "\t<name>").append(name).append("</name>\n"
                + "\t<comment></comment>\n"
                + "\t<projects>\n"
                + "\t</projects>\n"
                + "\t<buildSpec>\n"
                + "\t\t<buildCommand>\n"
                + "\t\t\t<name>com.android.ide.eclipse.adt.ResourceManagerBuilder</name>\n"
                + "\t\t\t<arguments>\n"
                + "\t\t\t</arguments>\n"
                + "\t\t</buildCommand>\n"
                + "\t\t<buildCommand>\n"
                + "\t\t\t<name>com.android.ide.eclipse.adt.PreCompilerBuilder</name>\n"
                + "\t\t\t<arguments>\n"
                + "\t\t\t</arguments>\n"
                + "\t\t</buildCommand>\n"
                + "\t\t<buildCommand>\n"
                + "\t\t\t<name>org.eclipse.jdt.core.javabuilder</name>\n"
                + "\t\t\t<arguments>\n"
                + "\t\t\t</arguments>\n"
                + "\t\t</buildCommand>\n"
                + "\t\t<buildCommand>\n"
                + "\t\t\t<name>com.android.ide.eclipse.adt.ApkBuilder</name>\n"
                + "\t\t\t<arguments>\n"
                + "\t\t\t</arguments>\n"
                + "\t\t</buildCommand>\n"
                + "\t</buildSpec>\n"
                + "\t<natures>\n");
        if (addAndroidNature) {
            sb.append("\t\t<nature>com.android.ide.eclipse.adt.AndroidNature</nature>\n");
        }
        if (addNdkNature) {
            sb.append("\t\t<nature>org.eclipse.cdt.core.cnature</nature>\n");
            sb.append("\t\t<nature>org.eclipse.cdt.core.ccnature</nature>\n");
        }
        sb.append("\t\t<nature>org.eclipse.jdt.core.javanature</nature>\n"
                + "\t</natures>\n"
                + "</projectDescription>\n");
        Files.write(sb.toString(), new File(projectDir, ".project"), UTF_8);
    }

    private static void createClassPath(
            @NonNull File projectDir,
            @Nullable File output,
            @NonNull List<File> sources,
            @NonNull List<File> jars) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<classpath>\n");
        for (File source : sources) {
            sb.append("\t<classpathentry kind=\"src\" path=\"").append(source.getPath()).
                    append("\"/>\n");
        }
        sb.append("\t<classpathentry kind=\"con\" path=\"com.android.ide.eclipse.adt.ANDROID_FRAMEWORK\"/>\n"
                + "\t<classpathentry exported=\"true\" kind=\"con\" path=\"com.android.ide.eclipse.adt.LIBRARIES\"/>\n"
                + "\t<classpathentry exported=\"true\" kind=\"con\" path=\"com.android.ide.eclipse.adt.DEPENDENCIES\"/>\n");
        for (File jar : jars) {
            sb.append("<classpathentry exported=\"true\" kind=\"lib\" path=\"").append(jar.getPath()).append("\"/>\n");
        }
        if (output != null) {
            sb.append("\t<classpathentry kind=\"output\" path=\"").append(output.getPath()).append("\"/>\n");
        }
        sb.append("</classpath>");
        Files.write(sb.toString(), new File(projectDir, ".classpath"), UTF_8);
    }

    private static void createProjectProperties(
            @NonNull File projectDir,
            @Nullable String target,
            Boolean mergeManifest,
            Boolean isLibrary,
            @Nullable String proguardConfig,
            @NonNull List<File> libraries) throws IOException {
        StringBuilder sb = new StringBuilder();

        sb.append("# This file is automatically generated by Android Tools.\n"
                + "# Do not modify this file -- YOUR CHANGES WILL BE ERASED!\n"
                + "#\n"
                + "# This file must be checked in Version Control Systems.\n"
                + "#\n"
                + "# To customize properties used by the Ant build system edit\n"
                + "# \"ant.properties\", and override values to adapt the script to your\n"
                + "# project structure.\n"
                + "#\n");
        if (proguardConfig != null) {
            sb.append("# To enable ProGuard to shrink and obfuscate your code, uncomment this "
                    + "(available properties: sdk.dir, user.home):\n");
            // TODO: When using this, escape proguard properly
            sb.append(proguardConfig);
            sb.append("\n");
        }

        if (target != null) {
            String escaped = escapeProperty("target", target);
            sb.append("# Project target.\n").append(escaped).append("\n");
        }

        if (mergeManifest != null) {
            String escaped = escapeProperty("manifestmerger.enabled",
                    Boolean.toString(mergeManifest));
            sb.append(escaped).append("\n");
        }

        if (isLibrary != null) {
            String escaped = escapeProperty("android.library", Boolean.toString(isLibrary));
            sb.append(escaped).append("\n");
        }

        for (int i = 0, n = libraries.size(); i < n; i++) {
            String path = libraries.get(i).getPath();
            String escaped = escapeProperty("android.library.reference." + Integer.toString(i + 1),
                    path);
            sb.append(escaped).append("\n");
        }

        Files.write(sb.toString(), new File(projectDir, "project.properties"), UTF_8);
    }

    private static  String escapeProperty(@NonNull String key, @NonNull String value)
            throws IOException {
        Properties properties = new Properties();
        properties.setProperty(key, value);
        StringWriter writer = new StringWriter();
        properties.store(writer, null);
        return writer.toString();
    }

    private static void createAndroidManifest(
            @NonNull File projectDir,
            @NonNull String packageName,
            int minSdkVersion,
            int targetSdkVersion,
            @Nullable String customApplicationBlock) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    package=\"").append(packageName).append("\"\n"
                + "    android:versionCode=\"1\"\n"
                + "    android:versionName=\"1.0\" >\n"
                + "\n");
        if (minSdkVersion != -1 || targetSdkVersion != -1) {
            sb.append("    <uses-sdk\n");
            if (minSdkVersion >= 1) {
                sb.append("        android:minSdkVersion=\"8\"\n");
            }
            if (targetSdkVersion >= 1) {
                sb.append("        android:targetSdkVersion=\"16\"\n");
            }
            sb.append("     />\n");
            sb.append("\n");
        }
        if (customApplicationBlock != null) {
            sb.append(customApplicationBlock);
        } else {
            sb.append(""
                    + "    <application\n"
                    + "        android:allowBackup=\"true\"\n"
                    + "        android:icon=\"@drawable/ic_launcher\"\n"
                    + "        android:label=\"@string/app_name\"\n"
                    + "    >\n"
                    + "    </application>\n");
        }

        sb.append("\n"
                + "</manifest>\n");
        Files.write(sb.toString(), new File(projectDir, ANDROID_MANIFEST_XML), UTF_8);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static File createSourceFile(@NonNull File projectDir, String relative,
            String contents) throws IOException {
        File file = new File(projectDir, relative.replace('/', separatorChar));
        file.getParentFile().mkdirs();
        Files.write(contents, file, UTF_8);
        return file;
    }

    private static File createSampleJavaSource(@NonNull File projectDir, String src, String pkg,
            String name) throws IOException {
        return createSourceFile(projectDir, src + '/' + pkg.replace('.','/') + '/' + name +
                DOT_JAVA, ""
                + "package " + pkg + ";\n"
                + "public class " + name + " {\n"
                + "}\n");
    }

    private static File createSampleAidlFile(@NonNull File projectDir, String src, String pkg)
            throws IOException {
        return createSourceFile(projectDir, src + '/' + pkg.replace('.','/') +
                "/IHardwareService.aidl", ""
                + "package " + pkg + ";\n"
                + "\n"
                + "/** {@hide} */\n"
                + "interface IHardwareService\n"
                + "{\n"
                + "    // Vibrator support\n"
                + "    void vibrate(long milliseconds);\n"
                + "    void vibratePattern(in long[] pattern, int repeat, IBinder token);\n"
                + "    void cancelVibrate();\n"
                + "\n"
                + "    // flashlight support\n"
                + "    boolean getFlashlightEnabled();\n"
                + "    void setFlashlightEnabled(boolean on);\n"
                + "    void enableCameraFlash(int milliseconds);\n"
                + "\n"
                + "    // sets the brightness of the backlights (screen, keyboard, button) 0-255\n"
                + "    void setBacklights(int brightness);\n"
                + "\n"
                + "    // for the phone\n"
                + "    void setAttentionLight(boolean on);\n"
                + "}");
    }

    private static File createSampleRsFile(@NonNull File projectDir, String src, String pkg)
            throws IOException {
        return createSourceFile(projectDir, src + '/' + pkg.replace('.', '/') + '/' + "latency.rs",
                ""
                        + "#pragma version(1)\n"
                        + "#pragma rs java_package_name(com.android.rs.cpptests)\n"
                        + "#pragma rs_fp_relaxed\n"
                        + "\n"
                        + "void root(const uint32_t *v_in, uint32_t *v_out) {\n"
                        + "\n"
                        + "}");
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static void createDefaultStrings(File dir) throws IOException {
        File strings = new File(dir, "res" + separator + "values" + separator + "strings.xml");
        strings.getParentFile().mkdirs();
        Files.write(""
                + "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<resources>\n"
                + "\n"
                + "    <string name=\"app_name\">Unit Test</string>\n"
                + "\n"
                + "</resources>", strings, UTF_8);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static void createDefaultIcon(File dir) throws IOException {
        File strings = new File(dir, "res" + separator + "drawable" + separator +
                "ic_launcher.xml");
        strings.getParentFile().mkdirs();
        Files.write(""
                + "<shape xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                + "    <solid android:color=\"#00000000\"/>\n"
                + "    <stroke android:width=\"1dp\" color=\"#ff000000\"/>\n"
                + "    <padding android:left=\"1dp\" android:top=\"1dp\"\n"
                + "        android:right=\"1dp\" android:bottom=\"1dp\" />\n"
                + "</shape>", strings, UTF_8);
    }

    private static void deleteDir(File root) {
        if (root.exists()) {
            File[] files = root.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDir(file);
                    } else {
                        boolean deleted = file.delete();
                        assert deleted : file;
                    }
                }
            }
            boolean deleted = root.delete();
            assert deleted : root;
        }
    }

    /** Environment variable or system property containing the full path to an SDK install */
    public static final String SDK_PATH_PROPERTY = "ADT_TEST_SDK_PATH";

    @Nullable
    protected static String getTestSdkPath() {
        String override = System.getProperty(SDK_PATH_PROPERTY);
        if (override != null) {
            assertTrue(override, new File(override).exists());
            return override;
        }
        override = System.getenv(SDK_PATH_PROPERTY);
        if (override != null) {
            return override;
        }

        return null;
    }

    private static final String BUILD_TOOLS_VERSION;
    static {
        String candidate = "19.0.1";
        String sdkLocation = getTestSdkPath();
        if (sdkLocation != null) {
            ILogger logger = new StdLogger(StdLogger.Level.INFO);
            SdkManager sdkManager = SdkManager.createManager(sdkLocation, logger);
            if (sdkManager != null) {
                final BuildToolInfo buildTool = sdkManager.getLatestBuildTool();
                if (buildTool != null) {
                    candidate = buildTool.getRevision().toString();
                }
            }
        }

        BUILD_TOOLS_VERSION = candidate;
    }

    private static final String DEFAULT_MOVED = ""
            + "* AndroidManifest.xml => app/src/main/AndroidManifest.xml\n"
            + "* res/ => app/src/main/res/\n"
            + "* src/ => app/src/main/java/\n";
}
