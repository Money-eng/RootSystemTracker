package io.github.rocsg.rsmlparser;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public interface IRootModelParser {

    IRootModelParser createRootModel(IRootModelParser rootModel, float time);

    IRootModelParser createRootModels(Map<LocalDate, List<IRootModelParser>> rootModels, float scaleFactor);
}


