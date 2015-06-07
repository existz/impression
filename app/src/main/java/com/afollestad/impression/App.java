package com.afollestad.impression;

import android.app.Activity;
import android.app.Application;

import com.afollestad.impression.accounts.base.Account;

public class App extends Application {

    private Account mCurrentAccount;

    public static void getCurrentAccount(Activity context, Account.AccountCallback callback) {
        ((App) context.getApplication()).getCurrentAccount(callback);
    }

    private void getCurrentAccount(final Account.AccountCallback callback) {
        final int current = Account.getActive(App.this);
        if (mCurrentAccount != null && mCurrentAccount.id() == current) {
            callback.onAccount(mCurrentAccount);
            return;
        }

        Account.getAll(this, new Account.AccountsCallback() {
            @Override
            public void onAccounts(Account[] accounts) {
                for (Account a : accounts) {
                    if (a.id() == current) {
                        mCurrentAccount = a;
                        break;
                    }
                }
                callback.onAccount(mCurrentAccount);
            }
        });
    }
}