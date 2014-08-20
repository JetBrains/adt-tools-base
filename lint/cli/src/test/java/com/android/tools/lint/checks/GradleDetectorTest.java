/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.tools.lint.checks;

import static com.android.tools.lint.checks.GradleDetector.ACCIDENTAL_OCTAL;
import static com.android.tools.lint.checks.GradleDetector.COMPATIBILITY;
import static com.android.tools.lint.checks.GradleDetector.DEPENDENCY;
import static com.android.tools.lint.checks.GradleDetector.DEPRECATED;
import static com.android.tools.lint.checks.GradleDetector.GRADLE_GETTER;
import static com.android.tools.lint.checks.GradleDetector.GRADLE_PLUGIN_COMPATIBILITY;
import static com.android.tools.lint.checks.GradleDetector.IMPROPER_PROJECT_LEVEL_STATEMENT;
import static com.android.tools.lint.checks.GradleDetector.MISPLACED_STATEMENT;
import static com.android.tools.lint.checks.GradleDetector.PATH;
import static com.android.tools.lint.checks.GradleDetector.PLUS;
import static com.android.tools.lint.checks.GradleDetector.REMOTE_VERSION;
import static com.android.tools.lint.checks.GradleDetector.STRING_INTEGER;
import static com.android.tools.lint.checks.GradleDetector.getNewValue;
import static com.android.tools.lint.checks.GradleDetector.getOldValue;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.DefaultPosition;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.utils.Pair;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.CodeVisitorSupport;
import org.codehaus.groovy.ast.GroovyCodeVisitor;
import org.codehaus.groovy.ast.builder.AstBuilder;
import org.codehaus.groovy.ast.expr.ArgumentListExpression;
import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.MapEntryExpression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.expr.NamedArgumentListExpression;
import org.codehaus.groovy.ast.expr.TupleExpression;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.ReturnStatement;
import org.codehaus.groovy.ast.stmt.Statement;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <b>NOTE</b>: Most GradleDetector unit tests are in the Studio plugin, as tests
 * for IntellijGradleDetector
 */
public class GradleDetectorTest extends AbstractCheckTest {

    public void testGetOldValue() {
        assertEquals("11.0.2", getOldValue(DEPENDENCY,
                "A newer version of com.google.guava:guava than 11.0.2 is available: 17.0.0"));
        assertNull(getOldValue(DEPENDENCY, "Bogus"));
        assertNull(getOldValue(DEPENDENCY, "bogus"));
        // targetSdkVersion 20, compileSdkVersion 19: Should replace targetVersion 20 with 19
        assertEquals("20", getOldValue(DEPENDENCY,
                "The targetSdkVersion (20) should not be higher than the compileSdkVersion (19)"));
        assertEquals("'19'", getOldValue(STRING_INTEGER,
                "Use an integer rather than a string here (replace '19' with just 19)"));
        assertEquals("android", getOldValue(DEPRECATED,
                "'android' is deprecated; use 'com.android.application' instead"));
        assertEquals("android-library", getOldValue(DEPRECATED,
                "'android-library' is deprecated; use 'com.android.library' instead"));
        assertEquals("packageName", getOldValue(DEPRECATED,
                "Deprecated: Replace 'packageName' with 'applicationId'"));
        assertEquals("packageNameSuffix", getOldValue(DEPRECATED,
                "Deprecated: Replace 'packageNameSuffix' with 'applicationIdSuffix'"));
        assertEquals("18.0.0", getOldValue(DEPENDENCY,
                "Old buildToolsVersion 18.0.0; recommended version is 19.1 or later"));
    }

    public void testGetNewValue() {
        assertEquals("17.0.0", getNewValue(DEPENDENCY,
                "A newer version of com.google.guava:guava than 11.0.2 is available: 17.0.0"));
        assertNull(getNewValue(DEPENDENCY,
                "A newer version of com.google.guava:guava than 11.0.2 is available"));
        assertNull(getNewValue(DEPENDENCY, "bogus"));
        // targetSdkVersion 20, compileSdkVersion 19: Should replace targetVersion 20 with 19
        assertEquals("19", getNewValue(DEPENDENCY,
                "The targetSdkVersion (20) should not be higher than the compileSdkVersion (19)"));
        assertEquals("19", getNewValue(STRING_INTEGER,
                "Use an integer rather than a string here (replace '19' with just 19)"));
        assertEquals("com.android.application", getNewValue(DEPRECATED,
                "'android' is deprecated; use 'com.android.application' instead"));
        assertEquals("com.android.library", getNewValue(DEPRECATED,
                "'android-library' is deprecated; use 'com.android.library' instead"));
        assertEquals("applicationId", getNewValue(DEPRECATED,
                "Deprecated: Replace 'packageName' with 'applicationId'"));
        assertEquals("applicationIdSuffix", getNewValue(DEPRECATED,
                "Deprecated: Replace 'packageNameSuffix' with 'applicationIdSuffix'"));
        assertEquals("19.1", getNewValue(DEPENDENCY,
                "Old buildToolsVersion 18.0.0; recommended version is 19.1 or later"));
    }

