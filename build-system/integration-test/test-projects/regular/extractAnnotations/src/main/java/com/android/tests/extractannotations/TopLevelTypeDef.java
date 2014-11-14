package com.android.tests.extractannotations;

import android.support.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@IntDef(flag=true, value={Constants.CONSTANT_1, Constants.CONSTANT_2})
@Retention(RetentionPolicy.SOURCE)
public @interface TopLevelTypeDef {
}
