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

package com.kylinmod.setupwizard.widget;

import android.content.Context;
import android.content.pm.PackageManager;
import android.view.inputmethod.InputMethodInfo;

public class InputMethodItem {
    private String imLabel;
    private String imPackage;

    public InputMethodItem(Context context, InputMethodInfo info) {
        PackageManager pm = context.getPackageManager();
        imLabel = info.loadLabel(pm).toString();
        imPackage = getRealName(info);
    }

    private String getRealName(InputMethodInfo info) {
        String packageName = info.getPackageName();
        return packageName + "/" + info.getServiceName().replace(packageName, "");
    }

    public String getImLabel() {
        return imLabel;
    }

    public String getImPackage() {
        return imPackage;
    }

}
