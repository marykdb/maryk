#include <jni.h>
#include <algorithm>
#include <new>
#include "rocksdb/comparator.h"
#include "rocksdb/slice.h"
#ifdef _WIN32
#include "windows_comparator.h"
#endif

namespace {
#ifdef _WIN32
class VersionedComparator final : public WindowsComparator {
#else
class VersionedComparator final : public rocksdb::Comparator {
#endif
 public:
  // Set once before handing the comparator to RocksDB; immutable while in use.
  size_t key_size = 0;

  const char* Name() const override { return "maryk.VersionedComparator"; }

  int Compare(const rocksdb::Slice& a, const rocksdb::Slice& b) const override {
    if (a.size() > key_size && b.size() > key_size) {
      const size_t ap = a.size() - 8, bp = b.size() - 8;
      const int prefix = ComparePart(a, 0, ap, b, 0, bp);
      return prefix == 0 ? ComparePart(a, ap, 8, b, bp, 8) : prefix;
    }
    return ComparePart(a, 0, a.size(), b, 0, b.size());
  }

  void FindShortestSeparator(std::string*, const rocksdb::Slice&) const override {}
  void FindShortSuccessor(std::string*) const override {}

 private:
  static int ComparePart(const rocksdb::Slice& a, size_t ao, size_t an,
                         const rocksdb::Slice& b, size_t bo, size_t bn) {
    const size_t count = std::min(an, bn);
    for (size_t i = 0; i < count; i++) {
      const int difference = static_cast<unsigned char>(a[ao + i]) -
                             static_cast<unsigned char>(b[bo + i]);
      if (difference != 0) return difference;
    }
    return an < bn ? -1 : an > bn ? 1 : 0;
  }
};

void Throw(JNIEnv* env, const char* type, const char* message) {
  jclass exception = env->FindClass(type);
  if (exception) env->ThrowNew(exception, message);
}
}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_maryk_datastore_rocksdb_NativeVersionedComparator_createNative(JNIEnv* env,
                                                                jobject) {
  try {
    return reinterpret_cast<jlong>(new VersionedComparator());
  } catch (const std::bad_alloc&) {
    Throw(env, "java/lang/OutOfMemoryError", "Cannot allocate RocksDB comparator");
  } catch (...) {
    Throw(env, "java/lang/IllegalStateException", "Cannot create RocksDB comparator");
  }
  return 0;
}

extern "C" JNIEXPORT void JNICALL
Java_maryk_datastore_rocksdb_NativeVersionedComparator_setKeySize(
    JNIEnv* env, jobject, jlong handle, jint key_size) {
  if (handle == 0 || key_size < 8) {
    Throw(env, "java/lang/IllegalArgumentException", "Invalid comparator key size");
    return;
  }
  reinterpret_cast<VersionedComparator*>(handle)->key_size =
      static_cast<size_t>(key_size);
}

// Exercises the same implementation directly in JVM ordering parity tests.
extern "C" JNIEXPORT jint JNICALL
Java_maryk_datastore_rocksdb_NativeVersionedComparator_compareNative(
    JNIEnv* env, jobject, jlong handle, jbyteArray a, jbyteArray b) {
  const jsize an = env->GetArrayLength(a), bn = env->GetArrayLength(b);
  jbyte* ap = an == 0 ? nullptr : env->GetByteArrayElements(a, nullptr);
  if (an != 0 && !ap) return 0;
  jbyte* bp = bn == 0 ? nullptr : env->GetByteArrayElements(b, nullptr);
  if (bn != 0 && !bp) {
    if (ap) env->ReleaseByteArrayElements(a, ap, JNI_ABORT);
    return 0;
  }
  const int result = reinterpret_cast<VersionedComparator*>(handle)->Compare(
      rocksdb::Slice(an == 0 ? "" : reinterpret_cast<char*>(ap), an),
      rocksdb::Slice(bn == 0 ? "" : reinterpret_cast<char*>(bp), bn));
  if (ap) env->ReleaseByteArrayElements(a, ap, JNI_ABORT);
  if (bp) env->ReleaseByteArrayElements(b, bp, JNI_ABORT);
  return result;
}
