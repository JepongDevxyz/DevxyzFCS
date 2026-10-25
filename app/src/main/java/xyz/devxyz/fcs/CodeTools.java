package xyz.devxyz.fcs;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Pure code tools behind the drawer items (Navigate Mode, Refactor, Code,
 * Format, Tools). No Android classes here on purpose: every method below
 * is unit-testable on a plain JVM, and the editor only wires them to UI.
 */
public class CodeTools {

    /** Brace-depth formatter. Indent unit comes from the Code Style settings. */
    public static String formatJava(String code, boolean useSpaces, int tabSize) {
        String unit = useSpaces ? repeat(" ", Math.max(1, tabSize)) : "\t";
        StringBuilder out = new StringBuilder();
        int depth = 0;
        for (String raw : code.split("\n", -1)) {
            String line = raw.trim();
            if (line.isEmpty()) {
                out.append("\n");
                continue;
            }
            String countSource = stripStrings(line);
            int opens = count(countSource, '{');
            int closes = count(countSource, '}');
            int indent = depth;
            if (line.startsWith("}")) indent = Math.max(0, depth - 1);
            for (int i = 0; i < indent; i++) out.append(unit);
            out.append(line).append("\n");
            depth = Math.max(0, depth + opens - closes);
        }
        return out.toString();
    }

    private static String stripStrings(String s) {
        return s.replaceAll("\"(?:\\\\.|[^\"\\\\])*\"", "\"\"")
                .replaceAll("'(?:\\\\.|[^'\\\\])*'", "''");
    }

