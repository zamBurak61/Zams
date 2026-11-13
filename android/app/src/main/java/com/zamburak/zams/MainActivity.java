package com.zamburak.zams;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Sign-in screen and a small status page; the widgets do the rest. */
public class MainActivity extends Activity {

    private LinearLayout root;
    private boolean dark;
    private int ink, muted, surface, accent, line;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        ink = Color.parseColor(dark ? "#E5EAF0" : "#16202B");
        muted = Color.parseColor(dark ? "#94A1AF" : "#5D6A78");
        surface = Color.parseColor(dark ? "#161D26" : "#FFFFFF");
        accent = Color.parseColor(dark ? "#7F98FF" : "#2747C9");
        line = Color.parseColor(dark ? "#28323E" : "#DCE2E9");
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(24);
        root.setPadding(p, dp(40), p, p);
        sv.addView(root, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(sv);
        show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (root != null) show();
    }

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    private TextView text(String s, float sp, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setLineSpacing(0, 1.15f);
        return t;
    }

    private void add(View v, int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(topDp);
        root.addView(v, lp);
    }

    private EditText field(String hint, int type) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setInputType(type);
        e.setSingleLine(true);
        e.setTextColor(ink);
        e.setHintTextColor(muted);
        GradientDrawable g = new GradientDrawable();
        g.setColor(surface);
        g.setCornerRadius(dp(10));
        g.setStroke(dp(1), line);
        e.setBackground(g);
        e.setPadding(dp(14), dp(12), dp(14), dp(12));
        return e;
    }

    private Button button(String s, boolean primary) {
        Button btn = new Button(this);
        btn.setText(s);
        btn.setAllCaps(false);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        btn.setTextColor(primary ? Color.WHITE : ink);
        GradientDrawable g = new GradientDrawable();
        g.setColor(primary ? Color.parseColor("#2747C9") : surface);
        g.setCornerRadius(dp(10));
        if (!primary) g.setStroke(dp(1), line);
        btn.setBackground(g);
        btn.setStateListAnimator(null);
        btn.setMinHeight(dp(48));
        return btn;
    }

    private void show() {
        root.removeAllViews();
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.mipmap.ic_launcher);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(64), dp(64));
        root.addView(icon, ip);
        add(text("Zam's Widget", 28, true, ink), 14);
        if (Store.loggedIn(this)) showSignedIn(); else showLogin(null);
    }

    private void showLogin(String msg) {
        add(text("Zam's'teki e-posta ve şifrenle giriş yap. Widget'lar programını bu hesaptan okur.", 15, false, muted), 6);
        EditText email = field("E-posta", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        email.setText(Store.email(this));
        EditText pass = field("Şifre", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        add(email, 22);
        add(pass, 10);
        Button go = button("Giriş yap", true);
        add(go, 16);
        TextView status = text(msg == null ? "" : msg, 14, false, Color.parseColor(dark ? "#F27689" : "#B42339"));
        add(status, 12);
        go.setOnClickListener(v -> {
            String e = email.getText().toString().trim(), pw = pass.getText().toString();
            if (e.isEmpty() || pw.isEmpty()) { status.setText("E-posta ve şifreni yaz."); return; }
            go.setEnabled(false);
            status.setTextColor(muted);
            status.setText("Giriş yapılıyor…");
            new Thread(() -> {
                String err = null;
                try {
                    Api.login(this, e, pw);
                    Api.fetch(this);
                } catch (Exception ex) {
                    err = Api.message(ex);
                }
                Updater.renderAll(this);
                final String fe = err;
                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    if (fe != null && !Store.loggedIn(this)) {
                        go.setEnabled(true);
                        status.setTextColor(Color.parseColor(dark ? "#F27689" : "#B42339"));
                        status.setText(fe);
                    } else show();
                });
            }).start();
        });
    }

    private void showSignedIn() {
        add(text("Giriş yapıldı: " + Store.email(this), 15, false, muted), 6);
        long at = Store.fetchedAt(this);
        String err = Store.error(this);
        String upd = at > 0 ? "Son güncelleme: " + new SimpleDateFormat("d MMM HH:mm", new Locale("tr")).format(new Date(at)) : "Henüz veri alınmadı.";
        TextView st = text(err != null ? upd + "\n" + err : upd, 14, false, err != null ? Color.parseColor(dark ? "#F0A060" : "#B4541A") : muted);
        add(st, 4);

        add(text("Widget nasıl eklenir?", 17, true, ink), 26);
        add(text("1. Ana ekranda boş bir yere basılı tut.\n2. \"Widget'lar\"a dokun.\n3. Listede \"Zam's Widget\"ı bul.\n4. Bugünün işleri, Bugün · takvim ya da Hafta'yı seçip ana ekrana sürükle.\n5. Kenarlarından tutup boyutunu ayarlayabilirsin.", 15, false, muted), 6);
        add(text("Widget'lar yaklaşık 30 dakikada bir kendini yeniler. Hemen yenilemek için widget'ın sağ üstündeki ↻ simgesine dokun. Widget'a dokununca Zam's açılır.", 14, false, muted), 14);

        Button refresh = button("Şimdi yenile", true);
        add(refresh, 22);
        refresh.setOnClickListener(v -> {
            refresh.setEnabled(false);
            refresh.setText("Yenileniyor…");
            new Thread(() -> {
                Updater.refresh(this);
                runOnUiThread(() -> { if (!isFinishing()) show(); });
            }).start();
        });
        Button open = button("Zam's'i aç", false);
        add(open, 10);
        open.setOnClickListener(v -> {
            try { startActivity(Updater.openApp(this)); } catch (Exception ignored) { }
        });
        Button out = button("Çıkış yap", false);
        add(out, 10);
        out.setOnClickListener(v -> {
            Store.logout(this, false);
            Updater.renderAll(this);
            show();
        });
        TextView ver = text("Sürüm " + versionName(), 12, false, muted);
        ver.setGravity(Gravity.CENTER_HORIZONTAL);
        add(ver, 28);
    }

    private String versionName() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception e) { return "?"; }
    }
}
