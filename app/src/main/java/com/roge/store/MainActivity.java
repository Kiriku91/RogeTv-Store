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
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
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

    // Palet Warna Dominan Biru Kehijauan (Teal / Cyan Ocean)
    private final int COLOR_BG_TOP = Color.parseColor("#041C2C");
    private final int COLOR_BG_BOTTOM = Color.parseColor("#073B4C");
    private final int COLOR_CARD_BG = Color.parseColor("#660A2E40");
    private final int COLOR_CARD_FOCUS = Color.parseColor("#CC0D4B63");
    private final int COLOR_ACCENT = Color.parseColor("#00E5FF");
    private final int COLOR_ACCENT_TEAL = Color.parseColor("#00BFA5");
    private final int COLOR_BORDER = Color.parseColor("#4D00E5FF");
    private final int COLOR_TEXT_SUB = Color.parseColor("#80CBC4");

    private FrameLayout contentArea;
    private View pageBeranda, pageAplikasi, pagePengaturan;
    private LinearLayout navBeranda, navAplikasi, navPengaturan;
    private TextView tvDeviceSidebar, tvDeviceHeader, tvStatusApp;
    private EditText etSearch;
    private ListView listViewApps;

    private List<AppItem> allApps = new ArrayList<>();
    private List<AppItem> filteredApps = new ArrayList<>();
    private AppListAdapter appAdapter;

    private String customDeviceName = null;
    private int serverStoreVersion = 1;
    private String serverStoreApkUrl = "";

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
        setupMainLayout();
        // Otomatis cek katalog & cek pembaruan aplikasi Roge Store saat dibuka
        fetchCatalog(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (appAdapter != null) {
            appAdapter.notifyDataSetChanged();
        }
    }

    private String getDeviceModel() {
        if (customDeviceName != null) return customDeviceName;
        String model = Build.MODEL;
        return (model != null && !model.isEmpty()) ? model.toUpperCase() : "ANDROID TV BOX";
    }

    private String getAndroidVersion() {
        return "Android " + Build.VERSION.RELEASE;
    }

    private int dp(int val) {
        return (int) (val * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable createBox(int bgColor, int strokeColor, int strokeWidthDp, int radiusDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(bgColor);
        gd.setCornerRadius(dp(radiusDp));
        if (strokeWidthDp > 0) {
            gd.setStroke(dp(strokeWidthDp), strokeColor);
        }
        return gd;
    }

    private void applyFocusStyle(View view, int normalBg, int focusBg, int normalStroke, int focusStroke, int radiusDp) {
        view.setFocusable(true);
        view.setClickable(true);
        view.setBackground(createBox(normalBg, normalStroke, 1, radiusDp));
        view.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                v.setBackground(createBox(focusBg, focusStroke, 2, radiusDp));
            } else {
                v.setBackground(createBox(normalBg, normalStroke, 1, radiusDp));
            }
        });
    }

    private void setupMainLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        GradientDrawable mainBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{COLOR_BG_TOP, COLOR_BG_BOTTOM, Color.parseColor("#052638")}
        );
        root.setBackground(mainBg);
        root.setPadding(dp(28), dp(24), dp(28), dp(24));

        // ================= 1. SIDEBAR KIRI =================
        LinearLayout sidebar = new LinearLayout(this);
        sidebar.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams sideParams = new LinearLayout.LayoutParams(dp(230), ViewGroup.LayoutParams.MATCH_PARENT);
        sideParams.rightMargin = dp(28);
        sidebar.setLayoutParams(sideParams);

        // Brand Logo & Judul
        LinearLayout brandBox = new LinearLayout(this);
        brandBox.setOrientation(LinearLayout.HORIZONTAL);
        brandBox.setGravity(Gravity.CENTER_VERTICAL);
        brandBox.setPadding(0, 0, 0, dp(22));

        TextView logoBadge = new TextView(this);
        logoBadge.setText("R");
        logoBadge.setTextColor(Color.WHITE);
        logoBadge.setTextSize(24);
        logoBadge.setTypeface(null, Typeface.BOLD);
        logoBadge.setGravity(Gravity.CENTER);
        GradientDrawable logoBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{COLOR_ACCENT, COLOR_ACCENT_TEAL}
        );
        logoBg.setCornerRadius(dp(12));
        logoBadge.setBackground(logoBg);
        LinearLayout.LayoutParams lbParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        lbParams.rightMargin = dp(12);
        brandBox.addView(logoBadge, lbParams);

        LinearLayout brandTextLayout = new LinearLayout(this);
        brandTextLayout.setOrientation(LinearLayout.VERTICAL);
        TextView tvBrandTitle = new TextView(this);
        tvBrandTitle.setText("ROGE STORE");
        tvBrandTitle.setTextColor(Color.WHITE);
        tvBrandTitle.setTextSize(18);
        tvBrandTitle.setTypeface(null, Typeface.BOLD);
        TextView tvBrandSub = new TextView(this);
        tvBrandSub.setText("ANDROID TV APP STORE");
        tvBrandSub.setTextColor(COLOR_TEXT_SUB);
        tvBrandSub.setTextSize(9);
        brandTextLayout.addView(tvBrandTitle);
        brandTextLayout.addView(tvBrandSub);
        brandBox.addView(brandTextLayout);
        sidebar.addView(brandBox);

        // Kotak Perangkat Aktif
        LinearLayout deviceBox = new LinearLayout(this);
        deviceBox.setOrientation(LinearLayout.VERTICAL);
        deviceBox.setPadding(dp(16), dp(14), dp(16), dp(14));
        deviceBox.setBackground(createBox(COLOR_CARD_BG, COLOR_BORDER, 1, 14));
        LinearLayout.LayoutParams devParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        devParams.bottomMargin = dp(24);
        deviceBox.setLayoutParams(devParams);

        TextView tvDevLabel = new TextView(this);
        tvDevLabel.setText("PERANGKAT AKTIF");
        tvDevLabel.setTextColor(COLOR_TEXT_SUB);
        tvDevLabel.setTextSize(10);
        tvDevLabel.setTypeface(null, Typeface.BOLD);

        tvDeviceSidebar = new TextView(this);
        tvDeviceSidebar.setText(getDeviceModel() + "\n" + getAndroidVersion());
        tvDeviceSidebar.setTextColor(Color.WHITE);
        tvDeviceSidebar.setTextSize(14);
        tvDeviceSidebar.setTypeface(null, Typeface.BOLD);
        tvDeviceSidebar.setPadding(0, dp(4), 0, 0);

        deviceBox.addView(tvDevLabel);
        deviceBox.addView(tvDeviceSidebar);
        sidebar.addView(deviceBox);

        // Menu Navigasi Sidebar
        navBeranda = createNavButton("⌂   Beranda", () -> switchPage(0));
        navAplikasi = createNavButton("❖   Aplikasi", () -> switchPage(1));
        navPengaturan = createNavButton("⚙   Pengaturan", () -> switchPage(2));

        sidebar.addView(navBeranda);
        sidebar.addView(navAplikasi);
        sidebar.addView(navPengaturan);

        root.addView(sidebar);

        // ================= 2. AREA KONTEN KANAN =================
        contentArea = new FrameLayout(this);
        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        contentArea.setLayoutParams(contentParams);

        pageBeranda = buildBerandaPage();
        pageAplikasi = buildAplikasiPage();
        pagePengaturan = buildPengaturanPage();

        contentArea.addView(pageBeranda);
        contentArea.addView(pageAplikasi);
        contentArea.addView(pagePengaturan);

        root.addView(contentArea);
        setContentView(root);

        switchPage(0);
        navBeranda.requestFocus();
    }

    private LinearLayout createNavButton(String title, Runnable onClick) {
        LinearLayout btn = new LinearLayout(this);
        btn.setOrientation(LinearLayout.HORIZONTAL);
        btn.setGravity(Gravity.CENTER_VERTICAL);
        btn.setPadding(dp(18), dp(14), dp(18), dp(14));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(10);
        btn.setLayoutParams(params);

        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(15);
        tv.setTypeface(null, Typeface.BOLD);
        btn.addView(tv);

        applyFocusStyle(btn, Color.TRANSPARENT, Color.parseColor("#3300E5FF"), Color.TRANSPARENT, COLOR_ACCENT, 12);
        btn.setOnClickListener(v -> onClick.run());
        return btn;
    }

    private void switchPage(int index) {
        pageBeranda.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        pageAplikasi.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        pagePengaturan.setVisibility(index == 2 ? View.VISIBLE : View.GONE);
    }

    // ================= HALAMAN 1: BERANDA =================
    private View buildBerandaPage() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);

        container.addView(buildTopHeader(false));

        // Banner Hero
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(28), dp(28), dp(28), dp(28));
        GradientDrawable heroBg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.parseColor("#004D40"), Color.parseColor("#006064"), Color.parseColor("#01579B")}
        );
        heroBg.setCornerRadius(dp(16));
        heroBg.setStroke(dp(1), COLOR_ACCENT);
        hero.setBackground(heroBg);
        LinearLayout.LayoutParams heroParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        heroParams.topMargin = dp(14);
        heroParams.bottomMargin = dp(16);
        hero.setLayoutParams(heroParams);

        TextView heroTitle = new TextView(this);
        heroTitle.setText("ROGE STORE TV");
        heroTitle.setTextColor(COLOR_ACCENT);
        heroTitle.setTextSize(26);
        heroTitle.setTypeface(null, Typeface.BOLD);

        TextView heroSub = new TextView(this);
        heroSub.setText("Pusat aplikasi resmi untuk Android TV & Set-Top Box Anda.\nTemukan dan pasang aplikasi streaming & utilitas terbaik dengan cepat, aman, dan ringan.");
        heroSub.setTextColor(Color.WHITE);
        heroSub.setTextSize(13);
        heroSub.setPadding(0, dp(8), 0, dp(14));

        TextView heroBadges = new TextView(this);
        heroBadges.setText("✔ Koleksi Lengkap     ✔ Aman & Teruji     ✔ Unduhan Cepat     ✔ Khusus Android TV");
        heroBadges.setTextColor(COLOR_TEXT_SUB);
        heroBadges.setTextSize(12);
        heroBadges.setTypeface(null, Typeface.BOLD);

        hero.addView(heroTitle);
        hero.addView(heroSub);
        hero.addView(heroBadges);
        container.addView(hero);

        // Card 1: Perangkat Aktif
        LinearLayout cardDev = buildActionCard(
                "Perangkat aktif",
                getDeviceModel() + " • " + getAndroidVersion(),
                "Ganti perangkat",
                () -> showChangeDeviceDialog()
        );
        container.addView(cardDev);

        // Card 2: Pintasan Daftar Aplikasi
        LinearLayout cardApps = buildActionCard(
                "Daftar Aplikasi",
                "Lihat dan pasang aplikasi yang telah disiapkan untuk perangkat Anda.",
                "Lihat aplikasi",
                () -> {
                    switchPage(1);
                    listViewApps.requestFocus();
                }
        );
        container.addView(cardApps);

        scroll.addView(container);
        return scroll;
    }

    private LinearLayout buildActionCard(String title, String subtitle, String btnText, Runnable action) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(22), dp(18), dp(22), dp(18));
        card.setBackground(createBox(COLOR_CARD_BG, COLOR_BORDER, 1, 14));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(14);
        card.setLayoutParams(params);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams colParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textCol.setLayoutParams(colParams);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(17);
        tvTitle.setTypeface(null, Typeface.BOLD);

        TextView tvSub = new TextView(this);
        tvSub.setText(subtitle);
        tvSub.setTextColor(COLOR_TEXT_SUB);
        tvSub.setTextSize(13);
        tvSub.setPadding(0, dp(4), 0, 0);

        textCol.addView(tvTitle);
        textCol.addView(tvSub);
        card.addView(textCol);

        TextView btn = new TextView(this);
        btn.setText(btnText);
        btn.setTextColor(Color.BLACK);
        btn.setTextSize(13);
        btn.setTypeface(null, Typeface.BOLD);
        btn.setPadding(dp(22), dp(10), dp(22), dp(10));
        applyFocusStyle(btn, COLOR_ACCENT_TEAL, COLOR_ACCENT, COLOR_ACCENT, Color.WHITE, 10);
        btn.setOnClickListener(v -> action.run());

        card.addView(btn);
        return card;
    }

    // ================= HALAMAN 2: DAFTAR APLIKASI =================
    private View buildAplikasiPage() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);

        container.addView(buildTopHeader(true));

        TextView tvHeading = new TextView(this);
        tvHeading.setText("Daftar Aplikasi");
        tvHeading.setTextColor(Color.WHITE);
        tvHeading.setTextSize(22);
        tvHeading.setTypeface(null, Typeface.BOLD);
        tvHeading.setPadding(0, dp(10), 0, dp(2));
        container.addView(tvHeading);

        tvStatusApp = new TextView(this);
        tvStatusApp.setText("Aplikasi yang telah disiapkan untuk " + getDeviceModel());
        tvStatusApp.setTextColor(COLOR_TEXT_SUB);
        tvStatusApp.setTextSize(13);
        tvStatusApp.setPadding(0, 0, 0, dp(12));
        container.addView(tvStatusApp);

        listViewApps = new ListView(this);
        listViewApps.setDivider(null);
        listViewApps.setDividerHeight(dp(12));
        listViewApps.setSelector(android.R.color.transparent);
        listViewApps.setItemsCanFocus(true);

        appAdapter = new AppListAdapter();
        listViewApps.setAdapter(appAdapter);

        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        container.addView(listViewApps, listParams);

        return container;
    }

    // ================= HALAMAN 3: PENGATURAN =================
    private View buildPengaturanPage() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("Pengaturan");
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(24);
        tvTitle.setTypeface(null, Typeface.BOLD);

        TextView tvSub = new TextView(this);
        tvSub.setText("Kelola perangkat dan informasi Roge Store");
        tvSub.setTextColor(COLOR_TEXT_SUB);
        tvSub.setTextSize(13);
        tvSub.setPadding(0, dp(2), 0, dp(18));

        container.addView(tvTitle);
        container.addView(tvSub);

        container.addView(buildSettingItem(
                "Perangkat saat ini",
                getDeviceModel() + " • " + getAndroidVersion(),
                () -> Toast.makeText(this, "Perangkat: " + getDeviceModel(), Toast.LENGTH_SHORT).show()
        ));

        container.addView(buildSettingItem(
                "Ganti perangkat",
                "Pilih atau ubah nama profil Android TV Box",
                () -> showChangeDeviceDialog()
        ));

        container.addView(buildSettingItem(
                "Deteksi perangkat",
                "Kenali perangkat keras secara otomatis",
                () -> {
                    customDeviceName = null;
                    refreshDeviceLabels();
                    Toast.makeText(this, "Terdeteksi otomatis: " + getDeviceModel(), Toast.LENGTH_SHORT).show();
                }
        ));

        container.addView(buildSettingItem(
                "Cek Pembaruan",
                "Periksa versi terbaru Roge Store dari server",
                () -> {
                    Toast.makeText(this, "Memeriksa pembaruan...", Toast.LENGTH_SHORT).show();
                    fetchCatalog(true, true);
                }
        ));

        container.addView(buildSettingItem(
                "Tentang Roge Store",
                "Informasi aplikasi toko dan versi saat ini",
                () -> {
                    String verName = "1.0";
                    int verCode = 1;
                    try {
                        PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
                        verName = pInfo.versionName;
                        verCode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                                ? (int) pInfo.getLongVersionCode() : pInfo.versionCode;
                    } catch (Exception ignored) {}

                    new AlertDialog.Builder(this)
                            .setTitle("Tentang Roge Store")
                            .setMessage("Roge Store TV v" + verName + " (Build " + verCode + ")\n\nToko aplikasi mandiri khusus Android TV & STB.\nServer: rogeproject.my.id")
                            .setPositiveButton("Tutup", null)
                            .show();
                }
        ));

        scroll.addView(container);
        return scroll;
    }

    private View buildSettingItem(String title, String subtitle, Runnable onClick) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(20), dp(16), dp(20), dp(16));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(12);
        item.setLayoutParams(params);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvT = new TextView(this);
        tvT.setText(title);
        tvT.setTextColor(Color.WHITE);
        tvT.setTextSize(16);
        tvT.setTypeface(null, Typeface.BOLD);

        TextView tvS = new TextView(this);
        tvS.setText(subtitle);
        tvS.setTextColor(COLOR_TEXT_SUB);
        tvS.setTextSize(12);
        tvS.setPadding(0, dp(3), 0, 0);

        textCol.addView(tvT);
        textCol.addView(tvS);
        item.addView(textCol);

        TextView arrow = new TextView(this);
        arrow.setText("❯");
        arrow.setTextColor(COLOR_ACCENT);
        arrow.setTextSize(16);
        item.addView(arrow);

        applyFocusStyle(item, COLOR_CARD_BG, COLOR_CARD_FOCUS, COLOR_BORDER, COLOR_ACCENT, 14);
        item.setOnClickListener(v -> onClick.run());
        return item;
    }

    // ================= HEADER KANAN ATAS (SEARCH & REFRESH) =================
    private View buildTopHeader(boolean withSearch) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        titleCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvMain = new TextView(this);
        tvMain.setText("ROGE APP STORE");
        tvMain.setTextColor(Color.WHITE);
        tvMain.setTextSize(22);
        tvMain.setTypeface(null, Typeface.BOLD);

        tvDeviceHeader = new TextView(this);
        tvDeviceHeader.setText("Perangkat: " + getDeviceModel());
        tvDeviceHeader.setTextColor(COLOR_TEXT_SUB);
        tvDeviceHeader.setTextSize(12);

        titleCol.addView(tvMain);
        titleCol.addView(tvDeviceHeader);
        header.addView(titleCol);

        if (withSearch) {
            etSearch = new EditText(this);
            etSearch.setHint("🔍  Cari aplikasi...");
            etSearch.setHintTextColor(COLOR_TEXT_SUB);
            etSearch.setTextColor(Color.WHITE);
            etSearch.setTextSize(14);
            etSearch.setSingleLine(true);
            etSearch.setPadding(dp(16), dp(10), dp(16), dp(10));
            LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(dp(260), ViewGroup.LayoutParams.WRAP_CONTENT);
            searchParams.rightMargin = dp(12);
            etSearch.setLayoutParams(searchParams);
            applyFocusStyle(etSearch, COLOR_CARD_BG, COLOR_CARD_FOCUS, COLOR_BORDER, COLOR_ACCENT, 12);

            etSearch.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    filterApps(s.toString());
                }
                @Override public void afterTextChanged(Editable s) {}
            });
            header.addView(etSearch);
        }

        TextView btnRefresh = new TextView(this);
        btnRefresh.setText("↻");
        btnRefresh.setTextColor(Color.WHITE);
        btnRefresh.setTextSize(20);
        btnRefresh.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams refParams = new LinearLayout.LayoutParams(dp(46), dp(46));
        btnRefresh.setLayoutParams(refParams);
        applyFocusStyle(btnRefresh, COLOR_CARD_BG, COLOR_CARD_FOCUS, COLOR_BORDER, COLOR_ACCENT, 12);
        btnRefresh.setOnClickListener(v -> {
            Toast.makeText(this, "Memperbarui katalog...", Toast.LENGTH_SHORT).show();
            fetchCatalog(true);
        });
        header.addView(btnRefresh);

        return header;
    }

    private void refreshDeviceLabels() {
        if (tvDeviceSidebar != null) tvDeviceSidebar.setText(getDeviceModel() + "\n" + getAndroidVersion());
        if (tvDeviceHeader != null) tvDeviceHeader.setText("Perangkat: " + getDeviceModel());
        if (tvStatusApp != null) tvStatusApp.setText("Aplikasi yang telah disiapkan untuk " + getDeviceModel());
    }

    private void showChangeDeviceDialog() {
        String[] presets = {"B860H V2", "HG680P", "Mi TV Stick", "Mi Box S", "Chromecast Google TV", "Deteksi Otomatis"};
        new AlertDialog.Builder(this)
                .setTitle("Pilih Profil Perangkat")
                .setItems(presets, (dialog, which) -> {
                    if (which == presets.length - 1) {
                        customDeviceName = null;
                    } else {
                        customDeviceName = presets[which];
                    }
                    refreshDeviceLabels();
                })
                .show();
    }

    // ================= KONEKSI API & SELF-UPDATE =================
    private void fetchCatalog(boolean checkStoreUpdate) {
        fetchCatalog(checkStoreUpdate, false);
    }

    private void fetchCatalog(boolean checkStoreUpdate, boolean isManualCheck) {
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
                serverStoreVersion = json.optInt("store_version_code", 1);
                serverStoreApkUrl = json.optString("store_apk_url", "");

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
                    item.description = obj.optString("description", "Aplikasi Android TV.");
                    temp.add(item);
                }

                mainHandler.post(() -> {
                    allApps.clear();
                    allApps.addAll(temp);
                    filterApps(etSearch != null ? etSearch.getText().toString() : "");
                    if (checkStoreUpdate) {
                        checkStoreSelfUpdate(isManualCheck);
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> Toast.makeText(this, "Gagal terhubung ke server: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        });
    }

    private void filterApps(String query) {
        filteredApps.clear();
        String q = query.trim().toLowerCase();
        for (AppItem item : allApps) {
            if (q.isEmpty() || item.name.toLowerCase().contains(q) || item.description.toLowerCase().contains(q)) {
                filteredApps.add(item);
            }
        }
        if (appAdapter != null) appAdapter.notifyDataSetChanged();
    }

    private void checkStoreSelfUpdate(boolean showLatestToast) {
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            int currentCode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? (int) pInfo.getLongVersionCode() : pInfo.versionCode;
            if (serverStoreVersion > currentCode && !serverStoreApkUrl.isEmpty()) {
                AppItem storeItem = new AppItem();
                storeItem.name = "Roge Store (Update v1." + serverStoreVersion + ")";
                storeItem.apkUrl = serverStoreApkUrl;
                new AlertDialog.Builder(this)
                        .setTitle("Pembaruan Roge Store Tersedia!")
                        .setMessage("Versi terbaru (Build " + serverStoreVersion + ") telah rilis.\nUnduh dan perbarui aplikasi toko sekarang?")
                        .setCancelable(false)
                        .setPositiveButton("Update Sekarang", (d, w) -> startDownloadAndInstall(storeItem))
                        .setNegativeButton("Nanti", null)
                        .show();
            } else if (showLatestToast) {
                Toast.makeText(this, "Roge Store sudah menggunakan versi terbaru (Build " + currentCode + ")!", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            if (showLatestToast) {
                Toast.makeText(this, "Gagal memeriksa versi.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private int getAppStatus(String packageName, int serverVersionCode) {
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(packageName, 0);
            int installedCode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? (int) pInfo.getLongVersionCode() : pInfo.versionCode;
            return (serverVersionCode > installedCode) ? 2 : 1;
        } catch (PackageManager.NameNotFoundException e) {
            return 0;
        }
    }

    // ================= ADAPTER DAFTAR APLIKASI =================
    class AppListAdapter extends BaseAdapter {
        @Override public int getCount() { return filteredApps.size(); }
        @Override public Object getItem(int position) { return filteredApps.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            AppItem item = filteredApps.get(position);

            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(18), dp(16), dp(20), dp(16));
            applyFocusStyle(row, COLOR_CARD_BG, COLOR_CARD_FOCUS, COLOR_BORDER, COLOR_ACCENT, 14);

            ImageView icon = new ImageView(MainActivity.this);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(68), dp(68));
            iconParams.rightMargin = dp(18);
            icon.setLayoutParams(iconParams);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            icon.setImageResource(android.R.drawable.sym_def_app_icon);
            loadIcon(item.iconUrl, icon);
            row.addView(icon);

            LinearLayout textCol = new LinearLayout(MainActivity.this);
            textCol.setOrientation(LinearLayout.VERTICAL);
            textCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView tvName = new TextView(MainActivity.this);
            tvName.setText(item.name);
            tvName.setTextColor(Color.WHITE);
            tvName.setTextSize(17);
            tvName.setTypeface(null, Typeface.BOLD);

            TextView tvDesc = new TextView(MainActivity.this);
            tvDesc.setText(item.description);
            tvDesc.setTextColor(Color.parseColor("#E0F2F1"));
            tvDesc.setTextSize(13);
            tvDesc.setMaxLines(2);
            tvDesc.setEllipsize(TextUtils.TruncateAt.END);
            tvDesc.setPadding(0, dp(3), 0, dp(4));

            TextView tvMeta = new TextView(MainActivity.this);
            tvMeta.setText("Versi " + item.versionName + "  •  Ukuran " + item.size);
            tvMeta.setTextColor(COLOR_TEXT_SUB);
            tvMeta.setTextSize(11);
            tvMeta.setTypeface(null, Typeface.ITALIC);

            textCol.addView(tvName);
            textCol.addView(tvDesc);
            textCol.addView(tvMeta);
            row.addView(textCol);

            int status = getAppStatus(item.packageName, item.versionCode);
            TextView actionBtn = new TextView(MainActivity.this);
            actionBtn.setTextSize(14);
            actionBtn.setTypeface(null, Typeface.BOLD);
            actionBtn.setGravity(Gravity.CENTER);
            actionBtn.setPadding(dp(24), dp(10), dp(24), dp(10));
            LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(dp(120), ViewGroup.LayoutParams.WRAP_CONTENT);
            btnParams.leftMargin = dp(16);
            actionBtn.setLayoutParams(btnParams);

            if (status == 1) {
                actionBtn.setText("Buka");
                actionBtn.setTextColor(Color.WHITE);
                actionBtn.setBackground(createBox(Color.parseColor("#0288D1"), COLOR_ACCENT, 1, 10));
            } else if (status == 2) {
                actionBtn.setText("Update");
                actionBtn.setTextColor(Color.BLACK);
                actionBtn.setBackground(createBox(Color.parseColor("#FFC107"), Color.WHITE, 1, 10));
            } else {
                actionBtn.setText("Install");
                actionBtn.setTextColor(Color.BLACK);
                actionBtn.setBackground(createBox(COLOR_ACCENT, Color.WHITE, 1, 10));
            }
            row.addView(actionBtn);

            row.setOnClickListener(v -> showAppDetailDialog(item));
            return row;
        }
    }

    private void showAppDetailDialog(AppItem item) {
        int status = getAppStatus(item.packageName, item.versionCode);
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle(item.name + " (v" + item.versionName + ")");
        b.setMessage("Ukuran: " + item.size + "\nPackage: " + item.packageName + "\n\n" + item.description);

        if (status == 0) {
            b.setPositiveButton("Install Sekarang", (d, w) -> startDownloadAndInstall(item));
        } else if (status == 2) {
            b.setPositiveButton("Update Aplikasi", (d, w) -> startDownloadAndInstall(item));
            b.setNeutralButton("Buka", (d, w) -> openApp(item.packageName));
        } else {
            b.setPositiveButton("Buka Aplikasi", (d, w) -> openApp(item.packageName));
            b.setNeutralButton("Install Ulang", (d, w) -> startDownloadAndInstall(item));
        }
        b.setNegativeButton("Batal", null);
        b.show();
    }

    private void openApp(String packageName) {
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(packageName);
        if (launchIntent != null) {
            startActivity(launchIntent);
        } else {
            Toast.makeText(this, "Aplikasi tidak dapat dibuka langsung.", Toast.LENGTH_SHORT).show();
        }
    }

    private void startDownloadAndInstall(AppItem item) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!getPackageManager().canRequestPackageInstalls()) {
                Toast.makeText(this, "Berikan izin install aplikasi untuk Roge Store!", Toast.LENGTH_LONG).show();
                startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())));
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
                    Toast.makeText(MainActivity.this, "Gagal mengunduh: " + e.getMessage(), Toast.LENGTH_LONG).show();
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
