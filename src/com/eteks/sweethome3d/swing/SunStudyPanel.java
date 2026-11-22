/*
 * SunStudyPanel.java
 *
 * Sweet Home 3D, Copyright (c) 2024 Space Mushrooms <info@sweethome3d.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package com.eteks.sweethome3d.swing;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Dictionary;
import java.util.GregorianCalendar;
import java.util.Hashtable;
import java.util.Locale;
import java.util.TimeZone;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JToggleButton;
import javax.swing.SpinnerDateModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import com.eteks.sweethome3d.j3d.SunLight;
import com.eteks.sweethome3d.model.Camera;
import com.eteks.sweethome3d.model.Compass;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.SunPosition;
import com.eteks.sweethome3d.viewcontroller.HomeGeoreference;

/**
 * The window where the user chooses the place, the date and the time of the Sun which lights
 * the 3D view of a home: a calendar, a slider which goes through the days of the year, another one
 * through the hours of the day, and the false colors which show the light received by the surfaces.
 * @since 7.29
 */
public class SunStudyPanel extends JPanel {
  private static final String   DIALOG_PROPERTY = "com.eteks.sweethome3d.swing.SunStudyPanel.dialog";
  /**
   * The client property of the component given to {@link #showDialog(JComponent, Home, HomeComponent3D) showDialog}
   * which holds the {@link PictureRenderer} drawing the pictures of this panel.
   */
  public static final String    PICTURE_RENDERER_PROPERTY = "com.eteks.sweethome3d.swing.SunStudyPanel.pictureRenderer";

  /**
   * What draws the pictures of the sun through a day or a year, and of the hours of sun.
   */
  public interface PictureRenderer {
    /**
     * Returns the PNG picture of the given sweep (<code>day</code>, <code>year</code> or <code>hours</code>)
     * with its description in <code>message [0]</code>, or <code>null</code> with the reason in it.
     */
    byte [] render(String sweep, String [] message);
  }

  private static final TimeZone UTC = TimeZone.getTimeZone("UTC");
  private static final int      TIME_STEP = 5;
  private static final int []   FALSE_COLORS_MAXIMUMS = {120000, 60000, 30000, 10000, 2000};
  private static final String [] DIRECTIONS = {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};

  private final Home            home;
  private final HomeComponent3D view3D;
  private boolean               updating;

  private JSpinner              latitudeSpinner;
  private JSpinner              longitudeSpinner;
  private JSpinner              northSpinner;
  private JComboBox             timeZoneComboBox;
  private MonthCalendar         monthCalendar;
  private JSpinner              dateSpinner;
  private JSlider               daySlider;
  private JToggleButton         playYearButton;
  private JSpinner              timeSpinner;
  private JSlider               timeSlider;
  private JToggleButton         playDayButton;
  private JLabel                sunLabel;
  private JCheckBox             followCheckBox;
  private JCheckBox             shadowsCheckBox;
  private JCheckBox             falseColorsCheckBox;
  private JComboBox             falseColorsComboBox;
  private FalseColorsLegend     legend;
  private JLabel                rendererLabel;
  private JButton               rendererButton;
  private JLabel                placeNoteLabel;
  private JButton               georeferenceButton;
  private JButton []            pictureButtons;
  private PictureRenderer       pictureRenderer;
  private Timer                 timer;
  private PropertyChangeListener homeListener;

  /**
   * Shows the window of the Sun of the given home, or brings it to front if it's already displayed.
   */
  public static void showDialog(JComponent parent, Home home, HomeComponent3D view3D) {
    final javax.swing.JRootPane rootPane = SwingUtilities.getRootPane(parent);
    JDialog dialog = rootPane != null ? (JDialog)rootPane.getClientProperty(DIALOG_PROPERTY) : null;
    if (dialog != null && dialog.isDisplayable()) {
      dialog.setVisible(true);
      dialog.toFront();
      return;
    }
    final SunStudyPanel panel = new SunStudyPanel(home, view3D);
    Object pictureRenderer = parent.getClientProperty(PICTURE_RENDERER_PROPERTY);
    if (pictureRenderer instanceof PictureRenderer) {
      panel.pictureRenderer = (PictureRenderer)pictureRenderer;
    }
    panel.refresh();
    Window window = SwingUtilities.getWindowAncestor(parent);
    dialog = new JDialog(window, "Sun and shadows", JDialog.ModalityType.MODELESS);
    dialog.setContentPane(panel);
    dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
    dialog.addWindowListener(new WindowAdapter() {
        @Override
        public void windowClosed(WindowEvent ev) {
          panel.dispose();
          if (rootPane != null) {
            rootPane.putClientProperty(DIALOG_PROPERTY, null);
          }
        }
      });
    dialog.pack();
    dialog.setMinimumSize(dialog.getSize());
    if (window != null) {
      // At the right of the window, where it hides the 3D view the least
      dialog.setLocation(Math.max(0, window.getX() + window.getWidth() - dialog.getWidth() - 20),
          Math.max(0, window.getY() + 80));
    }
    if (rootPane != null) {
      rootPane.putClientProperty(DIALOG_PROPERTY, dialog);
    }
    dialog.setVisible(true);
  }

  /**
   * Returns the panel of the window of the Sun displayed for the home of <code>parent</code>,
   * or <code>null</code> if it isn't open.
   */
  public static SunStudyPanel getDisplayedPanel(JComponent parent) {
    javax.swing.JRootPane rootPane = SwingUtilities.getRootPane(parent);
    Object dialog = rootPane != null ? rootPane.getClientProperty(DIALOG_PROPERTY) : null;
    return dialog instanceof JDialog && ((JDialog)dialog).isDisplayable()
          && ((JDialog)dialog).getContentPane() instanceof SunStudyPanel
        ? (SunStudyPanel)((JDialog)dialog).getContentPane()
        : null;
  }

  /**
   * Closes the window of the Sun displayed for the home of <code>parent</code>.
   */
  public static void closeDialog(JComponent parent) {
    SunStudyPanel panel = getDisplayedPanel(parent);
    Window dialog = panel != null ? SwingUtilities.getWindowAncestor(panel) : null;
    if (dialog != null) {
      dialog.dispose();
    }
  }

