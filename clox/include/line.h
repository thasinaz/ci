#ifndef clox_line_h
#define clox_line_h

#include "common.h"

typedef int Line;

typedef struct {
  int capacity;
  int count;
  Line* lines;
  int* line_count;
} LineArray;

void initLineArray(LineArray* array);
void writeLineArray(LineArray* array, Line line);
void freeLineArray(LineArray* array);
Line getLine(LineArray* array, int index);
void printLine(Line line);

#endif
