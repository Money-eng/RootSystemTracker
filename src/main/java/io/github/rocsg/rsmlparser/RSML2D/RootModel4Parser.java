package io.github.rocsg.rsmlparser.RSML2D;

import io.github.rocsg.fijiyama.registration.ItkTransform;
import io.github.rocsg.rsml.RootModel;
import io.github.rocsg.rsmlparser.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RootModel4Parser implements IRootModelParser {
    public final List<Scene> scenes;
    public Metadata metadatas;

    public RootModel4Parser() {
        this.scenes = new ArrayList<>();
        this.metadatas = new Metadata();
    }

    public RootModel4Parser(Metadata metadata) {
        this.scenes = new ArrayList<>();
        this.metadatas = metadata;
    }

    public void addScene(Scene scene) {
        this.scenes.add(scene);
    }

    // get metadata elements
    public float getVersion() {
        return metadatas.getVersion();
    }

    public String getUnit() {
        return metadatas.getUnit();
    }

    public float getResolution() {
        return metadatas.getResolution();
    }

    public LocalDateTime getModifDate() {
        return metadatas.getModifDate();
    }

    public String getSoftware() {
        return metadatas.getSoftware();
    }

    public String getUser() {
        return metadatas.getUser();
    }

    public String getFileKey() {
        return metadatas.getFileKey();
    }

    public List<Scene> getScenes() {
        return scenes;
    }

    @Override
    public String toString() {
        return "RootModel4Parser{" +
                "scenes=" + scenes +
                '}';
    }

    @Override
    public List<IRootParser> getRoots() {
        List<IRootParser> roots = new ArrayList<>();
        for (Scene scene : scenes) {
            for (Plant plant : scene.getPlants()) {
                roots.addAll(plant.getFlatRoots());
            }
        }
        return roots;
    }

    public List<IRootParser> getHierarchyRoots() {
        List<IRootParser> roots = new ArrayList<>();
        for (Scene scene : scenes) {
            for (Plant plant : scene.getPlants()) {
                roots.addAll(plant.getRoots());
            }
        }
        return roots;
    }

    @Override
    public IRootModelParser createRootModel(IRootModelParser rootModel, float time) {
        if (rootModel instanceof RootModel4Parser) {
            return rootModel;
        } else if (rootModel instanceof RootModel) {
            return new RootModel4Parser();
        }
        return null;
    }

    @Override
    public IRootModelParser createRootModels(Map<LocalDateTime, IRootModelParser> rootModels, float scaleFactor) {
        return null;
    }

    public void applyTransformToGeometry(ItkTransform transform, int timeIndex) {
        for (Scene scene : scenes) {
            for (Plant plant : scene.getPlants()) {
                for (IRootParser root : plant.getFlatRoots()) {
                    root.applyTransformToGeometry(transform, timeIndex);
                }
            }
        }
    }

    public void scaleGeometry(double scaleFactor) {
        for (Scene scene : this.scenes) {
            for (Plant plant : scene.getPlants()) {
                for (IRootParser root : plant.getFlatRoots()) {
                    ((Root4Parser) root).scaleGeometry(scaleFactor);
                }
            }
        }
    }
}
