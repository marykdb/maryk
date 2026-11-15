// The Windows JNI image exports JNI entry points, but no RocksDB C++ symbols.
// Supply the configuration behavior of this option-free comparator locally,
// while inheriting the exact pinned RocksDB class declarations and layout.
#pragma once

#include "rocksdb/comparator.h"
#include "rocksdb/convenience.h"

class WindowsComparator : public rocksdb::Comparator {
 public:
  rocksdb::Status GetOption(const rocksdb::ConfigOptions&, const std::string& name,
                            std::string* value) const override {
    if (name != "id") return rocksdb::Status::NotFound();
    *value = GetId();
    return rocksdb::Status::OK();
  }

  bool AreEquivalent(const rocksdb::ConfigOptions& options,
                     const rocksdb::Configurable* other,
                     std::string* mismatch) const override {
    if (options.sanity_level == rocksdb::ConfigOptions::kSanityLevelNone || this == other) return true;
    const auto* custom = static_cast<const rocksdb::Customizable*>(other);
    if (custom != nullptr && GetId() == custom->GetId()) return true;
    if (mismatch != nullptr) *mismatch = "id";
    return false;
  }

  rocksdb::Status PrepareOptions(const rocksdb::ConfigOptions&) override {
    return rocksdb::Status::OK();
  }
  rocksdb::Status ValidateOptions(const rocksdb::DBOptions&,
                                const rocksdb::ColumnFamilyOptions&) const override {
    return rocksdb::Status::OK();
  }

 protected:
  const void* GetOptionsPtr(const std::string&) const override { return nullptr; }
  rocksdb::Status ParseStringOptions(const rocksdb::ConfigOptions&, const std::string&) override {
    return rocksdb::Status::OK();
  }
  rocksdb::Status ConfigureOptions(const rocksdb::ConfigOptions& options,
      const std::unordered_map<std::string, std::string>& values,
      std::unordered_map<std::string, std::string>* unused) override {
    if (unused != nullptr) unused->insert(values.begin(), values.end());
    if (!values.empty() && unused == nullptr && !options.ignore_unknown_options) {
      return rocksdb::Status::NotFound();
    }
    return rocksdb::Status::OK();
  }
  // No options are registered, so the per-option hooks must never accept one.
  rocksdb::Status ParseOption(const rocksdb::ConfigOptions&, const rocksdb::OptionTypeInfo&,
      const std::string&, const std::string&, void*) override {
    return rocksdb::Status::NotSupported();
  }
  bool OptionsAreEqual(const rocksdb::ConfigOptions&, const rocksdb::OptionTypeInfo&,
      const std::string&, const void* a, const void* b, std::string*) const override {
    return a == b;
  }
  std::string SerializeOptions(const rocksdb::ConfigOptions&, const std::string&) const override {
    return GetId();
  }
  std::string GetOptionName(const std::string& name) const override {
    const std::string prefix = std::string(Name()) + ".";
    return name.compare(0, prefix.size(), prefix) == 0 ? name.substr(prefix.size()) : name;
  }
};
