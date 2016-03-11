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
import static com.android.SdkConstants.ATTR_HOST;
import static com.android.SdkConstants.ATTR_SCHEME;
import static com.android.SdkConstants.UTF_8;
import static com.android.xml.AndroidManifest.ATTRIBUTE_NAME;
import static com.android.xml.AndroidManifest.NODE_ACTION;
import static com.android.xml.AndroidManifest.NODE_CATEGORY;
import static com.android.xml.AndroidManifest.NODE_DATA;
import static com.android.xml.AndroidManifest.NODE_INTENT;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Check if the App Link which needs auto verification is correctly set.
 */
public class AppLinksAutoVerifyDetector extends Detector implements Detector.XmlScanner {

    private static final Implementation IMPLEMENTATION = new Implementation(
            AppLinksAutoVerifyDetector.class, Scope.MANIFEST_SCOPE);

    public static final Issue ISSUE_ERROR = Issue.create(
            "AppLinksAutoVerifyError", //$NON-NLS-1$
            "App Links Auto Verification Failure",
            "Ensures that app links are correctly set and associated with website.",
            Category.CORRECTNESS, 5, Severity.ERROR, IMPLEMENTATION)
            .addMoreInfo("https://g.co/appindexing/applinks").setEnabledByDefault(false);

    public static final Issue ISSUE_WARNING = Issue.create(
            "AppLinksAutoVerifyWarning", //$NON-NLS-1$
            "Potential App Links Auto Verification Failure",
            "Ensures that app links are correctly set and associated with website.",
            Category.CORRECTNESS, 5, Severity.WARNING, IMPLEMENTATION)
            .addMoreInfo("https://g.co/appindexing/applinks").setEnabledByDefault(false);

    private static final String ATTRIBUTE_AUTO_VERIFY = "autoVerify";
    private static final String JSON_RELATIVE_PATH = "/.well-known/assetlinks.json";

    @VisibleForTesting
    static final int STATUS_HTTP_CONNECT_FAIL = -1;
    @VisibleForTesting
    static final int STATUS_MALFORMED_URL = -2;
    @VisibleForTesting
    static final int STATUS_UNKNOWN_HOST = -3;
    @VisibleForTesting
    static final int STATUS_NOT_FOUND = -4;
    @VisibleForTesting
    static final int STATUS_WRONG_JSON_SYNTAX = -5;
    @VisibleForTesting
    static final int STATUS_JSON_PARSE_FAIL = -6;
    @VisibleForTesting
    static final int STATUS_HTTP_OK = 200;

    /* Maps website host url to a future task which will send HTTP request to fetch the JSON file
     * and also return the status code during the fetching process. */
    private final Map<String, Future<HttpResult>> mFutures = Maps.newHashMap();

    /* Maps website host url to host attribute in AndroidManifest.xml. */
    private final Map<String, Attr> mJsonHost = Maps.newHashMap();

