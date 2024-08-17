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

    // redefine equals method
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Polyline polyline = (Polyline) o;
        // all points in the polyline must be equal
        for (int i = 0; i < points.size(); i++) {
            if (!points.get(i).equals(polyline.points.get(i))) {
                return false;
            }
        }
        return true;
    }
}
