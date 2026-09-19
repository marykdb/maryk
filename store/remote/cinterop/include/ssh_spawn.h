#include <spawn.h>
#include <stddef.h>

#if defined(__APPLE__)
#include <crt_externs.h>
#define MARYK_ENVIRON (*_NSGetEnviron())
#else
extern char **environ;
#define MARYK_ENVIRON environ
#endif

/**
 * Spawn through the platform C library so PATH lookup and process creation
 * happen without running Kotlin code in a post-fork child.
 */
static inline int maryk_spawnp(pid_t *pid, char *const argv[]) {
    return posix_spawnp(pid, argv[0], NULL, NULL, argv, MARYK_ENVIRON);
}
