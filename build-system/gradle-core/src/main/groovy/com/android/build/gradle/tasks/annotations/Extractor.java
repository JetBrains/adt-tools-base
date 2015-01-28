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

package com.android.build.gradle.tasks.annotations;

import static com.android.SdkConstants.AMP_ENTITY;
import static com.android.SdkConstants.APOS_ENTITY;
import static com.android.SdkConstants.DOT_JAR;
import static com.android.SdkConstants.DOT_XML;
import static com.android.SdkConstants.GT_ENTITY;
import static com.android.SdkConstants.INT_DEF_ANNOTATION;
import static com.android.SdkConstants.LT_ENTITY;
import static com.android.SdkConstants.QUOT_ENTITY;
import static com.android.SdkConstants.STRING_DEF_ANNOTATION;
import static com.android.SdkConstants.SUPPORT_ANNOTATIONS_PREFIX;
import static com.android.SdkConstants.TYPE_DEF_FLAG_ATTRIBUTE;
import static com.android.SdkConstants.TYPE_DEF_VALUE_ATTRIBUTE;
import static com.android.SdkConstants.VALUE_TRUE;
import static com.android.tools.lint.detector.api.LintUtils.assertionsEnabled;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.utils.XmlUtils;
import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closeables;
import com.google.common.io.Files;
import com.google.common.xml.XmlEscapers;

import org.eclipse.jdt.internal.compiler.ASTVisitor;
import org.eclipse.jdt.internal.compiler.ast.AbstractMethodDeclaration;
import org.eclipse.jdt.internal.compiler.ast.Annotation;
import org.eclipse.jdt.internal.compiler.ast.Argument;
import org.eclipse.jdt.internal.compiler.ast.ArrayInitializer;
import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration;
import org.eclipse.jdt.internal.compiler.ast.ConstructorDeclaration;
import org.eclipse.jdt.internal.compiler.ast.Expression;
import org.eclipse.jdt.internal.compiler.ast.FalseLiteral;
import org.eclipse.jdt.internal.compiler.ast.FieldDeclaration;
import org.eclipse.jdt.internal.compiler.ast.MemberValuePair;
import org.eclipse.jdt.internal.compiler.ast.MethodDeclaration;
import org.eclipse.jdt.internal.compiler.ast.NameReference;
import org.eclipse.jdt.internal.compiler.ast.NumberLiteral;
import org.eclipse.jdt.internal.compiler.ast.StringLiteral;
import org.eclipse.jdt.internal.compiler.ast.TrueLiteral;
import org.eclipse.jdt.internal.compiler.ast.TypeDeclaration;
import org.eclipse.jdt.internal.compiler.impl.ReferenceContext;
import org.eclipse.jdt.internal.compiler.lookup.Binding;
import org.eclipse.jdt.internal.compiler.lookup.BlockScope;
import org.eclipse.jdt.internal.compiler.lookup.ClassScope;
import org.eclipse.jdt.internal.compiler.lookup.FieldBinding;
import org.eclipse.jdt.internal.compiler.lookup.LocalVariableBinding;
import org.eclipse.jdt.internal.compiler.lookup.MethodBinding;
import org.eclipse.jdt.internal.compiler.lookup.MethodScope;
import org.eclipse.jdt.internal.compiler.lookup.Scope;
import org.eclipse.jdt.internal.compiler.lookup.TypeBinding;
import org.eclipse.jdt.internal.compiler.lookup.TypeIds;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;

/**
 * Annotation extractor which looks for annotations in parsed compilation units and writes
 * the annotations into a format suitable for use by IntelliJ and Android Studio etc;
 * it's basically an XML file, organized by package, which lists the signatures for
 * fields and methods in classes in the given package, and identifiers method parameters
 * by index, and lists the annotations annotated on that element.
 * <p>
 * This is primarily intended for use in Android libraries such as the support library,
 * where you want to use the resource int ({@code StringRes}, {@code DrawableRes}, and so on)
 * annotations to indicate what types of id's are expected, or the {@code IntDef} or
 * {@code StringDef} annotations to record which specific constants are allowed in int and
 * String parameters.
 * <p>
 * However, the code is also used to extract SDK annotations from the platform, where
 * the package names of the annotations differ slightly (and where the nullness annotations
 * do not have class retention for example). Therefore, this code contains some extra
 * support not needed when extracting annotations in an Android library, such as code
 * to skip annotations for any method/field not mentioned in the API database, and code
 * to rewrite the android.jar file to insert annotations in the generated bytecode.
 * <p>
 * TODO:
 * - Warn if the {@code @IntDef} annotation is used on a non-int, and similarly if
 *   {@code @StringDef} is used on a non-string
 * - Ignore annotations defined on @hide elements
 */
public class Extractor {
    /**
     * Whether we should include class-retention annotations into the extracted file;
     * we don't need {@code android.support.annotation.Nullable} to be in the extracted XML
     * file since it has class retention and will appear in the compiled .jar version of
     * the library
     */
    private static final boolean INCLUDE_CLASS_RETENTION_ANNOTATIONS = false;

    /**
     * Whether we should skip nullable annotations in merged in annotations zip files
     * (these are typically from infer nullity, which sometimes is a bit aggressive
     * in assuming something should be marked as nullable; see for example issue #66999
     * or all the manual removals of findViewById @Nullable return value annotations
     */
    private static final boolean INCLUDE_INFERRED_NULLABLE = false;

    public static final String ANDROID_ANNOTATIONS_PREFIX = "android.annotation.";
    public static final String ANDROID_NULLABLE = "android.annotation.Nullable";
    public static final String SUPPORT_NULLABLE = "android.support.annotation.Nullable";
    public static final String RESOURCE_TYPE_ANNOTATIONS_SUFFIX = "Res";
    public static final String ANDROID_NOTNULL = "android.annotation.NonNull";
    public static final String SUPPORT_NOTNULL = "android.support.annotation.NonNull";
    public static final String ANDROID_INT_DEF = "android.annotation.IntDef";
    public static final String ANDROID_STRING_DEF = "android.annotation.StringDef";
    public static final String IDEA_NULLABLE = "org.jetbrains.annotations.Nullable";
    public static final String IDEA_NOTNULL = "org.jetbrains.annotations.NotNull";
    public static final String IDEA_MAGIC = "org.intellij.lang.annotations.MagicConstant";
    public static final String IDEA_CONTRACT = "org.jetbrains.annotations.Contract";
    public static final String IDEA_NON_NLS = "org.jetbrains.annotations.NonNls";

    @NonNull
    private final Map<String, AnnotationData> types = Maps.newHashMap();

    @NonNull
    private final Set<String> irrelevantAnnotations = Sets.newHashSet();

    private final File classDir;

    @NonNull
    private Map<String, Map<String, List<Item>>> itemMap = Maps.newHashMap();

    @Nullable
    private final ApiDatabase apiFilter;

