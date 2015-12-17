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

package com.android.tools.lint;

import static com.android.SdkConstants.INT_DEF_ANNOTATION;
import static com.android.SdkConstants.STRING_DEF_ANNOTATION;
import static com.android.SdkConstants.UTF_8;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.lint.client.api.JavaParser;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.detector.api.ClassContext;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import org.eclipse.jdt.core.compiler.CategorizedProblem;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.internal.compiler.CompilationResult;
import org.eclipse.jdt.internal.compiler.Compiler;
import org.eclipse.jdt.internal.compiler.DefaultErrorHandlingPolicies;
import org.eclipse.jdt.internal.compiler.ICompilerRequestor;
import org.eclipse.jdt.internal.compiler.IErrorHandlingPolicy;
import org.eclipse.jdt.internal.compiler.IProblemFactory;
import org.eclipse.jdt.internal.compiler.ast.AbstractMethodDeclaration;
import org.eclipse.jdt.internal.compiler.ast.AbstractVariableDeclaration;
import org.eclipse.jdt.internal.compiler.ast.AllocationExpression;
import org.eclipse.jdt.internal.compiler.ast.Annotation;
import org.eclipse.jdt.internal.compiler.ast.ArrayInitializer;
import org.eclipse.jdt.internal.compiler.ast.CharLiteral;
import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration;
import org.eclipse.jdt.internal.compiler.ast.DoubleLiteral;
import org.eclipse.jdt.internal.compiler.ast.ExplicitConstructorCall;
import org.eclipse.jdt.internal.compiler.ast.Expression;
import org.eclipse.jdt.internal.compiler.ast.FalseLiteral;
import org.eclipse.jdt.internal.compiler.ast.FieldDeclaration;
import org.eclipse.jdt.internal.compiler.ast.FloatLiteral;
import org.eclipse.jdt.internal.compiler.ast.IntLiteral;
import org.eclipse.jdt.internal.compiler.ast.Literal;
import org.eclipse.jdt.internal.compiler.ast.LocalDeclaration;
import org.eclipse.jdt.internal.compiler.ast.LongLiteral;
import org.eclipse.jdt.internal.compiler.ast.MagicLiteral;
import org.eclipse.jdt.internal.compiler.ast.MemberValuePair;
import org.eclipse.jdt.internal.compiler.ast.MessageSend;
import org.eclipse.jdt.internal.compiler.ast.NameReference;
import org.eclipse.jdt.internal.compiler.ast.NullLiteral;
import org.eclipse.jdt.internal.compiler.ast.NumberLiteral;
import org.eclipse.jdt.internal.compiler.ast.StringLiteral;
import org.eclipse.jdt.internal.compiler.ast.TrueLiteral;
import org.eclipse.jdt.internal.compiler.ast.TypeDeclaration;
import org.eclipse.jdt.internal.compiler.ast.TypeReference;
import org.eclipse.jdt.internal.compiler.batch.CompilationUnit;
import org.eclipse.jdt.internal.compiler.batch.FileSystem;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;
import org.eclipse.jdt.internal.compiler.env.ICompilationUnit;
import org.eclipse.jdt.internal.compiler.env.INameEnvironment;
import org.eclipse.jdt.internal.compiler.impl.BooleanConstant;
import org.eclipse.jdt.internal.compiler.impl.ByteConstant;
import org.eclipse.jdt.internal.compiler.impl.CharConstant;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;
import org.eclipse.jdt.internal.compiler.impl.Constant;
import org.eclipse.jdt.internal.compiler.impl.DoubleConstant;
import org.eclipse.jdt.internal.compiler.impl.FloatConstant;
import org.eclipse.jdt.internal.compiler.impl.IntConstant;
import org.eclipse.jdt.internal.compiler.impl.LongConstant;
import org.eclipse.jdt.internal.compiler.impl.ShortConstant;
import org.eclipse.jdt.internal.compiler.impl.StringConstant;
import org.eclipse.jdt.internal.compiler.lookup.AnnotationBinding;
import org.eclipse.jdt.internal.compiler.lookup.Binding;
import org.eclipse.jdt.internal.compiler.lookup.ElementValuePair;
import org.eclipse.jdt.internal.compiler.lookup.FieldBinding;
import org.eclipse.jdt.internal.compiler.lookup.LocalVariableBinding;
import org.eclipse.jdt.internal.compiler.lookup.LookupEnvironment;
import org.eclipse.jdt.internal.compiler.lookup.MethodBinding;
import org.eclipse.jdt.internal.compiler.lookup.NestedTypeBinding;
import org.eclipse.jdt.internal.compiler.lookup.PackageBinding;
import org.eclipse.jdt.internal.compiler.lookup.ProblemBinding;
import org.eclipse.jdt.internal.compiler.lookup.ProblemFieldBinding;
import org.eclipse.jdt.internal.compiler.lookup.ProblemMethodBinding;
import org.eclipse.jdt.internal.compiler.lookup.ProblemPackageBinding;
import org.eclipse.jdt.internal.compiler.lookup.ProblemReferenceBinding;
import org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding;
import org.eclipse.jdt.internal.compiler.lookup.TypeBinding;
import org.eclipse.jdt.internal.compiler.lookup.TypeConstants;
import org.eclipse.jdt.internal.compiler.parser.Parser;
import org.eclipse.jdt.internal.compiler.problem.AbortCompilation;
import org.eclipse.jdt.internal.compiler.problem.DefaultProblemFactory;
import org.eclipse.jdt.internal.compiler.problem.ProblemReporter;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import lombok.ast.Node;
import lombok.ast.VariableDeclaration;
import lombok.ast.VariableDefinition;
import lombok.ast.VariableDefinitionEntry;
import lombok.ast.ecj.EcjTreeConverter;

/**
 * Java parser which uses ECJ for parsing and type attribution
 */
public class EcjParser extends JavaParser {
    private static final boolean DEBUG_DUMP_PARSE_ERRORS = false;

    /**
     * Whether we're going to keep the ECJ compiler mLookupEnvironment around between
     * the parse phase and disposal. The lookup environment is important for type attribution.
     * We should be able to inline this field to true, but making it optional now to allow
     * people to revert this behavior in the field immediately if there's an unexpected
     * problem.
     */
    private static final boolean KEEP_LOOKUP_ENVIRONMENT = !Boolean.getBoolean("lint.reset.ecj");

    private final LintClient mClient;
    private final Project mProject;
    private Map<File, ICompilationUnit> mSourceUnits;
    private Map<String, TypeDeclaration> mTypeUnits;
    private Parser mParser;
    protected EcjResult mEcjResult;

    public EcjParser(@NonNull LintCliClient client, @Nullable Project project) {
        mClient = client;
        mProject = project;
        mParser = getParser();
    }

    /**
     * Create the default compiler options
     */
    public static CompilerOptions createCompilerOptions() {
        CompilerOptions options = new CompilerOptions();

        // Always using JDK 7 rather than basing it on project metadata since we
        // don't do compilation error validation in lint (we leave that to the IDE's
        // error parser or the command line build's compilation step); we want an
        // AST that is as tolerant as possible.
        long languageLevel = ClassFileConstants.JDK1_7;
        options.complianceLevel = languageLevel;
        options.sourceLevel = languageLevel;
        options.targetJDK = languageLevel;
        options.originalComplianceLevel = languageLevel;
        options.originalSourceLevel = languageLevel;
        options.inlineJsrBytecode = true; // >1.5

        options.parseLiteralExpressionsAsConstants = true;
        options.analyseResourceLeaks = false;
        options.docCommentSupport = false;
        options.defaultEncoding = UTF_8;
        options.suppressOptionalErrors = true;
        options.generateClassFiles = false;
        options.isAnnotationBasedNullAnalysisEnabled = false;
        options.reportUnusedDeclaredThrownExceptionExemptExceptionAndThrowable = false;
        options.reportUnusedDeclaredThrownExceptionIncludeDocCommentReference = false;
        options.reportUnusedDeclaredThrownExceptionWhenOverriding = false;
        options.reportUnusedParameterIncludeDocCommentReference = false;
        options.reportUnusedParameterWhenImplementingAbstract = false;
        options.reportUnusedParameterWhenOverridingConcrete = false;
        options.suppressWarnings = true;
        options.processAnnotations = true;
        options.storeAnnotations = true;
        options.verbose = false;
        return options;
    }

    public static long getLanguageLevel(int major, int minor) {
        assert major == 1;
        switch (minor) {
            case 5: return ClassFileConstants.JDK1_5;
            case 6: return ClassFileConstants.JDK1_6;
            case 7:
            default:
                return ClassFileConstants.JDK1_7;
        }
    }

    private Parser getParser() {
        if (mParser == null) {
            CompilerOptions options = createCompilerOptions();
            ProblemReporter problemReporter = new ProblemReporter(
                    DefaultErrorHandlingPolicies.exitOnFirstError(),
                    options,
                    new DefaultProblemFactory());
            mParser = new Parser(problemReporter,
                    options.parseLiteralExpressionsAsConstants);
            mParser.javadocParser.checkDocComment = false;
        }
        return mParser;
    }

