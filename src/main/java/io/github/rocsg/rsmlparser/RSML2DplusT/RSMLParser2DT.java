package io.github.rocsg.rsmlparser.RSML2DplusT;

import java.io.File;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.*;
import java.util.regex.Pattern;

import io.github.rocsg.rsml.Root;
import io.github.rocsg.rsml.RootModel;
import io.github.rocsg.rsmlparser.IRootModelParser;
import io.github.rocsg.rsmlparser.Metadata;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import io.github.rocsg.rsmlparser.Plant;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class RSMLParser2DT implements IRootModelParser {
    private Document document;

    public static void main(String[] args) {
        String rsmlFile = "D:\\loaiu\\MAM5\\Stage\\data\\UC1\\230629PN033\\61_graph_expertized.rsml";
       List<RootModel> rms = rootModelReadFromRsml(rsmlFile);
        System.out.println("Done");
    }

    public static List<RootModel> rootModelReadFromRsml(String rsmlFile) {
        List<RootModel> rootModels = new ArrayList<>();
        try {
            File file = new File(rsmlFile);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(file);
            doc.getDocumentElement().normalize();

            Element rootElement = doc.getDocumentElement();
            NodeList sceneNodes = rootElement.getElementsByTagName("scene");
            for (int i = 0; i < sceneNodes.getLength(); i++) {
                Element sceneElement = (Element) sceneNodes.item(i);
                RootModel rootModel = new RootModel();
                parseScene(sceneElement, rootModel);
                parseMetadata(rootElement, rootModel);
                rootModels.add(rootModel);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        rootModels.forEach(RootModel::standardOrderingOfRoots);
        return rootModels;
    }

    private static void parseMetadata(Element rootElement, RootModel rm) {
        Element metadata = (Element) rootElement.getElementsByTagName("metadata").item(0);
        rm.initializeMetadata();
        Metadata rootMetadata = rm.metadata;

        rootMetadata.setVersion(Float.parseFloat(metadata.getElementsByTagName("version").item(0).getTextContent()));
        rootMetadata.setUnit(metadata.getElementsByTagName("unit").item(0).getTextContent());
        rootMetadata.setSize(Double.parseDouble(metadata.getElementsByTagName("size").item(0).getTextContent()));
        rm.pixelSize = (float) rootMetadata.getSize();
        rootMetadata.setSoftware(metadata.getElementsByTagName("software").item(0).getTextContent());
        rootMetadata.setUser(metadata.getElementsByTagName("user").item(0).getTextContent());
        rootMetadata.setFileKey(metadata.getElementsByTagName("file-key").item(0).getTextContent());

        // <observation-hours>0.0,13.6599,19.6568,25.6655,31.6591,37.6594,43.6526,49.6572,55.6594,61.6578,67.6603,73.6592,79.664,85.6588,91.657,95.4302,101.4292,107.4299,113.4346,119.4347,125.4336,131.4316,137.4288,157.5066,159.2217,164.6051,170.6008,181.3911,182.6025</observation-hours>

        String[] observationHours = metadata.getElementsByTagName("observation-hours").item(0).getTextContent().split(",");
        double[] observationHoursDouble = new double[observationHours.length + 1 ];
        observationHoursDouble[0] = 0.0;
        for (int i = 0; i < observationHours.length; i++) {
            observationHoursDouble[i + 1] = Double.parseDouble(observationHours[i]);
        }
        rootMetadata.setObservationHours(observationHoursDouble);
        rm.hoursCorrespondingToTimePoints =  rootMetadata.getObservationHours();

        Element image = (Element) metadata.getElementsByTagName("image").item(0);
        rootMetadata.addImageInfo("label", image.getElementsByTagName("label").item(0).getTextContent());
        rootMetadata.addImageInfo("sha256", image.getElementsByTagName("sha256").item(0).getTextContent());
    }

    private static void parseScene(Element sceneElement, RootModel rootModel) {
        NodeList plantNodes = sceneElement.getElementsByTagName("plant");

        for (int i = 0; i < plantNodes.getLength(); i++) {
            Element plantElement = (Element) plantNodes.item(i);
            Plant plant = new Plant();
            plant.id = (plantElement.getAttribute("ID"));
            plant.label = (plantElement.getAttribute("label"));

            rootModel.imgName = plantElement.getAttribute("label");

            NodeList rootNodes = plantElement.getElementsByTagName("root");
            for (int j = 0; j < rootNodes.getLength(); j++) {
                Element rootElement = (Element) rootNodes.item(j);
                parseRoot(rootElement, null, rootModel, 1, new HashSet<String>());
            }
            //rootModel.standardOrderingOfRoots(); later in the code for debuging
        }
    }

    private static void parseRoot(Element rootElement, Root parentRoot, RootModel rm, int order, Set<String> rootsLabel) {
        // depends on the number of points in the id
        int ord = rootElement.getAttribute("ID").split("\\.").length - 1;
        if (ord != order) return;
        Root root = new Root(null, rm, rootElement.getAttribute("label"), order);
        parseRootGeometry(rootElement, root);
        root.computeDistances();
        if (order > 1 ) {
            root.attachParent(parentRoot);
            parentRoot.attachChild(root);
        }
        rootsLabel.add(root.rootID);
        NodeList childRootNodes = rootElement.getElementsByTagName("root");
        rm.rootList.add(root);
        for (int i = 0; i < childRootNodes.getLength(); i++) {
            Element childRootElement = (Element) childRootNodes.item(i);
            parseRoot(childRootElement, root, rm, order + 1, rootsLabel);
        }

    }

    private static void parseRootGeometry(Element rootElement, Root root) {
        // 1 geometry and 1 polyline per root
        Element geometryElement = (Element) rootElement.getElementsByTagName("geometry").item(0);
        Element polylineElement = (Element) geometryElement.getElementsByTagName("polyline").item(0);
        NodeList pointNodes = polylineElement.getElementsByTagName("point");
        for (int k = 0; k < pointNodes.getLength(); k++) {
            Element pointElement = (Element) pointNodes.item(k);
            root.addNode(
                    Float.parseFloat(pointElement.getAttribute("coord_x")),
                    Float.parseFloat(pointElement.getAttribute("coord_y")),
                    Float.parseFloat(pointElement.getAttribute("coord_t")),
                    Float.parseFloat(pointElement.getAttribute("coord_th")),
                    Float.parseFloat(pointElement.getAttribute("diameter")),
                    Float.parseFloat(pointElement.getAttribute("vx")),
                    Float.parseFloat(pointElement.getAttribute("vy")),
                    k == 0);
        }
    }


    @Override
    public IRootModelParser createRootModel(IRootModelParser rootModel, float time) {
        return null;
    }

    @Override
    public IRootModelParser createRootModels(Map<LocalDate, List<IRootModelParser>> rootModels, float scaleFactor) {
        return null;
    }
}
