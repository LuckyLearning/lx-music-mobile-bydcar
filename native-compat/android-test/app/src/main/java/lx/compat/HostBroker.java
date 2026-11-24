package lx.compat;

import java.net.URI;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Policy lives in the caller process. The isolated runtime has no method that can change this set.
 * A script-supplied policy string is ignored on purpose.
 */
public final class HostBroker {
    private final Set<String> scriptCleartext;
    private int generation = 1;
    private int dropped;

    public HostBroker(Set<String> scriptCleartextHosts) {
        HashSet<String> copy = new HashSet<String>();
        if (scriptCleartextHosts != null) {
            for (String host : scriptCleartextHosts) {
                if (host != null) copy.add(host.toLowerCase(Locale.US));
            }
        }
        this.scriptCleartext = Collections.unmodifiableSet(copy);
    }

    public int generation() {
        return generation;
    }

    public void bump() {
        generation++;
    }

    public int dropped() {
        return dropped;
    }

    public boolean acceptResult(int resultGeneration) {
        if (resultGeneration != generation) {
            dropped++;
            return false;
        }
        return true;
    }

    public String admitScriptRequest(String url, String ignoredScriptPolicy) {
        if (url == null || url.length() > 2048) return "rejected";
        URI uri;
        try {
            uri = new URI(url);
        } catch (Exception e) {
            return "rejected";
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.US);
        if (host.isEmpty() || uri.getUserInfo() != null) return "rejected";
        if (isPrivateHost(host)) return "rejected-private";
        if ("http".equalsIgnoreCase(uri.getScheme())) {
            if (scriptCleartext.contains(host)) return "scoped-cleartext";
            return "rejected-cleartext";
        }
        if ("https".equalsIgnoreCase(uri.getScheme())) return "https";
        return "rejected";
    }

    private static boolean isPrivateHost(String host) {
        return "localhost".equals(host)
            || host.endsWith(".local")
            || host.endsWith(".internal")
            || host.equals("127.0.0.1")
            || host.startsWith("10.")
            || host.startsWith("192.168.")
            || host.startsWith("169.254.")
            || host.equals("::1")
            || host.equals("[::1]");
    }
}
