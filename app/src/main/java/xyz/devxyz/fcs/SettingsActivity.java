package xyz.devxyz.fcs;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings, mirroring the classic IDE's Settings categories. Every toggle
 * and value here is really persisted in SharedPreferences (nothing is a
 * mock row): reopening Settings shows exactly what was set. The Editor
 * already wires one of them for real: "Keep display on when editing code"
 * keeps the screen on in the editor.
 */
public class SettingsActivity extends AppCompatActivity {

    static class Row {
        final String title;
        final boolean toggle;
        final String defStr;
        final boolean defBool;
        final boolean editable;

        Row(String title, boolean defBool) {
            this.title = title; this.toggle = true; this.defBool = defBool;
            this.defStr = ""; this.editable = false;
        }
        Row(String title, String defStr, boolean editable) {
            this.title = title; this.toggle = false; this.defStr = defStr;
            this.defBool = false; this.editable = editable;
        }
    }

    static class Cat {
        final String name;
        final Row[] rows;
        Cat(String name, Row[] rows) { this.name = name; this.rows = rows; }
    }

    static List<Cat> categories() {
        List<Cat> out = new ArrayList<>();
        out.add(new Cat("Application", new Row[]{
                new Row("Keep display on when running app", false),
                new Row("Report app errors to AIDE", true),
                new Row("Show toast messages when app is running", true),
                new Row("Display code completion list", true),
                new Row("Display code completion preview", true),
                new Row("Enable spell checker", false),
                new Row("Auto-capitalization", true),
        }));
        out.add(new Cat("Editor", new Row[]{
                new Row("Keep display on when editing code", false),
                new Row("Show line numbers", true),
                new Row("Show code fold markers", true),
                new Row("Show the soft keyboard when opening code files", true),
                new Row("Show the list of code completions", true),
                new Row("Enable spell checker", false),
                new Row("Auto-capitalization", true),
        }));
        out.add(new Cat("Code Style", new Row[]{
                new Row("Insert spaces instead of tab", true),
                new Row("Tab size", "4", true),
                new Row("Indent size", "4", true),
                new Row("Continuation indent size", "8", true),
                new Row("Indent empty lines", false),
                new Row("Automatically insert closing brackets, quotes and braces", true),
                new Row("Automatically re-indent current line", true),
                new Row("Re-indent current line on Enter", true),
                new Row("Automatically add closing braces when a new block is started", true),
                new Row("Format project when opening a project", false),
        }));
        out.add(new Cat("Build & Run", new Row[]{
                new Row("Keep display on when running app", false),
                new Row("Show toast messages when app is running", true),
                new Row("Report app errors to AIDE", true),
                new Row("Use 'Run' action to build & run project", true),
                new Row("Run activity class", "", true),
                new Row("App launcher command", "", true),
        }));
        out.add(new Cat("Dropbox", new Row[]{
                new Row("Dropbox account", "Not linked", false),
        }));
        out.add(new Cat("Git", new Row[]{
                new Row("Git account", "Not linked", false),
        }));
        out.add(new Cat("Keybindings", new Row[]{
                new Row("Keybindings profile", "Default", true),
        }));
        out.add(new Cat("Legal", new Row[]{
                new Row("Licenses", "", false),
                new Row("About DevxyzFCS", "", false),
        }));
        return out;
    }

    static SharedPreferences prefs(android.content.Context ctx) {
        return ctx.getSharedPreferences("devxyzfcs_settings", MODE_PRIVATE);
    }

    static boolean toggle(android.content.Context ctx, String cat, String title, boolean def) {
        return prefs(ctx).getBoolean(cat + ":" + title, def);
    }

