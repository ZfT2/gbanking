package de.gbanking.db;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SqlTemplateRepository {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([A-Z0-9_]+)}");
    private static final TemplateBundle DML = loadBundle(listSqlResources("sql/dml", false));
    private static final VersionedTemplateBundle DDL = loadVersionedBundle("sql/ddl");

    private SqlTemplateRepository() {
    }

    public static String getDml(String key) {
        return DML.get(key);
    }

    public static String getDdl(String key) {
        return DDL.get(key);
    }

    static List<String> getBaselineStatements() {
        return DDL.baselineStatements();
    }

    static VersionScript getBaselineVersionScript() {
        return DDL.baselineScript();
    }

    static List<VersionScript> getVersionScripts() {
        return DDL.versionScripts();
    }

    private static VersionedTemplateBundle loadVersionedBundle(String directory) {
        List<String> resources = listSqlResources(directory, true);
        TemplateBundle bundle = loadBundle(resources);

        TreeMap<String, List<String>> baselineStatementsByVersion = new TreeMap<String, List<String>>(new java.util.Comparator<String>() {
            @Override
            public int compare(String left, String right) {
                return DbMigrationRunner.compareVersions(left, right);
            }
        });
        TreeMap<String, List<String>> migrationStatementsByVersion = new TreeMap<String, List<String>>(new java.util.Comparator<String>() {
            @Override
            public int compare(String left, String right) {
                return DbMigrationRunner.compareVersions(left, right);
            }
        });

        for (String resource : resources) {
            String version = extractVersion(directory, resource);
            baselineStatementsByVersion.computeIfAbsent(version, key -> new ArrayList<>())
                    .addAll(bundle.executableStatementsFor(resource, StatementMode.BASELINE));
            migrationStatementsByVersion.computeIfAbsent(version, key -> new ArrayList<>())
                    .addAll(bundle.executableStatementsFor(resource, StatementMode.MIGRATION));
        }

        List<VersionScript> versionScripts = migrationStatementsByVersion.entrySet().stream()
                .map(entry -> new VersionScript(entry.getKey(), entry.getValue()))
                .toList();
        List<String> baselineStatements = baselineStatementsByVersion.isEmpty()
                ? List.of()
                : List.copyOf(baselineStatementsByVersion.firstEntry().getValue());
        return new VersionedTemplateBundle(bundle, versionScripts, baselineStatements);
    }

    private static String extractVersion(String rootDirectory, String resource) {
        String relativePath = resource.substring((rootDirectory + "/").length());
        int slashIndex = relativePath.indexOf('/');
        if (slashIndex < 0) {
            throw new IllegalStateException("DDL resource must be inside a version directory: " + resource);
        }
        return relativePath.substring(0, slashIndex);
    }

    private static TemplateBundle loadBundle(List<String> resources) {
        Map<String, String> rawValues = new LinkedHashMap<>();
        Map<String, String> resourceByKey = new LinkedHashMap<>();
        List<String> keyOrder = new ArrayList<>();

        for (String resource : resources) {
            parseResource(resource, rawValues, resourceByKey, keyOrder);
        }

        Map<String, String> resolvedValues = new LinkedHashMap<>();
        for (String key : rawValues.keySet()) {
            resolvedValues.put(key, resolveValue(key, rawValues, resolvedValues, new LinkedHashSet<>()));
        }

        return new TemplateBundle(resolvedValues, resourceByKey, keyOrder);
    }

    private static List<String> listSqlResources(String directory, boolean recursive) {
        try {
            Path codeSource = Path.of(SqlTemplateRepository.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            TreeSet<String> resources = new TreeSet<>();

            if (Files.isDirectory(codeSource)) {
                collectFromDirectory(codeSource, directory, recursive, resources);
            } else {
                collectFromJar(codeSource, directory, recursive, resources);
            }

            if (resources.isEmpty()) {
                throw new IllegalStateException("No SQL resources found below " + directory);
            }
            return List.copyOf(resources);
        } catch (IOException | URISyntaxException e) {
            throw new IllegalStateException("Could not scan SQL resources below " + directory, e);
        }
    }

    private static void collectFromDirectory(Path codeSource, String directory, boolean recursive, TreeSet<String> resources)
            throws IOException {
        Path resourceDirectory = codeSource.resolve(directory);
        if (!Files.isDirectory(resourceDirectory)) {
            return;
        }

        int maxDepth = recursive ? Integer.MAX_VALUE : 1;
        try (var stream = Files.walk(resourceDirectory, maxDepth)) {
            stream.filter(Files::isRegularFile)
                    .map(path -> codeSource.relativize(path).toString().replace('\\', '/'))
                    .filter(path -> path.endsWith(".sql"))
                    .forEach(resources::add);
        }
    }

    private static void collectFromJar(Path jarPath, String directory, boolean recursive, TreeSet<String> resources)
            throws IOException {
        String prefix = directory.endsWith("/") ? directory : directory + "/";
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            jarFile.stream()
                    .map(entry -> entry.getName())
                    .filter(name -> name.startsWith(prefix) && name.endsWith(".sql"))
                    .filter(name -> recursive || !name.substring(prefix.length()).contains("/"))
                    .forEach(resources::add);
        }
    }

    private static void parseResource(String resource, Map<String, String> rawValues, Map<String, String> resourceByKey,
            List<String> keyOrder) {
        try (InputStream in = getRequiredResource(resource);
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {

            String currentKey = null;
            StringBuilder currentValue = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("--")) {
                    if (currentKey != null && trimmed.isEmpty()) {
                        currentValue.append(System.lineSeparator());
                    }
                    continue;
                }
                if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                    if (currentKey != null) {
                        storeEntry(resource, currentKey, currentValue, rawValues, resourceByKey, keyOrder);
                    }
                    currentKey = trimmed.substring(1, trimmed.length() - 1).trim();
                    currentValue = new StringBuilder();
                    continue;
                }
                if (";".equals(trimmed)) {
                    if (currentKey != null) {
                        storeEntry(resource, currentKey, currentValue, rawValues, resourceByKey, keyOrder);
                        currentKey = null;
                        currentValue = new StringBuilder();
                    }
                    continue;
                }

                if (currentKey == null) {
                    throw new IllegalStateException("SQL content found outside a [KEY] block in " + resource + ": " + line);
                }

                if (trimmed.endsWith(";")) {
                    currentValue.append(stripStatementTerminator(line)).append(System.lineSeparator());
                    storeEntry(resource, currentKey, currentValue, rawValues, resourceByKey, keyOrder);
                    currentKey = null;
                    currentValue = new StringBuilder();
                    continue;
                }

                currentValue.append(line).append(System.lineSeparator());
            }
            if (currentKey != null) {
                storeEntry(resource, currentKey, currentValue, rawValues, resourceByKey, keyOrder);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not read SQL resource: " + resource, e);
        }
    }

    private static String stripStatementTerminator(String line) {
        int semicolonIndex = line.lastIndexOf(';');
        return semicolonIndex < 0 ? line : line.substring(0, semicolonIndex);
    }

    private static void storeEntry(String resource, String key, StringBuilder currentValue, Map<String, String> rawValues,
            Map<String, String> resourceByKey, List<String> keyOrder) {
        if (key == null) {
            throw new IllegalStateException("Found SQL terminator without key in " + resource);
        }
        if (rawValues.containsKey(key)) {
            throw new IllegalStateException("Duplicate SQL key '" + key + "' in resource " + resource);
        }
        rawValues.put(key, trimTrailingWhitespace(currentValue.toString()));
        resourceByKey.put(key, resource);
        keyOrder.add(key);
    }

    private static String resolveValue(String key, Map<String, String> rawValues, Map<String, String> resolvedValues,
            LinkedHashSet<String> stack) {
        String existing = resolvedValues.get(key);
        if (existing != null) {
            return existing;
        }

        String rawValue = rawValues.get(key);
        if (rawValue == null) {
            throw new IllegalArgumentException("Unknown SQL template key: " + key);
        }

        if (!stack.add(key)) {
            throw new IllegalStateException("Cyclic SQL placeholder detected: " + stack + " -> " + key);
        }

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(rawValue);
        StringBuffer resolved = new StringBuffer();
        while (matcher.find()) {
            String placeholderKey = matcher.group(1);
            String replacement = resolveValue(placeholderKey, rawValues, resolvedValues, stack);
            matcher.appendReplacement(resolved, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(resolved);
        stack.remove(key);
        return resolved.toString();
    }

    private static String trimTrailingWhitespace(String value) {
        return value.replaceFirst("\\s+$", "");
    }

    private enum StatementMode {
        BASELINE,
        MIGRATION
    }

    private static boolean isExecutableBaselineStatement(String key) {
        return key.startsWith("SQL_FOREIGN_KEY_")
                || key.startsWith("SQL_SETUP_CREATE_")
                || key.startsWith("SQL_SETUP_VIEW_")
                || key.startsWith("SQL_SETUP_INSERT_");
    }

    private static boolean isExecutableMigrationStatement(String key) {
        return key.startsWith("SQL_MIGRATION_");
    }

    private static InputStream getRequiredResource(String resource) {
        InputStream in = SqlTemplateRepository.class.getClassLoader().getResourceAsStream(resource);
        if (in == null) {
            throw new IllegalStateException("Missing SQL resource: " + resource);
        }
        return in;
    }

    static final class VersionScript {
        private final String version;
        private final List<String> statements;

        VersionScript(String version, List<String> statements) {
            this.version = version;
            this.statements = List.copyOf(statements);
        }

        String getVersion() {
            return version;
        }

        List<String> getStatements() {
            return statements;
        }

        String getSettingKey() {
            return "db.migration." + version;
        }

        String getResource() {
            return "sql/ddl/" + version;
        }
    }

    private static class TemplateBundle {
        private final Map<String, String> resolvedValues;
        private final Map<String, String> resourceByKey;
        private final List<String> keyOrder;

        TemplateBundle(Map<String, String> resolvedValues, Map<String, String> resourceByKey, List<String> keyOrder) {
            this.resolvedValues = Map.copyOf(resolvedValues);
            this.resourceByKey = Map.copyOf(resourceByKey);
            this.keyOrder = List.copyOf(keyOrder);
        }

        String get(String key) {
            String value = resolvedValues.get(key);
            if (value == null) {
                throw new IllegalArgumentException("Unknown SQL key: " + key);
            }
            return value;
        }

        List<String> executableStatementsFor(String resource, StatementMode mode) {
            List<String> statements = new ArrayList<>();
            for (String key : keyOrder) {
                if (!resource.equals(resourceByKey.get(key))) {
                    continue;
                }
                boolean include = mode == StatementMode.BASELINE
                        ? isExecutableBaselineStatement(key)
                        : isExecutableMigrationStatement(key);
                if (include) {
                    statements.add(get(key));
                }
            }
            return statements;
        }
    }

    private static final class VersionedTemplateBundle extends TemplateBundle {
        private final List<VersionScript> versionScripts;
        private final List<String> baselineStatements;

        private VersionedTemplateBundle(TemplateBundle delegate, List<VersionScript> versionScripts, List<String> baselineStatements) {
            super(delegate.resolvedValues, delegate.resourceByKey, delegate.keyOrder);
            this.versionScripts = List.copyOf(versionScripts);
            this.baselineStatements = List.copyOf(baselineStatements);
        }

        VersionScript baselineScript() {
            return versionScripts.isEmpty() ? null : versionScripts.get(0);
        }

        List<VersionScript> versionScripts() {
            return versionScripts;
        }

        List<String> baselineStatements() {
            return baselineStatements;
        }
    }
}
