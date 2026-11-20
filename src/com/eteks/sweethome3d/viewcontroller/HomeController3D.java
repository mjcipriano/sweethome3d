/*
 * HomeController3D.java 21 juin 07
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
package com.eteks.sweethome3d.viewcontroller;

import java.awt.geom.Point2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.swing.undo.UndoableEditSupport;

import com.eteks.sweethome3d.model.Camera;
import com.eteks.sweethome3d.model.CollectionEvent;
import com.eteks.sweethome3d.model.CollectionListener;
import com.eteks.sweethome3d.model.DimensionLine;
import com.eteks.sweethome3d.model.Elevatable;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomeEnvironment;
import com.eteks.sweethome3d.model.HomeFurnitureGroup;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Label;
import com.eteks.sweethome3d.model.Level;
import com.eteks.sweethome3d.model.ObserverCamera;
import com.eteks.sweethome3d.model.Polyline;
import com.eteks.sweethome3d.model.Room;
import com.eteks.sweethome3d.model.Selectable;
import com.eteks.sweethome3d.model.SelectionEvent;
import com.eteks.sweethome3d.model.SelectionListener;
import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.model.Wall;

/**
 * A MVC controller for the home 3D view.
 * @author Emmanuel Puybaret
 */
public class HomeController3D implements Controller {
  private final Home                  home;
  private final UserPreferences       preferences;
  private final ViewFactory           viewFactory;
  private final ContentManager        contentManager;
  private final UndoableEditSupport   undoSupport;
  private PlanController              planController;
  private View                        home3DView;
  // Possibles states
  private final CameraControllerState topCameraState;
  private final CameraControllerState observerCameraState;
  // Current state
  private CameraControllerState       cameraState;
  private float                       viewAspectRatio = 4f / 3;

  /**
   * Creates the controller of home 3D view.
   * @param home the home edited by this controller and its view
   */
  public HomeController3D(final Home home,
                          UserPreferences preferences,
                          ViewFactory viewFactory,
                          ContentManager contentManager,
                          UndoableEditSupport undoSupport) {
    this.home = home;
    this.preferences = preferences;
    this.viewFactory = viewFactory;
    this.contentManager = contentManager;
    this.undoSupport = undoSupport;
    // Initialize states
    this.topCameraState = new TopCameraState(preferences);
    this.observerCameraState = new ObserverCameraState();
    // Set default state
    setCameraState(home.getCamera() == home.getTopCamera()
        ? this.topCameraState
        : this.observerCameraState);
    addModelListeners(home);
  }

  /**
   * Creates the controller of home 3D view.
   * @param home the home edited by this controller and its view
   * @since 7.2
   */
  public HomeController3D(final Home home,
                          PlanController planController,
                          UserPreferences preferences,
                          ViewFactory viewFactory,
                          ContentManager contentManager,
                          UndoableEditSupport undoSupport) {
    // Constructor erased in JS viewer
    this(home, preferences, viewFactory, contentManager, undoSupport);
    this.planController = planController;
  }

  /**
   * Add listeners to model to update camera position accordingly.
   */
  private void addModelListeners(final Home home) {
    home.addPropertyChangeListener(Home.Property.CAMERA, new PropertyChangeListener() {
        public void propertyChange(PropertyChangeEvent ev) {
          setCameraState(home.getCamera() == home.getTopCamera()
              ? topCameraState
              : observerCameraState);
        }
      });
    // Add listeners to adjust observer camera elevation when the elevation of the selected level
    // or the level selection change
    final PropertyChangeListener levelElevationChangeListener = new PropertyChangeListener() {
        public void propertyChange(PropertyChangeEvent ev) {
          if (Level.Property.ELEVATION.name().equals(ev.getPropertyName())
              && home.getEnvironment().isObserverCameraElevationAdjusted()) {
            home.getObserverCamera().setZ(Math.max(getObserverCameraMinimumElevation(home),
                home.getObserverCamera().getZ() + (Float)ev.getNewValue() - (Float)ev.getOldValue()));
          }
        }
      };
    Level selectedLevel = home.getSelectedLevel();
    if (selectedLevel != null) {
      selectedLevel.addPropertyChangeListener(levelElevationChangeListener);
    }
    home.addPropertyChangeListener(Home.Property.SELECTED_LEVEL, new PropertyChangeListener() {
        public void propertyChange(PropertyChangeEvent ev) {
          Level oldSelectedLevel = (Level)ev.getOldValue();
          Level selectedLevel = home.getSelectedLevel();
          if (home.getEnvironment().isObserverCameraElevationAdjusted()) {
            home.getObserverCamera().setZ(Math.max(getObserverCameraMinimumElevation(home),
                home.getObserverCamera().getZ()
                + (selectedLevel == null ? 0 : selectedLevel.getElevation())
                - (oldSelectedLevel == null ? 0 : oldSelectedLevel.getElevation())));
          }
          if (oldSelectedLevel != null) {
            oldSelectedLevel.removePropertyChangeListener(levelElevationChangeListener);
          }
          if (selectedLevel != null) {
            selectedLevel.addPropertyChangeListener(levelElevationChangeListener);
          }
        }
      });
    // Add a listener to home to update visible levels according to selected level
    PropertyChangeListener selectedLevelListener = new PropertyChangeListener() {
         public void propertyChange(PropertyChangeEvent ev) {
           List<Level> levels = home.getLevels();
           Level selectedLevel = home.getSelectedLevel();
           boolean visible = true;
           for (int i = 0; i < levels.size(); i++) {
             levels.get(i).setVisible(visible);
             if (levels.get(i) == selectedLevel
                 && !home.getEnvironment().isAllLevelsVisible()) {
               visible = false;
             }
           }
         }
       };
    home.addPropertyChangeListener(Home.Property.SELECTED_LEVEL, selectedLevelListener);
    home.getEnvironment().addPropertyChangeListener(HomeEnvironment.Property.ALL_LEVELS_VISIBLE, selectedLevelListener);
  }

  private float getObserverCameraMinimumElevation(final Home home) {
    List<Level> levels = home.getLevels();
    float minimumElevation = levels.size() == 0  ? 10  : 10 + levels.get(0).getElevation();
    return minimumElevation;
  }

  /**
   * Returns the view associated with this controller.
   */
  public View getView() {
    // Create view lazily only once it's needed
    if (this.home3DView == null) {
      this.home3DView = this.viewFactory.createView3D(this.home, this.preferences, this);
    }
    return this.home3DView;
  }

  /**
   * Changes home camera for {@link Home#getTopCamera() top camera}.
   */
  public void viewFromTop() {
    this.home.setCamera(this.home.getTopCamera());
  }

  /**
   * Changes home camera for {@link Home#getObserverCamera() observer camera}.
   */
  public void viewFromObserver() {
    this.home.setCamera(this.home.getObserverCamera());
  }

  /**
   * Stores a clone of the current camera in home under the given <code>name</code>.
   */
  public void storeCamera(String name) {
    Camera camera = (Camera)this.home.getCamera().duplicate();
    camera.setName(name);
    List<Camera> homeStoredCameras = this.home.getStoredCameras();
    ArrayList<Camera> storedCameras = new ArrayList<Camera>(homeStoredCameras.size() + 1);
    storedCameras.addAll(homeStoredCameras);
    // Don't keep two cameras with the same name or the same location
    for (int i = storedCameras.size() - 1; i >= 0; i--) {
      Camera storedCamera = storedCameras.get(i);
      if (name.equals(storedCamera.getName())
          || (camera.getX() == storedCamera.getX()
              && camera.getY() == storedCamera.getY()
              && camera.getZ() == storedCamera.getZ()
              && camera.getPitch() == storedCamera.getPitch()
              && camera.getYaw() == storedCamera.getYaw()
              && camera.getFieldOfView() == storedCamera.getFieldOfView()
              && camera.getTime() == storedCamera.getTime()
              && camera.getLens() == storedCamera.getLens()
              && (camera.getRenderer() == storedCamera.getRenderer()
                  || camera.getRenderer() != null && camera.getRenderer().equals(storedCamera.getRenderer())))) {
        storedCameras.remove(i);
      }
    }
    storedCameras.add(0, camera);
    // Ensure home stored cameras don't contain more cameras than allowed
    while (storedCameras.size() > this.preferences.getStoredCamerasMaxCount()) {
      storedCameras.remove(storedCameras.size() - 1);
    }
    this.home.setStoredCameras(storedCameras);
  }

  /**
   * Switches to observer or top camera and move camera to the values as the current camera.
   */
  public void goToCamera(Camera camera) {
    if (camera instanceof ObserverCamera) {
      viewFromObserver();
    } else {
      viewFromTop();
    }
    this.cameraState.goToCamera(camera);
    // Reorder cameras
    ArrayList<Camera> storedCameras = new ArrayList<Camera>(this.home.getStoredCameras());
    storedCameras.remove(camera);
    storedCameras.add(0, camera);
    this.home.setStoredCameras(storedCameras);
  }