    public void test() throws Exception {
        mEnabled = null;
        assertEquals(""
            + "build.gradle:25: Error: This support library should not use a lower version (13) than the targetSdkVersion (17) [GradleCompatible]\n"
            + "    compile 'com.android.support:appcompat-v7:13.0.0'\n"
            + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
            + "build.gradle:1: Warning: 'android' is deprecated; use 'com.android.application' instead [GradleDeprecated]\n"
            + "apply plugin: 'android'\n"
            + "~~~~~~~~~~~~~~~~~~~~~~~\n"
            + "build.gradle:5: Warning: Old buildToolsVersion 19.0.0; recommended version is 19.1.0 or later [GradleDependency]\n"
            + "    buildToolsVersion \"19.0.0\"\n"
            + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
            + "build.gradle:25: Warning: A newer version of com.android.support:appcompat-v7 than 13.0.0 is available: 20.0.0 [GradleDependency]\n"
            + "    compile 'com.android.support:appcompat-v7:13.0.0'\n"
            + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
            + "build.gradle:23: Warning: Avoid using + in version numbers; can lead to unpredictable and unrepeatable builds (com.android.support:appcompat-v7:+) [GradleDynamicVersion]\n"
            + "    compile 'com.android.support:appcompat-v7:+'\n"
            + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
            + "build.gradle:24: Warning: A newer version of com.google.guava:guava than 11.0.2 is available: 17.0.0 [NewerVersionAvailable]\n"
            + "    freeCompile 'com.google.guava:guava:11.0.2'\n"
            + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
            + "1 errors, 5 warnings\n",

            lintProject("gradle/Dependencies.gradle=>build.gradle"));
    }

    public void testCompatibility() throws Exception {
        mEnabled = Collections.singleton(COMPATIBILITY);
        assertEquals(""
                + "build.gradle:16: Error: This support library should not use a lower version (18) than the targetSdkVersion (19) [GradleCompatible]\n"
                + "    compile 'com.android.support:support-v4:18.0.0'\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "1 errors, 0 warnings\n",

                lintProject("gradle/Compatibility.gradle=>build.gradle"));
    }

    public void testIncompatiblePlugin() throws Exception {
        mEnabled = Collections.singleton(GRADLE_PLUGIN_COMPATIBILITY);
        assertEquals(""
                + "build.gradle:6: Error: You must use a newer version of the Android Gradle plugin. The minimum supported version is 0.12.0 and the recommended version is 0.12.1 [AndroidGradlePluginVersion]\n"
                + "    classpath 'com.android.tools.build:gradle:0.1.0'\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "1 errors, 0 warnings\n",

                lintProject("gradle/IncompatiblePlugin.gradle=>build.gradle"));
    }

    public void testSetter() throws Exception {
        mEnabled = Collections.singleton(GRADLE_GETTER);
        assertEquals(""
                        + "build.gradle:18: Error: Bad method name: pick a unique method name which does not conflict with the implicit getters for the defaultConfig properties. For example, try using the prefix compute- instead of get-. [GradleGetter]\n"
                        + "        versionCode getVersionCode\n"
                        + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "build.gradle:19: Error: Bad method name: pick a unique method name which does not conflict with the implicit getters for the defaultConfig properties. For example, try using the prefix compute- instead of get-. [GradleGetter]\n"
                        + "        versionName getVersionName\n"
                        + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "2 errors, 0 warnings\n",

                lintProject("gradle/Setter.gradle=>build.gradle"));
    }

