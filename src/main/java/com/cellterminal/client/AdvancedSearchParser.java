package com.cellterminal.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.item.ItemStack;


/**
 * Parser and evaluator for advanced search queries.
 *
 * Syntax (when query starts with "?"):
 * - $name: Match against item contents (display name, registry name) - same as normal search
 * - $priority: Storage/bus priority (numeric comparison)
 * - $partition: Count of partition slots filled (numeric comparison)
 * - $items: Count of item types stored (numeric comparison)
 *
 * Comparisons: =, !=, <, >, <=, >=, ~ (contains for strings)
 * Logical operators: & (AND), | (OR)
 * Parentheses for grouping: ( )
 *
 * Examples:
 * - ?$priority>0
 * - ?$items=0&$partition>0
 * - ?$name~iron|$name~gold
 * - ?($priority>=5|$items=0)&$partition>0
 * FIXME: localization
 */
public class AdvancedSearchParser {

    private static final Pattern TOKEN_PATTERN = Pattern.compile(
        "\\$\\w+|[<>=!~]+|\\(|\\)|&|\\||\"[^\"]*\"|'[^']*'|[^\\s$<>=!~()&|\"']+"
    );

    /**
     * Result of parsing an advanced query.
     */
    public static class ParseResult {

        private final SearchMatcher matcher;
        private final List<String> errors;
        private final boolean success;

        private ParseResult(SearchMatcher matcher, List<String> errors) {
            this.matcher = matcher;
            this.errors = errors != null ? errors : new ArrayList<>();
            this.success = this.errors.isEmpty() && matcher != null;
        }

        public static ParseResult success(SearchMatcher matcher) {
            return new ParseResult(matcher, null);
        }

        public static ParseResult error(String error) {
            List<String> errors = new ArrayList<>();
            errors.add(error);

            return new ParseResult(null, errors);
        }

        public static ParseResult error(List<String> errors) {
            return new ParseResult(null, errors);
        }

        public boolean isSuccess() {
            return success;
        }

        public SearchMatcher getMatcher() {
            return matcher;
        }

        public List<String> getErrors() {
            return errors;
        }

        public String getErrorMessage() {
            if (errors.isEmpty()) return "";

            return String.join("\n", errors);
        }
    }

    /**
     * Check if a query is an advanced query (starts with "?").
     */
    public static boolean isAdvancedQuery(String query) {
        return query != null && query.startsWith("?");
    }

    /**
     * Parse and create a matcher for the given advanced query.
     * @param query The query string (with or without leading "?")
     * @return A ParseResult containing either a SearchMatcher or error messages
     */
    public static ParseResult parse(String query) {
        if (query == null || query.isEmpty()) {
            return ParseResult.error("Empty query");
        }

        // Strip leading "?" if present
        String expr = query.startsWith("?") ? query.substring(1).trim() : query.trim();
        if (expr.isEmpty()) {
            return ParseResult.error("Empty query after '?'");
        }

        List<String> errors = new ArrayList<>();
        List<String> tokens = tokenize(expr);

        if (tokens.isEmpty()) {
            return ParseResult.error("No valid tokens found");
        }

        int[] pos = {0};
        SearchMatcher matcher = parseExpression(tokens, pos, errors);

        // Check for unconsumed tokens
        if (pos[0] < tokens.size()) {
            errors.add("Unexpected token: '" + tokens.get(pos[0]) + "'");
        }

        if (!errors.isEmpty()) {
            return ParseResult.error(errors);
        }

        return ParseResult.success(matcher);
    }

    private static List<String> tokenize(String expr) {
        List<String> tokens = new ArrayList<>();
        Matcher matcher = TOKEN_PATTERN.matcher(expr);

        while (matcher.find()) {
            String token = matcher.group().trim();
            if (!token.isEmpty()) tokens.add(token);
        }

        return tokens;
    }