  /**
   * Deletes the given list of cameras from the ones stored in home.
   */
  public void deleteCameras(List<Camera> cameras) {
    List<Camera> homeStoredCameras = this.home.getStoredCameras();
    // Build a list of cameras that will contain only the cameras not in the camera list in parameter
    ArrayList<Camera> storedCameras = new ArrayList<Camera>(homeStoredCameras.size() - cameras.size());
    for (Camera camera : homeStoredCameras) {
      if (!cameras.contains(camera)) {
        storedCameras.add(camera);
      }
    }
    this.home.setStoredCameras(storedCameras);
  }

  /**
   * Makes all levels visible.
   */
  public void displayAllLevels() {
    this.home.getEnvironment().setAllLevelsVisible(true);
  }

  /**
   * Makes the selected level and below visible.
   */
  public void displaySelectedLevel() {
    this.home.getEnvironment().setAllLevelsVisible(false);
  }

  /**
   * Controls the edition of 3D attributes.
   */
  public void modifyAttributes() {
    new Home3DAttributesController(this.home, this.preferences,
        this.viewFactory, this.contentManager, this.undoSupport).displayView(getView());
  }

  /**
   * Changes current state of controller.
   */
  protected void setCameraState(CameraControllerState state) {
    if (this.cameraState != null) {
      this.cameraState.exit();
    }
    this.cameraState = state;
    this.cameraState.enter();
  }

  /**
   * Moves home camera of <code>delta</code>.
   * @param delta  the value in cm that the camera should move forward
   *               (with a negative delta) or backward (with a positive delta)
   */
  public void moveCamera(float delta) {
    this.cameraState.moveCamera(delta);
  }

  /**
   * Moves home camera sideways of <code>delta</code>.
   * @param delta  the value in cm that the camera should move left
   *               (with a negative delta) or right (with a positive delta)
   * @since 4.4
   */
  public void moveCameraSideways(float delta) {
    this.cameraState.moveCameraSideways(delta);
  }

  /**
   * Zooms the 3D view: in the aerial view, the distance to the home changes
   * by a ratio at each step, so the zoom is as fast from far away as it is
   * precise close to the home; in the virtual visit, the visitor walks 25 cm
   * per step.
   * @param steps the count of zoom steps, positive to get closer, negative to move away
   */
  public void zoomCamera(float steps) {
    this.cameraState.zoomCamera(steps);
  }

  /**
   * Shows the home in the aerial view from the given direction, at the
   * distance where it's entirely in sight.
   * @param yaw   the angle of the camera around the home in radians: 0 to look
   *              from the top of the plan (north), <code>PI</code> from its bottom
   * @param pitch the angle of the camera above the horizon in radians,
   *              <code>PI / 2</code> for a view from the top
   */
  public void viewFromDirection(float yaw, float pitch) {
    viewFromTop();
    // The view turns around the building, not around its garden
    this.cameraState.centerOnBuilding();
    Camera camera = this.home.getTopCamera();
    rotateCameraYaw(yaw - camera.getYaw());
    rotateCameraPitch(pitch - camera.getPitch());
    this.cameraState.zoomCameraToBounds();
  }

  /**
   * Sets the ratio between the width and the height of the 3D view, which tells
   * from how far a home is entirely in sight.
   */
  public void setViewAspectRatio(float viewAspectRatio) {
    if (viewAspectRatio > 0 && !Float.isNaN(viewAspectRatio) && !Float.isInfinite(viewAspectRatio)) {
      this.viewAspectRatio = Math.max(0.25f, Math.min(viewAspectRatio, 6));
    }
  }

  /**
   * Shows the building from one of nine standard directions, laid out as the keys 1 to 9
   * of a numeric keypad over the plan: 2 from the front (the bottom of the plan), 8 from
   * the back, 4 from the left, 6 from the right, 1, 3, 7 and 9 from the corners and 5 from
   * the top.
   * @param direction a number from 1 to 9
   * @param higher    <code>true</code> to look from higher: sides are seen from 30 degrees
   *                  above the horizon rather than level, corners from 50 degrees rather than 30
   */
  public void viewFromStandardDirection(int direction, boolean higher) {
    if (direction < 1 || direction > 9) {
      throw new IllegalArgumentException("Direction from 1 to 9");
    }
    if (direction == 5) {
      viewFromDirection((float)Math.PI, (float)Math.PI / 2);
    } else {
      // Columns of the keypad go from the west to the east, its rows from the south to the north
      int column = (direction - 1) % 3 - 1;
      int row = (direction - 1) / 3 - 1;
      // 0 looks from the north, PI / 2 from the east
      float yaw = (float)Math.atan2(column, row);
      boolean corner = column != 0 && row != 0;
      float pitch = corner
          ? (float)Math.toRadians(higher ? 50 : 30)
          : higher ? (float)Math.toRadians(30) : 0.02f;
      viewFromDirection(yaw < 0 ? yaw + 2 * (float)Math.PI : yaw, pitch);
    }
  }

  /**
   * Places the camera of the aerial view at the distance where the whole
   * home is in sight. Does nothing in the virtual visit.
   */
  public void zoomCameraToFit() {
    this.cameraState.zoomCameraToFit();
  }

  /**
   * Elevates home camera of <code>delta</code>.
   * @param delta the value in cm that the camera should move down
   *              (with a negative delta) or up (with a positive delta)
   */
  public void elevateCamera(float delta) {
    this.cameraState.elevateCamera(delta);
  }

  /**
   * Rotates home camera yaw angle of <code>delta</code> radians.
   * @param delta  the value in rad that the camera should turn around yaw axis
   */
  public void rotateCameraYaw(float delta) {
    this.cameraState.rotateCameraYaw(delta);
  }

  /**
   * Rotates home camera pitch angle of <code>delta</code> radians.
   * @param delta  the value in rad that the camera should turn around pitch axis
   */
  public void rotateCameraPitch(float delta) {
    this.cameraState.rotateCameraPitch(delta);
  }

  /**
   * Modifies home camera field of view of <code>delta</code>.
   * @param delta  the value in rad that should be added the field of view
   *               to get a narrower view (with a negative delta) or a wider view (with a positive delta)
   * @since 5.5
   */
  public void modifyFieldOfView(float delta) {
    this.cameraState.modifyFieldOfView(delta);
  }

  /**
   * Processes a mouse button pressed event.
   * @since 7.2
   */
  public void pressMouse(float x, float y, int clickCount, boolean shiftDown,
                         boolean alignmentActivated, boolean duplicationActivated, boolean magnetismToggled,
                         View.PointerType pointerType) {
    this.cameraState.pressMouse(x, y, clickCount, shiftDown,
        alignmentActivated, duplicationActivated, magnetismToggled, pointerType);
  }

  /**
   * Processes a mouse button released event.
   * @since 7.2
   */
  public void releaseMouse(float x, float y) {
    this.cameraState.releaseMouse(x, y);
  }

  /**
   * Processes a mouse button moved event.
   * @since 7.2
   */
  public void moveMouse(float x, float y) {
    this.cameraState.moveMouse(x, y);
  }

  /**
   * Returns <code>true</code> if this controller is moving items.
   * @since 7.2
   */
  public boolean isEditingState() {
    return this.cameraState.isEditingState();
  }

  /**
   * Escapes of current editing action.
   * @since 7.2
   */
  public void escape() {
    this.cameraState.escape();
  }

  /**
   * Toggles temporary magnetism feature of user preferences during editing action.
   * @param magnetismToggled if <code>true</code> then magnetism feature is toggled.
   * @since 7.2
   */
  public void toggleMagnetism(boolean magnetismToggled) {
    this.cameraState.toggleMagnetism(magnetismToggled);
  }

  /**
   * Activates or deactivates alignment feature during editing action.
   * @param alignmentActivated if <code>true</code> then alignment is active.
   * @since 7.2
   */
  public void setAlignmentActivated(boolean alignmentActivated) {
    this.cameraState.setAlignmentActivated(alignmentActivated);
  }

  /**
   * Activates or deactivates duplication feature during editing action.
   * @param duplicationActivated if <code>true</code> then duplication is active.
   * @since 7.2
   */
  public void setDuplicationActivated(boolean duplicationActivated) {
    this.cameraState.setDuplicationActivated(duplicationActivated);
  }

  /**
   * Returns the observer camera state.
   */
  protected CameraControllerState getObserverCameraState() {
    return this.observerCameraState;
  }

  /**
   * Returns the top camera state.
   */
  protected CameraControllerState getTopCameraState() {
    return this.topCameraState;
  }

  /**
   * Controller state classes super class.
   */
  protected static abstract class CameraControllerState {
    public void enter() {
    }

    public void exit() {
    }

    public void moveCamera(float delta) {
    }

    public void zoomCamera(float steps) {
      moveCamera(steps * 25);
    }

    public void zoomCameraToFit() {
    }

    public void centerOnBuilding() {
    }