    @Override
    public void visitDocument(@NonNull XmlContext context, @NonNull Document document) {

        // This check sends http request. Only done in batch mode.
        if (!context.getScope().contains(Scope.ALL_JAVA_FILES)) {
            return;
        }

        if (document.getDocumentElement() != null) {
            List<Element> intents = getTags(document.getDocumentElement(), NODE_INTENT);
            if (!needAutoVerification(intents)) {
                return;
            }

            for (Element intent : intents) {
                boolean actionView = hasNamedSubTag(intent, NODE_ACTION,
                        "android.intent.action.VIEW");
                boolean browsableCategory = hasNamedSubTag(intent, NODE_CATEGORY,
                        "android.intent.category.BROWSABLE");
                if (!actionView || !browsableCategory) {
                    continue;
                }
                mJsonHost.putAll(getJsonUrl(intent));
            }
        }

        Map<String, HttpResult> results = getJsonFileAsync();

        String packageName = context.getProject().getPackage();
        for (Map.Entry<String, HttpResult> result : results.entrySet()) {
            if (result.getValue() == null) {
                continue;
            }
            Attr host = mJsonHost.get(result.getKey());
            String jsonPath = result.getKey() + JSON_RELATIVE_PATH;
            switch (result.getValue().mStatus) {
                case STATUS_HTTP_OK:
                    List<String> packageNames = getPackageNameFromJson(
                            result.getValue().mJsonFile);
                    if (!packageNames.contains(packageName)) {
                        context.report(ISSUE_ERROR, host, context.getLocation(host), String.format(
                                "This host does not support app links to your app. Checks the Digital Asset Links JSON file: %s",
                                jsonPath));
                    }
                    break;
                case STATUS_HTTP_CONNECT_FAIL:
                    context.report(ISSUE_WARNING, host, context.getLocation(host),
                            String.format("Connection to Digital Asset Links JSON file %s fails",
                                    jsonPath));
                    break;
                case STATUS_MALFORMED_URL:
                    context.report(ISSUE_ERROR, host, context.getLocation(host), String.format(
                            "Malformed URL of Digital Asset Links JSON file: %s. An unknown protocol is specified",
                            jsonPath));
                    break;
                case STATUS_UNKNOWN_HOST:
                    context.report(ISSUE_WARNING, host, context.getLocation(host), String.format(
                            "Unknown host: %s. Check if the host exists, and check your network connection",
                            result.getKey()));
                    break;
                case STATUS_NOT_FOUND:
                    context.report(ISSUE_ERROR, host, context.getLocation(host), String.format(
                            "Digital Asset Links JSON file %s is not found on the host", jsonPath));
                    break;
                case STATUS_WRONG_JSON_SYNTAX:
                    context.report(ISSUE_ERROR, host, context.getLocation(host),
                            String.format("%s has incorrect JSON syntax", jsonPath));
                    break;
                case STATUS_JSON_PARSE_FAIL:
                    context.report(ISSUE_ERROR, host, context.getLocation(host),
                            String.format("Parsing JSON file %s fails", jsonPath));
                    break;
                default:
                    context.report(ISSUE_WARNING, host, context.getLocation(host), String.format(
                            "HTTP request for Digital Asset Links JSON file %1$s fails. HTTP response code: %2$s",
                            jsonPath, result.getValue().mStatus));
            }
        }
    }

