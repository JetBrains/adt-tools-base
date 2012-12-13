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

package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_PREFIX;
import static com.android.SdkConstants.ANDROID_THEME_PREFIX;
import static com.android.SdkConstants.ATTR_CLASS;
import static com.android.SdkConstants.ATTR_TARGET_API;
import static com.android.SdkConstants.CONSTRUCTOR_NAME;
import static com.android.SdkConstants.TARGET_API;
import static com.android.SdkConstants.TOOLS_URI;
import static com.android.SdkConstants.VIEW_TAG;
import static com.android.tools.lint.detector.api.LintUtils.getNextInstruction;
import static com.android.tools.lint.detector.api.Location.SearchDirection.BACKWARD;
import static com.android.tools.lint.detector.api.Location.SearchDirection.FORWARD;
import static com.android.tools.lint.detector.api.Location.SearchDirection.NEAREST;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.client.api.LintDriver;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ClassContext;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Location.SearchHints;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Speed;
import com.android.tools.lint.detector.api.XmlContext;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;

/**
 * Looks for usages of APIs that are not supported in all the versions targeted
 * by this application (according to its minimum API requirement in the manifest).
 */
public class ApiDetector extends ResourceXmlDetector implements Detector.ClassScanner {
    /**
     * Whether we flag variable, field, parameter and return type declarations of a type
     * not yet available. It appears Dalvik is very forgiving and doesn't try to preload
     * classes until actually needed, so there is no need to flag these, and in fact,
     * patterns used for supporting new and old versions sometimes declares these methods
     * and only conditionally end up actually accessing methods and fields, so only check
     * method and field accesses.
     */
    private static final boolean CHECK_DECLARATIONS = false;

    private static final boolean AOSP_BUILD = System.getenv("ANDROID_BUILD_TOP") != null; //$NON-NLS-1$

    /** Accessing an unsupported API */
    public static final Issue UNSUPPORTED = Issue.create("NewApi", //$NON-NLS-1$
            "Finds API accesses to APIs that are not supported in all targeted API versions",

            "This check scans through all the Android API calls in the application and " +
            "warns about any calls that are not available on *all* versions targeted " +
            "by this application (according to its minimum SDK attribute in the manifest).\n" +
            "\n" +
            "If you really want to use this API and don't need to support older devices just " +
            "set the `minSdkVersion` in your `AndroidManifest.xml` file." +
            "\n" +
            "If your code is *deliberately* accessing newer APIs, and you have ensured " +
            "(e.g. with conditional execution) that this code will only ever be called on a " +
            "supported platform, then you can annotate your class or method with the " +
            "`@TargetApi` annotation specifying the local minimum SDK to apply, such as " +
            "`@TargetApi(11)`, such that this check considers 11 rather than your manifest " +
            "file's minimum SDK as the required API level.\n" +
            "\n" +
            "Similarly, you can use tools:targetApi=\"11\" in an XML file to indicate that " +
            "the element will only be inflated in an adequate context.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            ApiDetector.class,
            EnumSet.of(Scope.CLASS_FILE, Scope.RESOURCE_FILE, Scope.MANIFEST))
            .addAnalysisScope(Scope.RESOURCE_FILE_SCOPE)
            .addAnalysisScope(Scope.CLASS_FILE_SCOPE);

    /** Accessing an unsupported API */
    public static final Issue OVERRIDE = Issue.create("Override", //$NON-NLS-1$
            "Finds method declarations that will accidentally override methods in later versions",

            "Suppose you are building against Android API 8, and you've subclassed Activity. " +
            "In your subclass you add a new method called `isDestroyed`(). At some later point, " +
            "a method of the same name and signature is added to Android. Your method will " +
            "now override the Android method, and possibly break its contract. Your method " +
            "is not calling `super.isDestroyed()`, since your compilation target doesn't " +
            "know about the method.\n" +
            "\n" +
            "The above scenario is what this lint detector looks for. The above example is " +
            "real, since `isDestroyed()` was added in API 17, but it will be true for *any* " +
            "method you have added to a subclass of an Android class where your build target " +
            "is lower than the version the method was introduced in.\n" +
            "\n" +
            "To fix this, either rename your method, or if you are really trying to augment " +
            "the builtin method if available, switch to a higher build target where you can " +
            "deliberately add `@Override` on your overriding method, and call `super` if " +
            "appropriate etc.\n",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            ApiDetector.class,
            Scope.CLASS_FILE_SCOPE);

