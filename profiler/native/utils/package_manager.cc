#include "package_manager.h"

#include <cstring>
#include <sstream>

using std::string;

namespace {
const string kPackagePrefix = "package:";
const string kPM_EXEC = "/system/bin/pm";
const string kDIR_KEY = "dataDir=";
}

namespace profiler {
PackageManager::PackageManager() : BashCommandRunner(kPM_EXEC) {}

bool PackageManager::GetAppBaseFolder(const string &package_name, string *path,
                                      string *error_string) const {
  string parameters;
  parameters.append("path ");
  parameters.append(package_name);
  bool success = Run(parameters, error_string);
  if (!success) {
    return false;
  }

  // pm returns the path to the apk. We need to parse the response:
  // package:/data/app/net.fabiensanglard.shmup-1/base.apk
  // into
  // /data/app/net.fabiensanglard.shmup-1

  //  Make sure input is well-formed.
  if (path->find(kPackagePrefix) == string::npos) {
    *path = "";
    *error_string =
        "Unable to retrieve app base folder for '" + package_name + ";";
    return false;
  }

  // Remove prefix and prefix.
  *path = path->substr(kPackagePrefix.size(), path->find_last_of("/"));
  return true;
}

bool PackageManager::GetAppDataPath(const string &package_name, string *path,
                                    string *error_string) const {
  string parameters;
  parameters.append("dump ");
  parameters.append(package_name);
  bool success = Run(parameters, error_string);
  if (!success) {
    return false;
  }

  std::string output;
  std::istringstream stream(output);
  string line;
  while (std::getline(stream, line)) {
    if (line.find(kDIR_KEY) != string::npos) {
      *path = line.substr(line.find(kDIR_KEY) + kDIR_KEY.length());
      return true;
    }
  }

  *error_string = "Could not find key '" + kDIR_KEY + "'";
  return false;
}
}  // namespace profiler