    public void zoomCameraToBounds() {
    }

    public void moveCameraSideways(float delta) {
    }

    public void elevateCamera(float delta) {
    }

    public void rotateCameraYaw(float delta) {
    }

    public void rotateCameraPitch(float delta) {
    }

    public void modifyFieldOfView(float delta) {
    }

    public void goToCamera(Camera camera) {
    }

    public boolean isEditingState() {
      return false;
    }

    public void pressMouse(float x, float y, int clickCount, boolean shiftDown,
                           boolean alignmentActivated, boolean duplicationActivated, boolean magnetismToggled,
                           View.PointerType pointerType) {
    }

    public void releaseMouse(float x, float y) {
    }

    public void moveMouse(float x, float y) {
    }

    public void escape() {
    }

    public void toggleMagnetism(boolean magnetismToggled) {
    }

    public void setAlignmentActivated(boolean alignmentActivated) {
    }

    public void setDuplicationActivated(boolean duplicationActivated) {
    }

    public void setEditionActivated(boolean editionActivated) {
    }
  }

  // CameraControllerState subclasses

  /**
   * Controller state handling mouse events to edit home items.
   * @since 7.2
   */
  protected abstract class EditingCameraState extends CameraControllerState {
    private boolean               cameraMoved;
    private boolean               mouseMoved;
    private boolean               elevationActivated;
    private boolean               rotationActivated;
    private float []              lastMousePressedPoint3D;
    private float                 distancesRatio;
    private float                 yLastMousePress;
    private Float                 angleMousePress;
    private boolean               alignmentActivated;
    private boolean               duplicationActivated;
    private boolean               magnetismToggled;
    private View.PointerType      pointerType;
    private ArrayList<HomePieceOfFurniture> movedItems;
    private HomePieceOfFurniture  closestMovedPiece;
    private float []              movedItemsStartPoint;
    private Float                 movedItemsDeltaX;
    private Float                 movedItemsDeltaY;

    /**
     * Returns <code>true</code> if this controller is moving items.
     */
    public boolean isEditingState() {
      return this.movedItems != null;
    }

    public void moveCamera(float delta) {
      this.cameraMoved = true;
    }

    public void moveCameraSideways(float delta) {
      this.cameraMoved = true;
    }

    public void elevateCamera(float delta) {
      this.cameraMoved = true;
    }

    public void rotateCameraYaw(float delta) {
      this.cameraMoved = true;
    }

    public void rotateCameraPitch(float delta) {
      this.cameraMoved = true;
    }

    public void modifyFieldOfView(float delta) {
      this.cameraMoved = true;
    }

    public void goToCamera(Camera camera) {
      this.cameraMoved = true;
    }

    /**
     * Processes a mouse button pressed event.
     */
    public void pressMouse(float x, float y, int clickCount, boolean shiftDown,
                           boolean alignmentActivated, boolean duplicationActivated, boolean magnetismToggled,
                           View.PointerType pointerType) {
      if (planController != null
          && preferences.isEditingIn3DViewEnabled()
          && getView() instanceof View3D
          && !planController.isModificationState()) {
        if (clickCount == 1) {
          Selectable closestItem = ((View3D)getView()).getClosestSelectableItemAt(Math.round(x), Math.round(y));
          List<Selectable> allSelectedItems = new ArrayList<Selectable>();
          List<Selectable> selectedItems = home.getSelectedItems();
          for (Selectable item : selectedItems) {
            if (item instanceof HomeFurnitureGroup) {
              allSelectedItems.addAll(((HomeFurnitureGroup)item).getAllFurniture());
            } else {
              allSelectedItems.add(item);
            }
          }
          if (allSelectedItems.contains(closestItem)
              && planController.isItemMovable(closestItem)
              && closestItem instanceof HomePieceOfFurniture) {
            this.movedItems = new ArrayList<HomePieceOfFurniture>();
            for (Selectable item : selectedItems) {
              if (planController.isItemMovable(item)
                  && item instanceof HomePieceOfFurniture) {
                this.movedItems.add((HomePieceOfFurniture)item);
              }
            }

            this.elevationActivated = !alignmentActivated && duplicationActivated;
            this.rotationActivated = alignmentActivated && duplicationActivated;
            if ((this.elevationActivated
                  || this.rotationActivated)
                && closestItem instanceof HomePieceOfFurniture) {
              if (selectedItems.size() > 1) {
                this.elevationActivated = false;
                this.rotationActivated = false;
              }
            }

            if (this.movedItems != null) {
              this.closestMovedPiece = (HomePieceOfFurniture)closestItem;
              float elevationLastMousePressed = this.closestMovedPiece.getGroundElevation() + this.closestMovedPiece.getHeightInPlan() / 2 * (float)Math.cos(home.getCamera().getPitch());
              this.lastMousePressedPoint3D = ((View3D)getView()).convertPixelLocationToVirtualWorld(Math.round(x), Math.round(y));
              float cameraToClosestPieceDistance = (float)Math.sqrt(
                    (home.getCamera().getX() - this.closestMovedPiece.getX()) * (home.getCamera().getX() - this.closestMovedPiece.getX())
                  + (home.getCamera().getY() - this.closestMovedPiece.getY()) * (home.getCamera().getY() - this.closestMovedPiece.getY())
                  + (home.getCamera().getZ() - elevationLastMousePressed) * (home.getCamera().getZ() - elevationLastMousePressed));
              float cameraToMousePressedPoint3DDistance = (float)Math.sqrt(
                    (home.getCamera().getX() - this.lastMousePressedPoint3D [0]) * (home.getCamera().getX() - this.lastMousePressedPoint3D [0])
                  + (home.getCamera().getY() - this.lastMousePressedPoint3D [1]) * (home.getCamera().getY() - this.lastMousePressedPoint3D [1])
                  + (home.getCamera().getZ() - this.lastMousePressedPoint3D [2]) * (home.getCamera().getZ() - this.lastMousePressedPoint3D [2]));
              // Prepare Thales ratio
              this.distancesRatio = cameraToClosestPieceDistance / cameraToMousePressedPoint3DDistance;
            }
            this.movedItemsDeltaX = null;
            this.movedItemsDeltaY = null;
          }
        } else if (clickCount == 2
                   && !home.getSelectedItems().isEmpty()) {
          planController.modifySelectedItem();
        }

        this.mouseMoved = false;
        this.cameraMoved = false;
        this.yLastMousePress = y;
        this.alignmentActivated = alignmentActivated;
        this.duplicationActivated = duplicationActivated;
        this.magnetismToggled = magnetismToggled;
        this.pointerType = pointerType;
      }
    }

    /**
     * Processes a mouse button released event.
     */
    public void releaseMouse(float x, float y) {
      if (planController != null
          && preferences.isEditingIn3DViewEnabled()
          && getView() instanceof View3D) {
        if (this.movedItems != null
            && this.movedItemsDeltaY != null) {
          // Simulate a mouse release in the plan
          planController.releaseMouse(this.movedItemsStartPoint [0] + this.movedItemsDeltaX,
              this.movedItemsStartPoint [1] + this.movedItemsDeltaY);
          planController.setFeedbackDisplayed(true);
        } else if (!planController.isModificationState()
                   && !this.mouseMoved
                   && !this.cameraMoved) {
          Selectable item = ((View3D)getView()).getClosestSelectableItemAt(Math.round(x), Math.round(y));
          if (item != null
              && home.isBasePlanLocked()
              && planController.isItemPartOfBasePlan(item)) {
            item = null;
          }
          if (this.alignmentActivated) {
            // Shift pressed without move
            if (item != null) {
              List<Selectable> selectedItems = new ArrayList<Selectable>(home.getSelectedItems());
              if (selectedItems.contains(item)) {
                home.deselectItem(item);
              } else {
                selectedItems.add(item);
                home.setSelectedItems(selectedItems);
              }
            }
          } else {
            if (item != null) {
              home.setSelectedItems(Arrays.asList(item));
            } else {
              List<Selectable> selectedItems = Collections.emptyList();
              home.setSelectedItems(selectedItems);
            }
          }
        }
        this.movedItems = null;
        this.closestMovedPiece = null;
        this.elevationActivated = false;
        this.rotationActivated = false;
      }
    }

