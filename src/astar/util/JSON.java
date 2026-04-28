package astar.util;

import java.util.*;

/**
 * Minimal JSON parser — no external dependencies needed.
 * Supports JSONObject and JSONArray for Goong API responses.
 */
public class JSON {

    public static class JSONObject {
        private final Map<String, Object> map = new LinkedHashMap<>();

        public JSONObject(String json) {
            json = json.trim();
            if (!json.startsWith("{")) throw new RuntimeException("Not a JSON object: " + json.substring(0, Math.min(30, json.length())));
            parse(json);
        }

        JSONObject(Map<String, Object> m) { map.putAll(m); }

        private void parse(String json) {
            json = json.trim().substring(1, json.trim().length() - 1).trim();
            int i = 0;
            while (i < json.length()) {
                // skip whitespace/commas
                while (i < json.length() && (json.charAt(i) == ',' || Character.isWhitespace(json.charAt(i)))) i++;
                if (i >= json.length()) break;
                // read key
                if (json.charAt(i) != '"') break;
                int[] end = {i};
                String key = readString(json, end);
                i = end[0];
                // skip colon
                while (i < json.length() && (json.charAt(i) == ':' || Character.isWhitespace(json.charAt(i)))) i++;
                // read value
                Object[] res = readValue(json, i);
                map.put(key, res[0]);
                i = (int) res[1];
            }
        }

        public String getString(String key) { return (String) map.get(key); }
        public double getDouble(String key) { return ((Number) map.get(key)).doubleValue(); }
        public int getInt(String key) { return ((Number) map.get(key)).intValue(); }
        public JSONObject getJSONObject(String key) { return (JSONObject) map.get(key); }
        public JSONArray getJSONArray(String key) { return (JSONArray) map.get(key); }
        public boolean has(String key) { return map.containsKey(key); }

        @Override public String toString() { return map.toString(); }
    }

    public static class JSONArray {
        private final List<Object> list = new ArrayList<>();

        public JSONArray(String json) {
            json = json.trim();
            if (!json.startsWith("[")) throw new RuntimeException("Not a JSON array");
            parse(json);
        }

        JSONArray(List<Object> l) { list.addAll(l); }

        private void parse(String json) {
            json = json.trim().substring(1, json.trim().length() - 1).trim();
            int i = 0;
            while (i < json.length()) {
                while (i < json.length() && (json.charAt(i) == ',' || Character.isWhitespace(json.charAt(i)))) i++;
                if (i >= json.length()) break;
                Object[] res = readValue(json, i);
                list.add(res[0]);
                i = (int) res[1];
            }
        }

        public int length() { return list.size(); }
        public JSONObject getJSONObject(int i) { return (JSONObject) list.get(i); }
        public JSONArray getJSONArray(int i) { return (JSONArray) list.get(i); }
        public String getString(int i) { return (String) list.get(i); }
        public double getDouble(int i) { return ((Number) list.get(i)).doubleValue(); }
    }

    static String readString(String json, int[] pos) {
        int i = pos[0] + 1;
        StringBuilder sb = new StringBuilder();
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                char next = json.charAt(i + 1);
                switch (next) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    default: sb.append(next);
                }
                i += 2;
            } else if (c == '"') {
                i++;
                break;
            } else {
                sb.append(c);
                i++;
            }
        }
        pos[0] = i;
        return sb.toString();
    }

    static Object[] readValue(String json, int i) {
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
        char c = json.charAt(i);
        if (c == '"') {
            int[] pos = {i};
            String s = readString(json, pos);
            return new Object[]{s, pos[0]};
        } else if (c == '{') {
            int end = findMatchingBrace(json, i, '{', '}');
            JSONObject obj = new JSONObject(json.substring(i, end + 1));
            return new Object[]{obj, end + 1};
        } else if (c == '[') {
            int end = findMatchingBrace(json, i, '[', ']');
            JSONArray arr = new JSONArray(json.substring(i, end + 1));
            return new Object[]{arr, end + 1};
        } else if (json.startsWith("true", i)) {
            return new Object[]{Boolean.TRUE, i + 4};
        } else if (json.startsWith("false", i)) {
            return new Object[]{Boolean.FALSE, i + 5};
        } else if (json.startsWith("null", i)) {
            return new Object[]{null, i + 4};
        } else {
            // number
            int j = i;
            while (j < json.length() && "0123456789.-+eE".indexOf(json.charAt(j)) >= 0) j++;
            String num = json.substring(i, j);
            Number n = num.contains(".") || num.contains("e") || num.contains("E")
                    ? Double.parseDouble(num) : Long.parseLong(num);
            return new Object[]{n, j};
        }
    }

    static int findMatchingBrace(String json, int start, char open, char close) {
        int depth = 0;
        boolean inStr = false;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\') { i++; continue; }
            if (c == '"') { inStr = !inStr; continue; }
            if (inStr) continue;
            if (c == open) depth++;
            else if (c == close) { depth--; if (depth == 0) return i; }
        }
        throw new RuntimeException("Unmatched brace from position " + start);
    }
}
