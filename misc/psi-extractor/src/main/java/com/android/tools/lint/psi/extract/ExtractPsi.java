/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.tools.lint.psi.extract;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.utils.XmlUtils;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closeables;
import com.google.common.io.Files;
import org.intellij.lang.annotations.Language;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import static com.android.SdkConstants.DOT_CLASS;
import static com.android.SdkConstants.DOT_JAR;
import static com.google.common.base.Charsets.UTF_8;
import static java.io.File.separator;
import static java.io.File.separatorChar;
import static org.objectweb.asm.Opcodes.ASM5;

// TODO: Attempt to load all classes!
// Clean up method body rewriting!
public class ExtractPsi {
    public static final String GROUP_ID = "com.android.tools.external.com-intellij";
    public static final String ARTIFACT_ID = "psi-subset";

    /*
     * List of classes from the included set of packages that we want to explicitly
     * skip; typically because they're not directly related to the AST/code model
     * we want to expose to lint. When updating to new versions of PSI in the future,
     * you may find additional classes that show up in the signature files that you
     * may want to exlude here.
     */
    private static final Set<String> SKIPPED_CLASSES = Sets.newHashSet(
            "com/intellij/psi/ClassFileViewProviderFactory",
            "com/intellij/psi/JavaPsiFacade",
            "com/intellij/psi/PsiParserFacade",
            "com/intellij/psi/SmartTypePointerManager",
            "com/intellij/psi/XmlElementFactory",
            "com/intellij/psi/XmlElementFactoryImpl",
            "com/intellij/psi/FileResolveScopeProvider",
            "com/intellij/psi/FileContextProvider",
            "com/intellij/psi/FileTypeFileViewProviders",
            "com/intellij/psi/FileViewProvider",
            "com/intellij/psi/FileViewProviderFactory",
            "com/intellij/psi/LanguageFileViewProviders",
            "com/intellij/psi/PsiElementFactory",
            "com/intellij/psi/PsiDocumentManager",
            "com/intellij/psi/PsiManager",
            "com/intellij/psi/SyntaxTraverser",
            "com/intellij/psi/SmartPointerManager",
            "com/intellij/psi/SmartPsiElementPointer",
            "com/intellij/psi/SmartPsiFileRange",
            "com/intellij/psi/SmartTypePointer",
            "com/intellij/psi/SmartTypePointerManager",
            "com/intellij/psi/IdentitySmartPointer",
            "com/intellij/psi/PsiElementFinder",
            "com/intellij/psi/NonClasspathClassFinder",
            "com/intellij/psi/JVMElementFactory",
            "com/intellij/psi/JVMElementFactoryProvider",
            "com/intellij/psi/ClassTypePointerFactory",
            "com/intellij/psi/JVMElementFactory",
            "com/intellij/psi/JVMElementFactoryProvider",
            "com/intellij/psi/JVMElementFactories",
            "com/intellij/psi/PsiFileFactory",
            "com/intellij/psi/JavaDirectoryService",
            "com/intellij/psi/JspPsiUtil",
            "com/intellij/psi/RefResolveService",
            "com/intellij/psi/SdkResolveScopeProvider",
            "com/intellij/psi/AbstractReparseTestCase",
            "com/intellij/psi/LanguageInjector",
            "com/intellij/psi/InjectedLanguagePlaces",
            "com/intellij/psi/CustomHighlighterTokenType",
            "com/intellij/psi/IntentionFilterOwner",
            "com/intellij/psi/LanguageFileViewProviders",
            "com/intellij/psi/PsiReferenceContributor",
            "com/intellij/psi/PsiReferenceProvider",
            "com/intellij/psi/PsiReferenceProviderBean",
            "com/intellij/psi/PsiReferenceRegistrar",
            "com/intellij/psi/PsiReferenceService",
            "com/intellij/psi/PsiResolveHelper",
            "com/intellij/psi/ReferenceProviderType",
            "com/intellij/psi/PsiJavaCodeReferenceCodeFragment",
            "com/intellij/psi/PsiExpressionCodeFragment",
            "com/intellij/psi/JavaCodeFragment",
            "com/intellij/psi/JavaCodeFragmentFactory",
            "com/intellij/psi/PsiTypeCodeFragment",
            "com/intellij/psi/util/TypeConversionUtil",
            "com/intellij/psi/ClassFileViewProvider",
            "com/intellij/psi/CommonReferenceProviderTypes",
            "com/intellij/psi/PsiTreeChangeEvent",
            "com/intellij/psi/PsiTreeChangeAdapter",
            "com/intellij/psi/PsiTreeChangeListener",
            "com/intellij/psi/PsiDiamondTypeImpl",
            // this and various helper look useful but point to a lot of impl stuff
            "com/intellij/psi/PsiInferenceHelper",
            "com/intellij/psi/GenericsUtil",
            "com/intellij/psi/DelegatePsiTarget",
            "com/intellij/psi/RenameableDelegatePsiTarget",
            "com/intellij/psi/ManipulatableTarget",
            "com/intellij/psi/LambdaUtil",
            "com/intellij/psi/PsiMethodReferenceUtil",
            "com/intellij/psi/XmlElementVisitor",
            "com/intellij/psi/XmlRecursiveElementVisitor",
            "com/intellij/psi/XmlRecursiveElementWalkingVisitor",
            "com/intellij/psi/WeigherExtensionPoint",
            "com/intellij/psi/LanguageSubstitutor",
            "com/intellij/psi/LanguageSubstitutors",
            "com/intellij/psi/ElementManipulators",
            "com/intellij/psi/LanguageAnnotationSupport",
            "com/intellij/psi/ElementDescriptionProvider",
            "com/intellij/psi/ElementDescriptionLocation",
            "com/intellij/psi/ElementDescriptionUtil",
            "com/intellij/psi/WeighingService"
    );


    private static final Map<String, String> WIPED_METHOD_BODIES = Maps.newHashMap();
    static {
        //   (1) PsiElementVisitor#visitElement(PsiElement element) -
        //       its current implementation is just
        //       ProgressIndicatorProvider.checkCanceled();
        //       which we *don't* want (service lookup for that provider etc)
        //   (2) Remove the constructor body of PsiDisjunctionType; remove the
        //       myManager field, and the newDisjunctionType(final List<PsiType> types)
        //       method. These don't work outside of

        WIPED_METHOD_BODIES.put("createException", "com/intellij/psi/util/PsiUtilCore$NullPsiElement()Lcom/intellij/psi/PsiInvalidElementAccessException;");
        WIPED_METHOD_BODIES.put("<init>", "com/intellij/psi/PsiDisjunctionType(Ljava/util/List;Lcom/intellij/psi/PsiManager;)V");
        WIPED_METHOD_BODIES.put("visitElement", "com/intellij/psi/PsiElementVisitor(Lcom/intellij/psi/PsiElement;)V");
        // PsiTypeVisitor touches implementation stuff we don't have yet
        WIPED_METHOD_BODIES.put("visitLambdaExpressionType", "com/intellij/psi/PsiTypeVisitor(Lcom/intellij/psi/PsiLambdaExpressionType;)Ljava/lang/Object;");
        WIPED_METHOD_BODIES.put("visitMethodReferenceType", "com/intellij/psi/PsiTypeVisitor(Lcom/intellij/psi/PsiMethodReferenceType;)Ljava/lang/Object;");
        WIPED_METHOD_BODIES.put("<init>", "com/intellij/psi/PsiInvalidElementAccessException(Lcom/intellij/psi/PsiElement;)V");
        WIPED_METHOD_BODIES.put("<init>", "com/intellij/psi/PsiInvalidElementAccessException(Lcom/intellij/psi/PsiElement;Ljava/lang/String;Ljava/lang/Throwable;)V");
        WIPED_METHOD_BODIES.put("getValueIcon", "com/intellij/openapi/ui/TreeComboBox$TreeListCellRenderer(Ljava/lang/Object;I)Ljavax/swing/Icon;");

        WIPED_METHOD_BODIES.put("getLeastUpperBound", "com/intellij/psi/PsiDisjunctionType()Lcom/intellij/psi/PsiType;");
        WIPED_METHOD_BODIES.put("getCanonicalText", "com/intellij/psi/PsiReferenceBase()Ljava/lang/String;");

        // Note: we'll also rename this when visiting the class to wipe out the
        WIPED_METHOD_BODIES.put("<init>", "com/intellij/psi/PsiDisjunctionType(Ljava/util/List;Lcom/intellij/psi/PsiManager;)V");
    }

