package com.textrefinery.ui.tray;

import com.textrefinery.domain.model.AiMode;
import com.textrefinery.domain.model.CleaningProfile;
import com.textrefinery.domain.model.Settings;
import com.textrefinery.ui.components.BrandLogo;
import java.awt.CheckboxMenuItem;
import java.awt.EventQueue;
import java.awt.Menu;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Icono y menú de bandeja (java.awt.SystemTray). Se construye en dos fases porque el notificador
 * necesita el icono antes de que existan los casos de uso a los que el menú llama.
 */
public class TrayController {

    public record Actions(Runnable cleanNow, Runnable restoreOriginal, Consumer<AiMode> refine,
                          Consumer<CleaningProfile> profileChanged, Consumer<Boolean> pausedChanged,
                          Runnable openSettings, Runnable exit) {
    }

    private final TrayIcon trayIcon;
    private final Map<CleaningProfile, CheckboxMenuItem> profileBoxes = new EnumMap<>(CleaningProfile.class);

    public TrayController() {
        trayIcon = SystemTray.isSupported()
                ? new TrayIcon(createImage(), "Text Refinery")
                : null;
        if (trayIcon != null) {
            trayIcon.setImageAutoSize(true);
        }
    }

    /** Vacío si el sistema no soporta bandeja (p. ej. algunos escritorios Linux). */
    public Optional<TrayIcon> icon() {
        return Optional.ofNullable(trayIcon);
    }

    public void install(Settings settings, Actions actions) {
        if (trayIcon == null) {
            return;
        }
        PopupMenu menu = new PopupMenu();

        MenuItem clean = new MenuItem("Limpiar portapapeles");
        clean.addActionListener(e -> actions.cleanNow().run());
        menu.add(clean);

        MenuItem restore = new MenuItem("Restaurar texto original");
        restore.addActionListener(e -> actions.restoreOriginal().run());
        menu.add(restore);

        Menu ai = new Menu("Refinar con IA");
        for (AiMode mode : AiMode.values()) {
            MenuItem item = new MenuItem(mode.label());
            item.addActionListener(e -> actions.refine().accept(mode));
            ai.add(item);
        }
        menu.add(ai);
        menu.addSeparator();

        Menu profiles = new Menu("Perfil");
        for (CleaningProfile profile : CleaningProfile.values()) {
            CheckboxMenuItem box = new CheckboxMenuItem(profile.label(), profile == settings.defaultProfile);
            box.addItemListener(e -> {
                selectProfile(profile);
                actions.profileChanged().accept(profile);
            });
            profileBoxes.put(profile, box);
            profiles.add(box);
        }
        menu.add(profiles);

        CheckboxMenuItem pause = new CheckboxMenuItem("Pausar atajos");
        pause.addItemListener(e -> actions.pausedChanged().accept(pause.getState()));
        menu.add(pause);
        menu.addSeparator();

        MenuItem settingsItem = new MenuItem("Ajustes…");
        settingsItem.addActionListener(e -> actions.openSettings().run());
        menu.add(settingsItem);

        MenuItem exit = new MenuItem("Salir");
        exit.addActionListener(e -> actions.exit().run());
        menu.add(exit);

        trayIcon.setPopupMenu(menu);
        trayIcon.addActionListener(e -> actions.openSettings().run()); // doble clic
        try {
            SystemTray.getSystemTray().add(trayIcon);
        } catch (java.awt.AWTException e) {
            throw new IllegalStateException("No se pudo añadir el icono a la bandeja", e);
        }
    }

    /**
     * Marca un perfil en el menú sin disparar el callback (para reflejar cambios hechos desde la ventana de ajustes).
     * Seguro de llamar desde cualquier hilo.
     */
    public void selectProfile(CleaningProfile selected) {
        EventQueue.invokeLater(() -> profileBoxes.forEach((p, box) -> box.setState(p == selected)));
    }

    public void remove() {
        if (trayIcon != null) {
            SystemTray.getSystemTray().remove(trayIcon);
        }
    }

    /** Logo de la marca a 64 px; AWT lo reduce al tamaño de la bandeja de cada sistema. */
    private static java.awt.Image createImage() {
        return BrandLogo.awtImage(64);
    }
}
