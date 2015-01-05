/*
 * Copyright (C) 2012 The Android Open Source Project
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
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.ClassContext;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Detector.ClassScanner;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.Interpreter;
import org.objectweb.asm.tree.analysis.Value;

import java.util.Arrays;
import java.util.List;

/**
 * Checks for missing {@code recycle} calls on resources that encourage it, and
 * for missing {@code commit} calls on FragmentTransactions, etc.
 */
public class CleanupDetector extends Detector implements ClassScanner {

    private static final Implementation IMPLEMENTATION = new Implementation(
            CleanupDetector.class,
            Scope.CLASS_FILE_SCOPE);

    /** Problems with missing recycle calls */
    public static final Issue RECYCLE_RESOURCE = Issue.create(
        "Recycle", //$NON-NLS-1$
        "Missing `recycle()` calls",

        "Many resources, such as TypedArrays, VelocityTrackers, etc., " +
        "should be recycled (with a `recycle()` call) after use. This lint check looks " +
        "for missing `recycle()` calls.",

        Category.PERFORMANCE,
        7,
        Severity.WARNING,
            IMPLEMENTATION);

    /** Problems with missing commit calls. */
    public static final Issue COMMIT_FRAGMENT = Issue.create(
            "CommitTransaction", //$NON-NLS-1$
            "Missing `commit()` calls",

            "After creating a `FragmentTransaction`, you typically need to commit it as well",

            Category.CORRECTNESS,
            7,
            Severity.WARNING,
            IMPLEMENTATION);

    // Target method names
    private static final String RECYCLE = "recycle";                                  //$NON-NLS-1$
    private static final String OBTAIN = "obtain";                                    //$NON-NLS-1$
    private static final String SHOW = "show";                                        //$NON-NLS-1$
    private static final String OBTAIN_NO_HISTORY = "obtainNoHistory";                //$NON-NLS-1$
    private static final String OBTAIN_ATTRIBUTES = "obtainAttributes";               //$NON-NLS-1$
    private static final String OBTAIN_TYPED_ARRAY = "obtainTypedArray";              //$NON-NLS-1$
    private static final String OBTAIN_STYLED_ATTRIBUTES = "obtainStyledAttributes";  //$NON-NLS-1$
    private static final String BEGIN_TRANSACTION = "beginTransaction";               //$NON-NLS-1$
    private static final String COMMIT = "commit";                                    //$NON-NLS-1$
    private static final String COMMIT_ALLOWING_LOSS = "commitAllowingStateLoss";     //$NON-NLS-1$

    // Target owners
    private static final String VELOCITY_TRACKER_CLS = "android/view/VelocityTracker";//$NON-NLS-1$
    private static final String TYPED_ARRAY_CLS = "android/content/res/TypedArray";   //$NON-NLS-1$
    private static final String CONTEXT_CLS = "android/content/Context";              //$NON-NLS-1$
    private static final String MOTION_EVENT_CLS = "android/view/MotionEvent";        //$NON-NLS-1$
    private static final String RESOURCES_CLS = "android/content/res/Resources";      //$NON-NLS-1$
    private static final String PARCEL_CLS = "android/os/Parcel";                     //$NON-NLS-1$
    private static final String FRAGMENT_MANAGER_CLS = "android/app/FragmentManager"; //$NON-NLS-1$
    private static final String FRAGMENT_MANAGER_V4_CLS =
            "android/support/v4/app/FragmentManager";                                 //$NON-NLS-1$
    private static final String FRAGMENT_TRANSACTION_CLS =
            "android/app/FragmentTransaction";                                        //$NON-NLS-1$
    private static final String FRAGMENT_TRANSACTION_V4_CLS =
            "android/support/v4/app/FragmentTransaction";                             //$NON-NLS-1$
    private static final String DIALOG_FRAGMENT_SHOW_DESC =
            "(Landroid/app/FragmentTransaction;Ljava/lang/String;)I";                 //$NON-NLS-1$
    private static final String DIALOG_FRAGMENT_SHOW_V4_DESC =
            "(Landroid/support/v4/app/FragmentTransaction;Ljava/lang/String;)I";      //$NON-NLS-1$

    // Target description signatures
    private static final String TYPED_ARRAY_SIG = "Landroid/content/res/TypedArray;"; //$NON-NLS-1$

