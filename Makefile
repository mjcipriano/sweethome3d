SHELL := /bin/bash

# Adjustable settings
VERSION ?= 7.5.0# x-release-please-version
CONDA_ACTIVATE ?=
JAVA_OPTS ?= -Xmx1024m \
  --add-opens=java.desktop/java.awt=ALL-UNNAMED \
  --add-opens=java.desktop/sun.awt=ALL-UNNAMED \
  --add-opens=java.desktop/com.apple.eio=ALL-UNNAMED \
  --add-opens=java.desktop/com.apple.eawt=ALL-UNNAMED
RUN_LANG ?= en_US.UTF-8
RUN_LC_ALL ?= en_US.UTF-8
USER_LANG ?= en
USER_COUNTRY ?= US
USER_VARIANT ?=
JAVA_TEST_OPTS ?= $(JAVA_OPTS) \
  --add-opens=java.base/java.util=ALL-UNNAMED \
  --add-exports=java.desktop/sun.awt=ALL-UNNAMED
TEST_JAVAC_FLAGS ?= -source 8 -target 8

# Derived helpers
RUN_IN_ENV := $(if $(strip $(CONDA_ACTIVATE)),$(CONDA_ACTIVATE) && ,)
ANT := $(RUN_IN_ENV)ant -Dversion=$(VERSION)
JAVA := $(RUN_IN_ENV)java
JAVAC := $(RUN_IN_ENV)javac
JAR := $(RUN_IN_ENV)jar
CPSEP := :
ifeq ($(OS),Windows_NT)
  CPSEP := ;
endif
JAVA_LIB_PATH ?= lib/linux/x64:lib/java3d-1.6/linux/amd64:lib/yafaray/linux/x64
ifeq ($(OS),Windows_NT)
  JAVA_LIB_PATH := lib\\windows\\x64;lib\\java3d-1.6\\windows\\amd64;lib\\yafaray\\windows\\x64
endif

