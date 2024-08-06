package io.github.rocsg.rstutils;

import com.opencsv.CSVWriter;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import io.github.rocsg.fijiyama.common.VitimageUtils;
import io.github.rocsg.fijiyama.registration.TransformUtils;
import io.github.rocsg.rsml.Node;
import io.github.rocsg.rsml.Root;
import io.github.rocsg.rsml.RootModel;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.statistics.HistogramDataset;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.*;

/**
 * SegmentToSegment class processes root models and generates visual representations of crossings and root complexities.
 */
public class SegmentToSegment {

    /**
     * Main method to execute the program.
     *
     * @param args Command line arguments (not used).
     */
    public static void main(String[] args) throws IOException {
        ImageJ ij = new ImageJ();
        String mainDataDir = "D:\\loaiu\\MAM5\\Stage\\data\\UC1\\230629PN033\\";
        double proximityThreshold = 1.0; // Proximity threshold for detecting crossings
        RootModel rm = RootModel.RootModelWildReadFromRsml(mainDataDir + "61_graph_expertized.rsml");
        ImagePlus seqReg = IJ.openImage(mainDataDir + "22_registered_stack.tif");
        ImagePlus img = drawComplexByTime(rm, seqReg, proximityThreshold);
        img.show();

        // Directory traversal to find matching folders
        String rootDirectory = "D:\\loaiu\\MAM5\\Stage\\data\\UC1";
        ArrayList<File> matchingFolders = new ArrayList<>();
        findMatchingFolders(new File(rootDirectory), matchingFolders);

        // Count crossings and store results per plant
        Map<String, PlantData> plantResults = new HashMap<>();
        for (File folder : matchingFolders) {
            String plantName = folder.getName();
            String rsmlPath = folder.getAbsolutePath() + "\\61_graph_expertized.rsml";
            String tifPath = folder.getAbsolutePath() + "\\22_registered_stack.tif";
            rm = RootModel.RootModelWildReadFromRsml(rsmlPath);
            seqReg = IJ.openImage(tifPath);
            PlantData plantData = new PlantData(plantName);
            countCrossingsPerSlice(rm, seqReg, proximityThreshold, plantData);

            if (!plantResults.containsKey(plantName)) {
                plantResults.put(plantName, plantData);
            }
        }

        // Create datasets for charts
        DefaultCategoryDataset datasetCrossings = new DefaultCategoryDataset();
        DefaultCategoryDataset datasetCumulativeCrossings = new DefaultCategoryDataset();

        for (Map.Entry<String, PlantData> entry : plantResults.entrySet()) {
            String plantName = entry.getKey();
            PlantData plantData = entry.getValue();
            int[] crossingCounts = plantData.crossingCounts;
            int N = crossingCounts.length;
            int[] cumulativeCrossings = new int[N];
            for (int i = 0; i < N; i++) {
                datasetCrossings.addValue(crossingCounts[i], plantName, String.valueOf(i + 1));
                if (i == 0) {
                    cumulativeCrossings[i] = crossingCounts[i];
                } else {
                    cumulativeCrossings[i] = cumulativeCrossings[i - 1] + crossingCounts[i];
                }
                datasetCumulativeCrossings.addValue(cumulativeCrossings[i], plantName, String.valueOf(i + 1));
            }
        }

        // Create and display bar charts
        JFreeChart barChartCrossings = ChartFactory.createBarChart(
                "Nombre de croisements par temps",
                "Temps",
                "Nombre de croisements",
                datasetCrossings,
                PlotOrientation.VERTICAL,
                true, true, false);

        JFreeChart barChartCumulativeCrossings = ChartFactory.createBarChart(
                "Somme cumulée des croisements par temps",
                "Temps",
                "Somme cumulée des croisements",
                datasetCumulativeCrossings,
                PlotOrientation.VERTICAL,
                true, true, false);

        // Display charts in a frame
        JFrame frame = new JFrame("Graphiques de croisements par plante");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new GridLayout(2, 1));

        ChartPanel chartPanelCrossings = new ChartPanel(barChartCrossings);
        ChartPanel chartPanelCumulativeCrossings = new ChartPanel(barChartCumulativeCrossings);

