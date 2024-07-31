package io.github.rocsg.rsmlparser;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.CurveFitter;
import ij.measure.SplineFitter;
import ij.process.ImageProcessor;
import io.github.rocsg.fijiyama.common.VitimageUtils;
import io.github.rocsg.fijiyama.registration.ItkTransform;
import io.github.rocsg.rsml.Node;
import io.github.rocsg.rsml.Root;
import io.github.rocsg.rsml.RootModel;
import io.github.rocsg.rstplugin.PipelineParamHandler;
import math3d.Point3d;
import org.apache.commons.math3.ml.clustering.CentroidCluster;
import org.apache.commons.math3.ml.clustering.Cluster;
import org.apache.commons.math3.ml.clustering.DoublePoint;
import org.apache.commons.math3.ml.clustering.KMeansPlusPlusClusterer;
import org.graphstream.graph.implementations.SingleGraph;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.List;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.github.rocsg.rsmlparser.RsmlParser.getDate;
import static io.github.rocsg.rstplugin.PipelineParamHandler.configurePipelineParams;

public class RootModelGraph {

    final static int STANDART_DIST = 1;
    final List<RootModel> rootModels;
    public List<org.graphstream.graph.Graph> graphs;
    PipelineParamHandler pph;
    ImagePlus image;
    List<ItkTransform> transforms;

    public RootModelGraph() throws IOException {
        this("D:\\loaiu\\MAM5\\Stage\\data\\UC3\\Rootsystemtracker\\Original_Data\\B73_R04_01\\", "D:\\loaiu\\MAM5\\Stage\\data\\TestParser\\Output-Copie\\Process\\B73_R04_01\\Transforms_2\\", "D:\\loaiu\\MAM5\\Stage\\data\\TestParser\\Output\\Inventory\\", "D:\\loaiu\\MAM5\\Stage\\data\\TestParser\\Output\\Process\\", "D:\\loaiu\\MAM5\\Stage\\data\\TestParser\\Output-Copie\\Process\\B73_R04_01\\11_stack.tif", "D:\\loaiu\\MAM5\\Stage\\data\\TestParser\\Output-Copie\\Process\\B73_R04_01\\22_registered_stack.tif", "D:\\loaiu\\MAM5\\Stage\\data\\TestParser\\Output-Copie\\Process\\B73_R04_01\\12_stack_cropped.tif");
        //this("D:\\loaiu\\MAM5\\Stage\\data\\UC3\\Rootsystemtracker\\Original_Data\\B73_R07_01\\", "D:\\loaiu\\MAM5\\Stage\\data\\TestParser\\Output-Copie\\Process\\B73_R07_01\\Transforms_2\\", "D:\\loaiu\\MAM5\\Stage\\data\\TestParser\\Output\\Inventory\\", "D:\\loaiu\\MAM5\\Stage\\data\\TestParser\\Output\\Process\\", "D:\\loaiu\\MAM5\\Stage\\data\\TestParser\\Output-Copie\\Process\\B73_R07_01\\11_stack.tif", "D:\\loaiu\\MAM5\\Stage\\data\\TestParser\\Output-Copie\\Process\\B73_R07_01\\22_registered_stack.tif", "D:\\loaiu\\MAM5\\Stage\\data\\TestParser\\Output-Copie\\Process\\B73_R07_01\\12_stack_cropped.tif");
    }