    private boolean mObtainsTypedArray;
    private boolean mRecyclesTypedArray;
    private boolean mObtainsTracker;
    private boolean mRecyclesTracker;
    private boolean mObtainsMotionEvent;
    private boolean mRecyclesMotionEvent;
    private boolean mObtainsParcel;
    private boolean mRecyclesParcel;
    private boolean mObtainsTransaction;
    private boolean mCommitsTransaction;

    /** Constructs a new {@link CleanupDetector} */
    public CleanupDetector() {
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        int phase = context.getDriver().getPhase();
        if (phase == 1) {
            if (mObtainsTypedArray && !mRecyclesTypedArray
                    || mObtainsTracker && !mRecyclesTracker
                    || mObtainsParcel && !mRecyclesParcel
                    || mObtainsMotionEvent && !mRecyclesMotionEvent
                    || mObtainsTransaction && !mCommitsTransaction) {
                context.getDriver().requestRepeat(this, Scope.CLASS_FILE_SCOPE);
            }
        }
    }

    // ---- Implements ClassScanner ----

    @Override
    @Nullable
    public List<String> getApplicableCallNames() {
        return Arrays.asList(
                RECYCLE,
                OBTAIN_STYLED_ATTRIBUTES,
                OBTAIN,
                OBTAIN_ATTRIBUTES,
                OBTAIN_TYPED_ARRAY,
                OBTAIN_NO_HISTORY,
                BEGIN_TRANSACTION,
                COMMIT,
                COMMIT_ALLOWING_LOSS,
                SHOW
        );
    }

    @Override
    public void checkCall(
            @NonNull ClassContext context,
            @NonNull ClassNode classNode,
            @NonNull MethodNode method,
            @NonNull MethodInsnNode call) {
        String name = call.name;
        String owner = call.owner;
        String desc = call.desc;
        int phase = context.getDriver().getPhase();
        if (SHOW.equals(name)) {
            if (desc.equals(DIALOG_FRAGMENT_SHOW_DESC)
                    || desc.equals(DIALOG_FRAGMENT_SHOW_V4_DESC)) {
                mCommitsTransaction = true;
            }
        } else if (RECYCLE.equals(name) && desc.equals("()V")) { //$NON-NLS-1$
            if (owner.equals(TYPED_ARRAY_CLS)) {
                mRecyclesTypedArray = true;
            } else if (owner.equals(VELOCITY_TRACKER_CLS)) {
                mRecyclesTracker = true;
            } else if (owner.equals(MOTION_EVENT_CLS)) {
                mRecyclesMotionEvent = true;
            } else if (owner.equals(PARCEL_CLS)) {
                mRecyclesParcel = true;
            }
        } else if ((COMMIT.equals(name) || COMMIT_ALLOWING_LOSS.equals(name))
                && desc.equals("()I")) { //$NON-NLS-1$
            if (owner.equals(FRAGMENT_TRANSACTION_CLS)
                    || owner.equals(FRAGMENT_TRANSACTION_V4_CLS)) {
                mCommitsTransaction = true;
            }
        } else if (owner.equals(MOTION_EVENT_CLS)) {
            if (OBTAIN.equals(name) || OBTAIN_NO_HISTORY.equals(name)) {
                mObtainsMotionEvent = true;
                if (phase == 2 && !mRecyclesMotionEvent) {
                    context.report(RECYCLE_RESOURCE, method, call, context.getLocation(call),
                            getErrorMessage(MOTION_EVENT_CLS));
                } else if (phase == 1
                        && checkMethodFlow(context, classNode, method, call, MOTION_EVENT_CLS)) {
                    // Already reported error above; don't do global check
                    mRecyclesMotionEvent = true;
                }
            }
        } else if (OBTAIN.equals(name)) {
            if (owner.equals(VELOCITY_TRACKER_CLS)) {
                mObtainsTracker = true;
                if (phase == 2 && !mRecyclesTracker) {
                    context.report(RECYCLE_RESOURCE, method, call, context.getLocation(call),
                            getErrorMessage(VELOCITY_TRACKER_CLS));
                }
            } else if (owner.equals(PARCEL_CLS)) {
                mObtainsParcel = true;
                if (phase == 2 && !mRecyclesParcel) {
                    context.report(RECYCLE_RESOURCE, method, call, context.getLocation(call),
                            getErrorMessage(PARCEL_CLS));
                } else if (phase == 1
                        && checkMethodFlow(context, classNode, method, call, PARCEL_CLS)) {
                    // Already reported error above; don't do global check
                    mRecyclesParcel = true;
                }
            }
        } else if (OBTAIN_STYLED_ATTRIBUTES.equals(name)
                || OBTAIN_ATTRIBUTES.equals(name)
                || OBTAIN_TYPED_ARRAY.equals(name)) {
            if ((owner.equals(CONTEXT_CLS) || owner.equals(RESOURCES_CLS))
                    && desc.endsWith(TYPED_ARRAY_SIG)) {
                mObtainsTypedArray = true;
                if (phase == 2 && !mRecyclesTypedArray) {
                    context.report(RECYCLE_RESOURCE, method, call, context.getLocation(call),
                            getErrorMessage(TYPED_ARRAY_CLS));
                } else if (phase == 1
                        && checkMethodFlow(context, classNode, method, call, TYPED_ARRAY_CLS)) {
                    // Already reported error above; don't do global check
                    mRecyclesTypedArray = true;
                }
            }
        } else if (BEGIN_TRANSACTION.equals(name)
                && (owner.equals(FRAGMENT_MANAGER_CLS) || owner.equals(FRAGMENT_MANAGER_V4_CLS))) {
            mObtainsTransaction = true;
            if (phase == 2 && !mCommitsTransaction) {
                context.report(COMMIT_FRAGMENT, method, call, context.getLocation(call),
                    getErrorMessage(owner.equals(FRAGMENT_MANAGER_CLS)
                            ? FRAGMENT_TRANSACTION_CLS : FRAGMENT_TRANSACTION_V4_CLS));
            } else if (phase == 1
                    && checkMethodFlow(context, classNode, method, call,
                    owner.equals(FRAGMENT_MANAGER_CLS)
                            ? FRAGMENT_TRANSACTION_CLS : FRAGMENT_TRANSACTION_V4_CLS)) {
                // Already reported error above; don't do global check
                mCommitsTransaction = true;
            }
        }
    }

