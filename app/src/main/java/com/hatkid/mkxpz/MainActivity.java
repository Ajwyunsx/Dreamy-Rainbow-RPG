package com.hatkid.mkxpz;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.hatkid.mkxpz.gamepad.Gamepad;
import com.hatkid.mkxpz.gamepad.GamepadConfig;

import org.libsdl.app.SDLActivity;

import java.io.File;
import java.util.Locale;

public class MainActivity extends SDLActivity
{
    private static final String TAG = "DreamyRainbowRPG[Activity]";
    private static String GAME_PATH = "";
    private static boolean DEBUG = false;

    protected boolean mStarted = false;

    protected static Handler mMainHandler;
    protected static Vibrator mVibrator;
    protected static TextView tvFps;

    private final Gamepad mGamepad = new Gamepad();
    private boolean mGamepadVisible = true;

    private void runSDLThread()
    {
        if (!mStarted) {
            Log.i(TAG, "Game path: " + GAME_PATH);
        }

        mStarted = true;

        if (mHasMultiWindow) {
            resumeNativeThread();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        String installDir = ChapterLaunchConfig.sanitizeInstallDir(
            getIntent().getStringExtra(ChapterLaunchConfig.EXTRA_GAME_SUBDIR)
        );
        GAME_PATH = new File(getFilesDir(), installDir).getAbsolutePath();
        mMainHandler = new Handler(getMainLooper());
        mVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        try {
            ActivityInfo actInfo = getPackageManager().getActivityInfo(this.getComponentName(), PackageManager.GET_META_DATA);
            if (actInfo.metaData != null) {
                DEBUG = actInfo.metaData.getBoolean("mkxp_debug");
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Failed to set debug flag", e);
        }

        GamepadConfig gpadConfig = new GamepadConfig();
        mGamepad.init(gpadConfig, false);
        mGamepad.setOnKeyDownListener(SDLActivity::onNativeKeyDown);
        mGamepad.setOnKeyUpListener(SDLActivity::onNativeKeyUp);

        if (mLayout != null) {
            mGamepad.attachTo(this, mLayout);
            applyGamepadVisibility(false);
        }

        tvFps = new TextView(this);
        tvFps.setTextSize((8 * ((float) getResources().getDisplayMetrics().densityDpi / DisplayMetrics.DENSITY_DEFAULT)));
        tvFps.setTextColor(Color.argb(96, 255, 255, 255));
        tvFps.setVisibility(View.GONE);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        params.setMargins(16, 16, 0, 0);
        tvFps.setLayoutParams(params);

        if (mLayout != null) {
            mLayout.addView(tvFps);
        }
    }

    @Override
    protected void onStart()
    {
        super.onStart();
        runSDLThread();
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        applyGamepadVisibility(false);
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();

        // Ruby cannot be cleanly re-initialized inside the same process.
        System.exit(0);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent evt)
    {
        if (shouldHideGamepadForKey(evt)) {
            setGamepadVisible(false, true);
        }

        if (mGamepad.processGamepadEvent(evt)) {
            return true;
        }

        return super.dispatchKeyEvent(evt);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent evt)
    {
        if (evt.getActionMasked() == MotionEvent.ACTION_DOWN || evt.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
            setGamepadVisible(true, true);
        }

        return super.dispatchTouchEvent(evt);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent evt)
    {
        if (isExternalMotion(evt)) {
            setGamepadVisible(false, true);
        }

        if (mGamepad.processDPadEvent(evt)) {
            return true;
        }

        return super.onGenericMotionEvent(evt);
    }

    @Override
    protected String[] getArguments()
    {
        if (DEBUG) {
            return new String[] { "debug" };
        }

        return new String[] {};
    }

    private void setGamepadVisible(boolean visible, boolean animate)
    {
        if (mGamepadVisible == visible && mGamepad.isVisible() == visible) {
            return;
        }

        mGamepadVisible = visible;
        applyGamepadVisibility(animate);
    }

    private void applyGamepadVisibility(boolean animate)
    {
        mGamepad.setVisible(mGamepadVisible, animate);
    }

    private boolean shouldHideGamepadForKey(KeyEvent evt)
    {
        int keyCode = evt.getKeyCode();
        return keyCode != KeyEvent.KEYCODE_BACK &&
            keyCode != KeyEvent.KEYCODE_VOLUME_UP &&
            keyCode != KeyEvent.KEYCODE_VOLUME_DOWN &&
            keyCode != KeyEvent.KEYCODE_VOLUME_MUTE &&
            keyCode != KeyEvent.KEYCODE_HEADSETHOOK;
    }

    private boolean isExternalMotion(MotionEvent evt)
    {
        int source = evt.getSource();
        return ((source & android.view.InputDevice.SOURCE_DPAD) == android.view.InputDevice.SOURCE_DPAD) ||
            ((source & android.view.InputDevice.SOURCE_GAMEPAD) == android.view.InputDevice.SOURCE_GAMEPAD) ||
            ((source & android.view.InputDevice.SOURCE_JOYSTICK) == android.view.InputDevice.SOURCE_JOYSTICK);
    }

    @SuppressLint("SetTextI18n")
    @SuppressWarnings("unused")
    private static void updateFPSText(int num)
    {
        mMainHandler.post(() -> tvFps.setText(num + " FPS"));
    }

    @SuppressWarnings("unused")
    private static void setFPSVisibility(boolean visible)
    {
        mMainHandler.post(() -> tvFps.setVisibility(visible ? View.VISIBLE : View.INVISIBLE));
    }

    @SuppressWarnings("unused")
    private static String getSystemLanguage()
    {
        return Locale.getDefault().toString();
    }

    @SuppressWarnings("unused")
    private static boolean hasVibrator()
    {
        return mVibrator != null && mVibrator.hasVibrator();
    }

    @SuppressWarnings("unused")
    private static void vibrate(int duration)
    {
        if (mVibrator == null) {
            return;
        }

        duration = Math.min(duration, 10000);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mVibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.EFFECT_HEAVY_CLICK));
        } else {
            mVibrator.vibrate(duration);
        }
    }

    @SuppressWarnings("unused")
    private static void vibrateStop()
    {
        if (mVibrator != null) {
            mVibrator.cancel();
        }
    }

    @SuppressWarnings("unused")
    private static boolean inMultiWindow(Activity activity)
    {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && activity.isInMultiWindowMode();
    }
}