    private static final Map<String, String> SKIPPED_METHODS = Maps.newHashMap();
    static {
        SKIPPED_METHODS.put("importClass", "");
        SKIPPED_METHODS.put("getVirtualFile", "");
        SKIPPED_METHODS.put("getContainingDirectory", "");
        SKIPPED_METHODS.put("getTokenBeforeOperand", "");
        SKIPPED_METHODS.put("getHierarchicalMethodSignature", "");
        SKIPPED_METHODS.put("getSignature", "");
        SKIPPED_METHODS.put("getDeclarationScope", "");
        SKIPPED_METHODS.put("isReplaceEquivalent", "");
        SKIPPED_METHODS.put("getPsiRoots", "");
        SKIPPED_METHODS.put("getFileType", "");
        SKIPPED_METHODS.put("findElementAt", "");
        SKIPPED_METHODS.put("findReferenceAt", "");
        SKIPPED_METHODS.put("subtreeChanged", "");
        SKIPPED_METHODS.put("findSuperMethodSignaturesIncludingStatic", "");
        SKIPPED_METHODS.put("containsClassNamed", "");
        SKIPPED_METHODS.put("add", "");
        SKIPPED_METHODS.put("addBefore", "");
        SKIPPED_METHODS.put("addAfter", "");
        SKIPPED_METHODS.put("checkAdd", "");
        SKIPPED_METHODS.put("addAnnotation", "");
        SKIPPED_METHODS.put("addRange", "");
        SKIPPED_METHODS.put("addRangeBefore", "");
        SKIPPED_METHODS.put("addRangeAfter", "");
        SKIPPED_METHODS.put("copy", "");
        SKIPPED_METHODS.put("delete", "");
        SKIPPED_METHODS.put("deleteChildRange", "");
        SKIPPED_METHODS.put("checkDelete", "");
        SKIPPED_METHODS.put("checkSetName", "");
        SKIPPED_METHODS.put("checkSetModifierProperty", "");
        SKIPPED_METHODS.put("replace", "");
        SKIPPED_METHODS.put("getCopyableUserData", "");
        SKIPPED_METHODS.put("putCopyableUserData", "");
        SKIPPED_METHODS.put("processDeclarations", "");
        SKIPPED_METHODS.put("processChildren", "");
        SKIPPED_METHODS.put("processVariants", "");
        SKIPPED_METHODS.put("advancedResolve", "");
        SKIPPED_METHODS.put("multiResolve", "");
        SKIPPED_METHODS.put("normalizeDeclaration", "");
        SKIPPED_METHODS.put("handleElementRename", "");
        SKIPPED_METHODS.put("handleQualifiedNameChange", "");
        SKIPPED_METHODS.put("occursInPackagePrefixes", "");
        SKIPPED_METHODS.put("getProject", "");
        SKIPPED_METHODS.put("isReferenceTo", "");
        SKIPPED_METHODS.put("isEquivalentTo", "");
        SKIPPED_METHODS.put("isConvertibleFrom", "(Lcom/intellij/psi/PsiType;)Z");
        SKIPPED_METHODS.put("getTypeByName", "(Ljava/lang/String;Lcom/intellij/openapi/project/Project;Lcom/intellij/psi/search/GlobalSearchScope;)Lcom/intellij/psi/PsiClassType;");
        SKIPPED_METHODS.put("getUseScope", "");
        SKIPPED_METHODS.put("getResolveScope", "");
        SKIPPED_METHODS.put("getVariants", "");
        SKIPPED_METHODS.put("equals", "com/intellij/psi/PsiClassType");
        SKIPPED_METHODS.put("getBoxedType", "com/intellij/psi/PsiPrimitiveType");
        SKIPPED_METHODS.put("getViewProvider", "");
        SKIPPED_METHODS.put("visitCodeFragment", "(Lcom/intellij/psi/JavaCodeFragment;)V");
        SKIPPED_METHODS.put("hasNonTrivialParameters", "com/intellij/psi/PsiClassType()Z");
        SKIPPED_METHODS.put("containsClassNamed", "com/intellij/psi/PsiPackage(Ljava/lang/String;)Z");
        SKIPPED_METHODS.put("treeWalkUp", "com/intellij/psi/util/PsiTreeUtil(Lcom/intellij/psi/scope/PsiScopeProcessor;Lcom/intellij/psi/PsiElement;Lcom/intellij/psi/PsiElement;Lcom/intellij/psi/ResolveState;)Z");
        SKIPPED_METHODS.put("mark", "com/intellij/psi/util/PsiTreeUtil(Lcom/intellij/psi/PsiElement;Ljava/lang/Object;)V");
        SKIPPED_METHODS.put("findElementOfClassAtOffsetWithStopSet", "com/intellij/psi/util/PsiTreeUtil(Lcom/intellij/psi/PsiFile;ILjava/lang/Class;Z[Ljava/lang/Class;)Lcom/intellij/psi/PsiElement;");
        SKIPPED_METHODS.put("releaseMark", "com/intellij/psi/util/PsiTreeUtil(Lcom/intellij/psi/PsiElement;Ljava/lang/Object;)Lcom/intellij/psi/PsiElement;");
        SKIPPED_METHODS.put("decodeIndices", "com/intellij/psi/util/PsiTreeUtil(Lcom/intellij/psi/PsiElement;[Lcom/intellij/psi/PsiElement;)V");
        SKIPPED_METHODS.put("copyElements", "com/intellij/psi/util/PsiTreeUtil([Lcom/intellij/psi/PsiElement;)[Lcom/intellij/psi/PsiElement;");
        SKIPPED_METHODS.put("getInjectedElements", "com/intellij/psi/util/PsiTreeUtil(Lcom/intellij/psi/templateLanguages/OuterLanguageElement;)Ljava/util/List;");
        SKIPPED_METHODS.put("findElementOfClassAtOffset", "com/intellij/psi/util/PsiTreeUtil");
        SKIPPED_METHODS.put("findElementOfClassAtOffsetWithStopSet", "com/intellij/psi/util/PsiTreeUtil");
        SKIPPED_METHODS.put("findElementOfClassAtRange", "com/intellij/psi/util/PsiTreeUtil");
        SKIPPED_METHODS.put("isAssignableFrom", "com/intellij/psi/PsiType");
        SKIPPED_METHODS.put("isConvertibleFrom", "com/intellij/psi/PsiType");
        SKIPPED_METHODS.put("getDirectories", "com/intellij/psi/PsiDirectoryContainer");
        SKIPPED_METHODS.put("getFiles", "com/intellij/psi/PsiPackage(Lcom/intellij/psi/search/GlobalSearchScope;)[Lcom/intellij/psi/PsiFile;");
        SKIPPED_METHODS.put("findClassByShortName", "com/intellij/psi/PsiPackage(Ljava/lang/String;Lcom/intellij/psi/search/GlobalSearchScope;)[Lcom/intellij/psi/PsiClass;");
        SKIPPED_METHODS.put("getForcedResolveScope", "com/intellij/psi/PsiCodeFragment()Lcom/intellij/psi/search/GlobalSearchScope;");
        SKIPPED_METHODS.put("forceResolveScope", "com/intellij/psi/PsiCodeFragment(Lcom/intellij/psi/search/GlobalSearchScope;)V");
        SKIPPED_METHODS.put("getManipulator", "com/intellij/psi/PsiReferenceBase()Lcom/intellij/psi/ElementManipulator;");
        SKIPPED_METHODS.put("calculateDefaultRangeInElement", "com/intellij/psi/PsiReferenceBase()Lcom/intellij/openapi/util/TextRange;");
        SKIPPED_METHODS.put("getRangeInElement", "com/intellij/psi/PsiReferenceBase()Lcom/intellij/openapi/util/TextRange;");
        SKIPPED_METHODS.put("getValue", "com/intellij/psi/PsiReferenceBase()Ljava/lang/String;");
        // Some additional methods with potentially generic sounding names, such as
        // getManager(), are specially filtered in isSkippedPsiMethod by also checking
        // the return type etc
    }

    private static final Set<String> REQUIRED_RESOURCES = Sets.newHashSet(
            // We have to include some resources from the IDE. I should prune this...
            "messages/JavaCoreBundle.properties"
    );

    private final File mInstalledIde;
    private final File mSourceDir;
    private final String mVersion;

    private final Map<String, ClassNode> mClassNodeMap = Maps.newHashMapWithExpectedSize(5000);

    private final Map<String, byte[]> mResourceMap = Maps.newHashMapWithExpectedSize(100);

    private Map<String, CgClass> mClassMap;

    private final Map<FieldNode, CgField> mFieldMap;

    private final Map<MethodNode, CgMethod> mMethodMap;

    public static final int MIN_MB = 2048;


    public static void main(String[] args) {
        try {
            if (Runtime.getRuntime().maxMemory() < MIN_MB/2*1024*1024L) {
                System.err.println("Note: This analysis requires a lot of memory so make sure "
                        + "you bump up the available space with this JVM flag: -Xmx" + MIN_MB + "m");
                System.exit(-1);
            }

            if (args.length != 2) {
                System.err.println("Usage: " + ExtractPsi.class.getSimpleName()
                        + " <studio-installation-root> <studio-source-tree>\n" +
                        "studio-installation-root: directory with an install of Android Studio,\n" +
                        "                          such as /Applications/Android Studio.app\n" +
                        "     studio-source-tree: repository checkout of Android Studio source code");
                System.exit(-1);
            }
            File studioDir = new File(args[0]);
            File sourceDir = new File(args[1]);
            if (!studioDir.exists()) {
                System.err.println(studioDir + " does not exist");
                System.exit(-1);
            }
            if (!sourceDir.exists()) {
                System.err.println(sourceDir + " does not exist");
                System.exit(-1);
            }
            File toolsDir = new File(sourceDir, "tools");
            if (!(toolsDir.isDirectory())) {
                System.out.println(sourceDir + " does not look like a Studio source tree; expected to find "
                        + toolsDir);
                System.exit(-1);
            }
            File content = new File(studioDir, "Contents");
            if (content.isDirectory()) {
                studioDir = content;
            }
            File lib = new File(studioDir, "lib");
            if (!(lib.isDirectory())) {
                System.out.println(studioDir + " does not look like a Studio installation; expected to find " + lib);
                System.exit(-1);
            }

            String version = findVersionTag(lib);

            ExtractPsi extractor = new ExtractPsi(studioDir, sourceDir, version);
            extractor.perform();
        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(-1);
        }
    }

