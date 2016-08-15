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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import org.eclipse.jdt.internal.compiler.ASTVisitor;
import org.eclipse.jdt.internal.compiler.ast.Annotation;
import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration;
import org.eclipse.jdt.internal.compiler.ast.Javadoc;
import org.eclipse.jdt.internal.compiler.ast.TypeDeclaration;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;
import org.eclipse.jdt.internal.compiler.lookup.ClassScope;
import org.eclipse.jdt.internal.compiler.lookup.CompilationUnitScope;
import org.eclipse.jdt.internal.compiler.lookup.SourceTypeBinding;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Gathers information about typedefs (@IntDef and @StringDef */
public class TypedefCollector extends ASTVisitor {
    private Map<String,List<Annotation>> mMap = Maps.newHashMap();

    private final boolean mRequireHide;
    private final boolean mRequireSourceRetention;
    private CompilationUnitDeclaration mCurrentUnit;
    private List<String> mPrivateTypedefs = Lists.newArrayList();

    public TypedefCollector(
            @NonNull Collection<CompilationUnitDeclaration> units,
            boolean requireHide,
            boolean requireSourceRetention) {
        mRequireHide = requireHide;
        mRequireSourceRetention = requireSourceRetention;

        for (CompilationUnitDeclaration unit : units) {
            mCurrentUnit = unit;
            unit.traverse(this, unit.scope);
            mCurrentUnit = null;
        }
    }

    public List<String> getPrivateTypedefClasses() {
        return mPrivateTypedefs;
    }

    public Map<String,List<Annotation>> getTypedefs() {
        return mMap;
    }

    @Override
    public boolean visit(TypeDeclaration memberTypeDeclaration, ClassScope scope) {
        return recordTypedefs(memberTypeDeclaration);

    }

    @Override
    public boolean visit(TypeDeclaration typeDeclaration, CompilationUnitScope scope) {
        return recordTypedefs(typeDeclaration);
    }

    private boolean recordTypedefs(TypeDeclaration declaration) {
        SourceTypeBinding binding = declaration.binding;
        if (binding == null) {
            return false;
        }
        Annotation[] annotations = declaration.annotations;
        if (annotations != null) {
            if (declaration.binding.isAnnotationType()) {
                for (Annotation annotation : annotations) {
                    String typeName = Extractor.getFqn(annotation);
                    if (typeName == null) {
                        continue;
                    }

                    if (Extractor.isNestedAnnotation(typeName)) {
                        String fqn = new String(binding.readableName());

                        List<Annotation> list = mMap.get(fqn);
                        if (list == null) {
                            list = new ArrayList<>(2);
                            mMap.put(fqn, list);
                        }
                        list.add(annotation);

                        if (mRequireHide && !javadocContainsHide(declaration.javadoc)) {
                            Extractor.warning(getFileName()
                                    + ": The typedef annotation " + fqn
                                    + " should specify @hide in a doc comment");
                        }
                        if (mRequireSourceRetention
                                && !Extractor.hasSourceRetention(annotations)) {
                            Extractor.warning(getFileName()
                                    + ": The typedef annotation " + fqn
                                    + " should have @Retention(RetentionPolicy.SOURCE)");
                        }
                        if (declaration.binding != null && isHiddenTypeDef(declaration)) {
                            StringBuilder sb = new StringBuilder(100);
                            for (char c : declaration.binding.qualifiedPackageName()) {
                                if (c == '.') {
                                    sb.append('/');
                                } else {
                                    sb.append(c);
                                }
                            }
                            sb.append('/');
                            for (char c : declaration.binding.qualifiedSourceName()) {
                                if (c == '.') {
                                    sb.append('$');
                                } else {
                                    sb.append(c);
                                }
                            }
                            String cls = sb.toString();
                            if (!mPrivateTypedefs.contains(cls)) {
                                mPrivateTypedefs.add(cls);
                            }
                        }
                    }
                }
            }
        }
        return true;
    }

    /**
     * Returns true if this type declaration for a typedef is hidden (e.g. should not
     * be extracted into an external annotation database)
     *
     * @param declaration the type declaration
     * @return true if the type is hidden
     */
    @SuppressWarnings("RedundantIfStatement")
    public static boolean isHiddenTypeDef(@NonNull TypeDeclaration declaration) {
        if ((declaration.modifiers & ClassFileConstants.AccPublic) == 0) {
            return true;
        }

        if (Extractor.REMOVE_HIDDEN_TYPEDEFS && javadocContainsHide(declaration.javadoc)) {
            return true;
        }

        return false;
    }

    /**
     * Returns true if the given javadoc contains a {@code @hide} marker
     *
     * @param javadoc the javadoc
     * @return true if the javadoc contains a hide marker
     */
    private static boolean javadocContainsHide(@Nullable Javadoc javadoc) {
        if (javadoc != null) {
            StringBuffer stringBuffer = new StringBuffer(200);
            javadoc.print(0, stringBuffer);
            String documentation = stringBuffer.toString();
            if (documentation.contains("@hide")) {
                return true;
            }
        }

        return false;
    }

    private String getFileName() {
        return new String(mCurrentUnit.getFileName());
    }
}