    public void testDependencies() throws Exception {
        mEnabled = Collections.singleton(DEPENDENCY);
        assertEquals(""
                + "build.gradle:5: Warning: Old buildToolsVersion 19.0.0; recommended version is 19.1.0 or later [GradleDependency]\n"
                + "    buildToolsVersion \"19.0.0\"\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "build.gradle:24: Warning: A newer version of com.google.guava:guava than 11.0.2 is available: 17.0.0 [GradleDependency]\n"
                + "    freeCompile 'com.google.guava:guava:11.0.2'\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "build.gradle:25: Warning: A newer version of com.android.support:appcompat-v7 than 13.0.0 is available: 20.0.0 [GradleDependency]\n"
                + "    compile 'com.android.support:appcompat-v7:13.0.0'\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "0 errors, 3 warnings\n",

                lintProject("gradle/Dependencies.gradle=>build.gradle"));
    }


    public void testDependenciesMinSdkVersion() throws Exception {
        mEnabled = Collections.singleton(DEPENDENCY);
        assertEquals(""
                + "build.gradle:13: Warning: Using the appcompat library when minSdkVersion >= 14 is not necessary [GradleDependency]\n"
                + "    compile 'com.android.support:appcompat-v7:+'\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "0 errors, 1 warnings\n",

                lintProject("gradle/Dependencies14.gradle=>build.gradle"));
    }

    public void testPaths() throws Exception {
        mEnabled = Collections.singleton(PATH);
        assertEquals(""
                        + "build.gradle:4: Warning: Do not use Windows file separators in .gradle files; use / instead [GradlePath]\n"
                        + "    compile files('my\\\\libs\\\\http.jar')\n"
                        + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "build.gradle:5: Warning: Avoid using absolute paths in .gradle files [GradlePath]\n"
                        + "    compile files('/libs/android-support-v4.jar')\n"
                        + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "0 errors, 2 warnings\n",

                lintProject("gradle/Paths.gradle=>build.gradle"));
    }

    public void testIdSuffix() throws Exception {
        mEnabled = Collections.singleton(PATH);
        assertEquals(""
                        + "build.gradle:6: Warning: Package suffix should probably start with a \".\" [GradlePath]\n"
                        + "            applicationIdSuffix \"debug\"\n"
                        + "            ~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "0 errors, 1 warnings\n",

                lintProject("gradle/IdSuffix.gradle=>build.gradle"));
    }

    public void testPackage() throws Exception {
        mEnabled = Collections.singleton(DEPRECATED);
        assertEquals(""
                + "build.gradle:5: Warning: Deprecated: Replace 'packageName' with 'applicationId' [GradleDeprecated]\n"
                + "        packageName 'my.pkg'\n"
                + "        ~~~~~~~~~~~~~~~~~~~~\n"
                + "build.gradle:9: Warning: Deprecated: Replace 'packageNameSuffix' with 'applicationIdSuffix' [GradleDeprecated]\n"
                + "            packageNameSuffix \".debug\"\n"
                + "            ~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "0 errors, 2 warnings\n",

                lintProject("gradle/Package.gradle=>build.gradle"));
    }

    public void testPlus() throws Exception {
        mEnabled = Collections.singleton(PLUS);
        assertEquals(""
                + "build.gradle:9: Warning: Avoid using + in version numbers; can lead to unpredictable and unrepeatable builds (com.android.support:appcompat-v7:+) [GradleDynamicVersion]\n"
                + "    compile 'com.android.support:appcompat-v7:+'\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "0 errors, 1 warnings\n",

                lintProject("gradle/Plus.gradle=>build.gradle"));
    }

    public void testStringInt() throws Exception {
        mEnabled = Collections.singleton(STRING_INTEGER);
        assertEquals(""
                        + "build.gradle:4: Error: Use an integer rather than a string here (replace '19' with just 19) [StringShouldBeInt]\n"
                        + "    compileSdkVersion '19'\n"
                        + "    ~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "build.gradle:7: Error: Use an integer rather than a string here (replace '8' with just 8) [StringShouldBeInt]\n"
                        + "        minSdkVersion '8'\n"
                        + "        ~~~~~~~~~~~~~~~~~\n"
                        + "build.gradle:8: Error: Use an integer rather than a string here (replace '16' with just 16) [StringShouldBeInt]\n"
                        + "        targetSdkVersion '16'\n"
                        + "        ~~~~~~~~~~~~~~~~~~~~~\n"
                        + "3 errors, 0 warnings\n",

                lintProject("gradle/StringInt.gradle=>build.gradle"));
    }

