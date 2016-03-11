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

import static com.android.tools.lint.psi.EcjPsiManager.getTypeName;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.EcjParser;
import com.android.tools.lint.ExternalAnnotationRepository;
import com.google.common.collect.Lists;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.HierarchicalMethodSignature;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassInitializer;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiTypeParameterList;
import com.intellij.psi.javadoc.PsiDocComment;

import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;
import org.eclipse.jdt.internal.compiler.lookup.AnnotationBinding;
import org.eclipse.jdt.internal.compiler.lookup.FieldBinding;
import org.eclipse.jdt.internal.compiler.lookup.MethodBinding;
import org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding;
import org.eclipse.jdt.internal.compiler.lookup.TypeConstants;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/** PSI class wrapping a binary (non source) dependency, e.g. .class file, possibly in .jar */
class EcjPsiBinaryClass extends EcjPsiBinaryElement implements PsiClass, PsiModifierList {
    private final String mQualifiedName;
    private final String mName;
    // Typically TypeBinding or ParameterizedTypeBinding
    private final ReferenceBinding mTypeBinding;

    EcjPsiBinaryClass(
            @NonNull EcjPsiManager manager,
            @NonNull ReferenceBinding typeBinding) {
        super(manager, typeBinding);
        mTypeBinding = typeBinding;
        char[][] symbols = mTypeBinding.compoundName;
        mQualifiedName = getTypeName(symbols);
        // Instead of new String(symbols[symbols.length - 1]) - use substring (shares same array)
        mName = mQualifiedName.substring(mQualifiedName.length() -
                symbols[symbols.length-1].length);
    }

    @Override
    public void accept(@NonNull PsiElementVisitor visitor) {
        if (visitor instanceof JavaElementVisitor) {
            ((JavaElementVisitor) visitor).visitClass(this);
        } else {
            visitor.visitElement(this);
        }
    }

    @Override
    public void acceptChildren(@NonNull PsiElementVisitor visitor) {
        // Only exposing methods, fields and inner classes for binary classes
        for (PsiMethod method : getMethods()) {
            method.accept(visitor);
        }
        for (PsiField field : getFields()) {
            field.accept(visitor);
        }
        for (PsiClass innerClass : getInnerClasses()) {
            innerClass.accept(visitor);
        }
    }

    @NonNull
    ReferenceBinding getTypeBinding() {
        return mTypeBinding;
    }

    @Nullable
    @Override
    public String getName() {
        return mName;
    }

    @Nullable
    @Override
    public String getQualifiedName() {
        return mQualifiedName;
    }

    @Override
    public boolean isInterface() {
        return mTypeBinding.isInterface();
    }

    @Override
    public boolean isAnnotationType() {
        return mTypeBinding.isAnnotationType();
    }

    @Override
    public boolean isEnum() {
        return mTypeBinding.isEnum();
    }

    @Nullable
    @Override
    public PsiReferenceList getExtendsList() {
        throw new UnimplementedLintPsiApiException();
    }

    @Nullable
    @Override
    public PsiReferenceList getImplementsList() {
        throw new UnimplementedLintPsiApiException();
    }

    @NonNull
    @Override
    public PsiClassType[] getExtendsListTypes() {
        PsiClassType classType = mManager.findClassType(mTypeBinding.superclass());
        assert classType != null;
        return new PsiClassType[] { classType };
    }

    @NonNull
    @Override
    public PsiClassType[] getImplementsListTypes() {
        return mManager.findClassTypes(mTypeBinding.superInterfaces());
    }

    @Nullable
    @Override
    public PsiClass getSuperClass() {
        return mManager.findClass(mTypeBinding.superclass());
    }

    @Override
    public PsiClass[] getInterfaces() {
        return mManager.findClasses(null, mTypeBinding.superInterfaces());
    }

    @NonNull
    @Override
    public PsiClass[] getSupers() {
        return mManager.findClasses(mTypeBinding.superclass(), mTypeBinding.superInterfaces());
    }

