#include <stdarg.h>
#include <stdio.h>
#include <string.h>
#include <time.h>

#include "common.h"
#include "compiler.h"
#include "debug.h"
#include "memory.h"
#include "object.h"
#include "vm.h"

VM vm;

static bool clockNative(int argCount, Value* args) {
  args[-1] = NUMBER_VAL((double)clock() / CLOCKS_PER_SEC);
  return true;
}

static bool errNative(int argCount, Value* args) {
  args[-1] = OBJ_VAL(stringLiteral("Error!", 6));
  return false;
}

static bool hasFieldNative(int argCount, Value* args) {
  if (!IS_INSTANCE(args[0])) {
    args[-1] = OBJ_VAL(stringLiteral("Not object", 10));
    return false;
  }
  if (!IS_STRING(args[1])) {
    args[-1] = OBJ_VAL(stringLiteral("Field name must be string", 25));
    return false;
  }

  ObjInstance* instance = AS_INSTANCE(args[0]);
  Value dummy;
  args[-1] = BOOL_VAL(tableGet(&instance->fields, args[1], &dummy));
  return true;
}

static bool getFieldNative(int argCount, Value* args) {
  if (!IS_INSTANCE(args[0])) {
    args[-1] = OBJ_VAL(stringLiteral("Not object", 10));
    return false;
  }
  if (!IS_STRING(args[1])) {
    args[-1] = OBJ_VAL(stringLiteral("Field name must be string", 25));
    return false;
  }

  ObjInstance* instance = AS_INSTANCE(args[0]);
  Value value;
  if (tableGet(&instance->fields, args[1], &value)) {
    args[-1] = value;
    return true;
  }

  args[-1] = OBJ_VAL(stringLiteral("Undefined field", 15));
  return false;
}

static bool setFieldNative(int argCount, Value* args) {
  if (!IS_INSTANCE(args[0])) {
    args[-1] = OBJ_VAL(stringLiteral("Not object", 10));
    return false;
  }
  if (!IS_STRING(args[1])) {
    args[-1] = OBJ_VAL(stringLiteral("Field name must be string", 25));
    return false;
  }

  ObjInstance* instance = AS_INSTANCE(args[0]);
  Value value;
  args[-1] = BOOL_VAL(tableSet(&instance->fields, args[1], args[2]));
  return true;
}

static bool delFieldNative(int argCount, Value* args) {
  if (!IS_INSTANCE(args[0])) {
    args[-1] = OBJ_VAL(stringLiteral("Not object", 10));
    return false;
  }
  if (!IS_STRING(args[1])) {
    args[-1] = OBJ_VAL(stringLiteral("Field name must be string", 25));
    return false;
  }

  ObjInstance* instance = AS_INSTANCE(args[0]);
  Value value;
  if (tableDelete(&instance->fields, args[1])) {
    args[-1] = NIL_VAL;
    return true;
  }

  args[-1] = OBJ_VAL(stringLiteral("Undefined field", 15));
  return false;
}

static void resetStack() {
  vm.stackTop = vm.stack;
  vm.frameCount = 0;
  vm.openUpvalues = NULL;
}

static void runtimeError(const char* format, ...) {
  va_list args;
  va_start(args, format);
  vfprintf(stderr, format, args);
  va_end(args);
  fputs("\n", stderr);

  for (int i = vm.frameCount - 1; i >= 0; i--) {
    CallFrame* frame = &vm.frames[i];
    ObjFunction* function = frame->function;
    size_t instruction = frame->ip - function->chunk.code - 1;
    fprintf(stderr, "[line %d] in ", getLine(&function->chunk.lines,
                                             instruction));
    if (function->name == NULL) {
      fprintf(stderr, "script\n");
    } else {
      fprintf(stderr, "%.*s()\n", function->name->length, function->name->chars);
    }
  }

  resetStack();
}

static void defineNative(const char* name, NativeFn function, int arity) {
  push(OBJ_VAL(stringLiteral(name, (int)strlen(name))));
  push(OBJ_VAL(newNative(function, arity)));
  int newIndex = vm.globalValues.count;
  writeValueArray(&vm.globalValues, vm.stack[1]);
  writeValueArray(&vm.globalIdentifiers, vm.stack[0]);
  tableSet(&vm.globalNames, vm.stack[0], NUMBER_VAL((double)newIndex));
  pop();
  pop();
}

