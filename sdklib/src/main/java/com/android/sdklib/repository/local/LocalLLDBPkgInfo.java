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

package com.android.sdklib.repository.local;

import com.android.annotations.NonNull;
import com.android.sdklib.repository.descriptors.IPkgDesc;
import com.android.sdklib.repository.descriptors.PkgDesc;
import com.android.repository.Revision;

import java.io.File;
import java.util.Properties;

/**
 * Local package representing the Android LLDB.
 *
 * @deprecated in favor of new sdk system
 */
@Deprecated
public class LocalLLDBPkgInfo extends LocalPkgInfo {
  /**
   * The LLDB SDK package revision's major and minor numbers are pinned in Android Studio.
   *
   * @deprecated in favor of LLDBSdkPackageInstaller#PINNED_REVISION.
   */

  @Deprecated
  public static final Revision PINNED_REVISION = new Revision(2, 0);

  @NonNull
  private final IPkgDesc mDesc;

  protected LocalLLDBPkgInfo(@NonNull LocalSdk localSdk,
                             @NonNull File localDir,
                             @NonNull Properties sourceProps,
                             @NonNull Revision revision) {
    super(localSdk, localDir, sourceProps);
    mDesc = PkgDesc.Builder.newLLDB(revision).setDescriptionShort("LLDB").setListDisplay("LLDB").create();
  }

  @NonNull
  @Override
  public IPkgDesc getDesc() {
    return mDesc;
  }
}