  /**
   * Plays the <code>day</code> from sunrise to sunset or the days of the <code>year</code>,
   * or stops the animation if <code>animation</code> is <code>null</code>.
   */
  public void play(String animation) {
    JToggleButton button = "day".equals(animation) ? this.playDayButton
        : "year".equals(animation) ? this.playYearButton : null;
    if (button != null) {
      if (!button.isSelected()) {
        button.doClick(0);
      }
    } else if (this.playDayButton.isSelected()) {
      this.playDayButton.doClick(0);
    } else if (this.playYearButton.isSelected()) {
      this.playYearButton.doClick(0);
    }
  }

  /**
   * Returns <code>day</code> or <code>year</code> if this animation plays, otherwise <code>null</code>.
   */
  public String getPlayedAnimation() {
    return this.playDayButton.isSelected() ? "day" : this.playYearButton.isSelected() ? "year" : null;
  }

  /**
   * Updates this panel after a change of the renderer of the 3D view or of its options.
   */
  public void update() {
    refresh();
  }

  public SunStudyPanel(Home home, HomeComponent3D view3D) {
    super(new GridBagLayout());
    this.home = home;
    this.view3D = view3D;
    createComponents();
    layoutComponents();
    addModelListeners();
    refresh();
  }

  // ---- Model ----

  /**
   * Returns the date and time of the cameras of the home: a calendar in UTC
   * whose fields are the local date and time of the place.
   */
  private Calendar getCalendar() {
    Calendar calendar = new GregorianCalendar(UTC);
    calendar.setTimeInMillis(this.home.getCamera().getTime());
    return calendar;
  }

  private void setTime(long time) {
    if (time != this.home.getCamera().getTime()) {
      this.home.getTopCamera().setTime(time);
      this.home.getObserverCamera().setTime(time);
      this.home.getCamera().setTime(time);
      this.home.setModified(true);
    }
  }

  private void setDate(int year, int month, int day) {
    Calendar calendar = getCalendar();
    calendar.set(Calendar.DAY_OF_MONTH, 1);
    calendar.set(Calendar.YEAR, year);
    calendar.set(Calendar.MONTH, month);
    calendar.set(Calendar.DAY_OF_MONTH, Math.min(day, calendar.getActualMaximum(Calendar.DAY_OF_MONTH)));
    setTime(calendar.getTimeInMillis());
  }

  private void setMinutes(int minutes) {
    Calendar calendar = getCalendar();
    calendar.set(Calendar.HOUR_OF_DAY, minutes / 60);
    calendar.set(Calendar.MINUTE, minutes % 60);
    calendar.set(Calendar.SECOND, 0);
    calendar.set(Calendar.MILLISECOND, 0);
    setTime(calendar.getTimeInMillis());
  }

  private void addModelListeners() {
    this.homeListener = new PropertyChangeListener() {
        public void propertyChange(PropertyChangeEvent ev) {
          if (!(ev.getSource() instanceof Camera)
              || Camera.Property.TIME.name().equals(ev.getPropertyName())) {
            refresh();
          }
        }
      };
    this.home.getTopCamera().addPropertyChangeListener(this.homeListener);
    this.home.getObserverCamera().addPropertyChangeListener(this.homeListener);
    this.home.getCompass().addPropertyChangeListener(this.homeListener);
    this.home.addPropertyChangeListener(Home.Property.CAMERA, this.homeListener);
    this.home.addPropertyChangeListener(HomeComponent3D.SUN_FOLLOWS_TIME_PROPERTY, this.homeListener);
    this.home.addPropertyChangeListener(HomeComponent3D.SUN_FALSE_COLORS_PROPERTY, this.homeListener);
    this.home.addPropertyChangeListener(HomeGeoreference.PROPERTY, this.homeListener);
  }

  /**
   * Stops the animations and the tracking of the home.
   */
  public void dispose() {
    this.timer.stop();
    this.home.getTopCamera().removePropertyChangeListener(this.homeListener);
    this.home.getObserverCamera().removePropertyChangeListener(this.homeListener);
    this.home.getCompass().removePropertyChangeListener(this.homeListener);
    this.home.removePropertyChangeListener(Home.Property.CAMERA, this.homeListener);
    this.home.removePropertyChangeListener(HomeComponent3D.SUN_FOLLOWS_TIME_PROPERTY, this.homeListener);
    this.home.removePropertyChangeListener(HomeComponent3D.SUN_FALSE_COLORS_PROPERTY, this.homeListener);
    this.home.removePropertyChangeListener(HomeGeoreference.PROPERTY, this.homeListener);
  }

