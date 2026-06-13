package com.eteks.sweethome3d.junit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Enumeration;

import javax.media.j3d.Appearance;
import javax.media.j3d.Group;
import javax.media.j3d.Node;
import javax.media.j3d.RenderingAttributes;
import javax.media.j3d.Shape3D;

import org.junit.Test;

import com.eteks.sweethome3d.j3d.Object3DBranch;
import com.eteks.sweethome3d.j3d.Object3DBranchFactory;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.Wall;

/**
 * Guards the Java 3D capability tuning of the 3D scene objects: writable
 * appearance-component capabilities must be declared infrequently changed.
 * Java 3D marks every capability "frequently changed" by default, which makes
 * its renderer match attribute/texture bins by object identity instead of by
 * value; with per-shape attribute instances no bin ever matches, so every
 * render-atom insertion scans and then grows the bin list. On the 435-piece
 * reference home this list walk was the dominant frame-time cost (the
 * RenderBin.findAttributeBin hotspot in docs/OPTIMIZATION_PROGRESS.md).
 */
public class Object3DBranchCapabilityTest {
  /**
   * The helper must set the capability and clear its is-frequent hint.
   */
  @Test
  public void testSetCapabilityInfrequent() {
    RenderingAttributes attributes = new RenderingAttributes();
    Object3DBranch.setCapabilityInfrequent(attributes, RenderingAttributes.ALLOW_VISIBLE_WRITE);
    assertTrue("Capability is set",
        attributes.getCapability(RenderingAttributes.ALLOW_VISIBLE_WRITE));
    assertFalse("Capability is not marked frequently changed",
        attributes.getCapabilityIsFrequent(RenderingAttributes.ALLOW_VISIBLE_WRITE));
  }

  /**
   * Every rendering-attributes instance reachable from a wall's 3D branch with
   * a writable visible flag must carry the infrequent hint, so render bins keep
   * matching by value.
   */
  @Test
  public void testWall3DRenderingAttributesAreInfrequent() {
    Home home = new Home();
    Wall wall = new Wall(0, 0, 400, 0, 7.5f, 250);
    home.addWall(wall);
    Node node = (Node)new Object3DBranchFactory().createObject3D(home, wall, true);
    int [] checked = new int [1];
    checkRenderingAttributes(node, checked);
    assertTrue("Wall branch exposes writable rendering attributes", checked [0] > 0);
  }

  private void checkRenderingAttributes(Node node, int [] checked) {
    if (node instanceof Group) {
      Enumeration<?> children = ((Group)node).getAllChildren();
      while (children.hasMoreElements()) {
        checkRenderingAttributes((Node)children.nextElement(), checked);
      }
    } else if (node instanceof Shape3D) {
      Appearance appearance = ((Shape3D)node).getAppearance();
      if (appearance != null) {
        RenderingAttributes attributes = appearance.getRenderingAttributes();
        if (attributes != null
            && attributes.getCapability(RenderingAttributes.ALLOW_VISIBLE_WRITE)) {
          assertFalse("Writable visible flag must be infrequent",
              attributes.getCapabilityIsFrequent(RenderingAttributes.ALLOW_VISIBLE_WRITE));
          checked [0]++;
        }
      }
    }
  }
}
