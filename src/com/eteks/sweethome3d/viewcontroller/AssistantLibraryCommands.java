/*
 * AssistantLibraryCommands.java 16 Nov 2026
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
package com.eteks.sweethome3d.viewcontroller;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import com.eteks.sweethome3d.model.CatalogPieceOfFurniture;
import com.eteks.sweethome3d.model.Content;
import com.eteks.sweethome3d.model.FurnitureCatalog;
import com.eteks.sweethome3d.model.FurnitureCategory;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeFurnitureGroup;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.PieceOfFurniture;
import com.eteks.sweethome3d.model.RecorderException;
import com.eteks.sweethome3d.model.Selectable;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.tools.URLContent;

/**
 * The commands of the design assistant that create furniture and furniture
 * libraries: a piece built from simple parts, pieces of the home added to the
 * furniture catalog of the user, and furniture library files
 * (<code>.sh3f</code>) written from pieces of the home or of the catalog, or
 * installed in the catalog.
 * @author Sweet Home 3D
 */
class AssistantLibraryCommands {
  static final String DEFAULT_CATEGORY = "My furniture";

  private static final String LIBRARY_EXTENSION = ".sh3f";
  private static final String CATALOG_FILE = "PluginFurnitureCatalog.properties";
  private static final int    MAX_LIBRARY_PIECES = 500;
  private static final long   MAX_LIBRARY_FILE_LENGTH = 300L * 1024 * 1024;

  private final Home                     home;
  private final UserPreferences          preferences;
  private final HomeController           homeController;
  private final AssistantCommandExecutor executor;

  AssistantLibraryCommands(Home home, UserPreferences preferences, HomeController homeController,
                           AssistantCommandExecutor executor) {
    this.home = home;
    this.preferences = preferences;
    this.homeController = homeController;
    this.executor = executor;
  }

  /**
   * Runs <code>command</code> if it's one of the commands of this class and
   * returns <code>true</code> in that case.
   */
  boolean apply(String action, AssistantCommand command, int [] tally, List<String> notes) {
    try {
      if ("create_furniture".equals(action) || "build_furniture".equals(action)) {
        createFurniture(command, tally, notes);
      } else if ("add_to_catalog".equals(action) || "save_to_catalog".equals(action)) {
        addToCatalog(command, notes);
      } else if ("export_furniture_library".equals(action) || "create_furniture_library".equals(action)) {
        exportLibrary(command, notes);
      } else if ("import_furniture_library".equals(action) || "install_furniture_library".equals(action)) {
        importLibrary(command, notes);
      } else {
        return false;
      }
    } catch (IOException ex) {
      notes.add("Couldn't run " + action + " (" + ex.getMessage() + ")");
    } catch (RecorderException ex) {
      notes.add("Couldn't run " + action + " (" + ex.getMessage() + ")");
    }
    return true;
  }

  // ---- Furniture made of parts ----

