/*
 * AssistantOnlineModels.java 25 nov. 2026
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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeFurnitureGroup;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.tools.OperatingSystem;

/**
 * The commands that search the libraries of 3D models of the web, import a model found
 * there with its author and its license, and list the credits of the models of a home.
 * These commands ask web services: they run only if the user allowed web services.
 * @author Sweet Home 3D contributors
 */
public final class AssistantOnlineModels {
  /** The largest file downloaded for a model, in bytes. */
  static final long MAX_DOWNLOAD_LENGTH = 150L * 1024 * 1024;
  /** The largest size of the files of an archive once extracted, in bytes. */
  static final long MAX_EXTRACTED_LENGTH = 400L * 1024 * 1024;
  private static final int  MAX_ARCHIVE_ENTRIES = 2000;
  private static final int  MAX_ANSWER_LENGTH = 8 * 1024 * 1024;
  private static final int  MAX_RESULTS = 24;

  private static final List<String> FIND_KEYS = Arrays.asList("action", "query", "library", "license", "max_triangles", "count");
  private static final List<String> SOURCE_KEYS = Arrays.asList("source", "token", "resolution");
  private static final List<String> TOKEN_KEYS = Arrays.asList("action", "library", "token", "remove");
  private static final List<String> CREDITS_KEYS = Arrays.asList("action");

  /**
   * Asks web servers.
   */
  public interface Fetcher {
    /**
     * Returns what the server answers to a GET request at <code>address</code>.
     * @param authorization the value of the <code>Authorization</code> header, or <code>null</code>.
     *          It must be sent to the host of <code>address</code> only, never to the host of a redirection.
     * @param maxLength the largest answer accepted, in bytes
     */
    byte [] fetch(String address, String authorization, long maxLength) throws IOException;
  }

  /**
   * Keeps the tokens that the user got from libraries which ask for an account.
   */
  public interface TokenStore {
    /**
     * Returns the token kept for the library <code>library</code>, or <code>null</code>.
     */
    String getToken(String library) throws IOException;

    /**
     * Keeps <code>token</code> for the library <code>library</code>, or forgets it if it's <code>null</code>.
     */
    void setToken(String library, String token) throws IOException;
  }

  /**
   * A model found in a library.
   */
  static final class Model {
    String library;
    String id;
    String name;
    String author;
    /** The short name of its license, like <code>CC BY</code>. */
    String license;
    String page;
    String preview;
    int    triangles = -1;
    /** The formats it may be downloaded at, with their size in bytes (0 if unknown). */
    final Map<String, Long> downloads = new LinkedHashMap<String, Long>();

    String getSource() {
      return this.library + ":" + this.id;
    }
  }

  /**
   * The files of a model brought from a library, in a folder of their own.
   */
  static final class Download {
    final File   file;
    final File   folder;
    final Model  model;

    Download(File file, File folder, Model model) {
      this.file = file;
      this.folder = folder;
      this.model = model;
    }
  }

  /**
   * A library of 3D models of the web.
   */
  interface Library {
    /**
     * Returns the name that designates this library in commands.
     */
    String getId();

    /**
     * Returns the host this library is asked at, the only one its token is sent to.
     */
    String getHost();

    /**
     * Returns <code>true</code> if models are downloaded with a token of the user.
     */
    boolean isTokenRequired();

    /**
     * Returns the models that match the words of <code>query</code>.
     * @param licenses the short names of the licenses accepted, in lower case, or an empty set for all
     * @param maxTriangles the largest count of triangles accepted, or 0
     */
    List<Model> search(String query, Set<String> licenses, int maxTriangles, int count, Fetcher fetcher) throws IOException;

    /**
     * Downloads the files of the model <code>id</code> in <code>folder</code> and returns the one to import.
     */
    Download download(String id, File folder, String token, String resolution, Fetcher fetcher) throws IOException;
  }

  private final Home                     home;
  private final AssistantCommandExecutor executor;
  private final AssistantObjectCommands  objectCommands;
  private final Map<String, Library>     libraries = new LinkedHashMap<String, Library>();
  private Fetcher                        fetcher = new HttpFetcher();
  private TokenStore                     tokenStore = new FileTokenStore();

  AssistantOnlineModels(Home home, AssistantCommandExecutor executor, AssistantObjectCommands objectCommands) {
    this.home = home;
    this.executor = executor;
    this.objectCommands = objectCommands;
    addLibrary(new Sketchfab());
    addLibrary(new PolyHaven());
  }

  void addLibrary(Library library) {
    this.libraries.put(library.getId(), library);
  }

  void setFetcher(Fetcher fetcher) {
    this.fetcher = fetcher;
  }

  void setTokenStore(TokenStore tokenStore) {
    this.tokenStore = tokenStore;
  }

  /**
   * Returns the lines of the reference of commands that describe the commands of this class.
   */
  static String getReference() {
    return "  {\"action\":\"find_model\",\"query\":\"<words>\",\"library\":\"sketchfab|polyhaven|all\",\"license\":\"cc0|by|by-sa|by-nd|by-nc|by-nc-sa|by-nc-nd\","
        + "\"max_triangles\":N,\"count\":N}  (searches the libraries of 3D models of the web for models that can be downloaded: "
        + "each line gives the \"source\" to import it, its name, its author, its license and what it requires, its count of "
        + "triangles, the size of its download, its page and a preview picture. \"license\" may be a list; Poly Haven models are "
        + "all CC0. 8 models by default, " + MAX_RESULTS + " at most. Needs web services allowed by the user; only the words of the search are sent)\n"
        + "  {\"action\":\"import_model\",\"source\":\"sketchfab:<uid>|polyhaven:<id>|https://...glb\",...the keys of import_furniture except path...}  "
        + "(downloads a model found by find_model, or a .glb, .gltf or .zip file at an https address, and imports it as "
        + "import_furniture does: \"length\", \"front\", \"materials\", \"target_triangles\", x, y... Its page, its author and its "
        + "license are recorded with the piece. Sketchfab downloads need the API token of the account of the user "
        + "(sketchfab.com/settings/password): given once as \"token\" or with set_model_token, it's kept in the folder of the "
        + "preferences of Sweet Home 3D, sent to api.sketchfab.com only, and never repeated in an answer. Poly Haven: "
        + "\"resolution\":\"1k|2k|4k\" chooses the size of the images (1k by default). Files of "
        + (MAX_DOWNLOAD_LENGTH / 1024 / 1024) + " MB at most. Without \"source\", import_model is import_furniture)\n"
        + "  {\"action\":\"set_model_token\",\"library\":\"sketchfab\",\"token\":\"<token>\"}  (keeps the token of the user for the "
        + "downloads of a library; \"remove\":true forgets it. The answer never repeats it)\n"
        + "  {\"action\":\"model_credits\"}  (lists the pieces of the home whose model has an author, a license or a source, "
        + "with what each license requires: attribution, no commercial use, no modified version. export_home writes the same "
        + "list in a CREDITS file beside what it exports)\n";
  }

