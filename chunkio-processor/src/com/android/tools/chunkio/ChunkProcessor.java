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

package com.android.tools.chunkio;

import com.android.tools.chunkio.processor.ClassEmitter;
import com.android.tools.chunkio.processor.Environment;
import com.android.tools.chunkio.processor.Validator;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.io.DataInputStream;
import java.util.Set;

/**
 * An annotation processor that finds fields annotated with {@link Chunk}
 * in classes annotated with {@link Chunked}. This processor generates
 * a new Java file for each annotated class it finds. The generated file
 * can be used to read an instance of that class from a {@link DataInputStream}.
 */
@SupportedAnnotationTypes({ "Chunked" })
public class ChunkProcessor extends AbstractProcessor {
    private Validator mValidator;
    private Environment mEnvironment;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnvironment) {
        super.init(processingEnvironment);
        mEnvironment = Environment.from(processingEnvironment);
        mValidator = new Validator(processingEnvironment.getTypeUtils(), mEnvironment);
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {
        Set<? extends Element> chunked = roundEnvironment.getElementsAnnotatedWith(Chunked.class);
        for (Element element : chunked) {
            if (!mValidator.validate(element)) return true;

            TypeElement typeElement = (TypeElement) element;

            ClassEmitter generator = new ClassEmitter(typeElement, mEnvironment);
            generator.emit();
        }

        return true;
    }
}
