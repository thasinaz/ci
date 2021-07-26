PACKAGE := lox
DIR := .

BUILD_DIR := build

GENERATED_FILE := Expr.java Stmt.java
GENERATED_FILE := $(addprefix $(DIR)/com/craftinginterpreters/$(PACKAGE)/, $(GENERATED_FILE))

SOURCES := $(wildcard $(DIR)/com/craftinginterpreters/$(PACKAGE)/*.java)
CLASSES := $(addprefix $(BUILD_DIR)/, $(SOURCES:.java=.class))

JAVA_OPTIONS := -Werror

all: $(CLASSES)

# Compile a single .java file to .class.
$(BUILD_DIR)/$(DIR)/%.class: $(DIR)/%.java
	@ mkdir -p $(BUILD_DIR)/$(DIR)
	@ javac -cp $(DIR) -d $(BUILD_DIR)/$(DIR) $(JAVA_OPTIONS) -implicit:none $<
	@ printf "%8s %-60s %s\n" javac $< "$(JAVA_OPTIONS)"

generate_code:
	java com/craftinginterpreters/tool/GenerateAst.java com/craftinginterpreters/$(PACKAGE)

clean:
	rm -r $(BUILD_DIR) $(GENERATED_FILE)

.PHONY: all generate_code clean
