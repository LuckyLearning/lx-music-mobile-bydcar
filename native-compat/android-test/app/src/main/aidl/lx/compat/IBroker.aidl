package lx.compat;

interface IBroker {
    String runScript(String script, String requestJson);
    String searchKg(String query);
    String search(String source, String query);
    String playlists(String source);
    String playlistSongs(String source, String id);
    String resolveReviewed(String requestJson);
    String resolveScript(String script, String requestJson);
    String installUserScript(String script);
    String scriptInfo();
    String resolveStored(String requestJson);
    String playKg(String hash, String quality);
    void installFixtureResolver(String mode);
}
