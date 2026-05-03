package ru.practicum.moviehub.store;

import ru.practicum.moviehub.model.Movie;

import java.util.*;
import java.util.stream.Collectors;

public class MoviesStore {

    private final Map<Integer, Movie> movies = new HashMap<>();
    private int nextId = 1;

    public List<Movie> getAll() {
        return Collections.unmodifiableList(
                (List<? extends Movie>) movies.values().stream()
                        .sorted(Comparator.comparingInt(Movie::id))
                        .collect(Collectors.toCollection(ArrayList::new))
        );
    }

    public List<Movie> getByYear(int year) {
        return Collections.unmodifiableList(
                (List<? extends Movie>) movies.values().stream()
                        .filter(m -> m.year() == year)
                        .sorted(Comparator.comparingInt(Movie::id))
                        .collect(Collectors.toCollection(ArrayList::new))
        );
    }

    public Movie add(String title, int year) {
        int id = nextId++;
        Movie movie = new Movie(id, title, year);
        movies.put(id, movie);
        return movie;
    }

    public Movie getById(int id) {
        return movies.get(id);
    }

    public boolean deleteById(int id) {
        return movies.remove(id) != null;
    }

    public void clear() {
        movies.clear();
        nextId = 1;
    }
}