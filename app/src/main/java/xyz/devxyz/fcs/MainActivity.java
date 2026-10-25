package xyz.devxyz.fcs;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.navigation.NavigationView;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private ProjectAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setSupportActionBar(findViewById(R.id.toolbar));
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
            if (id == R.id.nav_settings) {
                startActivity(new Intent(this, SettingsActivity.class));
            } else if (id == R.id.nav_exit) {
                finish();
            } else {
                android.widget.Toast.makeText(this,
                        "Susuportahan sa susunod na phase", android.widget.Toast.LENGTH_SHORT).show();
            }
            return true;
        });
        TextView breadcrumb = findViewById(R.id.breadcrumb);
        breadcrumb.setText(ProjectManager.projectsDir(this).getPath());
        RecyclerView list = findViewById(R.id.project_list);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ProjectAdapter(ProjectManager.listProjects(this));
        list.setAdapter(adapter);
        findViewById(R.id.row_create).setOnClickListener(v -> showNewProjectDialog());
        findViewById(R.id.row_clone).setOnClickListener(v -> showCloneDialog());
    }

    @Override
    protected void onResume() {
        super.onResume();
        adapter.setProjects(ProjectManager.listProjects(this));
    }

    private void showNewProjectDialog() {
        final EditText input = new EditText(this);
        input.setHint("Pangalan ng project (hal. MyApp)");
        new AlertDialog.Builder(this)
                .setTitle("Bagong AndroidX Project")
                .setView(input)
                .setPositiveButton("Gumawa", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) return;
                    try {
                        ProjectManager.createProject(this, name);
                        adapter.setProjects(ProjectManager.listProjects(this));
                        openEditor(name);
                    } catch (Exception e) {
                        new AlertDialog.Builder(this)
                                .setTitle("Hindi nagawa")
                                .setMessage(e.getMessage())
                                .setPositiveButton("OK", null)
                                .show();
                    }
                })
                .setNegativeButton("Kansela", null)
                .show();
    }

    private void showCloneDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(48, 24, 48, 0);
        final EditText urlInput = new EditText(this);
        urlInput.setHint("https://github.com/owner/repo");
        final EditText nameInput = new EditText(this);
        nameInput.setHint("Pangalan ng project");
        TextView note = new TextView(this);
        note.setText("GitHub URLs lang sa ngayon - dina-download ang repo "
                + "bilang zip (main, tapos master kung wala).");
        note.setTextColor(0xFF757575);
        box.addView(urlInput);
        box.addView(nameInput);
        box.addView(note);
        new AlertDialog.Builder(this)
                .setTitle("Clone Git repository...")
                .setView(box)
                .setPositiveButton("Clone", (d, w) -> {
                    String url = urlInput.getText().toString().trim();
                    String name = nameInput.getText().toString().trim();
                    if (url.isEmpty() || name.isEmpty()) return;
                    Toast.makeText(this, "Kino-clone ang " + name + "...",
                            Toast.LENGTH_SHORT).show();
                    new Thread(() -> {
                        try {
                            CodeTools.cloneGitHub(url, name,
                                    ProjectManager.projectsDir(MainActivity.this));
                            runOnUiThread(() -> {
                                adapter.setProjects(ProjectManager.listProjects(this));
                                openEditor(name);
                            });
                        } catch (Exception e) {
                            runOnUiThread(() -> new AlertDialog.Builder(this)
                                    .setTitle("Hindi na-clone")
                                    .setMessage(e.getMessage())
                                    .setPositiveButton("OK", null)
                                    .show());
                        }
                    }).start();
                })
                .setNegativeButton("Kansela", null)
                .show();
    }

    private void openEditor(String name) {
        Intent i = new Intent(this, EditorActivity.class);
        i.putExtra("project", name);
        startActivity(i);
    }

    private class ProjectAdapter extends RecyclerView.Adapter<ProjectAdapter.Holder> {
        private List<String> projects;

        ProjectAdapter(List<String> projects) { this.projects = projects; }

        void setProjects(List<String> projects) {
            this.projects = projects;
            notifyDataSetChanged();
        }

        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_project, parent, false);
            return new Holder(v);
        }

        @Override
        public void onBindViewHolder(Holder h, int pos) {
            String name = projects.get(pos);
            h.name.setText(name);
            int n = ProjectManager.listFiles(ProjectManager.projectDir(
                    MainActivity.this, name)).size();
            h.files.setText(n + (n == 1 ? " file" : " files"));
            h.itemView.setOnClickListener(v -> openEditor(name));
        }

        @Override
        public int getItemCount() { return projects.size(); }

        class Holder extends RecyclerView.ViewHolder {
            TextView name, files;
            Holder(View v) {
                super(v);
                name = v.findViewById(R.id.project_name);
                files = v.findViewById(R.id.project_files);
            }
        }
    }
}
