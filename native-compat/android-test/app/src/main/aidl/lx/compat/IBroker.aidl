package lx.compat;

interface IBroker {
    String runScript(String script, String requestJson);
    String searchKg(String query);
    String resolveReviewed(String requestJson);
    String playKg(String hash, String quality);
    void installFixtureResolver(String mode);
}
