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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.JavaParser.ResolvedClass;
import com.android.tools.lint.client.api.JavaParser.ResolvedMethod;
import com.android.tools.lint.client.api.JavaParser.ResolvedNode;
import com.android.tools.lint.client.api.JavaParser.TypeDescriptor;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Speed;

import java.util.Arrays;
import java.util.List;

import lombok.ast.AstVisitor;
import lombok.ast.Expression;
import lombok.ast.MethodInvocation;
import lombok.ast.StrictListAccessor;

public class SslCertificateSocketFactoryDetector extends Detector
        implements Detector.JavaScanner {

    private static final Implementation IMPLEMENTATION_JAVA = new Implementation(
            SslCertificateSocketFactoryDetector.class,
            Scope.JAVA_FILE_SCOPE);

    public static final Issue CREATE_SOCKET = Issue.create(
            "SSLCertificateSocketFactoryCreateSocket", //$NON-NLS-1$
            "Insecure call to `SSLCertificateSocketFactory.createSocket()`",
            "When `SSLCertificateSocketFactory.createSocket()` is called with an `InetAddress` " +
            "as the first parameter, TLS/SSL hostname verification is not performed, which " +
            "could result in insecure network traffic caused by trusting arbitrary " +
            "hostnames in TLS/SSL certificates presented by peers. In this case, developers " +
            "must ensure that the `InetAddress` is explicitly verified against the certificate " +
            "through other means, such as by calling " +
            "`SSLCertificateSocketFactory.getDefaultHostnameVerifier() to get a " +
            "`HostnameVerifier` and calling `HostnameVerifier.verify()`.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            IMPLEMENTATION_JAVA);

    public static final Issue GET_INSECURE = Issue.create(
            "SSLCertificateSocketFactoryGetInsecure", //$NON-NLS-1$
            "Call to `SSLCertificateSocketFactory.getInsecure()`",
            "The `SSLCertificateSocketFactory.getInsecure()` method returns " +
            "an SSLSocketFactory with all TLS/SSL security checks disabled, which " +
            "could result in insecure network traffic caused by trusting arbitrary " +
            "TLS/SSL certificates presented by peers. This method should be " +
            "avoided unless needed for a special circumstance such as " +
            "debugging. Instead, `SSLCertificateSocketFactory.getDefault()` " +
            "should be used.",
            Category.SECURITY,
            6,
            Severity.WARNING,
            IMPLEMENTATION_JAVA);

    private static final String INET_ADDRESS_CLASS =
            "java.net.InetAddress";

    private static final String SSL_CERTIFICATE_SOCKET_FACTORY_CLASS =
            "android.net.SSLCertificateSocketFactory";

    @NonNull
    @Override
    public Speed getSpeed() {
        return Speed.FAST;
    }

    // ---- Implements Detector.JavaScanner ----

    @Override
    public List<String> getApplicableMethodNames() {
        // Detect calls to potentially insecure SSLCertificateSocketFactory methods
        return Arrays.asList("createSocket", "getInsecure");
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @Nullable AstVisitor visitor,
            @NonNull MethodInvocation node) {
        ResolvedNode resolved = context.resolve(node);
        if (resolved instanceof ResolvedMethod) {
            String methodName = node.astName().astValue();
            ResolvedClass resolvedClass = ((ResolvedMethod) resolved).getContainingClass();
            if (resolvedClass.isSubclassOf(SSL_CERTIFICATE_SOCKET_FACTORY_CLASS, false)) {
                if ("createSocket".equals(methodName)) {
                    StrictListAccessor<Expression, MethodInvocation> argumentList =
                            node.astArguments();
                    if (argumentList != null) {
                        TypeDescriptor firstParameterType = context.getType(argumentList.first());
                        if (firstParameterType != null) {
                            ResolvedClass firstParameterClass = firstParameterType.getTypeClass();
                            if (firstParameterClass != null &&
                                    firstParameterClass.isSubclassOf(INET_ADDRESS_CLASS, false)) {
                                context.report(CREATE_SOCKET, node, context.getLocation(node),
                                        "Use of `SSLCertificateSocketFactory.createSocket()` " +
                                        "with an InetAddress parameter can cause insecure " +
                                        "network traffic due to trusting arbitrary hostnames in " +
                                        "TLS/SSL certificates presented by peers");
                            }
                        }
                    }
                } else if ("getInsecure".equals(methodName)) {
                    context.report(GET_INSECURE, node, context.getLocation(node),
                            "Use of `SSLCertificateSocketFactory.getInsecure()` can cause " +
                            "insecure network traffic due to trusting arbitrary TLS/SSL " +
                            "certificates presented by peers");
                }
            }
        }
    }
}