    @Override
    public void prepareJavaParse(@NonNull final List<JavaContext> contexts) {
        if (mProject == null || contexts.isEmpty()) {
            return;
        }

        List<ICompilationUnit> sources = Lists.newArrayListWithExpectedSize(contexts.size());
        mSourceUnits = Maps.newHashMapWithExpectedSize(sources.size());
        for (JavaContext context : contexts) {
            String contents = context.getContents();
            if (contents == null) {
                continue;
            }
            File file = context.file;
            CompilationUnit unit = new CompilationUnit(contents.toCharArray(), file.getPath(),
                    UTF_8);
            sources.add(unit);
            mSourceUnits.put(file, unit);
        }
        List<String> classPath = computeClassPath(contexts);
        try {
            mEcjResult = parse(createCompilerOptions(), sources, classPath, mClient);

            if (DEBUG_DUMP_PARSE_ERRORS) {
                for (CompilationUnitDeclaration unit : mEcjResult.getCompilationUnits()) {
                    // so maybe I don't need my map!!
                    CategorizedProblem[] problems = unit.compilationResult()
                            .getAllProblems();
                    if (problems != null) {
                        for (IProblem problem : problems) {
                            if (problem == null || !problem.isError()) {
                                continue;
                            }
                            System.out.println(
                                    new String(problem.getOriginatingFileName()) + ":"
                                    + (problem.isError() ? "Error" : "Warning") + ": "
                                    + problem.getSourceLineNumber() + ": " + problem.getMessage());
                        }
                    }
                }
            }
        } catch (Throwable t) {
            mClient.log(t, "ECJ compiler crashed");
        }
    }

    /**
     * A result from an ECJ compilation. In addition to the {@link #compilationUnits} it also
     * returns the {@link #mNameEnvironment} and {@link #mLookupEnvironment} which are sometimes
     * needed after parsing to perform for example type attribution. <b>NOTE</b>: Clients are
     * expected to dispose of the {@link #mNameEnvironment} when done with the compilation units!
     */
    public static class EcjResult {
        @Nullable private final INameEnvironment mNameEnvironment;
        @Nullable private final LookupEnvironment mLookupEnvironment;
        @NonNull  private final Map<ICompilationUnit, CompilationUnitDeclaration> compilationUnits;

        public EcjResult(@Nullable INameEnvironment nameEnvironment,
                @Nullable LookupEnvironment lookupEnvironment,
                @NonNull Map<ICompilationUnit, CompilationUnitDeclaration> compilationUnits) {
            this.mNameEnvironment = nameEnvironment;
            this.mLookupEnvironment = lookupEnvironment;
            this.compilationUnits = compilationUnits;
        }

        /**
         * Returns the collection of compilation units found by the parse task
         *
         * @return a read-only collection of compilation units
         */
        @NonNull
        public Collection<CompilationUnitDeclaration> getCompilationUnits() {
            return compilationUnits.values();
        }

        /**
         * Returns the compilation unit parsed from the given source unit, if any
         *
         * @param sourceUnit the original source passed to ECJ
         * @return the corresponding compilation unit, if created
         */
        @Nullable
        public CompilationUnitDeclaration getCompilationUnit(
                @NonNull ICompilationUnit sourceUnit) {
            return compilationUnits.get(sourceUnit);
        }

        /**
         * Removes the compilation unit for the given source unit, if any. Used when individual
         * source units are disposed to allow memory to be freed up.
         *
         * @param sourceUnit the source unit
         */
        void removeCompilationUnit(@NonNull ICompilationUnit sourceUnit) {
            compilationUnits.remove(sourceUnit);
        }

        /**
         * Disposes this parser result, allowing various ECJ data structures to be freed up even if
         * the parser instance hangs around.
         */
        public void dispose() {
            if (mNameEnvironment != null) {
                mNameEnvironment.cleanup();
            }

            if (mLookupEnvironment != null) {
                mLookupEnvironment.reset();
            }

            compilationUnits.clear();
        }
    }

    /** Parse the given source units and class path and store it into the given output map */
    @NonNull
    public static EcjResult parse(
            CompilerOptions options,
            @NonNull List<ICompilationUnit> sourceUnits,
            @NonNull List<String> classPath,
            @Nullable LintClient client) {
        Map<ICompilationUnit, CompilationUnitDeclaration> outputMap =
                Maps.newHashMapWithExpectedSize(sourceUnits.size());

        INameEnvironment environment = new FileSystem(
                classPath.toArray(new String[classPath.size()]), new String[0],
                options.defaultEncoding);
        IErrorHandlingPolicy policy = DefaultErrorHandlingPolicies.proceedWithAllProblems();
        IProblemFactory problemFactory = new DefaultProblemFactory(Locale.getDefault());
        ICompilerRequestor requestor = new ICompilerRequestor() {
            @Override
            public void acceptResult(CompilationResult result) {
                // Not used; we need the corresponding CompilationUnitDeclaration for the source
                // units (the AST parsed from source) which we don't get access to here, so we
                // instead subclass AST to get our hands on them.
            }
        };

        NonGeneratingCompiler compiler = new NonGeneratingCompiler(environment, policy, options,
                requestor, problemFactory, outputMap);
        try {
            compiler.compile(sourceUnits.toArray(new ICompilationUnit[sourceUnits.size()]));
        } catch (OutOfMemoryError e) {
            environment.cleanup();

            // Since we're running out of memory, if it's all still held we could potentially
            // fail attempting to log the failure. Actively get rid of the large ECJ data
            // structure references first so minimize the chance of that
            //noinspection UnusedAssignment
            compiler = null;
            //noinspection UnusedAssignment
            environment = null;
            //noinspection UnusedAssignment
            requestor = null;
            //noinspection UnusedAssignment
            problemFactory = null;
            //noinspection UnusedAssignment
            policy = null;

            String msg = "Ran out of memory analyzing .java sources with ECJ: Some lint checks "
                    + "may not be accurate (missing type information from the compiler)";
            if (client != null) {
                // Don't log exception too; this isn't a compiler error per se where we
                // need to pin point the exact unlucky code that asked for memory when it
                // had already run out
                client.log(null, msg);
            } else {
                System.out.println(msg);
            }
        } catch (Throwable t) {
            if (client != null) {
                CompilationUnitDeclaration currentUnit = compiler.getCurrentUnit();
                if (currentUnit == null || currentUnit.getFileName() == null) {
                    client.log(t, "ECJ compiler crashed");
                } else {
                    client.log(t, "ECJ compiler crashed processing %1$s",
                            new String(currentUnit.getFileName()));
                }
            } else {
                t.printStackTrace();
            }

            environment.cleanup();
            environment = null;
        }

        LookupEnvironment lookupEnvironment = compiler != null ? compiler.lookupEnvironment : null;
        return new EcjResult(environment, lookupEnvironment, outputMap);
    }

    @NonNull
    private List<String> computeClassPath(@NonNull List<JavaContext> contexts) {
        assert mProject != null;
        List<String> classPath = Lists.newArrayList();

        IAndroidTarget compileTarget = mProject.getBuildTarget();
        if (compileTarget != null) {
            String androidJar = compileTarget.getPath(IAndroidTarget.ANDROID_JAR);
            if (androidJar != null && new File(androidJar).exists()) {
                classPath.add(androidJar);
            }
        }

        Set<File> libraries = Sets.newHashSet();
        Set<String> names = Sets.newHashSet();
        for (File library : mProject.getJavaLibraries(true)) {
            libraries.add(library);
            names.add(getLibraryName(library));
        }
        for (Project project : mProject.getAllLibraries()) {
            for (File library : project.getJavaLibraries(true)) {
                String name = getLibraryName(library);
                // Avoid pulling in android-support-v4.jar from libraries etc
                // since we're pointing to the local copies rather than the real
                // maven/gradle source copies
                if (!names.contains(name)) {
                    libraries.add(library);
                    names.add(name);
                }
            }
        }

        for (File file : libraries) {
            if (file.exists()) {
                classPath.add(file.getPath());
            }
        }

        // In incremental mode we may need to point to other sources in the project
        // for type resolution
        EnumSet<Scope> scope = contexts.get(0).getScope();
        if (!scope.contains(Scope.ALL_JAVA_FILES)) {
            // May need other compiled classes too
            for (File dir : mProject.getJavaClassFolders()) {
                if (dir.exists()) {
                    classPath.add(dir.getPath());
                }
            }
        }

        return classPath;
    }

    @NonNull
    private static String getLibraryName(@NonNull File library) {
        String name = library.getName();
        if (name.equals(SdkConstants.FN_CLASSES_JAR)) {
            // For AAR artifacts they'll all clash with "classes.jar"; include more unique
            // context
            String path = library.getPath();
            int index = path.indexOf("exploded-aar");
            if (index != -1) {
                return path.substring(index);
            } else {
                index = path.indexOf("exploded-bundles");
                if (index != -1) {
                    return path.substring(index);
                }
            }
            File parent = library.getParentFile();
            if (parent != null) {
                return parent.getName() + File.separatorChar + name;
            }
        }
        return name;
    }

