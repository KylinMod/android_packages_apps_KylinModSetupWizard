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

package com.kylinmod.setupwizard;

import android.app.Application;
import android.app.StatusBarManager;
import android.content.Context;

public class KMSetupWizard extends Application {

    public static final String TAG = "KMSetupWizard";
    // Leave this off for release
    public static final boolean DEBUG = false;

    public static final String ACCOUNT_TYPE_GOOGLE = "com.google";

    public static final String ACTION_SETUP_WIFI = "com.android.net.wifi.SETUP_WIFI_NETWORK";

    public static final String EXTRA_FIRST_RUN = "firstRun";
    public static final String EXTRA_ALLOW_SKIP = "allowSkip";
    public static final String EXTRA_SHOW_SKIP = "extra_prefs_show_button_bar";
    public static final String EXTRA_AUTO_FINISH = "wifi_auto_finish_on_connect";
    public static final String EXTRA_SHOW_BUTTON_BAR = "extra_prefs_show_button_bar";
    public static final String EXTRA_PREF_BACK_TEXT = "extra_prefs_set_back_text";
    public static final String EXTRA_ONLY_ACCESS_POINTS = "only_access_points";

    public static final String GCM_PREFERENCES = "com.kylinmod.setupwizard.gcm";
    public static final String SETTINGS_PREFERENCES = "com.kylinmod.setupwizard_preferences";

    public static final int REQUEST_CODE_SETUP_WIFI = 0;

    private StatusBarManager mStatusBarManager;

    @Override
    public void onCreate() {
        super.onCreate();
        mStatusBarManager = (StatusBarManager) getSystemService(Context.STATUS_BAR_SERVICE);
    }

    public void disableStatusBar() {
        mStatusBarManager.disable(StatusBarManager.DISABLE_EXPAND
                | StatusBarManager.DISABLE_NOTIFICATION_ALERTS
                | StatusBarManager.DISABLE_NOTIFICATION_TICKER | StatusBarManager.DISABLE_RECENT
                | StatusBarManager.DISABLE_HOME
                | StatusBarManager.DISABLE_SEARCH);
    }

    public void enableStatusBar() {
        mStatusBarManager.disable(StatusBarManager.DISABLE_NONE);
    }

}
