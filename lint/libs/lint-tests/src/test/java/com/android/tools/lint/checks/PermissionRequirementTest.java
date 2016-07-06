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

package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.TAG_PERMISSION;
import static com.android.tools.lint.checks.PermissionRequirement.REVOCABLE_PERMISSION_NAMES;
import static com.android.tools.lint.checks.PermissionRequirement.isRevocableSystemPermission;
import static com.android.tools.lint.checks.SupportAnnotationDetector.PERMISSION_ANNOTATION;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.sdklib.AndroidVersion;
import com.android.testutils.SdkTestCase;
import com.android.testutils.TestUtils;
import com.android.tools.lint.checks.PermissionHolder.SetPermissionLookup;
import com.android.tools.lint.client.api.JavaParser.ResolvedAnnotation;
import com.android.utils.XmlUtils;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationParameterList;
import com.intellij.psi.PsiLiteral;
import com.intellij.psi.PsiNameValuePair;

import junit.framework.TestCase;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class PermissionRequirementTest extends TestCase {
    private static PsiAnnotation createAnnotation(
            @NonNull String name,
            // TODO: Put better mocks in here
            @NonNull ResolvedAnnotation.Value... values) {
        PsiAnnotation annotation = mock(PsiAnnotation.class);
        when(annotation.getQualifiedName()).thenReturn(name);

        PsiAnnotationParameterList parameterList = mock(PsiAnnotationParameterList.class);
        when(annotation.getParameterList()).thenReturn(parameterList);

        List<PsiNameValuePair> pairs = Lists.newArrayListWithCapacity(10);
        for (ResolvedAnnotation.Value value : values) {
            PsiNameValuePair pair = mock(PsiNameValuePair.class);
            when (pair.getName()).thenReturn(value.name);
            PsiLiteral literal = mock(PsiLiteral.class);
            when(literal.getValue()).thenReturn(value.value);
            when (pair.getValue()).thenReturn(literal);

            when(annotation.findAttributeValue(value.name)).thenReturn(literal);
            when(annotation.findDeclaredAttributeValue(value.name)).thenReturn(literal);
        }
        PsiNameValuePair[] attributes = pairs.toArray(PsiNameValuePair.EMPTY_ARRAY);
        when(parameterList.getAttributes()).thenReturn(attributes);

        return annotation;
    }

    public void testSingle() {
        ResolvedAnnotation.Value values = new ResolvedAnnotation.Value("value",
                "android.permission.ACCESS_FINE_LOCATION");
        Set<String> emptySet = Collections.emptySet();
        Set<String> fineSet = Collections.singleton("android.permission.ACCESS_FINE_LOCATION");
        PsiAnnotation annotation = createAnnotation(PERMISSION_ANNOTATION, values);
        PermissionRequirement req = PermissionRequirement.create(null, annotation);
        assertTrue(req.isRevocable(new SetPermissionLookup(emptySet)));

        assertFalse(req.isSatisfied(new SetPermissionLookup(emptySet)));
        assertFalse(req.isSatisfied(new SetPermissionLookup(Collections.singleton(""))));
        assertTrue(req.isSatisfied(new SetPermissionLookup(fineSet)));
        assertEquals("android.permission.ACCESS_FINE_LOCATION",
                req.describeMissingPermissions(new SetPermissionLookup(emptySet)));
        assertEquals(fineSet, req.getMissingPermissions(new SetPermissionLookup(emptySet)));
        assertEquals(emptySet, req.getMissingPermissions(new SetPermissionLookup(fineSet)));
        assertEquals(fineSet, req.getRevocablePermissions(new SetPermissionLookup(emptySet)));
        assertNull(req.getOperator());
        assertFalse(req.getChildren().iterator().hasNext());
    }

    public void testAny() {
        ResolvedAnnotation.Value values = new ResolvedAnnotation.Value("anyOf",
                new String[]{"android.permission.ACCESS_FINE_LOCATION",
                        "android.permission.ACCESS_COARSE_LOCATION"});
        Set<String> emptySet = Collections.emptySet();
        Set<String> fineSet = Collections.singleton("android.permission.ACCESS_FINE_LOCATION");
        Set<String> coarseSet = Collections.singleton("android.permission.ACCESS_COARSE_LOCATION");
        Set<String> bothSet = Sets.newHashSet(
                "android.permission.ACCESS_FINE_LOCATION",
                "android.permission.ACCESS_COARSE_LOCATION");

        PsiAnnotation annotation = createAnnotation(PERMISSION_ANNOTATION, values);
        PermissionRequirement req = PermissionRequirement.create(null, annotation);
        assertTrue(req.isRevocable(new SetPermissionLookup(emptySet)));
        assertFalse(req.isSatisfied(new SetPermissionLookup(emptySet)));
        assertFalse(req.isSatisfied(new SetPermissionLookup(Collections.singleton(""))));
        assertTrue(req.isSatisfied(new SetPermissionLookup(fineSet)));
        assertTrue(req.isSatisfied(new SetPermissionLookup(coarseSet)));
        assertEquals(
                "android.permission.ACCESS_FINE_LOCATION or android.permission.ACCESS_COARSE_LOCATION",
                req.describeMissingPermissions(new SetPermissionLookup(emptySet)));
        assertEquals(bothSet, req.getMissingPermissions(new SetPermissionLookup(emptySet)));
        assertEquals(bothSet, req.getRevocablePermissions(new SetPermissionLookup(emptySet)));
        assertSame(JavaTokenType.OROR, req.getOperator());
    }

    public void testAll() {
        ResolvedAnnotation.Value values = new ResolvedAnnotation.Value("allOf",
                new String[]{"android.permission.ACCESS_FINE_LOCATION",
                        "android.permission.ACCESS_COARSE_LOCATION"});
        Set<String> emptySet = Collections.emptySet();
        Set<String> fineSet = Collections.singleton("android.permission.ACCESS_FINE_LOCATION");
        Set<String> coarseSet = Collections.singleton("android.permission.ACCESS_COARSE_LOCATION");
        Set<String> bothSet = Sets.newHashSet(
                "android.permission.ACCESS_FINE_LOCATION",
                "android.permission.ACCESS_COARSE_LOCATION");

        PsiAnnotation annotation = createAnnotation(PERMISSION_ANNOTATION, values);
        PermissionRequirement req = PermissionRequirement.create(null, annotation);
        assertTrue(req.isRevocable(new SetPermissionLookup(emptySet)));
        assertFalse(req.isSatisfied(new SetPermissionLookup(emptySet)));
        assertFalse(req.isSatisfied(new SetPermissionLookup(Collections.singleton(""))));
        assertFalse(req.isSatisfied(new SetPermissionLookup(fineSet)));
        assertFalse(req.isSatisfied(new SetPermissionLookup(coarseSet)));
        assertTrue(req.isSatisfied(new SetPermissionLookup(bothSet)));
        assertEquals(
                "android.permission.ACCESS_FINE_LOCATION and android.permission.ACCESS_COARSE_LOCATION",
                req.describeMissingPermissions(new SetPermissionLookup(emptySet)));
        assertEquals(bothSet, req.getMissingPermissions(new SetPermissionLookup(emptySet)));
        assertEquals(
                "android.permission.ACCESS_COARSE_LOCATION",
                req.describeMissingPermissions(new SetPermissionLookup(fineSet)));
        assertEquals(coarseSet, req.getMissingPermissions(new SetPermissionLookup(fineSet)));
        assertEquals(
                "android.permission.ACCESS_FINE_LOCATION",
                req.describeMissingPermissions(new SetPermissionLookup(coarseSet)));
        assertEquals(fineSet, req.getMissingPermissions(new SetPermissionLookup(coarseSet)));
        assertEquals(bothSet, req.getRevocablePermissions(new SetPermissionLookup(emptySet)));
        assertSame(JavaTokenType.ANDAND, req.getOperator());
    }

    public void testSingleAsArray() {
        // Annotations let you supply a single string to an array method
        ResolvedAnnotation.Value values = new ResolvedAnnotation.Value("allOf",
                "android.permission.ACCESS_FINE_LOCATION");
        PsiAnnotation annotation = createAnnotation(PERMISSION_ANNOTATION, values);
        assertTrue(PermissionRequirement.create(null, annotation).isSingle());
    }

    public void testRevocable() {
        assertTrue(isRevocableSystemPermission("android.permission.ACCESS_FINE_LOCATION"));
        assertTrue(isRevocableSystemPermission("android.permission.ACCESS_COARSE_LOCATION"));
        assertFalse(isRevocableSystemPermission("android.permission.UNKNOWN_PERMISSION_NAME"));
    }

    public void testRevocable2() {
        assertTrue(new SetPermissionLookup(Collections.<String>emptySet(),
            Sets.newHashSet("my.permission1", "my.permission2")).isRevocable("my.permission2"));
    }

    public void testAppliesTo() {
        PsiAnnotation annotation;
        PermissionRequirement req;

        // No date range applies to permission
        annotation = createAnnotation(PERMISSION_ANNOTATION,
                new ResolvedAnnotation.Value("value", "android.permission.AUTHENTICATE_ACCOUNTS"));
        req = PermissionRequirement.create(null, annotation);
        assertTrue(req.appliesTo(getHolder(15, 1)));
        assertTrue(req.appliesTo(getHolder(15, 19)));
        assertTrue(req.appliesTo(getHolder(15, 23)));
        assertTrue(req.appliesTo(getHolder(22, 23)));
        assertTrue(req.appliesTo(getHolder(23, 23)));

        // Permission discontinued in API 23:
        annotation = createAnnotation(PERMISSION_ANNOTATION,
                new ResolvedAnnotation.Value("value", "android.permission.AUTHENTICATE_ACCOUNTS"),
                new ResolvedAnnotation.Value("apis", "..22"));
        req = PermissionRequirement.create(null, annotation);
        assertTrue(req.appliesTo(getHolder(15, 1)));
        assertTrue(req.appliesTo(getHolder(15, 19)));
        assertTrue(req.appliesTo(getHolder(15, 23)));
        assertTrue(req.appliesTo(getHolder(22, 23)));
        assertFalse(req.appliesTo(getHolder(23, 23)));

        // Permission requirement started in API 23
        annotation = createAnnotation(PERMISSION_ANNOTATION,
                new ResolvedAnnotation.Value("value", "android.permission.AUTHENTICATE_ACCOUNTS"),
                new ResolvedAnnotation.Value("apis", "23.."));
        req = PermissionRequirement.create(null, annotation);
        assertFalse(req.appliesTo(getHolder(15, 1)));
        assertFalse(req.appliesTo(getHolder(1, 19)));
        assertFalse(req.appliesTo(getHolder(15, 22)));
        assertTrue(req.appliesTo(getHolder(22, 23)));
        assertTrue(req.appliesTo(getHolder(23, 30)));

        // Permission requirement applied from API 14 through API 18
        annotation = createAnnotation(PERMISSION_ANNOTATION,
                new ResolvedAnnotation.Value("value", "android.permission.AUTHENTICATE_ACCOUNTS"),
                new ResolvedAnnotation.Value("apis", "14..18"));
        req = PermissionRequirement.create(null, annotation);
        assertFalse(req.appliesTo(getHolder(1, 5)));
        assertTrue(req.appliesTo(getHolder(15, 19)));
    }

    private static PermissionHolder getHolder(int min, int target) {
        return new PermissionHolder.SetPermissionLookup(Collections.<String>emptySet(),
                Collections.<String>emptySet(), new AndroidVersion(min, null),
                new AndroidVersion(target, null));
    }

    public void testDangerousPermissionsOrder() {
        // The order must be alphabetical to ensure binary search will work correctly
        String prev = null;
        for (String permission : REVOCABLE_PERMISSION_NAMES) {
            assertTrue(prev == null || prev.compareTo(permission) < 0);
            prev = permission;
        }
    }

    public static void testDbUpToDate() throws Exception {
        List<String> expected = getDangerousPermissions();
        if (expected == null) {
            return;
        }
        List<String> actual = Arrays.asList(REVOCABLE_PERMISSION_NAMES);
        if (!expected.equals(actual)) {
            System.out.println("Correct list of permissions:");
            for (String name : expected) {
                System.out.println("            \"" + name + "\",");
            }
            fail("List of revocable permissions has changed:\n" +
                // Make the diff show what it take to bring the actual results into the
                // expected results
                TestUtils.getDiff(Joiner.on('\n').join(actual),
                    Joiner.on('\n').join(expected)));
        }
    }

    @Nullable
    private static List<String> getDangerousPermissions() throws IOException {
        Pattern pattern = Pattern.compile("dangerous");
        String top = System.getenv("ANDROID_BUILD_TOP");   //$NON-NLS-1$

        // Alternatively, you can look up the version for the release branch on ag via
        // something like
        //   platform/frameworks/base/+/nyc-release/core/res/AndroidManifest.xml
        // and then set file = new File(path to copy of above)
        // TODO: We should ship this file with the SDK!
        File file = new File(top, "frameworks/base/core/res/AndroidManifest.xml");
        if (!file.exists()) {
            System.out.println("Set $ANDROID_BUILD_TOP to point to the git repository");
            return null;
        }
        boolean passedRuntimeHeader = false;
        boolean passedInstallHeader = false;
        String xml = Files.toString(file, Charsets.UTF_8);
        Document document = XmlUtils.parseDocumentSilently(xml, true);
        Set<String> revocable = Sets.newHashSet();
        if (document != null && document.getDocumentElement() != null) {
            NodeList children = document.getDocumentElement().getChildNodes();
            for (int i = 0, n = children.getLength(); i < n; i++) {
                Node child = children.item(i);
                short nodeType = child.getNodeType();
                if (nodeType == Node.COMMENT_NODE) {
                    String comment = child.getNodeValue();
                    if (comment.contains("RUNTIME PERMISSIONS")) {
                        passedRuntimeHeader = true;
                    } else if (comment.contains("INSTALLTIME PERMISSIONS"))
                        passedInstallHeader = true;
                } else if (passedRuntimeHeader
                        && !passedInstallHeader
                        && nodeType == Node.ELEMENT_NODE
                        && child.getNodeName().equals(TAG_PERMISSION)) {
                    Element element = (Element) child;
                    String protectionLevel = element.getAttributeNS(ANDROID_URI, "protectionLevel");
                    String name = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
                    if (!name.isEmpty() && pattern.matcher(protectionLevel).find()) {
                        revocable.add(name);
                    } else {
                        // Some permissions have been removed. We need to know about the historical
                        // data for these. Whenever a new annotation i
                        if (name.equals("android.permission.READ_SOCIAL_STREAM") ||
                            name.equals("android.permission.WRITE_SOCIAL_STREAM") ||
                            name.equals("android.permission.READ_PROFILE") ||
                            name.equals("android.permission.WRITE_PROFILE")) {
                            // Removed in 6d2c0e5
                            revocable.add(name);
                        } else if (name.equals("android.permission.USE_FINGERPRINT")) {
                            // Made normal in d6bd9da; apply targetSdkVersion?
                            // (It's not clear whether app developers have to worry about
                            // this in M. Find out.)
                            revocable.add(name);
                        } else if (name.equals("android.permission.WRITE_SETTINGS")) {
                            // This one seems to have gone from a dangerous permission
                            // in M to a signature permission
                            revocable.add(name);

                        }
                    }
                }
            }
        }

        List<String> expected = Lists.newArrayList(revocable);
        Collections.sort(expected);
        return expected;
    }
}