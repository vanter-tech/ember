package com.vanter.ember.hub.dashboard;

import com.vanter.ember.EmberApplication;
import com.vanter.ember.hub.bootstrap.HubBootstrapRunner;
import com.vanter.ember.hub.config.HubProperties;
import com.vanter.ember.hub.license.HubState;
import com.vanter.ember.hub.license.HubStateStore;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * v1 launcher/dashboard, replacing immediate auto-boot as the hub profile's entry point (spec
 * §2.3's system tray stays as a supplementary quick-access icon once the server is up — this is
 * now the primary way to start/stop Ember Hub). Deliberately plain Swing: zero new dependencies,
 * reuses {@link com.vanter.ember.hub.bootstrap.PortableDatabaseBootstrap}/{@code LicenseService}
 * unchanged. A branded web-based (Tauri) shell is planned as a v2 follow-up once this is validated
 * — see PROGRESS.md.
 */
public final class HubDashboard extends JFrame {

    private static final Logger log = LoggerFactory.getLogger(HubDashboard.class);

    private final HubProperties properties = HubProperties.fromEnvironment();
    private final HubBootstrapRunner bootstrapRunner = new HubBootstrapRunner(properties);
    private final HubStateStore stateStore = new HubStateStore(properties.stateFile());
    private final String[] launchArgs;

    private ConfigurableApplicationContext context;

    private final JLabel dbStatusLabel = new JLabel("Postgres: detenido");
    private final JLabel minioStatusLabel = new JLabel("MinIO: detenido");
    private final JLabel serverStatusLabel = new JLabel("Servidor: detenido");
    private final JLabel licenseStatusLabel = new JLabel("Licencia: sin estado local");
    private final JButton startButton = new JButton("Iniciar");
    private final JButton stopButton = new JButton("Detener");
    private final JButton openButton = new JButton("Abrir en navegador");
    private final JButton exitButton = new JButton("Salir");

    private HubDashboard(String[] launchArgs) {
        super("Ember Hub");
        this.launchArgs = launchArgs;

        JPanel statusPanel = new JPanel(new GridLayout(4, 1));
        statusPanel.add(dbStatusLabel);
        statusPanel.add(minioStatusLabel);
        statusPanel.add(serverStatusLabel);
        statusPanel.add(licenseStatusLabel);
        refreshLicenseStatus();

        JPanel buttonPanel = new JPanel(new GridLayout(1, 4, 8, 0));
        buttonPanel.add(startButton);
        buttonPanel.add(stopButton);
        buttonPanel.add(openButton);
        buttonPanel.add(exitButton);

        getContentPane().setLayout(new BorderLayout(0, 12));
        getContentPane().add(statusPanel, BorderLayout.CENTER);
        getContentPane().add(buttonPanel, BorderLayout.SOUTH);

        stopButton.setEnabled(false);
        openButton.setEnabled(false);

        startButton.addActionListener(e -> onStart());
        stopButton.addActionListener(e -> onStop());
        openButton.addActionListener(e -> onOpenBrowser());
        exitButton.addActionListener(e -> onExit());

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                onExit();
            }
        });

        setSize(360, 215);
        setLocationRelativeTo(null);
    }

    public static void launch(String[] args) {
        SwingUtilities.invokeLater(() -> new HubDashboard(args).setVisible(true));
    }

    private void onStart() {
        startButton.setEnabled(false);
        dbStatusLabel.setText("Postgres: iniciando...");
        minioStatusLabel.setText("MinIO: iniciando...");
        new Thread(this::startServicesInBackground, "hub-dashboard-start").start();
    }

    private void startServicesInBackground() {
        try {
            bootstrapRunner.startServices();
            SwingUtilities.invokeLater(() -> {
                dbStatusLabel.setText("Postgres: en ejecución");
                minioStatusLabel.setText("MinIO: en ejecución");
            });

            SpringApplication app = new SpringApplication(EmberApplication.class);
            app.addListeners((ApplicationListener<ApplicationReadyEvent>) event ->
                    SwingUtilities.invokeLater(() -> {
                        serverStatusLabel.setText("Servidor: listo");
                        openButton.setEnabled(true);
                        stopButton.setEnabled(true);
                        refreshLicenseStatus();
                    }));
            context = app.run(launchArgs);
        } catch (Exception e) {
            log.error("Ember Hub no pudo iniciar", e);
            SwingUtilities.invokeLater(() -> {
                dbStatusLabel.setText("Postgres: detenido");
                minioStatusLabel.setText("MinIO: detenido");
                startButton.setEnabled(true);
                JOptionPane.showMessageDialog(
                        this, e.getMessage(), "Ember Hub no puede iniciar", JOptionPane.ERROR_MESSAGE);
            });
        }
    }

    private void onStop() {
        stopButton.setEnabled(false);
        openButton.setEnabled(false);
        serverStatusLabel.setText("Servidor: deteniendo...");
        dbStatusLabel.setText("Postgres: deteniendo...");
        minioStatusLabel.setText("MinIO: deteniendo...");
        new Thread(this::stopServicesInBackground, "hub-dashboard-stop").start();
    }

    private void stopServicesInBackground() {
        if (context != null) {
            context.close();
            context = null;
        }
        bootstrapRunner.stopServices();
        SwingUtilities.invokeLater(() -> {
            dbStatusLabel.setText("Postgres: detenido");
            minioStatusLabel.setText("MinIO: detenido");
            serverStatusLabel.setText("Servidor: detenido");
            startButton.setEnabled(true);
            refreshLicenseStatus();
        });
    }

    private void refreshLicenseStatus() {
        licenseStatusLabel.setText(licenseStatusText(stateStore.load().orElse(null)));
    }

    private String licenseStatusText(HubState state) {
        if (state == null) {
            return "Licencia: sin estado local";
        }
        if (state.suspendedSince() != null) {
            return "Licencia: SUSPENDIDA (desde hace " + humanizeSince(state.suspendedSince()) + ")";
        }
        return "Licencia: OK · último contacto hace " + humanizeSince(state.lastHeartbeatAt());
    }

    private static String humanizeSince(Instant when) {
        Duration d = Duration.between(when, Instant.now());
        if (d.toMinutes() < 60) {
            return d.toMinutes() + " min";
        }
        if (d.toHours() < 48) {
            return d.toHours() + " h";
        }
        return d.toDays() + " d";
    }

    private void onOpenBrowser() {
        try {
            Desktop.getDesktop().browse(new URI("http://localhost:" + properties.serverPort() + "/app/"));
        } catch (Exception e) {
            log.error("No se pudo abrir el navegador", e);
        }
    }

    private void onExit() {
        exitButton.setEnabled(false);
        new Thread(() -> {
            if (context != null) {
                context.close();
            }
            bootstrapRunner.stopServices();
            System.exit(0);
        }, "hub-dashboard-exit").start();
    }
}