        frame.add(chartPanelCrossings);
        frame.add(chartPanelCumulativeCrossings);

        frame.pack();
        frame.setVisible(true);

        // Create and display averaged bar chart for crossings
        DefaultCategoryDataset datasetAvgCrossings = createAveragedDataset(plantResults);

        JFreeChart barChartAvgCrossings = ChartFactory.createBarChart(
                "Moyenne du nombre de croisements par temps",
                "Temps",
                "Nombre moyen de croisements",
                datasetAvgCrossings,
                PlotOrientation.VERTICAL,
                true, true, false);

        JFrame frame2 = new JFrame("Graphique moyen des croisements par plante");
        frame2.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame2.setLayout(new GridLayout(1, 1));

        ChartPanel chartPanelAvgCrossings = new ChartPanel(barChartAvgCrossings);
        frame2.add(chartPanelAvgCrossings);

        frame2.pack();
        frame2.setVisible(true);

        plotDistanceFromRootBase(plantResults);
        plotLengthFromRootBase(plantResults);

        // save
        saveToCSV(plantResults, "plant_data.csv");
        saveChartAsPNG(barChartCrossings, "bar_chart_crossings.png", 1920, 1080);
        saveChartAsPNG(barChartCumulativeCrossings, "bar_chart_cumulative_crossings.png", 1920, 1080);
        saveChartAsPNG(barChartAvgCrossings, "bar_chart_avg_crossings.png", 1920, 1080);
    }

    /**
     * Save a chart as a PNG file.
     *
     * @param chart    The JFreeChart object to save.
     * @param filePath The path to the file.
     * @param width    Width of the chart image.
     * @param height   Height of the chart image.
     * @throws IOException If an I/O error occurs.
     */
    public static void saveChartAsPNG(JFreeChart chart, String filePath, int width, int height) throws IOException {
        ChartUtils.saveChartAsPNG(new File(filePath), chart, width, height);
    }

    /**
     * Save plant results to a CSV file.
     *
     * @param plantResults Map containing PlantData for each plant.
     * @param filePath     Path to the CSV file.
     * @throws IOException If an I/O error occurs.
     */
    public static void saveToCSV(Map<String, PlantData> plantResults, String filePath) throws IOException {
        try (Writer writer = Files.newBufferedWriter(Paths.get(filePath));
             CSVWriter csvWriter = new CSVWriter(writer)) {

            String[] header = {"Plant Name", "Time Point", "Crossings", "x", "y", "t"};
            csvWriter.writeNext(header);

            for (Map.Entry<String, PlantData> entry : plantResults.entrySet()) {
                String plantName = entry.getKey();
                PlantData plantData = entry.getValue();

                for (CrossingInfo crossing : plantData.crossingInfos) {
                    String[] data = {plantName, String.valueOf(crossing.time), String.valueOf(plantData.recentlyAddedCrossings), String.valueOf(crossing.position[0]), String.valueOf(crossing.position[1]), String.valueOf(crossing.position[2])};
                    csvWriter.writeNext(data);
                }
            }
        }
    }

    /**
     * Plots a histogram showing the distribution of distances between crossing points and the base of the roots.
     *
     * @param plantResults Map containing PlantData for each plant.
     */
    public static void plotDistanceFromRootBase(Map<String, PlantData> plantResults) throws IOException {
        List<Double> distances = new ArrayList<>();

        for (Map.Entry<String, PlantData> entry : plantResults.entrySet()) {
            PlantData plantData = entry.getValue();

            for (CrossingInfo crossing : plantData.crossingInfos) {
                for (Root root : crossing.roots) {
                    Node firstNode = root.firstNode; // Assuming the first node is the base
                    double distance = Math.sqrt(
                            Math.pow(crossing.position[0] - firstNode.x, 2) +
                                    Math.pow(crossing.position[1] - firstNode.y, 2) +
                                    Math.pow(crossing.position[2] - firstNode.birthTime, 2));
                    distances.add(distance);
                }
            }
        }

        double[] distancesArray = distances.stream().mapToDouble(Double::doubleValue).toArray();

        HistogramDataset dataset = new HistogramDataset();
        dataset.addSeries("Distances", distancesArray, 20); // 20 bins

        JFreeChart histogram = ChartFactory.createHistogram(
                "Distribution des distances des croisements à la base des racines",
                "Distance",
                "Fréquence",
                dataset,
                PlotOrientation.VERTICAL,
                true, true, false);

        JFrame frame = new JFrame("Distribution des distances des croisements");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.add(new ChartPanel(histogram));
        frame.pack();
        frame.setVisible(true);
        saveChartAsPNG(histogram, "histogram_distances.png", 1920, 1080);
    }

    /**
     * Plots a histogram showing the distribution of root lengths between crossing points and the base of the roots.
     *
     * @param plantResults Map containing PlantData for each plant.
     */
    public static void plotLengthFromRootBase(Map<String, PlantData> plantResults) throws IOException {
        List<Double> lengths = new ArrayList<>();

        for (Map.Entry<String, PlantData> entry : plantResults.entrySet()) {
            PlantData plantData = entry.getValue();

            for (CrossingInfo crossing : plantData.crossingInfos) {
                for (Root root : crossing.roots) {
                    Node firstNode = root.firstNode; // Assuming the first node is the base
                    double length = 0;
                    double lengthFromRoot = 0;
                    firstNode.calcCLength();
                    while (firstNode.child != null) {
                        if (Math.sqrt(
                                Math.pow(crossing.position[0] - firstNode.x, 2) +
                                        Math.pow(crossing.position[1] - firstNode.y, 2)) >
                                Math.sqrt(
                                        Math.pow(crossing.position[0] - firstNode.child.x, 2) +
                                                Math.pow(crossing.position[1] - firstNode.child.y, 2))) {
                            length = lengthFromRoot + Math.sqrt(
                                    Math.pow(firstNode.x - firstNode.child.x, 2) +
                                            Math.pow(firstNode.y - firstNode.child.y, 2));
                        }
                        lengthFromRoot += Math.sqrt(
                                Math.pow(firstNode.x - firstNode.child.x, 2) +
                                        Math.pow(firstNode.y - firstNode.child.y, 2));
                        firstNode = firstNode.child;
                    }
                    if (Math.sqrt(
                            Math.pow(crossing.position[0] - firstNode.x, 2) +
                                    Math.pow(crossing.position[1] - firstNode.y, 2)) < length) {
                        length = lengthFromRoot + Math.sqrt(
                                Math.pow(firstNode.parent.x - firstNode.x, 2) +
                                        Math.pow(firstNode.parent.y - firstNode.y, 2));
                    }
                    lengths.add(length);
                }
            }
        }

        double[] lengthsArray = lengths.stream().mapToDouble(Double::doubleValue).toArray();

        HistogramDataset dataset = new HistogramDataset();
        dataset.addSeries("Lengths", lengthsArray, 20); // 20 bins

        JFreeChart histogram = ChartFactory.createHistogram(
                "Distribution des longueur racinaires entre les croisements et la base des racines",
                "Longueur",
                "Fréquence",
                dataset,
                PlotOrientation.VERTICAL,
                true, true, false);

        JFrame frame = new JFrame("Distribution des distances des croisements");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.add(new ChartPanel(histogram));
        frame.pack();
        frame.setVisible(true);
        saveChartAsPNG(histogram, "histogram_lengths.png", 1920, 1080);
    }

    /**
     * Creates a dataset that averages the number of crossings per time point across all plants.
     *
     * @param plantResults Map containing PlantData for each plant.
     * @return DefaultCategoryDataset containing the averaged crossings per time point.
     */
    public static DefaultCategoryDataset createAveragedDataset(Map<String, PlantData> plantResults) {
        Map<Integer, List<Integer>> crossingsByTime = new HashMap<>();
        int maxTime = 0;

        // Collect all crossing counts per time point
        for (PlantData plantData : plantResults.values()) {
            for (int i = 0; i < plantData.crossingCounts.length; i++) {
                crossingsByTime.computeIfAbsent(i, k -> new ArrayList<>()).add(plantData.crossingCounts[i]);
                if (i > maxTime) {
                    maxTime = i;
                }
            }
        }

        // Calculate averages
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        for (int i = 0; i <= maxTime; i++) {
            List<Integer> counts = crossingsByTime.get(i);
            if (counts != null) {
                double average = counts.stream().mapToInt(Integer::intValue).average().orElse(0);
                dataset.addValue(average, "Moyenne des croisements", String.valueOf(i + 1));
            }
        }

        return dataset;
    }

    /**
     * Draws a complex image by time from the root model and image sequence.
     *
     * @param rm                 Root model.
     * @param imgSequence        Image sequence.
     * @param proximityThreshold Proximity threshold for detecting crossings.
     * @return ImagePlus containing the drawn complex image.
     */
    public static ImagePlus drawComplexByTime(RootModel rm, ImagePlus imgSequence, double proximityThreshold) {
        int N = imgSequence.getStackSize();
        PlantData plantData = new PlantData("plant");
        plantData.crossingCounts = new int[N];
        ImagePlus[] slices = VitimageUtils.stackToSlices(imgSequence);
        for (int i = 1; i <= N; i++) {
            System.out.println("\n\n\n\n\n\nSTEP " + i);
            Object[] complex = rootModelComplexity(rm, proximityThreshold, i);
            plantData.addCrossingInfos((List<CrossingInfo>) complex[1]);
            plantData.crossingCounts[i - 1] = plantData.recentlyAddedCrossings + (i == 1 ? 0 : plantData.crossingCounts[i - 2]);

            slices[i - 1] = drawComplex(slices[i - 1], (ArrayList<CrossingInfo>) complex[1], proximityThreshold);
            slices[i - 1] = VitimageUtils.writeBlackTextOnGivenImage("N=" + complex[0], slices[i - 1], 20, 50, 50);
        }
        ImagePlus comp = VitimageUtils.slicesToStack(slices);
        return VitimageUtils.compositeNoAdjustOf(imgSequence, comp);
    }

    /**
     * Draws the complex image with crossing points.
     *
     * @param ref                Reference image.
     * @param pointsContact      List of crossing points.
     * @param proximityThreshold Proximity threshold for detecting crossings.
     * @return ImagePlus containing the drawn complex image.
     */
    public static ImagePlus drawComplex(ImagePlus ref, ArrayList<CrossingInfo> pointsContact, double proximityThreshold) {
        ImagePlus img = VitimageUtils.nullImage(ref);
        IJ.run(img, "32-bit", "");
        double radMin = 4;
        double radMax = 4;
        double valMin = 3;
        double valMax = 6;
        for (double[] contact : pointsContact.stream().map(info -> info.position).toArray(double[][]::new)) {
            double dist = contact[3];
            double x = contact[0];
            double y = contact[1];
            double percent = dist / proximityThreshold;
            double rad = (radMax - percent * (radMax - radMin)) * VitimageUtils.getVoxelSizes(img)[0]; // The closer, the bigger, the more colored
            double color = valMax - percent * (valMax - valMin);
            VitimageUtils.drawCircleIntoImageFloat(img, rad, (int) x, (int) y, 0, color);
        }
        img.setDisplayRange(0, valMax);
        IJ.run(img, "Fire", "");
        return img;
    }

    /**
     * Finds folders that contain both "22_registered_stack.tif" and "61_graph_expertized.rsml" files.
     *
     * @param dir             Root directory to start searching.
     * @param matchingFolders List to store the matching folders.
     */
    public static void findMatchingFolders(File dir, ArrayList<File> matchingFolders) {
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            boolean hasTif = false, hasRsml = false;
            if (files != null) {
                for (File file : files) {
                    if (file.getName().equals("22_registered_stack.tif")) hasTif = true;
                    if (file.getName().equals("61_graph_expertized.rsml")) hasRsml = true;
                }
                if (hasTif && hasRsml) matchingFolders.add(dir);
                for (File file : files) {
                    if (file.isDirectory()) findMatchingFolders(file, matchingFolders);
                }
            }
        }
    }

    /**
     * Counts the crossings per slice in the image sequence and updates the plant data.
     *
     * @param rm                 Root model.
     * @param imgSequence        Image sequence.
     * @param proximityThreshold Proximity threshold for detecting crossings.
     * @param plantData          Plant data to be updated.
     */
    public static void countCrossingsPerSlice(RootModel rm, ImagePlus imgSequence, double proximityThreshold, PlantData plantData) {
        int N = imgSequence.getStackSize();
        plantData.crossingCounts = new int[N];
        for (int i = 1; i <= N; i++) {
            Object[] complex = rootModelComplexity(rm, proximityThreshold, i);
            plantData.addCrossingInfos((List<CrossingInfo>) complex[1]);
            plantData.crossingCounts[i - 1] = plantData.recentlyAddedCrossings + (i == 1 ? 0 : plantData.crossingCounts[i - 2]);
        }
    }

    /**
     * Computes the complexity of the root model by finding crossings within a given threshold up to a specified day.
     *
     * @param rm                 Root model.
     * @param proximityThreshold Proximity threshold for detecting crossings.
     * @param dayMax             Maximum day to consider for crossings.
     * @return Object array containing total ambiguities and list of crossing information.
     */
    public static Object[] rootModelComplexity(RootModel rm, double proximityThreshold, int dayMax) {
        List<CrossingInfo> crossingInfos = new ArrayList<>();
        int totalAmbiguities = 0;
        int nRoot = rm.rootList.size();
        ArrayList<Root> arrRoot = rm.rootList;
        Root[] tabRoot = new Root[arrRoot.size()];
        double[] xMin = new double[nRoot];
        double[] xMax = new double[nRoot];
        double[] yMin = new double[nRoot];
        double[] yMax = new double[nRoot];
        for (int i = 0; i < nRoot; i++) {
            tabRoot[i] = arrRoot.get(i);
            tabRoot[i].computeDistances();
            xMin[i] = tabRoot[i].getXMin();
            xMax[i] = tabRoot[i].getXMax();
            yMin[i] = tabRoot[i].getYMin();
            yMax[i] = tabRoot[i].getYMax();
        }

        for (int i1 = 0; i1 < nRoot; i1++) {
            for (int i2 = i1 + 1; i2 < nRoot; i2++) {
                boolean touch = false;
                if (couldIntersect(xMin[i1], xMin[i2], xMax[i1], xMax[i2], proximityThreshold)) continue;
                if (couldIntersect(yMin[i1], yMin[i2], yMax[i1], yMax[i2], proximityThreshold)) continue;
                Root r1 = tabRoot[i1];
                Root r2 = tabRoot[i2];
                Root r1Primary = (r1.order == 1 ? r1 : r1.getParent());
                Root r2Primary = (r2.order == 1 ? r2 : r2.getParent());
                Node[] tabN1 = r1.getNodesList().toArray(new Node[0]);
                Node[] tabN2 = r2.getNodesList().toArray(new Node[0]);
                int N1 = tabN1.length;
                int N2 = tabN2.length;
                boolean[] proxN1 = new boolean[N1 - 1];
                boolean[] proxN2 = new boolean[N2 - 1];

                for (int n1 = 1; n1 < N1; n1++) {
                    if (tabN1[n1].distance < 2 * proximityThreshold) continue;
                    if (tabN1[n1].birthTime > dayMax) continue;
                    for (int n2 = 1; n2 < N2; n2++) {
                        if (tabN2[n2].distance < 2 * proximityThreshold) continue;
                        if (tabN2[n2].birthTime > dayMax) continue;
                        double[] Astart = {tabN1[n1 - 1].x, tabN1[n1 - 1].y, 0};
                        double[] Astop = {tabN1[n1].x, tabN1[n1].y, 0};
                        double[] Bstart = {tabN2[n2 - 1].x, tabN2[n2 - 1].y, 0};
                        double[] Bstop = {tabN2[n2].x, tabN2[n2].y, 0};

                        if (doSegmentsIntersect(Astart, Astop, Bstart, Bstop)) {
                            List<Root> roots = new ArrayList<>();
                            roots.add(r1);
                            roots.add(r2);
                            crossingInfos.add(new CrossingInfo(new double[]{(Astart[0] + Astop[0] + Bstart[0] + Bstop[0]) / 4, (Astart[1] + Astop[1] + Bstart[1] + Bstop[1]) / 4, tabN1[n1].birthTime, 0}, roots, r1Primary, r2Primary, Arrays.asList(tabN1[n1 - 1], tabN1[n1]), Arrays.asList(tabN2[n2 - 1], tabN2[n2]), dayMax));
                            touch = true;
                            proxN1[n1 - 1] = true;
                            proxN2[n2 - 1] = true;
                        } else {
                            double[] dist = distanceBetweenTwoSegments3D(Astart, Astop, Bstart, Bstop);
                            if (dist[0] <= proximityThreshold) {
                                List<Root> roots = new ArrayList<>();
                                roots.add(r1);
                                roots.add(r2);
                                crossingInfos.add(new CrossingInfo(new double[]{dist[1], dist[2], tabN1[n1].birthTime, dist[0]}, roots, r1Primary, r2Primary, Arrays.asList(tabN1[n1 - 1], tabN1[n1]), Arrays.asList(tabN2[n2 - 1], tabN2[n2]), dayMax));
                                touch = true;
                                proxN1[n1 - 1] = true;
                                proxN2[n2 - 1] = true;
                            }
                        }
                    }
                }

                if (touch) {
                    totalAmbiguities++;
                }
            }
        }
        return new Object[]{(double) totalAmbiguities, crossingInfos};
    }

    /**
     * Determines if two segments intersect.
     *
     * @param Astart Start point of segment A.
     * @param Astop  End point of segment A.
     * @param Bstart Start point of segment B.
     * @param Bstop  End point of segment B.
     * @return True if the segments intersect, false otherwise.
     */
    public static boolean doSegmentsIntersect(double[] Astart, double[] Astop, double[] Bstart, double[] Bstop) {
        double[] A = Astart;
        double[] B = Astop;
        double[] C = Bstart;
        double[] D = Bstop;

        double[] AB = {B[0] - A[0], B[1] - A[1]};
        double[] CD = {D[0] - C[0], D[1] - C[1]};

        double cross = AB[0] * CD[1] - AB[1] * CD[0];

        if (cross == 0) {
            return false;
        }

        double[] AC = {C[0] - A[0], C[1] - A[1]};
        double t1 = (AC[0] * CD[1] - AC[1] * CD[0]) / cross;
        double u = (AC[0] * AB[1] - AC[1] * AB[0]) / cross;

        return t1 >= 0 && t1 <= 1 && u >= 0 && u <= 1;
    }

    /**
     * Computes the distance between two 3D segments.
     *
     * @param Astart Start point of segment A.
     * @param Astop  End point of segment A.
     * @param Bstart Start point of segment B.
     * @param Bstop  End point of segment B.
     * @return Array containing the distance and mid-point coordinates between the segments.
     */
    public static double[] distanceBetweenTwoSegments3D(double[] Astart, double[] Astop, double[] Bstart, double[] Bstop) {
        double[] P1P0 = TransformUtils.vectorialSubstraction(Astop, Astart);
        double[] Q1Q0 = TransformUtils.vectorialSubstraction(Bstop, Bstart);
        double[] P0Q0 = TransformUtils.vectorialSubstraction(Astart, Bstart);
        double a = TransformUtils.scalarProduct(P1P0, P1P0);
        double b = TransformUtils.scalarProduct(P1P0, Q1Q0);
        double c = TransformUtils.scalarProduct(Q1Q0, Q1Q0);
        double d = TransformUtils.scalarProduct(P1P0, P0Q0);
        double e = TransformUtils.scalarProduct(Q1Q0, P0Q0);
        double s = 0;
        double t = 0;
        double det = a * c - b * b;
        if (det > 0) {
            s = (b * e - c * d) / det;
            t = (a * e - b * d) / det;
        } else {
            s = 0;
            t = e / c;
        }
        s = Math.max(0, Math.min(1, s));
        t = Math.max(0, Math.min(1, t));
        double[] ptA = TransformUtils.vectorialAddition(TransformUtils.multiplyVector(Astart, (1 - s)), TransformUtils.multiplyVector(Astop, s));
        double[] ptB = TransformUtils.vectorialAddition(TransformUtils.multiplyVector(Bstart, (1 - t)), TransformUtils.multiplyVector(Bstop, t));
        double distance = TransformUtils.norm(TransformUtils.vectorialSubstraction(ptA, ptB));
        return new double[]{distance, ptA[0] * 0.5 + ptB[0] * 0.5, ptA[1] * 0.5 + ptB[1] * 0.5, ptA[2] * 0.5 + ptB[2] * 0.5};
    }

    /**
     * Checks if two segments could intersect given their minimum and maximum values and a threshold.
     *
     * @param valMin1   Minimum value of the first segment.
     * @param valMin2   Minimum value of the second segment.
     * @param valMax1   Maximum value of the first segment.
     * @param valMax2   Maximum value of the second segment.
     * @param threshold Proximity threshold.
     * @return True if the segments could intersect, false otherwise.
     */
    public static boolean couldIntersect(double valMin1, double valMin2, double valMax1, double valMax2, double threshold) {
        return (valMin1 - threshold) > valMax2 || (valMin2 - threshold) > valMax1;
    }

    /**
     * Filters crossing information by time.
     *
     * @param crossingInfos Set of crossing information.
     * @param time          Time to filter by.
     * @return List of crossing information that matches the specified time.
     */
    public static List<CrossingInfo> filterCrossingInfoByTime(HashSet<CrossingInfo> crossingInfos, int time) {
        List<CrossingInfo> filtered = new ArrayList<>();
        for (CrossingInfo info : crossingInfos) {
            if (info.time == time) {
                filtered.add(info);
            }
        }
        return filtered;
    }
}

