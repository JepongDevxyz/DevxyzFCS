package xyz.devxyz.fcs;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Auto-fix for the structural errors the on-device Build Check can detect.
 *
 * Scope is deliberately narrow and honest: only fixes whose correctness can
 * be re-verified by re-running BuildValidator are applied automatically.
 * Every fix is previewed in the log before it is applied, the original file
 * is backed up under files/autofix-backups/&lt;project&gt;/ (outside the
 * project tree, so the file spinner never shows backups), and the user can
 * restore everything with one tap. Arbitrary Java logic errors are NOT
 * auto-fixed: a wrong guess there would break code instead of fixing it.
 *
 * Fix rules (v1):
 *  - R_IMPORT / BUILDCONFIG_IMPORT: an import of some other package's R or
 *    BuildConfig (the TriNet case: com.test.version.R) is rewritten to the
 *    project's own namespace, where AGP actually generates those classes.
 *  - MISSING_R_IMPORT / MISSING_BUILDCONFIG_IMPORT: code uses R. or
 *    BuildConfig. with no import at all; the namespace import is inserted.
 *  - APPCOMPAT_IMPORT / APPCOMPAT_EXTENDS: MainActivity gains the
 *    AppCompat import and "extends AppCompatActivity".
 *  - APPCOMPAT_DEP: the appcompat dependency is added to build.gradle.
 *  - DEAD_JBCRYPT: the dead JCenter artifact
 *    org.connectbot.jbcrypt:jbcrypt:1.0.0 is removed from build.gradle, its
 *    imports are rewritten to the vendored BCrypt, and the vendored
 *    BCrypt.java (BSD mindrot license, bundled in app assets) is copied
 *    into the project. This is the exact fix proven by the DevxyzVPN and
 *    :trinet CI builds.
 *  - DEAD_SERVICE: a manifest &lt;service&gt; whose class has no .java file
 *    anywhere in the project (the TriNet ProxyService case) is removed.
 */
public class AutoFixer {

    public static class Issue {
        public final String kind;
        public final File file;
        public final String title;
        public final String detail;

        public Issue(String kind, File file, String title, String detail) {
            this.kind = kind;
            this.file = file;
            this.title = title;
            this.detail = detail;
        }
    }

    private static final Pattern IMPORT_R =
            Pattern.compile("import\\s+([A-Za-z0-9_\\.]+)\\.R;");
    private static final Pattern IMPORT_BUILDCONFIG =
            Pattern.compile("import\\s+([A-Za-z0-9_\\.]+)\\.BuildConfig;");
    private static final Pattern PACKAGE_LINE =
            Pattern.compile("package\\s+([A-Za-z0-9_\\.]+);");
    private static final Pattern SERVICE_BLOCK = Pattern.compile(
            "<service\\b[^>]*?(?:/>|>.*?</service>)", Pattern.DOTALL);
    private static final Pattern SERVICE_NAME = Pattern.compile(
            "android:name=\"([^\"]+)\"");

