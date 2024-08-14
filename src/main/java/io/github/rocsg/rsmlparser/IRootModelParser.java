package io.github.rocsg.rsmlparser;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface IRootModelParser {

    IRootModelParser createRootModel(IRootModelParser rootModel, float time);

    IRootModelParser createRootModels(Map<LocalDateTime, List<IRootModelParser>> rootModels, float scaleFactor);
}