    private static final String TARGET_API_VMSIG = '/' + TARGET_API + ';';
    private static final String SWITCH_TABLE_PREFIX = "$SWITCH_TABLE$";  //$NON-NLS-1$
    private static final String ORDINAL_METHOD = "ordinal"; //$NON-NLS-1$

    private ApiLookup mApiDatabase;
    private int mMinApi = -1;

    /** Constructs a new API check */
    public ApiDetector() {
    }

    @NonNull
    @Override
    public Speed getSpeed() {
        return Speed.SLOW;
    }

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        mApiDatabase = ApiLookup.get(context.getClient());
        // We can't look up the minimum API required by the project here:
        // The manifest file hasn't been processed yet in the -before- project hook.
        // For now it's initialized lazily in getMinSdk(Context), but the
        // lint infrastructure should be fixed to parse manifest file up front.
    }

    // ---- Implements XmlScanner ----

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return true;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return ALL;
    }

    @Override
    public Collection<String> getApplicableAttributes() {
        return ALL;
    }

    @Override
    public void visitAttribute(@NonNull XmlContext context, @NonNull Attr attribute) {
        if (mApiDatabase == null) {
            return;
        }

        String value = attribute.getValue();

        String prefix;
        if (value.startsWith(ANDROID_PREFIX)) {
            prefix = ANDROID_PREFIX;
        } else if (value.startsWith(ANDROID_THEME_PREFIX)) {
            prefix = ANDROID_THEME_PREFIX;
        } else {
            return;
        }

        // Convert @android:type/foo into android/R$type and "foo"
        int index = value.indexOf('/', prefix.length());
        if (index != -1) {
            String owner = "android/R$"    //$NON-NLS-1$
                    + value.substring(prefix.length(), index);
            String name = value.substring(index + 1);
            if (name.indexOf('.') != -1) {
                name = name.replace('.', '_');
            }
            int api = mApiDatabase.getFieldVersion(owner, name);
            int minSdk = getMinSdk(context);
            if (api > minSdk && api > context.getFolderVersion()
                    && api > getLocalMinSdk(attribute.getOwnerElement())) {
                // Don't complain about resource references in the tools namespace,
                // such as for example "tools:layout="@android:layout/list_content",
                // used only for designtime previews
                if (TOOLS_URI.equals(attribute.getNamespaceURI())) {
                    return;
                }

                Location location = context.getLocation(attribute);
                String message = String.format(
                        "%1$s requires API level %2$d (current min is %3$d)",
                        value, api, minSdk);
                context.report(UNSUPPORTED, attribute, location, message, null);
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (mApiDatabase == null) {
            return;
        }

        String tag = element.getTagName();

        ResourceFolderType folderType = context.getResourceFolderType();
        if (folderType != ResourceFolderType.LAYOUT) {
            if (element.getParentNode().getNodeType() != Node.ELEMENT_NODE) {
                // Root node
                return;
            }
            NodeList childNodes = element.getChildNodes();
            for (int i = 0, n = childNodes.getLength(); i < n; i++) {
                Node textNode = childNodes.item(i);
                if (textNode.getNodeType() == Node.TEXT_NODE) {
                    String text = textNode.getNodeValue();
                    if (text.indexOf(ANDROID_PREFIX) != -1) {
                        text = text.trim();
                        // Convert @android:type/foo into android/R$type and "foo"
                        int index = text.indexOf('/', ANDROID_PREFIX.length());
                        if (index != -1) {
                            String owner = "android/R$"    //$NON-NLS-1$
                                    + text.substring(ANDROID_PREFIX.length(), index);
                            String name = text.substring(index + 1);
                            if (name.indexOf('.') != -1) {
                                name = name.replace('.', '_');
                            }
                            int api = mApiDatabase.getFieldVersion(owner, name);
                            int minSdk = getMinSdk(context);
                            if (api > minSdk && api > context.getFolderVersion()
                                    && api > getLocalMinSdk(element)) {
                                Location location = context.getLocation(textNode);
                                String message = String.format(
                                        "%1$s requires API level %2$d (current min is %3$d)",
                                        text, api, minSdk);
                                context.report(UNSUPPORTED, element, location, message, null);
                            }
                        }
                    }
                }
            }
        } else if (folderType == ResourceFolderType.LAYOUT) {
            if (VIEW_TAG.equals(tag)) {
                tag = element.getAttribute(ATTR_CLASS);
                if (tag == null || tag.isEmpty()) {
                    return;
                }
            }

            // Check widgets to make sure they're available in this version of the SDK.
            if (tag.indexOf('.') != -1 ||
                    folderType != ResourceFolderType.LAYOUT) {
                // Custom views aren't in the index
                return;
            }
            // TODO: Consider other widgets outside of android.widget.*
            int api = mApiDatabase.getCallVersion("android/widget/" + tag,  //$NON-NLS-1$
                    CONSTRUCTOR_NAME,
                    // Not all views provided this constructor right away, for example,
                    // LinearLayout added it in API 11 yet LinearLayout is much older:
                    // "(Landroid/content/Context;Landroid/util/AttributeSet;I)V"); //$NON-NLS-1$
                    "(Landroid/content/Context;)"); //$NON-NLS-1$
            int minSdk = getMinSdk(context);
            if (api > minSdk && api > context.getFolderVersion()
                    && api > getLocalMinSdk(element)) {
                Location location = context.getLocation(element);
                String message = String.format(
                        "View requires API level %1$d (current min is %2$d): <%3$s>",
                        api, minSdk, tag);
                context.report(UNSUPPORTED, element, location, message, null);
            }
        }
    }

    private int getMinSdk(Context context) {
        if (mMinApi == -1) {
            mMinApi = context.getMainProject().getMinSdk();
        }

        return mMinApi;
    }

    // ---- Implements ClassScanner ----

    @SuppressWarnings("rawtypes") // ASM API
    @Override
    public void checkClass(@NonNull final ClassContext context, @NonNull ClassNode classNode) {
        if (mApiDatabase == null) {
            return;
        }

        if (AOSP_BUILD && classNode.name.startsWith("android/support/")) { //$NON-NLS-1$
            return;
        }

        // Requires util package (add prebuilts/tools/common/asm-tools/asm-debug-all-4.0.jar)
        //classNode.accept(new TraceClassVisitor(new PrintWriter(System.out)));

        int classMinSdk = getClassMinSdk(context, classNode);
        if (classMinSdk == -1) {
            classMinSdk = getMinSdk(context);
        }

        List methodList = classNode.methods;
        if (methodList.isEmpty()) {
            return;
        }

        boolean checkCalls = context.isEnabled(UNSUPPORTED);
        boolean checkMethods = context.isEnabled(OVERRIDE)
                && context.getMainProject().getBuildSdk() >= 1;
        String frameworkParent = null;
        if (checkMethods) {
            LintDriver driver = context.getDriver();
            String owner = classNode.superName;
            while (owner != null) {
                // For virtual dispatch, walk up the inheritance chain checking
                // each inherited method
                if (owner.startsWith("android/")           //$NON-NLS-1$
                        || owner.startsWith("java/")       //$NON-NLS-1$
                        || owner.startsWith("javax/")) {   //$NON-NLS-1$
                    frameworkParent = owner;
                    break;
                }
                owner = driver.getSuperClass(owner);
            }
            if (frameworkParent == null) {
                checkMethods = false;
            }
        }

        for (Object m : methodList) {
            MethodNode method = (MethodNode) m;

            int minSdk = getLocalMinSdk(method.invisibleAnnotations);
            if (minSdk == -1) {
                minSdk = classMinSdk;
            }

            InsnList nodes = method.instructions;

            if (checkMethods && Character.isJavaIdentifierStart(method.name.charAt(0))) {
                int buildSdk = context.getMainProject().getBuildSdk();
                String name = method.name;
                assert frameworkParent != null;
                int api = mApiDatabase.getCallVersion(frameworkParent, name, method.desc);
                if (api > buildSdk && buildSdk != -1) {
                    // TODO: Don't complain if it's annotated with @Override; that means
                    // somehow the build target isn't correct.
                    String fqcn;
                    String owner = classNode.name;
                    if (CONSTRUCTOR_NAME.equals(name)) {
                        fqcn = "new " + ClassContext.getFqcn(owner); //$NON-NLS-1$
                    } else {
                        fqcn = ClassContext.getFqcn(owner) + '#' + name;
                    }
                    String message = String.format(
                            "This method is not overriding anything with the current build " +
                            "target, but will in API level %1$d (current target is %2$d): %3$s",
                            api, buildSdk, fqcn);

                    Location location = context.getLocation(method, classNode);
                    context.report(OVERRIDE, method, null, location, message, null);
                }
            }

            if (!checkCalls) {
                continue;
            }

            if (CHECK_DECLARATIONS) {
                // Check types in parameter list and types of local variables
                List localVariables = method.localVariables;
                if (localVariables != null) {
                    for (Object v : localVariables) {
                        LocalVariableNode var = (LocalVariableNode) v;
                        String desc = var.desc;
                        if (desc.charAt(0) == 'L') {
                            // "Lpackage/Class;" => "package/Bar"
                            String className = desc.substring(1, desc.length() - 1);
                            int api = mApiDatabase.getClassVersion(className);
                            if (api > minSdk) {
                                String fqcn = ClassContext.getFqcn(className);
                                String message = String.format(
                                    "Class requires API level %1$d (current min is %2$d): %3$s",
                                    api, minSdk, fqcn);
                                report(context, message, var.start, method,
                                        className.substring(className.lastIndexOf('/') + 1), null,
                                        SearchHints.create(NEAREST).matchJavaSymbol());
                            }
                        }
                    }
                }

                // Check return type
                // The parameter types are already handled as local variables so we can skip
                // right to the return type.
                // Check types in parameter list
                String signature = method.desc;
                if (signature != null) {
                    int args = signature.indexOf(')');
                    if (args != -1 && signature.charAt(args + 1) == 'L') {
                        String type = signature.substring(args + 2, signature.length() - 1);
                        int api = mApiDatabase.getClassVersion(type);
                        if (api > minSdk) {
                            String fqcn = ClassContext.getFqcn(type);
                            String message = String.format(
                                "Class requires API level %1$d (current min is %2$d): %3$s",
                                api, minSdk, fqcn);
                            AbstractInsnNode first = nodes.size() > 0 ? nodes.get(0) : null;
                            report(context, message, first, method, method.name, null,
                                    SearchHints.create(BACKWARD).matchJavaSymbol());
                        }
                    }
                }
            }

            for (int i = 0, n = nodes.size(); i < n; i++) {
                AbstractInsnNode instruction = nodes.get(i);
                int type = instruction.getType();
                if (type == AbstractInsnNode.METHOD_INSN) {
                    MethodInsnNode node = (MethodInsnNode) instruction;
                    String name = node.name;
                    String owner = node.owner;
                    String desc = node.desc;

                    // No need to check methods in this local class; we know they
                    // won't be an API match
                    if (node.getOpcode() == Opcodes.INVOKEVIRTUAL
                            && owner.equals(classNode.name)) {
                        owner = classNode.superName;
                    }

                    boolean checkingSuperClass = false;
                    while (owner != null) {
                        int api = mApiDatabase.getCallVersion(owner, name, desc);
                        if (api > minSdk) {
                            if (method.name.startsWith(SWITCH_TABLE_PREFIX)) {
                                // We're in a compiler-generated method to generate an
                                // array indexed by enum ordinal values to enum values. The enum
                                // itself must be requiring a higher API number than is
                                // currently used, but the call site for the switch statement
                                // will also be referencing it, so no need to report these
                                // calls.
                                break;
                            }

                            if (!checkingSuperClass
                                    && node.getOpcode() == Opcodes.INVOKEVIRTUAL
                                    && methodDefinedLocally(classNode, name, desc)) {
                                break;
                            }

                            String fqcn;
                            if (CONSTRUCTOR_NAME.equals(name)) {
                                fqcn = "new " + ClassContext.getFqcn(owner); //$NON-NLS-1$
                            } else {
                                fqcn = ClassContext.getFqcn(owner) + '#' + name;
                            }
                            String message = String.format(
                                    "Call requires API level %1$d (current min is %2$d): %3$s",
                                    api, minSdk, fqcn);

                            if (name.equals(ORDINAL_METHOD)
                                    && instruction.getNext() != null
                                    && instruction.getNext().getNext() != null
                                    && instruction.getNext().getOpcode() == Opcodes.IALOAD
                                    && instruction.getNext().getNext().getOpcode()
                                        == Opcodes.TABLESWITCH) {
                                message = String.format(
                                    "Enum for switch requires API level %1$d " +
                                    "(current min is %2$d): %3$s",
                                    api, minSdk, ClassContext.getFqcn(owner));
                            }

                            report(context, message, node, method, name, null,
                                    SearchHints.create(FORWARD).matchJavaSymbol());
                        }

                        // For virtual dispatch, walk up the inheritance chain checking
                        // each inherited method
                        if (owner.startsWith("android/")           //$NON-NLS-1$
                                || owner.startsWith("javax/")) {   //$NON-NLS-1$
                            // The API map has already inlined all inherited methods
                            // so no need to keep checking up the chain
                            owner = null;
                        } else if (owner.startsWith("java/")) {    //$NON-NLS-1$
                            if (owner.equals(LocaleDetector.DATE_FORMAT_OWNER)) {
                                checkSimpleDateFormat(context, method, node, minSdk);
                            }
                            // Already inlined; see comment above
                            owner = null;
                        } else if (node.getOpcode() == Opcodes.INVOKEVIRTUAL) {
                            owner = context.getDriver().getSuperClass(owner);
                        } else if (node.getOpcode() == Opcodes.INVOKESTATIC && api == -1) {
                            // Inherit through static classes as well
                            owner = context.getDriver().getSuperClass(owner);
                        } else {
                            owner = null;
                        }

                        checkingSuperClass = true;
                    }
                } else if (type == AbstractInsnNode.FIELD_INSN) {
                    FieldInsnNode node = (FieldInsnNode) instruction;
                    String name = node.name;
                    String owner = node.owner;
                    int api = mApiDatabase.getFieldVersion(owner, name);
                    if (api > minSdk) {
                        if (method.name.startsWith(SWITCH_TABLE_PREFIX)) {
                            checkSwitchBlock(context, classNode, node, method, name, owner,
                                    api, minSdk);
                            continue;
                        }
                        String fqcn = ClassContext.getFqcn(owner) + '#' + name;
                        String message = String.format(
                                "Field requires API level %1$d (current min is %2$d): %3$s",
                                api, minSdk, fqcn);
                        report(context, message, node, method, name, null,
                                SearchHints.create(FORWARD).matchJavaSymbol());
                    }
                } else if (type == AbstractInsnNode.LDC_INSN) {
                    LdcInsnNode node = (LdcInsnNode) instruction;
                    if (node.cst instanceof Type) {
                        Type t = (Type) node.cst;
                        String className = t.getInternalName();

                        int api = mApiDatabase.getClassVersion(className);
                        if (api > minSdk) {
                            String fqcn = ClassContext.getFqcn(className);
                            String message = String.format(
                                    "Class requires API level %1$d (current min is %2$d): %3$s",
                                    api, minSdk, fqcn);
                            report(context, message, node, method,
                                    className.substring(className.lastIndexOf('/') + 1), null,
                                    SearchHints.create(FORWARD).matchJavaSymbol());
                        }
                    }
                }
            }
        }
    }

    private static void checkSimpleDateFormat(ClassContext context, MethodNode method,
            MethodInsnNode node, int minSdk) {
        if (minSdk >= 9) {
            // Already OK
            return;
        }
        if (node.name.equals(CONSTRUCTOR_NAME) && !node.desc.equals("()V")) { //$NON-NLS-1$
            // Check first argument
            AbstractInsnNode prev = LintUtils.getPrevInstruction(node);
            if (prev != null && !node.desc.equals("(Ljava/lang/String;)V")) { //$NON-NLS-1$
                prev = LintUtils.getPrevInstruction(prev);
            }
            if (prev != null && prev.getOpcode() == Opcodes.LDC) {
                LdcInsnNode ldc = (LdcInsnNode) prev;
                Object cst = ldc.cst;
                if (cst instanceof String) {
                    String pattern = (String) cst;
                    boolean isEscaped = false;
                    for (int i = 0; i < pattern.length(); i++) {
                        char c = pattern.charAt(i);
                        if (c == '\'') {
                            isEscaped = !isEscaped;
                        } else  if (!isEscaped && (c == 'L' || c == 'c')) {
                            String message = String.format(
                                    "The pattern character '%1$c' requires API level 9 (current " +
                                    "min is %2$d) : \"%3$s\"", c, minSdk, pattern);
                            report(context, message, node, method, pattern, null,
                                    SearchHints.create(FORWARD));
                            return;
                        }
                    }
                }
            }
        }
    }

    @SuppressWarnings("rawtypes") // ASM API
    private static boolean methodDefinedLocally(ClassNode classNode, String name, String desc) {
        List methodList = classNode.methods;
        for (Object m : methodList) {
            MethodNode method = (MethodNode) m;
            if (name.equals(method.name) && desc.equals(method.desc)) {
                return true;
            }
        }

        return false;
    }

    @SuppressWarnings("rawtypes") // ASM API
    private static void checkSwitchBlock(ClassContext context, ClassNode classNode,
            FieldInsnNode field, MethodNode method, String name, String owner, int api,
            int minSdk) {
        // Switch statements on enums are tricky. The compiler will generate a method
        // which returns an array of the enum constants, indexed by their ordinal() values.
        // However, we only want to complain if the code is actually referencing one of
        // the non-available enum fields.
        //
        // For the android.graphics.PorterDuff.Mode enum for example, the first few items
        // in the array are populated like this:
        //
        //   L0
        //    ALOAD 0
        //    GETSTATIC android/graphics/PorterDuff$Mode.ADD : Landroid/graphics/PorterDuff$Mode;
        //    INVOKEVIRTUAL android/graphics/PorterDuff$Mode.ordinal ()I
        //    ICONST_1
        //    IASTORE
        //   L1
        //    GOTO L3
        //   L2
        //   FRAME FULL [[I] [java/lang/NoSuchFieldError]
        //    POP
        //   L3
        //   FRAME SAME
        //    ALOAD 0
        //    GETSTATIC android/graphics/PorterDuff$Mode.CLEAR : Landroid/graphics/PorterDuff$Mode;
        //    INVOKEVIRTUAL android/graphics/PorterDuff$Mode.ordinal ()I
        //    ICONST_2
        //    IASTORE
        //    ...
        // So if we for example find that the "ADD" field isn't accessible, since it requires
        // API 11, we need to
        //   (1) First find out what its ordinal number is. We can look at the following
        //       instructions to discover this; it's the "ICONST_1" and "IASTORE" instructions.
        //       (After ICONST_5 it moves on to BIPUSH 6, BIPUSH 7, etc.)
        //   (2) Find the corresponding *usage* of this switch method. For the above enum,
        //       the switch ordinal lookup method will be called
        //         "$SWITCH_TABLE$android$graphics$PorterDuff$Mode" with desc "()[I".
        //       This means we will be looking for an invocation in some other method which looks
        //       like this:
        //         INVOKESTATIC (current class).$SWITCH_TABLE$android$graphics$PorterDuff$Mode ()[I
        //       (obviously, it can be invoked more than once)
        //       Note that it can be used more than once in this class and all sites should be
        //       checked!
        //   (3) Look up the corresponding table switch, which should look something like this:
        //        INVOKESTATIC (current class).$SWITCH_TABLE$android$graphics$PorterDuff$Mode ()[I
        //        ALOAD 0
        //        INVOKEVIRTUAL android/graphics/PorterDuff$Mode.ordinal ()I
        //        IALOAD
        //        LOOKUPSWITCH
        //          2: L1
        //          11: L2
        //          default: L3
        //       Here we need to see if the LOOKUPSWITCH instruction is referencing our target
        //       case. Above we were looking for the "ADD" case which had ordinal 1. Since this
        //       isn't explicitly referenced, we can ignore this field reference.
        AbstractInsnNode next = field.getNext();
        if (next == null || next.getOpcode() != Opcodes.INVOKEVIRTUAL) {
            return;
        }
        next = next.getNext();
        if (next == null) {
            return;
        }
        int ordinal;
        switch (next.getOpcode()) {
            case Opcodes.ICONST_0: ordinal = 0; break;
            case Opcodes.ICONST_1: ordinal = 1; break;
            case Opcodes.ICONST_2: ordinal = 2; break;
            case Opcodes.ICONST_3: ordinal = 3; break;
            case Opcodes.ICONST_4: ordinal = 4; break;
            case Opcodes.ICONST_5: ordinal = 5; break;
            case Opcodes.BIPUSH: {
                IntInsnNode iin = (IntInsnNode) next;
                ordinal = iin.operand;
                break;
            }
            default:
                return;
        }

        // Find usages of this call site
        List methodList = classNode.methods;
        for (Object m : methodList) {
            InsnList nodes = ((MethodNode) m).instructions;
            for (int i = 0, n = nodes.size(); i < n; i++) {
                AbstractInsnNode instruction = nodes.get(i);
                if (instruction.getOpcode() != Opcodes.INVOKESTATIC){
                    continue;
                }
                MethodInsnNode node = (MethodInsnNode) instruction;
                if (node.name.equals(method.name)
                        && node.desc.equals(method.desc)
                        && node.owner.equals(classNode.name)) {
                    // Find lookup switch
                    AbstractInsnNode target = getNextInstruction(node);
                    while (target != null) {
                        if (target.getOpcode() == Opcodes.LOOKUPSWITCH) {
                            LookupSwitchInsnNode lookup = (LookupSwitchInsnNode) target;
                            @SuppressWarnings("unchecked") // ASM API
                            List<Integer> keys = lookup.keys;
                            if (keys != null && keys.contains(ordinal)) {
                                String fqcn = ClassContext.getFqcn(owner) + '#' + name;
                                String message = String.format(
                                        "Enum value requires API level %1$d " +
                                        "(current min is %2$d): %3$s",
                                        api, minSdk, fqcn);
                                report(context, message, lookup, (MethodNode) m, name, null,
                                        SearchHints.create(FORWARD).matchJavaSymbol());

                                // Break out of the inner target search only; the switch
                                // statement could be used in other places in this class as
                                // well and we want to report all problematic usages.
                                break;
                            }
                        }
                        target = getNextInstruction(target);
                    }
                }
            }
        }
    }

    /**
     * Return the {@code @TargetApi} level to use for the given {@code classNode};
     * this will be the {@code @TargetApi} annotation on the class, or any outer
     * methods (for anonymous inner classes) or outer classes (for inner classes)
     * of the given class.
     */
    private static int getClassMinSdk(ClassContext context, ClassNode classNode) {
        int classMinSdk = getLocalMinSdk(classNode.invisibleAnnotations);
        if (classMinSdk != -1) {
            return classMinSdk;
        }

        LintDriver driver = context.getDriver();
        while (classNode != null) {
            ClassNode prev = classNode;
            classNode = driver.getOuterClassNode(classNode);
            if (classNode != null) {
                // TODO: Should this be "curr" instead?
                if (prev.outerMethod != null) {
                    @SuppressWarnings("rawtypes") // ASM API
                    List methods = classNode.methods;
                    for (Object m : methods) {
                        MethodNode method = (MethodNode) m;
                        if (method.name.equals(prev.outerMethod)
                                && method.desc.equals(prev.outerMethodDesc)) {
                            // Found the outer method for this anonymous class; check method
                            // annotations on it, then continue up the class hierarchy
                            int methodMinSdk = getLocalMinSdk(method.invisibleAnnotations);
                            if (methodMinSdk != -1) {
                                return methodMinSdk;
                            }

                            break;
                        }
                    }
                }

                classMinSdk = getLocalMinSdk(classNode.invisibleAnnotations);
                if (classMinSdk != -1) {
                    return classMinSdk;
                }
            }
        }

        return -1;
    }

    /**
     * Returns the minimum SDK to use according to the given annotation list, or
     * -1 if no annotation was found.
     *
     * @param annotations a list of annotation nodes from ASM
     * @return the API level to use for this node, or -1
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int getLocalMinSdk(List annotations) {
        if (annotations != null) {
            for (AnnotationNode annotation : (List<AnnotationNode>)annotations) {
                String desc = annotation.desc;
                if (desc.endsWith(TARGET_API_VMSIG)) {
                    if (annotation.values != null) {
                        for (int i = 0, n = annotation.values.size(); i < n; i += 2) {
                            String key = (String) annotation.values.get(i);
                            if (key.equals("value")) {  //$NON-NLS-1$
                                Object value = annotation.values.get(i + 1);
                                if (value instanceof Integer) {
                                    return ((Integer) value).intValue();
                                }
                            }
                        }
                    }
                }
            }
        }

        return -1;
    }

    /**
     * Returns the minimum SDK to use in the given element context, or -1 if no
     * {@code tools:targetApi} attribute was found.
     *
     * @param element the element to look at, including parents
     * @return the API level to use for this element, or -1
     */
    private static int getLocalMinSdk(@NonNull Element element) {
        while (element != null) {
            String targetApi = element.getAttributeNS(TOOLS_URI, ATTR_TARGET_API);
            if (targetApi != null && !targetApi.isEmpty()) {
                if (Character.isDigit(targetApi.charAt(0))) {
                    try {
                        return Integer.parseInt(targetApi);
                    } catch (NumberFormatException nufe) {
                        break;
                    }
                }

                for (int api = 1; api < SdkConstants.HIGHEST_KNOWN_API; api++) {
                    String code = LintUtils.getBuildCode(api);
                    if (code != null && code.equalsIgnoreCase(targetApi)) {
                        return api;
                    }
                }
            }

            Node parent = element.getParentNode();
            if (parent != null && parent.getNodeType() == Node.ELEMENT_NODE) {
                element = (Element) parent;
            } else {
                break;
            }
        }

        return -1;
    }

    private static void report(final ClassContext context, String message, AbstractInsnNode node,
            MethodNode method, String patternStart, String patternEnd, SearchHints hints) {
        int lineNumber = node != null ? ClassContext.findLineNumber(node) : -1;

        // If looking for a constructor, the string we'll see in the source is not the
        // method name (<init>) but the class name
        if (patternStart != null && patternStart.equals(CONSTRUCTOR_NAME)
                && node instanceof MethodInsnNode) {
            if (hints != null) {
                hints = hints.matchConstructor();
            }
            patternStart = ((MethodInsnNode) node).owner;
        }

        if (patternStart != null) {
            int index = patternStart.lastIndexOf('$');
            if (index != -1) {
                patternStart = patternStart.substring(index + 1);
            }
            index = patternStart.lastIndexOf('/');
            if (index != -1) {
                patternStart = patternStart.substring(index + 1);
            }
        }

        Location location = context.getLocationForLine(lineNumber, patternStart, patternEnd,
                hints);
        context.report(UNSUPPORTED, method, node, location, message, null);
    }
}
