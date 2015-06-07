package com.afollestad.impression.ui.base;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.graphics.BitmapFactory;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;

import com.afollestad.impression.R;
import com.afollestad.impression.fragments.dialog.ColorChooserDialog;
import com.afollestad.materialdialogs.ThemeSingleton;

/**
 * @author Aidan Follestad (afollestad)
 */
public abstract class ThemedActivity extends AppCompatActivity {

    private boolean mLastDarkTheme;
    private int mLastPrimaryColor;
    private int mLastAccentColor;
    private boolean mLastColoredNav;

    protected int darkTheme() {
        return R.style.AppTheme_Dark;
    }

    protected int lightTheme() {
        return R.style.AppTheme;
    }

    public boolean isDarkTheme() {
        return PreferenceManager.getDefaultSharedPreferences(this).getBoolean("dark_theme", false);
    }

    public int primaryColor() {
        String key = "primary_color";
        if (mLastDarkTheme) key += "_dark";
        else key += "_light";
        final int defaultColor = getResources().getColor(mLastDarkTheme ?
                R.color.dark_theme_gray : R.color.material_indigo_500);
        return PreferenceManager.getDefaultSharedPreferences(this).getInt(key, defaultColor);
    }

    protected void primaryColor(int newColor) {
        String key = "primary_color";
        if (mLastDarkTheme) key += "_dark";
        else key += "_light";
        PreferenceManager.getDefaultSharedPreferences(this).edit().putInt(key, newColor).commit();
    }

    public int primaryColorDark() {
        return ColorChooserDialog.shiftColor(primaryColor());
    }

    public int accentColor() {
        String key = "accent_color";
        if (mLastDarkTheme) key += "_dark";
        else key += "_light";
        final int defaultColor = getResources().getColor(R.color.material_pink_500);
        return PreferenceManager.getDefaultSharedPreferences(this).getInt(key, defaultColor);
    }

    protected void accentColor(int newColor) {
        String key = "accent_color";
        if (mLastDarkTheme) key += "_dark";
        else key += "_light";
        PreferenceManager.getDefaultSharedPreferences(this).edit().putInt(key, newColor).commit();
    }

    public boolean isColoredNavBar() {
        return PreferenceManager.getDefaultSharedPreferences(this).getBoolean("colored_navbar", true);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mLastDarkTheme = isDarkTheme();
        mLastPrimaryColor = primaryColor();
        mLastAccentColor = accentColor();
        mLastColoredNav = isColoredNavBar();
        ThemeSingleton.get().positiveColor = mLastAccentColor;
        ThemeSingleton.get().neutralColor = mLastAccentColor;
        ThemeSingleton.get().negativeColor = mLastAccentColor;
        ThemeSingleton.get().widgetColor = mLastAccentColor;
        setTheme(mLastDarkTheme ? darkTheme() : lightTheme());
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // Sets color of entry in the system recents page
            ActivityManager.TaskDescription td = new ActivityManager.TaskDescription(
                    getString(R.string.app_name),
                    BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher),
                    primaryColor());
            setTaskDescription(td);
        }

        if (getSupportActionBar() != null)
            getSupportActionBar().setBackgroundDrawable(new ColorDrawable(primaryColor()));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && hasColoredBars()) {
            final int dark = primaryColorDark();
            if (allowStatusBarColoring())
                getWindow().setStatusBarColor(dark);
            else
                getWindow().setStatusBarColor(getResources().getColor(android.R.color.transparent));
            if (mLastColoredNav)
                getWindow().setNavigationBarColor(dark);
        }
    }

    protected boolean allowStatusBarColoring() {
        return false;
    }

    protected boolean hasColoredBars() {
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean darkTheme = isDarkTheme();
        int primaryColor = primaryColor();
        int accentColor = accentColor();
        boolean coloredNav = isColoredNavBar();
        if (darkTheme != mLastDarkTheme || primaryColor != mLastPrimaryColor ||
                accentColor != mLastAccentColor || coloredNav != mLastColoredNav) {
            recreate();
        }
    }
}
