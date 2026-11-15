// Windows x64 platform definitions for the host JDK's platform-neutral jni.h.
// jlong must remain 64 bits under Windows' LLP64 data model.
#pragma once
#define JNIEXPORT __declspec(dllexport)
#define JNIIMPORT __declspec(dllimport)
#define JNICALL __stdcall
typedef int jint;
typedef long long jlong;
typedef signed char jbyte;
