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

package com.android.sdklib.devices;

public class Storage {
    private long mNoBytes;

    public Storage(long amount, Unit unit){
        mNoBytes = amount * unit.getNumberOfBytes();
    }

    public Storage(long amount){
        this(amount, Unit.B);
    }

    /** Returns the amount of storage represented, in Bytes */
    public long getSize() {
        return getSizeAsUnit(Unit.B);
    }

    public Storage deepCopy() {
        return new Storage(mNoBytes);
    }

    /**
     * Return the amount of storage represented by the instance in the given unit
     * @param unit The unit of the result.
     * @return The size of the storage in the given unit.
     */
    public long getSizeAsUnit(Unit unit) {
        return mNoBytes / unit.getNumberOfBytes();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this){
            return true;
        }

        if (!(o instanceof Storage)) {
            return false;
        }

        Storage s = (Storage) o;
        if (s.getSize() == this.getSize()) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        int result = 17;
        return 31 * result + (int) (mNoBytes^(mNoBytes>>>32));
    }

    public enum Unit{
        B("B", 1),
        KiB("KiB", 1024),
        MiB("MiB", 1024 * 1024),
        GiB("GiB", 1024 * 1024 * 1024),
        TiB("TiB", 1024l * 1024l * 1024l * 1024l);

        private String mValue;
        /** The number of bytes needed to have one of the given unit */
        private long mNoBytes;

        private Unit(String val, long noBytes) {
            mValue = val;
            mNoBytes = noBytes;
        }

        public static Unit getEnum(String val) {
            for(Unit v : values()){
                if(v.mValue.equals(val)) {
                    return v;
                }
            }
            return null;
        }

        public long getNumberOfBytes() {
            return mNoBytes;
        }

        @Override
        public String toString() {
            return mValue;
        }
    }

    /**
     * Finds the largest {@link Unit} which can display the storage value as a positive integer
     * with no loss of accuracy.
     * @return The most appropriate {@link Unit}.
     */
    public Unit getApproriateUnits() {
        Unit optimalUnit = Unit.B;
        for(Unit unit : Unit.values()) {
            if(mNoBytes % unit.getNumberOfBytes() == 0) {
                optimalUnit = unit;
            } else {
                break;
            }
        }
        return optimalUnit;
    }
}
