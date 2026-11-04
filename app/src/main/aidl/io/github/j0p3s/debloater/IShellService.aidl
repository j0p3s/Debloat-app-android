package io.github.j0p3s.debloater;

import android.os.Bundle;
import android.os.ParcelFileDescriptor;

/**
 * Runs inside a Shizuku user service process (uid shell or root).
 * Every result Bundle contains: "exitCode" (int), "stdout" (String), "stderr" (String).
 */
interface IShellService {
    /** Reserved by Shizuku: called when the service is being destroyed. */
    void destroy() = 16777114;

    void exit() = 1;

    /** Runs a command with `sh -c`. */
    Bundle exec(String command) = 2;
    /** Cache operations use a shorter subprocess wait; never changes the command itself. */
    Bundle execCache(String command) = 5;

    /**
     * Installs one or more APK splits through a pm install session.
     * APK contents are streamed from the given file descriptors.
     */
    Bundle installApks(in ParcelFileDescriptor[] apks, in long[] sizes, in String[] names, int userId) = 3;

    /** Copies a file readable by the shell user into the given writable descriptor. Returns bytes copied or -1. */
    long copyFile(String path, in ParcelFileDescriptor dest) = 4;
}
