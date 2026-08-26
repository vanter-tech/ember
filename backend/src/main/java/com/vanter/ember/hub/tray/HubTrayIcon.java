package com.vanter.ember.hub.tray;

import java.awt.AWTException;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Minimal v1 tray icon: presence + a way to open/quit, per spec §2.3. Auto-updater notifications
 * (spec §2.10) are HUB-04's job, once there's a version to compare against from HUB-02's sync
 * response.
 */
@Component
@Profile("hub")
public class HubTrayIcon {

    private static final Logger log = LoggerFactory.getLogger(HubTrayIcon.class);

    @EventListener(ApplicationReadyEvent.class)
    public void show() {
        if (!SystemTray.isSupported()) {
            log.warn("System tray no soportado en este sistema; Ember Hub sigue corriendo sin ícono.");
            return;
        }
        try {
            SystemTray tray = SystemTray.getSystemTray();

            PopupMenu menu = new PopupMenu();
            MenuItem open = new MenuItem("Abrir Ember Hub");
            open.addActionListener(e -> openBrowser());
            menu.add(open);
            MenuItem exit = new MenuItem("Salir");
            exit.addActionListener(e -> System.exit(0));
            menu.add(exit);

            TrayIcon trayIcon = new TrayIcon(createIcon(), "Ember Hub", menu);
            trayIcon.setImageAutoSize(true);
            trayIcon.addActionListener(e -> openBrowser());
            tray.add(trayIcon);
        } catch (AWTException e) {
            log.error("No se pudo mostrar el ícono de bandeja del sistema", e);
        }
    }

    /**
     * Drawn at runtime instead of bundling a PNG resource — a real designed icon is a packaging
     * polish concern (HUB-03), not a blocker for the tray mechanism itself.
     */
    private Image createIcon() {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(0x8c, 0x17, 0x17));
        g.fillOval(0, 0, 16, 16);
        g.dispose();
        return image;
    }

    private void openBrowser() {
        try {
            Desktop.getDesktop().browse(new URI("http://localhost:8080/app/"));
        } catch (IOException | URISyntaxException e) {
            log.error("No se pudo abrir el navegador", e);
        }
    }
}
