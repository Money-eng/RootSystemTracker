package io.github.rocsg.rsmlparser;

import io.github.rocsg.fijiyama.registration.ItkTransform;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

public class Geometry {
    private final List<Polyline> polylines;
    public List<Double> totalLength;

    public Geometry() {
        this.polylines = new ArrayList<>();
        this.totalLength = new ArrayList<>();
    }

    public void addPolyline(Polyline polyline) {
        this.polylines.add(polyline);
    }

    public List<Point2D> get2Dpt() {
        List<Point2D> nodes = new ArrayList<>();
        for (Polyline polyline : polylines) {
            for (Point4Parser point : polyline.getPoints()) {
                nodes.add(new Point2D.Double(point.x, point.y));
            }
        }
        return nodes;
    }

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

    @Override
    public String toString() {
        return "Geometry{" +
                "polylines=" + polylines +
                '}';
    }

    public void applyTransform(ItkTransform transform, int time) {
        for (Polyline polyline : polylines) {
            for (Point4Parser point : polyline.getPoints()) {
                double[] pt = transform.transformPoint(new double[]{point.x, point.y, 0});
                point.x +=point.x - pt[0];
                point.y += point.y - pt[1];
            }
        }
    }

    public void scale(double scaleFactor) {
        for (Polyline polyline : polylines) {
            for (Point4Parser point : polyline.getPoints()) {
                point.x /= scaleFactor;
                point.y /= scaleFactor;
            }
        }
    }

    // redefine equals
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        Geometry geometry = (Geometry) obj;
        return polylines.equals(geometry.polylines);// && totalLength.equals(geometry.totalLength);
    }
}
