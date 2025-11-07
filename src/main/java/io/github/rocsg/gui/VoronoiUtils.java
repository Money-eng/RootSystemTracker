package io.github.rocsg.gui;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.triangulate.VoronoiDiagramBuilder;

import ij.ImagePlus;
import ij.gui.Line;
import ij.gui.OvalRoi;
import ij.gui.Overlay;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.process.ImageProcessor;
import ij.plugin.RGBStackMerge;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class VoronoiUtils {

    /**
     * Calcule et superpose sur l'image de base le diagramme de Voronoï à partir d'une liste de points
     * (sites RSML). Pour chaque cellule, le centroïde est calculé et marqué d'une croix bleue.
     * De plus, tous les points d'annotation RSML sont affichés sous forme de petits disques verts.
     *
     * @param baseImg L'image de base.
     * @param points  La liste des points (en pixels, déjà mis à l'échelle) issus des annotations RSML.
     * @return L'image de base dupliquée avec un overlay contenant les cellules de Voronoï, leurs centroïdes
     *         et les points RSML.
     */
    public static ImagePlus createVoronoiOverlay(ImagePlus baseImg, List<Point2D.Double> points) {
        // Création d'une collection de sites sous forme de coordonnées JTS.
        GeometryFactory geomFactory = new GeometryFactory();
        List<Coordinate> coords = new ArrayList<>();
        for (Point2D.Double pt : points) {
            coords.add(new Coordinate(pt.x, pt.y));
        }
        Geometry sites = geomFactory.createMultiPointFromCoords(coords.toArray(new Coordinate[0]));

        // Configuration et calcul du diagramme de Voronoï.
        VoronoiDiagramBuilder vdb = new VoronoiDiagramBuilder();
        vdb.setSites(sites);
        double width = baseImg.getWidth();
        double height = baseImg.getHeight();
        Envelope clipEnvelope = new Envelope(0, width, 0, height);
        vdb.setClipEnvelope(clipEnvelope);
        Geometry diagram = vdb.getDiagram(geomFactory);

        // Création d'un overlay pour dessiner le résultat.
        Overlay overlay = new Overlay();

        // Parcours de chaque cellule du diagramme pour l'afficher et y ajouter le centroïde.
        int crossSize = 5;
        for (int i = 0; i < diagram.getNumGeometries(); i++) {
            Geometry cell = diagram.getGeometryN(i);
            // Conversion de la cellule en PolygonRoi.
            PolygonRoi roiCell = convertGeometryToPolygonRoi(cell);
            roiCell.setStrokeColor(Color.RED);
            roiCell.setStrokeWidth(1);
            overlay.add(roiCell);

            // Calcul et affichage du centroïde (croix bleue).
            Coordinate centroidCoord = cell.getCentroid().getCoordinate();
            int cx = (int) Math.round(centroidCoord.x);
            int cy = (int) Math.round(centroidCoord.y);
            Roi horizontal = new Line(cx - crossSize, cy, cx + crossSize, cy);
            horizontal.setStrokeColor(Color.BLUE);
            horizontal.setStrokeWidth(1);
            Roi vertical = new Line(cx, cy - crossSize, cx, cy + crossSize);
            vertical.setStrokeColor(Color.BLUE);
            vertical.setStrokeWidth(1);
            overlay.add(horizontal);
            overlay.add(vertical);
        }

        // Ajout des points d'annotation RSML (par exemple, petits disques verts).
        int markerRadius = 1;
        for (Point2D.Double pt : points) {
            int x = (int) Math.round(pt.x);
            int y = (int) Math.round(pt.y);
            // Création d'un OvalRoi centré sur le point.
            OvalRoi marker = new OvalRoi(x - markerRadius, y - markerRadius, markerRadius * 2, markerRadius * 2);
            marker.setStrokeColor(Color.GREEN);
            marker.setFillColor(Color.GREEN);
            overlay.add(marker);
        }

        // Duplique l'image de base et y applique l'overlay.
        ImagePlus result = baseImg.duplicate();
        result.setOverlay(overlay);
        result.setTitle(baseImg.getTitle() + "_Voronoi");
        //result.show();
        return result;
    }

    /**
     * Convertit une géométrie JTS (par exemple, une cellule de Voronoï) en PolygonRoi ImageJ.
     *
     * @param geom La géométrie à convertir.
     * @return Le PolygonRoi correspondant.
     */
    public static PolygonRoi convertGeometryToPolygonRoi(Geometry geom) {
        Coordinate[] coords = geom.getCoordinates();
        int n = coords.length;
        int[] xPoints = new int[n];
        int[] yPoints = new int[n];
        for (int i = 0; i < n; i++) {
            xPoints[i] = (int) Math.round(coords[i].x);
            yPoints[i] = (int) Math.round(coords[i].y);
        }
        return new PolygonRoi(xPoints, yPoints, n, Roi.POLYGON);
    }
}