# Paths
MAIN_JAR := build/SweetHome3D.jar
INSTALL_JAR := install/SweetHome3D-$(VERSION).jar
DEV_RESOURCE_JARS := build/Furniture.jar build/Textures.jar build/Examples.jar build/Help.jar
DEV_CLASS_PATH := $(MAIN_JAR)$(CPSEP)build/Furniture.jar$(CPSEP)build/Textures.jar$(CPSEP)build/Examples.jar$(CPSEP)build/Help.jar
TEST_CLASSES := build/test-classes
PERFORMANCE_CLASSES := build/performance-classes
HOME_LOAD_BENCHMARK_SOURCE := test/com/eteks/sweethome3d/performance/HomeLoadBenchmark.java
PLAN_RENDER_BENCHMARK_SOURCE := test/com/eteks/sweethome3d/performance/PlanRenderBenchmark.java
HOME_3D_BENCHMARK_SOURCE := test/com/eteks/sweethome3d/performance/Home3DRenderBenchmark.java
STARTUP_BENCHMARK_SOURCE := test/com/eteks/sweethome3d/performance/StartupBenchmark.java
PLAN_INTERACTION_BENCHMARK_SOURCE := test/com/eteks/sweethome3d/performance/PlanInteractionBenchmark.java
TEST_JARS := libtest/junit-4.13.2.jar libtest/hamcrest-core-1.3.jar
JUNIT_URL := https://repo1.maven.org/maven2/junit/junit/4.13.2/junit-4.13.2.jar
HAMCREST_URL := https://repo1.maven.org/maven2/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar
TEST_COMPILE_CP := $(DEV_CLASS_PATH)$(CPSEP)lib/*$(CPSEP)lib/java3d-1.6/*$(CPSEP)libtest/*$(CPSEP)test
TEST_RUN_CP := $(TEST_CLASSES)$(CPSEP)$(DEV_CLASS_PATH)$(CPSEP)test$(CPSEP)lib/*$(CPSEP)lib/java3d-1.6/*$(CPSEP)libtest/*
GL_TEST_EXCLUDES := \
  com/eteks/sweethome3d/junit/PlanComponentTest \
  com/eteks/sweethome3d/junit/PrintTest \
  com/eteks/sweethome3d/junit/OBJWriterTest \
  com/eteks/sweethome3d/junit/PlanComponentWithFurnitureTest \
  com/eteks/sweethome3d/junit/UserPreferencesPanelTest \
  com/eteks/sweethome3d/junit/ImportedTextureWizardTest \
  com/eteks/sweethome3d/junit/PhotoCreationTest \
  com/eteks/sweethome3d/junit/IconManagerTest \
  com/eteks/sweethome3d/junit/HomeFileRecorderTest \
  com/eteks/sweethome3d/junit/ImportedFurnitureWizardTest \
  com/eteks/sweethome3d/junit/HomeCameraTest \
  com/eteks/sweethome3d/junit/ModelManagerTest \
  com/eteks/sweethome3d/junit/TransferHandlerTest \
  com/eteks/sweethome3d/junit/BackgroundImageWizardTest \
  com/eteks/sweethome3d/junit/HomeFurniturePanelTest \
  com/eteks/sweethome3d/junit/RoomTest \
  com/eteks/sweethome3d/junit/HomeControllerTest \
  com/eteks/sweethome3d/junit/LevelTest \
  com/eteks/sweethome3d/junit/PlanControllerTest \
  com/eteks/sweethome3d/junit/PackageDependenciesTest
ALL_TEST_SOURCES := $(shell find test -name "*Test.java")
CORE_TEST_SOURCES := \
  test/com/eteks/sweethome3d/io/HomeContentContextTest.java \
  test/com/eteks/sweethome3d/junit/HomeTest.java \
  test/com/eteks/sweethome3d/junit/OperatingSystemTest.java \
  test/com/eteks/sweethome3d/junit/PackageDependenciesTest.java
ifneq ($(strip $(TEST_SOURCES)),)
  FILTERED_TEST_SOURCES := $(TEST_SOURCES)
else
ifeq ($(SKIP_3D_TESTS),1)
  FILTERED_TEST_SOURCES := $(filter-out $(addprefix test/,$(addsuffix .java,$(GL_TEST_EXCLUDES))),$(ALL_TEST_SOURCES))
else
  FILTERED_TEST_SOURCES := $(ALL_TEST_SOURCES)
endif
endif
TEST_NAMES := $(subst /,.,$(FILTERED_TEST_SOURCES:test/%.java=%))

.PHONY: help build jar run run-dev test test-core test-gui test-local test-local-check benchmark-home-load benchmark-plan-render benchmark-plan-interaction benchmark-home-3d benchmark-startup clean test-deps

help:
	@echo "Common targets:"
	@echo "  make build      - Compile Sweet Home 3D jars with Ant (build/*.jar)."
	@echo "  make jar        - Build the distributable $(INSTALL_JAR)."
	@echo "  make run        - Run the distributable jar (builds it first)."
	@echo "  make run-dev    - Run from local classes/jars without repackaging."
	@echo "  make vr-plugin  - Build plugins/VRPreview.sh3p (legacy VR preview)."
	@echo "  make webxr-plugin - Build plugins/WebXRPreview.sh3p (WebXR OBJ preview)."
	@echo "  make run-webxr-preview [VR_HOME_FILE=<file.sh3d>] - Build plugin and launch app with WebXR plugin."
	@echo "  make test       - Compile and run the complete JUnit suite."
	@echo "  make test-core  - Run tests that don't require Java 3D / OpenGL."
	@echo "  make test-gui   - Run Swing/controller tests without the native Java 3D pipeline."
	@echo "  make test-local - Run the complete suite through WSLg/X11 or Xvfb."
	@echo "  make test-local-check - Check the local display and OpenGL setup."
	@echo "  make benchmark-home-load BENCHMARK_HOME=<file.sh3d> [BENCHMARK_MODE=recorder|direct]"
	@echo "  make benchmark-plan-render BENCHMARK_HOME=<file.sh3d> [BENCHMARK_ITERATIONS=10]"
	@echo "  make benchmark-plan-interaction BENCHMARK_HOME=<file.sh3d> [BENCHMARK_ITERATIONS=20]"
	@echo "  make benchmark-home-3d BENCHMARK_HOME=<file.sh3d> [BENCHMARK_MODE=scene|frame|update]"
	@echo "  make benchmark-startup BENCHMARK_HOME=<file.sh3d> [BENCHMARK_ITERATIONS=5]"
	@echo "  make clean      - Remove build artifacts produced by this Makefile."
	@echo "Variables: VERSION, CONDA_ACTIVATE, JAVA_OPTS."

# Build jars used for development
$(MAIN_JAR) $(DEV_RESOURCE_JARS):
	$(ANT) build furniture textures examples help

build: $(MAIN_JAR)

# Build the packaged executable jar (default Ant target does the same)
$(INSTALL_JAR):
	$(ANT) jarExecutable

jar: $(INSTALL_JAR)

# Run packaged application (matches release configuration)
run: $(INSTALL_JAR)
	LANG=$(RUN_LANG) LC_ALL=$(RUN_LC_ALL) $(JAVA) $(JAVA_OPTS) \
	  -Duser.language=$(USER_LANG) -Duser.country=$(USER_COUNTRY) -Duser.variant=$(USER_VARIANT) \
	  -Djava.library.path="$(JAVA_LIB_PATH)" -Djogamp.gluegen.UseTempJarCache=false -jar $(INSTALL_JAR)

# Run directly from build output and repo libs (useful during development)
run-dev: $(MAIN_JAR) $(DEV_RESOURCE_JARS)
	LANG=$(RUN_LANG) LC_ALL=$(RUN_LC_ALL) $(JAVA) $(JAVA_OPTS) \
	  -Duser.language=$(USER_LANG) -Duser.country=$(USER_COUNTRY) -Duser.variant=$(USER_VARIANT) \
	  -Djava.library.path="$(JAVA_LIB_PATH)" \
	  -Djogamp.gluegen.UseTempJarCache=false \
	  -cp "$(DEV_CLASS_PATH)$(CPSEP)lib/*$(CPSEP)lib/java3d-1.6/*$(CPSEP)libtest/jnlp.jar" \
	  com.eteks.sweethome3d.SweetHome3D

# Ensure JUnit dependencies are available for tests
$(TEST_JARS):
	@mkdir -p libtest
	@if [ "$@" = "libtest/junit-4.13.2.jar" ]; then \
	  echo "Downloading JUnit 4.13.2..."; \
	  curl -L --fail -o "$@" $(JUNIT_URL); \
	else \
	  echo "Downloading Hamcrest 1.3..."; \
	  curl -L --fail -o "$@" $(HAMCREST_URL); \
	fi

test-deps: $(TEST_JARS)

# Compile and run JUnit 4 tests
test: $(MAIN_JAR) test-deps
	@mkdir -p $(TEST_CLASSES)
	@rm -rf build/test-analysis && mkdir -p build/test-analysis
	@cd build/test-analysis && $(JAR) xf ../SweetHome3D.jar
	$(JAVAC) $(TEST_JAVAC_FLAGS) -encoding ISO-8859-1 -cp "$(TEST_COMPILE_CP)" \
	  -d $(TEST_CLASSES) $(FILTERED_TEST_SOURCES)
	LANG=$(RUN_LANG) LC_ALL=$(RUN_LC_ALL) $(JAVA) $(JAVA_TEST_OPTS) \
	  -Duser.language=$(USER_LANG) -Duser.country=$(USER_COUNTRY) -Duser.variant=$(USER_VARIANT) \
	  -Dsweethome3d.testClasses=build/test-analysis \
	  -Djava.library.path="$(JAVA_LIB_PATH)" -cp "$(TEST_RUN_CP)" \
	  org.junit.runner.JUnitCore $(TEST_NAMES)

test-core:
	$(MAKE) test TEST_SOURCES="$(CORE_TEST_SOURCES)" JAVA_TEST_OPTS="$(JAVA_TEST_OPTS) -Djava.awt.headless=true"

test-gui:
	$(MAKE) test SKIP_3D_TESTS=1

test-local:
	CONDA_ACTIVATE='$(CONDA_ACTIVATE)' TEST_DISPLAY_MODE='$(TEST_DISPLAY_MODE)' \
	  TEST_JAVA_HOME='$(TEST_JAVA_HOME)' TEST_JAVA='$(TEST_JAVA)' \
	  scripts/test-linux-display.sh run

test-local-check:
	CONDA_ACTIVATE='$(CONDA_ACTIVATE)' TEST_DISPLAY_MODE='$(TEST_DISPLAY_MODE)' \
	  TEST_JAVA_HOME='$(TEST_JAVA_HOME)' TEST_JAVA='$(TEST_JAVA)' \
	  scripts/test-linux-display.sh check

benchmark-home-load: $(MAIN_JAR) $(DEV_RESOURCE_JARS)
	@test -n "$(BENCHMARK_HOME)" || (echo "BENCHMARK_HOME is required" >&2; exit 2)
	@mkdir -p $(PERFORMANCE_CLASSES)
	$(JAVAC) $(TEST_JAVAC_FLAGS) -encoding ISO-8859-1 -cp "$(TEST_COMPILE_CP)" \
	  -d $(PERFORMANCE_CLASSES) $(HOME_LOAD_BENCHMARK_SOURCE)
	$(RUN_IN_ENV)HOME_LOAD_JFR='$(HOME_LOAD_JFR)' scripts/profile-home-load.sh \
	  "$(BENCHMARK_HOME)" "$(or $(BENCHMARK_MODE),recorder)" "$(or $(BENCHMARK_ITERATIONS),1)"

benchmark-plan-render: $(MAIN_JAR) $(DEV_RESOURCE_JARS)
	@test -n "$(BENCHMARK_HOME)" || (echo "BENCHMARK_HOME is required" >&2; exit 2)
	@mkdir -p $(PERFORMANCE_CLASSES)
	$(JAVAC) $(TEST_JAVAC_FLAGS) -encoding ISO-8859-1 -cp "$(TEST_COMPILE_CP)" \
	  -d $(PERFORMANCE_CLASSES) $(PLAN_RENDER_BENCHMARK_SOURCE)
	$(RUN_IN_ENV)PLAN_RENDER_JFR='$(PLAN_RENDER_JFR)' scripts/profile-plan-render.sh \
	  "$(BENCHMARK_HOME)" "$(or $(BENCHMARK_ITERATIONS),10)"

benchmark-home-3d: $(MAIN_JAR) $(DEV_RESOURCE_JARS)
	@test -n "$(BENCHMARK_HOME)" || (echo "BENCHMARK_HOME is required" >&2; exit 2)
	@mkdir -p $(PERFORMANCE_CLASSES)
	$(JAVAC) $(TEST_JAVAC_FLAGS) -encoding ISO-8859-1 -cp "$(TEST_COMPILE_CP)" \
	  -d $(PERFORMANCE_CLASSES) $(HOME_3D_BENCHMARK_SOURCE)
	$(RUN_IN_ENV)HOME_3D_JAVA='$(HOME_3D_JAVA)' HOME_3D_JFR='$(HOME_3D_JFR)' scripts/profile-home-3d.sh \
	  "$(BENCHMARK_HOME)" "$(or $(BENCHMARK_MODE),scene)" "$(or $(BENCHMARK_ITERATIONS),5)"

benchmark-plan-interaction: $(MAIN_JAR) $(DEV_RESOURCE_JARS)
	@test -n "$(BENCHMARK_HOME)" || (echo "BENCHMARK_HOME is required" >&2; exit 2)
	@mkdir -p $(PERFORMANCE_CLASSES)
	$(JAVAC) $(TEST_JAVAC_FLAGS) -encoding ISO-8859-1 -cp "$(TEST_COMPILE_CP)" \
	  -d $(PERFORMANCE_CLASSES) $(PLAN_INTERACTION_BENCHMARK_SOURCE)
	$(RUN_IN_ENV)PLAN_INTERACTION_JFR='$(PLAN_INTERACTION_JFR)' scripts/profile-plan-interaction.sh \
	  "$(BENCHMARK_HOME)" "$(or $(BENCHMARK_ITERATIONS),20)"

benchmark-startup: $(MAIN_JAR) $(DEV_RESOURCE_JARS)
	@test -n "$(BENCHMARK_HOME)" || (echo "BENCHMARK_HOME is required" >&2; exit 2)
	@mkdir -p $(PERFORMANCE_CLASSES)
	$(JAVAC) $(TEST_JAVAC_FLAGS) -encoding ISO-8859-1 -cp "$(TEST_COMPILE_CP)" \
	  -d $(PERFORMANCE_CLASSES) $(STARTUP_BENCHMARK_SOURCE)
	$(RUN_IN_ENV)STARTUP_JFR='$(STARTUP_JFR)' scripts/profile-startup.sh \
	  "$(BENCHMARK_HOME)" "$(or $(BENCHMARK_ITERATIONS),5)"

clean:
	rm -rf build $(INSTALL_JAR) $(TEST_CLASSES) $(PERFORMANCE_CLASSES)

# Build legacy VR preview plugin (.sh3p)
vr-plugin: $(INSTALL_JAR)
	@mkdir -p .plugin-build/vr plugins
	$(JAVAC) -encoding ISO-8859-1 -cp "$(INSTALL_JAR)" \
	  -d .plugin-build/vr src/com/eteks/sweethome3d/plugin/vr/VRPreviewPlugin.java
	cp src/com/eteks/sweethome3d/plugin/vr/ApplicationPlugin.properties .plugin-build/vr/
	jar cf plugins/VRPreview.sh3p -C .plugin-build/vr .
	rm -rf .plugin-build/vr
	@echo "Plugin built at plugins/VRPreview.sh3p"

# Build WebXR preview plugin (.sh3p)
webxr-plugin: $(INSTALL_JAR)
	@mkdir -p .plugin-build/webxr plugins
	$(JAVAC) --release 8 -encoding ISO-8859-1 -cp "$(INSTALL_JAR)$(CPSEP)lib/*$(CPSEP)lib/java3d-1.6/*" \
	  -d .plugin-build/webxr pluginsrc/com/eteks/sweethome3d/plugin/webxr/WebXRPreviewPlugin.java
	cp pluginsrc/com/eteks/sweethome3d/plugin/webxr/ApplicationPlugin.properties .plugin-build/webxr/
	$(JAR) cf plugins/WebXRPreview.sh3p -C .plugin-build/webxr .
	rm -rf .plugin-build/webxr
	@echo "Plugin built at plugins/WebXRPreview.sh3p"

# Build WebXR plugin and launch app with plugins folder set
run-webxr-preview: webxr-plugin
	@VR_HOME_FILE="$(VR_HOME_FILE)"; \
	if [ -n "$$VR_HOME_FILE" ]; then \
	  OPEN_ARG="$$VR_HOME_FILE"; \
	else \
	  OPEN_ARG=""; \
	fi; \
	LANG=$(RUN_LANG) LC_ALL=$(RUN_LC_ALL) $(JAVA) $(JAVA_OPTS) \
	  -Dcom.eteks.sweethome3d.applicationFolders="$(CURDIR)" \
	  -Duser.language=$(USER_LANG) -Duser.country=$(USER_COUNTRY) -Duser.variant=$(USER_VARIANT) \
	  -Djava.library.path="$(JAVA_LIB_PATH)" -Djogamp.gluegen.UseTempJarCache=false \
	  -jar $(INSTALL_JAR) $$OPEN_ARG
