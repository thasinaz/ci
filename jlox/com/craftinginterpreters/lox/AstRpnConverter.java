package com.craftinginterpreters.lox;

class AstRpnConverter implements Expr.Visitor<String> {
  String convert(Expr expr) {
    return expr.accept(this);
  }

  @Override
  public String visitAssignExpr(Expr.Assign expr) {
    StringBuilder builder = new StringBuilder(expr.name.lexeme);
    builder.append(expr.value.accept(this));
    builder.append("=");

    return builder.toString();
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
  public String visitTernaryExpr(Expr.Ternary expr) {
    return rpn("?:", expr.left, expr.middle, expr.right);
  }

  @Override
  public String visitUnaryExpr(Expr.Unary expr) {
    return rpn(expr.operator.lexeme, expr.right);
  }

  @Override
  public String visitVariableExpr(Expr.Variable expr) {
    return expr.name.lexeme;
  }

  private String rpn(String name, Expr... exprs) {
    StringBuilder builder = new StringBuilder();

    for (Expr expr : exprs) {
      builder.append(convert(expr));
      builder.append(" ");
    }
    builder.append(name);

    return builder.toString();
  }
}
