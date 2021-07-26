package com.craftinginterpreters.lox;

class AstPrinter implements Expr.Visitor<String>,
                             Stmt.Visitor<String> {
  String print(Stmt stmt) {
    return stmt.accept(this);
  }

  String print(Expr expr) {
    return expr.accept(this);
  }

  @Override
  public String visitBlockStmt(Stmt.Block stmt) {
    return parenthesize("do", stmt.statements.toArray(new Stmt[stmt.statements.size()]));
  }

  @Override
  public String visitExpressionStmt(Stmt.Expression stmt) {
    return stmt.expression.accept(this);
  }

  @Override
  public String visitPrintStmt(Stmt.Print stmt) {
    return parenthesize("print", stmt.expression);
  }

  @Override
  public String visitVarStmt(Stmt.Var stmt) {
    if (stmt.initializer != null) {
      return parenthesize("define " + stmt.name.lexeme, stmt.initializer);
    }
    return "(define " + stmt.name.lexeme + ")";
  }

  @Override
  public String visitAssignExpr(Expr.Assign expr) {
    return parenthesize("assign " + expr.name.lexeme, expr.value);
  }

  @Override
  public String visitBinaryExpr(Expr.Binary expr) {
    return parenthesize(expr.operator.lexeme,
                        expr.left, expr.right);
  }

  @Override
  public String visitGroupingExpr(Expr.Grouping expr) {
    return parenthesize("group", expr.expression);
  }

  @Override
  public String visitLiteralExpr(Expr.Literal expr) {
    if (expr.value == null) return "nil";
    if (expr.value instanceof String) return "\"" + expr.value.toString() + "\"";
    return expr.value.toString();
  }

  @Override
  public String visitTernaryExpr(Expr.Ternary expr) {
    return parenthesize(":?", expr.left, expr.middle, expr.right);
  }

  @Override
  public String visitUnaryExpr(Expr.Unary expr) {
    return parenthesize(expr.operator.lexeme, expr.right);
  }

  public String visitVariableExpr(Expr.Variable expr) {
    return expr.name.lexeme;
  }

  private String parenthesize(String name, Stmt... stmts) {
    StringBuilder builder = new StringBuilder();

    builder.append("(").append(name);
    for (Stmt stmt : stmts) {
      builder.append(" ");
      builder.append(stmt.accept(this));
    }
    builder.append(")");

    return builder.toString();
  }

  private String parenthesize(String name, Expr... exprs) {
    StringBuilder builder = new StringBuilder();

    builder.append("(").append(name);
    for (Expr expr : exprs) {
      builder.append(" ");
      builder.append(expr.accept(this));
    }
    builder.append(")");

    return builder.toString();
  }
}
