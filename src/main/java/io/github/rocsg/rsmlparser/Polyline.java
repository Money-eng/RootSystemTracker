package io.github.rocsg.rsmlparser;

import java.util.ArrayList;
import java.util.List;

public class Polyline {
    private final List<Point4Parser> points;

    public Polyline() {
        this.points = new ArrayList<>();
    }

    public void addPoint(Point4Parser point) {
        this.points.add(point);
    }

    public List<Point4Parser> getPoints() {
        return points;
    }

    @Override
    public String toString() {
        return "Polyline{" +
                "points=" + points +
                '}';
    }
}
