package io.github.rocsg.rsmlparser.RSML2D;

import io.github.rocsg.rsml.RootModel;
import io.github.rocsg.rsmlparser.IRootModelParser;
import io.github.rocsg.rsmlparser.Metadata;
import io.github.rocsg.rsmlparser.Scene;

import java.time.LocalDate;
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

    public LocalDate getModifDate() {
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
    public IRootModelParser createRootModel(IRootModelParser rootModel, float time) {
        if (rootModel instanceof RootModel4Parser) {
            return rootModel;
        } else if (rootModel instanceof RootModel) {
            return new RootModel4Parser();
        }
        return null;
    }

    @Override
    public IRootModelParser createRootModels(Map<LocalDate, List<IRootModelParser>> rootModels, float scaleFactor) {
        return null;
    }

}
