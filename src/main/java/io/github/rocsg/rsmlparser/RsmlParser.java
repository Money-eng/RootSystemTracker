package io.github.rocsg.rsmlparser;

import ij.ImageJ;
import ij.ImagePlus;
import io.github.rocsg.fijiyama.registration.ItkTransform;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RsmlParser {

    public static SelectionStrategy strategy;
    public String path2RSMLs;


    public RsmlParser(String path2RSMLs) {
        this.path2RSMLs = path2RSMLs;

        RSMLFileUtils.backupStrategy = SelectionStrategy.LAST_VERSION;
        strategy = SelectionStrategy.MOST_COMPLEXITY;
    }

    public static void main(String[] args) throws IOException {
        ImageJ ij = new ImageJ();
        RootModelGraph rootModelGraph = new RootModelGraph();
        ImagePlus imp = rootModelGraph.image;
        List<ItkTransform> itkTransforms = rootModelGraph.transforms;
    }

    /**
     * Function to parse the roots from the rsml files
     * The roots are parsed by iterating over the root elements in the rsml files
     * The root elements are parsed to extract the root ID, label, properties, geometry, and functions
     * The root elements are then recursively parsed to extract the child roots
     * The roots are added to the Plant object
     * The Plant object is added to the Scene object
     * The Scene object is added to the RootModel4Parser object
     *
     * @param rootList    The NodeList containing the root elements to parse
     * @param parentPlant The Plant object to which the roots are added
     * @param parentRoot  The Root4Parser object to which the child roots are added
     * @param order       The order of the root
     * @param dateOfFile  The date of the rsml file
     */
    private static void parseRoots(NodeList rootList, Plant parentPlant, Root4Parser parentRoot, int order, String dateOfFile) { // TODO : we assumed 2nd order root max
        for (int i = 0; i < rootList.getLength(); i++) {
            org.w3c.dom.Node rootNode = rootList.item(i);

            if (rootNode.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                Element rootElement = (Element) rootNode;
                String rootID = rootElement.getAttribute("ID");
                String rootLabel = rootElement.getAttribute("label");
                String poAccession = rootElement.getAttribute("po:accession");

                Root4Parser root = new Root4Parser(rootID, rootLabel, poAccession, parentRoot, order, getDate(dateOfFile));

                NodeList propertiesList = rootElement.getElementsByTagName("properties");
                if (propertiesList.getLength() > 0) {
                    Element propertiesElement = (Element) propertiesList.item(0);
                    NodeList properties = propertiesElement.getChildNodes();
                    for (int j = 0; j < properties.getLength(); j++) {
                        org.w3c.dom.Node property = properties.item(j);
                        if (property.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                            Element propertyElement = (Element) property;
                            root.addProperty(new Property(propertyElement.getNodeName(), propertyElement.getTextContent()));
                        }
                    }
                }

                NodeList geometryList = rootElement.getElementsByTagName("geometry");
                if (geometryList.getLength() > 0) {
                    Element geometryElement = (Element) geometryList.item(0);
                    Geometry geometry = new Geometry();
                    NodeList polylineList = geometryElement.getElementsByTagName("polyline");
                    for (int k = 0; k < polylineList.getLength(); k++) {
                        org.w3c.dom.Node polylineNode = polylineList.item(k);
                        if (polylineNode.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                            Element polylineElement = (Element) polylineNode;
                            Polyline polyline = new Polyline();
                            NodeList pointList = polylineElement.getElementsByTagName("point");
                            for (int l = 0; l < pointList.getLength(); l++) {
                                org.w3c.dom.Node pointNode = pointList.item(l);
                                if (pointNode.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                                    Element pointElement = (Element) pointNode;
                                    String x = pointElement.getAttribute("x");
                                    String y = pointElement.getAttribute("y");
                                    polyline.addPoint(new Point4Parser(x, y));
                                }
                            }
                            geometry.addPolyline(polyline);
                        }
                    }
                    root.setGeometry(geometry);
                }

                NodeList functionList = rootElement.getElementsByTagName("function");
                Root4Parser.numFunctions = Math.min(Root4Parser.numFunctions, functionList.getLength()); // TODO : assuming coherence between functions length
                for (int m = 0; m < functionList.getLength(); m++) {
                    org.w3c.dom.Node functionNode = functionList.item(m);
                    if (functionNode.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                        Element functionElement = (Element) functionNode;
                        String functionName = functionElement.getAttribute("name");
                        Function function = new Function(functionName);
                        NodeList sampleList = functionElement.getElementsByTagName("sample");
                        for (int n = 0; n < sampleList.getLength(); n++) {
                            org.w3c.dom.Node sampleNode = sampleList.item(n);
                            if (sampleNode.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                                Element sampleElement = (Element) sampleNode;
                                function.addSample(sampleElement.getTextContent());
                            }
                        }
                        root.addFunction(function);
                    }
                }
                if (order == 1) parentPlant.addRoot(root);

                // Recursively parse child roots
                NodeList childRoots = rootElement.getElementsByTagName("root");
                parseRoots(childRoots, parentPlant, root, order + 1, dateOfFile);
            }
        }

        List<String> listID = parentPlant.getListID();
        List<Root4Parser> list2Remove = new ArrayList<>();
        for (Root4Parser root : parentPlant.roots) {
            if (root.children != null) {
                for (IRootParser child : root.children) {
                    if (listID.contains(child.getId())) {
                        list2Remove.add(parentPlant.getRootByID(child.getId()));
                    }
                }
            }
        }
        parentPlant.roots.removeAll(list2Remove);

        // for the first order roots of plant, keep only the first 2 functions
        for (Root4Parser root : parentPlant.roots) {
            if (root.functions.size() > Root4Parser.numFunctions) {
                root.functions.subList(2, root.functions.size()).clear();
            }
        }
    }

    /**
     * Function to get the information described in the rsml files
     * <p>
     * It checks for the different rsml files similarity, takes into account the unique ones
     * And iterates over the unique ones to get the information used to describe the roots
     *
     * @param folderPath The path to the folder containing the rsml files
     * @return A TreeMap with the date as key and the list of rsml infos as value
     */
    public static Map<LocalDate, List<IRootModelParser>> getRSMLsInfos(Path folderPath) {
        // check the uniqueness of the rsml files
        Stack<String> keptRsmlFiles = RSMLFileUtils.checkUniquenessRSMLs(folderPath, strategy);

        // get Date of each rsml (that supposetly match the image date) // TODO generalize
        ConcurrentHashMap<String, LocalDate> fileDates = new ConcurrentHashMap<>();

        // get the date of the rsml files
        try {
            Files.list(folderPath)
                    .parallel()
                    .filter(path -> path.toString().matches(".*\\.(rsml|rsml01|rsml02|rsml03|rsml04)$"))
                    .forEach(path -> {
                        fileDates.put(path.toString(), Objects.requireNonNull(getDate(path.toString().split("\\.")[0])));
                    });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Map<LocalDate, List<IRootModelParser>> rsmlInfos = new TreeMap<>();

        // add dates as keys
        fileDates.values().forEach(date -> rsmlInfos.put(date, new ArrayList<>()));

        // get the information from the rsml files
        for (String rsmlFile : keptRsmlFiles) {
            try {
                File inputFile = new File(rsmlFile);
                DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
                DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
                Document doc = dBuilder.parse(inputFile);
                doc.getDocumentElement().normalize();

                NodeList metadataList = doc.getElementsByTagName("metadata");
                Metadata metadata = new Metadata();

                for (int i = 0; i < metadataList.getLength(); i++) {
                    org.w3c.dom.Node metadataNode = metadataList.item(i);

                    if (metadataNode.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                        Element metadataElement = (Element) metadataNode;
                        metadata.version = Float.parseFloat(metadataElement.getElementsByTagName("version").item(0).getTextContent());
                        metadata.unit = metadataElement.getElementsByTagName("unit").item(0).getTextContent();
                        metadata.resolution = Float.parseFloat(metadataElement.getElementsByTagName("resolution").item(0).getTextContent());
                        metadata.modifDate = (metadataElement.getElementsByTagName("last-modified").item(0).getTextContent().equals("today") ? LocalDate.now() : getDate(metadataElement.getElementsByTagName("last-modified").item(0).getTextContent()));
                        metadata.software = metadataElement.getElementsByTagName("software").item(0).getTextContent();
                        metadata.user = metadataElement.getElementsByTagName("user").item(0).getTextContent();
                        metadata.fileKey = metadataElement.getElementsByTagName("file-key").item(0).getTextContent();

                        NodeList propertydefList = metadataElement.getElementsByTagName("property-definition");
                        for (int j = 0; j < propertydefList.getLength(); j++) {
                            org.w3c.dom.Node propertydefNode = propertydefList.item(j);

                            if (propertydefNode.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                                Element propertydefElement = (Element) propertydefNode;
                                Metadata.PropertyDefinition propertyDefinition = metadata.new PropertyDefinition();
                                propertyDefinition.label = propertydefElement.getElementsByTagName("label").item(0).getTextContent();
                                propertyDefinition.type = propertydefElement.getElementsByTagName("type").item(0).getTextContent();
                                propertyDefinition.unit = propertydefElement.getElementsByTagName("unit").item(0).getTextContent();
                                metadata.propertiedef.add(propertyDefinition);
                            }
                        }
                        NodeList imageList = metadataElement.getElementsByTagName("image"); // TODO : generalize

                        // get the label node
                        for (int j = 0; j < imageList.getLength(); j++) {
                            org.w3c.dom.Node imageNode = imageList.item(j);

                            if (imageNode.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                                Element imageElement = (Element) imageNode;
                                metadata.date2Use = imageElement.getElementsByTagName("label").item(0).getTextContent();
                            }
                        }
                    }
                }

                RootModel4Parser RootModel4Parser = new RootModel4Parser(metadata);


                NodeList sceneList = doc.getElementsByTagName("scene");

                for (int i = 0; i < sceneList.getLength(); i++) {

                    org.w3c.dom.Node sceneNode = sceneList.item(i);

                    if (sceneNode.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                        Element sceneElement = (Element) sceneNode;
                        Scene scene = new Scene();

                        NodeList plantList = sceneElement.getElementsByTagName("plant");
                        for (int j = 0; j < plantList.getLength(); j++) {
                            org.w3c.dom.Node plantNode = plantList.item(j);

                            if (plantNode.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                                Element plantElement = (Element) plantNode;
                                Plant plant = new Plant();

                                NodeList rootList = plantElement.getElementsByTagName("root");
                                parseRoots(rootList, plant, null, 1, metadata.date2Use);
                                scene.addPlant(plant);
                            }
                        }
                        RootModel4Parser.addScene(scene);
                    }
                }
                // add the RootModel4Parser to the corresponding date
                rsmlInfos.get(fileDates.get(rsmlFile)).add(RootModel4Parser);

            } catch (ParserConfigurationException | SAXException | IOException e) {
                throw new RuntimeException(e);
            }
        }

        System.out.println("rsmlInfos : " + rsmlInfos);
        return rsmlInfos;
    }

    /**
     * Function to get the date from a String
     * The date is extracted from the String using a regular expression
     * The date is returned as a LocalDate
     *
     * @param Date The String from which to extract the date
     * @return The LocalDate extracted from the String
     */
    private static LocalDate getDate(String Date) {
        // TODO generalize
        // detect date in a String
        //// For now pattern is dd_mm_yyyy
        //// Time is set to 00:00:00
        Pattern pattern = Pattern.compile("\\d{2}_\\d{2}_\\d{4}");
        Matcher matcher = pattern.matcher(Date);
        LocalDate date = null;
        if (matcher.find()) {
            int year = Integer.parseInt(matcher.group(0).split("_")[2]);
            int month = Integer.parseInt(matcher.group(0).split("_")[1]);
            int day = Integer.parseInt(matcher.group(0).split("_")[0]);
            date = LocalDate.of(year, month, day);
        }

        Pattern pattern4Time = Pattern.compile("\\d{2}:\\d{2}:\\d{2}");
        Matcher matcher4Time = pattern4Time.matcher(Date);
        if (matcher4Time.find()) {
            int hour = Integer.parseInt(matcher4Time.group(0).split(":")[0]);
            int minute = Integer.parseInt(matcher4Time.group(0).split(":")[1]);
            int second = Integer.parseInt(matcher4Time.group(0).split(":")[2]);
            date = Objects.requireNonNull(date).atTime(hour, minute, second).toLocalDate();
        }
        return date;
    }
}