void initVM() {
  resetStack();
  vm.objects = NULL;
  vm.bytesAllocated = 0;
  vm.nextGC = 1024 * 1024;

  vm.grayCount = 0;
  vm.grayCapacity = 0;
  vm.grayStack = NULL;
  vm.markValue = true;

  initTable(&vm.globalNames);
  initValueArray(&vm.globalValues);
  initValueArray(&vm.globalIdentifiers);
  initTable(&vm.strings);

  vm.initString = NIL_VAL;
  vm.initString = OBJ_VAL(stringLiteral("init", 4));

  defineNative("clock", clockNative, 0);
  defineNative("err", errNative, 0);
  defineNative("hasField", hasFieldNative, 2);
  defineNative("getField", getFieldNative, 2);
  defineNative("setField", setFieldNative, 3);
  defineNative("delField", delFieldNative, 2);
}

void freeVM() {
  freeTable(&vm.globalNames);
  freeValueArray(&vm.globalValues);
  freeValueArray(&vm.globalIdentifiers);
  freeTable(&vm.strings);
  vm.initString = NIL_VAL;
  freeObjects();
}

Value* top() {
  return vm.stackTop - 1;
}

void push(Value value) {
  *vm.stackTop = value;
  vm.stackTop++;
}

Value pop() {
  vm.stackTop--;
  return *vm.stackTop;
}

static Value peek(int distance) {
  return vm.stackTop[-1 - distance];
}

static bool callClosure(ObjClosure* closure, int argCount) {
  if (argCount != closure->function->arity) {
    runtimeError("Expected %d arguments but got %d.",
        closure->function->arity, argCount);
    return false;
  }

  if (vm.frameCount == FRAMES_MAX) {
    runtimeError("Stack overflow.");
    return false;
  }

  CallFrame* frame = &vm.frames[vm.frameCount++];
  frame->closure = closure;
  frame->function = closure->function;
  frame->ip = closure->function->chunk.code;
  frame->slots = vm.stackTop - argCount - 1;
  return true;
}

static bool callFunction(ObjFunction* function, int argCount) {
  if (argCount != function->arity) {
    runtimeError("Expected %d arguments but got %d.",
        function->arity, argCount);
    return false;
  }

  if (vm.frameCount == FRAMES_MAX) {
    runtimeError("Stack overflow.");
    return false;
  }

  CallFrame* frame = &vm.frames[vm.frameCount++];
  frame->closure = NULL;
  frame->function = function;
  frame->ip = function->chunk.code;
  frame->slots = vm.stackTop - argCount - 1;
  return true;
}

static bool callValue(Value callee, int argCount) {
  if (IS_OBJ(callee)) {
    switch (OBJ_TYPE(callee)) {
      case OBJ_BOUND_METHOD: {
        ObjBoundMethod* bound = AS_BOUND_METHOD(callee);
        vm.stackTop[-argCount - 1] = bound->receiver;
        return callClosure(bound->method, argCount);
      }
      case OBJ_CLASS: {
        ObjClass* klass = AS_CLASS(callee);
        vm.stackTop[-argCount - 1] = OBJ_VAL(newInstance(klass));
        if (!IS_NIL(klass->init)) {
          return callValue(klass->init, argCount);
        } else if (argCount != 0) {
          runtimeError("Expected 0 arguments but got %d.",
                       argCount);
          return false;
        }
        return true;
      }
      case OBJ_CLOSURE:
        return callClosure(AS_CLOSURE(callee), argCount);
      case OBJ_FUNCTION:
        return callFunction(AS_FUNCTION(callee), argCount);
      case OBJ_NATIVE: {
        ObjNative *native = AS_NATIVE(callee);
        if (argCount != native->arity) {
          runtimeError("Expected %d arguments but got %d.",
              native->arity, argCount);
          return false;
        }
        if (!native->function(argCount, vm.stackTop - argCount)) {
          runtimeError("%*s",
                       AS_STRING(vm.stackTop[-argCount - 1])->length,
                       AS_CSTRING(vm.stackTop[-argCount - 1]));
          return false;
        }
        vm.stackTop -= argCount;
        return true;
      }
      default:
        break; // Non-callable object type.
    }
  }
  runtimeError("Can only call functions and classes.");
  return false;
}

static bool invokeFromClass(ObjClass* klass, Value name,
                            int argCount) {
  Value method;
  if (!tableGet(&klass->methods, name, &method)) {
    runtimeError("Undefined property '%.*s'.", AS_STRING(name)->length, AS_CSTRING(name));
    return false;
  }
  return callValue(method, argCount);
}

