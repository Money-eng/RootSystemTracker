package io.github.rocsg.rsml;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.scijava.vecmath.Point3d;

import java.util.*;

import static io.github.rocsg.rsml.RootModel.createRandomRootModel;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class RsmlExpertPluginTest {

    private final double USER_PRECISION_ON_CLICK = 20;
    int countPoints = 5;
    private RsmlExpert_Plugin plugin;
    private RootModel model;

    @BeforeEach
    public void setUp() {
        plugin = new RsmlExpert_Plugin();
        model = new RootModel();
        // Initialisation du modèle racinaire avec des données de test
        model = createTestRootModel();
        plugin.setCurrentModel(model);

        // random points count
        countPoints = (int) (Math.random() * 30);
    }

    private RootModel createTestRootModel() {
        int maxRoot = 5;
        int maxOrder = 2;
        RootModel rm = createRandomRootModel(maxRoot, maxOrder);
        // get all timeInhours of rootmodel
        HashSet<Double> timeInHours = new HashSet<>();
        for (Root root : rm.rootList) {
            Node node = root.firstNode;
            while (node != null) {
                timeInHours.add((double) node.birthTimeHours);
                node = node.child;
            }
        }
        timeInHours.stream().sorted();
        rm.hoursCorrespondingToTimePoints = timeInHours.stream().mapToDouble(Double::doubleValue).toArray();
        return rm;
    }

    private Point3d[] createRandomChronologicalPoints(int count) {
        Point3d[] points = new Point3d[count];
        float lastTime = 0;
        Random random = new Random();
        for (int i = 0; i < count; i++) {
            float x = random.nextFloat() * 100;
            float y = random.nextFloat() * 100;
            float time = lastTime + random.nextInt(2);
            if (time > model.hoursCorrespondingToTimePoints.length - 1) {
                time = model.hoursCorrespondingToTimePoints.length - 1;
            }
            points[i] = new Point3d(x, y, time);
            lastTime = time;
        }
        return points;
    }

    private Point3d[] createRandomChronologicalPointsNotFarFromRM(int count, RootModel rm) {
        // select random node in model
        int randomRootIndex = (int) (Math.random() * rm.rootList.size());
        Root randomRoot = rm.rootList.get(randomRootIndex);

        int randomPointIndex = (int) (Math.random() * randomRoot.nNodes);
        Node selectedNode = randomRoot.firstNode;
        while (randomPointIndex > 0) {
            selectedNode = selectedNode.child;
            randomPointIndex--;
        }

        Point3d[] points = new Point3d[count];
        float lastTime = selectedNode.child != null ? selectedNode.child.birthTime : selectedNode.birthTime;
        Random random = new Random();
        for (int i = 0; i < count; i++) {
            // more or less an epsilon factor
            float x = (float) (selectedNode.x + (float) Math.random() * USER_PRECISION_ON_CLICK / 2);
            float y = (float) (selectedNode.y + (float) Math.random() * USER_PRECISION_ON_CLICK / 2);
            float time = lastTime + random.nextInt(2);
            if (time > rm.hoursCorrespondingToTimePoints.length - 1) {
                time = rm.hoursCorrespondingToTimePoints.length - 1;
            }
            points[i] = new Point3d(x, y, time);
            lastTime = time;
        }
        return points;
    }

    private Point3d[] createRandomChronologicalPointsNotFarFromRM(int count, RootModel rm, boolean primary) {
        if (!primary) {
            return createRandomChronologicalPointsNotFarFromRM(count, rm);
        }
        // select random node in model
        int randomRootIndex = (int) (Math.random() * rm.rootList.size());
        Root randomRoot = rm.rootList.get(randomRootIndex);
        while (randomRoot.order != 1) {
            randomRootIndex = (int) (Math.random() * rm.rootList.size());
            randomRoot = rm.rootList.get(randomRootIndex);
        }

        int randomPointIndex = (int) (Math.random() * randomRoot.nNodes);
        Node selectedNode = randomRoot.firstNode;
        while (randomPointIndex > 0) {
            selectedNode = selectedNode.child;
            randomPointIndex--;
        }

        Point3d[] points = new Point3d[count];
        float lastTime = selectedNode.child != null ? selectedNode.child.birthTime : selectedNode.birthTime;
        Random random = new Random();
        for (int i = 0; i < count; i++) {
            // more or less an epsilon factor
            float x = (float) (selectedNode.x + (float) Math.random() * USER_PRECISION_ON_CLICK/2);
            float y = (float) (selectedNode.y + (float) Math.random() * USER_PRECISION_ON_CLICK/2);
            float time = lastTime + random.nextInt(2);
            if (time > rm.hoursCorrespondingToTimePoints.length - 1) {
                time = rm.hoursCorrespondingToTimePoints.length - 1;
            }
            points[i] = new Point3d(x, y, time);
            lastTime = time;
        }
        return points;
    }

    private Point3d[] createBERandomChronologicalPointsNotFarFromRM(int count, RootModel rm, boolean primary) {
        // select random node in model
        int randomRootIndex = (int) (Math.random() * rm.rootList.size());
        Root randomRoot = rm.rootList.get(randomRootIndex);
        while (randomRoot.order != 1) {
            randomRootIndex = (int) (Math.random() * rm.rootList.size());
            randomRoot = rm.rootList.get(randomRootIndex);
        }

        Node selectedNode = randomRoot.firstNode;

        Point3d[] points = new Point3d[count + 1];
        points[0] = new Point3d(selectedNode.x + Math.random() *2, selectedNode.y + Math.random() * 2, selectedNode.birthTime);
        float lastTime = selectedNode.birthTime;
        Random random = new Random();
        for (int i = 1; i < count + 1; i++) {
            // more or less an epsilon factor
            float x = (float) (selectedNode.x + (float) Math.random() * USER_PRECISION_ON_CLICK/2);
            float y = (float) (selectedNode.y + (float) Math.random() * USER_PRECISION_ON_CLICK/2);
            float time = lastTime - random.nextInt(2);
            if (time < 0) {
                time = 0;
            }
            points[i] = new Point3d(x, y, time);
            lastTime = time;
        }
        return points;
    }

    private Point3d[] createERandomChronologicalPointsNotFarFromRM(int count, RootModel rm, boolean primary) {
        // select random node in model
        int randomRootIndex = (int) (Math.random() * rm.rootList.size());
        Root randomRoot = rm.rootList.get(randomRootIndex);
        while (randomRoot.order != 1) {
            randomRootIndex = (int) (Math.random() * rm.rootList.size());
            randomRoot = rm.rootList.get(randomRootIndex);
        }

        Node selectedNode = randomRoot.lastNode;

        Point3d[] points = new Point3d[count + 1];
        points[0] = new Point3d(selectedNode.x + Math.random() *2, selectedNode.y + Math.random() * 2, selectedNode.birthTime);
        float lastTime = selectedNode.birthTime;
        Random random = new Random();
        for (int i = 1; i < count + 1; i++) {
            // more or less an epsilon factor
            float x = (float) (selectedNode.x + (float) Math.random() * USER_PRECISION_ON_CLICK/2);
            float y = (float) (selectedNode.y + (float) Math.random() * USER_PRECISION_ON_CLICK/2);
            float time = lastTime + i;
            if (time > rm.hoursCorrespondingToTimePoints.length - 1) {
                time = rm.hoursCorrespondingToTimePoints.length - 1;
            }
            points[i] = new Point3d(x, y, time);
        }
        return points;
    }

    @RepeatedTest(10)
    public void testMovePointInModel() {
        // select random point in model
        int randomRootIndex = (int) (Math.random() * model.rootList.size());
        Root randomRoot = model.rootList.get(randomRootIndex);

        int randomPointIndex = (int) (Math.random() * randomRoot.nNodes);
        Node selectedNode = randomRoot.firstNode;
        while (randomPointIndex > 0) {
            selectedNode = selectedNode.child;
            randomPointIndex--;
        }

        Point3d[] points = new Point3d[]{
                new Point3d(selectedNode.x, selectedNode.y, selectedNode.birthTime),
                new Point3d(selectedNode.x + 5, selectedNode.y + 5, selectedNode.birthTime)
        };

        String[] result = plugin.movePointInModel(points, model);
        assertNotNull(result);
        assertEquals("MOVEPOINT", result[0]);
    }

    @RepeatedTest(10)
    public void testRemovePointInModel() {
        // select random point in model
        int randomRootIndex = (int) (Math.random() * model.rootList.size());
        Root randomRoot = model.rootList.get(randomRootIndex);

        int randomPointIndex = (int) (Math.random() * randomRoot.nNodes);
        Node selectedNode = randomRoot.firstNode;
        while (randomPointIndex > 0) {
            selectedNode = selectedNode.child;
            randomPointIndex--;
        }

        Point3d[] points = new Point3d[]{
                new Point3d(selectedNode.x, selectedNode.y, selectedNode.birthTime),
                new Point3d(selectedNode.x + 5, selectedNode.y + 5, selectedNode.birthTime)
        };
        String[] result = plugin.removePointInModel(points, model);
        assertNotNull(result);
        assertEquals("REMOVEPOINT", result[0]);
    }

    @RepeatedTest(10)
    public void testRefineSegmentInModel() {
        // select random point in model
        int randomRootIndex = (int) (Math.random() * model.rootList.size());
        Root randomRoot = model.rootList.get(randomRootIndex);

        int randomPointIndex = (int) (Math.random() * randomRoot.nNodes);
        Node selectedNode = randomRoot.firstNode;
        while (randomPointIndex > 0) {
            selectedNode = selectedNode.child;
            randomPointIndex--;
        }

        Point3d[] points = new Point3d[]{
                new Point3d(selectedNode.x, selectedNode.y, selectedNode.birthTime),
                selectedNode.child != null ? new Point3d(selectedNode.child.x, selectedNode.child.y, selectedNode.child.birthTime) :
                        new Point3d(selectedNode.parent.x, selectedNode.parent.y, selectedNode.parent.birthTime),
        };
        String[] result = plugin.refineSegmentInModel(points, model);
        assertNotNull(result);
        assertEquals("ADDMIDDLE", result[0]);
    }

    @RepeatedTest(10)
    public void testCreatePrimaryInModel() {
        Point3d[] points = createRandomChronologicalPoints(countPoints);
        String[] result = plugin.createPrimaryInModel(points, model);

        assertNotNull(result);
        assertEquals("CREATEPRIMARY", result[0]);
    }

    @RepeatedTest(10)
    public void testCreateBranchInModel() {
        // random boolean
        Point3d[] points = createRandomChronologicalPointsNotFarFromRM(countPoints, model, Math.random() > 0.5);

        String[] result = plugin.createBranchInModel(points, model);

        assertNotNull(result);
        assertEquals("CREATEBRANCH", result[0]);
    }

    @RepeatedTest(10)
    public void testExtendBranchInModel() {
        // Étendre la branche sur des points chronologiquement plus tardifs
        Point3d[] extendPoints = createERandomChronologicalPointsNotFarFromRM(countPoints, model, Math.random() > 0.5);

        String[] result = plugin.extendBranchInModel(extendPoints, model);

        assertNotNull(result);
        assertEquals("EXTENDBRANCH", result[0]);
    }

    @RepeatedTest(10)
    public void testBackExtendBranchInModel() {
        // Back-étendre la branche sur des points chronologiquement antérieurs
        Point3d[] backExtendPoints = createBERandomChronologicalPointsNotFarFromRM(countPoints, model, Math.random() > 0.5);

        String[] result = plugin.backExtendBranchInModel(backExtendPoints, model);

        assertNotNull(result);
        assertEquals("BACKEXTENDBRANCH", result[0]);
    }
}
