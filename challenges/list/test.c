#include <assert.h>
#include <stdio.h>

#include "list.h"

void list_print(const list *l) {
  lnode *n = l->first;
  while (n != NULL) {
    printf("%s ", n->item);
    n = n->next;
  }
  printf("\n");
}

int main() {
  list l = {NULL, NULL};

  list_insert(&l, NULL, "b");
  list_print(&l);
  list_insert(&l, NULL, "c");
  list_print(&l);
  assert(list_find(&l, "b") != NULL);
  assert(list_find(&l, "c") != NULL);
  list_insert(&l, list_find(&l, "c"), "bb");
  assert(list_find(&l, "bb") != NULL);
  list_insert(&l, list_find(&l, "b"), "a");
  assert(list_find(&l, "a") != NULL);
  list_print(&l);
  list_delete(&l, list_find(&l, "bb"));
  assert(list_find(&l, "bb") == NULL);
  list_delete(&l, list_find(&l, "a"));
  assert(list_find(&l, "a") == NULL);
  list_delete(&l, list_find(&l, "c"));
  assert(list_find(&l, "c") == NULL);
  list_print(&l);
}
