import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Suppress deprecation warnings for this class if needed
@SuppressWarnings("deprecation")
public class CityCounterServer {

    // API endpoint (replace with valid URL and key if needed)
    private static final String API_URL = "https://samples.openweathermap.org/data/2.5/box/city?bbox=12,32,15,37,10&appid=b6907d289e10d714a6e88b30761fae22";

    // Simple class to represent the response
    static class CityCountResponse {
        private final int count;
        private final List<String> cities;

        public CityCountResponse(int count, List<String> cities) {
            this.count = count;
            this.cities = cities;
        }

        public String toJson() {
            // Using Java 17+ text blocks for cleaner JSON formatting
            return """
                {
                    "count": %d,
                    "cities": %s
                }
                """.formatted(
                count,
                cities.isEmpty() ? "[]" : "[" + String.join(", ", cities.stream().map(city -> "\"" + city + "\"").collect(Collectors.toList())) + "]"
            ).replaceAll("\\s+", ""); // Remove extra whitespace
        }
    }

    public static void main(String[] args) throws Exception {
        // Create HTTP server on port 8080
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/cities/count", new CityHandler());
        server.setExecutor(null); // Use default executor
        server.start();

        System.out.println("Server started on port 8080");
    }

    static class CityHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Handle CORS preflight (OPTIONS) request
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                handleOptions(exchange);
                return;
            }

            String query = exchange.getRequestURI().getQuery();
            String letter = "";
            if (query != null && query.startsWith("letter=")) {
                letter = query.substring(7); // Extract value after "letter="
            }

            CityCountResponse response;
            if (letter == null || letter.trim().isEmpty()) {
                response = new CityCountResponse(0, new ArrayList<>());
            } else {
                String input = letter.trim();
                List<String> allCities = fetchCitiesFromApi();
                List<String> matchingCities;
                if (input.length() == 1) {
                    // Filter cities starting with the letter (case-insensitive)
                    String firstLetter = input.toLowerCase();
                    matchingCities = allCities.stream()
                            .filter(name -> name.toLowerCase().startsWith(firstLetter))
                            .map(name -> name.substring(0, 1).toUpperCase() + name.substring(1).toLowerCase())
                            .sorted()
                            .collect(Collectors.toList());
                } else {
                    // Exact match for city name (case-insensitive)
                    String normalizedInput = input.substring(0, 1).toUpperCase() + input.substring(1).toLowerCase();
                    matchingCities = allCities.stream()
                            .filter(name -> name.equalsIgnoreCase(normalizedInput))
                            .map(name -> name.substring(0, 1).toUpperCase() + name.substring(1).toLowerCase())
                            .collect(Collectors.toList());
                }
                response = new CityCountResponse(matchingCities.size(), matchingCities);
            }

            // Send response with CORS headers
            String responseBody = response.toJson();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*"); // Allow all origins (use specific origin in production)
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
            exchange.sendResponseHeaders(200, responseBody.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBody.getBytes());
            }
        }

        private void handleOptions(HttpExchange exchange) throws IOException {
            // Handle CORS preflight request
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*"); // Allow all origins (use specific origin in production)
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
            exchange.sendResponseHeaders(204, -1); // No content for OPTIONS
        }

        private List<String> fetchCitiesFromApi() {
            List<String> cities = new ArrayList<>();
            try {
                URL url = new URL(API_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");

                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    String inputLine;
                    StringBuilder content = new StringBuilder();
                    while ((inputLine = in.readLine()) != null) {
                        content.append(inputLine);
                    }
                    in.close();

                    // Parse JSON to extract city names (basic regex approach)
                    String json = content.toString();
                    Pattern pattern = Pattern.compile("\"name\":\"([^\"]+)\"");
                    Matcher matcher = pattern.matcher(json);
                    while (matcher.find()) {
                        cities.add(matcher.group(1));
                    }
                } else {
                    System.err.println("API request failed with code: " + responseCode);
                }
                conn.disconnect();
            } catch (IOException e) {
                System.err.println("Error fetching data: " + e.getMessage());
            }
            return cities;
        }
    }
}