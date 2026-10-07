/* SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */

package com.android.inputmethod.keyboard.emoji;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.os.Build;
import android.view.ContextThemeWrapper;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.inputmethod.keyboard.Key;
import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.keyboard.KeyboardId;
import com.android.inputmethod.keyboard.KeyboardLayoutSet;
import com.android.inputmethod.keyboard.KeyboardTheme;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.RichInputMethodManager;
import com.android.inputmethod.latin.RichInputMethodSubtype;
import com.android.inputmethod.latin.settings.Settings;
import com.android.inputmethod.latin.utils.ResourceUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;

/**
 * Tests that the recent emoji are shown, added and saved only while there is a place to save
 * them, which there is not before the user unlocks the device or in fields that ask for no
 * personalized learning.
 */
// Context#deleteSharedPreferences() is from API 24, the app's minimum.
@TargetApi(Build.VERSION_CODES.N)
@SmallTest
@RunWith(AndroidJUnit4.class)
public class DynamicGridKeyboardRecentsTests {
    // A preferences file of these tests only, apart from the keyboard's own recent emoji.
    private static final String TEST_PREFS_NAME = "test_dynamic_grid_keyboard_recents";
    private static final int MAX_KEY_COUNT = 10;
    // DynamicGridKeyboard lays out keys by the template keys "0" and "1".
    private static final int TEMPLATE_KEY_CODE_0 = 0x30;

    private Context mContext;
    private KeyboardLayoutSet mLayoutSet;
    private Keyboard mTemplateKeyboard;
    private Key mKey;
    private SharedPreferences mPrefs;

    @Before
    public void setUp() {
        final Context targetContext = InstrumentationRegistry.getTargetContext();
        RichInputMethodManager.init(targetContext);
        final KeyboardTheme keyboardTheme = KeyboardTheme.getKeyboardTheme(targetContext);
        mContext = new ContextThemeWrapper(targetContext, keyboardTheme.mStyleId);
        KeyboardLayoutSet.onKeyboardThemeChanged();
        // Built as in EmojiPalettesView.
        final KeyboardLayoutSet.Builder builder =
                new KeyboardLayoutSet.Builder(mContext, null /* editorInfo */);
        builder.setSubtype(RichInputMethodSubtype.getEmojiSubtype());
        builder.setKeyboardGeometry(ResourceUtils.getDefaultKeyboardWidth(mContext),
                new EmojiLayoutParams(mContext).mEmojiKeyboardHeight);
        mLayoutSet = builder.build();
        mTemplateKeyboard = mLayoutSet.getKeyboard(KeyboardId.ELEMENT_EMOJI_RECENTS);
        for (final Key key : mTemplateKeyboard.getSortedKeys()) {
            if (key.getCode() == TEMPLATE_KEY_CODE_0) {
                mKey = key;
            }
        }
        assertNotNull(mKey);
        mContext.deleteSharedPreferences(TEST_PREFS_NAME);
        mPrefs = mContext.getSharedPreferences(TEST_PREFS_NAME, Context.MODE_PRIVATE);
    }

    @After
    public void tearDown() {
        mContext.deleteSharedPreferences(TEST_PREFS_NAME);
    }

    private DynamicGridKeyboard newRecentsKeyboard() {
        return new DynamicGridKeyboard(mTemplateKeyboard, MAX_KEY_COUNT, EmojiCategory.ID_RECENTS);
    }

    @Test
    public void testNothingIsShownOrSavedWithoutPreferences() {
        final DynamicGridKeyboard recents = newRecentsKeyboard();
        recents.loadRecentKeys(Collections.<DynamicGridKeyboard>emptyList(), null /* prefs */);
        recents.addKeyFirst(mKey);
        recents.addPendingKey(mKey);
        recents.flushPendingRecentKeys();
        assertTrue(recents.getSortedKeys().isEmpty());
    }

    @Test
    public void testRecentKeysAreSavedAndLoaded() {
        final DynamicGridKeyboard recents = newRecentsKeyboard();
        recents.loadRecentKeys(Collections.<DynamicGridKeyboard>emptyList(), mPrefs);
        recents.addKeyFirst(mKey);
        assertEquals(1, recents.getSortedKeys().size());
        assertFalse(Settings.readEmojiRecentKeys(mPrefs).isEmpty());

        // The saved code is looked up among the keys of the given keyboards.
        final DynamicGridKeyboard reloaded = newRecentsKeyboard();
        reloaded.loadRecentKeys(Arrays.asList(recents), mPrefs);
        assertEquals(1, reloaded.getSortedKeys().size());
        assertEquals(TEMPLATE_KEY_CODE_0, reloaded.getSortedKeys().get(0).getCode());
    }

    @Test
    public void testRecentKeysAreHiddenAndKeptWithoutPreferences() {
        final DynamicGridKeyboard recents = newRecentsKeyboard();
        recents.loadRecentKeys(Collections.<DynamicGridKeyboard>emptyList(), mPrefs);
        recents.addKeyFirst(mKey);
        final String saved = Settings.readEmojiRecentKeys(mPrefs);

        // As in an incognito field, after one that allows learning.
        recents.loadRecentKeys(Collections.<DynamicGridKeyboard>emptyList(), null /* prefs */);
        assertTrue(recents.getSortedKeys().isEmpty());
        recents.addKeyFirst(mKey);
        assertTrue(recents.getSortedKeys().isEmpty());
        assertEquals(saved, Settings.readEmojiRecentKeys(mPrefs));
    }

    // Built as in EmojiPalettesView, with the preferences of these tests.
    private EmojiCategory newEmojiCategory() {
        final TypedArray emojiPalettesViewAttr = mContext.obtainStyledAttributes(null /* set */,
                R.styleable.EmojiPalettesView, R.attr.emojiPalettesViewStyle,
                R.style.EmojiPalettesView);
        try {
            return new EmojiCategory(mPrefs, mContext.getResources(), mLayoutSet,
                    emojiPalettesViewAttr);
        } finally {
            emojiPalettesViewAttr.recycle();
        }
    }

    @Test
    public void testEmptyRecentsTabIsNotShown() {
        // The recents tab was the last one shown.
        Settings.writeLastShownEmojiCategoryId(mPrefs, EmojiCategory.ID_RECENTS);
        final EmojiCategory emojiCategory = newEmojiCategory();
        assertEquals(EmojiCategory.ID_RECENTS, emojiCategory.getCurrentCategoryId());

        // As before the user unlocks and in fields that ask for no personalized learning.
        emojiCategory.loadRecentKeys(null /* recentKeysPrefs */);
        final int defaultCategoryId = emojiCategory.getCategoryIdToShow();
        assertFalse(defaultCategoryId == EmojiCategory.ID_RECENTS);

        // With a recent emoji, the recents tab is shown again.
        final Key emojiKey =
                emojiCategory.getKeyboard(defaultCategoryId, 0).getSortedKeys().get(0);
        final DynamicGridKeyboard recents = newRecentsKeyboard();
        recents.loadRecentKeys(Collections.<DynamicGridKeyboard>emptyList(), mPrefs);
        recents.addKeyFirst(emojiKey);
        emojiCategory.loadRecentKeys(mPrefs);
        assertEquals(1, emojiCategory.getKeyboard(EmojiCategory.ID_RECENTS, 0)
                .getSortedKeys().size());
        assertEquals(EmojiCategory.ID_RECENTS, emojiCategory.getCategoryIdToShow());
    }
}
