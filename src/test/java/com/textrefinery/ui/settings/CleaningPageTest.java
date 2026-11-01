package com.textrefinery.ui.settings;

import static org.assertj.core.api.Assertions.assertThat;

import com.textrefinery.application.PipelineFactory;
import java.util.List;
import org.junit.jupiter.api.Test;

class CleaningPageTest {

    @Test
    void laListaDeProcesadoresDeLaUiCoincideConLaDelPipeline() {
        List<String> ui = CleaningPage.PROCESSORS.stream().map(CleaningPage.ProcessorInfo::name).toList();
        assertThat(ui).containsExactlyElementsOf(PipelineFactory.processorNames());
    }
}
