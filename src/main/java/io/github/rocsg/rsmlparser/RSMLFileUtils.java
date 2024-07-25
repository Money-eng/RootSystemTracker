package io.github.rocsg.rsmlparser;

import org.w3c.dom.Document;

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
import java.util.*;
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
    LEAST_DUPLICATES,
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
    public static Stack<String> checkUniquenessRSMLs(Path folderPath, SelectionStrategy strategy) {
        try {
            Map<String, List<Path>> groupedFiles = Files.list(folderPath)
                    .filter(path -> path.toString().matches(".*\\.(rsml|rsml\\d{2})$"))
                    .collect(Collectors.groupingBy(path -> path.toString().split("\\.")[0]));

            Stack<String> keptRsmlFiles = new Stack<>();

            // validate and normalize the RSML files
            for (List<Path> paths : groupedFiles.values()) {
                for (Path path : paths) {
                    if (!validateRSMLFile(path)) {
                        System.err.println("Invalid RSML file: " + path);
                        continue;
                    }
                    normalizeRSMLFile(path);
                }
            }

            for (List<Path> paths : groupedFiles.values()) {
                Path selectedFile = selectFile(paths, strategy);
                keptRsmlFiles.add(selectedFile.toString());
            }

            return keptRsmlFiles.stream().sorted().collect(Collectors.toCollection(Stack::new));
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
    private static Path selectFile(List<Path> paths, SelectionStrategy strategy) {
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
            case LEAST_DUPLICATES:
                return resolveTie(paths, SelectionStrategy.LEAST_DUPLICATES);
            case RANDOM:
                return paths.get(new Random().nextInt(paths.size()));
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
        List<Path> filteredPaths = applyCriterion(paths, primaryStrategy);

        if (filteredPaths.size() > 1) {
            return selectFile(filteredPaths, backupStrategy);
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
        List<Path> filteredPaths = applyCriterion(paths, primaryStrategy);

        if (filteredPaths.size() > 1) {
            return resolveTie(filteredPaths, secondaryStrategy);
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
    private static List<Path> applyCriterion(List<Path> paths, SelectionStrategy strategy) {
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
            case LEAST_DUPLICATES:
                return paths.stream()
                        .collect(Collectors.groupingBy(RSMLFileUtils::countDuplicates))
                        .entrySet().stream()
                        .min(Comparator.comparingInt(Map.Entry::getKey))
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

            // Additional validation logic here

            return true;
        } catch (Exception e) {
            System.err.println("Error validating file: " + filePath);
            e.printStackTrace();
            return false;
        }
    }

    /**
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
