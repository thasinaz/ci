#include <stdio.h>
#include <string.h>

#include "memory.h"
#include "object.h"
#include "value.h"
#include "vm.h"

#define ALLOCATE_OBJ(type, objectType) \
    (type*)allocateObject(sizeof(type), objectType)

static Obj* allocateObject(size_t size, ObjType type) {
  Obj* object = (Obj*)reallocate(NULL, 0, size);
  object->type = type;

  object->next = vm.objects;
  vm.objects = object;
  return object;
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

ObjString* takeString(char* chars, int length) {
  ObjString* string = allocateString(0);
  string->ownsChars = true;
  string->length = length;
  string->chars = chars;
  return string;
}

ObjString* stringLiteral(const char* chars, int length) {
  ObjString* string = allocateString(0);
  string->length = length;
  string->chars = chars;
  return string;
}

ObjString* copyString(const char* chars, int length) {
  ObjString* string = allocateString(length);
  string->length = length;
  memcpy(string->_chars, chars, length);
  return string;
}

void printObject(Value value) {
  switch (OBJ_TYPE(value)) {
    case OBJ_STRING:
      printf("%.*s", AS_STRING(value)->length, AS_CSTRING(value));
      break;
  }
}