    /**
     * Constructor of the RootModelGraph class
     * The RootModelGraph is created by reading the RSMLs from the specified path
     * The RootModels are extracted from the RSMLs
     * The RootModels are then converted to JGraphT Graphs
     * The JGraphT Graphs are then converted to ImagePlus images
     * The images are then resized using the resampling factor specified in the PipelineParamHandler
     * The transforms are read from the specified path
     *
     * @param path2RSMLs      The path to the RSMLs
     * @param transformerPath The path to the transforms
     * @param inputPathPPH    The path to the input PipelineParamHandler
     * @param outputPathPPH   The path to the output PipelineParamHandler
     * @throws IOException If an I/O error occurs
     */
    public RootModelGraph(String path2RSMLs, String transformerPath, String inputPathPPH, String outputPathPPH, String originalScaledImagePath, String registeredImagePath, String croppedImage) throws IOException {
        this.rootModels = new ArrayList<>();
        this.graphs = new ArrayList<>();
        transforms = new ArrayList<>();

        // Getting the resizer factor
        Map<String, String> configMap = new HashMap<>();
        configMap.put("scalingFactor", "4");
        configMap.put("xMinCrop", "350");
        configMap.put("yMinCrop", "87");
        configMap.put("dxCrop", "2380"); // 2305
        configMap.put("dyCrop", "2108");
        configMap.put("marginRegisterLeft", "5");
        configMap.put("marginRegisterUp", "248");
        configMap.put("marginRegisterDown", "5");

        configurePipelineParams(configMap);

        pph = new PipelineParamHandler(inputPathPPH, outputPathPPH);
        configurePipelineParams(configMap);
        // Reading all RSMLs and getting the RootModels


        /*****DEBUG*****/
        List<LocalDate> removedDates = new ArrayList<>(); // DANGER
        Map<LocalDate, List<IRootModelParser>> result = parseRsmlFiles(path2RSMLs, removedDates);

        List<LocalDate> datesFromImages = new ArrayList<>();

        ConcurrentHashMap<String, LocalDate> fileDates = new ConcurrentHashMap<>();
        // get the date of the rsml files
        try {
            Path path2Images = Paths.get(path2RSMLs);
            Files.list(path2Images)
                    .parallel()
                    .filter(path -> path.toString().matches(".*\\.(jpg)$"))
                    .forEach(path -> {
                        fileDates.put(path.toString(), Objects.requireNonNull(getDate(path.toString().split("\\.")[0])));
                    });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // from fileDates, get the dates of the images
        fileDates.forEach((path, date) -> {
            if (!datesFromImages.contains(date)) datesFromImages.add(date);
        });

        HashSet<LocalDate> totalDates = new HashSet<>(result.keySet());
        totalDates.addAll(removedDates);
        totalDates.addAll(datesFromImages);
        // sort the dates in ascending order
        List<LocalDate> sortedDates = new ArrayList<>(totalDates);
        sortedDates.sort(LocalDate::compareTo);

        // adding the dates to result
        sortedDates.forEach(date -> {
            if (!result.containsKey(date)) {
                result.put(date, new ArrayList<>());
            }
        });

        /*****DEBUG*****/
        RootModel rms = new RootModel();
        rms = (RootModel) rms.createRootModels(result, (float) PipelineParamHandler.subsamplingFactor);

        ImagePlus refImage = new ImagePlus(registeredImagePath);

        // if removedDatesIndex is not empty, remove the corresponding slices from the image
        image = refImage;

        // Read all the transforms and apply them
        ImagePlus imgInitSize = new ImagePlus(originalScaledImagePath);
        imgInitSize = new ImagePlus("D:\\loaiu\\MAM5\\Stage\\data\\UC3\\Rootsystemtracker\\Original_Data\\B73_R04_01\\NewRSMLs\\img.tif\\");
        //displayOnImage(createGraphFromRM(rms), imgInitSize, true).show();

        RootModel basicRM = new RootModel();
        basicRM = (RootModel) basicRM.createRootModels(result, (float)1);
        displayOnImage(createGraphFromRM(basicRM), imgInitSize, true).show();


        readAndApplyTransforms(transformerPath, rms, refImage, imgInitSize);
        rms.adjustRootModel();


        //ImagePlus img3 = displayOnImage(createGraphFromRM(rms), refImage);
        //img3.show();

        // save RootModel before and after adjustment
        String path2NewRSML = path2RSMLs + "\\NewRSMLs\\" + LocalDate.now() + ".rsml";
        // create folder if not exists
        Path path = Paths.get(path2NewRSML).getParent();
        if (!Files.exists(path)) Files.createDirectories(path);
        rms.writeRSML3D(new File(path2NewRSML).getAbsolutePath().replace("\\", "/"), "", true, false);

        //BlockMatchingRegistrationRootModel.setupAndRunRsmlBlockMatchingRegistration(rms, refImage, true);

        //PlantReconstruction pr = new PlantReconstruction(rms);*/

        //setupAndRunRsmlBlockMatchingRegistration(rms, refImage, false);


        // Display the final graph
        //ImagePlus img2 = toDisplayTemporalGraph(createGraphFromRM(rms), res2.getWidth(), res2.getHeight(), 1, res2.getNSlices());

        //ImagePlus img2 = displayOnImage(createGraphFromRM(rms), refImage);
        //img2.show();

        //Map<Root, List<Node>> insertionPoints = rms.getInsertionPoints();
        //ImagePlus img3 = displayOnImage(createGraphFromRM(rms), refImage, insertionPoints);
        //img3.show();
        // stop execution but not the running programs



        /*interpolatePointsSplineFitter(rms, displayOnImage(createGraphFromRM(rms), refImage, insertionPoints));

        interpolatePointsCurveFitter(rms, displayOnImage(createGraphFromRM(rms), refImage, insertionPoints));

        interpolatePoints(rms, displayOnImage(createGraphFromRM(rms), refImage, insertionPoints));

        interpolatePointsSpline(rms, displayOnImage(createGraphFromRM(rms), refImage, insertionPoints));

        interpolatePointsRBF(rms, displayOnImage(createGraphFromRM(rms), refImage, insertionPoints));

        interpolatePointsBezier(rms, displayOnImage(createGraphFromRM(rms), refImage, insertionPoints));*/
        //System.out.println("RootModelGraph : " + rms);

        //displayOnImage(createGraphFromRM(rms), refImage).show();
        //interpolatePointsSplineFitter(rms, imag.duplicate());

        //interpolatePointsCurveFitter(rms, imag.duplicate());

        //interpolatePoints(rms, imag.duplicate());

        //interpolatePointsRBF(rms, imag.duplicate());

        //interpolatePointsBezier(rms, imag.duplicate());

//        rms.closestNodes();
//        rms.alignByMinDist();
//        //rms.align2Time();
//        ImagePlus img3 = toDisplayTemporalGraph(createGraphFromRM(rms), res2.getWidth(), res2.getHeight(), 1, res2.getNSlices());
//        img3.show();
    }

    /**
     * Function to create a display of a GraphStream graph as an ImagePlus image
     *
     * @param g                 The graph made of the Nodes and Edges
     * @param width             The width of the image
     * @param height            The height of the image
     * @param subsamplingFactor The subsampling factor
     * @param numSlices         The number of slices
     * @return The ImagePlus image of the graph
     */
    public static ImagePlus toDisplayTemporalGraph(org.graphstream.graph.Graph g, int width, int height, float subsamplingFactor, int numSlices) {
        ImageStack stack = new ImageStack();
        for (int i = 1; i <= numSlices; i++) {
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = image.createGraphics();

            for (org.graphstream.graph.Node node : g) {
                Object[] xyz = node.getAttribute("xyz");
                double x = (float) xyz[0];
                double y = (float) xyz[1];
                double t = (float) xyz[2];
                if (t == i) g2d.fill(new Ellipse2D.Double(x - 2, y - 2, 4, 4));
            }

            for (org.graphstream.graph.Edge edge : g.getEdgeSet()) {
                Object[] xyz0 = edge.getNode0().getAttribute("xyz");
                Object[] xyz1 = edge.getNode1().getAttribute("xyz");
                double x1 = (float) xyz0[0];
                double y1 = (float) xyz0[1];
                double t1 = (float) xyz0[2];
                double x2 = (float) xyz1[0];
                double y2 = (float) xyz1[1];
                double t2 = (float) xyz1[2];
                if (t1 == i && t2 == i) g2d.draw(new Line2D.Double(x1, y1, x2, y2));
            }

            g2d.dispose();

            stack.addSlice(new ImagePlus("Graph", image).getProcessor());
        }
        return new ImagePlus("Root Model Graph", stack);
    }

    public static ImagePlus displayOnImage(org.graphstream.graph.Graph g, ImagePlus img) {
        return displayOnImage(g, img, false);
    }

    public static ImagePlus displayOnImage(org.graphstream.graph.Graph g, ImagePlus img, boolean justStack) {
        // convert to rgb image
        ImageStack rgbStack = new ImageStack(img.getWidth(), img.getHeight());
        int numSlices = img.getNSlices();
        for (int i = 1; i <= numSlices; i++) {
            rgbStack.addSlice(img.getStack().getProcessor(i).convertToRGB());
        }
        ImagePlus imag = new ImagePlus("RGB stack", rgbStack);
        ImageStack stack = imag.getImageStack();

        int numEdges = g.getEdgeSet().size();
        for (int i = 1; i <= numSlices; i++) {
            BufferedImage image = stack.getProcessor(i).getBufferedImage();
            Graphics2D g2d = image.createGraphics();
            g2d.setColor(Color.RED);

            for (org.graphstream.graph.Node node : g) {
                Object[] xyz = node.getAttribute("xyz?");
                double x = (float) xyz[0];
                double y = (float) xyz[1];
                double t = (float) xyz[2];
                boolean isInsertionPoint = (boolean) xyz[3];
                if (justStack && t == i) g2d.fill(new Ellipse2D.Double(x - 2, y - 2, 4, 4));
                else if (!justStack && t <= i) g2d.fill(new Ellipse2D.Double(x - 1, y - 1, 2, 2));
                if (!justStack && isInsertionPoint) {
                    g2d.setColor(Color.GREEN);
                    g2d.fill(new Ellipse2D.Double(x - 2, y - 2, 4, 4));
                    g2d.setColor(Color.RED);
                }
            }

            for (int j = 0; j < numEdges; j++) {
                org.graphstream.graph.Edge edge = g.getEdge(j);
                Object[] xyz0 = edge.getNode0().getAttribute("xyz?");
                Object[] xyz1 = edge.getNode1().getAttribute("xyz?");
                double x1 = (float) xyz0[0];
                double y1 = (float) xyz0[1];
                double t1 = (float) xyz0[2];
                double x2 = (float) xyz1[0];
                double y2 = (float) xyz1[1];
                double t2 = (float) xyz1[2];
                boolean isInsertionPoint = (boolean) xyz0[3];
                boolean isInsertionPoint1 = (boolean) xyz1[3];
                if (justStack && (t1 == i && t2 == i)) {
                    g2d.draw(new Line2D.Double(x1, y1, x2, y2));
                    // if line is superior length to a certain threshold, string plot
                    if (Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2)) > 100) {
                        g2d.drawString(edge.getId(), (float) (x1 + x2) / 2, (float) (y1 + y2) / 2);
                    }
                } else if (!justStack && (t1 <= i && t2 <= i)) {
                    g2d.draw(new Line2D.Double(x1, y1, x2, y2));
                    if (Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2)) > 100) {
                        g2d.drawString(edge.getId(), (float) (x1 + x2) / 2, (float) (y1 + y2) / 2);
                    }
                }
                if (!justStack && !isInsertionPoint && isInsertionPoint1 && t1 <= i && t2 <= i) {
                    g2d.setColor(Color.GREEN);
                    g2d.draw(new Line2D.Double(x1, y1, x2, y2));
                    g2d.setColor(Color.RED);
                }
            }

            g2d.dispose();
            stack.setProcessor(new ImagePlus("Graph", image).getProcessor(), i);
        }
        return new ImagePlus("Root Model Graph", stack);
    }

    public static ImagePlus displayOnImage(org.graphstream.graph.Graph g, ImagePlus img, Object o) {
        // if o is a Map<Root, List<Node>>
        if (o instanceof Map) {
            Map<Root, List<Node>> insertionPoints = (Map<Root, List<Node>>) o;
            // get all nodes position in a list
            List<Point3d> nodes = new ArrayList<>();
            for (Root r : insertionPoints.keySet()) {
                for (Node n : insertionPoints.get(r)) {
                    nodes.add(new Point3d(n.x, n.y, n.birthTime));
                }
            }
            // convert to rgb image
            ImageStack rgbStack = new ImageStack(img.getWidth(), img.getHeight());
            int numSlices = img.getNSlices();
            for (int i = 1; i <= numSlices; i++) {
                rgbStack.addSlice(img.getStack().getProcessor(i).convertToRGB());
            }
            ImagePlus imag = new ImagePlus("RGB stack", rgbStack);
            ImageStack stack = imag.getImageStack();

            int numEdges = g.getEdgeSet().size();
            for (int i = 1; i <= numSlices; i++) {
                BufferedImage image = stack.getProcessor(i).getBufferedImage();
                Graphics2D g2d = image.createGraphics();
                for (org.graphstream.graph.Node node : g) {
                    Object[] xyz = node.getAttribute("xyz?");
                    double x = (float) xyz[0];
                    double y = (float) xyz[1];
                    double t = (float) xyz[2];
                    boolean isInsertionPoint = (boolean) xyz[3];
                    if ((t == i) && (isInsertionPoint)) {
                        g2d.setColor(Color.GREEN);
                        g2d.fill(new Ellipse2D.Double(x - 2, y - 2, 4, 4));
                    } else if (t == i) {
                        g2d.setColor(Color.RED);
                        g2d.fill(new Ellipse2D.Double(x - 2, y - 2, 4, 4));
                    }
                }

                for (int j = 0; j < numEdges; j++) {
                    org.graphstream.graph.Edge edge = g.getEdge(j);
                    Object[] xyz0 = edge.getNode0().getAttribute("xyz?");
                    Object[] xyz1 = edge.getNode1().getAttribute("xyz?");
                    double x1 = (float) xyz0[0];
                    double y1 = (float) xyz0[1];
                    double t1 = (float) xyz0[2];
                    double x2 = (float) xyz1[0];
                    double y2 = (float) xyz1[1];
                    double t2 = (float) xyz1[2];
                    if (t1 == i && t2 == i) g2d.draw(new Line2D.Double(x1, y1, x2, y2));
                }

                g2d.dispose();
                stack.setProcessor(new ImagePlus("Graph", image).getProcessor(), i);
            }
            return new ImagePlus("Root Model Graph", stack);
        }
        return displayOnImage(g, img);
    }

    public static org.graphstream.graph.Graph createGraphFromRM(RootModel rootModel) {
        org.graphstream.graph.Graph g = new SingleGraph("RootModelGraph");

        for (Root r : rootModel.rootList) {
            io.github.rocsg.rsml.Node firstNode = r.firstNode;
            while (firstNode != null) {
                String nodeId = "Node_" + firstNode;
                if (g.getNode(nodeId) == null) {
                    org.graphstream.graph.Node node = g.addNode(nodeId);
                    node.setAttribute("xyz?", firstNode.x, firstNode.y, firstNode.birthTime, firstNode.isInsertionPoint);
                }
                firstNode = firstNode.child;
            }
        }

        for (Root r : rootModel.rootList) {
            io.github.rocsg.rsml.Node firstNode = r.firstNode;
            while (firstNode != null) {
                if (firstNode.child != null) {
                    String sourceId = "Node_" + firstNode;
                    String targetId = "Node_" + firstNode.child;
                    String edgeId = sourceId + "_" + targetId;
                    if (g.getEdge(edgeId) == null) {
                        g.addEdge(edgeId, sourceId, targetId);
                    }
                }
                firstNode = firstNode.child;
            }
        }

        return g;
    }

    /**
     * Function to parse the RSML files using the RsmlParser class
     *
     * @param path2RSMLs The path to the RSMLs
     * @return A Map with the date as key and the list of IRootModelParser as value
     * @throws IOException If an I/O error occurs
     */
    private Map<LocalDate, List<IRootModelParser>> parseRsmlFiles(String path2RSMLs, List<LocalDate> removedDates) throws IOException {
        RsmlParser rsmlParser = new RsmlParser(path2RSMLs, removedDates);
        Map<LocalDate, List<IRootModelParser>> result = RsmlParser.getRSMLsInfos(Paths.get(rsmlParser.path2RSMLs));
        result.forEach((date, rootModel4Parsers) -> {
            System.out.println("Date : " + date);
            rootModel4Parsers.forEach(System.out::println);
        });
        return result;
    }

    /**
     * Function to crop and resize the image using the specified parameters
     *
     * @param originalScaledImagePath The path to the original scaled image
     * @param subsamplingFactor       The subsampling factor
     * @param size                    The size of the image
     * @return The cropped and resized ImagePlus image
     */
    private ImagePlus cropAndResizeImage(String originalScaledImagePath, double subsamplingFactor, int size) {
        return VitimageUtils.cropImage(new ImagePlus(originalScaledImagePath), (int) (1400.0 / subsamplingFactor), (int) (350.0 / subsamplingFactor), 0, (int) ((10620.0 - 1400.0) / subsamplingFactor), (int) ((8783.0 - 350.0) / subsamplingFactor), size);
    }


    /**
     * Function to read and apply the transforms to the RootModel
     *
     * @param transformerPath The path to the transforms
     * @param rms             The RootModel
     * @param res2            The ImagePlus image
     * @param imgInitSize     The ImagePlus image of the initial size
     * @throws IOException If an I/O error occurs
     */
    private void readAndApplyTransforms(String transformerPath, RootModel rms, ImagePlus res2, ImagePlus imgInitSize) throws IOException {
        // Define these as class variables if the method is called multiple times
        final Pattern indexPattern = Pattern.compile("_(\\d+)\\.");
        final PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher("glob:**.transform.tif");

        List<Path> matchedPaths = new ArrayList<>();
        for (File file : Objects.requireNonNull(new File(transformerPath).listFiles())) {
            if (pathMatcher.matches(file.toPath())) {
                matchedPaths.add(file.toPath());
            }
        }

        // reorder path
        matchedPaths.sort(Comparator.comparingInt(o -> {
            Matcher matcher = indexPattern.matcher(o.toString());
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
            return -1;
        }));

        try {
            List<CompletableFuture<IndexedTransform>> futures = new ArrayList<>();
            // Create futures for each path
            for (int i = 0; i < matchedPaths.size(); i++) {
                final int index = i;
                Path path = matchedPaths.get(index);
                CompletableFuture<IndexedTransform> future = CompletableFuture.supplyAsync(() -> {
                    ItkTransform transform = new ItkTransform(ItkTransform.readAsDenseField(path.toString()));
                    return new IndexedTransform(index, transform);
                });
                futures.add(future);
            }

            // Wait for all futures to complete and collect the results
            List<IndexedTransform> indexedTransforms = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .thenApply(v -> futures.stream()
                            .map(CompletableFuture::join)
                            .collect(Collectors.toList()))
                    .get();

            // Sort the results based on the original index
            indexedTransforms.sort((it1, it2) -> Integer.compare(it1.index, it2.index));

            // Extract the transforms in the correct order
            transforms = indexedTransforms.stream()
                    .map(it -> it.transform)
                    .collect(Collectors.toList());

        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            for (Path path : matchedPaths) {
                ItkTransform transform = new ItkTransform(ItkTransform.readAsDenseField(path.toString()));
                transforms.add(transform);
            }
        }

        // creating a linear transform for the crop issue
        Point3d[] oldPos = new Point3d[1];
        Point3d[] newPos = new Point3d[1];
        oldPos[0] = new Point3d(0, 0, 0);
        newPos[0] = new Point3d(-PipelineParamHandler.getxMinCrop(), -PipelineParamHandler.getyMinCrop(), 0);
        ItkTransform linearTransform = ItkTransform.estimateBestTranslation3D(oldPos, newPos);


        for (ItkTransform transform : this.transforms) {
            rms.applyTransformToGeometry(transform, transforms.indexOf(transform) + 1);
        }
        rms.applyTransformToGeometry(linearTransform);
    }

    private void applySingleTransform(ItkTransform transform, RootModel rms) {
        for (Root root : rms.rootList) {
            Node firstNode = root.firstNode;
            while (firstNode != null) {
                Point3d point = new Point3d(firstNode.x, firstNode.y, firstNode.birthTime);
                point = transform.transformPoint(point);
                firstNode.x = (float) point.x;
                firstNode.y = (float) point.y;
                firstNode = firstNode.child;
            }
        }
    }

    // lookup for all the points of a root at time t
    private void showBlankWithPointOnRoot(List<Root> r, ImagePlus forSize) {
        ImagePlus blank = IJ.createImage("Blank", "RGB white", forSize.getWidth(), forSize.getHeight(), 1);
        List<DoublePoint> points;
        int birthTime = 0;
        int lastTime = forSize.getNSlices();

        for (Root root : r) {
            points = new ArrayList<>();
            Node firstnode = root.firstNode;
            birthTime = (int) firstnode.birthTime;
            // add the points to the blank image
            while (firstnode != null) {
                points.add(new DoublePoint(new double[]{firstnode.x, firstnode.y, firstnode.birthTime}));
                System.out.println("x : " + firstnode.x + " y : " + firstnode.y + " t : " + firstnode.birthTime);
                lastTime = (int) firstnode.birthTime;
                firstnode = firstnode.child;
            }

            // Perform clustering
            int k = root.getNodesList().size() / (lastTime - birthTime + 1);
            KMeansPlusPlusClusterer<DoublePoint> clusterer = new KMeansPlusPlusClusterer<>(k);
            List<CentroidCluster<DoublePoint>> clusters = clusterer.cluster(points);

            ImageProcessor ip = blank.getProcessor();
            for (int clusterIndex = 0; clusterIndex < clusters.size(); clusterIndex++) {
                Cluster<DoublePoint> cluster = clusters.get(clusterIndex);
                // random color
                ip.setColor(new Color((int) (Math.random() * 0x1000000)));
                for (DoublePoint point : cluster.getPoints()) {
                    //ip.drawDot((int) point.getPoint()[0], (int) point.getPoint()[1]);
                    ip.drawDot((int) clusters.get(clusterIndex).getCenter().getPoint()[0], (int) clusters.get(clusterIndex).getCenter().getPoint()[1]);
                    //ip.drawString(String.valueOf(clusters.get(clusterIndex).getCenter().getPoint()[2]), (int) clusters.get(clusterIndex).getCenter().getPoint()[0], (int) clusters.get(clusterIndex).getCenter().getPoint()[1]);
                    if (cluster.getPoints().get(0) == point) {
                        //ip.drawString(String.valueOf(point.getPoint()[2]), (int) point.getPoint()[0], (int) point.getPoint()[1]);
                        System.out.println("Cluster " + clusterIndex + " : " + point.getPoint()[0] + " " + point.getPoint()[1] + " " + point.getPoint()[2]);
                    }
                    if (point.getPoint()[2] < clusters.get(clusterIndex).getCenter().getPoint()[2]) {
                        // same but smaller string on image
                        //ip.drawString(String.valueOf(point.getPoint()[2]), (int) point.getPoint()[0], (int) point.getPoint()[1]);
                        System.out.println("Missclassified ? x : " + point.getPoint()[0] + " y : " + point.getPoint()[1] + " t : " + point.getPoint()[2]);
                    }
                }
            }
        }
        blank.show();

    }

    // Getting better points for interpolation
    public void getBetterPoints(RootModel rms) {
        Map<Root, List<List<Point3d>>> newPoints = new HashMap<>();
        Map<Root, List<Node>> insertionPoints = rms.getInsertionPoints();
        for (Root root : insertionPoints.keySet()) {
            newPoints.putIfAbsent(root, new ArrayList<>());
            for (Node node : insertionPoints.get(root)) {
                Node n = insertionPoints.get(root).get(insertionPoints.get(root).indexOf(node));
                newPoints.get(root).add(new ArrayList<>());
                newPoints.get(root).get(newPoints.get(root).size() - 1).add(new Point3d(n.x, n.y, n.birthTime));
                Node nChild = n.child;
                while ((nChild != null) && (nChild.birthTime == n.birthTime)) {
                    // there is a line between n and nChild, we want to get the points on this line
                    double x1 = n.x;
                    double y1 = n.y;
                    double x2 = nChild.x;
                    double y2 = nChild.y;
                    double dx = x2 - x1;
                    double dy = y2 - y1;
                    double d = Math.sqrt(dx * dx + dy * dy);
                    double step = 1;
                    while (step < d) {
                        double x = x1 + step * dx / d;
                        double y = y1 + step * dy / d;
                        newPoints.get(root).get(newPoints.get(root).size() - 1).add(new Point3d(x, y, n.birthTime));
                        step += STANDART_DIST;
                    }
                    n = nChild;
                    nChild = nChild.child;
                }
            }
        }
    }

    /**
     * Function to interpolate the points using the SplineFitter
     * Then display the interpolated points on the image
     *
     * @param rms The RootModel (source of the points)
     * @param img The ImagePlus image (destination of the points)
     */
    private void interpolatePoints(RootModel rms, ImagePlus img) {
        /*Map<Root, List<Node>> insertionPts = rms.getInsertionPoints();
        insertionPts.keySet().parallelStream().forEach(root -> {
            List<Node> nodes = insertionPts.get(root);
            nodes.forEach(node -> {
                // node is an insertion point
                // get its position and the positions of its childs that appeared at the same time
                if (root.order == 1)
                {
                    System.out.println("Root order 1");
                }
                List<Node> node2Interpolate = new ArrayList<>();
                node2Interpolate.add(node);
                while (node.child != null && node.child.birthTime == node.birthTime) {
                    node2Interpolate.add(node.child);
                    node = node.child;
                }
                try {
                    // find the best polynomial to interpolate the points
                    if (node2Interpolate.size() > 1) {
                        double[] x = new double[node2Interpolate.size()];
                        double[] y = new double[node2Interpolate.size()];
                        IntStream.range(0, node2Interpolate.size()).forEach(i -> {
                            x[i] = node2Interpolate.get(i).x;
                            y[i] = node2Interpolate.get(i).y;
                        });

                        CurveFitter curveFitter = new CurveFitter(x,y);
                        curveFitter.doFit(CurveFitter.POLY8);

                        // Display the interpolation on the image
                        ImageProcessor ip = img.getStack().getProcessor((int) node.birthTime).duplicate();
                        ip.setColor(Color.BLUE);
                        // node2Interpolate.forEach(n -> ip.drawOval((int) n.x, (int) n.y, 4, 4));
                        // draw full curve on the image TODO mayeb problematic plot
                        Node leftestNode = node2Interpolate.stream().min(Comparator.comparingDouble(n -> n.x)).get();
                        Node rightestNode = node2Interpolate.stream().max(Comparator.comparingDouble(n -> n.x)).get();
                        for (int i = (int) leftestNode.x; i < rightestNode.x; i++) {
                            ip.drawDot(i, (int) curveFitter.f(i));
                            // draw line between the points
                            if (i > leftestNode.x)
                                ip.drawLine((int) i - 1, (int) curveFitter.f(i - 1), (int) i, (int) curveFitter.f(i));
                        }

                        img.getStack().setProcessor(ip, (int) node.birthTime);
                        System.out.println("Interpolated points : " + node2Interpolate);
                    }
                } catch (Exception e) {
                    System.err.println("Interpolation failed: " + e.getMessage());
                }
            });
        });
        img.show();*/
        Map<Root, List<Node>> insertionPts = rms.getInsertionPoints();
        insertionPts.keySet().parallelStream().forEach(root -> {
            List<Node> nodes = insertionPts.get(root);
            nodes.forEach(node -> {
                // node is an insertion point
                // get its position and the positions of its children that appeared at the same time
                List<Node> node2Interpolate = new ArrayList<>();
                node2Interpolate.add(node);
                while (node.child != null && node.child.birthTime == node.birthTime) {
                    node2Interpolate.add(node.child);
                    node = node.child;
                }
                try {
                    // find the best polynomial to interpolate the points
                    if (node2Interpolate.size() > 1) {
                        double[] x = new double[node2Interpolate.size()];
                        double[] y = new double[node2Interpolate.size()];
                        IntStream.range(0, node2Interpolate.size()).forEach(i -> {
                            // Swap x and y coordinates
                            x[i] = node2Interpolate.get(i).y;
                            y[i] = node2Interpolate.get(i).x;
                        });

                        CurveFitter curveFitter = new CurveFitter(x, y);
                        curveFitter.doFit(CurveFitter.POLY8);

                        // Display the interpolation on the image
                        ImageProcessor ip = img.getStack().getProcessor((int) node.birthTime).duplicate();
                        ip.setColor(Color.BLUE);

                        // draw dots on interpolated points of x and draw line between these points
                        drawThing0(x, y, ip, curveFitter);

                        img.getStack().setProcessor(ip, (int) node.birthTime);
                        System.out.println("Interpolated points : " + node2Interpolate);
                    }
                } catch (Exception e) {
                    System.err.println("Interpolation failed: " + e.getMessage());
                }
            });
        });
        img.setTitle("Interpolated points - Polynomial");
        img.show();
    }

    /////////////// interpolating points ///////////////

    private void ensureIncreasingXValues(double[] x, double[] y) {
        boolean allIncreasing = true;
        boolean allDecreasing = true;

        for (int i = 1; i < x.length; i++) {
            if (x[i] < x[i - 1]) {
                allIncreasing = false;
            }
            if (x[i] > x[i - 1]) {
                allDecreasing = false;
            }
        }

        if (allDecreasing) {
            reverseArray(x);
            reverseArray(y);
        } else if (!allIncreasing) {
            // Find segments that are increasing or decreasing
            int start = 0;
            for (int i = 1; i < x.length; i++) {
                if ((x[i] < x[i - 1] && x[start] < x[i - 1]) || (x[i] > x[i - 1] && x[start] > x[i - 1])) {
                    handleSegment(Arrays.copyOfRange(x, start, i), Arrays.copyOfRange(y, start, i));
                    start = i;
                }
            }
            handleSegment(Arrays.copyOfRange(x, start, x.length), Arrays.copyOfRange(y, start, y.length));
        }
    }

    private void handleSegment(double[] xSegment, double[] ySegment) {
        if (xSegment.length < 2) {
            return; // Not enough points to interpolate
        }
        if (xSegment[0] > xSegment[xSegment.length - 1]) {
            reverseArray(xSegment);
            reverseArray(ySegment);
        }
        ensureIncreasingXValues(xSegment, ySegment);
    }

    private void reverseArray(double[] array) {
        int n = array.length;
        for (int i = 0; i < n / 2; i++) {
            double temp = array[i];
            array[i] = array[n - 1 - i];
            array[n - 1 - i] = temp;
        }
    }

    /**
     * Function to interpolate the points using the RBFInterpolator class
     *
     * @param rms The RootModel (source of the points)
     * @param img The ImagePlus image (destination of the points)
     */
    private void interpolatePointsRBF(RootModel rms, ImagePlus img) {
        /*Map<Root, List<Node>> insertionPts = rms.getInsertionPoints();
        insertionPts.keySet().forEach(root -> {
            List<Node> nodes = insertionPts.get(root);
            nodes.forEach(node -> {
                // node is an insertion point
                // get its position and the positions of its childs that appeared at the same time
                List<Node> node2Interpolate = new ArrayList<>();
                node2Interpolate.add(node);
                while (node.child != null && node.child.birthTime == node.birthTime) {
                    node2Interpolate.add(node.child);
                    node = node.child;
                }
                try {
                    // use RBF interpolation if we have more than one point
                    if (node2Interpolate.size() > 1) {
                        double[] x = new double[node2Interpolate.size()];
                        double[] y = new double[node2Interpolate.size()];
                        IntStream.range(0, node2Interpolate.size()).forEach(i -> {
                            x[i] = node2Interpolate.get(i).x;
                            y[i] = node2Interpolate.get(i).y;
                        });

                        // Create an RBF interpolator
                        double epsilon = 1.0; // Set an appropriate epsilon value
                        RBFInterpolator rbfInterpolator = new RBFInterpolator(x, y, epsilon);

                        // Display the interpolation on the image
                        ImageProcessor ip = img.getStack().getProcessor((int) node.birthTime).duplicate();
                        ip.setColor(Color.BLUE);

                        // Find the range of x values to plot
                        double minX = Arrays.stream(x).min().getAsDouble();
                        double maxX = Arrays.stream(x).max().getAsDouble();

                        // Plot the interpolated curve
                        for (double i = minX; i <= maxX; i += 0.1) {
                            ip.drawDot((int) i, (int) rbfInterpolator.interpolate(i));

                            // draw line between the points
                            if (i > minX)
                                ip.drawLine((int) i - 1, (int) rbfInterpolator.interpolate(i - 1), (int) i, (int) rbfInterpolator.interpolate(i));
                        }

                        img.getStack().setProcessor(ip, (int) node.birthTime);
                        System.out.println("Interpolated points : " + node2Interpolate);
                    }
                } catch (Exception e) {
                    System.err.println("Interpolation failed: " + e.getMessage());
                }
            });
        });
        img.show();*/
        Map<Root, List<Node>> insertionPts = rms.getInsertionPoints();
        insertionPts.keySet().forEach(root -> {
            List<Node> nodes = insertionPts.get(root);
            nodes.forEach(node -> {
                // node is an insertion point
                // get its position and the positions of its children that appeared at the same time
                List<Node> node2Interpolate = new ArrayList<>();
                node2Interpolate.add(node);
                while (node.child != null && node.child.birthTime == node.birthTime) {
                    node2Interpolate.add(node.child);
                    node = node.child;
                }
                try {
                    // use RBF interpolation if we have more than one point
                    if (node2Interpolate.size() > 1) {
                        double[] x = new double[node2Interpolate.size()];
                        double[] y = new double[node2Interpolate.size()];
                        IntStream.range(0, node2Interpolate.size()).forEach(i -> {
                            // Swap x and y coordinates
                            x[i] = node2Interpolate.get(i).y;
                            y[i] = node2Interpolate.get(i).x;
                        });

                        // Create an RBF interpolator
                        double epsilon = 1.0; // Set an appropriate epsilon value
                        RBFInterpolator rbfInterpolator = new RBFInterpolator(x, y, epsilon);

                        // Display the interpolation on the image
                        ImageProcessor ip = img.getStack().getProcessor((int) node.birthTime).duplicate();
                        ip.setColor(Color.BLUE);

                        // draw dots on interpolated points of x and draw line between these points
                        drawThing0(x, y, ip, rbfInterpolator);

                        img.getStack().setProcessor(ip, (int) node.birthTime);
                        System.out.println("Interpolated points : " + node2Interpolate);
                    }
                } catch (Exception e) {
                    System.err.println("Interpolation failed: " + e.getMessage());
                }
            });
        });
        img.setTitle("Interpolated points - RBF");
        img.show();
    }

    /**
     * Function to interpolate the points using the Bezier function method
     *
     * @param rms The RootModel (source of the points)
     * @param img The ImagePlus image (destination of the points)
     */
    private void interpolatePointsBezier(RootModel rms, ImagePlus img) {
        Map<Root, List<Node>> insertionPts = rms.getInsertionPoints();
        insertionPts.keySet().forEach(root -> {
            List<Node> nodes = insertionPts.get(root);
            nodes.forEach(node -> {
                List<Node> node2Interpolate = new ArrayList<>();
                node2Interpolate.add(node);
                while (node.child != null && node.child.birthTime == node.birthTime) {
                    node2Interpolate.add(node.child);
                    node = node.child;
                }
                try {
                    if (node2Interpolate.size() > 2) {
                        double[] x = new double[node2Interpolate.size()];
                        double[] y = new double[node2Interpolate.size()];
                        for (int i = 0; i < node2Interpolate.size(); i++) {
                            x[i] = node2Interpolate.get(i).x;
                            y[i] = node2Interpolate.get(i).y;
                        }
                        // Interpolation par courbe de Bézier
                        ImageProcessor ip = img.getStack().getProcessor((int) node.birthTime);
                        ip.setColor(Color.BLUE);

                        for (double t = 0; t <= 1; t += 0.01) {
                            double xt = 0, yt = 0;
                            int n = node2Interpolate.size() - 1;
                            for (int i = 0; i <= n; i++) {
                                double binomialCoeff = binomialCoefficient(n, i) * Math.pow(1 - t, n - i) * Math.pow(t, i);
                                xt += binomialCoeff * x[i];
                                yt += binomialCoeff * y[i];
                            }
                            ip.drawDot((int) xt, (int) yt);

                            // draw line between the points
                            if (t > 0) {
                                double prevT = t - 0.01;
                                double prevX = 0, prevY = 0;
                                for (int i = 0; i <= n; i++) {
                                    double binomialCoeff = binomialCoefficient(n, i) * Math.pow(1 - prevT, n - i) * Math.pow(prevT, i);
                                    prevX += binomialCoeff * x[i];
                                    prevY += binomialCoeff * y[i];
                                }
                                ip.drawLine((int) prevX, (int) prevY, (int) xt, (int) yt);
                            }
                        }

                        img.getStack().setProcessor(ip, (int) node.birthTime);
                        System.out.println("Interpolated points : " + node2Interpolate);
                    }
                } catch (Exception e) {
                    System.err.println("Interpolation failed: " + e.getMessage());
                }
            });
        });
        img.setTitle("Interpolated points - Bezier");
        img.show();
    }

    private double binomialCoefficient(int n, int k) {
        double res = 1;
        if (k > n - k) k = n - k;
        for (int i = 0; i < k; ++i) {
            res *= (n - i);
            res /= (i + 1);
        }
        return res;
    }

    /**
     * Function to interpolate the points using the SplineFitter class
     *
     * @param rms The RootModel (source of the points)
     * @param img The ImagePlus image (destination of the points)
     */
    private void interpolatePointsSplineFitter(RootModel rms, ImagePlus img) {
        Map<Root, List<Node>> insertionPts = rms.getInsertionPoints();
        insertionPts.keySet().forEach(root -> {
            List<Node> nodes = insertionPts.get(root);
            nodes.forEach(node -> {
                List<Node> node2Interpolate = new ArrayList<>();
                node2Interpolate.add(node);
                while (node.child != null && node.child.birthTime == node.birthTime) {
                    node2Interpolate.add(node.child);
                    node = node.child;
                }
                try {
                    if (node2Interpolate.size() > 2) {
                        float[] x = new float[node2Interpolate.size()];
                        float[] y = new float[node2Interpolate.size()];
                        for (int i = 0; i < node2Interpolate.size(); i++) {
                            // Swap x and y coordinates
                            x[i] = node2Interpolate.get(i).y;
                            y[i] = node2Interpolate.get(i).x;
                        }

                        SplineFitter fitter = new SplineFitter(x, y, x.length);


                        // Display the interpolation on the image
                        ImageProcessor ip = img.getStack().getProcessor((int) node.birthTime);
                        ip.setColor(Color.BLUE);


                        // draw dots on interpolated points of x and draw line between these points
                        drawThing0(x, y, ip, fitter);

                        img.getStack().setProcessor(ip, (int) node.birthTime);
                        System.out.println("Interpolated points : " + node2Interpolate);
                    }
                } catch (Exception e) {
                    System.err.println("Interpolation failed: " + e.getMessage());
                }
            });
        });
        img.setTitle("Interpolated points - Spline Fitter");
        img.show();
    }

    private void interpolatePointsCurveFitter(RootModel rms, ImagePlus img) {
        Map<Root, List<Node>> insertionPts = rms.getInsertionPoints();
        final String[] name = {""};
        insertionPts.keySet().forEach(root -> {
            List<Node> nodes = insertionPts.get(root);
            nodes.forEach(node -> {
                List<Node> node2Interpolate = new ArrayList<>();
                node2Interpolate.add(node);
                while (node.child != null && node.child.birthTime == node.birthTime) {
                    node2Interpolate.add(node.child);
                    node = node.child;
                }
                try {
                    if (node2Interpolate.size() > 2) {
                        double[] x = new double[node2Interpolate.size()];
                        double[] y = new double[node2Interpolate.size()];
                        for (int i = 0; i < node2Interpolate.size(); i++) {
                            // Swap x and y coordinates
                            x[i] = node2Interpolate.get(i).y;
                            y[i] = node2Interpolate.get(i).x;
                        }

                        CurveFitter curveFitter = new CurveFitter(x, y);
                        curveFitter.doFit(CurveFitter.GAUSSIAN);
                        name[0] = curveFitter.getName();

                        // Display the interpolation on the image
                        ImageProcessor ip = img.getStack().getProcessor((int) node.birthTime);
                        ip.setColor(Color.BLUE);

                        // draw dots on interpolated points of x and draw line between these points
                        drawThing0(x, y, ip, curveFitter);

                        img.getStack().setProcessor(ip, (int) node.birthTime);
                        System.out.println("Interpolated points : " + node2Interpolate);

                    }
                } catch (Exception e) {
                    System.err.println("Interpolation failed: " + e.getMessage());
                }
            });
        });
        img.setTitle("Interpolated points - " + name[0] + " Curve Fitter");
        img.show();
    }

    // draw things
    private void drawOnBlackLines(double[] x, double[] y, ImageProcessor ip) {
        for (int i = 0; i < x.length - 1; i++) {
            double x1 = x[i];
            double y1 = y[i];
            double x2 = x[i + 1];
            double y2 = y[i + 1];

            // Calculate the distance between consecutive points
            double dx = x2 - x1;
            double dy = y2 - y1;

            // Sample points along the line segment at regular intervals
            for (double t = 0; t <= 1; t += 0.01) {
                double px = x1 + t * dx;
                double py = y1 + t * dy;

                // Check the intensity of surrounding pixels and change their color if necessary
                for (int xOffset = -1; xOffset <= 1; xOffset++) {
                    for (int yOffset = -1; yOffset <= 1; yOffset++) {
                        int intensity = ip.getPixel((int) px + xOffset, (int) py + yOffset);
                        if (intensity <= 30) { // Adjust threshold as needed
                            ip.setColor(Color.ORANGE);
                            ip.drawDot((int) py + yOffset, (int) px + xOffset);
                        }
                    }
                }
            }
        }
    }

    private void drawThing0(double[] x, double[] y, ImageProcessor ip, CurveFitter curveFitter) {
        for (int i = (int) x[0]; i < x[x.length - 1]; i++) {
            ip.drawDot((int) curveFitter.f(i), i);
            // draw line between the points
            if (i > x[0])
                ip.drawLine((int) curveFitter.f(i - 1), i - 1, (int) curveFitter.f(i), i);
        }
    }

    private void drawThing0(float[] x, float[] y, ImageProcessor ip, SplineFitter fitter) {
        for (float xPoint : x) {
            float interpolatedY = (float) fitter.evalSpline(xPoint);
            ip.drawDot((int) interpolatedY, (int) xPoint);
            // draw line between the points
            if (xPoint > 0) {
                float prevInterpolatedY = (float) fitter.evalSpline(xPoint - 1);
                ip.drawLine((int) prevInterpolatedY, (int) (xPoint - 1), (int) interpolatedY, (int) xPoint);
            }
        }
    }

    private void drawThing0(double[] x, double[] y, ImageProcessor ip, RBFInterpolator interpolator) {
        for (double xPoint : x) {
            double interpolatedY = interpolator.interpolate(xPoint);
            ip.drawDot((int) interpolatedY, (int) xPoint);
            if (xPoint > 0) {
                double prevInterpolatedY = interpolator.interpolate(xPoint - 1);
                ip.drawLine((int) prevInterpolatedY, (int) (xPoint - 1), (int) interpolatedY, (int) xPoint);
            }
        }
    }

    // Helper class to hold the index and the transform
    private static class IndexedTransform {
        int index;
        ItkTransform transform;

        IndexedTransform(int index, ItkTransform transform) {
            this.index = index;
            this.transform = transform;
        }
    }
}

class RBFInterpolator {
    private final double[][] points;
    private final double[] values;
    private final double epsilon;

    public RBFInterpolator(double[] x, double[] y, double epsilon) {
        this.points = new double[x.length][2];
        this.values = new double[y.length];
        this.epsilon = epsilon;
        for (int i = 0; i < x.length; i++) {
            this.points[i][0] = x[i];
            this.points[i][1] = 0;
            this.values[i] = y[i];
        }
    }

    public double interpolate(double x) {
        double result = 0;
        double normFactor = 0;
        for (int i = 0; i < points.length; i++) {
            double distance = Math.abs(x - points[i][0]);
            double weight = gaussianRBF(distance);
            result += weight * values[i];
            normFactor += weight;
        }
        return result / normFactor;
    }

    private double gaussianRBF(double distance) {
        return Math.exp(-Math.pow(distance / epsilon, 2));
    }
}