    @Override
    public Node parseJava(@NonNull JavaContext context) {
        String code = context.getContents();
        if (code == null) {
            return null;
        }

        CompilationUnitDeclaration unit = getParsedUnit(context, code);
        try {
            EcjTreeConverter converter = new EcjTreeConverter();
            converter.visit(code, unit);
            List<? extends Node> nodes = converter.getAll();

            if (nodes != null) {
                // There could be more than one node when there are errors; pick out the
                // compilation unit node
                for (Node node : nodes) {
                    if (node instanceof lombok.ast.CompilationUnit) {
                        return node;
                    }
                }
            }

            return null;
        } catch (Throwable t) {
            mClient.log(t, "Failed converting ECJ parse tree to Lombok for file %1$s",
                    context.file.getPath());
            return null;
        }
    }

    @Nullable
    private CompilationUnitDeclaration getParsedUnit(
            @NonNull JavaContext context,
            @NonNull String code) {
        ICompilationUnit sourceUnit = null;
        if (mSourceUnits != null && mEcjResult != null) {
            sourceUnit = mSourceUnits.get(context.file);
            if (sourceUnit != null) {
                CompilationUnitDeclaration unit = mEcjResult.getCompilationUnit(sourceUnit);
                if (unit != null) {
                    return unit;
                }
            }
        }

        if (sourceUnit == null) {
            sourceUnit = new CompilationUnit(code.toCharArray(), context.file.getName(), UTF_8);
        }
        try {
            CompilationResult compilationResult = new CompilationResult(sourceUnit, 0, 0, 0);
            return getParser().parse(sourceUnit, compilationResult);
        } catch (AbortCompilation e) {
            // No need to report Java parsing errors while running in Eclipse.
            // Eclipse itself will already provide problem markers for these files,
            // so all this achieves is creating "multiple annotations on this line"
            // tooltips instead.
            return null;
        }
    }

    @NonNull
    @Override
    public Location getLocation(@NonNull JavaContext context, @NonNull Node node) {
        lombok.ast.Position position = node.getPosition();
        return Location.create(context.file, context.getContents(),
                position.getStart(), position.getEnd());
    }

    @NonNull
    @Override
    public
    Location.Handle createLocationHandle(@NonNull JavaContext context, @NonNull Node node) {
        return new LocationHandle(context.file, node);
    }

    @Override
    public void dispose(@NonNull JavaContext context, @NonNull Node compilationUnit) {
        if (mSourceUnits != null) {
            ICompilationUnit sourceUnit = mSourceUnits.get(context.file);
            if (sourceUnit != null) {
                mSourceUnits.remove(context.file);
                if (mEcjResult != null) {
                    CompilationUnitDeclaration unit = mEcjResult.getCompilationUnit(sourceUnit);
                    if (unit != null) {
                        // See if this compilation unit defines any enum types; if so,
                        // keep those around for the type map (see #findAnnotationDeclaration())
                        if (unit.types != null) {
                            for (TypeDeclaration type : unit.types) {
                                if (isAnnotationType(type)) {
                                    return;
                                }
                                if (type.memberTypes != null) {
                                    for (TypeDeclaration member : type.memberTypes) {
                                        if (isAnnotationType(member)) {
                                            return;
                                        }
                                    }
                                }
                            }
                        }
                        // Compilation unit is not defining an annotation type at the top two
                        // levels: we can remove it now; findAnnotationDeclaration will not need
                        // to go looking for it
                        mEcjResult.removeCompilationUnit(sourceUnit);
                    }
                }
            }
        }
    }

    private static boolean isAnnotationType(@NonNull TypeDeclaration type) {
        return TypeDeclaration.kind(type.modifiers) == TypeDeclaration.ANNOTATION_TYPE_DECL;
    }

    @Override
    public void dispose() {
        if (mEcjResult != null) {
            mEcjResult.dispose();
            mEcjResult = null;
        }

        mSourceUnits = null;
        mTypeUnits = null;
    }

    @Nullable
    private static Object getNativeNode(@NonNull Node node) {
        Object nativeNode = node.getNativeNode();
        if (nativeNode != null) {
            return nativeNode;
        }

        Node parent = node.getParent();
        // The ECJ native nodes are sometimes spotty; for example, for a
        // MethodInvocation node we can have a null native node, but its
        // parent expression statement will point to the real MessageSend node
        if (parent != null) {
            nativeNode = parent.getNativeNode();
            if (nativeNode != null) {
                return nativeNode;
            }
        }

        if (node instanceof VariableDefinitionEntry) {
            node = node.getParent().getParent();
        }
        if (node instanceof VariableDeclaration) {
            VariableDeclaration declaration = (VariableDeclaration) node;
            VariableDefinition definition = declaration.astDefinition();
            if (definition != null) {
                lombok.ast.TypeReference typeReference = definition.astTypeReference();
                if (typeReference != null) {
                    return typeReference.getNativeNode();
                }
            }
        }

        return null;
    }

    @Override
    @Nullable
    public ResolvedNode resolve(@NonNull JavaContext context, @NonNull Node node) {
        Object nativeNode = getNativeNode(node);
        if (nativeNode == null) {
            return null;
        }

        if (nativeNode instanceof NameReference) {
            return resolve(((NameReference) nativeNode).binding);
        } else if (nativeNode instanceof TypeReference) {
            return resolve(((TypeReference) nativeNode).resolvedType);
        } else if (nativeNode instanceof MessageSend) {
            return resolve(((MessageSend) nativeNode).binding);
        } else if (nativeNode instanceof AllocationExpression) {
            return resolve(((AllocationExpression) nativeNode).binding);
        } else if (nativeNode instanceof TypeDeclaration) {
            return resolve(((TypeDeclaration) nativeNode).binding);
        } else if (nativeNode instanceof ExplicitConstructorCall) {
            return resolve(((ExplicitConstructorCall) nativeNode).binding);
        } else if (nativeNode instanceof Annotation) {
            AnnotationBinding compilerAnnotation =
                    ((Annotation) nativeNode).getCompilerAnnotation();
            if (compilerAnnotation != null) {
                return new EcjResolvedAnnotation(compilerAnnotation);
            }
            return resolve(((Annotation) nativeNode).resolvedType);
        } else if (nativeNode instanceof AbstractMethodDeclaration) {
            return resolve(((AbstractMethodDeclaration) nativeNode).binding);
        } else if (nativeNode instanceof AbstractVariableDeclaration) {
            if (nativeNode instanceof LocalDeclaration) {
                return resolve(((LocalDeclaration) nativeNode).binding);
            } else if (nativeNode instanceof FieldDeclaration) {
                FieldDeclaration fieldDeclaration = (FieldDeclaration) nativeNode;
                if (fieldDeclaration.initialization instanceof AllocationExpression) {
                    AllocationExpression allocation =
                            (AllocationExpression)fieldDeclaration.initialization;
                    if (allocation.binding != null) {
                        // Field constructor call: this is an enum constant.
                        return new EcjResolvedMethod(allocation.binding);
                    }
                }
                return resolve(fieldDeclaration.binding);
            }
        }

        // TODO: Handle org.eclipse.jdt.internal.compiler.ast.SuperReference. It
        // doesn't contain an actual method binding; the parent node call should contain
        // it, but is missing a native node reference; investigate the ECJ bridge's super
        // handling.

        return null;
    }

    private ResolvedNode resolve(@Nullable Binding binding) {
        if (binding == null || binding instanceof ProblemBinding) {
            return null;
        }

        if (binding instanceof TypeBinding) {
            TypeBinding tb = (TypeBinding) binding;
            return new EcjResolvedClass(tb);
        } else if (binding instanceof MethodBinding) {
            MethodBinding mb = (MethodBinding) binding;
            if (mb instanceof ProblemMethodBinding) {
                return null;
            }
            //noinspection VariableNotUsedInsideIf
            if (mb.declaringClass != null) {
                return new EcjResolvedMethod(mb);
            }
        } else if (binding instanceof LocalVariableBinding) {
            LocalVariableBinding lvb = (LocalVariableBinding) binding;
            //noinspection VariableNotUsedInsideIf
            if (lvb.type != null) {
                return new EcjResolvedVariable(lvb);
            }
        } else if (binding instanceof FieldBinding) {
            FieldBinding fb = (FieldBinding) binding;
            if (fb instanceof ProblemFieldBinding) {
                return null;
            }
            if (fb.type != null && fb.declaringClass != null) {
                return new EcjResolvedField(fb);
            }
        }

        return null;
    }

    private TypeDeclaration findAnnotationDeclaration(@NonNull String signature) {
        if (mTypeUnits == null) {
            Collection<CompilationUnitDeclaration> units = mEcjResult.getCompilationUnits();
            mTypeUnits = Maps.newHashMapWithExpectedSize(units.size());
            for (CompilationUnitDeclaration unit : units) {
                if (unit.types != null) {
                    for (TypeDeclaration typeDeclaration : unit.types) {
                        addTypeDeclaration(typeDeclaration);
                    }
                }
            }
        }

        return mTypeUnits.get(signature);
    }

    private void addTypeDeclaration(TypeDeclaration typeDeclaration) {
        String type = new String(typeDeclaration.binding.readableName());
        mTypeUnits.put(type, typeDeclaration);
        // Recurse on member types
        if (typeDeclaration.memberTypes != null) {
            for (TypeDeclaration member : typeDeclaration.memberTypes) {
                addTypeDeclaration(member);
            }
        }
    }

