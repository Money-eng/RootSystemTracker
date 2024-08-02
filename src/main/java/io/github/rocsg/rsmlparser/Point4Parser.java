package io.github.rocsg.rsmlparser;

public class Point4Parser {
    final double x;
    final double y;

    public Point4Parser(String x, String y) {
        this.x = Double.parseDouble(x);
        this.y = Double.parseDouble(y);
    }

    public Point4Parser(double x, double y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public String toString() {
        return "Point{" +
                "x=" + x +
                ", y=" + y +
                '}';
    }
}