    public void testSuppressLine2() throws Exception {
        mEnabled = null;
        assertEquals("No warnings.",

                lintProject("gradle/SuppressLine2.gradle=>build.gradle"));
    }

    public void testDeprecatedPluginId() throws Exception {
        mEnabled = Sets.newHashSet(DEPRECATED);
        assertEquals(""
                        + "build.gradle:4: Warning: 'android' is deprecated; use 'com.android.application' instead [GradleDeprecated]\n"
                        + "apply plugin: 'android'\n"
                        + "~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "build.gradle:5: Warning: 'android-library' is deprecated; use 'com.android.library' instead [GradleDeprecated]\n"
                        + "apply plugin: 'android-library'\n"
                        + "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                        + "0 errors, 2 warnings\n",

                lintProject("gradle/DeprecatedPluginId.gradle=>build.gradle"));
    }

    public void testIgnoresGStringsInDependencies() throws Exception {
        mEnabled = null;
        assertEquals("No warnings.",

                lintProject("gradle/IgnoresGStringsInDependencies.gradle=>build.gradle"));
    }

    public void testAccidentalOctal() throws Exception {
        mEnabled = Collections.singleton(ACCIDENTAL_OCTAL);
        assertEquals(""
                + "build.gradle:13: Error: The leading 0 turns this number into octal which is probably not what was intended (interpreted as 8) [AccidentalOctal]\n"
                + "        versionCode 010\n"
                + "        ~~~~~~~~~~~~~~~\n"
                + "build.gradle:16: Error: The leading 0 turns this number into octal which is probably not what was intended (and it is not a valid octal number) [AccidentalOctal]\n"
                + "        versionCode 01 // line suffix comments are not handled correctly\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "2 errors, 0 warnings\n",

                lintProject("gradle/AccidentalOctal.gradle=>build.gradle"));
    }

    public void testRemoteVersions() throws Exception {
        mEnabled = Collections.singleton(REMOTE_VERSION);
        try {
            HashMap<String, String> data = Maps.newHashMap();
            GradleDetector.ourMockData = data;
            data.put("http://search.maven.org/solrsearch/select?q=g:%22joda-time%22+AND+a:%22joda-time%22&core=gav&rows=1&wt=json",
                    "{\"responseHeader\":{\"status\":0,\"QTime\":1,\"params\":{\"fl\":\"id,g,a,v,p,ec,timestamp,tags\",\"sort\":\"score desc,timestamp desc,g asc,a asc,v desc\",\"indent\":\"off\",\"q\":\"g:\\\"joda-time\\\" AND a:\\\"joda-time\\\"\",\"core\":\"gav\",\"wt\":\"json\",\"rows\":\"1\",\"version\":\"2.2\"}},\"response\":{\"numFound\":17,\"start\":0,\"docs\":[{\"id\":\"joda-time:joda-time:2.3\",\"g\":\"joda-time\",\"a\":\"joda-time\",\"v\":\"2.3\",\"p\":\"jar\",\"timestamp\":1376674285000,\"tags\":[\"replace\",\"time\",\"library\",\"date\",\"handling\"],\"ec\":[\"-javadoc.jar\",\"-sources.jar\",\".jar\",\".pom\"]}]}}");
            data.put("http://search.maven.org/solrsearch/select?q=g:%22com.squareup.dagger%22+AND+a:%22dagger%22&core=gav&rows=1&wt=json",
                    "{\"responseHeader\":{\"status\":0,\"QTime\":1,\"params\":{\"fl\":\"id,g,a,v,p,ec,timestamp,tags\",\"sort\":\"score desc,timestamp desc,g asc,a asc,v desc\",\"indent\":\"off\",\"q\":\"g:\\\"com.squareup.dagger\\\" AND a:\\\"dagger\\\"\",\"core\":\"gav\",\"wt\":\"json\",\"rows\":\"1\",\"version\":\"2.2\"}},\"response\":{\"numFound\":5,\"start\":0,\"docs\":[{\"id\":\"com.squareup.dagger:dagger:1.2.1\",\"g\":\"com.squareup.dagger\",\"a\":\"dagger\",\"v\":\"1.2.1\",\"p\":\"jar\",\"timestamp\":1392614597000,\"tags\":[\"dependency\",\"android\",\"injector\",\"java\",\"fast\"],\"ec\":[\"-javadoc.jar\",\"-sources.jar\",\"-tests.jar\",\".jar\",\".pom\"]}]}}");

            assertEquals(""
                    + "build.gradle:9: Warning: A newer version of joda-time:joda-time than 2.1 is available: 2.3.0 [NewerVersionAvailable]\n"
                    + "    compile 'joda-time:joda-time:2.1'\n"
                    + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                    + "build.gradle:10: Warning: A newer version of com.squareup.dagger:dagger than 1.2.0 is available: 1.2.1 [NewerVersionAvailable]\n"
                    + "    compile 'com.squareup.dagger:dagger:1.2.0'\n"
                    + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                    + "0 errors, 2 warnings\n",

                    lintProject("gradle/RemoteVersions.gradle=>build.gradle"));
        } finally {
            GradleDetector.ourMockData = null;
        }
    }