static bool invoke(Value name, int argCount) {
  Value receiver = peek(argCount);

  if (!IS_INSTANCE(receiver)) {
    runtimeError("Only instances have methods.");
    return false;
  }

  ObjInstance* instance = AS_INSTANCE(receiver);

  Value value;
  if (tableGet(&instance->fields, name, &value)) {
    vm.stackTop[-argCount - 1] = value;
    return callValue(value, argCount);
  }

  return invokeFromClass(instance->klass, name, argCount);
}

static bool bindMethod(ObjClass* klass, Value name) {
  Value method;
  if (!tableGet(&klass->methods, name, &method)) {
    runtimeError("Undefined property '%.*s'.", AS_STRING(name)->length, AS_CSTRING(name));
    return false;
  }

  ObjBoundMethod* bound = newBoundMethod(peek(0),
                                         AS_CLOSURE(method));
  pop();
  push(OBJ_VAL(bound));
  return true;
}

static ObjUpvalue* captureUpvalue(Value* local) {
  ObjUpvalue* prevUpvalue = NULL;
  ObjUpvalue* upvalue = vm.openUpvalues;
  while (upvalue != NULL && upvalue->location > local) {
    prevUpvalue = upvalue;
    upvalue = upvalue->next;
  }

  if (upvalue != NULL && upvalue->location == local) {
    return upvalue;
  }

  ObjUpvalue* createdUpvalue = newUpvalue(local);
  createdUpvalue->next = upvalue;

  if (prevUpvalue == NULL) {
    vm.openUpvalues = createdUpvalue;
  } else {
    prevUpvalue->next = createdUpvalue;
  }

  return createdUpvalue;
}

static void closeUpvalues(Value* last) {
  while (vm.openUpvalues != NULL &&
         vm.openUpvalues->location >= last) {
    ObjUpvalue* upvalue = vm.openUpvalues;
    upvalue->closed = *upvalue->location;
    upvalue->location = &upvalue->closed;
    vm.openUpvalues = upvalue->next;
  }
}

static void defineMethod(Value name) {
  Value method = peek(0);
  ObjClass* klass = AS_CLASS(peek(1));
  if (valuesEqual(name, vm.initString)) klass->init = method;
  tableSet(&klass->methods, name, method);
  pop();
}

static bool isFalsey(Value value) {
  return IS_NIL(value) || (IS_BOOL(value) && !AS_BOOL(value));
}

static void concatenate() {
  ObjString* b = AS_STRING(peek(0));
  ObjString* a = AS_STRING(peek(1));

  int length = a->length + b->length;
  ObjString* result = allocateString(length);
  result->length = length;
  memcpy(result->_chars, a->chars, a->length);
  memcpy(result->_chars + a->length, b->chars, b->length);
  result = internString(result);

  pop();
  pop();
  push(OBJ_VAL(result));
}

