package com.craftinginterpreters.lox;

import java.util.List;
import java.util.Map;
import java.util.Set;

class LoxClass extends LoxInstance implements LoxCallable {
  final String name;
  final LoxClass superclass;
  private final Map<String, LoxFunction> methods;
  private final Set<String> getters;

  LoxClass(String name, Map<String, LoxFunction> methods, Set<String> getters) {
    super(null);
    this.name = name;
    this.superclass = null;
    this.methods = methods;
    this.getters = getters;
  }

  LoxClass(String name, LoxClass superclass, Map<String, LoxFunction> staticMethods, Set<String> staticGetters, Map<String, LoxFunction> methods, Set<String> getters) {
    super(new LoxClass("_" + name, staticMethods, staticGetters));
    this.name = name;
    this.superclass = superclass;
    this.methods = methods;
    this.getters = getters;
  }

  @Override
  public Object call(Interpreter interpreter,
                     List<Object> arguments) {
    LoxInstance instance = new LoxInstance(this);
    LoxFunction initializer = findMethod("init");
    if (initializer != null) {
      initializer.bind(instance).call(interpreter, arguments);
    }

    return instance;
  }

  LoxFunction findMethod(String name) {
    if (methods.containsKey(name)) {
      return methods.get(name);
    }

    if (superclass != null) {
      return superclass.findMethod(name);
    }

    return null;
  }

  boolean isGetter(String name) {
    return getters.contains(name);
  }

  @Override
  public int arity() {
    LoxFunction initializer = findMethod("init");
    if (initializer == null) return 0;
    return initializer.arity();
  }

  @Override
  public String toString() {
    return name;
  }
}

