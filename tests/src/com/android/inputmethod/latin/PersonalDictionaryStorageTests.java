/* SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */

package com.android.inputmethod.latin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.inputmethod.latin.common.FileUtils;
import com.android.inputmethod.latin.settings.Settings;
import com.android.inputmethod.latin.spellcheck.AndroidSpellCheckerService;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

// Device-protected storage APIs are from API 24, the app's minimum.
@TargetApi(Build.VERSION_CODES.N)
@MediumTest
@RunWith(AndroidJUnit4.class)
public class PersonalDictionaryStorageTests {
    // Keeps the files of these tests apart from the keyboard's own dictionaries.
    private static final String TEST_DICT_NAME_PREFIX = "test_personal_storage_";

    private Context getContext() {
        return InstrumentationRegistry.getTargetContext();
    }

    @Test
    public void testCredentialProtectedStorageIsReachable() {
        final Context context = getContext();
        // The app defaults to device-protected storage.
        assertTrue(context.isDeviceProtectedStorage());
        // Instrumentation tests run on an unlocked device.
        assertTrue(PersonalDictionaryStorage.isAvailable(context));
        final Context credentialProtectedContext =
                PersonalDictionaryStorage.getCredentialProtectedContext(context);
        assertNotNull(credentialProtectedContext);
        assertFalse(credentialProtectedContext.isDeviceProtectedStorage());
    }

    @Test
    public void testDictFileIsInCredentialProtectedStorage() {
        final Context context = getContext();
        final File dictFile = ExpandableBinaryDictionary.getDictFile(
                context, TEST_DICT_NAME_PREFIX + "contacts.en_US", null /* dictFile */);
        final File credentialProtectedFilesDir =
                PersonalDictionaryStorage.getCredentialProtectedContext(context).getFilesDir();
        final File deviceProtectedFilesDir =
                context.createDeviceProtectedStorageContext().getFilesDir();
        assertEquals(credentialProtectedFilesDir, dictFile.getParentFile());
        assertFalse(deviceProtectedFilesDir.equals(dictFile.getParentFile()));
        assertFalse(context.getFilesDir().equals(dictFile.getParentFile()));
    }

    @Test
    public void testGivenDictFileIsKept() {
        final File givenFile = new File(getContext().getCacheDir(), "given.dict");
        assertSame(givenFile,
                ExpandableBinaryDictionary.getDictFile(getContext(), "unused", givenFile));
    }

    @Test
    public void testPersonalDictionaryFileNames() {
        final String[] personalNames = {
                "userunigram.en_US.dict",
                "contacts.en_US.dict",
                "UserHistoryDictionary.en_US.dict",
                "spellcheck_userunigram.en_US.dict",
                "spellcheck_contacts.en_US.dict",
                "contacts.en_US.dict.tmp",
                "UserHistoryDictionary.en_US.dict.migrating",
        };
        for (final String name : personalNames) {
            assertTrue(name, PersonalDictionaryStorage.isPersonalDictionaryFileName(name));
        }
        final String[] otherNames = {
                "dicts",
                "staging",
                "tmp",
                "main_en.dict",
                "contacts",
                "contactsfoo.en_US.dict",
                "spellcheck_main.en_US.dict",
        };
        for (final String name : otherNames) {
            assertFalse(name, PersonalDictionaryStorage.isPersonalDictionaryFileName(name));
        }
    }

    @Test
    public void testDeletePersonalDictionaryFiles() throws IOException {
        final File dir = new File(getContext().getCacheDir(), TEST_DICT_NAME_PREFIX + "dir");
        FileUtils.deleteRecursively(dir);
        assertTrue(dir.mkdirs());
        try {
            // Version 4 dictionaries are directories of files.
            final File userDict = newDictDirectory(dir, "userunigram.en_US.dict");
            final File contactsDict = newDictDirectory(dir, "spellcheck_contacts.en_US.dict");
            final File historyDict = newDictDirectory(dir, "UserHistoryDictionary.en_US.dict");
            final File mainDicts = newDictDirectory(dir, "dicts");

            assertTrue(PersonalDictionaryStorage.deletePersonalDictionaryFiles(dir));

            assertFalse(userDict.exists());
            assertFalse(contactsDict.exists());
            assertFalse(historyDict.exists());
            assertTrue(mainDicts.exists());
        } finally {
            FileUtils.deleteRecursively(dir);
        }
    }

