/*
 * AssistantPanel.java 10 June 2026
 *
 * Sweet Home 3D, Copyright (c) 2026 Space Mushrooms <info@sweethome3d.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package com.eteks.sweethome3d.swing;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.viewcontroller.AssistantClient;
import com.eteks.sweethome3d.viewcontroller.AssistantClient.ChatMessage;
import com.eteks.sweethome3d.viewcontroller.AssistantClient.Provider;
import com.eteks.sweethome3d.viewcontroller.HomeAssistantContext;

/**
 * A simple chat panel for the AI design assistant. It sends the user's questions
 * together with a plain-text description of the current home to the language
 * model provider configured in preferences (Anthropic or any OpenAI-compatible
 * endpoint such as OpenRouter, DeepSeek, LM Studio or Ollama) and shows the
 * reply.
 * @author Sweet Home 3D
 */
public class AssistantPanel extends JPanel {
  private static final String SYSTEM_PROMPT =
      "You are a helpful interior design assistant embedded in Sweet Home 3D. "
      + "Answer the user's questions about their home design project clearly and "
      + "concisely. When useful, refer to the rooms, walls and furniture below. "
      + "Here is the current project:\n\n";

  private final Home                home;
  private final UserPreferences     preferences;
  private final List<ChatMessage>   conversation = AssistantClient.newConversation();
  private final JTextArea           transcriptArea;
  private final JTextField          inputField;
  private final JButton             sendButton;

