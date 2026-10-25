package xyz.devxyz.fcs;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Creates and manages on-device IDE projects under the app's files dir:
 * files/projects/&lt;name&gt;/ with a real AndroidX AppCompat template.
 * The template files here are byte-identical in structure to the :sample
 * module in this repo, which CI builds to prove AndroidX/AppCompat compiles.
 */
public class ProjectManager {

    public static File projectsDir(android.content.Context ctx) {
        File d = new File(ctx.getFilesDir(), "projects");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    public static List<String> listProjects(android.content.Context ctx) {
        List<String> out = new ArrayList<>();
        File[] dirs = projectsDir(ctx).listFiles();
        if (dirs != null) {
            for (File f : dirs) if (f.isDirectory()) out.add(f.getName());
        }
        java.util.Collections.sort(out);
        return out;
    }

    public static File projectDir(android.content.Context ctx, String name) {
        return new File(projectsDir(ctx), name);
    }

    public static List<File> listFiles(File project) {
        List<File> out = new ArrayList<>();
        collect(project, out);
        java.util.Collections.sort(out, (a, b) -> a.getPath().compareTo(b.getPath()));
        return out;
    }

    private static void collect(File dir, List<File> out) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) collect(f, out);
            else out.add(f);
        }
    }

    public static String read(File f) throws IOException {
        FileInputStream in = new FileInputStream(f);
        byte[] buf = new byte[(int) f.length()];
        int n = in.read(buf);
        in.close();
        return new String(buf, 0, Math.max(n, 0), StandardCharsets.UTF_8);
    }

    public static void write(File f, String content) throws IOException {
        if (f.getParentFile() != null) f.getParentFile().mkdirs();
        FileOutputStream out = new FileOutputStream(f);
        out.write(content.getBytes(StandardCharsets.UTF_8));
        out.close();
    }

    /** Creates a new AndroidX AppCompat project from the built-in template. */
    public static File createProject(android.content.Context ctx, String name) throws IOException {
        File p = projectDir(ctx, name);
        if (p.exists()) throw new IOException("May project na na ganito ang pangalan: " + name);
        String pkg = "com.example." + name.toLowerCase().replaceAll("[^a-z0-9]", "");
        write(new File(p, "app/src/main/java/" + pkg.replace('.', '/') + "/MainActivity.java"),
                "package " + pkg + ";\n\n"
                + "import android.os.Bundle;\n"
                + "import androidx.appcompat.app.AppCompatActivity;\n\n"
                + "public class MainActivity extends AppCompatActivity {\n"
                + "    @Override\n"
                + "    protected void onCreate(Bundle savedInstanceState) {\n"
                + "        super.onCreate(savedInstanceState);\n"
                + "        setContentView(R.layout.activity_main);\n"
                + "    }\n"
                + "}\n");
        write(new File(p, "app/src/main/res/layout/activity_main.xml"),
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    android:layout_width=\"match_parent\"\n"
                + "    android:layout_height=\"match_parent\"\n"
                + "    android:gravity=\"center\"\n"
                + "    android:orientation=\"vertical\">\n\n"
                + "    <TextView\n"
                + "        android:layout_width=\"wrap_content\"\n"
                + "        android:layout_height=\"wrap_content\"\n"
                + "        android:text=\"Hello AndroidX!\" />\n"
                + "</LinearLayout>\n");
        write(new File(p, "app/src/main/AndroidManifest.xml"),
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">\n"
                + "    <application android:label=\"" + name + "\"\n"
                + "        android:theme=\"@style/Theme.AppCompat.Light.DarkActionBar\">\n"
                + "        <activity android:name=\".MainActivity\" android:exported=\"true\">\n"
                + "            <intent-filter>\n"
                + "                <action android:name=\"android.intent.action.MAIN\" />\n"
                + "                <category android:name=\"android.intent.category.LAUNCHER\" />\n"
                + "            </intent-filter>\n"
                + "        </activity>\n"
                + "    </application>\n"
                + "</manifest>\n");
        write(new File(p, "app/build.gradle"),
                "plugins { id 'com.android.application' }\n"
                + "android {\n"
                + "    namespace \"" + pkg + "\"\n"
                + "    compileSdkVersion 33\n"
                + "    defaultConfig {\n"
                + "        applicationId \"" + pkg + "\"\n"
                + "        minSdkVersion 21\n"
                + "        targetSdkVersion 33\n"
                + "        versionCode 1\n"
                + "        versionName \"1.0\"\n"
                + "    }\n"
                + "}\n"
                + "dependencies {\n"
                + "    implementation 'androidx.appcompat:appcompat:1.4.2'\n"
                + "    implementation 'com.google.android.material:material:1.6.1'\n"
                + "}\n");
        return p;
    }
}
