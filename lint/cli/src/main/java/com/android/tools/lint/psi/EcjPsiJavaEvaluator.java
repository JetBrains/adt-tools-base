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
import com.android.tools.lint.ExternalAnnotationRepository;
import com.android.tools.lint.client.api.JavaEvaluator;
import com.google.common.collect.Lists;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.util.PsiTreeUtil;

import org.eclipse.jdt.internal.compiler.ast.AbstractMethodDeclaration;
import org.eclipse.jdt.internal.compiler.ast.TypeDeclaration;
import org.eclipse.jdt.internal.compiler.lookup.AnnotationBinding;
import org.eclipse.jdt.internal.compiler.lookup.Binding;
import org.eclipse.jdt.internal.compiler.lookup.FieldBinding;
import org.eclipse.jdt.internal.compiler.lookup.LocalVariableBinding;
import org.eclipse.jdt.internal.compiler.lookup.MethodBinding;
import org.eclipse.jdt.internal.compiler.lookup.PackageBinding;
import org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding;
import org.eclipse.jdt.internal.compiler.problem.AbortCompilation;

import java.io.File;
import java.util.Collection;
import java.util.List;

public class EcjPsiJavaEvaluator extends JavaEvaluator {
    private final EcjPsiManager mManager;

    public EcjPsiJavaEvaluator(@NonNull EcjPsiManager manager) {
        mManager = manager;
    }

