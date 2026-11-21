package com.example.glasslauncher;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    static class App { String label, pkg, cls; Drawable icon; }

    PackageManager pm;
    float d;
    List<App> all = new ArrayList<>(), shown = new ArrayList<>();
    BaseAdapter adapter;
    EditText search;
    GridView grid;

    int dp(int v) { return Math.round(v * d); }

    // Frosted-glass look: translucent gradient fill + bright edge
    Drawable glass(int radius) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0x66FFFFFF, 0x1AFFFFFF});
        g.setCornerRadius(dp(radius));
        g.setStroke(dp(1), 0x77FFFFFF);
        return g;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        pm = getPackageManager();
        d = getResources().getDisplayMetrics().density;
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(12));

        // Glass clock card
        LinearLayout clock = new LinearLayout(this);
        clock.setOrientation(LinearLayout.VERTICAL);
        clock.setBackground(glass(32));
        clock.setPadding(dp(24), dp(16), dp(24), dp(18));
        TextClock time = new TextClock(this);
        time.setFormat12Hour("h:mm");
        time.setFormat24Hour("HH:mm");
        time.setTextSize(68);
        time.setTextColor(Color.WHITE);
        time.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        time.setShadowLayer(10, 0, 2, 0x55000000);
        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE, MMMM d");
        date.setFormat24Hour("EEEE, MMMM d");
        date.setTextSize(16);
        date.setTextColor(0xEEFFFFFF);
        clock.addView(time);
        clock.addView(date);
        root.addView(clock, new LinearLayout.LayoutParams(-1, -2));

        // Search
        search = new EditText(this);
        search.setHint("Search apps");
        search.setHintTextColor(0xBBFFFFFF);
        search.setTextColor(Color.WHITE);
        search.setSingleLine(true);
        search.setImeOptions(EditorInfo.IME_ACTION_GO);
        search.setBackground(glass(28));
        search.setPadding(dp(20), dp(12), dp(20), dp(12));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.topMargin = dp(12);
        root.addView(search, sp);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b2, int c) {}
            public void onTextChanged(CharSequence s, int a, int b2, int c) { filter(s.toString()); }
            public void afterTextChanged(Editable s) {}
        });
        search.setOnEditorActionListener((v, id, e) -> {
            if (!shown.isEmpty()) launch(shown.get(0));
            return true;
        });

        // App grid
        grid = new GridView(this);
        grid.setNumColumns(4);
        grid.setVerticalSpacing(dp(14));
        grid.setSelector(new ColorDrawable(0));
        grid.setPadding(dp(8), dp(16), dp(8), dp(8));
        grid.setClipToPadding(false);
        grid.setVerticalScrollBarEnabled(false);
        adapter = new BaseAdapter() {
            public int getCount() { return shown.size(); }
            public Object getItem(int i) { return shown.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(int i, View v, ViewGroup p) {
                if (v == null) {
                    LinearLayout c = new LinearLayout(MainActivity.this);
                    c.setOrientation(LinearLayout.VERTICAL);
                    c.setGravity(Gravity.CENTER_HORIZONTAL);
                    ImageView iv = new ImageView(MainActivity.this);
                    c.addView(iv, new LinearLayout.LayoutParams(dp(52), dp(52)));
                    TextView t = new TextView(MainActivity.this);
                    t.setTextColor(Color.WHITE);
                    t.setTextSize(11);
                    t.setSingleLine(true);
                    t.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    t.setGravity(Gravity.CENTER);
                    t.setShadowLayer(6, 0, 1, 0x88000000);
                    c.addView(t, new LinearLayout.LayoutParams(-1, -2));
                    v = c;
                }
                LinearLayout c = (LinearLayout) v;
                App a = shown.get(i);
                ((ImageView) c.getChildAt(0)).setImageDrawable(a.icon);
                ((TextView) c.getChildAt(1)).setText(a.label);
                return v;
            }
        };
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((p, v, i, id) -> launch(shown.get(i)));
        grid.setOnItemLongClickListener((p, v, i, id) -> {
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + shown.get(i).pkg)));
            } catch (Exception e) {}
            return true;
        });
        FrameLayout gridCard = new FrameLayout(this);
        gridCard.setBackground(glass(32));
        gridCard.addView(grid, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(-1, 0, 1f);
        gp.topMargin = dp(12);
        root.addView(gridCard, gp);

        // Dock
        LinearLayout dock = new LinearLayout(this);
        dock.setBackground(glass(36));
        dock.setPadding(dp(8), dp(12), dp(8), dp(12));
        Intent[] di = {
            new Intent(Intent.ACTION_DIAL),
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING),
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER),
            new Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        };
        for (final Intent in : di) {
            ResolveInfo ri = pm.resolveActivity(in, PackageManager.MATCH_DEFAULT_ONLY);
            if (ri == null) continue;
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(ri.loadIcon(pm));
            iv.setOnClickListener(v -> {
                try { in.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(in); } catch (Exception e) {}
            });
            dock.addView(iv, new LinearLayout.LayoutParams(0, dp(52), 1f));
        }
        LinearLayout.LayoutParams dp2 = new LinearLayout.LayoutParams(-1, -2);
        dp2.topMargin = dp(12);
        root.addView(dock, dp2);

        setContentView(root);
    }

    @Override protected void onResume() { super.onResume(); loadApps(); }

    @Override protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        search.setText("");
        grid.smoothScrollToPosition(0);
    }

    @Override public void onBackPressed() { search.setText(""); }

    void loadApps() {
        all.clear();
        Intent m = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo r : pm.queryIntentActivities(m, 0)) {
            if (r.activityInfo.packageName.equals(getPackageName())) continue;
            App a = new App();
            a.label = r.loadLabel(pm).toString();
            a.icon = r.loadIcon(pm);
            a.pkg = r.activityInfo.packageName;
            a.cls = r.activityInfo.name;
            all.add(a);
        }
        Collections.sort(all, (x, y) -> x.label.compareToIgnoreCase(y.label));
        filter(search.getText().toString());
    }

    void filter(String q) {
        q = q.trim().toLowerCase(Locale.getDefault());
        shown.clear();
        for (App a : all)
            if (q.isEmpty() || a.label.toLowerCase(Locale.getDefault()).contains(q)) shown.add(a);
        adapter.notifyDataSetChanged();
    }

    void launch(App a) {
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setClassName(a.pkg, a.cls)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try { startActivity(i); } catch (Exception e) {}
    }
}
