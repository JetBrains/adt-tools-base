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

import static com.android.SdkConstants.UTF_8;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.lint.client.api.JavaParser;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import org.eclipse.jdt.internal.compiler.CompilationResult;
import org.eclipse.jdt.internal.compiler.Compiler;
import org.eclipse.jdt.internal.compiler.DefaultErrorHandlingPolicies;
import org.eclipse.jdt.internal.compiler.ICompilerRequestor;
import org.eclipse.jdt.internal.compiler.IErrorHandlingPolicy;
import org.eclipse.jdt.internal.compiler.IProblemFactory;
import org.eclipse.jdt.internal.compiler.ast.AbstractMethodDeclaration;
import org.eclipse.jdt.internal.compiler.ast.AllocationExpression;
import org.eclipse.jdt.internal.compiler.ast.Annotation;
import org.eclipse.jdt.internal.compiler.ast.CharLiteral;
import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration;
import org.eclipse.jdt.internal.compiler.ast.DoubleLiteral;
import org.eclipse.jdt.internal.compiler.ast.ExplicitConstructorCall;
import org.eclipse.jdt.internal.compiler.ast.Expression;
import org.eclipse.jdt.internal.compiler.ast.FalseLiteral;
import org.eclipse.jdt.internal.compiler.ast.FloatLiteral;
import org.eclipse.jdt.internal.compiler.ast.IntLiteral;
import org.eclipse.jdt.internal.compiler.ast.Literal;
import org.eclipse.jdt.internal.compiler.ast.LongLiteral;
import org.eclipse.jdt.internal.compiler.ast.MagicLiteral;
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
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;
import org.eclipse.jdt.internal.compiler.lookup.Binding;
import org.eclipse.jdt.internal.compiler.lookup.FieldBinding;
import org.eclipse.jdt.internal.compiler.lookup.LocalVariableBinding;
import org.eclipse.jdt.internal.compiler.lookup.MethodBinding;
import org.eclipse.jdt.internal.compiler.lookup.NestedTypeBinding;
import org.eclipse.jdt.internal.compiler.lookup.ProblemBinding;
import org.eclipse.jdt.internal.compiler.lookup.ProblemFieldBinding;
import org.eclipse.jdt.internal.compiler.lookup.ProblemMethodBinding;
import org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding;
import org.eclipse.jdt.internal.compiler.lookup.TypeBinding;
import org.eclipse.jdt.internal.compiler.parser.Parser;
import org.eclipse.jdt.internal.compiler.problem.AbortCompilation;
import org.eclipse.jdt.internal.compiler.problem.DefaultProblemFactory;
import org.eclipse.jdt.internal.compiler.problem.ProblemReporter;

import java.io.File;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import lombok.ast.Node;
import lombok.ast.VariableDeclaration;
import lombok.ast.VariableDefinition;
import lombok.ast.ecj.EcjTreeConverter;

/**
 * Java parser which uses ECJ for parsing and type attribution
 */
public class EcjParser extends JavaParser {

