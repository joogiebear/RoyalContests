package com.mystipixel.royalcontests.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Text read without a code fallback must ship in the bundled files: YamlFile sets them as defaults for
// messages.yml and menus.yml, so a server file that predates a key still shows the shipped text.
class BundledTextTest {

    private static final Path SOURCES = Path.of("src", "main", "java");
    private static final List<String> FILES = List.of("config.yml", "messages.yml", "menus.yml", "crops.yml",
            "events.yml", "brackets.yml");
    // notice(player, key, ...) in ContestService forwards its key to lang.send
    private static final Pattern MESSAGE_CALL = Pattern.compile(
            "(?:\\blang(?:\\(\\))?\\s*\\.\\s*(raw|send|sendPlain|broadcast|broadcastPlain)|\\b(notice))\\s*\\(");
    private static final Pattern LITERAL = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern MENU_CALL = Pattern.compile("\\bmenu\\(\"([a-z-]+)\", *\\d+\\)");

    @Test
    void menuTextReadWithoutAFallbackShips() throws IOException {
        YamlConfiguration menus = load("menus.yml");
        Matcher m = MENU_CALL.matcher(Files.readString(SOURCES.resolve("com/mystipixel/royalcontests/gui/Menus.java")));
        int found = 0;
        while (m.find()) {
            found++;
            assertTrue(menus.isString(m.group(1) + ".title"), "menus.yml ships " + m.group(1) + ".title");
        }
        assertEquals(5, found, "menus opened by Menus");
        assertTrue(menus.isString("leaderboards.line"), "menus.yml ships leaderboards.line");
        assertTrue(load("messages.yml").isString("prefix"), "messages.yml ships prefix");
    }

    @Test
    void everyMessageKeyInCodeShipsInMessagesYml() throws IOException {
        YamlConfiguration shipped = load("messages.yml");
        List<String> problems = new ArrayList<>();
        int found = 0;
        List<Path> files;
        try (Stream<Path> walk = Files.walk(SOURCES)) {
            files = walk.filter(p -> p.toString().endsWith(".java")).toList();
        }
        for (Path file : files) {
            String source = Files.readString(file);
            Matcher m = MESSAGE_CALL.matcher(source);
            while (m.find()) {
                List<String> args = arguments(source, m.end());
                String verb = m.group(1) == null ? m.group(2) : m.group(1);
                if (args.get(0).startsWith("Player player")) {
                    continue; // the declaration of notice(...)
                }
                String keyArg = args.get(verb.equals("raw") || verb.startsWith("broadcast") ? 0 : 1);
                if (keyArg.equals("key")) {
                    continue; // forwarded from notice(...)
                }
                Matcher literal = LITERAL.matcher(keyArg);
                if (!literal.find()) {
                    problems.add(file.getFileName() + ":" + lineOf(source, m.start())
                            + ": message key must be a string literal, not " + keyArg);
                    continue;
                }
                literal.reset();
                while (literal.find()) {
                    found++;
                    String key = literal.group(1);
                    boolean shipsKey = shipped.isString(key);
                    if (!shipsKey) {
                        problems.add(file.getFileName() + ":" + lineOf(source, m.start()) + ": " + literal.group(1)
                                + " is not in messages.yml");
                    }
                }
            }
        }
        assertTrue(found > 40, "found only " + found + " message keys; is the scan still matching?");
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void bundledFilesHaveNoDuplicateKeys() throws IOException {
        for (String file : FILES) {
            assertEquals(List.of(), duplicates(resource(file)), "duplicate keys in " + file);
        }
    }

    @Test
    void generalTitleFallbackIsGone() throws IOException {
        // every menu used "&8Contests" when its title was missing; none of the shipped titles is that
        YamlConfiguration menus = load("menus.yml");
        for (String menu : List.of("main", "claim", "stats", "calendar", "leaderboards")) {
            assertTrue(!"&8Contests".equals(menus.getString(menu + ".title")), menu);
        }
    }

    // YAML keeps only the last of two equal keys, silently dropping the first
    private static List<String> duplicates(String yaml) {
        List<String> duplicates = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        List<String> path = new ArrayList<>();
        List<Integer> indents = new ArrayList<>();
        int items = 0;
        for (String line : yaml.split("\n")) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int indent = line.length() - line.stripLeading().length();
            if (trimmed.startsWith("-")) {
                // each list entry is its own map
                while (!indents.isEmpty() && indents.get(indents.size() - 1) >= indent) {
                    indents.remove(indents.size() - 1);
                    path.remove(path.size() - 1);
                }
                path.add("[" + items++ + "]");
                indents.add(indent);
                trimmed = trimmed.substring(1).strip();
                indent += 2;
            }
            int colon = trimmed.indexOf(':');
            if (colon < 0 || trimmed.startsWith("\"") || trimmed.startsWith("'")) {
                continue;
            }
            while (!indents.isEmpty() && indents.get(indents.size() - 1) >= indent) {
                indents.remove(indents.size() - 1);
                path.remove(path.size() - 1);
            }
            path.add(trimmed.substring(0, colon));
            indents.add(indent);
            String key = String.join(".", path);
            if (!seen.add(key)) {
                duplicates.add(key);
            }
        }
        return duplicates;
    }

    // top-level arguments of the call whose '(' ends just before start
    private static List<String> arguments(String source, int start) {
        List<String> args = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        for (int i = start; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '"') {
                int end = i + 1;
                while (source.charAt(end) != '"') {
                    end += source.charAt(end) == '\\' ? 2 : 1;
                }
                current.append(source, i, end + 1);
                i = end;
                continue;
            }
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                if (depth == 0) {
                    args.add(current.toString().strip());
                    return args;
                }
                depth--;
            } else if (c == ',' && depth == 0) {
                args.add(current.toString().strip());
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        throw new IllegalStateException("unclosed call");
    }

    private static int lineOf(String source, int index) {
        return (int) source.substring(0, index).chars().filter(c -> c == '\n').count() + 1;
    }

    private static String resource(String name) throws IOException {
        try (InputStream in = BundledTextTest.class.getClassLoader().getResourceAsStream(name)) {
            assertNotNull(in, name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static YamlConfiguration load(String name) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(resource(name));
        } catch (InvalidConfigurationException e) {
            throw new IOException(e);
        }
        return yaml;
    }
}
