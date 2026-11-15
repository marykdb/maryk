#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <tlhelp32.h>
#include <jni.h>

/* Identify engines by an exported JNI entry point, including randomly named
 * Java extractions. Return UTF-16 so paths outside the ANSI code page work. */
JNIEXPORT jcharArray JNICALL
Java_maryk_datastore_rocksdb_RocksDBNativeComparatorLibrary_loadedRocksDbImageWindows(
    JNIEnv* env, jobject owner) {
  (void)owner;
  HANDLE snapshot = CreateToolhelp32Snapshot(TH32CS_SNAPMODULE, GetCurrentProcessId());
  if (snapshot == INVALID_HANDLE_VALUE) return NULL;
  MODULEENTRY32W entry;
  entry.dwSize = sizeof(entry);
  WCHAR path[32768];
  DWORD length = 0;
  int count = 0;
  if (Module32FirstW(snapshot, &entry)) {
    do {
      if (GetProcAddress(entry.hModule, "Java_org_rocksdb_RocksDB_open__JLjava_lang_String_2")) {
        count++;
        length = GetModuleFileNameW(entry.hModule, path, sizeof(path) / sizeof(path[0]));
      }
    } while (Module32NextW(snapshot, &entry));
    if (GetLastError() != ERROR_NO_MORE_FILES) count = 0;
  }
  CloseHandle(snapshot);
  if (count != 1 || length == 0 || length >= sizeof(path) / sizeof(path[0])) return NULL;
  jcharArray result = (*env)->NewCharArray(env, (jsize)length);
  if (result) (*env)->SetCharArrayRegion(env, result, 0, (jsize)length, (const jchar*)path);
  return result;
}
