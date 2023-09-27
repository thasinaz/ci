cmake_minimum_required(VERSION 3.0.0)
project(clox VERSION 0.1.0)

add_executable(clox
    chunk.c
    compiler.c
    debug.c
    line.c
    main.c
    memory.c
    object.c
    scanner.c
    value.c
    vm.c
)

target_include_directories(clox
    PRIVATE
        ${PROJECT_SOURCE_DIR}/include
)
