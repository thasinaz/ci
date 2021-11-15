#include <stdio.h>

#include "memory.h"
#include "line.h"

void initLineArray(LineArray* array) {
  array->lines = NULL;
  array->line_count = NULL;
  array->capacity = 0;
  array->count = 0;
}

void writeLineArray(LineArray* array, Line line) {
  if (array->capacity < array->count + 1) {
    int oldCapacity = array->capacity;
    array->capacity = GROW_CAPACITY(oldCapacity);
    array->lines = GROW_ARRAY(Line, array->lines,
                              oldCapacity, array->capacity);
    array->line_count = GROW_ARRAY(int, array->line_count,
                                   oldCapacity, array->capacity);
  }

  if (array->count > 0 && line == array->lines[array->count - 1]) {
    array->line_count[array->count - 1]++;
    return;
  }

  array->lines[array->count] = line;
  array->line_count[array->count] = 1;
  array->count++;
}

void freeLineArray(LineArray* array) {
  FREE_ARRAY(Line, array->lines, array->capacity);
  FREE_ARRAY(int, array->line_count, array->capacity);
  initLineArray(array);
}

Line getLine(LineArray* array, int index) {
  int i = 0;
  for (int sum = 0; sum < index;
       sum += array->line_count[i]) {
    i++;
  }

  return array->lines[i];
}

void printLine(Line line) {
  printf("%4d", line);
}
