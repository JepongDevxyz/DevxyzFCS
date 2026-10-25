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