  /**
   * Runs <code>command</code> if it's one of the commands of this class and
   * returns <code>true</code> in that case.
   */
  boolean apply(String action, AssistantCommand command, int [] tally, List<String> notes) {
    try {
      if ("find_model".equals(action) || "find_models".equals(action) || "search_models".equals(action)) {
        findModels(command, notes);
      } else if (("import_model".equals(action) || "import_furniture".equals(action))
                 && command.getString("source") != null && command.getString("path") == null
                 && isOnlineSource(command.getString("source").trim())) {
        importModel(command, tally, notes);
      } else if ("set_model_token".equals(action)) {
        setToken(command, notes);
      } else if ("model_credits".equals(action) || "credits".equals(action)) {
        this.executor.warnUnknownKeys(command, CREDITS_KEYS);
        notes.add(getCredits(this.home));
      } else {
        return false;
      }
    } catch (IOException ex) {
      notes.add("Couldn't run " + action + " (" + ex.getMessage() + ")");
    }
    return true;
  }

  private boolean isOnlineSource(String source) {
    int colon = source.indexOf(':');
    return colon > 0 && (this.libraries.containsKey(source.substring(0, colon).toLowerCase(Locale.ENGLISH))
        || source.regionMatches(true, 0, "https://", 0, 8) || source.regionMatches(true, 0, "http://", 0, 7));
  }

  private void checkWebAccess(String action, String sent) throws IOException {
    if (!this.executor.isWebAccessAllowed()) {
      throw new IOException(action + " asks web services, which the user didn't allow (it sends " + sent + "). "
          + "They tick \"Allow web services\" in Sweet Home 3D (Tools > Claude MCP server...). Without it, the user "
          + "downloads the model, and import_furniture reads its file");
    }
  }

  // ---- Search ----

  private void findModels(AssistantCommand command, List<String> notes) throws IOException {
    this.executor.warnUnknownKeys(command, FIND_KEYS);
    String query = command.getString("query");
    if (query == null || query.trim().length() == 0) {
      throw new IOException("\"query\" is required: the words that describe the model");
    }
    query = query.trim();
    if (query.length() > 200) {
      query = query.substring(0, 200);
    }
    String libraryName = command.getString("library");
    List<Library> searched = new ArrayList<Library>();
    if (libraryName == null || "all".equalsIgnoreCase(libraryName.trim())) {
      searched.addAll(this.libraries.values());
    } else if (this.libraries.containsKey(libraryName.trim().toLowerCase(Locale.ENGLISH))) {
      searched.add(this.libraries.get(libraryName.trim().toLowerCase(Locale.ENGLISH)));
    } else {
      throw new IOException("\"library\" is one of " + this.libraries.keySet() + " or all");
    }
    Set<String> licenses = new LinkedHashSet<String>();
    List<String> licenseNames = command.getParameters().get("license") instanceof List
        ? command.getStringList("license")
        : command.getString("license") != null ? Arrays.asList(command.getString("license").split("[,|\\s]+")) : new ArrayList<String>();
    for (String license : licenseNames) {
      String code = getLicenseCode(license);
      if ("any".equals(code) || "all".equals(code)) {
        licenses.clear();
        break;
      } else if (code.length() > 0) {
        if (!LICENSE_CODES.contains(code)) {
          throw new IOException("\"license\" is among " + LICENSE_CODES + ", or a list of them");
        }
        licenses.add(code);
      }
    }
    int maxTriangles = (int)Math.max(0, Math.min(Integer.MAX_VALUE, command.getDouble("max_triangles", 0)));
    int count = (int)Math.max(1, Math.min(MAX_RESULTS, command.getDouble("count", 8)));
    checkWebAccess("find_model", "the words of the search to " + getHosts(searched));

    StringBuilder text = new StringBuilder();
    int found = 0;
    List<String> failures = new ArrayList<String>();
    for (Library library : searched) {
      List<Model> models;
      try {
        models = library.search(query, licenses, maxTriangles, count, this.fetcher);
      } catch (IOException ex) {
        failures.add(library.getId() + " couldn't be searched (" + ex.getMessage() + ")");
        continue;
      } catch (RuntimeException ex) {
        failures.add(library.getId() + " gave an answer that couldn't be read");
        continue;
      }
      for (Model model : models) {
        found++;
        text.append("\n- ").append(describe(model));
      }
      if (models.isEmpty()) {
        failures.add(library.getId() + " has no model for these words"
            + (licenses.isEmpty() && maxTriangles == 0 ? "" : " with this license and this count of triangles"));
      }
    }
    StringBuilder note = new StringBuilder(found == 0 ? "No model found for \"" + query + "\""
        : "Models found for \"" + query + "\" (import one with {\"action\":\"import_model\",\"source\":\"<source>\",\"length\":N}):");
    note.append(text);
    for (String failure : failures) {
      note.append(found == 0 ? ": " : "\n").append(failure);
    }
    if (found > 0) {
      note.append("\nThe counts of triangles are the ones the libraries give; \"target_triangles\" simplifies a model when it's "
          + "imported. A license with NC forbids commercial use, with ND modified versions (recoloring or simplifying is one), "
          + "and all but CC0 require to name the author: model_credits lists them.");
    }
    notes.add(note.toString());
  }

