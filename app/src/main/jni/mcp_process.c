#include <dirent.h>
#include <fcntl.h>
#include <jni.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/wait.h>
#include <unistd.h>
#include <errno.h>

#define UNUSED(x) x __attribute__((__unused__))

static int throw_runtime_exception(JNIEnv* env, char const* message)
{
    jclass exClass = (*env)->FindClass(env, "java/lang/RuntimeException");
    (*env)->ThrowNew(env, exClass, message);
    return -1;
}

static int create_subprocess(JNIEnv* env,
        char const* cmd,
        char const* cwd,
        char* const argv[],
        char** envp,
        int* pProcessId,
        int* pStdinFd,
        int* pStdoutFd)
{
    int stdin_pipe[2];
    int stdout_pipe[2];

    if (pipe(stdin_pipe) < 0) {
        return throw_runtime_exception(env, "Cannot create stdin pipe");
    }

    if (pipe(stdout_pipe) < 0) {
        close(stdin_pipe[0]);
        close(stdin_pipe[1]);
        return throw_runtime_exception(env, "Cannot create stdout pipe");
    }

    pid_t pid = fork();
    if (pid < 0) {
        close(stdin_pipe[0]);
        close(stdin_pipe[1]);
        close(stdout_pipe[0]);
        close(stdout_pipe[1]);
        return throw_runtime_exception(env, "Fork failed");
    } else if (pid > 0) {
        close(stdin_pipe[0]);
        close(stdout_pipe[1]);

        *pProcessId = (int) pid;
        *pStdinFd = stdin_pipe[1];
        *pStdoutFd = stdout_pipe[0];
        return 0;
    } else {
        sigset_t signals_to_unblock;
        sigfillset(&signals_to_unblock);
        sigprocmask(SIG_UNBLOCK, &signals_to_unblock, 0);

        close(stdin_pipe[1]);
        close(stdout_pipe[0]);

        dup2(stdin_pipe[0], STDIN_FILENO);
        dup2(stdout_pipe[1], STDOUT_FILENO);
        dup2(stdout_pipe[1], STDERR_FILENO);

        close(stdin_pipe[0]);
        close(stdout_pipe[1]);

        DIR* self_dir = opendir("/proc/self/fd");
        if (self_dir != NULL) {
            int self_dir_fd = dirfd(self_dir);
            struct dirent* entry;
            while ((entry = readdir(self_dir)) != NULL) {
                int fd = atoi(entry->d_name);
                if (fd > 2 && fd != self_dir_fd) close(fd);
            }
            closedir(self_dir);
        }

        clearenv();
        if (envp) for (; *envp; ++envp) putenv(*envp);

        if (chdir(cwd) != 0) {
            char* error_message;
            if (asprintf(&error_message, "chdir(\"%s\")", cwd) == -1) error_message = "chdir()";
            perror(error_message);
            fflush(stderr);
        }

        execvp(cmd, argv);
        char* error_message;
        if (asprintf(&error_message, "exec(\"%s\")", cmd) == -1) error_message = "exec()";
        perror(error_message);
        _exit(1);
    }
}

