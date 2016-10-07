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

package com.android.tools.lint.psi;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.tools.lint.EcjParser;
import com.android.tools.lint.ExternalAnnotationRepository;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.detector.api.ClassContext;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.MapMaker;
import com.google.common.collect.Maps;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaRecursiveElementVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;

import org.eclipse.jdt.internal.compiler.ast.ASTNode;
import org.eclipse.jdt.internal.compiler.ast.AbstractMethodDeclaration;
import org.eclipse.jdt.internal.compiler.ast.AllocationExpression;
import org.eclipse.jdt.internal.compiler.ast.Annotation;
import org.eclipse.jdt.internal.compiler.ast.ConstructorDeclaration;
import org.eclipse.jdt.internal.compiler.ast.FieldReference;
import org.eclipse.jdt.internal.compiler.ast.ImportReference;
import org.eclipse.jdt.internal.compiler.ast.LocalDeclaration;
import org.eclipse.jdt.internal.compiler.ast.MessageSend;
import org.eclipse.jdt.internal.compiler.ast.MethodDeclaration;
import org.eclipse.jdt.internal.compiler.ast.NameReference;
import org.eclipse.jdt.internal.compiler.ast.QualifiedNameReference;
import org.eclipse.jdt.internal.compiler.ast.TypeDeclaration;
import org.eclipse.jdt.internal.compiler.ast.TypeReference;
import org.eclipse.jdt.internal.compiler.ast.UnionTypeReference;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;
import org.eclipse.jdt.internal.compiler.impl.BooleanConstant;
import org.eclipse.jdt.internal.compiler.impl.ByteConstant;
import org.eclipse.jdt.internal.compiler.impl.CharConstant;
import org.eclipse.jdt.internal.compiler.impl.Constant;
import org.eclipse.jdt.internal.compiler.impl.DoubleConstant;
import org.eclipse.jdt.internal.compiler.impl.FloatConstant;
import org.eclipse.jdt.internal.compiler.impl.IntConstant;
import org.eclipse.jdt.internal.compiler.impl.LongConstant;
import org.eclipse.jdt.internal.compiler.impl.ShortConstant;
import org.eclipse.jdt.internal.compiler.impl.StringConstant;
import org.eclipse.jdt.internal.compiler.lookup.ArrayBinding;
import org.eclipse.jdt.internal.compiler.lookup.BaseTypeBinding;
import org.eclipse.jdt.internal.compiler.lookup.Binding;
import org.eclipse.jdt.internal.compiler.lookup.FieldBinding;
import org.eclipse.jdt.internal.compiler.lookup.LocalVariableBinding;
import org.eclipse.jdt.internal.compiler.lookup.LookupEnvironment;
import org.eclipse.jdt.internal.compiler.lookup.MethodBinding;
import org.eclipse.jdt.internal.compiler.lookup.MethodScope;
import org.eclipse.jdt.internal.compiler.lookup.ParameterizedFieldBinding;
import org.eclipse.jdt.internal.compiler.lookup.ParameterizedMethodBinding;
import org.eclipse.jdt.internal.compiler.lookup.ParameterizedTypeBinding;
import org.eclipse.jdt.internal.compiler.lookup.PolyTypeBinding;
import org.eclipse.jdt.internal.compiler.lookup.ProblemReferenceBinding;
import org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding;
import org.eclipse.jdt.internal.compiler.lookup.Scope;
import org.eclipse.jdt.internal.compiler.lookup.SourceTypeBinding;
import org.eclipse.jdt.internal.compiler.lookup.TypeBinding;
import org.eclipse.jdt.internal.compiler.lookup.WildcardBinding;

import java.util.List;
import java.util.Map;

/**
 * A manager for the ECJ-based PSI elements, which handles PSI node registration,
 * ECJ to PSI element lookup, type resolution, etc. Note that this is not an
 * instance of com.intellij.psi.PsiManager (which is not included in Lint's PSI
 * subset library since that class contains mostly hooks back to pointers outside
 * of PSI, such as the project system, etc.
 */
public class EcjPsiManager {

    /**
     * Whether for a {@link #findElement(Binding)} call we allow returning a binary
     * binding for bindings found in local types.
     */
    private static final boolean RESOLVE_TO_BINARY = false;