    /** Finds every auto-fixable issue in the project, in file order. */
    public static List<Issue> detect(File project) throws IOException {
        List<Issue> out = new ArrayList<>();
        List<File> files = ProjectManager.listFiles(project);
        List<File> javaFiles = new ArrayList<>();
        File manifest = null, gradle = null, mainActivity = null;
        for (File f : files) {
            String p = f.getPath();
            if (p.endsWith(".java")) javaFiles.add(f);
            if (p.endsWith("AndroidManifest.xml")) manifest = f;
            if (p.endsWith("build.gradle")) gradle = f;
            if (p.endsWith("MainActivity.java")) mainActivity = f;
        }
        String pkg = projectPackage(mainActivity, gradle);

        for (File f : javaFiles) {
            String c = ProjectManager.read(f);
            Matcher m = IMPORT_R.matcher(c);
            while (m.find()) {
                if (!pkg.isEmpty() && !m.group(1).equals(pkg)) {
                    out.add(new Issue("R_IMPORT", f, "Maling R import",
                            m.group(1) + ".R -> " + pkg + ".R"));
                }
            }
            Matcher b = IMPORT_BUILDCONFIG.matcher(c);
            while (b.find()) {
                if (!pkg.isEmpty() && !b.group(1).equals(pkg)) {
                    out.add(new Issue("BUILDCONFIG_IMPORT", f, "Maling BuildConfig import",
                            b.group(1) + ".BuildConfig -> " + pkg + ".BuildConfig"));
                }
            }
            boolean hasRImport = IMPORT_R.matcher(c).find();
            boolean hasBcImport = IMPORT_BUILDCONFIG.matcher(c).find();
            if (!pkg.isEmpty() && !hasRImport
                    && Pattern.compile("\\bR\\.").matcher(c).find()) {
                out.add(new Issue("MISSING_R_IMPORT", f, "Kulang ng R import",
                        "Idadagdag: import " + pkg + ".R;"));
            }
            if (!pkg.isEmpty() && !hasBcImport
                    && Pattern.compile("\\bBuildConfig\\.").matcher(c).find()) {
                out.add(new Issue("MISSING_BUILDCONFIG_IMPORT", f, "Kulang ng BuildConfig import",
                        "Idadagdag: import " + pkg + ".BuildConfig;"));
            }
        }

        if (mainActivity != null) {
            String c = ProjectManager.read(mainActivity);
            if (!c.contains("import androidx.appcompat.app.AppCompatActivity")) {
                out.add(new Issue("APPCOMPAT_IMPORT", mainActivity,
                        "Kulang ng AppCompat import",
                        "Idadagdag: import androidx.appcompat.app.AppCompatActivity;"));
            }
            if (Pattern.compile("extends\\s+Activity\\b").matcher(c).find()
                    && !c.contains("extends AppCompatActivity")) {
                out.add(new Issue("APPCOMPAT_EXTENDS", mainActivity,
                        "Hindi naka-AppCompatActivity",
                        "extends Activity -> extends AppCompatActivity"));
            }
        }

        if (gradle != null) {
            String c = ProjectManager.read(gradle);
            if (!c.contains("androidx.appcompat:appcompat")) {
                out.add(new Issue("APPCOMPAT_DEP", gradle,
                        "Kulang ng appcompat dependency",
                        "Idadagdag: implementation 'androidx.appcompat:appcompat:1.4.2'"));
            }
            if (c.contains("org.connectbot.jbcrypt:jbcrypt:1.0.0")) {
                out.add(new Issue("DEAD_JBCRYPT", gradle,
                        "Patay na jbcrypt dependency (JCenter)",
                        "Tatanggalin ang linya; BCrypt imports -> vendored BCrypt"));
            }
        }

        if (manifest != null) {
            String c = ProjectManager.read(manifest);
            Matcher s = SERVICE_BLOCK.matcher(c);
            while (s.find()) {
                String block = s.group();
                Matcher n = SERVICE_NAME.matcher(block);
                if (!n.find()) continue;
                String name = n.group(1);
                String simple = name.substring(name.lastIndexOf('.') + 1);
                boolean exists = false;
                for (File jf : javaFiles) {
                    if (jf.getName().equals(simple + ".java")) { exists = true; break; }
                }
                if (!exists) {
                    out.add(new Issue("DEAD_SERVICE", manifest,
                            "Service na walang class: " + name,
                            "Tatanggalin ang <service> block para sa " + name));
                }
            }
        }
        return out;
    }

    /** Applies one issue's fix, backing the original up first. */
    public static void apply(Context ctx, File project, Issue issue) throws IOException {
        backup(ctx, project, issue.file);
        String c = ProjectManager.read(issue.file);
        switch (issue.kind) {
            case "R_IMPORT": {
                Matcher m = IMPORT_R.matcher(c);
                if (m.find()) {
                    c = c.substring(0, m.start(1)) + projectPackageFor(project)
                            + c.substring(m.end(1));
                }
                break;
            }
            case "BUILDCONFIG_IMPORT": {
                Matcher m = IMPORT_BUILDCONFIG.matcher(c);
                if (m.find()) {
                    c = c.substring(0, m.start(1)) + projectPackageFor(project)
                            + c.substring(m.end(1));
                }
                break;
            }
            case "MISSING_R_IMPORT":
                c = insertImport(c, "import " + projectPackageFor(project) + ".R;");
                break;
            case "MISSING_BUILDCONFIG_IMPORT":
                c = insertImport(c, "import " + projectPackageFor(project) + ".BuildConfig;");
                break;
            case "APPCOMPAT_IMPORT":
                c = insertImport(c, "import androidx.appcompat.app.AppCompatActivity;");
                break;
            case "APPCOMPAT_EXTENDS":
                c = c.replace("extends Activity", "extends AppCompatActivity");
                break;
            case "APPCOMPAT_DEP":
                c = c.replaceFirst("dependencies\\s*\\{",
                        "dependencies {\n    implementation 'androidx.appcompat:appcompat:1.4.2'");
                break;
            case "DEAD_JBCRYPT": {
                StringBuilder kept = new StringBuilder();
                for (String line : c.split("\n", -1)) {
                    if (!line.contains("org.connectbot.jbcrypt:jbcrypt:1.0.0")) {
                        kept.append(line).append("\n");
                    }
                }
                c = kept.toString();
                // Rewrite BCrypt imports in every java file, then vendor BCrypt.java.
                for (File jf : ProjectManager.listFiles(project)) {
                    if (!jf.getPath().endsWith(".java")) continue;
                    String jc = ProjectManager.read(jf);
                    if (jc.contains("import org.mindrot.jbcrypt.BCrypt;")) {
                        backup(ctx, project, jf);
                        ProjectManager.write(jf, jc.replace(
                                "import org.mindrot.jbcrypt.BCrypt;",
                                "import com.trilead.ssh2.signature.BCrypt;"));
                    }
                }
                copyBundledBCrypt(ctx, project);
                break;
            }
            case "DEAD_SERVICE": {
                Matcher s = SERVICE_BLOCK.matcher(c);
                while (s.find()) {
                    Matcher n = SERVICE_NAME.matcher(s.group());
                    if (n.find() && issue.detail.contains(n.group(1))) {
                        c = c.substring(0, s.start()) + c.substring(s.end());
                        break;
                    }
                }
                break;
            }
            default:
                throw new IOException("Hindi kilalang fix: " + issue.kind);
        }
        ProjectManager.write(issue.file, c);
    }

