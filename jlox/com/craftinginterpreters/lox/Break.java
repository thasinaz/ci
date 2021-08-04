package com.craftinginterpreters.lox;

class Break extends RuntimeError{
  Break(Token token, String message) {
    super(token, message);
  }
}