    /**
     * Processes a mouse button moved event.
     */
    public void moveMouse(float x, float y) {
      if (planController != null
          && preferences.isEditingIn3DViewEnabled()
          && this.movedItems != null) {
        if (this.movedItemsDeltaY == null) {
          this.movedItemsStartPoint = this.rotationActivated
              ? this.movedItems.get(0).getPoints() [0] // Use rotation indicator as first point (rotated item can be a piece or a group)
              : new float [] {this.closestMovedPiece.getX(), this.closestMovedPiece.getY()};
          this.angleMousePress = null;
          planController.setFeedbackDisplayed(false);
          planController.moveMouse(movedItemsStartPoint [0], movedItemsStartPoint [1]);
          planController.pressMouse(movedItemsStartPoint [0], movedItemsStartPoint [1], 1, false, false,
              this.duplicationActivated, this.magnetismToggled);
          if (this.elevationActivated) {
            // Force piece elevation state in plan controller
            planController.setState(planController.getPieceOfFurnitureElevationState());
          } else if (this.rotationActivated) {
            // Force piece rotation state in plan controller
            planController.setState(planController.getPieceOfFurnitureRotationState());
          } else {
            home.setSelectedItems(this.movedItems);
            // Force selection move state in plan controller
            planController.setState(planController.getSelectionMoveState());
            planController.setAlignmentActivated(this.alignmentActivated);
          }
        }

        if (this.rotationActivated
            && this.angleMousePress == null) {
          this.angleMousePress = (float)Math.atan2(this.movedItemsStartPoint [1] - this.movedItems.get(0).getY(),
              this.movedItemsStartPoint [0] - this.movedItems.get(0).getX());
          if (this.pointerType == View.PointerType.TOUCH) {
            // Consider that mouse press was finally at the second touched point
            this.lastMousePressedPoint3D = ((View3D)getView()).convertPixelLocationToVirtualWorld(Math.round(x), Math.round(y));
          }
        }

        float [] point = ((View3D)getView()).convertPixelLocationToVirtualWorld(Math.round(x), Math.round(y));
        if (this.rotationActivated) {
          float newAngle = this.angleMousePress - (point [0] - this.lastMousePressedPoint3D [0]) * this.distancesRatio / 50;
          float indicatorCenterDistance = (float)Point2D.distance(this.movedItems.get(0).getX(), this.movedItems.get(0).getY(),
              this.movedItemsStartPoint [0], this.movedItemsStartPoint [1]);
          this.movedItemsDeltaX = this.movedItems.get(0).getX() + indicatorCenterDistance * (float)Math.cos(newAngle) - this.movedItemsStartPoint [0];
          this.movedItemsDeltaY = this.movedItems.get(0).getY() + indicatorCenterDistance * (float)Math.sin(newAngle) - this.movedItemsStartPoint [1];
        } else if (this.elevationActivated) {
          this.movedItemsDeltaX = 0f;
          this.movedItemsDeltaY = (this.lastMousePressedPoint3D [2] - point [2]) * this.distancesRatio
              + (y - this.yLastMousePress) * (float)(1 - Math.cos(home.getCamera().getPitch()));
        } else {
          this.movedItemsDeltaX = (float)(point [0] - this.lastMousePressedPoint3D [0]) * this.distancesRatio;
          this.movedItemsDeltaY = (float)(point [1] - this.lastMousePressedPoint3D [1]) * this.distancesRatio;
        }
        planController.moveMouse(this.movedItemsStartPoint [0] + this.movedItemsDeltaX,
            this.movedItemsStartPoint [1] + this.movedItemsDeltaY);
      }
      this.mouseMoved = true;
    }

    /**
     * Escapes of current editing action.
     */
    public void escape() {
      if (planController != null
          && preferences.isEditingIn3DViewEnabled()) {
        this.movedItems = null;
        planController.escape();
      }
    }

    /**
     * Toggles temporary magnetism feature of user preferences during editing action.
     * @param magnetismToggled if <code>true</code> then magnetism feature is toggled.
     */
    public void toggleMagnetism(boolean magnetismToggled) {
      if (planController != null
          && preferences.isEditingIn3DViewEnabled()) {
        this.magnetismToggled = magnetismToggled;
        planController.toggleMagnetism(magnetismToggled);
      }
    }

    /**
     * Activates or deactivates alignment feature during editing action.
     * @param alignmentActivated if <code>true</code> then alignment is active.
     */
    public void setAlignmentActivated(boolean alignmentActivated) {
      if (planController != null
          && preferences.isEditingIn3DViewEnabled()) {
        if (this.pointerType == View.PointerType.TOUCH
            && alignmentActivated
            && home.getSelectedItems().size() == 1
            && !this.mouseMoved) {
          this.elevationActivated = true;
          this.rotationActivated = false;
        }
        this.alignmentActivated = alignmentActivated;
        planController.setAlignmentActivated(alignmentActivated);
      }
    }

    /**
     * Activates or deactivates duplication feature during editing action.
     * @param duplicationActivated if <code>true</code> then duplication is active.
     */
    public void setDuplicationActivated(boolean duplicationActivated) {
      if (planController != null
          && preferences.isEditingIn3DViewEnabled()) {
        if (this.pointerType == View.PointerType.TOUCH
            && duplicationActivated
            && home.getSelectedItems().size() == 1) {
          if (this.elevationActivated
              && !this.mouseMoved) {
            this.elevationActivated = false;
            this.rotationActivated = true;
          } else {
            this.movedItemsStartPoint [0] += this.movedItemsDeltaX;
            this.movedItemsStartPoint [1] += this.movedItemsDeltaY;
            this.angleMousePress = null;
          }
        }
        this.duplicationActivated = duplicationActivated;
        if (!this.elevationActivated) {
          planController.setDuplicationActivated(duplicationActivated);
        }
      }
    }
  }

  /**
   * Top camera controller state.
   */
  private class TopCameraState extends EditingCameraState {
    private static final double ZOOM_STEP_RATIO = 0.88;
    private final float MIN_WIDTH  = 100;
    private final float MIN_DEPTH  = MIN_WIDTH;
    private final float MIN_HEIGHT = 20;

    private Camera      topCamera;
    // Furniture farther than this from the walls isn't a part of the building
    private static final float BUILDING_MARGIN = 150;
    private boolean     buildingBounds;
    // How far the user moved the center of the view sideways and up from the center of the home
    private float []    panOffset = new float [3];
    private float []    aerialViewBoundsLowerPoint;
    private float []    aerialViewBoundsUpperPoint;
    private float       minDistanceToAerialViewCenter;
    private float       maxDistanceToAerialViewCenter;
    private boolean     aerialViewCenteredOnSelectionEnabled;
    private boolean     previousSelectionEmpty;
    private float       distanceToCenterWithSelection = -1;

    private PropertyChangeListener objectChangeListener = new PropertyChangeListener() {
        public void propertyChange(PropertyChangeEvent ev) {
          updateCameraFromHomeBounds(false, false);
        }
      };
    private CollectionListener<Level> levelsListener = new CollectionListener<Level>() {
        public void collectionChanged(CollectionEvent<Level> ev) {
          if (ev.getType() == CollectionEvent.Type.ADD) {
            ev.getItem().addPropertyChangeListener(objectChangeListener);
          } else if (ev.getType() == CollectionEvent.Type.DELETE) {
            ev.getItem().removePropertyChangeListener(objectChangeListener);
          }
          updateCameraFromHomeBounds(false, false);
        }
      };
    private CollectionListener<Wall> wallsListener = new CollectionListener<Wall>() {
        public void collectionChanged(CollectionEvent<Wall> ev) {
          if (ev.getType() == CollectionEvent.Type.ADD) {
            ev.getItem().addPropertyChangeListener(objectChangeListener);
          } else if (ev.getType() == CollectionEvent.Type.DELETE) {
            ev.getItem().removePropertyChangeListener(objectChangeListener);
          }
          updateCameraFromHomeBounds(false, false);
        }
      };
    private CollectionListener<HomePieceOfFurniture> furnitureListener = new CollectionListener<HomePieceOfFurniture>() {
        public void collectionChanged(CollectionEvent<HomePieceOfFurniture> ev) {
          if (ev.getType() == CollectionEvent.Type.ADD) {
            addPropertyChangeListener(ev.getItem(), objectChangeListener);
            updateCameraFromHomeBounds(home.getFurniture().size() == 1
                && home.getWalls().isEmpty()
                && home.getRooms().isEmpty(), false);
          } else if (ev.getType() == CollectionEvent.Type.DELETE) {
            removePropertyChangeListener(ev.getItem(), objectChangeListener);
            updateCameraFromHomeBounds(false, false);
          }
        }
      };
    private CollectionListener<Room> roomsListener = new CollectionListener<Room>() {
        public void collectionChanged(CollectionEvent<Room> ev) {
          if (ev.getType() == CollectionEvent.Type.ADD) {
            ev.getItem().addPropertyChangeListener(objectChangeListener);
          } else if (ev.getType() == CollectionEvent.Type.DELETE) {
            ev.getItem().removePropertyChangeListener(objectChangeListener);
          }
          updateCameraFromHomeBounds(false, false);
        }
      };
    private CollectionListener<Polyline> polylinesListener = new CollectionListener<Polyline>() {
        public void collectionChanged(CollectionEvent<Polyline> ev) {
          if (ev.getType() == CollectionEvent.Type.ADD) {
            ev.getItem().addPropertyChangeListener(objectChangeListener);
          } else if (ev.getType() == CollectionEvent.Type.DELETE) {
            ev.getItem().removePropertyChangeListener(objectChangeListener);
          }
          updateCameraFromHomeBounds(false, false);
        }
      };
    private CollectionListener<DimensionLine> dimensionLinesListener = new CollectionListener<DimensionLine>() {
        public void collectionChanged(CollectionEvent<DimensionLine> ev) {
          if (ev.getType() == CollectionEvent.Type.ADD) {
            ev.getItem().addPropertyChangeListener(objectChangeListener);
          } else if (ev.getType() == CollectionEvent.Type.DELETE) {
            ev.getItem().removePropertyChangeListener(objectChangeListener);
          }
          updateCameraFromHomeBounds(false, false);
        }
      };
    private CollectionListener<Label> labelsListener = new CollectionListener<Label>() {
        public void collectionChanged(CollectionEvent<Label> ev) {
          if (ev.getType() == CollectionEvent.Type.ADD) {
            ev.getItem().addPropertyChangeListener(objectChangeListener);
          } else if (ev.getType() == CollectionEvent.Type.DELETE) {
            ev.getItem().removePropertyChangeListener(objectChangeListener);
          }
          updateCameraFromHomeBounds(false, false);
        }
      };
    private SelectionListener selectionListener = new SelectionListener() {
        public void selectionChanged(SelectionEvent ev) {
          boolean selectionEmpty = ev.getSelectedItems().isEmpty();
          updateCameraFromHomeBounds(false, previousSelectionEmpty && !selectionEmpty);
          previousSelectionEmpty = selectionEmpty;
        }
      };
    private UserPreferencesChangeListener userPreferencesChangeListener;

