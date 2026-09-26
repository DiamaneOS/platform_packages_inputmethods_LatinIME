/* SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */

package com.android.inputmethod.latin.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.inputmethod.latin.InputAttributes;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests that key preview popups, which show each pressed key enlarged, are off in password fields
 * whose text is hidden whatever the user setting is.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class SettingsValuesKeyPreviewTests {
    private static final int TEXT = InputType.TYPE_CLASS_TEXT;
    private static final int[] PASSWORD_INPUT_TYPES = {
            TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD,
            TEXT | InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD,
    };

    private SharedPreferences mPrefs;
    private boolean mHadPopupSetting;
    private boolean mPreviousPopupSetting;

    private Context getContext() {
        return InstrumentationRegistry.getTargetContext();
    }

    @Before
    public void setUp() {
        mPrefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        mHadPopupSetting = mPrefs.contains(Settings.PREF_POPUP_ON);
        mPreviousPopupSetting = mPrefs.getBoolean(Settings.PREF_POPUP_ON, false);
        // The user asks for key preview popups.
        mPrefs.edit().putBoolean(Settings.PREF_POPUP_ON, true).commit();
    }

    @After
    public void tearDown() {
        if (mHadPopupSetting) {
            mPrefs.edit().putBoolean(Settings.PREF_POPUP_ON, mPreviousPopupSetting).commit();
        } else {
            mPrefs.edit().remove(Settings.PREF_POPUP_ON).commit();
        }
    }

    private SettingsValues newSettingsValues(final int inputType) {
        final Context context = getContext();
        final EditorInfo editorInfo = new EditorInfo();
        editorInfo.inputType = inputType;
        editorInfo.packageName = context.getPackageName();
        return new SettingsValues(context, mPrefs, context.getResources(),
                new InputAttributes(editorInfo, false /* isFullscreenMode */,
                        context.getPackageName()));
    }

    @Test
    public void testKeyPreviewPopupFollowsSettingInTextFields() {
        // The setting can be hidden on some devices, in which case the default applies.
        assertEquals(Settings.readKeyPreviewPopupEnabled(mPrefs, getContext().getResources()),
                newSettingsValues(TEXT | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT)
                        .mKeyPreviewPopupOn);
    }

    @Test
    public void testKeyPreviewPopupFollowsSettingInVisiblePasswordFields() {
        // Visible password fields show their text, and apps also use them for text that is not
        // secret.
        assertEquals(Settings.readKeyPreviewPopupEnabled(mPrefs, getContext().getResources()),
                newSettingsValues(TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)
                        .mKeyPreviewPopupOn);
    }

    @Test
    public void testNoKeyPreviewPopupInPasswordFields() {
        for (final int inputType : PASSWORD_INPUT_TYPES) {
            assertFalse("inputType=" + inputType, newSettingsValues(inputType).mKeyPreviewPopupOn);
        }
    }
}
