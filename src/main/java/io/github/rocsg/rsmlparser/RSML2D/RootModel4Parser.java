package io.github.rocsg.rsmlparser.RSML2D;

import io.github.rocsg.fijiyama.registration.ItkTransform;
import io.github.rocsg.rsml.RootModel;
import io.github.rocsg.rsmlparser.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The RootModel4Parser class implements the IRootModelParser interface and is responsible for parsing
 * and managing root models in the RSML2D format.
 */
public class RootModel4Parser implements IRootModelParser {
    public final List<Scene> scenes; // List of scenes in the root model
    public Metadata metadatas; // Metadata associated with the root model

    /**
     * Default constructor initializing scenes and metadata.
     */
    public RootModel4Parser() {
        this.scenes = new ArrayList<>();
        this.metadatas = new Metadata();
    }

    /**
     * Constructor initializing with provided metadata.
     * @param metadata Metadata to initialize with.
     */
    public RootModel4Parser(Metadata metadata) {
        this.scenes = new ArrayList<>();
        this.metadatas = metadata;
    }

    /**
     * Adds a scene to the root model.
     * @param scene The scene to add.
     */
    public void addScene(Scene scene) {
        this.scenes.add(scene);
    }

    /**
     * Gets the version of the metadata.
     * @return The version as a float.
     */
    public float getVersion() {
        return metadatas.getVersion();
    }

    /**
     * Gets the unit of the metadata.
     * @return The unit as a String.
     */
    public String getUnit() {
        return metadatas.getUnit();
    }

    /**
     * Gets the resolution of the metadata.
     * @return The resolution as a float.
     */
    public float getResolution() {
        return metadatas.getResolution();
    }

    /**
     * Gets the modification date of the metadata.
     * @return The modification date as a LocalDateTime.
     */
    public LocalDateTime getModifDate() {
        return metadatas.getModifDate();
    }

    /**
     * Gets the software information from the metadata.
     * @return The software information as a String.
     */
    public String getSoftware() {
        return metadatas.getSoftware();
    }

    /**
     * Gets the user information from the metadata.
     * @return The user information as a String.
     */
    public String getUser() {
        return metadatas.getUser();
    }

    /**
     * Gets the file key from the metadata.
     * @return The file key as a String.
     */
    public String getFileKey() {
        return metadatas.getFileKey();
    }

    /**
     * Gets the list of scenes in the root model.
     * @return The list of scenes.
     */
    public List<Scene> getScenes() {
        return scenes;
    }

    /**
     * Returns a string representation of the RootModel4Parser.
     * @return A string representation of the object.
     */
    @Override
    public String toString() {
        return "RootModel4Parser{" +
                "scenes=" + scenes +
                '}';
    }

    /**
     * Gets the list of root parsers from the scenes.
     * @return The list of root parsers.
     */
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

    /**
     * Gets the list of hierarchical root parsers from the scenes.
     * @return The list of hierarchical root parsers.
     */
    public List<IRootParser> getHierarchyRoots() {
        List<IRootParser> roots = new ArrayList<>();
        for (Scene scene : scenes) {
            for (Plant plant : scene.getPlants()) {
                roots.addAll(plant.getRoots());
            }
        }
        return roots;
    }

    /**
     * Creates a new root model parser based on the provided root model and time.
     * @param rootModel The root model to base the new parser on.
     * @param time The time parameter.
     * @return A new IRootModelParser instance.
     */
    @Override
    public IRootModelParser createRootModel(IRootModelParser rootModel, float time) {
        if (rootModel instanceof RootModel4Parser) {
            return rootModel;
        } else if (rootModel instanceof RootModel) {
            return new RootModel4Parser();
        }
        return null;
    }

    /**
     * Creates a new root model parser based on the provided root models and scale factor.
     * @param rootModels The map of root models.
     * @param scaleFactor The scale factor.
     * @return A new IRootModelParser instance.
     */
    @Override
    public IRootModelParser createRootModels(Map<LocalDateTime, IRootModelParser> rootModels, float scaleFactor) {
        return null;
    }

    /**
     * Applies a transformation to the geometry of the roots in the scenes.
     * @param transform The transformation to apply.
     * @param timeIndex The time index for the transformation.
     */
    public void applyTransformToGeometry(ItkTransform transform, int timeIndex) {
        for (Scene scene : scenes) {
            for (Plant plant : scene.getPlants()) {
                for (IRootParser root : plant.getFlatRoots()) {
                    root.applyTransformToGeometry(transform, timeIndex);
                }
            }
        }
    }

    /**
     * Scales the geometry of the roots in the scenes by the given scale factor.
     * @param scaleFactor The scale factor to apply.
     */
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