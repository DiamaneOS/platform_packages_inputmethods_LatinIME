/* SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */

package com.android.inputmethod.latin;

import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import androidx.test.filters.LargeTest;

import com.android.inputmethod.latin.common.Constants;
import com.android.inputmethod.latin.common.LocaleUtils;
import com.android.inputmethod.latin.settings.Settings;

/**
 * Tests that fields with {@link EditorInfo#IME_FLAG_NO_PERSONALIZED_LEARNING}, such as incognito
 * browser tabs, neither teach the keyboard words nor get learned words suggested.
 *
 * Like InputLogicTests#testAutoCorrectByUserHistory, these tests rely on "qpmz" being
 * auto-corrected from "qpmx" only once "qpmz" has been learned. A word that is not in the main
 * dictionary is learned only when it is typed the second time: the first time adds it to the user
 * history with a count of 0, which is never suggested.
 */
@LargeTest
public class InputLogicTestsNoPersonalizedLearning extends InputTestsBase {
    private static final String LEARNED_WORD = "qpmz";
    private static final String SIMILAR_WORD = "qpmx";
    // The only test that starts in a field with IME_FLAG_NO_PERSONALIZED_LEARNING. The others
    // start in a field that allows learning.
    private static final String TEST_STARTING_WITH_FLAG =
            "testNothingIsLearnedWithNoPersonalizedLearningFlag";

    private boolean mPreviousUsePersonalizedDicts;

    @Override
    protected EditorInfo enrichEditorInfo(final EditorInfo ei) {
        if (TEST_STARTING_WITH_FLAG.equals(getName())) {
            ei.imeOptions |= EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING;
        }
        return ei;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // Learned words are off by default. Turn them on and set up the dictionaries again so
        // that the user history dictionary is in use.
        mPreviousUsePersonalizedDicts = setBooleanPreference(
                Settings.PREF_KEY_USE_PERSONALIZED_DICTS, true, false /* defaultValue */);
        mLatinIME.loadSettings();
        mLatinIME.replaceDictionariesForTest(LocaleUtils.constructLocaleFromString("en_US"));
        waitForDictionariesToBeLoaded();
        mLatinIME.clearPersonalizedDictionariesForTest();
    }

    @Override
    protected void tearDown() throws Exception {
        mLatinIME.clearPersonalizedDictionariesForTest();
        setBooleanPreference(Settings.PREF_KEY_USE_PERSONALIZED_DICTS,
                mPreviousUsePersonalizedDicts, false /* defaultValue */);
        super.tearDown();
    }

    // Types the given word and a space, and returns the text this added to the field.
    private String typeWordAndSpace(final String word) {
        final int startIndex = mEditText.getText().length();
        type(word);
        type(Constants.CODE_SPACE);
        final int endIndex = mEditText.getText().length();
        return mEditText.getText().subSequence(startIndex, endIndex).toString();
    }

    // Types the word to learn as many times as learning it takes in a field that allows it.
    private void typeLearnedWord() {
        typeWordAndSpace(LEARNED_WORD);
        typeWordAndSpace(LEARNED_WORD);
    }

    // Finishes input in the current field and starts it in a new one of the same editor.
    private void startInputInNewField(final boolean noPersonalizedLearning) {
        mLatinIME.onFinishInputView(true /* finishingInput */);
        mLatinIME.onFinishInput();
        runMessages();
        final EditorInfo ei = new EditorInfo();
        final InputConnection ic = mEditText.onCreateInputConnection(ei);
        if (noPersonalizedLearning) {
            ei.imeOptions |= EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING;
        }
        mLatinIME.onCreateInputMethodInterface().startInput(ic, ei);
        mLatinIME.onStartInputView(ei, false /* restarting */);
        mInputConnection = ic;
        sleep(DELAY_TO_WAIT_FOR_PREDICTIONS_MILLIS);
        runMessages();
        assertEquals(noPersonalizedLearning,
                Settings.getInstance().getCurrent().mInputAttributes.mNoPersonalizedLearning);
    }

    public void testLearnedWordIsUsedWithLearning() {
        assertFalse(Settings.getInstance().getCurrent().mInputAttributes.mNoPersonalizedLearning);
        typeLearnedWord();
        assertEquals("auto-corrected by user history", LEARNED_WORD + " ",
                typeWordAndSpace(SIMILAR_WORD));
    }

    public void testNothingIsLearnedWithNoPersonalizedLearningFlag() {
        assertTrue(Settings.getInstance().getCurrent().mInputAttributes.mNoPersonalizedLearning);
        typeLearnedWord();
        // Learned words are not suggested in the incognito field either, so this checks in a
        // field that allows learning that the word has not been learned.
        startInputInNewField(false /* noPersonalizedLearning */);
        assertFalse("auto-corrected by a word typed in an incognito field",
                (LEARNED_WORD + " ").equals(typeWordAndSpace(SIMILAR_WORD)));
    }

    public void testLearnedWordIsNotUsedWithNoPersonalizedLearningFlag() {
        typeLearnedWord();
        // Checks that the word has been learned, then that it is not used in an incognito field.
        startInputInNewField(false /* noPersonalizedLearning */);
        assertEquals("auto-corrected by user history", LEARNED_WORD + " ",
                typeWordAndSpace(SIMILAR_WORD));
        startInputInNewField(true /* noPersonalizedLearning */);
        assertFalse("auto-corrected by user history in an incognito field",
                (LEARNED_WORD + " ").equals(typeWordAndSpace(SIMILAR_WORD)));
    }
}