  private void createFurniture(AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    String name = command.getString("name");
    if (name == null || name.trim().length() == 0) {
      throw new IOException("the \"name\" parameter is required");
    }
    name = name.trim();
    Object parts = command.getParameters().get("parts");
    List<String> partNotes = new ArrayList<String>();
    AssistantGeneratedModel model = AssistantFurnitureBuilder.build(
        parts instanceof List ? (List<?>)parts : null, partNotes, this.executor.getShapeCommands().getTextureFinder());
    double x = command.getDouble("x", Double.NaN);
    double y = command.getDouble("y", Double.NaN);
    // A part under the height 0 puts the piece under the floor of its level
    float lowestPoint = model.getBounds() != null ? model.getBounds() [1] : 0;
    double elevation = command.getDouble("elevation", Math.abs(lowestPoint) < 0.01f ? 0 : lowestPoint);
    if (!AssistantCommandExecutor.sizeSane(model.getWidth()) || !AssistantCommandExecutor.sizeSane(model.getDepth())
        || !AssistantCommandExecutor.sizeSane(model.getHeight()) || !AssistantCommandExecutor.coordinateSane(x)
        || !AssistantCommandExecutor.coordinateSane(y) || !AssistantCommandExecutor.sizeSane(elevation)) {
      notes.add("Ignored create_furniture with out-of-range values");
      return;
    }
    CatalogPieceOfFurniture catalogPiece = new CatalogPieceOfFurniture(name, model.getIcon(), model.getModel(),
        model.getWidth(), model.getDepth(), model.getHeight(), (float)elevation, true, null, null,
        null, 0, null, command.getString("creator"), (float)Math.PI / 8, -(float)Math.PI / 16, 1, true);
    StringBuilder note = new StringBuilder("Created \"" + name + "\", " + Math.round(model.getWidth()) + " x "
        + Math.round(model.getDepth()) + " x " + Math.round(model.getHeight()) + " cm (width x depth x height)");
    String category = command.getString("catalog");
    if (category == null) {
      category = command.getString("category");
    }
    boolean place = command.getBoolean("place", true);
    if (category != null && category.trim().length() > 0) {
      String addedName = addToCatalog(catalogPiece, category.trim());
      note.append(addedName != null
          ? ", added to the catalog category \"" + category.trim() + "\""
              + (addedName.equals(name) ? "" : " as \"" + addedName + "\"")
          : ", not added to the catalog");
    } else if (!place) {
      throw new IOException("\"place\":false needs a \"catalog\" category to keep the piece");
    }
    if (place) {
      HomePieceOfFurniture piece = new HomePieceOfFurniture(catalogPiece);
      if (!Double.isNaN(x)) {
        piece.setX((float)x);
      }
      if (!Double.isNaN(y)) {
        piece.setY((float)y);
      }
      piece.setAngle((float)Math.toRadians(command.getDouble("angle", 0)));
      piece.setElevation((float)elevation);
      if (elevation < 0) {
        piece.setProperty(AssistantCommandExecutor.BELOW_FLOOR_PROPERTY, "true");
      }
      // Kept to change the parts later
      piece.setProperty(AssistantShapeCommands.PARTS_PROPERTY, AssistantShapeCommands.toJson(parts));
      if (model.getBounds() != null) {
        piece.setProperty(AssistantShapeCommands.PARTS_BOUNDS_PROPERTY, AssistantShapeCommands.formatBounds(model.getBounds()));
      }
      piece.setLevel(this.home.getSelectedLevel());
      List<String> tags = command.getStringList("tags");
      if (!tags.isEmpty()) {
        piece.setProperty(HomeItemTags.TAGS_PROPERTY,
            HomeItemTags.formatTags(HomeItemTags.parseTags(HomeItemTags.formatTags(tags))));
        HomeItemTags.showTagsColumn(this.home);
      }
      this.executor.beginTurnUpdate();
      this.homeController.getFurnitureController().addFurniture(Collections.singletonList(piece));
      tally [0]++;
    }
    for (String partNote : partNotes) {
      note.append("; ").append(partNote);
    }
    notes.add(note.toString());
  }

  // ---- Catalog ----

  /**
   * Returns the pieces of the home a command designates by ids, names,
   * selection or tag.
   */
  private List<HomePieceOfFurniture> getHomeFurniture(AssistantCommand command) {
    List<HomePieceOfFurniture> furniture = new ArrayList<HomePieceOfFurniture>();
    String tag = command.getString("tag");
    if (tag != null) {
      furniture.addAll(HomeItemTags.getFurniture(this.home, tag, null));
    }
    for (Selectable item : this.executor.resolveTargets(command)) {
      if (item instanceof HomePieceOfFurniture && !furniture.contains(item)) {
        furniture.add((HomePieceOfFurniture)item);
      }
    }
    return furniture;
  }

