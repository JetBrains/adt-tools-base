/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.resources;

import com.android.resources.FolderTypeRelationship;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;

import junit.framework.TestCase;

public class FolderTypeRelationShipTest extends TestCase {

    public void testResourceType() {
        // all resource type should be in the FolderTypeRelationShip map.
        // loop on all the enum, and make sure there's at least one folder type for it.
        for (ResourceType type : ResourceType.values()) {
            assertTrue(type.getDisplayName(),
                    FolderTypeRelationship.getRelatedFolders(type).size() > 0);
        }
    }

    public void testResourceFolderType() {
        // all resource folder type should generate at least one type of resource.
        // loop on all the enum, and make sure there's at least one res type for it.
        for (ResourceFolderType type : ResourceFolderType.values()) {
            assertTrue(type.getName(),
                    FolderTypeRelationship.getRelatedResourceTypes(type).size() > 0);
        }
    }
}