    /** Computes an error message for a missing recycle of the given type */
    private static String getErrorMessage(String owner) {
        if (FRAGMENT_TRANSACTION_CLS.equals(owner) || FRAGMENT_TRANSACTION_V4_CLS.equals(owner)) {
            return "This transaction should be completed with a `commit()` call";
        }
        String className = owner.substring(owner.lastIndexOf('/') + 1);
        return String.format("This `%1$s` should be recycled after use with `#recycle()`",
                className);
    }

    /**
     * Ensures that the given allocate call in the given method has a
     * corresponding recycle method, also within the same method, OR, the
     * allocated resource flows out of the method (either as a return value, or
     * into a field, or into some other method (with some known exceptions; e.g.
     * passing a MotionEvent into another MotionEvent's constructor is fine)
     * <p>
     * Returns true if an error was found
     */
    private static boolean checkMethodFlow(ClassContext context, ClassNode classNode,
            MethodNode method, MethodInsnNode call, String recycleOwner) {
        CleanupTracker interpreter = new CleanupTracker(context, method, call, recycleOwner);
        ResourceAnalyzer analyzer = new ResourceAnalyzer(interpreter);
        interpreter.setAnalyzer(analyzer);
        try {
            analyzer.analyze(classNode.name, method);
            if (!interpreter.isCleanedUp() && !interpreter.isEscaped()) {
                Location location = context.getLocation(call);
                String message = getErrorMessage(recycleOwner);
                Issue issue = call.owner.equals(FRAGMENT_MANAGER_CLS)
                        ? COMMIT_FRAGMENT : RECYCLE_RESOURCE;
                context.report(issue, method, call, location, message);
                return true;
            }
        } catch (AnalyzerException e) {
            context.log(e, null);
        }

        return false;
    }

    @VisibleForTesting
    static boolean hasReturnType(String owner, String desc) {
        int descLen = desc.length();
        int ownerLen = owner.length();
        if (descLen < ownerLen + 3) {
            return false;
        }
        if (desc.charAt(descLen - 1) != ';') {
            return false;
        }
        int typeBegin = descLen - 2 - ownerLen;
        if (desc.charAt(typeBegin - 1) != ')' || desc.charAt(typeBegin) != 'L') {
            return false;
        }
        return desc.regionMatches(typeBegin + 1, owner, 0, ownerLen);
    }