    private static SearchMatcher parseExpression(List<String> tokens, int[] pos, List<String> errors) {
        SearchMatcher left = parseAndExpression(tokens, pos, errors);

        while (pos[0] < tokens.size() && "|".equals(tokens.get(pos[0]))) {
            pos[0]++;  // Skip "|"

            if (pos[0] >= tokens.size()) {
                errors.add("Expected expression after '|'");

                return left;
            }

            SearchMatcher right = parseAndExpression(tokens, pos, errors);
            final SearchMatcher l = left, r = right;
            left = new SearchMatcher() {
                @Override
                public boolean matchesCell(CellInfo cell, StorageInfo storage, SearchFilterMode mode) {
                    return l.matchesCell(cell, storage, mode) || r.matchesCell(cell, storage, mode);
                }

                @Override
                public boolean matchesStorageBus(StorageBusInfo bus, SearchFilterMode mode) {
                    return l.matchesStorageBus(bus, mode) || r.matchesStorageBus(bus, mode);
                }
            };
        }

        return left;
    }

    private static SearchMatcher parseAndExpression(List<String> tokens, int[] pos, List<String> errors) {
        SearchMatcher left = parsePrimary(tokens, pos, errors);

        while (pos[0] < tokens.size() && "&".equals(tokens.get(pos[0]))) {
            pos[0]++;  // Skip "&"

            if (pos[0] >= tokens.size()) {
                errors.add("Expected expression after '&'");

                return left;
            }

            SearchMatcher right = parsePrimary(tokens, pos, errors);
            final SearchMatcher l = left, r = right;
            left = new SearchMatcher() {
                @Override
                public boolean matchesCell(CellInfo cell, StorageInfo storage, SearchFilterMode mode) {
                    return l.matchesCell(cell, storage, mode) && r.matchesCell(cell, storage, mode);
                }

                @Override
                public boolean matchesStorageBus(StorageBusInfo bus, SearchFilterMode mode) {
                    return l.matchesStorageBus(bus, mode) && r.matchesStorageBus(bus, mode);
                }
            };
        }

        return left;
    }

    private static SearchMatcher parsePrimary(List<String> tokens, int[] pos, List<String> errors) {
        if (pos[0] >= tokens.size()) {
            errors.add("Unexpected end of expression");

            return alwaysFalse();
        }

        String token = tokens.get(pos[0]);

        // Parenthesized expression
        if ("(".equals(token)) {
            pos[0]++;  // Skip "("
            SearchMatcher inner = parseExpression(tokens, pos, errors);

            if (pos[0] >= tokens.size() || !")".equals(tokens.get(pos[0]))) {
                errors.add("Missing closing parenthesis ')'");
            } else {
                pos[0]++;  // Skip ")"
            }

            return inner;
        }

        // Check for stray closing paren or operators
        if (")".equals(token)) {
            errors.add("Unexpected closing parenthesis ')'");

            return alwaysFalse();
        }

        if ("&".equals(token) || "|".equals(token)) {
            errors.add("Unexpected operator '" + token + "'");

            return alwaysFalse();
        }

        // Identifier with comparison
        if (token.startsWith("$")) return parseComparison(tokens, pos, errors);

        // Plain text - treat as $name contains (searches content)
        pos[0]++;
        String searchText = stripQuotes(token).toLowerCase(Locale.ROOT);

        return createContentContainsMatcher(searchText);
    }

    private static SearchMatcher parseComparison(List<String> tokens, int[] pos, List<String> errors) {
        String identifier = tokens.get(pos[0]).toLowerCase(Locale.ROOT);
        pos[0]++;

        // Validate identifier
        if (!isValidIdentifier(identifier)) {
            errors.add("Unknown identifier: '" + identifier + "'. Valid: $name, $priority, $partition, $items");
        }

        // Check for operator
        String operator = "~";  // Default to contains
        if (pos[0] < tokens.size()) {
            String next = tokens.get(pos[0]);
            if (isOperator(next)) {
                operator = normalizeOperator(next);
                pos[0]++;
            }
        }

        // Get the value
        String value = "";
        if (pos[0] < tokens.size()) {
            String next = tokens.get(pos[0]);
            if (!isLogicalOp(next) && !")".equals(next) && !"(".equals(next)) {
                value = stripQuotes(next);
                pos[0]++;
            }
        }

        // Validate numeric identifiers have numeric values
        if (isNumericIdentifier(identifier) && !value.isEmpty()) {
            try {
                Integer.parseInt(value.trim());
            } catch (NumberFormatException e) {
                errors.add("Expected number for '" + identifier + "', got: '" + value + "'");
            }
        }

        return createMatcher(identifier, operator, value, errors);
    }

