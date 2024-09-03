package io.github.rocsg.rsmlparser;

import java.util.ArrayList;
import java.util.List;

/**
 * The Polyline class represents a polyline composed of multiple points.
 */
public class Polyline {
    private final List<Point4Parser> points; // List of points in the polyline

    /**
     * Constructs a Polyline object with an empty list of points.
     */
    public Polyline() {
        this.points = new ArrayList<>();
    }

    /**
     * Adds a point to the polyline.
     *
     * @param point The point to add.
     */
    public void addPoint(Point4Parser point) {
        this.points.add(point);
    }

    /**
     * Gets the list of points in the polyline.
     *
     * @return The list of points.
     */
    public List<Point4Parser> getPoints() {
        return points;
    }

    /**
     * Returns a string representation of the Polyline object.
     *
     * @return A string representation of the Polyline object.
     */
    @Override
    public String toString() {
        return "Polyline{" +
                "points=" + points +
                '}';
    }

    /**
     * Checks if this Polyline object is equal to another object.
     *
     * @param o The object to compare with.
     * @return True if the objects are equal, false otherwise.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Polyline polyline = (Polyline) o;
        // All points in the polyline must be equal
        for (int i = 0; i < points.size(); i++) {
            if (!points.get(i).equals(polyline.points.get(i))) {
                return false;
            }
        }
        return true;
    }
}