  private static String getHosts(List<Library> libraries) {
    List<String> hosts = new ArrayList<String>();
    for (Library library : libraries) {
      hosts.add(library.getHost());
    }
    return hosts.toString();
  }

  private static String describe(Model model) {
    StringBuilder text = new StringBuilder(model.getSource());
    text.append("  \"").append(model.name).append('"');
    if (model.author != null) {
      text.append(" by ").append(model.author);
    }
    text.append(", license ").append(model.license != null ? model.license : "unknown");
    String requirements = getLicenseRequirements(model.license);
    if (requirements.length() > 0) {
      text.append(" (").append(requirements).append(")");
    }
    if (model.triangles >= 0) {
      text.append(", ").append(model.triangles).append(" triangles");
    }
    if (!model.downloads.isEmpty()) {
      text.append(", download ");
      boolean first = true;
      for (Map.Entry<String, Long> download : model.downloads.entrySet()) {
        text.append(first ? "" : " / ").append(download.getKey());
        if (download.getValue().longValue() > 0) {
          text.append(' ').append(formatSize(download.getValue().longValue()));
        }
        first = false;
      }
    }
    if (model.page != null) {
      text.append(", page ").append(model.page);
    }
    if (model.preview != null) {
      text.append(", preview ").append(model.preview);
    }
    return text.toString();
  }

  private static String formatSize(long length) {
    return length >= 1024 * 1024
        ? String.format(Locale.US, "%.1f MB", length / 1024. / 1024.)
        : Math.max(1, length / 1024) + " KB";
  }

  // ---- Licenses ----

  private static final List<String> LICENSE_CODES = Arrays.asList("cc0", "by", "by-sa", "by-nd", "by-nc", "by-nc-sa", "by-nc-nd");

  /**
   * Returns the code of a license written as <code>CC BY-NC</code>, <code>cc-by-nc</code>,
   * <code>CC Attribution-NonCommercial</code>..., or the text in lower case if it isn't a Creative Commons one.
   */
  static String getLicenseCode(String license) {
    if (license == null) {
      return "";
    }
    String text = license.trim().toLowerCase(Locale.ENGLISH);
    if (text.length() == 0) {
      return "";
    }
    if (text.contains("cc0") || text.contains("public domain") || text.contains("cc 0") || text.contains("cc-0")) {
      return "cc0";
    }
    boolean creativeCommons = text.startsWith("cc") || text.startsWith("by") || text.contains("creative commons")
        || text.contains("attribution");
    if (!creativeCommons) {
      return text;
    }
    String words = " " + text.replaceAll("[^a-z]+", " ") + " ";
    StringBuilder code = new StringBuilder("by");
    if (words.contains(" nc ") || words.contains("noncommercial") || words.contains(" non commercial ")) {
      code.append("-nc");
    }
    if (words.contains(" nd ") || words.contains("noderiv") || words.contains(" no deriv")) {
      code.append("-nd");
    } else if (words.contains(" sa ") || words.contains("sharealike") || words.contains(" share alike ")) {
      code.append("-sa");
    }
    return code.toString();
  }

  /**
   * Returns the short name of a license, like <code>CC BY-NC</code>.
   */
  static String getLicenseName(String license) {
    String code = getLicenseCode(license);
    return "cc0".equals(code) ? "CC0"
        : LICENSE_CODES.contains(code) ? "CC " + code.toUpperCase(Locale.ENGLISH)
        : license != null ? license.trim() : null;
  }

  /**
   * Returns what the given license requires, in a few words, or an empty string if it requires nothing or isn't known.
   */
  static String getLicenseRequirements(String license) {
    String code = getLicenseCode(license);
    if ("cc0".equals(code)) {
      return "nothing required";
    } else if (LICENSE_CODES.contains(code)) {
      return "attribution required" + (code.contains("-nc") ? ", no commercial use" : "")
          + (code.contains("-nd") ? ", no modified version" : "")
          + (code.contains("-sa") ? ", modified versions under the same license" : "");
    } else if (code.contains("editorial")) {
      return "editorial use only";
    } else if (code.length() > 0) {
      return "read its terms";
    } else {
      return "";
    }
  }

  // ---- Import ----

