package ru.practicum.moviehub.http;

import com.sun.net.httpserver.HttpServer;
import ru.practicum.moviehub.store.MoviesStore;
import ru.practicum.moviehub.http.MoviesHandler;

import java.io.IOException;
import java.net.InetSocketAddress;

public class MoviesServer {
    private final HttpServer server;

    public MoviesServer(MoviesStore moviesStore, int port) {
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/movies", new MoviesHandler(moviesStore));
        } catch (IOException e) {
            throw new RuntimeException("Не удалось создать HTTP-сервер", e);
        }
    }

    public void start() {

        server.start();
        System.out.println("HTTP Сервер запущен на порте: " + server.getAddress().getPort());
    }

    public void stop() {
        server.stop(0);
        System.out.println("HTTP Сервер остановлен!");
    }

}