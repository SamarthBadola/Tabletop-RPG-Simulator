# Generate a build-only header so the pinned upstream copy stays byte-for-byte intact.
set(httplib_upstream "${CMAKE_CURRENT_SOURCE_DIR}/third_party/httplib/httplib.h")
set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS "${httplib_upstream}")
file(READ "${httplib_upstream}" httplib)

function(adapt_httplib before after)
  string(FIND "${httplib}" "${before}" position)
  if(position EQUAL -1)
    message(FATAL_ERROR "The pinned cpp-httplib header no longer matches its local adaptation")
  endif()
  string(REPLACE "${before}" "${after}" httplib "${httplib}")
  set(httplib "${httplib}" PARENT_SCOPE)
endfunction()

# Count request-header bytes on the wire before trimming or parsing.
# Limit each read to the remaining block budget, including the final CRLF.
adapt_httplib(
  "  bool getline();"
  "  bool getline(size_t max_length = CPPHTTPLIB_MAX_LINE_LENGTH);")
adapt_httplib(
  "inline bool stream_line_reader::getline() {"
  "inline bool stream_line_reader::getline(size_t max_length) {")
adapt_httplib(
  "size() + take > CPPHTTPLIB_MAX_LINE_LENGTH"
  "size() + take > max_length")
adapt_httplib(
  "size() >= CPPHTTPLIB_MAX_LINE_LENGTH"
  "size() >= max_length")
adapt_httplib(
  "inline bool read_headers(Stream &strm, Headers &headers) {"
  "inline bool read_headers(Stream &strm, Headers &headers, size_t remaining = std::numeric_limits<size_t>::max()) {")
adapt_httplib([=[
  size_t header_count = 0;

  for (;;) {
    if (!line_reader.getline()) { return false; }
]=] [=[
  size_t header_count = 0;

  for (;;) {
    if (!line_reader.getline((std::min)(remaining, size_t{CPPHTTPLIB_MAX_LINE_LENGTH}))) {
      return false;
    }
    remaining -= line_reader.size();
]=])
adapt_httplib(
  "if (!detail::read_headers(strm, req.headers)) {"
  "if (!detail::read_headers(strm, req.headers, 8192)) {")

set(httplib_generated "${CMAKE_CURRENT_BINARY_DIR}/generated")
file(CONFIGURE OUTPUT "${httplib_generated}/httplib/httplib.h" CONTENT "${httplib}" @ONLY)
target_include_directories(pathfinder_server SYSTEM BEFORE PRIVATE "${httplib_generated}")
