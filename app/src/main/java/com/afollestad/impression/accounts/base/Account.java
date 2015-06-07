package com.afollestad.impression.accounts.base;

import android.content.Context;
import android.database.Cursor;
import android.os.Handler;
import android.preference.PreferenceManager;

import com.afollestad.impression.accounts.LocalAccount;
import com.afollestad.impression.adapters.MediaAdapter;
import com.afollestad.impression.api.AlbumEntry;
import com.afollestad.impression.api.base.MediaEntry;
import com.afollestad.impression.providers.AccountProvider;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Aidan Follestad (afollestad)
 */
public abstract class Account {

    private final Context mContext;

    protected Account(Context context) {
        mContext = context;
    }

    protected Context getContext() {
        return mContext;
    }

    private final static Object LOCK = new Object();
    private static Account[] mAccountsSingleton;

    public static void getAll(final Context context, final AccountsCallback callback) {
        synchronized (LOCK) {
            if (mAccountsSingleton != null) {
                callback.onAccounts(mAccountsSingleton);
                return;
            }

            final Handler mHandler = new Handler();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    final List<Account> results = new ArrayList<>();
                    Cursor cursor = context.getContentResolver().query(AccountProvider.CONTENT_URI, null, null, null, null);
                    while (cursor.moveToNext()) {
                        int id = cursor.getInt(0);
                        int type = cursor.getInt(cursor.getColumnIndex("type"));
                        switch (type) {
                            case TYPE_LOCAL:
                                results.add(new LocalAccount(context, id));
                                break;
                            case TYPE_GOOGLE_DRIVE:
                                // TODO
                                break;
                            case TYPE_DROPBOX:
                                // TODO
                                break;
                        }
                    }
                    cursor.close();
                    if (callback != null) {
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                mAccountsSingleton = results.toArray(new Account[results.size()]);
                                callback.onAccounts(mAccountsSingleton);
                            }
                        });
                    }
                }
            }).start();
        }
    }

    public static int getActive(Context context) {
        if (context == null) return 0;
        return PreferenceManager.getDefaultSharedPreferences(context).getInt("active_account", -1);
    }

    public static void setActive(Context context, Account acc) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putInt("active_account", acc.id()).commit();
    }


    public abstract int id();

    public abstract int type();

    public abstract String name();

    public abstract boolean hasIncludedFolders();

    public abstract void getAlbums(MediaAdapter.SortMode sort, MediaAdapter.FileFilterMode filter, AlbumCallback callback);

    public abstract void getIncludedFolders(AlbumEntry[] preEntries, AlbumCallback callback);

    public abstract void getEntries(String albumPath, int overviewMode, boolean explorerMode, MediaAdapter.FileFilterMode filter, MediaAdapter.SortMode sort, EntriesCallback callback);


    public static abstract class AlbumCallback {

        public abstract void onAlbums(AlbumEntry[] albums);

        public abstract void onError(Exception e);
    }

    public interface AccountCallback {
        void onAccount(Account account);
    }

    public interface AccountsCallback {
        void onAccounts(Account[] accounts);
    }

    public interface EntriesCallback {
        void onEntries(MediaEntry[] entries);

        void onError(Exception e);
    }

    public static final int TYPE_LOCAL = 1;
    public static final int TYPE_GOOGLE_DRIVE = 2;
    public static final int TYPE_DROPBOX = 3;
}