    /**
     * Gets all the tag elements with a specific tag name, within a parent tag element.
     *
     * @param element The parent tag element.
     * @return List of tag elements found.
     */
    @NonNull
    private static List<Element> getTags(@NonNull Element element, @NonNull String tagName) {
        List<Element> tagList = Lists.newArrayList();
        if (element.getTagName().equalsIgnoreCase(tagName)) {
            tagList.add(element);
        } else {
            NodeList children = element.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child instanceof Element) {
                    tagList.addAll(getTags((Element) child, tagName));
                }
            }
        }
        return tagList;
    }

    /**
     * Checks if auto verification is needed. i.e. any intent tag element's autoVerify attribute is
     * set to true.
     *
     * @param intents The intent tag elements.
     * @return true if auto verification is needed.
     */
    private static boolean needAutoVerification(@NonNull List<Element> intents) {
        for (Element intent : intents) {
            if (intent.getAttributeNS(ANDROID_URI, ATTRIBUTE_AUTO_VERIFY).equals(SdkConstants.VALUE_TRUE)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if the element has a sub tag with specific name and specific name attribute.
     *
     * @param element       The tag element.
     * @param tagName       The name of the sub tag.
     * @param nameAttrValue The value of the name attribute.
     * @return If the element has such a sub tag.
     */
    private static boolean hasNamedSubTag(@NonNull Element element, @NonNull String tagName,
            @NonNull String nameAttrValue) {
        NodeList children = element.getElementsByTagName(tagName);
        for (int i = 0; i < children.getLength(); i++) {
            Element e = (Element) children.item(i);
            if (e.getAttributeNS(ANDROID_URI, ATTRIBUTE_NAME).equals(nameAttrValue)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets the urls of all the host from which Digital Asset Links JSON files will be fetched.
     *
     * @param intent The intent tag element.
     * @return List of JSON file urls.
     */
    @NonNull
    private static Map<String, Attr> getJsonUrl(@NonNull Element intent) {
        List<String> schemes = Lists.newArrayList();
        List<Attr> hosts = Lists.newArrayList();
        NodeList dataTags = intent.getElementsByTagName(NODE_DATA);
        for (int k = 0; k < dataTags.getLength(); k++) {
            Element dataTag = (Element) dataTags.item(k);
            String scheme = dataTag.getAttributeNS(ANDROID_URI, ATTR_SCHEME);
            if (scheme.equals("http") || scheme.equals("https")) {
                schemes.add(scheme);
            }
            if (dataTag.hasAttributeNS(ANDROID_URI, ATTR_HOST)) {
                hosts.add(dataTag.getAttributeNodeNS(ANDROID_URI, ATTR_HOST));
            }
        }
        Map<String, Attr> urls = Maps.newHashMap();
        for (String scheme : schemes) {
            for (Attr host : hosts) {
                urls.put(scheme + "://" + host.getValue(), host);
            }
        }
        return urls;
    }

    /* Normally null. Used for testing. */
    @Nullable
    @VisibleForTesting
    static Map<String, HttpResult> sMockData;

    /**
     * Gets all the Digital Asset Links JSON file asynchronously.
     *
     * @return The map between the host url and the HTTP result.
     */
    private Map<String, HttpResult> getJsonFileAsync() {
        if (sMockData != null) {
            return sMockData;
        }

        ExecutorService executorService = Executors.newCachedThreadPool();
        for (final Map.Entry<String, Attr> url : mJsonHost.entrySet()) {
            Future<HttpResult> future = executorService.submit(new Callable<HttpResult>() {
                @Override
                public HttpResult call() {
                    return getJson(url.getKey() + JSON_RELATIVE_PATH);
                }
            });
            mFutures.put(url.getKey(), future);
        }
        executorService.shutdown();

        Map<String, HttpResult> jsons = Maps.newHashMap();
        for (Map.Entry<String, Future<HttpResult>> future : mFutures.entrySet()) {
            try {
                jsons.put(future.getKey(), future.getValue().get());
            } catch (Exception e) {
                jsons.put(future.getKey(), null);
            }
        }
        return jsons;
    }

    /**
     * Gets the Digital Asset Links JSON file on the website host.
     *
     * @param url The URL of the host on which JSON file will be fetched.
     */
    @NonNull
    private static HttpResult getJson(@NonNull String url) {
        try {
            URL urlObj = new URL(url);
            HttpURLConnection connection = (HttpURLConnection) urlObj.openConnection();
            if (connection == null) {
                return new HttpResult(STATUS_HTTP_CONNECT_FAIL, null);
            }
            try {
                InputStream inputStream = connection.getInputStream();
                if (inputStream == null) {
                    return new HttpResult(connection.getResponseCode(), null);
                }
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(inputStream, UTF_8));
                try {
                    String line;
                    StringBuilder response = new StringBuilder();
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                        response.append('\n');
                    }

                    try {
                        JsonElement jsonFile = new JsonParser().parse(response.toString());
                        return new HttpResult(connection.getResponseCode(), jsonFile);
                    } catch (JsonSyntaxException e) {
                        return new HttpResult(STATUS_WRONG_JSON_SYNTAX, null);
                    } catch (RuntimeException e) {
                        return new HttpResult(STATUS_JSON_PARSE_FAIL, null);
                    }
                } finally {
                    reader.close();
                }
            } finally {
                connection.disconnect();
            }
        } catch (MalformedURLException e) {
            return new HttpResult(STATUS_MALFORMED_URL, null);
        } catch (UnknownHostException e) {
            return new HttpResult(STATUS_UNKNOWN_HOST, null);
        } catch (FileNotFoundException e) {
            return new HttpResult(STATUS_NOT_FOUND, null);
        } catch (IOException e) {
            return new HttpResult(STATUS_HTTP_CONNECT_FAIL, null);
        }
    }

    /**
     * Gets the package names of all the apps from the Digital Asset Links JSON file.
     *
     * @param element The JsonElement of the json file.
     * @return All the package names.
     */
    private static List<String> getPackageNameFromJson(JsonElement element) {
        List<String> packageNames = Lists.newArrayList();
        if (element instanceof JsonArray) {
            JsonArray jsonArray = (JsonArray) element;
            for (int i = 0; i < jsonArray.size(); i++) {
                JsonElement app = jsonArray.get(i);
                if (app instanceof JsonObject) {
                    JsonObject target = ((JsonObject) app).getAsJsonObject("target");
                    if (target != null) {
                        // Checks namespace to ensure it is an app statement.
                        JsonElement namespace = target.get("namespace");
                        JsonElement packageName = target.get("package_name");
                        if (namespace != null && namespace.getAsString().equals("android_app")
                                && packageName != null) {
                            packageNames.add(packageName.getAsString());
                        }
                    }
                }
            }
        }
        return packageNames;
    }

    /* For storing the result of getting Digital Asset Links Json File */
    @VisibleForTesting
    static final class HttpResult {

        /* HTTP response code or others errors related to HTTP connection, JSON file parsing. */
        private final int mStatus;
        private final JsonElement mJsonFile;

        @VisibleForTesting
        HttpResult(int status, JsonElement jsonFile) {
            mStatus = status;
            mJsonFile = jsonFile;
        }
    }
}
