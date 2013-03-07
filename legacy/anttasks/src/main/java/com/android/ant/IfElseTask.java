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

package com.android.ant;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.taskdefs.Sequential;
import org.apache.tools.ant.taskdefs.condition.And;

/**
 * If (condition) then: {@link Sequential} else: {@link Sequential}.
 *
 * In XML:
 * <if condition="${prop with a boolean value}">
 *     <then>
 *     </then>
 *     <else>
 *     </else>
 * </if>
 *
 * or
 *
 * <if>
 *     <condition>
 *         ...
 *     </condition>
 *     <then>
 *     </then>
 *     <else>
 *     </else>
 * </if>
 *
 * both <then> and <else> behave like <sequential>.
 * <condition> behaves like an <and> condition.
 *
 * The presence of both <then> and <else> is not required, but one of them must be present.
 * <if condition="${some.condition}">
 *     <else>
 *     </else>
 * </if>
 * is perfectly valid.
 *
 */
public class IfElseTask extends Task {

    private boolean mCondition;
    private boolean mConditionIsSet = false;
    private And mAnd;
    private Sequential mThen;
    private Sequential mElse;

    /**
     * Sets the condition value
     */
    public void setCondition(boolean condition) {
        mCondition = condition;
        mConditionIsSet = true;
    }

    /**
     * Creates and returns the <condition> node which is basically a <and>.
     */
    public Object createCondition() {
        if (mConditionIsSet) {
            throw new BuildException("Cannot use both condition attribute and <condition> element");
        }

        mAnd = new And();
        mAnd.setProject(getProject());
        return mAnd;
    }

    /**
     * Creates and returns the <then> {@link Sequential}
     */
    public Object createThen() {
        mThen = new Sequential();
        return mThen;
    }

    /**
     * Creates and returns the <else> {@link Sequential}
     */
    public Object createElse() {
        mElse = new Sequential();
        return mElse;
    }

    @Override
    public void execute() throws BuildException {
        if (mConditionIsSet == false && mAnd == null) {
            throw new BuildException("condition attribute or element must be set.");
        }

        if (mAnd != null) {
            mCondition = mAnd.eval();
        }

        // need at least one.
        if (mThen == null && mElse == null) {
            throw new BuildException("Need at least <then> or <else>");
        }

        if (mCondition) {
            if (mThen != null) {
                mThen.execute();
            }
        } else {
            if (mElse != null) {
                mElse.execute();
            }
        }
    }
}
