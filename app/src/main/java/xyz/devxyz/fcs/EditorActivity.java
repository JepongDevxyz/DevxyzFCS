package xyz.devxyz.fcs;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class EditorActivity extends AppCompatActivity {

    private String projectName;
    private List<File> files = new ArrayList<>();
    private File currentFile;
    private EditText editor;
    private TextView logView;
    private TextView errorsHeader;
    private LinearLayout errorsPanel;
    private TextView lineNumbers;
    private Spinner spinner;
    private boolean navigateMode = false;
    private boolean formattedOnOpen = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editor);
        setSupportActionBar(findViewById(R.id.toolbar));
        projectName = getIntent().getStringExtra("project");
        setTitle("DevxyzFCS - " + projectName);
        errorsHeader = findViewById(R.id.errors_header);
        errorsPanel = findViewById(R.id.errors_panel);
        if (SettingsActivity.toggle(this, "Editor",
                "Keep display on when editing code", false)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, findViewById(R.id.toolbar),
                R.string.app_name, R.string.app_name);
        drawer.addDrawerListener(toggle);
        toggle.syncState();
        NavigationView nav = findViewById(R.id.nav_view);
        nav.setNavigationItemSelectedListener(item -> {
            drawer.closeDrawers();
            int id = item.getItemId();
            if (id == R.id.nav_navigate) {
                navigateMode = !navigateMode;
                Toast.makeText(this, navigateMode
                        ? "Navigate Mode ON — pindutin ang isang pangalan sa code para tumalon sa definition nito"
                        : "Navigate Mode OFF", Toast.LENGTH_SHORT).show();
            } else if (id == R.id.nav_find) {
                showFindDialog();
            } else if (id == R.id.nav_refactor) {
                showRefactorDialog();
            } else if (id == R.id.nav_code) {
                showCodeDialog();
            } else if (id == R.id.nav_format) {
                formatCurrentFile();
            } else if (id == R.id.nav_project) {
                finish();
            } else if (id == R.id.nav_tools) {
                showToolsDialog();
            } else if (id == R.id.nav_settings) {
                startActivity(new android.content.Intent(this, SettingsActivity.class));
            } else if (id == R.id.nav_exit) {
                finishAffinity();
            }
            return true;
        });
        editor = findViewById(R.id.editor);
        logView = findViewById(R.id.build_log);
        lineNumbers = findViewById(R.id.line_numbers);
        lineNumbers.setVisibility(SettingsActivity.toggle(this, "Editor",
                "Show line numbers", true) ? View.VISIBLE : View.GONE);
        editor.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { updateLineNumbers(); }
        });
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            editor.setOnScrollChangeListener((v, sx, sy, ox, oy) ->
                    lineNumbers.scrollTo(0, sy));
        }
        editor.setOnTouchListener((v, e) -> {
            if (navigateMode && e.getAction() == MotionEvent.ACTION_UP) {
                int offset = editor.getOffsetForPosition(e.getX(), e.getY());
                String word = CodeTools.wordAt(editor.getText().toString(), offset);
                if (word != null) navigateTo(word);
            }
            return false;
        });
        spinner = findViewById(R.id.file_spinner);
        refreshFileList(spinner);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                openFile(files.get(pos));
            }
            @Override
            public void onNothingSelected(AdapterView<?> p) {}
        });
        Button save = findViewById(R.id.btn_save);
        save.setOnClickListener(v -> {
            if (currentFile == null) return;
            try {
                ProjectManager.write(currentFile, editor.getText().toString());
                Toast.makeText(this, "Na-save: " + currentFile.getName(), Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
        Button build = findViewById(R.id.btn_build);
        build.setOnClickListener(v -> {
            File project = ProjectManager.projectDir(this, projectName);
            String log = BuildValidator.validate(project);
            logView.setText(log);
            refreshErrors(project);
        });
        Button autofix = findViewById(R.id.btn_autofix);
        autofix.setOnClickListener(v -> {
            File project = ProjectManager.projectDir(this, projectName);
            StringBuilder log = new StringBuilder();
            try {
                List<AutoFixer.Issue> issues = AutoFixer.detect(project);
                if (issues.isEmpty()) {
                    log.append("Auto-fix: walang maayos na error ang nakita.\n");
                } else {
                    // Preview first: show exactly what will change, then apply
                    // one fix at a time so a failure stops before later fixes.
                    log.append("Auto-fix: ").append(issues.size())
                            .append(" na ayos ang nakita:\n");
                    for (AutoFixer.Issue issue : issues) {
                        String rel = issue.file.getPath()
                                .substring(project.getPath().length() + 1);
                        log.append("• ").append(rel).append(" — ").append(issue.title)
                                .append(" (").append(issue.detail).append(")\n");
                    }
                    for (AutoFixer.Issue issue : issues) {
                        try {
                            AutoFixer.apply(this, project, issue);
                            log.append("NA-AYOS: ").append(issue.title)
                                    .append(" sa ").append(issue.file.getName()).append("\n");
                        } catch (Exception e) {
                            log.append("FAILED: ").append(issue.title)
                                    .append(": ").append(e.getMessage()).append("\n");
                            break;
                        }
                    }
                    log.append("Backup: nasa autofix-backups/")
                            .append(projectName)
                            .append(" — pindutin ang 'Ibalik ang backup' para i-undo.\n");
                }
                log.append("\n--- Re-check pagkatapos ng auto-fix ---\n");
                log.append(BuildValidator.validate(project));
            } catch (Exception e) {
                log.append("Auto-fix failed: ").append(e.getMessage()).append("\n");
            }
            logView.setText(log.toString());
            refreshErrors(project);
            refreshFileList(spinner);
            if (currentFile != null) openFile(currentFile);
        });
        Button restore = findViewById(R.id.btn_restore);
        restore.setOnClickListener(v -> {
            File project = ProjectManager.projectDir(this, projectName);
            try {
                int n = AutoFixer.restore(this, project);
                Toast.makeText(this, n == 0 ? "Walang backup na ibabalik"
                        : "Naibalik ang " + n + " na file", Toast.LENGTH_SHORT).show();
                String log = BuildValidator.validate(project);
                logView.setText(log);
                refreshErrors(project);
                refreshFileList(spinner);
                if (currentFile != null) openFile(currentFile);
            } catch (Exception e) {
                Toast.makeText(this, "Restore failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    /** Errors header + per-file errors panel, grouped like a classic IDE:
     *  file row (icon + 16sp name), then each message indented 40dp at 14sp gray. */
    private void refreshErrors(File project) {
        List<String[]> issues = BuildValidator.fileIssues(project);
        int n = issues.size();
        if (n == 0) {
            errorsHeader.setVisibility(View.GONE);
            errorsPanel.setVisibility(View.GONE);
            errorsPanel.removeAllViews();
            return;
        }
        errorsHeader.setVisibility(View.VISIBLE);
        errorsHeader.setText("\u2715 " + n + (n == 1 ? " error" : " errors"));
        errorsHeader.setTextColor(0xFFD32F2F);
        errorsPanel.setVisibility(View.VISIBLE);
        errorsPanel.removeAllViews();
        // Group messages under each file, in first-seen order.
        List<String> order = new ArrayList<>();
        java.util.Map<String, List<String>> byFile = new java.util.LinkedHashMap<>();
        for (String[] issue : issues) {
            if (!byFile.containsKey(issue[0])) {
                byFile.put(issue[0], new ArrayList<>());
                order.add(issue[0]);
            }
            byFile.get(issue[0]).add(issue[1]);
        }
        float density = getResources().getDisplayMetrics().density;
        int pad20 = Math.round(20 * density);
        int pad10 = Math.round(10 * density);
        int pad4 = Math.round(4 * density);
        int indent40 = Math.round(40 * density);
        for (String fileName : order) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(pad20, pad4, pad10, pad4);
            TextView x = new TextView(this);
            x.setText("\u2715");
            x.setTextColor(0xFFD32F2F);
            x.setTextSize(16);
            x.setPadding(0, 0, Math.round(6 * density), 0);
            row.addView(x);
            TextView file = new TextView(this);
            file.setText(fileName);
            file.setTextSize(16);
            file.setTextColor(0xFF222222);
            row.addView(file);
            row.setOnClickListener(v -> {
                for (File f : files) {
                    if (f.getName().equals(fileName)) {
                        openFile(f);
                        break;
                    }
                }
            });
            errorsPanel.addView(row);
            for (String msgText : byFile.get(fileName)) {
                TextView msg = new TextView(this);
                msg.setText(msgText);
                msg.setTextSize(14);
                msg.setTextColor(0xFF888888);
                msg.setPadding(indent40, pad4, pad10, pad4);
                errorsPanel.addView(msg);
            }
        }
    }

    private void showFindDialog() {
        final EditText input = new EditText(this);
        input.setHint("Hanapin sa file");
        new AlertDialog.Builder(this)
                .setTitle("Find")
                .setView(input)
                .setPositiveButton("Hanapin", (d, w) -> {
                    String query = input.getText().toString();
                    if (query.isEmpty()) return;
                    String text = editor.getText().toString();
                    int from = editor.getSelectionEnd();
                    int found = text.indexOf(query, from);
                    if (found < 0) found = text.indexOf(query);
                    if (found < 0) {
                        Toast.makeText(this, "Hindi mahanap", Toast.LENGTH_SHORT).show();
                    } else {
                        editor.setSelection(found, found + query.length());
                        editor.requestFocus();
                    }
                })
                .setNegativeButton("Kansela", null)
                .show();
    }

    private void updateLineNumbers() {
        int lines = Math.max(1, editor.getLineCount());
        StringBuilder b = new StringBuilder();
        for (int i = 1; i <= lines; i++) b.append(i).append("\n");
        lineNumbers.setText(b.toString());
        lineNumbers.scrollTo(0, editor.getScrollY());
    }

    private boolean codeStyleUseSpaces() {
        return SettingsActivity.toggle(this, "Code Style",
                "Insert spaces instead of tab", true);
    }

    private int codeStyleTabSize() {
        try {
            return Integer.parseInt(SettingsActivity.prefs(this)
                    .getString("Code Style:Tab size", "4"));
        } catch (NumberFormatException e) {
            return 4;
        }
    }

    private void formatCurrentFile() {
        editor.setText(CodeTools.formatJava(editor.getText().toString(),
                codeStyleUseSpaces(), codeStyleTabSize()));
        Toast.makeText(this, "Na-format (I-save para ma-apply)",
                Toast.LENGTH_SHORT).show();
    }

    private void navigateTo(String word) {
        try {
            Object[] def = CodeTools.findDefinition(
                    ProjectManager.projectDir(this, projectName), word);
            if (def == null) {
                Toast.makeText(this, "Walang definition na nakita para sa " + word,
                        Toast.LENGTH_SHORT).show();
                return;
            }
            File f = (File) def[0];
            int offset = (int) def[1];
            int idx = files.indexOf(f);
            if (idx >= 0) spinner.setSelection(idx);
            openFile(f);
            editor.setSelection(Math.min(offset, editor.getText().length()));
            editor.requestFocus();
        } catch (Exception e) {
            Toast.makeText(this, "Navigate failed: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void backupFile(File project, File f) throws Exception {
        String rel = f.getPath().substring(project.getPath().length() + 1);
        File dest = new File(new File(new File(getFilesDir(), "autofix-backups"),
                projectName), rel);
        dest.getParentFile().mkdirs();
        java.io.FileInputStream in = new java.io.FileInputStream(f);
        java.io.FileOutputStream out = new java.io.FileOutputStream(dest);
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        out.close();
    }

    private void showRefactorDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(48, 24, 48, 0);
        final EditText oldInput = new EditText(this);
        oldInput.setHint("Lumang pangalan");
        final EditText newInput = new EditText(this);
        newInput.setHint("Bagong pangalan");
        box.addView(oldInput);
        box.addView(newInput);
        new AlertDialog.Builder(this)
                .setTitle("Refactor — Rename")
                .setView(box)
                .setPositiveButton("Palitan", (d, w) -> {
                    String oldName = oldInput.getText().toString().trim();
                    String newName = newInput.getText().toString().trim();
                    if (oldName.isEmpty() || newName.isEmpty()) return;
                    try {
                        File project = ProjectManager.projectDir(this, projectName);
                        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                                "\\b" + java.util.regex.Pattern.quote(oldName) + "\\b");
                        for (File f : ProjectManager.listFiles(project)) {
                            String name = f.getName();
                            if (!(name.endsWith(".java") || name.endsWith(".xml")
                                    || name.endsWith(".gradle"))) continue;
                            if (p.matcher(ProjectManager.read(f)).find()) backupFile(project, f);
                        }
                        int changed = CodeTools.renameInProject(project, oldName, newName);
                        Toast.makeText(this, changed == 0
                                ? "Walang nahanap na " + oldName
                                : "Napalitan sa " + changed + " na file (backup: Ibalik ang backup)",
                                Toast.LENGTH_SHORT).show();
                        refreshFileList(spinner);
                        if (currentFile != null) openFile(currentFile);
                    } catch (Exception e) {
                        Toast.makeText(this, "Refactor failed: " + e.getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Kansela", null)
                .show();
    }

    private void showCodeDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Code")
                .setItems(new String[]{"Generate getters/setters", "Add import"},
                        (d, which) -> {
                            if (which == 0) {
                                String result = CodeTools.generateGettersSetters(
                                        editor.getText().toString());
                                if (result == null) {
                                    Toast.makeText(this, "Walang private field na kulang ng getter/setter",
                                            Toast.LENGTH_SHORT).show();
                                } else {
                                    editor.setText(result);
                                    Toast.makeText(this, "Na-generate (I-save para ma-apply)",
                                            Toast.LENGTH_SHORT).show();
                                }
                            } else {
                                final EditText input = new EditText(this);
                                input.setHint("hal. java.util.List");
                                new AlertDialog.Builder(this)
                                        .setTitle("Add import")
                                        .setView(input)
                                        .setPositiveButton("Idagdag", (d2, w2) -> {
                                            String imp = input.getText().toString().trim();
                                            if (imp.isEmpty()) return;
                                            String code = editor.getText().toString();
                                            if (code.contains("import " + imp + ";")) {
                                                Toast.makeText(this, "Naka-import na",
                                                        Toast.LENGTH_SHORT).show();
                                                return;
                                            }
                                            int at = code.indexOf(";\n");
                                            String updated;
                                            if (code.startsWith("package ") && at >= 0) {
                                                updated = code.substring(0, at + 2)
                                                        + "import " + imp + ";\n"
                                                        + code.substring(at + 2);
                                            } else {
                                                updated = "import " + imp + ";\n" + code;
                                            }
                                            editor.setText(updated);
                                            Toast.makeText(this, "Naidagdag (I-save para ma-apply)",
                                                    Toast.LENGTH_SHORT).show();
                                        })
                                        .setNegativeButton("Kansela", null)
                                        .show();
                            }
                        })
                .show();
    }

    private void showToolsDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Tools")
                .setItems(new String[]{"Export project as ZIP", "Delete project",
                                "Open sample project"},
                        (d, which) -> {
                            File project = ProjectManager.projectDir(this, projectName);
                            if (which == 0) {
                                try {
                                    File out = new File(new File(getFilesDir(), "exports"),
                                            projectName + ".zip");
                                    CodeTools.exportZip(project, out);
                                    android.net.Uri uri = androidx.core.content.FileProvider
                                            .getUriForFile(this,
                                                    "xyz.devxyz.fcs.fileprovider", out);
                                    Intent share = new Intent(Intent.ACTION_SEND);
                                    share.setType("application/zip");
                                    share.putExtra(Intent.EXTRA_STREAM, uri);
                                    share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                    startActivity(Intent.createChooser(share,
                                            "I-share ang " + projectName + ".zip"));
                                } catch (Exception e) {
                                    Toast.makeText(this, "Export failed: " + e.getMessage(),
                                            Toast.LENGTH_LONG).show();
                                }
                            } else if (which == 1) {
                                new AlertDialog.Builder(this)
                                        .setTitle("Burahin ang project?")
                                        .setMessage("Mabubura ang " + projectName
                                                + " at lahat ng file nito. Hindi ito maibabalik maliban sa backup.")
                                        .setPositiveButton("Burahin", (d2, w2) -> {
                                            CodeTools.deleteRecursive(project);
                                            finish();
                                        })
                                        .setNegativeButton("Kansela", null)
                                        .show();
                            } else {
                                try {
                                    ProjectManager.createProject(this, "SampleApp");
                                } catch (Exception ignored) {
                                    // May SampleApp na — buksan na lang.
                                }
                                Intent i = new Intent(this, EditorActivity.class);
                                i.putExtra("project", "SampleApp");
                                startActivity(i);
                            }
                        })
                .show();
    }

    private void refreshFileList(Spinner spinner) {
        File project = ProjectManager.projectDir(this, projectName);
        files = ProjectManager.listFiles(project);
        List<String> names = new ArrayList<>();
        for (File f : files) {
            String rel = f.getPath().substring(project.getPath().length() + 1);
            names.add(rel);
        }
        ArrayAdapter<String> a = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, names);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(a);
    }

    private void openFile(File f) {
        currentFile = f;
        try {
            String code = ProjectManager.read(f);
            if (!formattedOnOpen && f.getName().endsWith(".java")
                    && SettingsActivity.toggle(this, "Code Style",
                            "Format project when opening a project", false)) {
                formattedOnOpen = true;
                code = CodeTools.formatJava(code, codeStyleUseSpaces(), codeStyleTabSize());
            }
            editor.setText(code);
            updateLineNumbers();
            if (SettingsActivity.toggle(this, "Editor",
                    "Show the soft keyboard when opening code files", true)) {
                editor.requestFocus();
                InputMethodManager imm = (InputMethodManager)
                        getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(editor, 0);
            }
        } catch (Exception e) {
            editor.setText("");
            Toast.makeText(this, "Hindi mabuksan: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
