package com.afollestad.impression.views;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.ImageView;

import uk.co.senab.photoview.PhotoViewAttacher;

/**
 * @author Aidan Follestad (afollestad)
 */
public class ScaleListenerImageView extends ImageView {

    public ScaleListenerImageView(Context context) {
        super(context);
    }

    public ScaleListenerImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    private PhotoViewAttacher mAttacher;
    private boolean checkForChange;

    public PhotoViewAttacher setPhotoAttacher() {
        if (mAttacher != null) {
            mAttacher.cleanup();
            mAttacher = null;
        }
        mAttacher = new PhotoViewAttacher(this);
        return mAttacher;
    }

    @Override
    public void setScaleType(ScaleType scaleType) {
        super.setScaleType(scaleType);
        if (scaleType == ScaleType.MATRIX)
            checkForChange = true;
        else if (checkForChange && mAttacher != null) {
            mAttacher.cleanup();
            mAttacher = null;
            Log.v("ScaleListenerImageView", "Destroying PhotoViewAttacher instance.");
        }
    }
}
