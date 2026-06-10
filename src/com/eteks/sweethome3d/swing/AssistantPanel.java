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
import javax.swing.JCheckBox;
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
import com.eteks.sweethome3d.viewcontroller.AssistantCommand;
import com.eteks.sweethome3d.viewcontroller.AssistantCommandExecutor;
import com.eteks.sweethome3d.viewcontroller.AssistantCommandParser;
import com.eteks.sweethome3d.viewcontroller.HomeAssistantContext;
import com.eteks.sweethome3d.viewcontroller.HomeController;

/**
 * A simple chat panel for the AI design assistant. It sends the user's questions
 * together with a plain-text description of the current home to the language
 * model provider configured in preferences (Anthropic or any OpenAI-compatible
 * endpoint such as OpenRouter, DeepSeek, LM Studio or Ollama) and shows the
 * reply.
 * @author Sweet Home 3D
 */
public class AssistantPanel extends JPanel {
  private static final String ROLE_PROMPT =
      "You are a helpful interior design assistant embedded in Sweet Home 3D. "
      + "Answer the user's questions about their home design project clearly and concisely. ";

  /** Maximum number of model round-trips for one user request, to bound the agentic loop. */
  private static final int MAX_ASSISTANT_STEPS = 5;

  private final Home                       home;
  private final UserPreferences            preferences;
  private final AssistantCommandExecutor   commandExecutor;
  private final List<ChatMessage>          conversation = AssistantClient.newConversation();
  private final JTextArea                  transcriptArea;
  private final JTextField                 inputField;
  private final JButton                    sendButton;
  private final JCheckBox                  previewEditsCheckBox;
  /** Offset where the current assistant response starts, for streaming updates; -1 if none. */
  private int                              responseStartOffset = -1;

  public AssistantPanel(Home home, UserPreferences preferences, HomeController homeController) {
    super(new BorderLayout(0, 8));
    this.home = home;
    this.preferences = preferences;
    this.commandExecutor = homeController != null
        ? new AssistantCommandExecutor(home, preferences, homeController) : null;
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

    this.previewEditsCheckBox = new JCheckBox("Preview edits before applying");
    this.previewEditsCheckBox.setEnabled(this.commandExecutor != null);
    JPanel southPanel = new JPanel(new BorderLayout(0, 4));
    southPanel.add(this.previewEditsCheckBox, BorderLayout.NORTH);
    southPanel.add(inputPanel, BorderLayout.CENTER);
    add(southPanel, BorderLayout.SOUTH);

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

    final AssistantClient client = createClient();
    new Thread("Sweet Home 3D assistant") {
        @Override
        public void run() {
          try {
            runConversation(client);
          } finally {
            EventQueue.invokeLater(new Runnable() {
                public void run() {
                  setBusy(false);
                  inputField.requestFocusInWindow();
                }
              });
          }
        }
      }.start();
  }

  /**
   * Runs the assistant turn on the calling (worker) thread, looping while the
   * model keeps issuing edit commands so it can carry out a multi-step request.
   * After each step the applied changes are fed back with a refreshed project
   * brief, up to {@link #MAX_ASSISTANT_STEPS} steps. Network calls happen here,
   * off the event dispatch thread; reading and editing the home is marshalled
   * back onto it.
   */
  private void runConversation(AssistantClient client) {
    for (int step = 1; step <= MAX_ASSISTANT_STEPS; step++) {
      final String systemPrompt = buildSystemPrompt();
      runOnDispatchThread(new Runnable() {
          public void run() {
            beginResponse();
          }
        });
      String reply;
      try {
        reply = client.sendMessageStreaming(systemPrompt, this.conversation,
            new AssistantClient.StreamHandler() {
              public void onText(final String chunk) {
                EventQueue.invokeLater(new Runnable() {
                    public void run() {
                      appendChunk(chunk);
                    }
                  });
              }
            });
      } catch (final Exception ex) {
        final String message = "Couldn't reach the assistant: " + ex.getMessage()
            + "\n(Check the provider settings.)";
        runOnDispatchThread(new Runnable() {
            public void run() {
              finalizeResponse(message);
            }
          });
        return;
      }
      final String rawReply = reply;
      this.conversation.add(new ChatMessage("assistant", rawReply));
      final TurnResult result = new TurnResult();
      runOnDispatchThread(new Runnable() {
          public void run() {
            applyTurn(rawReply, result);
          }
        });
      // Stop once the model answers with no further edits, or nothing was applied
      // (including a previewed edit the user declined)
      if (!result.hadCommands || result.summary == null) {
        return;
      }
      if (step < MAX_ASSISTANT_STEPS) {
        this.conversation.add(new ChatMessage("user",
            "Applied: " + result.summary
            + "\nIf the request is now fully complete, reply with a brief confirmation and no "
            + "commands; otherwise issue the next commands. The refreshed project is in the system message."));
      } else {
        runOnDispatchThread(new Runnable() {
            public void run() {
              appendLine("[Reached the " + MAX_ASSISTANT_STEPS + "-step limit; stopping. "
                  + "Ask again to continue.]");
            }
          });
      }
    }
  }

