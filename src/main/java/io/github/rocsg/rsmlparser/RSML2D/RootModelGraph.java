package io.github.rocsg.rsmlparser.RSML2D;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.measure.CurveFitter;
import ij.measure.SplineFitter;
import ij.plugin.RGBStackMerge;
import ij.process.ImageProcessor;
import io.github.rocsg.fijiyama.common.VitimageUtils;
import io.github.rocsg.fijiyama.registration.ItkTransform;
import io.github.rocsg.rsml.Node;
import io.github.rocsg.rsml.Root;
import io.github.rocsg.rsml.RootModel;
import io.github.rocsg.rsmlparser.IRootModelParser;
import io.github.rocsg.rsmlparser.IRootParser;
import io.github.rocsg.rsmlparser.RSML2DplusT.RSMLWriter2DT;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.github.rocsg.rsmlparser.RSML2D.Rsml2DParser.*;
import static io.github.rocsg.rstplugin.PipelineParamHandler.configurePipelineParams;

/**
 * The RootModelGraph class represents a graph of root models.
 * It provides methods to read RSML files, apply transformations, and visualize the root models.
 */
public class RootModelGraph {


    final List<RootModel> rootModels; // List of root models
    public List<org.graphstream.graph.Graph> graphs; // List of graphs
    PipelineParamHandler pph; // Pipeline parameter handler
    ImagePlus image; // ImagePlus object representing the image
    List<ItkTransform> transforms; // List of ITK transforms
    boolean removed = false; // Flag indicating if any dates were removed TODO

    /**
     * Default constructor for the RootModelGraph class.
     * Initializes the RootModelGraph with default paths.
     *
     * @throws IOException If an I/O error occurs
     */
    public RootModelGraph() throws IOException {// Which box to work on
        this("D:\\loaiu\\MAM5\\Stage\\data\\UC3\\Rootsystemtracker\\Original_Data\\B73_R12_01\\", "D:\\loaiu\\MAM5\\Stage\\data\\UC3\\Rootsystemtracker\\Output_Data\\Process\\B73_R12_01\\Transforms_2\\", "D:\\loaiu\\MAM5\\Stage\\data\\TestParser\\Output\\Inventory\\", "D:\\loaiu\\MAM5\\Stage\\data\\UC3\\Rootsystemtracker\\Output_Data\\Inventory\\", "D:\\loaiu\\MAM5\\Stage\\data\\UC3\\Rootsystemtracker\\Output_Data\\Process\\B73_R12_01\\11_stack.tif", "D:\\loaiu\\MAM5\\Stage\\data\\UC3\\Rootsystemtracker\\Output_Data\\Process\\B73_R12_01\\22_registered_stack.tif", "D:\\loaiu\\MAM5\\Stage\\data\\UC3\\Rootsystemtracker\\Output_Data\\Process\\B73_R12_01\\12_stack_cropped.tif");
    }

    ///////////////////////////////// EXPERIMENAL CODE !!! /////////////////////////////////

//    Pipeline :
//    we get the params of the image (subsampling factor and crop position / distance)
//    We get the RSML files from the path2RSMLs in a map
//    we get the registered images
//    correct the missing dates if there are (troublesome)
//     we get the transforms of the image and apply them to the parsers
//    We match every Root if needed => modification of the identifier of each root (String)
        //  -> BEWARE THE VARIABLE OF ANONIMIZATION IN THE RSML2DParser CLASS
//    We create an object RootModel from the parsers (2D+t) : the roots nodes are stacked on top of each other
//     We ajust the RootModel : interpolation with time and alignment

