package io.github.rocsg.rsmlparser;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

// Plant.java
public class Plant {
    final List<IRootParser> roots;
    final HashSet<IRootParser> flatsetOfroots;
    public String id;
    public String label;

    public Plant() {
        this.roots = new ArrayList<>();
        this.flatsetOfroots = new HashSet<>();
        id = "";
        label = "";
    }

    public void addRoot(IRootParser root) {
        this.roots.add(root);
        this.flatsetOfroots.add(root);
    }

    public void add2FlatSet(IRootParser root) {
        this.flatsetOfroots.add(root);
    }

    public List<String> getListID() {
        List<String> listID = new ArrayList<>();
        for (IRootParser root : flatsetOfroots) {
            listID.add(root.getId());
        }
        return listID;
    }

    public IRootParser getRootByID(String id) {
        for (IRootParser root : flatsetOfroots) {
            if (root.getId().equals(id)) {
                return root;
            }
        }
        return null;
    }

    public List<IRootParser> getRoots() {
        return roots;
    }

    public HashSet<IRootParser> getFlatRoots() {
        return flatsetOfroots;
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
