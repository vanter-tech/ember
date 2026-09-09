package com.vanter.emberagent.ui;

import com.vanter.emberagent.status.StatusHub;
import java.awt.AWTException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/**
 * Minimal system-tray presence for the desktop agent, cloned almost verbatim from the Hub's
 * {@code HubTrayIcon}: a brand-red 16×16 icon drawn at runtime, a "Mostrar / Salir" popup, and a
 * tooltip that tracks {@link StatusHub.Phase}. If the platform has no tray the agent just keeps
 * running windowed — this is not fatal.
 */
public final class AgentTrayIcon {

    private static final Logger log = System.getLogger(AgentTrayIcon.class.getName());

    private AgentTrayIcon() {}

    public static void install(JFrame window, StatusHub hub) {
        if (!SystemTray.isSupported()) {
            log.log(Level.WARNING, "System tray no soportado; Ember Agent sigue corriendo sin ícono.");
            return;
        }
        try {
            PopupMenu menu = new PopupMenu();
            MenuItem show = new MenuItem("Mostrar Ember Agent");
            show.addActionListener(e -> showWindow(window));
            menu.add(show);
            MenuItem exit = new MenuItem("Salir");
            exit.addActionListener(e -> System.exit(0));
            menu.add(exit);

            TrayIcon trayIcon = new TrayIcon(createIcon(), "Ember Agent", menu);
            trayIcon.setImageAutoSize(true);
            trayIcon.addActionListener(e -> showWindow(window));
            SystemTray.getSystemTray().add(trayIcon);

            hub.addListener(snapshot -> trayIcon.setToolTip("Ember Agent — " + snapshot.detail()));
        } catch (AWTException e) {
            log.log(Level.ERROR, "No se pudo instalar el ícono de bandeja", e);
        }
    }

    private static void showWindow(JFrame window) {
        SwingUtilities.invokeLater(() -> {
            window.setVisible(true);
            window.setExtendedState(JFrame.NORMAL);
            window.toFront();
            window.requestFocus();
        });
    }

    private static Image createIcon() {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(0x8c, 0x17, 0x17));
        g.fillOval(0, 0, 16, 16);
        g.dispose();
        return image;
    }
}
