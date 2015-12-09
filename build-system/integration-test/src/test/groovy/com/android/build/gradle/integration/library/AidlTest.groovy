/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.build.gradle.integration.library
import com.android.build.gradle.integration.common.fixture.GradleTestProject
import com.android.build.gradle.integration.common.fixture.app.HelloWorldApp
import com.android.build.gradle.integration.common.runner.FilterableParameterized
import com.android.build.gradle.integration.common.truth.AarSubject
import com.android.build.gradle.integration.common.utils.TestFileUtils
import com.android.utils.FileUtils
import com.google.common.io.Files
import groovy.transform.CompileStatic
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

import static com.android.build.gradle.integration.common.truth.TruthHelper.assertThatAar
/**
 * Assemble tests for aidl.
 */
@CompileStatic
@RunWith(FilterableParameterized)
class AidlTest {

    @Parameterized.Parameters(name = "{0}")
    public static Iterable<Object[]> data() {
        return [
                ["com.android.application"].toArray(),
                ["com.android.library"].toArray(),
        ]
    }

    @Rule
    public GradleTestProject project

    private String plugin
    private File iTestAidl
    private File aidlDir
    private File activity

    public AidlTest(String plugin) {
        this.plugin = plugin
        this.project = GradleTestProject.builder()
                .fromTestApp(HelloWorldApp.forPlugin(plugin))
                .create()
    }

    @Before
    void setUp() {
        aidlDir = project.file("src/main/aidl/com/example/helloworld")

        FileUtils.mkdirs(aidlDir)

        new File(aidlDir, "MyRect.aidl") << """
package com.example.helloworld;

// Declare MyRect so AIDL can find it and knows that it implements
// the parcelable protocol.
parcelable MyRect;
"""

        iTestAidl = new File(aidlDir, "ITest.aidl")
        iTestAidl << """
package com.example.helloworld;

import com.example.helloworld.MyRect;

interface ITest {
    MyRect getMyRect();
    int getInt();
}"""

        new File(aidlDir, "WhiteListed.aidl") << """
package com.example.helloworld;

import com.example.helloworld.MyRect;

interface WhiteListed {
    MyRect getMyRect();
    int getInt();
}"""

        File javaDir = project.file("src/main/java/com/example/helloworld")
        activity = new File(javaDir, "HelloWorld.java")

        new File(javaDir, "MyRect.java") << """
package com.example.helloworld;

import android.os.Parcel;
import android.os.Parcelable;

public class MyRect implements Parcelable {
    public int left;
    public int top;
    public int right;
    public int bottom;

    public static final Parcelable.Creator<MyRect> CREATOR = new Parcelable.Creator<MyRect>() {
        public MyRect createFromParcel(Parcel in) {
            return new MyRect(in);
        }

        public MyRect[] newArray(int size) {
            return new MyRect[size];
        }
    };

    public MyRect() {
    }

    private MyRect(Parcel in) {
        readFromParcel(in);
    }

    public void writeToParcel(Parcel out) {
        out.writeInt(left);
        out.writeInt(top);
        out.writeInt(right);
        out.writeInt(bottom);
    }

    public void readFromParcel(Parcel in) {
        left = in.readInt();
        top = in.readInt();
        right = in.readInt();
        bottom = in.readInt();
    }

    public int describeContents() {
        // TODO Auto-generated method stub
        return 0;
    }

    public void writeToParcel(Parcel arg0, int arg1) {
        // TODO Auto-generated method stub

    }
}
"""

        TestFileUtils.addMethod(
                activity,
                """
                void useAidlClasses(ITest instance) throws Exception {
                    MyRect r = instance.getMyRect();
                    r.toString();
                }
                """)

        if (plugin.contains("library")) {
            project.buildFile <<
                    'android.aidlPackageWhiteList = ["com/example/helloworld/WhiteListed.aidl"]'
        }

    }

    @Test
    void lint() {
        project.execute("lint")
    }

    @Test
    void testAidl() {
        project.execute("assembleDebug")
        checkAar("ITest")

        TestFileUtils.searchAndReplace(iTestAidl, "int getInt\\(\\);", "")
        project.execute("assembleDebug")
        checkAar("ITest")

        TestFileUtils.searchAndReplace(iTestAidl, "ITest", "IRenamed")
        TestFileUtils.searchAndReplace(activity, "ITest", "IRenamed")
        Files.move(iTestAidl, new File(aidlDir, "IRenamed.aidl"))

        project.execute("assembleDebug")
        checkAar("IRenamed")
        checkAar("ITest")
    }

    private void checkAar(String dontInclude) {
        if (!this.plugin.contains("library")) {
            return
        }

        AarSubject aar = assertThatAar(project.getAar("debug"))
        aar.contains("aidl/com/example/helloworld/MyRect.aidl")
        aar.contains("aidl/com/example/helloworld/WhiteListed.aidl")
        aar.doesNotContain("aidl/com/example/helloworld/${dontInclude}.aidl")
    }
}