    private final boolean displayInfo;

    private Map<String,Integer> stats = Maps.newHashMap();
    private int filteredCount;
    private int mergedCount;
    private Set<CompilationUnitDeclaration> processedFiles = Sets.newHashSetWithExpectedSize(100);
    private Set<String> ignoredAnnotations = Sets.newHashSet();
    private boolean listIgnored;
    private Map<String,Annotation> typedefs;
    private List<File> classFiles;

    public Extractor(@Nullable ApiDatabase apiFilter, @Nullable File classDir, boolean displayInfo) {
        this.apiFilter = apiFilter;
        this.listIgnored = apiFilter != null;
        this.classDir = classDir;
        this.displayInfo = displayInfo;
    }

    public void extractFromProjectSource(Collection<CompilationUnitDeclaration> units) {
        TypedefCollector collector = new TypedefCollector(units, false /*requireHide*/,
                true /*requireSourceRetention*/);
        typedefs = collector.getTypedefs();
        classFiles = collector.getNonPublicTypedefClassFiles();

        for (CompilationUnitDeclaration unit : units) {
            analyze(unit);
        }
    }

    public void removeTypedefClasses() {
        if (classDir != null && classFiles != null && !classFiles.isEmpty()) {
            int count = 0;
            for (File file : classFiles) {
                if (!file.isAbsolute()) {
                    file = new File(classDir, file.getPath());
                }
                if (file.exists()) {
                    boolean deleted = file.delete();
                    if (deleted) {
                        count++;
                    } else {
                        warning("Could not delete typedef class " + file.getPath());
                    }
                }
            }
            info("Deleted " + count + " typedef annotation classes");
        }
    }

    public void export(@NonNull File output) {
        if (itemMap.isEmpty()) {
            if (output.exists()) {
                //noinspection ResultOfMethodCallIgnored
                output.delete();
            }
        } else if (writeOutputFile(output)) {
            writeStats();
            info("Annotations written to " + output);
        }
    }

    public void writeStats() {
        if (!displayInfo) {
            return;
        }

        if (!stats.isEmpty()) {
            List<String> annotations = Lists.newArrayList(stats.keySet());
            Collections.sort(annotations, new Comparator<String>() {
                @Override
                public int compare(String s1, String s2) {
                    int frequency1 = stats.get(s1);
                    int frequency2 = stats.get(s2);
                    int delta = frequency2 - frequency1;
                    if (delta != 0) {
                        return delta;
                    }
                    return s1.compareTo(s2);
                }
            });
            Map<String,String> fqnToName = Maps.newHashMap();
            int max = 0;
            int count = 0;
            for (String fqn : annotations) {
                String name = fqn.substring(fqn.lastIndexOf('.') + 1);
                fqnToName.put(fqn, name);
                max = Math.max(max, name.length());
                count += stats.get(fqn);
            }

            StringBuilder sb = new StringBuilder(200);
            sb.append("Extracted ").append(count).append(" Annotations:");
            for (String fqn : annotations) {
                sb.append('\n');
                String name = fqnToName.get(fqn);
                for (int i = 0, n = max - name.length() + 1; i < n; i++) {
                    sb.append(' ');
                }
                sb.append('@');
                sb.append(name);
                sb.append(':').append(' ');
                sb.append(Integer.toString(stats.get(fqn)));
            }
            if (sb.length() > 0) {
                info(sb.toString());
            }
        }

        if (filteredCount > 0) {
            info(filteredCount + " of these were filtered out (not in API database file)");
        }
        if (mergedCount > 0) {
            info(mergedCount + " additional annotations were merged in");
        }
    }

    @SuppressWarnings("UseOfSystemOutOrSystemErr")
    void info(final String message) {
        if (displayInfo) {
            System.out.println(message);
        }
    }

    @SuppressWarnings("UseOfSystemOutOrSystemErr")
    static void error(String message) {
        System.err.println("Error: " + message);
    }

    @SuppressWarnings("UseOfSystemOutOrSystemErr")
    static void warning(String message) {
        System.out.println("Warning: " + message);
    }

    private void analyze(CompilationUnitDeclaration unit) {
        if (processedFiles.contains(unit)) {
            // The code to process all roots seems to hit some of the same classes
            // repeatedly... so filter these out manually
            return;
        }
        processedFiles.add(unit);

        AnnotationVisitor visitor = new AnnotationVisitor();
        unit.traverse(visitor, unit.scope);
    }

    @Nullable
    private static ClassScope findClassScope(Scope scope) {
        while (scope != null) {
            if (scope instanceof ClassScope) {
                return (ClassScope)scope;
            }
            scope = scope.parent;
        }

        return null;
    }

    @Nullable
    static String getFqn(@NonNull Annotation annotation) {
        if (annotation.resolvedType != null) {
            return new String(annotation.resolvedType.readableName());
        }
        return null;
    }

    @Nullable
    private static String getFqn(@NonNull ClassScope scope) {
        TypeDeclaration typeDeclaration = scope.referenceType();
        if (typeDeclaration != null && typeDeclaration.binding != null) {
            return new String(typeDeclaration.binding.readableName());
        }
        return null;
    }

    @Nullable
    private static String getFqn(@NonNull MethodScope scope) {
        ClassScope classScope = findClassScope(scope);
        if (classScope != null) {
            return getFqn(classScope);
        }

        return null;
    }

    @Nullable
    private static String getFqn(@NonNull BlockScope scope) {
        ClassScope classScope = findClassScope(scope);
        if (classScope != null) {
            return getFqn(classScope);
        }

        return null;
    }

    static boolean hasSourceRetention(@NonNull Annotation[] annotations) {
        for (Annotation annotation : annotations) {
            String typeName = Extractor.getFqn(annotation);
            if ("java.lang.annotation.Retention".equals(typeName)) {
                MemberValuePair[] pairs = annotation.memberValuePairs();
                if (pairs == null || pairs.length != 1) {
                    warning("Expected exactly one parameter passed to @Retention");
                    return false;
                }
                MemberValuePair pair = pairs[0];
                Expression value = pair.value;
                if (value instanceof NameReference) {
                    NameReference reference = (NameReference) value;
                    Binding binding = reference.binding;
                    if (binding != null) {
                        if (binding instanceof FieldBinding) {
                            FieldBinding fb = (FieldBinding) binding;
                            if ("SOURCE".equals(new String(fb.name)) &&
                                    "java.lang.annotation.RetentionPolicy".equals(
                                            new String(fb.declaringClass.readableName()))) {
                                return true;
                            }

                        }
                    }
                }
            }
        }

        return false;
    }

    private void addAnnotations(@Nullable Annotation[] annotations, @NonNull Item item) {
        if (annotations != null) {
            for (Annotation annotation : annotations) {
                AnnotationData annotationData = createAnnotation(annotation);
                if (annotationData != null) {
                    item.annotations.add(annotationData);
                }
            }
        }
    }

