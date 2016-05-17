/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.tools.chunkio.processor;

import com.android.tools.chunkio.reader.ChunkReader;

import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.type.TypeVisitor;
import javax.lang.model.util.Elements;

public final class Environment {
    final Filer filer;
    final Elements elementUtils;
    final ErrorHandler errorHandler;
    final TypeVisitor<ChunkReader, Void> typeElementVisitor;

    private Environment(ProcessingEnvironment processingEnvironment) {
        filer = processingEnvironment.getFiler();
        elementUtils = processingEnvironment.getElementUtils();
        errorHandler = new ErrorHandler(processingEnvironment.getMessager());
        typeElementVisitor = new TypeElementVisitor(elementUtils, errorHandler);
    }

    public static Environment from(ProcessingEnvironment processingEnvironment) {
        return new Environment(processingEnvironment);
    }
}
