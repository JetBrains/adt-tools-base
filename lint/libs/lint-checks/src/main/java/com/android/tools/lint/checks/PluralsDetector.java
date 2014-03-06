/*
 * Copyright (C) 2013 The Android Open Source Project
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

import static com.android.SdkConstants.ATTR_QUANTITY;
import static com.android.SdkConstants.TAG_ITEM;
import static com.android.SdkConstants.TAG_PLURALS;
import static com.android.tools.lint.checks.PluralsDetector.Quantity.few;
import static com.android.tools.lint.checks.PluralsDetector.Quantity.many;
import static com.android.tools.lint.checks.PluralsDetector.Quantity.one;
import static com.android.tools.lint.checks.PluralsDetector.Quantity.two;
import static com.android.tools.lint.checks.PluralsDetector.Quantity.zero;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.utils.Pair;
import com.google.common.collect.Maps;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * Checks for issues with quantity strings
 * https://code.google.com/p/android/issues/detail?id=53015
 * 53015: lint could report incorrect usage of Resource.getQuantityString
 */
public class PluralsDetector extends ResourceXmlDetector {
    private static final Implementation IMPLEMENTATION = new Implementation(
            PluralsDetector.class,
            Scope.RESOURCE_FILE_SCOPE);

    /** This locale should define a quantity string for the given quantity */
    public static final Issue MISSING = Issue.create(
            "MissingQuantity", //$NON-NLS-1$
            "Missing quantity translation",
            "Checks for missing quantity strings relevant to each locale",
            "Different languages have different rules for grammatical agreement with " +
            "quantity. In English, for example, the quantity 1 is a special case. " +
            "We write \"1 book\", but for any other quantity we'd write \"n books\". " +
            "This distinction between singular and plural is very common, but other " +
            "languages make finer distinctions.\n" +
            "\n" +
            "This lint check looks at each translation of a `<plural>` and makes sure " +
            "that all the quantity strings considered by the given language are provided " +
            "by this translation.\n" +
            "\n" +
            "For example, an English translation must provide a string for `quantity=\"one\"`. " +
            "Similarly, a Czech translation must provide a string for `quantity=\"few\"`.",
            Category.MESSAGES,
            8,
            Severity.ERROR,
            IMPLEMENTATION).addMoreInfo(
            "http://developer.android.com/guide/topics/resources/string-resource.html#Plurals");

    /** This translation is not needed in this locale */
    public static final Issue EXTRA = Issue.create(
            "UnusedQuantity", //$NON-NLS-1$
            "Unused quantity translations",
            "Checks for quantity string translations which are not used in this language",
            "Android defines a number of different quantity strings, such as `zero`, `one`, " +
            "`few` and `many`. However, many languages do not distinguish grammatically " +
            "between all these different quantities.\n" +
            "\n" +
            "This lint check looks at the quantity strings defined for each translation and " +
            "flags any quantity strings that are unused (because the language does not make that " +
            "quantity distinction, and Android will therefore not look it up.)." +
            "\n" +
            "For example, in Chinese, only the `other` quantity is used, so even if you " +
            "provide translations for `zero` and `one`, these strings will *not* be returned " +
            "when `getQuantityString()` is called, even with `0` or `1`.",
            Category.MESSAGES,
            3,
            Severity.WARNING,
            IMPLEMENTATION).addMoreInfo(
            "http://developer.android.com/guide/topics/resources/string-resource.html#Plurals");

    /** Constructs a new {@link PluralsDetector} */
    public PluralsDetector() {
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.VALUES;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_PLURALS);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        int count = LintUtils.getChildCount(element);
        if (count == 0) {
            context.report(MISSING, element, context.getLocation(element),
                    "There should be at least one quantity string in this <plural> definition",
                    null);
            return;
        }

        Pair<String, String> locale = TypoDetector.getLocale(context);
        if (locale == null) {
            return;
        }
        String language = locale.getFirst();
        if (language == null) {
            return;
        }