JNIEXPORT jintArray JNICALL Java_dev_chungjungsoo_gptmobile_data_mcp_NativeProcess_createSubprocess(
        JNIEnv* env,
        jclass UNUSED(clazz),
        jstring cmd,
        jstring cwd,
        jobjectArray args,
        jobjectArray envVars)
{
    jsize size = args ? (*env)->GetArrayLength(env, args) : 0;
    char** argv = NULL;
    if (size > 0) {
        argv = (char**) malloc((size + 1) * sizeof(char*));
        if (!argv) {
            throw_runtime_exception(env, "Couldn't allocate argv array");
            return NULL;
        }
        for (int i = 0; i < size; ++i) {
            jstring arg_java_string = (jstring) (*env)->GetObjectArrayElement(env, args, i);
            char const* arg_utf8 = (*env)->GetStringUTFChars(env, arg_java_string, NULL);
            if (!arg_utf8) {
                for (int j = 0; j < i; ++j) free(argv[j]);
                free(argv);
                throw_runtime_exception(env, "GetStringUTFChars() failed for argv");
                return NULL;
            }
            argv[i] = strdup(arg_utf8);
            (*env)->ReleaseStringUTFChars(env, arg_java_string, arg_utf8);
        }
        argv[size] = NULL;
    }

    size = envVars ? (*env)->GetArrayLength(env, envVars) : 0;
    char** envp = NULL;
    if (size > 0) {
        envp = (char**) malloc((size + 1) * sizeof(char*));
        if (!envp) {
            if (argv) {
                for (char** tmp = argv; *tmp; ++tmp) free(*tmp);
                free(argv);
            }
            throw_runtime_exception(env, "malloc() for envp array failed");
            return NULL;
        }
        for (int i = 0; i < size; ++i) {
            jstring env_java_string = (jstring) (*env)->GetObjectArrayElement(env, envVars, i);
            char const* env_utf8 = (*env)->GetStringUTFChars(env, env_java_string, 0);
            if (!env_utf8) {
                for (int j = 0; j < i; ++j) free(envp[j]);
                free(envp);
                if (argv) {
                    for (char** tmp = argv; *tmp; ++tmp) free(*tmp);
                    free(argv);
                }
                throw_runtime_exception(env, "GetStringUTFChars() failed for env");
                return NULL;
            }
            envp[i] = strdup(env_utf8);
            (*env)->ReleaseStringUTFChars(env, env_java_string, env_utf8);
        }
        envp[size] = NULL;
    }

    int procId = 0;
    int stdinFd = -1;
    int stdoutFd = -1;
    char const* cmd_cwd = (*env)->GetStringUTFChars(env, cwd, NULL);
    char const* cmd_utf8 = (*env)->GetStringUTFChars(env, cmd, NULL);

    int result = create_subprocess(env, cmd_utf8, cmd_cwd, argv, envp, &procId, &stdinFd, &stdoutFd);

    (*env)->ReleaseStringUTFChars(env, cmd, cmd_utf8);
    (*env)->ReleaseStringUTFChars(env, cwd, cmd_cwd);

    if (argv) {
        for (char** tmp = argv; *tmp; ++tmp) free(*tmp);
        free(argv);
    }
    if (envp) {
        for (char** tmp = envp; *tmp; ++tmp) free(*tmp);
        free(envp);
    }

    if (result < 0) {
        return NULL;
    }

    jintArray resultArray = (*env)->NewIntArray(env, 3);
    if (!resultArray) {
        close(stdinFd);
        close(stdoutFd);
        kill(procId, SIGTERM);
        throw_runtime_exception(env, "Failed to create result array");
        return NULL;
    }

    jint values[3] = { procId, stdinFd, stdoutFd };
    (*env)->SetIntArrayRegion(env, resultArray, 0, 3, values);

    return resultArray;
}

JNIEXPORT jint JNICALL Java_dev_chungjungsoo_gptmobile_data_mcp_NativeProcess_writeBytes(
        JNIEnv* env,
        jclass UNUSED(clazz),
        jint fd,
        jbyteArray data,
        jint offset,
        jint length)
{
    if (fd < 0) return -1;

    jbyte* bytes = (*env)->GetByteArrayElements(env, data, NULL);
    if (!bytes) {
        throw_runtime_exception(env, "GetByteArrayElements failed");
        return -1;
    }

    ssize_t written = write(fd, bytes + offset, length);
    (*env)->ReleaseByteArrayElements(env, data, bytes, JNI_ABORT);

    return (jint) written;
}

JNIEXPORT jint JNICALL Java_dev_chungjungsoo_gptmobile_data_mcp_NativeProcess_readBytes(
        JNIEnv* env,
        jclass UNUSED(clazz),
        jint fd,
        jbyteArray buffer,
        jint offset,
        jint length)
{
    if (fd < 0) return -1;

    jbyte* bytes = (*env)->GetByteArrayElements(env, buffer, NULL);
    if (!bytes) {
        throw_runtime_exception(env, "GetByteArrayElements failed");
        return -1;
    }

    ssize_t bytesRead = read(fd, bytes + offset, length);
    (*env)->ReleaseByteArrayElements(env, buffer, bytes, 0);

    return (jint) bytesRead;
}

JNIEXPORT jint JNICALL Java_dev_chungjungsoo_gptmobile_data_mcp_NativeProcess_waitFor(
        JNIEnv* UNUSED(env),
        jclass UNUSED(clazz),
        jint pid)
{
    int status;
    waitpid(pid, &status, 0);
    if (WIFEXITED(status)) {
        return WEXITSTATUS(status);
    } else if (WIFSIGNALED(status)) {
        return -WTERMSIG(status);
    } else {
        return 0;
    }
}

JNIEXPORT void JNICALL Java_dev_chungjungsoo_gptmobile_data_mcp_NativeProcess_closeFd(
        JNIEnv* UNUSED(env),
        jclass UNUSED(clazz),
        jint fd)
{
    if (fd >= 0) {
        close(fd);
    }
}

JNIEXPORT void JNICALL Java_dev_chungjungsoo_gptmobile_data_mcp_NativeProcess_sendSignal(
        JNIEnv* UNUSED(env),
        jclass UNUSED(clazz),
        jint pid,
        jint signal)
{
    if (pid > 0) {
        kill(pid, signal);
    }
}

JNIEXPORT jboolean JNICALL Java_dev_chungjungsoo_gptmobile_data_mcp_NativeProcess_isProcessAlive(
        JNIEnv* UNUSED(env),
        jclass UNUSED(clazz),
        jint pid)
{
    if (pid <= 0) return JNI_FALSE;
    int result = kill(pid, 0);
    return (result == 0) ? JNI_TRUE : JNI_FALSE;
}
