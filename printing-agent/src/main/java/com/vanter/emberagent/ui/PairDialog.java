package com.vanter.emberagent.ui;

import com.vanter.emberagent.AgentCredential;
import com.vanter.emberagent.PairingClient;
import com.vanter.emberagent.PairingException;
import com.vanter.emberagent.credential.CredentialStore;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Window;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Modal "Emparejar este agente" dialog (spec §2.2). Normal path: type the 10-char code shown in
 * the admin, hit "Emparejar" → {@link PairingClient#redeem} exchanges it for the API key and saves
 * it. "Opciones avanzadas" reveals the backend URL (pre-filled for prod). "Tengo una API key"
 * swaps to a URL + key form that writes the credential directly, for support / manual installs.
 */
public final class PairDialog {

    private static final String DEFAULT_BACKEND = "https://api.ember.vanter.net/v1";

    private PairDialog() {}

    public static Optional<AgentCredential> show(Window owner, CredentialStore store) {
        JDialog dialog = new JDialog(owner, "Emparejar este agente", JDialog.ModalityType.APPLICATION_MODAL);
        Holder result = new Holder();

        JTextField codeField = new JTextField(12);
        upperCaseFilter(codeField, 10);
        JTextField urlField = new JTextField(DEFAULT_BACKEND, 26);
        JTextField keyField = new JTextField(26);

        JLabel message = new JLabel(" ");
        message.setForeground(new java.awt.Color(0xB0, 0x22, 0x22));

        JButton advancedToggle = linkButton("Opciones avanzadas");
        JPanel urlRow = labeledRow("Servidor:", urlField);
        urlRow.setVisible(false);
        advancedToggle.addActionListener(e -> urlRow.setVisible(!urlRow.isVisible()));

        JButton pairButton = new JButton("Emparejar");
        JButton apiKeyToggle = linkButton("Tengo una API key");

        JPanel codeCard = new JPanel();
        codeCard.setLayout(new BoxLayout(codeCard, BoxLayout.Y_AXIS));
        codeCard.add(labeledRow("Código:", codeField));
        codeCard.add(Box.createVerticalStrut(4));
        codeCard.add(leftFlow(advancedToggle));
        codeCard.add(urlRow);

        JPanel keyCard = new JPanel();
        keyCard.setLayout(new BoxLayout(keyCard, BoxLayout.Y_AXIS));
        keyCard.add(labeledRow("Servidor:", new JTextField(DEFAULT_BACKEND, 26)));
        keyCard.add(Box.createVerticalStrut(4));
        keyCard.add(labeledRow("API key:", keyField));
        keyCard.setVisible(false);

        // keyCard reuses its own url field; grab it back for the save handler
        JTextField keyUrlField = (JTextField) ((JPanel) keyCard.getComponent(0)).getComponent(1);

        apiKeyToggle.addActionListener(e -> {
            boolean toKey = !keyCard.isVisible();
            keyCard.setVisible(toKey);
            codeCard.setVisible(!toKey);
            pairButton.setText(toKey ? "Guardar" : "Emparejar");
            apiKeyToggle.setText(toKey ? "Usar un código" : "Tengo una API key");
            dialog.pack();
        });

        pairButton.addActionListener(e -> {
            pairButton.setEnabled(false);
            message.setText("Procesando…");
            final boolean keyMode = keyCard.isVisible();
            final String code = codeField.getText().trim();
            final String url = (keyMode ? keyUrlField : urlField).getText().trim();
            final String key = keyField.getText().trim();
            new Thread(() -> {
                try {
                    AgentCredential credential;
                    if (keyMode) {
                        if (key.isEmpty() || url.isEmpty()) {
                            throw new PairingException("Servidor y API key son obligatorios.");
                        }
                        credential = new AgentCredential(key, url);
                        store.save(credential);
                    } else {
                        credential = new PairingClient(store).redeem(url, code);
                    }
                    result.value = credential;
                    SwingUtilities.invokeLater(dialog::dispose);
                } catch (PairingException ex) {
                    SwingUtilities.invokeLater(() -> {
                        message.setText(ex.getMessage());
                        pairButton.setEnabled(true);
                    });
                }
            }, "ember-agent-pair").start();
        });

        JPanel body = new JPanel();
        body.setBorder(BorderFactory.createEmptyBorder(14, 16, 12, 16));
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.add(codeCard);
        body.add(keyCard);
        body.add(Box.createVerticalStrut(8));
        body.add(leftFlow(apiKeyToggle));
        body.add(Box.createVerticalStrut(4));
        body.add(leftFlow(message));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancel = new JButton("Cancelar");
        cancel.addActionListener(e -> dialog.dispose());
        buttons.add(cancel);
        buttons.add(pairButton);

        dialog.getContentPane().setLayout(new BorderLayout());
        dialog.getContentPane().add(body, BorderLayout.CENTER);
        dialog.getContentPane().add(buttons, BorderLayout.SOUTH);
        dialog.getRootPane().setDefaultButton(pairButton);
        dialog.pack();
        dialog.setMinimumSize(new Dimension(380, dialog.getHeight()));
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true); // blocks until disposed (modal)

        return Optional.ofNullable(result.value);
    }

    private static final class Holder {
        AgentCredential value;
    }

    private static void upperCaseFilter(JTextField field, int maxLen) {
        field.getDocument().addDocumentListener(new DocumentListener() {
            private boolean guard;

            private void normalize() {
                if (guard) {
                    return;
                }
                guard = true;
                SwingUtilities.invokeLater(() -> {
                    String t = field.getText().toUpperCase().replaceAll("[^A-Z0-9]", "");
                    if (t.length() > maxLen) {
                        t = t.substring(0, maxLen);
                    }
                    if (!t.equals(field.getText())) {
                        field.setText(t);
                    }
                    guard = false;
                });
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                normalize();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                normalize();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                normalize();
            }
        });
    }

    private static JPanel labeledRow(String label, JTextField field) {
        JPanel row = new JPanel(new GridLayout(1, 2, 8, 0));
        row.add(new JLabel(label));
        row.add(field);
        return row;
    }

    private static JPanel leftFlow(java.awt.Component c) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        p.add(c);
        return p;
    }

    private static JButton linkButton(String text) {
        JButton b = new JButton(text);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setFocusPainted(false);
        b.setForeground(new java.awt.Color(0x7a, 0x13, 0x15));
        b.setFont(b.getFont().deriveFont(Font.PLAIN, 11f));
        b.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        return b;
    }
}
