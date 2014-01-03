/*
 * Copyright (C) 2011 The Android Open Source Project
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

import static com.android.tools.lint.detector.api.LintUtils.assertionsEnabled;

import com.android.annotations.NonNull;
import com.android.annotations.VisibleForTesting;
import com.android.tools.lint.client.api.IssueRegistry;
import com.android.tools.lint.detector.api.Issue;
import com.google.common.annotations.Beta;
import com.google.common.collect.Sets;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Registry which provides a list of checks to be performed on an Android project */
public class BuiltinIssueRegistry extends IssueRegistry {
    private static final List<Issue> sIssues;

    static {
        final int initialCapacity = 162;
        List<Issue> issues = new ArrayList<Issue>(initialCapacity);

        issues.add(AccessibilityDetector.ISSUE);
        issues.add(LabelForDetector.ISSUE);
        issues.add(MathDetector.ISSUE);
        issues.add(FieldGetterDetector.ISSUE);
        issues.add(SdCardDetector.ISSUE);
        issues.add(ApiDetector.UNSUPPORTED);
        issues.add(ApiDetector.INLINED);
        issues.add(ApiDetector.OVERRIDE);
        issues.add(InvalidPackageDetector.ISSUE);
        issues.add(DuplicateIdDetector.CROSS_LAYOUT);
        issues.add(DuplicateIdDetector.WITHIN_LAYOUT);
        issues.add(DuplicateResourceDetector.ISSUE);
        issues.add(WrongIdDetector.UNKNOWN_ID);
        issues.add(WrongIdDetector.UNKNOWN_ID_LAYOUT);
        issues.add(WrongIdDetector.NOT_SIBLING);
        issues.add(WrongIdDetector.INVALID);
        issues.add(LayoutConsistencyDetector.INCONSISTENT_IDS);
        issues.add(StateListDetector.ISSUE);
        issues.add(StyleCycleDetector.ISSUE);
        issues.add(InefficientWeightDetector.INEFFICIENT_WEIGHT);
        issues.add(InefficientWeightDetector.NESTED_WEIGHTS);
        issues.add(InefficientWeightDetector.BASELINE_WEIGHTS);
        issues.add(InefficientWeightDetector.WRONG_0DP);
        issues.add(InefficientWeightDetector.ORIENTATION);
        issues.add(ScrollViewChildDetector.ISSUE);
        issues.add(DeprecationDetector.ISSUE);
        issues.add(ObsoleteLayoutParamsDetector.ISSUE);
        issues.add(MergeRootFrameLayoutDetector.ISSUE);
        issues.add(NestedScrollingWidgetDetector.ISSUE);
        issues.add(ChildCountDetector.SCROLLVIEW_ISSUE);
        issues.add(ChildCountDetector.ADAPTER_VIEW_ISSUE);
        issues.add(UseCompoundDrawableDetector.ISSUE);
        issues.add(UselessViewDetector.USELESS_PARENT);
        issues.add(UselessViewDetector.USELESS_LEAF);
        issues.add(TooManyViewsDetector.TOO_MANY);
        issues.add(TooManyViewsDetector.TOO_DEEP);
        issues.add(GridLayoutDetector.ISSUE);
        issues.add(OverrideDetector.ISSUE);
        issues.add(CallSuperDetector.ISSUE);
        issues.add(OnClickDetector.ISSUE);
        issues.add(ViewTagDetector.ISSUE);
        issues.add(LocaleDetector.STRING_LOCALE);
        issues.add(LocaleDetector.DATE_FORMAT);
        issues.add(RegistrationDetector.ISSUE);
        issues.add(MissingClassDetector.MISSING);
        issues.add(MissingClassDetector.INSTANTIATABLE);
        issues.add(MissingClassDetector.INNERCLASS);
        issues.add(MissingIdDetector.ISSUE);
        issues.add(WrongCaseDetector.WRONG_CASE);
        issues.add(HandlerDetector.ISSUE);
        issues.add(FragmentDetector.ISSUE);
        issues.add(TranslationDetector.EXTRA);
        issues.add(TranslationDetector.MISSING);
        issues.add(PluralsDetector.MISSING);
        issues.add(PluralsDetector.EXTRA);
        issues.add(HardcodedValuesDetector.ISSUE);
        issues.add(Utf8Detector.ISSUE);
        issues.add(DosLineEndingDetector.ISSUE);
        issues.add(CommentDetector.EASTER_EGG);
        issues.add(CommentDetector.STOP_SHIP);
        issues.add(ProguardDetector.WRONG_KEEP);
        issues.add(ProguardDetector.SPLIT_CONFIG);
        issues.add(PxUsageDetector.PX_ISSUE);
        issues.add(PxUsageDetector.DP_ISSUE);
        issues.add(PxUsageDetector.IN_MM_ISSUE);
        issues.add(PxUsageDetector.SMALL_SP_ISSUE);
        issues.add(TextFieldDetector.ISSUE);
        issues.add(TextViewDetector.ISSUE);
        issues.add(TextViewDetector.SELECTABLE);
        issues.add(UnusedResourceDetector.ISSUE);
        issues.add(UnusedResourceDetector.ISSUE_IDS);
        issues.add(ExtraTextDetector.ISSUE);
        issues.add(PrivateResourceDetector.ISSUE);
        issues.add(ArraySizeDetector.INCONSISTENT);
        issues.add(HardcodedDebugModeDetector.ISSUE);
        issues.add(ManifestDetector.ORDER);
        issues.add(ManifestDetector.USES_SDK);
        issues.add(ManifestDetector.MULTIPLE_USES_SDK);
        issues.add(ManifestDetector.WRONG_PARENT);
        issues.add(ManifestDetector.DUPLICATE_ACTIVITY);
        issues.add(ManifestDetector.TARGET_NEWER);
        issues.add(ManifestDetector.ALLOW_BACKUP);
        issues.add(ManifestDetector.UNIQUE_PERMISSION);
        issues.add(ManifestDetector.SET_VERSION);
        issues.add(ManifestDetector.ILLEGAL_REFERENCE);
        issues.add(ManifestDetector.DUPLICATE_USES_FEATURE);
        issues.add(ManifestDetector.APPLICATION_ICON);
        issues.add(ManifestDetector.DEVICE_ADMIN);
        issues.add(ManifestDetector.MOCK_LOCATION);
        issues.add(ManifestDetector.GRADLE_OVERRIDES);
        issues.add(ManifestTypoDetector.ISSUE);
        issues.add(SecurityDetector.EXPORTED_PROVIDER);
        issues.add(SecurityDetector.EXPORTED_SERVICE);
        issues.add(SecurityDetector.EXPORTED_RECEIVER);
        issues.add(SecurityDetector.OPEN_PROVIDER);
        issues.add(SecurityDetector.WORLD_READABLE);
        issues.add(SecurityDetector.WORLD_WRITEABLE);
        issues.add(SecureRandomDetector.ISSUE);
        issues.add(CheckPermissionDetector.ISSUE);
        issues.add(SecureRandomGeneratorDetector.ISSUE);
        issues.add(IconDetector.GIF_USAGE);
        issues.add(IconDetector.ICON_DENSITIES);
        issues.add(IconDetector.ICON_MISSING_FOLDER);
        issues.add(IconDetector.ICON_DIP_SIZE);
        issues.add(IconDetector.ICON_EXPECTED_SIZE);
        issues.add(IconDetector.ICON_LOCATION);
        issues.add(IconDetector.DUPLICATES_NAMES);
        issues.add(IconDetector.DUPLICATES_CONFIGURATIONS);
        issues.add(IconDetector.ICON_NODPI);
        issues.add(IconDetector.ICON_MIX_9PNG);
        issues.add(IconDetector.ICON_EXTENSION);
        issues.add(IconDetector.ICON_COLORS);
        issues.add(IconDetector.ICON_XML_AND_PNG);
        issues.add(IconDetector.ICON_LAUNCHER_SHAPE);
        issues.add(TypographyDetector.DASHES);
        issues.add(TypographyDetector.QUOTES);
        issues.add(TypographyDetector.FRACTIONS);
        issues.add(TypographyDetector.ELLIPSIS);
        issues.add(TypographyDetector.OTHER);
        issues.add(ButtonDetector.ORDER);
        issues.add(ButtonDetector.CASE);
        issues.add(ButtonDetector.BACK_BUTTON);
        issues.add(ButtonDetector.STYLE);
        issues.add(DetectMissingPrefix.MISSING_NAMESPACE);
        issues.add(OverdrawDetector.ISSUE);
        issues.add(StringFormatDetector.INVALID);
        issues.add(StringFormatDetector.ARG_COUNT);
        issues.add(StringFormatDetector.ARG_TYPES);
        issues.add(TypoDetector.ISSUE);
        issues.add(ViewTypeDetector.ISSUE);
        issues.add(ServiceCastDetector.ISSUE);
        issues.add(ParcelDetector.ISSUE);
        issues.add(WrongImportDetector.ISSUE);
        issues.add(WrongLocationDetector.ISSUE);
        issues.add(ViewConstructorDetector.ISSUE);
        issues.add(NamespaceDetector.CUSTOM_VIEW);
        issues.add(NamespaceDetector.UNUSED);
        issues.add(NamespaceDetector.TYPO);
        issues.add(NamespaceDetector.RES_AUTO);
        issues.add(AlwaysShowActionDetector.ISSUE);
        issues.add(TitleDetector.ISSUE);
        issues.add(ColorUsageDetector.ISSUE);
        issues.add(JavaPerformanceDetector.PAINT_ALLOC);
        issues.add(JavaPerformanceDetector.USE_VALUE_OF);
        issues.add(JavaPerformanceDetector.USE_SPARSE_ARRAY);
        issues.add(WakelockDetector.ISSUE);
        issues.add(CleanupDetector.RECYCLE_RESOURCE);
        issues.add(CleanupDetector.COMMIT_FRAGMENT);
        issues.add(SetJavaScriptEnabledDetector.ISSUE);
        issues.add(JavaScriptInterfaceDetector.ISSUE);
        issues.add(ToastDetector.ISSUE);
        issues.add(SharedPrefsDetector.ISSUE);
        issues.add(CutPasteDetector.ISSUE);
        issues.add(NonInternationalizedSmsDetector.ISSUE);
        issues.add(PrivateKeyDetector.ISSUE);
        issues.add(AnnotationDetector.ISSUE);
        issues.add(SystemPermissionsDetector.ISSUE);
        issues.add(RequiredAttributeDetector.ISSUE);
        issues.add(WrongCallDetector.ISSUE);
        issues.add(RtlDetector.COMPAT);
        issues.add(RtlDetector.ENABLED);
        issues.add(RtlDetector.USE_START);

        assert initialCapacity >= issues.size() : issues.size();

        sIssues = Collections.unmodifiableList(issues);

        // Check that ids are unique
        if (assertionsEnabled()) {
            Set<String> ids = new HashSet<String>();
            for (Issue issue : sIssues) {
                String id = issue.getId();
                assert !ids.contains(id) : "Duplicate id " + id; //$NON-NLS-1$
                ids.add(id);
            }
        }
    }

