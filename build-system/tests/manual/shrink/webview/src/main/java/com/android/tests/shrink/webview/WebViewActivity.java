package com.android.tests.shrink.webview;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;

public class WebViewActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.webview);

        WebView webview = (WebView) findViewById(R.id.webview);

        // Should mark R.drawable.used1 as used
        webview.loadUrl("file:///android_res/drawable/used1.xml");

        String html = "<html><img src=\"used2.xml\"/></html>";
        // This call should make me whitelist all strings
        webview.loadDataWithBaseURL("file:///android_res/drawable/", html, "text/html",
                "utf-8", null);

        // Should mark R.drawable.used1 as used
        webview.loadUrl("file:///android_res/raw/used_index.html");

    }
}