        EnumSet<Quantity> defined = EnumSet.noneOf(Quantity.class);
        NodeList children = element.getChildNodes();
        for (int i = 0, n = children.getLength(); i < n; i++) {
            Node noe = children.item(i);
            if (noe.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element child = (Element) noe;
            if (!TAG_ITEM.equals(child.getTagName())) {
                continue;
            }
            String quantityString = child.getAttribute(ATTR_QUANTITY);
            if (quantityString == null || quantityString.isEmpty()) {
                continue;
            }
            Quantity quantity = Quantity.get(quantityString);
            if (quantity == Quantity.other) { // Not stored in the database
                continue;
            }
            if (quantity != null) {
                defined.add(quantity);
            }
        }

        EnumSet<Quantity> relevant = getRelevant(language);
        if (relevant == null) {
            return;
        }

        if (relevant.equals(defined)) {
            return;
        }

        // Look for missing
        EnumSet<Quantity> missing = relevant.clone();
        missing.removeAll(defined);
        if (!missing.isEmpty()) {
            String message = String.format(
                    "For locale \"%1$s\" the following quantities should also be defined: %2$s",
                    language, formatSet(missing));
            context.report(MISSING, element, context.getLocation(element), message, null);
        }

        // Look for irrelevant
        EnumSet<Quantity> extra = defined.clone();
        extra.removeAll(relevant);
        if (!extra.isEmpty()) {
            String message = String.format(
                    "For language \"%1$s\" the following quantities are not relevant: %2$s",
                    language, formatSet(extra));
            context.report(EXTRA, element, context.getLocation(element), message, null);
        }
    }

    private static String formatSet(EnumSet<Quantity> set) {
        List<String> list = new ArrayList<String>(set.size());
        for (Quantity quantity : set) {
            list.add(quantity.name());
        }
        return LintUtils.formatList(list, Integer.MAX_VALUE);
    }

    enum Quantity {
        few, many, one, two, zero, other; // deliberately lower case to match attribute names

        @Nullable
        public static Quantity get(@NonNull String name) {
            for (Quantity quantity : values()) {
                if (name.equals(quantity.name())) {
                    return quantity;
                }
            }

            return null;
        }
    }

    private static Map<String, EnumSet<Quantity>> sPlurals;