    @Nullable
    private AnnotationData createAnnotation(@NonNull Annotation annotation) {
        String fqn = getFqn(annotation);
        if (fqn == null) {
            return null;
        }

        if (fqn.equals(ANDROID_NULLABLE) || fqn.equals(SUPPORT_NULLABLE)) {
            recordStats(fqn);
            return new AnnotationData(SUPPORT_NULLABLE);
        }

        if (fqn.equals(ANDROID_NOTNULL) || fqn.equals(SUPPORT_NOTNULL)) {
            recordStats(fqn);
            return new AnnotationData(SUPPORT_NOTNULL);
        }

        if (fqn.startsWith(SUPPORT_ANNOTATIONS_PREFIX)
                && fqn.endsWith(RESOURCE_TYPE_ANNOTATIONS_SUFFIX)) {
            recordStats(fqn);
            return new AnnotationData(fqn);
        }

        AnnotationData typedef = types.get(fqn);
        if (typedef != null) {
            return typedef;
        }

        boolean intDef = fqn.equals(ANDROID_INT_DEF) || fqn.equals(INT_DEF_ANNOTATION);
        boolean stringDef = fqn.equals(ANDROID_STRING_DEF) || fqn.equals(STRING_DEF_ANNOTATION);
        if (intDef || stringDef) {
            AtomicBoolean isFlag = new AtomicBoolean(false);
            String constants = getAnnotationConstants(annotation, isFlag);
            if (constants == null) {
                return null;
            }
            boolean flag = intDef && isFlag.get();
            recordStats(fqn);
            return new AnnotationData(
                    intDef ? INT_DEF_ANNOTATION : STRING_DEF_ANNOTATION,
                    TYPE_DEF_VALUE_ATTRIBUTE, constants,
                    flag ? TYPE_DEF_FLAG_ATTRIBUTE : null, flag ? VALUE_TRUE : null);
        }

        return null;
    }

    private void recordStats(String fqn) {
        Integer count = stats.get(fqn);
        if (count == null) {
            count = 0;
        }
        stats.put(fqn, count + 1);
    }

    @Nullable
    private String getAnnotationConstants(
            @NonNull Annotation annotation,
            @NonNull AtomicBoolean flag /*out*/) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');

        MemberValuePair[] annotationParameters = annotation.memberValuePairs();
        if (annotationParameters != null) {
            for (MemberValuePair pair : annotationParameters) {
                String name = pair.name != null ? new String(pair.name) : TYPE_DEF_VALUE_ATTRIBUTE;
                if (name.equals(TYPE_DEF_FLAG_ATTRIBUTE)) {
                    if (pair.value instanceof TrueLiteral) {
                        flag.set(true);
                    } else if (pair.value instanceof FalseLiteral) {
                        flag.set(false);
                    } else {
                        warning("Unexpected type of literal for annotation "
                                + "flag value: " + pair.value);
                    }

                    continue;
                }
                if (pair.value instanceof ArrayInitializer) {
                    ArrayInitializer arrayInitializer = (ArrayInitializer) pair.value;
                    appendConstants(sb, arrayInitializer);
                } else {
                    warning("Unexpected type for annotation initializer " + pair.value);
                }

            }
        }

