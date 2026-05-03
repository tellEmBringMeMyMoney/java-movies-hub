package ru.practicum.moviehub.http;

import com.google.gson.JsonParseException;
import com.sun.net.httpserver.HttpExchange;
import ru.practicum.moviehub.store.MoviesStore;
import ru.practicum.moviehub.api.ErrorResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;

public class MoviesHandler extends BaseHttpHandler {

    private static final int MIN_YEAR = 1888;
    private static final int TITLE_MAX_LEN = 100;

    private final MoviesStore moviesStore;

    public MoviesHandler(MoviesStore moviesStore) {
        this.moviesStore = moviesStore;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if ("/movies".equals(path) || "/movies/".equals(path)) {
            handleCollection(ex);
            return;
        }
        if (path.startsWith("/movies/")) {
            handleById(ex, path.substring("/movies/".length()));
            return;
        }
        sendJson(ex, 404, new ErrorResponse("Запрос не поддерживается"));
    }

    private void handleCollection(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        if ("GET".equalsIgnoreCase(method)) {
            String yearParam = extractYearQueryParam(ex);
            if (yearParam == null) {
                sendJson(ex, 200, moviesStore.getAll());
                return;
            }
            Integer year = parseIntOrNull(yearParam);
            if (year == null) {
                sendJson(ex, 400, new ErrorResponse("Некорректный параметр запроса - 'year'"));
                return;
            }
            sendJson(ex, 200, moviesStore.getByYear(year));
            return;
        }

        if ("POST".equalsIgnoreCase(method)) {
            String contentType = ex.getRequestHeaders().getFirst("Content-Type");
            if (contentType == null || !contentType.toLowerCase().startsWith("application/json")) {
                sendJson(ex, 415, new ErrorResponse("Неподдерживаемый формат"));
                return;
            }

            String rawBody = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            CreateMovieRequest request;
            try {
                request = GSON.fromJson(rawBody, CreateMovieRequest.class);
            } catch (JsonParseException parseException) {
                sendJson(ex, 422, new ErrorResponse("Ошибка валидации: Некорректный JSON"));
                return;
            }

            List<String> details = validateCreateRequest(request);
            if (!details.isEmpty()) {
                sendJson(ex, 422, new ErrorResponse("Ошибка валидации: " + String.join("; ", details)));
                return;
            }

            var created = moviesStore.add(request.title.trim(), request.year);
            sendJson(ex, 201, created);
            return;
        }

        sendJson(ex, 405, new ErrorResponse("Метод не доступен"));
    }

    private void handleById(HttpExchange ex, String rawId) throws IOException {
        Integer id = parseIntOrNull(rawId);
        if (id == null) {
            sendJson(ex, 400, new ErrorResponse("Некорректный ID"));
            return;
        }

        String method = ex.getRequestMethod();
        if ("GET".equalsIgnoreCase(method)) {
            var movie = moviesStore.getById(id);
            if (movie == null) {
                sendJson(ex, 404, new ErrorResponse("Фильм с ID '" + id + "' не найден"));
                return;
            }
            sendJson(ex, 200, movie);
            return;
        }

        if ("DELETE".equalsIgnoreCase(method)) {
            boolean deleted = moviesStore.deleteById(id);
            if (!deleted) {
                sendJson(ex, 404, new ErrorResponse("Фильм с ID '" + id + "' не найден"));
                return;
            }
            sendNoContent(ex);
            return;
        }

        sendJson(ex, 405, new ErrorResponse("Метод не доступен"));
    }

    private String extractYearQueryParam(HttpExchange ex) {
        String rawQuery = ex.getRequestURI().getRawQuery();
        if (rawQuery == null || rawQuery.isBlank()) {
            return null;
        }
        for (String part : rawQuery.split("&")) {
            String[] keyValue = part.split("=", 2);
            if ("year".equals(keyValue[0])) {
                return keyValue.length > 1 ? keyValue[1] : "";
            }
        }
        return null;
    }

    private List<String> validateCreateRequest(CreateMovieRequest request) {
        List<String> details = new ArrayList<>();
        if (request == null) {
            details.add("Тело запроса отсутствует");
            return details;
        }

        String title = request.title == null ? "" : request.title.trim();
        if (title.isEmpty()) {
            details.add("Название не должно быть пустым");
        } else if (title.length() > TITLE_MAX_LEN) {
            details.add("Название не должно быть длиннее 100 символов");
        }

        int maxYear = Year.now().getValue() + 1;
        if (request.year < MIN_YEAR || request.year > maxYear) {
            details.add("Год должен быть между 1888 и " + maxYear);
        }
        return details;
    }

    private Integer parseIntOrNull(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static class CreateMovieRequest {
        String title;
        int year;
    }
}
