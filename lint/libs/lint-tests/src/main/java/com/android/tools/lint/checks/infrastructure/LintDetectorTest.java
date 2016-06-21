/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.tools.lint.checks.infrastructure;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.DOT_JAVA;
import static com.android.SdkConstants.FN_ANDROID_MANIFEST_XML;
import static com.android.SdkConstants.NEW_ID_PREFIX;
import static com.android.utils.SdkUtils.escapePropertyValue;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.res2.AbstractResourceRepository;
import com.android.ide.common.res2.DuplicateDataException;
import com.android.ide.common.res2.MergingException;
import com.android.ide.common.res2.ResourceFile;
import com.android.ide.common.res2.ResourceItem;
import com.android.ide.common.res2.ResourceMerger;
import com.android.ide.common.res2.ResourceRepository;
import com.android.ide.common.res2.ResourceSet;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.sdklib.IAndroidTarget;
import com.android.testutils.SdkTestCase;
import com.android.tools.lint.EcjParser;
import com.android.tools.lint.ExternalAnnotationRepository;
import com.android.tools.lint.LintCliClient;
import com.android.tools.lint.LintCliFlags;
import com.android.tools.lint.Reporter;
import com.android.tools.lint.TextReporter;
import com.android.tools.lint.Warning;
import com.android.tools.lint.checks.BuiltinIssueRegistry;
import com.android.tools.lint.client.api.Configuration;
import com.android.tools.lint.client.api.DefaultConfiguration;
import com.android.tools.lint.client.api.IssueRegistry;
import com.android.tools.lint.client.api.JavaParser;
import com.android.tools.lint.client.api.JavaPsiVisitor;
import com.android.tools.lint.client.api.JavaVisitor;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.client.api.LintDriver;
import com.android.tools.lint.client.api.LintRequest;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.TextFormat;
import com.android.tools.lint.psi.EcjPsiBuilder;
import com.android.utils.FileUtils;
import com.android.utils.ILogger;
import com.android.utils.SdkUtils;
import com.android.utils.StdLogger;
import com.android.utils.XmlUtils;
import com.google.common.annotations.Beta;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

import org.eclipse.jdt.core.compiler.CategorizedProblem;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration;
import org.intellij.lang.annotations.Language;
import org.objectweb.asm.Opcodes;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;

import javax.xml.bind.DatatypeConverter;

/**
 * Test case for lint detectors.
 * <p>
 * <b>NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
 */
