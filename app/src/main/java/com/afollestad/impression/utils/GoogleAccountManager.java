package com.afollestad.impression.utils;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class GoogleAccountManager {

    private static final String ACC_NAME = "account_name";
    private static final int FAIL = -1;
    private static final int UNCHANGED = 0;
    private static final int CHANGED = +1;

    private String mCurrentEmail = null;  // cache locally
    private final Context context;

    public GoogleAccountManager(Context context) {
        this.context = context;
    }

    private Account[] getAll() {
        return AccountManager.get(appContext(context)).getAccountsByType("com.google");
    }

    public String[] getAllEmails() {
        Account[] all = getAll();
        String[] emails = new String[all.length];
        for (int i = 0; i < all.length; i++) {
            emails[i] = all[i].name;
        }
        return emails;
    }

    public Account getPrimary() {
        Account[] accts = getAll();
        return accts == null || accts.length == 0 ? null : accts[0];
    }

    public Account getActive() {
        return getAccountFromEmail(getActiveEmail());
    }

    private String getActiveEmail() {
        if (mCurrentEmail != null) {
            return mCurrentEmail;
        }
        mCurrentEmail = context == null ? null : prefs(context).getString(ACC_NAME, null);
        return mCurrentEmail;
    }

    private Account getAccountFromEmail(String email) {
        if (email != null) {
            Account[] accounts =
                    AccountManager.get(appContext(context)).getAccountsByType("com.google");
            for (Account account : accounts) {
                if (email.equalsIgnoreCase(account.name)) {
                    return account;
                }
            }
        }
        return null;
    }

    /**
     * Stores a new email in persistent app storage, reporting result
     *
     * @param newEmail new email, optionally null
     * @return FAIL, CHANGED or UNCHANGED (based on the following table)
     * OLD    NEW   SAVED   RESULT
     * ERROR                FAIL
     * null   null  null    FAIL
     * null   new   new     CHANGED
     * old    null  old     UNCHANGED
     * old != new   new     CHANGED
     * old == new   new     UNCHANGED
     */
    public int setEmail(String newEmail) {
        int result = FAIL;  // 0  0
        String prevEmail = getActiveEmail();
        if ((prevEmail == null) && (newEmail != null)) {
            result = CHANGED;
        } else if ((prevEmail != null) && (newEmail == null)) {
            result = UNCHANGED;
        } else if (prevEmail != null) {
            result = prevEmail.equalsIgnoreCase(newEmail) ? UNCHANGED : CHANGED;
        }
        if (result == CHANGED) {
            mCurrentEmail = newEmail;
            prefs(context).edit().putString(ACC_NAME, newEmail).apply();
        }
        return result;
    }

    private Context appContext(Context context) {
        return context == null ? null : context.getApplicationContext();
    }

    private SharedPreferences prefs(Context context) {
        return context == null ? null : PreferenceManager.getDefaultSharedPreferences(appContext(context));
    }
}