  private void addToCatalog(AssistantCommand command, List<String> notes) throws IOException {
    List<HomePieceOfFurniture> furniture = getHomeFurniture(command);
    if (furniture.isEmpty()) {
      throw new IOException("no furniture is designated; give \"ids\", \"target\":\"selection\" or \"tag\"");
    }
    String category = command.getString("category");
    if (category == null || category.trim().length() == 0) {
      category = DEFAULT_CATEGORY;
    }
    category = category.trim();
    List<String> added = new ArrayList<String>();
    List<String> skipped = new ArrayList<String>();
    for (HomePieceOfFurniture piece : furniture) {
      String reason = getUnsupportedReason(piece);
      if (reason != null) {
        skipped.add("\"" + piece.getName() + "\" (" + reason + ")");
        continue;
      }
      String name = command.getString("name") != null && furniture.size() == 1
          ? command.getString("name").trim() : piece.getName();
      CatalogPieceOfFurniture catalogPiece = new CatalogPieceOfFurniture(name, piece.getIcon(), piece.getModel(),
          piece.getWidth(), piece.getDepth(), piece.getHeight(), piece.getElevation(), piece.isMovable(),
          piece.getStaircaseCutOutShape(), piece.getColor(), piece.getModelRotation(), piece.getModelFlags(),
          piece.getModelSize(), piece.getCreator(), (float)Math.PI / 8, -(float)Math.PI / 16, 1, true);
      String addedName = addToCatalog(catalogPiece, category);
      if (addedName != null) {
        added.add("\"" + addedName + "\"");
      } else {
        skipped.add("\"" + name + "\" (already in this category)");
      }
    }
    notes.add("Added " + added.size() + (added.size() == 1 ? " piece" : " pieces") + " to the catalog category \""
        + category + "\"" + (added.isEmpty() ? "" : ": " + join(added))
        + (skipped.isEmpty() ? "" : "; skipped " + join(skipped)));
  }

  private static String getUnsupportedReason(PieceOfFurniture piece) {
    if (piece instanceof HomeFurnitureGroup) {
      return "a group: ungroup it or add its pieces";
    } else if (piece.isDoorOrWindow()) {
      return "doors and windows aren't supported";
    } else if (piece.getModel() == null || piece.getName() == null) {
      return "no model";
    } else {
      return null;
    }
  }

  /**
   * Adds a piece to a category of the catalog of the user, which the
   * application saves with its preferences, and returns its name there, or
   * <code>null</code> if the category already has a piece of this name.
   */
  private String addToCatalog(CatalogPieceOfFurniture piece, String categoryName) {
    FurnitureCatalog catalog = this.preferences.getFurnitureCatalog();
    for (FurnitureCategory category : catalog.getCategories()) {
      if (category.getName().equalsIgnoreCase(categoryName)) {
        categoryName = category.getName();
        for (CatalogPieceOfFurniture existingPiece : category.getFurniture()) {
          if (existingPiece.getName().equalsIgnoreCase(piece.getName())) {
            return null;
          }
        }
      }
    }
    try {
      catalog.add(new FurnitureCategory(categoryName), piece);
      return piece.getName();
    } catch (RuntimeException ex) {
      // Homonym found by the collator of the catalog
      return null;
    }
  }

  // ---- Libraries ----

  private File getLibraryFile(AssistantCommand command, boolean existing) throws IOException {
    String path = command.getString("path");
    if (path == null || path.trim().length() == 0) {
      throw new IOException("the \"path\" parameter is required");
    }
    path = path.trim();
    if (path.startsWith("~" + File.separator) || path.startsWith("~/")) {
      path = System.getProperty("user.home") + path.substring(1);
    }
    if (!path.toLowerCase(Locale.ENGLISH).endsWith(LIBRARY_EXTENSION)) {
      if (existing) {
        throw new IOException("a furniture library is a " + LIBRARY_EXTENSION + " file");
      }
      path += LIBRARY_EXTENSION;
    }
    File file = new File(path);
    if (!file.isAbsolute()) {
      throw new IOException("the path of the file must be absolute");
    } else if (existing && !file.isFile()) {
      throw new IOException("there is no file " + file);
    } else if (!existing && (file.getParentFile() == null || !file.getParentFile().isDirectory())) {
      throw new IOException("the folder " + file.getParent() + " doesn't exist");
    }
    return file;
  }

