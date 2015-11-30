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
import static com.android.SdkConstants.ATTR_EXPORTED;
import static com.android.SdkConstants.ATTR_HOST;
import static com.android.SdkConstants.ATTR_PATH;
import static com.android.SdkConstants.ATTR_PATH_PREFIX;
import static com.android.SdkConstants.ATTR_SCHEME;
import static com.android.SdkConstants.CLASS_ACTIVITY;
import static com.android.xml.AndroidManifest.ATTRIBUTE_MIME_TYPE;
import static com.android.xml.AndroidManifest.ATTRIBUTE_NAME;
import static com.android.xml.AndroidManifest.ATTRIBUTE_PORT;
import static com.android.xml.AndroidManifest.NODE_ACTION;
import static com.android.xml.AndroidManifest.NODE_ACTIVITY;
import static com.android.xml.AndroidManifest.NODE_APPLICATION;
import static com.android.xml.AndroidManifest.NODE_CATEGORY;
import static com.android.xml.AndroidManifest.NODE_DATA;
import static com.android.xml.AndroidManifest.NODE_INTENT;
import static com.android.xml.AndroidManifest.NODE_MANIFEST;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.res2.AbstractResourceRepository;
import com.android.ide.common.res2.ResourceItem;
import com.android.ide.common.resources.ResourceUrl;
import com.android.resources.ResourceType;
import com.android.tools.lint.client.api.JavaParser;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.client.api.XmlParser;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import lombok.ast.ClassDeclaration;
import lombok.ast.Expression;
import lombok.ast.ForwardingAstVisitor;
import lombok.ast.MethodInvocation;


/**
 * Check if the usage of App Indexing is correct.
 */
