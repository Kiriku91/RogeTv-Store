package com.roge.store;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final String API_URL = "https://rogeproject.my.id/store/api.php";
    private GridView gridView;
    private TextView statusText;
    private List<AppItem> appList = new ArrayList<>();
    private AppAdapter adapter;
    private ExecutorService executor = Executors.newFixedThreadPool(4);
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private Map<String, Bitmap> iconCache = new HashMap<>();

    static class AppItem {
        String name, packageName, versionName, size, iconUrl, apkUrl, description;
        int versionCode;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupUI();
        fetchCatalog();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (adapter != null) {
            adapter.notifyDataSetChanged(); // Refresh status tombol setelah selesai install APK
        }
    }

    private void setupUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#121212"));
        root.setPadding(48, 36, 48, 36);

        // Header Bar
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("ROGE STORE TV");
        title.setTextColor(Color.parseColor("#00E676"));
        title.setTextSize(26);
        title.getPaint().setFakeBoldText(true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        header.addView(title, titleParams);

        Button refreshBtn = new Button(this);
        refreshBtn.setText("Refresh Katalog");
        refreshBtn.setTextColor(Color.WHITE);
        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(Color.parseColor("#263238"));
        btnBg.setCornerRadius(12);
        refreshBtn.setBackground(btnBg);
        refreshBtn.setOnClickListener(v -> fetchCatalog());
        header.addView(refreshBtn);

        root.addView(header);

        statusText = new TextView(this);
        statusText.setText("Memuat katalog aplikasi...");
        statusText.setTextColor(Color.parseColor("#B0BEC5"));
        statusText.setTextSize(16);
        statusText.setPadding(0, 20, 0, 20);
        root.addView(statusText);

        // Grid Katalog ala Aptoide TV
        gridView = new GridView(this);
        gridView.setNumColumns(4);
        gridView.setHorizontalSpacing(28);
        gridView.setVerticalSpacing(28);
        gridView.setFocusable(true);

        adapter = new AppAdapter();
        gridView.setAdapter(adapter);

        gridView.setOnItemClickListener((AdapterView<?> parent, View view, int position, long id) -> {
            showAppDetailDialog(appList.get(position));
        });

        LinearLayout.LayoutParams gridParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        root.addView(gridView, gridParams);

        setContentView(root);
    }

    private void fetchCatalog() {
        statusText.setVisibility(View.VISIBLE);
        statusText.setText("Mengambil data dari server Roge Store...");
        executor.execute(() -> {
            try {
                URL url = new URL(API_URL + "?t=" + System.currentTimeMillis());
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                JSONObject json = new JSONObject(sb.toString());
                JSONArray apps = json.getJSONArray("apps");

                List<AppItem> temp = new ArrayList<>();
                for (int i = 0; i < apps.length(); i++) {
                    JSONObject obj = apps.getJSONObject(i);
                    AppItem item = new AppItem();
                    item.name = obj.optString("name", "Unknown");
                    item.packageName = obj.optString("package_name", "");
                    item.versionCode = obj.optInt("version_code", 1);
                    item.versionName = obj.optString("version_name", "1.0");
                    item.size = obj.optString("size", "- MB");
                    item.iconUrl = obj.optString("icon_url", "");
                    item.apkUrl = obj.optString("apk_url", "");
                    item.description = obj.optString("description", "Tidak ada deskripsi.");
                    temp.add(item);
                }

                mainHandler.post(() -> {
                    appList.clear();
                    appList.addAll(temp);
                    adapter.notifyDataSetChanged();
                    if (appList.isEmpty()) {
                        statusText.setText("Belum ada aplikasi di dalam katalog.");
                    } else {
                        statusText.setVisibility(View.GONE);
                        gridView.requestFocus();
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> statusText.setText("Gagal memuat katalog: " + e.getMessage()));
            }
        });
    }

    // Cek apakah aplikasi sudah terinstal atau butuh update
    private int getAppStatus(String packageName, int serverVersionCode) {
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(packageName, 0);
            int installedCode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? (int) pInfo.getLongVersionCode() : pInfo.versionCode;
            if (serverVersionCode > installedCode) {
                return 2; // UPDATE TERSEDIA
            }
            return 1; // SUDAH TERINSTAL
        } catch (PackageManager.NameNotFoundException e) {
            return 0; // BELUM TERINSTAL
        }
    }

    private void showAppDetailDialog(AppItem item) {
        int status = getAppStatus(item.packageName, item.versionCode);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(item.name + " (v" + item.versionName + ")");
        builder.setMessage("Ukuran: " + item.size + "\nPackage: " + item.packageName + "\n\n" + item.description);

        if (status == 0) {
            builder.setPositiveButton("INSTALL SEKARANG", (d, w) -> startDownloadAndInstall(item));
        } else if (status == 2) {
            builder.setPositiveButton("UPDATE (v" + item.versionName + ")", (d, w) -> startDownloadAndInstall(item));
            builder.setNeutralButton("BUKA APLIKASI", (d, w) -> openApp(item.packageName));
        } else {
            builder.setPositiveButton("BUKA APLIKASI", (d, w) -> openApp(item.packageName));
            builder.setNeutralButton("INSTALL ULANG", (d, w) -> startDownloadAndInstall(item));
        }

        builder.setNegativeButton("TUTUP", null);
        builder.show();
    }

    private void openApp(String packageName) {
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(packageName);
        if (launchIntent != null) {
            startActivity(launchIntent);
        } else {
            Toast.makeText(this, "Aplikasi tidak bisa dibuka langsung.", Toast.LENGTH_SHORT).show();
        }
    }

    private void startDownloadAndInstall(AppItem item) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!getPackageManager().canRequestPackageInstalls()) {
                Toast.makeText(this, "Izinkan Roge Store menginstal aplikasi terlebih dahulu!", Toast.LENGTH_LONG).show();
                Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName()));
                startActivity(intent);
                return;
            }
        }

        ProgressDialog pd = new ProgressDialog(this);
        pd.setTitle("Mengunduh " + item.name);
        pd.setMessage("Mohon tunggu...");
        pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        pd.setCancelable(false);
        pd.setMax(100);
        pd.show();

        executor.execute(() -> {
            try {
                URL url = new URL(item.apkUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.connect();

                int fileLength = conn.getContentLength();
                File cacheDir = getExternalCacheDir() != null ? getExternalCacheDir() : getCacheDir();
                File apkFile = new File(cacheDir, "download_temp.apk");
                if (apkFile.exists()) apkFile.delete();

                InputStream input = conn.getInputStream();
                FileOutputStream output = new FileOutputStream(apkFile);

                byte[] data = new byte[8192];
                long total = 0;
                int count;
                while ((count = input.read(data)) != -1) {
                    total += count;
                    if (fileLength > 0) {
                        int progress = (int) (total * 100 / fileLength);
                        mainHandler.post(() -> pd.setProgress(progress));
                    }
                    output.write(data, 0, count);
                }

                output.flush();
                output.close();
                input.close();

                mainHandler.post(() -> {
                    pd.dismiss();
                    installApk(apkFile);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    pd.dismiss();
                    Toast.makeText(MainActivity.this, "Gagal mendownload: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void installApk(File apkFile) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri apkUri;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                apkUri = Uri.parse("content://" + getPackageName() + ".provider/" + apkFile.getName());
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else {
                apkUri = Uri.fromFile(apkFile);
            }
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Gagal memicu instalasi: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    class AppAdapter extends BaseAdapter {
        @Override
        public int getCount() { return appList.size(); }

        @Override
        public Object getItem(int position) { return appList.get(position); }

        @Override
        public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            AppItem item = appList.get(position);

            LinearLayout card = new LinearLayout(MainActivity.this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER_HORIZONTAL);
            card.setPadding(24, 28, 24, 28);

            GradientDrawable cardBg = new GradientDrawable();
            cardBg.setColor(Color.parseColor("#1E1E1E"));
            cardBg.setCornerRadius(20);
            cardBg.setStroke(3, Color.parseColor("#333333"));
            card.setBackground(cardBg);

            // Efek Sorot Fokus Remote TV
            gridView.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                    for (int i = 0; i < p.getChildCount(); i++) {
                        View child = p.getChildAt(i);
                        GradientDrawable bg = (GradientDrawable) child.getBackground();
                        bg.setStroke(3, Color.parseColor("#333333"));
                        bg.setColor(Color.parseColor("#1E1E1E"));
                        child.setScaleX(1.0f);
                        child.setScaleY(1.0f);
                    }
                    if (v != null) {
                        GradientDrawable activeBg = (GradientDrawable) v.getBackground();
                        activeBg.setStroke(5, Color.parseColor("#00E676"));
                        activeBg.setColor(Color.parseColor("#263238"));
                        v.setScaleX(1.05f);
                        v.setScaleY(1.05f);
                    }
                }
                @Override
                public void onNothingSelected(AdapterView<?> p) {}
            });

            ImageView iconView = new ImageView(MainActivity.this);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(140, 140);
            iconView.setLayoutParams(iconParams);
            iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iconView.setImageResource(android.R.drawable.sym_def_app_icon);
            loadIcon(item.iconUrl, iconView);
            card.addView(iconView);

            TextView nameView = new TextView(MainActivity.this);
            nameView.setText(item.name);
            nameView.setTextColor(Color.WHITE);
            nameView.setTextSize(16);
            nameView.getPaint().setFakeBoldText(true);
            nameView.setSingleLine(true);
            nameView.setEllipsize(TextUtils.TruncateAt.END);
            nameView.setPadding(0, 16, 0, 4);
            card.addView(nameView);

            TextView sizeView = new TextView(MainActivity.this);
            sizeView.setText("v" + item.versionName + " • " + item.size);
            sizeView.setTextColor(Color.parseColor("#9E9E9E"));
            sizeView.setTextSize(12);
            card.addView(sizeView);

            // Badge Status Aplikasi (INSTALL / OPEN / UPDATE)
            int status = getAppStatus(item.packageName, item.versionCode);
            TextView badge = new TextView(MainActivity.this);
            badge.setTextSize(12);
            badge.getPaint().setFakeBoldText(true);
            badge.setPadding(24, 8, 24, 8);
            LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            badgeParams.topMargin = 14;
            badge.setLayoutParams(badgeParams);

            GradientDrawable badgeBg = new GradientDrawable();
            badgeBg.setCornerRadius(30);

            if (status == 1) {
                badge.setText("TERINSTAL (BUKA)");
                badge.setTextColor(Color.WHITE);
                badgeBg.setColor(Color.parseColor("#0288D1"));
            } else if (status == 2) {
                badge.setText("UPDATE TERSEDIA");
                badge.setTextColor(Color.BLACK);
                badgeBg.setColor(Color.parseColor("#FFC107"));
            } else {
                badge.setText("INSTALL");
                badge.setTextColor(Color.BLACK);
                badgeBg.setColor(Color.parseColor("#00E676"));
            }
            badge.setBackground(badgeBg);
            card.addView(badge);

            return card;
        }
    }

    private void loadIcon(String url, ImageView imageView) {
        if (url.isEmpty()) return;
        if (iconCache.containsKey(url)) {
            imageView.setImageBitmap(iconCache.get(url));
            return;
        }
        executor.execute(() -> {
            try {
                InputStream in = new URL(url).openStream();
                Bitmap bmp = BitmapFactory.decodeStream(in);
                in.close();
                if (bmp != null) {
                    iconCache.put(url, bmp);
                    mainHandler.post(() -> imageView.setImageBitmap(bmp));
                }
            } catch (Exception ignored) {}
        });
    }
}
