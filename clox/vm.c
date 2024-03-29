#include <stdarg.h>
#include <stdio.h>
#include <string.h>

#include "common.h"
#include "compiler.h"
#include "debug.h"
#include "memory.h"
#include "object.h"
#include "vm.h"

VM vm;

static void resetStack() {
  vm.stackTop = vm.stack;
}

static void runtimeError(const char* format, ...) {
  va_list args;
  va_start(args, format);
  vfprintf(stderr, format, args);
  va_end(args);
  fputs("\n", stderr);

  size_t instruction = vm.ip - vm.chunk->code - 1;
  int line = getLine(&vm.chunk->lines, instruction);
  fprintf(stderr, "[line %d] in script\n", line);
  resetStack();
}

void initVM() {
  resetStack();
  vm.objects = NULL;
  initTable(&vm.globalNames);
  initValueArray(&vm.globalValues);
  initValueArray(&vm.globalIdentifiers);
  initTable(&vm.strings);
}

void freeVM() {
  freeTable(&vm.globalNames);
  freeValueArray(&vm.globalValues);
  freeValueArray(&vm.globalIdentifiers);
  freeTable(&vm.strings);
  freeObjects();
}

Value* top() {
  return vm.stackTop;
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

static bool isFalsey(Value value) {
  return IS_NIL(value) || (IS_BOOL(value) && !AS_BOOL(value));
}

static void concatenate() {
  ObjString* b = AS_STRING(pop());
  ObjString* a = AS_STRING(pop());

  int length = a->length + b->length;
  ObjString* result = allocateString(length);
  result->length = length;
  memcpy(result->_chars, a->chars, a->length);
  memcpy(result->_chars + a->length, b->chars, b->length);
  result = internString(result);

  push(OBJ_VAL(result));
}

static InterpretResult run() {
#define READ_BYTE() (*vm.ip++)
#define READ_CONSTANT() (vm.chunk->constants.values[READ_BYTE()])
#define READ_STRING() AS_STRING(READ_CONSTANT())
#define BINARY_OP(valueType, op) \
    do { \
      if (!IS_NUMBER(peek(0)) || !IS_NUMBER(peek(1))) { \
        runtimeError("Operands must be numbers."); \
        return INTERPRET_RUNTIME_ERROR; \
      } \
      double b = AS_NUMBER(pop()); \
      *top() = valueType((AS_NUMBER(*top()) op b)); \
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
    disassembleInstruction(vm.chunk,
                          (int)(vm.ip - vm.chunk->code));
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
        Value constant = vm.chunk->constants.values[c << 16 | b << 8 | a];
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
        push(vm.stack[c << 16 | b << 8 | a]);
        break;
      }
      case OP_SET_LOCAL: {
        int a = (int)READ_BYTE();
        int b = (int)READ_BYTE();
        int c = (int)READ_BYTE();
        vm.stack[c << 16 | b << 8 | a] = peek(0);
        break;
      }
      case OP_GET_GLOBAL: {
        int a = (int)READ_BYTE();
        int b = (int)READ_BYTE();
        int c = (int)READ_BYTE();
        Value value = vm.globalValues.values[c << 16 | b << 8 | a];
        if (IS_UNDEFINED(value)) {
          runtimeError("Undefined variable.");
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
          runtimeError("Undefined variable.");
          return INTERPRET_RUNTIME_ERROR;
        }
        vm.globalValues.values[index] = peek(0);
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
          runtimeError(
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
          runtimeError("Operand must be a number.");
          return INTERPRET_RUNTIME_ERROR;
        }
        push(NUMBER_VAL(-AS_NUMBER(pop())));
        break;
      case OP_PRINT: {
        printValue(pop());
        printf("\n");
        break;
      }
      case OP_RETURN: {
        // Exit interpreter.
        return INTERPRET_OK;
      }
    }
  }
#undef READ_BYTE
#undef READ_CONSTANT
#undef BINARY_OP
}

InterpretResult interpret(const char* source) {
  Chunk chunk;
  initChunk(&chunk);

  if (!compile(source, &chunk)) {
    freeChunk(&chunk);
    return INTERPRET_COMPILE_ERROR;
  }

  vm.chunk = &chunk;
  vm.ip = vm.chunk->code;

  InterpretResult result = run();

  freeChunk(&chunk);
  return result;
}
