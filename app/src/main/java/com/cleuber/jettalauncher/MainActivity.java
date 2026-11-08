package com.cleuber.jettalauncher;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {

    static final String[] TILE_IDS = {"carplay", "aauto", "eq", "settings", "nav", "music"};
    static final String[] TILE_NAMES = {"CarPlay", "Android Auto", "Equalizador", "Configurações", "Navegação", "Música"};
    static final int[] TILE_ICONS = {R.drawable.ic_carplay, R.drawable.ic_car, R.drawable.ic_eq,
            R.drawable.ic_settings, R.drawable.ic_nav, R.drawable.ic_music};
    /** Palavras usadas para achar automaticamente o app de cada bloco na primeira vez. */
    static final String[][] KEYWORDS = {
            {"carplay", "zlink", "carlink", "autokit", "tlink", "phonelink", "easyconnect"},
            {"android auto", "androidauto", "zlink", "carlink", "autokit", "tlink"},
            {"equaliz", "eq", "dsp", "efeito sonoro", "áudio", "audio", "som", "sound"},
            {"configura", "definições", "ajustes", "settings"},
            {"waze", "google maps", "maps", "navega", "mapa", "gps", "sygic", "igo"},
            {"spotify", "música", "musica", "music", "deezer", "player"}
    };

    static class AppItem {
        String pkg, cls, label, origLabel;
        Drawable icon;
        boolean hidden;

        String key() {
            return pkg + "/" + cls;
        }
    }

    static class Tile {
        int index;
        LinearLayout view;
        ChamferDrawable bg;
        GradientDrawable boxBg;
        ImageView icon;
        TextView label, sub;
    }

    private SharedPreferences prefs;
    private PackageManager pm;
    private final List<AppItem> apps = new ArrayList<>();
    private final Tile[] tiles = new Tile[TILE_IDS.length];

    private FrameLayout root;
    private CarbonView homeBg, drawerBg;
    private SegmentsView slashes, drawerSlashes, revBar;
    private DashLineView dash;
    private ChamferDrawable appsBtnBg, hiddenBtnBg;
    private TextView brand, hiddenBtnText;
    private FrameLayout drawer;
    private DrawerAdapter drawerAdapter;
    private boolean showHidden;
    private Dialog openDialog;
    private int revLit = 13;

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            reloadApps();
        }
    };

    // ------------------------------------------------------------------ ciclo de vida

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window w = getWindow();
        w.setStatusBarColor(Color.BLACK);
        w.setNavigationBarColor(Color.BLACK);
        Ui.init(this);
        prefs = getSharedPreferences("jetta", MODE_PRIVATE);
        pm = getPackageManager();
        loadApps();

        root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);
        root.addView(buildHome(), new FrameLayout.LayoutParams(-1, -1));
        drawer = buildDrawer();
        drawer.setVisibility(View.GONE);
        root.addView(drawer, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        applyTheme();
        startupSweep();

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addAction(Intent.ACTION_PACKAGE_REPLACED);
        f.addDataScheme("package");
        registerReceiver(packageReceiver, f);

        if (!prefs.getBoolean("welcome_done", false)) {
            prefs.edit().putBoolean("welcome_done", true).apply();
            root.postDelayed(new Runnable() {
                @Override
                public void run() {
                    showWelcome();
                }
            }, 600);
        }
    }

    @Override
    protected void onDestroy() {
        try {
            unregisterReceiver(packageReceiver);
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Botão Home pressionado com a launcher já aberta: volta para a tela inicial.
        dismissDialog();
        closeDrawer();
    }

    @Override
    public void onBackPressed() {
        if (drawer.getVisibility() == View.VISIBLE) {
            closeDrawer();
        }
        // Na tela inicial o "voltar" não faz nada (é a tela inicial).
    }

    // ------------------------------------------------------------------ apps instalados

    private void loadApps() {
        Intent i = new Intent(Intent.ACTION_MAIN, null);
        i.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> list = pm.queryIntentActivities(i, 0);
        Set<String> hidden = prefs.getStringSet("hidden", new HashSet<String>());
        apps.clear();
        for (ResolveInfo ri : list) {
            ActivityInfo ai = ri.activityInfo;
            if (ai == null || getPackageName().equals(ai.packageName)) continue;
            AppItem a = new AppItem();
            a.pkg = ai.packageName;
            a.cls = ai.name;
            CharSequence lbl = ri.loadLabel(pm);
            a.origLabel = lbl == null ? a.pkg : lbl.toString();
            String custom = prefs.getString("name_" + a.key(), null);
            a.label = TextUtils.isEmpty(custom) ? a.origLabel : custom;
            a.icon = ri.loadIcon(pm);
            a.hidden = hidden.contains(a.key());
            apps.add(a);
        }
        Collections.sort(apps, new Comparator<AppItem>() {
            @Override
            public int compare(AppItem x, AppItem y) {
                return x.label.compareToIgnoreCase(y.label);
            }
        });
    }

    private void reloadApps() {
        loadApps();
        refreshTiles();
        if (drawerAdapter != null) drawerAdapter.rebuild();
    }

    private AppItem findApp(String key) {
        if (key == null) return null;
        for (AppItem a : apps) if (a.key().equals(key)) return a;
        return null;
    }

    private AppItem detectApp(int idx) {
        for (String kw : KEYWORDS[idx]) {
            for (AppItem a : apps) {
                if (matches(a.origLabel.toLowerCase(Locale.ROOT), a.pkg.toLowerCase(Locale.ROOT), kw)) return a;
            }
        }
        return null;
    }

    private static boolean matches(String label, String pkg, String kw) {
        if (kw.length() <= 3) {
            for (String token : label.split("[^\\p{L}\\p{N}]+")) if (token.equals(kw)) return true;
            return false;
        }
        return label.contains(kw) || pkg.contains(kw.replace(" ", ""));
    }

    private void launch(AppItem a) {
        Intent it = new Intent(Intent.ACTION_MAIN);
        it.addCategory(Intent.CATEGORY_LAUNCHER);
        it.setComponent(new ComponentName(a.pkg, a.cls));
        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        startSafe(it);
    }

    private void startSafe(Intent it) {
        try {
            startActivity(it);
        } catch (Exception e) {
            Toast.makeText(this, "Não foi possível abrir este aplicativo", Toast.LENGTH_SHORT).show();
        }
    }

    // ------------------------------------------------------------------ preferências dos blocos

    private int accent() {
        return prefs.getInt("accent", Ui.DEFAULT_ACCENT);
    }

    private int tileColor(int i) {
        int c = prefs.getInt("tile_" + TILE_IDS[i] + "_color", 0);
        return c == 0 ? accent() : c;
    }

    private String tileLabel(int i) {
        String s = prefs.getString("tile_" + TILE_IDS[i] + "_label", null);
        return TextUtils.isEmpty(s) ? TILE_NAMES[i] : s;
    }

    private boolean tileUsesAppIcon(int i) {
        return prefs.getBoolean("tile_" + TILE_IDS[i] + "_appicon", false);
    }

    private AppItem tileApp(int i) {
        String k = prefs.getString("tile_" + TILE_IDS[i] + "_app", null);
        AppItem a = findApp(k);
        return a != null ? a : detectApp(i);
    }

    private void openTile(int i) {
        AppItem a = tileApp(i);
        if (a != null) {
            launch(a);
        } else if (TILE_IDS[i].equals("settings")) {
            startSafe(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } else {
            showTileEditor(i);
        }
    }

    // ------------------------------------------------------------------ tela inicial

    private View buildHome() {
        FrameLayout home = new FrameLayout(this);
        homeBg = new CarbonView(this);
        home.addView(homeBg, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(Ui.s(30), Ui.s(26), Ui.s(26), Ui.s(26));
        home.addView(row, new FrameLayout.LayoutParams(-1, -1));

        // Coluna esquerda: marca, relógio, luzes, data, botão de apps
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        row.addView(left, new LinearLayout.LayoutParams(Ui.s(300), -1));

        LinearLayout brandRow = new LinearLayout(this);
        brandRow.setOrientation(LinearLayout.HORIZONTAL);
        brandRow.setGravity(Gravity.CENTER_VERTICAL);
        slashes = new SegmentsView(this, Ui.sf(7), Ui.sf(20), Ui.sf(4), 0.364f);
        brandRow.addView(slashes);
        brand = Ui.text(this, "", Ui.cond, 17, Ui.TEXT2);
        brand.setLetterSpacing(0.18f);
        brand.setSingleLine(true);
        brand.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0, -2, 1f);
        blp.leftMargin = Ui.s(14);
        brandRow.addView(brand, blp);
        left.addView(brandRow);

        TextClock clock = new TextClock(this);
        clock.setFormat24Hour("HH:mm");
        clock.setFormat12Hour("HH:mm");
        clock.setTypeface(Ui.condBold);
        clock.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sf(140));
        clock.setTextColor(Ui.TEXT);
        clock.setLetterSpacing(-0.02f);
        clock.setIncludeFontPadding(false);
        clock.setPadding(0, 0, Ui.s(12), 0);
        clock.setSingleLine(true);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, -2);
        clp.topMargin = Ui.s(14);
        left.addView(clock, clp);

        revBar = new SegmentsView(this, Ui.sf(13), Ui.sf(10), Ui.sf(4), 0.364f);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-2, -2);
        rlp.setMargins(Ui.s(4), Ui.s(8), 0, Ui.s(12));
        left.addView(revBar, rlp);

        TextClock date = new TextClock(this);
        date.setFormat24Hour("EEEE, d 'de' MMMM");
        date.setFormat12Hour("EEEE, d 'de' MMMM");
        date.setTypeface(Ui.body);
        date.setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.sf(22));
        date.setTextColor(Ui.TEXT2);
        date.setSingleLine(true);
        left.addView(date);

        // Toque longo no relógio abre as opções gerais do tema
        View.OnLongClickListener themeOptions = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                showThemeOptions();
                return true;
            }
        };
        clock.setOnLongClickListener(themeOptions);
        brandRow.setOnLongClickListener(themeOptions);
        date.setOnLongClickListener(themeOptions);

        left.addView(new View(this), new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout appsBtn = new LinearLayout(this);
        appsBtn.setOrientation(LinearLayout.HORIZONTAL);
        appsBtn.setGravity(Gravity.CENTER_VERTICAL);
        appsBtn.setPadding(Ui.s(22), 0, Ui.s(16), 0);
        appsBtnBg = new ChamferDrawable(Ui.BUTTON, Ui.BUTTON, Ui.sf(20), ChamferDrawable.LEFT_BAR);
        appsBtn.setBackground(appsBtnBg);
        ImageView appsIcon = new ImageView(this);
        appsIcon.setImageResource(R.drawable.ic_apps);
        appsIcon.setColorFilter(Ui.TEXT, PorterDuff.Mode.SRC_IN);
        appsBtn.addView(appsIcon, new LinearLayout.LayoutParams(Ui.s(30), Ui.s(30)));
        TextView appsTxt = Ui.text(this, "TODOS OS APLICATIVOS", Ui.cond, 22, Ui.TEXT);
        appsTxt.setLetterSpacing(0.02f);
        appsTxt.setSingleLine(true);
        LinearLayout.LayoutParams atl = new LinearLayout.LayoutParams(-2, -2);
        atl.leftMargin = Ui.s(14);
        appsBtn.addView(appsTxt, atl);
        appsBtn.setOnTouchListener(PressFeedback.INSTANCE);
        appsBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openDrawer();
            }
        });
        left.addView(appsBtn, new LinearLayout.LayoutParams(-1, Ui.s(66)));

        TextView hint = Ui.text(this, "Toque longo em um bloco para trocar o app, renomear ou mudar a cor.",
                Ui.body, 14, Ui.MUTED2);
        hint.setLineSpacing(0, 1.3f);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-1, -2);
        hlp.topMargin = Ui.s(14);
        left.addView(hint, hlp);

        // Costura tracejada
        dash = new DashLineView(this);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(Ui.s(4), -1);
        dlp.setMargins(Ui.s(20), Ui.s(8), Ui.s(15), Ui.s(8));
        row.addView(dash, dlp);

        // Grade 3x2 de blocos
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        row.addView(grid, new LinearLayout.LayoutParams(0, -1, 1f));
        for (int r = 0; r < 2; r++) {
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            grid.addView(line, new LinearLayout.LayoutParams(-1, 0, 1f));
            for (int c = 0; c < 3; c++) {
                int idx = r * 3 + c;
                Tile t = buildTile(idx);
                tiles[idx] = t;
                LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, -1, 1f);
                tlp.setMargins(Ui.s(7), Ui.s(7), Ui.s(7), Ui.s(7));
                line.addView(t.view, tlp);
            }
        }
        return home;
    }

    private Tile buildTile(final int idx) {
        Tile t = new Tile();
        t.index = idx;
        t.view = new LinearLayout(this);
        t.view.setOrientation(LinearLayout.VERTICAL);
        t.view.setPadding(Ui.s(22), Ui.s(20), Ui.s(22), Ui.s(24));
        t.bg = new ChamferDrawable(0xFF1A1F26, 0xFF111419, Ui.sf(26), ChamferDrawable.TILE);
        t.view.setBackground(t.bg);

        FrameLayout box = new FrameLayout(this);
        t.boxBg = new GradientDrawable();
        t.boxBg.setCornerRadius(Ui.sf(18));
        box.setBackground(t.boxBg);
        t.icon = new ImageView(this);
        t.icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        box.addView(t.icon, new FrameLayout.LayoutParams(Ui.s(48), Ui.s(48), Gravity.CENTER));
        t.view.addView(box, new LinearLayout.LayoutParams(Ui.s(84), Ui.s(84)));

        t.view.addView(new View(this), new LinearLayout.LayoutParams(-1, 0, 1f));

        t.label = Ui.text(this, "", Ui.cond, 30, Ui.TEXT);
        t.label.setAllCaps(true);
        t.label.setSingleLine(true);
        t.label.setEllipsize(TextUtils.TruncateAt.END);
        t.label.setLetterSpacing(0.02f);
        t.view.addView(t.label, new LinearLayout.LayoutParams(-1, -2));

        t.sub = Ui.text(this, "", Ui.body, 14, Ui.MUTED);
        t.sub.setSingleLine(true);
        t.sub.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
        slp.topMargin = Ui.s(6);
        t.view.addView(t.sub, slp);

        t.view.setOnTouchListener(PressFeedback.INSTANCE);
        t.view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openTile(idx);
            }
        });
        t.view.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                showTileEditor(idx);
                return true;
            }
        });
        return t;
    }

    private void applyTheme() {
        int a = accent();
        homeBg.setAccent(a);
        drawerBg.setAccent(a);
        int[] sl = {a, Ui.alpha(a, 0.65f), Ui.alpha(a, 0.35f)};
        slashes.setColors(sl);
        drawerSlashes.setColors(sl);
        updateRevBar();
        dash.setAccent(a);
        appsBtnBg.setAccent(a);
        hiddenBtnBg.setAccent(a);
        brand.setText(prefs.getString("brand", "VOLKSWAGEN JETTA"));
        refreshTiles();
    }

    private void updateRevBar() {
        int a = accent();
        int[] c = new int[16];
        for (int i = 0; i < 16; i++) {
            int base = i >= 12 ? a : (i >= 8 ? 0xFFE8B500 : 0xFF5B6370);
            c[i] = i < revLit ? base : Ui.alpha(0xFF3A4048, 0.5f);
        }
        revBar.setColors(c);
    }

    /** Ao abrir: as luzes de troca de marcha sobem até o fim e voltam, como um painel ligando. */
    private void startupSweep() {
        ValueAnimator va = ValueAnimator.ofInt(0, 16, 13);
        va.setDuration(1400);
        va.setStartDelay(250);
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                int v = (Integer) animation.getAnimatedValue();
                if (v != revLit) {
                    revLit = v;
                    updateRevBar();
                }
            }
        });
        va.start();
    }

    private void refreshTiles() {
        for (int i = 0; i < tiles.length; i++) {
            Tile t = tiles[i];
            if (t == null) continue;
            int color = tileColor(i);
            AppItem a = tileApp(i);
            t.bg.setAccent(color);
            t.boxBg.setColor(Ui.alpha(color, 0.14f));
            t.boxBg.setStroke(Math.max(1, Ui.s(1)), Ui.alpha(color, 0.35f));
            t.label.setText(tileLabel(i));
            if (a != null) {
                t.sub.setText(a.label);
            } else if (TILE_IDS[i].equals("settings")) {
                t.sub.setText("Configurações do sistema");
            } else {
                t.sub.setText("Toque para escolher o app");
            }
            if (tileUsesAppIcon(i) && a != null && a.icon != null) {
                t.icon.clearColorFilter();
                Drawable.ConstantState cs = a.icon.getConstantState();
                t.icon.setImageDrawable(cs != null ? cs.newDrawable(getResources()) : a.icon);
                ViewGroup.LayoutParams lp = t.icon.getLayoutParams();
                lp.width = Ui.s(64);
                lp.height = Ui.s(64);
                t.icon.setLayoutParams(lp);
            } else {
                t.icon.setImageResource(TILE_ICONS[i]);
                t.icon.setColorFilter(color, PorterDuff.Mode.SRC_IN);
                ViewGroup.LayoutParams lp = t.icon.getLayoutParams();
                lp.width = Ui.s(48);
                lp.height = Ui.s(48);
                t.icon.setLayoutParams(lp);
            }
        }
    }

    // ------------------------------------------------------------------ gaveta de aplicativos

    private FrameLayout buildDrawer() {
        FrameLayout d = new FrameLayout(this);
        d.setClickable(true);
        drawerBg = new CarbonView(this);
        d.addView(drawerBg, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Ui.s(28), Ui.s(22), Ui.s(28), Ui.s(16));
        d.addView(col, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        col.addView(header, new LinearLayout.LayoutParams(-1, Ui.s(60)));

        FrameLayout back = new FrameLayout(this);
        back.setBackground(new ChamferDrawable(Ui.BUTTON, Ui.BUTTON, Ui.sf(14), ChamferDrawable.PLAIN));
        back.setContentDescription("Voltar para a tela inicial");
        ImageView backIcon = new ImageView(this);
        backIcon.setImageResource(R.drawable.ic_back);
        backIcon.setColorFilter(Ui.TEXT, PorterDuff.Mode.SRC_IN);
        back.addView(backIcon, new FrameLayout.LayoutParams(Ui.s(28), Ui.s(28), Gravity.CENTER));
        back.setOnTouchListener(PressFeedback.INSTANCE);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeDrawer();
            }
        });
        header.addView(back, new LinearLayout.LayoutParams(Ui.s(58), Ui.s(58)));

        drawerSlashes = new SegmentsView(this, Ui.sf(7), Ui.sf(22), Ui.sf(4), 0.364f);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-2, -2);
        sp.leftMargin = Ui.s(18);
        header.addView(drawerSlashes, sp);

        TextView title = Ui.text(this, "APLICATIVOS", Ui.condBold, 36, Ui.TEXT);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1f);
        tp.leftMargin = Ui.s(14);
        header.addView(title, tp);

        LinearLayout hiddenBtn = new LinearLayout(this);
        hiddenBtn.setOrientation(LinearLayout.HORIZONTAL);
        hiddenBtn.setGravity(Gravity.CENTER_VERTICAL);
        hiddenBtn.setPadding(Ui.s(20), 0, Ui.s(22), 0);
        hiddenBtnBg = new ChamferDrawable(Ui.BUTTON, Ui.BUTTON, Ui.sf(14), ChamferDrawable.PLAIN);
        hiddenBtn.setBackground(hiddenBtnBg);
        ImageView eye = new ImageView(this);
        eye.setImageResource(R.drawable.ic_eye_off);
        eye.setColorFilter(Ui.TEXT2, PorterDuff.Mode.SRC_IN);
        hiddenBtn.addView(eye, new LinearLayout.LayoutParams(Ui.s(24), Ui.s(24)));
        hiddenBtnText = Ui.text(this, "MOSTRAR OCULTOS", Ui.cond, 19, Ui.TEXT2);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-2, -2);
        hp.leftMargin = Ui.s(10);
        hiddenBtn.addView(hiddenBtnText, hp);
        hiddenBtn.setOnTouchListener(PressFeedback.INSTANCE);
        hiddenBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showHidden = !showHidden;
                hiddenBtnText.setText(showHidden ? "ESCONDER OCULTOS" : "MOSTRAR OCULTOS");
                hiddenBtnBg.setBorder(showHidden ? accent() : 0);
                drawerAdapter.rebuild();
            }
        });
        header.addView(hiddenBtn, new LinearLayout.LayoutParams(-2, Ui.s(58)));

        GridView grid = new GridView(this);
        grid.setNumColumns(6);
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setHorizontalSpacing(Ui.s(12));
        grid.setVerticalSpacing(Ui.s(12));
        grid.setSelector(new ColorDrawable(Color.TRANSPARENT));
        grid.setVerticalScrollBarEnabled(false);
        grid.setClipToPadding(false);
        grid.setPadding(0, 0, 0, Ui.s(12));
        drawerAdapter = new DrawerAdapter();
        grid.setAdapter(drawerAdapter);
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                launch(drawerAdapter.getItem(position));
                root.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        closeDrawer();
                    }
                }, 400);
            }
        });
        grid.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                showAppMenu(drawerAdapter.getItem(position));
                return true;
            }
        });
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(-1, 0, 1f);
        gp.topMargin = Ui.s(18);
        col.addView(grid, gp);
        return d;
    }

    private void openDrawer() {
        drawerAdapter.rebuild();
        drawer.setAlpha(0f);
        drawer.setTranslationY(Ui.sf(30));
        drawer.setVisibility(View.VISIBLE);
        drawer.animate().alpha(1f).translationY(0f).setDuration(180).start();
    }

    private void closeDrawer() {
        if (drawer.getVisibility() != View.VISIBLE) return;
        drawer.animate().alpha(0f).translationY(Ui.sf(30)).setDuration(150).withEndAction(new Runnable() {
            @Override
            public void run() {
                drawer.setVisibility(View.GONE);
            }
        }).start();
    }

    private class DrawerAdapter extends BaseAdapter {
        private final List<AppItem> visible = new ArrayList<>();

        void rebuild() {
            visible.clear();
            for (AppItem a : apps) if (showHidden || !a.hidden) visible.add(a);
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return visible.size();
        }

        @Override
        public AppItem getItem(int position) {
            return visible.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout cell;
            if (convertView instanceof LinearLayout) {
                cell = (LinearLayout) convertView;
            } else {
                cell = new LinearLayout(MainActivity.this);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER);
                cell.setPadding(Ui.s(6), Ui.s(10), Ui.s(6), Ui.s(8));
                GradientDrawable g = new GradientDrawable();
                g.setColor(Ui.PANEL);
                g.setCornerRadius(Ui.sf(16));
                g.setStroke(Math.max(1, Ui.s(1)), 0xFF1F242B);
                cell.setBackground(g);
                cell.setLayoutParams(new AbsListView.LayoutParams(-1, Ui.s(142)));
                ImageView iv = new ImageView(MainActivity.this);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                cell.addView(iv, new LinearLayout.LayoutParams(Ui.s(72), Ui.s(72)));
                TextView tv = Ui.text(MainActivity.this, "", Ui.bodyMed, 16, Ui.TEXT);
                tv.setGravity(Gravity.CENTER);
                tv.setMaxLines(2);
                tv.setEllipsize(TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                lp.topMargin = Ui.s(10);
                cell.addView(tv, lp);
            }
            AppItem a = visible.get(position);
            ((ImageView) cell.getChildAt(0)).setImageDrawable(a.icon);
            ((TextView) cell.getChildAt(1)).setText(a.label);
            cell.setAlpha(a.hidden ? 0.4f : 1f);
            return cell;
        }
    }

    private void showAppMenu(final AppItem a) {
        final String[] opts = {"Renomear", a.hidden ? "Mostrar na lista" : "Ocultar da lista",
                "Restaurar nome original", "Informações do app", "Desinstalar"};
        AlertDialog d = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(a.label)
                .setItems(opts, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case 0:
                                askText("Renomear aplicativo", a.label, new TextCallback() {
                                    @Override
                                    public void onText(String s) {
                                        prefs.edit().putString("name_" + a.key(), s).apply();
                                        reloadApps();
                                    }
                                });
                                break;
                            case 1:
                                Set<String> hidden = new HashSet<>(prefs.getStringSet("hidden", new HashSet<String>()));
                                if (a.hidden) hidden.remove(a.key());
                                else hidden.add(a.key());
                                prefs.edit().putStringSet("hidden", hidden).apply();
                                reloadApps();
                                break;
                            case 2:
                                prefs.edit().remove("name_" + a.key()).apply();
                                reloadApps();
                                break;
                            case 3:
                                startSafe(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.parse("package:" + a.pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                                break;
                            case 4:
                                startSafe(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + a.pkg))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                                break;
                            default:
                                break;
                        }
                    }
                }).create();
        showDialog(d);
    }

    // ------------------------------------------------------------------ editor de bloco (toque longo)

    private void showTileEditor(final int idx) {
        dismissDialog();
        final Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        final int color = tileColor(idx);
        final AppItem current = tileApp(idx);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        ChamferDrawable pbg = new ChamferDrawable(Ui.PANEL, Ui.PANEL, Ui.sf(24), ChamferDrawable.PLAIN);
        pbg.setBorder(Ui.LINE);
        panel.setBackground(pbg);

        // Cabeçalho
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(Ui.s(24), 0, Ui.s(30), 0);
        FrameLayout box = new FrameLayout(this);
        GradientDrawable bb = new GradientDrawable();
        bb.setCornerRadius(Ui.sf(12));
        bb.setColor(Ui.alpha(color, 0.14f));
        box.setBackground(bb);
        ImageView hi = new ImageView(this);
        hi.setImageResource(TILE_ICONS[idx]);
        hi.setColorFilter(color, PorterDuff.Mode.SRC_IN);
        box.addView(hi, new FrameLayout.LayoutParams(Ui.s(26), Ui.s(26), Gravity.CENTER));
        header.addView(box, new LinearLayout.LayoutParams(Ui.s(46), Ui.s(46)));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView small = Ui.text(this, "EDITAR BLOCO", Ui.bodyMed, 13, Ui.MUTED);
        small.setLetterSpacing(0.15f);
        titles.addView(small);
        TextView big = Ui.text(this, tileLabel(idx), Ui.cond, 28, Ui.TEXT);
        big.setAllCaps(true);
        LinearLayout.LayoutParams bgl = new LinearLayout.LayoutParams(-2, -2);
        bgl.topMargin = Ui.s(4);
        titles.addView(big, bgl);
        LinearLayout.LayoutParams ttl = new LinearLayout.LayoutParams(0, -2, 1f);
        ttl.leftMargin = Ui.s(14);
        header.addView(titles, ttl);
        FrameLayout close = new FrameLayout(this);
        close.setBackground(new ChamferDrawable(Ui.PANEL2, Ui.PANEL2, Ui.sf(12), ChamferDrawable.PLAIN));
        close.setContentDescription("Fechar");
        ImageView ci = new ImageView(this);
        ci.setImageResource(R.drawable.ic_close);
        ci.setColorFilter(Ui.TEXT, PorterDuff.Mode.SRC_IN);
        close.addView(ci, new FrameLayout.LayoutParams(Ui.s(24), Ui.s(24), Gravity.CENTER));
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
            }
        });
        header.addView(close, new LinearLayout.LayoutParams(Ui.s(54), Ui.s(54)));
        panel.addView(header, new LinearLayout.LayoutParams(-1, Ui.s(80)));
        panel.addView(divider(true), new LinearLayout.LayoutParams(-1, Math.max(1, Ui.s(1))));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        panel.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));

        // Opções à esquerda
        LinearLayout opts = new LinearLayout(this);
        opts.setOrientation(LinearLayout.VERTICAL);
        opts.setPadding(Ui.s(18), Ui.s(18), Ui.s(18), Ui.s(18));
        body.addView(opts, new LinearLayout.LayoutParams(Ui.s(290), -1));

        opts.addView(optionButton(R.drawable.ic_edit, "Renomear", false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
                askText("Nome do bloco", tileLabel(idx), new TextCallback() {
                    @Override
                    public void onText(String s) {
                        prefs.edit().putString("tile_" + TILE_IDS[idx] + "_label", s).apply();
                        refreshTiles();
                        showTileEditor(idx);
                    }
                });
            }
        }), optionParams());

        final boolean appIcon = tileUsesAppIcon(idx);
        opts.addView(optionButton(R.drawable.ic_image, appIcon ? "Ícone: do aplicativo" : "Ícone: do tema", appIcon,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        prefs.edit().putBoolean("tile_" + TILE_IDS[idx] + "_appicon", !appIcon).apply();
                        refreshTiles();
                        showTileEditor(idx);
                    }
                }), optionParams());

        // Cores
        LinearLayout colorBox = new LinearLayout(this);
        colorBox.setOrientation(LinearLayout.VERTICAL);
        colorBox.setPadding(Ui.s(16), Ui.s(12), Ui.s(16), Ui.s(14));
        GradientDrawable cbg = new GradientDrawable();
        cbg.setColor(Ui.PANEL2);
        cbg.setCornerRadius(Ui.sf(12));
        cbg.setStroke(Math.max(1, Ui.s(1)), Ui.LINE);
        colorBox.setBackground(cbg);
        colorBox.addView(Ui.text(this, "Cor do bloco", Ui.bodyMed, 16, Ui.TEXT));
        int saved = prefs.getInt("tile_" + TILE_IDS[idx] + "_color", 0);
        for (int r = 0; r < 2; r++) {
            LinearLayout sw = new LinearLayout(this);
            sw.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams swl = new LinearLayout.LayoutParams(-1, -2);
            swl.topMargin = Ui.s(10);
            colorBox.addView(sw, swl);
            for (int k = 0; k < 4; k++) {
                final int pc = Ui.PALETTE[r * 4 + k];
                View dot = new View(this);
                GradientDrawable g = new GradientDrawable();
                g.setShape(GradientDrawable.OVAL);
                g.setColor(pc);
                boolean sel = (saved == 0 ? accent() : saved) == pc;
                g.setStroke(Ui.s(3), sel ? Color.WHITE : Color.TRANSPARENT);
                dot.setBackground(g);
                dot.setContentDescription(Ui.PALETTE_NAMES[r * 4 + k]);
                dot.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        prefs.edit().putInt("tile_" + TILE_IDS[idx] + "_color", pc).apply();
                        refreshTiles();
                        showTileEditor(idx);
                    }
                });
                LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(Ui.s(40), Ui.s(40));
                dl.rightMargin = Ui.s(12);
                sw.addView(dot, dl);
            }
        }
        opts.addView(colorBox, optionParams());

        opts.addView(new View(this), new LinearLayout.LayoutParams(-1, 0, 1f));
        opts.addView(optionButton(R.drawable.ic_reset, "Restaurar padrão", false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String id = TILE_IDS[idx];
                prefs.edit().remove("tile_" + id + "_app").remove("tile_" + id + "_label")
                        .remove("tile_" + id + "_color").remove("tile_" + id + "_appicon").apply();
                refreshTiles();
                d.dismiss();
            }
        }), new LinearLayout.LayoutParams(-1, Ui.s(56)));

        body.addView(divider(false), new LinearLayout.LayoutParams(Math.max(1, Ui.s(1)), -1));

        // Lista de apps à direita
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setPadding(Ui.s(20), Ui.s(16), Ui.s(20), Ui.s(8));
        body.addView(right, new LinearLayout.LayoutParams(0, -1, 1f));
        right.addView(Ui.text(this, "Escolha qual app este bloco abre:", Ui.body, 15, Ui.MUTED));
        ListView list = new ListView(this);
        list.setDivider(new ColorDrawable(Color.TRANSPARENT));
        list.setDividerHeight(Ui.s(8));
        list.setSelector(new ColorDrawable(Color.TRANSPARENT));
        list.setVerticalScrollBarEnabled(false);
        final List<AppItem> all = new ArrayList<>(apps);
        list.setAdapter(new BaseAdapter() {
            @Override
            public int getCount() {
                return all.size();
            }

            @Override
            public Object getItem(int position) {
                return all.get(position);
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                LinearLayout row;
                if (convertView instanceof LinearLayout) {
                    row = (LinearLayout) convertView;
                } else {
                    row = new LinearLayout(MainActivity.this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(Ui.s(14), 0, Ui.s(14), 0);
                    row.setLayoutParams(new AbsListView.LayoutParams(-1, Ui.s(60)));
                    ImageView iv = new ImageView(MainActivity.this);
                    row.addView(iv, new LinearLayout.LayoutParams(Ui.s(40), Ui.s(40)));
                    TextView tv = Ui.text(MainActivity.this, "", Ui.body, 18, Ui.TEXT);
                    tv.setSingleLine(true);
                    tv.setEllipsize(TextUtils.TruncateAt.END);
                    LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1f);
                    tl.leftMargin = Ui.s(14);
                    row.addView(tv, tl);
                    ImageView check = new ImageView(MainActivity.this);
                    check.setImageResource(R.drawable.ic_check);
                    row.addView(check, new LinearLayout.LayoutParams(Ui.s(26), Ui.s(26)));
                }
                AppItem a = all.get(position);
                boolean sel = current != null && current.key().equals(a.key());
                GradientDrawable g = new GradientDrawable();
                g.setCornerRadius(Ui.sf(12));
                g.setColor(sel ? Ui.alpha(color, 0.12f) : Ui.PANEL2);
                g.setStroke(Math.max(1, Ui.s(1)), sel ? color : Ui.LINE);
                row.setBackground(g);
                ((ImageView) row.getChildAt(0)).setImageDrawable(a.icon);
                ((TextView) row.getChildAt(1)).setText(a.label);
                ImageView check = (ImageView) row.getChildAt(2);
                check.setColorFilter(color, PorterDuff.Mode.SRC_IN);
                check.setVisibility(sel ? View.VISIBLE : View.INVISIBLE);
                return row;
            }
        });
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                prefs.edit().putString("tile_" + TILE_IDS[idx] + "_app", all.get(position).key()).apply();
                refreshTiles();
                d.dismiss();
            }
        });
        if (current != null) {
            for (int i = 0; i < all.size(); i++) {
                if (all.get(i).key().equals(current.key())) {
                    list.setSelection(Math.max(0, i - 1));
                    break;
                }
            }
        }
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-1, 0, 1f);
        ll.topMargin = Ui.s(12);
        right.addView(list, ll);

        d.setContentView(panel);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(Ui.s(820), Ui.s(500));
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setDimAmount(0.78f);
        }
        showDialog(d);
    }

    private View divider(boolean horizontal) {
        View v = new View(this);
        v.setBackgroundColor(0xFF232931);
        return v;
    }

    private LinearLayout.LayoutParams optionParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = Ui.s(10);
        return lp;
    }

    private View optionButton(int icon, String text, boolean active, View.OnClickListener click) {
        int a = accent();
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.HORIZONTAL);
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setPadding(Ui.s(16), 0, Ui.s(12), 0);
        b.setMinimumHeight(Ui.s(56));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(Ui.sf(12));
        g.setColor(active ? Ui.alpha(a, 0.14f) : Ui.PANEL2);
        g.setStroke(Math.max(1, Ui.s(1)), active ? a : Ui.LINE);
        b.setBackground(g);
        ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        iv.setColorFilter(Ui.TEXT, PorterDuff.Mode.SRC_IN);
        b.addView(iv, new LinearLayout.LayoutParams(Ui.s(22), Ui.s(22)));
        TextView tv = Ui.text(this, text, Ui.bodyMed, 18, Ui.TEXT);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
        tl.leftMargin = Ui.s(12);
        b.addView(tv, tl);
        b.setOnClickListener(click);
        return b;
    }

    // ------------------------------------------------------------------ opções gerais do tema

    private void showThemeOptions() {
        final String[] opts = {"Cor principal do tema", "Texto do topo", "Definir como tela inicial padrão",
                "Restaurar tudo ao padrão", "Sobre"};
        AlertDialog d = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Tema Jetta")
                .setItems(opts, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            pickAccent();
                        } else if (which == 1) {
                            askText("Texto do topo", prefs.getString("brand", "VOLKSWAGEN JETTA"), new TextCallback() {
                                @Override
                                public void onText(String s) {
                                    prefs.edit().putString("brand", s.toUpperCase(Locale.getDefault())).apply();
                                    applyTheme();
                                }
                            });
                        } else if (which == 2) {
                            openHomeSettings();
                        } else if (which == 3) {
                            confirmReset();
                        } else {
                            Toast.makeText(MainActivity.this, "Jetta Launcher " + BuildConfig.VERSION_NAME,
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                }).create();
        showDialog(d);
    }

    private void pickAccent() {
        AlertDialog d = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Cor principal")
                .setItems(Ui.PALETTE_NAMES, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        prefs.edit().putInt("accent", Ui.PALETTE[which]).apply();
                        applyTheme();
                    }
                }).create();
        showDialog(d);
    }

    private void confirmReset() {
        AlertDialog d = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Restaurar tudo?")
                .setMessage("Blocos, cores, nomes e apps ocultos voltam ao padrão.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Restaurar", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        prefs.edit().clear().putBoolean("welcome_done", true).apply();
                        reloadApps();
                        applyTheme();
                    }
                }).create();
        showDialog(d);
    }

    private void openHomeSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            startSafe(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
    }

    private boolean isDefaultHome() {
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_HOME);
        ResolveInfo ri = pm.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY);
        return ri != null && ri.activityInfo != null && getPackageName().equals(ri.activityInfo.packageName);
    }

    private void showWelcome() {
        if (isDefaultHome() || isFinishing()) return;
        AlertDialog d = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Usar o tema Jetta como tela inicial?")
                .setMessage("Na próxima tela, escolha \"Jetta Launcher\" como app de tela inicial. "
                        + "Se aparecer a pergunta ao apertar o botão Home, escolha \"Jetta Launcher\" e \"Sempre\".")
                .setNegativeButton("Agora não", null)
                .setPositiveButton("Configurar", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        openHomeSettings();
                    }
                }).create();
        showDialog(d);
    }

    // ------------------------------------------------------------------ utilidades de diálogo

    interface TextCallback {
        void onText(String s);
    }

    private void askText(String title, String current, final TextCallback cb) {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setSingleLine(true);
        input.setText(current);
        input.setSelectAllOnFocus(true);
        FrameLayout wrap = new FrameLayout(this);
        wrap.setPadding(Ui.s(24), Ui.s(8), Ui.s(24), 0);
        wrap.addView(input);
        AlertDialog d = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(title)
                .setView(wrap)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Salvar", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        cb.onText(input.getText().toString().trim());
                    }
                }).create();
        showDialog(d);
    }

    private void showDialog(Dialog d) {
        openDialog = d;
        try {
            d.show();
        } catch (Exception ignored) {
        }
    }

    private void dismissDialog() {
        if (openDialog != null) {
            try {
                openDialog.dismiss();
            } catch (Exception ignored) {
            }
            openDialog = null;
        }
    }
}
