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
package com.android.tools.lint.checks;

import static com.android.tools.lint.checks.PluralsDatabase.Quantity.few;
import static com.android.tools.lint.checks.PluralsDatabase.Quantity.many;
import static com.android.tools.lint.checks.PluralsDatabase.Quantity.one;
import static com.android.tools.lint.checks.PluralsDatabase.Quantity.two;
import static com.android.tools.lint.checks.PluralsDatabase.Quantity.zero;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.LintUtils;
import com.google.common.collect.Maps;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Database used by the {@link com.android.tools.lint.checks.PluralsDetector} to get information
 * about plural forms for a given language
 */
public class PluralsDatabase {
    private static final PluralsDatabase sInstance = new PluralsDatabase();

    private Map<String, EnumSet<Quantity>> mPlurals;
    private Map<Quantity, Map<Set<Quantity>,Boolean>> mMultiValueSetNames =
            Maps.newEnumMap(Quantity.class);


    @NonNull
    public static PluralsDatabase get() {
        return sInstance;
    }

    @Nullable
    public EnumSet<Quantity> getRelevant(@NonNull String language) {
        ensureInitialized();
        return mPlurals.get(language);
    }

    public boolean hasMultipleValuesForQuantity(@NonNull String language,
            @NonNull Quantity quantity) {
        if (quantity == Quantity.one || quantity == Quantity.two || quantity == Quantity.zero) {
            ensureInitialized();
            EnumSet<Quantity> relevant = mPlurals.get(language);
            if (relevant != null) {
                Map<Set<Quantity>,Boolean> names = mMultiValueSetNames.get(quantity);
                assert names != null : quantity;
                return names.containsKey(relevant);
            }
        }

        return false;
    }

    private void ensureInitialized() {
        // Based on the plurals table in plurals.txt in icu4c, version 52:
        //  external/icu4c/data/misc/plurals.txt
        // The format is documented here:
        // http://unicode.org/reports/tr35/tr35-numbers.html#Language_Plural_Rules

        if (mPlurals == null) {
            initialize();
        }
    }

