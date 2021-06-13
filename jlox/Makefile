SRC_PATH = src/main/com/craftinginterpreters/lox/
SRC := $(wildcard $(SRC_PATH)/*.java)
CLASS = $(SRC:.java=.class)

.PHONY: all
all: $(CLASS)

$(CLASS): $(SRC)
	javac $(SRC)

.PHONY: clean
clean:
	rm $(CLASS)