    /**
     * Constructor of the RootModelGraph class.
     * The RootModelGraph is created by reading the RSMLs from the specified path.
     * The RootModels are extracted from the RSMLs.
     * The RootModels are then converted to JGraphT Graphs.
     * The JGraphT Graphs are then converted to ImagePlus images.
     * The images are then resized using the resampling factor specified in the PipelineParamHandler.
     * The transforms are read from the specified path.
     *
     * @param path2RSMLs      The path to the RSMLs
     * @param transformerPath The path to the transforms
     * @param inputPathPPH    The path to the input PipelineParamHandler
     * @param outputPathPPH   The path to the output PipelineParamHandler
     * @param originalScaledImagePath The path to the original scaled image
     * @param registeredImagePath The path to the registered image
     * @param croppedImage The path to the cropped image
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

        // Take and replace the above variables with the ones present in a CSV file
        String csvPath = "D:\\loaiu\\MAM5\\Stage\\data\\UC3\\Rootsystemtracker\\Output_Data\\Process\\" + "InfoSerieRootSystemTracker.csv";
        // Read the CSV file
        try {
            List<String> lines = Files.readAllLines(Paths.get(csvPath));
            for (String line : lines) {
                String[] parts = line.split(",");
                if (parts.length == 2) {
                    configMap.put(parts[0], parts[1]);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        configurePipelineParams(configMap);

        pph = new PipelineParamHandler(inputPathPPH, outputPathPPH);
        configurePipelineParams(configMap);

        List<LocalDateTime> removedDates = new ArrayList<>(); // DANGER - hard to manage
        Map<LocalDateTime, IRootModelParser> result = parseRsmlFiles(path2RSMLs, removedDates);
        //List<LocalDateTime> datesFromImages = new ArrayList<>();

//        /*****DEBUG*****/
//        ConcurrentHashMap<String, LocalDateTime> fileDates = new ConcurrentHashMap<>();
//        // Get the date of the RSML files
//        try {
//            Path path2Images = Paths.get(path2RSMLs);
//            Files.list(path2Images)
//                    .parallel()
//                    .filter(path -> path.toString().matches(".*\\.(jpg)$"))
//                    .forEach(path -> {
//                        fileDates.put(path.toString(), Objects.requireNonNull(getDate(path.toString().split("\\.")[0])));
//                    });
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
//
//        // From fileDates, get the dates of the images
//        fileDates.forEach((path, date) -> {
//            if (!datesFromImages.contains(date)) datesFromImages.add(date);
//        });
//
//        HashSet<LocalDateTime> totalDates = new HashSet<>(result.keySet());
//        totalDates.addAll(removedDates);
//        totalDates.addAll(datesFromImages);
//        // Sort the dates in ascending order
//        List<LocalDateTime> sortedDates = new ArrayList<>(totalDates);
//        sortedDates.sort(LocalDateTime::compareTo);
//
//        // Adding the dates to result
//        sortedDates.forEach(date -> {
//            if (!result.containsKey(date)) {
//                result.put(date, null);
//            }
//        });
//
//        /*****DEBUG*****/

        //readAndApplyTransforms4Parser(transformerPath, result);


//        RootModel rms = new RootModel();
//        rms = (RootModel) rms.createRootModels(result, (float) PipelineParamHandler.subsamplingFactor);
        ImagePlus refImage = new ImagePlus(registeredImagePath); // Realigned and cropped image

        // If removedDatesIndex is not empty, remove the corresponding slices from the image
        image = refImage;
        // Calibrate image
        IJ.run(image, "Enhance Contrast", "saturated=0.35");
        // Read all the transforms and apply them
        ImagePlus imgInitSize = new ImagePlus(originalScaledImagePath); // Base image, raf

        //displayOnImage(createGraphFromRM(rms), imgInitSize, true).show();

        // Remove the image of index removedDatesIndex from the image
        if (!removedDates.isEmpty()) {
            refImage.getStack().deleteSlice(1);
            imgInitSize.getStack().deleteSlice(1);
            refImage.getStack().deleteSlice(1);
            imgInitSize.getStack().deleteSlice(1);
            removed = true;
        }

