package example.infrai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class InfraiClient {
    private final HttpClient http = HttpClient.newHttpClient();
    private final String baseUrl;
    private final String apiKey;

    public InfraiClient(String baseUrl, String apiKey) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
    }

    public String call(String method, String path, String body) throws IOException, InterruptedException {
        for (int attempt = 0; attempt < 4; attempt++) {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                            : HttpRequest.BodyPublishers.ofString(body));
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            String envelope = response.body();
            if (!envelope.contains("\"ok\":true")) {
                String error = extract(envelope, "code");
                if (response.statusCode() == 429 && attempt < 3) {
                    Thread.sleep(retryMillis(response, attempt));
                    continue;
                }
                throw new IOException("Infrai request rejected: " + error);
            }
            if (response.statusCode() >= 500 && attempt < 3) {
                Thread.sleep(retryMillis(response, attempt));
                continue;
            }
            return envelope;
        }
        throw new IOException("Infrai request did not complete");
    }

    private long retryMillis(HttpResponse<String> response, int attempt) {
        try {
            return Long.parseLong(response.headers().firstValue("Retry-After").orElse("0")) * 1000L;
        } catch (NumberFormatException ignored) {
            return 250L * (1L << attempt);
        }
    }

    public static String extract(String json, String field) {
        String marker = "\"" + field + "\":";
        int start = json.indexOf(marker);
        if (start < 0) return "";
        start += marker.length();
        while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '"')) start++;
        int end = start;
        while (end < json.length() && json.charAt(end) != '"' && json.charAt(end) != ',' && json.charAt(end) != '}') end++;
        return json.substring(start, end);
    }
}
