package com.afollestad.impression.providers;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import com.afollestad.impression.accounts.GoogleDriveAccount;
import com.afollestad.impression.accounts.LocalAccount;
import com.afollestad.impression.accounts.base.Account;
import com.afollestad.impression.providers.base.ProviderBase;

/**
 * @author Shirwa Mohamed (shirwaM)
 */
public class AccountProvider extends ProviderBase {

    private final static String COLUMNS = "_id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, type INTEGER";

    public AccountProvider() {
        super("account", COLUMNS);
    }

    public final static Uri CONTENT_URI = Uri.parse("content://com.afollestad.impression.accounts");

    public static Account add(Context context, String name, int type) {
        ContentValues values = new ContentValues();
        values.put("name", name);
        values.put("type", type);
        context.getContentResolver().insert(CONTENT_URI, values);
        Cursor cursor = context.getContentResolver().query(CONTENT_URI, null, null, null, null);
        cursor.moveToLast();
        int id = cursor.getInt(0);
        Account acc;
        switch (type) {
            default:
                acc = new LocalAccount(context, id);
                break;
            case Account.TYPE_GOOGLE_DRIVE:
                acc = new GoogleDriveAccount(context, name, id);
                break;
        }
        cursor.close();
        return acc;
    }

    public static void remove(Context context, Account account) {
        context.getContentResolver().delete(CONTENT_URI, "_id = " + account.id(), null);
    }
}