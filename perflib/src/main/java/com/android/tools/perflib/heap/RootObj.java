/*
 * Copyright (C) 2008 Google Inc.
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

package com.android.tools.perflib.heap;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;

public class RootObj extends Instance {
    public static final String UNDEFINED_CLASS_NAME = "no class defined!!";

    RootType mType = RootType.UNKNOWN;

    int mIndex;

    int mThread;

    public RootObj(RootType type) {
        this(type, 0, 0, null);
    }

    public RootObj(RootType type, long id) {
        this(type, id, 0, null);
    }

    public RootObj(RootType type, long id, int thread, StackTrace stack) {
        super(id, stack);
        mType = type;
        mThread = thread;
    }

    public final String getClassName(@NonNull Snapshot snapshot) {
        ClassObj theClass;

        if (mType == RootType.SYSTEM_CLASS) {
            theClass = snapshot.findClass(mId);
        } else {
            theClass = snapshot.findReference(mId).getClassObj();
        }

        if (theClass == null) {
            return UNDEFINED_CLASS_NAME;
        }

        return theClass.mClassName;
    }

    @Override
    public final void accept(Visitor visitor) {
        visitor.visitRootObj(this);
        Instance instance = getReferredInstance();
        if (instance != null) {
            visitor.visitLater(instance);
        }
    }

    public final String toString() {
        return String.format("%s@0x%08x", mType.getName(), mId);
    }

    @Nullable
    public Instance getReferredInstance() {
        if (mType == RootType.SYSTEM_CLASS) {
            return mHeap.mSnapshot.findClass(mId);
        } else {
            return mHeap.mSnapshot.findReference(mId);
        }
    }

    public RootType getRootType() {
        return mType;
    }
}
