/*
 * Machine-dependent JNI types for the Kotlin/Native JNI bridge.
 *
 * This header intentionally mirrors the unified NDK ABI: JNICALL is empty on
 * every target, and the primitive types are fixed-width on all platforms. It
 * lets the same cinterop definition be used for desktop JVMs and Android.
 */
#ifndef _JNI_MD_H_
#define _JNI_MD_H_

#ifndef JNIEXPORT
#define JNIEXPORT
#endif

#ifndef JNIIMPORT
#define JNIIMPORT
#endif

#ifndef JNICALL
#define JNICALL
#endif

typedef int jint;
typedef long long jlong;
typedef signed char jbyte;

#endif /* _JNI_MD_H_ */
