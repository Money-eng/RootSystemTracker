package io.github.rocsg.rsmlparser;

import io.github.rocsg.fijiyama.registration.ItkTransform;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

/**
 * The Geometry class represents a geometric structure composed of multiple polylines.
 */
public class Geometry {
    private final List<Polyline> polylines; // List of polylines in the geometry
    public List<Double> totalLength; // List of total lengths of each polyline

    /**
     * Constructs a Geometry object with an empty list of polylines and total lengths.
     */
    public Geometry() {
        this.polylines = new ArrayList<>();
        this.totalLength = new ArrayList<>();
    }

    /**
     * Adds a polyline to the geometry.
     *
     * @param polyline The polyline to add.
     */
    public void addPolyline(Polyline polyline) {
        this.polylines.add(polyline);
    }

    /**
     * Gets a list of 2D points from all polylines in the geometry.
     *
     * @return A list of 2D points.
     */
    public List<Point2D> get2Dpt() {
        List<Point2D> nodes = new ArrayList<>();
        for (Polyline polyline : polylines) {
            for (Point4Parser point : polyline.getPoints()) {
                nodes.add(new Point2D.Double(point.x, point.y));
            }
        }
        return nodes;
    }

    /**
     * Calculates and returns the total length of each polyline in the geometry.
     *
     * @return A list of total lengths.
     */
    public List<Double> getTotalLength() {
        for (Polyline polyline : polylines) {
            double length = 0;
            for (int i = 0; i < polyline.getPoints().size() - 1; i++) {
                Point4Parser point1 = polyline.getPoints().get(i);
                Point4Parser point2 = polyline.getPoints().get(i + 1);
                length += Math.sqrt(Math.pow(point1.x - point2.x, 2) + Math.pow(point1.y - point2.y, 2));
            }
            totalLength.add(length);
        }
        return totalLength;
    }

    /**
     * Returns a string representation of the Geometry object.
     *
     * @return A string representation of the object.
     */
    @Override
    public String toString() {
        return "Geometry{" +
                "polylines=" + polylines +
                '}';
    }

    /**
     * Applies a transformation to the geometry using the specified transform and time index.
     *
     * @param transform The transformation to apply.
     * @param time The time index for the transformation.
     */
    public void applyTransform(ItkTransform transform, int time) {
        for (Polyline polyline : polylines) {
            for (Point4Parser point : polyline.getPoints()) {
                double[] pt = transform.transformPoint(new double[]{point.x, point.y, 0});
                point.x += point.x - pt[0];
                point.y += point.y - pt[1];
            }
        }
    }

    /**
     * Scales the geometry by the given scale factor.
     *
     * @param scaleFactor The scale factor to apply.
     */
    public void scale(double scaleFactor) {
        for (Polyline polyline : polylines) {
            for (Point4Parser point : polyline.getPoints()) {
                point.x /= scaleFactor;
                point.y /= scaleFactor;
            }
        }
    }

    /**
     * Checks if this Geometry object is equal to another object.
     *
     * @param obj The object to compare with.
     * @return True if the objects are equal, false otherwise.
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        Geometry geometry = (Geometry) obj;
        return polylines.equals(geometry.polylines); // && totalLength.equals(geometry.totalLength);
    }
}