    public void testBadDependenciesInBuildscriptBlock() throws Exception {
        mEnabled = Sets.newHashSet(IMPROPER_PROJECT_LEVEL_STATEMENT, MISPLACED_STATEMENT);
        assertEquals(""
                + "build.gradle:12: Warning: Only `classpath` dependencies should appear in the `buildscript` dependencies block [ImproperProjectLevelStatement]\n"
                + "    compile 'com.android.support:appcompat-v7:19.+'\n"
                + "    ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "0 errors, 1 warnings\n",

                lintProject("gradle/BadDependenciesInBuildscriptBlock.gradle=>build.gradle"));
    }

    public void testDependenciesInAndroidBlock() throws Exception {
        mEnabled = Sets.newHashSet(IMPROPER_PROJECT_LEVEL_STATEMENT, MISPLACED_STATEMENT);
        assertEquals(""
                + "build.gradle:14: Warning: A `dependencies` block doesn't belong here. [MisplacedStatement]\n"
                + "  dependencies {\n"
                + "  ^\n"
                + "0 errors, 1 warnings\n",

                lintProject("gradle/DependenciesInAndroidBlock.gradle=>build.gradle"));
    }

    public void testNoAndroidStatement() throws Exception {
        mEnabled = Sets.newHashSet(IMPROPER_PROJECT_LEVEL_STATEMENT, MISPLACED_STATEMENT);
        assertEquals(""
                + "build.gradle:1: Warning: The `apply plugin` statement should only be used if there is a corresponding module for this build file. [ImproperProjectLevelStatement]\n"
                + "apply plugin: 'com.android.application'\n"
                + "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "0 errors, 1 warnings\n",

                lintProject("gradle/NoAndroidStatement.gradle=>build.gradle"));
    }

    public void testRepositoriesInDependenciesBlock() throws Exception {
        mEnabled = Sets.newHashSet(IMPROPER_PROJECT_LEVEL_STATEMENT, MISPLACED_STATEMENT);
        assertEquals(""
                + "build.gradle:31: Warning: A `repositories` block doesn't belong here. [MisplacedStatement]\n"
                + "  repositories {\n"
                + "  ^\n"
                + "0 errors, 1 warnings\n",

                lintProject("gradle/RepositoriesInDependenciesBlock.gradle=>build.gradle"));
    }

    public void testTopLevelDependenciesBlock() throws Exception {
        mEnabled = Sets.newHashSet(IMPROPER_PROJECT_LEVEL_STATEMENT, MISPLACED_STATEMENT);
        assertEquals(""
                + "build.gradle:33: Warning: A top-level `dependencies` block should only appear in build files that correspond to a module. [ImproperProjectLevelStatement]\n"
                + "dependencies {\n"
                + "^\n"
                + "0 errors, 1 warnings\n",

                lintProject("gradle/TopLevelDependenciesBlock.gradle=>build.gradle"));
    }

    public void testTopLevelRepositoriesBlock() throws Exception {
        mEnabled = Sets.newHashSet(IMPROPER_PROJECT_LEVEL_STATEMENT, MISPLACED_STATEMENT);
        assertEquals(""
                + "build.gradle:17: Warning: A top-level `repositories` block should only appear in build files that correspond to a module. [ImproperProjectLevelStatement]\n"
                + "repositories {\n"
                + "^\n"
                + "0 errors, 1 warnings\n",

                lintProject("gradle/TopLevelRepositoriesBlock.gradle=>build.gradle"));
    }