        //RootModel basicRM = new RootModel();
        //basicRM = (RootModel) basicRM.createRootModels(result, (float) 4);
        //displayOnImage(createGraphFromRM(basicRM), imgInitSize, true).show();
//        readAndApplyTransforms(transformerPath, rms);
//
//        displayOnImage(createGraphFromRM(rms), refImage, true).show();
//        rms.adjustRootModel(refImage);
//
//
//        ImagePlus img3 = displayOnImage(createGraphFromRM(rms), refImage);
//        img3.show();



        readAndApplyTransforms4Parser(transformerPath, result);
        Map<String, List<Boolean>> rootPresenceMap = getRootPresenceMap(result);
        Map<String, List<String>> classifyRoots = classifyRoots(rootPresenceMap);
        //result = gottaClassifyThemAllByCluster(result, rootPresenceMap, classifyRoots);
        result = gottaClassifyThemAll(result, rootPresenceMap, classifyRoots);


        Rsml2DParser.anonymize = false;
        Map<LocalDateTime, IRootModelParser> result4Compare = parseRsmlFiles(path2RSMLs, removedDates);
        readAndApplyTransforms4Parser(transformerPath, result4Compare);

        compareRoots(result, result4Compare);


        RootModel rms = new RootModel();
        rms = (RootModel) rms.createRootModels(result4Compare, 1.0F);
        ImagePlus refImage2 = refImage.duplicate();
        displayOnImage(createGraphFromRM(rms), refImage, true).show();
        rms.adjustRootModel(refImage2);
        displayOnImage(createGraphFromRM(rms), refImage2).show();

        ImagePlus finalImage = projectRsmlOnImage(rms, refImage, 1, refImage.getStackSize(), VitimageUtils.stackToSlices(refImage));
        finalImage.show();

        // Save RootModel before and after adjustment
        String path2NewRSML = path2RSMLs + "\\NewRSMLs\\" + LocalDate.now() + ".rsml";
        // Create folder if not exists
        Path path = Paths.get(path2NewRSML).getParent();
        if (!Files.exists(path)) Files.createDirectories(path);
        //rms.writeRSML3D(new File(path2NewRSML).getAbsolutePath().replace("\\", "/"), "", true, false);

        RSMLWriter2DT.writeRSML(rms, path2NewRSML);

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
        // Stop execution but not the running programs

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
     * Function to parse the RSML files using the RsmlParser class.
     *
     * @param path2RSMLs The path to the RSMLs.
     * @param removedDates A list to store the dates of removed RSML files.
     * @return A Map with the date as key and the list of IRootModelParser as value.
     */
    private Map<LocalDateTime, IRootModelParser> parseRsmlFiles(String path2RSMLs, List<LocalDateTime> removedDates) {
        Rsml2DParser rsml2DParser = new Rsml2DParser(path2RSMLs, removedDates);
        Map<LocalDateTime, List<IRootModelParser>> result = Rsml2DParser.getRSMLsInfos(Paths.get(rsml2DParser.path2RSMLs));
        removedDates.addAll(Rsml2DParser.removedDates);
        result.forEach((date, rootModel4Parsers) -> {
            System.out.println("Date : " + date);
            rootModel4Parsers.forEach(System.out::println);
        });
        TreeMap<LocalDateTime, IRootModelParser> res = new TreeMap<>();
        for (LocalDateTime dateTime : result.keySet()) {
            assert result.get(dateTime).size() == 1;
            res.putIfAbsent(dateTime, result.get(dateTime).get(0));
        }
        return res;
    }

    /**
     * Displays the given graph on the provided image.
     *
     * @param g The graph to be displayed.
     * @param img The image on which the graph will be displayed.
     * @return An ImagePlus object with the graph displayed on it.
     */
    public static ImagePlus displayOnImage(org.graphstream.graph.Graph g, ImagePlus img) {
        return displayOnImage(g, img, false);
    }

    /**
     * Displays the given graph on the provided image with an option to stack.
     *
     * @param g The graph to be displayed.
     * @param img The image on which the graph will be displayed.
     * @param justStack A boolean indicating whether to stack the images.
     * @return An ImagePlus object with the graph displayed on it.
     */
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

