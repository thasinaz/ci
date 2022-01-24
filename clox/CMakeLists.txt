cmake_minimum_required(VERSION 3.0.0)
project(clox VERSION 0.1.0)

add_executable(clox main.c chunk.c memory.c debug.c value.c line.c vm.c compiler.c scanner.c)

target_include_directories(clox
    PRIVATE
        ${PROJECT_SOURCE_DIR}/include
)
