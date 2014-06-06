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

package com.kylinmod.setupwizard.setup;

import com.kylinmod.setupwizard.R;
import com.kylinmod.setupwizard.ui.SetupPageFragment;
import com.kylinmod.setupwizard.widget.InputMethodItem;

import android.app.Fragment;
import android.content.ContentResolver;
import android.content.Context;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RadioGroup.OnCheckedChangeListener;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class InputMethodPage extends Page {

    private static final String TAG = InputMethodPage.class.getSimpleName();

    protected InputMethodPage(Context context, SetupDataCallbacks callbacks, int titleResourceId) {
        super(context, callbacks, titleResourceId);
    }

    @Override
    public Fragment createFragment() {
        Bundle args = new Bundle();
        args.putString(Page.KEY_PAGE_ARGUMENT, getKey());

        InputMethodFragment fragment = new InputMethodFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public int getNextButtonResId() {
        return R.string.next;
    }

    public static class InputMethodFragment extends SetupPageFragment {

        private Context mContext;

        private List<InputMethodItem> mImList;
        private List<InputMethodInfo> infoList;

        private RadioGroup mImGroup;

        private String mEnabledIM;
        private String mDefaultIM;

        private ContentResolver mContentResolver;

        @Override
        public void onActivityCreated(Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);
            mContext = getActivity();
            mContentResolver = mContext.getContentResolver();

            // get IM
            InputMethodManager manager = (InputMethodManager) mContext
                    .getSystemService(Context.INPUT_METHOD_SERVICE);
            List<InputMethodInfo> infoList = manager.getInputMethodList();
            mEnabledIM = Settings.Secure.getString(mContentResolver,
                    Settings.Secure.ENABLED_INPUT_METHODS);
            mDefaultIM = Settings.Secure.getString(mContentResolver,
                    Settings.Secure.DEFAULT_INPUT_METHOD);
            mImList = new ArrayList<InputMethodItem>();

            int total = (infoList == null ? 0 : infoList.size());
            int defindex = 0;
            if (total != 0) {
                for (int i = 0; i < total; i++) {
                    InputMethodItem inputMethodItem = new InputMethodItem(mContext, infoList.get(i));
                    mImList.add(inputMethodItem);
                    RadioButton button = new RadioButton(mContext);
                    String packageName = inputMethodItem.getImPackage();
                    button.setTag(packageName);
                    String inputMethodName = inputMethodItem.getImLabel();
                    if (packageName.contains("com.android.inputmethod.latin")){
                        inputMethodName = inputMethodName + " " + getString(R.string.setup_inputmethod_multilingual);
                    } else if (packageName.contains("com.iflytek.inputmethod")) {
                        inputMethodName = inputMethodName + " " + getString(R.string.setup_inputmethod_chinese);
                        mDefaultIM = "com.iflytek.inputmethod";
                        defindex = i;
                    }
                    button.setText(inputMethodName);
                    mImGroup.addView(button);
                }
                mImGroup.setOnCheckedChangeListener(mInputMethodChangedListener);
                mImGroup.check(mImGroup.getChildAt(defindex).getId());
            } else {
                TextView tvInfo = (TextView) mRootView.findViewById(R.id.input_method_summary);
                tvInfo.setText(getResources().getString(R.string.setup_inputmethod_none));
                tvInfo.setTextAppearance(mContext, R.style.InputMethodSummary);
            }
        }

        @Override
        protected void setUpPage() {
            mImGroup = (RadioGroup) mRootView.findViewById(R.id.input_method_group);
        }

        private OnCheckedChangeListener mInputMethodChangedListener = new OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(RadioGroup radioGroup, int checkedId) {
                RadioButton button = (RadioButton)radioGroup.findViewById(checkedId);
                String defaultTag = button.getTag().toString();
                Settings.Secure.putString(mContext.getContentResolver(),
                        Settings.Secure.DEFAULT_INPUT_METHOD, defaultTag);
                if (!mEnabledIM.contains(defaultTag)) {
                    Settings.Secure.putString(mContext.getContentResolver(),
                            Settings.Secure.ENABLED_INPUT_METHODS, mEnabledIM
                                    + ":" + defaultTag);
                }
            }
        };

        @Override
        protected int getLayoutResource() {
            return R.layout.setup_inputmethod_page;
        }

        @Override
        protected int getTitleResource() {
            return R.string.setup_inputmethod;
        }
    }
}