  private void exportLibrary(AssistantCommand command, List<String> notes) throws IOException, RecorderException {
    File file = getLibraryFile(command, false);
    if (file.exists() && !command.getBoolean("replace", false)) {
      throw new IOException(file + " exists; give \"replace\":true to overwrite it");
    }
    // Pieces of a category of the catalog, and / or pieces of the home
    List<PieceOfFurniture> furniture = new ArrayList<PieceOfFurniture>();
    List<String> categories = new ArrayList<String>();
    List<String> tags = new ArrayList<String>();
    String catalogCategory = command.getString("category");
    String libraryCategory = command.getString("library_category");
    List<HomePieceOfFurniture> homeFurniture = getHomeFurniture(command);
    if (homeFurniture.isEmpty() && catalogCategory != null) {
      for (FurnitureCategory category : this.preferences.getFurnitureCatalog().getCategories()) {
        if (category.getName().equalsIgnoreCase(catalogCategory.trim())) {
          for (CatalogPieceOfFurniture piece : category.getFurniture()) {
            furniture.add(piece);
            categories.add(libraryCategory != null ? libraryCategory.trim() : category.getName());
            tags.add(piece.getTags() != null && piece.getTags().length > 0
                ? HomeItemTags.formatTags(java.util.Arrays.asList(piece.getTags())) : null);
          }
        }
      }
      if (furniture.isEmpty()) {
        throw new IOException("the catalog has no category \"" + catalogCategory + "\"");
      }
    } else {
      for (HomePieceOfFurniture piece : homeFurniture) {
        furniture.add(piece);
        categories.add(libraryCategory != null ? libraryCategory.trim()
            : catalogCategory != null ? catalogCategory.trim() : DEFAULT_CATEGORY);
        tags.add(piece.getProperty(HomeItemTags.TAGS_PROPERTY));
      }
    }
    if (furniture.isEmpty()) {
      throw new IOException("no furniture is designated; give \"ids\", \"target\":\"selection\", \"tag\", "
          + "or the \"category\" of the catalog to export");
    }
    String libraryName = command.getString("name");
    if (libraryName == null || libraryName.trim().length() == 0) {
      libraryName = file.getName().substring(0, file.getName().length() - LIBRARY_EXTENSION.length());
    }
    List<String> skipped = new ArrayList<String>();
    int count = writeLibrary(file, libraryName.trim(), command.getString("description"),
        command.getString("creator"), furniture, categories, tags, skipped);
    StringBuilder note = new StringBuilder("Wrote " + count + (count == 1 ? " piece" : " pieces")
        + " in the furniture library " + file);
    if (!skipped.isEmpty()) {
      note.append("; skipped ").append(join(skipped));
    }
    if (count > 0 && command.getBoolean("install", false)) {
      this.preferences.addFurnitureLibrary(file.getPath());
      note.append("; installed in the furniture catalog");
    } else {
      note.append("; install it with import_furniture_library, or by double-clicking the file");
    }
    notes.add(note.toString());
  }