    @Override
    @Nullable
    public TypeDescriptor getType(@NonNull JavaContext context, @NonNull Node node) {
        Object nativeNode = getNativeNode(node);
        if (nativeNode == null) {
            return null;
        }

        if (nativeNode instanceof MessageSend) {
            nativeNode = ((MessageSend)nativeNode).binding;
        } else if (nativeNode instanceof AllocationExpression) {
            nativeNode = ((AllocationExpression)nativeNode).resolvedType;
        } else if (nativeNode instanceof NameReference) {
            nativeNode = ((NameReference)nativeNode).resolvedType;
        } else if (nativeNode instanceof Expression) {
            if (nativeNode instanceof Literal) {
                if (nativeNode instanceof StringLiteral) {
                    return getTypeDescriptor(TYPE_STRING);
                } else if (nativeNode instanceof NumberLiteral) {
                    if (nativeNode instanceof IntLiteral) {
                        return getTypeDescriptor(TYPE_INT);
                    } else if (nativeNode instanceof LongLiteral) {
                        return getTypeDescriptor(TYPE_LONG);
                    } else if (nativeNode instanceof CharLiteral) {
                        return getTypeDescriptor(TYPE_CHAR);
                    } else if (nativeNode instanceof FloatLiteral) {
                        return getTypeDescriptor(TYPE_FLOAT);
                    } else if (nativeNode instanceof DoubleLiteral) {
                        return getTypeDescriptor(TYPE_DOUBLE);
                    }
                } else if (nativeNode instanceof MagicLiteral) {
                    if (nativeNode instanceof TrueLiteral || nativeNode instanceof FalseLiteral) {
                        return getTypeDescriptor(TYPE_BOOLEAN);
                    } else if (nativeNode instanceof NullLiteral) {
                        return getTypeDescriptor(TYPE_NULL);
                    }
                }
            }
            nativeNode = ((Expression)nativeNode).resolvedType;
        } else if (nativeNode instanceof TypeDeclaration) {
            nativeNode = ((TypeDeclaration) nativeNode).binding;
        } else if (nativeNode instanceof AbstractMethodDeclaration) {
            nativeNode = ((AbstractMethodDeclaration) nativeNode).binding;
        }

        if (nativeNode instanceof Binding) {
            Binding binding = (Binding) nativeNode;
            if (binding instanceof TypeBinding) {
                TypeBinding tb = (TypeBinding) binding;
                return getTypeDescriptor(tb);
            } else if (binding instanceof LocalVariableBinding) {
                LocalVariableBinding lvb = (LocalVariableBinding) binding;
                if (lvb.type != null) {
                    return getTypeDescriptor(lvb.type);
                }
            } else if (binding instanceof FieldBinding) {
                FieldBinding fb = (FieldBinding) binding;
                if (fb.type != null) {
                    return getTypeDescriptor(fb.type);
                }
            } else if (binding instanceof MethodBinding) {
                return getTypeDescriptor(((MethodBinding) binding).returnType);
            } else if (binding instanceof ProblemBinding) {
                // Unresolved type. We just don't know.
                return null;
            }
        }
        return null;
    }

    @Nullable
    @Override
    public ResolvedClass findClass(@NonNull JavaContext context,
            @NonNull String fullyQualifiedName) {
        // Inner classes must use $ as separators. Switch to internal name first
        // to make it more likely that we handle this correctly:
        String internal = ClassContext.getInternalName(fullyQualifiedName);

        // Convert "foo/bar/Baz" into char[][] 'foo','bar','Baz' as required for
        // ECJ name lookup
        List<char[]> arrays = Lists.newArrayList();
        for (String segment : Splitter.on('/').split(internal)) {
            arrays.add(segment.toCharArray());
        }
        char[][] compoundName = new char[arrays.size()][];
        for (int i = 0, n = arrays.size(); i < n; i++) {
            compoundName[i] = arrays.get(i);
        }

        LookupEnvironment lookup = mEcjResult.mLookupEnvironment;
        if (lookup != null) {
            ReferenceBinding type = lookup.getType(compoundName);
            if (type != null && !(type instanceof ProblemReferenceBinding)) {
                return new EcjResolvedClass(type);
            }
        }

        return null;
    }

    @Nullable
    private TypeDescriptor getTypeDescriptor(@Nullable TypeBinding resolvedType) {
        if (resolvedType == null) {
            return null;
        }
        return new EcjTypeDescriptor(resolvedType);
    }

    private static TypeDescriptor getTypeDescriptor(String fqn) {
        return new DefaultTypeDescriptor(fqn);
    }

    /** Computes the super method, if any, given a method binding */
    private static MethodBinding findSuperMethodBinding(@NonNull MethodBinding binding) {
        try {
            ReferenceBinding superclass = binding.declaringClass.superclass();
            while (superclass != null) {
                MethodBinding[] methods = superclass.getMethods(binding.selector,
                        binding.parameters.length);
                for (MethodBinding method : methods) {
                    if (method.areParameterErasuresEqual(binding)) {
                        return method;
                    }
                }

                superclass = superclass.superclass();
            }
        } catch (Exception ignore) {
            // Work around ECJ bugs; see https://code.google.com/p/android/issues/detail?id=172268
        }

        return null;
    }

    @NonNull
    private static Collection<ResolvedAnnotation> merge(
            @Nullable Collection<ResolvedAnnotation> first,
            @Nullable Collection<ResolvedAnnotation> second) {
        if (first == null || first.isEmpty()) {
            if (second == null) {
                return Collections.emptyList();
            } else {
                return second;
            }
        } else if (second == null || second.isEmpty()) {
            return first;
        } else {
            int size = first.size() + second.size();
            List<ResolvedAnnotation> merged = Lists.newArrayListWithExpectedSize(size);
            merged.addAll(first);
            merged.addAll(second);
            return merged;
        }
    }

    /* Handle for creating positions cheaply and returning full fledged locations later */
    private static class LocationHandle implements Location.Handle {
        private File mFile;
        private Node mNode;
        private Object mClientData;

        public LocationHandle(File file, Node node) {
            mFile = file;
            mNode = node;
        }

        @NonNull
        @Override
        public Location resolve() {
            lombok.ast.Position pos = mNode.getPosition();
            return Location.create(mFile, null /*contents*/, pos.getStart(), pos.getEnd());
        }

        @Override
        public void setClientData(@Nullable Object clientData) {
            mClientData = clientData;
        }

        @Override
        @Nullable
        public Object getClientData() {
            return mClientData;
        }
    }

    // Custom version of the compiler which skips code generation and records source units
    private static class NonGeneratingCompiler extends Compiler {
        private Map<ICompilationUnit, CompilationUnitDeclaration> mUnits;
        private CompilationUnitDeclaration mCurrentUnit;

        public NonGeneratingCompiler(INameEnvironment environment, IErrorHandlingPolicy policy,
                CompilerOptions options, ICompilerRequestor requestor,
                IProblemFactory problemFactory,
                Map<ICompilationUnit, CompilationUnitDeclaration> units) {
            super(environment, policy, options, requestor, problemFactory, null, null);
            mUnits = units;
        }

        @Nullable
        CompilationUnitDeclaration getCurrentUnit() {
            // Can't use mLookupEnvironment.unitBeingCompleted directly; it gets nulled out
            // as part of the exception catch handling in the compiler before this method
            // is called from lint -- therefore we stash a copy in our own mCurrentUnit field
            return mCurrentUnit;
        }

        @Override
        protected synchronized void addCompilationUnit(ICompilationUnit sourceUnit,
                CompilationUnitDeclaration parsedUnit) {
            super.addCompilationUnit(sourceUnit, parsedUnit);
            mUnits.put(sourceUnit, parsedUnit);
        }

        @Override
        public void process(CompilationUnitDeclaration unit, int unitNumber) {
            mCurrentUnit = lookupEnvironment.unitBeingCompleted = unit;

            parser.getMethodBodies(unit);
            if (unit.scope != null) {
                unit.scope.faultInTypes();
                unit.scope.verifyMethods(lookupEnvironment.methodVerifier());
            }
            unit.resolve();
            unit.analyseCode();

            // This is where we differ from super: DON'T call generateCode().
            // Sadly we can't just set ignoreMethodBodies=true to have the same effect,
            // since that would also skip the analyseCode call, which we DO, want:
            //     unit.generateCode();

            if (options.produceReferenceInfo && unit.scope != null) {
                unit.scope.storeDependencyInfo();
            }
            unit.finalizeProblems();
            unit.compilationResult.totalUnitsKnown = totalUnits;
            lookupEnvironment.unitBeingCompleted = null;
        }

        @Override
        public void reset() {
            if (KEEP_LOOKUP_ENVIRONMENT) {
                // Same as super.reset() in ECJ 4.4.2, but omits the following statement:
                //  this.mLookupEnvironment.reset();
                // because we need the lookup environment to stick around even after the
                // parse phase is done: at that point we're going to use the parse trees
                // from java detectors which may need to resolve types
                this.parser.scanner.source = null;
                this.unitsToProcess = null;
                if (DebugRequestor != null) DebugRequestor.reset();
                this.problemReporter.reset();

            } else {
                super.reset();
            }
        }
    }

    private class EcjTypeDescriptor extends TypeDescriptor {
        private final TypeBinding mBinding;

