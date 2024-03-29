#ifndef clox_object_h
#define clox_object_h

#include "common.h"
#include "value.h"

#define OBJ_TYPE(value)        (AS_OBJ(value)->type)

#define IS_STRING(value)       isObjType(value, OBJ_STRING)
#define IS_VECTOR(value)       isObjType(value, OBJ_VECTOR)

#define AS_STRING(value)       ((ObjString*)AS_OBJ(value))
#define AS_CSTRING(value)      (((ObjString*)AS_OBJ(value))->chars)
#define AS_VECTOR(value)       ((ObjVector*)AS_OBJ(value))

typedef enum {
  OBJ_STRING,
  OBJ_VECTOR,
} ObjType;

struct Obj {
  ObjType type;
  struct Obj* next;
};

struct ObjString {
  Obj obj;
  bool ownsChars;
  uint32_t hash;
  int length;
  int size;
  const char* chars;
  char _chars[];
};

ObjString* allocateString(int size);
void freeString(ObjString* string);
ObjString* internString(ObjString* string);
ObjString* takeString(char* chars, int length);
ObjString* stringLiteral(const char* chars, int length);
ObjString* copyString(const char* chars, int length);
void printObject(Value value);

struct ObjVector {
  Obj obj;
  ValueArray valueArray;
};

ObjVector* allocateVector();

static inline bool isObjType(Value value, ObjType type) {
  return IS_OBJ(value) && OBJ_TYPE(value) == type;
}

#endif
