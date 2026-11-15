#ifndef __APPLE__
#define _GNU_SOURCE
#endif
#include <jni.h>
#include <string.h>
#ifdef __APPLE__
#include <mach-o/dyld.h>
#else
#include <link.h>
#endif

struct ImageSearch {
  const char* path;
  int count;
};

#ifndef __APPLE__
static int visit_image(struct dl_phdr_info* image, size_t size, void* context) {
  (void)size;
  struct ImageSearch* search = context;
  if (image->dlpi_name && strstr(image->dlpi_name, "librocksdbjni")) {
    search->path = image->dlpi_name;
    search->count++;
  }
  return 0;
}
#endif

/* Reuse the exact active engine, including when Java extracted it to a random name. */
JNIEXPORT jbyteArray JNICALL
Java_maryk_datastore_rocksdb_RocksDBNativeComparatorLibrary_loadedRocksDbImage(
    JNIEnv* env, jobject owner) {
  (void)owner;
  struct ImageSearch search = {NULL, 0};
#ifdef __APPLE__
  uint32_t count = _dyld_image_count();
  for (uint32_t i = 0; i < count; i++) {
    const char* path = _dyld_get_image_name(i);
    if (path && strstr(path, "librocksdbjni")) {
      search.path = path;
      search.count++;
    }
  }
#else
  dl_iterate_phdr(visit_image, &search);
#endif
  /* Multiple class loaders can own different engines. Never guess between them. */
  if (search.count != 1) return NULL;
  size_t length = strlen(search.path);
  jbyteArray result = (*env)->NewByteArray(env, (jsize)length);
  if (result) {
    (*env)->SetByteArrayRegion(env, result, 0, (jsize)length,
                              (const jbyte*)search.path);
  }
  return result;
}