    /**
     * ASM interpreter which tracks the instances of the allocated resource, and
     * checks whether it is eventually passed to a {@code recycle()} call. If the
     * value flows out of the method (to a field, or a method call), it will
     * also consider the resource recycled.
     */
    private static class CleanupTracker extends Interpreter {
        // Only identity matters, not value
        private static final Value INSTANCE = BasicValue.INT_VALUE;
        private static final Value RECYCLED = BasicValue.FLOAT_VALUE;
        private static final Value UNKNOWN = BasicValue.UNINITIALIZED_VALUE;

        private final ClassContext mContext;
        private final MethodNode mMethod;
        private final MethodInsnNode mObtainNode;
        private boolean mIsCleanedUp;
        private boolean mEscapes;
        private final String mRecycleOwner;
        private ResourceAnalyzer mAnalyzer;

        public CleanupTracker(
                @NonNull ClassContext context,
                @NonNull MethodNode method,
                @NonNull MethodInsnNode obtainNode,
                @NonNull String recycleOwner) {
            super(Opcodes.ASM5);
            mContext = context;
            mMethod = method;
            mObtainNode = obtainNode;
            mRecycleOwner = recycleOwner;
        }

        /**
         * Sets the analyzer associated with the interpreter, such that it can
         * get access to the execution frames
         */
        void setAnalyzer(ResourceAnalyzer analyzer) {
            mAnalyzer = analyzer;
        }

        /**
         * Returns whether a recycle call was found for the given method
         *
         * @return true if the resource was recycled
         */
        public boolean isCleanedUp() {
            return mIsCleanedUp;
        }

        /**
         * Returns whether the target resource escapes from the method, for
         * example as a return value, or a field assignment, or getting passed
         * to another method
         *
         * @return true if the resource escapes
         */
        public boolean isEscaped() {
            return mEscapes;
        }

        @Override
        public Value newOperation(AbstractInsnNode node) throws AnalyzerException {
            return UNKNOWN;
        }

        @Override
        public Value newValue(final Type type) {
            if (type != null && type.getSort() == Type.VOID) {
                return null;
            } else {
                return UNKNOWN;
            }
        }

        @Override
        public Value copyOperation(AbstractInsnNode node, Value value) throws AnalyzerException {
            return value;
        }

        @Override
        public Value binaryOperation(AbstractInsnNode node, Value value1, Value value2)
                throws AnalyzerException {
            if (node.getOpcode() == Opcodes.PUTFIELD) {
                if (value2 == INSTANCE) {
                    mEscapes = true;
                }
            }
            return merge(value1, value2);
        }

