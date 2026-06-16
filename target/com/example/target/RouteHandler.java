package com.example.target;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class RouteHandler implements HttpHandler {

    private final MathService mathService = new MathService();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String response;
        int statusCode;

        try {
            Map<String, String> params = parseQuery(exchange.getRequestURI());
            String op = params.get("op");
            double a = Double.parseDouble(params.get("a"));
            double b = Double.parseDouble(params.get("b"));

            double result = mathService.calculate(op, a, b);

            response = "{\"op\":\"" + op + "\",\"a\":" + a + ",\"b\":" + b
                    + ",\"result\":" + result + "}";
            statusCode = 200;
        } catch (Exception e) {
            response = "{\"error\":\"" + e.getMessage() + "\"}";
            statusCode = 400;
        }

        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private Map<String, String> parseQuery(URI uri) {
        Map<String, String> result = new HashMap<>();
        String query = uri.getQuery();
        if (query == null) {
            return result;
        }
        for (String pair : query.split("&")) {
            int idx = pair.indexOf('=');
            if (idx > 0) {
                String key = pair.substring(0, idx);
                String value = pair.substring(idx + 1);
                result.put(key, value);
            }
        }
        return result;
    }
}
