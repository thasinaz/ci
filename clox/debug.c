#include <stdio.h>

#include "debug.h"
#include "line.h"
#include "object.h"
#include "value.h"
#include "vm.h"

void disassembleFunction(ObjFunction* function) {
  function->name != NULL
      ? printf("== %.*s ==\n", function->name->length, function->name->chars)
      : printf("== %s ==\n",  "<script>");

  Chunk* chunk = &function->chunk;
  for (int offset = 0; offset < chunk->count;) {
    offset = disassembleInstruction(chunk, offset);
  }
}

static int identifierInstruction(const char* name, Chunk* chunk,
                                 int offset) {
  uint8_t constant[3] = {chunk->code[offset + 1], chunk->code[offset + 2], chunk->code[offset + 3]};
  printf("%-16s %4d %4d %4d '", name, constant[0], constant[1], constant[2]);
  int index = (int)constant[2] << 16 | (int)constant[1] << 8 | (int)constant[0];
  printValue(vm.globalIdentifiers.values[index]);
  printf("'\n");
  return offset + 4;
}

static int constantInstruction(const char* name, Chunk* chunk,
                               int offset) {
  uint8_t constant = chunk->code[offset + 1];
  printf("%-16s %4d '", name, constant);
  printValue(chunk->constants.values[constant]);
  printf("'\n");
  return offset + 2;
}

static int constantLongInstruction(const char* name, Chunk* chunk,
                                   int offset) {
  uint8_t constant[3] = {chunk->code[offset + 1], chunk->code[offset + 2], chunk->code[offset + 3]};
  printf("%-16s %4d %4d %4d '", name, constant[0], constant[1], constant[2]);
  int index = (int)constant[2] << 16 | (int)constant[1] << 8 | (int)constant[0];
  printValue(chunk->constants.values[index]);
  printf("'\n");
  return offset + 4;
}

static int simpleInstruction(const char* name, int offset) {
  printf("%s\n", name);
  return offset + 1;
}

static int byteInstruction(const char* name, Chunk* chunk,
                           int offset) {
  uint8_t slot = chunk->code[offset + 1];
  printf("%-16s %4d\n", name, slot);
  return offset + 2;
}

static int longInstruction(const char* name, Chunk* chunk,
                           int offset) {
  uint8_t slot[3] = {chunk->code[offset + 1], chunk->code[offset + 2], chunk->code[offset + 3]};
  printf("%-16s %4d %4d %4d\n", name, slot[0], slot[1], slot[2]);
  return offset + 4;
}

static int jumpInstruction(const char* name, int sign,
                           Chunk* chunk, int offset) {
  uint16_t jump = (uint16_t)(chunk->code[offset + 1] << 8);
  jump |= chunk->code[offset + 2];
  printf("%-16s %4d -> %d\n", name, offset,
         offset + 3 + sign * jump);
  return offset + 3;
}

int disassembleInstruction(Chunk* chunk, int offset) {
  printf("%04d ", offset);
  if (offset > 0 &&
      getLine(&chunk->lines, offset) == getLine(&chunk->lines, offset - 1)) {
    printf("   | ");
  } else {
    printLine(getLine(&chunk->lines, offset));
    printf(" ");
  }

  uint8_t instruction = chunk->code[offset];
  switch (instruction) {
    case OP_CONSTANT:
      return constantInstruction("OP_CONSTANT", chunk, offset);
    case OP_CONSTANT_LONG:
      return constantLongInstruction("OP_CONSTANT_LONG", chunk, offset);
    case OP_NIL:
      return simpleInstruction("OP_NIL", offset);
    case OP_TRUE:
      return simpleInstruction("OP_TRUE", offset);
    case OP_FALSE:
      return simpleInstruction("OP_FALSE", offset);
    case OP_POP:
      return simpleInstruction("OP_POP", offset);
    case OP_GET_LOCAL:
      return longInstruction("OP_GET_LOCAL", chunk, offset);
    case OP_SET_LOCAL:
      return longInstruction("OP_SET_LOCAL", chunk, offset);
    case OP_GET_GLOBAL:
      return identifierInstruction("OP_GET_GLOBAL", chunk, offset);
    case OP_DEFINE_GLOBAL:
      return identifierInstruction("OP_DEFINE_GLOBAL", chunk,
                                 offset);
    case OP_SET_GLOBAL:
      return identifierInstruction("OP_SET_GLOBAL", chunk, offset);
    case OP_GET_UPVALUE:
      return longInstruction("OP_GET_UPVALUE", chunk, offset);
    case OP_SET_UPVALUE:
      return longInstruction("OP_SET_UPVALUE", chunk, offset);
    case OP_EQUAL:
      return simpleInstruction("OP_EQUAL", offset);
    case OP_GREATER:
      return simpleInstruction("OP_GREATER", offset);
    case OP_LESS:
      return simpleInstruction("OP_LESS", offset);
    case OP_ADD:
      return simpleInstruction("OP_ADD", offset);
    case OP_SUBTRACT:
      return simpleInstruction("OP_SUBTRACT", offset);
    case OP_MULTIPLY:
      return simpleInstruction("OP_MULTIPLY", offset);
    case OP_DIVIDE:
      return simpleInstruction("OP_DIVIDE", offset);
    case OP_NOT:
      return simpleInstruction("OP_NOT", offset);
    case OP_NEGATE:
      return simpleInstruction("OP_NEGATE", offset);
    case OP_PRINT:
      return simpleInstruction("OP_PRINT", offset);
    case OP_JUMP:
      return jumpInstruction("OP_JUMP", 1, chunk, offset);
    case OP_JUMP_IF_FALSE:
      return jumpInstruction("OP_JUMP_IF_FALSE", 1, chunk, offset);
    case OP_LOOP:
      return jumpInstruction("OP_LOOP", -1, chunk, offset);
    case OP_CALL:
      return byteInstruction("OP_CALL", chunk, offset);
    case OP_CLOSURE: {
      uint8_t constant[3] = {chunk->code[offset + 1], chunk->code[offset + 2], chunk->code[offset + 3]};
      offset += 4;
      printf("%-16s %4d %4d %4d '", "OP_CLOSURE", constant[0], constant[1], constant[2]);
      int index = (int)constant[2] << 16 | (int)constant[1] << 8 | (int)constant[0];
      printValue(chunk->constants.values[index]);
      printf("\n");

      ObjFunction* function = AS_FUNCTION(
          chunk->constants.values[index]);
      for (int j = 0; j < function->upvalueCount; j++) {
        int isLocal = chunk->code[offset++];
        int index = chunk->code[offset++];
        printf("%04d      |                     %s %d\n",
               offset - 2, isLocal ? "local" : "upvalue", index);
      }
      return offset;
    }
    case OP_CLOSE_UPVALUE:
      return simpleInstruction("OP_CLOSE_UPVALUE", offset);
    case OP_RETURN:
      return simpleInstruction("OP_RETURN", offset);
    default:
      printf("Unknown opcode %d\n", instruction);
      return offset + 1;
  }
}