    private static boolean isValidIdentifier(String id) {
        return "$name".equals(id) || "$priority".equals(id) || "$partition".equals(id) || "$items".equals(id);
    }

    private static boolean isNumericIdentifier(String id) {
        return "$priority".equals(id) || "$partition".equals(id) || "$items".equals(id);
    }

    private static boolean isOperator(String s) {
        return "=".equals(s) || "!=".equals(s) || "<".equals(s) || ">".equals(s)
            || "<=".equals(s) || ">=".equals(s) || "~".equals(s)
            || s.startsWith("=") || s.startsWith("!") || s.startsWith("<") || s.startsWith(">") || s.startsWith("~");
    }

    private static String normalizeOperator(String s) {
        // Handle cases where operator and value are merged (e.g., ">=5" as one token)
        if (s.startsWith("!=")) return "!=";
        if (s.startsWith("<=")) return "<=";
        if (s.startsWith(">=")) return ">=";
        if (s.startsWith("=")) return "=";
        if (s.startsWith("<")) return "<";
        if (s.startsWith(">")) return ">";
        if (s.startsWith("~")) return "~";

        return s;
    }

    private static boolean isLogicalOp(String s) {
        return "&".equals(s) || "|".equals(s);
    }

    private static String stripQuotes(String s) {
        if (s.length() >= 2) {
            if ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"))) {
                return s.substring(1, s.length() - 1);
            }
        }