@Beta
@SuppressWarnings("javadoc")
public abstract class LintDetectorTest extends SdkTestCase {
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        BuiltinIssueRegistry.reset();
        JavaVisitor.clearCrashCount();
        JavaPsiVisitor.clearCrashCount();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        List<Issue> issues;
        try {
            // Some detectors extend LintDetectorTest but don't actually
            // provide issues and assert instead; gracefully ignore those
            // here
            issues = getIssues();
        } catch (Throwable t) {
            issues = Collections.emptyList();
        }
        for (Issue issue : issues) {
            if (issue.getImplementation().getScope().contains(Scope.JAVA_FILE)) {
                if (JavaVisitor.getCrashCount() > 0 || JavaPsiVisitor.getCrashCount() > 0) {
                    fail("There was a crash during lint execution; consult log for details");
                }
                break;
            }
        }
    }

    protected abstract Detector getDetector();

    private Detector mDetector;

    protected final Detector getDetectorInstance() {
        if (mDetector == null) {
            mDetector = getDetector();
        }

        return mDetector;
    }

    protected boolean allowCompilationErrors() {
        return false;
    }

    /**
     * If false (the default), lint will run your detectors <b>twice</b>, first on the
     * plain source code, and then a second time where it has inserted whitespace
     * and parentheses pretty much everywhere, to help catch bugs where your detector
     * is only checking direct parents or siblings rather than properly allowing for
     * whitespace and parenthesis nodes which can be present for example when using
     * PSI inside the IDE.
     */
    protected boolean skipExtraTokenChecks() {
        return false;
    }

    protected abstract List<Issue> getIssues();

    public class CustomIssueRegistry extends IssueRegistry {
        @NonNull
        @Override
        public List<Issue> getIssues() {
            return LintDetectorTest.this.getIssues();
        }
    }

    protected String lintFiles(String... relativePaths) throws Exception {
        List<File> files = new ArrayList<File>();
        File targetDir = getTargetDir();
        for (String relativePath : relativePaths) {
            File file = getTestfile(targetDir, relativePath);
            assertNotNull(file);
            files.add(file);
        }

        Collections.sort(files, new Comparator<File>() {
            @Override
            public int compare(File file1, File file2) {
                ResourceFolderType folder1 = ResourceFolderType.getFolderType(
                        file1.getParentFile().getName());
                ResourceFolderType folder2 = ResourceFolderType.getFolderType(
                        file2.getParentFile().getName());
                if (folder1 != null && folder2 != null && folder1 != folder2) {
                    return folder1.compareTo(folder2);
                }
                return file1.compareTo(file2);
            }
        });

        addManifestFile(targetDir);

        return checkLint(files);
    }

    protected String checkLint(List<File> files) throws Exception {
        TestLintClient lintClient = createClient();
        return checkLint(lintClient, files);
    }

    protected String checkLint(TestLintClient lintClient, List<File> files) throws Exception {
        if (System.getenv("ANDROID_BUILD_TOP") != null) {
            fail("Don't run the lint tests with $ANDROID_BUILD_TOP set; that enables lint's "
                    + "special support for detecting AOSP projects (looking for .class "
                    + "files in $ANDROID_HOST_OUT etc), and this confuses lint.");
        }

        mOutput = new StringBuilder();
        String result = lintClient.analyze(files);

        if (getDetector() instanceof Detector.JavaPsiScanner && !skipExtraTokenChecks()) {
            mOutput.setLength(0);
            lintClient.reset();
            try {
                //lintClient.mWarnings.clear();
                Field field = LintCliClient.class.getDeclaredField("mWarnings");
                field.setAccessible(true);
                List list = (List)field.get(lintClient);
                list.clear();
            } catch (Throwable t) {
                fail(t.toString());
            }

            String secondResult;
            try {
                EcjPsiBuilder.setDebugOptions(true, true);
                secondResult = lintClient.analyze(files);
            } finally {
                EcjPsiBuilder.setDebugOptions(false, false);
            }

            assertEquals("The lint check produced different results when run on the "
                    + "normal test files and a version where parentheses and whitespace tokens "
                    + "have been inserted everywhere. The lint check should be resilient towards "
                    + "these kinds of differences (since in the IDE, PSI will include both "
                    + "types of nodes. Your detector should call LintUtils.skipParenthes(parent) "
                    + "to jump across parentheses nodes when checking parents, and there are "
                    + "similar methods in LintUtils to skip across whitespace siblings.\n",
                    result, secondResult);
        }

        // The output typically contains a few directory/filenames.
        // On Windows we need to change the separators to the unix-style
        // forward slash to make the test as OS-agnostic as possible.
        if (File.separatorChar != '/') {
            result = result.replace(File.separatorChar, '/');
        }

        for (File f : files) {
            deleteFile(f);
        }

        return result;
    }

    protected void checkReportedError(
            @NonNull Context context,
            @NonNull Issue issue,
            @NonNull Severity severity,
            @NonNull Location location,
            @NonNull String message) {
    }

    protected TestLintClient createClient() {
        return new TestLintClient();
    }

    protected TestConfiguration getConfiguration(LintClient client, Project project) {
        return new TestConfiguration(client, project, null);
    }

    protected void configureDriver(LintDriver driver) {
    }

    /**
     * Run lint on the given files when constructed as a separate project
     *
     * @return The output of the lint check. On Windows, this transforms all directory separators to
     * the unix-style forward slash.
     */
    protected String lintProject(String... relativePaths) throws Exception {
        File projectDir = getProjectDir(null, relativePaths);
        return checkLint(Collections.singletonList(projectDir));
    }

    protected String lintProjectIncrementally(String currentFile, String... relativePaths)
            throws Exception {
        File projectDir = getProjectDir(null, relativePaths);
        File current = new File(projectDir, currentFile.replace('/', File.separatorChar));
        assertTrue(current.exists());
        TestLintClient client = createClient();
        client.setIncremental(current);
        return checkLint(client, Collections.singletonList(projectDir));
    }

    protected String lintProjectIncrementally(String currentFile, TestFile... files)
            throws Exception {
        File projectDir = getProjectDir(null, files);
        File current = new File(projectDir, currentFile.replace('/', File.separatorChar));
        assertTrue(current.exists());
        TestLintClient client = createClient();
        client.setIncremental(current);
        return checkLint(client, Collections.singletonList(projectDir));
    }

    /**
     * Run lint on the given files when constructed as a separate project
     * @return The output of the lint check. On Windows, this transforms all directory
     *   separators to the unix-style forward slash.
     */
    protected String lintProject(TestFile... files) throws Exception {
        File projectDir = getProjectDir(null, files);
        return checkLint(Collections.singletonList(projectDir));
    }

    @Override
    protected File getTargetDir() {
        File targetDir = new File(getTempDir(), getClass().getSimpleName() + "_" + getName());
        addCleanupDir(targetDir);
        return targetDir;
    }

    @NonNull
    public TestFile file() {
        return new TestFile();
    }

    @NonNull
    public TestFile source(@NonNull String to, @NonNull String source) {
        return file().to(to).withSource(source);
    }

    @NonNull
    public TestFile java(@NonNull String to, @NonNull @Language("JAVA") String source) {
        return file().to(to).withSource(source);
    }

    private static final Pattern PACKAGE_PATTERN = Pattern.compile("package\\s+(.*)\\s*;");
    private static final Pattern CLASS_PATTERN = Pattern
            .compile("\\s*(\\S+)\\s*(extends.*)?(implements.*)?\\{");

    @NonNull
    public TestFile java(@NonNull @Language("JAVA") String source) {
        // Figure out the "to" path: the package plus class name + java in the src/ folder
        Matcher matcher = PACKAGE_PATTERN.matcher(source);
        assertTrue("Couldn't find package declaration in source", matcher.find());
        String pkg = matcher.group(1).trim();
        matcher = CLASS_PATTERN.matcher(source);
        assertTrue("Couldn't find class declaration in source", matcher.find());
        String cls = matcher.group(1).trim();
        String to = "src/" + pkg.replace('.', '/') + '/' + cls + DOT_JAVA;

        return file().to(to).withSource(source);
    }

    @NonNull
    public TestFile xml(@NonNull String to, @NonNull @Language("XML") String source) {
        return file().to(to).withSource(source);
    }

    @NonNull
    public TestFile copy(@NonNull String from, @NonNull String to) {
        return file().from(from).to(to);
    }

    @NonNull
    public ManifestTestFile manifest() {
        return new ManifestTestFile();
    }

    public class ManifestTestFile extends TestFile {
        public String pkg = "test.pkg";
        public int minSdk;
        public int targetSdk;
        public String[] permissions;

        public ManifestTestFile() {
            to(FN_ANDROID_MANIFEST_XML);
        }

        public ManifestTestFile minSdk(int min) {
            minSdk = min;
            return this;
        }

        public ManifestTestFile targetSdk(int target) {
            targetSdk = target;
            return this;
        }

        public ManifestTestFile permissions(String... permissions) {
            this.permissions = permissions;
            return this;
        }

        @Override
        @NonNull
        public String getContents() {
            if (contents == null) {
                StringBuilder sb = new StringBuilder();
                sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
                sb.append("<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n");
                sb.append("    package=\"").append(pkg).append("\"\n");
                sb.append("    android:versionCode=\"1\"\n");
                sb.append("    android:versionName=\"1.0\" >\n");
                if (minSdk > 0 || targetSdk > 0) {
                    sb.append("    <uses-sdk ");
                    if (minSdk > 0) {
                        sb.append(" android:minSdkVersion=\"").append(Integer.toString(minSdk))
                                .append("\"");
                    }
                    if (targetSdk > 0) {
                        sb.append(" android:targetSdkVersion=\"")
                                .append(Integer.toString(targetSdk))
                                .append("\"");
                    }
                    sb.append(" />\n");
                }
                if (permissions != null && permissions.length > 0) {
                    StringBuilder permissionBlock = new StringBuilder();
                    for (String permission : permissions) {
                        permissionBlock
                                .append("    <uses-permission android:name=\"")
                                .append(permission)
                                .append("\" />\n");
                    }
                    sb.append(permissionBlock);
                    sb.append("\n");
                }

                sb.append(""
                        + "\n"
                        + "    <application\n"
                        + "        android:icon=\"@drawable/ic_launcher\"\n"
                        + "        android:label=\"@string/app_name\" >\n"
                        + "    </application>\n"
                        + "\n"
                        + "</manifest>");
                contents = sb.toString();
            }

            return contents;
        }

        @NonNull
        @Override
        public File createFile(@NonNull File targetDir) throws IOException {
            getContents(); // lazy init
            return super.createFile(targetDir);
        }
    }

    @NonNull
    public PropertyTestFile projectProperties() {
        return new PropertyTestFile();
    }

    public class PropertyTestFile extends TestFile {
        @SuppressWarnings("StringBufferField")
        private StringBuilder mStringBuilder = new StringBuilder();

        private int mNextLibraryIndex = 1;

        public PropertyTestFile() {
            to("project.properties");
        }

        public PropertyTestFile property(String key, String value) {
            String escapedValue = escapePropertyValue(value);
            mStringBuilder.append(key).append('=').append(escapedValue).append('\n');
            return this;
        }

        public PropertyTestFile compileSdk(int target) {
            mStringBuilder.append("target=android-").append(Integer.toString(target)).append('\n');
            return this;
        }

        public PropertyTestFile library(boolean isLibrary) {
            mStringBuilder.append("android.library=").append(Boolean.toString(isLibrary))
                    .append('\n');
            return this;
        }

        public PropertyTestFile manifestMerger(boolean merger) {
            mStringBuilder.append("manifestmerger.enabled=").append(Boolean.toString(merger))
                    .append('\n');
            return this;
        }

        public PropertyTestFile dependsOn(String relative) {
            assertTrue(relative.startsWith("../"));
            mStringBuilder.append("android.library.reference.")
                    .append(Integer.toString(mNextLibraryIndex++))
                    .append("=").append(escapePropertyValue(relative))
                    .append('\n');
            return this;
        }

        @Override
        public TestFile withSource(@NonNull String source) {
            fail("Don't call withSource on " + this.getClass());
            return this;
        }

        @Override
        @NonNull
        public String getContents() {
            contents = mStringBuilder.toString();
            return contents;
        }

        @NonNull
        @Override
        public File createFile(@NonNull File targetDir) throws IOException {
            getContents(); // lazy init
            return super.createFile(targetDir);
        }
    }

    /** Produces byte arrays, for use with {@link BinaryTestFile} */
    public static class ByteProducer {
        @SuppressWarnings("MethodMayBeStatic") // intended for override
        @NonNull
        public byte[] produce() {
            return new byte[0];
        }
    }

    public static class BytecodeProducer extends ByteProducer implements Opcodes {
        /**
         *  Typically generated by first getting asm output like this:
         * <pre>
         *     assertEquals("", asmify(new File("/full/path/to/my/file.class")));
         * </pre>
         * ...and when the test fails, the actual test output is the necessary assembly
         *
         */
        @Override
        @SuppressWarnings("MethodMayBeStatic") // intended for override
        @NonNull
        public byte[] produce() {
            return new byte[0];
        }
    }

    @NonNull
    public BinaryTestFile bytecode(@NonNull String to, @NonNull BytecodeProducer producer) {
        return new BinaryTestFile(to, producer);
    }

    public static String toBase64(@NonNull byte[] bytes) {
        String base64 = DatatypeConverter.printBase64Binary(bytes);
        return Joiner.on("").join(Splitter.fixedLength(60).split(base64));
    }

    public static String toBase64(@NonNull File file) throws IOException {
        return toBase64(Files.toByteArray(file));
    }

    /**
     * Creates a test file from the given base64 data. To create this data, use {@link
     * #toBase64(File)} or {@link #toBase64(byte[])}, for example via <pre>{@code assertEquals("",
     * uuencode(new File("path/to/your.class")));} </pre>
     *
     * @param to      the file to write as
     * @param encoded the encoded data
     * @return the new test file
     */
    public BinaryTestFile base64(@NonNull String to, @NonNull String encoded) {
        encoded = encoded.replaceAll("\n", "");
        final byte[] bytes = DatatypeConverter.parseBase64Binary(encoded);
        return new BinaryTestFile(to, new BytecodeProducer() {
            @NonNull
            @Override
            public byte[] produce() {
                return bytes;
            }
        });
    }

    public class BinaryTestFile extends TestFile {
        private final BytecodeProducer mProducer;

        public BinaryTestFile(@NonNull String to, @NonNull BytecodeProducer producer) {
            to(to);
            mProducer = producer;
        }

        @Override
        public TestFile withSource(@NonNull String source) {
            fail("Don't call withSource on " + this.getClass());
            return this;
        }

        @Override
        @NonNull
        public String getContents() {
            fail("Don't call getContents on binary " + this.getClass());
            return null;
        }

        public byte[] getBinaryContents() {
            return mProducer.produce();
        }

        @NonNull
        @Override
        public File createFile(@NonNull File targetDir) throws IOException {
            int index = targetRelativePath.lastIndexOf('/');
            String relative = null;
            String name = targetRelativePath;
            if (index != -1) {
                name = targetRelativePath.substring(index + 1);
                relative = targetRelativePath.substring(0, index);
            }
            InputStream stream = new ByteArrayInputStream(getBinaryContents());
            return makeTestFile(targetDir, name, relative, stream);
        }
    }

    @NonNull
    public JarTestFile jar(@NonNull String to) {
        return new JarTestFile(to);
    }

    @NonNull
    public JarTestFile jar(@NonNull String to, @NonNull TestFile... files) {
        JarTestFile jar = new JarTestFile(to);
        jar.files(files);
        return jar;
    }

    public class JarTestFile extends TestFile {
        private List<TestFile> mFiles = Lists.newArrayList();
        private Map<TestFile, String> mPath = Maps.newHashMap();

        public JarTestFile(@NonNull String to) {
            to(to);
        }

        public JarTestFile files(@NonNull TestFile... files) {
            mFiles.addAll(Arrays.asList(files));
            return this;
        }

        public JarTestFile add(@NonNull TestFile file, @NonNull String path) {
            add(file);
            mPath.put(file, path);
            return this;
        }

        public JarTestFile add(@NonNull TestFile file) {
            mFiles.add(file);
            mPath.put(file, null);
            return this;
        }

        @Override
        public TestFile withSource(@NonNull String source) {
            fail("Don't call withSource on " + this.getClass());
            return this;
        }

        @Override
        @NonNull
        public String getContents() {
            fail("Don't call getContents on binary " + this.getClass());
            return null;
        }

        @NonNull
        @Override
        public File createFile(@NonNull File targetDir) throws IOException {
            int index = targetRelativePath.lastIndexOf('/');
            String relative = null;
            String name = targetRelativePath;
            if (index != -1) {
                name = targetRelativePath.substring(index + 1);
                relative = targetRelativePath.substring(0, index);
            }

            File dir = targetDir;
            if (relative != null) {
                dir = new File(dir, relative);
                if (!dir.exists()) {
                    boolean mkdir = dir.mkdirs();
                    assertTrue(dir.getPath(), mkdir);
                }
            } else if (!dir.exists()) {
                boolean mkdir = dir.mkdirs();
                assertTrue(dir.getPath(), mkdir);
            }
            File tempFile = new File(dir, name);
            if (tempFile.exists()) {
                boolean deleted = tempFile.delete();
                assertTrue(tempFile.getPath(), deleted);
            }

            Manifest manifest = new Manifest();
            manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            JarOutputStream jarOutputStream = new JarOutputStream(
                    new BufferedOutputStream(new FileOutputStream(tempFile)), manifest);

            try {
                for (TestFile file : mFiles) {
                    String path = mPath.get(file);
                    if (path == null) {
                        path = file.targetRelativePath;
                    }
                    jarOutputStream.putNextEntry(new ZipEntry(path));
                    if (file instanceof BinaryTestFile) {
                        byte[] bytes = ((BinaryTestFile)file).getBinaryContents();
                        assertNotNull(file.targetRelativePath, bytes);
                        ByteStreams.copy(new ByteArrayInputStream(bytes), jarOutputStream);
                    } else {
                        String contents = file.getContents();
                        assertNotNull(file.targetRelativePath, contents);
                        byte[] bytes = contents.getBytes(Charsets.UTF_8);
                        ByteStreams.copy(new ByteArrayInputStream(bytes), jarOutputStream);
                    }
                    jarOutputStream.closeEntry();
                }
            } finally {
                jarOutputStream.close();
            }

            return tempFile;
        }
    }

    @NonNull
    public TestFile copy(@NonNull String from) {
        return file().from(from).to(from);
    }

    /** Creates a project directory structure from the given files */
    protected File getProjectDir(String name, String ...relativePaths) throws Exception {
        assertFalse("getTargetDir must be overridden to make a unique directory",
                getTargetDir().equals(getTempDir()));

        List<TestFile> testFiles = Lists.newArrayList();
        for (String relativePath : relativePaths) {
            testFiles.add(file().copy(relativePath));
        }
        return getProjectDir(name, testFiles.toArray(new TestFile[testFiles.size()]));
    }

    /** Creates a project directory structure from the given files */
    protected File getProjectDir(String name, TestFile... testFiles) throws Exception {
        assertFalse("getTargetDir must be overridden to make a unique directory",
                getTargetDir().equals(getTempDir()));

        File projectDir = getTargetDir();
        if (name != null) {
            projectDir = new File(projectDir, name);
        }
        if (!projectDir.exists()) {
            assertTrue(projectDir.getPath(), projectDir.mkdirs());
        }

        for (TestFile fp : testFiles) {
            File file = fp.createFile(projectDir);
            assertNotNull(file);
        }

        addManifestFile(projectDir);
        return projectDir;
    }

    private static void addManifestFile(File projectDir) throws IOException {
        // Ensure that there is at least a manifest file there to make it a valid project
        // as far as Lint is concerned:
        if (!new File(projectDir, "AndroidManifest.xml").exists()) {
            File manifest = new File(projectDir, "AndroidManifest.xml");
            FileWriter fw = new FileWriter(manifest);
            fw.write("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                "    package=\"foo.bar2\"\n" +
                "    android:versionCode=\"1\"\n" +
                "    android:versionName=\"1.0\" >\n" +
                "</manifest>\n");
            fw.close();
        }
    }

    private StringBuilder mOutput = null;

    @Override
    protected InputStream getTestResource(String relativePath, boolean expectExists) {
        String path = "data" + File.separator + relativePath; //$NON-NLS-1$
        InputStream stream = getClass().getResourceAsStream(path);
        if (!expectExists && stream == null) {
            return null;
        }
        return stream;
    }

    protected boolean isEnabled(Issue issue) {
        Class<? extends Detector> detectorClass = getDetectorInstance().getClass();
        if (issue.getImplementation().getDetectorClass() == detectorClass) {
            return true;
        }

        if (issue == IssueRegistry.LINT_ERROR) {
            return !ignoreSystemErrors();
        } else if (issue == IssueRegistry.PARSER_ERROR) {
            return !allowCompilationErrors();
        }

        return false;
    }

    protected boolean includeParentPath() {
        return false;
    }

    protected EnumSet<Scope> getLintScope(List<File> file) {
        return null;
    }

    public String getSuperClass(Project project, String name) {
        return null;
    }

    protected boolean ignoreSystemErrors() {
        return true;
    }

    public class TestLintClient extends LintCliClient {
        private StringWriter mWriter = new StringWriter();
        private File mIncrementalCheck;

        public TestLintClient() {
            super(new LintCliFlags(), "test");
            mFlags.getReporters().add(new TextReporter(this, mFlags, mWriter, false));
        }

        @Override
        public String getSuperClass(@NonNull Project project, @NonNull String name) {
            String superClass = LintDetectorTest.this.getSuperClass(project, name);
            if (superClass != null) {
                return superClass;
            }

            return super.getSuperClass(project, name);
        }

        @Override
        public void reset() {
            super.reset();
            mWriter.getBuffer().setLength(0);
        }

        public String analyze(List<File> files) throws Exception {
            mDriver = new LintDriver(new CustomIssueRegistry(), this);
            configureDriver(mDriver);
            LintRequest request = new LintRequest(this, files);
            if (mIncrementalCheck != null) {
                assertEquals(1, files.size());
                File projectDir = files.get(0);
                assertTrue(isProjectDirectory(projectDir));
                Project project = createProject(projectDir, projectDir);
                project.addFile(mIncrementalCheck);
                List<Project> projects = Collections.singletonList(project);
                request.setProjects(projects);
            }

            mDriver.analyze(request.setScope(getLintScope(files)));

            // Check compare contract
            Warning prev = null;
            for (Warning warning : mWarnings) {
                if (prev != null) {
                    boolean equals = warning.equals(prev);
                    assertEquals(equals, prev.equals(warning));
                    int compare = warning.compareTo(prev);
                    assertEquals(equals, compare == 0);
                    assertEquals(-compare, prev.compareTo(warning));
                }
                prev = warning;
            }

            Collections.sort(mWarnings);

            // Check compare contract and transitivity
            Warning prev2 = prev;
            prev = null;
            for (Warning warning : mWarnings) {
                if (prev != null && prev2 != null) {
                    assertTrue(warning.compareTo(prev) >= 0);
                    assertTrue(prev.compareTo(prev2) >= 0);
                    assertTrue(warning.compareTo(prev2) >= 0);

                    assertTrue(prev.compareTo(warning) <= 0);
                    assertTrue(prev2.compareTo(prev) <= 0);
                    assertTrue(prev2.compareTo(warning) <= 0);
                }
                prev2 = prev;
                prev = warning;
            }

            for (Reporter reporter : mFlags.getReporters()) {
                reporter.write(mErrorCount, mWarningCount, mWarnings);
            }

            mOutput.append(mWriter.toString());

            if (mOutput.length() == 0) {
                mOutput.append("No warnings.");
            }

            String result = mOutput.toString();
            if (result.equals("No issues found.\n")) {
                result = "No warnings.";
            }

            result = cleanup(result);

            return result;
        }

        public String getErrors() throws Exception {
            return mWriter.toString();
        }

        @Override
        public JavaParser getJavaParser(@Nullable Project project) {
            return new EcjParser(this, project) {
                @Override
                public void prepareJavaParse(@NonNull List<JavaContext> contexts) {
                    super.prepareJavaParse(contexts);
                    if (!allowCompilationErrors() && mEcjResult != null) {
                        StringBuilder sb = new StringBuilder();
                        for (CompilationUnitDeclaration unit : mEcjResult.getCompilationUnits()) {
                            // so maybe I don't need my map!!
                            CategorizedProblem[] problems = unit.compilationResult()
                                    .getAllProblems();
                            if (problems != null) {
                                for (IProblem problem : problems) {
                                    if (problem == null || !problem.isError()) {
                                        continue;
                                    }
                                    String filename = new File(new String(
                                            problem.getOriginatingFileName())).getName();
                                    sb.append(filename)
                                            .append(":")
                                            .append(problem.isError() ? "Error" : "Warning")
                                            .append(": ").append(problem.getSourceLineNumber())
                                            .append(": ").append(problem.getMessage())
                                            .append('\n');
                                }
                            }
                        }
                        if (sb.length() > 0) {
                            fail("Found compilation problems in lint test not overriding "
                                    + "allowCompilationErrors():\n" + sb);
                        }

                    }
                }
            };
        }

        @Override
        public void report(
                @NonNull Context context,
                @NonNull Issue issue,
                @NonNull Severity severity,
                @NonNull Location location,
                @NonNull String message,
                @NonNull TextFormat format) {
            assertNotNull(location);

            if (ignoreSystemErrors() && (issue == IssueRegistry.LINT_ERROR)) {
                return;
            }
            // Use plain ascii in the test golden files for now. (This also ensures
            // that the markup is well-formed, e.g. if we have a ` without a matching
            // closing `, the ` would show up in the plain text.)
            message = format.convertTo(message, TextFormat.TEXT);

            checkReportedError(context, issue, severity, location, message);

            if (severity == Severity.FATAL) {
                // Treat fatal errors like errors in the golden files.
                severity = Severity.ERROR;
            }

            // For messages into all secondary locations to ensure they get
            // specifically included in the text report
            if (location.getSecondary() != null) {
                Location l = location.getSecondary();
                if (l == location) {
                    fail("Location link cycle");
                }
                while (l != null) {
                    if (l.getMessage() == null) {
                        l.setMessage("<No location-specific message");
                    }
                    if (l == l.getSecondary()) {
                        fail("Location link cycle");
                    }
                    l = l.getSecondary();
                }
            }

            super.report(context, issue, severity, location, message, format);

            // Make sure errors are unique!
            Warning prev = null;
            for (Warning warning : mWarnings) {
                assertNotSame(warning, prev);
                assert prev == null || !warning.equals(prev) : warning;
                prev = warning;
            }
        }

        @Override
        public void log(Throwable exception, String format, Object... args) {
            if (exception != null) {
                exception.printStackTrace();
            }
            StringBuilder sb = new StringBuilder();
            if (format != null) {
                sb.append(String.format(format, args));
            }
            if (exception != null) {
                sb.append(exception.toString());
            }
            System.err.println(sb);

            if (exception != null) {
                // Ensure that we get the full cause
                //fail(exception.toString());
                throw new RuntimeException(exception);
            }
        }

        @NonNull
        @Override
        public Configuration getConfiguration(@NonNull Project project,
                @Nullable LintDriver driver) {
            return LintDetectorTest.this.getConfiguration(this, project);
        }

        @Override
        public File findResource(@NonNull String relativePath) {
            if (relativePath.equals("platform-tools/api/api-versions.xml")) {
                // Look in the current Git repository and try to find it there
                File rootDir = getRootDir();
                if (rootDir != null) {
                    File file = new File(rootDir, "development" + File.separator + "sdk"
                            + File.separator + "api-versions.xml");
                    if (file.exists()) {
                        return file;
                    }
                }
                // Look in an SDK install, if found
                File home = getSdkHome();
                if (home != null) {
                    return new File(home, relativePath);
                }
            } else if (relativePath.equals(ExternalAnnotationRepository.SDK_ANNOTATIONS_PATH)) {
                // Look in the current Git repository and try to find it there
                File rootDir = getRootDir();
                if (rootDir != null) {
                    File file = new File(rootDir,
                            "tools" + File.separator
                            + "adt" + File.separator
                            + "idea" + File.separator
                            + "android" + File.separator
                            + "annotations");
                    if (file.exists()) {
                        return file;
                    }
                }
                // Look in an SDK install, if found
                File home = getSdkHome();
                if (home != null) {
                    File file = new File(home, relativePath);
                    return file.exists() ? file : null;
                }
            } else if (relativePath.startsWith("tools/support/")) {
                // Look in the current Git repository and try to find it there
                String base = relativePath.substring("tools/support/".length());
                File rootDir = getRootDir();
                if (rootDir != null) {
                    File file = new File(rootDir, "tools"
                            + File.separator + "base"
                            + File.separator + "files"
                            + File.separator + "typos"
                            + File.separator + base);
                    if (file.exists()) {
                        return file;
                    }
                }
                // Look in an SDK install, if found
                File home = getSdkHome();
                if (home != null) {
                    return new File(home, relativePath);
                }
            } else {
                fail("Unit tests don't support arbitrary resource lookup yet.");
            }

            return super.findResource(relativePath);
        }

        @NonNull
        @Override
        public List<File> findGlobalRuleJars() {
            // Don't pick up random custom rules in ~/.android/lint when running unit tests
            return Collections.emptyList();
        }

        public void setIncremental(File currentFile) {
            mIncrementalCheck = currentFile;
        }

        @Override
        public boolean supportsProjectResources() {
            return mIncrementalCheck != null;
        }

        @Nullable
        @Override
        public AbstractResourceRepository getProjectResources(Project project,
                boolean includeDependencies) {
            if (mIncrementalCheck == null) {
                return null;
            }

            ResourceRepository repository = new ResourceRepository(false);
            ILogger logger = new StdLogger(StdLogger.Level.INFO);
            ResourceMerger merger = new ResourceMerger(0);

            ResourceSet resourceSet = new ResourceSet(getName(), null) {
                @Override
                protected void checkItems() throws DuplicateDataException {
                    // No checking in ProjectResources; duplicates can happen, but
                    // the project resources shouldn't abort initialization
                }
            };
            // Only support 1 resource folder in test setup right now
            int size = project.getResourceFolders().size();
            assertTrue("Found " + size + " test resources folders", size <= 1);
            if (size == 1) {
                resourceSet.addSource(project.getResourceFolders().get(0));
            }
            try {
                resourceSet.loadFromFiles(logger);
                merger.addDataSet(resourceSet);
                merger.mergeData(repository.createMergeConsumer(), true);

                // Make tests stable: sort the item lists!
                Map<ResourceType, ListMultimap<String, ResourceItem>> map = repository.getItems();
                for (Map.Entry<ResourceType, ListMultimap<String, ResourceItem>> entry : map.entrySet()) {
                    Map<String, List<ResourceItem>> m = Maps.newHashMap();
                    ListMultimap<String, ResourceItem> value = entry.getValue();
                    List<List<ResourceItem>> lists = Lists.newArrayList();
                    for (Map.Entry<String, ResourceItem> e : value.entries()) {
                        String key = e.getKey();
                        ResourceItem item = e.getValue();

                        List<ResourceItem> list = m.get(key);
                        if (list == null) {
                            list = Lists.newArrayList();
                            lists.add(list);
                            m.put(key, list);
                        }
                        list.add(item);
                    }

                    for (List<ResourceItem> list : lists) {
                        Collections.sort(list, new Comparator<ResourceItem>() {
                            @Override
                            public int compare(ResourceItem o1, ResourceItem o2) {
                                return o1.getKey().compareTo(o2.getKey());
                            }
                        });
                    }

                    // Store back in list multi map in new sorted order
                    value.clear();
                    for (Map.Entry<String, List<ResourceItem>> e : m.entrySet()) {
                        String key = e.getKey();
                        List<ResourceItem> list = e.getValue();
                        for (ResourceItem item : list) {
                            value.put(key, item);
                        }
                    }
                }

                // Workaround: The repository does not insert ids from layouts! We need
                // to do that here.
                Map<ResourceType,ListMultimap<String,ResourceItem>> items = repository.getItems();
                ListMultimap<String, ResourceItem> layouts = items
                        .get(ResourceType.LAYOUT);
                if (layouts != null) {
                    for (ResourceItem item : layouts.values()) {
                        ResourceFile source = item.getSource();
                        if (source == null) {
                            continue;
                        }
                        File file = source.getFile();
                        try {
                            String xml = Files.toString(file, Charsets.UTF_8);
                            Document document = XmlUtils.parseDocumentSilently(xml, true);
                            assertNotNull(document);
                            Set<String> ids = Sets.newHashSet();
                            addIds(ids, document); // TODO: pull parser
                            if (!ids.isEmpty()) {
                                ListMultimap<String, ResourceItem> idMap =
                                        items.get(ResourceType.ID);
                                if (idMap == null) {
                                    idMap = ArrayListMultimap.create();
                                    items.put(ResourceType.ID, idMap);
                                }
                                for (String id : ids) {
                                    ResourceItem idItem = new ResourceItem(id, ResourceType.ID,
                                            null, null);
                                    String qualifiers = file.getParentFile().getName();
                                    if (qualifiers.startsWith("layout-")) {
                                        qualifiers = qualifiers.substring("layout-".length());
                                    } else if (qualifiers.equals("layout")) {
                                        qualifiers = "";
                                    }

                                    // Creating the resource file will set the source of
                                    // idItem.
                                    //noinspection ResultOfObjectAllocationIgnored
                                    ResourceFile.createSingle(file, idItem, qualifiers);
                                    idMap.put(id, idItem);
                                }
                            }
                        } catch (IOException e) {
                            fail(e.toString());
                        }
                    }
                }
            }
            catch (MergingException e) {
                fail(e.getMessage());
            }

            return repository;
        }

        private void addIds(Set<String> ids, Node node) {
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) node;
                String id = element.getAttributeNS(ANDROID_URI, ATTR_ID);
                if (id != null && !id.isEmpty()) {
                    ids.add(LintUtils.stripIdPrefix(id));
                }

                NamedNodeMap attributes = element.getAttributes();
                for (int i = 0, n = attributes.getLength(); i < n; i++) {
                    Attr attribute = (Attr) attributes.item(i);
                    String value = attribute.getValue();
                    if (value.startsWith(NEW_ID_PREFIX)) {
                        ids.add(value.substring(NEW_ID_PREFIX.length()));
                    }
                }
            }

            NodeList children = node.getChildNodes();
            for (int i = 0, n = children.getLength(); i < n; i++) {
                Node child = children.item(i);
                addIds(ids, child);
            }
        }

        @Nullable
        @Override
        public IAndroidTarget getCompileTarget(@NonNull Project project) {
            IAndroidTarget compileTarget = super.getCompileTarget(project);
            if (compileTarget == null) {
                IAndroidTarget[] targets = getTargets();
                for (int i = targets.length - 1; i >= 0; i--) {
                    IAndroidTarget target = targets[i];
                    if (target.isPlatform()) {
                        return target;
                    }
                }
            }

            return compileTarget;
        }

        @NonNull
        @Override
        public List<File> getTestSourceFolders(@NonNull Project project) {
            List<File> testSourceFolders = super.getTestSourceFolders(project);

            //List<File> tests = new ArrayList<File>();
            File tests = new File(project.getDir(), "test");
            if (tests.exists()) {
                List<File> all = Lists.newArrayList(testSourceFolders);
                all.add(tests);
                testSourceFolders = all;
            }

            return testSourceFolders;
        }
    }

    /**
     * Returns the Android source tree root dir.
     * @return the root dir or null if it couldn't be computed.
     */
    protected File getRootDir() {
        CodeSource source = getClass().getProtectionDomain().getCodeSource();
        if (source != null) {
            URL location = source.getLocation();
            try {
                File dir = SdkUtils.urlToFile(location);
                assertTrue(dir.getPath(), dir.exists());
                while (dir != null) {
                    File settingsGradle = new File(dir, "settings.gradle"); //$NON-NLS-1$
                    if (settingsGradle.exists()) {
                        return dir.getParentFile().getParentFile();
                    }
                    File lint = new File(dir, "lint");  //$NON-NLS-1$
                    if (lint.exists() && new File(lint, "cli").exists()) { //$NON-NLS-1$
                        return dir.getParentFile().getParentFile();
                    }

                    File tools = new File(dir, "tools");
                    if (tools.exists() && FileUtils.join(tools, "base", "lint", "cli").exists()) {
                        return dir;
                    }
                    dir = dir.getParentFile();
                }

                return null;
            } catch (MalformedURLException e) {
                fail(e.getLocalizedMessage());
            }
        }

        return null;
    }

    public class TestConfiguration extends DefaultConfiguration {
        protected TestConfiguration(
                @NonNull LintClient client,
                @NonNull Project project,
                @Nullable Configuration parent) {
            super(client, project, parent);
        }

        public TestConfiguration(
                @NonNull LintClient client,
                @Nullable Project project,
                @Nullable Configuration parent,
                @NonNull File configFile) {
            super(client, project, parent, configFile);
        }

        @Override
        @NonNull
        protected Severity getDefaultSeverity(@NonNull Issue issue) {
            // In unit tests, include issues that are ignored by default
            Severity severity = super.getDefaultSeverity(issue);
            if (severity == Severity.IGNORE) {
                if (issue.getDefaultSeverity() != Severity.IGNORE) {
                    return issue.getDefaultSeverity();
                }
                return Severity.WARNING;
            }
            return severity;
        }

        @Override
        public boolean isEnabled(@NonNull Issue issue) {
            return LintDetectorTest.this.isEnabled(issue);
        }

        @Override
        public void ignore(@NonNull Context context, @NonNull Issue issue,
                @Nullable Location location, @NonNull String message) {
            fail("Not supported in tests.");
        }

        @Override
        public void setSeverity(@NonNull Issue issue, @Nullable Severity severity) {
            fail("Not supported in tests.");
        }
    }
}