    @Override
    protected void checkReportedError(@NonNull Context context, @NonNull Issue issue,
            @NonNull Severity severity, @Nullable Location location, @NonNull String message,
            @Nullable Object data) {
        if (issue == DEPENDENCY && message.startsWith("Using the appcompat library when ")) {
            // No data embedded in this specific message
            return;
        }

        // Issues we're supporting getOldFrom
        if (issue == DEPENDENCY
                || issue == STRING_INTEGER
                || issue == DEPRECATED
                || issue == PLUS) {
            assertNotNull("Could not extract message tokens from " + message,
                    GradleDetector.getOldValue(issue, message));
        }

        if (issue == DEPENDENCY
                || issue == STRING_INTEGER
                || issue == DEPRECATED) {
            assertNotNull("Could not extract message tokens from " + message,
                    GradleDetector.getNewValue(issue, message));
        }
    }

    // -------------------------------------------------------------------------------------------
    // Test infrastructure below here
    // -------------------------------------------------------------------------------------------

    static final Implementation IMPLEMENTATION = new Implementation(
            GroovyGradleDetector.class,
            Scope.GRADLE_SCOPE);
    static {
        for (Issue issue : new BuiltinIssueRegistry().getIssues()) {
            if (issue.getImplementation().getDetectorClass() == GradleDetector.class) {
                issue.setImplementation(IMPLEMENTATION);
            }
        }
    }

    @Override
    protected Detector getDetector() {
        return new GroovyGradleDetector();
    }

    private Set<Issue> mEnabled;

    @Override
    protected TestConfiguration getConfiguration(LintClient client, Project project) {
        return new TestConfiguration(client, project, null) {
            @Override
            public boolean isEnabled(@NonNull Issue issue) {
                return super.isEnabled(issue) && (mEnabled == null || mEnabled.contains(issue));
            }
        };
    }


    // Copy of com.android.build.gradle.tasks.GroovyGradleDetector (with "static" added as
    // a modifier, and the unused field IMPLEMENTATION removed, and with fail(t.toString())
    // inserted into visitBuildScript's catch handler.
    //
    // THIS CODE DUPLICATION IS NOT AN IDEAL SITUATION! But, it's preferable to a lack of
    // tests.
    //
    // A more proper fix would be to extract the groovy detector into a library shared by
    // the testing framework and the gradle plugin.

    public static class GroovyGradleDetector extends GradleDetector {
        @Override
        public void visitBuildScript(@NonNull final Context context, Map<String, Object> sharedData) {
            try {
                visitQuietly(context, sharedData);
            } catch (Throwable t) {
                // ignore
                // Parsing the build script can involve class loading that we sometimes can't
                // handle. This happens for example when running lint in build-system/tests/api/.
                // This is a lint limitation rather than a user error, so don't complain
                // about these. Consider reporting a Issue#LINT_ERROR.
                fail(t.toString());
            }
        }