    @Test
    public void testDeleteContactsDictionaryFiles() throws IOException {
        final File dir = new File(getContext().getCacheDir(), TEST_DICT_NAME_PREFIX + "dir");
        FileUtils.deleteRecursively(dir);
        assertTrue(dir.mkdirs());
        try {
            final File contactsDict = newDictDirectory(dir, "contacts.en_US.dict");
            final File otherLocaleContactsDict = newDictDirectory(dir, "contacts.de.dict");
            final File spellCheckerContactsDict =
                    newDictDirectory(dir, "spellcheck_contacts.en_US.dict");
            final File userDict = newDictDirectory(dir, "userunigram.en_US.dict");

            assertTrue(PersonalDictionaryStorage.deleteContactsDictionaryFiles(dir, ""));

            assertFalse(contactsDict.exists());
            assertFalse(otherLocaleContactsDict.exists());
            // Other name prefixes and other dictionaries are kept.
            assertTrue(spellCheckerContactsDict.exists());
            assertTrue(userDict.exists());
        } finally {
            FileUtils.deleteRecursively(dir);
        }
    }

    @Test
    public void testDeleteDeviceProtectedCopiesNow() throws IOException {
        final Context context = getContext();
        final File deviceProtectedFilesDir =
                context.createDeviceProtectedStorageContext().getFilesDir();
        // No personal dictionary is kept in device-protected storage, so this name is unused.
        final File contactsDict = new File(deviceProtectedFilesDir, "contacts.zz_ZZ.dict");
        FileUtils.deleteRecursively(contactsDict);
        try {
            newDictDirectory(deviceProtectedFilesDir, contactsDict.getName());
            PersonalDictionaryStorage.deleteDeviceProtectedCopiesNow(context);
            assertFalse(contactsDict.exists());
        } finally {
            FileUtils.deleteRecursively(contactsDict);
        }
    }

    @Test
    public void testRecentEmojiAreInCredentialProtectedStorage() {
        final Context context = getContext();
        final SharedPreferences prefs =
                PersonalDictionaryStorage.getRecentEmojiPreferences(context);
        assertNotNull(prefs);
        final File credentialProtectedPrefsDir = new File(PersonalDictionaryStorage
                .getCredentialProtectedContext(context).getDataDir(), "shared_prefs");
        final File deviceProtectedPrefsDir = new File(
                context.createDeviceProtectedStorageContext().getDataDir(), "shared_prefs");
        final File prefsFile = new File(credentialProtectedPrefsDir, "recent_emoji.xml");
        final boolean prefsFileExisted = prefsFile.exists();
        // A key of this test only, which leaves the keyboard's recent emoji as they are.
        final String testKey = TEST_DICT_NAME_PREFIX + "key";
        try {
            assertTrue(prefs.edit().putString(testKey, "value").commit());
            assertTrue(prefsFile.exists());
            assertFalse(new File(deviceProtectedPrefsDir, prefsFile.getName()).exists());
        } finally {
            prefs.edit().remove(testKey).commit();
            if (!prefsFileExisted) {
                PersonalDictionaryStorage.getCredentialProtectedContext(context)
                        .deleteSharedPreferences("recent_emoji");
            }
        }
    }

    @Test
    public void testDeleteDeviceProtectedCopiesNowRemovesRecentEmoji() {
        final Context context = getContext();
        final SharedPreferences deviceProtectedPrefs = PreferenceManager
                .getDefaultSharedPreferences(context.createDeviceProtectedStorageContext());
        // Recent emoji are no longer kept in device-protected storage, so this key is unused.
        assertTrue(deviceProtectedPrefs.edit()
                .putString(Settings.PREF_EMOJI_RECENT_KEYS, "[{\"Integer\":128512}]").commit());
        try {
            PersonalDictionaryStorage.deleteDeviceProtectedCopiesNow(context);
            assertFalse(deviceProtectedPrefs.contains(Settings.PREF_EMOJI_RECENT_KEYS));
        } finally {
            deviceProtectedPrefs.edit().remove(Settings.PREF_EMOJI_RECENT_KEYS).commit();
        }
    }

