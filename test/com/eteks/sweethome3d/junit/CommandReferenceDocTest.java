/*
 * CommandReferenceDocTest.java 20 nov. 2026
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

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

import com.eteks.sweethome3d.viewcontroller.HomeAssistantContext;

/**
 * Checks that the guides follow the reference of commands given to assistants.
 */
public class CommandReferenceDocTest {
  /**
   * Every command of the reference is described in docs/MCP_COMMANDS.md.
   */
  @Test
  public void testEveryCommandIsDocumented() throws Exception {
    File guide = new File("docs/MCP_COMMANDS.md");
    assertTrue("Tests run from the root of the repository", guide.isFile());
    String text = new String(Files.readAllBytes(guide.toPath()), Charset.forName("UTF-8"));
    Set<String> missing = new TreeSet<String>();
    Matcher matcher = Pattern.compile("\\{\"action\":\"(\\w+)\"").matcher(HomeAssistantContext.getCommandReference());
    int count = 0;
    while (matcher.find()) {
      count++;
      if (!text.contains("`" + matcher.group(1) + "`")) {
        missing.add(matcher.group(1));
      }
    }
    assertTrue("The reference of commands lists commands", count > 50);
    assertTrue("Commands missing in docs/MCP_COMMANDS.md: " + missing, missing.isEmpty());
  }
}
