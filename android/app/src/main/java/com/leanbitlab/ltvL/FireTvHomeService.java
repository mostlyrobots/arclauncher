package com.omeda.arc;

import android.Manifest;
import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

public class FireTvHomeService extends AccessibilityService {
    private static final String PREFS = "FlutterSharedPreferences";
    private static final String KEY_REMAP = "flutter.fire_tv_home_remap_enabled";
    private static final String AMAZON_LAUNCHER = "com.amazon.tv.launcher";
    private static final String HOME_STARTER = "com.amazon.firehomestarter";
    private static final String KIDS_LAUNCHER = "com.amazon.tahoe";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private long lastRedirectAt;
    private String foregroundPkg;
    private View keepAliveOverlay;
    private final Runnable reassert = () -> {
        if (isStockLauncher(foregroundPkg) && isRemapEnabled(this)) {
            launchArc();
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        sync(this);
        attachKeepAliveOverlay();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence pkgCs = event.getPackageName(), clsCs = event.getClassName();
        if (pkgCs == null || clsCs == null) return;
        String pkg = pkgCs.toString(), cls = clsCs.toString();
        if (!cls.startsWith("android.")) foregroundPkg = pkg;

        if (isStockLauncher(pkg)
                && (cls.endsWith(".HomeActivity") || cls.endsWith(".HomeActivity_vNext")
                        || cls.endsWith(".HomeStarterActivity"))
                && isRemapEnabled(this)) {
            long now = System.currentTimeMillis();
            if (now - lastRedirectAt >= 100L) {
                lastRedirectAt = now;
                launchArc();
                handler.removeCallbacks(reassert);
                handler.postDelayed(reassert, 200L);
            }
        }
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event != null && event.getKeyCode() == KeyEvent.KEYCODE_HOME && isRemapEnabled(this)) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) launchArc();
            return true;
        }
        return false;
    }

    @Override
    public void onInterrupt() {
        handler.removeCallbacks(reassert);
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(reassert);
        detachKeepAliveOverlay();
        super.onDestroy();
    }

    private void attachKeepAliveOverlay() {
        if (keepAliveOverlay != null) return;
        try {
            WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            if (wm == null) return;
            View v = new View(this);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    1, 1,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.START;
            wm.addView(v, lp);
            keepAliveOverlay = v;
        } catch (Exception ignored) {
        }
    }

    private void detachKeepAliveOverlay() {
        if (keepAliveOverlay == null) return;
        try {
            WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            if (wm != null) wm.removeView(keepAliveOverlay);
        } catch (Exception ignored) {
        }
        keepAliveOverlay = null;
    }

    private static boolean isStockLauncher(String pkg) {
        return AMAZON_LAUNCHER.equals(pkg) || HOME_STARTER.equals(pkg) || KIDS_LAUNCHER.equals(pkg);
    }

    private void launchArc() {
        try {
            startActivity(new Intent(this, MainActivity.class)
                    .setAction(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                            | Intent.FLAG_ACTIVITY_NO_ANIMATION));
        } catch (Exception ignored) {
        }
    }

    public static boolean isFireTv(Context ctx) {
        return "Amazon".equalsIgnoreCase(Build.MANUFACTURER)
                || ctx.getPackageManager().hasSystemFeature("amazon.hardware.fire_tv");
    }

    public static boolean isRemapEnabled(Context ctx) {
        return ctx.getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_REMAP, isFireTv(ctx));
    }

    public static void sync(Context ctx) {
        if (!isFireTv(ctx) || ctx.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                != PackageManager.PERMISSION_GRANTED) return;
        try {
            ContentResolver cr = ctx.getContentResolver();
            String svc = ctx.getPackageName() + "/" + FireTvHomeService.class.getName();
            String cur = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (cur == null || !cur.contains(svc)) {
                Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                        (cur == null || cur.isEmpty()) ? svc : cur + ":" + svc);
            }
            Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1);
            // Registering our service in voice_interaction_service triggers Android's
            // VoiceInteractionManagerService to add our UID to ActivityManagerService.mAllowAppSwitchUids,
            // exempting Arc from the 5-second stopAppSwitches() Home button penalty on Fire OS.
            if (!svc.equals(Settings.Secure.getString(cr, "voice_interaction_service"))) {
                Settings.Secure.putString(cr, "voice_interaction_service", svc);
            }
        } catch (SecurityException ignored) {
        }
    }

    public static class BootReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (context != null) sync(context);
        }
    }
}
