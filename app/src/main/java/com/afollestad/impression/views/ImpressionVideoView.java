package com.afollestad.impression.views;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.media.MediaPlayer;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewPropertyAnimator;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.VideoView;

import com.afollestad.impression.R;
import com.afollestad.impression.fragments.viewer.ViewerPageFragment;
import com.afollestad.impression.ui.viewer.ViewerActivity;
import com.afollestad.impression.utils.Utils;

import java.util.concurrent.TimeUnit;

/**
 * @author Aidan Follestad (afollestad)
 */
public class ImpressionVideoView extends VideoView {

    public ImpressionVideoView(Context context) {
        super(context);
    }

    public ImpressionVideoView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    private static final int FADE_DELAY = ViewerActivity.TOOLBAR_FADE_OFFSET;
    private static final int FADE_DURATION = ViewerActivity.TOOLBAR_FADE_DURATION;

    private ViewerPageFragment mFragment;
    private ImageView mIcon;
    private View mOverlay;
    private View mSeekerFrame;
    private TextView mPosition;
    private TextView mDuration;
    private SeekBar mSeeker;
    private Handler mUpdateHandler;
    private boolean mWasPlaying;
    private boolean mClickedOverlay;

    private void reset(View view) {
        view.animate().cancel();
        view.setAlpha(1f);
        view.setVisibility(View.VISIBLE);
    }

    private void fade(final View view, boolean delay, final float dest) {
        view.animate().cancel();
        ViewPropertyAnimator animator = view.animate()
                .setDuration(FADE_DURATION)
                .alpha(dest)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                        if (dest == 0f) {
                            view.setVisibility(View.GONE);
                        } else view.setVisibility(View.VISIBLE);
                    }
                });
        if (delay) {
            animator = animator.setStartDelay(FADE_DELAY);
        }
        animator.start();
    }

    public void pause(boolean fade) {
        mClickedOverlay = false;
        super.pause();
        mIcon.setImageResource(R.drawable.ic_play);
        reset(mIcon);
        reset(mSeekerFrame);
        if (fade) {
            fade(mIcon, true, 0f);
            fade(mSeekerFrame, true, 0f);
        }
    }

    @Override
    public void pause() {
        pause(true);
    }

    @Override
    public void start() {
        mClickedOverlay = false;
        super.start();
        startSeekerUpdates();
        mIcon.setImageResource(R.drawable.ic_pause);
        reset(mIcon);
        reset(mSeekerFrame);
        fade(mIcon, true, 0f);
        fade(mSeekerFrame, true, 0f);
    }

    public void hookViews(ViewerPageFragment fragment, View overlay) {
        mFragment = fragment;
        mOverlay = overlay;
        mIcon = (ImageView) overlay.findViewById(R.id.icon);
        mPosition = (TextView) overlay.findViewById(R.id.position);
        mDuration = (TextView) overlay.findViewById(R.id.duration);
        mSeeker = (SeekBar) overlay.findViewById(R.id.seeker);
        mSeekerFrame = overlay.findViewById(R.id.seekerFrame);

        overlay.setBackgroundResource(Utils.resolveDrawable(fragment.getActivity(), R.attr.video_overlay_selector));

        overlay.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mClickedOverlay) {
                    mFragment.invokeToolbar(new ViewerActivity.ToolbarFadeListener() {
                        @Override
                        public void onFade() {
                            mClickedOverlay = false;
                        }
                    });
                }
                if (isPlaying()) {
                    if (mClickedOverlay || mIcon.getAlpha() > 0f) {
                        pause();
                    } else {
                        mClickedOverlay = true;
                        reset(mIcon);
                        reset(mSeekerFrame);
                        fade(mIcon, true, 0f);
                        fade(mSeekerFrame, true, 0f);
                    }
                } else {
                    if (mClickedOverlay || mIcon.getAlpha() > 0f || getCurrentPosition() == 0) {
                        start();
                    } else {
                        mClickedOverlay = true;
                        reset(mIcon);
                        reset(mSeekerFrame);
                        fade(mIcon, true, 0f);
                        fade(mSeekerFrame, true, 0f);
                    }
                }
            }
        });

        setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                // Load initial frame and position/duration times
                ImpressionVideoView.super.start();
                ImpressionVideoView.super.pause();
                startSeekerUpdates();
            }
        });
        setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                reset(mIcon);
                mIcon.setImageResource(R.drawable.ic_play);
            }
        });
        mSeeker.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    seekTo(progress);
                    mPosition.setText(minutesAndSeconds(getCurrentPosition()));
                    mDuration.setText(minutesAndSeconds(getDuration()));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                mWasPlaying = isPlaying();
                if (mWasPlaying) mOverlay.performClick();
                reset(mSeekerFrame);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (mWasPlaying) {
                    mOverlay.performClick();
                }
            }
        });
    }

    private void startSeekerUpdates() {
        mUpdateHandler = new Handler();
        mUpdateHandler.post(updateSeekerTask);
    }

    private final Runnable updateSeekerTask = new Runnable() {
        @Override
        public void run() {
            mPosition.setText(minutesAndSeconds(getCurrentPosition()));
            mDuration.setText(minutesAndSeconds(getDuration()));
            mSeeker.setProgress(getCurrentPosition());
            mSeeker.setMax(getDuration());
            if (isPlaying())
                mUpdateHandler.postDelayed(updateSeekerTask, 150);
        }
    };

    private String minutesAndSeconds(long millis) {
        String minutes = TimeUnit.MILLISECONDS.toSeconds(millis) / 60 + ":";
        String seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60 + "";
        if (seconds.length() == 1) seconds = "0" + seconds;
        return minutes + seconds;
    }
}
