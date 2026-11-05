package com.omeda.arc;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.VideoView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NotificationOverlayService extends Service {
    private static final String PREFS = "FlutterSharedPreferences";
    private static final String KEY_ALLOW = "flutter.allow_notifications";
    private static final int PORT = 7979;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService workerPool = Executors.newCachedThreadPool();
    private ServerSocket serverSocket;
    private Thread acceptThread;
    private View activePopup;
    private VideoView activeVideo;
    private WebView activeWeb;
    private final Runnable dismissRunnable = this::removePopup;

    @Override
    public void onCreate() {
        super.onCreate();
        startServer();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!isEnabled(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        startServer();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopServer();
        mainHandler.removeCallbacks(dismissRunnable);
        removePopup();
        workerPool.shutdownNow();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private synchronized void startServer() {
        if (serverSocket != null && !serverSocket.isClosed()) return;
        acceptThread = new Thread(() -> {
            try {
                ServerSocket ss = new ServerSocket();
                ss.setReuseAddress(true);
                ss.bind(new InetSocketAddress(PORT));
                synchronized (this) {
                    serverSocket = ss;
                }
                while (!Thread.currentThread().isInterrupted() && !ss.isClosed()) {
                    Socket client = ss.accept();
                    workerPool.execute(() -> handleClient(client));
                }
            } catch (Exception ignored) {
            }
        }, "ArcPipupServer");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    private synchronized void stopServer() {
        if (acceptThread != null) {
            acceptThread.interrupt();
            acceptThread = null;
        }
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (Exception ignored) {
            }
            serverSocket = null;
        }
    }

    private void handleClient(Socket socket) {
        try (Socket s = socket;
             InputStream in = s.getInputStream();
             OutputStream out = s.getOutputStream()) {
            s.setSoTimeout(10000);
            String requestLine = readLine(in);
            if (requestLine == null || requestLine.isEmpty()) return;
            String[] parts = requestLine.split(" ");
            String method = parts.length > 0 ? parts[0] : "";
            String path = parts.length > 1 ? parts[1] : "";

            Map<String, String> headers = new HashMap<>();
            String line;
            while ((line = readLine(in)) != null && !line.isEmpty()) {
                int idx = line.indexOf(':');
                if (idx > 0) {
                    headers.put(line.substring(0, idx).trim().toLowerCase(Locale.ROOT),
                            line.substring(idx + 1).trim());
                }
            }

            if (!"POST".equalsIgnoreCase(method)) {
                writeResponse(out, 405, "Method Not Allowed");
                return;
            }

            int contentLength = 0;
            try {
                contentLength = Integer.parseInt(headers.getOrDefault("content-length", "0"));
            } catch (NumberFormatException ignored) {
            }
            byte[] body = readBytes(in, contentLength);

            if ("/cancel".equals(path)) {
                mainHandler.post(this::removePopup);
                writeResponse(out, 200, "OK");
                return;
            }

            if ("/notify".equals(path)) {
                String ct = headers.getOrDefault("content-type", "");
                PopupSpec spec = ct.toLowerCase(Locale.ROOT).startsWith("multipart/form-data")
                        ? parseMultipart(body, ct)
                        : parseJson(new String(body, StandardCharsets.UTF_8));
                if (spec != null) {
                    mainHandler.post(() -> showPopup(spec));
                    writeResponse(out, 200, "OK");
                } else {
                    writeResponse(out, 400, "Bad Request");
                }
                return;
            }

            writeResponse(out, 404, "Not Found");
        } catch (Exception ignored) {
        }
    }

    private static String readLine(InputStream in) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream(128);
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') break;
            if (b != '\r') buf.write(b);
        }
        if (b == -1 && buf.size() == 0) return null;
        return buf.toString(StandardCharsets.UTF_8.name());
    }

    private static byte[] readBytes(InputStream in, int len) throws Exception {
        if (len <= 0) return new byte[0];
        byte[] data = new byte[len];
        int read = 0;
        while (read < len) {
            int n = in.read(data, read, len - read);
            if (n == -1) break;
            read += n;
        }
        return data;
    }

    private static void writeResponse(OutputStream out, int code, String msg) throws Exception {
        byte[] body = msg.getBytes(StandardCharsets.UTF_8);
        String head = "HTTP/1.1 " + code + " " + msg + "\r\n"
                + "Content-Type: text/plain; charset=utf-8\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Connection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.UTF_8));
        out.write(body);
        out.flush();
    }

    private PopupSpec parseJson(String jsonStr) {
        try {
            JSONObject obj = new JSONObject(jsonStr);
            PopupSpec s = new PopupSpec();
            s.duration = obj.optInt("duration", 10);
            s.position = obj.optInt("position", 0);
            s.backgroundColor = obj.optString("backgroundColor", "#CC000000");
            s.title = obj.optString("title", "");
            s.titleSize = (float) obj.optDouble("titleSize", 20.0);
            s.titleColor = obj.optString("titleColor", "#FFFFFF");
            s.message = obj.optString("message", "");
            s.messageSize = (float) obj.optDouble("messageSize", 16.0);
            s.messageColor = obj.optString("messageColor", "#FFFFFF");

            JSONObject media = obj.optJSONObject("media");
            if (media != null) {
                if (media.has("image")) {
                    JSONObject img = media.getJSONObject("image");
                    String uri = img.optString("uri", "");
                    s.mediaWidth = img.optInt("width", 320);
                    if (!uri.isEmpty()) s.bitmap = fetchBitmap(uri);
                } else if (media.has("video")) {
                    JSONObject vid = media.getJSONObject("video");
                    s.videoUri = vid.optString("uri", "");
                    s.mediaWidth = vid.optInt("width", 480);
                } else if (media.has("web")) {
                    JSONObject web = media.getJSONObject("web");
                    s.webUri = web.optString("uri", "");
                    s.mediaWidth = web.optInt("width", 480);
                    s.mediaHeight = web.optInt("height", 320);
                }
            }
            return s;
        } catch (Exception e) {
            return null;
        }
    }

    private PopupSpec parseMultipart(byte[] body, String contentType) {
        try {
            int bIdx = contentType.indexOf("boundary=");
            if (bIdx < 0) return null;
            String boundary = "--" + contentType.substring(bIdx + 9).trim().replace("\"", "");
            byte[] delim = boundary.getBytes(StandardCharsets.ISO_8859_1);
            PopupSpec s = new PopupSpec();
            int pos = indexOf(body, delim, 0);
            while (pos >= 0) {
                int start = pos + delim.length;
                if (start + 1 < body.length && body[start] == '\r' && body[start + 1] == '\n') start += 2;
                int next = indexOf(body, delim, start);
                if (next < 0) break;
                int partEnd = next - 2; // strip trailing \r\n before boundary
                int headerEnd = indexOf(body, new byte[]{'\r', '\n', '\r', '\n'}, start);
                if (headerEnd > start && headerEnd < partEnd) {
                    String hdr = new String(body, start, headerEnd - start, StandardCharsets.UTF_8);
                    int contentStart = headerEnd + 4;
                    int contentLen = Math.max(0, partEnd - contentStart);
                    String name = extractParam(hdr, "name=\"");
                    if ("image".equals(name)) {
                        s.bitmap = BitmapFactory.decodeByteArray(body, contentStart, contentLen);
                    } else if (name != null) {
                        String val = new String(body, contentStart, contentLen, StandardCharsets.UTF_8).trim();
                        switch (name) {
                            case "duration" -> s.duration = Integer.parseInt(val);
                            case "position" -> s.position = Integer.parseInt(val);
                            case "backgroundColor" -> s.backgroundColor = val;
                            case "title" -> s.title = val;
                            case "titleSize" -> s.titleSize = Float.parseFloat(val);
                            case "titleColor" -> s.titleColor = val;
                            case "message" -> s.message = val;
                            case "messageSize" -> s.messageSize = Float.parseFloat(val);
                            case "messageColor" -> s.messageColor = val;
                            case "imageWidth" -> s.mediaWidth = Integer.parseInt(val);
                        }
                    }
                }
                pos = next;
            }
            return s;
        } catch (Exception e) {
            return null;
        }
    }

    private static String extractParam(String hdr, String key) {
        int i = hdr.indexOf(key);
        if (i < 0) return null;
        int start = i + key.length();
        int end = hdr.indexOf('"', start);
        return end > start ? hdr.substring(start, end) : null;
    }

    private static int indexOf(byte[] data, byte[] pattern, int from) {
        outer:
        for (int i = from; i <= data.length - pattern.length; i++) {
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) continue outer;
            }
            return i;
        }
        return -1;
    }

    private static Bitmap fetchBitmap(String uri) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(uri).openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            try (InputStream is = conn.getInputStream()) {
                return BitmapFactory.decodeStream(is);
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private void showPopup(PopupSpec spec) {
        if (!isEnabled(this)) return;
        mainHandler.removeCallbacks(dismissRunnable);
        removePopup();

        try {
            WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            if (wm == null) return;

            LinearLayout root = new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            int pad = dp(16);
            root.setPadding(pad, pad, pad, pad);

            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(10));
            bg.setColor(parseColor(spec.backgroundColor, 0xCC000000));
            root.setBackground(bg);

            if (spec.bitmap != null) {
                ImageView iv = new ImageView(this);
                int w = dp(spec.mediaWidth > 0 ? spec.mediaWidth : 320);
                int h = Math.max(1, Math.round(spec.bitmap.getHeight() * (w / (float) spec.bitmap.getWidth())));
                iv.setImageBitmap(spec.bitmap);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
                lp.bottomMargin = (!spec.title.isEmpty() || !spec.message.isEmpty()) ? dp(10) : 0;
                lp.gravity = Gravity.CENTER_HORIZONTAL;
                root.addView(iv, lp);
            } else if (spec.videoUri != null && !spec.videoUri.isEmpty()) {
                int w = dp(spec.mediaWidth > 0 ? spec.mediaWidth : 480);
                int h = Math.round(w * 9f / 16f);
                FrameLayout frame = new FrameLayout(this);
                VideoView vv = new VideoView(this);
                vv.setVideoURI(Uri.parse(spec.videoUri));
                vv.setOnPreparedListener(mp -> {
                    mp.setLooping(true);
                    vv.start();
                });
                frame.addView(vv, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
                lp.bottomMargin = (!spec.title.isEmpty() || !spec.message.isEmpty()) ? dp(10) : 0;
                root.addView(frame, lp);
                activeVideo = vv;
            } else if (spec.webUri != null && !spec.webUri.isEmpty()) {
                int w = dp(spec.mediaWidth > 0 ? spec.mediaWidth : 480);
                int h = dp(spec.mediaHeight > 0 ? spec.mediaHeight : 320);
                WebView wv = new WebView(this);
                wv.getSettings().setJavaScriptEnabled(true);
                wv.setWebViewClient(new WebViewClient());
                wv.loadUrl(spec.webUri);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
                lp.bottomMargin = (!spec.title.isEmpty() || !spec.message.isEmpty()) ? dp(10) : 0;
                root.addView(wv, lp);
                activeWeb = wv;
            }

            if (!spec.title.isEmpty()) {
                TextView tv = new TextView(this);
                tv.setText(spec.title);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, spec.titleSize);
                tv.setTextColor(parseColor(spec.titleColor, Color.WHITE));
                tv.setTypeface(Typeface.DEFAULT_BOLD);
                root.addView(tv, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }

            if (!spec.message.isEmpty()) {
                TextView mv = new TextView(this);
                mv.setText(spec.message);
                mv.setTextSize(TypedValue.COMPLEX_UNIT_SP, spec.messageSize);
                mv.setTextColor(parseColor(spec.messageColor, Color.WHITE));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                if (!spec.title.isEmpty()) lp.topMargin = dp(4);
                root.addView(mv, lp);
            }

            int overlayType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
            WindowManager.LayoutParams wlp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    overlayType,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                    PixelFormat.TRANSLUCENT);
            wlp.gravity = switch (spec.position) {
                case 1 -> Gravity.TOP | Gravity.START;
                case 2 -> Gravity.BOTTOM | Gravity.END;
                case 3 -> Gravity.BOTTOM | Gravity.START;
                case 4 -> Gravity.CENTER;
                default -> Gravity.TOP | Gravity.END;
            };
            wlp.x = dp(24);
            wlp.y = dp(24);

            AlphaAnimation fadeIn = new AlphaAnimation(0f, 1f);
            fadeIn.setDuration(200);
            root.startAnimation(fadeIn);

            wm.addView(root, wlp);
            activePopup = root;

            if (spec.duration > 0) {
                mainHandler.postDelayed(dismissRunnable, spec.duration * 1000L);
            }
        } catch (Exception ignored) {
        }
    }

    private void removePopup() {
        if (activeVideo != null) {
            try {
                activeVideo.stopPlayback();
            } catch (Exception ignored) {
            }
            activeVideo = null;
        }
        if (activeWeb != null) {
            try {
                activeWeb.destroy();
            } catch (Exception ignored) {
            }
            activeWeb = null;
        }
        if (activePopup != null) {
            try {
                WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
                if (wm != null) wm.removeView(activePopup);
            } catch (Exception ignored) {
            }
            activePopup = null;
        }
    }

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    private static int parseColor(String hex, int fallback) {
        try {
            return Color.parseColor(hex);
        } catch (Exception e) {
            return fallback;
        }
    }

    public static boolean isEnabled(Context ctx) {
        return ctx.getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_ALLOW, true);
    }

    public static void sync(Context ctx) {
        try {
            Intent intent = new Intent(ctx, NotificationOverlayService.class);
            if (isEnabled(ctx)) {
                ctx.startService(intent);
            } else {
                ctx.stopService(intent);
            }
        } catch (Exception ignored) {
        }
    }

    public static class BootReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (context != null) sync(context);
        }
    }

    private static class PopupSpec {
        int duration = 10;
        int position = 0;
        String backgroundColor = "#CC000000";
        String title = "";
        float titleSize = 20f;
        String titleColor = "#FFFFFF";
        String message = "";
        float messageSize = 16f;
        String messageColor = "#FFFFFF";
        int mediaWidth = 320;
        int mediaHeight = 240;
        Bitmap bitmap;
        String videoUri;
        String webUri;
    }
}
