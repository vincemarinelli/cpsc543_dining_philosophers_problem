SHELL := bash
.SHELLFLAGS := -eu -o pipefail -c
.DELETE_ON_ERROR:
.SUFFIXES:
MAKEFLAGS += --warn-undefined-variables
MAKEFLAGS += --no-builtin-rules

PROJECT      := dining-philosophers
SRCDIR       := src
OUTDIR       := out
TEST_SRCDIR  := src/test
TEST_OUTDIR  := out-test
LIB_DIR      := lib
REPORTS_DIR  := reports
COVERAGE_DIR := coverage
JAVADOC_DIR  := docs/javadoc

# ── Tool versions ─────────────────────────────────────────────────────────────
SPOTBUGS_VERSION   := 4.8.6
CHECKSTYLE_VERSION := 10.17.0
JUNIT_VERSION      := 1.11.4
JACOCO_VERSION     := 0.8.12

SPOTBUGS_DIR := $(LIB_DIR)/spotbugs
SPOTBUGS_ZIP := $(SPOTBUGS_DIR)/spotbugs-$(SPOTBUGS_VERSION).zip
SPOTBUGS_JAR := $(SPOTBUGS_DIR)/spotbugs-$(SPOTBUGS_VERSION)/lib/spotbugs.jar

CHECKSTYLE_JAR := $(LIB_DIR)/checkstyle-$(CHECKSTYLE_VERSION)-all.jar
JUNIT_JAR      := $(LIB_DIR)/junit-platform-console-standalone-$(JUNIT_VERSION).jar
JACOCO_AGENT   := $(LIB_DIR)/org.jacoco.agent-$(JACOCO_VERSION)-runtime.jar
JACOCO_CLI     := $(LIB_DIR)/org.jacoco.cli-$(JACOCO_VERSION)-nodeps.jar
JCIP_JAR       := $(LIB_DIR)/jcip-annotations-1.0.jar

SOURCES      := $(shell find $(SRCDIR) -name '*.java' -not -path '$(TEST_SRCDIR)/*')
TEST_SOURCES := $(shell find $(TEST_SRCDIR) -name '*.java' 2>/dev/null)

SENTINEL := $(OUTDIR)/.compiled
MAIN_CP  := $(JCIP_JAR)
TEST_CP  := $(OUTDIR):$(TEST_OUTDIR):$(MAIN_CP):$(JUNIT_JAR)

EXEC_FILE   := $(COVERAGE_DIR)/jacoco.exec
REPORT_FILE := $(COVERAGE_DIR)/jacoco.csv

.PHONY: all run compile-tests test coverage spotbugs checkstyle analyze javadoc libs clean help

## Compile all Java sources (default)
all: $(SENTINEL)

$(SENTINEL): $(SOURCES) $(JCIP_JAR) | $(OUTDIR)
	javac -cp $(MAIN_CP) -sourcepath $(SRCDIR) -d $(OUTDIR) $(SOURCES)
	@touch $@

$(OUTDIR):
	@mkdir -p $@

## Run the simulator (interactive)
run: $(SENTINEL)
	java -cp $(OUTDIR):$(MAIN_CP) Main

# ── Library downloads ─────────────────────────────────────────────────────────
$(LIB_DIR):
	@mkdir -p $@

$(JCIP_JAR): | $(LIB_DIR)
	curl -fL -o $@ \
	  https://repo1.maven.org/maven2/net/jcip/jcip-annotations/1.0/jcip-annotations-1.0.jar

$(JUNIT_JAR): | $(LIB_DIR)
	curl -fL -o $@ \
	  https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/$(JUNIT_VERSION)/junit-platform-console-standalone-$(JUNIT_VERSION).jar

$(JACOCO_AGENT): | $(LIB_DIR)
	curl -fL -o $@ \
	  https://repo1.maven.org/maven2/org/jacoco/org.jacoco.agent/$(JACOCO_VERSION)/org.jacoco.agent-$(JACOCO_VERSION)-runtime.jar

$(JACOCO_CLI): | $(LIB_DIR)
	curl -fL -o $@ \
	  https://repo1.maven.org/maven2/org/jacoco/org.jacoco.cli/$(JACOCO_VERSION)/org.jacoco.cli-$(JACOCO_VERSION)-nodeps.jar

## Download all runtime and test dependencies (jcip, JUnit, JaCoCo)
libs: $(JCIP_JAR) $(JUNIT_JAR) $(JACOCO_AGENT) $(JACOCO_CLI)

# ── SpotBugs download ─────────────────────────────────────────────────────────
$(SPOTBUGS_DIR):
	@mkdir -p $@

