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
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * RsmlParser2 class for parsing RSML files and extracting root models.
 * This class provides methods to parse RSML files, extract metadata, and build hierarchical root models.
 */
public class Rsml2DParser {

    public static SelectionStrategy strategy;
    public static List<LocalDateTime> removedDates = new ArrayList<>();
    public String path2RSMLs;

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

    public static void main(String[] args) throws IOException {
        // Load the SimpleITK Java library for image processing.
        System.loadLibrary("SimpleITKJava");

        // Initialize ImageJ for image visualization.
        ImageJ ij = new ImageJ();
        RootModelGraph rootModelGraph = new RootModelGraph();
    }

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
        RSMLFileUtils.backupStrategy = SelectionStrategy.LAST_VERSION;
        strategy = SelectionStrategy.MOST_COMPLEXITY;
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
    private static void parseRoots(NodeList rootList, Plant parentPlant, Root4Parser parentRoot, int order, String dateOfFile, Set<String> addedRootIDs) {
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
                Root4Parser root = createRoot4Parser(rootElement, parentRoot, order, dateOfFile);

                // Parse properties, geometry, and functions of the root.
                parseProperties(rootElement, root);
                parseGeometry(rootElement, root);
                parseFunctions(rootElement, root);
                parseAnnotations(rootElement, root);

                // Add root to parent plant if it is a primary root.
                if (order == 1) {
                    parentPlant.addRoot(root);
                }

                // Mark this root ID as added.
                addedRootIDs.add(rootID);

                // Recursively parse child roots.
                NodeList childRoots = rootElement.getElementsByTagName("root");
                parseRoots(childRoots, parentPlant, root, order + 1, dateOfFile, addedRootIDs);
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
    private static Root4Parser createRoot4Parser(Element rootElement, Root4Parser parentRoot, int order, String dateOfFile) {
        String rootID = rootElement.getAttribute("ID");
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
                        parseRoots(rootList, plant, null, 1, metadata.getDate2Use(), addedRootIDs);
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
}
