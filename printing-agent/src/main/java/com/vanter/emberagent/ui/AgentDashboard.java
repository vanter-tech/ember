package com.vanter.emberagent.ui;

import com.vanter.emberagent.AgentPaths;
import com.vanter.emberagent.AgentRunner;
import com.vanter.emberagent.DiagnosticsReport;
import com.vanter.emberagent.DiscoveredPrinter;
import com.vanter.emberagent.PrinterConfigClient;
import com.vanter.emberagent.WindowsPrintQueueSender;
import com.vanter.emberagent.WindowsPrinterEnumerator;
import com.vanter.emberagent.credential.CredentialStore;
import com.vanter.emberagent.status.StatusHub;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;

/**
 * v1 desktop window for the print agent (spec §2.1), plain Swing like the Hub's {@code
 * HubDashboard} — zero new dependencies. Read-only: it observes a {@link StatusHub} the {@link
 * AgentRunner} feeds and never drives the connection itself. Printer <em>registration</em> stays
 * in the admin UI (spec §5); the "Impresora" zone here only lists local Windows queues and fires a
 * local test print, deliberately without a "Guardar" — noted deviation from the §2.1 table.
 */
public final class AgentDashboard extends JFrame {

    private static final Logger log = System.getLogger(AgentDashboard.class.getName());
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final transient StatusHub hub;
    private final transient CredentialStore store;
    private final transient WindowsPrinterEnumerator enumerator = new WindowsPrinterEnumerator();

