import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.*;
import javax.swing.*;
import javax.swing.text.JTextComponent;

public class DocShots {
  static void collect(Container c, java.util.List<Component> out) {
    for (Component child : c.getComponents()) { out.add(child); if (child instanceof Container) collect((Container)child, out); }
    if (c instanceof JMenu) { for (Component child : ((JMenu)c).getMenuComponents()) { out.add(child); if (child instanceof Container) collect((Container)child, out); } }
  }
  static JFrame mainFrame() {
    for (Window w : Window.getWindows()) if (w instanceof JFrame && w.isVisible() && ((JFrame)w).getJMenuBar() != null) return (JFrame)w;
    return null;
  }
  static Window top() {
    Window best = null;
    for (Window w : Window.getWindows()) if (w.isVisible() && w != mainFrame() && (w instanceof JDialog || w instanceof JFrame)) best = w;
    return best;
  }
  static String run(String line) throws Exception {
    String cmd = line, arg = "";
    int i = line.indexOf(' ');
    if (i > 0) { cmd = line.substring(0, i); arg = line.substring(i + 1); }
    StringBuilder out = new StringBuilder();
    java.util.List<Component> all = new ArrayList<Component>();
    if (cmd.equals("item") || cmd.equals("tree") || cmd.equals("menu")) {
      collect(mainFrame().getJMenuBar(), all);
      for (Component c : all) {
        if (c instanceof JMenuItem) {
          String text = ((JMenuItem)c).getText();
          if (cmd.equals("tree")) out.append(c instanceof JMenu ? "\n[" + text + "] " : text + (c.isEnabled() ? "" : "(off)") + " | ");
          else if (text != null && (text.equals(arg) || text.startsWith(arg))) {
            if (cmd.equals("menu")) { ((JMenu)c).doClick(); return "menu " + text; }
            if (!(c instanceof JMenu)) { final JMenuItem item = (JMenuItem)c; EventQueue.invokeLater(new Runnable() { public void run() { item.doClick(0); }}); return "clicked " + text + " enabled=" + c.isEnabled(); }
          }
        }
      }
      return cmd.equals("tree") ? out.toString() : "NOT FOUND " + arg;
    } else if (cmd.equals("menuoff")) {
      MenuSelectionManager.defaultManager().clearSelectedPath(); return "ok";
    } else if (cmd.equals("windows")) {
      for (Window w : Window.getWindows()) if (w.isVisible()) {
        String title = w instanceof Dialog ? ((Dialog)w).getTitle() : w instanceof Frame ? ((Frame)w).getTitle() : "";
        out.append(w.getClass().getSimpleName() + " '" + title + "' " + w.getBounds() + "\n");
        if (w != mainFrame()) { all.clear(); collect(w, all);
          int n = 0;
          for (Component c : all) {
            if (c instanceof AbstractButton && ((AbstractButton)c).getText() != null && ((AbstractButton)c).getText().length() > 0) out.append("  B:" + ((AbstractButton)c).getText() + (c.isEnabled() ? "" : "(off)"));
            if (c instanceof JTextComponent) out.append("  T" + (n++) + ":" + c.getClass().getSimpleName());
            if (c instanceof JComboBox) out.append("  C:" + ((JComboBox)c).getSelectedItem());
          }
          out.append("\n"); }
      }
      return out.toString();
    } else if (cmd.equals("click")) {
      Window w = top(); if (w == null) return "no dialog";
      collect(w, all);
      for (Component c : all) if (c instanceof AbstractButton && arg.equals(((AbstractButton)c).getText())) { final AbstractButton b = (AbstractButton)c; EventQueue.invokeLater(new Runnable() { public void run() { b.doClick(0); }}); return "clicked"; }
      return "NOT FOUND";
    } else if (cmd.equals("set")) {
      Window w = top(); if (w == null) return "no dialog";
      collect(w, all); int n = 0; int k = arg.indexOf(' '); int index = Integer.parseInt(arg.substring(0, k));
      for (Component c : all) if (c instanceof JTextComponent && n++ == index) { ((JTextComponent)c).setText(arg.substring(k + 1).replace("\\n", "\n")); return "set"; }
      return "NOT FOUND";
    } else if (cmd.equals("enter")) {
      Window w = top(); collect(w, all); int n = 0; int index = Integer.parseInt(arg);
      for (Component c : all) if (c instanceof JTextField && n++ == index) { ((JTextField)c).postActionEvent(); return "enter"; }
      return "NOT FOUND";
    } else if (cmd.equals("combo")) {
      Window w = top(); collect(w, all); int k = arg.indexOf(' '); int index = Integer.parseInt(arg.substring(0, k)); int n = 0;
      for (Component c : all) if (c instanceof JComboBox && n++ == index) { ((JComboBox)c).setSelectedIndex(Integer.parseInt(arg.substring(k + 1))); return "combo " + ((JComboBox)c).getSelectedItem(); }
      return "NOT FOUND";
    } else if (cmd.equals("close")) {
      for (Window w : Window.getWindows()) if (w.isVisible() && w != mainFrame() && (w instanceof JDialog || w instanceof JFrame)) w.dispose();
      return "closed";
    } else if (cmd.equals("size")) {
      String [] p = arg.split(" "); mainFrame().setBounds(60, 40, Integer.parseInt(p [0]), Integer.parseInt(p [1])); mainFrame().validate(); return "ok";
    } else if (cmd.equals("dsize")) {
      String [] p = arg.split(" "); Window w = top(); w.setSize(Integer.parseInt(p [0]), Integer.parseInt(p [1])); w.validate(); return "ok";
    } else if (cmd.equals("split")) {
      collect(mainFrame(), all); String [] p = arg.split(" "); int n = 0;
      for (Component c : all) if (c instanceof JSplitPane) { if (n++ == Integer.parseInt(p [0])) { ((JSplitPane)c).setDividerLocation(Integer.parseInt(p [1])); return "split"; } }
      return "NOT FOUND " + n;
    }
    return "unknown";
  }
  public static void main(final String [] args) throws Exception {
    new Thread() { public void run() { try {
      ServerSocket server = new ServerSocket(47999, 5, InetAddress.getByName("127.0.0.1"));
      while (true) {
        Socket s = server.accept();
        BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), "UTF-8"));
        final String line = in.readLine();
        final String [] result = {""};
        try { EventQueue.invokeAndWait(new Runnable() { public void run() { try { result [0] = DocShots.run(line); } catch (Exception ex) { result [0] = "ERR " + ex; } }}); } catch (Exception ex) { result [0] = "ERR " + ex; }
        Writer w = new OutputStreamWriter(s.getOutputStream(), "UTF-8"); w.write(result [0] + "\n"); w.flush(); s.close();
      }
    } catch (Exception ex) { ex.printStackTrace(); } } }.start();
    Class.forName("com.eteks.sweethome3d.Sweet" + "Home3D").getMethod("main", String [].class).invoke(null, (Object)args);
  }
}
