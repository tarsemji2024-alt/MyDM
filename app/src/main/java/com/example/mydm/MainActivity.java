package com.example.mydm;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.Comparator;

public class MainActivity extends Activity {

    // ---- रंग ----
    static final int BG = Color.parseColor("#0F172A");
    static final int CARD = Color.parseColor("#1E293B");
    static final int ACCENT = Color.parseColor("#14B8A6");
    static final int AMBER = Color.parseColor("#F59E0B");
    static final int RED = Color.parseColor("#EF4444");
    static final int TEXT = Color.parseColor("#F1F5F9");
    static final int MUTED = Color.parseColor("#94A3B8");

    EditText input;
    Button btnDownload, btnPause;
    ProgressBar progress;
    TextView status;
    LinearLayout listBox;

    volatile boolean paused = false;
    volatile boolean running = false;
    File dir;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().setStatusBarColor(BG);
            getWindow().setNavigationBarColor(BG);
        }
        if (getActionBar() != null) getActionBar().hide();
        setContentView(buildUi());
        refreshList();
    }

    // ================= UI =================
    View buildUi() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        sv.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(36), dp(18), dp(24));
        sv.addView(root, new ViewGroup.LayoutParams(-1, -2));

        // हेडर
        TextView title = tv("MyDM", 32, TEXT, true);
        root.addView(title);
        TextView sub = tv("तेज़ और आसान डाउनलोड मैनेजर", 14, MUTED, false);
        sub.setPadding(0, 0, 0, dp(18));
        root.addView(sub);

        // इनपुट कार्ड
        LinearLayout card = card();
        root.addView(card);

        input = new EditText(this);
        input.setHint("डाउनलोड लिंक यहाँ पेस्ट करें");
        input.setHintTextColor(MUTED);
        input.setTextColor(TEXT);
        input.setTextSize(15);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        input.setPadding(dp(14), dp(12), dp(14), dp(12));
        input.setBackground(round(Color.parseColor("#0B1220"), 12, ACCENT, 1));
        card.addView(input, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout row1 = new LinearLayout(this);
        row1.setPadding(0, dp(10), 0, 0);
        card.addView(row1);
        row1.addView(smallBtn("पेस्ट", Color.parseColor("#334155"), v -> pasteLink()), weight());
        row1.addView(space());
        row1.addView(smallBtn("साफ़ करें", Color.parseColor("#334155"), v -> input.setText("")), weight());

        // मुख्य बटन
        LinearLayout row2 = new LinearLayout(this);
        row2.setPadding(0, dp(14), 0, 0);
        card.addView(row2);
        btnDownload = bigBtn("⬇  डाउनलोड / रिज्यूम", ACCENT, v -> startDownload());
        btnPause = bigBtn("⏸  रोकें", AMBER, v -> paused = true);
        btnPause.setEnabled(false);
        btnPause.setAlpha(0.4f);
        row2.addView(btnDownload, weight());
        row2.addView(space());
        row2.addView(btnPause, weight());

        // प्रोग्रेस
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.getProgressDrawable().setColorFilter(ACCENT, android.graphics.PorterDuff.Mode.SRC_IN);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, dp(8));
        pp.topMargin = dp(16);
        card.addView(progress, pp);

        status = tv("तैयार", 13, MUTED, false);
        status.setPadding(0, dp(8), 0, 0);
        card.addView(status);

        // डाउनलोड हुई फ़ाइलें
        TextView h = tv("डाउनलोड की गई फ़ाइलें", 18, TEXT, true);
        h.setPadding(0, dp(24), 0, dp(10));
        root.addView(h);

        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(listBox, new LinearLayout.LayoutParams(-1, -2));
        return sv;
    }

    void refreshList() {
        listBox.removeAllViews();
        File[] files = dir.listFiles((d, n) -> !n.endsWith(".part"));
        if (files == null || files.length == 0) {
            TextView e = tv("अभी कोई फ़ाइल नहीं", 14, MUTED, false);
            e.setPadding(dp(4), dp(8), 0, 0);
            listBox.addView(e);
            return;
        }
        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
        for (File f : files) listBox.addView(fileRow(f));
    }

    View fileRow(final File f) {
        LinearLayout c = card();
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(10);
        c.setLayoutParams(lp);

        TextView name = tv(f.getName(), 15, TEXT, true);
        c.addView(name);
        TextView size = tv(fmt(f.length()), 12, MUTED, false);
        size.setPadding(0, dp(2), 0, dp(10));
        c.addView(size);

        LinearLayout r = new LinearLayout(this);
        c.addView(r);
        r.addView(smallBtn("💾 सेव करें", ACCENT, v -> saveToDownloads(f)), weight());
        r.addView(space());
        r.addView(smallBtn("🗑 हटाएँ", RED, v -> {
            f.delete();
            refreshList();
        }), weight());
        return c;
    }

    // ================= डाउनलोड =================
    void pasteLink() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null && cm.hasPrimaryClip()) {
            ClipData.Item it = cm.getPrimaryClip().getItemAt(0);
            if (it.getText() != null) input.setText(it.getText().toString().trim());
        }
    }

    String fileNameFrom(String url) {
        try {
            String p = url.split("\\?")[0];
            String n = p.substring(p.lastIndexOf('/') + 1);
            n = URLDecoder.decode(n, "UTF-8").replaceAll("[\\\\/:*?\"<>|]", "_").trim();
            return n.isEmpty() ? "download_" + System.currentTimeMillis() : n;
        } catch (Exception e) {
            return "download_" + System.currentTimeMillis();
        }
    }

    void startDownload() {
        final String url = input.getText().toString().trim();
        if (!url.startsWith("http")) {
            toast("सही लिंक डालें (http/https)");
            return;
        }
        if (running) return;
        final String name = fileNameFrom(url);
        paused = false;
        running = true;
        setButtons(true);

        new Thread(() -> {
            File part = new File(dir, name + ".part");
            File fin = new File(dir, name);
            try {
                long have = part.exists() ? part.length() : 0;
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(20000);
                if (have > 0) c.setRequestProperty("Range", "bytes=" + have + "-");
                c.connect();
                int code = c.getResponseCode();
                if (code == 416) { // पहले ही पूरा
                    part.renameTo(fin);
                    done("पूरा हुआ ✔");
                    return;
                }
                if (code != 200 && code != 206) {
                    done("त्रुटि: सर्वर कोड " + code);
                    return;
                }
                boolean append = code == 206;
                if (!append) have = 0;
                long len = c.getContentLengthLong();
                long total = len > 0 ? len + have : -1;

                InputStream in = c.getInputStream();
                FileOutputStream out = new FileOutputStream(part, append);
                byte[] buf = new byte[16384];
                long got = have;
                int n;
                while (!paused && (n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                    got += n;
                    final long g = got, t = total;
                    runOnUiThread(() -> {
                        if (t > 0) progress.setProgress((int) (g * 100 / t));
                        status.setText("डाउनलोड हो रहा है… " + fmt(g) + (t > 0 ? " / " + fmt(t) : ""));
                    });
                }
                out.close();
                in.close();
                if (paused) {
                    done("रुका हुआ ⏸ (" + fmt(got) + ") — रिज्यूम दबाएँ");
                } else {
                    part.renameTo(fin);
                    runOnUiThread(() -> progress.setProgress(100));
                    done("पूरा हुआ ✔  नीचे लिस्ट से सेव करें");
                }
            } catch (Exception e) {
                done("त्रुटि: " + e.getMessage() + " — रिज्यूम दबाएँ");
            }
        }).start();
    }

    void done(final String msg) {
        running = false;
        runOnUiThread(() -> {
            status.setText(msg);
            setButtons(false);
            refreshList();
        });
    }

    void setButtons(boolean downloading) {
        btnDownload.setEnabled(!downloading);
        btnDownload.setAlpha(downloading ? 0.4f : 1f);
        btnPause.setEnabled(downloading);
        btnPause.setAlpha(downloading ? 1f : 0.4f);
    }

    // ================= सेव =================
    void saveToDownloads(File f) {
        try {
            String mime = URLConnection.guessContentTypeFromName(f.getName());
            if (mime == null) mime = "application/octet-stream";
            OutputStream os;
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues v = new ContentValues();
                v.put(MediaStore.Downloads.DISPLAY_NAME, f.getName());
                v.put(MediaStore.Downloads.MIME_TYPE, mime);
                v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/MyDM");
                Uri u = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                os = getContentResolver().openOutputStream(u);
            } else {
                File d = new File(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS), "MyDM");
                d.mkdirs();
                os = new FileOutputStream(new File(d, f.getName()));
            }
            InputStream in = new java.io.FileInputStream(f);
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) != -1) os.write(buf, 0, n);
            in.close();
            os.close();
            toast("सेव हो गई: Downloads/MyDM/" + f.getName());
        } catch (Exception e) {
            toast("सेव नहीं हुई: " + e.getMessage());
        }
    }

    // ================= हेल्पर =================
    int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    GradientDrawable round(int color, int radiusDp, int strokeColor, int strokeDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) g.setStroke(dp(strokeDp), strokeColor);
        return g;
    }

    LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(16), dp(16), dp(16));
        c.setBackground(round(CARD, 18, 0, 0));
        return c;
    }

    TextView tv(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    Button bigBtn(String s, int color, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTextColor(Color.WHITE);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setBackground(round(color, 14, 0, 0));
        b.setOnClickListener(l);
        b.setMinHeight(dp(52));
        return b;
    }

    Button smallBtn(String s, int color, View.OnClickListener l) {
        Button b = bigBtn(s, color, l);
        b.setTextSize(13);
        b.setMinHeight(dp(42));
        return b;
    }

    LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, -2, 1f);
    }

    View space() {
        return new View(this) {{
            setLayoutParams(new LinearLayout.LayoutParams(dp(10), 1));
        }};
    }

    String fmt(long b) {
        if (b < 1024) return b + " B";
        if (b < 1024 * 1024) return String.format("%.1f KB", b / 1024.0);
        if (b < 1024L * 1024 * 1024) return String.format("%.1f MB", b / 1048576.0);
        return String.format("%.2f GB", b / 1073741824.0);
    }

    void toast(String s) {
        runOnUiThread(() -> Toast.makeText(this, s, Toast.LENGTH_LONG).show());
    }
}
