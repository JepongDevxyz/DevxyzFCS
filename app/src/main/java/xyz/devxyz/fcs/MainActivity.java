package xyz.devxyz.fcs;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private ProjectAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        RecyclerView list = findViewById(R.id.project_list);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ProjectAdapter(ProjectManager.listProjects(this));
        list.setAdapter(adapter);
        FloatingActionButton fab = findViewById(R.id.fab_new);
        fab.setOnClickListener(v -> showNewProjectDialog());
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
            h.itemView.setOnClickListener(v -> openEditor(name));
        }

        @Override
        public int getItemCount() { return projects.size(); }

        class Holder extends RecyclerView.ViewHolder {
            TextView name;
            Holder(View v) {
                super(v);
                name = v.findViewById(R.id.project_name);
            }
        }
    }
}
