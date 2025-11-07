package io.github.rocsg.gui;

import ij.ImagePlus;
import ij.gui.Plot;
import io.github.rocsg.rsml.RSML2DplusT.Node;
import io.github.rocsg.rsml.RSML2DplusT.Root;
import io.github.rocsg.rsml.RSML2DplusT.RootModel;
import org.scijava.vecmath.Point3d;
import java.util.*;

/**
 * Classe pour caractériser le modèle de racines.
 */
public class RootModelCaracterization {
    private ImagePlus image = null;
    private final RootModel rootModel;
    private double circleRadius;

    public RootModelCaracterization(RootModel rootModel) {
        this.rootModel = rootModel;
    }

    public RootModelCaracterization(ImagePlus image, RootModel rootModel, double circleRadius) {
        this.image = image;
        this.rootModel = rootModel;
        this.circleRadius = circleRadius;
    }

    /**
     * Estime les nœuds isolés de chaque racine.
     *
     * @return Map contenant les racines et leurs nœuds isolés.
     */
    public Map<Root, HashSet<Node>> estimateIsolatedNodes() {
        circleRadius = rootModel.getMeanSpacingBetweenNodes();

        HashMap<Root, HashSet<Node>> isolatedNodes = new HashMap<>();
        for (Root r : rootModel.rootList) {
            isolatedNodes.put(r, new HashSet<>());
            Node n = r.firstNode;
            while (n != null) {
                Node closestNodeNotInRoot = (Node) rootModel.getClosestNodeInOtherRoot(new Point3d(n.x, n.y, n.birthTime), r)[0];
                if (closestNodeNotInRoot != null) {
                    double distance = Math.sqrt(Math.pow(n.x - closestNodeNotInRoot.x, 2) + Math.pow(n.y - closestNodeNotInRoot.y, 2));
                    if (distance > circleRadius) {
                        isolatedNodes.get(r).add(n);
                    }
                }
                n = n.child;
            }
        }
        return isolatedNodes;
    }

    public void plotHistogramOfIsolatedNodes() {
        Map<Root, HashSet<Node>> isolatedNodes = estimateIsolatedNodes();
        Map<Node, List<Float>> pixelValues = new HashMap<>();
        for (Root r : isolatedNodes.keySet()) {
            HashSet<Node> nodes = isolatedNodes.get(r);
            List<Float> pixelValuesST = new ArrayList<>();
            for (Node n : nodes) {
                for (int i = 1; i <= this.image.getNSlices(); i++) {
                    pixelValuesST.add(this.image.getImageStack().getProcessor(i).getPixelValue((int) (n.x + 0.5) * 2 - 3, (int) (n.y + 0.5) * 2 - 3));
                }
                pixelValues.put(n, pixelValuesST);
                pixelValuesST = new ArrayList<>();
            }
        }

        // plot all the curves made up of the pixel values of the isolated nodes in the same graph
        for (Node n : pixelValues.keySet()) {
            List<Float> pixelValuesST = pixelValues.get(n);
            float[] values = new float[pixelValuesST.size()];
            for (int i = 0; i < pixelValuesST.size(); i++) {
                values[i] = pixelValuesST.get(i);
            }
            Plot plot = new Plot("Pixel values of isolated nodes", "Time", "Pixel value", new float[0], values);
            plot.show();
        }
    }
}