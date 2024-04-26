#ifndef clox_debug_h
#define clox_debug_h

#include "chunk.h"
#include "object.h"

void disassembleFunction(ObjFunction* function);
int disassembleInstruction(Chunk* chunk, int offset);

#endif