        return s;
    }

    private static SearchMatcher createMatcher(String identifier, String operator, String value, List<String> errors) {
        switch (identifier) {
            case "$name":
                return createContentMatcher(operator, value);
            case "$priority":
                return createPriorityMatcher(operator, value);
            case "$partition":
                return createPartitionMatcher(operator, value);
            case "$items":
                return createItemsMatcher(operator, value);
            default:
                // Already reported as error, return false matcher
                return alwaysFalse();
        }
    }

    /**
     * Create a matcher that searches item contents (display name, registry name).
     * This matches the behavior of normal search.
     */
    private static SearchMatcher createContentMatcher(String operator, String value) {
        final String searchValue = value.toLowerCase(Locale.ROOT);

        return new SearchMatcher() {
            @Override
            public boolean matchesCell(CellInfo cell, StorageInfo storage, SearchFilterMode mode) {
                // Match against inventory, partition, or both based on mode
                boolean matchesInventory = matchesItemList(cell.getContents(), searchValue, operator);
                boolean matchesPartition = matchesItemList(cell.getPartition(), searchValue, operator);

                switch (mode) {
                    case INVENTORY:
                        return matchesInventory;
                    case PARTITION:
                        return matchesPartition;
                    case MIXED:
                    default:
                        return matchesInventory || matchesPartition;
                }
            }

            @Override
            public boolean matchesStorageBus(StorageBusInfo bus, SearchFilterMode mode) {
                boolean matchesInventory = matchesItemList(bus.getContents(), searchValue, operator);
                boolean matchesPartition = matchesItemList(bus.getPartition(), searchValue, operator);

                switch (mode) {
                    case INVENTORY:
                        return matchesInventory;
                    case PARTITION:
                        return matchesPartition;
                    case MIXED:
                    default:
                        return matchesInventory || matchesPartition;
                }
            }
        };
    }

    /**
     * Create a matcher for plain text search (no operator, just contains).
     * Searches item contents like normal search.
     */
    private static SearchMatcher createContentContainsMatcher(String searchText) {
        return new SearchMatcher() {
            @Override
            public boolean matchesCell(CellInfo cell, StorageInfo storage, SearchFilterMode mode) {
                boolean matchesInventory = matchesItemList(cell.getContents(), searchText, "~");
                boolean matchesPartition = matchesItemList(cell.getPartition(), searchText, "~");

                switch (mode) {
                    case INVENTORY:
                        return matchesInventory;
                    case PARTITION:
                        return matchesPartition;
                    case MIXED:
                    default:
                        return matchesInventory || matchesPartition;
                }
            }

            @Override
            public boolean matchesStorageBus(StorageBusInfo bus, SearchFilterMode mode) {
                boolean matchesInventory = matchesItemList(bus.getContents(), searchText, "~");
                boolean matchesPartition = matchesItemList(bus.getPartition(), searchText, "~");

                switch (mode) {
                    case INVENTORY:
                        return matchesInventory;
                    case PARTITION:
                        return matchesPartition;
                    case MIXED:
                    default:
                        return matchesInventory || matchesPartition;
                }
            }
        };
    }

    private static boolean matchesItemList(List<ItemStack> items, String searchValue, String operator) {
        for (ItemStack stack : items) {
            if (stack.isEmpty()) continue;

            // Check display name
            String displayName = stack.getDisplayName().toLowerCase(Locale.ROOT);
            if (compareString(displayName, operator, searchValue)) return true;

            // Check registry name
            if (stack.getItem().getRegistryName() != null) {
                String registryName = stack.getItem().getRegistryName().toString().toLowerCase(Locale.ROOT);
                if (compareString(registryName, operator, searchValue)) return true;
            }
        }

        return false;
    }

    private static SearchMatcher createPriorityMatcher(String operator, String value) {
        final int targetValue = parseIntSafe(value, 0);

        return new SearchMatcher() {
            @Override
            public boolean matchesCell(CellInfo cell, StorageInfo storage, SearchFilterMode mode) {
                if (storage == null) return false;

                return compareInt(storage.getPriority(), operator, targetValue);
            }

            @Override
            public boolean matchesStorageBus(StorageBusInfo bus, SearchFilterMode mode) {
                return compareInt(bus.getPriority(), operator, targetValue);
            }
        };
    }

    private static SearchMatcher createPartitionMatcher(String operator, String value) {
        final int targetValue = parseIntSafe(value, 0);

        return new SearchMatcher() {
            @Override
            public boolean matchesCell(CellInfo cell, StorageInfo storage, SearchFilterMode mode) {
                int partitionCount = countNonEmpty(cell.getPartition());

                return compareInt(partitionCount, operator, targetValue);
            }

            @Override
            public boolean matchesStorageBus(StorageBusInfo bus, SearchFilterMode mode) {
                return compareInt(bus.getPartitionCount(), operator, targetValue);
            }
        };
    }

    private static SearchMatcher createItemsMatcher(String operator, String value) {
        final int targetValue = parseIntSafe(value, 0);

        return new SearchMatcher() {
            @Override
            public boolean matchesCell(CellInfo cell, StorageInfo storage, SearchFilterMode mode) {
                int itemCount = cell.getContents().size();

                return compareInt(itemCount, operator, targetValue);
            }

            @Override
            public boolean matchesStorageBus(StorageBusInfo bus, SearchFilterMode mode) {
                return compareInt(bus.getContentTypeCount(), operator, targetValue);
            }
        };
    }

    private static int countNonEmpty(List<ItemStack> list) {
        int count = 0;
        for (ItemStack stack : list) {
            if (!stack.isEmpty()) count++;
        }

        return count;
    }

    private static SearchMatcher alwaysFalse() {
        return new SearchMatcher() {
            @Override
            public boolean matchesCell(CellInfo cell, StorageInfo storage, SearchFilterMode mode) {
                return false;
            }

            @Override
            public boolean matchesStorageBus(StorageBusInfo bus, SearchFilterMode mode) {
                return false;
            }
        };
    }

    private static boolean compareString(String actual, String operator, String expected) {
        switch (operator) {
            case "=":
                return actual.equals(expected);
            case "!=":
                return !actual.equals(expected);
            case "~":
            default:
                return actual.contains(expected);
        }
    }

    private static boolean compareInt(int actual, String operator, int expected) {
        switch (operator) {
            case "=":
                return actual == expected;
            case "!=":
                return actual != expected;
            case "<":
                return actual < expected;
            case ">":
                return actual > expected;
            case "<=":
                return actual <= expected;
            case ">=":
                return actual >= expected;
            default:
                return actual == expected;
        }
    }

    private static int parseIntSafe(String s, int defaultValue) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Interface for matching cells and storage buses.
     */
    public interface SearchMatcher {
        boolean matchesCell(CellInfo cell, StorageInfo storage, SearchFilterMode mode);
        boolean matchesStorageBus(StorageBusInfo bus, SearchFilterMode mode);
    }
}
