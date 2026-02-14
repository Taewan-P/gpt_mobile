cmake_minimum_required(VERSION 3.22.1)
project("mcp_process")

add_library(mcp_process SHARED mcp_process.c)

find_library(log-lib log)
target_link_libraries(mcp_process ${log-lib})
