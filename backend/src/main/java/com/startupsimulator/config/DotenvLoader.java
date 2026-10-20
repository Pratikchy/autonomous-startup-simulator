package com.startupsimulator.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lightweight, zero-dependency .env loader that populates JVM system properties
 * from project-level .env files before Spring context initialization.
 *
 * <p>Preserves existing environment variables and JVM system properties so that
 * explicitly configured runtime properties always take precedence.</p>
 */
public final class DotenvLoader {

    private static final Logger log = LoggerFactory.getLogger(DotenvLoader.class);

    private DotenvLoader() {}

    /**
     * Finds and loads candidate .env files into System properties,
     * and ensures database path predictability.
     */
    public static void load() {
        load(findCandidateFiles());
        ensureStableDatabasePath();
    }

    /**
     * Ensures the file-based H2 database path is stable and predictable regardless
     * of whether the application is launched from the repository root or backend directory.
     */
    public static void ensureStableDatabasePath() {
        if (System.getProperty("spring.datasource.url") != null || System.getenv("SPRING_DATASOURCE_URL") != null) {
            return;
        }
        try {
            Path cwd = Paths.get("").toAbsolutePath();
            Path dataDir;
            if (Files.isDirectory(cwd.resolve("backend"))) {
                dataDir = cwd.resolve("backend").resolve("data");
            } else if (cwd.endsWith("backend") || Files.exists(cwd.resolve("pom.xml"))) {
                dataDir = cwd.resolve("data");
            } else {
                dataDir = cwd.resolve("data");
            }
            Files.createDirectories(dataDir);
            Path dbFile = dataDir.resolve("startupsim").toAbsolutePath().normalize();
            String url = "jdbc:h2:file:" + dbFile + ";MODE=PostgreSQL;AUTO_SERVER=TRUE";
            System.setProperty("spring.datasource.url", url);
            System.setProperty("DB_FILE_PATH", dbFile.toString());
            log.info("Configured stable H2 database URL: {}", url);
        } catch (Exception e) {
            log.warn("Could not configure stable database path: {}", e.getMessage());
        }
    }

    /**
     * Resolves standard candidate locations for the .env file.
     * When running from `backend/`, `../.env` is the project root.
     * When running from root, `.env` or `backend/.env` is resolved.
     */
    public static List<Path> findCandidateFiles() {
        List<Path> candidates = new ArrayList<>();
        // Priority 1: local .env in current working directory
        candidates.add(Paths.get(".env"));
        // Priority 2: parent directory (project root when running from backend/)
        candidates.add(Paths.get("../.env"));
        // Priority 3: backend subfolder (when running from project root)
        candidates.add(Paths.get("backend/.env"));
        return candidates;
    }

    /**
     * Loads properties from a list of candidate file paths into System properties.
     */
    public static void load(List<Path> candidatePaths) {
        if (candidatePaths == null || candidatePaths.isEmpty()) {
            return;
        }

        List<Path> loadedFiles = new ArrayList<>();
        int totalLoaded = 0;

        for (Path path : candidatePaths) {
            if (path != null && Files.isRegularFile(path)) {
                try {
                    Path realPath = path.toRealPath();
                    if (loadedFiles.contains(realPath)) {
                        continue;
                    }
                    loadedFiles.add(realPath);

                    List<String> lines = Files.readAllLines(path);
                    Map<String, String> parsed = parse(lines);
                    int applied = applyToSystemProperties(parsed);
                    totalLoaded += applied;

                    log.info("Loaded {} configuration property/properties from .env at {}", applied, path);
                } catch (IOException e) {
                    log.warn("Could not read .env file at {}: {}", path, e.getMessage());
                }
            }
        }

        if (loadedFiles.isEmpty()) {
            log.debug("No .env file found in candidate locations.");
        }
    }

    /**
     * Parses .env file lines safely:
     * - Ignores empty lines and comments (# or //)
     * - Trims keys and values
     * - Unquotes single and double quoted values
     * - Removes unquoted inline comments
     */
    public static Map<String, String> parse(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, String> result = new LinkedHashMap<>();
        for (String rawLine : lines) {
            if (rawLine == null) {
                continue;
            }
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) {
                continue;
            }

            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }

            String key = line.substring(0, eq).trim();
            String val = line.substring(eq + 1).trim();

            if (key.isEmpty()) {
                continue;
            }

            // Unquote if wrapped in matching quotes
            if ((val.startsWith("\"") && val.endsWith("\"") && val.length() >= 2)
                    || (val.startsWith("'") && val.endsWith("'") && val.length() >= 2)) {
                val = val.substring(1, val.length() - 1);
            } else {
                // For unquoted values, strip trailing comments if preceded by whitespace or at boundary
                int commentIdx = val.indexOf('#');
                if (commentIdx >= 0) {
                    val = val.substring(0, commentIdx).trim();
                }
            }

            result.put(key, val);
        }
        return result;
    }

    /**
     * Applies parsed entries to System properties without overwriting existing
     * environment variables or JVM system properties.
     */
    public static int applyToSystemProperties(Map<String, String> entries) {
        if (entries == null || entries.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            // Precedence: never overwrite existing system environment variables or system properties
            if (System.getenv(key) == null && System.getProperty(key) == null) {
                System.setProperty(key, value);
                count++;
            }
        }
        return count;
    }
}