  /**
   * Writes a furniture library file and returns the count of its pieces.
   */
  static int writeLibrary(File file, String name, String description, String creator,
                          List<? extends PieceOfFurniture> furniture, List<String> categories,
                          List<String> tags, List<String> skipped) throws IOException {
    Properties catalog = new Properties();
    String libraryId = name.replaceAll("[^A-Za-z0-9]+", "") + "-" + Long.toHexString(System.currentTimeMillis());
    catalog.setProperty("id", libraryId);
    catalog.setProperty("name", name);
    if (description != null) {
      catalog.setProperty("description", description);
    }
    if (creator != null) {
      catalog.setProperty("provider", creator);
    }
    catalog.setProperty("version", "1.0");
    File temporaryFile = new File(file.getPath() + ".part");
    ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(temporaryFile));
    int count = 0;
    boolean written = false;
    try {
      Set<String> names = new HashSet<String>();
      for (int i = 0; i < furniture.size() && count < MAX_LIBRARY_PIECES; i++) {
        PieceOfFurniture piece = furniture.get(i);
        String reason = getUnsupportedReason(piece);
        if (reason == null && !names.add((categories.get(i) + "/" + piece.getName()).toLowerCase(Locale.ENGLISH))) {
          reason = "same name as another piece";
        }
        if (reason != null) {
          skipped.add("\"" + piece.getName() + "\" (" + reason + ")");
          continue;
        }
        int index = count + 1;
        String folder = String.valueOf(index);
        String modelName = writeContent(zip, piece.getModel(), folder, true, "model.obj");
        catalog.setProperty("model#" + index, "/" + folder + "/" + modelName);
        catalog.setProperty("multiPartModel#" + index, "true");
        if (piece.getIcon() != null) {
          String iconName = writeContent(zip, piece.getIcon(), folder + "/icon", false, "icon.png");
          catalog.setProperty("icon#" + index, "/" + folder + "/icon/" + iconName);
        }
        catalog.setProperty("id#" + index, libraryId + "#" + index);
        catalog.setProperty("name#" + index, piece.getName());
        catalog.setProperty("category#" + index, categories.get(i));
        catalog.setProperty("width#" + index, String.valueOf(piece.getWidth()));
        catalog.setProperty("depth#" + index, String.valueOf(piece.getDepth()));
        catalog.setProperty("height#" + index, String.valueOf(piece.getHeight()));
        catalog.setProperty("movable#" + index, String.valueOf(piece.isMovable()));
        catalog.setProperty("doorOrWindow#" + index, "false");
        if (piece.getElevation() != 0) {
          catalog.setProperty("elevation#" + index, String.valueOf(piece.getElevation()));
        }
        if (piece.getStaircaseCutOutShape() != null) {
          catalog.setProperty("staircaseCutOutShape#" + index, piece.getStaircaseCutOutShape());
        }
        float [][] rotation = piece.getModelRotation();
        if (rotation != null && rotation.length == 3) {
          StringBuilder text = new StringBuilder();
          for (float [] row : rotation) {
            for (float value : row) {
              text.append(text.length() > 0 ? " " : "").append(value);
            }
          }
          catalog.setProperty("modelRotation#" + index, text.toString());
        }
        if (piece.getModelFlags() != 0) {
          catalog.setProperty("modelFlags#" + index, String.valueOf(piece.getModelFlags()));
        }
        String pieceCreator = piece.getCreator() != null ? piece.getCreator() : creator;
        if (pieceCreator != null) {
          catalog.setProperty("creator#" + index, pieceCreator);
        }
        if (tags.get(i) != null) {
          catalog.setProperty("tags#" + index, tags.get(i));
        }
        count++;
      }
      zip.putNextEntry(new ZipEntry(CATALOG_FILE));
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      catalog.store(out, "Furniture library " + name);
      zip.write(out.toByteArray());
      zip.closeEntry();
      written = count > 0;
    } finally {
      zip.close();
      if (!written) {
        temporaryFile.delete();
      }
    }
    if (count == 0) {
      throw new IOException("none of the pieces can go in a library"
          + (skipped.isEmpty() ? "" : ": " + join(skipped)));
    }
    if (file.exists() && !file.delete()) {
      temporaryFile.delete();
      throw new IOException(file + " can't be replaced");
    }
    if (!temporaryFile.renameTo(file)) {
      temporaryFile.delete();
      throw new IOException(file + " can't be written");
    }
    return count;
  }

  /**
   * Copies a content in a folder of the library, with the files stored
   * beside it when it's the entry of an archive (materials and images of a
   * model), and returns its name in that folder.
   */
  private static String writeContent(ZipOutputStream zip, Content content, String folder,
                                     boolean siblings, String defaultName) throws IOException {
    if (content instanceof URLContent && ((URLContent)content).isJAREntry()) {
      URLContent urlContent = (URLContent)content;
      String entryName = urlContent.getJAREntryName();
      URL archiveUrl = urlContent.getJAREntryURL();
      int slash = entryName.lastIndexOf('/');
      String entryFolder = slash >= 0 ? entryName.substring(0, slash + 1) : "";
      String name = entryName.substring(slash + 1);
      if (siblings && "file".equals(archiveUrl.getProtocol())) {
        ZipFile archive = new ZipFile(new File(URLDecoder.decode(archiveUrl.getPath().replace("+", "%2B"), "UTF-8")));
        try {
          for (Enumeration<? extends ZipEntry> entries = archive.entries(); entries.hasMoreElements(); ) {
            ZipEntry entry = entries.nextElement();
            if (!entry.isDirectory() && entry.getName().startsWith(entryFolder)
                && entry.getName().indexOf("..") < 0) {
              InputStream in = archive.getInputStream(entry);
              try {
                writeEntry(zip, folder + "/" + entry.getName().substring(entryFolder.length()), in);
              } finally {
                in.close();
              }
            }
          }
        } finally {
          archive.close();
        }
        return name;
      }
      InputStream in = content.openStream();
      try {
        writeEntry(zip, folder + "/" + name, in);
      } finally {
        in.close();
      }
      return name;
    }
    String name = defaultName;
    if (content instanceof URLContent) {
      String path = ((URLContent)content).getURL().getPath();
      String fileName = path.substring(path.lastIndexOf('/') + 1);
      if (fileName.matches("[A-Za-z0-9_.-]+\\.[A-Za-z0-9]+")) {
        name = fileName;
      }
    }
    InputStream in = content.openStream();
    try {
      writeEntry(zip, folder + "/" + name, in);
    } finally {
      in.close();
    }
    return name;
  }

  private static void writeEntry(ZipOutputStream zip, String name, InputStream in) throws IOException {
    zip.putNextEntry(new ZipEntry(name));
    byte [] buffer = new byte [8192];
    long length = 0;
    int size;
    while ((size = in.read(buffer)) != -1) {
      length += size;
      if (length > MAX_LIBRARY_FILE_LENGTH) {
        throw new IOException("a file of the library is larger than "
            + (MAX_LIBRARY_FILE_LENGTH / 1024 / 1024) + " MB");
      }
      zip.write(buffer, 0, size);
    }
    zip.closeEntry();
  }

  private void importLibrary(AssistantCommand command, List<String> notes) throws IOException, RecorderException {
    File file = getLibraryFile(command, true);
    if (this.preferences.furnitureLibraryExists(file.getPath()) && !command.getBoolean("replace", false)) {
      throw new IOException("a library named " + file.getName()
          + " is already installed; give \"replace\":true to replace it");
    }
    int countBefore = countCatalogFurniture();
    this.preferences.addFurnitureLibrary(file.getPath());
    int added = countCatalogFurniture() - countBefore;
    notes.add("Installed the furniture library " + file.getName() + " in the catalog"
        + (added > 0 ? " (" + added + (added == 1 ? " new piece)" : " new pieces)") : "")
        + "; find its pieces with search_catalog");
  }

  private int countCatalogFurniture() {
    int count = 0;
    for (FurnitureCategory category : this.preferences.getFurnitureCatalog().getCategories()) {
      count += category.getFurnitureCount();
    }
    return count;
  }

  private static String join(List<String> values) {
    StringBuilder text = new StringBuilder();
    for (String value : values) {
      text.append(text.length() > 0 ? ", " : "").append(value);
    }
    return text.toString();
  }
}
