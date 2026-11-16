/*
 * WebFolderLaunchers.java 16 Nov 2026
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
package com.eteks.sweethome3d.tools;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Writes in a folder of web pages the small programs that show it in a
 * browser without any web server to install: browsers refuse to load a 3D
 * scene from a page opened from the disk, so each launcher starts a web
 * server limited to this computer and this folder, with what its system
 * already has (PowerShell under Windows; Python, Ruby or PHP under macOS and
 * Linux), then opens the page.
 * @author Sweet Home 3D
 */
public final class WebFolderLaunchers {
  public static final String WINDOWS_LAUNCHER = "View in browser (Windows).bat";
  public static final String WINDOWS_SERVER = "view-in-browser.ps1";
  public static final String MAC_LAUNCHER = "View in browser (macOS).command";
  public static final String UNIX_LAUNCHER = "view-in-browser.sh";

  private WebFolderLaunchers() {
  }

  /**
   * Writes the launchers in <code>folder</code>, which contains the given page.
   */
  public static void write(File folder, String page) throws IOException {
    if (!page.matches("[A-Za-z0-9_.-]+")) {
      throw new IOException("Unexpected page name");
    }
    writeFile(new File(folder, WINDOWS_LAUNCHER), getWindowsLauncher(), "\r\n");
    writeFile(new File(folder, WINDOWS_SERVER), getWindowsServer(page), "\r\n");
    File unixLauncher = new File(folder, UNIX_LAUNCHER);
    writeFile(unixLauncher, getUnixLauncher(page), "\n");
    File macLauncher = new File(folder, MAC_LAUNCHER);
    writeFile(macLauncher, getUnixLauncher(page), "\n");
    // Double-clicked under macOS and Linux, if the folder keeps this permission when it's copied
    unixLauncher.setExecutable(true, false);
    macLauncher.setExecutable(true, false);
  }

  /**
   * Returns the lines to add to the notice of a folder about its launchers.
   */
  public static String getNotice() {
    return "To see it on a computer, without any web server to install:\r\n"
        + "- Windows: double-click \"" + WINDOWS_LAUNCHER + "\".\r\n"
        + "- macOS: double-click \"" + MAC_LAUNCHER + "\" (the first time: right-click > Open).\r\n"
        + "- Linux, or macOS from the Terminal: run  sh " + UNIX_LAUNCHER + "  in this folder.\r\n"
        + "A window opens and says at which address the folder is shown, the browser opens that\r\n"
        + "address, and closing the window stops it. Nothing is installed, and the folder is only\r\n"
        + "shown to this computer. Windows uses PowerShell, which it always has; macOS and Linux\r\n"
        + "use Python 3, Ruby or PHP, whichever is there.\r\n";
  }

  private static void writeFile(File file, String text, String lineSeparator) throws IOException {
    OutputStream out = new FileOutputStream(file);
    try {
      out.write(text.replace("\r\n", "\n").replace("\n", lineSeparator).getBytes("UTF-8"));
    } finally {
      out.close();
    }
  }

  static String getWindowsLauncher() {
    return "@echo off\n"
        + "rem Shows this folder in the browser: starts a web server limited to this computer and this folder.\n"
        + "title 3D home - close this window to stop\n"
        + "powershell -NoProfile -ExecutionPolicy Bypass -File \"%~dp0" + WINDOWS_SERVER + "\"\n"
        + "if errorlevel 1 pause\n";
  }

