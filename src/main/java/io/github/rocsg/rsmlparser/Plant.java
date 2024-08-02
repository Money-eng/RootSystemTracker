package io.github.rocsg.rsmlparser;

import io.github.rocsg.rsmlparser.RSML2D.Root4Parser;

import java.util.ArrayList;
import java.util.List;

// Plant.java
public class Plant {
    public String id;
    public String label;
    final List<IRootParser> roots;

    public Plant() {
        this.roots = new ArrayList<>();
        id = "";
        label = "";
    }

    public void addRoot(IRootParser root) {
        this.roots.add(root);
    }

    public List<String> getListID() {
        List<String> listID = new ArrayList<>();
        for (IRootParser root : roots) {
            listID.add(root.getId());
        }
        return listID;
    }

    public IRootParser getRootByID(String id) {
        for (IRootParser root : roots) {
            if (root.getId().equals(id)) {
                return root;
            }
        }
        return null;
    }

    public List<IRootParser> getRoots() {
        return roots;
    }

    public List<IRootParser> getFirstOrderRoots() {
        List<IRootParser> firstOrderRoots = new ArrayList<>();
        for (IRootParser root : roots) {
            if (root.getOrder() == 1) {
                firstOrderRoots.add(root);
            }
        }
        return firstOrderRoots;
    }

    @Override
    public String toString() {
        return "\nPlant{" +
                "roots=" + roots +
                "}\n";
    }
}
