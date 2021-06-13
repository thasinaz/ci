#ifndef LIST_H_
#define LIST_H_

typedef struct __lnode {
  struct __lnode *prev;
  char *item;
  struct __lnode *next;
} lnode;

typedef struct __list {
  lnode *first;
  lnode *last;
} list;

void list_insert(list *l, lnode *n, char *item);
lnode *list_find(const list *l, const char *item);
void list_delete(list *l, lnode *n);

#endif // LIST_H_
