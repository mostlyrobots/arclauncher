package com.omeda.arc;

import android.Manifest;
import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

public class FireTvHomeService extends AccessibilityService {
    private static final String PREFS = "FlutterSharedPreferences";
    private static final String KEY_REMAP = "flutter.fire_tv_home_remap_enabled";
    private static final String AMAZON_LAUNCHER = "com.amazon.tv.launcher";
    private static final String KIDS_LAUNCHER = "com.amazon.tahoe";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private long lastRedirectAt;
    private String foregroundPkg;
    private final Runnable reassert = () -> {
        if ((AMAZON_LAUNCHER.equals(foregroundPkg) || KIDS_LAUNCHER.equals(foregroundPkg)) && isRemapEnabled(this)) {
            launchArc();
        }
    };

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence pkgCs = event.getPackageName(), clsCs = event.getClassName();
        if (pkgCs == null || clsCs == null) return;
        String pkg = pkgCs.toString(), cls = clsCs.toString();
        if (!cls.startsWith("android.")) foregroundPkg = pkg;

        if ((AMAZON_LAUNCHER.equals(pkg) || KIDS_LAUNCHER.equals(pkg))
                && (cls.endsWith(".HomeActivity") || cls.endsWith(".HomeActivity_vNext"))
                && isRemapEnabled(this)) {
            long now = System.currentTimeMillis();
            if (now - lastRedirectAt >= 250L) {
                lastRedirectAt = now;
                launchArc();
                handler.removeCallbacks(reassert);
                handler.postDelayed(reassert, 600L);
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
