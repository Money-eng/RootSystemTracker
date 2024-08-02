package io.github.rocsg.rsmlparser.RSML2D;

import ij.ImagePlus;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Enum representing different selection strategies for RSML files.
 */
enum SelectionStrategy {
    LAST_VERSION,
    FIRST_VERSION,
    MOST_LINES,
    LEAST_ZEROS,
    LEAST_ZEROS_MOST_LINES,
    MOST_LINES_LEAST_ZEROS,
    MOST_RECENT_TIMESTAMP,
    MOST_COMPLEXITY,
    MOST_ORGANS,
    LEAST_DUPLICATES,
    CLOSEST_PIXEL_VALUE,
    CLOSEST_PIXEL_VALUE_MOST_ORGANS,
    RANDOM
}

/**
 * Utility class for handling RSML files.
 */
public class RSMLFileUtils {

    // Backup selection strategy in case of a tie
    public static SelectionStrategy backupStrategy = SelectionStrategy.LAST_VERSION;

    /**
     * Checks the uniqueness of RSML files in a given folder and selects files based on the provided strategy.
     *
     * @param folderPath the path to the folder containing RSML files
     * @param strategy   the selection strategy to use
     * @return a stack of selected RSML file paths
     */
    public static TreeSet<String> checkUniquenessRSMLs(Path folderPath, ConcurrentHashMap<String, LocalDate> dates, SelectionStrategy strategy, List<LocalDate> removedDates) {
        try {
            Map<String, List<Path>> groupedFiles = Files.list(folderPath)
                    .filter(path -> path.toString().matches(".*\\.(rsml|rsml\\d{2})$"))
                    .collect(Collectors.groupingBy(path -> path.toString().split("\\.")[0]));

            HashSet<String> keptRsmlFiles = new HashSet<>();
            HashSet<Path> removedRsmlFiles = new HashSet<>();

            // validate every single RSML files
            for (List<Path> paths : groupedFiles.values()) {
                for (Path path : paths) {
                    if (!validateRSMLFile(path)) {
                        System.err.println("Invalid RSML file: " + path);
                        boolean corrected = correctInvalidRSMLFile(path);
                        if (!corrected) {
                            removedRsmlFiles.add(path);
                        } else {
                            System.err.println("Managed to correct RSML file: " + path);
                            // replace the path with the corrected one
                            Path correctedPath = path.resolveSibling(path.getFileName().toString().replace(".rsml", "_corrected.rsml"));
                            paths.set(paths.indexOf(path), correctedPath);
                        }
                    }
                    //normalizeRSMLFile(path);
                }
            }

            // if 2 keys are the same but one of them has a '_corrected' value, then remove then mix the two lists
            for (String key : groupedFiles.keySet()) {
                if (groupedFiles.containsKey(key + "_corrected")) {
                    groupedFiles.get(key).addAll(groupedFiles.get(key + "_corrected"));
                }
            }

            // removing all keys with '_corrected' value
            groupedFiles.keySet().removeIf(key -> key.contains("_corrected"));

            // remove all invalid files from the groupedFiles
            for (Path path : removedRsmlFiles) {
                for (List<Path> paths : groupedFiles.values()) {
                    paths.remove(path);
                }
            }

            // if one of the keys of the groupedFiles has no associated value, then remove the key
            for (String key : groupedFiles.keySet()) {
                if (groupedFiles.get(key).isEmpty()) {
                    System.err.println("No valid RSML file for time: " + dates.get(key + ".rsml"));
                    groupedFiles.remove(key);
                    // DANGER : TODO generalize
                    removedDates.add(dates.get(key + ".rsml"));
                    dates.remove(key + ".rsml");
                }
            }

            // sort the groupedFiles by key
            try {
                groupedFiles.values().parallelStream().forEach(paths -> {
                    ImagePlus image = null;
                    double targetValue = 30.0;
                    if (strategy == SelectionStrategy.CLOSEST_PIXEL_VALUE || backupStrategy == SelectionStrategy.CLOSEST_PIXEL_VALUE
                            || strategy == SelectionStrategy.CLOSEST_PIXEL_VALUE_MOST_ORGANS || backupStrategy == SelectionStrategy.CLOSEST_PIXEL_VALUE_MOST_ORGANS) {
                        // path to image is path to rsml file with .png extension instead of .rsml
                        String imagePath = paths.get(0).toString().replace("_corrected", "");
                        // remove the numbers after the .rsml
                        if (imagePath.matches(".*\\d{2}")) {
                            imagePath = imagePath.substring(0, imagePath.length() - 2);
                        }
                        imagePath = imagePath.replace(".rsml", ".jpg");
                        // load the image with the lib imageio
                        image = new ImagePlus(imagePath);
                        // convert the image to 32 bit image
                        image.getProcessor().convertToFloat();
                        // get the target value
                        targetValue = 0.0; // TODO : generalize
                    }
                    Path selectedFile = selectFile(paths, strategy, image, targetValue);
                    synchronized (keptRsmlFiles) {
                        keptRsmlFiles.add(selectedFile.toString());
                    }
                    // clear memory
                    if (image != null) image.flush();
                    Objects.requireNonNull(image).close();
                });

            } catch (Exception e) {
                // empty keptRsmlFiles
                keptRsmlFiles.clear();
                for (List<Path> paths : groupedFiles.values()) {
                    ImagePlus image = null;
                    double targetValue = 0.0;
                    if (strategy == SelectionStrategy.CLOSEST_PIXEL_VALUE || backupStrategy == SelectionStrategy.CLOSEST_PIXEL_VALUE
                            || strategy == SelectionStrategy.CLOSEST_PIXEL_VALUE_MOST_ORGANS || backupStrategy == SelectionStrategy.CLOSEST_PIXEL_VALUE_MOST_ORGANS) {
                        // path to image is path to rsml file with .png extension instead of .rsml
                        String imagePath = paths.get(0).toString().replace(".rsml", ".jpg");
                        // load the image with the lib imageio
                        image = new ImagePlus(imagePath);
                        // convert the image to 32 bit image
                        image.getProcessor().convertToFloat();
                        // get the target value
                        targetValue = 0.0; // TODO : generalize
                    }
                    Path selectedFile = selectFile(paths, strategy, image, targetValue);
                    keptRsmlFiles.add(selectedFile.toString());
                }
            }

            System.out.println("Selected RSML files: " + keptRsmlFiles);
            return new TreeSet<>(keptRsmlFiles);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Selects a file from a list of paths based on the provided strategy.
     *
     * @param paths    the list of file paths
     * @param strategy the selection strategy to use
     * @return the selected file path
     */
    private static Path selectFile(List<Path> paths, SelectionStrategy strategy, ImagePlus image, double targetValue) {
        switch (strategy) {
            case LAST_VERSION:
                return paths.stream().max(Comparator.comparingInt(RSMLFileUtils::extractVersion)).orElseThrow(NoSuchElementException::new);
            case FIRST_VERSION:
                return paths.stream().min(Comparator.comparingInt(RSMLFileUtils::extractVersion)).orElseThrow(NoSuchElementException::new);
            case MOST_LINES:
                return resolveTie(paths, SelectionStrategy.MOST_LINES);
            case LEAST_ZEROS:
                return resolveTie(paths, SelectionStrategy.LEAST_ZEROS);
            case LEAST_ZEROS_MOST_LINES:
                return resolveTie(paths, SelectionStrategy.LEAST_ZEROS, SelectionStrategy.MOST_LINES);
            case MOST_LINES_LEAST_ZEROS:
                return resolveTie(paths, SelectionStrategy.MOST_LINES, SelectionStrategy.LEAST_ZEROS);
            case MOST_RECENT_TIMESTAMP:
                return resolveTie(paths, SelectionStrategy.MOST_RECENT_TIMESTAMP);
            case MOST_COMPLEXITY:
                return resolveTie(paths, SelectionStrategy.MOST_COMPLEXITY);
            case MOST_ORGANS:
                return resolveTie(paths, SelectionStrategy.MOST_ORGANS, SelectionStrategy.LEAST_DUPLICATES);
            case LEAST_DUPLICATES:
                return resolveTie(paths, SelectionStrategy.LEAST_DUPLICATES);
            case RANDOM:
                return paths.get(new Random().nextInt(paths.size()));
            case CLOSEST_PIXEL_VALUE: // Handle new strategy
                return resolveTie(paths, SelectionStrategy.CLOSEST_PIXEL_VALUE, image, targetValue);
            case CLOSEST_PIXEL_VALUE_MOST_ORGANS:
                return resolveTie(paths, SelectionStrategy.CLOSEST_PIXEL_VALUE, SelectionStrategy.MOST_ORGANS, image, targetValue);
            default:
                throw new IllegalArgumentException("Unknown selection strategy: " + strategy);
        }
    }

    /**
     * Resolves a tie by applying the primary strategy and, if necessary, the backup strategy.
     *
     * @param paths           the list of file paths
     * @param primaryStrategy the primary selection strategy to use
     * @return the selected file path
     */
    private static Path resolveTie(List<Path> paths, SelectionStrategy primaryStrategy) {
        List<Path> filteredPaths = applyCriterion(paths, primaryStrategy, null, 0.0);

        if (filteredPaths.size() > 1) {
            return selectFile(filteredPaths, backupStrategy, null, 0.0);
        }

        return filteredPaths.isEmpty() ? paths.get(0) : filteredPaths.get(0);
    }

    /**
     * Resolves a tie by applying the primary and secondary strategies.
     *
     * @param paths             the list of file paths
     * @param primaryStrategy   the primary selection strategy to use
     * @param secondaryStrategy the secondary selection strategy to use
     * @return the selected file path
     */
    private static Path resolveTie(List<Path> paths, SelectionStrategy primaryStrategy, SelectionStrategy secondaryStrategy) {
        List<Path> filteredPaths = applyCriterion(paths, primaryStrategy, null, 0.0);

        if (filteredPaths.size() > 1) {
            return resolveTie(filteredPaths, secondaryStrategy);
        }

        return filteredPaths.isEmpty() ? paths.get(0) : filteredPaths.get(0);
    }

    /**
     * Resolves a tie by applying the primary and backup strategies.
     * This method is used for the CLOSEST_PIXEL_VALUE strategy.
     *
     * @param paths           the list of file paths
     * @param primaryStrategy the primary selection strategy to use
     * @param image           the image to extract pixel values from
     * @param targetValue     the target pixel value
     * @return the selected file path
     */
    private static Path resolveTie(List<Path> paths, SelectionStrategy primaryStrategy, ImagePlus image, double targetValue) {
        List<Path> filteredPaths = applyCriterion(paths, primaryStrategy, image, targetValue);

        if (filteredPaths.size() > 1) {
            return selectFile(filteredPaths, backupStrategy, image, targetValue);
        }

        return filteredPaths.isEmpty() ? paths.get(0) : filteredPaths.get(0);
    }

    private static Path resolveTie(List<Path> paths, SelectionStrategy primaryStrategy, SelectionStrategy secondaryStrategy, ImagePlus image, double targetValue) {
        List<Path> filteredPaths = applyCriterion(paths, primaryStrategy, image, targetValue);

        if (filteredPaths.size() > 1) {
            return selectFile(filteredPaths, secondaryStrategy, image, targetValue);
        }

        return filteredPaths.isEmpty() ? paths.get(0) : filteredPaths.get(0);
    }

    /**
     * Applies the given selection strategy to the list of paths.
     *
     * @param paths    the list of file paths
     * @param strategy the selection strategy to use
     * @return the list of paths that match the criterion
     */
    private static List<Path> applyCriterion(List<Path> paths, SelectionStrategy strategy, ImagePlus image, double targetValue) {
        switch (strategy) {
            case MOST_LINES:
            case MOST_LINES_LEAST_ZEROS:
                return paths.stream()
                        .collect(Collectors.groupingBy(path -> getCriterionValue(path, strategy)))
                        .entrySet().stream()
                        .max(Comparator.comparingInt(Map.Entry::getKey))
                        .map(Map.Entry::getValue)
                        .orElseThrow(NoSuchElementException::new);
            case LEAST_ZEROS:
            case LEAST_ZEROS_MOST_LINES:
                return paths.stream()
                        .collect(Collectors.groupingBy(path -> getCriterionValue(path, strategy)))
                        .entrySet().stream()
                        .min(Comparator.comparingInt(Map.Entry::getKey))
                        .map(Map.Entry::getValue)
                        .orElseThrow(NoSuchElementException::new);
            case MOST_RECENT_TIMESTAMP:
                return paths.stream()
                        .collect(Collectors.groupingBy(RSMLFileUtils::getFileTimestamp))
                        .entrySet().stream()
                        .max(Comparator.comparingLong(Map.Entry::getKey))
                        .map(Map.Entry::getValue)
                        .orElseThrow(NoSuchElementException::new);
            case MOST_COMPLEXITY:
                return paths.stream()
                        .collect(Collectors.groupingBy(RSMLFileUtils::getFileComplexity))
                        .entrySet().stream()
                        .max(Comparator.comparingInt(Map.Entry::getKey))
                        .map(Map.Entry::getValue)
                        .orElseThrow(NoSuchElementException::new);
            case MOST_ORGANS:
                return paths.stream()
                        .collect(Collectors.groupingBy(RSMLFileUtils::getNumOrgans))
                        .entrySet().stream()
                        .max(Comparator.comparingInt(Map.Entry::getKey))
                        .map(Map.Entry::getValue)
                        .orElseThrow(NoSuchElementException::new);
            case LEAST_DUPLICATES:
                return paths.stream()
                        .collect(Collectors.groupingBy(RSMLFileUtils::countDuplicates))
                        .entrySet().stream()
                        .min(Comparator.comparingInt(Map.Entry::getKey))
                        .map(Map.Entry::getValue)
                        .orElseThrow(NoSuchElementException::new);
            case CLOSEST_PIXEL_VALUE: // Handle new strategy
                return paths.stream()
                        .collect(Collectors.groupingBy(path -> calculateMinimumDistanceToTargetPixelValue(path, image, targetValue)))
                        .entrySet().stream()
                        .min(Comparator.comparingDouble(Map.Entry::getKey))
                        .map(Map.Entry::getValue)
                        .orElseThrow(NoSuchElementException::new);
            default:
                throw new IllegalArgumentException("Unknown selection strategy: " + strategy);
        }
    }

    /**
     * Gets the criterion value for a given path based on the strategy.
     *
     * @param path     the file path
     * @param strategy the selection strategy
     * @return the criterion value
     */
    private static int getCriterionValue(Path path, SelectionStrategy strategy) {
        switch (strategy) {
            case MOST_LINES:
            case MOST_LINES_LEAST_ZEROS:
                return countLines(path);
            case LEAST_ZEROS:
            case LEAST_ZEROS_MOST_LINES:
                return countZeros(path);
            case MOST_RECENT_TIMESTAMP:
                return (int) getFileTimestamp(path);
            case MOST_COMPLEXITY:
                return getFileComplexity(path);
            case LEAST_DUPLICATES:
                return countDuplicates(path);
            default:
                throw new IllegalArgumentException("Unknown selection strategy: " + strategy);
        }
    }

    /**
     * Calculate the minimum distance between the pixel values of an image and a target value.
     * The distance is calculated as the absolute difference between the pixel value and the target value.
     * The minimum distance is the smallest distance found in the RSML file.
     *
     * @param rsmlPath    the path to the RSML file
     * @param image       the image to extract pixel values from
     * @param targetValue the target pixel value
     * @return the minimum distance to the target pixel value
     */
    private static double calculateMinimumDistanceToTargetPixelValue(Path rsmlPath, ImagePlus image, double targetValue) {
        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(rsmlPath.toFile());

            NodeList pointNodes = doc.getElementsByTagName("point");
            double minDistance = Double.MAX_VALUE;

            for (int i = 0; i < pointNodes.getLength(); i++) {
                Element pointElement = (Element) pointNodes.item(i);
                int x = (int) Double.parseDouble(pointElement.getAttribute("x"));
                int y = (int) Double.parseDouble(pointElement.getAttribute("y"));
                // get pixel value for an 8 or 32 bit image
                int pixelValue = image.getProcessor().getPixel(x, y);

                double distance = Math.abs(pixelValue - targetValue);
                if (distance < minDistance) {
                    minDistance = distance;
                }
            }

            return minDistance;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Gets the last modified timestamp of a file.
     *
     * @param path the file path
     * @return the last modified timestamp in milliseconds
     */
    private static long getFileTimestamp(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Gets the number of organs in a file.
     *
     * @param path the file path
     * @return the number of organs (here root elements with a label attribute)
     */
    private static int getNumOrgans(Path path) {
        try {
            return Files.lines(path)
                    .map(line -> line.split("<root").length - 1)
                    .mapToInt(Integer::intValue)
                    .sum();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Calculates the complexity of a file.
     *
     * @param path the file path
     * @return the complexity value, i.e., the number of opening tags
     */
    private static int getFileComplexity(Path path) {
        try {
            return Files.lines(path)
                    .map(line -> line.split("<").length - 1)
                    .mapToInt(Integer::intValue)
                    .sum();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Counts the number of duplicate lines in a file.
     *
     * @param path the file path
     * @return the number of duplicate lines
     */
    private static int countDuplicates(Path path) {
        try {
            List<String> lines = Files.lines(path).collect(Collectors.toList());
            return (int) lines.stream()
                    .filter(line -> Collections.frequency(lines, line) > 1)
                    .count();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Extracts the version number from a file path.
     *
     * @param path the file path
     * @return the version number
     */
    private static int extractVersion(Path path) {
        String filename = path.toString();
        if (filename.matches(".*\\.rsml$")) filename += "00";
        if (filename.matches(".*\\.rsml\\d{2}$")) {
            return Integer.parseInt(filename.substring(filename.length() - 2));
        }
        return 0;
    }

    /**
     * Counts the number of lines in a file.
     *
     * @param path the file path
     * @return the number of lines
     */
    private static int countLines(Path path) {
        try {
            return (int) Files.lines(path).count();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Counts the number of lines containing a zero value in a file.
     *
     * @param path the file path
     * @return the number of lines containing a zero value
     */
    private static int countZeros(Path path) {
        try {
            return (int) Files.lines(path)
                    .filter(line -> line.trim().matches("<.*>0.0</.*>"))
                    .count();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    ////////////////////////// UTILS //////////////////////////

    /**
     * Validate the RSML file by checking for the presence of required elements.
     *
     * @param filePath The path to the RSML file to validate
     * @return true if the file is valid, false otherwise
     */
    public static boolean validateRSMLFile(Path filePath) {
        try {
            // Basic validation checks for the presence of required elements

            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(filePath.toFile());

            // Example validation checks
            if (doc.getElementsByTagName("metadata").getLength() == 0) {
                System.err.println("Missing metadata in file: " + filePath);
                return false;
            }

            if (doc.getElementsByTagName("scene").getLength() == 0) {
                System.err.println("Missing scene in file: " + filePath);
                return false;
            }

            if (doc.getElementsByTagName("plant").getLength() == 0) {
                System.err.println("Missing plant in file: " + filePath);
                return false;
            }

            if (doc.getElementsByTagName("root").getLength() == 0) {
                System.err.println("Missing root in file: " + filePath);
                return false;
            }

            if (doc.getElementsByTagName("point").getLength() == 0) {
                System.err.println("Missing points in file: " + filePath);
                return false;
            }

            // Checking the structure of the file attributes
            NodeList nodeList = doc.getElementsByTagName("root");
            for (int i = 0; i < nodeList.getLength(); i++) {
                org.w3c.dom.Node node = nodeList.item(i);
                if (node.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                    Element element = (Element) node;
                    // if the root has as ownernode name "plant" but has the attribute "label" containing "lat", then return false
                    if (element.getAttribute("label").contains("lat") && element.getParentNode().getNodeName().equals("plant")) {
                        System.err.println("Invalid lateral root in file: " + filePath);
                        return false;
                    }

                    // if the root has name "root" and has "label" containing "root" but has as ownernode another root, then return false
                    if (element.getAttribute("label").contains("root") && element.getParentNode().getNodeName().equals("root")) {
                        System.err.println("Invalid primary root in file: " + filePath); // TODO : never tested
                        return false;
                    }
                }
            }
            return true;
        } catch (Exception e) {
            System.err.println("Error validating file: " + filePath);
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Corrects the RSML file by moving lateral roots to the primary root if only one primary root exists.
     *
     * @param filePath The path to the RSML file to correct
     * @return true if the file was corrected and saved successfully, false otherwise
     */
    public static boolean correctInvalidRSMLFile(Path filePath) {
        try {
            // Parse the file
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(filePath.toFile());

            // primary's contains 'root' in their label while lateral's contains 'lat'
            NodeList rootNodes = document.getElementsByTagName("root");
            List<Element> primaryRoots = new ArrayList<>();
            List<Element> lateralRoots = new ArrayList<>();
            for (int i = 0; i < rootNodes.getLength(); i++) {
                Element rootElement = (Element) rootNodes.item(i);
                if (rootElement.getAttribute("label").contains("root")) {
                    primaryRoots.add(rootElement);
                } else if (rootElement.getAttribute("label").contains("lat")) {
                    lateralRoots.add(rootElement);
                }
            }

            // if there is only one primary root, then move the lateral roots to the primary root
            if (primaryRoots.size() == 1) {
                Element primaryRoot = primaryRoots.get(0);
                for (Element lateralRoot : lateralRoots) {
                    org.w3c.dom.Node parentNode = lateralRoot.getParentNode();
                    primaryRoot.appendChild(lateralRoot);
                    //parentNode.removeChild(lateralRoot);
                }

                // save the corrected as new file with a "corrected" suffix
                TransformerFactory transformerFactory = TransformerFactory.newInstance();
                Transformer transformer = transformerFactory.newTransformer();
                transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
                DOMSource source = new DOMSource(document);
                Path correctedFilePath = filePath.resolveSibling(filePath.getFileName().toString().replace(".rsml", "_corrected.rsml"));
                StreamResult result = new StreamResult(Files.newOutputStream(correctedFilePath));
                transformer.transform(source, result);
                System.out.println("File corrected and saved to: " + correctedFilePath);
            } else {
                return false;
            }
        } catch (Exception e) {
            System.err.println("Error correcting file: " + filePath);
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     * DANGER
     * Normalize the RSML file by reformatting it and removing unnecessary whitespace.
     *
     * @param filePath The path to the RSML file to normalize
     */
    public static void normalizeRSMLFile(Path filePath) {
        try {
            // Parse the file
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(filePath.toFile());

            // Normalize the XML structure
            document.getDocumentElement().normalize();

            // Create a transformer to format the XML
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

            // Set up the source and result for the transformation
            DOMSource source = new DOMSource(document);
            StreamResult result = new StreamResult(Files.newOutputStream(filePath.toFile().toPath()));

            // Transform and write the normalized XML to the file
            transformer.transform(source, result);

        } catch (Exception e) {
            throw new RuntimeException("Error normalizing RSML file: " + filePath, e);
        }
    }

}
