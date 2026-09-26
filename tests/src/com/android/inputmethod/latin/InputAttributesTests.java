/* SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */

package com.android.inputmethod.latin;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.text.InputType;
import android.view.inputmethod.EditorInfo;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class InputAttributesTests {
    private static final String PACKAGE_NAME = "com.android.inputmethod.latin";

    private static final int TEXT = InputType.TYPE_CLASS_TEXT;
    private static final int NUMBER = InputType.TYPE_CLASS_NUMBER;
    private static final int[] PASSWORD_INPUT_TYPES = {
            TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD,
            TEXT | InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD,
    };

    private static EditorInfo newEditorInfo(final int inputType, final int imeOptions) {
        final EditorInfo editorInfo = new EditorInfo();
        editorInfo.inputType = inputType;
        editorInfo.imeOptions = imeOptions;
        return editorInfo;
    }

    private static InputAttributes newInputAttributes(final int inputType, final int imeOptions) {
        return new InputAttributes(newEditorInfo(inputType, imeOptions),
                false /* isFullscreenMode */, PACKAGE_NAME);
    }

    @Test
    public void testLearningAllowedInTextField() {
        final InputAttributes attributes = newInputAttributes(
                TEXT | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT, EditorInfo.IME_ACTION_SEND);
        assertFalse(attributes.mNoPersonalizedLearning);
    }

    @Test
    public void testNoLearningWithNoPersonalizedLearningFlag() {
        assertTrue(newInputAttributes(TEXT | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT,
                EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING).mNoPersonalizedLearning);
        assertTrue(newInputAttributes(TEXT | InputType.TYPE_TEXT_VARIATION_URI,
                EditorInfo.IME_ACTION_GO | EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)
                .mNoPersonalizedLearning);
        // The flag counts outside text fields as well.
        assertTrue(newInputAttributes(NUMBER, EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)
                .mNoPersonalizedLearning);
    }

    @Test
    public void testNoLearningInPasswordFields() {
        for (final int inputType : PASSWORD_INPUT_TYPES) {
            final InputAttributes attributes =
                    newInputAttributes(inputType, EditorInfo.IME_ACTION_DONE);
            assertTrue("inputType=" + inputType, attributes.mIsPasswordField);
            assertTrue("inputType=" + inputType, attributes.mNoPersonalizedLearning);
        }
    }

    @Test
    public void testObscuredPasswordFields() {
        assertTrue(newInputAttributes(TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD,
                EditorInfo.IME_ACTION_DONE).mIsObscuredPasswordField);
        assertTrue(newInputAttributes(NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD,
                EditorInfo.IME_ACTION_DONE).mIsObscuredPasswordField);
        // Visible password fields show their text.
        assertFalse(newInputAttributes(TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                EditorInfo.IME_ACTION_DONE).mIsObscuredPasswordField);
        assertFalse(newInputAttributes(TEXT | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT,
                EditorInfo.IME_ACTION_DONE).mIsObscuredPasswordField);
    }

    @Test
    public void testNoLearningWithoutEditorInfo() {
        final InputAttributes attributes =
                new InputAttributes(null /* editorInfo */, false /* isFullscreenMode */,
                        PACKAGE_NAME);
        assertFalse(attributes.mNoPersonalizedLearning);
    }

    @Test
    public void testIsSameInputTypeFollowsNoPersonalizedLearningFlag() {
        final int inputType = TEXT | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT;
        final InputAttributes normal = newInputAttributes(inputType, EditorInfo.IME_ACTION_GO);
        final InputAttributes incognito = newInputAttributes(inputType,
                EditorInfo.IME_ACTION_GO | EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        final EditorInfo normalEditorInfo = newEditorInfo(inputType, EditorInfo.IME_ACTION_GO);
        final EditorInfo incognitoEditorInfo = newEditorInfo(inputType,
                EditorInfo.IME_ACTION_GO | EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);

        assertTrue(normal.isSameInputType(normalEditorInfo));
        assertTrue(incognito.isSameInputType(incognitoEditorInfo));
        // Turning the flag on or off in the same field needs the attributes reloaded.
        assertFalse(normal.isSameInputType(incognitoEditorInfo));
        assertFalse(incognito.isSameInputType(normalEditorInfo));
        // Other IME options do not.
        assertTrue(normal.isSameInputType(newEditorInfo(inputType, EditorInfo.IME_ACTION_SEND)));
        // In a password field the flag changes nothing.
        final int passwordInputType = TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD;
        final InputAttributes password =
                newInputAttributes(passwordInputType, EditorInfo.IME_ACTION_DONE);
        assertTrue(password.isSameInputType(newEditorInfo(passwordInputType,
                EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)));
    }
}
