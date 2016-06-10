#include "installer.h"
#include <sstream>

#include "android_studio_version.h"
#include "bash_command.h"
#include "log.h"
#include "package_manager.h"
#include "profiler_file.h"

using std::string;

namespace profiler {
Installer::Installer(const char *app_package_name)
    : app_package_name_(app_package_name) {}

bool Installer::Install(const string &src_path, string *error_string) const {
  LOG("Request to install sampler in app '%s\n'", app_package_name_.c_str());

  // Check if the sampler is already there.
  string dst_path;
  if (!GetInstallationPath(app_package_name_, &dst_path, error_string)) {
    error_string->append("Unable to generate installation path");
    return false;
  }

  ProfilerFile dst = ProfilerFile(dst_path);
  if (dst.Exists()) {
    LOG("'%s' executable is already installed (found at '%s').\n",
        app_package_name_.c_str(), dst_path.c_str());
    return true;
  }

  LOG("'%s' executable requires installation (missing from '%s').\n",
      app_package_name_.c_str(), dst_path.c_str());
  // We need to copy sampler to the app folder.

  ProfilerFile src(src_path);
  if (!src.Exists()) {
    *error_string = "Source does not exists (" + src_path + ").";
    return false;
  }

  if (!BashCommandRunner::IsRunAsCapable()) {
    *error_string = "System is not run-as capable";
    return false;
  }

  LOG("Copying...\n");
  // sh -c \"cat /data/local/tmp/foo.so | run-as com.google.android.calendar sh
  // -c 'cat > foo.so ; chmod 700 foo.so'
  // TODO: Implement this in a clean way. With fork, execv and pipes?

  std::stringstream copy_command;
  copy_command << "sh -c \"";
  copy_command << "cat ";
  copy_command << src_path;
  copy_command << " | ";
  copy_command << "run-as ";
  copy_command << app_package_name_;
  copy_command << " sh -c 'cat > ";
  copy_command << dst_path;
  copy_command << "; chmod 700 ";
  copy_command << dst_path;
  copy_command << "'\"";
  BashCommandRunner cmd(copy_command.str());

  string out;
  bool success = cmd.Run("", &out);
  if (!success || !dst.Exists()) {
    *error_string = out;
    return false;
  }

  return true;
}

bool Installer::Uninstall(const string &binary_path,
                          string *error_string) const {
  ProfilerFile target(binary_path);
  if (!target.Exists()) {
    *error_string = "Cannot delete file '" + binary_path +
                    "': ProfilerFile does not exists.";
    return false;
  }
  BashCommandRunner rm("rm");
  string parameters;
  parameters.append(target.GetPath());
  bool success = rm.Run(parameters, error_string);
  if (!success || target.Exists()) {
    return false;
  }
  return true;
}

bool Installer::GetInstallationPath(const string &executable_path,
                                    string *install_path,
                                    string *error_string) const {
  string error_message;

  // Build the installation destination install_path:
  PackageManager pm;
  string app_base;
  bool ret = pm.GetAppDataPath(app_package_name_, &app_base, &error_message);
  if (!ret) {
    *error_string = error_message;
    return false;
  }

  ProfilerFile b(executable_path);
  string binary_filename = b.GetFileName();

  install_path->clear();
  install_path->append(app_base);
  install_path->append("/");
  install_path->append(GetBinaryNameForPackage(binary_filename));
  return true;
}

const string Installer::GetBinaryNameForPackage(
    const string &executable_filename) const {
  string binary_name;
  binary_name.append(executable_filename);
  binary_name.append("_for");
  binary_name.append("-");
  binary_name.append(this->app_package_name_);
  binary_name.append("-");
  binary_name.append("aarch64");  // TODO: Use "uname -m" instead or config
  // file.
  binary_name.append("-v");
  binary_name.append(kAndroidStudioVersion);
  return binary_name;
}
}  // namespace profiler