    private RecyclerView listView;
    private LinearLayout detailView;
    private TextView detailTitle;
    private LinearLayout detailRows;
    private Cat current;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        setSupportActionBar(findViewById(R.id.toolbar));
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Settings");
        }
        listView = findViewById(R.id.settings_list);
        listView.setLayoutManager(new LinearLayoutManager(this));
        listView.setAdapter(new CatAdapter(categories()));
        detailView = findViewById(R.id.settings_detail);
        detailTitle = findViewById(R.id.detail_title);
        detailRows = findViewById(R.id.detail_rows);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            if (current != null) showList();
            else finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        if (current != null) showList();
        else super.onBackPressed();
    }

    private void showList() {
        current = null;
        detailView.setVisibility(View.GONE);
        listView.setVisibility(View.VISIBLE);
        if (getSupportActionBar() != null) getSupportActionBar().setTitle("Settings");
        listView.getAdapter().notifyDataSetChanged();
    }

    private void showDetail(Cat cat) {
        current = cat;
        if (getSupportActionBar() != null) getSupportActionBar().setTitle(cat.name);
        listView.setVisibility(View.GONE);
        detailView.setVisibility(View.VISIBLE);
        detailTitle.setText(cat.name);
        detailRows.removeAllViews();
        for (Row row : cat.rows) {
            View v = LayoutInflater.from(this)
                    .inflate(R.layout.item_setting, detailRows, false);
            TextView title = v.findViewById(R.id.setting_title);
            TextView value = v.findViewById(R.id.setting_value);
            Switch sw = v.findViewById(R.id.setting_switch);
            title.setText(row.title);
            String key = cat.name + ":" + row.title;
            if (row.toggle) {
                sw.setVisibility(View.VISIBLE);
                value.setVisibility(View.GONE);
                sw.setChecked(prefs(this).getBoolean(key, row.defBool));
                sw.setOnCheckedChangeListener((b, checked) ->
                        prefs(this).edit().putBoolean(key, checked).apply());
            } else {
                sw.setVisibility(View.GONE);
                value.setVisibility(View.VISIBLE);
                if (row.title.equals("Licenses")) {
                    value.setText("View");
                } else if (row.title.equals("About DevxyzFCS")) {
                    value.setText("1.0-aide");
                } else {
                    value.setText(prefs(this).getString(key, row.defStr));
                }
                v.setOnClickListener(view -> onValueRow(cat, row, value));
            }
            detailRows.addView(v);
        }
        if (cat.name.equals("Dropbox") || cat.name.equals("Git")) {
            TextView note = new TextView(this);
            note.setText("Account linking, susunod na phase.");
            note.setPadding(16, 16, 16, 16);
            note.setTextColor(0xFF757575);
            detailRows.addView(note);
        }
    }

    private void onValueRow(Cat cat, Row row, TextView valueView) {
        String key = cat.name + ":" + row.title;
        if (row.title.equals("Licenses")) {
            new AlertDialog.Builder(this)
                    .setTitle("Licenses")
                    .setMessage("DevxyzFCS — sariling branding at code ng JepongDevxyz.\n\n"
                            + "Third-party: AndroidX libraries (Apache License 2.0), "
                            + "Material Components (Apache License 2.0), "
                            + "vendored BCrypt (BSD-style mindrot license, see BCrypt.java).")
                    .setPositiveButton("OK", null).show();
            return;
        }
        if (row.title.equals("About DevxyzFCS")) {
            new AlertDialog.Builder(this)
                    .setTitle("About DevxyzFCS")
                    .setMessage("DevxyzFCS 1.0-aide — AIDE FCS-like Android IDE "
                            + "na nakakapag-build ng AndroidX/AppCompat, may Auto-fix "
                            + "at errors panel.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        if (!row.editable) {
            Toast.makeText(this, "Hindi pa suportado sa Phase 2", Toast.LENGTH_SHORT).show();
            return;
        }
        final EditText input = new EditText(this);
        input.setText(prefs(this).getString(key, row.defStr));
        new AlertDialog.Builder(this)
                .setTitle(row.title)
                .setView(input)
                .setPositiveButton("Save", (d, w) -> {
                    String val = input.getText().toString();
                    prefs(this).edit().putString(key, val).apply();
                    valueView.setText(val);
                })
                .setNegativeButton("Kansela", null).show();
    }

    private class CatAdapter extends RecyclerView.Adapter<CatAdapter.Holder> {
        private final List<Cat> cats;
        CatAdapter(List<Cat> cats) { this.cats = cats; }

        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_setting_category, parent, false);
            return new Holder(v);
        }

        @Override
        public void onBindViewHolder(Holder h, int pos) {
            Cat cat = cats.get(pos);
            h.name.setText(cat.name);
            h.itemView.setOnClickListener(v -> showDetail(cat));
        }

        @Override
        public int getItemCount() { return cats.size(); }

        class Holder extends RecyclerView.ViewHolder {
            TextView name;
            Holder(View v) {
                super(v);
                name = v.findViewById(R.id.category_name);
            }
        }
    }
}
