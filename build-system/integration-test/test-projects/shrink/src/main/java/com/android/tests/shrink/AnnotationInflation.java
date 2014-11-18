package com.android.tests.shrink;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public class AnnotationInflation {
    public static View createView(Context context, Class<?> type, ViewGroup container) {
        Layout screen = type.getAnnotation(Layout.class);
        if (screen == null) {
            Layouts screens = type.getAnnotation(Layouts.class);
            if (screens == null) {
                return null;
            }
            View last = null;
            for (int id : screens.value()) {
                last = LayoutInflater.from(context).inflate(id, container, false);
            }
            return last;
        }
        int layout = screen.value();
        return LayoutInflater.from(context).inflate(layout, container, false);
    }
}