  public AssistantPanel(Home home, UserPreferences preferences) {
    super(new BorderLayout(0, 8));
    this.home = home;
    this.preferences = preferences;
    setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

    this.transcriptArea = new JTextArea(18, 50);
    this.transcriptArea.setEditable(false);
    this.transcriptArea.setLineWrap(true);
    this.transcriptArea.setWrapStyleWord(true);
    this.transcriptArea.setText("Ask the design assistant about your home - for example "
        + "\"What is the total floor area?\" or \"Which models are heaviest to reduce?\".\n");
    add(new JScrollPane(this.transcriptArea), BorderLayout.CENTER);

    this.inputField = new JTextField();
    this.sendButton = new JButton("Send");
    JButton settingsButton = new JButton("Settings...");
    JPanel inputPanel = new JPanel(new BorderLayout(6, 0));
    inputPanel.add(this.inputField, BorderLayout.CENTER);
    JPanel buttons = new JPanel(new BorderLayout(6, 0));
    buttons.add(this.sendButton, BorderLayout.CENTER);
    buttons.add(settingsButton, BorderLayout.EAST);
    inputPanel.add(buttons, BorderLayout.EAST);
    add(inputPanel, BorderLayout.SOUTH);

    ActionListener sendListener = new ActionListener() {
        public void actionPerformed(ActionEvent ev) {
          send();
        }
      };
    this.inputField.addActionListener(sendListener);
    this.sendButton.addActionListener(sendListener);
    settingsButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent ev) {
          showSettings();
        }
      });
  }

  /**
   * Returns the preferred size of this panel.
   */
  @Override
  public Dimension getPreferredSize() {
    Dimension size = super.getPreferredSize();
    size.width = Math.max(size.width, 480);
    return size;
  }

  private void send() {
    final String question = this.inputField.getText().trim();
    if (question.length() == 0) {
      return;
    }
    this.inputField.setText("");
    appendLine("You: " + question);
    this.conversation.add(new ChatMessage("user", question));
    setBusy(true);
    appendLine("Assistant is thinking...");

    final String systemPrompt = SYSTEM_PROMPT + HomeAssistantContext.describeHome(this.home);
    final AssistantClient client = createClient();
    new Thread("Sweet Home 3D assistant") {
        @Override
        public void run() {
          String reply;
          boolean error = false;
          try {
            reply = client.sendMessage(systemPrompt, conversation);
          } catch (final Exception ex) {
            reply = "Couldn't reach the assistant: " + ex.getMessage()
                + "\n(Check the provider settings.)";
            error = true;
          }
          final String replyText = reply;
          final boolean failed = error;
          EventQueue.invokeLater(new Runnable() {
              public void run() {
                replaceThinkingLine(failed ? replyText : "Assistant: " + replyText);
                if (!failed) {
                  conversation.add(new ChatMessage("assistant", replyText));
                }
                setBusy(false);
                inputField.requestFocusInWindow();
              }
            });
        }
      }.start();
  }

  private AssistantClient createClient() {
    Provider provider = "anthropic".equalsIgnoreCase(this.preferences.getAssistantProvider())
        ? Provider.ANTHROPIC : Provider.OPENAI_COMPATIBLE;
    return new AssistantClient(provider, this.preferences.getAssistantApiUrl(),
        this.preferences.getAssistantApiKey(), this.preferences.getAssistantModel());
  }

  private void appendLine(String line) {
    this.transcriptArea.append("\n" + line + "\n");
    this.transcriptArea.setCaretPosition(this.transcriptArea.getDocument().getLength());
  }

  private void replaceThinkingLine(String line) {
    String text = this.transcriptArea.getText();
    int index = text.lastIndexOf("Assistant is thinking...");
    if (index >= 0) {
      this.transcriptArea.replaceRange(line, index, index + "Assistant is thinking...".length());
    } else {
      appendLine(line);
    }
    this.transcriptArea.setCaretPosition(this.transcriptArea.getDocument().getLength());
  }

  private void setBusy(boolean busy) {
    this.sendButton.setEnabled(!busy);
    this.inputField.setEnabled(!busy);
  }

  private void showSettings() {
    final JComboBox providerComboBox = new JComboBox(new String [] {
        "OpenAI-compatible (OpenRouter, DeepSeek, LM Studio, Ollama)", "Anthropic"});
    providerComboBox.setSelectedIndex(
        "anthropic".equalsIgnoreCase(this.preferences.getAssistantProvider()) ? 1 : 0);
    JTextField urlField = new JTextField(this.preferences.getAssistantApiUrl(), 28);
    JPasswordField keyField = new JPasswordField(this.preferences.getAssistantApiKey(), 28);
    JTextField modelField = new JTextField(this.preferences.getAssistantModel(), 28);

    JPanel panel = new JPanel(new GridBagLayout());
    Insets labelInsets = new Insets(2, 0, 2, 8);
    Insets fieldInsets = new Insets(2, 0, 2, 0);
    panel.add(new JLabel("Provider:"), new GridBagConstraints(
        0, 0, 1, 1, 0, 0, GridBagConstraints.LINE_END, GridBagConstraints.NONE, labelInsets, 0, 0));
    panel.add(providerComboBox, new GridBagConstraints(
        1, 0, 1, 1, 1, 0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL, fieldInsets, 0, 0));
    panel.add(new JLabel("API URL:"), new GridBagConstraints(
        0, 1, 1, 1, 0, 0, GridBagConstraints.LINE_END, GridBagConstraints.NONE, labelInsets, 0, 0));
    panel.add(urlField, new GridBagConstraints(
        1, 1, 1, 1, 1, 0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL, fieldInsets, 0, 0));
    panel.add(new JLabel("API key (optional):"), new GridBagConstraints(
        0, 2, 1, 1, 0, 0, GridBagConstraints.LINE_END, GridBagConstraints.NONE, labelInsets, 0, 0));
    panel.add(keyField, new GridBagConstraints(
        1, 2, 1, 1, 1, 0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL, fieldInsets, 0, 0));
    panel.add(new JLabel("Model:"), new GridBagConstraints(
        0, 3, 1, 1, 0, 0, GridBagConstraints.LINE_END, GridBagConstraints.NONE, labelInsets, 0, 0));
    panel.add(modelField, new GridBagConstraints(
        1, 3, 1, 1, 1, 0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL, fieldInsets, 0, 0));

    if (JOptionPane.showConfirmDialog(SwingUtilities.getWindowAncestor(this), panel,
            "Assistant settings", JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
      this.preferences.setAssistantProvider(providerComboBox.getSelectedIndex() == 1 ? "anthropic" : "openai");
      this.preferences.setAssistantApiUrl(urlField.getText());
      this.preferences.setAssistantApiKey(new String(keyField.getPassword()));
      this.preferences.setAssistantModel(modelField.getText());
      try {
        this.preferences.write();
      } catch (Exception ex) {
        // Settings will still apply for this session even if they can't be saved
      }
    }
  }
}
