PACKAGE := lox
DIR := .

BUILD_DIR := build

SOURCES := $(wildcard $(DIR)/com/craftinginterpreters/$(PACKAGE)/*.java)
CLASSES := $(addprefix $(BUILD_DIR)/, $(SOURCES:.java=.class))

JAVA_OPTIONS := -Werror

default: generate_code build clean_generated_code

build: $(CLASSES)

# Compile a single .java file to .class.
$(BUILD_DIR)/$(DIR)/%.class: $(DIR)/%.java
	@ mkdir -p $(BUILD_DIR)/$(DIR)
	@ javac -cp $(DIR) -d $(BUILD_DIR)/$(DIR) $(JAVA_OPTIONS) -implicit:none $<
	@ printf "%8s %-60s %s\n" javac $< "$(JAVA_OPTIONS)"

generate_code:
	java com/craftinginterpreters/tool/GenerateAst.java com/craftinginterpreters/lox

clean_generated_code:
	rm com/craftinginterpreters/lox/Expr.java

clean:
	rm -r $(BUILD_DIR)

.PHONY: default build generate_code clean_generated_code clean
