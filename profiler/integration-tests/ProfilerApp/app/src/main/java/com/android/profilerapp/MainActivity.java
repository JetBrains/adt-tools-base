/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.profilerapp;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;

import com.android.profilerapp.cpu.CpuFragment;
import com.android.profilerapp.memory.MemoryFragment;
import com.android.profilerapp.network.NetworkFragment;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    TabLayout mTabLayout = null;
    ViewPager mViewPager = null;
    TabsAdapter mTabsAdapter = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTabLayout = (TabLayout) findViewById(R.id.tab_layout);
        mViewPager = (ViewPager) findViewById(R.id.tab_pager);

        mTabsAdapter = new TabsAdapter(this, getSupportFragmentManager());
        mTabsAdapter.AddTabInfo(MemoryFragment.class, "Memory", null);
        mTabsAdapter.AddTabInfo(CpuFragment.class, "CPU", null);
        mTabsAdapter.AddTabInfo(NetworkFragment.class, "Network", null);

        mViewPager.setAdapter(mTabsAdapter);
        mTabLayout.setupWithViewPager(mViewPager);
    }

    private static class TabsAdapter extends FragmentPagerAdapter {

        private final ArrayList<TabInfo> mTabInfos = new ArrayList<TabInfo>();
        private final Context mContext;

        static final class TabInfo {
            private final Class<?> mClass;
            private final String mDisplayName;
            private final Bundle mArgs;

            TabInfo(Class<?> klass, String name, Bundle args) {
                mClass = klass;
                mDisplayName = name;
                mArgs = args;
            }
        }

        public TabsAdapter(Activity activity, FragmentManager fm) {
            super(fm);

            mContext = activity;
        }

        public void AddTabInfo(Class<?> klass, String displayName, Bundle args) {
            mTabInfos.add(new TabInfo(klass, displayName, args));
        }

        @Override
        public Fragment getItem(int pos) {
            TabInfo info = mTabInfos.get(pos);
            return Fragment.instantiate(mContext, info.mClass.getName(), info.mArgs);
        }

        @Override
        public int getCount() {
            return mTabInfos.size();
        }

        @Override
        public CharSequence getPageTitle(int pos) {
            TabInfo info = mTabInfos.get(pos);
            return info.mDisplayName;
        }
    }
}