        sb.append('}');
        return sb.toString();
    }

    private void appendConstants(StringBuilder sb, ArrayInitializer mv) {
        boolean first = true;
        for (Expression v : mv.expressions) {
            if (v instanceof NameReference) {
                NameReference reference = (NameReference) v;
                if (reference.binding != null) {
                    if (reference.binding instanceof FieldBinding) {
                        FieldBinding fb = (FieldBinding)reference.binding;
                        if (fb.declaringClass != null) {
                            if (apiFilter != null &&
                                    !apiFilter.hasField(
                                            new String(fb.declaringClass.readableName()),
                                            new String(fb.name))) {
                                if (isListIgnored()) {
                                    info("Filtering out typedef constant "
                                            + new String(fb.declaringClass.readableName()) + "."
                                            + new String(fb.name) + "");
                                }
                                continue;
                            }

                            if (first) {
                                first = false;
                            } else {
                                sb.append(", ");
                            }
                            sb.append(fb.declaringClass.readableName());
                            sb.append('.');
                            sb.append(fb.name);
                        } else {
                            sb.append(reference.binding.readableName());
                        }
                    } else {
                        sb.append(reference.binding.readableName());
                    }
                } else {
                    warning("No binding for reference " + reference);
                }
            } else if (v instanceof StringLiteral) {
                if (first) {
                    first = false;
                } else {
                    sb.append(", ");
                }
                StringLiteral s = (StringLiteral) v;
                sb.append('"');
                sb.append(s.source());
                sb.append('"');
            } else if (v instanceof NumberLiteral) {
                if (first) {
                    first = false;
                } else {
                    sb.append(", ");
                }
                NumberLiteral number = (NumberLiteral) v;
                sb.append(number.source());
            } else {
                // BinaryExpression etc can happen if you put "3 + 4" in as an integer!
                if (v.constant != null) {
                    if (v.constant.typeID() == TypeIds.T_int) {
                        if (first) {
                            first = false;
                        } else {
                            sb.append(", ");
                        }
                        sb.append(v.constant.intValue());
                    } else if (v.constant.typeID() == TypeIds.T_JavaLangString) {
                        if (first) {
                            first = false;
                        } else {
                            sb.append(", ");
                        }
                        sb.append('"');
                        sb.append(v.constant.stringValue());
                        sb.append('"');
                    } else {
                        warning("Unexpected type for constant " + v.constant.toString());
                    }
                } else {
                    warning("Unexpected annotation expression of type " + v.getClass() + " and is "
                                    + v);
                }
            }
        }
    }

    private boolean hasRelevantAnnotations(@Nullable Annotation[] annotations) {
        if (annotations == null) {
            return false;
        }

        for (Annotation annotation : annotations) {
            String fqn = getFqn(annotation);
            if (fqn == null) {
                continue;
            }
            if (fqn.startsWith(SUPPORT_ANNOTATIONS_PREFIX)) {
                //noinspection PointlessBooleanExpression,ConstantConditions,RedundantIfStatement
                if (!INCLUDE_CLASS_RETENTION_ANNOTATIONS &&
                        (SUPPORT_NULLABLE.equals(fqn) ||
                        SUPPORT_NOTNULL.equals(fqn))) {
                    // @Nullable and @NonNull in the support package have class
                    // retention; don't include them in the side-door file!
                    return false;
                }

                return true;
            }
            if (fqn.equals(ANDROID_NULLABLE) || fqn.equals(ANDROID_NOTNULL)
                    || isMagicConstant(fqn)) {
                return true;
            } else if (fqn.equals(IDEA_CONTRACT)) {
                return true;
            }
        }

        return false;
    }

    boolean isMagicConstant(String typeName) {
        if (irrelevantAnnotations.contains(typeName)
                || typeName.startsWith("java.lang.")) { // @Override, @SuppressWarnings, etc.
            return false;
        }
        if (types.containsKey(typeName) ||
                typeName.equals(INT_DEF_ANNOTATION) ||
                typeName.equals(STRING_DEF_ANNOTATION) ||
                typeName.equals(ANDROID_INT_DEF) ||
                typeName.equals(ANDROID_STRING_DEF)) {
            return true;
        }

        Annotation typeDef = typedefs.get(typeName);
        // We only support a single level of IntDef type annotations, not arbitrary nesting
        if (typeDef != null) {
            String fqn = getFqn(typeDef);
            if (fqn != null &&
                    (fqn.equals(INT_DEF_ANNOTATION) ||
                            fqn.equals(STRING_DEF_ANNOTATION) ||
                            fqn.equals(ANDROID_INT_DEF) ||
                            fqn.equals(ANDROID_STRING_DEF))) {
                AnnotationData a = createAnnotation(typeDef);
                if (a != null) {
                    types.put(typeName, a);
                    return true;
                }
            }
        }


        irrelevantAnnotations.add(typeName);

        return false;
    }

    private boolean writeOutputFile(File dest) {
        try {
            FileOutputStream fileOutputStream = new FileOutputStream(dest);
            JarOutputStream zos = new JarOutputStream(fileOutputStream);
            try {
                List<String> sortedPackages = new ArrayList<String>(itemMap.keySet());
                Collections.sort(sortedPackages);
                for (String pkg : sortedPackages) {
                    // Note: Using / rather than File.separator: jar lib requires it
                    String name = pkg.replace('.', '/') + "/annotations.xml";

                    JarEntry outEntry = new JarEntry(name);
                    zos.putNextEntry(outEntry);

                    StringWriter stringWriter = new StringWriter(1000);
                    PrintWriter writer = new PrintWriter(stringWriter);
                    try {
                        writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                                "<root>");

                        Map<String, List<Item>> classMap = itemMap.get(pkg);
                        List<String> classes = new ArrayList<String>(classMap.keySet());
                        Collections.sort(classes);
                        for (String cls : classes) {
                            List<Item> items = classMap.get(cls);
                            Collections.sort(items);
                            for (Item item : items) {
                                item.write(writer);
                            }
                        }

                        writer.println("</root>\n");
                        writer.close();
                        String xml = stringWriter.toString();

                        // Validate
                        if (assertionsEnabled()) {
                            Document document = checkDocument(xml, false);
                            if (document == null) {
                                error("Could not parse XML document back in for entry " + name
                                        + ": invalid XML?\n\"\"\"\n" + xml + "\n\"\"\"\n");
                                return false;
                            }
                        }

                        byte[] bytes = xml.getBytes(Charsets.UTF_8);
                        zos.write(bytes);
                        zos.closeEntry();
                    } finally {
                        writer.close();
                    }
                }
            } finally {
                zos.flush();
                zos.close();
            }
        } catch (IOException ioe) {
            error(ioe.toString());
            return false;
        }

        return true;
    }

    /**
     *  Returns the annotation name to use, if any. The index is the parameter index, or -1
     *  for the method return value.
     */
    String getMethodAnnotation(@NonNull String fqn, @NonNull String name,
            @Nullable String returnType, @NonNull String parameterList, boolean isConstructor,
            int index) {
        if (index == -1) {
            MethodItem item = new MethodItem(fqn, returnType, name, parameterList,
                    isConstructor);
            return getJarMarkerAnnotationName(findItem(fqn, item));
        } else {
            ParameterItem item = new ParameterItem(fqn, returnType, name, parameterList,
                    isConstructor, Integer.toString(index));
            return getJarMarkerAnnotationName(findItem(fqn, item));
        }
    }

    String getFieldAnnotation(@NonNull String fqn, @NonNull String name) {
        FieldItem item = new FieldItem(fqn, name);
        return getJarMarkerAnnotationName(findItem(fqn, item));
    }

    /** Returns the marker annotation name to write into android.jar, if any */
    @Nullable
    private static String getJarMarkerAnnotationName(Item item) {
        if (item != null) {
            for (AnnotationData annotation : item.annotations) {
                if (annotation.name.equals(IDEA_NOTNULL)
                        || annotation.name.equals(ANDROID_NOTNULL)) {
                    return SUPPORT_NOTNULL;
                } else if (annotation.name.equals(IDEA_NULLABLE)
                        || annotation.name.equals(ANDROID_NULLABLE)) {
                    return SUPPORT_NULLABLE;
                }
            }
        }

        return null;
    }

    private void addItem(@NonNull String fqn, @NonNull Item item) {
        // Not part of the API?
        if (apiFilter != null && item.isFiltered(apiFilter)) {
            if (isListIgnored()) {
                info("Skipping API because it is not part of the API file: " + item);
            }

            filteredCount++;
            return;
        }

        String pkg = getPackage(fqn);
        Map<String, List<Item>> classMap = itemMap.get(pkg);
        if (classMap == null) {
            classMap = Maps.newHashMapWithExpectedSize(100);
            itemMap.put(pkg, classMap);
        }
        List<Item> items = classMap.get(fqn);
        if (items == null) {
            items = Lists.newArrayList();
            classMap.put(fqn, items);
        }

        items.add(item);
    }

    @Nullable
    private Item findItem(@NonNull String fqn, @NonNull Item item) {
        String pkg = getPackage(fqn);
        Map<String, List<Item>> classMap = itemMap.get(pkg);
        if (classMap == null) {
            return null;
        }
        List<Item> items = classMap.get(fqn);
        if (items == null) {
            return null;
        }
        for (Item existing : items) {
            if (existing.equals(item)) {
                return existing;
            }
        }

        return null;
    }

    @Nullable
    private static Document checkDocument(@NonNull String xml, boolean namespaceAware) {
        try {
            return XmlUtils.parseDocument(xml, namespaceAware);
        } catch (SAXException sax) {
            warning(sax.toString());
        } catch (Exception e) {
            // pass
            // This method is deliberately silent; will return null
        }

        return null;
    }

    public void mergeExisting(@NonNull File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File child : files) {
                    mergeExisting(child);
                }
            }
        } else if (file.isFile()) {
            if (file.getPath().endsWith(DOT_JAR)) {
                mergeFromJar(file);
            } else if (file.getPath().endsWith(DOT_XML)) {
                try {
                    String xml = Files.toString(file, Charsets.UTF_8);
                    mergeAnnotationsXml(xml);
                } catch (IOException e) {
                    error("Aborting: I/O problem during transform: " + e.toString());
                }
            }
        }
    }

    private void mergeFromJar(@NonNull File jar) {
        // Reads in an existing annotations jar and merges in entries found there
        // with the annotations analyzed from source.
        JarInputStream zis = null;
        try {
            @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
            FileInputStream fis = new FileInputStream(jar);
            zis = new JarInputStream(fis);
            ZipEntry entry = zis.getNextEntry();
            while (entry != null) {
                if (entry.getName().endsWith(".xml")) {
                    byte[] bytes = ByteStreams.toByteArray(zis);
                    String xml = new String(bytes, Charsets.UTF_8);
                    mergeAnnotationsXml(xml);
                }
                entry = zis.getNextEntry();
            }
        } catch (IOException e) {
            error("Aborting: I/O problem during transform: " + e.toString());
        } finally {
            //noinspection deprecation
            try {
                Closeables.close(zis, true /* swallowIOException */);
            } catch (IOException e) {
                // cannot happen
            }
        }
    }

    private void mergeAnnotationsXml(@NonNull String xml) {
        try {
            Document document = XmlUtils.parseDocument(xml, false);
            mergeDocument(document);
        } catch (Exception e) {
            warning(e.toString());
        }
    }

    private void mergeDocument(@NonNull Document document) {
        final Pattern XML_SIGNATURE = Pattern.compile(
                // Class (FieldName | Type? Name(ArgList) Argnum?)
                //"(\\S+) (\\S+|(.*)\\s+(\\S+)\\((.*)\\)( \\d+)?)");
                "(\\S+) (\\S+|((.*)\\s+)?(\\S+)\\((.*)\\)( \\d+)?)");

        Element root = document.getDocumentElement();
        String rootTag = root.getTagName();
        assert rootTag.equals("root") : rootTag;

        for (Element item : getChildren(root)) {
            String signature = item.getAttribute("name");
            if (signature == null || signature.equals("null")) {
                continue; // malformed item
            }

            if (!hasRelevantAnnotations(item)) {
                continue;
            }

            signature = unescapeXml(signature);
            Matcher matcher = XML_SIGNATURE.matcher(signature);
            if (matcher.matches()) {
                String containingClass = matcher.group(1);
                if (containingClass == null) {
                    warning("Could not find class for " + signature);
                }
                String methodName = matcher.group(5);
                if (methodName != null) {
                    String type = matcher.group(4);
                    boolean isConstructor = type == null;
                    String parameters = matcher.group(6);
                    mergeMethodOrParameter(item, matcher, containingClass, methodName, type,
                            isConstructor, parameters);
                } else {
                    String fieldName = matcher.group(2);
                    mergeField(item, containingClass, fieldName);
                }
            } else {
                if (signature.indexOf(' ') != -1 || signature.indexOf('.') == -1) {
                    warning("No merge match for signature " + signature);
                } // else: probably just a class signature, e.g. for @NonNls
            }
        }
    }

    @NonNull
    private static String unescapeXml(@NonNull String escaped) {
        String workingString = escaped.replace(QUOT_ENTITY, "\"");
        workingString = workingString.replace(LT_ENTITY, "<");
        workingString = workingString.replace(GT_ENTITY, ">");
        workingString = workingString.replace(APOS_ENTITY, "'");
        workingString = workingString.replace(AMP_ENTITY, "&");

        return workingString;
    }

    @NonNull
    private static String escapeXml(@NonNull String unescaped) {
        String escaped = XmlEscapers.xmlAttributeEscaper().escape(unescaped);
        assert unescaped.equals(unescapeXml(escaped)) : unescaped + " to " + escaped;
        return escaped;
    }

    private void mergeField(Element item, String containingClass, String fieldName) {
        if (apiFilter != null &&
                !apiFilter.hasField(containingClass, fieldName)) {
            if (isListIgnored()) {
                info("Skipping imported element because it is not part of the API file: "
                        + containingClass + "#" + fieldName);
            }
            filteredCount++;
        } else {
            FieldItem fieldItem = new FieldItem(containingClass, fieldName);
            Item existing = findItem(containingClass, fieldItem);
            if (existing != null) {
                mergedCount += mergeAnnotations(item, existing);
            } else {
                addItem(containingClass, fieldItem);
                mergedCount += addAnnotations(item, fieldItem);
            }
        }
    }

    private void mergeMethodOrParameter(Element item, Matcher matcher, String containingClass,
            String methodName, String type, boolean constructor, String parameters) {
        parameters = fixParameterString(parameters);

        if (apiFilter != null &&
                !apiFilter.hasMethod(containingClass, methodName, parameters)) {
            if (isListIgnored()) {
                info("Skipping imported element because it is not part of the API file: "
                        + containingClass + "#" + methodName + "(" + parameters + ")");
            }
            filteredCount++;
            return;
        }

        String argNum = matcher.group(7);
        if (argNum != null) {
            argNum = argNum.trim();
            ParameterItem parameterItem = new ParameterItem(containingClass, type,
                    methodName, parameters, constructor, argNum);
            Item existing = findItem(containingClass, parameterItem);
            if (existing != null) {
                mergedCount += mergeAnnotations(item, existing);
            } else {
                addItem(containingClass, parameterItem);
                mergedCount += addAnnotations(item, parameterItem);
            }
        } else {
            MethodItem methodItem = new MethodItem(containingClass, type, methodName,
                    parameters, constructor);
            Item existing = findItem(containingClass, methodItem);
            if (existing != null) {
                mergedCount += mergeAnnotations(item, existing);
            } else {
                addItem(containingClass, methodItem);
                mergedCount += addAnnotations(item, methodItem);
            }
        }
    }

    // The parameter declaration used in XML files should not have duplicated spaces,
    // and there should be no space after commas (we can't however strip out all spaces,
    // since for example the spaces around the "extends" keyword needs to be there in
    // types like Map<String,? extends Number>
    private static String fixParameterString(String parameters) {
        return parameters.replaceAll("  ", " ").replace(", ", ",");
    }

    private boolean hasRelevantAnnotations(Element item) {
        for (Element annotationElement : getChildren(item)) {
            if (isRelevantAnnotation(annotationElement)) {
                return true;
            }
        }

        return false;
    }


    private boolean isRelevantAnnotation(Element annotationElement) {
        AnnotationData annotation = createAnnotation(annotationElement);
        if (isNullable(annotation.name) || isNonNull(annotation.name)
                || annotation.name.startsWith(ANDROID_ANNOTATIONS_PREFIX)
                || annotation.name.startsWith(SUPPORT_ANNOTATIONS_PREFIX)) {
            return true;
        } else if (annotation.name.equals(IDEA_CONTRACT)) {
            return true;
        } else if (annotation.name.equals(IDEA_NON_NLS)) {
            return false;
        } else {
            if (!ignoredAnnotations.contains(annotation.name)) {
                ignoredAnnotations.add(annotation.name);
                if (isListIgnored()) {
                    info("(Ignoring merge annotation " + annotation.name + ")");
                }
            }
        }

        return false;
    }

    @NonNull
    private static List<Element> getChildren(@NonNull Element element) {
        NodeList itemList = element.getChildNodes();
        int length = itemList.getLength();
        List<Element> result = new ArrayList<Element>(Math.max(5, length / 2 + 1));
        for (int i = 0; i < length; i++) {
            Node node = itemList.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            result.add((Element) node);
        }

        return result;
    }

    private int addAnnotations(Element itemElement, Item item) {
        int count = 0;
        for (Element annotationElement : getChildren(itemElement)) {
            if (!isRelevantAnnotation(annotationElement)) {
                continue;
            }
            AnnotationData annotation = createAnnotation(annotationElement);
            item.annotations.add(annotation);
            count++;
        }
        return count;
    }

    private int mergeAnnotations(Element itemElement, Item item) {
        int count = 0;
        loop:
        for (Element annotationElement : getChildren(itemElement)) {
            if (!isRelevantAnnotation(annotationElement)) {
                continue;
            }
            AnnotationData annotation = createAnnotation(annotationElement);
            boolean haveNullable = false;
            boolean haveNotNull = false;
            for (AnnotationData existing : item.annotations) {
                if (isNonNull(existing.name)) {
                    haveNotNull = true;
                }
                if (isNullable(existing.name)) {
                    haveNullable = true;
                }
                if (existing.equals(annotation)) {
                    continue loop;
                }
            }

            // Make sure we don't have a conflict between nullable and not nullable
            if (isNonNull(annotation.name) && haveNullable ||
                    isNullable(annotation.name) && haveNotNull) {
                warning("Found both @Nullable and @NonNull after import for " + item);
                continue;
            }

            item.annotations.add(annotation);
            count++;
        }

        return count;
    }

    private static boolean isNonNull(String name) {
        return name.equals(IDEA_NOTNULL)
                || name.equals(ANDROID_NOTNULL)
                || name.equals(SUPPORT_NOTNULL);
    }

    private static boolean isNullable(String name) {
        return name.equals(IDEA_NULLABLE)
                || name.equals(ANDROID_NULLABLE)
                || name.equals(SUPPORT_NULLABLE);
    }

    private AnnotationData createAnnotation(Element annotationElement) {
        String tagName = annotationElement.getTagName();
        assert tagName.equals("annotation") : tagName;
        String name = annotationElement.getAttribute("name");
        assert name != null && !name.isEmpty();
        AnnotationData annotation;
        if (IDEA_MAGIC.equals(name)) {
            List<Element> children = getChildren(annotationElement);
            assert children.size() == 1 : children.size();
            Element valueElement = children.get(0);
            String valName = valueElement.getAttribute("name");
            String value = valueElement.getAttribute("val");
            boolean flag = valName.equals("flags");
            if (valName.equals("valuesFromClass") || valName.equals("flagsFromClass")) {
                // Not supported
                return null;
            }

            //noinspection VariableNotUsedInsideIf
            if (apiFilter != null) {
                value = removeFiltered(value);
            }

            annotation = new AnnotationData(
                    valName.equals("stringValues") ? STRING_DEF_ANNOTATION : INT_DEF_ANNOTATION,
                    TYPE_DEF_VALUE_ATTRIBUTE, value,
                    flag ? TYPE_DEF_FLAG_ATTRIBUTE : null, flag ? VALUE_TRUE : null);
        } else if (STRING_DEF_ANNOTATION.equals(name) || ANDROID_STRING_DEF.equals(name) ||
                INT_DEF_ANNOTATION.equals(name) || ANDROID_INT_DEF.equals(name)) {
            List<Element> children = getChildren(annotationElement);
            Element valueElement = children.get(0);
            String valName = valueElement.getAttribute("name");
            assert TYPE_DEF_VALUE_ATTRIBUTE.equals(valName);
            String value = valueElement.getAttribute("val");
            boolean flag = false;
            if (children.size() == 2) {
                valueElement = children.get(1);
                assert TYPE_DEF_FLAG_ATTRIBUTE.equals(valueElement.getAttribute("name"));
                flag = VALUE_TRUE.equals(valueElement.getAttribute("val"));
            }
            boolean intDef = INT_DEF_ANNOTATION.equals(name) || ANDROID_INT_DEF.equals(name);
            annotation = new AnnotationData(
                    intDef ? INT_DEF_ANNOTATION : STRING_DEF_ANNOTATION,
                    TYPE_DEF_VALUE_ATTRIBUTE, value,
                    flag ? TYPE_DEF_FLAG_ATTRIBUTE : null, flag ? VALUE_TRUE : null);
        } else if (IDEA_CONTRACT.equals(name)) {
            List<Element> children = getChildren(annotationElement);
            assert children.size() == 1 : children.size();
            Element valueElement = children.get(0);
            String value = valueElement.getAttribute("val");
            annotation = new AnnotationData(name, TYPE_DEF_VALUE_ATTRIBUTE, value, null, null);
        } else if (isNonNull(name)) {
            annotation = new AnnotationData(SUPPORT_NOTNULL);
        } else if (isNullable(name)) {
            //noinspection PointlessBooleanExpression,ConstantConditions
            if (!INCLUDE_INFERRED_NULLABLE && IDEA_NULLABLE.equals(name)) {
                return null;
            }
            annotation = new AnnotationData(SUPPORT_NULLABLE);
        } else {
            annotation = new AnnotationData(name, null, null);
        }
        return annotation;
    }

    private String removeFiltered(String value) {
        assert apiFilter != null;
        if (value.startsWith("{")) {
            value = value.substring(1);
        }
        if (value.endsWith("}")) {
            value = value.substring(0, value.length() - 1);
        }
        value = value.trim();
        StringBuilder sb = new StringBuilder(value.length());
        sb.append('{');
        for (String fqn : Splitter.on(',').omitEmptyStrings().trimResults().split(value)) {
            fqn = unescapeXml(fqn);
            if (fqn.startsWith("\"")) {
                continue;
            }
            int index = fqn.lastIndexOf('.');
            String cls = fqn.substring(0, index);
            String field = fqn.substring(index + 1);
            if (apiFilter.hasField(cls, field)) {
                if (sb.length() > 1) { // 0: '{'
                    sb.append(", ");
                }
                sb.append(fqn);
            } else if (isListIgnored()) {
                info("Skipping constant from typedef because it is not part of the SDK: " + fqn);
            }
        }
        sb.append('}');
        return escapeXml(sb.toString());
    }


    private static String getPackage(String fqn) {
        // Extract package from the given fqn. Attempts to handle inner classes;
        // e.g.  "foo.bar.Foo.Bar will return "foo.bar".
        int index = 0;
        int last = 0;
        while (true) {
            index = fqn.indexOf('.', index);
            if (index == -1) {
                break;
            }
            last = index;
            if (index < fqn.length() - 1) {
                char next = fqn.charAt(index + 1);
                if (Character.isUpperCase(next)) {
                    break;
                }
            }
            index++;
        }

        return fqn.substring(0, last);
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setListIgnored(boolean listIgnored) {
        this.listIgnored = listIgnored;
    }

    public boolean isListIgnored() {
        return listIgnored;
    }

    private static class AnnotationData {
        @NonNull
        public final String name;

        @Nullable
        public final String attributeName1;

        @Nullable
        public final String attributeValue1;

        @Nullable
        public final String attributeName2;

        @Nullable
        public final String attributeValue2;

        private AnnotationData(@NonNull String name) {
            this(name, null, null, null, null);
        }

        private AnnotationData(@NonNull String name, @Nullable String attributeName,
                @Nullable String attributeValue) {
            this(name, attributeName, attributeValue, null, null);
        }

        private AnnotationData(@NonNull String name,
                @Nullable String attributeName1, @Nullable String attributeValue1,
                @Nullable String attributeName2, @Nullable String attributeValue2) {
            assert name.indexOf('.') != -1 : "Should use fully qualified name for " + name;
            this.name = name;
            this.attributeName1 = attributeName1;
            this.attributeValue1 = attributeValue1;
            this.attributeName2 = attributeName2;
            this.attributeValue2 = attributeValue2;
        }

        void write(PrintWriter writer) {
            writer.print("    <annotation name=\"");
            writer.print(name);

            if (attributeValue1 != null) {
                writer.print("\">");
                writer.println();
                writer.print("      <val name=\"");
                writer.print(attributeName1);
                writer.print("\" val=\"");
                writer.print(escapeXml(attributeValue1));
                writer.println("\" />");
                if (attributeValue2 != null) {
                    writer.print("      <val name=\"");
                    writer.print(attributeName2);
                    writer.print("\" val=\"");
                    writer.print(escapeXml(attributeValue2));
                    writer.println("\" />");
                }

                writer.println("    </annotation>");
            } else {
                writer.println("\" />");
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            AnnotationData that = (AnnotationData) o;

            return name.equals(that.name);
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }
    }

    /**
     * An item in the XML file: this corresponds to a method, a field, or a method parameter, and
     * has an associated set of annotations
     */
    private abstract static class Item implements Comparable<Item> {

        public final List<AnnotationData> annotations = Lists.newArrayList();

        void write(PrintWriter writer) {
            if (!isValid()) {
                return;
            }
            writer.print("  <item name=\"");
            writer.print(getSignature());
            writer.println("\">");

            // TODO: Show annotations WITH their items, if applicable. Such as in parameter lists!
            for (AnnotationData annotation : annotations) {
                annotation.write(writer);
            }
            writer.print("  </item>");
            writer.println();
        }

        abstract boolean isValid();

        abstract boolean isFiltered(@NonNull ApiDatabase database);

        abstract String getSignature();

        @Override
        public int compareTo(@NonNull Item item) {
            String signature1 = getSignature();
            String signature2 = item.getSignature();

            // IntelliJ's sorting order is not on the escaped HTML but the original
            // signatures, which means android.os.AsyncTask<Params,Progress,Result>
            // should appear *after* android.os.AsyncTask.Status, which when the <'s are
            // escaped it does not
            signature1 = signature1.replace('&', '.');
            signature2 = signature2.replace('&', '.');

            return signature1.compareTo(signature2);
        }
    }

    private static class FieldItem extends Item {

        @NonNull
        public final String fieldName;

        @NonNull
        public final String containingClass;

        private FieldItem(@NonNull String containingClass, @NonNull String fieldName) {
            this.containingClass = containingClass;
            this.fieldName = fieldName;
        }

        @Nullable
        static FieldItem create(String classFqn, FieldBinding field) {
            String name = new String(field.name);
            return classFqn != null ? new FieldItem(classFqn, name) : null;
        }

        @Override
        boolean isValid() {
            return true;
        }

        @Override
        boolean isFiltered(@NonNull ApiDatabase database) {
            return !database.hasField(containingClass, fieldName);
        }

        @Override
        String getSignature() {
            return escapeXml(containingClass) + ' ' + fieldName;
        }

        @Override
        public String toString() {
            return "Field " + containingClass + "#" + fieldName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            FieldItem that = (FieldItem) o;

            return containingClass.equals(that.containingClass) &&
                    fieldName.equals(that.fieldName);
        }

        @Override
        public int hashCode() {
            int result = fieldName.hashCode();
            result = 31 * result + containingClass.hashCode();
            return result;
        }
    }

    private static class MethodItem extends Item {

        @NonNull
        public final String methodName;

        @NonNull
        public final String containingClass;

        @NonNull
        public final String parameterList;

        @Nullable
        public final String returnType;

        public final boolean isConstructor;

        private MethodItem(@NonNull String containingClass, @Nullable String returnType,
                @NonNull String methodName, @NonNull String parameterList, boolean isConstructor) {
            this.containingClass = containingClass;
            this.returnType = returnType;
            this.methodName = methodName;
            this.parameterList = parameterList;
            this.isConstructor = isConstructor;
        }

        @Nullable
        static MethodItem create(String classFqn, MethodBinding binding) {
            if (classFqn == null || binding == null) {
                return null;
            }
            String returnType = getReturnType(binding);
            String methodName = getMethodName(binding);
            String parameterList = getParameterList(binding);
            if (returnType == null || methodName == null || parameterList == null) {
                return null;
            }
            return new MethodItem(classFqn, returnType,
                    methodName, parameterList,
                    binding.isConstructor());
        }

        @Override
        boolean isValid() {
            return true;
        }

        @Override
        String getSignature() {
            StringBuilder sb = new StringBuilder(100);
            sb.append(escapeXml(containingClass));
            sb.append(' ');

            if (isConstructor) {
                sb.append(escapeXml(methodName));
            } else {
                assert returnType != null;
                sb.append(escapeXml(returnType));
                sb.append(' ');
                sb.append(escapeXml(methodName));
            }

            sb.append('(');
            // The signature must match *exactly* the formatting used by IDEA,
            // since it looks up external annotations in a map by this key.
            // Therefore, it is vital that the parameter list uses exactly one
            // space after each comma between parameters, and *no* spaces between
            // generics variables, e.g. foo(Map<A,B>, int)
            assert parameterList.indexOf(' ') == -1 : parameterList + " in " +
                    containingClass + "#" + methodName;

            // Insert spaces between commas, but not in generics signatures
            int balance = 0;
            for (int i = 0, n = parameterList.length(); i < n; i++) {
                char c = parameterList.charAt(i);
                if (c == '<') {
                    balance++;
                    sb.append("&lt;");
                } else if (c == '>') {
                    balance--;
                    sb.append("&gt;");
                } else if (c == ',') {
                    sb.append(',');
                    if (balance == 0) {
                        sb.append(' ');
                    }
                } else {
                    sb.append(c);
                }
            }
            sb.append(')');
            return sb.toString();
        }

        @Override
        boolean isFiltered(@NonNull ApiDatabase database) {
            return !database.hasMethod(containingClass, methodName, parameterList);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            MethodItem that = (MethodItem) o;

            return isConstructor == that.isConstructor && containingClass
                    .equals(that.containingClass) && methodName.equals(that.methodName)
                    && parameterList.equals(that.parameterList) && !(returnType != null
                    ? !returnType.equals(that.returnType) : that.returnType != null);

        }

        @Override
        public int hashCode() {
            int result = methodName.hashCode();
            result = 31 * result + containingClass.hashCode();
            result = 31 * result + parameterList.hashCode();
            result = 31 * result + (returnType != null ? returnType.hashCode() : 0);
            result = 31 * result + (isConstructor ? 1 : 0);
            return result;
        }

        @Override
        public String toString() {
            return "Method " + containingClass + "#" + methodName;
        }
    }

    @Nullable
    private static String getReturnType(MethodBinding binding) {
        if (binding.returnType != null) {
            return new String(binding.returnType.readableName());
        } else if (binding.declaringClass != null) {
            assert binding.isConstructor();
            return new String(binding.declaringClass.readableName());
        }

        return null;
    }

    @Nullable
    private static String getMethodName(@NonNull MethodBinding binding) {
        if (binding.isConstructor()) {
            if (binding.declaringClass != null) {
                String classFqn = new String(binding.declaringClass.readableName());
                return classFqn.substring(classFqn.lastIndexOf('.') + 1);
            }

        }
        if (binding.selector != null) {
            return new String(binding.selector);
        }

        assert binding.isConstructor();

        return null;
    }

    @Nullable
    private static String getParameterList(@NonNull MethodBinding binding) {
        // Create compact type signature (no spaces around commas or generics arguments)
        StringBuilder sb = new StringBuilder();
        boolean isFirst = true;
        TypeBinding[] typeParameters = binding.parameters;
        if (typeParameters != null) {
            for (TypeBinding parameter : typeParameters) {
                if (isFirst) {
                    isFirst = false;
                } else {
                    sb.append(',');
                }
                sb.append(fixParameterString(new String(parameter.readableName())));
            }
        }
        return sb.toString();
    }

    private static class ParameterItem extends MethodItem {
        @NonNull
        public String argIndex;

        private ParameterItem(@NonNull String containingClass, @Nullable String returnType,
                @NonNull String methodName, @NonNull String parameterList, boolean isConstructor,
                @NonNull String argIndex) {
            super(containingClass, returnType, methodName, parameterList, isConstructor);
            this.argIndex = argIndex;
        }

        @Nullable
        static ParameterItem create(AbstractMethodDeclaration methodDeclaration, Argument argument,
                String classFqn, MethodBinding methodBinding,
                LocalVariableBinding parameterBinding) {
            if (classFqn == null || methodBinding == null || parameterBinding == null) {
                return null;
            }

            String methodName = getMethodName(methodBinding);
            String parameterList = getParameterList(methodBinding);
            String returnType = getReturnType(methodBinding);
            if (methodName == null || parameterList == null || returnType == null) {
                return null;
            }

            int index = 0;
            boolean found = false;
            if (methodDeclaration.arguments != null) {
                for (Argument a : methodDeclaration.arguments) {
                    if (a == argument) {
                        found = true;
                        break;
                    }
                    index++;
                }
            }
            if (!found) {
                return null;
            }
            String argNum = Integer.toString(index);
            return new ParameterItem(classFqn, returnType, methodName, parameterList,
                    methodBinding.isConstructor(), argNum);
        }


        @Override
        String getSignature() {
            return super.getSignature() + ' ' + argIndex;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            if (!super.equals(o)) {
                return false;
            }

            ParameterItem that = (ParameterItem) o;

            return argIndex.equals(that.argIndex);

        }

        @Override
        public int hashCode() {
            int result = super.hashCode();
            result = 31 * result + argIndex.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return "Parameter #" + argIndex + " in " + super.toString();
        }
    }

    class AnnotationVisitor extends ASTVisitor {
        @Override
        public boolean visit(Argument argument, BlockScope scope) {
            Annotation[] annotations = argument.annotations;
            if (hasRelevantAnnotations(annotations)) {
                ReferenceContext referenceContext = scope.referenceContext();
                if (referenceContext instanceof AbstractMethodDeclaration) {
                    MethodBinding binding = ((AbstractMethodDeclaration) referenceContext).binding;
                    String fqn = getFqn(scope);
                    Item item = ParameterItem.create(
                            (AbstractMethodDeclaration) referenceContext, argument, fqn,
                            binding, argument.binding);
                    if (item != null) {
                        assert fqn != null;
                        addItem(fqn, item);
                        addAnnotations(annotations, item);
                    }
                }
            }
            return false;
        }

        @Override
        public boolean visit(ConstructorDeclaration constructorDeclaration, ClassScope scope) {
            Annotation[] annotations = constructorDeclaration.annotations;
            if (hasRelevantAnnotations(annotations)) {
                MethodBinding constructorBinding = constructorDeclaration.binding;
                if (constructorBinding == null) {
                    return false;
                }

                String fqn = getFqn(scope);
                Item item = MethodItem.create(fqn, constructorBinding);
                if (item != null) {
                    assert fqn != null;
                    addItem(fqn, item);
                    addAnnotations(annotations, item);
                }
            }

            Argument[] arguments = constructorDeclaration.arguments;
            if (arguments != null) {
                for (Argument argument : arguments) {
                    argument.traverse(this, constructorDeclaration.scope);
                }
            }
            return false;
        }

        @Override
        public boolean visit(FieldDeclaration fieldDeclaration, MethodScope scope) {
            Annotation[] annotations = fieldDeclaration.annotations;
            if (hasRelevantAnnotations(annotations)) {
                FieldBinding fieldBinding = fieldDeclaration.binding;
                if (fieldBinding == null) {
                    return false;
                }

                String fqn = getFqn(scope);
                Item item = FieldItem.create(fqn, fieldBinding);
                if (item != null) {
                    assert fqn != null;
                    addItem(fqn, item);
                    addAnnotations(annotations, item);
                }

            }
            return false;
        }

        @Override
        public boolean visit(MethodDeclaration methodDeclaration, ClassScope scope) {
            Annotation[] annotations = methodDeclaration.annotations;
            if (hasRelevantAnnotations(annotations)) {
                MethodBinding methodBinding = methodDeclaration.binding;
                if (methodBinding == null) {
                    return false;
                }

                String fqn = getFqn(scope);
                Item item = MethodItem.create(fqn, methodDeclaration.binding);
                if (item != null) {
                    assert fqn != null;
                    addItem(fqn, item);
                    addAnnotations(annotations, item);
                }
            }

            Argument[] arguments = methodDeclaration.arguments;
            if (arguments != null) {
                for (Argument argument : arguments) {
                    argument.traverse(this, methodDeclaration.scope);
                }
            }
            return false;
        }
    }
}
