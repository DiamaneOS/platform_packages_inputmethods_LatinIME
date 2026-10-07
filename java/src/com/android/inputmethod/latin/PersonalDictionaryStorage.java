/* SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */

package com.android.inputmethod.latin;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.UserManager;
import android.preference.PreferenceManager;
import android.util.Log;

import com.android.inputmethod.annotations.UsedForTesting;
import com.android.inputmethod.compat.CompatUtils;
import com.android.inputmethod.latin.common.FileUtils;
import com.android.inputmethod.latin.settings.Settings;
import com.android.inputmethod.latin.utils.ExecutorUtils;

import java.io.File;
import java.io.FilenameFilter;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nullable;

/**
 * Storage of the personal dictionaries: the copy of the user (personal) dictionary, contact names
 * and learned words ({@link UserBinaryDictionary}, {@link ContactsBinaryDictionary} and
 * {@link com.android.inputmethod.latin.personalization.UserHistoryDictionary}), and of the recent
 * emoji.
 *
 * The app keeps device-protected storage as its default because the keyboard has to work before
 * the first unlock, when the device passphrase is typed with it. Device-protected storage can be
 * read before that unlock, so these dictionaries are kept only in credential-protected storage
 * and are only opened once the user has unlocked. Before that the main dictionary is the only
 * one in use and no recent emoji are shown. If credential-protected storage cannot be reached,
 * the personal dictionaries and recent emoji are not used at all rather than being put in
 * device-protected storage.
 */
public final class PersonalDictionaryStorage {
    private static final String TAG = PersonalDictionaryStorage.class.getSimpleName();

    // Context#createCredentialProtectedStorageContext() is a system API, which the public SDK
    // that this app is built against does not include.
    private static final Method METHOD_createCredentialProtectedStorageContext =
            CompatUtils.getMethod(Context.class, "createCredentialProtectedStorageContext");

    // File name stems of the personal dictionaries: UserBinaryDictionary.NAME,
    // ContactsBinaryDictionary.NAME and UserHistoryDictionary.NAME. LatinIME names its files
    // without a prefix and AndroidSpellCheckerService with "spellcheck_", for example
    // "contacts.en_US.dict" and "spellcheck_contacts.en_US.dict". Writing and migrating a
    // dictionary adds siblings with the same start (".tmp", ".migrate" and ".migrating").
    private static final String CONTACTS_DICT_NAME_STEM = "contacts";
    private static final String[] DICT_NAME_STEMS =
            { "userunigram", CONTACTS_DICT_NAME_STEM, "UserHistoryDictionary" };
    private static final String[] DICT_NAME_PREFIXES = { "", "spellcheck_" };

    // Preferences file in credential-protected storage for the recent emoji, which earlier builds
    // kept in the default preferences in device-protected storage.
    private static final String RECENT_EMOJI_PREFS_NAME = "recent_emoji";

    private static final AtomicBoolean sDeviceProtectedCopiesDeleted = new AtomicBoolean(false);

    private PersonalDictionaryStorage() {
        // This utility class is not publicly instantiable.
    }

    /**
     * Returns whether the personal dictionaries can be used: the user has unlocked the device
     * since it started and credential-protected storage can be reached.
     */
    public static boolean isAvailable(final Context context) {
        return isUserUnlocked(context) && getCredentialProtectedContext(context) != null;
    }

    private static boolean isUserUnlocked(final Context context) {
        final UserManager userManager = context.getSystemService(UserManager.class);
        return userManager != null && userManager.isUserUnlocked();
    }

    /**
     * Returns a context whose storage is credential-protected, or null if there is none.
     */
    @Nullable
    @UsedForTesting
    public static Context getCredentialProtectedContext(final Context context) {
        final Object result = CompatUtils.invoke(context, null /* defaultValue */,
                METHOD_createCredentialProtectedStorageContext);
        if (!(result instanceof Context) || ((Context) result).isDeviceProtectedStorage()) {
            return null;
        }
        return (Context) result;
    }

    /**
     * Returns the directory for personal dictionary files, the files directory in
     * credential-protected storage, or null before the user unlocks the device or if there is no
     * such storage.
     */
    @Nullable
    public static File getFilesDir(final Context context) {
        if (!isUserUnlocked(context)) {
            return null;
        }
        final Context credentialProtectedContext = getCredentialProtectedContext(context);
        return credentialProtectedContext == null ? null
                : credentialProtectedContext.getFilesDir();
    }

