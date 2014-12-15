package com.android.tests.dependencies;

import com.android.tests.dependencies.jar.StringHelper2;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class MainActivity extends Activity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        TextView tv = (TextView) findViewById(R.id.text);

        // use reflection since the jar project is in the packaging scope but not
        // the compile scope.
        try {
            Class<?> clazz = getClassLoader().loadClass("com.android.tests.dependencies.jar.StringHelper");
            Method getString = clazz.getMethod("getString", String.class);

            tv.setText((String) getString.invoke(null, "Foo"));
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public String getString2(String foo) {
        return StringHelper2.getString2(foo);
    }
}