    @NonNull
    @Override
    public PsiClassType[] getSuperTypes() {
        PsiClass[] supers = getSupers();
        List<PsiClassType> types = Lists.newArrayListWithCapacity(supers.length);
        for (PsiClass cls : supers) {
            PsiClassType type = mManager.getClassType(cls);
            if (type != null) {
                types.add(type);
            }
        }
        return types.toArray(PsiClassType.EMPTY_ARRAY);
    }

    @NonNull
    @Override
    public PsiField[] getAllFields() {
        return getFields(true);
    }

    @NonNull
    @Override
    public PsiField[] getFields() {
        return getFields(false);
    }

    private PsiField[] getFields(boolean includeInherited) {
        if (mBinding instanceof ReferenceBinding) {
            ReferenceBinding cls = (ReferenceBinding) mBinding;
            if (includeInherited) {
                List<EcjPsiBinaryField> result = null;
                while (cls != null) {
                    FieldBinding[] fields = cls.fields();
                    if (fields != null) {
                        int count = fields.length;
                        if (count > 0) {
                            if (result == null) {
                                result = Lists.newArrayListWithExpectedSize(count);
                            }
                            for (FieldBinding field : fields) {
                                if ((field.modifiers & Modifier.PRIVATE) != 0 &&
                                        cls != mBinding) {
                                    // Ignore parent fields that are private
                                    continue;
                                }

                                // See if this field looks like it's masked
                                boolean masked = false;
                                for (EcjPsiBinaryField f : result) {
                                    FieldBinding mb = f.getBinding();
                                    if (Arrays.equals(mb.readableName(),
                                            field.readableName())) {
                                        masked = true;
                                        break;
                                    }
                                }
                                if (masked) {
                                    continue;
                                }

                                result.add(new EcjPsiBinaryField(mManager, field));
                            }
                        }
                    }
                    cls = cls.superclass();
                }

                return result != null ? result.toArray(PsiField.EMPTY_ARRAY) : PsiField.EMPTY_ARRAY;
            } else {
                FieldBinding[] fields = cls.fields();
                if (fields != null) {
                    int count = fields.length;
                    List<EcjPsiBinaryField> result = Lists.newArrayListWithExpectedSize(count);
                    for (FieldBinding field : fields) {
                        result.add(new EcjPsiBinaryField(mManager, field));
                    }
                    return result.toArray(PsiField.EMPTY_ARRAY);
                }
            }
        }

        return PsiField.EMPTY_ARRAY;
    }

    @NonNull
    @Override
    public PsiMethod[] getMethods() {
        return findMethods(null, false, true);
    }

    @NonNull
    @Override
    public PsiMethod[] getConstructors() {
        if (mBinding instanceof ReferenceBinding) {
            ReferenceBinding cls = (ReferenceBinding) mBinding;
            MethodBinding[] methods = cls.getMethods(TypeConstants.INIT);
            if (methods != null) {
                int count = methods.length;
                List<EcjPsiBinaryMethod> result = Lists.newArrayListWithExpectedSize(count);
                for (MethodBinding method : methods) {
                    if (method.isConstructor()) {
                        result.add(new EcjPsiBinaryMethod(mManager, method));
                    }
                }
                return result.toArray(PsiMethod.EMPTY_ARRAY);
            }
        }

        return PsiMethod.EMPTY_ARRAY;
    }

    @NonNull
    @Override
    public PsiClass[] getInnerClasses() {
        ReferenceBinding[] referenceBindings = mTypeBinding.memberTypes();
        if (referenceBindings.length == 0) {
            return PsiClass.EMPTY_ARRAY;
        }
        PsiClass[] result = new PsiClass[referenceBindings.length];
        for (int i = 0; i < referenceBindings.length; i++) {
            result[i] = new EcjPsiBinaryClass(mManager, referenceBindings[i]);
        }
        return result;
    }

    @NonNull
    @Override
    public PsiClassInitializer[] getInitializers() {
        return PsiClassInitializer.EMPTY_ARRAY;
    }

