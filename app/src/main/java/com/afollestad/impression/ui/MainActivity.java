package com.afollestad.impression.ui;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.SharedElementCallback;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.afollestad.impression.R;
import com.afollestad.impression.api.AlbumEntry;
import com.afollestad.impression.cab.MediaCab;
import com.afollestad.impression.fragments.MediaFragment;
import com.afollestad.impression.fragments.NavDrawerFragment;
import com.afollestad.impression.fragments.dialog.FolderSelectorDialog;
import com.afollestad.impression.providers.IncludedFolderProvider;
import com.afollestad.impression.providers.SortMemoryProvider;
import com.afollestad.impression.ui.base.ThemedActivity;
import com.afollestad.impression.utils.Utils;
import com.afollestad.impression.views.BreadCrumbLayout;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * @author Aidan Follestad (afollestad)
 */
public class MainActivity extends ThemedActivity
        implements FolderSelectorDialog.FolderCallback {

    public DrawerLayout mDrawerLayout;
    private ActionBarDrawerToggle mDrawerToggle;
    public boolean mPickMode;
    private SelectAlbumMode mSelectAlbumMode = SelectAlbumMode.NONE;
    public MediaCab mMediaCab;
    public Toolbar mToolbar;
    public BreadCrumbLayout mCrumbs;

    private CharSequence mTitle;
    public boolean drawerArrowOpen;
    private final static int SETTINGS_REQUEST = 9000;

    private static final String TAG = "MainActivity";
    private static final boolean DEBUG = true;
    public static final String EXTRA_CURRENT_ITEM_POSITION = "extra_current_item_position";
    public static final String EXTRA_OLD_ITEM_POSITION = "extra_old_item_position";
    public static final String ACTION_SELECT_ALBUM = "com.afollestad.impression.SELECT_FOLDER";

    public enum SelectAlbumMode {
        NONE,
        COPY,
        MOVE,
        CHOOSE
    }

    public RecyclerView mRecyclerView;
    public Bundle mTmpState;
    public boolean mIsReentering;

    public void animateDrawerArrow(boolean closed) {
        if (mDrawerToggle == null || drawerArrowOpen == !closed) return;
        ValueAnimator anim;
        drawerArrowOpen = !closed;
        if (closed) {
            anim = ValueAnimator.ofFloat(1f, 0f);
        } else {
            anim = ValueAnimator.ofFloat(0f, 1f);
        }
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                float slideOffset = (Float) valueAnimator.getAnimatedValue();
                mDrawerToggle.onDrawerSlide(null, slideOffset);
            }
        });
        anim.setInterpolator(new DecelerateInterpolator());
        anim.setDuration(300);
        anim.start();
    }

    public void setStatus(String status) {
        TextView view = (TextView) findViewById(R.id.status);
        if (status == null) {
            view.setVisibility(View.GONE);
        } else {
            view.setVisibility(View.VISIBLE);
            view.setText(status);
            invalidateStatusColor();
        }
    }

    public void invalidateStatusColor() {
        View toolbarFrame = findViewById(R.id.toolbar_frame);
        if (mMediaCab != null) {
            // This makes the status text view background the same as the CAB
            toolbarFrame.setBackgroundColor(getResources().getColor(R.color.dark_theme_gray_lighter));
        } else {
            toolbarFrame.setBackgroundColor(primaryColor());
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void setupSharedElementCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return;
        final SharedElementCallback mCallback = new SharedElementCallback() {
            @Override
            public void onMapSharedElements(List<String> names, Map<String, View> sharedElements) {
                LOG("onMapSharedElements(List<String>, Map<String, View>)", mIsReentering);
                boolean shouldAdd = true;
                int oldPosition = mTmpState != null ? mTmpState.getInt(EXTRA_OLD_ITEM_POSITION) : 0;
                int currentPosition = mTmpState != null ? mTmpState.getInt(EXTRA_CURRENT_ITEM_POSITION) : 0;
                mTmpState = null;
                if (mIsReentering) {
                    shouldAdd = currentPosition != oldPosition;
                }
                if (shouldAdd && mRecyclerView != null) {
                    View newSharedView = mRecyclerView.findViewWithTag(currentPosition);
                    if (newSharedView != null) {
                        newSharedView = newSharedView.findViewById(R.id.image);
                        final String transName = newSharedView.getTransitionName();
                        names.clear();
                        names.add(transName);
                        sharedElements.clear();
                        sharedElements.put(transName, newSharedView);
                    }
                }

                //Somehow this works (setting status bar color in both MediaFragment and here)
                //to avoid image glitching through on when ViewActivity is first created.
                getWindow().setStatusBarColor(primaryColorDark());

                View decor = getWindow().getDecorView();
                View navigationBar = decor.findViewById(android.R.id.navigationBarBackground);
                View statusBar = decor.findViewById(android.R.id.statusBarBackground);

                if (navigationBar != null && !sharedElements.containsKey(Window.NAVIGATION_BAR_BACKGROUND_TRANSITION_NAME)) {
                    if (!names.contains(Window.NAVIGATION_BAR_BACKGROUND_TRANSITION_NAME))
                        names.add(Window.NAVIGATION_BAR_BACKGROUND_TRANSITION_NAME);
                    sharedElements.put(Window.NAVIGATION_BAR_BACKGROUND_TRANSITION_NAME, navigationBar);
                }

                View toolbarFrame = findViewById(R.id.toolbar_frame);
                if (toolbarFrame != null && !sharedElements.containsKey(toolbarFrame.getTransitionName())) {
                    if (!names.contains(toolbarFrame.getTransitionName()))
                        names.add(toolbarFrame.getTransitionName());
                    sharedElements.put(toolbarFrame.getTransitionName(), toolbarFrame);
                }

                if (statusBar != null && !sharedElements.containsKey(Window.STATUS_BAR_BACKGROUND_TRANSITION_NAME)) {
                    if (!names.contains(Window.STATUS_BAR_BACKGROUND_TRANSITION_NAME))
                        names.add(Window.STATUS_BAR_BACKGROUND_TRANSITION_NAME);
                    sharedElements.put(Window.STATUS_BAR_BACKGROUND_TRANSITION_NAME, statusBar);
                }

                LOG("=== names: " + names.toString(), mIsReentering);
                LOG("=== sharedElements: " + Utils.setToString(sharedElements.keySet()), mIsReentering);
            }

            @Override
            public void onSharedElementStart(List<String> sharedElementNames, List<View> sharedElements,
                                             List<View> sharedElementSnapshots) {
                LOG("onSharedElementStart(List<String>, List<View>, List<View>)", mIsReentering);
                logSharedElementsInfo(sharedElementNames, sharedElements);
            }

            @Override
            public void onSharedElementEnd(List<String> sharedElementNames, List<View> sharedElements,
                                           List<View> sharedElementSnapshots) {
                LOG("onSharedElementEnd(List<String>, List<View>, List<View>)", mIsReentering);
                logSharedElementsInfo(sharedElementNames, sharedElements);

                if (mIsReentering) {
                    View statusBar = getWindow().getDecorView().findViewById(android.R.id.statusBarBackground);
                    if (statusBar != null) {
                        statusBar.post(new Runnable() {
                            @Override
                            public void run() {
                                getWindow().setStatusBarColor(getResources().getColor(android.R.color.transparent));
                            }
                        });
                    }
                }
            }

            private void logSharedElementsInfo(List<String> names, List<View> sharedElements) {
                LOG("=== names: " + names.toString(), mIsReentering);
                for (View view : sharedElements) {
                    int[] loc = new int[2];
                    //noinspection ResourceType
                    view.getLocationInWindow(loc);
                    Log.i(TAG, "=== " + view.getTransitionName() + ": " + "(" + loc[0] + ", " + loc[1] + ")");
                }
            }
        };
        setExitSharedElementCallback(mCallback);
    }

    private void saveScrollPosition() {
        Fragment frag = getFragmentManager().findFragmentById(R.id.content_frame);
        if (frag != null) {
            ((MediaFragment) frag).saveScrollPosition();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setupSharedElementCallback();

        mToolbar = (Toolbar) findViewById(R.id.toolbar);
        mToolbar.setSubtitleTextAppearance(this, R.style.ToolbarSubtitleStyle);
        setSupportActionBar(mToolbar);
        findViewById(R.id.toolbar_frame).setBackgroundColor(primaryColor());

        processIntent(getIntent());

        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setHomeButtonEnabled(true);

        if (!isSelectAlbumMode()) {
            mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
            mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout,
                    R.string.navigation_drawer_open, R.string.navigation_drawer_close) {
                @Override
                public void onDrawerOpened(View drawerView) {
                }

                @Override
                public void onDrawerSlide(View drawerView, float slideOffset) {
                    if (drawerView == null) super.onDrawerSlide(mDrawerLayout, slideOffset);
                }

                @Override
                public void onDrawerClosed(View drawerView) {
                    Fragment nav = getFragmentManager().findFragmentByTag("NAV_DRAWER");
                    if (nav != null)
                        ((NavDrawerFragment) nav).notifyClosed();
                }
            };
            mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);
            mDrawerLayout.post(new Runnable() {
                @Override
                public void run() {
                    mDrawerToggle.syncState();
                }
            });
            mDrawerLayout.setDrawerListener(mDrawerToggle);
            mDrawerLayout.setStatusBarBackgroundColor(primaryColorDark());

            FrameLayout navDrawerFrame = (FrameLayout) findViewById(R.id.nav_drawer_frame);
            int navDrawerMargin = getResources().getDimensionPixelSize(R.dimen.nav_drawer_margin);
            DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
            int navDrawerWidthLimit = getResources().getDimensionPixelSize(R.dimen.nav_drawer_width_limit);
            int navDrawerWidth = displayMetrics.widthPixels - navDrawerMargin;
            if (navDrawerWidth > navDrawerWidthLimit) {
                navDrawerWidth = navDrawerWidthLimit;
            }
            navDrawerFrame.setLayoutParams(new DrawerLayout.LayoutParams(navDrawerWidth, DrawerLayout.LayoutParams.MATCH_PARENT, Gravity.START));
            navDrawerFrame.setBackgroundColor(primaryColorDark());

            if (getIntent().getAction() != null &&
                    (getIntent().getAction().equals(Intent.ACTION_GET_CONTENT) ||
                            getIntent().getAction().equals(Intent.ACTION_PICK))) {
                mTitle = getTitle();
                getSupportActionBar().setTitle(R.string.pick_something);
                mPickMode = true;
            }
        } else {
            actionBar.setHomeAsUpIndicator(R.drawable.ic_action_discard);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // The drawer layout would handle this if album selection mode wasn't active
                getWindow().setStatusBarColor(primaryColorDark());
            }
        }

        mCrumbs = (BreadCrumbLayout) findViewById(R.id.breadCrumbs);
        mCrumbs.setFragmentManager(getFragmentManager());
        mCrumbs.setCallback(new BreadCrumbLayout.SelectionCallback() {
            @Override
            public void onCrumbSelection(BreadCrumbLayout.Crumb crumb, int count, int index) {
                if (index == -1) {
                    onBackPressed();
                } else {
                    saveScrollPosition();
                    int active = mCrumbs.getActiveIndex();
                    if (active > index) {
                        final int difference = Math.abs(active - index);
                        for (int i = 0; i < difference; i++) {
                            try {
                                getFragmentManager().popBackStack();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    } else if (active < index) {
                        for (int i = active + 1; i != index + 1; i++)
                            addArtificalBackStack(mCrumbs.getCrumb(i).getPath(), true);
                    }
                    mCrumbs.setActive(crumb);
                }
            }

            @Override
            public void onArtificialSelection(BreadCrumbLayout.Crumb crumb, String path, boolean backStack) {
                addArtificalBackStack(path, backStack);
            }
        });

        if (savedInstanceState == null) {
            // Show initial page (overview)
            switchPage(AlbumEntry.ALBUM_OVERVIEW, true);
        } else if (!isSelectAlbumMode()) {
            if (mTitle != null) getSupportActionBar().setTitle(mTitle);
        }

        getFragmentManager().addOnBackStackChangedListener(new FragmentManager.OnBackStackChangedListener() {
            @Override
            public void onBackStackChanged() {
                MediaFragment content = (MediaFragment) getFragmentManager().findFragmentById(R.id.content_frame);
                if (content != null)
                    content.onBackStackResume();
                NavDrawerFragment nav = (NavDrawerFragment) getFragmentManager().findFragmentByTag("NAV_DRAWER");
                if (content != null && nav != null && content.getAlbumPath() != null) {
                    nav.notifyBackStack(content.getAlbumPath());
                }
            }
        });

        if (savedInstanceState != null && savedInstanceState.containsKey("breadcrumbs_state")) {
            mCrumbs.restoreFromStateWrapper((BreadCrumbLayout.SavedStateWrapper)
                    savedInstanceState.getSerializable("breadcrumbs_state"), this);
        }

        SortMemoryProvider.cleanup(this);
    }

    private void addArtificalBackStack(final String to, boolean backStack) {
        Fragment frag = MediaFragment.create(to);
        String tag = null;
        if (to != null &&
                (to.equals(Environment.getExternalStorageDirectory().getAbsolutePath()) ||
                        to.equals(AlbumEntry.ALBUM_OVERVIEW))) {
            tag = "[root]";
        }
        @SuppressLint("CommitTransaction")
        FragmentTransaction transaction = getFragmentManager().beginTransaction()
                .replace(R.id.content_frame, frag, tag);
        if (backStack)
            transaction.addToBackStack(null);
        try {
            transaction.commit();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
        invalidateCrumbs();
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveScrollPosition();
    }

    public void invalidateCrumbs() {
        final boolean explorerMode = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("explorer_mode", false);
        mCrumbs.setVisibility(explorerMode ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable("breadcrumbs_state", mCrumbs.getStateWrapper());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        processIntent(intent);
    }

    private void processIntent(final Intent intent) {
        String action = intent.getAction();
        if (action != null && action.equals(ACTION_SELECT_ALBUM)) {
            switch (intent.getIntExtra("mode", -1)) {
                default:
                    setSelectAlbumMode(SelectAlbumMode.CHOOSE);
                    break;
                case R.id.copyTo:
                    setSelectAlbumMode(SelectAlbumMode.COPY);
                    break;
                case R.id.moveTo:
                    setSelectAlbumMode(SelectAlbumMode.MOVE);
                    break;
            }
        }
    }

    public boolean isSelectAlbumMode() {
        return mSelectAlbumMode != SelectAlbumMode.NONE;
    }

    private void setSelectAlbumMode(SelectAlbumMode mode) {
        mSelectAlbumMode = mode;
        switch (mSelectAlbumMode) {
            default:
                getSupportActionBar().setTitle(R.string.choose_album);
                break;
            case COPY:
                getSupportActionBar().setTitle(R.string.copy_to);
                break;
            case MOVE:
                getSupportActionBar().setTitle(R.string.move_to);
                break;
        }
        invalidateOptionsMenu();
    }

    @Override
    public void setTitle(CharSequence title) {
        mTitle = title;
        if (!mPickMode && mSelectAlbumMode == SelectAlbumMode.NONE && getSupportActionBar() != null) {
            getSupportActionBar().setTitle(title);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mDrawerToggle.onConfigurationChanged(newConfig);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onActivityReenter(int resultCode, Intent data) {
        super.onActivityReenter(resultCode, data);
        mIsReentering = true;
        mTmpState = new Bundle(data.getExtras());
        int oldPosition = mTmpState.getInt(EXTRA_OLD_ITEM_POSITION);
        int currentPosition = mTmpState.getInt(EXTRA_CURRENT_ITEM_POSITION);
        if (oldPosition != currentPosition && mRecyclerView != null) {
            mRecyclerView.scrollToPosition(currentPosition);
        }
        if (mRecyclerView != null) {
            postponeEnterTransition();
            mRecyclerView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    mRecyclerView.getViewTreeObserver().removeOnPreDrawListener(this);
                    mRecyclerView.requestLayout();
                    startPostponedEnterTransition();
                    return true;
                }
            });
        }
    }

    @Override
    public void onBackPressed() {
        if (mMediaCab != null) {
            mMediaCab.finish();
            mMediaCab = null;
        } else {
            if (mDrawerLayout != null && mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
                mDrawerLayout.closeDrawer(GravityCompat.START);
            } else if (mCrumbs.canPop()) {
                mCrumbs.pop();
            } else {
                super.onBackPressed();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        menu.findItem(R.id.settings).setVisible(!mPickMode && mSelectAlbumMode == SelectAlbumMode.NONE);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.settings) {
            startActivityForResult(new Intent(this, SettingsActivity.class), SETTINGS_REQUEST);
            return true;
        } else if (item.getItemId() == android.R.id.home) {
            if (drawerArrowOpen) {
                onBackPressed();
                return true;
            } else if (isSelectAlbumMode()) {
                finish();
                return true;
            }
        }
        return mDrawerLayout != null && (mDrawerLayout.getDrawerLockMode(GravityCompat.START) == DrawerLayout.LOCK_MODE_LOCKED_CLOSED ||
                mDrawerToggle.onOptionsItemSelected(item) || super.onOptionsItemSelected(item));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SETTINGS_REQUEST && resultCode == Activity.RESULT_OK) {
            MediaFragment content = (MediaFragment) getFragmentManager().findFragmentById(R.id.content_frame);
            if (content != null) content.reload();
            reloadNavDrawerAlbums();
        }
    }

    private void switchPage(String path, boolean closeDrawer) {
        switchPage(path, closeDrawer, false);
    }

    public void switchPage(String to, boolean closeDrawer, boolean backStack) {
        boolean wasNull = false;
        if (to == null) {
            // Initial directory
            wasNull = true;
            to = Environment.getExternalStorageDirectory().getAbsolutePath();
        }
        BreadCrumbLayout.Crumb crumb = new BreadCrumbLayout.Crumb(this, to);
        if (!backStack) {
            mCrumbs.clearCrumbs();
            mCrumbs.addCrumb(crumb, true);
            addArtificalBackStack(to, false);
        } else {
            final boolean explorerMode = PreferenceManager.getDefaultSharedPreferences(this)
                    .getBoolean("explorer_mode", false);
            mCrumbs.setActiveOrAdd(crumb, !explorerMode, wasNull);
        }
        if (closeDrawer && mDrawerLayout != null)
            mDrawerLayout.closeDrawers();
    }

    public void notifyFoldersChanged() {
        FragmentManager fm = getFragmentManager();
        Fragment frag = fm.findFragmentByTag("[root]");
        if (frag != null)
            ((MediaFragment) frag).reload();
        for (int i = 0; i < fm.getBackStackEntryCount(); i++) {
            final String name = fm.getBackStackEntryAt(i).getName();
            if (name != null) {
                frag = fm.findFragmentByTag(name);
                if (frag != null) ((MediaFragment) frag).reload();
            }
        }
    }

    private static void LOG(String message, boolean isReentering) {
        if (DEBUG) {
            Log.i(TAG, String.format("%s: %s", isReentering ? "REENTERING" : "EXITING", message));
        }
    }

    @Override
    public void onFolderSelection(File folder) {
        IncludedFolderProvider.add(this, folder);
        reloadNavDrawerAlbums();
        notifyFoldersChanged();
    }

    public void reloadNavDrawerAlbums() {
        NavDrawerFragment nav = (NavDrawerFragment) getFragmentManager().findFragmentByTag("NAV_DRAWER");
        if (nav != null) {
            if (nav.mCurrentAccount == null)
                nav.reloadAccounts();
            else nav.getAlbums(nav.mCurrentAccount);
        }
    }
}