    private final LanguageLevel mLanguageLevel;
    private final Map<Binding, PsiElement> mElementMap;
    private final Map<ReferenceBinding,PsiType> mTypeMap;
    private final LintClient mClient;
    private final EcjParser.EcjResult mEcjResult;

    private ExternalAnnotationRepository mAnnotationRepository;


    public EcjPsiManager(
            @Nullable LintClient client,
            @NonNull EcjParser.EcjResult ecjResult,
            long ecjLanguageLevel) {
        mClient = client;
        mEcjResult = ecjResult;
        mLanguageLevel = toLanguageLevel(ecjLanguageLevel);
        // TODO: Base these numbers on the class count estimate from the ECJ result
        mElementMap = new MapMaker()
                .initialCapacity(1000)
                .weakValues()
                .concurrencyLevel(1)
                .makeMap();
        mTypeMap = Maps.newHashMapWithExpectedSize(50);

    }

    @NonNull
    static String getInternalName(@NonNull char[][] name) {
        StringBuilder sb = new StringBuilder(50);
        for (char[] segment : name) {
            if (sb.length() != 0) {
                sb.append('/');
            }
            for (char c : segment) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    @NonNull
    public static String getTypeName(@NonNull char[][] name) {
        StringBuilder sb = new StringBuilder(50);
        for (char[] segment : name) {
            if (sb.length() != 0) {
                sb.append('.');
            }
            for (char c : segment) {
                if (c == '$') {
                    c = '.';
                }
                sb.append(c);
            }
        }
        return sb.toString();
    }

    @NonNull
    public static String getTypeName(@NonNull char[] name) {
        StringBuilder sb = new StringBuilder(name.length);
        for (char c : name) {
            if (c == '$' || c == '/') {
                c = '.';
            }
            sb.append(c);
        }
        return sb.toString();
    }

    @NonNull
    public static String getTypeName(@NonNull TypeReference typeReference) {
        return getTypeName(typeReference.getTypeName());
    }

    @Nullable
    public static String getTypeName(@NonNull ReferenceBinding binding) {
        String typeName = null;
        //noinspection ConstantConditions
        while (binding != null) {
            if (binding.compoundName == null) {
                if (binding instanceof WildcardBinding) {
                    binding = ((WildcardBinding) binding).genericType;
                    if (binding.compoundName == null) {
                        return null;
                    }
                } else {
                    return null;
                }
            }
            String prefix = getTypeName(binding.compoundName);
            if (binding.compoundName.length >= 2 && !Character.isUpperCase(prefix.charAt(0))) {
                // Some bindings are already fully qualified, and others are not. ECJ does
                // not seem consistent on this point, so instead, use heuristic that
                // fully qualified names use package elements (lower case) and specify at least
                // two segments
                return prefix;
            }
            if (typeName == null) {
                typeName = prefix;
            } else if (typeName.startsWith(".")) {
                typeName = prefix + typeName;
            } else {
                typeName = prefix + "." + typeName;
            }
            binding = binding.enclosingType();
        }
        return typeName;
    }

    public LintClient getClient() {
        return mClient;
    }

    @NonNull
    private static LanguageLevel toLanguageLevel(long ecjLanguageLevel) {
        if (ecjLanguageLevel == ClassFileConstants.JDK1_7) {
            return LanguageLevel.JDK_1_7;
        }
        if (ecjLanguageLevel == ClassFileConstants.JDK1_6) {
            return LanguageLevel.JDK_1_6;
        }
        if (ecjLanguageLevel == ClassFileConstants.JDK1_8) {
            return LanguageLevel.JDK_1_8;
        }
        if (ecjLanguageLevel == ClassFileConstants.JDK1_5) {
            return LanguageLevel.JDK_1_5;
        }

        return LanguageLevel.JDK_1_7;
    }

    @Nullable
    public PsiClass findClass(@Nullable TypeReference typeReference) {
        if (typeReference == null) {
            return null;
        }

        TypeBinding resolvedType = typeReference.resolvedType;
        if (resolvedType instanceof ReferenceBinding) {
            ReferenceBinding type = (ReferenceBinding) resolvedType;
            return findClass(type);
        }

        return null;
    }

    @NonNull
    public LanguageLevel getLanguageLevel() {
        return mLanguageLevel;
    }

    @SuppressWarnings("MethodMayBeStatic")
    @Nullable
    public PsiType findType(@NonNull AbstractMethodDeclaration declaration) {
        if (declaration.isConstructor()) {
            MethodBinding binding = ((ConstructorDeclaration) declaration).binding;
            if (binding != null) {
                return findType(binding.declaringClass);
            }
            return null;
        } else if (declaration instanceof MethodDeclaration) {
            MethodDeclaration methodDeclaration = (MethodDeclaration) declaration;
            TypeReference type = methodDeclaration.returnType;
            return findType(type);
        }
        return null;
    }

    @Nullable
    public PsiType findType(@Nullable TypeReference type) {
        if (type != null) {
            if (type instanceof UnionTypeReference) {
                UnionTypeReference unionTypeReference = (UnionTypeReference) type;
                List<PsiType> types = Lists.newArrayListWithCapacity(
                        unionTypeReference.typeReferences.length);
                for (TypeReference ref : unionTypeReference.typeReferences) {
                    PsiType t = findType(ref);
                    if (t != null) {
                        types.add(t);
                    }
                }
                PsiType leastUpperBound = findType(unionTypeReference.resolvedType);
                return new EcjPsiDisjunctionType(types, leastUpperBound);
            }
            return findType(type.resolvedType);
        }
        return null;
    }

    @Nullable
    public PsiType findType(@Nullable ReferenceBinding referenceBinding) {
        if (referenceBinding != null) {
            PsiType type = mTypeMap.get(referenceBinding);
            if (type == null) {
                type = new EcjPsiClassType(this, referenceBinding);
                mTypeMap.put(referenceBinding, type);
            }
            return type;
        }

        return null;
    }

    @Nullable
    public PsiType findType(@Nullable TypeBinding typeBinding) {
        if (typeBinding instanceof ReferenceBinding) {
            return findType((ReferenceBinding)typeBinding);
        } else if (typeBinding instanceof BaseTypeBinding) {
            if (typeBinding == BaseTypeBinding.INT) {
                return PsiType.INT;
            } else if (typeBinding == BaseTypeBinding.BOOLEAN) {
                return PsiType.BOOLEAN;
            } else if (typeBinding == BaseTypeBinding.VOID) {
                return PsiType.VOID;
            } else if (typeBinding == BaseTypeBinding.LONG) {
                return PsiType.LONG;
            } else if (typeBinding == BaseTypeBinding.DOUBLE) {
                return PsiType.DOUBLE;
            } else if (typeBinding == BaseTypeBinding.BYTE) {
                return PsiType.BYTE;
            } else if (typeBinding == BaseTypeBinding.SHORT) {
                return PsiType.SHORT;
            } else if (typeBinding == BaseTypeBinding.CHAR) {
                return PsiType.CHAR;
            } else if (typeBinding == BaseTypeBinding.FLOAT) {
                return PsiType.FLOAT;
            } else if (typeBinding == BaseTypeBinding.NULL) {
                return PsiType.NULL;
            }
        } else if (typeBinding instanceof ArrayBinding) {
            ArrayBinding binding = (ArrayBinding) typeBinding;
            PsiType type = findType(binding.leafComponentType);
            if (type != null) {
                for (int i = 0; i < binding.dimensions; i++) {
                    type = type.createArrayType();
                }
            }
            return type;
        } else if (typeBinding instanceof PolyTypeBinding) {
            // There isn't an accessor on PolyTypeBinding which gives us the type we need :-(
            // (it's only available on PolyTypeBinding.expression (either valueIfTrue or
            // valueIfFalse)
            return null;
        }

        return null;
    }

    @Nullable
    public PsiClassType findClassType(@Nullable ReferenceBinding binding) {
        if (binding == null) {
            return null;
        }
        PsiType type = findType(binding);
        if (type instanceof PsiClassType) {
            return (PsiClassType) type;
        }

        return null;
    }

    @NonNull
    public PsiClassType[] findClassTypes(@Nullable ReferenceBinding[] referenceBindings) {
        if (referenceBindings == null || referenceBindings.length == 0) {
            return PsiClassType.EMPTY_ARRAY;
        }
        List<PsiClassType> types = Lists.newArrayListWithCapacity(referenceBindings.length);
        for (ReferenceBinding binding : referenceBindings) {
            PsiType type = findType(binding);
            if (type instanceof PsiClassType) {
                types.add((PsiClassType) type);
            }
        }

        return types.toArray(PsiClassType.EMPTY_ARRAY);
    }

    @Nullable
    public PsiClassType getClassType(@NonNull PsiClass psiClass) {
        if (psiClass instanceof EcjPsiClass) {
            TypeDeclaration typeDeclaration = (TypeDeclaration)
                    ((EcjPsiClass)psiClass).getNativeNode();
            assert typeDeclaration != null;
            return new EcjPsiClassType(this, (typeDeclaration.binding));
        } else if (psiClass instanceof EcjPsiBinaryClass) {
            ReferenceBinding binding =
                    (ReferenceBinding)((EcjPsiBinaryClass) psiClass).getBinding();
            return new EcjPsiClassType(this, binding);
        }

        return null;
    }

    @NonNull
    public PsiClassType[] getClassTypes(@Nullable PsiClass[] classes) {
        if (classes != null && classes.length > 0) {
            List<PsiClassType> types = Lists.newArrayListWithCapacity(classes.length);
            for (PsiClass cls : classes) {
                PsiClassType classType = getClassType(cls);
                if (classType != null) {
                    types.add(classType);
                }
            }
            return types.toArray(PsiClassType.EMPTY_ARRAY);
        }

        return PsiClassType.EMPTY_ARRAY;
    }

    public PsiClass findClass(@NonNull String fullyQualifiedName) {
        // findClass will only work if mLookupEnvironment is non null;
        // avoid computing compound strings if it isn't.
        //noinspection VariableNotUsedInsideIf
        if (mEcjResult.getLookupEnvironment() != null) {
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

            return findClass(compoundName);
        }
        return null;
    }

    public PsiClass findClass(@NonNull char[][] compoundName) {
        LookupEnvironment lookupEnvironment = mEcjResult.getLookupEnvironment();
        if (lookupEnvironment != null) {
            ReferenceBinding type = lookupEnvironment.getType(compoundName);
            if (type != null && !(type instanceof ProblemReferenceBinding)) {
                return findClass(type);
            }
        }

        return null;
    }

    @Nullable
    public PsiClass findClass(@Nullable Binding referenceBinding) {
        return (PsiClass) findElement(referenceBinding);
    }

    @Nullable
    public PsiMethod findMethod(@Nullable Binding binding) {
        return (PsiMethod) findElement(binding);
    }

    @SuppressWarnings("VariableNotUsedInsideIf")
    @NonNull
    public PsiClass[] findClasses(@Nullable ReferenceBinding binding,
            @Nullable ReferenceBinding[] bindings) {
        int count = 0;
        if (binding != null) {
            count++;
        }
        if (bindings != null) {
            count += bindings.length;
        }

        if (count == 0) {
            return PsiClass.EMPTY_ARRAY;
        }
        List<PsiClass> classes = Lists.newArrayListWithCapacity(count);
        if (binding != null) {
            PsiClass cls = findClass(binding);
            if (cls != null) {
                classes.add(cls);
            }
        }
        if (bindings != null) {
            for (ReferenceBinding b : bindings) {
                PsiClass cls = findClass(b);
                if (cls != null) {
                    classes.add(cls);
                }
            }
        }

        return classes.toArray(PsiClass.EMPTY_ARRAY);
    }

    @Nullable
    public PsiElement findElement(@Nullable ASTNode node) {
        if (node instanceof NameReference) {
            NameReference ref = (NameReference) node;
            if (node instanceof QualifiedNameReference) {
                QualifiedNameReference qualifiedNameReference = (QualifiedNameReference) node;
                if (qualifiedNameReference.otherBindings != null &&
                        qualifiedNameReference.otherBindings.length == 1) {
                    // for example, array.length
                    PsiElement element = findElement(qualifiedNameReference.otherBindings[0]);
                    if (element != null) {
                        return element;
                    }
                }
            }
            return findElement(ref.binding);
        } else if (node instanceof MessageSend) {
            return findMethod(((MessageSend) node).binding);
        } else if (node instanceof TypeReference) {
            return findElement(((TypeReference) node).resolvedType);
        } else if (node instanceof AllocationExpression) {
            return findMethod(((AllocationExpression) node).binding);
        } else if (node instanceof FieldReference) {
            return findElement(((FieldReference) node).binding);
        } else if (node instanceof ImportReference) {
            // There are no bindings on import statements in ECJ.
            // I need to look this up via the symbol table instead
            ImportReference ref = (ImportReference)node;
            if (ref.isStatic()) {
                // Drop last token and then find field afterwards
                // after looking up the class
                int classTokenCount = ref.tokens.length - 1;
                char[][] className = new char[classTokenCount][];
                System.arraycopy(ref.tokens, 0, className, 0, classTokenCount);
                PsiClass cls = findClass(className);
                if (cls != null) {
                    String name = new String(ref.tokens[classTokenCount]);
                    PsiField field = cls.findFieldByName(name, false);
                    if (field != null) {
                        return field;
                    }
                    // Could be a static method import too
                    PsiMethod[] methods = cls.findMethodsByName(name, false);
                    if (methods.length == 1) {
                        return methods[0];
                    }
                    // Try a little harder and search parents in case you've imported
                    // via a subclass
                    field = cls.findFieldByName(name, true);
                    if (field != null) {
                        return field;
                    }
                    methods = cls.findMethodsByName(name, true);
                    if (methods.length == 1) {
                        return methods[0];
                    }
                }
                return null;
            } else {
                return findClass(ref.tokens);
            }
        }
        return null;
    }

    @Nullable
    public PsiElement findElement(@Nullable Binding binding) {
        if (binding == null) {
            return null;
        }

        PsiElement element = mElementMap.get(binding);
        if (element != null) {
            return element;
        }
        if (binding instanceof ProblemReferenceBinding) {
            binding = ((ProblemReferenceBinding)binding).closestReferenceMatch();
            if (binding != null) {
                return findElement(binding);
            } else {
                return null;
            }
        }

        if (binding instanceof WildcardBinding) {
            return findElement(((WildcardBinding)binding).actualType());
        }

        if (binding instanceof ParameterizedTypeBinding) {
            TypeBinding typeBinding = ((ParameterizedTypeBinding) binding).original();
            element = mElementMap.get(typeBinding);
            //noinspection PointlessBooleanExpression
            if (!RESOLVE_TO_BINARY
                    && element == null
                    && typeBinding != null
                    && typeBinding.actualType() instanceof SourceTypeBinding) {
                return null;
            }
            return findElement(typeBinding);
        }

        if (binding instanceof ParameterizedMethodBinding) {
            ParameterizedMethodBinding methodBinding = (ParameterizedMethodBinding) binding;
            binding = methodBinding.original();
            element = mElementMap.get(binding);
            //noinspection PointlessBooleanExpression
            if (!RESOLVE_TO_BINARY
                    && element == null
                    && methodBinding.isConstructor()
                    && methodBinding.declaringClass != null
                    && methodBinding.declaringClass.actualType() instanceof SourceTypeBinding) {
                // It's a local binding but there's no actual constructor there
                // (it's the default constructor)
                return null;
            }
            return findElement(binding);
        }

        if (binding instanceof ParameterizedFieldBinding) {
            ParameterizedFieldBinding fieldBinding = (ParameterizedFieldBinding) binding;
            binding = fieldBinding.original();
            element = mElementMap.get(binding);
            //noinspection PointlessBooleanExpression
            if (!RESOLVE_TO_BINARY
                    && element == null
                    && fieldBinding.declaringClass != null
                    && fieldBinding.declaringClass.actualType() instanceof SourceTypeBinding) {
                // It's a local binding but there's no actual constructor there
                // (it's the default constructor)
                return null;
            }
            return findElement(binding);
        }

        // No binding in map yet: the PSI file may not have been parsed yet. Attempt
        // to initialize these:
        if (binding instanceof FieldBinding) {
            ReferenceBinding declaringClass = ((FieldBinding) binding).declaringClass;
            if (declaringClass instanceof SourceTypeBinding) {
                PsiJavaFile file = mEcjResult.findFileContaining(declaringClass);
                //noinspection VariableNotUsedInsideIf
                if (file != null) {
                    element = mElementMap.get(binding);
                    if (element != null) {
                        return element;
                    }
                }
            }
        } else if (binding instanceof MethodBinding) {
            ReferenceBinding declaringClass = ((MethodBinding) binding).declaringClass;
            if (declaringClass instanceof SourceTypeBinding) {
                PsiJavaFile file = mEcjResult.findFileContaining(declaringClass);
                //noinspection VariableNotUsedInsideIf
                if (file != null) {
                    element = mElementMap.get(binding);
                    if (element != null) {
                        return element;
                    }
                }
            }
        }

        // Binary references. Computed on the fly for now since they are lightweight.
        if (binding instanceof MethodBinding) {
            MethodBinding methodBinding = (MethodBinding) binding;

            // Default constructor? Allow binding to binary!
            if (methodBinding.isConstructor()
                    && (methodBinding.parameters == null || methodBinding.parameters.length == 0)) {
                return new EcjPsiBinaryMethod(this, methodBinding);
            }
            //noinspection PointlessBooleanExpression
            if (!RESOLVE_TO_BINARY
                    && methodBinding.declaringClass instanceof SourceTypeBinding) {
                return null;
            }
            return new EcjPsiBinaryMethod(this, methodBinding);
        } else if (binding instanceof ReferenceBinding) {
            ReferenceBinding referenceBinding = (ReferenceBinding) binding;
            if (referenceBinding.compoundName == null) {
                // For example, TypeVariableBindings
                return null;
            }
            return new EcjPsiBinaryClass(this, referenceBinding);
        } else if (binding instanceof FieldBinding) {
            FieldBinding fieldBinding = (FieldBinding) binding;
            //noinspection PointlessBooleanExpression
            if (!RESOLVE_TO_BINARY
                    && fieldBinding.declaringClass instanceof SourceTypeBinding) {
                return null;
            }
            return new EcjPsiBinaryField(this, fieldBinding);
        } else {
            // Search in AST, e.g. to resolve local variables etc
            if (binding instanceof LocalVariableBinding) {
                LocalVariableBinding lvb = (LocalVariableBinding) binding;
                Scope scope = lvb.declaringScope;
                while (scope != null) {
                    if (scope instanceof MethodScope) {
                        MethodScope methodScope = (MethodScope) scope;
                        MethodBinding methodBinding = methodScope.referenceMethodBinding();
                        if (methodBinding != null) {
                            PsiElement method = mElementMap.get(methodBinding);
                            if (method != null) {
                                PsiElement declaration = findElementWithBinding(method, lvb);
                                if (declaration != null) {
                                    return declaration;
                                }
                            }
                        }
                    }
                    scope = scope.parent;
                }
            }
        }

        return null;
    }

    private static PsiElement findElementWithBinding(PsiElement root, Binding variableBinding) {
        VariableDeclarationFinder finder = new VariableDeclarationFinder(variableBinding);
        root.accept(finder);
        return finder.getMatch();
    }

    public void registerElement(@Nullable Binding binding, @NonNull PsiElement element) {
        if (binding != null) {
            assert !mElementMap.containsKey(binding);
            mElementMap.put(binding, element);
        }
    }

    private static class VariableDeclarationFinder extends JavaRecursiveElementVisitor {
        private PsiElement mMatch;
        private final Binding mTargetBinding;

        public VariableDeclarationFinder(@NonNull Binding binding) {
            mTargetBinding = binding;
        }

        @Nullable
        public PsiElement getMatch() {
            return mMatch;
        }

        @Override
        public void visitParameter(PsiParameter parameter) {
            EcjPsiSourceElement element = (EcjPsiSourceElement) parameter;
            Object nativeNode = element.getNativeNode();
            if (nativeNode instanceof LocalDeclaration) {
                LocalDeclaration node = (LocalDeclaration) nativeNode;
                if (node.binding == mTargetBinding) {
                    mMatch = parameter;
                }

            }
            super.visitParameter(parameter);
        }

        @Override
        public void visitLocalVariable(PsiLocalVariable variable) {
            EcjPsiSourceElement element = (EcjPsiSourceElement) variable;
            Object nativeNode = element.getNativeNode();
            if (nativeNode instanceof LocalDeclaration) {
                LocalDeclaration node = (LocalDeclaration) nativeNode;
                if (node.binding == mTargetBinding) {
                    mMatch = variable;
                }
            }
            super.visitLocalVariable(variable);
        }
    }

    @Nullable
    static Object inlineConstants(Object value) {
        if (value instanceof Constant) {
            return getConstantValue((Constant) value);
        } else if (value instanceof Object[]) {
            Object[] array = (Object[]) value;
            if (array.length > 0) {
                List<Object> list = Lists.newArrayListWithExpectedSize(array.length);
                for (Object element : array) {
                    list.add(inlineConstants(element));
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
            } else {
                return value;
            }
        } else {
            return value;
        }
    }

    @Nullable
    static Object getConstantValue(@Nullable Constant value) {
        if (value == null || value == Constant.NotAConstant) {
            return null;
        } else if (value instanceof StringConstant) {
            return value.stringValue();
        } else if (value instanceof IntConstant) {
            return value.intValue();
        } else if (value instanceof BooleanConstant) {
            return value.booleanValue();
        } else if (value instanceof FloatConstant) {
            return value.floatValue();
        } else if (value instanceof LongConstant) {
            return value.longValue();
        } else if (value instanceof DoubleConstant) {
            return value.doubleValue();
        } else if (value instanceof ShortConstant) {
            return value.shortValue();
        } else if (value instanceof CharConstant) {
            return value.charValue();
        } else if (value instanceof ByteConstant) {
            return value.byteValue();
        }

        return null;
    }

    /** Computes the super method, if any, given a method binding */
    @SuppressWarnings("SameParameterValue")
    @Nullable
    static MethodBinding findSuperMethodBinding(@NonNull MethodBinding binding,
            boolean allowStatic, boolean allowPrivate) {
        if (binding.isConstructor()) {
            return null;
        }
        if (!allowPrivate && binding.isPrivate()) {
            return null;
        }
        if (!allowStatic && binding.isStatic()) {
            return null;
        }
        try {
            ReferenceBinding superclass = binding.declaringClass.superclass();
            while (superclass != null) {
                MethodBinding[] methods = superclass.getMethods(binding.selector,
                        binding.parameters.length);
                for (MethodBinding method : methods) {
                    if (method.isStatic() != binding.isStatic() ) {
                        continue;
                    }
                    if (method.areParameterErasuresEqual(binding)) {
                        if (method.isPrivate()) {
                            if (method.declaringClass.outermostEnclosingType()
                                    == binding.declaringClass.outermostEnclosingType()) {
                                return method;
                            } else {
                                return null;
                            }
                        } else {
                            return method;
                        }
                    }
                }

                // TODO: Check interfaces too!

                superclass = superclass.superclass();
            }
        } catch (Exception ignore) {
            // Work around ECJ bugs; see https://code.google.com/p/android/issues/detail?id=172268
        }

        return null;
    }

    @Nullable
    ExternalAnnotationRepository getAnnotationRepository() {
        if (mAnnotationRepository == null && mClient != null) {
            mAnnotationRepository = ExternalAnnotationRepository.get(mClient);
        }

        return mAnnotationRepository;
    }

    /**
     * It is valid (and in fact encouraged by IntelliJ's inspections) to specify the same
     * annotation on overriding methods and overriding parameters. However, we shouldn't
     * return all these "duplicates" when you ask for the annotation on a given element.
     * This method filters out duplicates.
     */
    @VisibleForTesting
    @NonNull
    static PsiAnnotation[] ensureUnique(@NonNull List<PsiAnnotation> list) {
        if (list.isEmpty()) {
            return PsiAnnotation.EMPTY_ARRAY;
        } else if (list.size() == 1) {
            return new PsiAnnotation[] { list.get(0) };
        }

        List<PsiAnnotation> result = Lists.newArrayListWithCapacity(list.size());

        // The natural way to deduplicate would be to create a Set of seen names, iterate
        // through the list and look to see if the current annotation's name is already in the
        // set (if so, remove this annotation from the list, else add it to the set of seen names)
        // but this involves creating the set and all the Map entry objects; that's not
        // necessary here since these lists are always very short 2-5 elements.
        // Instead we'll just do an O(n^2) iteration comparing each subsequent element with each
        // previous element and removing if matches, which is fine for these tiny lists.
        int n = list.size();
        for (int i = 0; i < n; i++) {
            PsiAnnotation current = list.get(i);
            String currentName = current.getQualifiedName();
            if (currentName != null) {
                boolean hasDuplicate = false;
                for (int j = n - 1; j > i; j--) {
                    PsiAnnotation later = list.get(j);
                    String laterName = later.getQualifiedName();
                    if (currentName.equals(laterName)) {
                        hasDuplicate = true;
                        break;
                    }
                }
                if (!hasDuplicate) {
                    result.add(current);
                }
            }
        }

        return result.toArray(PsiAnnotation.EMPTY_ARRAY);
    }
}