    /**
     * Constructs a new {@link BuiltinIssueRegistry}
     */
    public BuiltinIssueRegistry() {
    }

    @NonNull
    @Override
    public List<Issue> getIssues() {
        return sIssues;
    }

    private static Set<Issue> sAdtFixes;

    /**
     * Returns true if the given issue has an automatic IDE fix.
     *
     * @param tool the name of the tool to be checked
     * @param issue the issue to be checked
     * @return true if the given tool is known to have an automatic fix for the
     *         given issue
     */
    @Beta
    @SuppressWarnings("MethodMayBeStatic") // Intentionally instance method so it can be overridden
    public boolean hasAutoFix(String tool, Issue issue) {
        assert tool.equals("adt"); // This is not yet a generic facility;
        // the primary purpose right now is to allow for example the HTML report
        // to give a hint to the user that some fixes don't require manual work

        return getIssuesWithFixes().contains(issue);
    }

    private static Set<Issue> getIssuesWithFixes() {
        if (sAdtFixes == null) {
            sAdtFixes = Sets.newHashSetWithExpectedSize(25);
            sAdtFixes.add(InefficientWeightDetector.INEFFICIENT_WEIGHT);
            sAdtFixes.add(AccessibilityDetector.ISSUE);
            sAdtFixes.add(InefficientWeightDetector.BASELINE_WEIGHTS);
            sAdtFixes.add(HardcodedValuesDetector.ISSUE);
            sAdtFixes.add(UselessViewDetector.USELESS_LEAF);
            sAdtFixes.add(UselessViewDetector.USELESS_PARENT);
            sAdtFixes.add(PxUsageDetector.PX_ISSUE);
            sAdtFixes.add(TextFieldDetector.ISSUE);
            sAdtFixes.add(SecurityDetector.EXPORTED_SERVICE);
            sAdtFixes.add(DetectMissingPrefix.MISSING_NAMESPACE);
            sAdtFixes.add(ScrollViewChildDetector.ISSUE);
            sAdtFixes.add(ObsoleteLayoutParamsDetector.ISSUE);
            sAdtFixes.add(TypographyDetector.DASHES);
            sAdtFixes.add(TypographyDetector.ELLIPSIS);
            sAdtFixes.add(TypographyDetector.FRACTIONS);
            sAdtFixes.add(TypographyDetector.OTHER);
            sAdtFixes.add(TypographyDetector.QUOTES);
            sAdtFixes.add(UseCompoundDrawableDetector.ISSUE);
            sAdtFixes.add(ApiDetector.UNSUPPORTED);
            sAdtFixes.add(ApiDetector.INLINED);
            sAdtFixes.add(TypoDetector.ISSUE);
            sAdtFixes.add(ManifestDetector.ALLOW_BACKUP);
            sAdtFixes.add(MissingIdDetector.ISSUE);
            sAdtFixes.add(TranslationDetector.MISSING);
            sAdtFixes.add(DosLineEndingDetector.ISSUE);
        }

        return sAdtFixes;
    }

    /**
     * Reset the registry such that it recomputes its available issues.
     * <p>
     * NOTE: This is only intended for testing purposes.
     */
    @VisibleForTesting
    public static void reset() {
        IssueRegistry.reset();
    }
}