  /**
   * Parses one model reply (already streamed into the transcript), and applies
   * any edit commands, optionally after a confirmation. Must run on the event
   * dispatch thread. Fills <code>result</code> with what happened.
   */
  private void applyTurn(String rawReply, TurnResult result) {
    AssistantCommandParser parsed = AssistantCommandParser.parse(rawReply);
    String displayText = parsed.getReply() != null && parsed.getReply().length() > 0
        ? parsed.getReply() : rawReply;
    boolean hasCommands = this.commandExecutor != null && !parsed.getCommands().isEmpty();
    String footer = null;
    if (hasCommands) {
      if (this.previewEditsCheckBox.isSelected() && !confirmEdits(parsed.getCommands())) {
        footer = "Edit cancelled.";
        // Leave result.hadCommands false so the multi-step loop stops
      } else {
        result.hadCommands = true;
        result.summary = this.commandExecutor.execute(parsed.getCommands());
        footer = result.summary;
      }
    }
    finalizeResponse(displayText + (footer != null ? "\n[" + footer + "]" : ""));
  }

  /**
   * Asks the user to confirm the edits the assistant wants to make, returning
   * <code>true</code> to proceed.
   */
  private boolean confirmEdits(List<AssistantCommand> commands) {
    StringBuilder message = new StringBuilder("Apply ")
        .append(commands.size()).append(commands.size() == 1 ? " change?" : " changes?").append('\n');
    for (AssistantCommand command : commands) {
      message.append("\n  - ").append(command.getAction().replace('_', ' '));
      String name = command.getString("name");
      String id = command.getString("id");
      if (id != null) {
        message.append(" ").append(id);
      } else if (name != null) {
        message.append(" \"").append(name).append('"');
      }
    }
    return JOptionPane.showConfirmDialog(SwingUtilities.getWindowAncestor(this),
        message.toString(), "Apply assistant edits?",
        JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.OK_OPTION;
  }

  /**
   * Builds the system prompt (role, command protocol and current project brief).
   * Reads the home, so it is evaluated on the event dispatch thread.
   */
  private String buildSystemPrompt() {
    final StringBuilder builder = new StringBuilder(ROLE_PROMPT);
    runOnDispatchThread(new Runnable() {
        public void run() {
          if (commandExecutor != null) {
            builder.append(HomeAssistantContext.getCommandProtocol());
          }
          builder.append("\n\nCurrent project:\n").append(HomeAssistantContext.describeHome(home));
        }
      });
    return builder.toString();
  }

  /**
   * Runs <code>runnable</code> on the event dispatch thread and waits for it,
   * whether called from the EDT or a worker thread.
   */
  private static void runOnDispatchThread(Runnable runnable) {
    if (EventQueue.isDispatchThread()) {
      runnable.run();
    } else {
      try {
        EventQueue.invokeAndWait(runnable);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
      } catch (java.lang.reflect.InvocationTargetException ex) {
        throw new RuntimeException(ex.getCause());
      }
    }
  }

  /**
   * Outcome of applying one model reply, shared between the worker and the EDT.
   */
  private static final class TurnResult {
    private boolean hadCommands;
    private String  summary;
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

  /**
   * Starts a fresh assistant response region, remembering where it begins so the
   * streamed text can be replaced with the finalized reply.
   */
  private void beginResponse() {
    this.transcriptArea.append("\n");
    this.responseStartOffset = this.transcriptArea.getDocument().getLength();
    this.transcriptArea.append("Assistant: ");
    this.transcriptArea.setCaretPosition(this.transcriptArea.getDocument().getLength());
  }

  /**
   * Appends a streamed chunk to the current assistant response.
   */
  private void appendChunk(String chunk) {
    this.transcriptArea.append(chunk);
    this.transcriptArea.setCaretPosition(this.transcriptArea.getDocument().getLength());
  }

  /**
   * Replaces the streamed assistant response with its finalized text (the parsed
   * reply plus any applied-changes footer).
   */
  private void finalizeResponse(String text) {
    int end = this.transcriptArea.getDocument().getLength();
    if (this.responseStartOffset >= 0 && this.responseStartOffset <= end) {
      this.transcriptArea.replaceRange("Assistant: " + text, this.responseStartOffset, end);
    } else {
      appendLine("Assistant: " + text);
    }
    this.responseStartOffset = -1;
    this.transcriptArea.append("\n");
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