    @Test
    public void testPersonalDictionariesAreUsedOnceUnlocked() throws InterruptedException {
        final Context context = getContext();
        // Instrumentation tests run on an unlocked device.
        assertTrue(PersonalDictionaryStorage.isAvailable(context));
        final DictionaryFacilitatorImpl facilitator = new DictionaryFacilitatorImpl();
        try {
            facilitator.resetDictionaries(context, Locale.US, false /* useContactsDict */,
                    false /* usePersonalizedDicts */, false /* forceReloadMainDictionary */,
                    null /* account */, TEST_DICT_NAME_PREFIX, null /* listener */);
            facilitator.waitForLoadingDictionariesForTesting(5, TimeUnit.SECONDS);
            assertNotNull(facilitator.getSubDictForTesting(Dictionary.TYPE_USER));
            assertNull(facilitator.getSubDictForTesting(Dictionary.TYPE_USER_HISTORY));
            assertFalse(facilitator.needsResetAfterUserUnlock(context));
        } finally {
            closeAndDeleteTestDictionaries(facilitator);
        }
    }

    @Test
    public void testUnusedContactsDictionariesAreDeleted() throws IOException {
        final Context context = getContext();
        final File contactsDict = new File(PersonalDictionaryStorage.getFilesDir(context),
                TEST_DICT_NAME_PREFIX + "contacts.en_US.dict");
        FileUtils.deleteRecursively(contactsDict);
        newDictDirectory(contactsDict.getParentFile(), contactsDict.getName());
        final DictionaryFacilitatorImpl facilitator = new DictionaryFacilitatorImpl();
        try {
            facilitator.resetDictionaries(context, Locale.US, false /* useContactsDict */,
                    false /* usePersonalizedDicts */, false /* forceReloadMainDictionary */,
                    null /* account */, TEST_DICT_NAME_PREFIX, null /* listener */);
            // The deletion is queued on the executor that the dictionaries use.
            facilitator.getSubDictForTesting(Dictionary.TYPE_USER).waitAllTasksForTests();
            assertFalse(contactsDict.exists());
        } finally {
            closeAndDeleteTestDictionaries(facilitator);
        }
    }

    @Test
    public void testUnusedSpellCheckerContactsDictionariesAreDeleted() throws IOException {
        final Context context = getContext();
        // The spell checker's contacts dictionaries have a fixed name prefix. No locale has this
        // name, so this file is of this test only.
        final File contactsDict = new File(PersonalDictionaryStorage.getFilesDir(context),
                AndroidSpellCheckerService.DICTIONARY_NAME_PREFIX + "contacts.zz_ZZ.dict");
        FileUtils.deleteRecursively(contactsDict);
        newDictDirectory(contactsDict.getParentFile(), contactsDict.getName());
        // With its contacts setting off, the spell checker cannot use contact names, whether
        // READ_CONTACTS is granted or not.
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        final boolean previousUseContacts =
                AndroidSpellCheckerService.isContactsDictionaryEnabled(prefs);
        assertTrue(prefs.edit()
                .putBoolean(AndroidSpellCheckerService.PREF_USE_CONTACTS_KEY, false).commit());
        final DictionaryFacilitatorImpl facilitator = new DictionaryFacilitatorImpl();
        try {
            // A dictionary setup of the keyboard, not of the spell checker.
            facilitator.resetDictionaries(context, Locale.US, false /* useContactsDict */,
                    false /* usePersonalizedDicts */, false /* forceReloadMainDictionary */,
                    null /* account */, TEST_DICT_NAME_PREFIX, null /* listener */);
            // The deletion is queued on the executor that the dictionaries use.
            facilitator.getSubDictForTesting(Dictionary.TYPE_USER).waitAllTasksForTests();
            assertFalse(contactsDict.exists());
        } finally {
            prefs.edit().putBoolean(AndroidSpellCheckerService.PREF_USE_CONTACTS_KEY,
                    previousUseContacts).commit();
            FileUtils.deleteRecursively(contactsDict);
            closeAndDeleteTestDictionaries(facilitator);
        }
    }

    private void closeAndDeleteTestDictionaries(final DictionaryFacilitatorImpl facilitator) {
        final ExpandableBinaryDictionary userDict =
                facilitator.getSubDictForTesting(Dictionary.TYPE_USER);
        facilitator.closeDictionaries();
        if (userDict != null) {
            userDict.waitAllTasksForTests();
        }
        FileUtils.deleteFilteredFiles(PersonalDictionaryStorage.getFilesDir(getContext()),
                new FilenameFilter() {
                    @Override
                    public boolean accept(final File dir, final String name) {
                        return name.startsWith(TEST_DICT_NAME_PREFIX);
                    }
                });
    }

    private static File newDictDirectory(final File parent, final String name)
            throws IOException {
        final File dictDir = new File(parent, name);
        assertTrue(dictDir.mkdirs());
        assertTrue(new File(dictDir, name + ".header").createNewFile());
        return dictDir;
    }
}