        @Override
        public Value naryOperation(AbstractInsnNode node, List values) throws AnalyzerException {
            if (node == mObtainNode) {
                return INSTANCE;
            }

            MethodInsnNode call = null;
            if (node.getType() == AbstractInsnNode.METHOD_INSN) {
                call = (MethodInsnNode) node;
                if (node.getOpcode() == Opcodes.INVOKEVIRTUAL) {
                    if (call.name.equals(RECYCLE) && call.owner.equals(mRecycleOwner)) {
                        if (values != null && values.size() == 1 && values.get(0) == INSTANCE) {
                            mIsCleanedUp = true;
                            Frame frame = mAnalyzer.getCurrentFrame();
                            if (frame != null) {
                                int localSize = frame.getLocals();
                                for (int i = 0; i < localSize; i++) {
                                    Value local = frame.getLocal(i);
                                    if (local == INSTANCE) {
                                        frame.setLocal(i, RECYCLED);
                                    }
                                }
                                int stackSize = frame.getStackSize();
                                if (stackSize == 1 && frame.getStack(0) == INSTANCE) {
                                    frame.pop();
                                    frame.push(RECYCLED);
                                }
                            }
                            return RECYCLED;
                        }
                    } else if ((call.name.equals(COMMIT) || call.name.equals(COMMIT_ALLOWING_LOSS))
                            && call.owner.equals(mRecycleOwner)) {
                        if (values != null && values.size() == 1 && values.get(0) == INSTANCE) {
                            mIsCleanedUp = true;
                            return INSTANCE;
                        }
                    } else if (call.name.equals(SHOW) && (
                            call.desc.equals(DIALOG_FRAGMENT_SHOW_DESC)
                                || call.desc.equals(DIALOG_FRAGMENT_SHOW_V4_DESC))) {
                        if (values != null && values.size() == 3 && values.get(1) == INSTANCE) {
                            mIsCleanedUp = true;
                            return INSTANCE;
                        }
                    } else if (call.owner.equals(mRecycleOwner)
                            && hasReturnType(mRecycleOwner, call.desc)) {
                        // Called method which returns self. This helps handle cases where you call
                        //   createTransaction().method1().method2().method3().commit() -- if
                        // method1, 2 and 3 all return "this" then the commit call is really
                        // called on the createTransaction instance
                        return INSTANCE;
                    }
                }
            }

            if (values != null && values.size() >= 1) {
                // Skip the first element: method calls *on* the TypedArray are okay
                int start = node.getOpcode() == Opcodes.INVOKESTATIC ? 0 : 1;
                for (int i = 0, n = values.size(); i < n; i++) {
                    Object v = values.get(i);
                    if (v == INSTANCE && i >= start) {
                        // Known special cases
                        if (node.getOpcode() == Opcodes.INVOKESTATIC) {
                            assert call != null;
                            if (call.name.equals(OBTAIN) &&
                                    call.owner.equals(MOTION_EVENT_CLS)) {
                                return UNKNOWN;
                            }
                        }

                        // Passing the instance to another method: could leak
                        // the instance out of this method (for example calling
                        // a method which recycles it on our behalf, or store it
                        // in some holder which will recycle it later). In this
                        // case, just assume that things are okay.
                        mEscapes = true;
                    } else if (v == RECYCLED && call != null) {
                        Location location = mContext.getLocation(call);
                        String message = String.format("This `%1$s` has already been recycled",
                                mRecycleOwner.substring(mRecycleOwner.lastIndexOf('/') + 1));
                        mContext.report(RECYCLE_RESOURCE, mMethod, call, location, message);
                    }
                }
            }

            return UNKNOWN;
        }

        @Override
        public Value unaryOperation(AbstractInsnNode node, Value value) throws AnalyzerException {
            return value;
        }

        @Override
        public Value ternaryOperation(AbstractInsnNode node, Value value1, Value value2,
                Value value3) throws AnalyzerException {
            if (value1 == RECYCLED || value2 == RECYCLED || value3 == RECYCLED) {
                return RECYCLED;
            } else  if (value1 == INSTANCE || value2 == INSTANCE || value3 == INSTANCE) {
                return INSTANCE;
            }
            return UNKNOWN;
        }

        @Override
        public void returnOperation(AbstractInsnNode node, Value value1, Value value2)
                throws AnalyzerException {
            if (value1 == INSTANCE || value2 == INSTANCE) {
                mEscapes = true;
            }
        }

        @Override
        public Value merge(Value value1, Value value2) {
            if (value1 == RECYCLED || value2 == RECYCLED) {
                return RECYCLED;
            } else if (value1 == INSTANCE || value2 == INSTANCE) {
                return INSTANCE;
            }
            return UNKNOWN;
        }
    }

    private static class ResourceAnalyzer extends Analyzer {
        private Frame mCurrent;
        private Frame mFrame1;
        private Frame mFrame2;

        public ResourceAnalyzer(Interpreter interpreter) {
            super(interpreter);
        }

        Frame getCurrentFrame() {
            return mCurrent;
        }

        @Override
        protected void init(String owner, MethodNode m) throws AnalyzerException {
            mCurrent = mFrame2;
            super.init(owner, m);
        }

        @Override
        protected Frame newFrame(int nLocals, int nStack) {
            // Stash the two most recent frame allocations. When init is called the second
            // most recently seen frame is the current frame used during execution, which
            // is where we need to replace INSTANCE with RECYCLED when the void
            // recycle method is called.
            Frame newFrame = super.newFrame(nLocals, nStack);
            mFrame2 = mFrame1;
            mFrame1 = newFrame;
            return newFrame;
        }
    }
}
