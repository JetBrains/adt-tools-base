package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Scope;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;

import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detector that looks for leaked credentials in strings.
 */
public class StringAuthLeakDetector extends Detector implements Detector.JavaPsiScanner {

    /** Looks for hidden code */
    public static final Issue AUTH_LEAK = Issue.create(
            "AuthLeak", "Code might contain an auth leak",
            "Strings in java apps can be discovered by decompiling apps, this lint check looks " +
            "for code which looks like it may contain an url with a username and password",
            Category.SECURITY, 6, Severity.WARNING,
            new Implementation(StringAuthLeakDetector.class, Scope.JAVA_FILE_SCOPE));

    @Nullable
    @Override
    public List<Class<? extends PsiElement>> getApplicablePsiTypes() {
        return Collections.<Class<? extends PsiElement>>singletonList(PsiLiteralExpression.class);
    }

    @Nullable
    @Override
    public JavaElementVisitor createPsiVisitor(@NonNull JavaContext context) {
        return new AuthLeakChecker(context);
    }

    private static class AuthLeakChecker extends JavaElementVisitor {
        private final static String LEGAL_CHARS = "([\\w_.!~*\'()%;&=+$,-]+)";      // From RFC 2396
        private final static Pattern AUTH_REGEXP =
                Pattern.compile("([\\w+.-]+)://" + LEGAL_CHARS + ':' + LEGAL_CHARS + '@' +
                        LEGAL_CHARS);

        private final JavaContext mContext;

        private AuthLeakChecker(JavaContext context) {
            mContext = context;
        }

        @Override
        public void visitLiteralExpression(PsiLiteralExpression node) {
            if (node.getValue() instanceof String) {
                Matcher matcher = AUTH_REGEXP.matcher((String)node.getValue());
                if (matcher.find()) {
                    String password = matcher.group(3);
                    if (password == null || (password.startsWith("%") && password.endsWith("s"))) {
                        return;
                    }
                    TextRange textRange = node.getTextRange();
                    Location location = mContext.getRangeLocation(node, matcher.start() + 1, node,
                            -(textRange.getLength() - matcher.end() - 1));
                    mContext.report(AUTH_LEAK, node, location, "Possible credential leak");
                }
            }
        }
    }
}