/**
 * PlantData class stores information about a plant's crossings and their counts.
 */
class PlantData {
    String plantName;
    int[] crossingCounts;
    HashSet<CrossingInfo> crossingInfos;
    int recentlyAddedCrossings = 0;

    /**
     * Constructor for PlantData.
     *
     * @param plantName Name of the plant.
     */
    public PlantData(String plantName) {
        this.plantName = plantName;
        this.crossingInfos = new HashSet<>();
    }

    /**
     * Adds a single crossing information to the plant data.
     *
     * @param info Crossing information to be added.
     */
    public void addCrossingInfo(CrossingInfo info) {
        this.crossingInfos.add(info);
    }

    /**
     * Adds multiple crossing information to the plant data, avoiding repetitions.
     *
     * @param infos List of crossing information to be added.
     */
    public void addCrossingInfos(List<CrossingInfo> infos) {
        recentlyAddedCrossings = 0;
        for (CrossingInfo info : infos) {
            boolean add = true;
            for (CrossingInfo crossingInfo : this.crossingInfos) {
                if (Math.sqrt(Math.pow(crossingInfo.position[0] - info.position[0], 2) + Math.pow(crossingInfo.position[1] - info.position[1], 2)) < 1) {
                    add = false;
                    break;
                }
            }
            if (add) {
                recentlyAddedCrossings++;
                this.addCrossingInfo(info);
            }
        }
    }
}

