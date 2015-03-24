package com.android.tests;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.junit.Ignore;
import android.app.Application;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.util.ArrayMap;
import android.os.Debug;
import org.junit.Test;

import java.lang.RuntimeException;

public class UnitTest {
    @Test
    public void referenceProductionCode() {
        // Reference production code:
        Foo foo = new Foo();
        assertEquals("production code", foo.foo());
    }

    @Test
    public void mockFinalMethod() {
        Activity activity = mock(Activity.class);
        Application app = mock(Application.class);
        when(activity.getApplication()).thenReturn(app);

        assertSame(app, activity.getApplication());

        verify(activity).getApplication();
        verifyNoMoreInteractions(activity);
    }

    @Test
    public void mockFinalClass() {
        BluetoothAdapter adapter = mock(BluetoothAdapter.class);
        when(adapter.isEnabled()).thenReturn(true);

        assertTrue(adapter.isEnabled());

        verify(adapter).isEnabled();
        verifyNoMoreInteractions(adapter);
    }

    @Test
    public void exceptions() {
        try {
            ArrayMap map = new ArrayMap();
            map.isEmpty();
            fail();
        } catch (RuntimeException e) {
            assertEquals(RuntimeException.class, e.getClass());
            assertTrue(e.getMessage().contains("isEmpty"));
            assertTrue(e.getMessage().contains("not mocked"));
            assertTrue(e.getMessage().contains("tech-docs/unit-testing-support"));
        }

        try {
            Debug.getThreadAllocCount();
            fail();
        } catch (RuntimeException e) {
            assertEquals(RuntimeException.class, e.getClass());
            assertTrue(e.getMessage().contains("getThreadAllocCount"));
            assertTrue(e.getMessage().contains("not mocked"));
            assertTrue(e.getMessage().contains("tech-docs/unit-testing-support"));
        }

    }

    @Test
    @Ignore
    public void thisIsIgnored() {
      // Just excercise more JUnit features.
    }
}
