#include <stdio.h>
#include <string.h>

#include "memory.h"
#include "object.h"
#include "table.h"
#include "value.h"
#include "vm.h"

#define ALLOCATE_OBJ(type, objectType) \
    (type*)allocateObject(sizeof(type), objectType)

static Obj* allocateObject(size_t size, ObjType type) {
  Obj* object = (Obj*)reallocate(NULL, 0, size);
  object->type = type;
  object->mark = !vm.markValue;

  object->next = vm.objects;

#ifdef DEBUG_LOG_GC
  printf("%p allocate %zu for %d\n", (void*)object, size, type);
#endif

  vm.objects = object;
  return object;
}

ObjClosure* newClosure(ObjFunction* function) {
  ObjUpvalue** upvalues = ALLOCATE(ObjUpvalue*,
                                   function->upvalueCount);
  for (int i = 0; i < function->upvalueCount; i++) {
    upvalues[i] = NULL;
  }

  ObjClosure* closure = ALLOCATE_OBJ(ObjClosure, OBJ_CLOSURE);
  closure->function = function;
  closure->upvalues = upvalues;
  closure->upvalueCount = function->upvalueCount;
  return closure;
}

ObjFunction* newFunction() {
  ObjFunction* function = ALLOCATE_OBJ(ObjFunction, OBJ_FUNCTION);
  function->arity = 0;
  function->upvalueCount = 0;
  function->name = NULL;
  initChunk(&function->chunk);
  return function;
}

ObjNative* newNative(NativeFn function, int arity) {
  ObjNative* native = ALLOCATE_OBJ(ObjNative, OBJ_NATIVE);
  native->arity = arity;
  native->function = function;
  return native;
}

static uint32_t hashString(const char* key, int length) {
  uint32_t hash = 2166136261u;
  for (int i = 0; i < length; i++) {
    hash ^= (uint8_t)key[i];
    hash *= 16777619;
  }
  return hash;
}

ObjString* allocateString(int size) {
  ObjString* string =
      (ObjString*)allocateObject(sizeof(ObjString) + size * sizeof(char),
                                 OBJ_STRING);
  string->ownsChars = false;
  string->length = 0;
  string->size = size;
  string->chars = string->_chars;
  return string;
}

void freeString(ObjString* string) {
  if (string->ownsChars) {
    FREE_ARRAY(char, (char*)string->chars, string->length);
  }
  reallocate(string, sizeof(ObjString) + string->size * sizeof(char), 0);
}

ObjString* internString(ObjString* string) {
  const char* chars = string->chars;
  int length = string->length;
  uint32_t hash = hashString(chars, length);
  ObjString* interned = tableFindString(&vm.strings, chars, length,
                                        hash);
  if (interned != NULL) return interned;

  string->hash = hash;
  push(OBJ_VAL(string));
  tableSet(&vm.strings, OBJ_VAL(string), NIL_VAL);
  pop();
  return string;
}

ObjString* takeString(char* chars, int length) {
  uint32_t hash = hashString(chars, length);
  ObjString* interned = tableFindString(&vm.strings, chars, length,
                                        hash);
  if (interned != NULL) {
    FREE_ARRAY(char, chars, length);
    return interned;
  }

  ObjString* string = allocateString(0);
  string->ownsChars = true;
  string->hash = hash;
  string->length = length;
  string->chars = chars;
  push(OBJ_VAL(string));
  tableSet(&vm.strings, OBJ_VAL(string), NIL_VAL);
  pop();
  return string;
}

ObjString* stringLiteral(const char* chars, int length) {
  uint32_t hash = hashString(chars, length);
  ObjString* interned = tableFindString(&vm.strings, chars, length,
                                        hash);
  if (interned != NULL) return interned;

  ObjString* string = allocateString(0);
  string->hash = hash;
  string->length = length;
  string->chars = chars;
  push(OBJ_VAL(string));
  tableSet(&vm.strings, OBJ_VAL(string), NIL_VAL);
  pop();
  return string;
}

ObjVector* allocateVector() {
  ObjVector* vector = (ObjVector*)allocateObject(sizeof(ObjVector), OBJ_VECTOR);
  initValueArray(&vector->valueArray);
  return vector;
}

ObjUpvalue* newUpvalue(Value* slot) {
  ObjUpvalue* upvalue = ALLOCATE_OBJ(ObjUpvalue, OBJ_UPVALUE);
  upvalue->closed = NIL_VAL;
  upvalue->location = slot;
  upvalue->next = NULL;
  return upvalue;
}

static void printFunction(ObjFunction* function) {
  if (function->name == NULL) {
    printf("<script>");
    return;
  }
  printf("<fn %.*s>", function->name->length, function->name->chars);
}

void printObject(Value value) {
  switch (OBJ_TYPE(value)) {
    case OBJ_CLOSURE:
      printFunction(AS_CLOSURE(value)->function);
      break;
    case OBJ_FUNCTION:
      printFunction(AS_FUNCTION(value));
      break;
    case OBJ_NATIVE:
      printf("<native fn>");
      break;
    case OBJ_STRING:
      printf("%.*s", AS_STRING(value)->length, AS_CSTRING(value));
      break;
    case OBJ_VECTOR:
      printf("[");
      if (AS_VECTOR(value)->valueArray.count > 0) {
        printValue(AS_VECTOR(value)->valueArray.values[0]);
      }
      for (int i = 1; i < AS_VECTOR(value)->valueArray.count; i++) {
        printf(", ");
        printValue(AS_VECTOR(value)->valueArray.values[i]);
      }
      printf("]");
      break;
    case OBJ_UPVALUE:
      printf("upvalue");
      break;
  }
}