  private void importModel(AssistantCommand command, int [] tally, List<String> notes) throws IOException {
    String source = command.getString("source").trim();
    int colon = source.indexOf(':');
    String libraryName = source.substring(0, colon).toLowerCase(Locale.ENGLISH);
    Library library = this.libraries.get(libraryName);
    File folder = createFolder();
    Download download;
    String kept = "";
    if (library != null) {
      String id = source.substring(colon + 1).trim();
      if (!id.matches("[A-Za-z0-9_.-]{1,80}")) {
        throw new IOException("\"source\" is " + libraryName + ":<the id that find_model gives>");
      }
      String token = null;
      if (library.isTokenRequired()) {
        token = command.getString("token");
        if (token != null && token.trim().length() > 0) {
          token = checkToken(token);
          this.tokenStore.setToken(library.getId(), token);
          kept = " The token is kept for the next downloads.";
        } else {
          token = this.tokenStore.getToken(library.getId());
        }
        if (token == null) {
          throw new IOException("the downloads of " + library.getId() + " need the API token of the account of the user, "
              + "who finds it at https://sketchfab.com/settings/password : give it once as \"token\", or with "
              + "{\"action\":\"set_model_token\",\"library\":\"" + library.getId() + "\",\"token\":\"...\"}. It's kept in the "
              + "folder of the preferences of Sweet Home 3D and sent to " + library.getHost() + " only");
        }
      }
      checkWebAccess("import_model", "the id of the model" + (token != null ? " and the token of the user" : "")
          + " to " + library.getHost());
      try {
        download = library.download(id, folder, token, command.getString("resolution"), this.fetcher);
      } catch (RuntimeException ex) {
        throw new IOException(library.getId() + " gave an answer that couldn't be read");
      }
    } else {
      URL url;
      try {
        url = new URL(source);
      } catch (IOException ex) {
        throw new IOException("\"source\" isn't an address");
      }
      if (!"https".equalsIgnoreCase(url.getProtocol())) {
        throw new IOException("\"source\" must be an https address");
      }
      String fileName = new File(url.getPath()).getName();
      if (!fileName.toLowerCase(Locale.ENGLISH).matches(".*\\.(glb|gltf|zip|obj)")) {
        throw new IOException("the address must be the one of a .glb, .gltf, .obj or .zip file");
      }
      checkWebAccess("import_model", "a request for this file to " + url.getHost());
      File file = new File(folder, fileName.replaceAll("[^A-Za-z0-9_.-]", "_"));
      write(file, this.fetcher.fetch(source, null, MAX_DOWNLOAD_LENGTH));
      Model model = new Model();
      model.page = source.indexOf('?') >= 0 ? source.substring(0, source.indexOf('?')) : source;
      download = new Download(file, folder, model);
    }
    File modelFile = download.file;
    if (modelFile.getName().toLowerCase(Locale.ENGLISH).endsWith(".zip")) {
      File extracted = extract(modelFile, folder);
      if (extracted != null) {
        modelFile = extracted;
      }
    }

    // Imported as a file is, with where it comes from
    Map<String, Object> parameters = new LinkedHashMap<String, Object>(command.getParameters());
    for (String key : SOURCE_KEYS) {
      parameters.remove(key);
    }
    parameters.put("action", "import_furniture");
    parameters.put("path", modelFile.getAbsolutePath());
    Model model = download.model;
    if (model.page != null) {
      parameters.put("source", model.page);
    }
    if (model.author != null && command.getString("author") == null) {
      parameters.put("author", model.author);
    }
    if (model.license != null && command.getString("license") == null) {
      parameters.put("license", model.license);
    }
    if (model.name != null && command.getString("name") == null) {
      parameters.put("name", model.name);
    }
    List<String> importNotes = new ArrayList<String>();
    int pieces = tally [0];
    this.objectCommands.apply("import_furniture", new AssistantCommand("import_furniture", parameters), tally, importNotes);
    boolean imported = tally [0] > pieces;
    for (String note : importNotes) {
      notes.add(note.replace(folder.getAbsolutePath() + File.separator, "").replace(folder.getAbsolutePath(), "the download"));
    }
    if (imported) {
      String requirements = getLicenseRequirements(model.license);
      notes.add("Downloaded from " + (library != null ? library.getId() : new URL(source).getHost())
          + (model.license != null ? ": license " + model.license + (requirements.length() > 0 ? " (" + requirements + ")" : "") : "")
          + (model.license != null && getLicenseCode(model.license).contains("-nd")
                && (command.has("materials") || command.has("target_triangles"))
              ? ". WARNING: this license doesn't allow modified versions, and the model was recolored or simplified" : "")
          + "." + kept + " model_credits lists the credits of the home.");
    }
    // The model of the piece is a copy of these files
    if (!modelFile.getName().toLowerCase(Locale.ENGLISH).matches(".*\\.(glb|gltf)")) {
      folder.deleteOnExit();
    } else {
      delete(folder);
    }
  }

  private static String checkToken(String token) throws IOException {
    token = token.trim();
    if (!token.matches("[A-Za-z0-9_.~+/=-]{8,200}")) {
      throw new IOException("the token doesn't look like one: letters and digits, 8 characters at least");
    }
    return token;
  }

  private void setToken(AssistantCommand command, List<String> notes) throws IOException {
    this.executor.warnUnknownKeys(command, TOKEN_KEYS);
    String libraryName = command.getString("library") != null ? command.getString("library").trim().toLowerCase(Locale.ENGLISH) : "";
    Library library = this.libraries.get(libraryName);
    if (library == null || !library.isTokenRequired()) {
      List<String> names = new ArrayList<String>();
      for (Library other : this.libraries.values()) {
        if (other.isTokenRequired()) {
          names.add(other.getId());
        }
      }
      throw new IOException("\"library\" is one of the libraries which need a token: " + names);
    }
    String token = command.getString("token");
    if (command.getBoolean("remove", false) || token == null || token.trim().length() == 0) {
      this.tokenStore.setToken(library.getId(), null);
      notes.add("Forgot the token of " + library.getId() + ".");
    } else {
      this.tokenStore.setToken(library.getId(), checkToken(token));
      notes.add("Kept the token of " + library.getId() + " in the folder of the preferences of Sweet Home 3D: it's sent to "
          + library.getHost() + " only, when a model is downloaded.");
    }
  }

  private static File createFolder() throws IOException {
    File folder = OperatingSystem.createTemporaryFile("model", "");
    if (!folder.delete() || !folder.mkdir()) {
      throw new IOException("can't create a folder for the download");
    }
    return folder;
  }

