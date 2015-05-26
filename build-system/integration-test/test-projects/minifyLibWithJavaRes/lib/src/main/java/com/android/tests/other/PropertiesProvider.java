package com.android.tests.other;

import java.io.InputStream;
import java.io.IOException;
import java.lang.RuntimeException;
import java.util.Properties;
import java.util.Enumeration;
import java.net.URL;

/**
 * String provider getting the string format from a co-bundled resources.properties file.
 */
public class PropertiesProvider {

    private final static Properties properties = new Properties();

    static {
        try {
            InputStream inputStream = null;
            try {
                inputStream = PropertiesProvider.class.getResourceAsStream("resources.properties");
                if (inputStream == null) {
                    properties.put("the.string", "Error, cannot find resources.properties for %d");
                } else {
                    properties.load(inputStream);
                }
            } finally {
                if (inputStream != null) {
                    inputStream.close();
                }
            }
            try {
               // load the second resource file, using absolute path.
                inputStream = PropertiesProvider.class.getResourceAsStream("/com/android/tests/other/another.properties");
                if (inputStream == null) {
                    properties.put("the.format", "Error, cannot load another.properties %s %d");
                } else {
                    properties.load(inputStream);
                }
            } finally {
                if (inputStream != null) {
                    inputStream.close();
                }
            }

        } catch(IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getProperty(String key) {
        return properties.getProperty(key);
    }
}