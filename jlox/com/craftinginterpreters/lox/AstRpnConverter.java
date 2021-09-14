package com.craftinginterpreters.lox;

class AstRpnConverter implements Expr.Visitor<String>,
                                 Stmt.Visitor<String> {
  String convert(Stmt stmt) {
    return stmt.accept(this);
  }

  String convert(Expr expr) {
    return expr.accept(this);
  }

  @Override
  public String visitBlockStmt(Stmt.Block stmt) {
    StringBuilder builder = new StringBuilder("{ ");
    builder.append(rpn("}", stmt.statements.toArray(new Stmt[stmt.statements.size()])));

    return builder.toString();
  }

  @Override
  public String visitBreakStmt(Stmt.Break stmt) {
    return stmt.keyword.lexeme;
  }

  @Override
  public String visitClassStmt(Stmt.Class stmt) {
    StringBuilder builder = new StringBuilder(stmt.name.lexeme);
    builder.append(" ");
    builder.append(rpn("class", stmt.methods.toArray(new Stmt[stmt.methods.size()])));

    return builder.toString();
  }

  @Override
  public String visitExpressionStmt(Stmt.Expression stmt) {
    return convert(stmt.expression);
  }

  @Override
  public String visitFunctionStmt(Stmt.Function stmt) {
    StringBuilder builder = new StringBuilder(stmt.name.lexeme);
    builder.append(" ");
    builder.append(convert(stmt.lambda));
    builder.append(" define");

    return builder.toString();
  }

  @Override
  public String visitIfStmt(Stmt.If stmt) {
    StringBuilder builder = new StringBuilder(convert(stmt.condition));
    builder.append(" ");
    if (stmt.elseBranch == null) {
      builder.append(rpn("if", stmt.thenBranch));
    } else {
      builder.append(rpn("if", stmt.thenBranch, stmt.elseBranch));
    }

    return builder.toString();
  }

  @Override
  public String visitPrintStmt(Stmt.Print stmt) {
    return rpn("print", stmt.expression);
  }

  @Override
  public String visitReturnStmt(Stmt.Return stmt) {
    if (stmt.value != null) {
      return rpn("return", stmt.value);
    }
    return "nil return";
  }

  @Override
  public String visitVarStmt(Stmt.Var stmt) {
    StringBuilder builder = new StringBuilder(stmt.name.lexeme);
    builder.append(" ");
    if (stmt.initializer == null) {
      builder.append("define");
    } else {
      builder.append(rpn("define", stmt.initializer));
    }

    return builder.toString();
  }

  @Override
  public String visitWhileStmt(Stmt.While stmt) {
    StringBuilder builder = new StringBuilder(convert(stmt.condition));
    builder.append(" ");
    builder.append(rpn("while", stmt.body));

    return builder.toString();
  }

  @Override
  public String visitAssignExpr(Expr.Assign expr) {
    StringBuilder builder = new StringBuilder(expr.name.lexeme);
    builder.append(" ");
    builder.append(rpn("=", expr.value));

    return builder.toString();
  }

  @Override
  public String visitBinaryExpr(Expr.Binary expr) {
    return rpn(expr.operator.lexeme,
                        expr.left, expr.right);
  }

  @Override
  public String visitCallExpr(Expr.Call expr) {
    StringBuilder builder = new StringBuilder(convert(expr.callee));
    builder.append(" ");
    builder.append(rpn("call", expr.arguments.toArray(new Expr[expr.arguments.size()])));

    return builder.toString();
  }

  @Override
  public String visitGetExpr(Expr.Get expr) {
    StringBuilder builder = new StringBuilder(convert(expr.object));
    builder.append(" ");
    builder.append(expr.name.lexeme);
    builder.append(" get");

    return builder.toString();
  }

  @Override
  public String visitGroupingExpr(Expr.Grouping expr) {
    return rpn("group", expr.expression);
  }

  @Override
  public String visitLambdaExpr(Expr.Lambda expr) {
    StringBuilder builder = new StringBuilder();
    for (Token param : expr.params) {
      builder.append(param.lexeme);
      builder.append(" ");
    }
    builder.append(convert(new Stmt.Block(expr.body)));
    builder.append(" lambda");

    return builder.toString();
  }

  @Override
  public String visitLiteralExpr(Expr.Literal expr) {
    if (expr.value == null) return "nil";
    if (expr.value instanceof String) return "\"" + expr.value.toString() + "\"";
    return expr.value.toString();
  }

  @Override
  public String visitLogicalExpr(Expr.Logical expr) {
    return rpn(expr.operator.lexeme, expr.left, expr.right);
  }

  @Override
  public String visitSetExpr(Expr.Set expr) {
    StringBuilder builder = new StringBuilder(convert(expr.object));
    builder.append(" ");
    builder.append(expr.name.lexeme);
    builder.append(" ");
    builder.append(convert(expr.value));
    builder.append(" set");

    return builder.toString();
  }

  @Override
  public String visitSuperExpr(Expr.Super expr) {
    return "super";
  }

  @Override
  public String visitTernaryExpr(Expr.Ternary expr) {
    return rpn("?:", expr.left, expr.middle, expr.right);
  }

  @Override
  public String visitThisExpr(Expr.This expr) {
    return "this";
  }

  @Override
  public String visitUnaryExpr(Expr.Unary expr) {
    return rpn(expr.operator.lexeme, expr.right);
  }

  @Override
  public String visitVariableExpr(Expr.Variable expr) {
    return expr.name.lexeme;
  }

  private String rpn(String name, Stmt... stmts) {
    StringBuilder builder = new StringBuilder();

    for (Stmt stmt : stmts) {
      builder.append(convert(stmt));
      builder.append(" ");
    }
    builder.append(name);

    return builder.toString();
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