    @SuppressWarnings({"UnnecessaryLocalVariable", "UnusedDeclaration"})
    @Nullable
    public static EnumSet<Quantity> getRelevant(@NonNull String language) {
        // Based on the plurals table in plurals.txt in icu4c
        if (sPlurals == null) {
            EnumSet<Quantity> empty = EnumSet.noneOf(Quantity.class);
            EnumSet<Quantity> set1 = EnumSet.of(few, many, one, two, zero);
            EnumSet<Quantity> set10 = EnumSet.of(few, many, one);
            EnumSet<Quantity> set11 = EnumSet.of(few, one);
            EnumSet<Quantity> set12 = set10;
            EnumSet<Quantity> set13 = EnumSet.of(few, one, two);
            EnumSet<Quantity> set14 = set12;
            EnumSet<Quantity> set15 = EnumSet.of(one);
            EnumSet<Quantity> set16 = set1;
            EnumSet<Quantity> set17 = EnumSet.of(one, zero);
            EnumSet<Quantity> set18 = set11;
            EnumSet<Quantity> set19 = EnumSet.of(few, many, one, two);
            EnumSet<Quantity> set2 = set15;
            EnumSet<Quantity> set20 = set17;
            EnumSet<Quantity> set21 = set2;
            EnumSet<Quantity> set22 = set2;
            EnumSet<Quantity> set23 = set13;
            EnumSet<Quantity> set3 = set2;
            EnumSet<Quantity> set4 = set2;
            EnumSet<Quantity> set5 = set20;
            EnumSet<Quantity> set6 = EnumSet.of(one, two);
            EnumSet<Quantity> set7 = set19;
            EnumSet<Quantity> set8 = set11;
            EnumSet<Quantity> set9 = set8;

            final int INITIAL_CAPACITY = 124;
            sPlurals = Maps.newHashMapWithExpectedSize(INITIAL_CAPACITY);
            sPlurals.put("af", set2);
            sPlurals.put("ak", set3);
            sPlurals.put("am", set3);
            sPlurals.put("ar", set1);
            sPlurals.put("az", empty);
            sPlurals.put("be", set10);
            sPlurals.put("bg", set2);
            sPlurals.put("bh", set3);
            sPlurals.put("bm", empty);
            sPlurals.put("bn", set2);
            sPlurals.put("bo", empty);
            sPlurals.put("br", set19);
            sPlurals.put("bs", set10);
            sPlurals.put("ca", set2);
            sPlurals.put("cs", set11);
            sPlurals.put("cy", set16);
            sPlurals.put("da", set2);
            sPlurals.put("de", set2);
            sPlurals.put("dv", set2);
            sPlurals.put("dz", empty);
            sPlurals.put("ee", set2);
            sPlurals.put("el", set2);
            sPlurals.put("en", set2);
            sPlurals.put("eo", set2);
            sPlurals.put("es", set2);
            sPlurals.put("et", set2);
            sPlurals.put("eu", set2);
            sPlurals.put("fa", empty);
            sPlurals.put("ff", set4);
            sPlurals.put("fi", set2);
            sPlurals.put("fo", set2);
            sPlurals.put("fr", set4);
            sPlurals.put("fy", set2);
            sPlurals.put("ga", set7);
            sPlurals.put("gd", set23);
            sPlurals.put("gl", set2);
            sPlurals.put("gu", set2);
            sPlurals.put("gv", set22);
            sPlurals.put("ha", set2);
            sPlurals.put("he", set2);
            sPlurals.put("hi", set3);
            sPlurals.put("hr", set10);
            sPlurals.put("hu", empty);
            sPlurals.put("id", empty);
            sPlurals.put("ig", empty);
            sPlurals.put("ii", empty);
            sPlurals.put("is", set2);
            sPlurals.put("it", set2);
            sPlurals.put("iu", set6);
            sPlurals.put("ja", empty);
            sPlurals.put("jv", empty);
            sPlurals.put("ka", empty);
            sPlurals.put("kk", set2);
            sPlurals.put("kl", set2);
            sPlurals.put("km", empty);
            sPlurals.put("kn", empty);
            sPlurals.put("ko", empty);
            sPlurals.put("ku", set2);
            sPlurals.put("kw", set6);
            sPlurals.put("lb", set2);
            sPlurals.put("lg", set2);
            sPlurals.put("ln", set3);
            sPlurals.put("lo", empty);
            sPlurals.put("lt", set9);
            sPlurals.put("lv", set5);
            sPlurals.put("mg", set3);
            sPlurals.put("mk", set15);
            sPlurals.put("ml", set2);
            sPlurals.put("mn", set2);
            sPlurals.put("mo", set8);
            sPlurals.put("mr", set2);
            sPlurals.put("ms", empty);
            sPlurals.put("mt", set14);
            sPlurals.put("my", empty);
            sPlurals.put("nb", set2);
            sPlurals.put("nd", set2);
            sPlurals.put("ne", set2);
            sPlurals.put("nl", set2);
            sPlurals.put("nn", set2);
            sPlurals.put("no", set2);
            sPlurals.put("nr", set2);
            sPlurals.put("ny", set2);
            sPlurals.put("om", set2);
            sPlurals.put("or", set2);
            sPlurals.put("pa", set2);
            sPlurals.put("pl", set12);
            sPlurals.put("ps", set2);
            sPlurals.put("pt", set2);
            sPlurals.put("rm", set2);
            sPlurals.put("ro", set8);
            sPlurals.put("ru", set10);
            sPlurals.put("se", set6);
            sPlurals.put("sg", empty);
            sPlurals.put("sh", set10);
            sPlurals.put("sk", set11);
            sPlurals.put("sl", set13);
            sPlurals.put("sn", set2);
            sPlurals.put("so", set2);
            sPlurals.put("sq", set2);
            sPlurals.put("sr", set10);
            sPlurals.put("ss", set2);
            sPlurals.put("st", set2);
            sPlurals.put("sv", set2);
            sPlurals.put("sw", set2);
            sPlurals.put("ta", set2);
            sPlurals.put("te", set2);
            sPlurals.put("th", empty);
            sPlurals.put("ti", set3);
            sPlurals.put("tk", set2);
            sPlurals.put("tl", set3);
            sPlurals.put("tn", set2);
            sPlurals.put("to", empty);
            sPlurals.put("tr", empty);
            sPlurals.put("ts", set2);
            sPlurals.put("uk", set10);
            sPlurals.put("ur", set2);
            sPlurals.put("ve", set2);
            sPlurals.put("vi", empty);
            sPlurals.put("wa", set3);
            sPlurals.put("wo", empty);
            sPlurals.put("xh", set2);
            sPlurals.put("yo", empty);
            sPlurals.put("zh", empty);
            sPlurals.put("zu", set2);
            assert sPlurals.size() == INITIAL_CAPACITY : sPlurals.size();
        }
        return sPlurals.get(language);
    }
}
