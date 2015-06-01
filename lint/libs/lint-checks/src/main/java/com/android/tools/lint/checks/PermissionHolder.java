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

package com.android.tools.lint.checks;

import com.android.annotations.NonNull;

import java.util.Set;

/**
 * A {@linkplain PermissionHolder} knows which permissions are held/granted and can look up
 * individual permissions and respond to queries about whether they are held or not.
 */
public interface PermissionHolder {

    boolean hasPermission(@NonNull String permission);

    /** A convenience implementation of {@link PermissionHolder} backed by a set
     */
    class SetPermissionLookup implements PermissionHolder {
        private Set<String> myPermissions;

        public SetPermissionLookup(@NonNull Set<String> permissions) {
            myPermissions = permissions;
        }

        @Override
        public boolean hasPermission(@NonNull String permission) {
            return myPermissions.contains(permission);
        }
    }
}