            int cont = 0;
            for (org.graphstream.graph.Node node : g) {
                cont++;
                if (!(cont % 4 == 0)) continue;
                Object[] xyz = node.getAttribute("xyz?");
                double x = (float) xyz[0];
                double y = (float) xyz[1];
                double t = (float) xyz[2];
                g2d.setColor(Color.RED);
                boolean isInsertionPoint = (boolean) xyz[3];
                //if (justStack && t == i) g2d.fill(new Ellipse2D.Double(x - 2, y - 2, 2, 2));
                // random color

                if (justStack) {
                    g2d.setColor(new Color((int) (Math.random() * 0x1000000)));
                    g2d.fill(new Ellipse2D.Double(x - 4, y - 4, 4, 4));
                } else if (!justStack && t <= i) {
                    g2d.setColor(Color.blue);
                    g2d.fill(new Ellipse2D.Double(x - 4, y - 4, 4, 4));
                }
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
                g2d.setColor(Color.RED);
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

    /**
     * Creates a graph from the given RootModel.
     *
     * @param rootModel The RootModel object containing the RSML data.
     * @return A GraphStream graph representing the RootModel.
     */
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
                        //g.addEdge(edgeId, sourceId, targetId); // TODO why ? problème récent
                    }
                }
                firstNode = firstNode.child;
            }
        }

        return g;
    }

    /**
     * This static method projects the Root System Markup Language (RSML) model onto an image.
     * It creates a grayscale image for each time point in the model, then merges this with the registered stack image.
     * The resulting images are combined into a stack and returned.
     *
     * @param rm          the RootModel object which contains the RSML data.
     * @param imgInitSize the initial size of the images.
     * @param zoomFactor  the zoom factor to apply.
     * @param Nt          the number of time points.
     * @param tabReg      an array of registered images to merge with the RSML projections.
     * @return an ImagePlus object which is a stack of images with the RSML model projected onto them.
     */
    public static ImagePlus projectRsmlOnImage(RootModel rm, ImagePlus imgInitSize, int zoomFactor, int Nt, ImagePlus[] tabReg) {
        // Start a timer to measure the execution time of this method
        Timer t = new Timer();

        // Create an array to store processed images
        ImagePlus[] processedImages = new ImagePlus[Nt];

        try {
            // Loop over each time point in the model
            IntStream.range(0, Nt).parallel().forEach(i -> {
                // Create a grayscale image of the RSML model at this time point
                ImagePlus imgRSML = rm.createColorfulImageWithTime(imgInitSize, zoomFactor, false, (i + 1), true,
                        new boolean[]{true, true, true, false, true}, new double[]{2, 2});

                // Set the display range of the image
                imgRSML.setDisplayRange(0, Nt + 3);

                // Merge the grayscale image with the registered stack image
                processedImages[i] = RGBStackMerge.mergeChannels(new ImagePlus[]{tabReg[i], imgRSML}, true);
                // Convert the image to RGB color
                IJ.run(processedImages[i], "RGB Color", "");
            });
        } catch (Exception e) {
            // Loop over each time point in the model
            IntStream.range(0, Nt).forEach(i -> {
                // Create a grayscale image of the RSML model at this time point
                ImagePlus imgRSML = rm.createGrayScaleImageWithTime(imgInitSize, zoomFactor, false, (i + 1), true,
                        new boolean[]{true, true, true, false, true}, new double[]{2, 2});

                // Set the display range of the image
                imgRSML.setDisplayRange(0, Nt + 3);

                // Merge the grayscale image with the registered stack image
                processedImages[i] = RGBStackMerge.mergeChannels(new ImagePlus[]{tabReg[i], imgRSML}, true);
                // Convert the image to RGB color
                IJ.run(processedImages[i], "RGB Color", "");
            });
        }

        // Print the execution time of this method
        System.out.println("Updating root model took : ");

        // Return the image
        return VitimageUtils.slicesToStack(processedImages);
    }

    /**
     * Function to read and apply the transforms to the RootModel.
     *
     * @param transformerPath The path to the transforms.
     * @param rms             The RootModel.
     */
    private void readAndApplyTransforms(String transformerPath, RootModel rms) {
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

        rms.applyTransformToGeometry(linearTransform);
        for (ItkTransform transform : this.transforms) {
            System.out.println("Index : " + (transforms.indexOf(transform) + 1));
            rms.applyTransformToGeometry(transform, transforms.indexOf(transform) + 1);
        }

    }

    /**
     * Reads and applies transforms to the RootModelParser objects.
     *
     * @param transformerPath The path to the transforms.
     * @param resultOfParsing A map of parsed RSML files with their corresponding dates.
     */
    private void readAndApplyTransforms4Parser(String transformerPath, Map<LocalDateTime, IRootModelParser> resultOfParsing) {
        double scaleFactor = PipelineParamHandler.subsamplingFactor;
        for (IRootModelParser rootModel : resultOfParsing.values()) {
            ((RootModel4Parser) rootModel).scaleGeometry(scaleFactor);
        }

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

        if (removed) {
            transforms.remove(0);
            transforms.remove(0);
        }

        int index = 0;
        for (IRootModelParser rootModel4Parser : resultOfParsing.values()) {
            if (index == resultOfParsing.keySet().size() - 1) break;
            ((RootModel4Parser) rootModel4Parser).applyTransformToGeometry(transforms.get(index), 0);
            ((RootModel4Parser) rootModel4Parser).applyTransformToGeometry(linearTransform, 0);
            index++;
        }
        ((RootModel4Parser) resultOfParsing.get(resultOfParsing.keySet().toArray()[resultOfParsing.keySet().size() - 1])).applyTransformToGeometry(linearTransform, 0);
    }

    /**
     * Compares the roots between two sets of root models and prints the comparison results.
     *
     * @param result A map containing the root models to be compared, with the date as the key.
     * @param result4Compare A map containing the ground truth root models, with the date as the key.
     */
    public void compareRoots(Map<LocalDateTime, IRootModelParser> result, Map<LocalDateTime, IRootModelParser> result4Compare) {
        // Parcourir chaque date pour effectuer les comparaisons
        for (LocalDateTime date : result.keySet()) {
            IRootModelParser model = result.get(date);
            IRootModelParser groundTruth = result4Compare.get(date);

            if (model == null || groundTruth == null) continue;

            // Récupérer les labels des racines pour comparaison
            Map<String, String> modelLabels = model.getRoots().stream()
                    .collect(Collectors.toMap(IRootParser::getId, IRootParser::getLabel));
            Map<String, String> groundTruthLabels = groundTruth.getRoots().stream()
                    .collect(Collectors.toMap(IRootParser::getId, IRootParser::getLabel));

            // Calcul des racines manquantes
            Set<String> missingRoots = new HashSet<>(groundTruthLabels.keySet());
            missingRoots.removeAll(modelLabels.keySet());

            // Calcul des racines mal classifiées
            int misclassifiedRoots = 0;
            for (String rootId : groundTruthLabels.keySet()) {
                if (modelLabels.containsKey(rootId)) {
                    String predictedLabel = modelLabels.get(rootId);
                    String trueLabel = groundTruthLabels.get(rootId);
                    if (!predictedLabel.equals(trueLabel)) {
                        misclassifiedRoots++;
                    }
                }
            }

            // Affichage des résultats pour chaque date
            System.out.println("Date: " + date);
            System.out.println("Unclassified Roots: " + missingRoots.size());
            System.out.println("Misclassified Roots: " + misclassifiedRoots);
            System.out.println("Total number of Roots: " + groundTruthLabels.size());
            System.out.println("-----------------------------------");
        }
    }



    /////////////// interpolating points - NO USE ANYMORE  ///////////////

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