    @NonNull
    @Override
    public PsiMethod[] getAllMethods() {
        return findMethods(null, true, true);
    }

    @NonNull
    @Override
    public PsiClass[] getAllInnerClasses() {
        throw new UnimplementedLintPsiApiException();
    }

    @Nullable
    @Override
    public PsiField findFieldByName(String name, boolean checkBases) {
        if (mBinding instanceof ReferenceBinding) {
            ReferenceBinding cls = (ReferenceBinding) mBinding;
            while (cls != null) {
                FieldBinding[] fields = cls.fields();
                if (fields != null) {
                    for (FieldBinding field : fields) {
                        if ((field.modifiers & Modifier.PRIVATE) != 0 &&
                                cls != mBinding) {
                            // Ignore parent methods that are private
                            continue;
                        }

                        if (EcjParser.sameChars(name, field.name)) {
                            return new EcjPsiBinaryField(mManager, field);
                        }
                    }
                }
                if (checkBases) {
                    cls = cls.superclass();
                } else {
                    break;
                }
            }
        }

        return null;
    }

    @NonNull
    private PsiMethod[] findMethods(@Nullable String name, boolean includeInherited,
            boolean includeConstructors) {
        if (mBinding instanceof ReferenceBinding) {
            ReferenceBinding cls = (ReferenceBinding) mBinding;
            if (includeInherited) {
                List<EcjPsiBinaryMethod> result = null;
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
                                if ((method.modifiers & Modifier.PRIVATE) != 0 &&
                                        cls != mBinding) {
                                    // Ignore parent methods that are private
                                    continue;
                                }

                                if (includeConstructors || !method.isConstructor()) {
                                    // See if this method looks like it's masked
                                    boolean masked = false;
                                    for (PsiMethod m : result) {
                                        MethodBinding mb =
                                                ((EcjPsiBinaryMethod) m).getBinding();
                                        if (mb.areParameterErasuresEqual(method)) {
                                            masked = true;
                                            break;
                                        }
                                    }
                                    if (masked) {
                                        continue;
                                    }
                                    result.add(new EcjPsiBinaryMethod(mManager, method));
                                }
                            }
                        }
                    }
                    cls = cls.superclass();
                }

                return result != null ? result.toArray(PsiMethod.EMPTY_ARRAY) : PsiMethod.EMPTY_ARRAY;
            } else {
                MethodBinding[] methods =
                        name != null ? cls.getMethods(name.toCharArray()) : cls.methods();
                if (methods != null) {
                    int count = methods.length;
                    List<EcjPsiBinaryMethod> result = Lists.newArrayListWithExpectedSize(count);
                    for (MethodBinding method : methods) {
                        if (includeConstructors || !method.isConstructor()) {
                            result.add(new EcjPsiBinaryMethod(mManager, method));
                        }
                    }
                    return result.toArray(PsiMethod.EMPTY_ARRAY);
                }
            }
        }

        return PsiMethod.EMPTY_ARRAY;
    }


    @Nullable
    @Override
    public PsiMethod findMethodBySignature(PsiMethod psiMethod, boolean b) {
        throw new UnimplementedLintPsiApiException();
    }

    @NonNull
    @Override
    public PsiMethod[] findMethodsBySignature(PsiMethod psiMethod, boolean b) {
        throw new UnimplementedLintPsiApiException();
    }

    @NonNull
    @Override
    public PsiMethod[] findMethodsByName(String name, boolean checkBases) {
        return findMethods(name, checkBases, false);
    }

    @NonNull
    @Override
    public List<Pair<PsiMethod, PsiSubstitutor>> findMethodsAndTheirSubstitutorsByName(String s,
            boolean b) {
        throw new UnimplementedLintPsiApiException();
    }

    @NonNull
    @Override
    public List<Pair<PsiMethod, PsiSubstitutor>> getAllMethodsAndTheirSubstitutors() {
        throw new UnimplementedLintPsiApiException();
    }

    @Nullable
    @Override
    public PsiClass findInnerClassByName(String name, boolean checkBases) {
        throw new UnimplementedLintPsiApiException();
    }

    @Nullable
    @Override
    public PsiElement getLBrace() {
        throw new UnimplementedLintPsiApiException();
    }

    @Nullable
    @Override
    public PsiElement getRBrace() {
        throw new UnimplementedLintPsiApiException();
    }

    @Nullable
    @Override
    public PsiIdentifier getNameIdentifier() {
        return null;
    }

    @Override
    public PsiElement getScope() {
        return getContainingFile();
    }

    @Override
    public boolean isInheritor(@NonNull PsiClass psiClass, boolean b) {
        throw new UnimplementedLintPsiApiException();
    }

    @Override
    public boolean isInheritorDeep(PsiClass psiClass, PsiClass psiClass1) {
        throw new UnimplementedLintPsiApiException();
    }

    @Nullable
    @Override
    public PsiClass getContainingClass() {
        throw new UnimplementedLintPsiApiException();
    }

    @NonNull
    @Override
    public Collection<HierarchicalMethodSignature> getVisibleSignatures() {
        throw new UnimplementedLintPsiApiException();
    }

    @Nullable
    @Override
    public PsiDocComment getDocComment() {
        return null;
    }

    @Override
    public boolean isDeprecated() {
        return (mTypeBinding.modifiers & ClassFileConstants.AccDeprecated) != 0;
    }

    @Override
    public boolean hasTypeParameters() {
        return mTypeBinding.isGenericType();
    }

    @Nullable
    @Override
    public PsiTypeParameterList getTypeParameterList() {
        throw new UnimplementedLintPsiApiException();
    }

    @NonNull
    @Override
    public PsiTypeParameter[] getTypeParameters() {
        // ((BinaryTypeBinding)mTypeBinding).typeVariables()
        throw new UnimplementedLintPsiApiException();
    }

    // Modifier list inlined here

    @NonNull
    @Override
    public PsiModifierList getModifierList() {
        return this;
    }

    @Override
    public boolean hasModifierProperty(@NonNull @PsiModifier.ModifierConstant String s) {
        return hasExplicitModifier(s);
    }

    @Override
    public boolean hasExplicitModifier(@NonNull @PsiModifier.ModifierConstant String s) {
        return EcjPsiModifierList.hasModifier(mTypeBinding.modifiers, s);
    }

    @NonNull
    @Override
    public PsiAnnotation[] getAnnotations() {
        return getApplicableAnnotations();
    }

    @NonNull
    @Override
    public PsiAnnotation[] getApplicableAnnotations() {
        return findAnnotations(false);
    }

    @SuppressWarnings("SameParameterValue")
    private PsiAnnotation[] findAnnotations(boolean includeSuper) {
        List<PsiAnnotation> all = Lists.newArrayListWithExpectedSize(4);
        ExternalAnnotationRepository manager = mManager.getAnnotationRepository();

        if (mBinding instanceof ReferenceBinding) {
            ReferenceBinding cls = (ReferenceBinding) mBinding;
            while (cls != null) {
                AnnotationBinding[] annotations = cls.getAnnotations();
                int count = annotations.length;
                if (count > 0) {
                    all = Lists.newArrayListWithExpectedSize(count);
                    for (AnnotationBinding annotation : annotations) {
                        if (annotation != null) {
                            all.add(new EcjPsiBinaryAnnotation(mManager, this, annotation));
                        }
                    }
                }

                // Look for external annotations
                if (manager != null) {
                    Collection<PsiAnnotation> external = manager.getAnnotations(cls);
                    if (external != null) {
                        all.addAll(external);
                    }
                }

                if (!includeSuper) {
                    break;
                }

                cls = cls.superclass();
            }
        }

        return EcjPsiManager.ensureUnique(all);
    }

    @Nullable
    @Override
    public PsiAnnotation findAnnotation(@NonNull String s) {
        for (PsiAnnotation annotation : getAnnotations()) {
            String qualifiedName = annotation.getQualifiedName();
            if (s.equals(qualifiedName)) {
                return annotation;
            }
        }
        return null;
    }
}
