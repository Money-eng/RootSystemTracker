package io.github.rocsg.rsmlparser;

/**
 * The Point4Parser class represents a point in a 2D space with x and y coordinates.
 */
public class Point4Parser {
    double x; // The x-coordinate of the point
    double y; // The y-coordinate of the point

    /**
     * Constructs a Point4Parser object with the specified x and y coordinates as strings.
     *
     * @param x The x-coordinate of the point as a string.
     * @param y The y-coordinate of the point as a string.
     */
    public Point4Parser(String x, String y) {
        this.x = Double.parseDouble(x);
        this.y = Double.parseDouble(y);
    }

    /**
     * Constructs a Point4Parser object with the specified x and y coordinates as doubles.
     *
     * @param x The x-coordinate of the point as a double.
     * @param y The y-coordinate of the point as a double.
     */
    public Point4Parser(double x, double y) {
        this.x = x;
        this.y = y;
    }

    /**
     * Returns a string representation of the Point4Parser object.
     *
     * @return A string representation of the Point4Parser object.
     */
    @Override
    public String toString() {
        return "Point{" +
                "x=" + x +
                ", y=" + y +
                '}';
    }
}
// TODO : create point in 2D+t ?