package com.craftinginterpreters.lox;

class AstRpnConverter implements Expr.Visitor<String> {
  String convert(Expr expr) {
    return expr.accept(this);
  }

  @Override
  public String visitBinaryExpr(Expr.Binary expr) {
    return rpn(expr.operator.lexeme,
                        expr.left, expr.right);
  }

  @Override
  public String visitGroupingExpr(Expr.Grouping expr) {
    return rpn("group", expr.expression);
  }

  @Override
  public String visitLiteralExpr(Expr.Literal expr) {
    if (expr.value == null) return "nil";
    return expr.value.toString();
  }

  @Override
  public String visitUnaryExpr(Expr.Unary expr) {
    return rpn(expr.operator.lexeme, expr.right);
  }

  @Override
  public String visitTernaryExpr(Expr.Ternary expr) {
    return rpn("?:", expr.left, expr.middle, expr.right);
  }

  private String rpn(String name, Expr... exprs) {
    StringBuilder builder = new StringBuilder();

    for (Expr expr : exprs) {
      builder.append(expr.accept(this));
      builder.append(" ");
    }
    builder.append(name);

    return builder.toString();
  }

  public static void main(String[] args) {
    Expr expression = new Expr.Binary(
        new Expr.Unary(
            new Token(TokenType.MINUS, "-", null, 1),
            new Expr.Literal(123)),
        new Token(TokenType.STAR, "*", null, 1),
        new Expr.Grouping(
            new Expr.Literal(45.67)));

    System.out.println(new AstRpnConverter().convert(expression));
  }
}
