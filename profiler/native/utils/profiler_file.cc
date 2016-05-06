#include "profiler_file.h"

#include <fcntl.h>
#include <sys/stat.h>
#include <unistd.h>

using std::string;

namespace profiler {
ProfilerFile::ProfilerFile(string path) : path_(path) {}

bool ProfilerFile::Exists() const {
  struct stat buffer;
  return (stat(path_.c_str(), &buffer) == 0);
}

size_t ProfilerFile::GetSize() const {
  int fd = open(path_.c_str(), O_RDONLY);
  struct stat st;
  fstat(fd, &st);
  if (fd) {
    close(fd);
  }
  return st.st_size;
}

bool ProfilerFile::Delete() const { return 0 == remove(path_.c_str()); }

const string ProfilerFile::GetFileName() const {
  return path_.substr(path_.find_last_of("/") + 1);
}
}  // namespace profiler
