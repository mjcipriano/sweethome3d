cmake_minimum_required(VERSION 3.16)
project(sweethome3d_model_lod LANGUAGES CXX)

find_package(JNI REQUIRED)

add_library(sweethome3d_model_lod SHARED
  ModelLODNative.cpp
  meshoptimizer/allocator.cpp
  meshoptimizer/indexgenerator.cpp
  meshoptimizer/simplifier.cpp
  meshoptimizer/vfetchoptimizer.cpp)

target_include_directories(sweethome3d_model_lod PRIVATE
  ${JNI_INCLUDE_DIRS}
  ${CMAKE_CURRENT_SOURCE_DIR}/meshoptimizer)
target_compile_features(sweethome3d_model_lod PRIVATE cxx_std_11)
set_target_properties(sweethome3d_model_lod PROPERTIES
  CXX_VISIBILITY_PRESET hidden
  VISIBILITY_INLINES_HIDDEN YES)
