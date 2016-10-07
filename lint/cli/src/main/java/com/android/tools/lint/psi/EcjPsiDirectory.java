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
import com.intellij.psi.PsiDirectory;

public abstract class EcjPsiDirectory extends EcjPsiSourceElement implements PsiDirectory {

    // Not intended to be called, but we need the class to exist (used in PsiJavaFile
    // overriding method) and since the parent doesn't have a null constructor we have to
    // define one here
    @SuppressWarnings("unused")
    private EcjPsiDirectory(@NonNull EcjPsiManager manager) {
        super(manager, null);
    }

    @Override
    public EcjPsiDirectory getParent() {
        return (EcjPsiDirectory) super.getParent();
    }
}