static InterpretResult run() {
  CallFrame* frame = &vm.frames[vm.frameCount - 1];
  register uint8_t* ip = frame->ip;

#define RUNTIME_ERROR(...) \
    (frame->ip = ip, \
    runtimeError(__VA_ARGS__))

#define READ_BYTE() (*ip++)

#define READ_SHORT() \
    (ip += 2, \
    (uint16_t)((ip[-2] << 8) | ip[-1]))

#define READ_CONSTANT() \
    (frame->function->chunk.constants.values[READ_BYTE()])

#define READ_STRING() AS_STRING(READ_CONSTANT())
#define BINARY_OP(valueType, op) \
    do { \
      if (!IS_NUMBER(peek(0)) || !IS_NUMBER(peek(1))) { \
        RUNTIME_ERROR("Operands must be numbers."); \
        return INTERPRET_RUNTIME_ERROR; \
      } \
      double b = AS_NUMBER(pop()); \
      *top() = valueType((AS_NUMBER(peek(0)) op b)); \
    } while (false)

  for (;;) {
#ifdef DEBUG_TRACE_EXECUTION
    printf("          ");
    for (Value* slot = vm.stack; slot < vm.stackTop; slot++) {
      printf("[ ");
      printValue(*slot);
      printf(" ]");
    }
    printf("\n");
    disassembleInstruction(&frame->function->chunk,
                           (int)(ip - frame->function->chunk.code));
#endif

    uint8_t instruction;
    switch (instruction = READ_BYTE()) {
      case OP_CONSTANT: {
        Value constant = READ_CONSTANT();
        push(constant);
        break;
      }
      case OP_CONSTANT_LONG: {
        int a = (int)READ_BYTE();
        int b = (int)READ_BYTE();
        int c = (int)READ_BYTE();
        Value constant = frame->function->chunk.constants.values[c << 16 | b << 8 | a];
        push(constant);
        break;
      }
      case OP_NIL: push(NIL_VAL); break;
      case OP_TRUE: push(BOOL_VAL(true)); break;
      case OP_FALSE: push(BOOL_VAL(false)); break;
      case OP_POP: pop(); break;
      case OP_GET_LOCAL: {
        int a = (int)READ_BYTE();
        int b = (int)READ_BYTE();
        int c = (int)READ_BYTE();
        push(frame->slots[c << 16 | b << 8 | a]);
        break;
      }
      case OP_SET_LOCAL: {
        int a = (int)READ_BYTE();
        int b = (int)READ_BYTE();
        int c = (int)READ_BYTE();
        frame->slots[c << 16 | b << 8 | a] = peek(0);
        break;
      }
      case OP_GET_GLOBAL: {
        int a = (int)READ_BYTE();
        int b = (int)READ_BYTE();
        int c = (int)READ_BYTE();
        Value value = vm.globalValues.values[c << 16 | b << 8 | a];
        if (IS_UNDEFINED(value)) {
          RUNTIME_ERROR("Undefined variable.");
          return INTERPRET_RUNTIME_ERROR;
        }
        push(value);
        break;
      }
      case OP_DEFINE_GLOBAL: {
        int a = (int)READ_BYTE();
        int b = (int)READ_BYTE();
        int c = (int)READ_BYTE();
        vm.globalValues.values[c << 16 | b << 8 | a] = pop();
        break;
      }
      case OP_SET_GLOBAL: {
        int a = (int)READ_BYTE();
        int b = (int)READ_BYTE();
        int c = (int)READ_BYTE();
        int index = c << 16 | b << 8 | a;
        if (IS_UNDEFINED(vm.globalValues.values[index])) {
          RUNTIME_ERROR("Undefined variable.");
          return INTERPRET_RUNTIME_ERROR;
        }
        vm.globalValues.values[index] = peek(0);
        break;
      }
      case OP_GET_UPVALUE: {
        int a = (int)READ_BYTE();
        int b = (int)READ_BYTE();
        int c = (int)READ_BYTE();
        int slot = c << 16 | b << 8 | a;
        push(*frame->closure->upvalues[slot]->location);
        break;
      }
      case OP_SET_UPVALUE: {
        int a = (int)READ_BYTE();
        int b = (int)READ_BYTE();
        int c = (int)READ_BYTE();
        int slot = c << 16 | b << 8 | a;
        *frame->closure->upvalues[slot]->location = peek(0);
        break;
      }
      case OP_GET_PROPERTY: {
        if (!IS_INSTANCE(peek(0))) {
          runtimeError("Only instances have properties.");
          return INTERPRET_RUNTIME_ERROR;
        }

        ObjInstance* instance = AS_INSTANCE(peek(0));
        int a = (int)READ_BYTE();
        int b = (int)READ_BYTE();
        int c = (int)READ_BYTE();
        Value name = frame->function->chunk.constants.values[c << 16 | b << 8 | a];

        Value value;
        if (tableGet(&instance->fields, name, &value)) {
          pop(); // Instance.
          push(value);
          break;
        }

        if (!bindMethod(instance->klass, name)) {
          return INTERPRET_RUNTIME_ERROR;
        }
        break;
      }
      case OP_SET_PROPERTY: {
        if (!IS_INSTANCE(peek(1))) {
          runtimeError("Only instances have fields.");
          return INTERPRET_RUNTIME_ERROR;
        }

        ObjInstance* instance = AS_INSTANCE(peek(1));
        int a = (int)READ_BYTE();
        int b = (int)READ_BYTE();
        int c = (int)READ_BYTE();
        Value field = frame->function->chunk.constants.values[c << 16 | b << 8 | a];
        tableSet(&instance->fields, field, peek(0));
        Value value = pop();
        pop();
        push(value);
        break;
      }
      case OP_EQUAL: {
        Value b = pop();
        Value a = pop();
        push(BOOL_VAL(valuesEqual(a, b)));
        break;
      }
      case OP_GREATER:  BINARY_OP(BOOL_VAL, >); break;
      case OP_LESS:     BINARY_OP(BOOL_VAL, <); break;
      case OP_ADD: {
        if (IS_STRING(peek(0)) && IS_STRING(peek(1))) {
          concatenate();
        } else if (IS_NUMBER(peek(0)) && IS_NUMBER(peek(1))) {
          double b = AS_NUMBER(pop());
          double a = AS_NUMBER(pop());
          push(NUMBER_VAL(a + b));
        } else {
          RUNTIME_ERROR(
              "Operands must be two numbers or two strings.");
          return INTERPRET_RUNTIME_ERROR;
        }
        break;
      }
      case OP_SUBTRACT: BINARY_OP(NUMBER_VAL, -); break;
      case OP_MULTIPLY: BINARY_OP(NUMBER_VAL, *); break;
      case OP_DIVIDE:   BINARY_OP(NUMBER_VAL, /); break;
      case OP_NOT:
        push(BOOL_VAL(isFalsey(pop())));
        break;
      case OP_NEGATE:
        if (!IS_NUMBER(peek(0))) {
          RUNTIME_ERROR("Operand must be a number.");
          return INTERPRET_RUNTIME_ERROR;
        }
        push(NUMBER_VAL(-AS_NUMBER(pop())));
        break;
      case OP_PRINT: {
        printValue(pop());
        printf("\n");
        break;
      }
      case OP_JUMP: {
        uint16_t offset = READ_SHORT();
        ip += offset;
        break;
      }
      case OP_JUMP_IF_FALSE: {
        uint16_t offset = READ_SHORT();
        if (isFalsey(peek(0))) ip += offset;
        break;
      }
      case OP_LOOP: {
        uint16_t offset = READ_SHORT();
        ip -= offset;
        break;
      }
      case OP_CALL: {
        int argCount = READ_BYTE();
        frame->ip = ip;
        if (!callValue(peek(argCount), argCount)) {
          return INTERPRET_RUNTIME_ERROR;
        }
        frame = &vm.frames[vm.frameCount - 1];
        ip = frame->ip;
        break;
      }
      case OP_INVOKE: {
        int a = (int)READ_BYTE();
        int b = (int)READ_BYTE();
        int c = (int)READ_BYTE();
        Value method = frame->function->chunk.constants.values[c << 16 | b << 8 | a];
        int argCount = READ_BYTE();
        frame->ip = ip;
        if (!invoke(method, argCount)) {
          return INTERPRET_RUNTIME_ERROR;
        }
        frame = &vm.frames[vm.frameCount - 1];
        ip = frame->ip;
        break;
      }
      case OP_CLOSURE: {
        int a = (int)READ_BYTE();
        int b = (int)READ_BYTE();
        int c = (int)READ_BYTE();
        ObjFunction* function = AS_FUNCTION(frame->function->chunk.constants.values[c << 16 | b << 8 | a]);
        ObjClosure* closure = newClosure(function);
        push(OBJ_VAL(closure));
        for (int i = 0; i < closure->upvalueCount; i++) {
          uint8_t isLocal = READ_BYTE();
          uint8_t index = READ_BYTE();
          if (isLocal) {
            closure->upvalues[i] =
                captureUpvalue(frame->slots + index);
          } else {
            closure->upvalues[i] = frame->closure->upvalues[index];
          }
        }
        break;
      }
      case OP_CLOSE_UPVALUE:
        closeUpvalues(vm.stackTop - 1);
        pop();
        break;
      case OP_RETURN: {
        Value result = pop();
        closeUpvalues(frame->slots);
        vm.frameCount--;
        if (vm.frameCount == 0) {
          pop();
          return INTERPRET_OK;
        }

        vm.stackTop = frame->slots;
        push(result);
        frame = &vm.frames[vm.frameCount - 1];
        ip = frame->ip;
        break;
      }
      case OP_CLASS: {
        int a = (int)READ_BYTE();
        int b = (int)READ_BYTE();
        int c = (int)READ_BYTE();
        Value name = frame->function->chunk.constants.values[c << 16 | b << 8 | a];
        push(OBJ_VAL(newClass(AS_STRING(name))));
        break;
      }
      case OP_METHOD: {
        int a = (int)READ_BYTE();
        int b = (int)READ_BYTE();
        int c = (int)READ_BYTE();
        Value name = frame->function->chunk.constants.values[c << 16 | b << 8 | a];
        defineMethod(name);
        break;
      }
    }
  }
#undef READ_BYTE
#undef READ_SHORT
#undef READ_CONSTANT
#undef BINARY_OP
#undef RUNTIME_ERROR
}

InterpretResult interpret(const char* source) {
  ObjFunction* function = compile(source);
  if (function == NULL) return INTERPRET_COMPILE_ERROR;

  push(OBJ_VAL(function));
  callFunction(function, 0);

  return run();
}