    private static int count(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == c) n++;
        return n;
    }

    private static String repeat(String s, int n) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n; i++) b.append(s);
        return b.toString();
    }

    /** Word-boundary rename across .java/.xml/.gradle files. Returns files changed. */
    public static int renameInProject(File project, String oldName, String newName) throws IOException {
        Pattern p = Pattern.compile("\\b" + Pattern.quote(oldName) + "\\b");
        int changed = 0;
        for (File f : ProjectManager.listFiles(project)) {
            String name = f.getName();
            if (!(name.endsWith(".java") || name.endsWith(".xml") || name.endsWith(".gradle"))) continue;
            String code = ProjectManager.read(f);
            String updated = p.matcher(code).replaceAll(Matcher.quoteReplacement(newName));
            if (!updated.equals(code)) {
                ProjectManager.write(f, updated);
                changed++;
            }
        }
        return changed;
    }

    private static final Pattern FIELD =
            Pattern.compile("private\\s+([\\w<>\\[\\],\\s]+?)\\s+(\\w+)\\s*;");

    /** Generates getters/setters for private fields that lack them. Null if none. */
    public static String generateGettersSetters(String code) {
        Matcher m = FIELD.matcher(code);
        List<String[]> fields = new ArrayList<>();
        while (m.find()) fields.add(new String[]{m.group(1).trim(), m.group(2)});
        if (fields.isEmpty()) return null;
        StringBuilder methods = new StringBuilder();
        for (String[] f : fields) {
            String type = f[0], name = f[1];
            String cap = Character.toUpperCase(name.charAt(0)) + name.substring(1);
            String getter = (type.equals("boolean") ? "is" : "get") + cap;
            if (!code.contains(getter + "(")) {
                methods.append("\n    public ").append(type).append(" ").append(getter)
                        .append("() {\n        return ").append(name).append(";\n    }\n");
            }
            if (!code.contains("set" + cap + "(")) {
                methods.append("\n    public void set").append(cap).append("(").append(type)
                        .append(" ").append(name).append(") {\n        this.").append(name)
                        .append(" = ").append(name).append(";\n    }\n");
            }
        }
        if (methods.length() == 0) return null;
        int last = code.lastIndexOf('}');
        if (last < 0) return null;
        return code.substring(0, last) + methods + "}\n";
    }

    /** Finds a definition of word in the project. Returns {file, char offset} or null. */
    public static Object[] findDefinition(File project, String word) throws IOException {
        Pattern typeDef = Pattern.compile("\\b(class|interface|enum)\\s+" + Pattern.quote(word) + "\\b");
        Pattern methodDef = Pattern.compile("\\b" + Pattern.quote(word) + "\\s*\\(");
        for (File f : ProjectManager.listFiles(project)) {
            if (!f.getName().endsWith(".java")) continue;
            String code = ProjectManager.read(f);
            Matcher m = typeDef.matcher(code);
            if (m.find()) return new Object[]{f, m.start()};
            m = methodDef.matcher(code);
            if (m.find()) return new Object[]{f, m.start()};
        }
        return null;
    }

    /** Zips a whole project directory (relative paths) into out. */
    public static void exportZip(File project, File out) throws IOException {
        if (out.getParentFile() != null) out.getParentFile().mkdirs();
        ZipOutputStream z = new ZipOutputStream(new FileOutputStream(out));
        for (File f : ProjectManager.listFiles(project)) {
            String rel = f.getPath().substring(project.getPath().length() + 1);
            z.putNextEntry(new ZipEntry(rel));
            FileInputStream in = new FileInputStream(f);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) z.write(buf, 0, n);
            in.close();
            z.closeEntry();
        }
        z.close();
    }

    /**
     * Clones a GitHub repo by downloading its zipball (main, then master)
     * and unzipping it into projectsDir/name, stripping the top folder.
     * GitHub URLs only - a plain git clone needs a git binary the phone
     * does not have, so this is the honest on-device equivalent.
     */
    public static File cloneGitHub(String url, String name, File projectsDir) throws IOException {
        Matcher m = Pattern.compile(
                "^https://github\\.com/([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+?)(?:\\.git)?/?$")
                .matcher(url.trim());
        if (!m.matches()) {
            throw new IOException("GitHub URLs lang ang suportado: https://github.com/owner/repo");
        }
        String owner = m.group(1), repo = m.group(2);
        File dest = new File(projectsDir, name);
        if (dest.exists()) throw new IOException("May project na na ganito ang pangalan: " + name);
        IOException lastError = null;
        for (String branch : new String[]{"main", "master"}) {
            try {
                downloadAndUnzip("https://codeload.github.com/" + owner + "/" + repo
                        + "/zip/refs/heads/" + branch, dest);
                return dest;
            } catch (IOException e) {
                lastError = e;
                deleteRecursive(dest);
            }
        }
        throw new IOException("Hindi ma-download ang repo ("
                + (lastError != null ? lastError.getMessage() : "unknown") + ")");
    }

    private static void downloadAndUnzip(String zipUrl, File dest) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(zipUrl).openConnection();
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(60000);
        conn.setRequestProperty("User-Agent", "DevxyzFCS");
        if (conn.getResponseCode() != 200) {
            throw new IOException("HTTP " + conn.getResponseCode() + " mula sa " + zipUrl);
        }
        InputStream in = new BufferedInputStream(conn.getInputStream());
        ZipInputStream z = new ZipInputStream(in);
        ZipEntry e;
        boolean any = false;
        while ((e = z.getNextEntry()) != null) {
            String path = e.getName();
            int slash = path.indexOf('/');
            if (slash < 0) continue;
            String rel = path.substring(slash + 1);
            if (rel.isEmpty()) continue;
            File out = new File(dest, rel);
            if (e.isDirectory()) {
                out.mkdirs();
            } else {
                out.getParentFile().mkdirs();
                FileOutputStream o = new FileOutputStream(out);
                byte[] buf = new byte[8192];
                int n;
                while ((n = z.read(buf)) > 0) o.write(buf, 0, n);
                o.close();
                any = true;
            }
            z.closeEntry();
        }
        z.close();
        if (!any) throw new IOException("Walang laman ang na-download na zip");
    }

    public static void deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteRecursive(k);
        }
        f.delete();
    }

    /** Word characters around offset in text, or null when there is no word. */
    public static String wordAt(String text, int offset) {
        if (text == null || text.isEmpty()) return null;
        int start = Math.min(offset, text.length() - 1);
        int end = start;
        while (start > 0 && isWordChar(text.charAt(start - 1))) start--;
        while (end < text.length() && isWordChar(text.charAt(end))) end++;
        if (start == end) return null;
        return text.substring(start, end);
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }
}