    @SuppressWarnings({"UnnecessaryLocalVariable", "UnusedDeclaration"})
    private void initialize() {
        // Quantity.other appears in every single set, so it was instead removed and
        // handled at the check site.
        EnumSet<Quantity> empty = EnumSet.noneOf(Quantity.class);

        EnumSet<Quantity> set0 = EnumSet.of(few, many, one, two, zero);
        EnumSet<Quantity> set1 = EnumSet.of(many, one, two);
        EnumSet<Quantity> set10 = EnumSet.of(few, many, one);
        EnumSet<Quantity> set11 = EnumSet.of(few, one);  // "many" are only for fractions
        EnumSet<Quantity> set12 = set10;
        EnumSet<Quantity> set13 = EnumSet.of(few, one, two);
        EnumSet<Quantity> set14 = set10;
        EnumSet<Quantity> set15 = EnumSet.of(one);
        EnumSet<Quantity> set16 = set0;
        EnumSet<Quantity> set17 = EnumSet.of(one, zero);
        EnumSet<Quantity> set18 = set11;
        EnumSet<Quantity> set19 = EnumSet.of(few, many, one, two);
        EnumSet<Quantity> set2 = set15;
        EnumSet<Quantity> set20 = set17;
        EnumSet<Quantity> set21 = set15;
        EnumSet<Quantity> set22 = set13;
        EnumSet<Quantity> set23 = set22;
        EnumSet<Quantity> set24 = empty;
        EnumSet<Quantity> set25 = set15;
        EnumSet<Quantity> set26 = set15;
        EnumSet<Quantity> set27 = set15;
        EnumSet<Quantity> set28 = set15;
        EnumSet<Quantity> set29 = set15;
        EnumSet<Quantity> set3 = set15;
        EnumSet<Quantity> set30 = set15;
        EnumSet<Quantity> set31 = set15;
        EnumSet<Quantity> set32 = set15;
        EnumSet<Quantity> set33 = set18;
        EnumSet<Quantity> set34 = EnumSet.of(many, one);
        EnumSet<Quantity> set35 = set10;
        EnumSet<Quantity> set36 = set15;
        EnumSet<Quantity> set37 = set15;
        EnumSet<Quantity> set38 = set15;
        EnumSet<Quantity> set39 = set13;
        EnumSet<Quantity> set4 = set15;
        EnumSet<Quantity> set40 = EnumSet.of(many);
        EnumSet<Quantity> set41 = set13;
        EnumSet<Quantity> set42 = set13;
        EnumSet<Quantity> set43 = set19;
        EnumSet<Quantity> set44 = set19;
        EnumSet<Quantity> set45 = set10;
        EnumSet<Quantity> set5 = set17;
        EnumSet<Quantity> set6 = EnumSet.of(one, two);
        EnumSet<Quantity> set7 = set19;
        EnumSet<Quantity> set8 = set18;
        EnumSet<Quantity> set9 = set18; // "many" only for fractions, so using different set

        // The following sets are used by the mMultiValueSetNames map, and therefore need
        // to have their own instances since we will look up set identity
        set10 = EnumSet.copyOf(set10);
        set13 = EnumSet.copyOf(set13);
        set15 = EnumSet.copyOf(set15);
        set18 = EnumSet.copyOf(set18);
        set19 = EnumSet.copyOf(set19);
        set21 = EnumSet.copyOf(set21);
        set22 = EnumSet.copyOf(set22);
        set23 = EnumSet.copyOf(set23);
        set25 = EnumSet.copyOf(set25);
        set3 = EnumSet.copyOf(set3);
        set30 = EnumSet.copyOf(set30);
        set31 = EnumSet.copyOf(set31);
        set32 = EnumSet.copyOf(set32);
        set33 = EnumSet.copyOf(set33);
        set34 = EnumSet.copyOf(set34);
        set35 = EnumSet.copyOf(set35);
        set38 = EnumSet.copyOf(set38);
        set39 = EnumSet.copyOf(set39);
        set4 = EnumSet.copyOf(set4);
        set40 = EnumSet.copyOf(set40);
        set42 = EnumSet.copyOf(set42);
        set43 = EnumSet.copyOf(set43);
        set44 = EnumSet.copyOf(set44);
        set45 = EnumSet.copyOf(set45);
        set5 = EnumSet.copyOf(set5);
        set9 = EnumSet.copyOf(set9);

        final int INITIAL_CAPACITY = 133;
        mPlurals = Maps.newHashMapWithExpectedSize(INITIAL_CAPACITY);
        mPlurals.put("af", set2);
        mPlurals.put("ak", set3);
        mPlurals.put("am", set30);
        mPlurals.put("ar", set0);
        mPlurals.put("az", set2);
        mPlurals.put("be", set10);
        mPlurals.put("bg", set2);
        mPlurals.put("bh", set3);
        mPlurals.put("bm", set24);
        mPlurals.put("bn", set30);
        mPlurals.put("bo", set24);
        mPlurals.put("br", set19);
        mPlurals.put("bs", set33);
        mPlurals.put("ca", set26);
        mPlurals.put("cs", set11);
        mPlurals.put("cy", set16);
        mPlurals.put("da", set28);
        mPlurals.put("de", set26);
        mPlurals.put("dv", set2);
        mPlurals.put("dz", set24);
        mPlurals.put("ee", set2);
        mPlurals.put("el", set2);
        mPlurals.put("en", set26);
        mPlurals.put("eo", set2);
        mPlurals.put("es", set2);
        mPlurals.put("et", set26);
        mPlurals.put("eu", set2);
        mPlurals.put("fa", set30);
        mPlurals.put("ff", set4);
        mPlurals.put("fi", set26);
        mPlurals.put("fo", set2);
        mPlurals.put("fr", set4);
        mPlurals.put("fy", set2);
        mPlurals.put("ga", set7);
        mPlurals.put("gd", set23);
        mPlurals.put("gl", set26);
        mPlurals.put("gu", set30);
        mPlurals.put("gv", set22);
        mPlurals.put("ha", set2);
        mPlurals.put("he", set1);
        mPlurals.put("hi", set30);
        mPlurals.put("hr", set33);
        mPlurals.put("hu", set2);
        mPlurals.put("hy", set4);
        mPlurals.put("id", set24);
        mPlurals.put("ig", set24);
        mPlurals.put("ii", set24);
        mPlurals.put("in", set24);
        mPlurals.put("is", set31);
        mPlurals.put("it", set26);
        mPlurals.put("iu", set6);
        mPlurals.put("iw", set1);
        mPlurals.put("ja", set24);
        mPlurals.put("ji", set26);
        mPlurals.put("jv", set24);
        // Javanese replaced by "jv"
        //mPlurals.put("jw", set24);
        mPlurals.put("ka", set2);
        mPlurals.put("kk", set2);
        mPlurals.put("kl", set2);
        mPlurals.put("km", set24);
        mPlurals.put("kn", set30);
        mPlurals.put("ko", set24);
        mPlurals.put("ks", set2);
        mPlurals.put("ku", set2);
        mPlurals.put("kw", set6);
        mPlurals.put("ky", set2);
        mPlurals.put("lb", set2);
        mPlurals.put("lg", set2);
        mPlurals.put("ln", set3);
        mPlurals.put("lo", set24);
        mPlurals.put("lt", set9);
        mPlurals.put("lv", set5);
        mPlurals.put("mg", set3);
        mPlurals.put("mk", set15);
        mPlurals.put("ml", set2);
        mPlurals.put("mn", set2);
        // Deprecated
        //mPlurals.put("mo", set8);
        mPlurals.put("mr", set30);
        mPlurals.put("ms", set24);
        mPlurals.put("mt", set14);
        mPlurals.put("my", set24);
        mPlurals.put("nb", set2);
        mPlurals.put("nd", set2);
        mPlurals.put("ne", set2);
        mPlurals.put("nl", set26);
        mPlurals.put("nn", set2);
        mPlurals.put("no", set2);
        mPlurals.put("nr", set2);
        mPlurals.put("ny", set2);
        mPlurals.put("om", set2);
        mPlurals.put("or", set2);
        mPlurals.put("os", set2);
        mPlurals.put("pa", set3);
        mPlurals.put("pl", set12);
        mPlurals.put("ps", set2);
        mPlurals.put("pt", set27);
        // Luckily these sets are identical so we don't need to make a region distinction
        // in the API
        //mPlurals.put("pt_PT", set29); // XXX
        mPlurals.put("rm", set2);
        mPlurals.put("ro", set8);
        mPlurals.put("ru", set34);
        mPlurals.put("se", set6);
        mPlurals.put("sg", set24);
        // sh was removed from 639-1 to 639-2
        //mPlurals.put("sh", set33);
        mPlurals.put("si", set32);
        mPlurals.put("sk", set11);
        mPlurals.put("sl", set13);
        mPlurals.put("sn", set2);
        mPlurals.put("so", set2);
        mPlurals.put("sq", set2);
        mPlurals.put("sr", set33);
        mPlurals.put("ss", set2);
        mPlurals.put("st", set2);
        mPlurals.put("sv", set26);
        mPlurals.put("sw", set26);
        mPlurals.put("ta", set2);
        mPlurals.put("te", set2);
        mPlurals.put("th", set24);
        mPlurals.put("ti", set3);
        mPlurals.put("tk", set2);
        mPlurals.put("tl", set25);
        mPlurals.put("tn", set2);
        mPlurals.put("to", set24);
        mPlurals.put("tr", set2);
        mPlurals.put("ts", set2);
        mPlurals.put("uk", set35);
        mPlurals.put("ur", set26);
        mPlurals.put("uz", set2);
        mPlurals.put("ve", set2);
        mPlurals.put("vi", set24);
        mPlurals.put("vo", set2);
        mPlurals.put("wa", set3);
        mPlurals.put("wo", set24);
        mPlurals.put("xh", set2);
        mPlurals.put("yi", set26);
        mPlurals.put("yo", set24);
        mPlurals.put("zh", set24);
        mPlurals.put("zu", set30);

        assert mPlurals.size() == INITIAL_CAPACITY : mPlurals.size();

        // Sets where more than a single integer maps to one. Take for example
        // set 10:
        //    set10{
        //        one{
        //            "n % 10 = 1 and n % 100 != 11 @integer 1, 21, 31, 41, 51, 61, 71, 81,"
        //            " 101, 1001, … @decimal 1.0, 21.0, 31.0, 41.0, 51.0, 61.0, 71.0, 81.0"
        //            ", 101.0, 1001.0, …"
        //        }
        //    }
        // Here we see that both "1" and "21" will match the "one" category.
        // Note that this only applies to integers (since getQuantityString only takes integer)
        // whereas the plurals data also covers fractions. I was not sure what to do about
        // set17:
        //    set17{
        //        one{"i = 0,1 and n != 0 @integer 1 @decimal 0.1~1.6"}
        //    }
        // since it looks to me like this only differs from 1 in the fractional part.

        //noinspection unchecked
        mMultiValueSetNames.put(Quantity.one, newIdentityHashMap(
                set10, set13, set15, set18, set19, set21, set22, set23, set25, set3, set30,
                set31, set32, set33, set34, set35, set38, set39, set4, set40, set42, set45,
                set5, set9
        ));

        // Sets where more than a single integer maps to two.
        //noinspection unchecked
        mMultiValueSetNames.put(Quantity.two, newIdentityHashMap(
                set13, set19, set22, set23, set40, set43, set44, set45
        ));

        // Sets where more than a single integer maps to zero.
        //noinspection unchecked
        mMultiValueSetNames.put(Quantity.zero, newIdentityHashMap(set5));
    }

    private static Map<Set<Quantity>,Boolean> newIdentityHashMap(Set<Quantity>... elements) {
        Map<Set<Quantity>,Boolean> map = Maps.newIdentityHashMap();
        for (Set<Quantity> set : elements) {
            map.put(set, true);
        }
        return map;
    }

    @SuppressWarnings({"MethodMayBeStatic", "UnusedParameters"})
    @Nullable
    public String findIntegerExamples(@NonNull String language, @NonNull Quantity quantity) {
        // Need plurals database
        return null;
    }

    public enum Quantity {
        // deliberately lower case to match attribute names
        few, many, one, two, zero, other;

        @Nullable
        public static Quantity get(@NonNull String name) {
            for (Quantity quantity : values()) {
                if (name.equals(quantity.name())) {
                    return quantity;
                }
            }

            return null;
        }

        public static String formatSet(EnumSet<Quantity> set) {
            List<String> list = new ArrayList<String>(set.size());
            for (Quantity quantity : set) {
                list.add(quantity.name());
            }
            return LintUtils.formatList(list, Integer.MAX_VALUE);
        }
    }
}