  private static void write(File file, byte [] data) throws IOException {
    File parent = file.getParentFile();
    if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
      throw new IOException("can't create a folder for the download");
    }
    OutputStream out = new FileOutputStream(file);
    try {
      out.write(data);
    } finally {
      out.close();
    }
  }

  private static void delete(File file) {
    File [] children = file.listFiles();
    if (children != null) {
      for (File child : children) {
        delete(child);
      }
    }
    file.delete();
  }

  /**
   * Extracts the files of <code>archive</code> in <code>folder</code> if it holds a model at glTF or GLB format,
   * and returns that model. Returns <code>null</code> for an archive of an other format, read as it is.
   */
  static File extract(File archive, File folder) throws IOException {
    // Looked through first: an archive with an OBJ, DAE, 3DS or KMZ file is read by import_furniture itself
    List<String> names = new ArrayList<String>();
    ZipInputStream zip = new ZipInputStream(new FileInputStream(archive));
    try {
      for (ZipEntry entry; (entry = zip.getNextEntry()) != null; ) {
        if (!entry.isDirectory()) {
          names.add(entry.getName());
        }
        if (names.size() > MAX_ARCHIVE_ENTRIES) {
          throw new IOException("the archive holds more than " + MAX_ARCHIVE_ENTRIES + " files");
        }
      }
    } finally {
      zip.close();
    }
    String modelName = null;
    for (String name : names) {
      String lowerName = name.toLowerCase(Locale.ENGLISH);
      if ((lowerName.endsWith(".gltf") || lowerName.endsWith(".glb"))
          && !new File(name).getName().startsWith(".") && !lowerName.startsWith("__macosx")
          // The one closest to the root of the archive
          && (modelName == null || name.split("[/\\\\]").length < modelName.split("[/\\\\]").length)) {
        modelName = name;
      }
    }
    if (modelName == null) {
      return null;
    }
    File target = new File(folder, "files");
    String root = target.getCanonicalPath() + File.separator;
    long length = 0;
    File model = null;
    byte [] buffer = new byte [65536];
    zip = new ZipInputStream(new FileInputStream(archive));
    try {
      for (ZipEntry entry; (entry = zip.getNextEntry()) != null; ) {
        if (entry.isDirectory()) {
          continue;
        }
        File file = new File(target, entry.getName().replace('\\', '/'));
        // No file out of the folder, whatever the names written in the archive
        if (!file.getCanonicalPath().startsWith(root)) {
          throw new IOException("the archive holds a file out of its folder");
        }
        file.getParentFile().mkdirs();
        OutputStream out = new FileOutputStream(file);
        try {
          for (int size; (size = zip.read(buffer)) != -1; ) {
            length += size;
            if (length > MAX_EXTRACTED_LENGTH) {
              throw new IOException("the files of the archive are larger than " + (MAX_EXTRACTED_LENGTH / 1024 / 1024) + " MB");
            }
            out.write(buffer, 0, size);
          }
        } finally {
          out.close();
        }
        if (entry.getName().equals(modelName)) {
          model = file;
        }
      }
    } finally {
      zip.close();
    }
    return model;
  }

  // ---- Credits ----

  /**
   * Returns <code>true</code> if a piece of <code>home</code> has a model with an author, a license or a source.
   */
  static boolean hasCredits(Home home) {
    return !getCreditedPieces(home.getFurniture(), new ArrayList<HomePieceOfFurniture>()).isEmpty();
  }

  private static List<HomePieceOfFurniture> getCreditedPieces(List<HomePieceOfFurniture> furniture,
                                                              List<HomePieceOfFurniture> pieces) {
    for (HomePieceOfFurniture piece : furniture) {
      if (piece instanceof HomeFurnitureGroup) {
        getCreditedPieces(((HomeFurnitureGroup)piece).getFurniture(), pieces);
      } else if (piece.getProperty(AssistantObjectCommands.MODEL_SOURCE_PROPERTY) != null
                 || piece.getProperty(AssistantObjectCommands.MODEL_AUTHOR_PROPERTY) != null
                 || piece.getProperty(AssistantObjectCommands.MODEL_LICENSE_PROPERTY) != null) {
        pieces.add(piece);
      }
    }
    return pieces;
  }

  /**
   * Returns the credits of the models of <code>home</code> that have an author, a license or a source:
   * one line per model, with what its license requires.
   */
  static String getCredits(Home home) {
    List<HomePieceOfFurniture> pieces = getCreditedPieces(home.getFurniture(), new ArrayList<HomePieceOfFurniture>());
    if (pieces.isEmpty()) {
      return "No piece of this home has a model with an author, a license or a source recorded "
          + "(import_furniture and import_model record them with \"source\", \"author\" and \"license\").";
    }
    // The pieces that share a model, an author and a license are one line
    Map<String, int []> counts = new LinkedHashMap<String, int []>();
    Map<String, String []> lines = new LinkedHashMap<String, String []>();
    for (HomePieceOfFurniture piece : pieces) {
      String author = piece.getProperty(AssistantObjectCommands.MODEL_AUTHOR_PROPERTY);
      String license = piece.getProperty(AssistantObjectCommands.MODEL_LICENSE_PROPERTY);
      String source = piece.getProperty(AssistantObjectCommands.MODEL_SOURCE_PROPERTY);
      String key = piece.getName() + "\n" + author + "\n" + license + "\n" + source;
      if (!counts.containsKey(key)) {
        counts.put(key, new int [1]);
        lines.put(key, new String [] {piece.getName(), author, license, source});
      }
      counts.get(key) [0]++;
    }
    StringBuilder text = new StringBuilder("Credits of the 3D models of this home:");
    boolean attribution = false;
    boolean nonCommercial = false;
    boolean noDerivatives = false;
    boolean unknown = false;
    for (Map.Entry<String, String []> entry : lines.entrySet()) {
      String [] line = entry.getValue();
      int count = counts.get(entry.getKey()) [0];
      text.append("\n- \"").append(line [0]).append('"').append(count > 1 ? " (" + count + " pieces)" : "");
      text.append(line [1] != null ? " by " + line [1] : ", author unknown");
      if (line [2] != null) {
        String requirements = getLicenseRequirements(line [2]);
        text.append(", license ").append(line [2]).append(requirements.length() > 0 ? " (" + requirements + ")" : "");
        String code = getLicenseCode(line [2]);
        if (LICENSE_CODES.contains(code)) {
          attribution |= !"cc0".equals(code);
          nonCommercial |= code.contains("-nc");
          noDerivatives |= code.contains("-nd");
        } else {
          unknown = true;
        }
      } else {
        text.append(", license unknown");
        unknown = true;
      }
      if (line [3] != null) {
        text.append(", ").append(line [3]);
      }
    }
    if (attribution) {
      text.append("\nThe models with a CC BY license must be credited with their author, their license and their address "
          + "wherever pictures or copies of this home are published.");
    }
    if (nonCommercial) {
      text.append("\nThe models with NC in their license can't be used for a commercial purpose.");
    }
    if (noDerivatives) {
      text.append("\nThe models with ND in their license can't be published in a modified version.");
    }
    if (unknown) {
      text.append("\nThe terms of the models without a known license must be read where they come from.");
    }
    return text.toString();
  }

  // ---- Web ----

  /**
   * Asks web servers with the connections of Java.
   */
  private static final class HttpFetcher implements Fetcher {
    public byte [] fetch(String address, String authorization, long maxLength) throws IOException {
      URL url = new URL(address);
      String host = url.getHost();
      for (int redirections = 0; redirections < 6; redirections++) {
        if (!"https".equalsIgnoreCase(url.getProtocol())) {
          throw new IOException("only https addresses are asked");
        }
        HttpURLConnection connection = (HttpURLConnection)url.openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(120000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("User-Agent", "SweetHome3D");
        connection.setRequestProperty("Accept", "*/*");
        // The token of a library goes to its host only
        if (authorization != null && url.getHost().equalsIgnoreCase(host)) {
          connection.setRequestProperty("Authorization", authorization);
        }
        int code = connection.getResponseCode();
        if (code >= 300 && code < 400 && connection.getHeaderField("Location") != null) {
          url = new URL(url, connection.getHeaderField("Location"));
          connection.disconnect();
          continue;
        }
        if (code == 401 || code == 403) {
          throw new IOException("the server " + url.getHost() + " refused the request (" + code + ")"
              + (authorization != null ? ": the token may be wrong or out of date, or this model can't be downloaded" : ""));
        } else if (code == 404) {
          throw new IOException("the server " + url.getHost() + " knows no such model or file (404)");
        } else if (code == 429) {
          throw new IOException("the server " + url.getHost() + " received too many requests: try again later");
        } else if (code != 200) {
          throw new IOException("the server " + url.getHost() + " answered " + code);
        }
        if (connection.getContentLengthLong() > maxLength) {
          connection.disconnect();
          throw new IOException("the file is larger than " + formatSize(maxLength));
        }
        InputStream in = connection.getInputStream();
        try {
          ByteArrayOutputStream out = new ByteArrayOutputStream();
          byte [] buffer = new byte [65536];
          for (int size; (size = in.read(buffer)) != -1; ) {
            out.write(buffer, 0, size);
            if (out.size() > maxLength) {
              throw new IOException("the file is larger than " + formatSize(maxLength));
            }
          }
          return out.toByteArray();
        } finally {
          in.close();
        }
      }
      throw new IOException("the server " + host + " redirects too many times");
    }
  }

  /**
   * Keeps the tokens in a file of the folder of the preferences of the application,
   * that its owner only can read.
   */
  private static final class FileTokenStore implements TokenStore {
    private static final String FILE_NAME = "model-library-tokens.properties";

    private File getFile() throws IOException {
      return new File(OperatingSystem.getDefaultApplicationFolder(), FILE_NAME);
    }

    private Properties read() throws IOException {
      Properties properties = new Properties();
      File file = getFile();
      if (file.isFile()) {
        InputStream in = new FileInputStream(file);
        try {
          properties.load(in);
        } finally {
          in.close();
        }
      }
      return properties;
    }

    public String getToken(String library) throws IOException {
      String token = read().getProperty(library);
      return token != null && token.trim().length() > 0 ? token.trim() : null;
    }

    public void setToken(String library, String token) throws IOException {
      Properties properties = read();
      if (token == null) {
        properties.remove(library);
      } else {
        properties.setProperty(library, token);
      }
      File file = getFile();
      if (properties.isEmpty()) {
        file.delete();
        return;
      }
      file.getParentFile().mkdirs();
      // Restricted to its owner before the secret is written
      file.delete();
      if (!file.createNewFile()) {
        throw new IOException("can't write the file of the tokens");
      }
      file.setReadable(false, false);
      file.setWritable(false, false);
      file.setReadable(true, true);
      file.setWritable(true, true);
      OutputStream out = new FileOutputStream(file);
      try {
        properties.store(out, "Tokens of the libraries of 3D models, sent to their own servers only");
      } finally {
        out.close();
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> map(Object value) {
    return value instanceof Map ? (Map<String, Object>)value : Collections.<String, Object>emptyMap();
  }

  @SuppressWarnings("unchecked")
  private static List<Object> list(Object value) {
    return value instanceof List ? (List<Object>)value : Collections.emptyList();
  }

  private static String string(Object value) {
    return value instanceof String && ((String)value).trim().length() > 0 ? ((String)value).trim() : null;
  }

  private static long number(Object value) {
    return value instanceof Number ? ((Number)value).longValue() : 0;
  }

  private static Object parse(byte [] answer) throws IOException {
    try {
      return JSONParser.parse(new String(answer, "UTF-8"));
    } catch (IllegalArgumentException ex) {
      throw new IOException("the answer of the server isn't written in JSON");
    }
  }

  // ---- Sketchfab ----

  /**
   * The library of sketchfab.com, searched without an account and downloaded with the token of one.
   */
  static final class Sketchfab implements Library {
    private static final String API = "https://api.sketchfab.com/v3/";

    public String getId() {
      return "sketchfab";
    }

    public String getHost() {
      return "api.sketchfab.com";
    }

    public boolean isTokenRequired() {
      return true;
    }

    public List<Model> search(String query, Set<String> licenses, int maxTriangles, int count, Fetcher fetcher) throws IOException {
      StringBuilder address = new StringBuilder(API + "search?type=models&downloadable=true&q=" + URLEncoder.encode(query, "UTF-8"));
      // The service takes one license: several are sorted out here, among more answers
      if (licenses.size() == 1) {
        address.append("&license=").append(licenses.iterator().next());
      }
      if (maxTriangles > 0) {
        address.append("&max_face_count=").append(maxTriangles);
      }
      address.append("&count=").append(licenses.size() > 1 ? MAX_RESULTS : count);
      Map<String, Object> answer = map(parse(fetcher.fetch(address.toString(), null, MAX_ANSWER_LENGTH)));
      List<Model> models = new ArrayList<Model>();
      for (Object result : list(answer.get("results"))) {
        Model model = readModel(map(result));
        if (model != null && Boolean.TRUE.equals(map(result).get("isDownloadable"))
            && (licenses.isEmpty() || licenses.contains(getLicenseCode(model.license)))
            && (maxTriangles <= 0 || model.triangles < 0 || model.triangles <= maxTriangles)
            && models.size() < count) {
          models.add(model);
        }
      }
      return models;
    }

    private Model readModel(Map<String, Object> result) {
      String uid = string(result.get("uid"));
      if (uid == null || !uid.matches("[A-Za-z0-9]{8,64}")) {
        return null;
      }
      Model model = new Model();
      model.library = getId();
      model.id = uid;
      model.name = string(result.get("name")) != null ? string(result.get("name")) : uid;
      Map<String, Object> user = map(result.get("user"));
      model.author = string(user.get("displayName")) != null ? string(user.get("displayName")) : string(user.get("username"));
      if (model.author != null && string(user.get("profileUrl")) != null) {
        model.author += " (" + string(user.get("profileUrl")) + ")";
      }
      Map<String, Object> license = map(result.get("license"));
      model.license = getLicenseName(string(license.get("label")) != null ? string(license.get("label")) : string(license.get("slug")));
      model.page = string(result.get("viewerUrl")) != null ? string(result.get("viewerUrl")) : "https://sketchfab.com/3d-models/" + uid;
      model.triangles = result.get("faceCount") instanceof Number ? ((Number)result.get("faceCount")).intValue() : -1;
      // The picture closest to 256 pixels wide
      long bestWidth = 0;
      for (Object item : list(map(result.get("thumbnails")).get("images"))) {
        Map<String, Object> image = map(item);
        long width = number(image.get("width"));
        if (string(image.get("url")) != null
            && (model.preview == null || Math.abs(width - 256) < Math.abs(bestWidth - 256))) {
          model.preview = string(image.get("url"));
          bestWidth = width;
        }
      }
      Map<String, Object> archives = map(result.get("archives"));
      for (String format : new String [] {"glb", "gltf"}) {
        Map<String, Object> archive = map(archives.get(format));
        if (!archive.isEmpty()) {
          model.downloads.put(format, Long.valueOf(number(archive.get("size"))));
          if (model.triangles < 0 && archive.get("faceCount") instanceof Number) {
            model.triangles = ((Number)archive.get("faceCount")).intValue();
          }
        }
      }
      return model;
    }

    public Download download(String id, File folder, String token, String resolution, Fetcher fetcher) throws IOException {
      if (!id.matches("[A-Za-z0-9]{8,64}")) {
        throw new IOException("the id of a Sketchfab model is the series of letters and digits that find_model gives");
      }
      Model model = readModel(map(parse(fetcher.fetch(API + "models/" + id, null, MAX_ANSWER_LENGTH))));
      if (model == null) {
        throw new IOException("Sketchfab knows no model " + id);
      }
      Map<String, Object> links = map(parse(fetcher.fetch(API + "models/" + id + "/download", "Token " + token, MAX_ANSWER_LENGTH)));
      // One file when there is one, otherwise the archive of the .gltf file, its data and its images
      for (String format : new String [] {"glb", "gltf"}) {
        Map<String, Object> link = map(links.get(format));
        String address = string(link.get("url"));
        if (address != null) {
          long size = number(link.get("size"));
          if (size > MAX_DOWNLOAD_LENGTH) {
            if ("glb".equals(format) && string(map(links.get("gltf")).get("url")) != null
                && number(map(links.get("gltf")).get("size")) <= MAX_DOWNLOAD_LENGTH) {
              continue;
            }
            throw new IOException("the download of this model is " + formatSize(size) + ", more than the "
                + formatSize(MAX_DOWNLOAD_LENGTH) + " accepted");
          }
          // The address of the file is signed for this download: the token isn't sent to its host
          File file = new File(folder, "glb".equals(format) ? "model.glb" : "model.zip");
          write(file, fetcher.fetch(address, null, MAX_DOWNLOAD_LENGTH));
          return new Download(file, folder, model);
        }
      }
      throw new IOException("Sketchfab gives no glTF file for this model: it may not be downloadable");
    }
  }

  // ---- Poly Haven ----

  /**
   * The library of polyhaven.com, whose models are all in the public domain (CC0).
   */
  static final class PolyHaven implements Library {
    private static final String API = "https://api.polyhaven.com/";
    private Map<String, Object> assets;

    public String getId() {
      return "polyhaven";
    }

    public String getHost() {
      return "api.polyhaven.com";
    }

    public boolean isTokenRequired() {
      return false;
    }

    private Map<String, Object> getAssets(Fetcher fetcher) throws IOException {
      // The service gives its whole list of models at once, sorted out here
      if (this.assets == null) {
        this.assets = map(parse(fetcher.fetch(API + "assets?t=models", null, MAX_ANSWER_LENGTH)));
      }
      return this.assets;
    }

    private Model readModel(String id, Map<String, Object> asset) {
      Model model = new Model();
      model.library = getId();
      model.id = id;
      model.name = string(asset.get("name")) != null ? string(asset.get("name")) : id;
      List<String> authors = new ArrayList<String>(map(asset.get("authors")).keySet());
      model.author = authors.isEmpty() ? "Poly Haven" : authors.toString().replace("[", "").replace("]", "");
      model.license = "CC0";
      model.page = "https://polyhaven.com/a/" + id;
      model.preview = string(asset.get("thumbnail_url")) != null
          ? string(asset.get("thumbnail_url"))
          : "https://cdn.polyhaven.com/asset_img/thumbs/" + id + ".png?height=256";
      model.triangles = asset.get("polycount") instanceof Number ? ((Number)asset.get("polycount")).intValue() : -1;
      model.downloads.put("gltf", Long.valueOf(0));
      return model;
    }

    public List<Model> search(String query, Set<String> licenses, int maxTriangles, int count, Fetcher fetcher) throws IOException {
      List<Model> models = new ArrayList<Model>();
      if (!licenses.isEmpty() && !licenses.contains("cc0")) {
        return models;
      }
      String [] words = query.toLowerCase(Locale.ENGLISH).split("[^a-z0-9]+");
      final Map<Model, double []> scores = new LinkedHashMap<Model, double []>();
      for (Map.Entry<String, Object> entry : getAssets(fetcher).entrySet()) {
        Map<String, Object> asset = map(entry.getValue());
        if (!entry.getKey().matches("[A-Za-z0-9_.-]{1,80}")) {
          continue;
        }
        String title = (entry.getKey() + " " + asset.get("name")).toLowerCase(Locale.ENGLISH);
        String details = (list(asset.get("tags")) + " " + list(asset.get("categories"))).toLowerCase(Locale.ENGLISH);
        double score = 0;
        int matched = 0;
        for (String word : words) {
          if (word.length() == 0) {
            continue;
          }
          // The singular of a word matches too
          String stem = word.length() > 3 && word.endsWith("s") ? word.substring(0, word.length() - 1) : word;
          if (title.contains(stem)) {
            score += 3;
            matched++;
          } else if (details.contains(stem)) {
            score += 1;
            matched++;
          }
        }
        Model model = readModel(entry.getKey(), asset);
        if (matched > 0 && (maxTriangles <= 0 || model.triangles < 0 || model.triangles <= maxTriangles)) {
          // The models that match more words first, then the ones downloaded most
          scores.put(model, new double [] {matched * 100 + score, number(asset.get("download_count"))});
        }
      }
      models.addAll(scores.keySet());
      Collections.sort(models, new Comparator<Model>() {
          public int compare(Model model1, Model model2) {
            double [] score1 = scores.get(model1);
            double [] score2 = scores.get(model2);
            return score1 [0] != score2 [0] ? Double.compare(score2 [0], score1 [0]) : Double.compare(score2 [1], score1 [1]);
          }
        });
      return models.size() > count ? new ArrayList<Model>(models.subList(0, count)) : models;
    }

    public Download download(String id, File folder, String token, String resolution, Fetcher fetcher) throws IOException {
      Map<String, Object> asset = map(getAssets(fetcher).get(id));
      if (asset.isEmpty()) {
        throw new IOException("Poly Haven has no model \"" + id + "\": find_model gives their ids");
      }
      Map<String, Object> formats = map(map(parse(fetcher.fetch(API + "files/" + id, null, MAX_ANSWER_LENGTH))).get("gltf"));
      if (formats.isEmpty()) {
        throw new IOException("Poly Haven gives no glTF file for " + id);
      }
      String size = resolution != null ? resolution.trim().toLowerCase(Locale.ENGLISH) : "1k";
      if (!formats.containsKey(size)) {
        if (resolution != null) {
          throw new IOException("\"resolution\" is among " + formats.keySet() + " for this model");
        }
        size = formats.keySet().iterator().next();
        for (String other : formats.keySet()) {
          // The smallest images
          if (other.compareTo(size) < 0 && other.length() <= size.length() || other.length() < size.length()) {
            size = other;
          }
        }
      }
      Map<String, Object> gltf = map(map(formats.get(size)).get("gltf"));
      String address = string(gltf.get("url"));
      if (address == null) {
        throw new IOException("Poly Haven gives no glTF file for " + id);
      }
      byte [] gltfData = fetcher.fetch(address, null, MAX_ANSWER_LENGTH * 4L);
      File file = new File(folder, "model.gltf");
      write(file, gltfData);
      // The data of the model and the images of its colors: the other images aren't read
      Set<String> needed = getNeededFiles(map(parse(gltfData)));
      Map<String, Object> included = map(gltf.get("include"));
      String root = folder.getCanonicalPath() + File.separator;
      long length = gltfData.length;
      for (String uri : needed) {
        Map<String, Object> link = map(included.get(uri));
        String fileAddress = string(link.get("url"));
        if (fileAddress == null) {
          continue;
        }
        length += number(link.get("size"));
        if (length > MAX_DOWNLOAD_LENGTH) {
          throw new IOException("the files of this model are larger than " + formatSize(MAX_DOWNLOAD_LENGTH)
              + ": ask for a smaller \"resolution\"");
        }
        File target = new File(folder, uri);
        if (!target.getCanonicalPath().startsWith(root)) {
          throw new IOException("the model names a file out of its folder");
        }
        write(target, fetcher.fetch(fileAddress, null, MAX_DOWNLOAD_LENGTH));
      }
      return new Download(file, folder, readModel(id, asset));
    }

    /**
     * Returns the files beside a glTF file that hold its data and the images of the colors of its materials.
     */
    static Set<String> getNeededFiles(Map<String, Object> gltf) {
      Set<String> files = new LinkedHashSet<String>();
      for (Object buffer : list(gltf.get("buffers"))) {
        addFile(files, map(buffer).get("uri"));
      }
      List<Object> textures = list(gltf.get("textures"));
      List<Object> images = list(gltf.get("images"));
      for (Object item : list(gltf.get("materials"))) {
        Map<String, Object> material = map(item);
        Object colorTexture = map(material.get("pbrMetallicRoughness")).get("baseColorTexture");
        if (colorTexture == null) {
          colorTexture = map(map(material.get("extensions")).get("KHR_materials_pbrSpecularGlossiness")).get("diffuseTexture");
        }
        Object index = map(colorTexture).get("index");
        if (index instanceof Number && ((Number)index).intValue() >= 0 && ((Number)index).intValue() < textures.size()) {
          Object source = map(textures.get(((Number)index).intValue())).get("source");
          if (source instanceof Number && ((Number)source).intValue() >= 0 && ((Number)source).intValue() < images.size()) {
            addFile(files, map(images.get(((Number)source).intValue())).get("uri"));
          }
        }
      }
      return files;
    }

    private static void addFile(Set<String> files, Object uri) {
      if (uri instanceof String && !((String)uri).startsWith("data:") && ((String)uri).indexOf("://") < 0) {
        try {
          files.add(java.net.URLDecoder.decode(((String)uri).replace("+", "%2B"), "UTF-8"));
        } catch (IOException ex) {
          files.add((String)uri);
        } catch (IllegalArgumentException ex) {
          files.add((String)uri);
        }
      }
    }
  }
}
