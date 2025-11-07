package io.github.rocsg.gui;

import io.github.rocsg.rsml.RSML2DplusT.Node;
import io.github.rocsg.rsml.RSML2DplusT.Root;
import io.github.rocsg.rsml.RSML2DplusT.RootModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.scijava.vecmath.Point3d;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class RsmlExpertPluginTest {

    private final double USER_PRECISION_ON_CLICK = 20;
    int countPoints = 5;
    private io.github.rocsg.gui.RsmlExpert_Plugin plugin;
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
        RootModel rm = new RootModel();
        // Temps de référence
        rm.hoursCorrespondingToTimePoints = new double[]{1, 2, 3, 4};

        // --- Racine primaire (ordre 1) ---
        Root primaryRoot = new Root(null, rm, "", 1);
        Node n1 = new Node(10, 10, null, false);   n1.birthTime = 1; n1.birthTimeHours = 1;
        Node n2 = new Node(15, 15, n1, true);      n2.birthTime = 2; n2.birthTimeHours = 1;  n1.child = n2;
        Node n3 = new Node(20, 20, n2, true);      n3.birthTime = 3; n3.birthTimeHours = 2;  n2.child = n3;
        Node n4 = new Node(25, 25, n3, true);      n4.birthTime = 4; n4.birthTimeHours = 3;  n3.child = n4;
        Node n5 = new Node(30, 30, n4, true);      n5.birthTime = 5; n5.birthTimeHours = 4;  n4.child = n5;

        primaryRoot.firstNode = n1;
        primaryRoot.lastNode = n5;
        primaryRoot.updateTiming();
        rm.rootList.add(primaryRoot);
        rm.increaseNbPlants();

        // --- Première racine latérale de second ordre depuis n2 (t=1h) ---
        // L1 : ordre 2, émerge de la primaire
        Root lateralRoot1 = new Root(null, rm, "", 2);
        Node l21 = new Node(15, 20, null, false);  l21.birthTime = 1; l21.birthTimeHours = 1;
        Node l22 = new Node(15, 25, l21, true);    l22.birthTime = 2; l22.birthTimeHours = 2; l21.child = l22;
        Node l23 = new Node(15, 30, l22, true);    l23.birthTime = 3; l23.birthTimeHours = 3; l22.child = l23;

        lateralRoot1.firstNode = l21;
        lateralRoot1.lastNode = l23;
        lateralRoot1.order = 2;
        lateralRoot1.updateTiming();
        primaryRoot.attachChild(lateralRoot1);
        lateralRoot1.attachParent(primaryRoot);
        rm.rootList.add(lateralRoot1);

        // À partir de l22 (t=2h), une latérale de 3ème ordre (L1.1)
        Root lateralRoot11 = new Root(null, rm, "", 3);
        Node l31 = new Node(10, 25, null, false);  l31.birthTime = 2; l31.birthTimeHours = 2;
        Node l32 = new Node(10, 30, l31, true);    l32.birthTime = 3; l32.birthTimeHours = 3; l31.child = l32;

        lateralRoot11.firstNode = l31;
        lateralRoot11.lastNode = l32;
        lateralRoot11.order = 3;
        lateralRoot11.updateTiming();
        lateralRoot1.attachChild(lateralRoot11);
        lateralRoot11.attachParent(lateralRoot1);
        rm.rootList.add(lateralRoot11);

        // --- Deuxième racine latérale de second ordre depuis n3 (t=2h) ---
        // L2 : ordre 2, émerge de la primaire
        Root lateralRoot2 = new Root(null, rm, "", 2);
        Node m21 = new Node(20, 25, null, false);  m21.birthTime = 2; m21.birthTimeHours = 2;
        Node m22 = new Node(20, 30, m21, true);    m22.birthTime = 3; m22.birthTimeHours = 3; m21.child = m22;

        lateralRoot2.firstNode = m21;
        lateralRoot2.lastNode = m22;
        lateralRoot2.order = 2;
        lateralRoot2.updateTiming();
        primaryRoot.attachChild(lateralRoot2);
        lateralRoot2.attachParent(primaryRoot);
        rm.rootList.add(lateralRoot2);

        // À partir de m22 (t=3h), une latérale de 3ème ordre (L2.1)
        Root lateralRoot21 = new Root(null, rm, "", 3);
        Node m31 = new Node(18, 30, null, false);  m31.birthTime = 3; m31.birthTimeHours = 3;
        Node m32 = new Node(18, 35, m31, true);    m32.birthTime = 4; m32.birthTimeHours = 4; m31.child = m32;

        lateralRoot21.firstNode = m31;
        lateralRoot21.lastNode = m32;
        lateralRoot21.order = 3;
        lateralRoot21.updateTiming();
        lateralRoot2.attachChild(lateralRoot21);
        lateralRoot21.attachParent(lateralRoot2);
        rm.rootList.add(lateralRoot21);

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

    private Point3d[] createBERandomChronologicalPointsNotFarFromRM(int count, RootModel rm) {
        // select random root in model
        int randomRootIndex = (int) (Math.random() * rm.rootList.size());
        Root randomRoot = rm.rootList.get(randomRootIndex);

        Node selectedNode = randomRoot.firstNode;

        Point3d[] points = new Point3d[count + 1];// TODO extension to plus or minus
        points[0] = new Point3d(selectedNode.x , selectedNode.y , selectedNode.birthTime);
        float lastTime = selectedNode.birthTime;
        Random random = new Random();
        for (int i = 1; i < count + 1; i++) {
            // more or less an epsilon factor
            float x = (float) (selectedNode.x + (float) Math.random() * USER_PRECISION_ON_CLICK / 2);
            float y = (float) (selectedNode.y + (float) Math.random() * USER_PRECISION_ON_CLICK / 2);
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
        points[0] = new Point3d(selectedNode.x + Math.random() * 2, selectedNode.y + Math.random() * 2, selectedNode.birthTime);
        float lastTime = selectedNode.birthTime;
        Random random = new Random();
        for (int i = 1; i < count + 1; i++) {
            // more or less an epsilon factor
            float x = (float) (selectedNode.x + (float) Math.random() * USER_PRECISION_ON_CLICK / 2);
            float y = (float) (selectedNode.y + (float) Math.random() * USER_PRECISION_ON_CLICK / 2);
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

        int randomPointIndex = (int) Math.floor(Math.random() * randomRoot.nNodes) - 1;
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
        // small variation of the point (<20)
        points[1].x += Math.random() * USER_PRECISION_ON_CLICK / 4; // WEIRD
        points[1].y += Math.random() * USER_PRECISION_ON_CLICK / 4;
        points[0].x += Math.random() * USER_PRECISION_ON_CLICK / 4;
        points[0].y += Math.random() * USER_PRECISION_ON_CLICK / 4;
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
        // consider a random int of points to add
        int numberOfPoints = (int) (Math.random() * 10);

        // Create those points in decreasing order of time with random poisition execept for the first point
        Point3d[] backExtendPoints = createBERandomChronologicalPointsNotFarFromRM(numberOfPoints, model);

        String[] result = plugin.backExtendBranchInModel(backExtendPoints, model);
        assertNotNull(result);
        assertEquals("BACKEXTENDBRANCH", result[0]);
    }
}
