test: list.o test.o
	gcc -o test list.o test.o

list.o: list.h list.c
	gcc -c list.c

test.o: list.h test.c
	gcc -c test.c

.PHONY: clean
clean:
	rm *.o test
