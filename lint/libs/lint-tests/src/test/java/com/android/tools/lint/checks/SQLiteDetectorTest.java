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

import com.android.tools.lint.detector.api.Detector;

public class SQLiteDetectorTest extends AbstractCheckTest {
    @Override
    protected Detector getDetector() {
        return new SQLiteDetector();
    }

    public void test() throws Exception {
        assertEquals(""
                + "src/test/pkg/SQLiteTest.java:25: Warning: Using column type STRING; did you mean to use TEXT? (STRING is a numeric type and its value can be adjusted; for example, strings that look like integers can drop leading zeroes. See issue explanation for details.) [SQLiteString]\n"
                + "        db.execSQL(\"CREATE TABLE \" + name + \"(\" + Tables.AppKeys.SCHEMA + \");\"); // ERROR\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "src/test/pkg/SQLiteTest.java:30: Warning: Using column type STRING; did you mean to use TEXT? (STRING is a numeric type and its value can be adjusted; for example, strings that look like integers can drop leading zeroes. See issue explanation for details.) [SQLiteString]\n"
                + "        db.execSQL(TracksColumns.CREATE_TABLE); // ERROR\n"
                + "        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "0 errors, 2 warnings\n",

                lintProject(
                        java("src/test/pkg/TracksColumns.java", ""
                                + "package test.pkg;\n"
                                + "\n"
                                + "import android.provider.BaseColumns;\n"
                                + "\n"
                                + "public interface TracksColumns extends BaseColumns {\n"
                                + "\n"
                                + "    String TABLE_NAME = \"tracks\";\n"
                                + "\n"
                                + "    String NAME = \"name\";\n"
                                + "    String CATEGORY = \"category\";\n"
                                + "    String STARTTIME = \"starttime\";\n"
                                + "    String MAXGRADE = \"maxgrade\";\n"
                                + "    String MAPID = \"mapid\";\n"
                                + "    String TABLEID = \"tableid\";\n"
                                + "    String ICON = \"icon\";\n"
                                + "\n"
                                + "    String CREATE_TABLE = \"CREATE TABLE \" + TABLE_NAME + \" (\"\n"
                                + "            + _ID + \" INTEGER PRIMARY KEY AUTOINCREMENT, \"\n"
                                + "            + NAME + \" STRING, \"\n"
                                + "            + CATEGORY + \" STRING, \"\n"
                                + "            + STARTTIME + \" INTEGER, \"\n"
                                + "            + MAXGRADE + \" FLOAT, \"\n"
                                + "            + MAPID + \" STRING, \"\n"
                                + "            + TABLEID + \" STRING, \"\n"
                                + "            + ICON + \" STRING\"\n"
                                + "            + \");\";\n"
                                + "}\n"),

                        java("src/test/pkg/SQLiteTest.java", ""
                                + "package test.pkg;\n"
                                + "\n"
                                + "import android.database.sqlite.SQLiteDatabase;\n"
                                + "\n"
                                + "@SuppressWarnings({\"unused\", \"SpellCheckingInspection\"})\n"
                                + "public class SQLiteTest {\n"
                                + "    public interface Tables {\n"
                                + "        interface AppKeys {\n"
                                + "            String NAME = \"appkeys\";\n"
                                + "\n"
                                + "            interface Columns {\n"
                                + "                String _ID = \"_id\";\n"
                                + "                String PKG_NAME = \"packageName\";\n"
                                + "                String PKG_SIG = \"signatureDigest\";\n"
                                + "            }\n"
                                + "\n"
                                + "            String SCHEMA =\n"
                                + "                    Columns._ID + \" INTEGER PRIMARY KEY AUTOINCREMENT,\" +\n"
                                + "                            Columns.PKG_NAME + \" STRING NOT NULL,\" +\n"
                                + "                            Columns.PKG_SIG + \" STRING NOT NULL\";\n"
                                + "        }\n"
                                + "    }\n"
                                + "\n"
                                + "    public void test(SQLiteDatabase db, String name) {\n"
                                + "        db.execSQL(\"CREATE TABLE \" + name + \"(\" + Tables.AppKeys.SCHEMA + \");\"); // ERROR\n"
                                + "\n"
                                + "    }\n"
                                + "\n"
                                + "    public void onCreate(SQLiteDatabase db) {\n"
                                + "        db.execSQL(TracksColumns.CREATE_TABLE); // ERROR\n"
                                + "    }\n"
                                + "\n"
                                + "    private void doCreate(SQLiteDatabase db) {\n"
                                + "        // Not yet handled; we need to flow string concatenation across procedure calls\n"
                                + "        createTable(db, Tables.AppKeys.NAME, Tables.AppKeys.SCHEMA); // ERROR\n"
                                + "    }\n"
                                + "\n"
                                + "    private void createTable(SQLiteDatabase db, String tableName, String schema) {\n"
                                + "        db.execSQL(\"CREATE TABLE \" + tableName + \"(\" + schema + \");\");\n"
                                + "    }\n"
                                + "}"),

                        // stub for type resolution
                        java("src/android/database/sqlite/SQLiteDatabase.java", ""
                                + "package android.database.sqlite;\n"
                                + "\n"
                                + "import android.database.SQLException;\n"
                                + "\n"
                                + "// Lint unit testing stub\n"
                                + "public class SQLiteDatabase {\n"
                                + "    public void execSQL(String sql) throws SQLException {\n"
                                + "    }\n"
                                + "}")
                ));
    }
}