    public TopCameraState(UserPreferences preferences) {
      this.userPreferencesChangeListener = new UserPreferencesChangeListener(this);
    }

    private void addPropertyChangeListener(HomePieceOfFurniture piece, PropertyChangeListener listener) {
      if (piece instanceof HomeFurnitureGroup) {
        for (HomePieceOfFurniture child : ((HomeFurnitureGroup)piece).getFurniture()) {
          addPropertyChangeListener(child, listener);
        }
      } else {
        piece.addPropertyChangeListener(listener);
      }
    }

    private void removePropertyChangeListener(HomePieceOfFurniture piece, PropertyChangeListener listener) {
      if (piece instanceof HomeFurnitureGroup) {
        for (HomePieceOfFurniture child : ((HomeFurnitureGroup)piece).getFurniture()) {
          removePropertyChangeListener(child, listener);
        }
      } else {
        piece.removePropertyChangeListener(listener);
      }
    }

    @Override
    public void enter() {
      this.topCamera = home.getCamera();
      this.previousSelectionEmpty = home.getSelectedItems().isEmpty();
      this.aerialViewCenteredOnSelectionEnabled = preferences.isAerialViewCenteredOnSelectionEnabled();
      updateCameraFromHomeBounds(false, false);
      for (Level level : home.getLevels()) {
        level.addPropertyChangeListener(this.objectChangeListener);
      }
      home.addLevelsListener(this.levelsListener);
      for (Wall wall : home.getWalls()) {
        wall.addPropertyChangeListener(this.objectChangeListener);
      }
      home.addWallsListener(this.wallsListener);
      for (HomePieceOfFurniture piece : home.getFurniture()) {
        addPropertyChangeListener(piece, this.objectChangeListener);
      }
      home.addFurnitureListener(this.furnitureListener);
      for (Room room : home.getRooms()) {
        room.addPropertyChangeListener(this.objectChangeListener);
      }
      home.addRoomsListener(this.roomsListener);
      for (Polyline polyline : home.getPolylines()) {
        polyline.addPropertyChangeListener(this.objectChangeListener);
      }
      home.addPolylinesListener(this.polylinesListener);
      for (DimensionLine dimensionLine : home.getDimensionLines()) {
        dimensionLine.addPropertyChangeListener(this.objectChangeListener);
      }
      home.addDimensionLinesListener(this.dimensionLinesListener);
      for (Label label : home.getLabels()) {
        label.addPropertyChangeListener(this.objectChangeListener);
      }
      home.addLabelsListener(this.labelsListener);
      home.addSelectionListener(this.selectionListener);
      preferences.addPropertyChangeListener(UserPreferences.Property.AERIAL_VIEW_CENTERED_ON_SELECTION_ENABLED,
          this.userPreferencesChangeListener);
    }

    /**
     * Sets whether aerial view should be centered on selection or not.
     */
    public void setAerialViewCenteredOnSelectionEnabled(boolean aerialViewCenteredOnSelectionEnabled) {
      this.aerialViewCenteredOnSelectionEnabled = aerialViewCenteredOnSelectionEnabled;
      updateCameraFromHomeBounds(false, false);
    }

    /**
     * Updates camera location from home bounds.
     */
    private void updateCameraFromHomeBounds(boolean firstPieceOfFurnitureAddedToEmptyHome, boolean selectionChange) {
      if (!isEditingState()) {
        if (this.aerialViewBoundsLowerPoint == null) {
          updateAerialViewBoundsFromHomeBounds(this.aerialViewCenteredOnSelectionEnabled);
        }
        float distanceToCenter;
        if (selectionChange
            && preferences.isAerialViewCenteredOnSelectionEnabled()
            && this.distanceToCenterWithSelection != -1) {
          distanceToCenter = this.distanceToCenterWithSelection;
        } else {
          distanceToCenter = getCameraToAerialViewCenterDistance();
        }
        if (!home.getSelectedItems().isEmpty()) {
          this.distanceToCenterWithSelection = distanceToCenter;
        }
        updateAerialViewBoundsFromHomeBounds(this.aerialViewCenteredOnSelectionEnabled);
        updateCameraIntervalToAerialViewCenter();
        placeCameraAt(distanceToCenter, firstPieceOfFurnitureAddedToEmptyHome);
      }
    }

    /**
     * Returns the distance between the current camera location and home bounds center.
     */
    private float getCameraToAerialViewCenterDistance() {
      return (float)Math.sqrt(Math.pow((this.aerialViewBoundsLowerPoint [0] + this.aerialViewBoundsUpperPoint [0]) / 2 - this.topCamera.getX(), 2)
          + Math.pow((this.aerialViewBoundsLowerPoint [1] + this.aerialViewBoundsUpperPoint [1]) / 2 - this.topCamera.getY(), 2)
          + Math.pow((this.aerialViewBoundsLowerPoint [2] + this.aerialViewBoundsUpperPoint [2]) / 2 - this.topCamera.getZ(), 2));
    }

