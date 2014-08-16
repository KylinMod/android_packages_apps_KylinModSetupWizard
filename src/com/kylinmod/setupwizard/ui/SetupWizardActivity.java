/*
 * Copyright (C) 2013 The KylinMod OpenSource Project
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

package com.kylinmod.setupwizard.ui;

import android.accounts.*;
import android.content.pm.ThemeUtils;
import android.content.res.ThemeManager;
import android.content.res.ThemeManager.ThemeChangeListener;
import com.kylinmod.setupwizard.KMSetupWizard;
import com.kylinmod.setupwizard.R;
import com.kylinmod.setupwizard.gcm.GCMUtil;
import com.kylinmod.setupwizard.setup.*;
import com.kylinmod.setupwizard.util.KMAccountUtils;
import com.kylinmod.setupwizard.util.EnableAccessibilityController;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AppGlobals;
import android.app.Dialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.support.v13.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;

import java.io.IOException;
import java.util.List;

public class SetupWizardActivity extends Activity implements SetupDataCallbacks,
    ThemeChangeListener {

    private static final String TAG = SetupWizardActivity.class.getSimpleName();

    private static final String GOOGLE_SETUPWIZARD_PACKAGE = "com.google.android.setupwizard";
    private static final String KEY_SIM_MISSING_SHOWN = "sim-missing-shown";
    private static final String KEY_G_ACCOUNT_SHOWN = "g-account-shown";

    private static final int DIALOG_FINISHING = 1;

    private ViewPager mViewPager;
    private KMPagerAdapter mPagerAdapter;

    private Button mNextButton;
    private Button mPrevButton;

    private PageList mPageList;

    private AbstractSetupData mSetupData;

    private final Handler mHandler = new Handler();

    private SharedPreferences mSharedPreferences;
    private boolean mSetupComplete = false;
    private boolean mGoogleAccountSetupComplete = false;
    private boolean mTriedEnablingWifiOnce;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.setup_main);
        ((KMSetupWizard) AppGlobals.getInitialApplication()).disableStatusBar();
        mSharedPreferences = getSharedPreferences(KMSetupWizard.SETTINGS_PREFERENCES, Context.MODE_PRIVATE);
        mSetupData = (AbstractSetupData)getLastNonConfigurationInstance();
        if (mSetupData == null) {
            mSetupData = new KMSetupWizardData(this);
        }

        if (savedInstanceState != null) {
            mSetupData.load(savedInstanceState.getBundle("data"));
        } else {
            mSharedPreferences.edit().putBoolean(KEY_SIM_MISSING_SHOWN, false).commit();
        }
        mNextButton = (Button) findViewById(R.id.next_button);
        mPrevButton = (Button) findViewById(R.id.prev_button);
        mSetupData.registerListener(this);
        mPagerAdapter = new KMPagerAdapter(getFragmentManager());
        mViewPager = (ViewPager) findViewById(R.id.pager);
        mViewPager.setPageTransformer(true, new DepthPageTransformer());
        mViewPager.setAdapter(mPagerAdapter);
        mViewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                if (position < mPageList.size()) {
                    onPageLoaded(mPageList.get(position));
                }
            }
        });
        mNextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                doNext();
            }
        });
        mPrevButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                doPrevious();
            }
        });
        final EnableAccessibilityController acc = new EnableAccessibilityController(this);
        mViewPager.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return acc.onInterceptTouchEvent(event);
            }
        });
        onPageTreeChanged();
        removeUnNeededPages();
    }

    @Override
    protected void onResume() {
        super.onResume();
        onPageTreeChanged();
        if (!KMAccountUtils.isNetworkConnected(this) && mTriedEnablingWifiOnce) {
            KMAccountUtils.tryEnablingWifi(this);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mSetupData.unregisterListener(this);
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        return mSetupData;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBundle("data", mSetupData.save());
    }

    @Override
    protected Dialog onCreateDialog(int id, Bundle args) {
        switch (id) {
            case DIALOG_FINISHING:
                return new AlertDialog.Builder(this)
                        .setTitle(R.string.setup_finalizing)
                        .setCancelable(false)
                        .setView(getLayoutInflater().inflate(R.layout.setup_finalizing, null))
                        .create();
            default:
                return super.onCreateDialog(id, args);
        }
    }

    @Override
    public void onBackPressed() {
        doPrevious();
    }

    public void doNext() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                final int currentItem = mViewPager.getCurrentItem();
                final Page currentPage = mPageList.get(currentItem);

                switch (currentPage.getId()) {
                    case R.string.setup_complete:
                        finishSetup();
                        break;
                    case R.string.setup_welcome:
                        onPageFinished(currentPage);
                        // fall through
                    default:
                        mViewPager.setCurrentItem(currentItem + 1, true);
                }
            }
        });
    }

    public void doPrevious() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                final int currentItem = mViewPager.getCurrentItem();
                if (currentItem > 0) {
                    mViewPager.setCurrentItem(currentItem - 1, true);
                }
            }
        });
    }

    private void removeSetupPage(final Page page, boolean animate) {
        if (page == null || getPage(page.getKey()) == null || page.getId() == R.string.setup_complete) return;
        final int position = mViewPager.getCurrentItem();
        if (animate) {
            mViewPager.setCurrentItem(0);
            mSetupData.removePage(page);
            mViewPager.setCurrentItem(position, true);
        } else {
            mSetupData.removePage(page);
        }
        onPageLoaded(mPageList.get(position));
    }

    private void updateNextPreviousState() {
        final int position = mViewPager.getCurrentItem();
        mNextButton.setEnabled(position != mPagerAdapter.getCutOffPage());
        mPrevButton.setVisibility(position <= 0 ? View.INVISIBLE : View.VISIBLE);
    }

    @Override
    public void onPageLoaded(Page page) {
        mNextButton.setText(page.getNextButtonResId());
        if (page.isRequired()) {
            if (recalculateCutOffPage()) {
                mPagerAdapter.notifyDataSetChanged();
            }
        }
        updateNextPreviousState();
    }

    @Override
    public void onPageTreeChanged() {
        mPageList = mSetupData.getPageList();
        recalculateCutOffPage();
        mPagerAdapter.notifyDataSetChanged();
        updateNextPreviousState();
    }

    @Override
    public Page getPage(String key) {
        return mSetupData.findPage(key);
    }

    @Override
    public Page getPage(int key) {
        return mSetupData.findPage(key);
    }

    @Override
    public void onPageFinished(final Page page) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (page == null) {
                    doNext();
                } else {
                    switch (page.getId()) {
                        case R.string.setup_welcome:
                            if (!mTriedEnablingWifiOnce) {
                                mTriedEnablingWifiOnce = true;
                                KMAccountUtils.launchWifiSetup(SetupWizardActivity.this);
                            }
                            break;
                        case R.string.setup_google_account:
                            if (mGoogleAccountSetupComplete) {
                                removeSetupPage(page, false);
                            }
                            break;
                    }
                }
                onPageTreeChanged();
            }
        });
    }

    private boolean recalculateCutOffPage() {
        // Cut off the pager adapter at first required page that isn't completed
        int cutOffPage = mPageList.size();
        for (int i = 0; i < mPageList.size(); i++) {
            Page page = mPageList.get(i);
            if (page.isRequired() && !page.isCompleted()) {
                cutOffPage = i;
                break;
            }
        }

        if (mPagerAdapter.getCutOffPage() != cutOffPage) {
            mPagerAdapter.setCutOffPage(cutOffPage);
            return true;
        }

        return false;
    }

    private void removeUnNeededPages() {
        boolean pagesRemoved = false;
        Page page = mPageList.findPage(R.string.setup_google_account);
        if (page != null && (!GCMUtil.googleServicesExist(SetupWizardActivity.this) || accountExists(KMSetupWizard.ACCOUNT_TYPE_GOOGLE))
                || MKAccountUtils.isUnableToModifyAccounts(SetupWizardActivity.this)) {
            removeSetupPage(page, false);
            pagesRemoved = true;
        }
        page = mPageList.findPage(R.string.setup_personalization);
        if (page != null && PersonalizationPage.skipPage(this)) {
            removeSetupPage(page, false);
            pagesRemoved = true;
        }
        if (pagesRemoved) {
            onPageTreeChanged();
        }
    }

    private void disableSetupWizards(Intent intent) {
        final PackageManager pm = getPackageManager();
        final List<ResolveInfo> resolveInfos = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        for (ResolveInfo info : resolveInfos) {
            if (GOOGLE_SETUPWIZARD_PACKAGE.equals(info.activityInfo.packageName)) {
                final ComponentName componentName = new ComponentName(info.activityInfo.packageName, info.activityInfo.name);
                pm.setComponentEnabledSetting(componentName, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
            }
        }
        pm.setComponentEnabledSetting(getComponentName(), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
    }

    public void launchGoogleAccountSetup() {
        Bundle bundle = new Bundle();
        bundle.putBoolean(KMSetupWizard.EXTRA_FIRST_RUN, true);
        bundle.putBoolean(KMSetupWizard.EXTRA_ALLOW_SKIP, true);
        AccountManager.get(this).addAccount(KMSetupWizard.ACCOUNT_TYPE_GOOGLE, null, null, bundle, this, new AccountManagerCallback<Bundle>() {
            @Override
            public void run(AccountManagerFuture<Bundle> bundleAccountManagerFuture) {
                if (isDestroyed()) return; //There is a change this activity has been torn down.
                String token = null;
                try {
                    token = bundleAccountManagerFuture.getResult().getString(AccountManager.KEY_AUTHTOKEN);
                    mGoogleAccountSetupComplete = true;
                    Page page = mPageList.findPage(R.string.setup_google_account);
                    if (page != null) {
                        onPageFinished(page);
                    }
                } catch (OperationCanceledException e) {
                } catch (IOException e) {
                } catch (AuthenticatorException e) {
                }

            }
        }, null);
    }

    private void finishSetup() {
        if (mSetupComplete) return;
        mSetupComplete = true;
        boolean applyingDefaultTheme = handleDefaultThemeSetup();

        Settings.Global.putInt(getContentResolver(), Settings.Global.DEVICE_PROVISIONED, 1);
        Settings.Secure.putInt(getContentResolver(), Settings.Secure.USER_SETUP_COMPLETE, 1);
        UserManager.get(this).setUserName(UserHandle.myUserId(), getString(com.android.internal.R.string.owner_name));
        ((KMSetupWizard) AppGlobals.getInitialApplication()).enableStatusBar();
        if (!applyingDefaultTheme)  {
            finalizeSetup();
        } else {
            showDialog(DIALOG_FINISHING);
        }
    }

    private boolean accountExists(String accountType) {
        return AccountManager.get(this).getAccountsByType(accountType).length > 0;
    }

    private boolean handleDefaultThemeSetup() {
        Page page = getPage(R.string.setup_personalization);
        if (page == null) {
            return false;
        }
        Bundle privacyData = page.getData();
        if (privacyData != null && privacyData.getBoolean("apply_default_theme")) {
            Log.d(TAG, "Applying default theme");
            ThemeManager tm = (ThemeManager) this.getSystemService(Context.THEME_SERVICE);
            tm.addClient(ThemeUtils.getDefaultThemePackageName(this), this);
            tm.applyDefaultTheme();
            return true;
        }
        return false;
    }

    @Override
    public void onProgress(int progress) {}

    @Override
    public void onFinish(boolean isSuccess) {
        removeDialog(DIALOG_FINISHING);
        ThemeManager tm = (ThemeManager) this.getSystemService(Context.THEME_SERVICE);
        tm.removeClient(ThemeUtils.getDefaultThemePackageName(this));
        finalizeSetup();
    }

    private void finalizeSetup() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        disableSetupWizards(intent);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private class KMPagerAdapter extends FragmentStatePagerAdapter {

        private int mCutOffPage;

        private KMPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int i) {
            return mPageList.get(i).createFragment();
        }

        @Override
        public int getItemPosition(Object object) {
            return POSITION_NONE;
        }

        @Override
        public int getCount() {
            if (mPageList == null)
                return 0;
            return Math.min(mCutOffPage, mPageList.size());
        }

        public void setCutOffPage(int cutOffPage) {
            if (cutOffPage < 0) {
                cutOffPage = Integer.MAX_VALUE;
            }
            mCutOffPage = cutOffPage;
        }

        public int getCutOffPage() {
            return mCutOffPage;
        }
    }

    public static class DepthPageTransformer implements ViewPager.PageTransformer {
        private static float MIN_SCALE = 0.5f;

        public void transformPage(View view, float position) {
            int pageWidth = view.getWidth();

            if (position < -1) {
                view.setAlpha(0);

            } else if (position <= 0) { // [-1,0]
                // Use the default slide transition when moving to the left page
                view.setAlpha(1);
                view.setTranslationX(0);
                view.setScaleX(1);
                view.setScaleY(1);

            } else if (position <= 1) { // (0,1]
                // Fade the page out.
                view.setAlpha(1 - position);

                // Counteract the default slide transition
                view.setTranslationX(pageWidth * -position);

                // Scale the page down (between MIN_SCALE and 1)
                float scaleFactor = MIN_SCALE
                        + (1 - MIN_SCALE) * (1 - Math.abs(position));
                view.setScaleX(scaleFactor);
                view.setScaleY(scaleFactor);

            } else { // (1,+Infinity]
                // This page is way off-screen to the right.
                view.setAlpha(0);
            }
        }
    }
}
