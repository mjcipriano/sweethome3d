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
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JFileChooser;
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
  /** Client of the request in progress, or <code>null</code>; used to cancel it. */
  private volatile AssistantClient         currentClient;
  private volatile boolean                 cancelled;
  private boolean                          busy;
  /** Picture to send with the next message, shrunk for the model, and its file. */
  private byte []                          pendingImage;
  private File                             pendingImageFile;
  private final JButton                    attachButton;
  /** Role, command protocol and catalog part of the system prompt of the running request. */
  private String                           systemPromptPrefix;

  /** Environment variable giving the API key when none is stored in preferences. */
  public static final String API_KEY_ENVIRONMENT_VARIABLE = "SWEETHOME3D_ASSISTANT_API_KEY";

  public AssistantPanel(Home home, UserPreferences preferences, HomeController homeController) {
    super(new BorderLayout(0, 8));
    this.home = home;
    this.preferences = preferences;
    this.commandExecutor = homeController != null
        ? new AssistantCommandExecutor(home, preferences, homeController) : null;
    if (this.commandExecutor != null) {
      this.commandExecutor.setModelSizeProvider(new AssistantModelSizeProvider());
    }
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
    this.attachButton = new JButton("Picture...");
    this.attachButton.setToolTipText("Attach a picture (a photo of furniture, a texture, a plan) to your next "
        + "message; the model must be able to read images");
    JPanel buttons = new JPanel(new BorderLayout(6, 0));
    buttons.add(this.attachButton, BorderLayout.WEST);
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
    // The Send button stops the request in progress while one is running
    this.sendButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent ev) {
          if (busy) {
            cancel();
          } else {
            send();
          }
        }
      });
    this.attachButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent ev) {
          attachPicture();
        }
      });
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
    if (this.pendingImage != null) {
      appendLine("You: " + question + "  [picture: " + this.pendingImageFile.getName() + "]");
      // Commands designate the last picture with "image":"attached"
      this.conversation.add(new ChatMessage("user", question
          + "\n(A picture is attached; commands can use it with \"image\":\"attached\".)",
          this.pendingImage, "image/jpeg"));
      if (this.commandExecutor != null) {
        this.commandExecutor.setAttachedImage(this.pendingImageFile);
      }
      this.pendingImage = null;
      this.attachButton.setText("Picture...");
    } else {
      appendLine("You: " + question);
      this.conversation.add(new ChatMessage("user", question));
    }
    setBusy(true);

    final AssistantClient client = createClient();
    this.systemPromptPrefix = null;
    this.cancelled = false;
    this.currentClient = client;
    Thread thread = new Thread("Sweet Home 3D assistant") {
        @Override
        public void run() {
          try {
            runConversation(client);
          } catch (final RuntimeException ex) {
            EventQueue.invokeLater(new Runnable() {
                public void run() {
                  finalizeResponse("Couldn't apply the reply: " + ex);
                }
              });
          } finally {
            currentClient = null;
            EventQueue.invokeLater(new Runnable() {
                public void run() {
                  setBusy(false);
                  inputField.requestFocusInWindow();
                }
              });
          }
        }
      };
    thread.setDaemon(true);
    thread.start();
  }

  /**
   * Lets the user choose the picture sent with the next message. The model
   * gets a copy shrunk to 1024 pixels; commands use the original file.
   */
  private void attachPicture() {
    JFileChooser fileChooser = new JFileChooser();
    fileChooser.setDialogTitle("Attach a picture");
    fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
        "Pictures (PNG, JPEG, GIF)", "png", "jpg", "jpeg", "gif"));
    if (fileChooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
      return;
    }
    File file = fileChooser.getSelectedFile();
    try {
      BufferedImage image = ImageIO.read(file);
      if (image == null) {
        throw new IOException("not a picture");
      }
      float scale = Math.min(1f, 1024f / Math.max(image.getWidth(), image.getHeight()));
      int width = Math.max(1, Math.round(image.getWidth() * scale));
      int height = Math.max(1, Math.round(image.getHeight() * scale));
      BufferedImage sentImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
      Graphics2D g2D = sentImage.createGraphics();
      g2D.setColor(Color.WHITE);
      g2D.fillRect(0, 0, width, height);
      g2D.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      g2D.drawImage(image, 0, 0, width, height, null);
      g2D.dispose();
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      ImageIO.write(sentImage, "jpg", out);
      this.pendingImage = out.toByteArray();
      this.pendingImageFile = file;
      this.attachButton.setText("Picture \u2713");
      appendLine("[" + file.getName() + " will be sent with your next message]");
    } catch (IOException ex) {
      JOptionPane.showMessageDialog(SwingUtilities.getWindowAncestor(this),
          "Couldn't read " + file.getName() + ": " + ex.getMessage(),
          "Attach a picture", JOptionPane.ERROR_MESSAGE);
    }
  }

  /**
   * Cancels the request in progress, if any: its reply is dropped and no further
   * edit is applied to the home. Called when the assistant window is closed.
   */
  public void cancel() {
    this.cancelled = true;
    AssistantClient client = this.currentClient;
    if (client != null) {
      client.abort();
    }
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
      if (this.cancelled) {
        return;
      }
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
        // Drop the unanswered question so the next request doesn't send two
        // consecutive user messages
        if (!this.conversation.isEmpty()
            && "user".equals(this.conversation.get(this.conversation.size() - 1).getRole())) {
          this.conversation.remove(this.conversation.size() - 1);
        }
        final String message = this.cancelled
            ? "Cancelled."
            : "Couldn't reach the assistant: " + ex.getMessage()
              + "\n(Check the provider settings.)";
        runOnDispatchThread(new Runnable() {
            public void run() {
              finalizeResponse(message);
            }
          });
        return;
      }
      if (this.cancelled) {
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
      if (result.declined) {
        // Tell the model so the history doesn't imply the edits were applied
        this.conversation.add(new ChatMessage("user",
            "The user declined those edits; nothing was applied."));
        this.conversation.add(new ChatMessage("assistant",
            "{\"reply\":\"Understood, nothing was changed.\",\"commands\":[]}"));
        return;
      }
      // Stop once the model answers with no further edits, or nothing was applied
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
    AssistantCommandParser parsed;
    try {
      parsed = AssistantCommandParser.parse(rawReply);
    } catch (RuntimeException ex) {
      finalizeResponse(rawReply);
      return;
    }
    String displayText = parsed.getReply() != null && parsed.getReply().length() > 0
        ? parsed.getReply() : rawReply;
    boolean hasCommands = this.commandExecutor != null && !parsed.getCommands().isEmpty();
    String footer = null;
    if (hasCommands) {
      if (this.previewEditsCheckBox.isSelected() && !confirmEdits(parsed.getCommands())) {
        footer = "Edit cancelled.";
        // Leave result.hadCommands false so the multi-step loop stops
        result.declined = true;
      } else {
        result.hadCommands = true;
        try {
          result.summary = this.commandExecutor.execute(parsed.getCommands());
          footer = result.summary;
        } catch (RuntimeException ex) {
          footer = "Couldn't apply the edits: " + ex;
        }
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
   * Builds the system prompt (role, command protocol, catalog vocabulary and
   * current project brief). Reads the home, so it is evaluated on the event
   * dispatch thread.
   */
  private String buildSystemPrompt() {
    final StringBuilder builder = new StringBuilder();
    runOnDispatchThread(new Runnable() {
        public void run() {
          // The role, protocol and catalog don't change during a request: build
          // them once, and only refresh the project brief at each step
          if (systemPromptPrefix == null) {
            StringBuilder prefix = new StringBuilder(HomeAssistantContext.ROLE_PROMPT);
            if (commandExecutor != null) {
              prefix.append(HomeAssistantContext.getCommandProtocol());
              String catalog = HomeAssistantContext.describeCatalog(preferences);
              if (catalog.length() > 0) {
                prefix.append("\n\n").append(catalog);
              }
            }
            systemPromptPrefix = prefix.toString();
          }
          builder.append(systemPromptPrefix)
              .append("\n\nCurrent project:\n").append(HomeAssistantContext.describeHome(home));
          if (commandExecutor != null) {
            // Ids of the reply must designate the items described now, even if
            // the user edits the home while the request is in flight
            commandExecutor.snapshotDescribedItems();
          }
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
    private boolean declined;
    private String  summary;
  }

  private AssistantClient createClient() {
    Provider provider = "anthropic".equalsIgnoreCase(this.preferences.getAssistantProvider())
        ? Provider.ANTHROPIC : Provider.OPENAI_COMPATIBLE;
    String apiKey = this.preferences.getAssistantApiKey();
    if (apiKey == null || apiKey.trim().length() == 0) {
      // A key given by the environment is never written to the preferences file
      try {
        apiKey = System.getenv(API_KEY_ENVIRONMENT_VARIABLE);
      } catch (SecurityException ex) {
        apiKey = null;
      }
    }
    return new AssistantClient(provider, this.preferences.getAssistantApiUrl(),
        apiKey, this.preferences.getAssistantModel());
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
    this.busy = busy;
    this.sendButton.setText(busy ? "Stop" : "Send");
    this.inputField.setEnabled(!busy);
  }

  private void showSettings() {
    final JComboBox providerComboBox = new JComboBox(new String [] {
        "OpenAI-compatible (OpenRouter, DeepSeek, LM Studio, Ollama)", "Anthropic"});
    providerComboBox.setSelectedIndex(
        "anthropic".equalsIgnoreCase(this.preferences.getAssistantProvider()) ? 1 : 0);
    final JTextField urlField = new JTextField(this.preferences.getAssistantApiUrl(), 28);
    final JPasswordField keyField = new JPasswordField(this.preferences.getAssistantApiKey(), 28);
    // A key belongs to the service it was entered for: clear it as soon as the
    // provider or the API URL changes, so it isn't sent to another service
    final String storedKey = this.preferences.getAssistantApiKey();
    final String storedUrl = this.preferences.getAssistantApiUrl();
    final int storedProviderIndex = providerComboBox.getSelectedIndex();
    final Runnable keyUpdater = new Runnable() {
        private boolean storedService = true;

        public void run() {
          boolean service = providerComboBox.getSelectedIndex() == storedProviderIndex
              && urlField.getText().trim().equals(storedUrl != null ? storedUrl.trim() : "");
          if (service != this.storedService) {
            this.storedService = service;
            if (service) {
              keyField.setText(storedKey);
            } else if (new String(keyField.getPassword()).equals(storedKey)) {
              keyField.setText("");
            }
          }
        }
      };
    providerComboBox.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent ev) {
          keyUpdater.run();
        }
      });
    urlField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
        public void insertUpdate(javax.swing.event.DocumentEvent ev) {
          keyUpdater.run();
        }

        public void removeUpdate(javax.swing.event.DocumentEvent ev) {
          keyUpdater.run();
        }

        public void changedUpdate(javax.swing.event.DocumentEvent ev) {
          keyUpdater.run();
        }
      });
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