    private final JLabel statusDot = new JLabel("●");
    private final JLabel backendLabel = new JLabel("—");
    private final JLabel phaseLabel = new JLabel("—");
    private final JLabel lastSeenLabel = new JLabel("—");
    private final JComboBox<String> queueCombo = new JComboBox<>();
    private final DefaultTableModel activityModel = new DefaultTableModel(
            new Object[] {"Hora", "Rol", "Cola", "Estado", "Error"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };

    private AgentDashboard(StatusHub hub, CredentialStore store) {
        super("Ember Agent");
        this.hub = hub;
        this.store = store;

        getContentPane().setLayout(new BorderLayout(0, 10));
        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        getContentPane().add(buildHeader(), BorderLayout.NORTH);
        getContentPane().add(buildCenter(), BorderLayout.CENTER);
        getContentPane().add(buildFooter(), BorderLayout.SOUTH);

        refreshQueues();

        hub.addListener(snapshot -> SwingUtilities.invokeLater(() -> render(snapshot)));

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                setVisible(false); // minimize to tray, never exit
            }
        });
        setSize(560, 460);
        setLocationRelativeTo(null);
    }

    public static void launch(StatusHub hub, CredentialStore store, AgentRunner runner, boolean startInTray) {
        SwingUtilities.invokeLater(() -> {
            AgentDashboard frame = new AgentDashboard(hub, store);
            AgentTrayIcon.install(frame, hub);
            frame.setVisible(!startInTray);

            if (store.load().isEmpty()) {
                PairDialog.show(frame, store).ifPresent(c -> runner.requestReconnect());
                frame.setVisible(!startInTray);
            }
        });
    }

    private JPanel buildHeader() {
        JLabel title = new JLabel("Ember Agent");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        JLabel version = new JLabel("v" + DiagnosticsReport.version());
        version.setForeground(Color.GRAY);
        statusDot.setFont(statusDot.getFont().deriveFont(18f));
        statusDot.setForeground(Color.GRAY);

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        left.add(statusDot);
        left.add(title);
        left.add(version);
        JPanel header = new JPanel(new BorderLayout());
        header.add(left, BorderLayout.WEST);
        return header;
    }

    private JPanel buildCenter() {
        JPanel center = new JPanel();
        center.setLayout(new javax.swing.BoxLayout(center, javax.swing.BoxLayout.Y_AXIS));
        center.add(section("Conexión", connectionPanel()));
        center.add(javax.swing.Box.createVerticalStrut(8));
        center.add(section("Impresora (local, sólo prueba)", printerPanel()));
        center.add(javax.swing.Box.createVerticalStrut(8));
        center.add(section("Actividad", activityPanel()));
        return center;
    }

    private JPanel connectionPanel() {
        JPanel p = new JPanel(new GridLayout(3, 2, 8, 2));
        p.add(new JLabel("Servidor:"));
        p.add(backendLabel);
        p.add(new JLabel("Estado:"));
        p.add(phaseLabel);
        p.add(new JLabel("Última vez visto:"));
        p.add(lastSeenLabel);
        return p;
    }

    private JPanel printerPanel() {
        JButton refresh = new JButton("Actualizar");
        refresh.addActionListener(e -> refreshQueues());
        JButton testPrint = new JButton("Imprimir página de prueba");
        testPrint.addActionListener(e -> onTestPrint());

        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.add(queueCombo, BorderLayout.CENTER);
        row.add(refresh, BorderLayout.EAST);
        JPanel p = new JPanel(new BorderLayout(0, 6));
        p.add(row, BorderLayout.NORTH);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        actions.add(testPrint);
        p.add(actions, BorderLayout.CENTER);
        return p;
    }

    private JPanel activityPanel() {
        JTable table = new JTable(activityModel);
        table.setFillsViewportHeight(true);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(520, 160));
        JPanel p = new JPanel(new BorderLayout());
        p.add(scroll, BorderLayout.CENTER);
        return p;
    }

    private JPanel buildFooter() {
        JButton logs = new JButton("Abrir carpeta de logs");
        logs.addActionListener(e -> openLogs());
        JButton diag = new JButton("Copiar diagnóstico");
        diag.addActionListener(e -> copyDiagnostics());
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT));
        p.add(logs);
        p.add(diag);
        return p;
    }

    private static JPanel section(String title, JPanel content) {
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBorder(BorderFactory.createTitledBorder(title));
        wrap.add(content, BorderLayout.CENTER);
        return wrap;
    }

    // --- state rendering -------------------------------------------------------

    private void render(StatusHub.Snapshot s) {
        statusDot.setForeground(dotColor(s.phase()));
        phaseLabel.setText(s.detail() == null ? s.phase().toString() : s.detail());
        lastSeenLabel.setText(s.lastSeen() == null ? "nunca" : humanizeSince(s.lastSeen()));
        if (s.agentId() != null && !s.agentId().isBlank()) {
            backendLabel.setText(backendHost());
        }

        activityModel.setRowCount(0);
        for (StatusHub.JobRecord j : s.recentJobs()) {
            activityModel.addRow(new Object[] {
                    j.at() == null ? "" : TIME.format(j.at()),
                    dash(j.role()), dash(j.queue()), dash(j.result()), dash(j.error())
            });
        }
    }

    private static Color dotColor(StatusHub.Phase phase) {
        return switch (phase) {
            case CONNECTED -> new Color(0x1f, 0x9d, 0x55);
            case CONNECTING, RETRYING -> new Color(0xd9, 0x8e, 0x04);
            case UNPAIRED -> new Color(0xb0, 0x22, 0x22);
        };
    }

    private String backendHost() {
        return store.load()
                .map(c -> {
                    try {
                        String h = java.net.URI.create(c.backendBaseUrl()).getHost();
                        return h != null ? h : c.backendBaseUrl();
                    } catch (RuntimeException e) {
                        return c.backendBaseUrl();
                    }
                })
                .orElse("(sin emparejar)");
    }

    private static String humanizeSince(Instant when) {
        Duration d = Duration.between(when, Instant.now());
        if (d.toMinutes() < 1) {
            return "hace un momento";
        }
        if (d.toMinutes() < 60) {
            return "hace " + d.toMinutes() + " min";
        }
        if (d.toHours() < 48) {
            return "hace " + d.toHours() + " h";
        }
        return "hace " + d.toDays() + " d";
    }

    // --- actions -------------------------------------------------------------

    private void refreshQueues() {
        List<DiscoveredPrinter> printers = enumerator.enumerate();
        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        for (DiscoveredPrinter p : printers) {
            model.addElement(p.name());
        }
        queueCombo.setModel(model);
        queueCombo.putClientProperty("printers", printers);
    }

    @SuppressWarnings("unchecked")
    private void onTestPrint() {
        Object selected = queueCombo.getSelectedItem();
        if (selected == null) {
            JOptionPane.showMessageDialog(this, "No hay ninguna cola seleccionada.",
                    "Prueba de impresión", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String queue = selected.toString();
        boolean inkjet = false;
        Object cached = queueCombo.getClientProperty("printers");
        if (cached instanceof List<?> list) {
            inkjet = ((List<DiscoveredPrinter>) list).stream()
                    .anyMatch(p -> p.name().equals(queue) && p.inkjetGuess());
        }
        String renderMode = inkjet ? "DRIVER" : "RAW";
        PrinterConfigClient.PrinterConfigDto dto = new PrinterConfigClient.PrinterConfigDto(
                "test", "test", "KITCHEN", "WINDOWS_QUEUE",
                null, null, null, queue, renderMode, queue, true);
        String ticket = "*** Ember Agent ***\nPrueba de impresión\n" + Instant.now() + "\n";
        new Thread(() -> {
            try {
                new WindowsPrintQueueSender().print(dto, ticket);
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                        "Enviado a '" + queue + "' (" + renderMode + ").",
                        "Prueba de impresión", JOptionPane.INFORMATION_MESSAGE));
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                        "Falló: " + ex.getMessage(),
                        "Prueba de impresión", JOptionPane.ERROR_MESSAGE));
            }
        }, "ember-agent-testprint").start();
    }

    private void openLogs() {
        try {
            Desktop.getDesktop().open(AgentPaths.logsDir().toFile());
        } catch (Exception e) {
            log.log(Level.WARNING, "No se pudo abrir la carpeta de logs: " + e.getMessage());
        }
    }

    private void copyDiagnostics() {
        String text = DiagnosticsReport.build(hub.snapshot(), store);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
        JOptionPane.showMessageDialog(this, "Diagnóstico copiado al portapapeles.",
                "Diagnóstico", JOptionPane.INFORMATION_MESSAGE);
    }

    private static String dash(String s) {
        return s == null || s.isBlank() ? "—" : s;
    }
}
