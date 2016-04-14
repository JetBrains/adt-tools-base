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
package com.android.ddmlib.jdwp;

/**
 * JDWP command constants as specified here:
 * http://docs.oracle.com/javase/7/docs/platform/jpda/jdwp/jdwp-protocol.html
 */
public class JdwpCommands {
    public static final int SET_VM = 1;
    public static final int CMD_VM_VERSION = 1;
    public static final int CMD_VM_CLASSESBYSIGNATURE = 2;
    public static final int CMD_VM_ALLCLASSES = 3;
    public static final int CMD_VM_ALLTHREADS = 4;
    public static final int CMD_VM_TOPLEVELTHREADGROUPS = 5;
    public static final int CMD_VM_DISPOSE = 6;
    public static final int CMD_VM_IDSIZES = 7;
    public static final int CMD_VM_SUSPEND = 8;
    public static final int CMD_VM_RESUME = 9;
    public static final int CMD_VM_EXIT = 10;
    public static final int CMD_VM_CREATESTRING = 11;
    public static final int CMD_VM_CAPABILITIES = 12;
    public static final int CMD_VM_CLASSPATHS = 13;
    public static final int CMD_VM_DISPOSEOBJECTS = 14;
    public static final int CMD_VM_HOLDEVENTS = 15;
    public static final int CMD_VM_RELEASEEVENTS = 16;
    public static final int CMD_VM_CAPABILITIESNEW = 17;
    public static final int CMD_VM_REDEFINECLASSES = 18;
    public static final int CMD_VM_SETDEFAULTSTRATUM = 19;
    public static final int CMD_VM_ALLCLASSESWITHGENERIC = 20;

    public static final int SET_REFTYPE = 2;
    public static final int CMD_REFTYPE_SIGNATURE = 1;
    public static final int CMD_REFTYPE_CLASSLOADER = 2;
    public static final int CMD_REFTYPE_MODIFIERS = 3;
    public static final int CMD_REFTYPE_FIELDS = 4;
    public static final int CMD_REFTYPE_METHODS = 5;
    public static final int CMD_REFTYPE_GETVALUES = 6;
    public static final int CMD_REFTYPE_SOURCEFILE = 7;
    public static final int CMD_REFTYPE_NESTEDTYPES = 8;
    public static final int CMD_REFTYPE_STATUS = 9;
    public static final int CMD_REFTYPE_INTERFACES = 10;
    public static final int CMD_REFTYPE_CLASSOBJECT = 11;
    public static final int CMD_REFTYPE_SOURCEDEBUGEXTENSION = 12;
    public static final int CMD_REFTYPE_SIGNATUREWITHGENERIC = 13;
    public static final int CMD_REFTYPE_FIELDSWITHGENERIC = 14;
    public static final int CMD_REFTYPE_METHODSWITHGENERIC = 15;

    public static final int SET_CLASSTYPE = 3;
    public static final int CMD_CLASSTYPE_SUPERCLASS = 1;
    public static final int CMD_CLASSTYPE_SETVALUES = 2;
    public static final int CMD_CLASSTYPE_INVOKEMETHOD = 3;
    public static final int CMD_CLASSTYPE_NEWINSTANCE = 4;

    public static final int SET_ARRAYTYPE = 4;
    public static final int CMD_ARRAYTYPE_NEWINSTANCE = 1;

    public static final int SET_INTERFACETYPE = 5;

    public static final int SET_METHOD = 6;
    public static final int CMD_METHOD_LINETABLE = 1;
    public static final int CMD_METHOD_VARIABLETABLE = 2;
    public static final int CMD_METHOD_BYTECODES = 3;
    public static final int CMD_METHOD_ISOBSOLETE = 4;
    public static final int CMD_METHOD_VARIABLETABLEWITHGENERIC = 5;

    public static final int SET_FIELD = 8;

    public static final int SET_OBJREF = 9;
    public static final int CMD_OBJREF_REFERENCETYPE = 1;
    public static final int CMD_OBJREF_GETVALUES = 2;
    public static final int CMD_OBJREF_SETVALUES = 3;
    public static final int CMD_OBJREF_MONITORINFO = 5;
    public static final int CMD_OBJREF_INVOKEMETHOD = 6;
    public static final int CMD_OBJREF_DISABLECOLLECTION = 7;
    public static final int CMD_OBJREF_ENABLECOLLECTION = 8;
    public static final int CMD_OBJREF_ISCOLLECTED = 9;

    public static final int SET_STRINGREF = 10;
    public static final int CMD_STRINGREF_VALUE = 1;

    public static final int SET_THREADREF = 11;
    public static final int CMD_THREADREF_NAME = 1;
    public static final int CMD_THREADREF_SUSPEND = 2;
    public static final int CMD_THREADREF_RESUME = 3;
    public static final int CMD_THREADREF_STATUS = 4;
    public static final int CMD_THREADREF_THREADGROUP = 5;
    public static final int CMD_THREADREF_FRAMES = 6;
    public static final int CMD_THREADREF_FRAMECOUNT = 7;
    public static final int CMD_THREADREF_OWNEDMONITORS = 8;
    public static final int CMD_THREADREF_CURRENTCONTENDEDMONITOR = 9;
    public static final int CMD_THREADREF_STOP = 10;
    public static final int CMD_THREADREF_INTERRUPT = 11;
    public static final int CMD_THREADREF_SUSPENDCOUNT = 12;

    public static final int SET_THREADGROUPREF = 12;
    public static final int CMD_THREADGROUPREF_NAME = 1;
    public static final int CMD_THREADGROUPREF_PARENT = 2;
    public static final int CMD_THREADGROUPREF_CHILDREN = 3;

    public static final int SET_ARRAYREF = 13;
    public static final int CMD_ARRAYREF_LENGTH = 1;
    public static final int CMD_ARRAYREF_GETVALUES = 2;
    public static final int CMD_ARRAYREF_SETVALUES = 3;

    public static final int SET_CLASSLOADERREF = 14;
    public static final int CMD_CLASSLOADERREF_VISIBLECLASSES = 1;

    public static final int SET_EVENTREQUEST = 15;
    public static final int CMD_EVENTREQUEST_SET = 1;
    public static final int CMD_EVENTREQUEST_CLEAR = 2;
    public static final int CMD_EVENTREQUEST_CLEARALLBREAKPOINTS = 3;

    public static final int SET_STACKFRAME = 16;
    public static final int CMD_STACKFRAME_GETVALUES = 1;
    public static final int CMD_STACKFRAME_SETVALUES = 2;
    public static final int CMD_STACKFRAME_THISOBJECT = 3;
    public static final int CMD_STACKFRAME_POPFRAMES = 4;

    public static final int SET_CLASSOBJECTREF = 17;
    public static final int CMD_CLASSOBJECTREF_REFLECTEDTYPE = 1;

    public static final int SET_EVENT = 64;
    public static final int CMD_EVENT_COMPOSITE = 100;
}
