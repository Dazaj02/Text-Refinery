package com.textrefinery.infrastructure.notify;

import com.textrefinery.domain.port.NotifierPort;
import java.awt.TrayIcon;

/** Notificaciones nativas del sistema a través del icono de bandeja. */
public class TrayNotifier implements NotifierPort {

    private static final String TITLE = "Text Refinery";

    private final TrayIcon icon;

    public TrayNotifier(TrayIcon icon) {
        this.icon = icon;
    }

    @Override
    public void info(String message) {
        icon.displayMessage(TITLE, message, TrayIcon.MessageType.INFO);
    }

    @Override
    public void error(String message) {
        icon.displayMessage(TITLE, message, TrayIcon.MessageType.WARNING);
    }
}