    /**
     * Sets the bounds that includes walls, furniture and rooms, or only selected items
     * if <code>centerOnSelection</code> is <code>true</code>.
     */
    private void updateAerialViewBoundsFromHomeBounds(boolean centerOnSelection) {
      this.aerialViewBoundsLowerPoint =
      this.aerialViewBoundsUpperPoint = null;
      List<Selectable> selectedItems = Collections.emptyList();
      if (centerOnSelection) {
        selectedItems = new ArrayList<Selectable>();
        for (Selectable item : home.getSelectedItems()) {
          if (item instanceof Elevatable
              && isItemAtVisibleLevel((Elevatable)item)
              && (!(item instanceof HomePieceOfFurniture)
                  || ((HomePieceOfFurniture)item).isVisible())
              && (!(item instanceof Polyline)
                  || ((Polyline)item).isVisibleIn3D())
              && (!(item instanceof DimensionLine)
                  || ((DimensionLine)item).isVisibleIn3D())
              && (!(item instanceof Label)
                  || ((Label)item).getPitch() != null)) {
            selectedItems.add(item);
          }
        }
      }
      boolean selectionEmpty = selectedItems.size() == 0 || !centerOnSelection;

      // Compute plan bounds to include rooms, walls and furniture
      boolean containsVisibleWalls = false;
      for (Wall wall : selectionEmpty
                           ? home.getWalls()
                           : Home.getWallsSubList(selectedItems)) {
        if (isItemAtVisibleLevel(wall)) {
          containsVisibleWalls = true;

          float wallElevation = wall.getLevel() != null
              ? wall.getLevel().getElevation()
              : 0;
          float minZ = selectionEmpty
              ? 0
              : wallElevation;

          Float height = wall.getHeight();
          float maxZ;
          if (height != null) {
            maxZ = wallElevation + height;
          } else {
            maxZ = wallElevation + home.getWallHeight();
          }
          Float heightAtEnd = wall.getHeightAtEnd();
          if (heightAtEnd != null) {
            maxZ = Math.max(maxZ, wallElevation + heightAtEnd);
          }
          for (float [] point : wall.getPoints()) {
            updateAerialViewBounds(point [0], point [1], minZ, maxZ);
          }
        }
      }

      // The box of the walls, to tell the furniture of the building from the one of its garden
      float [] buildingBox = null;
      if (selectionEmpty && this.buildingBounds && this.aerialViewBoundsLowerPoint != null) {
        buildingBox = new float [] {this.aerialViewBoundsLowerPoint [0] - BUILDING_MARGIN,
                                    this.aerialViewBoundsLowerPoint [1] - BUILDING_MARGIN,
                                    this.aerialViewBoundsUpperPoint [0] + BUILDING_MARGIN,
                                    this.aerialViewBoundsUpperPoint [1] + BUILDING_MARGIN};
      }
      for (HomePieceOfFurniture piece : selectionEmpty
                                            ? home.getFurniture()
                                            : Home.getFurnitureSubList(selectedItems)) {
        if (selectionEmpty
            && (containsVisibleWalls && HomeTerrain.isTerrain(piece)
                || buildingBox != null
                    && (piece.getX() < buildingBox [0] || piece.getY() < buildingBox [1]
                        || piece.getX() > buildingBox [2] || piece.getY() > buildingBox [3]))) {
          // The ground would make the home a small thing in the middle of its plot,
          // and so would trees or a driveway for a view of the building
          continue;
        }
        if (piece.isVisible() && isItemAtVisibleLevel(piece)) {
          float minZ;
          float maxZ;
          if (selectionEmpty) {
            minZ = Math.max(0, piece.getGroundElevation());
            maxZ = Math.max(0, piece.getGroundElevation() + piece.getHeightInPlan());
          } else {
            minZ = piece.getGroundElevation();
            maxZ = piece.getGroundElevation() + piece.getHeightInPlan();
          }
          for (float [] point : piece.getPoints()) {
            updateAerialViewBounds(point [0], point [1], minZ, maxZ);
          }
        }
      }

      for (Room room : selectionEmpty
                           ? home.getRooms()
                           : Home.getRoomsSubList(selectedItems)) {
        if (isItemAtVisibleLevel(room)) {
          float minZ = 0;
          float maxZ = MIN_HEIGHT;
          Level roomLevel = room.getLevel();
          if (roomLevel != null) {
            minZ = roomLevel.getElevation() - roomLevel.getFloorThickness();
            maxZ = roomLevel.getElevation();
            if (selectionEmpty) {
              minZ = Math.max(0, minZ);
              maxZ = Math.max(MIN_HEIGHT, roomLevel.getElevation());
            }
          }
          for (float [] point : room.getPoints()) {
            updateAerialViewBounds(point [0], point [1], minZ, maxZ);
          }
        }
      }

      for (Polyline polyline : selectionEmpty
                ? home.getPolylines()
                : Home.getPolylinesSubList(selectedItems)) {
        if (polyline.isVisibleIn3D() && isItemAtVisibleLevel(polyline)) {
          float minZ;
          float maxZ;
          if (selectionEmpty) {
            minZ = Math.max(0, polyline.getGroundElevation());
            maxZ = Math.max(MIN_HEIGHT, polyline.getGroundElevation());
          } else {
            minZ =
            maxZ = polyline.getGroundElevation();
          }
          for (float [] point : polyline.getPoints()) {
            updateAerialViewBounds(point [0], point [1], minZ, maxZ);
          }
        }
      }

      for (DimensionLine dimensionLine : selectionEmpty
                ? home.getDimensionLines()
                : Home.getDimensionLinesSubList(selectedItems)) {
        if (dimensionLine.isVisibleIn3D() && isItemAtVisibleLevel(dimensionLine)) {
          float levelElevation = dimensionLine.getLevel() != null ? dimensionLine.getLevel().getElevation() : 0;
          float minZ;
          float maxZ;
          if (selectionEmpty) {
            minZ = Math.max(0, levelElevation + dimensionLine.getElevationStart());
            maxZ = Math.max(MIN_HEIGHT, levelElevation + dimensionLine.getElevationEnd());
          } else {
            minZ = levelElevation + dimensionLine.getElevationStart();
            maxZ = levelElevation + dimensionLine.getElevationEnd();
          }
          for (float [] point : dimensionLine.getPoints()) {
            updateAerialViewBounds(point [0], point [1], minZ, maxZ);
          }
        }
      }

      for (Label label : selectionEmpty
                             ? home.getLabels()
                             : Home.getLabelsSubList(selectedItems)) {
        if (label.getPitch() != null && isItemAtVisibleLevel(label)) {
          float minZ;
          float maxZ;
          if (selectionEmpty) {
            minZ = Math.max(0, label.getGroundElevation());
            maxZ = Math.max(MIN_HEIGHT, label.getGroundElevation());
          } else {
            minZ =
            maxZ = label.getGroundElevation();
          }
          for (float [] point : label.getPoints()) {
            updateAerialViewBounds(point [0], point [1], minZ, maxZ);
          }
        }
      }

      if (this.aerialViewBoundsLowerPoint == null) {
        this.aerialViewBoundsLowerPoint = new float [] {0, 0, 0};
        this.aerialViewBoundsUpperPoint = new float [] {MIN_WIDTH, MIN_DEPTH, MIN_HEIGHT};
      } else if (containsVisibleWalls && selectionEmpty) {
        // If home contains walls, ensure bounds are always minimum 1 meter wide centered in middle of 3D view
        if (MIN_WIDTH > this.aerialViewBoundsUpperPoint [0] - this.aerialViewBoundsLowerPoint [0]) {
          this.aerialViewBoundsLowerPoint [0] = (this.aerialViewBoundsLowerPoint [0] + this.aerialViewBoundsUpperPoint [0]) / 2 - MIN_WIDTH / 2;
          this.aerialViewBoundsUpperPoint [0] = this.aerialViewBoundsLowerPoint [0] + MIN_WIDTH;
        }
        if (MIN_DEPTH > this.aerialViewBoundsUpperPoint [1] - this.aerialViewBoundsLowerPoint [1]) {
          this.aerialViewBoundsLowerPoint [1] = (this.aerialViewBoundsLowerPoint [1] + this.aerialViewBoundsUpperPoint [1]) / 2 - MIN_DEPTH / 2;
          this.aerialViewBoundsUpperPoint [1] = this.aerialViewBoundsLowerPoint [1] + MIN_DEPTH;
        }
        if (MIN_HEIGHT > this.aerialViewBoundsUpperPoint [2] - this.aerialViewBoundsLowerPoint [2]) {
          this.aerialViewBoundsLowerPoint [2] = (this.aerialViewBoundsLowerPoint [2] + this.aerialViewBoundsUpperPoint [2]) / 2 - MIN_HEIGHT / 2;
          this.aerialViewBoundsUpperPoint [2] = this.aerialViewBoundsLowerPoint [2] + MIN_HEIGHT;
        }
      }
      // The view stays where the user moved it sideways when the home changes
      for (int i = 0; i < 3; i++) {
        this.aerialViewBoundsLowerPoint [i] += this.panOffset [i];
        this.aerialViewBoundsUpperPoint [i] += this.panOffset [i];
      }
    }

    /**
     * Moves the center of the aerial view by the given distances, with the camera.
     */
    private void pan(float dx, float dy, float dz) {
      if (this.aerialViewBoundsLowerPoint == null) {
        return;
      }
      float centerZ = (this.aerialViewBoundsLowerPoint [2] + this.aerialViewBoundsUpperPoint [2]) / 2;
      // Not under the ground, nor higher than the home is far
      dz = Math.max(-centerZ, Math.min(dz, this.maxDistanceToAerialViewCenter - centerZ));
      float [] delta = {dx, dy, dz};
      for (int i = 0; i < 3; i++) {
        this.panOffset [i] += delta [i];
        this.aerialViewBoundsLowerPoint [i] += delta [i];
        this.aerialViewBoundsUpperPoint [i] += delta [i];
      }
      this.topCamera.setX(this.topCamera.getX() + dx);
      this.topCamera.setY(this.topCamera.getY() + dy);
      this.topCamera.setZ(this.topCamera.getZ() + dz);
    }

    /**
     * Returns the ratio between a move of the camera and the same move in the virtual
     * visit: from far away, a step sideways must be larger to show.
     */
    private float getPanScale() {
      return Math.max(1, getCameraToAerialViewCenterDistance() / 250);
    }

    /**
     * Moves the camera and the point it turns around to the left (negative delta) or to the right.
     */
    @Override
    public void moveCameraSideways(float delta) {
      super.moveCameraSideways(delta);
      delta *= getPanScale();
      pan(-(float)Math.cos(this.topCamera.getYaw()) * delta, -(float)Math.sin(this.topCamera.getYaw()) * delta, 0);
    }

    /**
     * Moves the camera and the point it turns around up or down.
     */
    @Override
    public void elevateCamera(float delta) {
      super.elevateCamera(delta);
      pan(0, 0, delta * getPanScale());
    }