  /**
   * Draws the picture of the given sweep and shows it, with a button to save it.
   */
  private void showPicture(String sweep, String title) {
    if (this.pictureRenderer == null) {
      return;
    }
    String [] message = {null};
    byte [] image;
    Window window = SwingUtilities.getWindowAncestor(this);
    java.awt.Cursor cursor = window != null ? window.getCursor() : null;
    if (window != null) {
      window.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.WAIT_CURSOR));
    }
    try {
      image = this.pictureRenderer.render(sweep, message);
    } finally {
      if (window != null) {
        window.setCursor(cursor);
      }
    }
    java.awt.image.BufferedImage picture = null;
    try {
      picture = image != null ? javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(image)) : null;
    } catch (java.io.IOException ex) {
      // No picture
    }
    if (picture == null) {
      JOptionPane.showMessageDialog(this, message [0] != null ? message [0] : "The picture couldn't be drawn.",
          title, JOptionPane.WARNING_MESSAGE);
      return;
    }
    // Shown at a size that fits the screen
    Dimension screenSize = getToolkit().getScreenSize();
    double scale = Math.min(1, Math.min((screenSize.width - 160.) / picture.getWidth(), (screenSize.height - 260.) / picture.getHeight()));
    java.awt.Image shownPicture = scale < 1
        ? picture.getScaledInstance((int)(picture.getWidth() * scale), (int)(picture.getHeight() * scale), java.awt.Image.SCALE_SMOOTH)
        : picture;
    String [] options = {"Save as...", "Close"};
    if (JOptionPane.showOptionDialog(this, new JLabel(new javax.swing.ImageIcon(shownPicture)), title,
            JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options [1]) == 0) {
      javax.swing.JFileChooser chooser = new javax.swing.JFileChooser();
      chooser.setDialogTitle(title);
      java.io.File folder = this.home.getName() != null ? new java.io.File(this.home.getName()).getParentFile() : null;
      chooser.setSelectedFile(new java.io.File(folder, "sun-" + sweep + ".png"));
      if (chooser.showSaveDialog(this) == javax.swing.JFileChooser.APPROVE_OPTION) {
        java.io.File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith(".png")) {
          file = new java.io.File(file.getPath() + ".png");
        }
        if (!file.exists() || JOptionPane.showConfirmDialog(this, file.getName() + " exists. Replace it?", title,
                JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
          try {
            java.io.OutputStream out = new java.io.FileOutputStream(file);
            try {
              out.write(image);
            } finally {
              out.close();
            }
          } catch (java.io.IOException ex) {
            JOptionPane.showMessageDialog(this, "Couldn't write " + file + ": " + ex.getMessage());
          }
        }
      }
    }
  }

  // ---- Components ----

  private void createComponents() {
    final Compass compass = this.home.getCompass();
    this.latitudeSpinner = new JSpinner(new SpinnerNumberModel(0., -90., 90., 0.1));
    this.latitudeSpinner.setEditor(new JSpinner.NumberEditor(this.latitudeSpinner, "0.0000"));
    this.longitudeSpinner = new JSpinner(new SpinnerNumberModel(0., -180., 180., 0.1));
    this.longitudeSpinner.setEditor(new JSpinner.NumberEditor(this.longitudeSpinner, "0.0000"));
    this.northSpinner = new JSpinner(new SpinnerNumberModel(0., 0., 360., 1.));
    this.northSpinner.setToolTipText("Angle from the top of the plan to the north, clockwise");
    String [] timeZones = TimeZone.getAvailableIDs();
    Arrays.sort(timeZones);
    this.timeZoneComboBox = new JComboBox(timeZones);
    this.timeZoneComboBox.setPrototypeDisplayValue("America/Argentina/Buenos_Aires");
    ChangeListener placeListener = new ChangeListener() {
        public void stateChanged(ChangeEvent ev) {
          if (!updating) {
            compass.setLatitude((float)Math.toRadians(((Number)latitudeSpinner.getValue()).doubleValue()));
            compass.setLongitude((float)Math.toRadians(((Number)longitudeSpinner.getValue()).doubleValue()));
            compass.setNorthDirection((float)Math.toRadians(((Number)northSpinner.getValue()).doubleValue() % 360));
            home.setModified(true);
          }
        }
      };
    this.latitudeSpinner.addChangeListener(placeListener);
    this.longitudeSpinner.addChangeListener(placeListener);
    this.northSpinner.addChangeListener(placeListener);
    this.timeZoneComboBox.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent ev) {
          if (!updating && timeZoneComboBox.getSelectedItem() != null) {
            compass.setTimeZone((String)timeZoneComboBox.getSelectedItem());
            home.setModified(true);
          }
        }
      });

    this.monthCalendar = new MonthCalendar();
    this.dateSpinner = createDateSpinner("yyyy-MM-dd", Calendar.DAY_OF_MONTH);
    this.dateSpinner.addChangeListener(new ChangeListener() {
        public void stateChanged(ChangeEvent ev) {
          if (!updating) {
            Calendar calendar = new GregorianCalendar(UTC);
            calendar.setTime((Date)dateSpinner.getValue());
            setDate(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
          }
        }
      });
    this.daySlider = new JSlider(1, 366, 1);
    this.daySlider.setPaintTicks(true);
    this.daySlider.setPaintLabels(true);
    this.daySlider.setToolTipText("Goes through the days of the year at the chosen time");
    this.daySlider.addChangeListener(new ChangeListener() {
        public void stateChanged(ChangeEvent ev) {
          if (!updating) {
            Calendar calendar = getCalendar();
            calendar.set(Calendar.DAY_OF_YEAR, Math.min(daySlider.getValue(), calendar.getActualMaximum(Calendar.DAY_OF_YEAR)));
            setTime(calendar.getTimeInMillis());
          }
        }
      });
    this.timeSpinner = createDateSpinner("HH:mm", Calendar.MINUTE);
    this.timeSpinner.addChangeListener(new ChangeListener() {
        public void stateChanged(ChangeEvent ev) {
          if (!updating) {
            Calendar calendar = new GregorianCalendar(UTC);
            calendar.setTime((Date)timeSpinner.getValue());
            setMinutes(calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE));
          }
        }
      });
    this.timeSlider = new JSlider(0, 24 * 60 - TIME_STEP, 12 * 60);
    this.timeSlider.setMajorTickSpacing(180);
    this.timeSlider.setMinorTickSpacing(60);
    this.timeSlider.setPaintTicks(true);
    Dictionary<Integer, JLabel> hourLabels = new Hashtable<Integer, JLabel>();
    for (int hour = 0; hour < 24; hour += 3) {
      hourLabels.put(hour * 60, new JLabel(hour + " h"));
    }
    this.timeSlider.setLabelTable(hourLabels);
    this.timeSlider.setPaintLabels(true);
    this.timeSlider.setToolTipText("Goes through the hours of the chosen day");
    this.timeSlider.addChangeListener(new ChangeListener() {
        public void stateChanged(ChangeEvent ev) {
          if (!updating) {
            setMinutes(timeSlider.getValue() / TIME_STEP * TIME_STEP);
          }
        }
      });

    // Animations
    this.playYearButton = new JToggleButton("\u25b6 Play the year");
    this.playYearButton.setToolTipText("Shows each day of the year at the chosen time");
    this.playDayButton = new JToggleButton("\u25b6 Play the day");
    this.playDayButton.setToolTipText("Shows the chosen day from sunrise to sunset");
    this.timer = new Timer(70, new ActionListener() {
        public void actionPerformed(ActionEvent ev) {
          Calendar calendar = getCalendar();
          if (playYearButton.isSelected()) {
            int year = calendar.get(Calendar.YEAR);
            calendar.add(Calendar.DAY_OF_YEAR, 1);
            if (calendar.get(Calendar.YEAR) != year) {
              calendar.set(Calendar.YEAR, year);
              calendar.set(Calendar.DAY_OF_YEAR, 1);
            }
            setTime(calendar.getTimeInMillis());
          } else if (playDayButton.isSelected()) {
            int minutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE) + 2 * TIME_STEP;
            int [] day = getDayMinutes();
            if (minutes > day [1] + 20 || minutes < day [0] - 20) {
              // Back to sunrise
              minutes = Math.max(0, (day [0] - 20) / TIME_STEP * TIME_STEP);
            }
            setMinutes(Math.min(minutes, 24 * 60 - TIME_STEP));
          } else {
            timer.stop();
          }
        }
      });
    ActionListener playListener = new ActionListener() {
        public void actionPerformed(ActionEvent ev) {
          JToggleButton other = ev.getSource() == playYearButton ? playDayButton : playYearButton;
          other.setSelected(false);
          if (((JToggleButton)ev.getSource()).isSelected()) {
            timer.start();
          } else {
            timer.stop();
          }
          playYearButton.setText(playYearButton.isSelected() ? "\u25a0 Stop" : "\u25b6 Play the year");
          playDayButton.setText(playDayButton.isSelected() ? "\u25a0 Stop" : "\u25b6 Play the day");
        }
      };
    this.playYearButton.addActionListener(playListener);
    this.playDayButton.addActionListener(playListener);

    this.sunLabel = new JLabel();
    this.sunLabel.setVerticalAlignment(SwingConstants.TOP);

    // Display
    this.followCheckBox = new JCheckBox("Light the 3D view from the sun");
    this.followCheckBox.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent ev) {
          home.setProperty(HomeComponent3D.SUN_FOLLOWS_TIME_PROPERTY, followCheckBox.isSelected() ? "true" : null);
          home.setModified(true);
        }
      });
    this.shadowsCheckBox = new JCheckBox("Shadows");
    this.shadowsCheckBox.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent ev) {
          HomeComponent3D.setOpenGLShadowsSelected(shadowsCheckBox.isSelected());
          if (view3D != null) {
            view3D.updateRenderer();
          }
        }
      });
    this.falseColorsCheckBox = new JCheckBox("False colors of the light level");
    this.falseColorsCheckBox.setToolTipText("<html>Replaces the colors of the 3D view by the light the surfaces receive<br>"
        + "from the sun and the sky by clear weather, in lux.</html>");
    String [] maximums = new String [FALSE_COLORS_MAXIMUMS.length];
    for (int i = 0; i < maximums.length; i++) {
      maximums [i] = String.format("up to %,d lux", FALSE_COLORS_MAXIMUMS [i]);
    }
    this.falseColorsComboBox = new JComboBox(maximums);
    ActionListener falseColorsListener = new ActionListener() {
        public void actionPerformed(ActionEvent ev) {
          if (!updating) {
            if (falseColorsCheckBox.isSelected() && !followCheckBox.isSelected()) {
              home.setProperty(HomeComponent3D.SUN_FOLLOWS_TIME_PROPERTY, "true");
            }
            home.setProperty(HomeComponent3D.SUN_FALSE_COLORS_PROPERTY, falseColorsCheckBox.isSelected()
                ? String.valueOf(FALSE_COLORS_MAXIMUMS [Math.max(0, falseColorsComboBox.getSelectedIndex())])
                : null);
            home.setModified(true);
          }
        }
      };
    this.falseColorsCheckBox.addActionListener(falseColorsListener);
    this.falseColorsComboBox.addActionListener(falseColorsListener);
    this.legend = new FalseColorsLegend();
    // The place of the home when it is known, and the pictures
    this.placeNoteLabel = new JLabel();
    this.georeferenceButton = new JButton("Use the GPS position of the home");
    this.georeferenceButton.setToolTipText("Gives the sun the latitude, the longitude and the north of the georeference of the home");
    this.georeferenceButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent ev) {
          HomeGeoreference georeference = HomeGeoreference.get(home);
          if (georeference != null) {
            georeference.applyTo(home.getCompass());
            home.setModified(true);
          }
        }
      });
    final String [][] pictures = {
        {"day", "The day in pictures...", "The day from sunrise to sunset, in six pictures seen from the camera of the 3D view"},
        {"year", "The year in pictures...", "The equinoxes and the solstices at the chosen time, seen from the camera of the 3D view"},
        {"hours", "Hours of sun...", "Colors what the camera of the 3D view sees by the hours of direct sun of the chosen day"}};
    this.pictureButtons = new JButton [pictures.length];
    for (int i = 0; i < pictures.length; i++) {
      final String [] picture = pictures [i];
      this.pictureButtons [i] = new JButton(picture [1]);
      this.pictureButtons [i].setToolTipText(picture [2]);
      this.pictureButtons [i].addActionListener(new ActionListener() {
          public void actionPerformed(ActionEvent ev) {
            showPicture(picture [0], picture [1].replace("...", ""));
          }
        });
    }
    this.rendererLabel = new JLabel("Shadows and false colors need the OpenGL renderer.");
    this.rendererButton = new JButton("Use the OpenGL renderer");
    this.rendererButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent ev) {
          HomeComponent3D.setOpenGLRendererSelected(true);
          if (view3D != null) {
            view3D.updateRenderer();
          }
          refresh();
        }
      });
  }

  private JSpinner createDateSpinner(String pattern, int calendarField) {
    JSpinner spinner = new JSpinner(new SpinnerDateModel(new Date(), null, null, calendarField));
    JSpinner.DateEditor editor = new JSpinner.DateEditor(spinner, pattern);
    editor.getFormat().setTimeZone(UTC);
    spinner.setEditor(editor);
    return spinner;
  }

  private JButton createDateButton(String text, final int month, final int day) {
    JButton button = new JButton(text);
    button.setMargin(new Insets(2, 6, 2, 6));
    button.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent ev) {
          if (month < 0) {
            // Now at the place
            Calendar now = new GregorianCalendar(TimeZone.getTimeZone(home.getCompass().getTimeZone()));
            Calendar calendar = new GregorianCalendar(UTC);
            calendar.clear();
            calendar.set(now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH),
                now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE) / TIME_STEP * TIME_STEP);
            setTime(calendar.getTimeInMillis());
          } else {
            setDate(getCalendar().get(Calendar.YEAR), month, day);
          }
        }
      });
    return button;
  }

  private void layoutComponents() {
    setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
    Insets labelInsets = new Insets(2, 0, 2, 5);
    Insets fieldInsets = new Insets(2, 0, 2, 12);

    JPanel placePanel = new JPanel(new GridBagLayout());
    placePanel.setBorder(BorderFactory.createTitledBorder("Place"));
    placePanel.add(new JLabel("Latitude (\u00b0, north +):"), new GridBagConstraints(0, 0, 1, 1, 0, 0,
        GridBagConstraints.LINE_END, GridBagConstraints.NONE, labelInsets, 0, 0));
    placePanel.add(this.latitudeSpinner, new GridBagConstraints(1, 0, 1, 1, 1, 0,
        GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL, fieldInsets, 0, 0));
    placePanel.add(new JLabel("Longitude (\u00b0, east +):"), new GridBagConstraints(2, 0, 1, 1, 0, 0,
        GridBagConstraints.LINE_END, GridBagConstraints.NONE, labelInsets, 0, 0));
    placePanel.add(this.longitudeSpinner, new GridBagConstraints(3, 0, 1, 1, 1, 0,
        GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL, new Insets(2, 0, 2, 0), 0, 0));
    placePanel.add(new JLabel("Time zone:"), new GridBagConstraints(0, 1, 1, 1, 0, 0,
        GridBagConstraints.LINE_END, GridBagConstraints.NONE, labelInsets, 0, 0));
    placePanel.add(this.timeZoneComboBox, new GridBagConstraints(1, 1, 1, 1, 1, 0,
        GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL, fieldInsets, 0, 0));
    placePanel.add(new JLabel("North of the plan (\u00b0):"), new GridBagConstraints(2, 1, 1, 1, 0, 0,
        GridBagConstraints.LINE_END, GridBagConstraints.NONE, labelInsets, 0, 0));
    placePanel.add(this.northSpinner, new GridBagConstraints(3, 1, 1, 1, 1, 0,
        GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL, new Insets(2, 0, 2, 0), 0, 0));
    JPanel placeNotePanel = new JPanel(new BorderLayout(8, 0));
    placeNotePanel.add(this.placeNoteLabel, BorderLayout.CENTER);
    JPanel georeferenceButtonPanel = new JPanel(new FlowLayout(FlowLayout.TRAILING, 0, 0));
    georeferenceButtonPanel.add(this.georeferenceButton);
    placeNotePanel.add(georeferenceButtonPanel, BorderLayout.LINE_END);
    placePanel.add(placeNotePanel, new GridBagConstraints(0, 2, 4, 1, 1, 0,
        GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL, new Insets(2, 0, 0, 0), 0, 0));

    JPanel datePanel = new JPanel(new GridBagLayout());
    datePanel.setBorder(BorderFactory.createTitledBorder("Date"));
    datePanel.add(this.monthCalendar, new GridBagConstraints(0, 0, 1, 2, 0, 0,
        GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 12), 0, 0));
    JPanel dateButtonsPanel = new JPanel(new GridLayout(0, 1, 0, 3));
    JPanel dateSpinnerPanel = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
    dateSpinnerPanel.add(this.dateSpinner);
    dateButtonsPanel.add(dateSpinnerPanel);
    dateButtonsPanel.add(createDateButton("Today, now", -1, 0));
    dateButtonsPanel.add(createDateButton("March equinox", Calendar.MARCH, 20));
    dateButtonsPanel.add(createDateButton("June solstice", Calendar.JUNE, 21));
    dateButtonsPanel.add(createDateButton("September equinox", Calendar.SEPTEMBER, 22));
    dateButtonsPanel.add(createDateButton("December solstice", Calendar.DECEMBER, 21));
    datePanel.add(dateButtonsPanel, new GridBagConstraints(1, 0, 1, 1, 1, 0,
        GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
    datePanel.add(this.playYearButton, new GridBagConstraints(1, 1, 1, 1, 1, 1,
        GridBagConstraints.SOUTHWEST, GridBagConstraints.NONE, new Insets(6, 0, 0, 0), 0, 0));
    datePanel.add(this.daySlider, new GridBagConstraints(0, 2, 2, 1, 1, 0,
        GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, new Insets(6, 0, 0, 0), 0, 0));

    JPanel timePanel = new JPanel(new GridBagLayout());
    timePanel.setBorder(BorderFactory.createTitledBorder("Local time"));
    timePanel.add(this.timeSpinner, new GridBagConstraints(0, 0, 1, 1, 0, 0,
        GridBagConstraints.LINE_START, GridBagConstraints.NONE, new Insets(0, 0, 0, 12), 0, 0));
    timePanel.add(this.playDayButton, new GridBagConstraints(1, 0, 1, 1, 1, 0,
        GridBagConstraints.LINE_START, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
    timePanel.add(this.timeSlider, new GridBagConstraints(0, 1, 2, 1, 1, 0,
        GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, new Insets(6, 0, 0, 0), 0, 0));

    JPanel sunPanel = new JPanel(new BorderLayout());
    sunPanel.setBorder(BorderFactory.createTitledBorder("Sun"));
    sunPanel.add(this.sunLabel, BorderLayout.CENTER);

    JPanel displayPanel = new JPanel(new GridBagLayout());
    displayPanel.setBorder(BorderFactory.createTitledBorder("3D view"));
    displayPanel.add(this.followCheckBox, new GridBagConstraints(0, 0, 1, 1, 0, 0,
        GridBagConstraints.LINE_START, GridBagConstraints.NONE, new Insets(0, 0, 0, 12), 0, 0));
    displayPanel.add(this.shadowsCheckBox, new GridBagConstraints(1, 0, 1, 1, 1, 0,
        GridBagConstraints.LINE_START, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
    displayPanel.add(this.falseColorsCheckBox, new GridBagConstraints(0, 1, 1, 1, 0, 0,
        GridBagConstraints.LINE_START, GridBagConstraints.NONE, new Insets(0, 0, 0, 12), 0, 0));
    displayPanel.add(this.falseColorsComboBox, new GridBagConstraints(1, 1, 1, 1, 1, 0,
        GridBagConstraints.LINE_START, GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
    displayPanel.add(this.legend, new GridBagConstraints(0, 2, 2, 1, 1, 0,
        GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, new Insets(4, 0, 0, 0), 0, 0));
    JPanel rendererPanel = new JPanel();
    rendererPanel.setLayout(new BoxLayout(rendererPanel, BoxLayout.LINE_AXIS));
    rendererPanel.add(this.rendererLabel);
    rendererPanel.add(Box.createHorizontalStrut(8));
    rendererPanel.add(this.rendererButton);
    displayPanel.add(rendererPanel, new GridBagConstraints(0, 3, 2, 1, 1, 0,
        GridBagConstraints.LINE_START, GridBagConstraints.NONE, new Insets(4, 0, 0, 0), 0, 0));
    JPanel picturesPanel = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
    for (int i = 0; i < this.pictureButtons.length; i++) {
      picturesPanel.add(this.pictureButtons [i]);
      picturesPanel.add(Box.createHorizontalStrut(6));
    }
    displayPanel.add(picturesPanel, new GridBagConstraints(0, 4, 2, 1, 1, 0,
        GridBagConstraints.LINE_START, GridBagConstraints.NONE, new Insets(6, 0, 0, 0), 0, 0));

    JComponent [] panels = {placePanel, datePanel, timePanel, sunPanel, displayPanel};
    for (int i = 0; i < panels.length; i++) {
      add(panels [i], new GridBagConstraints(0, i, 1, 1, 1, panels [i] == sunPanel ? 1 : 0,
          GridBagConstraints.NORTH, GridBagConstraints.BOTH, new Insets(0, 0, 4, 0), 0, 0));
    }
  }

  // ---- Display of the model ----

  /**
   * Returns the minutes of the sunrise and of the sunset of the chosen day in local time,
   * or the whole day if the sun doesn't rise or doesn't set.
   */
  private int [] getDayMinutes() {
    Compass compass = this.home.getCompass();
    TimeZone timeZone = TimeZone.getTimeZone(compass.getTimeZone());
    long time = Camera.convertTimeToTimeZone(this.home.getCamera().getTime(), compass.getTimeZone());
    long [] instants = SunPosition.getSunriseNoonSunset(time, timeZone,
        Math.toDegrees(compass.getLatitude()), Math.toDegrees(compass.getLongitude()));
    if (instants [0] == Long.MIN_VALUE || instants [2] == Long.MIN_VALUE) {
      return new int [] {0, 24 * 60 - TIME_STEP};
    }
    Calendar calendar = new GregorianCalendar(timeZone);
    calendar.setTimeInMillis(instants [0]);
    int sunrise = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE);
    calendar.setTimeInMillis(instants [2]);
    int sunset = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE);
    return sunset > sunrise
        ? new int [] {sunrise, sunset}
        : new int [] {0, 24 * 60 - TIME_STEP};
  }

  /**
   * Updates the components from the home.
   */
  private void refresh() {
    this.updating = true;
    try {
      Compass compass = this.home.getCompass();
      double latitude = Math.toDegrees(compass.getLatitude());
      double longitude = Math.toDegrees(compass.getLongitude());
      this.latitudeSpinner.setValue(Math.round(latitude * 10000) / 10000.);
      this.longitudeSpinner.setValue(Math.round(longitude * 10000) / 10000.);
      this.northSpinner.setValue((double)Math.round((Math.toDegrees(compass.getNorthDirection()) % 360 + 360) % 360 * 10) / 10);
      this.timeZoneComboBox.setSelectedItem(compass.getTimeZone());

      Calendar calendar = getCalendar();
      this.monthCalendar.setDate(calendar);
      this.dateSpinner.setValue(calendar.getTime());
      this.timeSpinner.setValue(calendar.getTime());
      int dayCount = calendar.getActualMaximum(Calendar.DAY_OF_YEAR);
      if (this.daySlider.getMaximum() != dayCount || this.daySlider.getLabelTable() == null) {
        this.daySlider.setMaximum(dayCount);
        // A label and a tick at the first day of each month
        Dictionary<Integer, JLabel> monthLabels = new Hashtable<Integer, JLabel>();
        String [] months = new DateFormatSymbols(Locale.ENGLISH).getShortMonths();
        Calendar month = new GregorianCalendar(UTC);
        month.clear();
        for (int i = 0; i < 12; i++) {
          month.set(calendar.get(Calendar.YEAR), i, 1);
          monthLabels.put(month.get(Calendar.DAY_OF_YEAR), new JLabel(months [i]));
        }
        this.daySlider.setLabelTable(monthLabels);
      }
      this.daySlider.setValue(calendar.get(Calendar.DAY_OF_YEAR));
      this.timeSlider.setValue(calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE));

      // The sun
      TimeZone timeZone = TimeZone.getTimeZone(compass.getTimeZone());
      long time = Camera.convertTimeToTimeZone(this.home.getCamera().getTime(), compass.getTimeZone());
      SunPosition sun = SunPosition.getInstance(time, latitude, longitude);
      long [] instants = SunPosition.getSunriseNoonSunset(time, timeZone, latitude, longitude);
      SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm");
      timeFormat.setTimeZone(timeZone);
      StringBuilder text = new StringBuilder("<html><table cellpadding='1' cellspacing='0'>");
      if (instants [0] != Long.MIN_VALUE && instants [2] != Long.MIN_VALUE) {
        long length = (instants [2] - instants [0]) / 60000;
        addRow(text, "Sunrise, sunset:", timeFormat.format(new Date(instants [0] + 30000)) + " \u2013 " + timeFormat.format(new Date(instants [2] + 30000))
            + String.format(" &nbsp; (day of %d h %02d min)", length / 60, length % 60));
      } else {
        addRow(text, "Sunrise, sunset:", SunPosition.getInstance(instants [1], latitude, longitude).getApparentElevation() > 0
            ? "the sun doesn't set this day" : "the sun doesn't rise this day");
      }
      addRow(text, "Solar noon:", timeFormat.format(new Date(instants [1] + 30000))
          + String.format(Locale.US, " &nbsp; (sun at %.1f\u00b0)",
              SunPosition.getInstance(instants [1], latitude, longitude).getApparentElevation()));
      double elevation = sun.getApparentElevation();
      if (elevation > 0) {
        addRow(text, "Now:", String.format(Locale.US, "%.1f\u00b0 over the horizon, in the %s (azimuth %.1f\u00b0)",
            elevation, DIRECTIONS [(int)Math.round(sun.getAzimuth() / 45) % 8], sun.getAzimuth()));
        addRow(text, "Shadow:", String.format(Locale.US, "%.2f times the height of what casts it", sun.getShadowLength()));
        double horizontal = sun.getDirectNormalIlluminance() * Math.sin(Math.toRadians(elevation))
            + sun.getDiffuseHorizontalIlluminance();
        addRow(text, "Light by clear sky:", String.format("%,d lux on the ground in the sun, %,d lux in the shade",
            Math.round(horizontal / 100) * 100, Math.round(sun.getDiffuseHorizontalIlluminance() / 100) * 100));
      } else {
        addRow(text, "Now:", String.format(Locale.US, "night (sun %.1f\u00b0 under the horizon)", -elevation));
        addRow(text, "Shadow:", "none");
        addRow(text, "Light by clear sky:", String.format("%,d lux", Math.round(sun.getDiffuseHorizontalIlluminance())));
      }
      text.append("</table></html>");
      this.sunLabel.setText(text.toString());

      // The 3D view
      boolean follow = "true".equals(this.home.getProperty(HomeComponent3D.SUN_FOLLOWS_TIME_PROPERTY));
      boolean openGL = HomeComponent3D.isOpenGLRendererSelected();
      this.followCheckBox.setSelected(follow);
      this.shadowsCheckBox.setSelected(HomeComponent3D.isOpenGLShadowsSelected());
      this.shadowsCheckBox.setEnabled(openGL);
      float maximum = 0;
      try {
        String property = this.home.getProperty(HomeComponent3D.SUN_FALSE_COLORS_PROPERTY);
        maximum = property != null ? Float.parseFloat(property) : 0;
      } catch (NumberFormatException ex) {
        // No false colors
      }
      this.falseColorsCheckBox.setSelected(maximum > 0 && follow);
      this.falseColorsCheckBox.setEnabled(openGL);
      this.falseColorsComboBox.setEnabled(openGL);
      if (maximum > 0) {
        int index = 0;
        for (int i = 0; i < FALSE_COLORS_MAXIMUMS.length; i++) {
          if (Math.abs(FALSE_COLORS_MAXIMUMS [i] - maximum) < Math.abs(FALSE_COLORS_MAXIMUMS [index] - maximum)) {
            index = i;
          }
        }
        this.falseColorsComboBox.setSelectedIndex(index);
      }
      this.legend.setMaximum(FALSE_COLORS_MAXIMUMS [Math.max(0, this.falseColorsComboBox.getSelectedIndex())]);
      this.legend.setEnabled(this.falseColorsCheckBox.isSelected() && openGL);
      this.rendererLabel.setVisible(!openGL);
      this.rendererButton.setVisible(!openGL);
      for (JButton pictureButton : this.pictureButtons) {
        pictureButton.setEnabled(this.pictureRenderer != null);
      }
      // What would make the sun wrong: an other place than the one of the home, or an other time zone
      String placeDifference = HomeGeoreference.describeCompassDifference(this.home);
      String timeZoneDifference = HomeGeoreference.describeTimeZoneDifference(compass);
      String note = "";
      if (placeDifference != null) {
        note += placeDifference + ".";
      } else if (HomeGeoreference.get(this.home) != null) {
        note += "The place and the north are the GPS position of the home.";
      }
      if (timeZoneDifference != null) {
        note += (note.length() > 0 ? "<br>" : "") + timeZoneDifference + ".";
      }
      this.placeNoteLabel.setText(note.length() > 0 ? "<html><div style='width:330px'>" + note + "</div></html>" : "");
      this.placeNoteLabel.setForeground(placeDifference != null || timeZoneDifference != null
          ? new Color(190, 95, 0) : javax.swing.UIManager.getColor("Label.disabledForeground"));
      this.placeNoteLabel.setVisible(note.length() > 0);
      this.georeferenceButton.setVisible(placeDifference != null);
      // Room for a note which appears
      Window window = SwingUtilities.getWindowAncestor(this);
      if (window != null && window.isDisplayable() && window.getHeight() < window.getPreferredSize().height) {
        window.setSize(window.getWidth(), window.getPreferredSize().height);
      }
    } finally {
      this.updating = false;
    }
  }

  private static void addRow(StringBuilder text, String label, String value) {
    text.append("<tr><td align='right'>").append(label).append("&nbsp;</td><td>").append(value).append("</td></tr>");
  }

  /**
   * The days of a month, where a click chooses a day.
   */
  private class MonthCalendar extends JPanel {
    private final JLabel     monthLabel = new JLabel("", SwingConstants.CENTER);
    private final JButton [] dayButtons = new JButton [42];
    private final int        firstDayOfWeek = Calendar.getInstance().getFirstDayOfWeek();
    private int              year;
    private int              month;
    private int              day;
    // The month which is displayed, which isn't always the one of the chosen day
    private int              shownYear;
    private int              shownMonth;

    public MonthCalendar() {
      super(new BorderLayout(0, 2));
      JPanel header = new JPanel(new BorderLayout());
      JPanel previousPanel = new JPanel(new FlowLayout(FlowLayout.LEADING, 1, 0));
      previousPanel.add(createMoveButton("\u00ab", -12, "Previous year"));
      previousPanel.add(createMoveButton("\u2039", -1, "Previous month"));
      JPanel nextPanel = new JPanel(new FlowLayout(FlowLayout.TRAILING, 1, 0));
      nextPanel.add(createMoveButton("\u203a", 1, "Next month"));
      nextPanel.add(createMoveButton("\u00bb", 12, "Next year"));
      header.add(previousPanel, BorderLayout.LINE_START);
      header.add(this.monthLabel, BorderLayout.CENTER);
      header.add(nextPanel, BorderLayout.LINE_END);
      this.monthLabel.setFont(this.monthLabel.getFont().deriveFont(Font.BOLD));
      add(header, BorderLayout.NORTH);

      JPanel daysPanel = new JPanel(new GridLayout(7, 7, 1, 1));
      String [] weekdays = new DateFormatSymbols(Locale.ENGLISH).getShortWeekdays();
      for (int i = 0; i < 7; i++) {
        JLabel label = new JLabel(weekdays [(this.firstDayOfWeek - 1 + i) % 7 + 1].substring(0, 2), SwingConstants.CENTER);
        label.setFont(label.getFont().deriveFont(label.getFont().getSize2D() - 1));
        daysPanel.add(label);
      }
      for (int i = 0; i < this.dayButtons.length; i++) {
        final JButton button = new JButton();
        button.setMargin(new Insets(1, 1, 1, 1));
        button.setFocusPainted(false);
        button.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ev) {
              SunStudyPanel.this.setDate(shownYear, shownMonth, Integer.parseInt(button.getText()));
            }
          });
        this.dayButtons [i] = button;
        daysPanel.add(button);
      }
      FontMetrics metrics = getFontMetrics(this.dayButtons [0].getFont());
      int cellSize = Math.max(metrics.stringWidth("30") + 12, metrics.getHeight() + 6);
      daysPanel.setPreferredSize(new Dimension(7 * (cellSize + 1), 7 * (cellSize + 1)));
      add(daysPanel, BorderLayout.CENTER);
    }

    private JButton createMoveButton(String text, final int months, String toolTip) {
      JButton button = new JButton(text);
      button.setMargin(new Insets(0, 5, 0, 5));
      button.setToolTipText(toolTip);
      button.addActionListener(new ActionListener() {
          public void actionPerformed(ActionEvent ev) {
            int count = shownYear * 12 + shownMonth + months;
            shownYear = count / 12;
            shownMonth = count % 12;
            update();
          }
        });
      return button;
    }

    public void setDate(Calendar calendar) {
      if (this.year != calendar.get(Calendar.YEAR)
          || this.month != calendar.get(Calendar.MONTH)
          || this.day != calendar.get(Calendar.DAY_OF_MONTH)
          || this.monthLabel.getText().length() == 0) {
        this.year = this.shownYear = calendar.get(Calendar.YEAR);
        this.month = this.shownMonth = calendar.get(Calendar.MONTH);
        this.day = calendar.get(Calendar.DAY_OF_MONTH);
        update();
      }
    }

    private void update() {
      Calendar calendar = new GregorianCalendar(UTC);
      calendar.clear();
      calendar.set(this.shownYear, this.shownMonth, 1);
      this.monthLabel.setText(new DateFormatSymbols(Locale.ENGLISH).getMonths() [this.shownMonth] + " " + this.shownYear);
      int offset = (calendar.get(Calendar.DAY_OF_WEEK) - this.firstDayOfWeek + 7) % 7;
      int dayCount = calendar.getActualMaximum(Calendar.DAY_OF_MONTH);
      Color selectionColor = javax.swing.UIManager.getColor("List.selectionBackground");
      Color selectionForeground = javax.swing.UIManager.getColor("List.selectionForeground");
      for (int i = 0; i < this.dayButtons.length; i++) {
        JButton button = this.dayButtons [i];
        int buttonDay = i - offset + 1;
        boolean shown = buttonDay >= 1 && buttonDay <= dayCount;
        button.setVisible(shown);
        if (shown) {
          button.setText(String.valueOf(buttonDay));
          boolean selected = buttonDay == this.day && this.shownMonth == this.month && this.shownYear == this.year;
          button.setBackground(selected ? selectionColor : null);
          button.setForeground(selected ? selectionForeground : null);
          button.setFont(button.getFont().deriveFont(selected ? Font.BOLD : Font.PLAIN));
        }
      }
    }
  }

  /**
   * The false colors of the 3D view, with the light levels they show.
   */
  private static class FalseColorsLegend extends JComponent {
    private int maximum = FALSE_COLORS_MAXIMUMS [0];

    public void setMaximum(int maximum) {
      if (maximum != this.maximum) {
        this.maximum = maximum;
        repaint();
      }
    }

    @Override
    public Dimension getPreferredSize() {
      return new Dimension(300, 14 + getFontMetrics(getFont()).getHeight() + 2);
    }

    @Override
    protected void paintComponent(Graphics g) {
      FontMetrics metrics = g.getFontMetrics();
      String last = String.format("%,d lux", this.maximum);
      int margin = metrics.stringWidth(last) / 2 + 2;
      int width = getWidth() - 2 * margin;
      for (int x = 0; x < width; x++) {
        float [] color = SunLight.getFalseColor(x / (float)Math.max(1, width - 1));
        Color barColor = new Color(color [0], color [1], color [2]);
        if (!isEnabled()) {
          int gray = (barColor.getRed() + barColor.getGreen() + barColor.getBlue()) / 3;
          barColor = new Color((gray + 510) / 3, (gray + 510) / 3, (gray + 510) / 3);
        }
        g.setColor(barColor);
        g.drawLine(margin + x, 0, margin + x, 13);
      }
      g.setColor(isEnabled() ? getForeground() : Color.GRAY);
      for (int i = 0; i <= 4; i++) {
        int x = margin + (width - 1) * i / 4;
        g.drawLine(x, 12, x, 15);
        String label = i == 4 ? last : String.format("%,d", this.maximum * i / 4);
        g.drawString(label, x - metrics.stringWidth(label) / 2, 15 + metrics.getAscent());
      }
    }
  }
}