        private void visitQuietly(@NonNull final Context context,
                @SuppressWarnings("UnusedParameters") Map<String, Object> sharedData) {
            String source = context.getContents();
            if (source == null) {
                return;
            }

            List<ASTNode> astNodes = new AstBuilder().buildFromString(source);
            GroovyCodeVisitor visitor = new CodeVisitorSupport() {
                private List<MethodCallExpression> mMethodCallStack = Lists.newArrayList();
                @Override
                public void visitMethodCallExpression(MethodCallExpression expression) {
                    mMethodCallStack.add(expression);
                    super.visitMethodCallExpression(expression);
                    Expression arguments = expression.getArguments();
                    String parent = expression.getMethodAsString();
                    String parentParent = getParentParent();
                    if (arguments instanceof ArgumentListExpression) {
                        ArgumentListExpression ale = (ArgumentListExpression)arguments;
                        List<Expression> expressions = ale.getExpressions();
                        if (expressions.size() == 1 &&
                                expressions.get(0) instanceof ClosureExpression) {
                            if (isInterestingBlock(parent, parentParent)) {
                                checkBlock(context, parent, parentParent, expression);
                                ClosureExpression closureExpression =
                                        (ClosureExpression)expressions.get(0);
                                Statement block = closureExpression.getCode();
                                if (block instanceof BlockStatement) {
                                    BlockStatement bs = (BlockStatement)block;
                                    for (Statement statement : bs.getStatements()) {
                                        if (statement instanceof ExpressionStatement) {
                                            ExpressionStatement e = (ExpressionStatement)statement;
                                            if (e.getExpression() instanceof MethodCallExpression) {
                                                checkDslProperty(parent,
                                                        (MethodCallExpression)e.getExpression(),
                                                        parentParent);
                                            }
                                        } else if (statement instanceof ReturnStatement) {
                                            // Single item in block
                                            ReturnStatement e = (ReturnStatement)statement;
                                            if (e.getExpression() instanceof MethodCallExpression) {
                                                checkDslProperty(parent,
                                                        (MethodCallExpression)e.getExpression(),
                                                        parentParent);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else if (arguments instanceof TupleExpression) {
                        if (isInterestingStatement(parent, parentParent)) {
                            TupleExpression te = (TupleExpression) arguments;
                            Map<String, String> namedArguments = Maps.newHashMap();
                            List<String> unnamedArguments = Lists.newArrayList();
                            for (Expression subExpr : te.getExpressions()) {
                                if (subExpr instanceof NamedArgumentListExpression) {
                                    NamedArgumentListExpression nale = (NamedArgumentListExpression) subExpr;
                                    for (MapEntryExpression mae : nale.getMapEntryExpressions()) {
                                        namedArguments.put(mae.getKeyExpression().getText(),
                                                mae.getValueExpression().getText());
                                    }
                                }
                            }
                            checkMethodCall(context, parent, parentParent, namedArguments, unnamedArguments, expression);
                        }
                    }
                    assert !mMethodCallStack.isEmpty();
                    assert mMethodCallStack.get(mMethodCallStack.size() - 1) == expression;
                    mMethodCallStack.remove(mMethodCallStack.size() - 1);
                }

                private String getParentParent() {
                    for (int i = mMethodCallStack.size() - 2; i >= 0; i--) {
                        MethodCallExpression expression = mMethodCallStack.get(i);
                        Expression arguments = expression.getArguments();
                        if (arguments instanceof ArgumentListExpression) {
                            ArgumentListExpression ale = (ArgumentListExpression)arguments;
                            List<Expression> expressions = ale.getExpressions();
                            if (expressions.size() == 1 &&
                                    expressions.get(0) instanceof ClosureExpression) {
                                return expression.getMethodAsString();
                            }
                        }
                    }

                    return null;
                }

                private void checkDslProperty(String parent, MethodCallExpression c,
                        String parentParent) {
                    String property = c.getMethodAsString();
                    if (isInterestingProperty(property, parent, getParentParent())) {
                        String value = getText(c.getArguments());
                        checkDslPropertyAssignment(context, property, value, parent, parentParent, c, c);
                    }
                }

                private String getText(ASTNode node) {
                    String source = context.getContents();
                    Pair<Integer, Integer> offsets = getOffsets(node, context);
                    return source.substring(offsets.getFirst(), offsets.getSecond());
                }
            };

            for (ASTNode node : astNodes) {
                node.visit(visitor);
            }
        }

        @NonNull
        private static Pair<Integer, Integer> getOffsets(ASTNode node, Context context) {
            String source = context.getContents();
            assert source != null; // because we successfully parsed
            int start = 0;
            int end = source.length();
            int line = 1;
            int startLine = node.getLineNumber();
            int startColumn = node.getColumnNumber();
            int endLine = node.getLastLineNumber();
            int endColumn = node.getLastColumnNumber();
            int column = 1;
            for (int index = 0, len = end; index < len; index++) {
                if (line == startLine && column == startColumn) {
                    start = index;
                }
                if (line == endLine && column == endColumn) {
                    end = index;
                    break;
                }

                char c = source.charAt(index);
                if (c == '\n') {
                    line++;
                    column = 1;
                } else {
                    column++;
                }
            }

            return Pair.of(start, end);
        }

        @Override
        protected int getStartOffset(@NonNull Context context, @NonNull Object cookie) {
            ASTNode node = (ASTNode) cookie;
            Pair<Integer, Integer> offsets = getOffsets(node, context);
            return offsets.getFirst();
        }

        @Override
        protected Location createLocation(@NonNull Context context, @NonNull Object cookie) {
            ASTNode node = (ASTNode) cookie;
            Pair<Integer, Integer> offsets = getOffsets(node, context);
            int fromLine = node.getLineNumber() - 1;
            int fromColumn = node.getColumnNumber() - 1;
            int toLine = node.getLastLineNumber() - 1;
            int toColumn = node.getLastColumnNumber() - 1;
            return Location.create(context.file,
                    new DefaultPosition(fromLine, fromColumn, offsets.getFirst()),
                    new DefaultPosition(toLine, toColumn, offsets.getSecond()));
        }
    }
}