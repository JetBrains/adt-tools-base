package com.android.testutils;

import com.android.utils.DateProvider;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * An implementation of {@link DateProvider} that uses the virtual notion of time of a
 * {@link VirtualTimeScheduler} to provide a presentation of "now". To be used in tests where it is
 * better not to have fluent notion of time.
 */
public class VirtualTimeDateProvider implements DateProvider {

    private final VirtualTimeScheduler virtualTimeScheduler;

    public VirtualTimeDateProvider(VirtualTimeScheduler virtualTimeScheduler) {
        this.virtualTimeScheduler = virtualTimeScheduler;
    }

    @Override
    public Date now() {
        return new Date(TimeUnit.NANOSECONDS.toMillis(virtualTimeScheduler.getCurrentTimeNanos()));
    }
}
