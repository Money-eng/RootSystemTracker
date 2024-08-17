package io.github.rocsg.rsmlparser.RSML2D;

import ij.ImageJ;
import io.github.rocsg.rsmlparser.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


/**
 * RsmlParser2 class for parsing RSML files and extracting root models.
 * This class provides methods to parse RSML files, extract metadata, and build hierarchical root models.
 */
public class Rsml2DParser {

    public static boolean anonymize = false;
    public static final double probaOfAno = 1; // Probability of anonymization

    // Formatter pour LocalDateTime (dates avec heures)
    private static final DateTimeFormatter DATE_TIME_FORMATTER = new DateTimeFormatterBuilder()
            .appendOptional(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))
            .appendOptional(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"))
            .appendOptional(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"))
            .appendOptional(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            .appendOptional(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"))
            .appendOptional(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss"))
            .appendOptional(DateTimeFormatter.ofPattern("yyyyMMdd HH:mm:ss"))
            .appendOptional(DateTimeFormatter.ofPattern("dd_MM_yyyy_HH_mm_ss"))
            .appendOptional(DateTimeFormatter.ofPattern("dd_MM_yyyy_HH_mm"))
            .appendOptional(DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm_ss"))
            .appendOptional(DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm"))
            .appendOptional(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            .toFormatter(Locale.getDefault());
    // Formatter pour LocalDate (dates sans heures)
    private static final DateTimeFormatter DATE_FORMATTER = new DateTimeFormatterBuilder()
            .appendOptional(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
            .appendOptional(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
            .appendOptional(DateTimeFormatter.ofPattern("yyyy/MM/dd"))
            .appendOptional(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            .appendOptional(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
            .appendOptional(DateTimeFormatter.ofPattern("yyyy.MM.dd"))
            .appendOptional(DateTimeFormatter.ofPattern("yyyyMMdd"))
            .appendOptional(DateTimeFormatter.ofPattern("dd_MM_yyyy"))
            .appendOptional(DateTimeFormatter.ofPattern("yyyy_MM_dd"))
            .appendOptional(DateTimeFormatter.ISO_LOCAL_DATE)
            .toFormatter(Locale.getDefault());
    public static SelectionStrategy strategy;
    public static List<LocalDateTime> removedDates = new ArrayList<>();
    public String path2RSMLs;

    /**
     * Constructor for RsmlParser2.
     * Initializes the path to RSML files and the list of dates to be removed.
     *
     * @param path2RSMLs   Path to the directory containing RSML files.
     * @param removedDates List of dates to be removed.
     */
    public Rsml2DParser(String path2RSMLs, List<LocalDateTime> removedDates) {
        this.path2RSMLs = path2RSMLs;
        Rsml2DParser.removedDates = new ArrayList<>(removedDates);

        // Set the backup strategy and selection strategy for RSML files.
        strategy = SelectionStrategy.FIRST_VERSION;
    }

    public static void main(String[] args) throws IOException {
        // Load the SimpleITK Java library for image processing.
        System.loadLibrary("SimpleITKJava");

        // Initialize ImageJ for image visualization.
        ImageJ ij = new ImageJ();
        RootModelGraph rootModelGraph = new RootModelGraph();
    }

    /**
     * Parse the root elements from the RSML files.
     * This function recursively parses root elements and their children, extracting relevant properties and geometry.
     *
     * @param rootList     The NodeList containing root elements to parse.
     * @param parentPlant  The Plant object to which the roots are added.
     * @param parentRoot   The parent root element.
     * @param order        The order of the root (1 for primary roots, 2 for secondary, etc.).
     * @param dateOfFile   The date of the RSML file.
     * @param addedRootIDs Set of already added root IDs to avoid duplicates.
     */
    private static void parseRoots(NodeList rootList, Plant parentPlant, Root4Parser parentRoot, int order, String dateOfFile, Set<String> addedRootIDs, boolean anonymize) {
        for (int i = 0; i < rootList.getLength(); i++) {
            Node rootNode = rootList.item(i);

            if (rootNode.getNodeType() == Node.ELEMENT_NODE) {
                Element rootElement = (Element) rootNode;
                String rootID = rootElement.getAttribute("ID");
                // Skip if root with this ID is already added
                if (addedRootIDs.contains(rootID)) {
                    continue;
                }

                // Create a new Root4Parser object for this root element.
                Root4Parser root = createRoot4Parser(rootElement, parentRoot, order, dateOfFile, anonymize);

                // Parse properties, geometry, and functions of the root.
                parseProperties(rootElement, root);
                parseGeometry(rootElement, root);
                parseFunctions(rootElement, root);
                parseAnnotations(rootElement, root);

                // Add root to the parent plant if it is a primary root.
                boolean alreadyAdded = (addedRootIDs.contains(root.getId()));
                if (alreadyAdded) {
                    Root4Parser prevAddedRoot = (Root4Parser) parentPlant.getRootByID(root.getId());
                    if (root.getOrder() < prevAddedRoot.getOrder()) {
                        prevAddedRoot = root;
                    }
                } else if (root.getOrder() == 1) parentPlant.addRoot(root);
                parentPlant.add2FlatSet(root);

                // Mark this root ID as added.
                addedRootIDs.add(rootID);

                // Recursively parse child roots.
                NodeList childRoots = rootElement.getElementsByTagName("root");
                parseRoots(childRoots, parentPlant, root, order + 1, dateOfFile, addedRootIDs, anonymize);
            }
        }

        // Trim functions to ensure they do not exceed the predefined number.
        List<IRootParser> roots = parentPlant.getRoots();
        List<Root4Parser> elements = new ArrayList<>();
        for (IRootParser root : roots) {
            elements.add((Root4Parser) root);
        }
        trimFunctions(elements);
    }

    /**
     * Create a new Root4Parser object.
     * Extracts basic attributes from the root element and initializes a Root4Parser object.
     *
     * @param rootElement The root element.
     * @param parentRoot  The parent root.
     * @param order       The order of the root.
     * @param dateOfFile  The date of the RSML file.
     * @return A new Root4Parser object.
     */
    private static Root4Parser createRoot4Parser(Element rootElement, Root4Parser parentRoot, int order, String dateOfFile, boolean anonymize) {
        String rootID = rootElement.getAttribute("ID");
        if (anonymize && Math.random() < probaOfAno) {
            rootID = new Random().ints(35, 33, 126).mapToObj(s -> String.valueOf((char) s)).collect(Collectors.joining());
        }
        String rootLabel = rootElement.getAttribute("label");
        String poAccession = rootElement.getAttribute("po:accession");
        return new Root4Parser(rootID, rootLabel, poAccession, parentRoot, order, getDate(dateOfFile));
    }

    /**
     * Parse properties of a root element.
     * Extracts properties from the root element and adds them to the Root4Parser object.
     *
     * @param rootElement The root element.
     * @param root        The Root4Parser object.
     */
    private static void parseProperties(Element rootElement, Root4Parser root) {
        NodeList propertiesList = rootElement.getElementsByTagName("properties");
        if (propertiesList.getLength() > 0) {
            Element propertiesElement = (Element) propertiesList.item(0);
            NodeList properties = propertiesElement.getChildNodes();
            for (int j = 0; j < properties.getLength(); j++) {
                Node property = properties.item(j);
                if (property.getNodeType() == Node.ELEMENT_NODE) {
                    Element propertyElement = (Element) property;
                    root.addProperty(new Property(propertyElement.getNodeName(), propertyElement.getTextContent()));
                }
            }
        }
    }

    /**
     * Parse geometry of a root element.
     * Extracts geometric information such as polylines and points from the root element and adds them to the Root4Parser object.
     *
     * @param rootElement The root element.
     * @param root        The Root4Parser object.
     */
    private static void parseGeometry(Element rootElement, Root4Parser root) {
        NodeList geometryList = rootElement.getElementsByTagName("geometry");
        if (geometryList.getLength() > 0) {
            Element geometryElement = (Element) geometryList.item(0);
            Geometry geometry = new Geometry();
            NodeList polylineList = geometryElement.getElementsByTagName("polyline");
            for (int k = 0; k < polylineList.getLength(); k++) {
                Element polylineElement = (Element) polylineList.item(k);
                Polyline polyline = new Polyline();
                NodeList pointList = polylineElement.getElementsByTagName("point");
                for (int l = 0; l < pointList.getLength(); l++) {
                    Element pointElement = (Element) pointList.item(l);
                    polyline.addPoint(new Point4Parser(pointElement.getAttribute("x"), pointElement.getAttribute("y")));
                }
                geometry.addPolyline(polyline);
            }
            root.setGeometry(geometry);
        }
    }

    /**
     * Parse functions of a root element.
     * Extracts functional information from the root element and adds them to the Root4Parser object.
     *
     * @param rootElement The root element.
     * @param root        The Root4Parser object.
     */
    private static void parseFunctions(Element rootElement, Root4Parser root) {
        NodeList functionList = rootElement.getElementsByTagName("function");
        Root4Parser.numFunctions = Math.min(Root4Parser.numFunctions, functionList.getLength());
        for (int m = 0; m < functionList.getLength(); m++) {
            Element functionElement = (Element) functionList.item(m);
            Function function = getFunction(functionElement);
            root.addFunction(function);
        }
    }

    /**
     * Parse annotations of a root element.
     * Extracts annotations from the root element and adds them to the Root4Parser object.
     *
     * @param rootElement The root element.
     * @param root        The Root4Parser object.
     */
    private static void parseAnnotations(Element rootElement, Root4Parser root) {
        NodeList annotationsList = rootElement.getElementsByTagName("annotations");
        if (annotationsList.getLength() > 0) {
            Element annotationsElement = (Element) annotationsList.item(0);
            NodeList annotationNodes = annotationsElement.getElementsByTagName("annotation");
            for (int j = 0; j < annotationNodes.getLength(); j++) {
                Node annotationNode = annotationNodes.item(j);
                if (annotationNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element annotationElement = (Element) annotationNode;
                    String annotationName = annotationElement.getAttribute("name");

                    Annotation annotation = new Annotation(annotationName);

                    // Iterate over child nodes of the annotation element
                    NodeList childNodes = annotationElement.getChildNodes();
                    for (int k = 0; k < childNodes.getLength(); k++) {
                        Node childNode = childNodes.item(k);
                        if (childNode.getNodeType() == Node.ELEMENT_NODE) {
                            Element childElement = (Element) childNode;
                            String nodeName = childElement.getNodeName();
                            String nodeValue = childElement.getTextContent();

                            // For point element, add attributes x and y
                            if ("point".equals(nodeName)) {
                                String x = childElement.getAttribute("x");
                                String y = childElement.getAttribute("y");
                                annotation.addAttribute("point_x", x);
                                annotation.addAttribute("point_y", y);
                            } else {
                                annotation.addAttribute(nodeName, nodeValue);
                            }
                        }
                    }

                    root.addAnnotation(annotation);
                }
            }
        }
    }

    /**
     * Create a Function object from a function element.
     *
     * @param functionNode The function element.
     * @return A new Function object.
     */
    private static Function getFunction(Element functionNode) {
        String functionName = functionNode.getAttribute("name");
        Function function = new Function(functionName);
        NodeList sampleList = functionNode.getElementsByTagName("sample");
        for (int n = 0; n < sampleList.getLength(); n++) {
            Element sampleElement = (Element) sampleList.item(n);
            function.addSample(sampleElement.getTextContent());
        }
        return function;
    }

    /**
     * Retrieve RSML file information and parse root models.
     * This function iterates over RSML files in the specified folder, extracts metadata, and builds root models.
     *
     * @param folderPath Path to the folder containing RSML files.
     * @return A map of LocalDate to lists of IRootModelParser objects.
     */
    public static Map<LocalDateTime, List<IRootModelParser>> getRSMLsInfos(Path folderPath) {
        Map<String, LocalDateTime> fileDates = getFileDates(folderPath);
        TreeSet<String> keptRsmlFiles = RSMLFileUtils.checkUniquenessRSMLs(folderPath, (ConcurrentHashMap<String, LocalDateTime>) fileDates, strategy, removedDates);
        Map<LocalDateTime, List<IRootModelParser>> rsmlInfos = initializeRsmlInfos(fileDates);

        for (String rsmlFile : keptRsmlFiles) {
            try {
                Document doc = parseXmlFile(rsmlFile);
                Metadata metadata = extractMetadata(doc);
                RootModel4Parser rootModel = new RootModel4Parser(metadata);

                NodeList sceneList = doc.getElementsByTagName("scene");
                for (int i = 0; i < sceneList.getLength(); i++) {
                    Element sceneElement = (Element) sceneList.item(i);
                    Scene scene = new Scene();
                    NodeList plantList = sceneElement.getElementsByTagName("plant");
                    for (int j = 0; j < plantList.getLength(); j++) {
                        Element plantElement = (Element) plantList.item(j);
                        Plant plant = new Plant();
                        NodeList rootList = plantElement.getElementsByTagName("root");
                        Set<String> addedRootIDs = new HashSet<>();
                        // if last date, anonymize = false
                        if (keptRsmlFiles.last().equals(rsmlFile)) Rsml2DParser.anonymize = false;
                        parseRoots(rootList, plant, null, 1, metadata.getDate2Use(), addedRootIDs, Rsml2DParser.anonymize);
                        scene.addPlant(plant);
                    }
                    rootModel.addScene(scene);
                }
                rsmlInfos.get(fileDates.get(rsmlFile)).add(rootModel);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        System.out.println("rsmlInfos : " + rsmlInfos);
        return rsmlInfos;
    }

    /**
     * Get the dates of RSML files in the specified folder.
     * Extracts dates from the filenames using a predefined pattern.
     *
     * @param folderPath Path to the folder containing RSML files.
     * @return A map of filenames to LocalDate objects.
     */
    private static Map<String, LocalDateTime> getFileDates(Path folderPath) {
        Map<String, LocalDateTime> fileDates = new ConcurrentHashMap<>();
        try {
            Files.list(folderPath)
                    .parallel()
                    .filter(path -> path.toString().matches(".*\\.(rsml|rsml01|rsml02|rsml03|rsml04)$"))
                    .forEach(path -> fileDates.put(path.toString(), getDate(path.toString().split("\\.")[0])));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return fileDates;
    }

    /**
     * Initialize RSML information map with dates as keys.
     *
     * @param fileDates Map of filenames to LocalDate objects.
     * @return A map of LocalDate to lists of IRootModelParser objects.
     */
    private static Map<LocalDateTime, List<IRootModelParser>> initializeRsmlInfos(Map<String, LocalDateTime> fileDates) {
        Map<LocalDateTime, List<IRootModelParser>> rsmlInfos = new TreeMap<>();
        fileDates.values().forEach(date -> rsmlInfos.put(date, new ArrayList<>()));
        return rsmlInfos;
    }

    /**
     * Parse an XML file into a Document object.
     *
     * @param filePath Path to the XML file.
     * @return A Document object representing the parsed XML file.
     * @throws ParserConfigurationException If a DocumentBuilder cannot be created.
     * @throws IOException                  If an I/O error occurs.
     * @throws SAXException                 If a parsing error occurs.
     */
    private static Document parseXmlFile(String filePath) throws ParserConfigurationException, IOException, SAXException {
        File inputFile = new File(filePath);
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(inputFile);
        doc.getDocumentElement().normalize();
        return doc;
    }

    /**
     * Extract metadata from a Document object.
     *
     * @param doc The Document object representing the parsed XML file.
     * @return A Metadata object containing extracted metadata.
     */
    private static Metadata extractMetadata(Document doc) {
        NodeList metadataList = doc.getElementsByTagName("metadata");
        Metadata metadata = new Metadata();
        for (int i = 0; i < metadataList.getLength(); i++) {
            Element metadataElement = (Element) metadataList.item(i);
            metadata.setVersion(Float.parseFloat(metadataElement.getElementsByTagName("version").item(0).getTextContent()));
            metadata.setUnit(metadataElement.getElementsByTagName("unit").item(0).getTextContent());
            metadata.setResolution(Float.parseFloat(metadataElement.getElementsByTagName("resolution").item(0).getTextContent()));
            metadata.setModifDate(parseModifDate(metadataElement));
            metadata.setSoftware(metadataElement.getElementsByTagName("software").item(0).getTextContent());
            metadata.setUser(metadataElement.getElementsByTagName("user").item(0).getTextContent());
            metadata.setFileKey(metadataElement.getElementsByTagName("file-key").item(0).getTextContent());
            metadata.setDate2Use(getDateFromImageLabel(metadataElement));
            parsePropertyDefinitions(metadataElement, metadata);
        }
        return metadata;
    }

    /**
     * Parse the modification date from a metadata element.
     *
     * @param metadataElement The metadata element.
     * @return The parsed LocalDate object.
     */
    private static LocalDateTime parseModifDate(Element metadataElement) {
        String dateStr = metadataElement.getElementsByTagName("last-modified").item(0).getTextContent();
        return "today".equals(dateStr) ? LocalDateTime.now() : getDate(dateStr);
    }

    /**
     * Get the date from an image label element.
     *
     * @param metadataElement The metadata element.
     * @return The extracted date as a String.
     */
    private static String getDateFromImageLabel(Element metadataElement) {
        NodeList imageList = metadataElement.getElementsByTagName("image");
        for (int j = 0; j < imageList.getLength(); j++) {
            Element imageElement = (Element) imageList.item(j);
            return imageElement.getElementsByTagName("label").item(0).getTextContent();
        }
        return "";
    }

    /**
     * Parse property definitions from a metadata element.
     *
     * @param metadataElement The metadata element.
     * @param metadata        The Metadata object to which the property definitions are added.
     */
    private static void parsePropertyDefinitions(Element metadataElement, Metadata metadata) {
        NodeList propertyDefList = metadataElement.getElementsByTagName("property-definition");
        for (int j = 0; j < propertyDefList.getLength(); j++) {
            Element propertyDefElement = (Element) propertyDefList.item(j);
            Metadata.PropertyDefinition propertyDefinition = metadata.new PropertyDefinition();
            propertyDefinition.label = propertyDefElement.getElementsByTagName("label").item(0).getTextContent();
            propertyDefinition.type = propertyDefElement.getElementsByTagName("type").item(0).getTextContent();
            propertyDefinition.unit = propertyDefElement.getElementsByTagName("unit").item(0).getTextContent();
            metadata.propertiedef.add(propertyDefinition);
        }
    }

    /**
     * Extract date from a string using a predefined pattern.
     *
     * @param inputStr The string containing the date.
     * @return The extracted LocalDateTime object, or null if the pattern does not match.
     */
    public static LocalDateTime getDate(String inputStr) {
        // Pattern regex pour extraire des segments de date potentiels
        Pattern datePattern = Pattern.compile("\\d{2}[_\\-/\\.]\\d{2}[_\\-/\\.]\\d{4}(?:[_\\-/\\.]\\d{2})?(?:[_\\-/\\.]\\d{2})?(?:[_\\-/\\.]\\d{2})?");
        Matcher matcher = datePattern.matcher(inputStr);

        while (matcher.find()) {
            String dateSegment = matcher.group(0); // Extrait la date potentielle
            // Remplacer tous les séparateurs par "_"
            dateSegment = dateSegment.replace("-", "_").replace("/", "_").replace(".", "_");

            try {
                // Tente de parser comme LocalDateTime (date avec heure)
                return LocalDateTime.parse(dateSegment, DATE_TIME_FORMATTER);
            } catch (DateTimeParseException e1) {
                try {
                    // Si échec, tente de parser comme LocalDate (date sans heure)
                    return LocalDate.parse(dateSegment, DATE_FORMATTER).atStartOfDay();
                } catch (DateTimeParseException e2) {
                    // Si échec, continuer à chercher dans la chaîne
                }
            }
        }

        System.out.println("No valid date found in: " + inputStr);
        return null;
    }

    /**
     * Trim functions of roots to ensure they do not exceed the predefined number.
     *
     * @param roots List of Root4Parser objects.
     */
    private static void trimFunctions(List<Root4Parser> roots) {
        for (Root4Parser root : roots) {
            if (root.functions.size() > Root4Parser.numFunctions) {
                root.functions.subList(2, root.functions.size()).clear();
            }
        }
    }

    /**
     * Between multiple RSML files with different dates, we can have new roots, but we always keep the previously existing roots.
     * We take the roots of the previous RSML and try to find them in the new RSML (by ID). Two by two, we compare the roots of the two RSML files.
     * If some previously defined roots are not found in the new RSML or the following, we return true.
     * If all the roots are found in the new RSML or the following, we return false.
     *
     * @param stackOfRoots Map of dates to lists of RootModelParser objects extracted from RSML files.
     * @return True if we need to compare roots, false otherwise.
     */
    public static boolean checkIfAnonymized(Map<LocalDateTime, List<IRootModelParser>> stackOfRoots) {
        // Obtain the dates in ascending order
        List<LocalDateTime> dates = new ArrayList<>(stackOfRoots.keySet());
        Collections.sort(dates);

        // Iterate over pairs of consecutive dates
        for (int i = 0; i < dates.size() - 1; i++) {
            LocalDateTime currentDate = dates.get(i);
            LocalDateTime nextDate = dates.get(i + 1);

            List<IRootModelParser> currentRoots = stackOfRoots.get(currentDate);
            List<IRootModelParser> nextRoots = stackOfRoots.get(nextDate);

            // Collect all root IDs from current roots
            Set<String> currentRootIDs = new HashSet<>();
            for (IRootModelParser currentRootModel : currentRoots) {
                for (IRootParser root : currentRootModel.getRoots()) {
                    currentRootIDs.add(root.getId());
                }
            }

            // Collect all root IDs from next roots
            Set<String> nextRootIDs = new HashSet<>();
            for (IRootModelParser nextRootModel : nextRoots) {
                for (IRootParser root : nextRootModel.getRoots()) {
                    nextRootIDs.add(root.getId());
                }
            }

            // Check if any root ID from the current date is missing in the next date
            for (String rootID : currentRootIDs) {
                if (!nextRootIDs.contains(rootID)) {
                    // If a root ID is missing, return true (indicating anonymization might have occurred)

                    // try the following date
                    if (i + 2 < dates.size()) {
                        LocalDateTime nextNextDate = dates.get(i + 2);
                        List<IRootModelParser> nextNextRoots = stackOfRoots.get(nextNextDate);
                        Set<String> nextNextRootIDs = new HashSet<>();
                        for (IRootModelParser nextNextRootModel : nextNextRoots) {
                            for (IRootParser root : nextNextRootModel.getRoots()) {
                                nextNextRootIDs.add(root.getId());
                            }
                        }
                        if (!nextNextRootIDs.contains(rootID)) {
                            return true;
                        }
                    }
                }
            }
        }
        // If no root ID is missing in any comparison, return false
        return false;
    }

    /**
     * Between multiple RSML files with different dates, we can have new roots, but we always keep the previously existing roots.
     * This function tracks the presence or absence of each root across different dates. For each root ID, we maintain a list
     * of booleans indicating whether the root is present (true) or absent (false) at each date.
     *
     * @param stackOfRoots Map of dates to RootModelParser objects extracted from RSML files.
     * @return A map linking each root ID to a list of booleans representing its presence or absence over time.
     */
    public static Map<String, List<Boolean>> getRootPresenceMap(Map<LocalDateTime, IRootModelParser> stackOfRoots) {
        Map<String, List<Boolean>> rootPresenceMap = new HashMap<>();
        List<LocalDateTime> dates = new ArrayList<>(stackOfRoots.keySet());
        Collections.sort(dates); // Sort dates in chronological order

        // Initialize the rootPresenceMap with all possible root IDs across all dates
        for (LocalDateTime date : dates) {
            IRootModelParser rootModel = stackOfRoots.get(date);
            for (IRootParser root : rootModel.getRoots()) {
                rootPresenceMap.putIfAbsent(root.getId(), new ArrayList<>(Collections.nCopies(dates.size(), false)));
            }
        }

        // Fill the presence map with true/false values
        for (int i = 0; i < dates.size(); i++) {
            LocalDateTime date = dates.get(i);
            IRootModelParser rootModel = stackOfRoots.get(date);

            // Mark the presence of each root at this date
            for (IRootParser root : rootModel.getRoots()) {
                rootPresenceMap.get(root.getId()).set(i, true);
            }
        }

        return rootPresenceMap;
    }

    /**
     * Vérifie si une racine est mal classifiée, bien classifiée ou inconnue en fonction de la séquence de présences.
     * Une racine est mal classifiée si elle est présente (true) puis absente (false) dans les itérations suivantes.
     * Une racine est bien classifiée si elle est d'abord absente (false) puis présente (true) sans interruption.
     * Une racine est classée comme "unknown" si elle n'apparaît qu'au dernier temps.
     *
     * @param rootPresenceMap Map associant chaque ID de racine à une liste de booléens représentant sa présence ou absence.
     * @return Une map contenant trois listes de racines : "misclassified", "well_classified", "last_time".
     */
    public static Map<String, List<String>> classifyRoots(Map<String, List<Boolean>> rootPresenceMap) {
        Map<String, List<String>> classificationResult = new HashMap<>();
        classificationResult.put("misclassified", new ArrayList<>());
        classificationResult.put("well_classified", new ArrayList<>());
        classificationResult.put("last_time", new ArrayList<>());

        for (Map.Entry<String, List<Boolean>> entry : rootPresenceMap.entrySet()) {
            String rootId = entry.getKey();
            List<Boolean> presenceSequence = entry.getValue();

            boolean foundTrue = false;
            boolean isMisclassified = false;
            boolean isUnknown = false;

            for (int i = 0; i < presenceSequence.size(); i++) {
                if (presenceSequence.get(i)) {
                    foundTrue = true;

                    // Vérifier si le true n'apparaît qu'au dernier temps
                    if (i == presenceSequence.size() - 1 && !presenceSequence.subList(0, i).contains(true)) {
                        isUnknown = true;
                        break;
                    }
                } else if (foundTrue) {
                    // Si on trouve un "false" après un "true", la racine est mal classifiée
                    isMisclassified = true;
                    break;
                }
            }

            if (isUnknown) {
                classificationResult.get("last_time").add(rootId);
            } else if (isMisclassified) {
                classificationResult.get("misclassified").add(rootId);
            } else if (!foundTrue || (presenceSequence.indexOf(true) > presenceSequence.lastIndexOf(false))) {
                // Si la racine n'a que des "false" suivis de "true", elle est bien classifiée
                classificationResult.get("well_classified").add(rootId);
            }
        }

        return classificationResult;
    }

    // Function to classify roots over time by comparing them between consecutive time steps.
// The function iterates from the second last time step to the first and tries to classify
// each root based on various criteria, comparing with roots from the next time step.
    public static Map<LocalDateTime, IRootModelParser> gottaClassifyThemAll(
            Map<LocalDateTime, IRootModelParser> result,
            Map<String, List<Boolean>> rootPresenceMap,
            Map<String, List<String>> classifyRoots) {

        // Initialize counters to track the number of correctly and incorrectly classified roots.
        int numbadlyclassified = 0;
        int numwellclassified = 0;

        // Get the most recent time step.
        LocalDateTime lastTime = new ArrayList<>(result.keySet()).get(result.keySet().size() - 1);
        List<IRootParser> nextTimeRoots = new ArrayList<>(result.get(lastTime).getRoots());

        // Iterate over the time steps, starting from the second last to the first.
        for (int i = result.keySet().size() - 2; i >= 0; i--) {
            LocalDateTime currentTime = new ArrayList<>(result.keySet()).get(i);
            List<IRootParser> currentRoots = new ArrayList<>(result.get(currentTime).getRoots());

            // Iterate through each root in the current time step.
            for (IRootParser cRoot : currentRoots) {
                // Skip roots that are already classified as well classified or belong to the last time step.
                if (classifyRoots.get("well_classified").contains(cRoot.getId()) ||
                        classifyRoots.get("last_time").contains(cRoot.getId())) {
                    numwellclassified++;
                    continue;
                }

                // Find the best matching root from the next time step.
                IRootParser foundRoot = findBestMatchingRoot(cRoot, nextTimeRoots, classifyRoots);

                // Compare the labels of the current root and the found root to determine correct classification.
                if (foundRoot != null) {
                    if (foundRoot.getLabel().equals(cRoot.getLabel())) {
                        numwellclassified++;

                        // Remove the root from the misclassified list.
                        classifyRoots.get("misclassified").remove(cRoot.getId());

                        // Update the ID of the current root to match the found root.
                        cRoot.setId(foundRoot.getId());

                        // Update the presence map to indicate that the root is present in this time step.
                        List<Boolean> presence = rootPresenceMap.get(foundRoot.getId());
                        presence.set(i, true);
                        rootPresenceMap.put(foundRoot.getId(), presence);
                    } else {
                        numbadlyclassified++;
                    }
                } else {
                    numbadlyclassified++;
                }
            }

            // Move on to the roots of the next time step.
            nextTimeRoots = currentRoots;
        }

        // Print the results of the classification process.
        System.out.println("Number of badly classified roots: " + numbadlyclassified);
        System.out.println("Number of well classified roots: " + numwellclassified);

        // Return the modified result map.
        return result;
    }

    // Helper function to find the best matching root from the next time step based on various criteria.
    private static IRootParser findBestMatchingRoot(IRootParser currentRoot, List<IRootParser> nextTimeRoots, Map<String, List<String>> classifyRoots) {
        double bestScore = Double.MAX_VALUE;
        IRootParser bestMatch = null;

        // Extract the list of points for the current root.
        List<Point2D> currentPoints = ((Root4Parser) currentRoot).getGeometry().get2Dpt();

        // Iterate through all candidate roots in the next time step.
        for (IRootParser candidateRoot : nextTimeRoots) {
            // Skip already misclassified roots.
            if (classifyRoots.get("misclassified").contains(candidateRoot.getId())) continue;

            // Extract the list of points for the candidate root.
            List<Point2D> candidatePoints = ((Root4Parser) candidateRoot).getGeometry().get2Dpt();

            // Calculate the DTW (Dynamic Time Warping) distance between the two polylines.
            double dtwScore = calculateDTWDistance(currentPoints, candidatePoints);

            // Check if this is the best match so far.
            if (dtwScore < bestScore) {
                bestScore = dtwScore;
                bestMatch = candidateRoot;
            }
        }

        // Return the best matching root found.
        return bestMatch;
    }

    // Function to calculate the DTW (Dynamic Time Warping) distance between two sets of points.
    private static double calculateDTWDistance(List<Point2D> points1, List<Point2D> points2) {
        int n = points1.size();
        int m = points2.size();

        // Initialize the DTW cost matrix.
        double[][] dtw = new double[n][m];

        // Set the first cell.
        dtw[0][0] = points1.get(0).distance(points2.get(0));

        // Fill the first column of the DTW matrix.
        for (int i = 1; i < n; i++) {
            dtw[i][0] = dtw[i - 1][0] + points1.get(i).distance(points2.get(0));
        }

        // Fill the first row of the DTW matrix.
        for (int j = 1; j < m; j++) {
            dtw[0][j] = dtw[0][j - 1] + points1.get(0).distance(points2.get(j));
        }

        // Complete the DTW matrix using the recurrence relation.
        for (int i = 1; i < n; i++) {
            for (int j = 1; j < m; j++) {
                double cost = points1.get(i).distance(points2.get(j));
                dtw[i][j] = cost + Math.min(Math.min(dtw[i - 1][j], dtw[i][j - 1]), dtw[i - 1][j - 1]);
            }
        }

        // Return the final DTW distance, which is the value in the bottom-right corner of the matrix.
        return dtw[n - 1][m - 1];
    }

    // Function to find the best matching root based on insertion point and shape similarity.
    private static IRootParser findRootBySHAPEandINSERTION(IRootParser currentRoot, List<IRootParser> nextTimeRoots, Map<String, List<String>> classifyRoots) {
        double bestScore = Double.MAX_VALUE;
        IRootParser bestMatch = null;

        // Iterate through all candidate roots in the next time step.
        for (IRootParser candidateRoot : nextTimeRoots) {
            if (currentRoot.getOrder() != candidateRoot.getOrder()) continue;
            if (classifyRoots.get("misclassified").contains(candidateRoot.getId())) continue;

            // Calculate the insertion point distance, shape similarity, and length difference.
            double insertionPointScore = calculateInsertionPointDistance(currentRoot, candidateRoot);
            double shapeSimilarityScore = calculatePolylineShapeSimilarity(currentRoot, candidateRoot);
            double lengthDifferenceScore = calculateLengthDifference(currentRoot, candidateRoot);

            // Combine the scores with appropriate weights to find the best match.
            double combinedScore = (0.2 * insertionPointScore) + (0.8 * shapeSimilarityScore);

            if (combinedScore < bestScore) {
                bestScore = combinedScore;
                bestMatch = candidateRoot;
            }
        }

        // Return the best matching root found.
        return bestMatch;
    }

    // Function to calculate the distance between the insertion points of two roots.
    private static double calculateInsertionPointDistance(IRootParser root1, IRootParser root2) {
        Point2D point1 = ((Root4Parser) root1).getGeometry().get2Dpt().get(0);
        Point2D point2 = ((Root4Parser) root2).getGeometry().get2Dpt().get(0);
        return point1.distance(point2);
    }

    // Function to calculate the shape similarity between two roots based on their polylines.
    private static double calculatePolylineShapeSimilarity(IRootParser root1, IRootParser root2) {
        List<Point2D> points1 = ((Root4Parser) root1).getGeometry().get2Dpt();
        List<Point2D> points2 = ((Root4Parser) root2).getGeometry().get2Dpt();
        double distanceSum = 0;
        int numPointsToCompare = Math.min(points1.size(), points2.size());

        for (int i = 0; i < numPointsToCompare; i++) {
            distanceSum += points1.get(i).distance(points2.get(i));
        }
        return distanceSum / numPointsToCompare;
    }

    // Function to calculate the difference in length between two roots.
    private static double calculateLengthDifference(IRootParser root1, IRootParser root2) {
        double length1 = ((Root4Parser) root1).getGeometry().getTotalLength().stream().mapToDouble(Double::doubleValue).sum();
        double length2 = ((Root4Parser) root2).getGeometry().getTotalLength().stream().mapToDouble(Double::doubleValue).sum();
        return Math.abs(length1 - length2);
    }

    // Function to find the root with the smallest mean distance to the current root.
    private static IRootParser findRootMeanDistance(IRootParser cRoot, List<IRootParser> nextTimeRoots, Map<String, List<String>> classifyRoots) {
        double minDistance = Double.MAX_VALUE;
        IRootParser foundRoot = null;

        // Get the list of points for the current root.
        List<Point2D> pointsC = ((Root4Parser) cRoot).getGeometry().get2Dpt();

        // Iterate through all candidate roots in the next time step.
        for (IRootParser nRoot : nextTimeRoots) {
            if (cRoot.getOrder() != nRoot.getOrder()) continue;
            if (classifyRoots.get("misclassified").contains(nRoot.getId())) continue;

            // Get the list of points for the candidate root.
            List<Point2D> pointsN = ((Root4Parser) nRoot).getGeometry().get2Dpt();

            // Calculate the cumulative distance between corresponding points.
            double cumulativeDistance = 0.0;
            int pointsToCompare = Math.min(pointsC.size(), pointsN.size());

            for (int i = 0; i < pointsToCompare; i++) {
                cumulativeDistance += pointsC.get(i).distance(pointsN.get(i));
            }

            double meanDistance = cumulativeDistance / pointsToCompare;

            // Update the best match if a closer root is found.
            if (meanDistance < minDistance) {
                minDistance = meanDistance;
                foundRoot = nRoot;
            }
        }

        return foundRoot;
    }

    // Function to find the best matching root based solely on insertion point proximity.
    private static IRootParser findRootByInsertionPoint(IRootParser cRoot, List<IRootParser> nexTimeRoots, Map<String, List<String>> classifyRoots) {
        // Compare insertion point positions.
        Point2D insertionPointcRoot = (((Root4Parser) cRoot).getGeometry().get2Dpt().get(0));
        double minDistance = Double.MAX_VALUE;
        IRootParser foundRoot = null;

        for (IRootParser nRoot : nexTimeRoots) {
            if (cRoot.getOrder() != nRoot.getOrder()) continue;
            if (classifyRoots.get("misclassified").contains(nRoot.getId())) continue;

            Point2D insertionPointnRoot = (((Root4Parser) nRoot).getGeometry().get2Dpt().get(0));
            if (insertionPointcRoot.distance(insertionPointnRoot) < minDistance) {
                minDistance = insertionPointcRoot.distance(insertionPointnRoot);
                foundRoot = nRoot;
            }
        }

        return foundRoot;
    }

    public static Map<LocalDateTime, IRootModelParser> gottaClassifyThemAllByCluster(
            Map<LocalDateTime, IRootModelParser> result,
            Map<String, List<Boolean>> rootPresenceMap,
            Map<String, List<String>> classifyRoots) {

        int numBadlyClassified = 0;
        int numWellClassified = 0;

        // Organiser les racines par temps pour effectuer le clustering
        Map<LocalDateTime, List<IRootParser>> rootsByTime = new TreeMap<>();
        for (LocalDateTime date : result.keySet()) {
            List<IRootParser> roots = new ArrayList<>(result.get(date).getRoots());
            rootsByTime.put(date, roots);
        }

        // Clusteriser les racines par temps
        List<RootClustering.RootCluster> clusters = RootClustering.clusterRootsByTime(rootsByTime);

        // Obtenir le temps le plus récent
        LocalDateTime lastTime = rootsByTime.keySet().stream().max(LocalDateTime::compareTo).orElse(null);
        if (lastTime == null) return result;

        List<IRootParser> nextTimeRoots = new ArrayList<>(rootsByTime.get(lastTime));

        // Parcourir les temps du plus récent au plus ancien
        List<LocalDateTime> sortedTimes = new ArrayList<>(rootsByTime.keySet());
        sortedTimes.sort(Collections.reverseOrder()); // Du plus récent au plus ancien

        for (int i = 1; i < sortedTimes.size(); i++) {
            LocalDateTime currentTime = sortedTimes.get(i);
            List<IRootParser> currentRoots = rootsByTime.get(currentTime);

            // Pour chaque racine à l'instant t, trouver la correspondance à t+1
            for (IRootParser cRoot : currentRoots) {
                // Vérifier si la racine est déjà bien classifiée ou appartient à la dernière itération
                if (classifyRoots.get("well_classified").contains(cRoot.getId()) || classifyRoots.get("last_time").contains(cRoot.getId())) {
                    numWellClassified++;
                    continue;
                }

                // Utiliser le clustering pour trouver la meilleure correspondance
                IRootParser foundRoot = RootClustering.findBestCluster(cRoot, clusters, i).roots.stream()
                        .filter(nextTimeRoots::contains)
                        .findFirst()
                        .orElse(null);

                // Comparer les labels pour vérifier si la correspondance est correcte
                if (foundRoot != null) {
                    if (foundRoot.getLabel().equals(cRoot.getLabel())) {
                        numWellClassified++;
                        classifyRoots.get("misclassified").remove(cRoot.getId()); // Retirer des mal classifiés
                        rootPresenceMap.remove(cRoot.getId()); // Retirer du suivi des présences

                        // Mettre à jour l'ID de la racine pour maintenir la continuité
                        cRoot.setId(foundRoot.getId());

                        // Mettre à jour le rootPresenceMap pour refléter la continuité
                        List<Boolean> presence = rootPresenceMap.get(foundRoot.getId());
                        try {
                            presence.set(i, true);
                        }
                        catch (Exception e) {
                            System.out.println("Error: " + e);
                        }

                        rootPresenceMap.put(foundRoot.getId(), presence);
                    } else {
                        numBadlyClassified++;
                    }
                } else {
                    numBadlyClassified++;
                }
            }
            nextTimeRoots = currentRoots;
        }

        System.out.println("Number of well-classified roots: " + numWellClassified);
        System.out.println("Number of badly-classified roots: " + numBadlyClassified);

        return result;
    }

}

class RootClustering {

    // Classe représentant un cluster de racines
    public static class RootCluster {
        public List<IRootParser> roots;
        public Point2D centroid;
        public int order; // Ajouter l'ordre comme critère
        public int totalChildren; // Suivre le nombre d'enfants au fil du temps

        public RootCluster(int order) {
            this.roots = new ArrayList<>();
            this.order = order;
            this.totalChildren = 0;
        }

        // Mise à jour du centroïde du cluster
        public void updateCentroid() {
            double xSum = 0, ySum = 0;
            int count = 0;

            for (IRootParser root : roots) {
                Point2D insertionPoint = ((Root4Parser) root).getGeometry().get2Dpt().get(0);
                xSum += insertionPoint.getX();
                ySum += insertionPoint.getY();
                count++;
            }

            this.centroid = new Point2D.Double(xSum / count, ySum / count);
        }

        // Ajout d'une racine au cluster
        public void addRoot(IRootParser root) {
            this.roots.add(root);
            this.totalChildren += root.getChildren().size();
            updateCentroid();
        }

        // Vérification dynamique du nombre d'enfants
        public boolean isValidChildCount(int nextTimeChildCount) {
            return nextTimeChildCount >= this.totalChildren;
        }
    }

    // Initialisation des clusters avec les racines du dernier temps
    public static List<RootCluster> initializeClusters(List<IRootParser> finalTimeRoots) {
        List<RootCluster> clusters = new ArrayList<>();
        for (IRootParser root : finalTimeRoots) {
            RootCluster cluster = new RootCluster(root.getOrder());
            cluster.addRoot(root);
            clusters.add(cluster);
        }
        return clusters;
    }

    // Fonction principale pour organiser les racines par clusters à travers le temps
    public static List<RootCluster> clusterRootsByTime(Map<LocalDateTime, List<IRootParser>> rootsByTime) {
        // Obtenez la liste des temps triés
        List<LocalDateTime> times = new ArrayList<>(rootsByTime.keySet());
        Collections.sort(times);

        // Initialisez les clusters avec les racines du dernier temps
        LocalDateTime finalTime = times.get(times.size() - 1);
        List<RootCluster> clusters = initializeClusters(rootsByTime.get(finalTime));

        // Parcourez les temps en sens inverse (du dernier au premier)
        for (int t = times.size() - 2; t >= 0; t--) {
            LocalDateTime currentTime = times.get(t);
            List<IRootParser> currentRoots = rootsByTime.get(currentTime);

            // Assignez chaque racine au cluster le plus proche en termes de distance d'insertion et autres critères
            for (IRootParser root : currentRoots) {
                RootCluster bestCluster = findBestCluster(root, clusters, t);
                if (bestCluster != null && validateRoot(root, bestCluster, t)) { // Ajout des vérifications
                    bestCluster.addRoot(root);
                } else {
                    System.out.println("Incohérence détectée pour la racine " + root.getId() + " à l'instant " + currentTime);
                }
            }
        }

        return clusters;
    }

    // Validation des conditions supplémentaires
    private static boolean validateRoot(IRootParser root, RootCluster bestCluster, int timeIndex) {
        Root4Parser currentRoot = (Root4Parser) root;

        // Vérification de l'existence du parent
        boolean hasValidParent = currentRoot.getParent() != null;

        // Validation du nombre d'enfants
        boolean validChildCount = bestCluster.isValidChildCount(currentRoot.getChildren().size());

        return hasValidParent && validChildCount;
    }

    // Trouver le meilleur cluster pour une racine en fonction de la distance d'insertion et d'autres critères
    public static RootCluster findBestCluster(IRootParser root, List<RootCluster> clusters, int timeIndex) {
        Point2D insertionPoint = ((Root4Parser) root).getGeometry().get2Dpt().get(0);
        RootCluster bestCluster = null;
        double minDistance = Double.MAX_VALUE;

        for (RootCluster cluster : clusters) {
            // Assurez-vous que l'ordre correspond
            if (root.getOrder() != cluster.order) continue;

            // Calculer la distance pondérée basée sur plusieurs critères
            double distance = insertionPoint.distance(cluster.centroid);
            double shapeSimilarity = calculateShapeSimilarity(root, cluster);
            double combinedScore = 0.7 * distance + 0.3 * shapeSimilarity;

            if (combinedScore < minDistance) {
                minDistance = combinedScore;
                bestCluster = cluster;
            }
        }

        return bestCluster;
    }

    // Calcul de la similarité de la forme entre une racine et un cluster
    private static double calculateShapeSimilarity(IRootParser root, RootCluster cluster) {
        List<Point2D> rootPoints = ((Root4Parser) root).getGeometry().get2Dpt();
        double similarityScore = 0.0;
        int count = 0;

        for (IRootParser clusterRoot : cluster.roots) {
            List<Point2D> clusterPoints = ((Root4Parser) clusterRoot).getGeometry().get2Dpt();
            similarityScore += calculateMeanDistance(rootPoints, clusterPoints);
            count++;
        }

        return similarityScore / count;
    }

    // Calcul de la distance moyenne entre deux polylignes (formes)
    private static double calculateMeanDistance(List<Point2D> points1, List<Point2D> points2) {
        double distanceSum = 0;
        int numPointsToCompare = Math.min(points1.size(), points2.size());

        for (int i = 0; i < numPointsToCompare; i++) {
            distanceSum += points1.get(i).distance(points2.get(i));
        }

        return distanceSum / numPointsToCompare;
    }
}