  static String getWindowsServer(String page) {
    return "# Shows the folder of this script in the browser: a web server for this computer only,\n"
        + "# which serves the files of this folder and nothing else. Close the window to stop it.\n"
        + "param([int]$Port = 8765)\n"
        + "$root = [IO.Path]::GetFullPath((Split-Path -Parent $MyInvocation.MyCommand.Path))\n"
        + "$listener = $null\n"
        + "for ($tried = $Port; $tried -lt $Port + 30 -and -not $listener; $tried++) {\n"
        + "  try {\n"
        + "    $candidate = New-Object System.Net.HttpListener\n"
        + "    $candidate.Prefixes.Add(\"http://localhost:$tried/\")\n"
        + "    $candidate.Start()\n"
        + "    $listener = $candidate\n"
        + "    $Port = $tried\n"
        + "  } catch {\n"
        + "    # Port in use: try the next one\n"
        + "  }\n"
        + "}\n"
        + "if (-not $listener) {\n"
        + "  Write-Host \"No free port to show the home between $Port and $($Port + 29).\"\n"
        + "  exit 1\n"
        + "}\n"
        + "$types = @{'.html' = 'text/html; charset=utf-8'; '.js' = 'text/javascript; charset=utf-8'; '.mjs' = 'text/javascript; charset=utf-8';\n"
        + "  '.json' = 'application/json'; '.obj' = 'text/plain'; '.mtl' = 'text/plain'; '.txt' = 'text/plain; charset=utf-8';\n"
        + "  '.png' = 'image/png'; '.jpg' = 'image/jpeg'; '.jpeg' = 'image/jpeg'; '.gif' = 'image/gif'; '.bmp' = 'image/bmp'; '.svg' = 'image/svg+xml'}\n"
        + "$address = \"http://localhost:$Port/" + page + "\"\n"
        + "Write-Host \"The home is shown at $address\"\n"
        + "Write-Host 'Close this window to stop.'\n"
        + "Start-Process $address\n"
        + "while ($listener.IsListening) {\n"
        + "  $context = $listener.GetContext()\n"
        + "  $response = $context.Response\n"
        + "  try {\n"
        + "    $path = [Uri]::UnescapeDataString($context.Request.Url.AbsolutePath).TrimStart('/')\n"
        + "    if ($path -eq '') { $path = '" + page + "' }\n"
        + "    $file = [IO.Path]::GetFullPath((Join-Path $root $path))\n"
        + "    $method = $context.Request.HttpMethod\n"
        + "    if ($method -ne 'GET' -and $method -ne 'HEAD') {\n"
        + "      $response.StatusCode = 405\n"
        + "    } elseif ($file.StartsWith($root + [IO.Path]::DirectorySeparatorChar) -and (Test-Path -LiteralPath $file -PathType Leaf)) {\n"
        + "      $type = $types[[IO.Path]::GetExtension($file).ToLower()]\n"
        + "      if ($type) { $response.ContentType = $type }\n"
        + "      $stream = [IO.File]::OpenRead($file)\n"
        + "      try {\n"
        + "        $response.ContentLength64 = $stream.Length\n"
        + "        if ($method -eq 'GET') { $stream.CopyTo($response.OutputStream) }\n"
        + "      } finally {\n"
        + "        $stream.Close()\n"
        + "      }\n"
        + "    } else {\n"
        + "      $response.StatusCode = 404\n"
        + "    }\n"
        + "  } catch {\n"
        + "    # A page closed while it was loading\n"
        + "  } finally {\n"
        + "    try { $response.Close() } catch { }\n"
        + "  }\n"
        + "}\n";
  }

  static String getUnixLauncher(String page) {
    return "#!/bin/sh\n"
        + "# Shows this folder in the browser: starts a web server limited to this computer and this folder,\n"
        + "# with Python 3, Ruby or PHP, whichever this computer has. Ctrl+C or closing the window stops it.\n"
        + "cd \"$(dirname \"$0\")\" || exit 1\n"
        + "PORT=${1:-$((8700 + $$ % 200))}\n"
        + "ADDRESS=\"http://localhost:$PORT/" + page + "\"\n"
        + "open_browser() {\n"
        + "  sleep 1\n"
        + "  if command -v open >/dev/null 2>&1; then\n"
        + "    open \"$ADDRESS\"\n"
        + "  elif command -v xdg-open >/dev/null 2>&1; then\n"
        + "    xdg-open \"$ADDRESS\" >/dev/null 2>&1\n"
        + "  fi\n"
        + "}\n"
        + "echo \"The home is shown at $ADDRESS\"\n"
        + "echo \"Press Ctrl+C or close this window to stop.\"\n"
        + "if python3 -c \"import http.server\" >/dev/null 2>&1; then\n"
        + "  open_browser &\n"
        + "  exec python3 -m http.server \"$PORT\" --bind 127.0.0.1\n"
        + "elif command -v ruby >/dev/null 2>&1; then\n"
        + "  open_browser &\n"
        + "  exec ruby -run -e httpd . -p \"$PORT\" -b 127.0.0.1\n"
        + "elif command -v php >/dev/null 2>&1; then\n"
        + "  open_browser &\n"
        + "  exec php -S \"127.0.0.1:$PORT\"\n"
        + "else\n"
        + "  echo \"This computer has neither Python 3, Ruby nor PHP to show the folder.\"\n"
        + "  echo \"Install Python 3 (python.org), or put the folder on a web site.\"\n"
        + "  exit 1\n"
        + "fi\n";
  }
}