    /** Restores every backed-up file for this project. Returns files restored. */
    public static int restore(Context ctx, File project) throws IOException {
        File root = backupRoot(ctx, project);
        if (!root.exists()) return 0;
        List<File> backs = ProjectManager.listFiles(root);
        int n = 0;
        for (File b : backs) {
            String rel = b.getPath().substring(root.getPath().length() + 1);
            File target = new File(project, rel);
            ProjectManager.write(target, ProjectManager.read(b));
            b.delete();
            n++;
        }
        deleteEmptyDirs(root);
        return n;
    }

    // ---- helpers ----

    private static String projectPackage(File mainActivity, File gradle) throws IOException {
        if (mainActivity != null) {
            Matcher m = PACKAGE_LINE.matcher(ProjectManager.read(mainActivity));
            if (m.find()) return m.group(1);
        }
        if (gradle != null) {
            Matcher m = Pattern.compile("namespace\\s+\"([^\"]+)\"")
                    .matcher(ProjectManager.read(gradle));
            if (m.find()) return m.group(1);
        }
        return "";
    }

    private static String projectPackageFor(File project) throws IOException {
        File mainActivity = null, gradle = null;
        for (File f : ProjectManager.listFiles(project)) {
            if (f.getPath().endsWith("MainActivity.java")) mainActivity = f;
            if (f.getPath().endsWith("build.gradle")) gradle = f;
        }
        return projectPackage(mainActivity, gradle);
    }

    private static String insertImport(String code, String imp) {
        Matcher m = PACKAGE_LINE.matcher(code);
        if (m.find()) {
            return code.substring(0, m.end()) + "\n" + imp + code.substring(m.end());
        }
        return imp + "\n" + code;
    }

    private static File backupRoot(Context ctx, File project) {
        return new File(new File(ctx.getFilesDir(), "autofix-backups"), project.getName());
    }

    private static void backup(Context ctx, File project, File f) throws IOException {
        String rel = f.getPath().substring(project.getPath().length() + 1);
        File dest = new File(backupRoot(ctx, project), rel);
        if (dest.exists()) return; // keep the pre-batch original, not intermediates
        ProjectManager.write(dest, ProjectManager.read(f));
    }

    private static void copyBundledBCrypt(Context ctx, File project) throws IOException {
        String javaRoot = null;
        for (File f : ProjectManager.listFiles(project)) {
            String p = f.getPath();
            if (p.endsWith("MainActivity.java") && p.contains("/java/")) {
                javaRoot = p.substring(0, p.indexOf("/java/") + "/java/".length());
                break;
            }
        }
        if (javaRoot == null) return;
        File dest = new File(javaRoot, "com/trilead/ssh2/signature/BCrypt.java");
        if (dest.exists()) return;
        InputStream in = ctx.getAssets().open("bcrypt/BCrypt.java");
        byte[] buf = new byte[4096];
        StringBuilder sb = new StringBuilder();
        int n;
        while ((n = in.read(buf)) != -1) {
            sb.append(new String(buf, 0, n, StandardCharsets.UTF_8));
        }
        in.close();
        ProjectManager.write(dest, sb.toString());
    }

    private static void deleteEmptyDirs(File dir) {
        File[] kids = dir.listFiles();
        if (kids == null) return;
        for (File k : kids) if (k.isDirectory()) deleteEmptyDirs(k);
        if (dir.listFiles() != null && dir.listFiles().length == 0) dir.delete();
    }
}
