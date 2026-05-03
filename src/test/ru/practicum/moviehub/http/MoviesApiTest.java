package ru.practicum.moviehub.http;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Year;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class MoviesApiTest {

    private static final String BASE = "http://localhost:8080";
    private static final Gson GSON = new Gson();
    private static MoviesServer server;
    private static MoviesStore moviesStore;
    private static HttpClient client;

    @BeforeAll
    static void beforeAll() {
        moviesStore = new MoviesStore();
        server = new MoviesServer(moviesStore, 8080);
        server.start();
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }

    @BeforeEach
    void beforeEach() {
        moviesStore.clear();
    }

    @AfterAll
    static void afterAll() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void getMovies_getEmptyArray_whenEmptyRequest() throws Exception {
        HttpResponse<String> resp = sendGet("/movies");

        assertEquals(200, resp.statusCode(), "GET /movies should return 200");
        assertJsonContentType(resp);
        List<Movie> movies = parseMovieList(resp.body());
        assertTrue(movies.isEmpty(), "Empty storage returns empty List");
    }

    @Test
    void postMovie_correctBody_getCorrectMovie() throws Exception {
        int year = 2006;
        HttpResponse<String> resp = sendPost("/movies", "{\"title\":\"Cars\",\"year\":" + year + "}", "application/json");

        assertEquals(201, resp.statusCode(), "POST /movies returns 201");
        assertJsonContentType(resp);

        Movie created = GSON.fromJson(resp.body(), Movie.class);
        assertNotNull(created, "body not empty");
        assertTrue(created.id() > 0, "ID is positive number");
        assertEquals("Cars", created.title(), "same title");
        assertEquals(2006, created.year(), "same year");
    }

    @Test
    void getMovies_returnsLastAddedMovie() throws Exception {
        int year = Year.now().getValue();
        sendPost("/movies", "{\"title\":\"Movie A\",\"year\":" + year + "}", "application/json");
        sendPost("/movies", "{\"title\":\"Movie B\",\"year\":" + year + "}", "application/json");

        HttpResponse<String> resp = sendGet("/movies");
        assertEquals(200, resp.statusCode());
        assertJsonContentType(resp);

        List<Movie> movies = parseMovieList(resp.body());
        assertEquals(2, movies.size(), "Both prev added movies shold return");
    }

    @Test
    void postMovie_noTitle_422() throws Exception {
        HttpResponse<String> resp = sendPost("/movies", "{\"title\":\"   \",\"year\":2020}", "application/json");

        assertEquals(422, resp.statusCode());
        assertJsonContentType(resp);
        assertValidationError(resp, "Название не должно быть пустым");
    }

    @Test
    void postMovie_withTooLongTitle_returns422() throws Exception {
        String longTitle = "a".repeat(101);
        HttpResponse<String> resp = sendPost("/movies", "{\"title\":\"" + longTitle + "\",\"year\":2020}", "application/json");

        assertEquals(422, resp.statusCode());
        assertJsonContentType(resp);
        assertValidationError(resp, "Название не должно быть длиннее 100 символов");
    }

    @Test
    void postMovie_withInvalidYear_returns422() throws Exception {
        int invalidYear = Year.now().getValue() + 3;
        HttpResponse<String> resp = sendPost("/movies", "{\"title\":\"Valid title\",\"year\":" + invalidYear + "}", "application/json");

        assertEquals(422, resp.statusCode());
        assertJsonContentType(resp);
        assertValidationError(resp, "Год должен быть между 1888");
    }

    @Test
    void postMovie_withUnsupportedContentType_returns415() throws Exception {
        HttpResponse<String> resp = sendPost("/movies", "{\"title\":\"A\",\"year\":2020}", "text/plain");

        assertEquals(415, resp.statusCode());
        assertJsonContentType(resp);
        assertError(resp, "Неподдерживаемый формат");
    }

    @Test
    void postMovie_withMalformedJson_returns422() throws Exception {
        HttpResponse<String> resp = sendPost("/movies", "{\"title\":\"A\",\"year\":", "application/json");

        assertEquals(422, resp.statusCode());
        assertJsonContentType(resp);
        assertValidationError(resp, "Некорректный JSON");
    }

    @Test
    void getMovieById_whenExists_returnsMovie() throws Exception {
        HttpResponse<String> createResp = sendPost("/movies", "{\"title\":\"Arrival\",\"year\":2016}", "application/json");
        Movie created = GSON.fromJson(createResp.body(), Movie.class);

        HttpResponse<String> resp = sendGet("/movies/" + created.id());
        assertEquals(200, resp.statusCode());
        assertJsonContentType(resp);

        Movie movie = GSON.fromJson(resp.body(), Movie.class);
        assertEquals(created.id(), movie.id());
        assertEquals("Arrival", movie.title());
    }

    @Test
    void getMovieById_whenNotFound_returns404() throws Exception {
        HttpResponse<String> resp = sendGet("/movies/9999");

        assertEquals(404, resp.statusCode());
        assertJsonContentType(resp);
        assertError(resp, "Фильм с ID '9999' не найден");
    }

    @Test
    void getMovieById_whenIdNotNumber_returns400() throws Exception {
        HttpResponse<String> resp = sendGet("/movies/abc");

        assertEquals(400, resp.statusCode());
        assertJsonContentType(resp);
        assertError(resp, "Некорректный ID");
    }

    @Test
    void deleteMovie_whenExists_returns204AndRemovesMovie() throws Exception {
        HttpResponse<String> createResp = sendPost("/movies", "{\"title\":\"Dune\",\"year\":2021}", "application/json");
        Movie created = GSON.fromJson(createResp.body(), Movie.class);

        HttpResponse<String> deleteResp = sendDelete("/movies/" + created.id());
        assertEquals(204, deleteResp.statusCode());
        assertJsonContentType(deleteResp);

        HttpResponse<String> getResp = sendGet("/movies/" + created.id());
        assertEquals(404, getResp.statusCode());
    }

    @Test
    void deleteMovie_whenNotFound_returns404() throws Exception {
        HttpResponse<String> resp = sendDelete("/movies/12345");
        assertEquals(404, resp.statusCode());
        assertJsonContentType(resp);
        assertError(resp, "Фильм с ID '12345' не найден");
    }

    @Test
    void deleteMovie_whenIdNotNumber_returns400() throws Exception {
        HttpResponse<String> resp = sendDelete("/movies/not-a-number");
        assertEquals(400, resp.statusCode());
        assertJsonContentType(resp);
        assertError(resp, "Некорректный ID");
    }

    @Test
    void getMoviesByYear_whenYearProvided_returnsOnlyMatchingMovies() throws Exception {
        sendPost("/movies", "{\"title\":\"Old\",\"year\":2001}", "application/json");
        sendPost("/movies", "{\"title\":\"New\",\"year\":2002}", "application/json");
        sendPost("/movies", "{\"title\":\"Old 2\",\"year\":2001}", "application/json");

        HttpResponse<String> resp = sendGet("/movies?year=2001");
        assertEquals(200, resp.statusCode());
        assertJsonContentType(resp);

        List<Movie> movies = parseMovieList(resp.body());
        assertEquals(2, movies.size());
        assertTrue(movies.stream().allMatch(m -> m.year() == 2001));
    }

    @Test
    void getMoviesByYear_whenNoMoviesForYear_returnsEmptyArray() throws Exception {
        sendPost("/movies", "{\"title\":\"Only\",\"year\":2005}", "application/json");
        HttpResponse<String> resp = sendGet("/movies?year=2010");
        assertEquals(200, resp.statusCode());
        assertJsonContentType(resp);
        assertTrue(parseMovieList(resp.body()).isEmpty());
    }

    @Test
    void getMoviesByYear_whenYearNotNumber_returns400() throws Exception {
        HttpResponse<String> resp = sendGet("/movies?year=abcd");
        assertEquals(400, resp.statusCode());
        assertJsonContentType(resp);
        assertError(resp, "Некорректный параметр запроса - 'year'");
    }

    @Test
    void unsupportedMethod_returns405() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + "/movies"))
                .method("PUT", HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(405, resp.statusCode());
        assertJsonContentType(resp);
        assertError(resp, "Метод не доступен");
    }


    private HttpResponse<String> sendGet(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + path))
                .GET()
                .build();
        return client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> sendDelete(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + path))
                .DELETE()
                .build();
        return client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> sendPost(String path, String body, String contentType) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE + path))
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private List<Movie> parseMovieList(String json) {
        String body = json.trim();
        assertTrue(body.startsWith("[") && body.endsWith("]"), "Ожидается JSON-массив");
        return GSON.fromJson(body, new ListOfMoviesTypeToken().getType());
    }

    private void assertJsonContentType(HttpResponse<String> resp) {
        String contentTypeHeaderValue = resp.headers().firstValue("Content-Type").orElse("");
        assertEquals("application/json; charset=UTF-8", contentTypeHeaderValue,
                "Content-Type должен содержать формат данных и кодировку");
    }

    private void assertError(HttpResponse<String> resp, String expectedError) {
        JsonObject json = GSON.fromJson(resp.body(), JsonObject.class);
        assertEquals(expectedError, json.get("error").getAsString());
    }

    private void assertValidationError(HttpResponse<String> resp, String detailsContains) {
        JsonObject json = GSON.fromJson(resp.body(), JsonObject.class);
        String error = json.get("error").getAsString();
        assertTrue(error.startsWith("Ошибка валидации"),
                "Ошибка должна начинаться с 'Ошибка валидации'");
        assertTrue(error.contains(detailsContains),
                "В ошибке должна быть причина: " + detailsContains);
    }
}