    /**
     * Adds the point at the given coordinates to aerial view bounds.
     */
    private void updateAerialViewBounds(float x, float y, float minZ, float maxZ) {
      if (this.aerialViewBoundsLowerPoint == null) {
        this.aerialViewBoundsLowerPoint = new float [] {x, y, minZ};
        this.aerialViewBoundsUpperPoint = new float [] {x, y, maxZ};
      } else {
        this.aerialViewBoundsLowerPoint [0] = Math.min(this.aerialViewBoundsLowerPoint [0], x);
        this.aerialViewBoundsUpperPoint [0] = Math.max(this.aerialViewBoundsUpperPoint [0], x);
        this.aerialViewBoundsLowerPoint [1] = Math.min(this.aerialViewBoundsLowerPoint [1], y);
        this.aerialViewBoundsUpperPoint [1] = Math.max(this.aerialViewBoundsUpperPoint [1], y);
        this.aerialViewBoundsLowerPoint [2] = Math.min(this.aerialViewBoundsLowerPoint [2], minZ);
        this.aerialViewBoundsUpperPoint [2] = Math.max(this.aerialViewBoundsUpperPoint [2], maxZ);
      }
    }

    /**
     * Returns <code>true</code> if the given <code>item</code> is at a visible level.
     */
    private boolean isItemAtVisibleLevel(Elevatable item) {
      return item.getLevel() == null || item.getLevel().isViewableAndVisible();
    }

    /**
     * Updates the minimum and maximum distances of the camera to the center of the aerial view.
     */
    /**
     * Centers the aerial view on the walls of the home and what stands among them, leaving
     * out the garden, until the home changes. Does nothing in a home without wall.
     */
    @Override
    public void centerOnBuilding() {
      if (!isEditingState()) {
        float distanceToCenter = this.aerialViewBoundsLowerPoint != null
            ? getCameraToAerialViewCenterDistance()
            : -1;
        this.panOffset = new float [3];
        this.buildingBounds = true;
        try {
          updateAerialViewBoundsFromHomeBounds(this.aerialViewCenteredOnSelectionEnabled);
        } finally {
          this.buildingBounds = false;
        }
        updateCameraIntervalToAerialViewCenter();
        placeCameraAt(distanceToCenter > 0 ? distanceToCenter : this.minDistanceToAerialViewCenter, false);
      }
    }

    private void updateCameraIntervalToAerialViewCenter() {
      float homeBoundsWidth = this.aerialViewBoundsUpperPoint [0] - this.aerialViewBoundsLowerPoint [0];
      float homeBoundsDepth = this.aerialViewBoundsUpperPoint [1] - this.aerialViewBoundsLowerPoint [1];
      float homeBoundsHeight = this.aerialViewBoundsUpperPoint [2] - this.aerialViewBoundsLowerPoint [2];
      float halfDiagonal = (float)Math.sqrt(homeBoundsWidth * homeBoundsWidth
          + homeBoundsDepth * homeBoundsDepth
          + homeBoundsHeight * homeBoundsHeight) / 2;
      this.minDistanceToAerialViewCenter = halfDiagonal * 1.05f;
      // Far enough to see the home with its surroundings
      this.maxDistanceToAerialViewCenter = Math.max(12 * this.minDistanceToAerialViewCenter, 10000);
    }

    @Override
    public void moveCamera(float delta) {
      super.moveCamera(delta);
      // Use a 5 times bigger delta for top camera move
      delta *= 5;
      float newDistanceToCenter = getCameraToAerialViewCenterDistance() - delta;
      placeCameraAt(newDistanceToCenter, false);
    }

    @Override
    public void zoomCamera(float steps) {
      super.moveCamera(steps);
      float distanceToCenter = getCameraToAerialViewCenterDistance();
      // 12 % of the distance per step, and at least the 12.5 cm of a move
      float newDistanceToCenter = (float)(distanceToCenter * Math.pow(ZOOM_STEP_RATIO, steps));
      float minimumChange = 12.5f * Math.abs(steps);
      if (Math.abs(newDistanceToCenter - distanceToCenter) < minimumChange) {
        newDistanceToCenter = distanceToCenter - Math.signum(steps) * minimumChange;
      }
      placeCameraAt(newDistanceToCenter, false);
    }

    @Override
    public void zoomCameraToFit() {
      // The whole home, where it stands
      this.panOffset = new float [3];
      updateAerialViewBoundsFromHomeBounds(this.aerialViewCenteredOnSelectionEnabled);
      updateCameraIntervalToAerialViewCenter();
      zoomCameraToBounds();
    }

    @Override
    public void zoomCameraToBounds() {
      super.moveCamera(0);
      // The distance from which the home is in the view, whose field of view is the one of its width
      double tanHalfWidth = Math.tan(Math.max(Math.toRadians(10),
          Math.min(this.topCamera.getFieldOfView() / 2, Math.PI / 2 - 0.05)));
      double tanHalfHeight = tanHalfWidth / viewAspectRatio;
      double yaw = this.topCamera.getYaw();
      double pitch = this.topCamera.getPitch();
      // The direction the camera looks at, its right and its top, with z upward
      double [] forward = {-Math.sin(yaw) * Math.cos(pitch), Math.cos(yaw) * Math.cos(pitch), -Math.sin(pitch)};
      double [] right = {-Math.cos(yaw), -Math.sin(yaw), 0};
      double [] up = {right [1] * forward [2] - right [2] * forward [1],
                      right [2] * forward [0] - right [0] * forward [2],
                      right [0] * forward [1] - right [1] * forward [0]};
      // The home is taken as the ellipsoid in its box rather than as the box itself, whose
      // corners are empty: seen from a corner, a box would move the camera twice too far
      double side = 0;
      double height = 0;
      double depth = 0;
      for (int axis = 0; axis < 3; axis++) {
        double halfSize = (this.aerialViewBoundsUpperPoint [axis] - this.aerialViewBoundsLowerPoint [axis]) / 2;
        side += halfSize * right [axis] * halfSize * right [axis];
        height += halfSize * up [axis] * halfSize * up [axis];
        depth += halfSize * forward [axis] * halfSize * forward [axis];
      }
      // What is the closest to the camera is seen the largest
      double distance = Math.max(Math.sqrt(side) / tanHalfWidth, Math.sqrt(height) / tanHalfHeight) + Math.sqrt(depth);
      // A margin around the home
      placeCameraAt((float)(distance * 1.2), false);
    }

    public void placeCameraAt(float distanceToCenter, boolean firstPieceOfFurnitureAddedToEmptyHome) {
      // Check camera is always outside the sphere centered in home center and with a radius equal to minimum distance
      distanceToCenter = Math.max(distanceToCenter, this.minDistanceToAerialViewCenter);
      // Check camera isn't too far
      distanceToCenter = Math.min(distanceToCenter, this.maxDistanceToAerialViewCenter);
      if (firstPieceOfFurnitureAddedToEmptyHome) {
        // Get closer to the first piece of furniture added to an empty home when that is small
        distanceToCenter = Math.min(distanceToCenter, 3 * this.minDistanceToAerialViewCenter);
      }
      double distanceToCenterAtGroundLevel = distanceToCenter * Math.cos(this.topCamera.getPitch());
      this.topCamera.setX((this.aerialViewBoundsLowerPoint [0] + this.aerialViewBoundsUpperPoint [0]) / 2
          + (float)(Math.sin(this.topCamera.getYaw()) * distanceToCenterAtGroundLevel));
      this.topCamera.setY((this.aerialViewBoundsLowerPoint [1] + this.aerialViewBoundsUpperPoint [1]) / 2
          - (float)(Math.cos(this.topCamera.getYaw()) * distanceToCenterAtGroundLevel));
      this.topCamera.setZ((this.aerialViewBoundsLowerPoint [2] + this.aerialViewBoundsUpperPoint [2]) / 2
          + (float)Math.sin(this.topCamera.getPitch()) * distanceToCenter);
    }

    @Override
    public void rotateCameraYaw(float delta) {
      super.rotateCameraYaw(delta);
      float newYaw = this.topCamera.getYaw() + delta;
      double distanceToCenterAtGroundLevel = getCameraToAerialViewCenterDistance() * Math.cos(this.topCamera.getPitch());
      // Change camera yaw and location so user turns around home
      this.topCamera.setYaw(newYaw);
      this.topCamera.setX((this.aerialViewBoundsLowerPoint [0] + this.aerialViewBoundsUpperPoint [0]) / 2
          + (float)(Math.sin(newYaw) * distanceToCenterAtGroundLevel));
      this.topCamera.setY((this.aerialViewBoundsLowerPoint [1] + this.aerialViewBoundsUpperPoint [1]) / 2
          - (float)(Math.cos(newYaw) * distanceToCenterAtGroundLevel));
    }

