/*
 * HomeMCPRobustnessTest.java 25 nov. 2026
 *
 * Sweet Home 3D, Copyright (c) 2026 Space Mushrooms <info@sweethome3d.com>
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
package com.eteks.sweethome3d.junit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.spi.IIORegistry;
import javax.imageio.spi.ImageWriterSpi;
import javax.imageio.stream.ImageOutputStream;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.eteks.sweethome3d.io.DefaultUserPreferences;
import com.eteks.sweethome3d.io.HomeFileRecorder;
import com.eteks.sweethome3d.model.Camera;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeApplication;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.HomeRecorder;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.swing.HomeMCPBridge;
import com.eteks.sweethome3d.swing.HomeMCPServer;
import com.eteks.sweethome3d.swing.HomePane;
import com.eteks.sweethome3d.swing.SwingViewFactory;
import com.eteks.sweethome3d.tools.ImageProviders;
import com.eteks.sweethome3d.viewcontroller.AssistantCommandExecutor;
import com.eteks.sweethome3d.viewcontroller.HomeController;
import com.eteks.sweethome3d.viewcontroller.HomeMCPHandler;
import com.eteks.sweethome3d.viewcontroller.JSONParser;

/**
 * Tests the MCP server in the conditions that broke it once released: the pictures
 * drawn by the window of a home itself rather than by a stand-in, with a plug-in which
 * registers a writer of images that can't work; errors told with their cause; and a
 * client whose home changes behind its back.
 * @author Sweet Home 3D contributors
 */
public class HomeMCPRobustnessTest {
  private String         previousNo3D;
  private File           folder;
  private ImageWriterSpi failingWriterProvider;

  @Before
  public void setUp() throws Exception {
    this.previousNo3D = System.getProperty("com.eteks.sweethome3d.no3D");
    System.setProperty("com.eteks.sweethome3d.no3D", "true");
    this.folder = File.createTempFile("mcp-robustness", "");
    this.folder.delete();
    assertTrue(this.folder.mkdir());
  }

  @After
  public void tearDown() {
    if (this.failingWriterProvider != null) {
      IIORegistry.getDefaultInstance().deregisterServiceProvider(this.failingWriterProvider);
    }
    File [] files = this.folder.listFiles();
    if (files != null) {
      for (File file : files) {
        file.delete();
      }
    }
    this.folder.delete();
    if (this.previousNo3D == null) {
      System.clearProperty("com.eteks.sweethome3d.no3D");
    } else {
      System.setProperty("com.eteks.sweethome3d.no3D", this.previousNo3D);
    }
  }

  /**
   * A writer of PNG images like the one that a plug-in written for an other version of the
   * application registers: it needs a class of a library that isn't distributed anymore.
   */
  public static class FailingPNGWriterProvider extends ImageWriterSpi {
    public FailingPNGWriterProvider() {
      super("Plug-in", "1.0", new String [] {"png", "PNG"}, new String [] {"png"}, new String [] {"image/png"},
          FailingPNGWriter.class.getName(), new Class [] {ImageOutputStream.class}, null,
          false, null, null, null, null, false, null, null, null, null);
    }

    @Override
    public boolean canEncodeImage(ImageTypeSpecifier type) {
      return true;
    }

    @Override
    public ImageWriter createWriterInstance(Object extension) {
      return new FailingPNGWriter(this);
    }

    @Override
    public String getDescription(Locale locale) {
      return "PNG writer of a plug-in";
    }
  }

  private static class FailingPNGWriter extends ImageWriter {
    public FailingPNGWriter(ImageWriterSpi provider) {
      super(provider);
    }

    @Override
    public void write(IIOMetadata streamMetadata, IIOImage image, ImageWriteParam param) {
      throw new NoClassDefFoundError("org/freehep/graphicsio/png/PNGEncoder");
    }

    @Override
    public IIOMetadata convertStreamMetadata(IIOMetadata inData, ImageWriteParam param) {
      return null;
    }

    @Override
    public IIOMetadata convertImageMetadata(IIOMetadata inData, ImageTypeSpecifier imageType, ImageWriteParam param) {
      return null;
    }

    @Override
    public IIOMetadata getDefaultImageMetadata(ImageTypeSpecifier imageType, ImageWriteParam param) {
      return null;
    }

    @Override
    public IIOMetadata getDefaultStreamMetadata(ImageWriteParam param) {
      return null;
    }
  }