public class AppIndexingApiDetector extends Detector
        implements Detector.XmlScanner, Detector.JavaScanner {

    private static final Implementation DEEP_LINK_IMPLEMENTATION = new Implementation(
            AppIndexingApiDetector.class, Scope.MANIFEST_SCOPE);

    private static final Implementation APP_INDEXING_API_IMPLEMENTATION =
            new Implementation(
                    AppIndexingApiDetector.class,
                    EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST),
                    Scope.JAVA_FILE_SCOPE, Scope.MANIFEST_SCOPE);

    public static final Issue ISSUE_DEEP_LINK_ERROR = Issue.create(
            "GoogleAppIndexingDeepLinkError", //$NON-NLS-1$
            "Deep link not supported by app for Google App Indexing",
            "Ensure the deep link is supported by your app, to get installs and traffic to your"
                    + "app from Google Search.",
            Category.USABILITY, 5, Severity.ERROR, DEEP_LINK_IMPLEMENTATION)
            .addMoreInfo("https://g.co/AppIndexing/AndroidStudio");

    public static final Issue ISSUE_APP_INDEXING =
      Issue.create(
        "GoogleAppIndexingWarning", //$NON-NLS-1$
        "Missing support for Google App Indexing",
        "Adds deep links to get your app into the Google index, to get installs"
          + " and traffic to your app from Google Search.",
        Category.USABILITY, 5, Severity.WARNING, DEEP_LINK_IMPLEMENTATION)
        .addMoreInfo("https://g.co/AppIndexing/AndroidStudio");

    public static final Issue ISSUE_APP_INDEXING_API =
            Issue.create(
                    "GoogleAppIndexingApiWarning", //$NON-NLS-1$
                    "Missing support for Google App Indexing Api",
                    "Adds deep links to get your app into the Google index, to get installs"
                            + " and traffic to your app from Google Search.",
                    Category.USABILITY, 5, Severity.WARNING, APP_INDEXING_API_IMPLEMENTATION)
                    .addMoreInfo("https://g.co/AppIndexing/AndroidStudio")
                    .setEnabledByDefault(false);

    private static final String[] PATH_ATTR_LIST = new String[]{ATTR_PATH_PREFIX, ATTR_PATH};
    private static final String SCHEME_MISSING = "android:scheme is missing";
    private static final String HOST_MISSING = "android:host is missing";
    private static final String DATA_MISSING = "Missing data element";
    private static final String URL_MISSING = "Missing URL for the intent filter";
    private static final String NOT_BROWSABLE
            = "Activity supporting ACTION_VIEW is not set as BROWSABLE";
    private static final String ILLEGAL_NUMBER = "android:port is not a legal number";

    private static final String APP_INDEX_START = "start"; //$NON-NLS-1$
    private static final String APP_INDEX_END = "end"; //$NON-NLS-1$
    private static final String APP_INDEX_VIEW = "view"; //$NON-NLS-1$
    private static final String APP_INDEX_VIEW_END = "viewEnd"; //$NON-NLS-1$
    private static final String CLIENT_CONNECT = "connect"; //$NON-NLS-1$
    private static final String CLIENT_DISCONNECT = "disconnect"; //$NON-NLS-1$
    private static final String ADD_API = "addApi"; //$NON-NLS-1$

    private static final String APP_INDEXING_API_CLASS
            = "com.google.android.gms.appindexing.AppIndexApi";
    private static final String GOOGLE_API_CLIENT_CLASS
            = "com.google.android.gms.common.api.GoogleApiClient";
    private static final String GOOGLE_API_CLIENT_BUILDER_CLASS
            = "com.google.android.gms.common.api.GoogleApiClient.Builder";
    private static final String API_CLASS = "com.google.android.gms.appindexing.AppIndex";

    public enum IssueType {
        SCHEME_MISSING(AppIndexingApiDetector.SCHEME_MISSING),
        HOST_MISSING(AppIndexingApiDetector.HOST_MISSING),
        DATA_MISSING(AppIndexingApiDetector.DATA_MISSING),
        URL_MISSING(AppIndexingApiDetector.URL_MISSING),
        NOT_BROWSABLE(AppIndexingApiDetector.NOT_BROWSABLE),
        ILLEGAL_NUMBER(AppIndexingApiDetector.ILLEGAL_NUMBER),
        EMPTY_FIELD("cannot be empty"),
        MISSING_SLASH("attribute should start with '/'"),
        UNKNOWN("unknown error type");

        private String message;

        IssueType(String str) {
            this.message = str;
        }

        public static IssueType parse(String str) {
            for (IssueType type : IssueType.values()) {
                if (str.contains(type.message)) {
                    return type;
                }
            }
            return UNKNOWN;
        }
    }

    // ---- Implements XmlScanner ----
    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(NODE_APPLICATION);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element application) {
        List<Element> activities = extractChildrenByName(application, NODE_ACTIVITY);
        boolean applicationHasActionView = false;
        for (Element activity : activities) {
            List<Element> intents = extractChildrenByName(activity, NODE_INTENT);
            boolean activityHasActionView = false;
            for (Element intent : intents) {
                boolean actionView = hasActionView(intent);
                if (actionView) {
                    activityHasActionView = true;
                }
                visitIntent(context, intent);
            }
            if (activityHasActionView) {
                applicationHasActionView = true;
                if (activity.hasAttributeNS(ANDROID_URI, ATTR_EXPORTED)) {
                    Attr exported = activity.getAttributeNodeNS(ANDROID_URI, ATTR_EXPORTED);
                    if (!exported.getValue().equals("true")) {
                        // Report error if the activity supporting action view is not exported.
                        context.report(ISSUE_DEEP_LINK_ERROR, activity,
                                       context.getLocation(activity),
                                       "Activity supporting ACTION_VIEW is not exported");
                    }
                }
            }
        }
        if (!applicationHasActionView) {
            // Report warning if there is no activity that supports action view.
            context.report(ISSUE_APP_INDEXING, application, context.getLocation(application),
                           // This error message is more verbose than the other app indexing lint warnings, because it
                           // shows up on a blank project, and we want to make it obvious by just looking at the error
                           // message what this is
                           "App is not indexable by Google Search; consider adding at least one Activity with an ACTION-VIEW " +
                           "intent-filler. See issue explanation for more details.");
        }
    }

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(CLASS_ACTIVITY);
    }

    @Override
    public void checkClass(@NonNull JavaContext javaContext,
                           @Nullable ClassDeclaration node,
                           @NonNull lombok.ast.Node declarationOrAnonymous,
                           @NonNull JavaParser.ResolvedClass cls) {
        if (node == null) {
            return;
        }

        // In case linting the base class itself.
        if (!cls.isInheritingFrom(CLASS_ACTIVITY, true)) {
            return;
        }

        node.accept(new MethodVisitor(javaContext));
    }

    static class MethodVisitor extends ForwardingAstVisitor {
        private final JavaContext mJavaContext;
        private List<MethodInvocation> mStartMethods;
        private List<MethodInvocation> mEndMethods;
        private List<MethodInvocation> mConnectMethods;
        private List<MethodInvocation> mDisconnectMethods;
        private boolean mHasAddAppIndexApi;

        MethodVisitor(JavaContext javaContext) {
            mJavaContext = javaContext;
            mStartMethods = Lists.newArrayListWithExpectedSize(2);
            mEndMethods = Lists.newArrayListWithExpectedSize(2);
            mConnectMethods = Lists.newArrayListWithExpectedSize(2);
            mDisconnectMethods = Lists.newArrayListWithExpectedSize(2);
        }

        @Override
        public boolean visitMethodInvocation(MethodInvocation node) {
            JavaParser.ResolvedNode resolved = mJavaContext.resolve(node);
            if (!(resolved instanceof JavaParser.ResolvedMethod)) {
                return super.visitMethodInvocation(node);
            }
            JavaParser.ResolvedMethod method = (JavaParser.ResolvedMethod) resolved;
            String methodName = node.astName().astValue();

            if (methodName.equals(APP_INDEX_START)) {
                if (method.getContainingClass().getName().equals(APP_INDEXING_API_CLASS)) {
                    mStartMethods.add(node);
                }
            } else if (methodName.equals(APP_INDEX_END)) {
                if (method.getContainingClass().getName().equals(APP_INDEXING_API_CLASS)) {
                    mEndMethods.add(node);
                }
            } else if (methodName.equals(APP_INDEX_VIEW)) {
                if (method.getContainingClass().getName().equals(APP_INDEXING_API_CLASS)) {
                    mStartMethods.add(node);
                }
            } else if (methodName.equals(APP_INDEX_VIEW_END)) {
                if (method.getContainingClass().getName().equals(APP_INDEXING_API_CLASS)) {
                    mEndMethods.add(node);
                }
            } else if (methodName.equals(CLIENT_CONNECT)) {
                if (method.getContainingClass().getName().equals(GOOGLE_API_CLIENT_CLASS)) {
                    mConnectMethods.add(node);
                }
            } else if (methodName.equals(CLIENT_DISCONNECT)) {
                if (method.getContainingClass().getName().equals(GOOGLE_API_CLIENT_CLASS)) {
                    mDisconnectMethods.add(node);
                }
            } else if (methodName.equals(ADD_API)) {
                if (method.getContainingClass().getName().equals(GOOGLE_API_CLIENT_BUILDER_CLASS)) {
                    JavaParser.ResolvedNode arg0 = mJavaContext
                            .resolve(node.astArguments().first());
                    if (arg0 instanceof JavaParser.ResolvedField) {
                        JavaParser.ResolvedField resolvedArg0 = (JavaParser.ResolvedField) arg0;
                        if (resolvedArg0.getContainingClass().getName().equals(API_CLASS)) {
                            mHasAddAppIndexApi = true;
                        }
                    }
                }
            }
            return super.visitMethodInvocation(node);
        }

        @Override
        public void endVisit(lombok.ast.Node root){
            if (!(root instanceof ClassDeclaration)) {
                return;
            }
            ClassDeclaration node = (ClassDeclaration)root;
            JavaParser.ResolvedNode resolvedNode = mJavaContext.resolve(node);
            if (resolvedNode == null || !(resolvedNode instanceof JavaParser.ResolvedClass)) {
                return;
            }

            if (!((JavaParser.ResolvedClass)resolvedNode).isInheritingFrom(CLASS_ACTIVITY, true)) {
                return;
            }

            // finds the activity classes that need app activity annotation
            Set<String> activitiesToCheck = getActivitiesToCheck(mJavaContext);

            // app indexing API used but no support in manifest
            boolean hasIntent = activitiesToCheck.contains(resolvedNode.getName());
            if (!hasIntent) {
                for (MethodInvocation method : mStartMethods) {
                    mJavaContext.report(ISSUE_APP_INDEXING_API, method,
                            mJavaContext.getLocation(method.astName()),
                            "Missing support for Google App Indexing in the manifest");
                }
                for (MethodInvocation method : mEndMethods) {
                    mJavaContext.report(ISSUE_APP_INDEXING_API, method,
                            mJavaContext.getLocation(method.astName()),
                            "Missing support for Google App Indexing in the manifest");
                }
                return;
            }

            // `AppIndex.AppIndexApi.start / end / view / viewEnd` should exist
            if (mStartMethods.isEmpty() && mEndMethods.isEmpty()) {
                mJavaContext.report(ISSUE_APP_INDEXING_API, node,
                        mJavaContext.getLocation(node.astName()),
                        "Missing support for Google App Indexing API");
                return;
            }

            for (MethodInvocation startNode : mStartMethods) {
                Expression startClient = startNode.astArguments().first();

                // GoogleApiClient should `addApi(AppIndex.APP_INDEX_API)`
                if (!mHasAddAppIndexApi) {
                    String message = String.format(
                            "GoogleApiClient `%1$s` has not added support for App Indexing API",
                            startClient.toString());
                    mJavaContext.report(ISSUE_APP_INDEXING_API, startClient,
                            mJavaContext.getLocation(startClient), message);
                }

                // GoogleApiClient `connect` should exist
                if (!hasOperand(startClient, mConnectMethods)) {
                    String message = String
                            .format("GoogleApiClient `%1$s` is not connected", startClient.toString());
                    mJavaContext.report(ISSUE_APP_INDEXING_API, startClient,
                            mJavaContext.getLocation(startClient), message);
                }

                // `AppIndex.AppIndexApi.end` should pair with `AppIndex.AppIndexApi.start`
                if (!hasFirstArgument(startClient, mEndMethods)) {
                    mJavaContext.report(ISSUE_APP_INDEXING_API, startNode,
                            mJavaContext.getLocation(startNode.astName()),
                            "Missing corresponding `AppIndex.AppIndexApi.end` method");
                }
            }

            for (MethodInvocation endNode : mEndMethods) {
                Expression endClient = endNode.astArguments().first();

                // GoogleApiClient should `addApi(AppIndex.APP_INDEX_API)`
                if (!mHasAddAppIndexApi) {
                    String message = String.format(
                            "GoogleApiClient `%1$s` has not added support for App Indexing API",
                            endClient.toString());
                    mJavaContext.report(ISSUE_APP_INDEXING_API, endClient,
                            mJavaContext.getLocation(endClient), message);
                }

                // GoogleApiClient `disconnect` should exist
                if (!hasOperand(endClient, mDisconnectMethods)) {
                    String message = String.format("GoogleApiClient `%1$s`"
                            + " is not disconnected", endClient.toString());
                    mJavaContext.report(ISSUE_APP_INDEXING_API, endClient,
                            mJavaContext.getLocation(endClient), message);
                }

                // `AppIndex.AppIndexApi.start` should pair with `AppIndex.AppIndexApi.end`
                if (!hasFirstArgument(endClient, mStartMethods)) {
                    mJavaContext.report(ISSUE_APP_INDEXING_API, endNode,
                            mJavaContext.getLocation(endNode.astName()),
                            "Missing corresponding `AppIndex.AppIndexApi.start` method");
                }
            }
        }
    }

    /**
     * Gets names of activities which needs app indexing. i.e. the activities have data tag in their
     * intent filters.
     * TODO: Cache the activities to speed up batch lint.
     *
     * @param context The context to check in.
     */
    private static Set<String> getActivitiesToCheck(Context context) {
        Set<String> activitiesToCheck = Sets.newHashSet();
        List<File> manifestFiles = context.getProject().getManifestFiles();
        XmlParser xmlParser = context.getDriver().getClient().getXmlParser();
        if (xmlParser != null) {
            // TODO: Avoid visit all manifest files before enable this check by default.
            for (File manifest : manifestFiles) {
                XmlContext xmlContext =
                        new XmlContext(context.getDriver(), context.getProject(),
                                null, manifest, null, xmlParser);
                Document doc = xmlParser.parseXml(xmlContext);
                if (doc != null) {
                    List<Element> children = LintUtils.getChildren(doc);
                    for (Element child : children) {
                        if (child.getNodeName().equals(NODE_MANIFEST)) {
                            List<Element> apps = extractChildrenByName(child, NODE_APPLICATION);
                            for (Element app : apps) {
                                List<Element> acts = extractChildrenByName(app, NODE_ACTIVITY);
                                for (Element act : acts) {
                                    List<Element> intents = extractChildrenByName(act, NODE_INTENT);
                                    for (Element intent : intents) {
                                        List<Element> data = extractChildrenByName(intent,
                                                NODE_DATA);
                                        if (!data.isEmpty() && act.hasAttributeNS(
                                                ANDROID_URI, ATTRIBUTE_NAME)) {
                                            Attr attr = act.getAttributeNodeNS(
                                                    ANDROID_URI, ATTRIBUTE_NAME);
                                            String activityName = attr.getValue();
                                            int dotIndex = activityName.indexOf('.');
                                            if (dotIndex <= 0) {
                                                String pkg = context.getMainProject().getPackage();
                                                if (pkg != null) {
                                                    if (dotIndex == 0) {
                                                        activityName = pkg + activityName;
                                                    }
                                                    else {
                                                        activityName = pkg + '.' + activityName;
                                                    }
                                                }
                                            }
                                            activitiesToCheck.add(activityName);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return activitiesToCheck;
    }

    private static void visitIntent(@NonNull XmlContext context, @NonNull Element intent) {
        boolean actionView = hasActionView(intent);
        boolean browsable = isBrowsable(intent);
        boolean isHttp = false;
        boolean hasScheme = false;
        boolean hasHost = false;
        boolean hasPort = false;
        boolean hasPath = false;
        boolean hasMimeType = false;
        Element firstData = null;
        List<Element> children = extractChildrenByName(intent, NODE_DATA);
        for (Element data : children) {
            if (firstData == null) {
                firstData = data;
            }
            if (isHttpSchema(data)) {
                isHttp = true;
            }
            checkSingleData(context, data);

            for (String name : PATH_ATTR_LIST) {
                if (data.hasAttributeNS(ANDROID_URI, name)) {
                    hasPath = true;
                }
            }

            if (data.hasAttributeNS(ANDROID_URI, ATTR_SCHEME)) {
                hasScheme = true;
            }

            if (data.hasAttributeNS(ANDROID_URI, ATTR_HOST)) {
                hasHost = true;
            }

            if (data.hasAttributeNS(ANDROID_URI, ATTRIBUTE_PORT)) {
                hasPort = true;
            }

            if (data.hasAttributeNS(ANDROID_URI, ATTRIBUTE_MIME_TYPE)) {
                hasMimeType = true;
            }
        }

        // In data field, a URL is consisted by
        // <scheme>://<host>:<port>[<path>|<pathPrefix>|<pathPattern>]
        // Each part of the URL should not have illegal character.
        if ((hasPath || hasHost || hasPort) && !hasScheme) {
            context.report(ISSUE_DEEP_LINK_ERROR, firstData, context.getLocation(firstData),
                    SCHEME_MISSING);
        }

        if ((hasPath || hasPort) && !hasHost) {
            context.report(ISSUE_DEEP_LINK_ERROR, firstData, context.getLocation(firstData),
                    HOST_MISSING);
        }

        if (actionView && browsable) {
            if (firstData == null) {
                // If this activity is an ACTION_VIEW action with category BROWSABLE, but doesn't
                // have data node, it may be a mistake and we will report error.
                context.report(ISSUE_DEEP_LINK_ERROR, intent, context.getLocation(intent),
                        DATA_MISSING);
            } else if (!hasScheme && !hasMimeType) {
                // If this activity is an action view, is browsable, but has neither a
                // URL nor mimeType, it may be a mistake and we will report error.
                context.report(ISSUE_DEEP_LINK_ERROR, firstData, context.getLocation(firstData),
                        URL_MISSING);
            }
        }

        // If this activity is an ACTION_VIEW action, has a http URL but doesn't have
        // BROWSABLE, it may be a mistake and and we will report warning.
        if (actionView && isHttp && !browsable) {
            context.report(ISSUE_APP_INDEXING, intent, context.getLocation(intent),
                    NOT_BROWSABLE);
        }

        if (actionView && !hasScheme) {
            context.report(ISSUE_APP_INDEXING, intent, context.getLocation(intent),
                    "Missing deep link");
        }
    }

    /**
     * Check if the intent filter supports action view.
     *
     * @param intent the intent filter
     * @return true if it does
     */
    private static boolean hasActionView(@NonNull Element intent) {
        List<Element> children = extractChildrenByName(intent, NODE_ACTION);
        for (Element action : children) {
            if (action.hasAttributeNS(ANDROID_URI, ATTRIBUTE_NAME)) {
                Attr attr = action.getAttributeNodeNS(ANDROID_URI, ATTRIBUTE_NAME);
                if (attr.getValue().equals("android.intent.action.VIEW")) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Check if the intent filter is browsable.
     *
     * @param intent the intent filter
     * @return true if it does
     */
    private static boolean isBrowsable(@NonNull Element intent) {
        List<Element> children = extractChildrenByName(intent, NODE_CATEGORY);
        for (Element e : children) {
            if (e.hasAttributeNS(ANDROID_URI, ATTRIBUTE_NAME)) {
                Attr attr = e.getAttributeNodeNS(ANDROID_URI, ATTRIBUTE_NAME);
                if (attr.getNodeValue().equals("android.intent.category.BROWSABLE")) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Check if the data node contains http schema
     *
     * @param data the data node
     * @return true if it does
     */
    private static boolean isHttpSchema(@NonNull Element data) {
        if (data.hasAttributeNS(ANDROID_URI, ATTR_SCHEME)) {
            String value = data.getAttributeNodeNS(ANDROID_URI, ATTR_SCHEME).getValue();
            if (value.equalsIgnoreCase("http") || value.equalsIgnoreCase("https")) {
                return true;
            }
        }
        return false;
    }

    private static void checkSingleData(@NonNull XmlContext context, @NonNull Element data) {
        // path, pathPrefix and pathPattern should starts with /.
        for (String name : PATH_ATTR_LIST) {
            if (data.hasAttributeNS(ANDROID_URI, name)) {
                Attr attr = data.getAttributeNodeNS(ANDROID_URI, name);
                String path = replaceUrlWithValue(context, attr.getValue());
                if (!path.startsWith("/") && !path.startsWith(SdkConstants.PREFIX_RESOURCE_REF)) {
                    context.report(ISSUE_DEEP_LINK_ERROR, attr, context.getLocation(attr),
                            "android:" + name + " attribute should start with '/', but it is : "
                                    + path);
                }
            }
        }

        // port should be a legal number.
        if (data.hasAttributeNS(ANDROID_URI, ATTRIBUTE_PORT)) {
            Attr attr = data.getAttributeNodeNS(ANDROID_URI, ATTRIBUTE_PORT);
            try {
                String port = replaceUrlWithValue(context, attr.getValue());
                Integer.parseInt(port);
            } catch (NumberFormatException e) {
                context.report(ISSUE_DEEP_LINK_ERROR, attr, context.getLocation(attr),
                        ILLEGAL_NUMBER);
            }
        }

        // Each field should be non empty.
        NamedNodeMap attrs = data.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            Node item = attrs.item(i);
            if (item.getNodeType() == Node.ATTRIBUTE_NODE) {
                Attr attr = (Attr) attrs.item(i);
                if (attr.getValue().isEmpty()) {
                    context.report(ISSUE_DEEP_LINK_ERROR, attr, context.getLocation(attr),
                            attr.getName() + " cannot be empty");
                }
            }
        }
    }

    private static String replaceUrlWithValue(@NonNull XmlContext context,
            @NonNull String str) {
        Project project = context.getProject();
        LintClient client = context.getClient();
        if (!client.supportsProjectResources()) {
            return str;
        }
        ResourceUrl style = ResourceUrl.parse(str);
        if (style == null || style.type != ResourceType.STRING || style.framework) {
            return str;
        }
        AbstractResourceRepository resources = client.getProjectResources(project, true);
        if (resources == null) {
            return str;
        }
        List<ResourceItem> items = resources.getResourceItem(ResourceType.STRING, style.name);
        if (items == null || items.isEmpty()) {
            return str;
        }
        ResourceValue resourceValue = items.get(0).getResourceValue(false);
        if (resourceValue == null) {
            return str;
        }
        return resourceValue.getValue() == null ? str : resourceValue.getValue();
    }

    /**
     * If a method with a certain argument exists in the list of methods.
     *
     * @param argument The first argument of the method.
     * @param list     The methods list.
     * @return If such a method exists in the list.
     */
    private static boolean hasFirstArgument(Expression argument, List<MethodInvocation> list) {
        for (MethodInvocation method : list) {
            Expression argument1 = method.astArguments().first();
            if (argument.toString().equals(argument1.toString())) {
                return true;
            }
        }
        return false;
    }

    /**
     * If a method with a certain operand exists in the list of methods.
     *
     * @param operand The operand of the method.
     * @param list    The methods list.
     * @return If such a method exists in the list.
     */
    private static boolean hasOperand(Expression operand, List<MethodInvocation> list) {
        for (MethodInvocation method : list) {
            Expression operand1 = method.astOperand();
            if (operand.toString().equals(operand1.toString())) {
                return true;
            }
        }
        return false;
    }

    private static List<Element> extractChildrenByName(@NonNull Element node,
            @NonNull String name) {
        List<Element> result = Lists.newArrayList();
        List<Element> children = LintUtils.getChildren(node);
        for (Element child : children) {
            if (child.getNodeName().equals(name)) {
                result.add(child);
            }
        }
        return result;
    }
}