        private EcjTypeDescriptor(@NonNull TypeBinding binding) {
            mBinding = binding;
        }

        @NonNull
        @Override
        public String getName() {
            return new String(mBinding.readableName());
        }

        @Override
        public boolean matchesName(@NonNull String name) {
            return sameChars(name, mBinding.readableName());
        }

        @Override
        public boolean matchesSignature(@NonNull String signature) {
            return sameChars(signature, mBinding.readableName());
        }

        @Override
        public boolean isPrimitive() {
            return mBinding.isPrimitiveType();
        }

        @Override
        public boolean isArray() {
            return mBinding.isArrayType();
        }

        @NonNull
        @Override
        public String getSignature() {
            return getName();
        }

        @Override
        @Nullable
        public ResolvedClass getTypeClass() {
            if (!mBinding.isPrimitiveType()) {
                return new EcjResolvedClass(mBinding);
            }
            return null;
        }

        @SuppressWarnings("RedundantIfStatement")
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            EcjTypeDescriptor that = (EcjTypeDescriptor) o;

            if (!mBinding.equals(that.mBinding)) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            return mBinding.hashCode();
        }
    }

    private class EcjResolvedMethod extends ResolvedMethod {
        private MethodBinding mBinding;

        private EcjResolvedMethod(MethodBinding binding) {
            mBinding = binding;
            assert mBinding.declaringClass != null;
        }

        @NonNull
        @Override
        public String getName() {
            char[] c = isConstructor() ? mBinding.declaringClass.readableName() : mBinding.selector;
            return new String(c);
        }

        @Override
        public boolean matches(@NonNull String name) {
            char[] c = isConstructor() ? mBinding.declaringClass.readableName() : mBinding.selector;
            return sameChars(name, c);
        }

        @NonNull
        @Override
        public ResolvedClass getContainingClass() {
            return new EcjResolvedClass(mBinding.declaringClass);
        }

        @Override
        public int getArgumentCount() {
            return mBinding.parameters != null ? mBinding.parameters.length : 0;
        }

        @NonNull
        @Override
        public TypeDescriptor getArgumentType(int index) {
            TypeBinding parameterType = mBinding.parameters[index];
            TypeDescriptor typeDescriptor = getTypeDescriptor(parameterType);
            assert typeDescriptor != null; // because parameter is not null
            return typeDescriptor;
        }

        @Override
        public boolean argumentMatchesType(int index, @NonNull String signature) {
            return sameChars(signature, mBinding.parameters[index].readableName());
        }

        @Nullable
        @Override
        public TypeDescriptor getReturnType() {
            return isConstructor() ? null : getTypeDescriptor(mBinding.returnType);
        }

        @Override
        public boolean isConstructor() {
            return mBinding.isConstructor();
        }

        @Override
        @Nullable
        public ResolvedMethod getSuperMethod() {
            MethodBinding superBinding = findSuperMethodBinding(mBinding);
            if (superBinding != null) {
                return new EcjResolvedMethod(superBinding);
            }

            return null;
        }

        @NonNull
        @Override
        public Iterable<ResolvedAnnotation> getAnnotations() {
            List<ResolvedAnnotation> all = Lists.newArrayListWithExpectedSize(4);
            ExternalAnnotationRepository manager = ExternalAnnotationRepository.get(mClient);

            MethodBinding binding = this.mBinding;
            while (binding != null) {
                AnnotationBinding[] annotations = binding.getAnnotations();
                int count = annotations.length;
                if (count > 0) {
                    for (AnnotationBinding annotation : annotations) {
                        if (annotation != null) {
                            all.add(new EcjResolvedAnnotation(annotation));
                        }
                    }
                }

                // Look for external annotations
                Collection<ResolvedAnnotation> external = manager.getAnnotations(
                        new EcjResolvedMethod(binding));
                if (external != null) {
                    all.addAll(external);
                }

                binding = findSuperMethodBinding(binding);
            }

            all = ensureUnique(all);
            return all;
        }

        @NonNull
        @Override
        public Iterable<ResolvedAnnotation> getParameterAnnotations(int index) {
            List<ResolvedAnnotation> all = Lists.newArrayListWithExpectedSize(4);
            ExternalAnnotationRepository manager = ExternalAnnotationRepository.get(mClient);

            MethodBinding binding = this.mBinding;
            while (binding != null) {
                AnnotationBinding[][] parameterAnnotations = binding.getParameterAnnotations();
                if (parameterAnnotations != null &&
                        index >= 0 && index < parameterAnnotations.length) {
                    AnnotationBinding[] annotations = parameterAnnotations[index];
                    int count = annotations.length;
                    if (count > 0) {
                        for (AnnotationBinding annotation : annotations) {
                            if (annotation != null) {
                                all.add(new EcjResolvedAnnotation(annotation));
                            }
                        }
                    }
                }

                // Look for external annotations
                Collection<ResolvedAnnotation> external = manager.getAnnotations(
                        new EcjResolvedMethod(binding), index);
                if (external != null) {
                    all.addAll(external);
                }

                binding = findSuperMethodBinding(binding);
            }

            all = ensureUnique(all);
            return all;
        }

        @Override
        public int getModifiers() {
            return mBinding.getAccessFlags();
        }

        @Override
        public String getSignature() {
            return mBinding.toString();
        }

        @Override
        public boolean isInPackage(@NonNull String pkgName, boolean includeSubPackages) {
            PackageBinding pkg = mBinding.declaringClass.getPackage();
            //noinspection SimplifiableIfStatement
            if (pkg != null) {
                return includeSubPackages ?
                        startsWithCompound(pkgName, pkg.compoundName) :
                        equalsCompound(pkgName, pkg.compoundName);
            }
            return false;
        }

        @SuppressWarnings("RedundantIfStatement")
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            EcjResolvedMethod that = (EcjResolvedMethod) o;

            if (mBinding != null ? !mBinding.equals(that.mBinding) : that.mBinding != null) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            return mBinding != null ? mBinding.hashCode() : 0;
        }
    }

    /**
     * It is valid (and in fact encouraged by IntelliJ's inspections) to specify the same
     * annotation on overriding methods and overriding parameters. However, we shouldn't
     * return all these "duplicates" when you ask for the annotation on a given element.
     * This method filters out duplicates.
     */
    @VisibleForTesting
    @NonNull
    static List<ResolvedAnnotation> ensureUnique(@NonNull List<ResolvedAnnotation> list) {
        if (list.size() < 2) {
            return list;
        }

        // The natural way to deduplicate would be to create a Set of seen names, iterate
        // through the list and look to see if the current annotation's name is already in the
        // set (if so, remove this annotation from the list, else add it to the set of seen names)
        // but this involves creating the set and all the Map entry objects; that's not
        // necessary here since these lists are always very short 2-5 elements.
        // Instead we'll just do an O(n^2) iteration comparing each subsequent element with each
        // previous element and removing if matches, which is fine for these tiny lists.
        int n = list.size();
        for (int i = 0; i < n - 1; i++) {
            ResolvedAnnotation current = list.get(i);
            String currentName = current.getName();
            // Deleting duplicates at end reduces number of elements that have to be shifted
            for (int j = n - 1; j > i; j--) {
                ResolvedAnnotation later = list.get(j);
                String laterName = later.getName();
                if (currentName.equals(laterName)) {
                    list.remove(j);
                    n--;
                }
            }
        }

        return list;
    }

    private class EcjResolvedClass extends ResolvedClass {
        protected final TypeBinding mBinding;

        private EcjResolvedClass(TypeBinding binding) {
            mBinding = binding;
        }

        @NonNull
        @Override
        public String getName() {
            String name = new String(mBinding.readableName());
            if (name.indexOf('.') == -1 && mBinding.enclosingType() != null) {
                return new String(mBinding.enclosingType().readableName()) + '.' +
                        name;
            }

            return name;
        }

        @NonNull
        @Override
        public String getSimpleName() {
            return new String(mBinding.shortReadableName());
        }

        @Override
        public boolean matches(@NonNull String name) {
            return sameChars(name, mBinding.readableName());
        }

        @Nullable
        @Override
        public ResolvedClass getSuperClass() {
            if (mBinding instanceof ReferenceBinding) {
                ReferenceBinding refBinding = (ReferenceBinding) mBinding;
                ReferenceBinding superClass = refBinding.superclass();
                if (superClass != null) {
                    return new EcjResolvedClass(superClass);
                }
            }

            return null;
        }

        @Nullable
        @Override
        public ResolvedClass getContainingClass() {
            if (mBinding instanceof NestedTypeBinding) {
                NestedTypeBinding ntb = (NestedTypeBinding) mBinding;
                if (ntb.enclosingType != null) {
                    return new EcjResolvedClass(ntb.enclosingType);
                }
            }

            return null;
        }

        @Override
        public boolean isSubclassOf(@NonNull String name, boolean strict) {
            if (mBinding instanceof ReferenceBinding) {
                ReferenceBinding cls = (ReferenceBinding) mBinding;
                if (strict) {
                    cls = cls.superclass();
                }
                for (; cls != null; cls = cls.superclass()) {
                    if (equalsCompound(name, cls.compoundName)) {
                        return true;
                    }
                }
            }

            return false;
        }

        @Override
        public boolean isImplementing(@NonNull String name, boolean strict) {
            if (mBinding instanceof ReferenceBinding) {
                ReferenceBinding cls = (ReferenceBinding) mBinding;
                if (strict) {
                    cls = cls.superclass();
                }
                return isInheritor(cls, name);
            }

            return false;
        }

        @Override
        public boolean isInheritingFrom(@NonNull String name, boolean strict) {
            if (mBinding instanceof ReferenceBinding) {
                ReferenceBinding cls = (ReferenceBinding) mBinding;
                if (strict) {
                    cls = cls.superclass();
                }
                return isInheritor(cls, name);
            }

            return false;
        }

        @Override
        @NonNull
        public Iterable<ResolvedMethod> getConstructors() {
            if (mBinding instanceof ReferenceBinding) {
                ReferenceBinding cls = (ReferenceBinding) mBinding;
                MethodBinding[] methods = cls.getMethods(TypeConstants.INIT);
                if (methods != null) {
                    int count = methods.length;
                    List<ResolvedMethod> result = Lists.newArrayListWithExpectedSize(count);
                    for (MethodBinding method : methods) {
                        if (method.isConstructor()) {
                            result.add(new EcjResolvedMethod(method));
                        }
                    }
                    return result;
                }
            }

            return Collections.emptyList();
        }

        @Override
        @NonNull
        public Iterable<ResolvedMethod> getMethods(@NonNull String name,
                boolean includeInherited) {
            return findMethods(name, includeInherited);
        }

        @Override
        @NonNull
        public Iterable<ResolvedMethod> getMethods(boolean includeInherited) {
            return findMethods(null, includeInherited);
        }

        @NonNull
        private Iterable<ResolvedMethod> findMethods(@Nullable String name,
                boolean includeInherited) {
            if (mBinding instanceof ReferenceBinding) {
                ReferenceBinding cls = (ReferenceBinding) mBinding;
                if (includeInherited) {
                    List<ResolvedMethod> result = null;
                    while (cls != null) {
                        MethodBinding[] methods =
                                name != null ? cls.getMethods(name.toCharArray()) : cls.methods();
                        if (methods != null) {
                            int count = methods.length;
                            if (count > 0) {
                                if (result == null) {
                                    result = Lists.newArrayListWithExpectedSize(count);
                                }
                                for (MethodBinding method : methods) {
                                    if (!method.isConstructor()) {
                                        // See if this method looks like it's masked
                                        boolean masked = false;
                                        for (ResolvedMethod m : result) {
                                            MethodBinding mb = ((EcjResolvedMethod) m).mBinding;
                                            if (mb.areParameterErasuresEqual(method)) {
                                                masked = true;
                                                break;
                                            }
                                        }
                                        if (masked) {
                                            continue;
                                        }

                                        result.add(new EcjResolvedMethod(method));
                                    }
                                }
                            }
                        }
                        cls = cls.superclass();
                    }

                    return result != null ? result : Collections.<ResolvedMethod>emptyList();
                } else {
                    MethodBinding[] methods =
                            name != null ? cls.getMethods(name.toCharArray()) : cls.methods();
                    if (methods != null) {
                        int count = methods.length;
                        List<ResolvedMethod> result = Lists.newArrayListWithExpectedSize(count);
                        for (MethodBinding method : methods) {
                            if (!method.isConstructor()) {
                                result.add(new EcjResolvedMethod(method));
                            }
                        }
                        return result;
                    }
                }
            }

            return Collections.emptyList();
        }

        @NonNull
        @Override
        public Iterable<ResolvedAnnotation> getAnnotations() {
            List<ResolvedAnnotation> all = Lists.newArrayListWithExpectedSize(2);
            ExternalAnnotationRepository manager = ExternalAnnotationRepository.get(mClient);

            if (mBinding instanceof ReferenceBinding) {
                ReferenceBinding cls = (ReferenceBinding) mBinding;
                while (cls != null) {
                    AnnotationBinding[] annotations = cls.getAnnotations();
                    int count = annotations.length;
                    if (count > 0) {
                        all = Lists.newArrayListWithExpectedSize(count);
                        for (AnnotationBinding annotation : annotations) {
                            if (annotation != null) {
                                all.add(new EcjResolvedAnnotation(annotation));
                            }
                        }
                    }

                    // Look for external annotations
                    Collection<ResolvedAnnotation> external = manager.getAnnotations(
                            new EcjResolvedClass(cls));
                    if (external != null) {
                        all.addAll(external);
                    }

                    cls = cls.superclass();
                }
            } else {
                Collection<ResolvedAnnotation> external = manager.getAnnotations(this);
                if (external != null) {
                    all.addAll(external);
                }
            }

            all = ensureUnique(all);
            return all;
        }

        @NonNull
        @Override
        public Iterable<ResolvedField> getFields(boolean includeInherited) {
            if (mBinding instanceof ReferenceBinding) {
                ReferenceBinding cls = (ReferenceBinding) mBinding;
                if (includeInherited) {
                    List<ResolvedField> result = null;
                    while (cls != null) {
                        FieldBinding[] fields = cls.fields();
                        if (fields != null) {
                            int count = fields.length;
                            if (count > 0) {
                                if (result == null) {
                                    result = Lists.newArrayListWithExpectedSize(count);
                                }
                                for (FieldBinding field : fields) {
                                    // See if this field looks like it's masked
                                    boolean masked = false;
                                    for (ResolvedField f : result) {
                                        FieldBinding mb = ((EcjResolvedField) f).mBinding;
                                        if (Arrays.equals(mb.readableName(),
                                                field.readableName())) {
                                            masked = true;
                                            break;
                                        }
                                    }
                                    if (masked) {
                                        continue;
                                    }

                                    result.add(new EcjResolvedField(field));
                                }
                            }
                        }
                        cls = cls.superclass();
                    }

                    return result != null ? result : Collections.<ResolvedField>emptyList();
                } else {
                    FieldBinding[] fields = cls.fields();
                    if (fields != null) {
                        int count = fields.length;
                        List<ResolvedField> result = Lists.newArrayListWithExpectedSize(count);
                        for (FieldBinding field : fields) {
                            result.add(new EcjResolvedField(field));
                        }
                        return result;
                    }
                }
            }

            return Collections.emptyList();
        }

        @Override
        @Nullable
        public ResolvedField getField(@NonNull String name, boolean includeInherited) {
            if (mBinding instanceof ReferenceBinding) {
                ReferenceBinding cls = (ReferenceBinding) mBinding;
                while (cls != null) {
                    FieldBinding[] fields = cls.fields();
                    if (fields != null) {
                        for (FieldBinding field : fields) {
                            if (sameChars(name, field.name)) {
                                return new EcjResolvedField(field);
                            }
                        }
                    }
                    if (includeInherited) {
                        cls = cls.superclass();
                    } else {
                        break;
                    }
                }
            }

            return null;
        }

        @Nullable
        @Override
        public ResolvedPackage getPackage() {
            return new EcjResolvedPackage(mBinding.getPackage());
        }

        @Override
        public int getModifiers() {
            if (mBinding instanceof ReferenceBinding) {
                ReferenceBinding cls = (ReferenceBinding) mBinding;
                // These constants from ClassFileConstants luckily agree with the Modifier
                // constants in the low bits we care about (public, abstract, static, etc)
                return cls.getAccessFlags();
            }
            return 0;
        }

        @Override
        public String getSignature() {
            return getName();
        }

        @Override
        public boolean isInPackage(@NonNull String pkgName, boolean includeSubPackages) {
            PackageBinding pkg = mBinding.getPackage();
            //noinspection SimplifiableIfStatement
            if (pkg != null) {
                return includeSubPackages ?
                        startsWithCompound(pkgName, pkg.compoundName) :
                        equalsCompound(pkgName, pkg.compoundName);
            }
            return false;
        }

        @SuppressWarnings("RedundantIfStatement")
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            EcjResolvedClass that = (EcjResolvedClass) o;

            if (mBinding != null ? !mBinding.equals(that.mBinding) : that.mBinding != null) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            return mBinding != null ? mBinding.hashCode() : 0;
        }
    }

    // "package-info" as a char
    private static final char[] PACKAGE_INFO_CHARS = new char[] {
            'p', 'a', 'c', 'k', 'a', 'g', 'e', '-', 'i', 'n', 'f', 'o'
    };

    private class EcjResolvedPackage extends ResolvedPackage {
        private final PackageBinding mBinding;

        public EcjResolvedPackage(PackageBinding binding) {
            mBinding = binding;
        }

        @NonNull
        @Override
        public String getName() {
            return new String(mBinding.readableName());
        }

        @Override
        public String getSignature() {
            return getName();
        }

        @NonNull
        @Override
        public Iterable<ResolvedAnnotation> getAnnotations() {
            List<ResolvedAnnotation> all = Lists.newArrayListWithExpectedSize(2);

            AnnotationBinding[] annotations = mBinding.getAnnotations();
            int count = annotations.length;
            if (count == 0) {
                Binding pkgInfo = mBinding.getTypeOrPackage(PACKAGE_INFO_CHARS);
                if (pkgInfo != null) {
                    annotations = pkgInfo.getAnnotations();
                }
                count = annotations.length;
            }
            if (count > 0) {
                for (AnnotationBinding annotation : annotations) {
                    if (annotation != null) {
                        all.add(new EcjResolvedAnnotation(annotation));
                    }
                }
            }

            // Merge external annotations
            ExternalAnnotationRepository manager = ExternalAnnotationRepository.get(mClient);
            Collection<ResolvedAnnotation> external = manager.getAnnotations(this);
            if (external != null) {
                all.addAll(external);
            }

            all = ensureUnique(all);
            return all;
        }

        @Override
        @Nullable
        public ResolvedPackage getParentPackage() {
            char[][] compoundName = mBinding.compoundName;
            if (compoundName.length == 1) {
                return null;
            } else {
                PackageBinding defaultPackage = mBinding.environment.defaultPackage;
                PackageBinding packageBinding =
                        (PackageBinding) defaultPackage.getTypeOrPackage(compoundName[0]);
                if (packageBinding == null || packageBinding instanceof ProblemPackageBinding) {
                    return null;
                }

                for (int i = 1, packageLength = compoundName.length - 1; i < packageLength; i++) {
                    Binding next = packageBinding.getTypeOrPackage(compoundName[i]);
                    if (next == null) {
                        return null;
                    }
                    if (next instanceof PackageBinding) {
                        if (next instanceof ProblemPackageBinding) {
                            return null;
                        }
                        packageBinding = (PackageBinding) next;
                    }
                }

                return new EcjResolvedPackage(packageBinding);
            }
        }

        @Override
        public int getModifiers() {
            return 0;
        }
    }

    private class EcjResolvedField extends ResolvedField {
        private FieldBinding mBinding;

        private EcjResolvedField(FieldBinding binding) {
            mBinding = binding;
        }

        @NonNull
        @Override
        public String getName() {
            return new String(mBinding.readableName());
        }

        @Override
        public boolean matches(@NonNull String name) {
            return sameChars(name, mBinding.readableName());
        }

        @NonNull
        @Override
        public TypeDescriptor getType() {
            TypeDescriptor typeDescriptor = getTypeDescriptor(mBinding.type);
            assert typeDescriptor != null; // because mBinding.type is known not to be null
            return typeDescriptor;
        }

        @NonNull
        @Override
        public ResolvedClass getContainingClass() {
            return new EcjResolvedClass(mBinding.declaringClass);
        }

        @Nullable
        @Override
        public Object getValue() {
            return getConstantValue(mBinding.constant());
        }

        @NonNull
        @Override
        public Iterable<ResolvedAnnotation> getAnnotations() {
            List<ResolvedAnnotation> compiled = null;
            AnnotationBinding[] annotations = mBinding.getAnnotations();
            int count = annotations.length;
            if (count > 0) {
                compiled = Lists.newArrayListWithExpectedSize(count);
                for (AnnotationBinding annotation : annotations) {
                    if (annotation != null) {
                        compiled.add(new EcjResolvedAnnotation(annotation));
                    }
                }
            }

            // Look for external annotations
            ExternalAnnotationRepository manager = ExternalAnnotationRepository.get(mClient);
            Collection<ResolvedAnnotation> external = manager.getAnnotations(this);

            return merge(compiled, external);
        }

        @Override
        public int getModifiers() {
            return mBinding.getAccessFlags();
        }

        @Override
        public String getSignature() {
            return mBinding.toString();
        }

        @Override
        public boolean isInPackage(@NonNull String pkgName, boolean includeSubPackages) {
            PackageBinding pkg = mBinding.declaringClass.getPackage();
            //noinspection SimplifiableIfStatement
            if (pkg != null) {
                return includeSubPackages ?
                        startsWithCompound(pkgName, pkg.compoundName) :
                        equalsCompound(pkgName, pkg.compoundName);
            }
            return false;
        }

        @SuppressWarnings("RedundantIfStatement")
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            EcjResolvedField that = (EcjResolvedField) o;

            if (mBinding != null ? !mBinding.equals(that.mBinding) : that.mBinding != null) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            return mBinding != null ? mBinding.hashCode() : 0;
        }
    }

    private class EcjResolvedVariable extends ResolvedVariable {
        private LocalVariableBinding mBinding;

        private EcjResolvedVariable(LocalVariableBinding binding) {
            mBinding = binding;
        }

        @NonNull
        @Override
        public String getName() {
            return new String(mBinding.readableName());
        }

        @Override
        public boolean matches(@NonNull String name) {
            return sameChars(name, mBinding.readableName());
        }

        @NonNull
        @Override
        public TypeDescriptor getType() {
            TypeDescriptor typeDescriptor = getTypeDescriptor(mBinding.type);
            assert typeDescriptor != null; // because mBinding.type is known not to be null
            return typeDescriptor;
        }

        @Override
        public int getModifiers() {
            return mBinding.modifiers;
        }

        @NonNull
        @Override
        public Iterable<ResolvedAnnotation> getAnnotations() {
            AnnotationBinding[] annotations = mBinding.getAnnotations();
            int count = annotations.length;
            if (count > 0) {
                List<ResolvedAnnotation> result = Lists.newArrayListWithExpectedSize(count);
                for (AnnotationBinding annotation : annotations) {
                    if (annotation != null) {
                        result.add(new EcjResolvedAnnotation(annotation));
                    }
                }
                return result;
            }

            // No external annotations for variables

            return Collections.emptyList();
        }

        @Override
        public String getSignature() {
            return mBinding.toString();
        }

        @SuppressWarnings("RedundantIfStatement")
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            EcjResolvedVariable that = (EcjResolvedVariable) o;

            if (mBinding != null ? !mBinding.equals(that.mBinding) : that.mBinding != null) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            return mBinding != null ? mBinding.hashCode() : 0;
        }
    }

    private class EcjResolvedAnnotation extends ResolvedAnnotation {
        private final AnnotationBinding mBinding;
        private final String mName;

        private EcjResolvedAnnotation(@NonNull final AnnotationBinding binding) {
            mBinding = binding;
            mName = new String(mBinding.getAnnotationType().readableName());
        }

        @NonNull
        @Override
        public String getName() {
            return mName;
        }

        @Override
        public boolean matches(@NonNull String name) {
            return name.equals(mName);
        }

        @NonNull
        @Override
        public TypeDescriptor getType() {
            TypeDescriptor typeDescriptor = getTypeDescriptor(mBinding.getAnnotationType());
            assert typeDescriptor != null; // because mBinding.type is known not to be null
            return typeDescriptor;
        }

        @Override
        public ResolvedClass getClassType() {
            ReferenceBinding annotationType = mBinding.getAnnotationType();
            return new EcjResolvedClass(annotationType) {
                @NonNull
                @Override
                public Iterable<ResolvedAnnotation> getAnnotations() {
                    AnnotationBinding[] annotations = mBinding.getAnnotations();
                    int count = annotations.length;
                    if (count > 0) {
                        List<ResolvedAnnotation> result = Lists.newArrayListWithExpectedSize(count);
                        for (AnnotationBinding annotation : annotations) {
                            if (annotation != null) {
                                // Special case: If you look up the annotations *on* annotations,
                                // you're probably working with the typedef annotations, @IntDef
                                // and @StringDef. For these, we can't use the normal annotation
                                // handling, because the compiler only keeps the values of the
                                // constants, not the references to the constants which is what we
                                // care about for those annotations. So in this case, construct
                                // a special subclass of ResolvedAnnotation: EcjAstAnnotation, where
                                // we keep the AST node for the annotation definition such that
                                // we can look up the constant references themselves when queries
                                // via the annotation's getValue() lookup methods.
                                char[] readableName = annotation.getAnnotationType().readableName();
                                if (sameChars(INT_DEF_ANNOTATION, readableName)
                                        || sameChars(STRING_DEF_ANNOTATION, readableName)) {
                                    TypeDeclaration typeDeclaration =
                                            findAnnotationDeclaration(getName());
                                    if (typeDeclaration != null && typeDeclaration.annotations != null) {
                                        Annotation astAnnotation = null;
                                        for (Annotation a : typeDeclaration.annotations) {
                                            if (a.resolvedType != null
                                                    && (sameChars(INT_DEF_ANNOTATION, a.resolvedType.readableName()) ||
                                                    sameChars(STRING_DEF_ANNOTATION, a.resolvedType.readableName()))) {
                                                astAnnotation = a;
                                                break;
                                            }
                                        }

                                        if (astAnnotation != null) {
                                            result.add(new EcjAstAnnotation(annotation, astAnnotation));
                                            continue;
                                        }
                                    } else {
                                        // Don't record these typedef annotations; without
                                        // finding the bindings, we'll get the literal values
                                        // from the ECJ annotation, and that will lead to incorrect
                                        // typedef warnings.
                                        continue;
                                    }
                                }

                                result.add(new EcjResolvedAnnotation(annotation));
                            }
                        }
                        return result;
                    }

                    return Collections.emptyList();
                }
            };
        }

        @NonNull
        @Override
        public List<Value> getValues() {
            ElementValuePair[] pairs = mBinding.getElementValuePairs();
            if (pairs != null && pairs.length > 0) {
                List<Value> values = Lists.newArrayListWithExpectedSize(pairs.length);
                for (ElementValuePair pair : pairs) {
                    values.add(new Value(new String(pair.getName()), getPairValue(pair)));
                }
            }

            return Collections.emptyList();
        }

        @Nullable
        @Override
        public Object getValue(@NonNull String name) {
            ElementValuePair[] pairs = mBinding.getElementValuePairs();
            if (pairs != null) {
                for (ElementValuePair pair : pairs) {
                    if (sameChars(name, pair.getName())) {
                        return getPairValue(pair);
                    }
                }
            }

            return null;
        }

        private Object getPairValue(ElementValuePair pair) {
            return getConstantValue(pair.getValue());
        }

        @Override
        public String getSignature() {
            return mName;
        }

        @Override
        public int getModifiers() {
            // Not applicable; move from ResolvedNode into ones that matter?
            return 0;
        }

        @NonNull
        @Override
        public Iterable<ResolvedAnnotation> getAnnotations() {
            List<ResolvedAnnotation> compiled = null;
            AnnotationBinding[] annotations = mBinding.getAnnotationType().getAnnotations();
            int count = annotations.length;
            if (count > 0) {
                compiled = Lists.newArrayListWithExpectedSize(count);
                for (AnnotationBinding annotation : annotations) {
                    if (annotation != null) {
                        compiled.add(new EcjResolvedAnnotation(annotation));
                    }
                }
            }

            // Look for external annotations
            ExternalAnnotationRepository manager = ExternalAnnotationRepository.get(mClient);
            Collection<ResolvedAnnotation> external = manager.getAnnotations(this);

            return merge(compiled, external);
        }

        private class EcjAstAnnotation extends EcjResolvedAnnotation {

            private final Annotation mAstAnnotation;
            private List<Value> mValues;

            public EcjAstAnnotation(
                    @NonNull AnnotationBinding binding, @NonNull Annotation astAnnotation) {
                super(binding);
                mAstAnnotation = astAnnotation;
            }

            @NonNull
            @Override
            public List<Value> getValues() {
                if (mValues == null) {
                    MemberValuePair[] memberValuePairs = mAstAnnotation.memberValuePairs();
                    List<Value> result = Lists
                            .newArrayListWithExpectedSize(memberValuePairs.length);

                    for (MemberValuePair pair : memberValuePairs) {
                        //  String n = new String(pair.name);
                        Expression expression = pair.value;
                        Object value = null;
                        if (expression instanceof ArrayInitializer) {
                            ArrayInitializer initializer = (ArrayInitializer) expression;
                            Expression[] expressions = initializer.expressions;
                            List<Object> values = Lists.newArrayList();
                            for (Expression e : expressions) {
                                if (e instanceof NameReference) {
                                    ResolvedNode resolved = resolve(((NameReference) e).binding);
                                    if (resolved != null) {
                                        values.add(resolved);
                                    }
                                } else if (e instanceof IntLiteral) {
                                    values.add(((IntLiteral) e).value);
                                } else if (e instanceof StringLiteral) {
                                    values.add(String.valueOf(((StringLiteral) e).source()));
                                } else {
                                    values.add(e.toString());
                                }
                            }
                            value = values.toArray();
                        } else if (expression instanceof IntLiteral) {
                            IntLiteral intLiteral = (IntLiteral) expression;
                            value = intLiteral.value;
                        } else if (expression instanceof TrueLiteral) {
                            value = true;
                        } else if (expression instanceof FalseLiteral) {
                            value = false;
                        } else if (expression instanceof StringLiteral) {
                            value = String.valueOf(((StringLiteral) expression).source());
                        }
                        // Unfortunately, FloatLiteral, LongLiteral etc do not
                        // expose the value field as public. Luckily, we don't need that
                        // for our current annotations.

                        result.add(new Value(new String(pair.name), value));
                    }
                    mValues = result;
                }

                return mValues;
            }

            @Nullable
            @Override
            public Object getValue(@NonNull String name) {
                for (Value value : getValues()) {
                    if (name.equals(value.name)) {
                        return value.value;
                    }
                }
                return null;
            }
        }

        @SuppressWarnings("RedundantIfStatement")
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            EcjResolvedAnnotation that = (EcjResolvedAnnotation) o;

            if (mBinding != null ? !mBinding.equals(that.mBinding) : that.mBinding != null) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            return mBinding != null ? mBinding.hashCode() : 0;
        }
    }

    @Nullable
    private Object getConstantValue(@Nullable Object value) {
        if (value instanceof Constant) {
            if (value == Constant.NotAConstant) {
                return null;
            }
            if (value instanceof StringConstant) {
                return ((StringConstant) value).stringValue();
            } else if (value instanceof IntConstant) {
                return ((IntConstant) value).intValue();
            } else if (value instanceof BooleanConstant) {
                return ((BooleanConstant) value).booleanValue();
            } else if (value instanceof FloatConstant) {
                return ((FloatConstant) value).floatValue();
            } else if (value instanceof LongConstant) {
                return ((LongConstant) value).longValue();
            } else if (value instanceof DoubleConstant) {
                return ((DoubleConstant) value).doubleValue();
            } else if (value instanceof ShortConstant) {
                return ((ShortConstant) value).shortValue();
            } else if (value instanceof CharConstant) {
                return ((CharConstant) value).charValue();
            } else if (value instanceof ByteConstant) {
                return ((ByteConstant) value).byteValue();
            }
        } else if (value instanceof Object[]) {
            Object[] array = (Object[]) value;
            if (array.length > 0) {
                List<Object> list = Lists.newArrayListWithExpectedSize(array.length);
                for (Object element : array) {
                    list.add(getConstantValue(element));
                }
                // Pick type of array. Annotations are limited to Strings, Classes
                // and Annotations
                if (!list.isEmpty()) {
                    Object first = list.get(0);
                    if (first instanceof String) {
                        //noinspection SuspiciousToArrayCall
                        return list.toArray(new String[list.size()]);
                    } else if (first instanceof java.lang.annotation.Annotation) {
                        //noinspection SuspiciousToArrayCall
                        return list.toArray(new Annotation[list.size()]);
                    } else if (first instanceof Class) {
                        //noinspection SuspiciousToArrayCall
                        return list.toArray(new Class[list.size()]);
                    }
                }

                return list.toArray();
            }
        } else if (value instanceof AnnotationBinding) {
            return new EcjResolvedAnnotation((AnnotationBinding) value);
        } else if (value instanceof FieldBinding) {
            return new EcjResolvedField((FieldBinding)value);
        }

        return value;
    }

    private static boolean sameChars(String str, char[] chars) {
        int length = str.length();
        if (chars.length != length) {
            return false;
        }

        for (int i = 0; i < length; i++) {
            if (chars[i] != str.charAt(i)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Does the given compound name match the given string?
     * <p>
     * TODO: Check if ECJ already has this as a utility somewhere
     */
    @VisibleForTesting
    static boolean startsWithCompound(@NonNull String name, @NonNull char[][] compoundName) {
        int length = name.length();
        if (length == 0) {
            return false;
        }
        int index = 0;
        for (int i = 0, n = compoundName.length; i < n; i++) {
            char[] o = compoundName[i];
            //noinspection ForLoopReplaceableByForEach
            for (int j = 0, m = o.length; j < m; j++) {
                if (index == length) {
                    return false; // Don't allow prefix in a compound name
                }
                if (name.charAt(index) != o[j]
                        // Allow using . as an inner class separator whereas the
                        // symbol table will always use $
                        && !(o[j] == '$' && name.charAt(index) == '.')) {
                    return false;
                }
                index++;
            }
            if (i < n - 1) {
                if (index == length) {
                    return true;
                }
                if (name.charAt(index) != '.') {
                    return false;
                }
                index++;
                if (index == length) {
                    return true;
                }
            }
        }

        return index == length;
    }

    @VisibleForTesting
    static boolean equalsCompound(@NonNull String name, @NonNull char[][] compoundName) {
        int length = name.length();
        if (length == 0) {
            return false;
        }
        int index = 0;
        for (int i = 0, n = compoundName.length; i < n; i++) {
            char[] o = compoundName[i];
            //noinspection ForLoopReplaceableByForEach
            for (int j = 0, m = o.length; j < m; j++) {
                if (index == length) {
                    return false; // Don't allow prefix in a compound name
                }
                if (name.charAt(index) != o[j]
                        // Allow using . as an inner class separator whereas the
                        // symbol table will always use $
                        && !(o[j] == '$' && name.charAt(index) == '.')) {
                    return false;
                }
                index++;
            }
            if (i < n - 1) {
                if (index == length) {
                    return false;
                }
                if (name.charAt(index) != '.') {
                    return false;
                }
                index++;
                if (index == length) {
                    return false;
                }
            }
        }

        return index == length;
    }

    /** Checks whether the given class extends or implements a class with the given name */
    private static boolean isInheritor(@Nullable ReferenceBinding cls, @NonNull String name) {
        for (; cls != null; cls = cls.superclass()) {
            ReferenceBinding[] interfaces = cls.superInterfaces();
            for (ReferenceBinding binding : interfaces) {
                if (isInheritor(binding, name)) {
                    return true;
                }
            }

            if (equalsCompound(name, cls.compoundName)) {
                return true;
            }
        }

        return false;
    }
}