  /**
   * Registers a writer of PNG images that fails, ahead of the one of Java.
   */
  private void registerFailingWriter() {
    IIORegistry registry = IIORegistry.getDefaultInstance();
    List<ImageWriterSpi> pngProviders = new ArrayList<ImageWriterSpi>();
    for (Iterator<ImageWriter> it = ImageIO.getImageWritersByFormatName("png"); it.hasNext(); ) {
      pngProviders.add(it.next().getOriginatingProvider());
    }
    assertFalse(pngProviders.isEmpty());
    this.failingWriterProvider = new FailingPNGWriterProvider();
    // Takes the place of the one registered before, as the one of each home does
    registry.registerServiceProvider(this.failingWriterProvider, ImageWriterSpi.class);
    for (ImageWriterSpi provider : pngProviders) {
      registry.setOrdering(ImageWriterSpi.class, this.failingWriterProvider, provider);
    }
  }

  private static boolean isPNGWritten() throws IOException {
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      return ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "png", out) && out.size() > 0;
    } catch (NoClassDefFoundError ex) {
      return false;
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> call(HomeMCPHandler handler, String name, String arguments) {
    Map<String, Object> response = (Map<String, Object>)JSONParser.parse(handler.handle(
        "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\",\"params\":{\"name\":\"" + name + "\",\"arguments\":" + arguments + "}}"));
    assertNotNull("A tool result and no protocol error: " + response, response.get("result"));
    return (Map<String, Object>)response.get("result");
  }

  @SuppressWarnings("unchecked")
  private static String text(Map<String, Object> toolResult) {
    StringBuilder text = new StringBuilder();
    for (Object content : (List<Object>)toolResult.get("content")) {
      if ("text".equals(((Map<String, Object>)content).get("type"))) {
        text.append(((Map<String, Object>)content).get("text")).append('\n');
      }
    }
    return text.toString();
  }

  /**
   * Returns the picture of an answer, checked to be a PNG image that Java reads.
   */
  @SuppressWarnings("unchecked")
  private static BufferedImage image(Map<String, Object> toolResult) throws IOException {
    assertEquals(text(toolResult), Boolean.FALSE, toolResult.get("isError"));
    for (Object content : (List<Object>)toolResult.get("content")) {
      if ("image".equals(((Map<String, Object>)content).get("type"))) {
        assertEquals("image/png", ((Map<String, Object>)content).get("mimeType"));
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(
            java.util.Base64.getDecoder().decode((String)((Map<String, Object>)content).get("data"))));
        assertNotNull(image);
        return image;
      }
    }
    fail("No picture in " + text(toolResult));
    return null;
  }

  /**
   * A home in the window of the application, and the handler that this window creates
   * for MCP clients: its pictures are drawn by the code that draws them for users.
   */
  private static final class DisplayedHome {
    final Home            home;
    final UserPreferences preferences;
    final HomeController  homeController;
    final HomeMCPHandler  handler;
    final String          pieceName;

    DisplayedHome(HomeApplication application, UserPreferences preferences) throws Exception {
      this.home = new Home(250);
      this.preferences = preferences;
      this.homeController = application != null
          ? new HomeController(this.home, application, new SwingViewFactory())
          : new HomeController(this.home, preferences, new SwingViewFactory());
      this.home.addRoom(new Room(new float [][] {{0, 0}, {400, 0}, {400, 300}, {0, 300}}));
      // A piece of the catalog of the application, with its icon
      this.pieceName = preferences.getFurnitureCatalog().getCategories().get(0).getFurniture().get(0).getName();
      for (int x = 100; x <= 300; x += 200) {
        HomePieceOfFurniture sofa = new HomePieceOfFurniture(
            preferences.getFurnitureCatalog().getCategories().get(0).getFurniture().get(0));
        sofa.setX(x);
        sofa.setY(150);
        this.home.addPieceOfFurniture(sofa);
      }
      HomePane homePane = (HomePane)this.homeController.getView();
      java.lang.reflect.Method getMCPHandler = HomePane.class.getDeclaredMethod("getMCPHandler");
      getMCPHandler.setAccessible(true);
      this.handler = (HomeMCPHandler)getMCPHandler.invoke(homePane);
      this.handler.setEditingAllowed(true);
    }
  }

  /**
   * The pictures of the plan and of the catalog are the ones of the window of the home,
   * whatever the writer of images that a plug-in registered.
   */
  @Test
  public void testPicturesOfDisplayedHomeWithFailingImageWriter() throws Exception {
    DisplayedHome displayedHome = new DisplayedHome(null, new DefaultUserPreferences());
    String names = "{\"names\":[\"" + displayedHome.pieceName.replace("\"", "\\\"") + "\"]}";
    HomeMCPHandler handler = displayedHome.handler;
    // Without any plug-in
    BufferedImage plan = image(call(handler, "get_plan_image", "{\"area\":{\"x\":-50,\"y\":-50,\"width\":500,\"height\":400},\"max_size\":500}"));
    assertEquals(500, plan.getWidth());
    BufferedImage icon = image(call(handler, "get_catalog_image", names));
    assertTrue(icon.getWidth() > 8);

    registerFailingWriter();
    assertFalse("The failing writer is the one that Java picks", isPNGWritten());
    // Each call puts the writers of Java first again
    plan = image(call(handler, "get_plan_image", "{\"area\":{\"x\":-50,\"y\":-50,\"width\":500,\"height\":400},\"max_size\":500}"));
    assertEquals(500, plan.getWidth());
    // The room is drawn in it
    int darkPixels = 0;
    for (int x = 0; x < plan.getWidth(); x += 2) {
      for (int y = 0; y < plan.getHeight(); y += 2) {
        if ((plan.getRGB(x, y) & 0xFF) < 200) {
          darkPixels++;
        }
      }
    }
    assertTrue(darkPixels + " dark pixels", darkPixels > 50);
    image(call(handler, "get_plan_image", "{\"target\":\"R1\"}"));
    image(call(handler, "get_catalog_image", names));
    assertTrue(isPNGWritten());

    // The pictures in 3D aren't available in this test: the answer tells it, without failing
    Map<String, Object> result = call(handler, "get_3d_image", "{\"view\":\"top\",\"width\":400,\"height\":300}");
    assertEquals(Boolean.TRUE, result.get("isError"));

    // The camera of the window moves at the request of the client
    Map<String, Object> moved = call(handler, "apply_commands",
        "{\"commands\":[{\"action\":\"set_camera\",\"mode\":\"visitor\",\"x\":200,\"y\":150,\"height\":170,\"look_at_x\":400,\"look_at_y\":150}]}");
    assertEquals(text(moved), Boolean.FALSE, moved.get("isError"));
    Camera camera = displayedHome.home.getCamera();
    assertEquals(displayedHome.home.getObserverCamera(), camera);
    assertEquals(200, camera.getX(), 0.5f);
    assertEquals(150, camera.getY(), 0.5f);
  }

  /**
   * The readers and the writers of images of Java are preferred to the ones registered later.
   */
  @Test
  public void testPlatformImageProvidersArePreferred() throws Exception {
    assertTrue(isPNGWritten());
    registerFailingWriter();
    assertFalse(isPNGWritten());
    assertTrue(ImageProviders.preferPlatformProviders() >= 1);
    assertTrue(isPNGWritten());
    // The other writer remains for who asks for it
    boolean registered = false;
    for (Iterator<ImageWriter> it = ImageIO.getImageWritersByFormatName("png"); it.hasNext(); ) {
      registered |= it.next().getOriginatingProvider() == this.failingWriterProvider;
    }
    assertTrue(registered);
    // Registered again by the plug-in of an other home
    registerFailingWriter();
    assertFalse(isPNGWritten());
    ImageProviders.preferPlatformProviders();
    assertTrue(isPNGWritten());
  }

  /**
   * A tool which fails tells why, to a client called through the handler,
   * through HTTP and through the program launched by clients.
   */
  @Test
  public void testToolErrorsTellTheirCause() throws Exception {
    Home home = new Home();
    home.addRoom(new Room(new float [][] {{0, 0}, {400, 0}, {400, 300}, {0, 300}}));
    HomeMCPHandler handler = new HomeMCPHandler(home, AssistantCannedProtocolTest.createCatalogPreferences(), null, "test");
    handler.setImageProvider(new HomeMCPHandler.ImageProvider() {
        public byte [] getPlanImage(int maxSize, Level level, float [] area) {
          throw new NoClassDefFoundError("org/freehep/graphicsio/png/PNGEncoder");
        }

        public HomeMCPHandler.Image3D get3DImage(HomeMCPHandler.Image3DRequest request) {
          throw new OutOfMemoryError("Java heap space");
        }

        public byte [] getCatalogImage(String name, boolean texture) {
          throw new ExceptionInInitializerError("writer");
        }

        public String get3DInfo() {
          return null;
        }
      });
    Map<String, Object> result = call(handler, "get_plan_image", "{}");
    assertEquals(Boolean.TRUE, result.get("isError"));
    assertTrue(text(result), text(result).contains("org/freehep/graphicsio/png/PNGEncoder"));
    assertTrue(text(result), text(result).contains("plug-in"));
    result = call(handler, "get_catalog_image", "{\"names\":[\"Sofa\"]}");
    assertEquals(Boolean.TRUE, result.get("isError"));
    assertTrue(text(result), text(result).contains("ExceptionInInitializerError"));
    result = call(handler, "get_3d_image", "{\"view\":\"top\"}");
    assertEquals(Boolean.TRUE, result.get("isError"));
    assertTrue(text(result), text(result).contains("memory"));
    // The tools which draw nothing still work
    assertEquals(Boolean.FALSE, call(handler, "get_home", "{}").get("isError"));

    // Through the program launched by clients: an answer of the tool, not an HTTP error without reason
    HomeMCPServer server = new HomeMCPServer(handler, HomeMCPServer.getOrCreateToken(this.folder), 0);
    server.start();
    try {
      HomeMCPServer.writePort(this.folder, server.getPort());
      String [] lines = runBridge("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"get_plan_image\",\"arguments\":{}}}\n");
      assertEquals(1, lines.length);
      assertTrue(lines [0], lines [0].contains("PNGEncoder"));
      assertFalse(lines [0], lines [0].contains("HTTP 500"));
    } finally {
      server.stop();
    }
  }

  private String [] runBridge(String messages) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    HomeMCPBridge.run(new ByteArrayInputStream(messages.getBytes("UTF-8")), out, this.folder);
    return out.toString("UTF-8").split("\n");
  }

  private static String toolCall(int id, String name, String arguments) {
    return "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"method\":\"tools/call\",\"params\":{\"name\":\"" + name
        + "\",\"arguments\":" + arguments + "}}\n";
  }

  /**
   * Returns a stream of the given parts read one after the other. Each action is
   * run once the part of its index was read by the program, before the next one.
   */
  private static InputStream sequence(final String [] parts, final Runnable [] actions) {
    return new SequenceInputStream(new Enumeration<InputStream>() {
        private int index;

        public boolean hasMoreElements() {
          return this.index < parts.length;
        }

        public InputStream nextElement() {
          if (this.index > 0 && actions [this.index - 1] != null) {
            actions [this.index - 1].run();
          }
          try {
            return new ByteArrayInputStream(parts [this.index++].getBytes("UTF-8"));
          } catch (java.io.UnsupportedEncodingException ex) {
            throw new IllegalStateException(ex);
          }
        }
      });
  }

  private static Home createHomeWithSofas(UserPreferences preferences, HomeController [] controller, String name) throws Exception {
    Home home = new Home();
    home.setName(name);
    controller [0] = new HomeController(home, preferences, new SwingViewFactory());
    AssistantCommandExecutor executor = new AssistantCommandExecutor(home, preferences, controller [0]);
    for (int i = 0; i < 3; i++) {
      HomePieceOfFurniture sofa = new HomePieceOfFurniture(executor.findCatalogPiece("Sofa", false));
      sofa.setX(100 + 200 * i);
      home.addPieceOfFurniture(sofa);
    }
    return home;
  }

  /**
   * The window of the home a client works on is closed: the server of an other home
   * answers in its place, where the same ids designate other items. The first call
   * of the client isn't run, and tells it.
   */
  @Test
  public void testHomeChangedBehindTheClient() throws Exception {
    UserPreferences preferences = AssistantCannedProtocolTest.createCatalogPreferences();
    HomeController [] controller = new HomeController [1];
    final Home mainHome = createHomeWithSofas(preferences, controller, new File(this.folder, "main-house.sh3d").getPath());
    HomeMCPHandler mainHandler = new HomeMCPHandler(mainHome, preferences, controller [0], "test");
    mainHandler.setEditingAllowed(true);
    List<HomePieceOfFurniture> mainSofas = new ArrayList<HomePieceOfFurniture>(mainHome.getFurniture());
    Home scratchHome = createHomeWithSofas(preferences, controller, null);
    HomeMCPHandler scratchHandler = new HomeMCPHandler(scratchHome, preferences, controller [0], "test");
    scratchHandler.setEditingAllowed(true);
    assertFalse(mainHandler.getHomeId().equals(scratchHandler.getHomeId()));

    String token = HomeMCPServer.getOrCreateToken(this.folder);
    final HomeMCPServer mainServer = new HomeMCPServer(mainHandler, token, 0);
    final HomeMCPServer scratchServer = new HomeMCPServer(scratchHandler, token, 0);
    mainServer.start();
    scratchServer.start();
    try {
      // The client works on the scratch home
      HomeMCPServer.writePort(this.folder, scratchServer.getPort());
      String delete = toolCall(3, "apply_commands", "{\"commands\":[{\"action\":\"delete\",\"ids\":[\"F1\",\"F2\",\"F3\"]}]}");
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      HomeMCPBridge.run(sequence(
          new String [] {
              toolCall(1, "get_home", "{}") + toolCall(2, "apply_commands", "{\"commands\":[{\"action\":\"move\",\"id\":\"F1\",\"dx\":10}]}"),
              // The call meant for the scratch home, then calls made knowingly
              delete + toolCall(4, "list_homes", "{}") + toolCall(5, "get_home", "{}")
                  + toolCall(6, "apply_commands", "{\"commands\":[{\"action\":\"delete\",\"ids\":[\"F3\"]}]}")},
          new Runnable [] {
              new Runnable() {
                public void run() {
                  // The user closes the window of the scratch home: the one of the main home answers
                  scratchServer.stop();
                  try {
                    HomeMCPServer.writePort(folder, mainServer.getPort());
                  } catch (IOException ex) {
                    throw new IllegalStateException(ex);
                  }
                }
              }, null}), out, this.folder);
      String [] lines = out.toString("UTF-8").split("\n");
      assertEquals(6, lines.length);
      assertTrue(lines [1], lines [1].contains("\"isError\":false"));
      assertTrue("The home that was edited is named: " + lines [1], lines [1].contains("Home edited: a home never saved"));
      // Nothing was deleted in the main home
      assertTrue(lines [2], lines [2].contains("\"isError\":true"));
      assertTrue(lines [2], lines [2].contains("Nothing was done"));
      assertTrue(lines [2], lines [2].contains("main-house.sh3d"));
      // Once told, the client works on the main home
      assertTrue(lines [3], lines [3].contains("main-house.sh3d"));
      assertTrue(lines [4], lines [4].contains("\"isError\":false"));
      assertTrue(lines [5], lines [5].contains("\"isError\":false"));
      assertTrue(lines [5], lines [5].contains("main-house.sh3d"));
      // Only the piece deleted knowingly left the main home
      assertEquals(2, mainHome.getFurniture().size());
      assertTrue(mainHome.getFurniture().contains(mainSofas.get(0)));
      assertTrue(mainHome.getFurniture().contains(mainSofas.get(1)));
      assertEquals(3, scratchHome.getFurniture().size());

      // A program started while the main home is served isn't refused anything
      out = new ByteArrayOutputStream();
      HomeMCPBridge.run(new ByteArrayInputStream((toolCall(1, "apply_commands",
          "{\"commands\":[{\"action\":\"delete\",\"ids\":[\"F2\"]}]}")).getBytes("UTF-8")), out, this.folder);
      assertTrue(out.toString("UTF-8"), out.toString("UTF-8").contains("\"isError\":false"));
      assertEquals(1, mainHome.getFurniture().size());
    } finally {
      mainServer.stop();
      scratchServer.stop();
    }

    // The same check, asked to the handler
    String request = "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\",\"params\":{\"name\":\"get_home\",\"arguments\":{}}}";
    assertTrue(mainHandler.handle(request, scratchHandler.getHomeId()).contains("Nothing was done"));
    assertFalse(mainHandler.handle(request, mainHandler.getHomeId()).contains("Nothing was done"));
    assertFalse(mainHandler.handle(request, null).contains("Nothing was done"));
    assertFalse(mainHandler.handle(request.replace("get_home", "list_homes"), scratchHandler.getHomeId()).contains("Nothing was done"));
    assertFalse("The next call of an other client isn't concerned", mainHandler.handle(request).contains("Nothing was done"));
  }

  /**
   * The home chosen by the client itself with switch_home is edited without any refusal,
   * and apply_commands checks the home it's meant for when asked to.
   */
  @Test
  public void testHomeChosenByTheClient() throws Exception {
    final UserPreferences preferences = AssistantCannedProtocolTest.createCatalogPreferences();
    final HomeRecorder recorder = new HomeFileRecorder();
    HomeApplication application = new HomeApplication() {
        @Override
        public UserPreferences getUserPreferences() {
          return preferences;
        }

        @Override
        public HomeRecorder getHomeRecorder() {
          return recorder;
        }
      };
    final Home mainHome = new Home();
    mainHome.setName(new File(this.folder, "main-house.sh3d").getPath());
    final Home otherHome = new Home();
    otherHome.setName(new File(this.folder, "garden-shed.sh3d").getPath());
    application.addHome(mainHome);
    application.addHome(otherHome);
    HomeController mainController = new HomeController(mainHome, application, new SwingViewFactory());
    HomeController otherController = new HomeController(otherHome, application, new SwingViewFactory());
    HomeMCPHandler mainHandler = new HomeMCPHandler(mainHome, preferences, mainController, "test");
    HomeMCPHandler otherHandler = new HomeMCPHandler(otherHome, preferences, otherController, "test");
    mainHandler.setEditingAllowed(true);
    otherHandler.setEditingAllowed(true);
    String token = HomeMCPServer.getOrCreateToken(this.folder);
    HomeMCPServer mainServer = new HomeMCPServer(mainHandler, token, 0);
    final HomeMCPServer otherServer = new HomeMCPServer(otherHandler, token, 0);
    mainHandler.setHomeOperations(new HomeMCPHandler.HomeOperations() {
        public String export(String format, File target, Map<String, Object> options) {
          return null;
        }

        public String serve(Home home) throws IOException {
          assertEquals(otherHome, home);
          HomeMCPServer.writePort(folder, otherServer.getPort());
          return "This connection now reads and edits " + home.getName() + ", from your next call.";
        }

        public String startVisit(String mode) {
          return null;
        }
      });
    mainServer.start();
    otherServer.start();
    try {
      HomeMCPServer.writePort(this.folder, mainServer.getPort());
      String addRoom = "{\"action\":\"add_room\",\"points\":[[0,0],[300,0],[300,200],[0,200]]}";
      String [] lines = runBridge(toolCall(1, "apply_commands", "{\"commands\":[" + addRoom + "]}")
          // A home that isn't open: the client stays on the main home
          + toolCall(2, "switch_home", "{\"home\":\"missing\"}")
          + toolCall(3, "apply_commands", "{\"commands\":[" + addRoom + "]}")
          + toolCall(4, "switch_home", "{\"home\":\"garden-shed\"}")
          + toolCall(5, "apply_commands", "{\"commands\":[" + addRoom + "]}")
          // Meant for an other home than the one of the connection
          + toolCall(6, "apply_commands", "{\"home\":\"main-house\",\"commands\":[" + addRoom + "]}")
          + toolCall(7, "apply_commands", "{\"home\":\"garden-shed.sh3d\",\"commands\":[" + addRoom + "]}"));
      assertEquals(7, lines.length);
      assertTrue(lines [0], lines [0].contains("\"isError\":false") && lines [0].contains("main-house.sh3d"));
      assertTrue(lines [1], lines [1].contains("\"isError\":true"));
      assertTrue(lines [2], lines [2].contains("\"isError\":false"));
      assertTrue(lines [3], lines [3].contains("\"isError\":false"));
      assertTrue("Not refused: " + lines [4], lines [4].contains("\"isError\":false") && lines [4].contains("garden-shed.sh3d"));
      assertTrue(lines [5], lines [5].contains("\"isError\":true") && lines [5].contains("Nothing was done"));
      assertTrue(lines [6], lines [6].contains("\"isError\":false"));
      assertEquals(2, mainHome.getRooms().size());
      assertEquals(2, otherHome.getRooms().size());
    } finally {
      mainServer.stop();
      otherServer.stop();
    }
    // A home never saved is named "unsaved"
    HomeMCPHandler newHandler = new HomeMCPHandler(new Home(), preferences,
        new HomeController(new Home(), preferences, new SwingViewFactory()), "test");
    newHandler.setEditingAllowed(true);
    assertEquals(Boolean.TRUE, call(newHandler, "apply_commands", "{\"home\":\"main-house\",\"commands\":[{\"action\":\"add_room\",\"points\":[[0,0],[300,0],[300,200]]}]}").get("isError"));
    assertTrue(text(call(newHandler, "apply_commands", "{\"home\":\"unsaved\",\"commands\":[{\"action\":\"help\"}]}")),
        !text(call(newHandler, "apply_commands", "{\"home\":\"unsaved\",\"commands\":[{\"action\":\"help\"}]}")).contains("Nothing was done"));
  }
}
