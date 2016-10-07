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

package com.android.build.gradle.integration.common.fixture.app;

/**
 * A simple annotation processor library.
 *
 * This is a Java library with an annotation processor.  It provides the ProvideString annotation.
 * Annotation a class with ProvideString will generate a StringValue class, which contains a 'value'
 * field.
 */
public class AnnotationProcessorLib extends AbstractAndroidTestApp implements AndroidTestApp {
    private static final TestSourceFile annotation = new TestSourceFile(
            "src/main/java/com/example/annotation", "ProvideString.java",
            "package com.example.annotation;\n"
                    + "import java.lang.annotation.ElementType;\n"
                    + "import java.lang.annotation.Retention;\n"
                    + "import java.lang.annotation.RetentionPolicy;\n"
                    + "import java.lang.annotation.Target;\n"
                    + "\n"
                    + "@Target(ElementType.TYPE)\n"
                    + "@Retention(RetentionPolicy.CLASS)\n"
                    + "public @interface ProvideString {\n"
                    + "}\n");

    private static final TestSourceFile metatinf = new TestSourceFile(
            "src/main/resources/META-INF/services", "javax.annotation.processing.Processor",
            "com.example.annotation.Processor");

    private static final TestSourceFile processor = new TestSourceFile(
            "src/main/java/com/example/annotation", "Processor.java",
            "package com.example.annotation;\n"
                    + "import javax.annotation.processing.AbstractProcessor;\n"
                    + "import javax.annotation.processing.ProcessingEnvironment;\n"
                    + "import javax.annotation.processing.RoundEnvironment;\n"
                    + "import javax.annotation.processing.SupportedOptions;\n"
                    + "import javax.annotation.processing.SupportedAnnotationTypes;\n"
                    + "import javax.lang.model.SourceVersion;\n"
                    + "import javax.lang.model.element.Element;\n"
                    + "import javax.lang.model.element.ElementKind;\n"
                    + "import javax.lang.model.element.TypeElement;\n"
                    + "import javax.tools.JavaFileObject;\n"
                    + "import java.io.IOException;\n"
                    + "import java.io.Writer;\n"
                    + "import java.util.Set;\n"
                    + "\n"
                    + "@SupportedOptions({\"value\"})\n"
                    + "@SupportedAnnotationTypes({\"com.example.annotation.ProvideString\"})\n"
                    + "public class Processor extends AbstractProcessor {\n"
                    + "\n"
                    + "    private final static String DEFAULT_VALUE = \"Hello World!\";"
                    + "    @Override\n"
                    + "    public synchronized void init(ProcessingEnvironment processingEnvironment) {\n"
                    + "        super.init(processingEnvironment);\n"
                    + "    }\n"
                    + "\n"
                    + "    @Override\n"
                    + "    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {\n"
                    + "        String optionValue = processingEnv.getOptions().get(\"value\");\n"
                    + "        String value = optionValue != null ? optionValue : DEFAULT_VALUE;\n"
                    + "        for (Element annotatedElement : roundEnvironment.getElementsAnnotatedWith(ProvideString.class)) {\n"
                    + "            // Check if a class has been annotated with @ProvideString\n"
                    + "            if (annotatedElement.getKind() != ElementKind.CLASS) {\n"
                    + "                return true; // Exit processing\n"
                    + "            }\n"
                    + "            TypeElement typeElement = (TypeElement) annotatedElement;\n"
                    + "            String className = typeElement.getSimpleName() + \"StringValue\";\n"
                    + "\n"
                    + "            JavaFileObject jfo = null;\n"
                    + "            Writer writer = null;\n"
                    + "            try {\n"
                    + "                jfo = processingEnv.getFiler().createSourceFile(className);\n"
                    + "                writer = jfo.openWriter();\n"
                    + "                writer.write(\"package com.example.annotation;\\n\");\n"
                    + "                writer.write(\"public class \" + className + \" {\\n\");\n"
                    + "                writer.write(\"    public String value = \\\"\" + value + \"\\\";\\n\");\n"
                    + "                writer.write(\"    public String processor = \\\"\" + this.getClass().getSimpleName() + \"\\\";\\n\");\n"
                    + "                writer.write(\"}\\n\");\n"
                    + "            } catch (IOException e) {\n"
                    + "                throw new RuntimeException(e);\n"
                    + "            } finally {\n"
                    + "                if (writer != null) {\n"
                    + "                    try {\n"
                    + "                        writer.close();\n"
                    + "                    } catch (IOException e) {\n"
                    + "                        // ignore\n"
                    + "                    }\n"
                    + "                }\n"
                    + "            }\n"
                    + "        }\n"
                    + "        return true;\n"
                    + "    }\n"
                    + "\n"
                    + "    public SourceVersion getSupportedSourceVersion() {\n"
                    + "        return SourceVersion.latest();\n"
                    + "    }\n"
                    + "}\n");

    private static final TestSourceFile buildGradle = new TestSourceFile(
            "", "build.gradle",
            "apply plugin: \"java\"\n"
                    + "targetCompatibility = '1.7'\n"
                    + "sourceCompatibility = '1.7'\n");

    private  AnnotationProcessorLib(boolean isCompiler) {
        addFiles(annotation, buildGradle);
        if (isCompiler) {
            addFiles(processor, metatinf);
        }
    }

    public static AnnotationProcessorLib createCompiler() {
        return new AnnotationProcessorLib(true);
    }

    public static AnnotationProcessorLib createLibrary() {
        return new AnnotationProcessorLib(false);
    }

    @Override
    public boolean containsFullBuildScript() {
        return true;
    }
}
