cmake_minimum_required(VERSION 3.22.1)

project("callmedia" LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}/bridge
    ${CMAKE_CURRENT_SOURCE_DIR}/engine
)

add_library(
    callmedia
    SHARED
    bridge/call_media_jni.cpp
    engine/call_session.cpp
)

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(
    callmedia
    ${log-lib}
    ${android-lib}
)
