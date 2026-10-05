package com.textrefinery.domain.port;

import com.textrefinery.domain.model.Settings;

public interface SettingsPort {

    /** Carga la configuración; si no existe o está corrupta devuelve los valores por defecto. */
    Settings load();

    void save(Settings settings);
}
