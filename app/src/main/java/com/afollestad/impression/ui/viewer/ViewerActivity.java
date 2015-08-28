package com.afollestad.impression.ui.viewer;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.annotation.TargetApi;
import android.app.SharedElementCallback;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.nfc.NfcEvent;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v4.print.PrintHelper;
import android.support.v4.view.ViewPager;
import android.support.v7.internal.widget.TintImageView;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.afollestad.impression.R;
import com.afollestad.impression.adapters.MediaAdapter;
import com.afollestad.impression.adapters.ViewerPageAdapter;
import com.afollestad.impression.api.PhotoEntry;
import com.afollestad.impression.api.base.MediaEntry;
import com.afollestad.impression.fragments.dialog.SlideshowInitDialog;
import com.afollestad.impression.fragments.viewer.ViewerPageFragment;
import com.afollestad.impression.ui.base.ThemedActivity;
import com.afollestad.impression.utils.TimeUtils;
import com.afollestad.impression.utils.Utils;
import com.afollestad.materialdialogs.MaterialDialog;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import static com.afollestad.impression.fragments.viewer.ViewerPageFragment.LIGHT_MODE_ON;
import static com.afollestad.impression.ui.MainActivity.EXTRA_CURRENT_ITEM_POSITION;
import static com.afollestad.impression.ui.MainActivity.EXTRA_OLD_ITEM_POSITION;

/**
 * @author Aidan Follestad (afollestad)
 */
public class ViewerActivity extends ThemedActivity implements SlideshowInitDialog.SlideshowCallback {

    @Override
    protected int darkTheme() {
        return R.style.AppTheme_Viewer_Dark;
    }

    @Override
    protected int lightTheme() {
        return R.style.AppTheme_Viewer;
    }

    private List<MediaEntry> mEntries;
    private ViewPager mPager;
    private ViewerPageAdapter mAdapter;
    public Toolbar mToolbar;
    private Timer mTimer;

    public static final int TOOLBAR_FADE_OFFSET = 2750;
    public static final int TOOLBAR_FADE_DURATION = 400;
    private static final int EDIT_REQUEST = 1000;

    private static final String STATE_CURRENT_POSITION = "state_current_position";
    private static final String STATE_OLD_POSITION = "state_old_position";
    private int mCurrentPosition;
    private int mOriginalPosition;
    private boolean startedPostponedTransition;
    public boolean mFinishedTransition;
    private boolean mLightMode;
    private int mStatusBarHeight;

    private boolean mIsReturning;
    private boolean mAllVideos;

    private TintImageView mOverflow;

    private class FileBeamCallback implements NfcAdapter.CreateBeamUrisCallback {

        public FileBeamCallback() {
        }

        @Override
        public Uri[] createBeamUris(NfcEvent event) {
            if (mCurrentPosition == -1) return null;
            return new Uri[]{
                    Utils.getImageContentUri(ViewerActivity.this,
                            new File(mEntries.get(mCurrentPosition).data()))
            };
        }
    }

