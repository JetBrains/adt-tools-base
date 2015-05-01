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

package com.android.build.gradle.integration.common.truth;

import static com.google.common.truth.Truth.assert_;

import com.android.annotations.NonNull;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Dependencies;
import com.android.builder.model.SyncIssue;
import com.android.builder.model.Variant;
import com.google.common.base.Optional;
import com.google.common.truth.BooleanSubject;
import com.google.common.truth.ClassSubject;
import com.google.common.truth.CollectionSubject;
import com.google.common.truth.ComparableSubject;
import com.google.common.truth.DefaultSubject;
import com.google.common.truth.IntegerSubject;
import com.google.common.truth.IterableSubject;
import com.google.common.truth.ListSubject;
import com.google.common.truth.LongSubject;
import com.google.common.truth.MapSubject;
import com.google.common.truth.ObjectArraySubject;
import com.google.common.truth.OptionalSubject;
import com.google.common.truth.PrimitiveBooleanArraySubject;
import com.google.common.truth.PrimitiveByteArraySubject;
import com.google.common.truth.PrimitiveCharArraySubject;
import com.google.common.truth.PrimitiveDoubleArraySubject;
import com.google.common.truth.PrimitiveFloatArraySubject;
import com.google.common.truth.PrimitiveIntArraySubject;
import com.google.common.truth.PrimitiveLongArraySubject;
import com.google.common.truth.StringSubject;
import com.google.common.truth.Subject;
import com.google.common.truth.TestVerb;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Helper for custom Truth factories.
 */
public class TruthHelper {

    @NonNull
    public static ApkSubject assertThatApk(@NonNull File apk) {
        return assert_().about(ApkSubject.Factory.get()).that(apk);
    }

    @NonNull
    public static AarSubject assertThatAar(@NonNull File aar) {
        return assert_().about(AarSubject.Factory.get()).that(aar);
    }

    @NonNull
    public static ZipFileSubject assertThatZip(@NonNull File file) {
        return assert_().about(ZipFileSubject.Factory.get()).that(file);
    }

    @NonNull
    public static ModelSubject assertThat(@NonNull AndroidProject androidProject) {
        return assert_().about(ModelSubject.Factory.get()).that(androidProject);
    }

    @NonNull
    public static VariantSubject assertThat(@NonNull Variant variant) {
        return assert_().about(VariantSubject.Factory.get()).that(variant);
    }

    @NonNull
    public static ArtifactSubject assertThat(@NonNull AndroidArtifact artifact) {
        return assert_().about(ArtifactSubject.Factory.get()).that(artifact);
    }

    @NonNull
    public static DependenciesSubject assertThat(@NonNull Dependencies dependencies) {
        return assert_().about(DependenciesSubject.Factory.get()).that(
                dependencies);
    }

    @NonNull
    public static IssueSubject assertThat(@NonNull SyncIssue issue) {
        return assert_().about(IssueSubject.Factory.get()).that(issue);
    }


    // ---- helper method from com.google.common.truth.Truth
    // this to allow a single static import of assertThat

    public static <T extends Comparable<?>> ComparableSubject<?, T> assertThat(T target) {
        return assert_().that(target);
    }

    public static Subject<DefaultSubject, Object> assertThat(Object target) {
        return assert_().that(target);
    }

    public static ClassSubject assertThat(Class<?> target) {
        return assert_().that(target);
    }

    public static LongSubject assertThat(Long target) {
        return assert_().that(target);
    }

    public static IntegerSubject assertThat(Integer target) {
        return assert_().that(target);
    }

    public static BooleanSubject assertThat(Boolean target) {
        return assert_().that(target);
    }

    public static StringSubject assertThat(String target) {
        return assert_().that(target);
    }

    public static <T, C extends Iterable<T>> IterableSubject<? extends IterableSubject<?, T, C>, T, C>
    assertThat(Iterable<T> target) {
        return assert_().that(target);
    }

    public static <T, C extends Collection<T>>
    CollectionSubject<? extends CollectionSubject<?, T, C>, T, C>
    assertThat(Collection<T> target) {
        return assert_().that(target);
    }

    public static <T, C extends List<T>> ListSubject<? extends ListSubject<?, T, C>, T, C>
    assertThat(List<T> target) {
        return assert_().that(target);
    }

    public static <T> ObjectArraySubject<T> assertThat(T[] target) {
        return assert_().that(target);
    }

    public static PrimitiveBooleanArraySubject assertThat(boolean[] target) {
        return assert_().that(target);
    }

    public static PrimitiveIntArraySubject assertThat(int[] target) {
        return assert_().that(target);
    }

    public static PrimitiveLongArraySubject assertThat(long[] target) {
        return assert_().that(target);
    }

    public static PrimitiveByteArraySubject assertThat(byte[] target) {
        return assert_().that(target);
    }

    public static PrimitiveCharArraySubject assertThat(char[] target) {
        return assert_().that(target);
    }

    public static PrimitiveFloatArraySubject assertThat(float[] target) {
        return assert_().that(target);
    }

    public static PrimitiveDoubleArraySubject assertThat(double[] target) {
        return assert_().that(target);
    }

    public static <T> OptionalSubject<T> assertThat(Optional<T> target) {
        return assert_().that(target);
    }

    public static <K, V, M extends Map<K, V>> MapSubject<? extends MapSubject<?, K, V, M>, K, V, M>
    assertThat(Map<K, V> target) {
        return assert_().that(target);
    }

    public static TestVerb assertWithMessage(String message) {
        return assert_().withFailureMessage(message);
    }
}
