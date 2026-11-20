cmake_minimum_required(VERSION 3.25)
project(rpg_pathfinder LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

add_library(pathfinder_core src/grid.cpp src/search.cpp src/api.cpp)
target_include_directories(pathfinder_core PUBLIC include)
target_include_directories(pathfinder_core SYSTEM PUBLIC third_party)
if(CMAKE_CXX_COMPILER_ID MATCHES "GNU|Clang")
  target_compile_options(pathfinder_core PRIVATE -Wall -Wextra -Wpedantic)
endif()

enable_testing()
add_executable(pathfinder_tests test/test_search.cpp)
target_link_libraries(pathfinder_tests PRIVATE pathfinder_core)
add_test(NAME pathfinder_tests COMMAND pathfinder_tests)

add_executable(pathfinder_api_tests test/test_api.cpp)
target_link_libraries(pathfinder_api_tests PRIVATE pathfinder_core)
add_test(NAME pathfinder_api_tests COMMAND pathfinder_api_tests)
