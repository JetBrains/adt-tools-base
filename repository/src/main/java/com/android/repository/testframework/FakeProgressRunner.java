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

package com.android.repository.testframework;

import com.android.annotations.NonNull;
import com.android.repository.api.ProgressRunner;

/**
 * A basic {@link ProgressRunner} that uses a {@link FakeProgressIndicator}.
 */
public class FakeProgressRunner implements ProgressRunner {

    FakeProgressIndicator mProgressIndicator = new FakeProgressIndicator();

    @Override
    public void runAsyncWithProgress(@NonNull final ProgressRunner.ProgressRunnable r) {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                r.run(mProgressIndicator, FakeProgressRunner.this);
            }
        });
    }

    @Override
    public void runSyncWithProgress(@NonNull ProgressRunner.ProgressRunnable r) {
        r.run(mProgressIndicator, this);
    }

    @Override
    public void runSyncWithoutProgress(@NonNull Runnable r) {
        r.run();
    }

    public FakeProgressIndicator getProgressIndicator() {
        return mProgressIndicator;
    }
}
