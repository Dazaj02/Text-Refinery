package com.textrefinery.domain.port;

/** El motor de IA está en marcha pero no tiene el modelo pedido (hay que descargarlo). */
public class AiModelMissingException extends AiProviderException {

    private final String model;

    public AiModelMissingException(String model) {
        super("Ollama no tiene el modelo " + model);
        this.model = model;
    }

    public String model() {
        return model;
    }
}