    /**
     * Returns the preferences that hold the recent emoji, in credential-protected storage, or null
     * before the user unlocks the device or if there is no such storage.
     */
    @Nullable
    public static SharedPreferences getRecentEmojiPreferences(final Context context) {
        if (!isUserUnlocked(context)) {
            return null;
        }
        final Context credentialProtectedContext = getCredentialProtectedContext(context);
        return credentialProtectedContext == null ? null
                : credentialProtectedContext.getSharedPreferences(
                        RECENT_EMOJI_PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Deletes, in the background and once per process, the personal dictionary files and the
     * recent emoji that earlier builds kept in device-protected storage. Their contents are not
     * moved: the user dictionary and contacts copies are rebuilt from their providers in
     * credential-protected storage, and learned words, which are off by default, and recent
     * emoji are lost once. Moving them would leave them readable before the first unlock.
     */
    public static void deleteDeviceProtectedCopies(final Context context) {
        if (!sDeviceProtectedCopiesDeleted.compareAndSet(false, true)) {
            return;
        }
        final Context deviceProtectedContext = context.createDeviceProtectedStorageContext();
        ExecutorUtils.getBackgroundExecutor(ExecutorUtils.KEYBOARD).execute(new Runnable() {
            @Override
            public void run() {
                deleteFromDeviceProtectedStorage(deviceProtectedContext);
            }
        });
    }

    /**
     * Deletes on the calling thread the personal dictionary files and the recent emoji that
     * earlier builds kept in device-protected storage. {@link SystemBroadcastReceiver} calls this
     * at boot, as it kills the process when this is not the current keyboard, before any
     * dictionary is set up.
     */
    static void deleteDeviceProtectedCopiesNow(final Context context) {
        deleteFromDeviceProtectedStorage(context.createDeviceProtectedStorageContext());
    }

    private static void deleteFromDeviceProtectedStorage(final Context deviceProtectedContext) {
        if (!deletePersonalDictionaryFiles(deviceProtectedContext.getFilesDir())) {
            Log.e(TAG, "Cannot remove personal dictionaries from device-protected storage.");
        }
        final SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(deviceProtectedContext);
        if (prefs.contains(Settings.PREF_EMOJI_RECENT_KEYS)
                && !prefs.edit().remove(Settings.PREF_EMOJI_RECENT_KEYS).commit()) {
            Log.e(TAG, "Cannot remove recent emoji from device-protected storage.");
        }
    }

    /**
     * Deletes, in the background, the contacts dictionaries of every locale whose file names
     * start with the given prefix, so that contact names are not kept once they are not used,
     * for example after contact suggestions are turned off or READ_CONTACTS is revoked. This
     * does nothing before the user unlocks the device. The dictionaries use the same background
     * executor, so closing them, when queued before, is done first.
     */
    public static void deleteContactsDictionaries(final Context context,
            final String dictNamePrefix) {
        final File filesDir = getFilesDir(context);
        if (filesDir == null) {
            return;
        }
        ExecutorUtils.getBackgroundExecutor(ExecutorUtils.KEYBOARD).execute(new Runnable() {
            @Override
            public void run() {
                if (!deleteContactsDictionaryFiles(filesDir, dictNamePrefix)) {
                    Log.e(TAG, "Cannot remove unused contacts dictionaries.");
                }
            }
        });
    }

    /**
     * Deletes the contacts dictionary files and directories with the given name prefix directly
     * in the given directory.
     *
     * @return false if some of them could not be deleted.
     */
    @UsedForTesting
    static boolean deleteContactsDictionaryFiles(final File dir, final String dictNamePrefix) {
        if (!dir.isDirectory()) {
            return true;
        }
        final String nameStart = dictNamePrefix + CONTACTS_DICT_NAME_STEM + ".";
        return FileUtils.deleteFilteredFiles(dir, new FilenameFilter() {
            @Override
            public boolean accept(final File parent, final String name) {
                return name.startsWith(nameStart);
            }
        });
    }

    /**
     * Deletes the personal dictionary files and directories directly in the given directory.
     *
     * @return false if some of them could not be deleted.
     */
    @UsedForTesting
    static boolean deletePersonalDictionaryFiles(final File dir) {
        if (!dir.isDirectory()) {
            // Nothing was ever stored there.
            return true;
        }
        return FileUtils.deleteFilteredFiles(dir, new FilenameFilter() {
            @Override
            public boolean accept(final File parent, final String name) {
                return isPersonalDictionaryFileName(name);
            }
        });
    }

    @UsedForTesting
    static boolean isPersonalDictionaryFileName(final String name) {
        for (final String prefix : DICT_NAME_PREFIXES) {
            for (final String stem : DICT_NAME_STEMS) {
                if (name.startsWith(prefix + stem + ".")) {
                    return true;
                }
            }
        }
        return false;
    }
}
