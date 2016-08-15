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

package com.android.build.gradle.tasks.annotations;

import static com.google.common.base.Charsets.UTF_8;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.Collections;

public class TypedefRemoverTest {
    @Rule
    public TemporaryFolder testFolder = new TemporaryFolder();

    /* Compiled version of pp/build/intermediates/classes/debug/test/pkg/OuterClass.class from
        package test.pkg;

        import android.support.annotation.IntDef;

        public class OuterClass {
            public static final int CONSTANT = 1;

            @IntDef(CONSTANT)
            public @interface InnerClass {
            }
        }
     */
    public static final byte[] OUTER_CLASS = Base64.getDecoder().decode(""
            + "yv66vgAAADMAGAoAAwAUBwAVBwAWBwAXAQAKSW5uZXJDbGFzcwEADElubmVy"
            + "Q2xhc3NlcwEACENPTlNUQU5UAQABSQEADUNvbnN0YW50VmFsdWUDAAAAAQEA"
            + "Bjxpbml0PgEAAygpVgEABENvZGUBAA9MaW5lTnVtYmVyVGFibGUBABJMb2Nh"
            + "bFZhcmlhYmxlVGFibGUBAAR0aGlzAQAVTHRlc3QvcGtnL091dGVyQ2xhc3M7"
            + "AQAKU291cmNlRmlsZQEAD091dGVyQ2xhc3MuamF2YQwACwAMAQATdGVzdC9w"
            + "a2cvT3V0ZXJDbGFzcwEAEGphdmEvbGFuZy9PYmplY3QBAB50ZXN0L3BrZy9P"
            + "dXRlckNsYXNzJElubmVyQ2xhc3MAIQACAAMAAAABABkABwAIAAEACQAAAAIA"
            + "CgABAAEACwAMAAEADQAAAC8AAQABAAAABSq3AAGxAAAAAgAOAAAABgABAAAA"
            + "BQAPAAAADAABAAAABQAQABEAAAACABIAAAACABMABgAAAAoAAQAEAAIABSYJ");

    /** Compiled version of inner class listed as part of {@link #OUTER_CLASS} */
    public static final byte[] INNER_CLASS = Base64.getDecoder().decode(""
            + "yv66vgAAADMADQcABwcACgcACwEAClNvdXJjZUZpbGUBAA9PdXRlckNsYXNz"
            + "LmphdmEHAAwBAB50ZXN0L3BrZy9PdXRlckNsYXNzJElubmVyQ2xhc3MBAApJ"
            + "bm5lckNsYXNzAQAMSW5uZXJDbGFzc2VzAQAQamF2YS9sYW5nL09iamVjdAEA"
            + "H2phdmEvbGFuZy9hbm5vdGF0aW9uL0Fubm90YXRpb24BABN0ZXN0L3BrZy9P"
            + "dXRlckNsYXNzJgEAAQACAAEAAwAAAAAAAgAEAAAAAgAFAAkAAAAKAAEAAQAG"
            + "AAgmCQ==");

    /** The expected binary version of the outer class when the references
     * to inner class have been removed */
    public static final byte[] REWRITTEN_OUTER_CLASS = Base64.getDecoder().decode(""
            + "yv66vgAAADMAFAEAE3Rlc3QvcGtnL091dGVyQ2xhc3MHAAEBABBqYXZhL2xh"
            + "bmcvT2JqZWN0BwADAQAPT3V0ZXJDbGFzcy5qYXZhAQAIQ09OU1RBTlQBAAFJ"
            + "AwAAAAEBAAY8aW5pdD4BAAMoKVYMAAkACgoABAALAQAEdGhpcwEAFUx0ZXN0"
            + "L3BrZy9PdXRlckNsYXNzOwEADUNvbnN0YW50VmFsdWUBAARDb2RlAQASTG9j"
            + "YWxWYXJpYWJsZVRhYmxlAQAPTGluZU51bWJlclRhYmxlAQAKU291cmNlRmls"
            + "ZQAhAAIABAAAAAEAGQAGAAcAAQAPAAAAAgAIAAEAAQAJAAoAAQAQAAAALwAB"
            + "AAEAAAAFKrcADLEAAAACABEAAAAMAAEAAAAFAA0ADgAAABIAAAAGAAEAAAAF"
            + "AAEAEwAAAAIABQ==");

    @Test
    public void testRecipeFile() throws IOException {
        TypedefRemover remover = new TypedefRemover();
        File typedefFile = testFolder.newFile("typedefs.txt");
        Files.write(""
                + "D test/pkg/IntDefTest$DialogFlags\n"
                + "D test/pkg/IntDefTest$DialogStyle\n"
                + "D test/pkg/OuterClass$InnerClass\n",
                typedefFile, UTF_8);

        InputStream input = new ByteArrayInputStream(new byte[] { 1, 2, 3, 4, 5});
        remover.setTypedefFile(typedefFile);

        InputStream filtered;

        // Most files should be passed through directly: input stream == filtered stream
        filtered = remover.filter("foo/bar/Baz.class", input);
        assertThat(filtered).isSameAs(input);

        // Removed typedefs should return null as the filtered output
        filtered = remover.filter("test/pkg/IntDefTest$DialogFlags.class", input);
        assertThat(filtered).isNull();

        filtered = remover.filter("test/pkg/IntDefTest$DialogStyle.class", input);
        assertThat(filtered).isNull();

        filtered = remover.filter("test/pkg/OuterClass$InnerClass.class", input);
        assertThat(filtered).isNull();

        byte[] outerClass = OUTER_CLASS;

        filtered = remover.filter("test/pkg/OuterClass.class",
                new ByteArrayInputStream(outerClass));

        assertThat(filtered).isNotNull();
        assertThat(filtered).isNotSameAs(input);
        byte[] rewritten = ByteStreams.toByteArray(filtered);
        Files.write(rewritten, new File("/tmp/rewritten.class"));
        assertThat(rewritten).isNotEqualTo(outerClass);

        assertThat(rewritten).isEqualTo(REWRITTEN_OUTER_CLASS);
    }

    @Test
    public void testRemoveFiles() throws IOException {
        TypedefRemover remover = new TypedefRemover();
        testFolder.newFolder("test", "pkg");
        File innerClass = testFolder.newFile("test/pkg/OuterClass$InnerClass.class");
        File outerClass = testFolder.newFile("test/pkg/OuterClass.class");
        File unrelated = testFolder.newFile("test/pkg/Unrelated.class");
        //noinspection ResultOfMethodCallIgnored
        innerClass.getParentFile().mkdirs();
        Files.write(INNER_CLASS, innerClass);
        Files.write(OUTER_CLASS, outerClass);
        Files.write(new byte[0], unrelated);

        remover.remove(testFolder.getRoot(),
                Collections.singletonList("test/pkg/OuterClass$InnerClass"));

        assertThat(unrelated.isFile()).isTrue();
        assertThat(innerClass.exists()).isFalse();
        assertThat(Files.toByteArray(outerClass)).isEqualTo(REWRITTEN_OUTER_CLASS);
    }
}