    // Derive the version by looking up
    //   lilb/resources.jar and reading in idea/AndroidStudioApplicationInfo.xml,
    //   and then reading in component/build:apiVersion and stripping the AI- prefix
    //
    // This currently computes to "143.1821.5".
    private static String findVersionTag(File lib) {
        File resources = new File(lib, "resources.jar");
        if (!resources.isFile()) {
            System.err.println(resources + " is not a file");
        }

        try {
            String fileUrl = resources.toURI().toURL().toExternalForm();
            URL url = new URL("jar:" + fileUrl + "!/idea/AndroidStudioApplicationInfo.xml");
            InputStream stream = url.openStream();
            assert stream != null;
            byte[] bytes = ByteStreams.toByteArray(stream);
            String xml = new String(bytes, Charsets.UTF_8);
            Document document = XmlUtils.parseDocumentSilently(xml, false);
            assert document != null;
            NodeList childNodes = document.getDocumentElement().getChildNodes();
            for (int i = 0, n = childNodes.getLength(); i < n; i++) {
                Node node = childNodes.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE && "build".equals(node.getNodeName())) {
                    Element element = (Element)node;
                    String apiVersion = element.getAttribute("apiVersion");
                    assert apiVersion.startsWith("AI-") : apiVersion;
                    return apiVersion.substring(3);
                }
            }
        } catch (Throwable e) {
            System.err.println("Failed to read/parse tag version: " + e);
        }