    @Override
    public void rotateCameraPitch(float delta) {
      super.rotateCameraPitch(delta);
      float newPitch = this.topCamera.getPitch() + delta;
      // Check new pitch is between 0 and PI / 2
      newPitch = Math.max(newPitch, (float)0);
      newPitch = Math.min(newPitch, (float)Math.PI / 2);
      // Compute new z to keep the same distance to view center
      double distanceToCenter = getCameraToAerialViewCenterDistance();
      double distanceToCenterAtGroundLevel = distanceToCenter * Math.cos(newPitch);
      // Change camera pitch
      this.topCamera.setPitch(newPitch);
      this.topCamera.setX((this.aerialViewBoundsLowerPoint [0] + this.aerialViewBoundsUpperPoint [0]) / 2
          + (float)(Math.sin(this.topCamera.getYaw()) * distanceToCenterAtGroundLevel));
      this.topCamera.setY((this.aerialViewBoundsLowerPoint [1] + this.aerialViewBoundsUpperPoint [1]) / 2
          - (float)(Math.cos(this.topCamera.getYaw()) * distanceToCenterAtGroundLevel));
      this.topCamera.setZ((this.aerialViewBoundsLowerPoint [2] + this.aerialViewBoundsUpperPoint [2]) / 2
          + (float)(distanceToCenter * Math.sin(newPitch)));
    }

    @Override
    public void goToCamera(Camera camera) {
      super.goToCamera(camera);
      this.topCamera.setCamera(camera);
      this.topCamera.setTime(camera.getTime());
      this.topCamera.setLens(camera.getLens());
      this.topCamera.setRenderer(camera.getRenderer());
      updateCameraFromHomeBounds(false, false);
    }

   @Override
    public void releaseMouse(float x, float y) {
      super.releaseMouse(x, y);
      updateCameraFromHomeBounds(false, false);
    }

    @Override
    public void exit() {
      this.topCamera = null;
      for (Wall wall : home.getWalls()) {
        wall.removePropertyChangeListener(this.objectChangeListener);
      }
      home.removeWallsListener(this.wallsListener);
      for (HomePieceOfFurniture piece : home.getFurniture()) {
        removePropertyChangeListener(piece, this.objectChangeListener);
      }
      home.removeFurnitureListener(this.furnitureListener);
      for (Room room : home.getRooms()) {
        room.removePropertyChangeListener(this.objectChangeListener);
      }
      home.removeRoomsListener(this.roomsListener);
      for (Polyline polyline : home.getPolylines()) {
        polyline.removePropertyChangeListener(this.objectChangeListener);
      }
      home.removePolylinesListener(this.polylinesListener);
      for (DimensionLine dimensionLine : home.getDimensionLines()) {
        dimensionLine.removePropertyChangeListener(this.objectChangeListener);
      }
      home.removeDimensionLinesListener(this.dimensionLinesListener);
      for (Label label : home.getLabels()) {
        label.removePropertyChangeListener(this.objectChangeListener);
      }
      home.removeLabelsListener(this.labelsListener);
      for (Level level : home.getLevels()) {
        level.removePropertyChangeListener(this.objectChangeListener);
      }
      home.removeLevelsListener(this.levelsListener);
      home.removeSelectionListener(this.selectionListener);
      preferences.removePropertyChangeListener(UserPreferences.Property.AERIAL_VIEW_CENTERED_ON_SELECTION_ENABLED,
          this.userPreferencesChangeListener);
    }
  }

  /**
   * Preferences property listener bound to top camera state with a weak reference to avoid
   * strong link between user preferences and top camera state.
   */
  private static class UserPreferencesChangeListener implements PropertyChangeListener {
    private WeakReference<TopCameraState>  topCameraState;

    public UserPreferencesChangeListener(TopCameraState topCameraState) {
      this.topCameraState = new WeakReference<TopCameraState>(topCameraState);
    }

    public void propertyChange(PropertyChangeEvent ev) {
      // If top camera state was garbage collected, remove this listener from preferences
      TopCameraState topCameraState = this.topCameraState.get();
      UserPreferences preferences = (UserPreferences)ev.getSource();
      if (topCameraState == null) {
        preferences.removePropertyChangeListener(UserPreferences.Property.valueOf(ev.getPropertyName()), this);
      } else {
        topCameraState.setAerialViewCenteredOnSelectionEnabled(preferences.isAerialViewCenteredOnSelectionEnabled());
      }
    }
  }

  /**
   * Observer camera controller state.
   */
  private class ObserverCameraState extends EditingCameraState {
    private ObserverCamera observerCamera;
    private PropertyChangeListener levelElevationChangeListener = new PropertyChangeListener() {
        public void propertyChange(PropertyChangeEvent ev) {
          if (Level.Property.ELEVATION.name().equals(ev.getPropertyName())) {
            updateCameraMinimumElevation();
          }
        }
      };
    private CollectionListener<Level> levelsListener = new CollectionListener<Level>() {
        public void collectionChanged(CollectionEvent<Level> ev) {
          if (ev.getType() == CollectionEvent.Type.ADD) {
            ev.getItem().addPropertyChangeListener(levelElevationChangeListener);
          } else if (ev.getType() == CollectionEvent.Type.DELETE) {
            ev.getItem().removePropertyChangeListener(levelElevationChangeListener);
          }
          updateCameraMinimumElevation();
        }
      };

    @Override
    public void enter() {
      this.observerCamera = (ObserverCamera)home.getCamera();
      for (Level level : home.getLevels()) {
        level.addPropertyChangeListener(this.levelElevationChangeListener);
      }
      home.addLevelsListener(this.levelsListener);
      selectCamera();
    }

    /**
     * Selects the camera in home if required conditions are met.
     */
    private void selectCamera() {
      if (preferences.isObserverCameraSelectedAtChange()) {
        List<Selectable> selectedItems = home.getSelectedItems();
        if (!preferences.isEditingIn3DViewEnabled()
            || selectedItems.isEmpty()
            || selectedItems.size() == 1
                && selectedItems.get(0) == this.observerCamera) {
          // Select observer camera for user feedback
          home.setSelectedItems(Arrays.asList(new Selectable [] {this.observerCamera}));
        }
      }
    }

    @Override
    public void moveCamera(float delta) {
      super.moveCamera(delta);
      this.observerCamera.setX(this.observerCamera.getX() - (float)Math.sin(this.observerCamera.getYaw()) * delta);
      this.observerCamera.setY(this.observerCamera.getY() + (float)Math.cos(this.observerCamera.getYaw()) * delta);
      selectCamera();
    }

    @Override
    public void moveCameraSideways(float delta) {
      super.moveCameraSideways(delta);
      this.observerCamera.setX(this.observerCamera.getX() - (float)Math.cos(this.observerCamera.getYaw()) * delta);
      this.observerCamera.setY(this.observerCamera.getY() - (float)Math.sin(this.observerCamera.getYaw()) * delta);
      selectCamera();
    }

    @Override
    public void elevateCamera(float delta) {
      super.elevateCamera(delta);
      float newElevation = this.observerCamera.getZ() + delta;
      newElevation = Math.min(Math.max(newElevation, getMinimumElevation()), preferences.getLengthUnit().getMaximumElevation());
      this.observerCamera.setZ(newElevation);
      selectCamera();
    }

    private void updateCameraMinimumElevation() {
      observerCamera.setZ(Math.max(observerCamera.getZ(), getMinimumElevation()));
    }

    public float getMinimumElevation() {
      List<Level> levels = home.getLevels();
      if (levels.size() > 0) {
        return 10 + levels.get(0).getElevation();
      } else {
        return 10;
      }
    }

    @Override
    public void rotateCameraYaw(float delta) {
      super.rotateCameraYaw(delta);
      this.observerCamera.setYaw(this.observerCamera.getYaw() + delta);
      selectCamera();
    }

    @Override
    public void rotateCameraPitch(float delta) {
      super.rotateCameraPitch(delta);
      float newPitch = this.observerCamera.getPitch() + delta;
      // Check new angle is between -90° and 90°
      newPitch = Math.min(Math.max(-(float)Math.PI / 2, newPitch), (float)Math.PI / 2);;
      this.observerCamera.setPitch(newPitch);
      selectCamera();
    }

    @Override
    public void modifyFieldOfView(float delta) {
      super.modifyFieldOfView(delta);
      float newFieldOfView = this.observerCamera.getFieldOfView() + delta;
      // Check new angle is between 2° and 120°
      newFieldOfView = (float)Math.min(Math.max(Math.toRadians(2), newFieldOfView), Math.toRadians(120));
      this.observerCamera.setFieldOfView(newFieldOfView);
      selectCamera();
    }

    @Override
    public void goToCamera(Camera camera) {
      super.goToCamera(camera);
      this.observerCamera.setCamera(camera);
      this.observerCamera.setTime(camera.getTime());
      this.observerCamera.setLens(camera.getLens());
      this.observerCamera.setRenderer(camera.getRenderer());
    }

    @Override
    public void exit() {
      // Remove observer camera from selection
      List<Selectable> selectedItems = home.getSelectedItems();
      if (selectedItems.contains(this.observerCamera)) {
        selectedItems = new ArrayList<Selectable>(selectedItems);
        selectedItems.remove(this.observerCamera);
        home.setSelectedItems(selectedItems);
      }
      for (Level level : home.getLevels()) {
        level.removePropertyChangeListener(this.levelElevationChangeListener);
      }
      home.removeLevelsListener(this.levelsListener);
      this.observerCamera = null;
    }
  }
}
