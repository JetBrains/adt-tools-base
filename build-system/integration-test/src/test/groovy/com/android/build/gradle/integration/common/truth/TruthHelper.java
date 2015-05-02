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
import com.android.annotations.Nullable;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Dependencies;
import com.android.builder.model.SyncIssue;
import com.android.builder.model.Variant;
import com.google.common.annotations.GwtIncompatible;
import com.google.common.base.Optional;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multiset;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Table;
import com.google.common.truth.BigDecimalSubject;
import com.google.common.truth.BooleanSubject;
import com.google.common.truth.ClassSubject;
import com.google.common.truth.ComparableSubject;
import com.google.common.truth.DefaultSubject;
import com.google.common.truth.DoubleSubject;
import com.google.common.truth.IntegerSubject;
import com.google.common.truth.IterableSubject;
import com.google.common.truth.ListMultimapSubject;
import com.google.common.truth.LongSubject;
import com.google.common.truth.MapSubject;
import com.google.common.truth.MultimapSubject;
import com.google.common.truth.MultisetSubject;
import com.google.common.truth.ObjectArraySubject;
import com.google.common.truth.OptionalSubject;
import com.google.common.truth.PrimitiveBooleanArraySubject;
import com.google.common.truth.PrimitiveByteArraySubject;
import com.google.common.truth.PrimitiveCharArraySubject;
import com.google.common.truth.PrimitiveDoubleArraySubject;
import com.google.common.truth.PrimitiveFloatArraySubject;
import com.google.common.truth.PrimitiveIntArraySubject;
import com.google.common.truth.PrimitiveLongArraySubject;
import com.google.common.truth.SetMultimapSubject;
import com.google.common.truth.StringSubject;
import com.google.common.truth.Subject;
import com.google.common.truth.TableSubject;
import com.google.common.truth.TestVerb;
import com.google.common.truth.ThrowableSubject;

import java.io.File;
import java.math.BigDecimal;
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
    public static IssueSubject assertThat(@NonNull SyncIssue issue) {
        return assert_().about(IssueSubject.Factory.get()).that(issue);
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

    // ---- helper method from com.google.common.truth.Truth
    // this to allow a single static import of assertThat

    /**
     * Returns a {@link TestVerb} that will prepend the given message to the failure message in
     * the event of a test failure.
     */
    public static TestVerb assertWithMessage(String messageToPrepend) {
        return assert_().withFailureMessage(messageToPrepend);
    }

    public static <T extends Comparable<?>> ComparableSubject<?, T> assertThat(@Nullable T target) {
        return assert_().that(target);
    }

    public static BigDecimalSubject assertThat(@Nullable BigDecimal target) {
        return assert_().that(target);
    }

    public static Subject<DefaultSubject, Object> assertThat(@Nullable Object target) {
        return assert_().that(target);
    }

    @GwtIncompatible("ClassSubject.java")
    public static ClassSubject assertThat(@Nullable Class<?> target) {
        return assert_().that(target);
    }

    public static ThrowableSubject assertThat(@Nullable Throwable target) {
        return assert_().that(target);
    }

    public static LongSubject assertThat(@Nullable Long target) {
        return assert_().that(target);
    }

    public static DoubleSubject assertThat(@Nullable Double target) {
        return assert_().that(target);
    }

    public static IntegerSubject assertThat(@Nullable Integer target) {
        return assert_().that(target);
    }

    public static BooleanSubject assertThat(@Nullable Boolean target) {
        return assert_().that(target);
    }

    public static StringSubject assertThat(@Nullable String target) {
        return assert_().that(target);
    }

    public static <T, C extends Iterable<T>> IterableSubject<? extends IterableSubject<?, T, C>, T, C>
    assertThat(@Nullable Iterable<T> target) {
        return assert_().that(target);
    }

    public static <T> ObjectArraySubject<T> assertThat(@Nullable T[] target) {
        return assert_().that(target);
    }

    public static PrimitiveBooleanArraySubject assertThat(@Nullable boolean[] target) {
        return assert_().that(target);
    }

    public static PrimitiveIntArraySubject assertThat(@Nullable int[] target) {
        return assert_().that(target);
    }

    public static PrimitiveLongArraySubject assertThat(@Nullable long[] target) {
        return assert_().that(target);
    }

    public static PrimitiveByteArraySubject assertThat(@Nullable byte[] target) {
        return assert_().that(target);
    }

    public static PrimitiveCharArraySubject assertThat(@Nullable char[] target) {
        return assert_().that(target);
    }

    public static PrimitiveFloatArraySubject assertThat(@Nullable float[] target) {
        return assert_().that(target);
    }

    public static PrimitiveDoubleArraySubject assertThat(@Nullable double[] target) {
        return assert_().that(target);
    }

    public static <T> OptionalSubject<T> assertThat(@Nullable Optional<T> target) {
        return assert_().that(target);
    }

    public static MapSubject assertThat(@Nullable Map<?, ?> target) {
        return assert_().that(target);
    }

    public static <K, V, M extends Multimap<K, V>>
    MultimapSubject<? extends MultimapSubject<?, K, V, M>, K, V, M> assertThat(
            @Nullable Multimap<K, V> target) {
        return assert_().that(target);
    }

    public static <K, V, M extends ListMultimap<K, V>>
    ListMultimapSubject<? extends ListMultimapSubject<?, K, V, M>, K, V, M> assertThat(
            @Nullable ListMultimap<K, V> target) {
        return assert_().that(target);
    }

    public static <K, V, M extends SetMultimap<K, V>>
    SetMultimapSubject<? extends SetMultimapSubject<?, K, V, M>, K, V, M> assertThat(
            @Nullable SetMultimap<K, V> target) {
        return assert_().that(target);
    }

    public static <E, M extends Multiset<E>>
    MultisetSubject<? extends MultisetSubject<?, E, M>, E, M> assertThat(
            @Nullable Multiset<E> target) {
        return assert_().that(target);
    }

    public static TableSubject assertThat(@Nullable Table<?, ?, ?> target) {
        return assert_().that(target);
    }
}
