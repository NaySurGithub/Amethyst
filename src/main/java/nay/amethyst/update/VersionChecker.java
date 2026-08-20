package nay.amethyst.update;

import nay.amethyst.AmethystPlugin;
import org.powernukkitx.utils.JSONUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

public final class VersionChecker {
    private static final URI LATEST_RELEASE = URI.create(
            "https://api.github.com/repos/NaySurGithub/Amethyst/releases/latest");

    private VersionChecker() {
    }

    public static void checkAsync(AmethystPlugin plugin) {
        String current = plugin.getDescription().getVersion();
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        HttpRequest request = HttpRequest.newBuilder(LATEST_RELEASE)
                .timeout(Duration.ofSeconds(5))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Amethyst/" + current)
                .GET()
                .build();

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .orTimeout(6, TimeUnit.SECONDS)
                .thenAccept(response -> handle(plugin, current, response))
                .exceptionally(error -> {
                    plugin.getLogger().debug("Unable to check for Amethyst updates: " + error.getMessage());
                    return null;
                });
    }

    private static void handle(AmethystPlugin plugin, String current,
                               HttpResponse<String> response) {
        if (response.statusCode() != 200) {
            plugin.getLogger().debug("Unable to check for Amethyst updates: GitHub returned "
                    + response.statusCode());
            return;
        }
        GithubRelease release = JSONUtils.fromLenient(response.body(), GithubRelease.class);
        if (release == null || release.tag_name == null) return;
        String latest = stripPrefix(release.tag_name);
        if (compare(latest, stripPrefix(current)) <= 0) return;

        String url = release.html_url == null
                ? "https://github.com/NaySurGithub/Amethyst/releases/latest" : release.html_url;
        plugin.getLogger().warning("A new Amethyst version is available: " + release.tag_name
                + " (running v" + stripPrefix(current) + ") " + url);
    }

    private static int compare(String first, String second) {
        String[] left = first.split("[-+]", 2)[0].split("\\.");
        String[] right = second.split("[-+]", 2)[0].split("\\.");
        for (int index = 0; index < Math.max(left.length, right.length); index++) {
            int leftPart = index < left.length ? number(left[index]) : 0;
            int rightPart = index < right.length ? number(right[index]) : 0;
            if (leftPart != rightPart) return Integer.compare(leftPart, rightPart);
        }
        return 0;
    }

    private static int number(String part) {
        String digits = part.replaceFirst("[^0-9].*$", "");
        if (digits.isEmpty()) return 0;
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String stripPrefix(String version) {
        return version != null && (version.startsWith("v") || version.startsWith("V"))
                ? version.substring(1) : version;
    }

    private static final class GithubRelease {
        private String tag_name;
        private String html_url;
    }
}
