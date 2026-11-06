package com.minierp.backend.support.refactoring;

import java.util.regex.Pattern;
/** Rules are deliberately value/path specific; timestamps and ordinary numbers are never masked. */
public final class SnapshotMask {
    private static final Pattern UUID = Pattern.compile(
            "(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern PROJECT_KEY = Pattern.compile("(?<![A-Za-z0-9])[0-9A-F]{10}(?=-|$)");
    private static final Pattern API_PROJECT_KEY = Pattern.compile("(?<![A-Za-z0-9])X[0-9A-F]{8}(?=-|$)");
    private static final Pattern SHORT_LOGIN = Pattern.compile("\\b(chief|leader|employee|outside)[0-9a-f]{8}(?=@|$)");
    private SnapshotMask() {
    }

    public static ExactJson.Atom apply(String test, String path, ExactJson.Atom atom) {
        String field = path.substring(path.lastIndexOf('.') + 1).replaceAll("\\[.*", "");
        if (!atom.kind().equals("string")) {
            return atom;
        }
        String text = atom.text();
        if (UUID.matcher(text).find()) {
            SafetyNetRun.mask(test, path, "random UUID in test fixture");
            text = UUID.matcher(text).replaceAll("<UUID>");
        }
        String next = SHORT_LOGIN.matcher(text).replaceAll("$1<UUID8>");
        if (!next.equals(text)) {
            SafetyNetRun.mask(test, path, "M1AcceptanceTest login fixture prefix + random UUID8");
        }
        text = next;
        next = PROJECT_KEY.matcher(text).replaceAll("<PROJECT_KEY>");
        next = API_PROJECT_KEY.matcher(next).replaceAll("<API_PROJECT_KEY>");
        if (!next.equals(text)) {
            SafetyNetRun.mask(test, path, "BoardTestSupport/M4BoardApiTest UUID-derived project key");
        }
        text = next;
        if (field.equals("accessToken")) {
            SafetyNetRun.mask(test, path, "signed access token; never persist bearer credentials");
            text = "<ACCESS_TOKEN>";
        }
        return new ExactJson.Atom(atom.kind(), text);
    }

    public static String path(String path) {
        return UUID.matcher(path).replaceAll("<UUID>");
    }
}
