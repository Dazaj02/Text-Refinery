package com.textrefinery.infrastructure.notify;

import com.textrefinery.domain.port.NotifierPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Alternativa cuando el sistema no ofrece bandeja (algunos entornos Linux). */
public class LogNotifier implements NotifierPort {

    private static final Logger log = LoggerFactory.getLogger(LogNotifier.class);

    @Override
    public void info(String message) {
        log.info(message);
    }

    @Override
    public void error(String message) {
        log.warn(message);
    }
}
