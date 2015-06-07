package com.afollestad.impression.ui.editor;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.effect.Effect;
import android.media.effect.EffectContext;
import android.media.effect.EffectFactory;
import android.net.Uri;
import android.opengl.GLES20;
import android.opengl.GLException;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.SeekBar;
import android.widget.TextView;

import com.afollestad.impression.R;
import com.afollestad.impression.utils.Utils;
import com.afollestad.impression.views.IconizedMenu;
import com.afollestad.materialdialogs.MaterialDialog;
import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.ion.Ion;

import java.io.File;
import java.nio.IntBuffer;
import java.util.Timer;
import java.util.TimerTask;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class EditorActivity extends AppCompatActivity implements GLSurfaceView.Renderer {

    private GLSurfaceView mEffectView;
    private Bitmap mBitmap;

    private final int[] mTextures = new int[2];
    private EffectContext mEffectContext;
    private Effect mEffect;
    private final TextureRenderer mTexRenderer = new TextureRenderer();
    private int mImageWidth;
    private int mImageHeight;
    private boolean mInitialized = false;
    private String mCurrentEffect;
    private int mCurrentRotation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getIntent().getData() == null) {
            finish();
            return;
        }
        setContentView(R.layout.activity_editor);

        Toolbar mToolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(mToolbar);
        mToolbar.setNavigationIcon(R.drawable.ic_action_discard);
        mToolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });

        mEffectView = (GLSurfaceView) findViewById(R.id.effectsview);
        mEffectView.setEGLContextClientVersion(2);
        mEffectView.setRenderer(EditorActivity.this);
        mEffectView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

        Uri data = getIntent().getData();
        if (data.getScheme().equals("content")) {
            Ion.with(this)
                    .load(data.toString())
                    .asBitmap()
                    .setCallback(new FutureCallback<Bitmap>() {
                        @Override
                        public void onCompleted(Exception e, final Bitmap bitmap) {
                            displayBitmap(bitmap);
                        }
                    });
        } else {
            Ion.with(this)
                    .load(new File(data.getPath()))
                    .asBitmap()
                    .setCallback(new FutureCallback<Bitmap>() {
                        @Override
                        public void onCompleted(Exception e, final Bitmap bitmap) {
                            displayBitmap(bitmap);
                        }
                    });
        }

        hookupOptions();

        new MaterialDialog.Builder(this)
                .title("Preview")
                .content("This is preview of the editor. It is not done and will not save edits to your image. A future update will include a fully functional editor.")
                .positiveText(android.R.string.ok)
                .show();
    }

    private interface AdjusterCallack {
        void onAdjusted(int value);
    }

    private Timer mApplyDelayer;
    private AdjusterCallack mAdjusterCallback;

    private void updateSeekLabel(float newValue) {
        TextView label = (TextView) findViewById(R.id.seekbarLabel);
        String text = Utils.roundToDecimals(newValue, 1) + "";
        if (newValue == 0) text = "0";
        else if (newValue > 0) text = "+" + text;
        label.setText(text);
    }

    private void toggleAdjuster(boolean visible, int max, int defaultValue, AdjusterCallack callback) {
        findViewById(R.id.seekerFrame).setVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible) {
            mAdjusterCallback = callback;
            SeekBar adjuster = (SeekBar) findViewById(R.id.seekbar);
            adjuster.setMax(max);
            adjuster.setProgress(defaultValue);
            adjuster.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    mAdjusterCallback.onAdjusted(progress);
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });
        } else {
            setCurrentEffect(null, true);
        }
    }

    private Bitmap createBitmapFromGLSurface(int x, int y, int w, int h, GL10 gl) throws OutOfMemoryError {
        int bitmapBuffer[] = new int[w * h];
        int bitmapSource[] = new int[w * h];
        IntBuffer intBuffer = IntBuffer.wrap(bitmapBuffer);
        intBuffer.position(0);
        try {
            gl.glReadPixels(x, y, w, h, GL10.GL_RGBA, GL10.GL_UNSIGNED_BYTE, intBuffer);
            int offset1, offset2;
            for (int i = 0; i < h; i++) {
                offset1 = i * w;
                offset2 = (h - i - 1) * w;
                for (int j = 0; j < w; j++) {
                    int texturePixel = bitmapBuffer[offset1 + j];
                    int blue = (texturePixel >> 16) & 0xff;
                    int red = (texturePixel << 16) & 0x00ff0000;
                    int pixel = (texturePixel & 0xff00ff00) | red | blue;
                    bitmapSource[offset2 + j] = pixel;
                }
            }
        } catch (GLException e) {
            return null;
        }
        return Bitmap.createBitmap(bitmapSource, w, h, Bitmap.Config.ARGB_8888);
    }

    private void scheduleEffectApply() {
        if (mApplyDelayer != null) {
            mApplyDelayer.cancel();
            mApplyDelayer.purge();
        }
        mApplyDelayer = new Timer();
        mApplyDelayer.schedule(new TimerTask() {
            @Override
            public void run() {
                mEffectView.requestRender();
            }
        }, 150);
    }

    private void hookupOptions() {
        findViewById(R.id.edit_autofix).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (v.isActivated()) {
                    v.setActivated(false);
                    toggleAdjuster(false, 0, 0, null);
                } else {
                    v.setActivated(true);
                    setCurrentEffect(EffectFactory.EFFECT_AUTOFIX, false);
                    toggleAdjuster(true, 10, 0, new AdjusterCallack() {
                        @Override
                        public void onAdjusted(int value) {
                            final float calculatedValue = (float) value / 10f;
                            updateSeekLabel(calculatedValue);
                            mEffect.setParameter("scale", calculatedValue);
                            scheduleEffectApply();
                        }
                    });
                }
            }
        });
        findViewById(R.id.edit_filters).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PopupMenu menu = new PopupMenu(EditorActivity.this, v);
                menu.inflate(R.menu.filters);
                menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        setCurrentEffect(item.getItemId());
                        return true;
                    }
                });
                menu.show();
            }
        });
        findViewById(R.id.edit_crop).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // TODO
            }
        });
        findViewById(R.id.edit_rotate).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                IconizedMenu menu = new IconizedMenu(EditorActivity.this, v);
                menu.inflate(R.menu.rotation_popup);
                menu.setOnMenuItemClickListener(new IconizedMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        if (item.getItemId() == R.id.rotateLeft) {
                            if (mCurrentRotation == 0) mCurrentRotation = 270;
                            else if (mCurrentRotation == 90) mCurrentRotation = 0;
                            else if (mCurrentRotation == 180) mCurrentRotation = 90;
                            else if (mCurrentRotation == 270) mCurrentRotation = 180;
                        } else {
                            if (mCurrentRotation == 0) mCurrentRotation = 90;
                            else if (mCurrentRotation == 90) mCurrentRotation = 180;
                            else if (mCurrentRotation == 180) mCurrentRotation = 270;
                            else if (mCurrentRotation == 270) mCurrentRotation = 0;
                        }
                        setCurrentEffect(EffectFactory.EFFECT_ROTATE, false);
                        mEffect.setParameter("angle", mCurrentRotation);
                        mEffectView.requestRender();
                        return true;
                    }
                });
                menu.show();
            }
        });
        findViewById(R.id.edit_flip).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                IconizedMenu menu = new IconizedMenu(EditorActivity.this, v);
                menu.inflate(R.menu.flip_popup);
                menu.setOnMenuItemClickListener(new IconizedMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        if (item.getItemId() == R.id.flipVertical) {
                            if (mCurrentRotation == 0) {
                                mCurrentRotation = 180;
                                setCurrentEffect(EffectFactory.EFFECT_FLIP, false);
                                mEffect.setParameter("vertical", true);
                            } else {
                                mCurrentRotation = 0;
                                setCurrentEffect(null, false);
                            }
                        } else {
                            if (mCurrentRotation == 0) {
                                mCurrentRotation = 180;
                                setCurrentEffect(EffectFactory.EFFECT_FLIP, false);
                                mEffect.setParameter("horizontal", true);
                            } else {
                                mCurrentRotation = 0;
                                setCurrentEffect(null, false);
                            }
                        }
                        mEffectView.requestRender();
                        return true;
                    }
                });
                menu.show();
            }
        });
        findViewById(R.id.edit_brightness).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (v.isActivated()) {
                    v.setActivated(false);
                    toggleAdjuster(false, 0, 0, null);
                } else {
                    v.setActivated(true);
                    setCurrentEffect(EffectFactory.EFFECT_BRIGHTNESS, false);
                    updateSeekLabel(1f);
                    toggleAdjuster(true, 20, 10, new AdjusterCallack() {
                        @Override
                        public void onAdjusted(int value) {
                            final float calculatedValue = (float) value / 10f;
                            updateSeekLabel(calculatedValue - 1f);
                            mEffect.setParameter("brightness", calculatedValue);
                            scheduleEffectApply();
                        }
                    });
                }
            }
        });
        findViewById(R.id.edit_contrast).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (v.isActivated()) {
                    v.setActivated(false);
                    toggleAdjuster(false, 0, 0, null);
                } else {
                    v.setActivated(true);
                    setCurrentEffect(EffectFactory.EFFECT_CONTRAST, false);
                    updateSeekLabel(1f);
                    toggleAdjuster(true, 40, 10, new AdjusterCallack() {
                        @Override
                        public void onAdjusted(int value) {
                            final float calculatedValue = (float) value / 10f;
                            updateSeekLabel(calculatedValue - 1f);
                            mEffect.setParameter("contrast", calculatedValue);
                            scheduleEffectApply();
                        }
                    });
                }
            }
        });
        findViewById(R.id.edit_saturation).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (v.isActivated()) {
                    v.setActivated(false);
                    toggleAdjuster(false, 0, 0, null);
                } else {
                    v.setActivated(true);
                    setCurrentEffect(EffectFactory.EFFECT_SATURATE, false);
                    updateSeekLabel(0f);
                    toggleAdjuster(true, 20, 10, new AdjusterCallack() {
                        @Override
                        public void onAdjusted(int value) {
                            if (value == 10) value = 0;
                            else if (value < 10)
                                value = -1 * (10 - value);
                            else value /= 2;
                            final float calculatedValue = (float) value / 10f;
                            updateSeekLabel(calculatedValue);
                            mEffect.setParameter("scale", calculatedValue);
                            scheduleEffectApply();
                        }
                    });
                }
            }
        });
        findViewById(R.id.edit_sharpen).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (v.isActivated()) {
                    v.setActivated(false);
                    toggleAdjuster(false, 0, 0, null);
                } else {
                    v.setActivated(true);
                    setCurrentEffect(EffectFactory.EFFECT_SHARPEN, false);
                    updateSeekLabel(0f);
                    toggleAdjuster(true, 10, 0, new AdjusterCallack() {
                        @Override
                        public void onAdjusted(int value) {
                            final float calculatedValue = (float) value / 10f;
                            updateSeekLabel(calculatedValue);
                            mEffect.setParameter("scale", calculatedValue);
                            scheduleEffectApply();
                        }
                    });
                }
            }
        });
    }

    private void displayBitmap(Bitmap bitmap) {
        Log.v("EditorActivity", "Displaying loaded bitmap...");
        findViewById(R.id.progress).setVisibility(View.GONE);
        findViewById(R.id.editControls).setVisibility(View.VISIBLE);
        mBitmap = bitmap;
        mEffectView.requestRender();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.editor, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig eglConfig) {
        // Nothing to do here
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        if (mTexRenderer != null)
            mTexRenderer.updateViewSize(width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        if (mBitmap != null) {
            if (!mInitialized) {
                mEffectContext = EffectContext.createWithCurrentGlContext();
                mTexRenderer.init();
                loadTextures();
                mInitialized = true;
            }
            if (mCurrentEffect != null)
                applyEffect();
            renderResult();
        }
    }

    private void loadTextures() {
        if (mBitmap != null) {
            GLES20.glGenTextures(2, mTextures, 0);
            mImageWidth = mBitmap.getWidth();
            mImageHeight = mBitmap.getHeight();
            mTexRenderer.updateTextureSize(mImageWidth, mImageHeight);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextures[0]);
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, mBitmap, 0);
            GLToolbox.initTexParams();

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    int width = mImageWidth;
                    if (width > mEffectView.getWidth()) width = mEffectView.getWidth();
                    int height = mImageHeight;
                    if (height > mEffectView.getHeight()) height = mEffectView.getHeight();
//                    final Rect bitmapRect = ImageViewUtil.getBitmapRectCenterInsideHelper(
//                            width,
//                            height,
//                            mEffectView.getWidth(),
//                            mEffectView.getHeight()
//                    );
//                   TODO ((CropOverlayView) findViewById(R.id.cropView)).setBitmapRect(bitmapRect);
                }
            });
        }
    }

    private void setCurrentEffect(int id) {
        // TODO allow scales to be changed with the seeker
        switch (id) {
            default:
                setCurrentEffect(null, true);
                return;
            case R.id.crossprocess:
                setCurrentEffect(EffectFactory.EFFECT_CROSSPROCESS, false);
                break;
            case R.id.documentary:
                setCurrentEffect(EffectFactory.EFFECT_DOCUMENTARY, false);
                break;
            case R.id.duotone:
                setCurrentEffect(EffectFactory.EFFECT_DUOTONE, false);
                mEffect.setParameter("first_color", Color.YELLOW);
                mEffect.setParameter("second_color", Color.DKGRAY);
                break;
            case R.id.filllight:
                setCurrentEffect(EffectFactory.EFFECT_FILLLIGHT, false);
                mEffect.setParameter("strength", .8f);
                break;
            case R.id.fisheye:
                setCurrentEffect(EffectFactory.EFFECT_FISHEYE, false);
                mEffect.setParameter("scale", .5f);
                break;
            case R.id.grain:
                setCurrentEffect(EffectFactory.EFFECT_GRAIN, false);
                mEffect.setParameter("strength", 1.0f);
                break;
            case R.id.grayscale:
                setCurrentEffect(EffectFactory.EFFECT_GRAYSCALE, false);
                break;
            case R.id.lomoish:
                setCurrentEffect(EffectFactory.EFFECT_LOMOISH, false);
                break;
            case R.id.negative:
                setCurrentEffect(EffectFactory.EFFECT_NEGATIVE, false);
                break;
            case R.id.posterize:
                setCurrentEffect(EffectFactory.EFFECT_POSTERIZE, false);
                break;
            case R.id.sepia:
                setCurrentEffect(EffectFactory.EFFECT_SEPIA, false);
                break;
            case R.id.temperature:
                setCurrentEffect(EffectFactory.EFFECT_TEMPERATURE, false);
                mEffect.setParameter("scale", .9f);
                break;
            case R.id.tint:
                // TODO custom colors?
                setCurrentEffect(EffectFactory.EFFECT_TINT, false);
                mEffect.setParameter("tint", Color.MAGENTA);
                break;
            case R.id.vignette:
                setCurrentEffect(EffectFactory.EFFECT_VIGNETTE, false);
                mEffect.setParameter("scale", .5f);
                break;
        }
        mEffectView.requestRender();
    }

    private void setCurrentEffect(String effect, boolean renderNow) {
        EffectFactory effectFactory = mEffectContext.getFactory();
        if (mEffect != null) {
            mEffect.release();
            mEffect = null;
        }
        mCurrentEffect = effect;
        if (effect != null) {
            mEffect = effectFactory.createEffect(effect);
        } else {
            mCurrentRotation = 0;
            mAdjusterCallback = null;
            if (mApplyDelayer != null) {
                mApplyDelayer.cancel();
                mApplyDelayer.purge();
                mApplyDelayer = null;
            }
        }
        if (renderNow)
            mEffectView.requestRender();
    }

    private void applyEffect() {
        if (mEffect == null) return;
        mEffect.apply(mTextures[0], mImageWidth, mImageHeight, mTextures[1]);
    }

    private void renderResult() {
        if (mCurrentEffect != null) {
            // if no effect is chosen, just render the original bitmap
            mTexRenderer.renderTexture(mTextures[1]);
        } else {
            // render the result of applyEffect()
            mTexRenderer.renderTexture(mTextures[0]);
        }
    }
}
