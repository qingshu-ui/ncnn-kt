/*
 * Portable entry point for the ncnn C API cinterop.
 *
 * The prebuilt Android packages ship a platform.h that includes <android/log.h>
 * instead of <stdio.h>, but c_api.h's stdio DataReader entry point uses FILE.
 * Including <stdio.h> first makes the header self-contained on every platform;
 * it is harmless where platform.h already includes it.
 */
#ifndef NCNN_C_API_PORTABLE_H
#define NCNN_C_API_PORTABLE_H

#include <stdio.h>

#include "ncnn/c_api.h"

#endif /* NCNN_C_API_PORTABLE_H */
