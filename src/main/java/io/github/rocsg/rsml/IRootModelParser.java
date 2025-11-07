package io.github.rocsg.rsml;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface IRootModelParser {

    public List<IRootParser> getRoots();

    IRootModelParser createRootModel(IRootModelParser rootModel, float time);

    IRootModelParser createRootModels(Map<LocalDateTime, IRootModelParser> rootModels, float scaleFactor);
}


