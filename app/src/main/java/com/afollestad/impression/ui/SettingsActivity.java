package com.afollestad.impression.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.TwoStatePreference;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;

import com.afollestad.impression.R;
import com.afollestad.impression.fragments.dialog.ColorChooserDialog;
import com.afollestad.impression.ui.base.ThemedActivity;
import com.afollestad.impression.utils.Utils;
import com.afollestad.impression.views.ImpressionPreference;
import com.afollestad.materialdialogs.MaterialDialog;

/**
 * @author Aidan Follestad (afollestad)
 */
public class SettingsActivity extends ThemedActivity implements ColorChooserDialog.ColorCallback {

    private static final int EXCLUDED_REQUEST = 8000;

    @Override
    protected int darkTheme() {
        return R.style.AppTheme_Settings_Dark;
    }

    @Override
    protected int lightTheme() {
        return R.style.AppTheme_Settings;
    }

    @Override
    protected boolean allowStatusBarColoring() {
        return true;
    }

    @Override
    public void onColorSelection(int title, int color) {
        if (title == R.string.primary_color)
            primaryColor(color);
        else
            accentColor(color);
        recreate();
    }

    public static class SettingsFragment extends PreferenceFragment {

        private void invalidateOverviewMode() {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
            TwoStatePreference overviewMode = (TwoStatePreference) findPreference("overview_mode");
            final int currentMode = prefs.getInt("overview_mode", 1);
            overviewMode.setChecked(currentMode == 0);
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.settings);

            findPreference("about").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    try {
                        Activity a = getActivity();
                        PackageInfo pInfo = a.getPackageManager().getPackageInfo(a.getPackageName(), 0);
                        new MaterialDialog.Builder(a)
                                .title(getString(R.string.about_dialog_title, pInfo.versionName))
                                .positiveText(R.string.dismiss)
                                .content(Html.fromHtml(getString(R.string.about_body)))
                                .iconRes(R.drawable.ic_launcher)
                                .build()
                                .show();
                    } catch (PackageManager.NameNotFoundException e) {
                        e.printStackTrace();
                    }
                    return true;
                }
            });

            invalidateOverviewMode();
            findPreference("overview_mode").setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    PreferenceManager.getDefaultSharedPreferences(getActivity()).edit()
                            .putInt("overview_mode", ((Boolean) newValue) ? 0 : 1).commit();
                    invalidateOverviewMode();
                    return true;
                }
            });

            findPreference("excluded_folders").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    getActivity().startActivityForResult(new Intent(getActivity(), ExcludedFolderActivity.class), EXCLUDED_REQUEST);
                    return false;
                }
            });

            findPreference("dark_theme").setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    if (getActivity() != null)
                        getActivity().recreate();
                    return true;
                }
            });

            findPreference("colored_navbar").setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    if (getActivity() != null)
                        getActivity().recreate();
                    return true;
                }
            });

            findPreference("include_subfolders").setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    getActivity().setResult(RESULT_OK);
                    return true;
                }
            });

            ImpressionPreference primaryColor = (ImpressionPreference) findPreference("primary_color");
            primaryColor.setColor(((ThemedActivity) getActivity()).primaryColor(), Utils.resolveColor(getActivity(), R.attr.colorAccent));
            primaryColor.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    new ColorChooserDialog().show(getActivity(), preference.getTitleRes(),
                            ((ThemedActivity) getActivity()).primaryColor());
                    return true;
                }
            });


            ImpressionPreference accentColor = (ImpressionPreference) findPreference("accent_color");
            accentColor.setColor(((ThemedActivity) getActivity()).accentColor(), Utils.resolveColor(getActivity(), R.attr.colorAccent));
            accentColor.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    new ColorChooserDialog().show(getActivity(), preference.getTitleRes(),
                            ((ThemedActivity) getActivity()).accentColor());
                    return true;
                }
            });
        }

        @Override
        public void onViewCreated(View view, Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            ListView list = (ListView) view.findViewById(android.R.id.list);
            list.setDivider(null);
            list.setDividerHeight(0);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.preference_activity_custom);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setBackgroundColor(primaryColor());
        setSupportActionBar(toolbar);
        //noinspection ConstantConditions
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        if (savedInstanceState == null)
            getFragmentManager().beginTransaction().replace(R.id.content_frame, new SettingsFragment()).commit();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == EXCLUDED_REQUEST) {
            setResult(resultCode);
        }
    }
}