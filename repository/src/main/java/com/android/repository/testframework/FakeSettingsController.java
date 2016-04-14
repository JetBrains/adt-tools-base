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

import com.android.repository.api.Channel;
import com.android.repository.api.SettingsController;

/**
 * A simple {@link SettingsController} where values can be set directly.
 */
public class FakeSettingsController implements SettingsController {

    private boolean mForceHttp;
    private Channel myChannel;

    public FakeSettingsController(boolean forceHttp) {
        mForceHttp = forceHttp;
    }

    @Override
    public boolean getForceHttp() {
        return mForceHttp;
    }

    @Override
    public void setForceHttp(boolean force) {
        mForceHttp = force;
    }

    public void setChannel(Channel channel) {
        myChannel = channel;
    }

    @Override
    public Channel getChannel() {
        return myChannel;
    }
}
