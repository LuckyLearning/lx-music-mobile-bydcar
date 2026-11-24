package lx.compat;

interface IBroker {
    String runScript(String script, String requestJson);
    String searchKg(String query);
    String search(String source, String query);
    String resolveReviewed(String requestJson);
    String resolveScript(String script, String requestJson);
    String playKg(String hash, String quality);
    void installFixtureResolver(String mode);
}