    private final LintClient mClient;
    private final Project mProject;
    private Map<File, ICompilationUnit> mSourceUnits;
    private Map<ICompilationUnit, CompilationUnitDeclaration> mCompiled;
    private Parser mParser;

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
        mCompiled = Maps.newHashMapWithExpectedSize(mSourceUnits.size());
        parse(createCompilerOptions(), sources, classPath, mCompiled);
    }

    /** Parse the given source units and class path and store it into the given output map */
    public static void parse(
            CompilerOptions options,
            @NonNull List<ICompilationUnit> sourceUnits,
            @NonNull List<String> classPath,
            @NonNull Map<ICompilationUnit, CompilationUnitDeclaration> outputMap) {
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
        Compiler compiler = new NonGeneratingCompiler(environment, policy, options, requestor,
                problemFactory, outputMap);
        compiler.compile(sourceUnits.toArray(new ICompilationUnit[sourceUnits.size()]));
    }

    @NonNull
    private List<String> computeClassPath(@NonNull List<JavaContext> contexts) {
        assert mProject != null;
        List<String> classPath = Lists.newArrayList();

        IAndroidTarget compileTarget = mProject.getBuildTarget();
        if (compileTarget != null) {
            String androidJar = compileTarget.getPath(IAndroidTarget.ANDROID_JAR);
            if (androidJar != null) {
                classPath.add(androidJar);
            }
        }

        Set<File> libraries = Sets.newHashSet();
        Set<String> names = Sets.newHashSet();
        for (File library : mProject.getJavaLibraries()) {
            libraries.add(library);
            names.add(library.getName());
        }
        for (Project project : mProject.getAllLibraries()) {
            for (File library : project.getJavaLibraries()) {
                String name = library.getName();
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
            classPath.add(file.getPath());
        }

        // In incremental mode we may need to point to other sources in the project
        // for type resolution
        EnumSet<Scope> scope = contexts.get(0).getScope();
        if (!scope.contains(Scope.ALL_JAVA_FILES)) {
            // May need other compiled classes too
            for (File dir : mProject.getJavaClassFolders()) {
                classPath.add(dir.getPath());
            }
        }

        return classPath;
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
        if (mSourceUnits != null && mCompiled != null) {
            sourceUnit = mSourceUnits.get(context.file);
            if (sourceUnit != null) {
                CompilationUnitDeclaration unit = mCompiled.get(sourceUnit);
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
    public void dispose(@NonNull JavaContext context,
            @NonNull Node compilationUnit) {
        if (mSourceUnits != null && mCompiled != null) {
            ICompilationUnit sourceUnit = mSourceUnits.get(context.file);
            if (sourceUnit != null) {
                mSourceUnits.remove(context.file);
                mCompiled.remove(sourceUnit);
            }
        }
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
            return resolve(((Annotation) nativeNode).resolvedType);
        } else if (nativeNode instanceof AbstractMethodDeclaration) {
            return resolve(((AbstractMethodDeclaration) nativeNode).binding);
        }

        // TODO: Handle org.eclipse.jdt.internal.compiler.ast.SuperReference. It
        // doesn't contain an actual method binding; the parent node call should contain
        // it, but is missing a native node reference; investigate the ECJ bridge's super
        // handling.

        return null;
    }

    private static ResolvedNode resolve(@Nullable Binding binding) {
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
            nativeNode = ((AllocationExpression) nativeNode).binding;
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
    private static TypeDescriptor getTypeDescriptor(@Nullable TypeBinding resolvedType) {
        if (resolvedType == null) {
            return null;
        }
        return new EcjTypeDescriptor(resolvedType.readableName());
    }

    private static TypeDescriptor getTypeDescriptor(String fqn) {
        return new DefaultTypeDescriptor(fqn);
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

        public NonGeneratingCompiler(INameEnvironment environment, IErrorHandlingPolicy policy,
                CompilerOptions options, ICompilerRequestor requestor,
                IProblemFactory problemFactory,
                Map<ICompilationUnit, CompilationUnitDeclaration> units) {
            super(environment, policy, options, requestor, problemFactory, null, null);
            mUnits = units;
        }

        @Override
        protected synchronized void addCompilationUnit(ICompilationUnit sourceUnit,
                CompilationUnitDeclaration parsedUnit) {
            super.addCompilationUnit(sourceUnit, parsedUnit);
            mUnits.put(sourceUnit, parsedUnit);
        }

        @Override
        public void process(CompilationUnitDeclaration unit, int unitNumber) {
            lookupEnvironment.unitBeingCompleted = unit;
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

    }

    private static class EcjTypeDescriptor extends TypeDescriptor {
        private String mName;
        private char[] mChars;

        private EcjTypeDescriptor(char[] chars) {
            mChars = chars;
        }

        @NonNull
        @Override
        public String getName() {
            if (mName == null) {
                mName = new String(mChars);
            }
            return mName;
        }

        @NonNull
        @Override
        public String getSignature() {
            return getName();
        }

        @Override
        public boolean matchesName(@NonNull String name) {
            int length = name.length();
            if (mChars.length != length) {
                return false;
            }
            for (int i = 0; i < length; i++) {
                if (mChars[i] != name.charAt(i)) {
                    return false;
                }
            }

            return true;
        }

        @Override
        public boolean matchesSignature(@NonNull String signature) {
            return matchesName(signature);
        }

        @Override
        public String toString() {
            return getSignature();
        }
    }

    private static class EcjResolvedMethod extends ResolvedMethod {
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
            int length = name.length();
            char[] c = isConstructor() ? mBinding.declaringClass.readableName() : mBinding.selector;
            if (c.length != length) {
                return false;
            }
            for (int i = 0; i < length; i++) {
                if (c[i] != name.charAt(i)) {
                    return false;
                }
            }

            return true;
        }

        @NonNull
        @Override
        public TypeDescriptor getContainingClass() {
            return new EcjTypeDescriptor(mBinding.declaringClass.readableName());
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
        public String getSignature() {
            return mBinding.toString();
        }
    }

    private static class EcjResolvedClass extends ResolvedClass {
        private TypeBinding mBinding;

        private EcjResolvedClass(TypeBinding binding) {
            mBinding = binding;
        }

        @NonNull
        @Override
        public String getName() {
            return new String(mBinding.readableName());
        }

        @Override
        public boolean matches(@NonNull String name) {
            int length = name.length();
            char[] chars = mBinding.readableName();
            if (chars.length != length) {
                return false;
            }
            for (int i = 0; i < length; i++) {
                if (chars[i] != name.charAt(i)) {
                    return false;
                }
            }

            return true;
        }

        @Nullable
        @Override
        public TypeDescriptor getSuperClass() {
            if (mBinding instanceof ReferenceBinding) {
                ReferenceBinding refBinding = (ReferenceBinding) mBinding;
                ReferenceBinding superClass = refBinding.superclass();
                if (superClass != null) {
                    return getTypeDescriptor(superClass);
                }
            }

            return null;
        }

        @Nullable
        @Override
        public TypeDescriptor getContainingClass() {
            if (mBinding instanceof NestedTypeBinding) {
                NestedTypeBinding ntb = (NestedTypeBinding) mBinding;
                if (ntb.enclosingType != null) {
                    return getTypeDescriptor(ntb.enclosingType);
                }
            }

            return null;
        }

        @Override
        public String getSignature() {
            return getName();
        }
    }

    private static class EcjResolvedField extends ResolvedField {
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
            int length = name.length();
            char[] chars = mBinding.readableName();
            if (chars.length != length) {
                return false;
            }
            for (int i = 0; i < length; i++) {
                if (chars[i] != name.charAt(i)) {
                    return false;
                }
            }

            return true;
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
        public TypeDescriptor getContainingClass() {
            TypeDescriptor typeDescriptor = getTypeDescriptor(mBinding.declaringClass);
            assert typeDescriptor != null; // because declaringClass is known not to be null
            return typeDescriptor;
        }

        @Override
        public String getSignature() {
            return mBinding.toString();
        }
    }

    private static class EcjResolvedVariable extends ResolvedVariable {
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
            int length = name.length();
            char[] chars = mBinding.readableName();
            if (chars.length != length) {
                return false;
            }
            for (int i = 0; i < length; i++) {
                if (chars[i] != name.charAt(i)) {
                    return false;
                }
            }

            return true;
        }

        @NonNull
        @Override
        public TypeDescriptor getType() {
            TypeDescriptor typeDescriptor = getTypeDescriptor(mBinding.type);
            assert typeDescriptor != null; // because mBinding.type is known not to be null
            return typeDescriptor;
        }

        @Override
        public String getSignature() {
            return mBinding.toString();
        }
    }
}
