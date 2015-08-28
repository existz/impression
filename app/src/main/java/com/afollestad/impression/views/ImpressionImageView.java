package com.afollestad.impression.views;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;

import com.afollestad.impression.R;
import com.afollestad.impression.api.AlbumEntry;
import com.afollestad.impression.api.base.MediaEntry;
import com.afollestad.impression.utils.Utils;
import com.koushikdutta.ion.Ion;
import com.koushikdutta.ion.builder.AnimateGifMode;

import java.lang.ref.WeakReference;

/**
 * @author Aidan Follestad (afollestad)
 */
public class ImpressionImageView extends ImageView {

    public ImpressionImageView(Context context) {
        super(context);
        init(context);
    }

    public ImpressionImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        if (isInEditMode()) return;
        final Resources r = getResources();
        mCheck = BitmapFactory.decodeResource(r, R.drawable.ic_check);
        mPlay = BitmapFactory.decodeResource(r, R.drawable.ic_play);
        mGif = BitmapFactory.decodeResource(r, R.drawable.ic_gif);
        mPlayOverlay = ContextCompat.getColor(context, R.color.video_play_overlay);
        mSelectedColor = ContextCompat.getColor(context, R.color.picture_activated_overlay);
    }

    private MediaEntry mEntry;
    private WeakReference<View> mProgress;
    private Bitmap mCheck;
    private Bitmap mPlay;
    private Bitmap mGif;
    private int mPlayOverlay;
    private int mSelectedColor;
    private boolean isGif;

    public void load(MediaEntry entry, View progress) {
        setTag(null);
        if (isInEditMode()) return;
        mEntry = entry;
        if (mEntry == null) return;
        if (mProgress == null && progress != null)
            mProgress = new WeakReference<>(progress);
        if (getMeasuredWidth() == 0) return;

        setImageDrawable(null);
        if (mProgress != null && mProgress.get() != null) {
            mProgress.get().setVisibility(View.VISIBLE);
        }
        String pathToLoad = entry.data();
        if (entry.isAlbum())
            pathToLoad = ((AlbumEntry) entry).mFirstPath;
        String ext = Utils.getExtension(entry.data());
        isGif = ext != null && ext.equalsIgnoreCase("gif");
        Ion.with(this)
                .centerCrop()
                .animateGif(AnimateGifMode.NO_ANIMATE)
                .load(pathToLoad);
    }

    private void load(int resourceId) {
        setTag(resourceId);
        if (getMeasuredWidth() == 0) return;
        setImageDrawable(null);
        Ion.with(this)
                .centerCrop()
                .load("android.resource://" + getContext().getPackageName() + "/" + resourceId);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        setMeasuredDimension(widthMeasureSpec, widthMeasureSpec);
        if (getTag() != null)
            load((Integer) getTag());
        else load(mEntry, null);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (getMeasuredWidth() > 0 && getDrawable() != null) {
            int targetDimen = mCheck.getWidth();
            if (mCheck.getWidth() > getMeasuredWidth())
                targetDimen = getMeasuredWidth();
            if (isActivated()) {
                if (mEntry != null && !mEntry.isFolder())
                    canvas.drawColor(mSelectedColor);
                canvas.drawBitmap(mCheck,
                        (getMeasuredWidth() / 2) - (targetDimen / 2),
                        (getMeasuredHeight() / 2) - (targetDimen / 2),
                        null);
            } else if (isGif) {
                canvas.drawColor(mPlayOverlay);
                canvas.drawBitmap(mGif,
                        (getMeasuredWidth() / 2) - (targetDimen / 2),
                        (getMeasuredHeight() / 2) - (targetDimen / 2),
                        null);
            } else if (mEntry != null && mEntry.isVideo()) {
                canvas.drawColor(mPlayOverlay);
                canvas.drawBitmap(mPlay,
                        (getMeasuredWidth() / 2) - (targetDimen / 2),
                        (getMeasuredHeight() / 2) - (targetDimen / 2),
                        null);
            }
        }
    }

    @Override
    public void setImageBitmap(Bitmap bm) {
        super.setImageBitmap(bm);
        if (mProgress != null && mProgress.get() != null) {
            mProgress.get().setVisibility(View.GONE);
        }
    }

    @Override
    public void setImageDrawable(Drawable drawable) {
        super.setImageDrawable(drawable);
        if (mProgress != null && mProgress.get() != null) {
            mProgress.get().setVisibility(View.GONE);
        }
    }

    @Override
    public void setImageResource(int resId) {
        super.setImageResource(resId);
        if (mProgress != null && mProgress.get() != null) {
            mProgress.get().setVisibility(View.GONE);
        }
    }
}