$(SPOTBUGS_ZIP): | $(SPOTBUGS_DIR)
	curl -fL -o $@ \
	  https://github.com/spotbugs/spotbugs/releases/download/$(SPOTBUGS_VERSION)/spotbugs-$(SPOTBUGS_VERSION).zip

$(SPOTBUGS_JAR): $(SPOTBUGS_ZIP)
	unzip -q -o $< -d $(SPOTBUGS_DIR)
	touch $@

# ── CheckStyle download ───────────────────────────────────────────────────────
$(CHECKSTYLE_JAR): | $(LIB_DIR)
	curl -fL -o $@ \
	  https://github.com/checkstyle/checkstyle/releases/download/checkstyle-$(CHECKSTYLE_VERSION)/checkstyle-$(CHECKSTYLE_VERSION)-all.jar

# ── Tests ─────────────────────────────────────────────────────────────────────
$(TEST_OUTDIR):
	@mkdir -p $@

## Compile test sources (place tests under src/test/ mirroring the source package structure)
compile-tests: $(SENTINEL) $(JUNIT_JAR) | $(TEST_OUTDIR)
	@[ -z "$(TEST_SOURCES)" ] && echo "No test sources in $(TEST_SRCDIR)" || \
	  javac -cp $(OUTDIR):$(MAIN_CP):$(JUNIT_JAR) -d $(TEST_OUTDIR) $(TEST_SOURCES)

## Run JUnit tests
test: compile-tests
	java -cp $(TEST_CP) \
	     org.junit.platform.console.ConsoleLauncher \
	     --scan-class-path=$(TEST_OUTDIR) \
	     --fail-if-no-tests

## Run tests under JaCoCo agent; CSV report written to coverage/jacoco.csv
coverage: compile-tests $(JACOCO_AGENT) $(JACOCO_CLI)
	@mkdir -p $(COVERAGE_DIR)
	java -javaagent:$(JACOCO_AGENT)=destfile=$(EXEC_FILE) \
	     -cp $(TEST_CP) \
	     org.junit.platform.console.ConsoleLauncher \
	     --scan-class-path=$(TEST_OUTDIR) \
	     --fail-if-no-tests
	java -jar $(JACOCO_CLI) report $(EXEC_FILE) \
	     --classfiles $(OUTDIR) \
	     --sourcefiles $(SRCDIR) \
	     --csv $(REPORT_FILE)
	@echo "Coverage report: $(REPORT_FILE)"

# ── Static analysis ───────────────────────────────────────────────────────────
$(REPORTS_DIR):
	@mkdir -p $@

## Run SpotBugs on compiled classes; report written to reports/spotbugs.txt
spotbugs: $(SENTINEL) $(SPOTBUGS_JAR) | $(REPORTS_DIR)
	java -jar $(SPOTBUGS_JAR) \
	  -textui \
	  -effort:max \
	  -auxclasspath $(MAIN_CP) \
	  -sourcepath $(SRCDIR) \
	  -output $(REPORTS_DIR)/spotbugs.txt \
	  $(OUTDIR) || true
	@echo "SpotBugs report → $(REPORTS_DIR)/spotbugs.txt"

## Run CheckStyle (Google style) on all sources; report written to reports/checkstyle.txt
checkstyle: $(CHECKSTYLE_JAR) | $(REPORTS_DIR)
	java -jar $(CHECKSTYLE_JAR) \
	  -c /google_checks.xml \
	  -f plain \
	  -o $(REPORTS_DIR)/checkstyle.txt \
	  $(SRCDIR) || true
	@echo "CheckStyle report → $(REPORTS_DIR)/checkstyle.txt"

## Run SpotBugs and CheckStyle
analyze: spotbugs checkstyle

# ── Javadoc ───────────────────────────────────────────────────────────────────
## Generate Javadoc to docs/javadoc/
javadoc: $(SENTINEL)
	@mkdir -p $(JAVADOC_DIR)
	javadoc -cp $(MAIN_CP) \
	        -sourcepath $(SRCDIR) \
	        -subpackages controller:model:solutions:monitor \
	        -d $(JAVADOC_DIR) \
	        -windowtitle "Dining Philosophers" \
	        -doctitle "Dining Philosophers"
	@echo "Javadoc generated: $(JAVADOC_DIR)/index.html"

# ── Housekeeping ──────────────────────────────────────────────────────────────
## Remove all generated files and downloaded libraries
clean:
	$(RM) -r $(OUTDIR) $(TEST_OUTDIR) $(COVERAGE_DIR) $(LIB_DIR) $(REPORTS_DIR) $(JAVADOC_DIR)

## Show available targets
help:
	@echo "$(PROJECT)"
	@echo ""
	@sed -n 's/^## //p' $(MAKEFILE_LIST)