    @Override
    public boolean extendsClass(
            @Nullable PsiClass cls,
            @NonNull String className,
            boolean strict) {
        ReferenceBinding binding;
        if (cls instanceof EcjPsiClass) {
            TypeDeclaration declaration = (TypeDeclaration) ((EcjPsiClass) cls).mNativeNode;
            binding = declaration.binding;
        } else if (cls instanceof EcjPsiBinaryClass) {
            binding = ((EcjPsiBinaryClass)cls).getTypeBinding();
        } else {
            return false;
        }
        if (strict) {
            try {
                binding = binding.superclass();
            } catch (AbortCompilation ignore) {
                // Encountered symbol that couldn't be resolved (e.g. compiled class references
                // class not found on the classpath
                return false;
            }
        }

        for (; binding != null; binding = binding.superclass()) {
            if (equalsCompound(className, binding.compoundName)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean implementsInterface(
            @NonNull PsiClass cls,
            @NonNull String interfaceName,
            boolean strict) {
        ReferenceBinding binding;
        if (cls instanceof EcjPsiClass) {
            TypeDeclaration declaration = (TypeDeclaration) ((EcjPsiClass) cls).mNativeNode;
            binding = declaration.binding;
        } else if (cls instanceof EcjPsiBinaryClass) {
            binding = ((EcjPsiBinaryClass)cls).getTypeBinding();
        } else {
            return false;
        }
        if (strict) {
            try {
                binding = binding.superclass();
            } catch (AbortCompilation ignore) {
                // Encountered symbol that couldn't be resolved (e.g. compiled class references
                // class not found on the classpath
                return false;
            }
        }
        return isInheritor(binding, interfaceName);
    }

    @Override
    public boolean inheritsFrom(
            @NonNull PsiClass cls,
            @NonNull String className,
            boolean strict) {
        return /*extendsClass(cls, className, strict) || */implementsInterface(cls, className, strict);
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
        while (cls != null) {
            ReferenceBinding[] interfaces = cls.superInterfaces();
            for (ReferenceBinding binding : interfaces) {
                if (isInheritor(binding, name)) {
                    return true;
                }
            }

            if (equalsCompound(name, cls.compoundName)) {
                return true;
            }

            try {
                cls = cls.superclass();
            } catch (AbortCompilation ignore) {
                // Encountered symbol that couldn't be resolved (e.g. compiled class references
                // class not found on the classpath
                break;
            }
        }

        return false;
    }

    @NonNull
    @Override
    public String getInternalName(@NonNull PsiClass psiClass) {
        ReferenceBinding binding = null;
        if (psiClass instanceof EcjPsiClass) {
            //noinspection ConstantConditions
            binding = ((TypeDeclaration) ((EcjPsiClass) psiClass).getNativeNode()).binding;
        } else if (psiClass instanceof EcjPsiBinaryClass) {
            Binding binaryBinding = ((EcjPsiBinaryClass) psiClass).getBinding();
            if (binaryBinding instanceof ReferenceBinding) {
                binding = (ReferenceBinding) binaryBinding;
            }
        }
        if (binding == null) {
            return super.getInternalName(psiClass);
        }

        return EcjPsiManager.getInternalName(binding.compoundName);
    }

    @NonNull
    @Override
    public String getInternalName(@NonNull PsiClassType psiClassType) {
        if (psiClassType instanceof EcjPsiClassType) {
            EcjPsiManager.getTypeName(((EcjPsiClassType)psiClassType).getBinding());
        }
        return super.getInternalName(psiClassType);
    }

    @Override
    @Nullable
    public PsiClass findClass(@NonNull String fullyQualifiedName) {
        return mManager.findClass(fullyQualifiedName);
    }

    @Nullable
    @Override
    public PsiClassType getClassType(@Nullable PsiClass psiClass) {
        if (psiClass != null) {
            return mManager.getClassType(psiClass);
        }
        return null;
    }

    @NonNull
    @Override
    public PsiAnnotation[] getAllAnnotations(@NonNull PsiModifierListOwner owner,
            boolean inHierarchy) {
        if (!inHierarchy) {
            return getDirectAnnotations(owner);
        }

        PsiModifierList modifierList = owner.getModifierList();
        if (modifierList == null) {
            return getDirectAnnotations(owner);
        }

        if (owner instanceof PsiMethod) {
            MethodBinding method;
            if (owner instanceof EcjPsiMethod) {
                EcjPsiMethod psiMethod = (EcjPsiMethod) owner;
                AbstractMethodDeclaration declaration = (AbstractMethodDeclaration) psiMethod.getNativeNode();
                assert declaration != null;
                method = declaration.binding;
            } else if (owner instanceof EcjPsiBinaryMethod) {
                method = ((EcjPsiBinaryMethod) owner).getBinding();
            } else {
                assert false : owner.getClass();
                return PsiAnnotation.EMPTY_ARRAY;
            }

            List<PsiAnnotation> all = Lists.newArrayListWithExpectedSize(2);
            ExternalAnnotationRepository manager = mManager.getAnnotationRepository();

            while (method != null) {
                if (method.declaringClass == null) {
                    // for example, for unresolved problem bindings
                    break;
                }
                AnnotationBinding[] annotations = method.getAnnotations();
                int count = annotations.length;
                if (count > 0) {
                    all = Lists.newArrayListWithExpectedSize(count);
                    for (AnnotationBinding annotation : annotations) {
                        if (annotation != null) {
                            all.add(new EcjPsiBinaryAnnotation(mManager, modifierList, annotation));
                        }
                    }
                }

                // Look for external annotations
                if (manager != null) {
                    Collection<PsiAnnotation> external = manager.getAnnotations(method);
                    if (external != null) {
                        all.addAll(external);
                    }
                }

                method = EcjPsiManager.findSuperMethodBinding(method, false, false);
            }

            return EcjPsiManager.ensureUnique(all);
        } else if (owner instanceof PsiClass) {
            ReferenceBinding cls;
            if (owner instanceof EcjPsiClass) {
                EcjPsiClass psiClass = (EcjPsiClass) owner;
                TypeDeclaration declaration = (TypeDeclaration) psiClass.getNativeNode();
                assert declaration != null;
                cls = declaration.binding;
            } else if (owner instanceof EcjPsiBinaryClass) {
                cls = ((EcjPsiBinaryClass) owner).getTypeBinding();
            } else {
                assert false : owner.getClass();
                return PsiAnnotation.EMPTY_ARRAY;
            }

            List<PsiAnnotation> all = Lists.newArrayListWithExpectedSize(2);
            ExternalAnnotationRepository manager = mManager.getAnnotationRepository();

            while (cls != null) {
                AnnotationBinding[] annotations = cls.getAnnotations();
                int count = annotations.length;
                if (count > 0) {
                    all = Lists.newArrayListWithExpectedSize(count);
                    for (AnnotationBinding annotation : annotations) {
                        if (annotation != null) {
                            all.add(new EcjPsiBinaryAnnotation(mManager, modifierList, annotation));
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

                try {
                    cls = cls.superclass();
                } catch (AbortCompilation ignore) {
                    // Encountered symbol that couldn't be resolved (e.g. compiled class references
                    // class not found on the classpath
                    break;
                }
            }

            return EcjPsiManager.ensureUnique(all);
        } else if (owner instanceof PsiParameter) {
            MethodBinding method;
            int index;

            if (owner instanceof EcjPsiBinaryParameter) {
                EcjPsiBinaryParameter parameter = (EcjPsiBinaryParameter) owner;
                method = parameter.getOwnerMethod().getBinding();
                index = parameter.getIndex();
            } else if (owner instanceof EcjPsiParameter) {
                EcjPsiParameter parameter = (EcjPsiParameter) owner;
                if (parameter.getParent() instanceof PsiParameterList) {
                    EcjPsiMethod psiMethod = (EcjPsiMethod)PsiTreeUtil.getParentOfType(
                            parameter.getParent(), PsiMethod.class, true);
                    if (psiMethod == null) {
                        return getDirectAnnotations(owner);
                    }
                    index = ((PsiParameterList)parameter.getParent()).getParameterIndex(parameter);
                    AbstractMethodDeclaration declaration = (AbstractMethodDeclaration) psiMethod.getNativeNode();
                    assert declaration != null;
                    method = declaration.binding;
                } else {
                    // For each block, catch block
                    return getDirectAnnotations(owner);
                }
            } else {
                // Unexpected method type
                assert false : owner.getClass();
                return getDirectAnnotations(owner);
            }

            List<PsiAnnotation> all = Lists.newArrayListWithExpectedSize(2);
            ExternalAnnotationRepository manager = mManager.getAnnotationRepository();

            while (method != null) {
                if (method.declaringClass == null) {
                    // for example, for unresolved problem bindings
                    break;
                }
                AnnotationBinding[][] parameterAnnotations = method.getParameterAnnotations();
                if (parameterAnnotations != null && index < parameterAnnotations.length) {
                    AnnotationBinding[] annotations = parameterAnnotations[index];
                    int count = annotations.length;
                    if (count > 0) {
                        all = Lists.newArrayListWithExpectedSize(count);
                        for (AnnotationBinding annotation : annotations) {
                            if (annotation != null) {
                                all.add(new EcjPsiBinaryAnnotation(mManager, modifierList,
                                        annotation));
                            }
                        }
                    }
                }

                // Look for external annotations
                if (manager != null) {
                    Collection<PsiAnnotation> external = manager.getParameterAnnotations(method,
                            index);
                    if (external != null) {
                        all.addAll(external);
                    }
                }

                method = EcjPsiManager.findSuperMethodBinding(method, false, false);
            }

            return EcjPsiManager.ensureUnique(all);
        } else {
            // PsiField, PsiLocalVariable etc: no inheritance
            return getDirectAnnotations(owner);
        }
    }

    @NonNull
    private PsiAnnotation[] getDirectAnnotations(@NonNull PsiModifierListOwner owner) {
        // Look up external annotations to merge in
        ExternalAnnotationRepository repository = mManager.getAnnotationRepository();
        Collection<PsiAnnotation> annotations = null;
        if (repository != null) {
            if (owner instanceof EcjPsiMethod) {
                MethodBinding binding = ((EcjPsiMethod) owner).getBinding();
                if (binding != null) {
                    annotations = repository.getAnnotations(binding);
                }
            } else if (owner instanceof EcjPsiBinaryMethod) {
                MethodBinding binding = ((EcjPsiBinaryMethod) owner).getBinding();
                annotations = repository.getAnnotations(binding);
            } else if (owner instanceof EcjPsiClass) {
                ReferenceBinding binding = ((EcjPsiClass) owner).getBinding();
                if (binding != null) {
                    annotations = repository.getAnnotations(binding);
                }
            } else if (owner instanceof EcjPsiBinaryClass) {
                ReferenceBinding binding = ((EcjPsiBinaryClass) owner).getTypeBinding();
                annotations = repository.getAnnotations(binding);
            } else if (owner instanceof EcjPsiField) {
                FieldBinding binding = ((EcjPsiField) owner).getFieldBinding();
                if (binding != null) {
                    annotations = repository.getAnnotations(binding);
                }
            } else if (owner instanceof EcjPsiParameter) {
                EcjPsiParameter parameter = (EcjPsiParameter) owner;
                EcjPsiSourceElement parent = parameter.getParent();
                LocalVariableBinding binding = parameter.getVariableBinding();
                if (parent instanceof PsiParameterList && binding != null) {
                    int index = ((PsiParameterList) parent).getParameterIndex(parameter);
                    MethodBinding enclosingMethod = binding.getEnclosingMethod();
                    if (enclosingMethod != null) {
                        if (index != -1) {
                            annotations = repository.getParameterAnnotations(enclosingMethod,
                                    index);
                        }
                    }
                }
            } else if (owner instanceof EcjPsiBinaryParameter) {
                EcjPsiBinaryParameter parameter = (EcjPsiBinaryParameter) owner;
                PsiElement parent = parameter.getParent();
                EcjPsiBinaryMethod method = parameter.getOwnerMethod();
                MethodBinding enclosingMethod = method.getBinding();
                if (parent instanceof PsiParameterList) {
                    int index = ((PsiParameterList) parent).getParameterIndex(parameter);
                    if (index != -1) {
                        annotations = repository.getParameterAnnotations(enclosingMethod, index);
                    }
                }
            } else if (owner instanceof EcjPsiBinaryField) {
                FieldBinding binding = ((EcjPsiBinaryField) owner).getBinding();
                if (binding != null) {
                    annotations = repository.getAnnotations(binding);
                }
            } else if (owner instanceof EcjPsiPackage) {
                PackageBinding binding = ((EcjPsiPackage) owner).getPackageBinding();
                if (binding != null) {
                    annotations = repository.getAnnotations(binding);
                }
            }
        }

        PsiModifierList modifierList = owner.getModifierList();
        if (modifierList != null) {
            PsiAnnotation[] modifierAnnotations = modifierList.getAnnotations();
            if (modifierAnnotations.length > 0) {
                if (annotations != null && !annotations.isEmpty()) {
                    List<PsiAnnotation> combined = Lists.newArrayList(modifierAnnotations);
                    combined.addAll(annotations);
                    return combined.toArray(PsiAnnotation.EMPTY_ARRAY);
                } else {
                    return modifierAnnotations;
                }
            } else if (annotations != null && !annotations.isEmpty()) {
                return annotations.toArray(PsiAnnotation.EMPTY_ARRAY);
            }
            return modifierAnnotations;
        } else if (annotations != null && !annotations.isEmpty()) {
            return annotations.toArray(PsiAnnotation.EMPTY_ARRAY);
        } else {
            return PsiAnnotation.EMPTY_ARRAY;
        }
    }

    @Nullable
    @Override
    public PsiAnnotation findAnnotationInHierarchy(@NonNull PsiModifierListOwner listOwner,
            @NonNull String... annotationNames) {
        throw new UnimplementedLintPsiApiException();
    }

    @Nullable
    @Override
    public PsiAnnotation findAnnotation(@Nullable PsiModifierListOwner listOwner,
            @NonNull String... annotationNames) {
        throw new UnimplementedLintPsiApiException();
    }

    @Nullable
    @Override
    public File getFile(@NonNull PsiFile file) {
        if (file instanceof EcjPsiJavaFile) {
            EcjPsiJavaFile javaFile = (EcjPsiJavaFile) file;
            return javaFile.getIoFile();
        }

        return null;
    }
}