        System.err.println("Failed to find IDEA version number to use");
        return null;
    }

    public ExtractPsi(@NonNull File installedIde, @NonNull File sourceDir, @NonNull String version) {
        mInstalledIde = installedIde;
        mSourceDir = sourceDir;
        mVersion = version;

        mClassMap = Maps.newHashMapWithExpectedSize(2000);
        mFieldMap = Maps.newHashMapWithExpectedSize(1000);
        mMethodMap = Maps.newHashMapWithExpectedSize(1000);
    }

    public void perform() {
        System.out.println("Performing analysis...");

        try {
            List<File> jars = findJars();

            // Read all into class maps
            readClasses(jars);
            System.out.println("Analyzing class graph for " + mClassNodeMap.values().size() + " classes");
        } catch (IOException ioe) {
            System.err.println("Couldn't read in classes: " + ioe.getMessage());
            System.exit(-1);
        }

        analyzeCallGraph();

        try {
            writeSignatureFile();
            writeMavenArtifact();
        } catch (IOException ioe) {
            System.err.println("Couldn't write artifact: " + ioe.getMessage());
            System.exit(-1);
        }
    }

    private void writeSignatureFile() {
        String report = recordSignatures();
        try {
            File file = new File(mSourceDir, ("tools/base/misc/psi-extractor/src/main/java/com/android/" +
                    "tools/lint/psi/extract/signatures.txt").replace('/', separatorChar));
            if (!file.exists()) {
                file = File.createTempFile("report", ".txt");
            }
            Files.write(report, file, UTF_8);
            System.out.println("Recorded API map in " + file);
        } catch (IOException ignore) {
        }
    }

    private static void writeCheckSumFiles(@NonNull File file) throws IOException {
        byte[] bytes = Files.toByteArray(file);
        Files.write(Hashing.md5().hashBytes(bytes).toString(),
                new File(file.getPath() + ".md5"), UTF_8);
        Files.write(Hashing.sha1().hashBytes(bytes).toString(),
                new File(file.getPath() + ".sha1"), UTF_8);
    }

    private void writeMavenArtifact() throws IOException {
        // Write artifact
        File maven = new File(mSourceDir, "prebuilts/tools/common/m2/repository".replace('/', separatorChar));
        File artifactDir = new File(maven, GROUP_ID.replace('.', separatorChar) +
                separator + ARTIFACT_ID + separator + mVersion);
        if (!artifactDir.exists()) {
            boolean ok = artifactDir.mkdirs();
            if (!ok) {
                System.err.println("Couldn't create artifact directory " + artifactDir);
                System.exit(-1);
            }
        }
        File metadaDir = artifactDir.getParentFile();

        // Write NOTICE file
        File license = new File(mInstalledIde, "LICENSE.txt");
        if (license.exists()) {
            try {
                Files.copy(license, new File(artifactDir, "NOTICE"));
            } catch (IOException e) {
                System.err.println("Couldn't copy license file " + license);
                System.exit(-1);
            }
        }

        String currentTime = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        @Language("XML")
        String mavenMetadata = ""
                + "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<metadata>\n"
                + "  <groupId>" + GROUP_ID + "</groupId>\n"
                + "  <artifactId>" + ARTIFACT_ID + "</artifactId>\n"
                + "  <versioning>\n"
                + "    <latest>" + mVersion + "</latest>\n"
                + "    <release>" + mVersion + "</release>\n"
                + "    <versions>\n"
                + "      <version>" + mVersion + "</version>\n"
                + "    </versions>\n"
                + "    <lastUpdated>" + currentTime + "</lastUpdated>\n"
                + "  </versioning>\n"
                + "</metadata>";
        File mavenMetadataFile = new File(metadaDir, "maven-metadata.xml");
        Files.write(mavenMetadata, mavenMetadataFile, UTF_8);
        writeCheckSumFiles(mavenMetadataFile);

        String baseName = ARTIFACT_ID + "-" + mVersion;

        @Language("XML")
        String pom = ""
                + "<project xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd\" xmlns:xsi=\"http\\\n"
                + "://www.w3.org/2001/XMLSchema-instance\" xmlns=\"http://maven.apache.org/POM/4.0.0\">\n"
                + "    <modelVersion>4.0.0</modelVersion>\n"
                + "    <groupId>" + GROUP_ID + "</groupId>\n"
                + "    <artifactId>" + ARTIFACT_ID + "</artifactId>\n"
                + "    <version>" + mVersion + "</version>\n"
                + "    <packaging>jar</packaging>\n"
                + "    <name>" + ARTIFACT_ID + "</name>\n"
                + "    <url>http://www.jetbrains.com/idea</url>\n"
                + "    <description>A subset of IntelliJ IDEA's PSI APIs and implementation,\n"
                + "    focusing on the read-only aspects, intended for use by Android Lint\n"
                + "    when running outside of the IDE (typically from Gradle.)\n"
                + "    </description>\n"
                + "    <licenses>\n"
                + "      <license>\n"
                + "        <name>Apache License, Version 2.0</name>\n"
                + "        <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>\n"
                + "      </license>\n"
                + "    </licenses>\n"
                + "</project>";
        File pomFile = new File(artifactDir, baseName + ".pom");
        Files.write(pom, pomFile, UTF_8);
        writeCheckSumFiles(pomFile);

        File jarFile = new File(artifactDir, baseName + DOT_JAR);
        writeJar(jarFile);
        writeCheckSumFiles(jarFile);

        testJar(jarFile);

        System.out.println("Wrote artifact " + GROUP_ID + ":" + ARTIFACT_ID + ":" + mVersion + " to \n" +
                artifactDir);
    }

    private void readClasses(@NonNull List<File> jars) throws IOException {
        for (File jar : jars) {
            JarInputStream zis = null;
            try {
                FileInputStream fis = new FileInputStream(jar);
                try {
                    zis = new JarInputStream(fis);
                    ZipEntry entry = zis.getNextEntry();
                    while (entry != null) {
                        boolean directory = entry.isDirectory();
                        String name = entry.getName();
                        if (!directory && name.endsWith(DOT_CLASS)) {
                            byte[] bytes = ByteStreams.toByteArray(zis);
                            if (bytes != null) {
                                ClassReader reader;
                                ClassNode classNode;
                                reader = new ClassReader(bytes);
                                classNode = new ClassNode();
                                reader.accept(classNode, 0 /* flags */);
                                // Classes shouldn't have been duplicated
                                //assert !mClassNodeMap.containsKey(name) : name;
                                mClassNodeMap.put(name, classNode);
                            }
                        } else if (!directory) {
                            byte[] bytes = ByteStreams.toByteArray(zis);
                            if (bytes != null) {
                                mResourceMap.put(name, bytes);
                            }
                        }
                        entry = zis.getNextEntry();
                    }
                } finally {
                    Closeables.close(fis, true);
                }
            } finally {
                Closeables.close(zis, false);
            }
        }
    }

    @NonNull
    private List<File> findJars() {
        List<File> jars = Lists.newArrayList();
        File root = mInstalledIde;
        File libs = new File(root, "lib");
        jars.add(new File(libs, "openapi.jar"));
        jars.add(new File(libs, "util.jar"));
        jars.add(new File(libs, "trove4j.jar"));
        jars.add(new File(libs, "annotations.jar"));

// Later: consider including *all* jars in the tree in the call graph analysis.
//        File[] files = libs.listFiles();
//        if (files != null) {
//            for (File file : files) {
//                if (file.getPath().endsWith(DOT_JAR)) {
//                    jars.add(file);
//                }
//            }
//        }

        return jars;
    }

    private void analyzeCallGraph() {
        Collection<ClassNode> classNodes = mClassNodeMap.values();
        for (ClassNode classNode : classNodes) {
            mClassMap.put(classNode.name, new CgClass(classNode));
        }

        for (CgClass clazz : mClassMap.values()) {
            clazz.recordDependencies();
        }

        applyPsiReachability();

        // First go and figure out which classes are *unreachable* by applying blacklisted
        // elements
        ArrayList<CgClass> classes = Lists.newArrayList(mClassMap.values());
        for (CgClass clazz : classes) {
            if (clazz.isUnreachable()) {
                clazz.visitUnreachable();
            }
        }

        for (CgClass clazz : classes) {
            if (clazz.isReachable()) {
                clazz.visitReachable();
            }
        }

        // Any methods overriding an inherited method that is kept should also
        // be kept. Similarly, if a subclass method is reached, so should the
        // super class method!
        for (CgClass clazz : classes) {
            // First compute superclasses
            for (CgMethod method : clazz.mMethods.values()) {
                List<CgMethod> superMethods = method.getSuperMethods();
                for (CgMethod superMethod : superMethods) {
                    superMethod.addSubMethod(method);
                }
            }
        }

        while (true) {
            // Keep flowing values until we're not making progress
            mAddedReachableCount = 0;
            // Next apply reachability across the chains
            for (CgClass clazz : classes) {
                for (CgMethod method : clazz.mMethods.values()) {
                    if (method.isSkipped()) {
                        continue;
                    }
                    if ((method.isSubMethodReachable() ||
                            (method.mContainingClass.isReachable()
                                    && method.isSuperMethodReachable()))) {
                        method.markSuperMethodsReachable(true);
                        method.markSubMethodsReachable(true);
                    } else if (/*method.mContainingClass.isReachable()
                            &&*/ method.mContainingClass.extendsJdk()) {
                        method.markSuperMethodsReachable(true);
                        method.markSubMethodsReachable(true);
                    }
                }
            }
            // Now we need to revisit in case there are further changes
            for (CgClass clazz : classes) {
                if (clazz.isReachable()) {
                    clazz.visitReachable();
                }
            }

            if (mAddedReachableCount == 0) {
                break;
            }
        }

        // Don't want to have to put icons on each PsiElement
        CgClass disjunctionType = findClass("com/intellij/psi/PsiDisjunctionType");
        assert disjunctionType != null;
        // Mark DisjunctionType as abstract such that we can instantiate a subclass of it
        // Ensure that we include the constructor, even though it references PsiManager; we'll
        // wipe out that parameter in the bytecode writer (and we've already wiped its instruction
        // list)
        disjunctionType.mMethods.get("<init>(Ljava/util/List;Lcom/intellij/psi/PsiManager;)").markReachable(true, false, true);

        CgClass iconable = findClass("com/intellij/openapi/util/Iconable");
        assert iconable != null && !iconable.mMethods.get("getIcon(I)").isReachable();

        for (CgClass clazz : mClassMap.values()) {
            if (clazz.isReachable()) {
                clazz.pruneIncoming();
            }
        }

        // Resources
        Collection<String> resourceNames = Lists.newArrayList(mResourceMap.keySet());
        for (String name : resourceNames) {
            if (!REQUIRED_RESOURCES.contains(name)) {
                mResourceMap.remove(name);
            }
        }
    }

    private String recordSignatures() {
        StringBuilder sb = new StringBuilder();
        List<CgClass> sortedClasses = Lists.newArrayList();
        for (CgClass clazz : mClassMap.values()) {
            if (clazz.isReachable()) {
                sortedClasses.add(clazz);
            }
        }
        Collections.sort(sortedClasses);
        for (CgClass cls : sortedClasses) {
            List<CgMethod> sortedMethods = Lists.newArrayList();
            for (CgMethod method : cls.mMethods.values()) {
                if (method.isReachable()) {
                    sortedMethods.add(method);
                }
            }
            Collections.sort(sortedMethods);

            List<CgField> sortedFields = Lists.newArrayList();
            for (CgField field : cls.mFields.values()) {
                if (field.isReachable()) {
                    sortedFields.add(field);
                }
            }
            Collections.sort(sortedFields);

            sb.append(cls.getName()).append("\n");
            for (CgField field : sortedFields) {
                sb.append("    ").append(field.mFieldNode.name).append("\n");
            }
            for (CgMethod method : sortedMethods) {
                sb.append("    ").append(method.mMethodNode.name).append(method.mMethodNode.desc).append("\n");
            }
        }

        return sb.toString();
    }

    public void writeJar(File dest) throws IOException {
        if (dest.exists()) {
            boolean deleted = dest.delete();
            if (!deleted) {
                throw new IOException("Could not delete " + dest);
            }
        }

        FileOutputStream fos = new FileOutputStream(dest);
        JarOutputStream zos = new JarOutputStream(fos);
        try {
            for (CgClass clazz : mClassMap.values()) {
                // TODO: Sort classes in the jar file?
                if (clazz.isReachable()) {
                    byte[] bytes = computeClass(clazz);
                    if (bytes != null) {
                        String name = clazz.getName() + DOT_CLASS;
                        JarEntry outEntry = new JarEntry(name);
                        zos.putNextEntry(outEntry);
                        zos.write(bytes);
                        zos.closeEntry();
                    }
                }
            }

            // Resources
            for (String name : mResourceMap.keySet()) {
                byte[] bytes = mResourceMap.get(name);
                JarEntry outEntry = new JarEntry(name);
                zos.putNextEntry(outEntry);
                zos.write(bytes);
                zos.closeEntry();
            }

            zos.flush();
        } finally {
            Closeables.close(zos, false);
        }
    }

    private void testJar(File jar) {
        try {
            URL url = jar.toURI().toURL();
            URLClassLoader loader = new URLClassLoader(new URL[] { url }, getClass().getClassLoader());

            List<CgClass> verify = Lists.newArrayList();
            //verify.addAll(mClassMap.values());
            //Collections.sort(verify);
            verify.add(findClass("com/intellij/pom/java/LanguageLevel"));
            verify.add(findClass("com/intellij/psi/PsiClass"));
            verify.add(findClass("com/intellij/util/containers/ConcurrentWeakValueIntObjectHashMap"));
            // Check all reachable classes in the PSI package
            for (CgClass cls : mClassMap.values()) {
                if (cls.isReachable() && cls.getName().startsWith("com/intellij/psi/") &&
                        cls.getName().indexOf('/', "com/intellij/psi/".length()) == -1) {
                    verify.add(cls);
                }
            }

            for (CgClass clazz : verify) {
                if (clazz.isReachable()) {
                    //noinspection CaughtExceptionImmediatelyRethrown
                    try {
                        String name = clazz.getName();
                        name = name.replace('/', '.');
                        Class<?> aClass = loader.loadClass(name);
                        aClass.getMethods();
                        aClass.getFields();
                    } catch (NoClassDefFoundError e) {
                        System.err.println("While loading " + clazz.getName() + ": " + e);
                        //throw e;
                    } catch (VerifyError e) {
                        System.err.println("While verifying " + clazz.getName() + ": " + e);
                        //throw e;
                    }
                }
            }
        } catch (Throwable t) {
            System.err.println("Invalid classes found in the generated jar: " + t);
            System.exit(-1);
        }
    }

    public byte[] computeClass(final CgClass clazz) throws IOException {
        assert clazz.isReachable();
        ClassWriter classWriter = new ClassWriter(ASM5);
        ClassVisitor classVisitor = new ClassVisitor(ASM5, classWriter) {
            @Override
            public void visitInnerClass(String name, String outerName, String innerName,
                                        int access) {
                CgClass clz = findClass(name);
                if (clz != null && clz.isReachable()) {
                    super.visitInnerClass(name, outerName, innerName, access);
                }
            }

            @Override
            public void visit(int version, int access, String name, String signature,
                              String superName,
                              String[] interfaces) {
                // TODO: Filter out interfaces if they're not used?
                super.visit(version, access, name, signature, superName, interfaces);
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String signature,
                                             String[] exceptions) {
                // Special cases: strip out method bodies for
                //   (1) PsiElementVisitor#visitElement(PsiElement element) -
                //       its current implementation is just
                //       ProgressIndicatorProvider.checkCanceled();
                //       which we *don't* want (service lookup for that provider etc)
                //   (2) Remove the constructor body of PsiDisjunctionType; remove the
                //       myManager field, and the newDisjunctionType(final List<PsiType> types)
                //       method. These don't work outside of

                // TODO: What about inner classes? Here the class name won't be right!
                CgMethod method = findMethod(clazz.getName(), name, desc);
                if (method != null && method.isReachable()) {
                    if (method.toString().equals("com/intellij/psi/PsiDisjunctionType#<init>(Ljava/util/List;Lcom/intellij/psi/PsiManager;)V")) {
                        // Strip off the PsiManager parameter. The method body referencing it
                        // has already been wiped out.
                        desc = "(Ljava/util/List;)V";
                        signature = "(Ljava/util/List<Lcom/intellij/psi/PsiType;>;)V";
                        method.mMethodNode.invisibleParameterAnnotations = null;
                        ArrayList<Object> parameters = new ArrayList<Object>();
                        parameters.add(method.mMethodNode.localVariables.get(0));
                        parameters.add(method.mMethodNode.localVariables.get(1));
                        method.mMethodNode.localVariables = parameters;
                    }

                    return super.visitMethod(access, name, desc, signature, exceptions);
                }

                return null;
            }

            @Override
            public FieldVisitor visitField(int access, String name, String desc, String signature,
                                           Object value) {
                CgField method = findField(clazz.getName(), name);
                if (method != null && method.isReachable()) {
                    return super.visitField(access, name, desc, signature, value);
                }

                return null;
            }
        };
        clazz.mClassNode.accept(classVisitor);
        return classWriter.toByteArray();
    }

    public void applyPsiReachability() {
        String pkgName = "com/intellij/psi/";
        for (CgClass clazz : mClassMap.values()) {
            String owner = clazz.getName();
            if (owner.startsWith(pkgName) && owner.indexOf('/', pkgName.length()) == -1) {
                if (clazz.isSkipped()) {
                    clazz.markReachable(false, false, false);
                    continue;
                }
                clazz.markReachable(true, true, false);
            }
        }

        pkgName = "com/intellij/psi/util/";
        for (CgClass clazz : mClassMap.values()) {
            String name = clazz.getName();
            if (name.startsWith(pkgName) && name.indexOf('/', pkgName.length()) == -1) {
                // Here we're only white-listing PsiTreeUtil for now
                if (name.equals("com/intellij/psi/util/PsiTreeUtil")) {
                    clazz.markReachable(true, true, false);
                }
            }
        }

        // Include everything in javadoc
        pkgName = "com/intellij/psi/javadoc/";
        for (CgClass clazz : mClassMap.values()) {
            String name = clazz.getName();
            if (name.startsWith(pkgName) && name.indexOf('/', pkgName.length()) == -1) {
                clazz.markReachable(true, true, false);
            }
        }

        // We also need TextRange
        CgClass clazz = findClass("com/intellij/openapi/util/TextRange");
        assert clazz != null;
        clazz.markReachable(true, true, false);

        // And Language Level constants
        clazz = findClass("com/intellij/pom/java/LanguageLevel");
        assert clazz != null;
        clazz.markReachable(true, true, false);

        // ConcurrentHashMap performs some crazy reflection to access fields; whitelist these
        clazz = findClass("com/intellij/util/containers/ConcurrentHashMap");
        assert clazz != null;
        clazz.markReachable(true, true, false);
        clazz = findClass("com/intellij/util/containers/ConcurrentLongObjectHashMap");
        assert clazz != null;
        clazz.markReachable(true, true, false);
        clazz = findClass("com/intellij/util/containers/ConcurrentIntObjectHashMap");
        assert clazz != null;
        clazz.markReachable(true, true, false);
        clazz = findClass("com/intellij/util/containers/ConcurrentWeakValueIntObjectHashMap");
        assert clazz != null;
        clazz.markReachable(true, true, false);
    }

    @Nullable
    private CgClass findClass(@NonNull String owner) {
        return mClassMap.get(owner);
    }

    @Nullable
    private CgField findField(@NonNull String owner, @NonNull String name) {
        CgClass clazz = mClassMap.get(owner);
        if (clazz != null) {
            CgField field = clazz.mFields.get(name);
            if (field != null) {
                return field;
            }
        }
        return null;
    }

    @NonNull
    private static String getMethodHandle(@NonNull String name, @NonNull String desc) {
        StringBuilder sb = new StringBuilder(name.length() + desc.length());
        sb.append(name);
        for (int i = 0, n = desc.length(); i < n; i++) {
            char c = desc.charAt(i);
            sb.append(c);
            if (c == ')') {
                break;
            }
        }
        return sb.toString();
    }

    @Nullable
    private CgMethod findMethod(@NonNull String owner, @NonNull String name, @NonNull String desc) {
        CgClass clazz = mClassMap.get(owner);
        if (clazz != null) {
            CgMethod method = clazz.mMethods.get(getMethodHandle(name, desc));
            if (method != null) {
                return method;
            }
        }

        return null;
    }

    /** Class Graph node */
    private abstract class CgNode {
        /** TRUE: Yes, FALSE: No, null: Not yet considered */
        public Boolean reachable;

        protected final List<CgNode> mIncoming = Lists.newArrayList();

        protected final List<CgNode> mOutgoing = Lists.newArrayList();

        @Override
        public abstract String toString();

        protected void addDependency(CgNode to) {
            if (to == this) {
                // No need to point to "self"
                return;
            }
            mOutgoing.add(to);
            to.mIncoming.add(this);
        }

        public boolean isReachable() {
            return reachable == Boolean.TRUE;
        }

        public boolean isUnreachable() {
            // Not the same as !isReachable - null means "not yet visited"
            return reachable == Boolean.FALSE;
        }

        //private abstract boolean isSkipped();

        protected void visitReachable() {
            assert isReachable(); // should only be called on reachable nodes
            for (CgNode node : mOutgoing) {
                if (node.reachable == null) {
                    if (!node.isSkipped()) {
                        node.markReachable(true, false, true);
                        node.visitReachable();
                    }
                }

                if (node.reachable == Boolean.FALSE ||
                        node instanceof Member
                                && ((Member)node).mContainingClass.reachable == Boolean.FALSE) {
                    // Uh oh - reachable code calls code deliberately marked as non-reachable
                    // We need to go and strip out
                    String message = "Reachable code path (" + this + ") points "
                            + "to blacklisted code (" + node + ")";
                    if (isWipedMethod()) {
                        // It's okay to call a method where we've removed the implementation
                        return;
                    }

                    if (message.equals("Reachable code path (com/intellij/psi/util/PsiTreeUtil#getParentOfType(Lcom/intellij/psi/PsiElement;Ljava/lang/Class;ZI)Lcom/intellij/psi/PsiElement;) points to blacklisted code (com/intellij/psi/PsiElement#getNode()Lcom/intellij/lang/ASTNode;)")) {
                        // Deliberately whitelisted: the getNode() path is usually not
                        // called (but I should consider rewriting it to remove it instead)
                        return;
                    }
                    throw new IllegalStateException(message);
                }
            }
        }

        protected boolean addTypeDependency(@Nullable Type type) {
            if (type == null) {
                return false;
            }
            if (type.getSort() == Type.ARRAY) {
                type = type.getElementType();
            }
            if (type.getSort() != Type.OBJECT) {
                return false;
            }
            String internalName = type.getInternalName();
            if (internalName.indexOf('/') != -1) { // object type?
                CgClass clz = findClass(internalName);
                if (clz != null) {
                    addDependency(clz);
                    return true;
                }
            }

            return false;
        }

        protected void addAnnotationDependency(@Nullable List list) {
            @SuppressWarnings("unchecked")
            List<AnnotationNode> annotations = (List<AnnotationNode>) list;
            if (annotations != null) {
                for (AnnotationNode annotation : annotations) {
                    addAnnotationDependency(annotation);
                }
            }
        }

        public abstract boolean isSkipped();

        public boolean isWipedMethod() {
            return false;
        }

        protected void addAnnotationDependency(AnnotationNode annotation) {
            addTypeDependency(Type.getType(annotation.desc));

            if (annotation.values != null) {
                for (Object o : annotation.values) {
                    if (o instanceof List) {
                        for (Object o2 : (List)o) {
                            if (o2 instanceof String[]) {
                                String[] strings = (String[])o2;
                                if (strings.length == 2 && strings[0].startsWith("L") && strings[0].endsWith(";")) {
                                    addTypeDependency(Type.getType(strings[0]));
                                }
                            }
                        }
                    } else if (o instanceof String[]) {
                        String[] strings = (String[])o;
                        if (strings.length == 2 && strings[0].startsWith("L") && strings[0].endsWith(";")) {
                            addTypeDependency(Type.getType(strings[0]));
                        }
                    }
                }
            }
        }

        protected void visitUnreachable() {
            assert isUnreachable(); // should only be called on reachable nodes
            for (CgNode node : mIncoming) {
                if (node.reachable == null) {
                    node.markReachable(false, false, false);
                    node.visitUnreachable();
                }

                if (node.reachable == Boolean.TRUE) {
                    // Uh oh - unreachable code calls code deliberately marked as reachable
                    // We need to go and figure out how to resolve this!
                    String message = "Reachable code " + node + " points to blacklisted code "
                            + this;
                    if (node.isWipedMethod()) {
                        return;
                    }
                    throw new IllegalStateException(message);
                }
            }
        }

        // Removes edges that aren't from reachable nodes
        void pruneIncoming() {
            ListIterator<CgNode> iterator = mIncoming.listIterator();
            while (iterator.hasNext()) {
                CgNode edge = iterator.next();
                if (edge.reachable == null) {
                    iterator.remove();
                }
            }
        }

        public void markReachable(
                boolean reachable,
                boolean includeChildren,
                boolean includeParents) {
            if (reachable && this.reachable == null) {
                mAddedReachableCount++;
            }
            this.reachable = reachable;
        }
    }

    private int mAddedReachableCount;

    private abstract class Member extends CgNode {
        protected final CgClass mContainingClass;

        public Member(CgClass containingClass) {
            mContainingClass = containingClass;
        }

        @Override
        public void markReachable(
                boolean reachable,
                boolean includeChildren,
                boolean includeParents) {
            super.markReachable(reachable, includeChildren, includeParents);

            // If a member is reachable, so is its parent class
            if (reachable && includeParents) {
                mContainingClass.markReachable(true, false, true);
            }
        }
    }

    private class CgField extends Member implements Comparable<CgField> {
        private final FieldNode mFieldNode;

        public CgField(CgClass clazz, FieldNode fieldNode) {
            super(clazz);
            mFieldNode = fieldNode;
            mFieldMap.put(fieldNode, this);
        }

        @Override
        public void markReachable(boolean reachable, boolean includeChildren,
                                  boolean includeParents) {
            assert !reachable || !isSkipped() : this;
            super.markReachable(reachable, includeChildren, includeParents);
        }

        public void recordDependencies() {
            if (mFieldNode.signature != null) {
                new SignatureReader(mFieldNode.signature).accept(new SignatureVisitor(ASM5) {
                    @Override
                    public void visitClassType(String name) {
                        CgClass clazz = findClass(name);
                        if (clazz != null) {
                            addDependency(clazz);
                        }
                        super.visitClassType(name);
                    }
                });
            }
            String desc = mFieldNode.desc;
            addTypeDependency(Type.getReturnType(desc));

            addAnnotationDependency(mFieldNode.visibleTypeAnnotations);
            addAnnotationDependency(mFieldNode.invisibleTypeAnnotations);
            addAnnotationDependency(mFieldNode.visibleAnnotations);
            addAnnotationDependency(mFieldNode.invisibleAnnotations);
        }

        @Override
        public boolean isSkipped() {
            String name = mFieldNode.name;
            String desc = mFieldNode.desc;
            String clsOwner = mContainingClass.getName();

            // These types contain a LOT of implementation gunk; remove them for now
            // (except the accept method required for external visitor calls)
            // startsWith instead of equals: include inner classes too
            if (clsOwner.startsWith("com/intellij/psi/PsiWildcardType") ||
                    clsOwner.startsWith("com/intellij/psi/PsiIntersectionType") ||
                    clsOwner.startsWith("com/intellij/psi/PsiCapturedWildcardType") ||
                    clsOwner.startsWith("com/intellij/psi/PsiDisjunctionType")) {
                if (desc.equals("Lcom/intellij/psi/PsiManager;")) {
                    return true;
                }
                if (name.equals("myLubCache")) {
                    return true;
                }
            }

            return false;
        }

        @Override
        public String toString() {
            return mContainingClass.getName() + "#" + mFieldNode.name;
        }

        @Override
        public int compareTo(@NonNull CgField other) {
            int delta = mFieldNode.name.compareTo(other.mFieldNode.name);
            if (delta == 0) {
                delta = mFieldNode.desc.compareTo(other.mFieldNode.desc);
            }
            return delta;
        }
    }

    @SuppressWarnings("RedundantIfStatement")
    public class CgMethod extends Member implements Comparable<CgMethod> {
        private final MethodNode mMethodNode;

        private List<CgMethod> mSuperMethods;

        private List<CgMethod> mSubMethods;

        public CgMethod(CgClass containingClass, MethodNode methodNode) {
            super(containingClass);
            mMethodNode = methodNode;
            mMethodMap.put(methodNode, this);
        }

        @Override
        public boolean isWipedMethod() {
            String name = mMethodNode.name;
            String clsOwner = mContainingClass.getName();

            if (clsOwner.equals("com/intellij/psi/PsiInvalidElementAccessException")) {
                // Remove most methods; this exception is important since it's included
                // in lots of signatures, but we'll never raise it (and it calls into ASTNode
                // via getNode() in the constructor.)
                return name.equals("<init>");
            }

            return matches(WIPED_METHOD_BODIES);
        }

        protected boolean matches(Map<String,String> map) {
            String name = mMethodNode.name;
            String desc = mMethodNode.desc;
            String clsOwner = mContainingClass.getName();

            String skipDesc = map.get(name);
            if (skipDesc != null) {
                if (skipDesc.isEmpty()) { // no desc in skip map means match all signatures
                    // ...but only in the core packages
                    if (clsOwner.startsWith("com/intellij/psi/") || clsOwner.startsWith("com/intellij/pom/")) {
                        return true;
                    } else {
                        return false;
                    }
                }
                if (!skipDesc.startsWith("(")) {
                    // Owner
                    String owner = skipDesc;
                    int index = owner.indexOf('(');
                    if (index != -1) {
                        owner = owner.substring(0, index);
                    }
                    if (!owner.equals(clsOwner)) {
                        return false;
                    }
                    if (index != -1) {
                        skipDesc = skipDesc.substring(index);
                        // Continue with parameter check
                    } else {
                        return true;
                    }
                }

                if (skipDesc.equals(desc)) {
                    return true;
                } else if (getMethodHandle(name, skipDesc).equals(getMethodHandle(name, desc))) {
                    System.out.println("Only return type was different: verify that this is as expected");
                }
            }

            return false;
        }

        private List<CgMethod> getSuperMethods() {
            if (mSuperMethods == null) {
                mSuperMethods = Lists.newArrayList();
                String methodHandle = getMethodHandle(mMethodNode.name, mMethodNode.desc);
                addSuperMethods(mSuperMethods, methodHandle);
            }

            return mSuperMethods;
        }

        private void addSuperMethods(List<CgMethod> result, String methodHandle) {
            CgClass cls = mContainingClass.findSuperClass();

            while (cls != null) {
                CgMethod method = cls.mMethods.get(methodHandle);
                if (method != null) {
                    result.add(method);
                    break;
                }

                cls = cls.findSuperClass();
            }

            @SuppressWarnings("unchecked") // ASM API
                    List<String> interfaceList = mContainingClass.mClassNode.interfaces;
            if (interfaceList != null) {
                for (String name : interfaceList) {
                    cls = findClass(name);
                    while (cls != null) {
                        CgMethod method = cls.mMethods.get(methodHandle);
                        if (method != null) {
                            result.add(method);
                            break;
                        }

                        cls = cls.findSuperClass();
                    }
                }
            }

        }

        // Return true if this method or any of its overriding/sub methods are reachable
        protected boolean isSubMethodReachable() {
            // Once you get to an overriding method in a class that isn't referenced,
            // you can ignore the methods (and any subclasses, which can't be reachable
            // either since otherwise this class would be.)
            if (!mContainingClass.isReachable() || isSkipped()) {
                return false;
            }

            if (isReachable()) {
                return true;
            }

            for (CgMethod sub : getSubMethods()) {
                if (sub.isSubMethodReachable()) {
                    return true;
                }
            }

            return false;
        }

        protected boolean isSuperMethodReachable() {
            if (isReachable()) {
                return true;
            }

            for (CgMethod sup : getSuperMethods()) {
                if (sup.isSuperMethodReachable()) {
                    return true;
                }
            }

            return false;
        }

        @Override
        public void markReachable(boolean reachable, boolean includeChildren,
                                  boolean includeParents) {
            assert !reachable
                    || !isSkipped()
                    // Special case handled by rewriting
                    || "com/intellij/psi/PsiDisjunctionType#<init>(Ljava/util/List;Lcom/intellij/psi/PsiManager;)V".equals(toString())
                    : this;
            super.markReachable(reachable, includeChildren, includeParents);
        }

        protected void markSubMethodsReachable(boolean reachable) {
            if (!mContainingClass.isReachable() || isSkipped()) {
                return;
            }

            markReachable(reachable, false, false);

            for (CgMethod sub : getSubMethods()) {
                sub.markSubMethodsReachable(reachable);
            }
        }

        protected void markSuperMethodsReachable(boolean reachable) {
            if (isSkipped()) {
                return;
            }
            markReachable(reachable, false, false);

            for (CgMethod sup : getSuperMethods()) {
                sup.markSuperMethodsReachable(reachable);
            }
        }

        public void addSubMethod(CgMethod method) {
            if (mSubMethods == null) {
                mSubMethods = Lists.newArrayList();
            }
            mSubMethods.add(method);
        }

        public List<CgMethod> getSubMethods() {
            return mSubMethods == null ? Collections.<CgMethod>emptyList() : mSubMethods;
        }

        @Override
        public String toString() {
            return mContainingClass.getName() + "#" + mMethodNode.name + mMethodNode.desc;
        }

        @Override
        public boolean isSkipped() {
            String name = mMethodNode.name;
            String desc = mMethodNode.desc;
            String clsOwner = mContainingClass.getName();

            if (name.equals("getInstance") && desc.startsWith("(Lcom/intellij/openapi/project/Project;)L")) {
                return true;
            }

            // Strip out methods from UserData and Iconable - don't want them on PsiElement
            if (name.equals("getUserData")
                    || name.equals("putUserData")) {
                return true;
            }

            // Nothing particularly hard about this, but we don't model packages in lint
            // (and haven't had a need for it) so don't include methods that don't work
            if ((name.equals("getClasses") || name.equals("getSubPackages"))
                    && clsOwner.equals("com/intellij/psi/PsiPackage")) {
                return true;
            }
            if (name.equals("getIcon") &&
                    (clsOwner.startsWith("com/intellij/psi/")
                            || clsOwner.startsWith("com/intellij/pom/")
                            || clsOwner.equals("com/intellij/openapi/util/Iconable")
                            || clsOwner.equals("com/intellij/navigation/ItemPresentation"))) {
                return true;
            }

            if (name.equals("getPresentation") &&
                    (clsOwner.startsWith("com/intellij/psi/")
                            || clsOwner.startsWith("com/intellij/pom/")
                            || clsOwner.equals("com/intellij/navigation/NavigationItem"))) {
                return true;
            }

            // Skip setters etc - but only in PSI, not for example com/intellij/util/containers
            if (clsOwner.startsWith("com/intellij/psi/") || clsOwner.startsWith("com/intellij/pom/")) {
                if (name.startsWith("set") && desc.endsWith(")V")) {
                    // Skip void setters
                    return true;
                }

                if (name.startsWith("set")
                        && name.length() >= 4
                        && Character.isUpperCase(name.charAt(3))
                        && !desc.contains("()")) {
                    return true;
                }

                if (name.startsWith("bind")
                        && name.length() >= 5
                        && Character.isUpperCase(name.charAt(4))
                        && !desc.contains("()")) {
                    return true;
                }

                if (name.startsWith("handle")
                        && name.length() >= 6
                        && Character.isUpperCase(name.charAt(5))
                        && !desc.contains("()")) {
                    return true;
                }

                if (name.startsWith("getJavaLang") && desc
                        .endsWith(")Lcom/intellij/psi/PsiClassType;")) {
                    // getJavaLangThrowable, etc
                    return true;
                }

                if (name.equals("getNode") && desc.startsWith("()") && desc.contains("ASTNode")) {
                    // ASTNode, FileASTNode etc
                    return true;
                }

                if ((name.equals("getManager") || name.equals("getPsiManager"))
                        && desc.equals("()Lcom/intellij/psi/PsiManager;")) {
                    return true;
                }

                if (name.equals("visitFile") &&
                        (clsOwner.equals("com/intellij/psi/PsiRecursiveElementWalkingVisitor") ||
                                clsOwner.equals("com/intellij/psi/PsiRecursiveElementVisitor"))) {
                    // Just use super; these are looking up file providers etc
                    return true;
                }

                if (clsOwner.equals("com/intellij/psi/PsiInvalidElementAccessException")) {
                    // Remove most methods; this exception is important since it's included
                    // in lots of signatures, but we'll never raise it (and it calls into ASTNode
                    // via getNode() in the constructor.)
                    return !name.equals("<init>");
                }

                // These types contain a LOT of implementation gunk; remove them for now
                // (except the accept method required for external visitor calls)
                if (clsOwner.startsWith("com/intellij/psi/PsiWildcardType") ||
                        clsOwner.startsWith("com/intellij/psi/PsiIntersectionType") ||
                        clsOwner.startsWith("com/intellij/psi/PsiCapturedWildcardType")) {
                    return !name.equals("accept")
                            // PsiIntersectionType - needed by visitor
                            && !name.equals("getConjuncts")
                            // PsiCapturedWildType - needed by visitor
                            && !name.equals("getWildcard");
                }

                if (clsOwner.startsWith("com/intellij/psi/PsiDisjunctionType")) {
                    if (name.equals("getLeastUpperBound")
                            || name.equals("getPresentableText")
                            || name.equals("getCanonicalText")
                            || name.equals("getInternalCanonicalText")
                            || name.equals("equalsToText")
                            || name.equals("getSuperTypes")
                            || name.equals("getDisjunctions")
                            || name.equals("isValid")
                            || name.equals("equals")
                            || name.equals("hashCode")
                            || name.equals("accept")
                            // inner classes
                            || (clsOwner.startsWith("com/intellij/psi/PsiDisjunctionType$")
                            && (name.equals("<init>")
                            || name.equals("fun")))) { // in inner classes
                        // but we wipe the method impl; ecj must must customize
                        // TODO: Mark the method as abstract?
                        return false;
                    } else {
                        return true;
                    }
                }
            }

            if (matches(SKIPPED_METHODS)) {
                return true;
            }

            return false;
        }

        public void recordDependencies() {
            // Include return type
            // using signature instead of desc to get type variables
            // e.g. for findMethodsAndTheirSubstitutorsByName we may have
            // desc=(Ljava/lang/String;Z)Ljava/util/List;
            // signature=(Ljava/lang/String;Z)Ljava/util/List<Lcom/intellij/openapi/util/Pair<Lcom/intellij/psi/PsiMethod;Lcom/intellij/psi/PsiSubstitutor;>;>;
            // and we want to include the types as well
            if (mMethodNode.signature != null) {
                new SignatureReader(mMethodNode.signature).accept(new SignatureVisitor(ASM5) {
                    @Override
                    public void visitClassType(String name) {
                        CgClass clazz = findClass(name);
                        if (clazz != null) {
                            addDependency(clazz);
                        }
                        super.visitClassType(name);
                    }

                    @Override
                    public void visitInnerClassType(String name) {
                        CgClass clazz = findClass(name);
                        if (clazz != null) {
                            addDependency(clazz);
                        }
                        super.visitInnerClassType(name);
                    }
                });
            }
            String desc = mMethodNode.desc;
            Type[] argumentTypes = Type.getArgumentTypes(desc);
            for (Type type : argumentTypes) {
                addTypeDependency(type);
            }
            addTypeDependency(Type.getReturnType(desc));

            if (isWipedMethod()) {

                // TODO: Move this to the WIPED_METHODs list: put replacement code
                // there!
                if (mMethodNode.name.equals("<init>") &&
                        mContainingClass.getName().equals("com/intellij/psi/PsiInvalidElementAccessException")) {
                    mMethodNode.instructions.clear();
                    mMethodNode.tryCatchBlocks = null;
                    mMethodNode.instructions.add(new InsnNode(Opcodes.RETURN));
                } else if (mMethodNode.name.equals("<init>") &&
                        mContainingClass.getName().equals("com/intellij/psi/PsiDisjunctionType")) {

                    mMethodNode.instructions.clear();
                    mMethodNode.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    mMethodNode.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, "com/intellij/psi/PsiAnnotation", "EMPTY_ARRAY", "[Lcom/intellij/psi/PsiAnnotation;"));
                    mMethodNode.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "com/intellij/psi/PsiType$Stub", "<init>", "([Lcom/intellij/psi/PsiAnnotation;)V", false));
                    mMethodNode.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    mMethodNode.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
                    mMethodNode.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, "com/intellij/psi/PsiDisjunctionType", "myTypes", "Ljava/util/List;"));
                    mMethodNode.instructions.add(new InsnNode(Opcodes.RETURN));
                } else {
                    mMethodNode.instructions.clear();
                    if (desc.endsWith(")V")) {
                        mMethodNode.instructions.add(new InsnNode(Opcodes.RETURN));
                    } else if (desc.endsWith(";")) {
                        mMethodNode.instructions.add(new InsnNode(Opcodes.ACONST_NULL));
                        mMethodNode.instructions.add(new InsnNode(Opcodes.ARETURN));
                    } else {
                        // Need to handle this scenario: primitive value return
                        throw new IllegalArgumentException(desc);
                    }
                }
            } else {
                InsnList nodes = mMethodNode.instructions;
                for (int i = 0, n = nodes.size(); i < n; i++) {
                    AbstractInsnNode instruction = nodes.get(i);
                    if (instruction instanceof InvokeDynamicInsnNode) {
                        throw new IllegalArgumentException("PSI is using dynamic instructions now: update handling here");
                    } else if (instruction instanceof MethodInsnNode) {
                        MethodInsnNode node = (MethodInsnNode) instruction;
                        CgMethod method = findMethod(node.owner, node.name, node.desc);
                        if (method != null) {
                            addDependency(method);
                        } else if (node.owner.equals("java/lang/Class") &&
                                (node.name.equals("getDeclaredField") ||
                                        node.name.equals("getField") ||
                                        node.name.equals("getMethod") ||
                                        node.name.equals("getDeclaredMethod") ||
                                        node.name.equals("forName"))) {
                            boolean ok = false;

                            String containingClass = mContainingClass.getName();
                            if (containingClass.equals("com/intellij/psi/impl/ConstantExpressionVisitor#visitReferenceExpression(Lcom/intellij/psi/PsiReferenceExpression;)V")
                                    || containingClass.equals("com/intellij/util/containers/ConcurrentIntHashMap")
                                    || containingClass.equals("com/intellij/util/containers/ConcurrentHashMap")
                                    || containingClass.equals("com/intellij/util/containers/ConcurrentLongObjectHashMap")
                                    || containingClass.equals("com/intellij/util/containers/ConcurrentIntObjectHashMap")) {
                                // already handled
                                ok = true;
                            } else {
                                AbstractInsnNode prev = LintUtils.getPrevInstruction(node);
                                if (prev instanceof LdcInsnNode) {
                                    AbstractInsnNode prev2 = LintUtils.getPrevInstruction(prev);
                                    if (prev2 instanceof LdcInsnNode) {
                                        LdcInsnNode c = (LdcInsnNode)prev2;
                                        if (c.cst instanceof Type) {
                                            if (addTypeDependency((Type)c.cst)) {
                                                // Potentially check for method/field here
                                                System.out.println("TODO: Reflection access found for type " + c.cst +
                                                    ": Also look for method/field references on that type?");
                                            } else {
                                                ok = true; // Referring to some other system class
                                            }
                                        }
                                    }
                                }
                            }

                            // Manually verified: these reflection accesses do not affect PSI call graph:
                            if (!ok && (containingClass.equals("com/intellij/openapi/options/BeanConfigurable$BeanField")
                                || containingClass.equals("com/intellij/util/lang/UrlClassLoader")
                                || containingClass.equals("com/intellij/openapi/util/DefaultJDOMExternalizer")
                                || containingClass.equals("com/intellij/openapi/util/io/FileUtil")
                                || containingClass.startsWith("com/intellij/util/ui/UIUtil")
                                || containingClass.equals("com/intellij/openapi/ui/popup/util/PopupUtil")
                                || containingClass.equals("com/intellij/ide/ClassUtilCore")
                                || containingClass.equals("com/intellij/openapi/diagnostic/LoggerRt$IdeaFactory")
                                || containingClass.startsWith("com/intellij/util/io/PagedFileStorage$StorageLock")
                                || containingClass.equals("com/intellij/openapi/util/JDOMExternalizableStringList")
                                || containingClass.equals("com/intellij/util/InstanceofCheckerGenerator")
                                || containingClass.startsWith("com/intellij/openapi/util/io/FileUtilRt")
                                || containingClass.startsWith("com/intellij/openapi/util/io/FileSystemUtil")
                                || containingClass.equals("com/intellij/codeInsight/completion/CompletionAutoPopupTestCase")
                                || containingClass.equals("com/intellij/openapi/ui/FixedComboBoxEditor")
                                || containingClass.equals("com/intellij/openapi/projectRoots/JdkUtil")
                                || containingClass.equals("com/intellij/openapi/application/ModalityState")
                                || containingClass.equals("com/intellij/openapi/components/ServiceBean")
                                || containingClass.equals("com/intellij/util/MemoryDumpHelper")
                                || containingClass.equals("com/intellij/openapi/MnemonicHelper")
                                || containingClass.equals("com/intellij/ide/ui/PublicMethodBasedOptionDescription")
                                || containingClass.equals("com/intellij/openapi/MnemonicWrapper")
                                || containingClass.equals("com/intellij/execution/rmi/RemoteUtil")
                                || containingClass.equals("com/intellij/openapi/ui/DialogWrapperPeerFactory")
                                || containingClass.equals("com/intellij/openapi/externalSystem/util/ExternalSystemApiUtil")
                                || containingClass.equals("com/intellij/openapi/util/IconLoader")
                                || containingClass.equals("com/intellij/openapi/ui/MasterDetailsComponent")
                                || containingClass.startsWith("com/intellij/ui/SearchTextField$1")
                                || containingClass.startsWith("com/intellij/openapi/ui/Messages$MessageDialog")
                                || containingClass.equals("com/intellij/lang/cacheBuilder/CacheBuilderEP")
                                || containingClass.equals("com/intellij/openapi/module/ModuleType")
                                || containingClass.equals("com/intellij/openapi/module/StdModuleTypes")
                                || containingClass.equals("com/intellij/ide/ui/PublicFieldBasedOptionDescription")
                                || containingClass.equals("com/intellij/openapi/externalSystem/model/DataNode$1")
                                || containingClass.equals("com/intellij/ui/mac/foundation/MacUtil")
                                || containingClass.equals("com/intellij/codeInspection/ui/SingleIntegerFieldOptionsPanel")
                                || containingClass.startsWith("com/intellij/openapi/extensions/")
                                || containingClass.startsWith("com/intellij/util/containers/Concurrent")
                                || containingClass.equals("com/intellij/util/ReflectionUtil"))) {
                                ok = true;
                            }

                            if (!ok) {
                                System.out.println("WARNING: REFLECTION found in " + this + ": check for potentially missed code");
                            }
                        }
                    } else if (instruction instanceof FieldInsnNode) {
                        FieldInsnNode node = (FieldInsnNode) instruction;
                        CgField field = findField(node.owner, node.name);
                        if (field != null) {
                            addDependency(field);
                        }
                    } else if (instruction instanceof TypeInsnNode) {
                        TypeInsnNode node = (TypeInsnNode) instruction;
                        if (node.desc.endsWith(";")) {
                            Type type = Type.getType(node.desc);
                            if (type != null) {
                                CgClass clazz = findClass(type.getInternalName());
                                if (clazz != null) {
                                    addDependency(clazz);
                                }
                            }
                        }
                    } else if (instruction instanceof LdcInsnNode) {
                        LdcInsnNode ldc = (LdcInsnNode) instruction;
                        if (ldc.cst instanceof Type) {
                            CgClass clazz = findClass(((Type)ldc.cst).getInternalName());
                            if (clazz != null) {
                                addDependency(clazz);
                            }
                        }

                    }
                }
            }

            //noinspection unchecked
            List<String> exceptions = mMethodNode.exceptions;
            for (String s : exceptions) {
                CgClass clazz = findClass(s);
                if (clazz != null) {
                    addDependency(clazz);
                }
            }

            addAnnotationDependency(mMethodNode.visibleTypeAnnotations);
            addAnnotationDependency(mMethodNode.invisibleTypeAnnotations);
            addAnnotationDependency(mMethodNode.visibleAnnotations);
            addAnnotationDependency(mMethodNode.invisibleAnnotations);
        }

        @Override
        public int compareTo(@NonNull CgMethod c2) {
            int delta = mMethodNode.name.compareTo(c2.mMethodNode.name);
            if (delta == 0) {
                delta = mMethodNode.desc.compareTo(c2.mMethodNode.desc);
            }
            return delta;
        }
    }

    public class CgClass extends CgNode implements Comparable<CgClass> {

        private final Map<String, CgMethod> mMethods = Maps.newHashMap();

        private final Map<String, CgField> mFields = Maps.newHashMap();

        private final ClassNode mClassNode;

        public boolean isInterface() {
            return (mClassNode.access & Opcodes.ACC_INTERFACE) != 0;
        }

        public boolean isAnnotation() {
            return (mClassNode.access & Opcodes.ACC_ANNOTATION) != 0;
        }

        @NonNull
        public String getName() {
            return mClassNode.name;
        }

        public boolean extendsJdk() {
            // Returns true if this class extends a JDK class (other than java.lang.Object)
            CgClass superCls = findSuperClass();
            if (superCls != null && superCls.extendsJdk()) {
                return true;
            }

            if (isInterestingJdkClass(mClassNode.superName)) {
                return true;
            }

            if (mClassNode.interfaces != null) {
                @SuppressWarnings("unchecked") // ASM API
                List<String> interfaceList = mClassNode.interfaces;
                for (String signature : interfaceList) {
                    CgClass clazz = findClass(signature);
                    if (clazz != null && clazz.extendsJdk()) {
                        return true;
                    }
                    if (isInterestingJdkClass(signature)) {
                        return true;
                    }
                }
            }

            return false;
        }

        private boolean isInterestingJdkClass(@Nullable String owner) {
            return owner != null
                    && (owner.startsWith("java/") || owner.startsWith("javax/"))
                    && !owner.equals("java/lang/Object");
        }

        @Nullable
        public CgClass findSuperClass() {
            if (mClassNode.superName != null) {
                return findClass(mClassNode.superName);
            }
            return null;
        }

        public CgClass(ClassNode classNode) {
            mClassNode = classNode;
            @SuppressWarnings("rawtypes") // ASM API
                    List fieldList = classNode.fields;
            for (Object f : fieldList) {
                FieldNode fieldNode = (FieldNode) f;
                CgField field = new CgField(this, fieldNode);
                mFields.put(fieldNode.name, field);
            }

            @SuppressWarnings("rawtypes") // ASM API
                    List methodList = classNode.methods;
            for (Object f : methodList) {
                MethodNode methodNode = (MethodNode) f;
                CgMethod method = new CgMethod(this, methodNode);
                mMethods.put(getMethodHandle(methodNode.name, methodNode.desc), method);
            }
        }

        @Override
        protected void visitReachable() {
            super.visitReachable();

            for (CgField field : mFields.values()) {
                if (field.isReachable()) {
                    field.visitReachable();
                }
            }
            for (CgMethod method : mMethods.values()) {
                if (method.isSkipped()) {
                    continue;
                }
                if (isInterface() | isAnnotation()) {
                    // Interface methods need to all be included if the interface is included
                    // Also include all annotation methods
                    method.markReachable(true, false, false);
                } else if (method.mMethodNode.name.equals("<clinit>")) {
                    // Class initializers must be included if a class is!
                    method.markReachable(true, false, false);
                }
                if (method.isReachable()) {
                    method.visitReachable();
                }
            }
        }

        @Override
        protected void visitUnreachable() {
            super.visitUnreachable();

            for (CgField field : mFields.values()) {
                if (field.isUnreachable()) {
                    field.visitUnreachable();
                }
            }
            for (CgMethod method : mMethods.values()) {
                if (method.isUnreachable()) {
                    method.visitUnreachable();
                }
            }
        }

        @Override
        public boolean isSkipped() {
            String name = getName();
            if (SKIPPED_CLASSES.contains(name)) {
                return true;
            }
            int index = name.indexOf('$');
            return index != -1 && SKIPPED_CLASSES.contains(name.substring(0, index));
        }

        @Override
        public void markReachable(
                boolean reachable,
                boolean includeChildren,
                boolean includeParents) {
            assert !reachable || !isSkipped() : this;
            super.markReachable(reachable, includeChildren, includeParents);

            // If a class is reachable, so are all its outer classes
            if (reachable && includeParents) {
                String outerClass = mClassNode.outerClass;
                if (outerClass != null) {
                    CgClass clz = findClass(outerClass);
                    if (clz != null) {
                        clz.markReachable(true, false, true); // recursively
                    }
                }
            }

            if (includeChildren) {
                for (CgField field : mFields.values()) {
                    if (field.isSkipped()) {
                        field.markReachable(false, true, false);
                        continue;
                    }
                    field.markReachable(reachable, true, false);
                }
                for (CgMethod method : mMethods.values()) {
                    // White-list all methods, except those specifically prohibited
                    if (method.isSkipped()) {
                        method.markReachable(false, true, false);
                        continue;
                    }
                    method.markReachable(reachable, true, false);
                }
            }

            // What about inner classes?
        }

        public void recordDependencies() {

            for (CgField field : mFields.values()) {
                field.recordDependencies();
            }

            // TODO: Specially handled field initializations?

            for (CgMethod method : mMethods.values()) {
                method.recordDependencies();
            }

            if (mClassNode.signature != null) {
                new SignatureReader(mClassNode.signature).accept(new SignatureVisitor(ASM5) {
                    @Override
                    public void visitClassType(String name) {
                        CgClass clazz = findClass(name);
                        if (clazz != null) {
                            addDependency(clazz);
                        }
                        super.visitClassType(name);
                    }

                    @Override
                    public void visitInnerClassType(String name) {
                        CgClass clazz = findClass(name);
                        if (clazz != null) {
                            addDependency(clazz);
                        }
                        super.visitInnerClassType(name);
                    }
                });
            }
            if (mClassNode.superName != null) {
                CgClass clazz = findClass(mClassNode.superName);
                if (clazz != null) {
                    addDependency(clazz);
                }
            }

            if (mClassNode.interfaces != null) {
                @SuppressWarnings("unchecked") // ASM API
                        List<String> interfaceList = mClassNode.interfaces;
                for (String signature : interfaceList) {
                    CgClass clazz = findClass(signature);
                    if (clazz != null) {
                        addDependency(clazz);
                    }
                }
            }

            addAnnotationDependency(mClassNode.visibleTypeAnnotations);
            addAnnotationDependency(mClassNode.invisibleTypeAnnotations);
            addAnnotationDependency(mClassNode.visibleAnnotations);
            addAnnotationDependency(mClassNode.invisibleAnnotations);
        }

        @Override
        public String toString() {
            return getName();
        }

        @Override
        public int compareTo(@NonNull CgClass other) {
            return getName().compareTo(other.getName());
        }
    }
}
