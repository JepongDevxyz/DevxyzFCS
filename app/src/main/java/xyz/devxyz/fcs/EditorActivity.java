package xyz.devxyz.fcs;

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class EditorActivity extends AppCompatActivity {

    private String projectName;
    private List<File> files = new ArrayList<>();
    private File currentFile;
    private EditText editor;
    private TextView logView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editor);
        projectName = getIntent().getStringExtra("project");
        setTitle("DevxyzFCS - " + projectName);
        editor = findViewById(R.id.editor);
        logView = findViewById(R.id.build_log);
        Spinner spinner = findViewById(R.id.file_spinner);
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
            logView.setText(BuildValidator.validate(project));
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
                logView.setText(BuildValidator.validate(project));
                refreshFileList(spinner);
                if (currentFile != null) openFile(currentFile);
            } catch (Exception e) {
                Toast.makeText(this, "Restore failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
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
            editor.setText(ProjectManager.read(f));
        } catch (Exception e) {
            editor.setText("");
            Toast.makeText(this, "Hindi mabuksan: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
