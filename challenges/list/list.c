#include "list.h"

#include <stddef.h>
#include <stdlib.h>
#include <string.h>

void list_insert(list *l, lnode *n, char *item) {
  lnode *new_node = (lnode *)malloc(sizeof(lnode));
  new_node->item = item;

  lnode *next = n;
  new_node->next = next;
  if (next == NULL) {
    new_node->prev = l->last;
    if (l->last == NULL) {
      l->first = new_node;
      l->last = new_node;
    } else {
      l->last->next = new_node;
    }
    l->last = new_node;
    return;
  }
  lnode *prev = next->prev;
  new_node->prev = prev;
  next->prev = new_node;

  if (prev == NULL) {
    l->first = new_node;
  } else {
    prev->next = new_node;
  }
}

lnode *list_find(const list *l, const char *item) {
  lnode *n = l->first;
  while (n != NULL) {
    if (strcmp(item, n->item) == 0) {
      break;
    }
    n = n->next;
  }
  return n;
}

void list_delete(list *l, lnode *n) {
  lnode *prev = n->prev;
  lnode *next = n->next;
  free(n);

  if (prev == NULL) {
    l->first = next;
  } else {
    prev->next = next;
  }

  if (next == NULL) {
    l->last = prev;
  } else {
    next->prev = prev;
  }
}
