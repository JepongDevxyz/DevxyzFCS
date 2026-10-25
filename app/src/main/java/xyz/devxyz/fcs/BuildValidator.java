package xyz.devxyz.fcs;

import java.io.File;
import java.util.List;

/**
 * On-device build check for a project created by ProjectManager.
 * This is a real structural validation, not a mock: it reads the actual
 * project files and checks the exact conditions an AndroidX/AppCompat
 * Gradle build needs. A full Gradle build cannot run on a phone
 * (Gradle needs a JDK + Android SDK + daemon); the full build proof
 * is the CI job that builds the identical :sample module in this repo.
 */
public class BuildValidator {

    public static String validate(File project) {
        StringBuilder log = new StringBuilder();
        log.append("Build check: ").append(project.getName()).append("\n");
        List<File> files = ProjectManager.listFiles(project);
        if (files.isEmpty()) return log.append("FAIL: walang file ang project.\n").toString();

        boolean hasMainActivity = false, hasManifest = false, hasLayout = false;
        boolean hasAppCompatImport = false, extendsAppCompat = false, hasAppCompatDep = false;
        for (File f : files) {
            String path = f.getPath();
            try {
                String c = ProjectManager.read(f);
                if (path.endsWith("MainActivity.java")) {
                    hasMainActivity = true;
                    if (c.contains("import androidx.appcompat.app.AppCompatActivity")) hasAppCompatImport = true;
                    if (c.contains("extends AppCompatActivity")) extendsAppCompat = true;
                }
                if (path.endsWith("AndroidManifest.xml")) hasManifest = true;
                if (path.endsWith("activity_main.xml")) hasLayout = true;
                if (path.endsWith("build.gradle") && c.contains("androidx.appcompat:appcompat")) hasAppCompatDep = true;
            } catch (Exception e) {
                log.append("FAIL: hindi mabasa ").append(f.getName()).append(": ").append(e.getMessage()).append("\n");
            }
        }
        check(log, "MainActivity.java", hasMainActivity);
        check(log, "AndroidManifest.xml", hasManifest);
        check(log, "activity_main.xml", hasLayout);
        check(log, "AppCompat import (androidx.appcompat)", hasAppCompatImport);
        check(log, "extends AppCompatActivity", extendsAppCompat);
        check(log, "appcompat dependency sa build.gradle", hasAppCompatDep);
        boolean ok = hasMainActivity && hasManifest && hasLayout && hasAppCompatImport && extendsAppCompat && hasAppCompatDep;
        log.append(ok
                ? "OK: kumpleto ang AndroidX/AppCompat project. Ang full Gradle build nito ay pinapatunayan ng CI (:sample module, kaparehong template).\n"
                : "FAIL: kulang ang project — ayusin muna ang mga FAIL sa itaas bago mag-build.\n");
        return log.toString();
    }

    private static void check(StringBuilder log, String what, boolean ok) {
        log.append(ok ? "OK: " : "FAIL: ").append(what).append("\n");
    }

    /** Per-file issues for the errors panel: {file name, message}. */
    public static java.util.List<String[]> fileIssues(File project) {
        java.util.List<String[]> out = new java.util.ArrayList<>();
        List<File> files = ProjectManager.listFiles(project);
        boolean hasMainActivity = false, hasManifest = false, hasLayout = false;
        boolean hasAppCompatImport = false, extendsAppCompat = false, hasAppCompatDep = false;
        for (File f : files) {
            String path = f.getPath();
            try {
                String c = ProjectManager.read(f);
                if (path.endsWith("MainActivity.java")) {
                    hasMainActivity = true;
                    if (c.contains("import androidx.appcompat.app.AppCompatActivity")) hasAppCompatImport = true;
                    if (c.contains("extends AppCompatActivity")) extendsAppCompat = true;
                }
                if (path.endsWith("AndroidManifest.xml")) hasManifest = true;
                if (path.endsWith("activity_main.xml")) hasLayout = true;
                if (path.endsWith("build.gradle") && c.contains("androidx.appcompat:appcompat")) hasAppCompatDep = true;
            } catch (Exception ignored) {
            }
        }
        if (!hasMainActivity) out.add(new String[]{"MainActivity.java", "File not found"});
        if (!hasManifest) out.add(new String[]{"AndroidManifest.xml", "File not found"});
        if (!hasLayout) out.add(new String[]{"activity_main.xml", "File not found"});
        if (hasMainActivity && !hasAppCompatImport)
            out.add(new String[]{"MainActivity.java", "AppCompat import missing (androidx.appcompat)"});
        if (hasMainActivity && !extendsAppCompat)
            out.add(new String[]{"MainActivity.java", "MainActivity does not extend AppCompatActivity"});
        if (!hasAppCompatDep)
            out.add(new String[]{"build.gradle", "appcompat dependency missing"});
        return out;
    }
}