    public void invalidateLightMode(boolean lightMode) {
        if (lightMode == mLightMode) return;
        mLightMode = lightMode;
        final Drawable navIcon = ContextCompat.getDrawable(this, R.drawable.ic_nav_back);
        final int darkGray = ContextCompat.getColor(this, R.color.viewer_lightmode_icons);
        navIcon.setColorFilter(mLightMode ? darkGray : Color.WHITE, PorterDuff.Mode.SRC_ATOP);
        mToolbar.setNavigationIcon(navIcon);
        invalidateOptionsMenu();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void invalidateTransition() {
        if (startedPostponedTransition || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
            return;
        startedPostponedTransition = true;
        startPostponedEnterTransition();
    }

    public static class MediaWrapper implements Serializable {

        private final List<MediaEntry> mMediaEntries;

        public MediaWrapper(List<MediaEntry> mediaEntries, boolean allowFolders) {
            mMediaEntries = new ArrayList<>();
            for (int i = 0; i < mediaEntries.size(); i++) {
                MediaEntry p = mediaEntries.get(i);
                p.setRealIndex(i);
                if (allowFolders || !p.isFolder())
                    mMediaEntries.add(p);
            }
        }

        public List<MediaEntry> getMedia() {
            return mMediaEntries;
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void setupSharedElementCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return;
        final SharedElementCallback mCallback = new SharedElementCallback() {
            @Override
            public void onMapSharedElements(List<String> names, Map<String, View> sharedElements) {
                if (mIsReturning) {
                    View sharedView = mAdapter.getCurrentDetailsFragment().getSharedElement();
                    names.clear();
                    sharedElements.clear();

                    if (sharedView != null) {
                        final String transName = sharedView.getTransitionName();
                        names.add(transName);
                        sharedElements.put(transName, sharedView);
                    }

                    invalidateLightMode(false);
                }

                View decor = getWindow().getDecorView();
                View navigationBar = decor.findViewById(android.R.id.navigationBarBackground);
                View statusBar = decor.findViewById(android.R.id.statusBarBackground);

                if (navigationBar != null && !sharedElements.containsKey(Window.NAVIGATION_BAR_BACKGROUND_TRANSITION_NAME)) {
                    if (!names.contains(Window.NAVIGATION_BAR_BACKGROUND_TRANSITION_NAME))
                        names.add(Window.NAVIGATION_BAR_BACKGROUND_TRANSITION_NAME);
                    sharedElements.put(Window.NAVIGATION_BAR_BACKGROUND_TRANSITION_NAME, navigationBar);
                }

                if (mToolbar != null && !sharedElements.containsKey(mToolbar.getTransitionName())) {
                    if (!names.contains(mToolbar.getTransitionName()))
                        names.add(mToolbar.getTransitionName());
                    sharedElements.put(mToolbar.getTransitionName(), mToolbar);
                }

                if (statusBar != null && !sharedElements.containsKey(Window.STATUS_BAR_BACKGROUND_TRANSITION_NAME)) {
                    if (!names.contains(Window.STATUS_BAR_BACKGROUND_TRANSITION_NAME))
                        names.add(Window.STATUS_BAR_BACKGROUND_TRANSITION_NAME);
                    sharedElements.put(Window.STATUS_BAR_BACKGROUND_TRANSITION_NAME, statusBar);
                }
            }

            @Override
            public void onSharedElementStart(List<String> sharedElementNames, List<View> sharedElements,
                                             List<View> sharedElementSnapshots) {
                int black = ContextCompat.getColor(ViewerActivity.this, android.R.color.black);
                int duration = 200;

                View decor = getWindow().getDecorView();
                View navigationBar = decor.findViewById(android.R.id.navigationBarBackground);
                View statusBar = decor.findViewById(android.R.id.statusBarBackground);

                if (!mIsReturning) {
                    int primaryColorDark = primaryColorDark();
                    int viewerOverlayColor = ContextCompat.getColor(ViewerActivity.this, R.color.viewer_overlay);

                    ObjectAnimator.ofObject(mToolbar, "backgroundColor", new ArgbEvaluator(), primaryColor(), viewerOverlayColor)
                            .setDuration(duration)
                            .start();
                    if (navigationBar != null && isColoredNavBar())
                        ObjectAnimator.ofObject(navigationBar, "backgroundColor", new ArgbEvaluator(), primaryColorDark, black)
                                .setDuration(duration)
                                .start();
                    if (statusBar != null)
                        ObjectAnimator.ofObject(statusBar, "backgroundColor", new ArgbEvaluator(), primaryColorDark, black)
                                .setDuration(duration)
                                .start();
                } else {
                    mToolbar.setBackgroundColor(primaryColor());

                    if (navigationBar != null && isColoredNavBar())
                        ObjectAnimator.ofObject(navigationBar, "backgroundColor", new ArgbEvaluator(), black, primaryColorDark())
                                .setDuration(duration)
                                .start();
                    if (statusBar != null)
                        ObjectAnimator.ofObject(statusBar, "backgroundColor", new ArgbEvaluator(), black, primaryColorDark())
                                .setDuration(duration)
                                .start();
                }
            }

            @Override
            public void onSharedElementEnd(List<String> sharedElementNames, List<View> sharedElements,
                                           List<View> sharedElementSnapshots) {
            }
        };
        setEnterSharedElementCallback(mCallback);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_CURRENT_POSITION, mCurrentPosition);
        outState.putInt(STATE_OLD_POSITION, mOriginalPosition);
    }

    private int getStatusBarHeight() {
        if (mStatusBarHeight == 0) {
            int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
            if (resourceId > 0) {
                mStatusBarHeight = getResources().getDimensionPixelSize(resourceId);
            }
        }
        return mStatusBarHeight;
    }

    @Override
    protected boolean hasColoredBars() {
        return false;
    }

    public int getNavigationBarHeight(boolean portraitOnly, boolean landscapeOnly) {
        final Configuration config = getResources().getConfiguration();
        final Resources r = getResources();
        int id;
        if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            if (portraitOnly) return 0;
            id = r.getIdentifier("navigation_bar_height_landscape", "dimen", "android");
        } else {
            if (landscapeOnly) return 0;
            id = r.getIdentifier("navigation_bar_height", "dimen", "android");
        }
        if (id > 0)
            return r.getDimensionPixelSize(id);
        return 0;
    }

    private ViewPager.OnPageChangeListener mPagerListener = new ViewPager.OnPageChangeListener() {

        int previousState;
        boolean userScrollChange;

        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        }

        @Override
        public void onPageSelected(int position) {
            if (userScrollChange)
                stopSlideshow();
            ViewerPageFragment noActive = (ViewerPageFragment) getFragmentManager().findFragmentByTag("page:" + mCurrentPosition);
            if (noActive != null)
                noActive.setIsActive(false);
            mCurrentPosition = position;
            ViewerPageFragment active = (ViewerPageFragment) getFragmentManager().findFragmentByTag("page:" + mCurrentPosition);
            if (active != null) {
                active.setIsActive(true);
                mLightMode = active.mLightMode == LIGHT_MODE_ON;
            }
            mAdapter.mCurrentPage = position;
            invalidateOptionsMenu();
        }

        @Override
        public void onPageScrollStateChanged(int state) {
            if (previousState == ViewPager.SCROLL_STATE_DRAGGING
                    && state == ViewPager.SCROLL_STATE_SETTLING)
                userScrollChange = true;
            else if (previousState == ViewPager.SCROLL_STATE_SETTLING
                    && state == ViewPager.SCROLL_STATE_IDLE)
                userScrollChange = false;

            previousState = state;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            startedPostponedTransition = false;
            postponeEnterTransition();
        }
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_viewer);

        mToolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(mToolbar);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, getStatusBarHeight(), 0, 0);
        mToolbar.setLayoutParams(params);
        mToolbar.setNavigationIcon(R.drawable.ic_nav_back);
        mToolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });

        if (savedInstanceState == null) {
            if (getIntent() != null && getIntent().getExtras() != null) {
                mCurrentPosition = getIntent().getExtras().getInt(EXTRA_CURRENT_ITEM_POSITION);
                mOriginalPosition = mCurrentPosition;
            }
        } else {
            mCurrentPosition = savedInstanceState.getInt(STATE_CURRENT_POSITION);
            mOriginalPosition = savedInstanceState.getInt(STATE_OLD_POSITION);
        }

        boolean dontSetPos = false;
        if (getIntent() != null) {
            if (getIntent().hasExtra("media_entries")) {
                mEntries = ((MediaWrapper) getIntent().getSerializableExtra("media_entries")).getMedia();
            } else if (getIntent().getData() != null) {
                mEntries = new ArrayList<>();
                Uri data = getIntent().getData();
                String path = null;
                if (data.getScheme() != null) {
                    path = data.toString();
                    if (data.getScheme().equals("file")) {
                        path = data.getPath();
                        if (!new File(path).exists()) {
                            path = null;
                        } else {
                            final File file = new File(path);
                            final List<MediaEntry> brothers = Utils.getEntriesFromFolder(this, file.getParentFile(), false, false, MediaAdapter.FileFilterMode.ALL);
                            mEntries.addAll(brothers);
                            for (int i = 0; i < brothers.size(); i++) {
                                if (brothers.get(i).data().equals(file.getAbsolutePath())) {
                                    mCurrentPosition = i;
                                    dontSetPos = true;
                                    break;
                                }
                            }
                        }
                    } else {
                        String tempPath = null;
                        try {
                            Cursor cursor = getContentResolver().query(data, new String[]{"_data"}, null, null, null);
                            if (cursor.moveToFirst())
                                tempPath = cursor.getString(0);
                            cursor.close();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        if (tempPath != null) {
                            // @author Viswanath Lekshmanan
                            // #282 Fix to load all other photos in the same album when loading using URI
                            final File file = new File(tempPath);
                            final List<MediaEntry> brothers = Utils.getEntriesFromFolder(this, file.getParentFile(), false, false, MediaAdapter.FileFilterMode.ALL);
                            mEntries.addAll(brothers);
                            for (int i = 0; i < brothers.size(); i++) {
                                if (brothers.get(i).data().equals(file.getAbsolutePath())) {
                                    mCurrentPosition = i;
                                    dontSetPos = true;
                                    break;
                                }
                            }
                        } else {
                            path = null;
                        }
                    }
                }

                if (path == null) {
                    new MaterialDialog.Builder(this)
                            .title(R.string.error)
                            .content(R.string.invalid_file_path_error)
                            .positiveText(android.R.string.ok)
                            .cancelable(false)
                            .callback(new MaterialDialog.ButtonCallback() {
                                @Override
                                public void onPositive(MaterialDialog dialog) {
                                    super.onPositive(dialog);
                                    finish();
                                }
                            }).show();
                    return;
                }
            }

            mAdapter = new ViewerPageAdapter(getFragmentManager(), mEntries,
                    getIntent().getStringExtra("bitmapInfo"), mCurrentPosition);
            mPager = (ViewPager) findViewById(R.id.pager);
            mPager.setOffscreenPageLimit(1);
            mPager.setAdapter(mAdapter);

            processEntries(dontSetPos);

            // When the view pager is swiped, fragments are notified if they're active or not
            // And the menu updates based on the color mode (light or dark).

            mPager.addOnPageChangeListener(mPagerListener);

            mFinishedTransition = getIntent().getData() != null || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP;
            setupSharedElementCallback();
        }

        // Android Beam stuff
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 &&
                getPackageManager().hasSystemFeature(PackageManager.FEATURE_NFC)) {
            NfcAdapter mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
            mNfcAdapter.setBeamPushUrisCallback(new FileBeamCallback(), this);
        }

        // Callback used to know when the user swipes up to show system UI
        getWindow().getDecorView().setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener() {
            @Override
            public void onSystemUiVisibilityChange(int visibility) {
                if (visibility == View.VISIBLE) {
                    invokeToolbar(false);
                    systemUIFocus = false; // this is inverted by the method below
                    systemUIFocusChange();
                }
            }
        });

        // Prevents nav bar from overlapping toolbar options in landscape
        mToolbar.setPadding(
                mToolbar.getPaddingLeft(),
                mToolbar.getPaddingTop(),
                getNavigationBarHeight(false, true),
                mToolbar.getPaddingBottom()
        );
    }

    @Override
    protected void onPause() {
        super.onPause();
        mPager.removeOnPageChangeListener(mPagerListener);
    }

    private int translateToViewerIndex(int remote) {
        for (int i = 0; i < mEntries.size(); i++) {
            if (mEntries.get(i).realIndex() == remote) {
                if (mEntries.size() - 1 < i) {
                    return 0;
                } else {
                    return i;
                }
            }
        }
        return 0;
    }

    private void processEntries(boolean dontSetPos) {
        mAllVideos = true;
        for (MediaEntry e : mEntries) {
            if (!e.isVideo()) {
                mAllVideos = false;
                break;
            }
        }
        if (!dontSetPos)
            mCurrentPosition = translateToViewerIndex(mCurrentPosition);
        mPager.setCurrentItem(mCurrentPosition);
    }

    private void hideSystemUI() {
        // Set the IMMERSIVE flag.
        // Set the content to appear under the system bars so that the content
        // doesn't resize when the system bars hide and show.
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide nav bar
                        | View.SYSTEM_UI_FLAG_FULLSCREEN // hide status bar
                        | View.SYSTEM_UI_FLAG_IMMERSIVE);
    }

    // This snippet shows the system bars. It does this by removing all the flags
    // except for the ones that make the content appear under the system bars.
    private void showSystemUI() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    private boolean systemUIFocus = false;

    public void systemUIFocusChange() {
        systemUIFocus = !systemUIFocus;
        if (systemUIFocus) {
            showSystemUI();
            if (mTimer != null) {
                mTimer.cancel();
                mTimer.purge();
            }
            mTimer = new Timer();
            mTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    systemUIFocus = false;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            hideSystemUI();
                        }
                    });
                }
            }, TOOLBAR_FADE_OFFSET);
        } else hideSystemUI();
    }

    @Override
    public boolean onMenuOpened(int featureId, Menu menu) {
        mToolbar.animate().cancel();
        return super.onMenuOpened(featureId, menu);
    }

    @Override
    public void onOptionsMenuClosed(Menu menu) {
        super.onOptionsMenuClosed(menu);
        // Resume the fade animation
        invokeToolbar(false);
    }

    public interface ToolbarFadeListener {
        void onFade();
    }

    private void invokeToolbar(boolean tapped) {
        invokeToolbar(tapped, null);
    }

    public void invokeToolbar(boolean tapped, final ToolbarFadeListener listener) {
        mToolbar.animate().cancel();
        if (tapped && mToolbar.getAlpha() > 0f) {
            // User tapped to hide the toolbar immediately
            mToolbar.animate().setDuration(TOOLBAR_FADE_DURATION)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            super.onAnimationEnd(animation);
                            if (listener != null) listener.onFade();
                        }
                    }).alpha(0f).setStartDelay(0).start();
        } else {
            mToolbar.setAlpha(1f);
            mToolbar.animate().setDuration(TOOLBAR_FADE_DURATION).setStartDelay(TOOLBAR_FADE_OFFSET).alpha(0f).start();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Start the toolbar fader
        invokeToolbar(false);
        systemUIFocusChange();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mToolbar.animate().cancel();
    }

    private Uri getCurrentUri() {
        return Uri.fromFile(new File(mEntries.get(mCurrentPosition).data()));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.viewer, menu);
        if (mEntries.size() > 0) {
            MediaEntry currentEntry = mEntries.get(mCurrentPosition);
            if (currentEntry == null || currentEntry.isVideo()) {
                menu.findItem(R.id.print).setVisible(false);
                menu.findItem(R.id.edit).setVisible(false);
                menu.findItem(R.id.set_as).setVisible(false);
            } else {
                menu.findItem(R.id.print).setVisible(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT);
                menu.findItem(R.id.edit).setVisible(true);
                menu.findItem(R.id.set_as).setVisible(true);
            }
        }
        menu.findItem(R.id.slideshow).setVisible(!mAllVideos && mSlideshowTimer == null);

        final int darkGray = ContextCompat.getColor(this, R.color.viewer_lightmode_icons);
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            if (item.getIcon() != null)
                item.getIcon().setColorFilter(mLightMode ? darkGray : Color.WHITE, PorterDuff.Mode.SRC_ATOP);
        }
        setOverflowButtonColor(mLightMode ? darkGray : Color.WHITE);

        return super.onCreateOptionsMenu(menu);
    }

    private void setOverflowButtonColor(final int color) {
        if (mOverflow != null) {
            mOverflow.setColorFilter(color, PorterDuff.Mode.SRC_ATOP);
            return;
        }

        ViewTreeObserver viewTreeObserver = mToolbar.getViewTreeObserver();
        viewTreeObserver.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                final ArrayList<View> outViews = new ArrayList<>();
                final String overflowDescription = getString(R.string.abc_action_menu_overflow_description);
                mToolbar.findViewsWithText(outViews, overflowDescription, View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION);
                if (outViews.isEmpty()) {
                    return;
                }
                mOverflow = (TintImageView) outViews.get(0);
                mOverflow.setColorFilter(color, PorterDuff.Mode.SRC_ATOP);
                removeOnGlobalLayoutListener(mToolbar, this);
            }
        });
    }

    @SuppressWarnings("deprecation")
    private static void removeOnGlobalLayoutListener(View v, ViewTreeObserver.OnGlobalLayoutListener listener) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            v.getViewTreeObserver().removeGlobalOnLayoutListener(listener);
        } else {
            v.getViewTreeObserver().removeOnGlobalLayoutListener(listener);
        }
    }

    private Bitmap loadBitmap(File file) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.inSampleSize = 1;
        Bitmap bitmap;
        while (true) {
            Log.v("ViewerActivity", "loadBitmap(" + file.getAbsolutePath() + "), sample size: " + options.inSampleSize);
            try {
                bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
                break;
            } catch (OutOfMemoryError e) {
                options.inSampleSize += 1;
            }
        }
        return bitmap;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.share) {
            try {
                final String mime = mEntries.get(mCurrentPosition).isVideo() ? "video/*" : "image/*";
                startActivity(new Intent(Intent.ACTION_SEND)
                        .setType(mime)
                        .putExtra(Intent.EXTRA_STREAM, getCurrentUri()));
            } catch (ActivityNotFoundException e) {
                Toast.makeText(getApplicationContext(), R.string.no_app_complete_action, Toast.LENGTH_SHORT).show();
            }
            return true;
        } else if (item.getItemId() == R.id.edit) {
            try {
                startActivityForResult(new Intent(Intent.ACTION_EDIT)
                        .setDataAndType(getCurrentUri(), "image/*"), EDIT_REQUEST);
                setResult(RESULT_OK); // signals that list should reload on returning
            } catch (ActivityNotFoundException e) {
                e.printStackTrace();
            }
            return true;
        } else if (item.getItemId() == R.id.set_as) {
            try {
                startActivity(new Intent(Intent.ACTION_ATTACH_DATA)
                        .setDataAndType(getCurrentUri(), "image/*")
                        .putExtra("mimeType", "image/*"));
            } catch (ActivityNotFoundException e) {
                Toast.makeText(getApplicationContext(), R.string.no_app_complete_action, Toast.LENGTH_SHORT).show();
            }
            return true;
        } else if (item.getItemId() == R.id.print) {
//            new Thread(new Runnable() {
//                @Override
//                public void run() {
            PrintHelper photoPrinter = new PrintHelper(ViewerActivity.this);
            photoPrinter.setScaleMode(PrintHelper.SCALE_MODE_FIT);
            final File currentFile = new File(mEntries.get(mCurrentPosition).data());
            Bitmap bitmap = loadBitmap(currentFile);
            photoPrinter.printBitmap(currentFile.getName(), bitmap);
//                    bitmap.recycle();
//                }
//            }).start();
        } else if (item.getItemId() == R.id.details) {
            final MediaEntry entry = mEntries.get(mCurrentPosition);
            final File file = new File(entry.data());
            final Calendar cal = new GregorianCalendar();
            cal.setTimeInMillis(entry.dateTaken());
            new MaterialDialog.Builder(this)
                    .title(R.string.details)
                    .content(Html.fromHtml(getString(R.string.details_contents,
                            TimeUtils.toStringLong(cal),
                            entry.width() + " x " + entry.height(),
                            file.getName(),
                            Utils.readableFileSize(file.length()),
                            file.getAbsolutePath())))
                    .contentLineSpacing(1.6f)
                    .positiveText(R.string.dismiss)
                    .show();
        } else if (item.getItemId() == R.id.delete) {
            final MediaEntry currentEntry = mEntries.get(mCurrentPosition);
            new MaterialDialog.Builder(this)
                    .content(currentEntry.isVideo() ? R.string.delete_confirm_video : R.string.delete_confirm_photo)
                    .positiveText(R.string.yes)
                    .negativeText(R.string.no)
                    .callback(new MaterialDialog.ButtonCallback() {
                        @Override
                        public void onPositive(MaterialDialog materialDialog) {
                            mEntries.get(mCurrentPosition).delete(ViewerActivity.this);
                            mAdapter.remove(mCurrentPosition);
                            if (mEntries.size() == 0) finish();
                        }

                        @Override
                        public void onNegative(MaterialDialog materialDialog) {
                        }
                    }).build().show();
        } else if (item.getItemId() == R.id.slideshow) {
            new SlideshowInitDialog().show(this);
        }
        return super.onOptionsItemSelected(item);
    }

    private long mSlideshowDelay;
    private boolean mSlideshowLoop;
    private Timer mSlideshowTimer;

    @Override
    public void onStartSlideshow(long delay, boolean loop) {
        mSlideshowDelay = delay;
        mSlideshowLoop = loop;
        mSlideshowTimer = new Timer();
        incrementSlideshow();
        invalidateOptionsMenu();

        while (true) {
            MediaEntry e = mEntries.get(mCurrentPosition);
            if (e.isVideo()) {
                mCurrentPosition += 1;
                if (mCurrentPosition > mEntries.size() - 1)
                    mCurrentPosition = 0;
            } else {
                mPager.setCurrentItem(mCurrentPosition);
                break;
            }
        }
    }

    private void incrementSlideshow() {
        mSlideshowTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                performSlide();
            }
        }, mSlideshowDelay);
    }

    private void performSlide() {
        int nextPage = mPager.getCurrentItem() + 1;
        if (nextPage > mEntries.size() - 1) {
            nextPage = mSlideshowLoop ? 0 : -1;
        } else {
            MediaEntry nextEntry = mEntries.get(nextPage);
            if (nextEntry.isVideo()) {
                final int fNextPage = nextPage;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mPager.setCurrentItem(fNextPage);
                        performSlide();
                    }
                });
                return;
            }
        }
        final int fNextPage = nextPage;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (fNextPage != -1) {
                    mPager.setCurrentItem(fNextPage);
                    incrementSlideshow();
                } else {
                    stopSlideshow();
                }
            }
        });
    }

    private void stopSlideshow() {
        if (mSlideshowTimer != null) {
            mSlideshowDelay = 0;
            mSlideshowLoop = false;
            mSlideshowTimer.cancel();
            mSlideshowTimer.purge();
            mSlideshowTimer = null;
            invalidateOptionsMenu();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mSlideshowTimer != null) {
            mSlideshowTimer.cancel();
            mSlideshowTimer.purge();
            mSlideshowTimer = null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        // When you edit a photo, the result it will be inserted as the first page so you can scroll to the new image
        if (requestCode == EDIT_REQUEST && resultCode == RESULT_OK) {
            Uri data = intent.getData();
            if (data != null) {
                if (data.getScheme() == null || data.getScheme().equals("file")) {
                    MediaEntry pic = new PhotoEntry().load(new File(data.getPath()));
                    mAdapter.add(pic);
                    mPager.setCurrentItem(mPager.getCurrentItem() + 1);
                } else {
                    try {
                        Cursor cursor = getContentResolver().query(data, new PhotoEntry().projection(), null, null, null);
                        if (cursor != null && cursor.moveToFirst()) {
                            MediaEntry pic = new PhotoEntry().load(cursor);
                            mAdapter.add(pic);
                            mPager.setCurrentItem(mPager.getCurrentItem() + 1);
                            cursor.close();
                        }
                    } catch (SecurityException e) {
                        new MaterialDialog.Builder(this)
                                .title(R.string.error)
                                .content(R.string.open_permission_error)
                                .positiveText(android.R.string.ok)
                                .cancelable(false)
                                .callback(new MaterialDialog.ButtonCallback() {
                                    @Override
                                    public void onPositive(MaterialDialog dialog) {
                                        super.onPositive(dialog);
                                        finish();
                                    }
                                }).show();
                    }
                }
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void finishAfterTransition() {
        mIsReturning = true;
        Intent data = new Intent();
        if (getIntent() != null)
            data.putExtra(EXTRA_OLD_ITEM_POSITION, getIntent().getIntExtra(EXTRA_CURRENT_ITEM_POSITION, 0));
        data.putExtra(EXTRA_CURRENT_ITEM_POSITION, mCurrentPosition);
        setResult(RESULT_OK, data);
        super.finishAfterTransition();
    }
}