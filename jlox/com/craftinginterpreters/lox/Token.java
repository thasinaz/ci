package com.craftinginterpreters.lox;

class Token {
  final TokenType type;
  final String lexeme;
  final Object literal;
  final int line;

  Token(TokenType type, String lexeme, Object literal, int line) {
    this.type = type;
    this.lexeme = lexeme;
    this.literal = literal;
    this.line = line;
  }

  @Override
  public boolean equals(Object object) {
    if (object instanceof Token) {
      return this.lexeme.equals(((Token)object).lexeme);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return lexeme.hashCode();
  }

  @Override
  public String toString() {
    return type + " " + lexeme + " " + literal;
  }
}
