/* SPDX-License-Identifier: Apache-2.0
 * Copyright 2026 The DiamaneOS Project
 */

package com.android.inputmethod.latin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.accounts.AccountManager;
import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.inputmethod.dictionarypack.DictionaryPackConstants;
import com.android.inputmethod.dictionarypack.DictionarySettingsActivity;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Checks the privacy-relevant parts of the manifest.
 */
// ComponentInfo#directBootAware is from API 24, the app's minimum.
@TargetApi(Build.VERSION_CODES.N)
@SmallTest
@RunWith(AndroidJUnit4.class)
public class ManifestPrivacyTests {
    private static final String[] UNUSED_PERMISSIONS = {
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.DOWNLOAD_WITHOUT_NOTIFICATION",
            "android.permission.GET_ACCOUNTS",
            "android.permission.READ_PROFILE",
            "android.permission.READ_SYNC_SETTINGS",
            "android.permission.READ_SYNC_STATS",
            "android.permission.USE_CREDENTIALS",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.WRITE_SYNC_SETTINGS",
    };

    private Context getContext() {
        return InstrumentationRegistry.getTargetContext();
    }

    @Test
    public void testBackupIsDisabled() {
        final ApplicationInfo applicationInfo = getContext().getApplicationInfo();
        assertEquals(0, applicationInfo.flags & ApplicationInfo.FLAG_ALLOW_BACKUP);
    }

    @Test
    public void testUnusedPermissionsAreNotRequested() throws Exception {
        final Context context = getContext();
        final PackageInfo packageInfo = context.getPackageManager().getPackageInfo(
                context.getPackageName(), PackageManager.GET_PERMISSIONS);
        final List<String> requestedPermissions = packageInfo.requestedPermissions == null
                ? Collections.<String>emptyList()
                : Arrays.asList(packageInfo.requestedPermissions);
        for (final String permission : UNUSED_PERMISSIONS) {
            assertFalse(permission, requestedPermissions.contains(permission));
        }
    }

    @Test
    public void testDictionaryPackUpdaterIsNotReachable() {
        final Context context = getContext();
        final String[] actions = {
                DictionaryPackConstants.UPDATE_NOW_INTENT_ACTION,
                DictionaryPackConstants.INIT_AND_UPDATE_NOW_INTENT_ACTION,
        };
        for (final String action : actions) {
            final Intent intent = new Intent(action).setPackage(context.getPackageName());
            assertTrue(action, context.getPackageManager()
                    .queryBroadcastReceivers(intent, 0 /* flags */).isEmpty());
        }
    }

    @Test
    public void testDictionarySettingsActivityIsNotExported() throws Exception {
        final Context context = getContext();
        assertFalse(context.getPackageManager().getActivityInfo(
                new ComponentName(context, DictionarySettingsActivity.class), 0 /* flags */)
                .exported);
    }

    @Test
    public void testAccountsChangedReceiverIsNotDeclared() {
        final Context context = getContext();
        final Intent intent = new Intent(AccountManager.LOGIN_ACCOUNTS_CHANGED_ACTION)
                .setPackage(context.getPackageName());
        assertTrue(context.getPackageManager()
                .queryBroadcastReceivers(intent, 0 /* flags */).isEmpty());
    }

    @Test
    public void testKeyboardIsDirectBootAware() throws Exception {
        // The device passphrase is typed with this keyboard before the first unlock.
        final Context context = getContext();
        assertTrue(context.getPackageManager().getServiceInfo(
                new ComponentName(context, LatinIME.class), 0 /* flags */).directBootAware);
    }
}