/**
 * CrossingInfo class stores information about a crossing point, including position, involved roots, and time.
 */
class CrossingInfo {
    double[] position;
    List<Root> roots;
    List<Node> nodeRoot1;
    List<Node> nodeRoot2;
    Root primaryRoot1;
    Root primaryRoot2;
    boolean samePrimaryRoot;
    int time;

    /**
     * Constructor for CrossingInfo.
     *
     * @param position     Position of the crossing point.
     * @param roots        List of roots involved in the crossing.
     * @param primaryRoot1 Primary root for the first involved root.
     * @param primaryRoot2 Primary root for the second involved root.
     * @param nodeRoot1    List of nodes for the first involved root.
     * @param nodeRoot2    List of nodes for the second involved root.
     * @param time         Time of the crossing.
     */
    public CrossingInfo(double[] position, List<Root> roots, Root primaryRoot1, Root primaryRoot2, List<Node> nodeRoot1, List<Node> nodeRoot2, int time) {
        this.position = position;
        this.roots = roots;
        this.primaryRoot1 = primaryRoot1;
        this.primaryRoot2 = primaryRoot2;
        this.nodeRoot1 = nodeRoot1;
        this.nodeRoot2 = nodeRoot2;
        this.samePrimaryRoot = primaryRoot1.equals(primaryRoot2);
        this.time = time;
    }
}
