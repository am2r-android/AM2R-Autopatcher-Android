package com.community.am2r.patcher;

import android.content.pm.PackageInstaller;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class InstallReport {
    private static final Pattern CODE = Pattern.compile("\\bINSTALL_(?:FAILED|PARSE_FAILED)_[A-Z0-9_]+\\b");

    // Keep the useful Android error code without copying file paths or other personal details.
    static String failureCode(String message) {
        Matcher match = CODE.matcher(message == null ? "" : message);
        return match.find() ? match.group() : "Not provided";
    }

    static String summary(int status) {
        switch (status) {
            case PackageInstaller.STATUS_SUCCESS: return "AM2R installed successfully.";
            case PackageInstaller.STATUS_FAILURE_ABORTED: return "Installation was cancelled. Your APK is still in Downloads.";
            case PackageInstaller.STATUS_FAILURE_STORAGE: return "Android could not allocate enough storage. Free some internal storage, then try again.";
            case PackageInstaller.STATUS_FAILURE_CONFLICT: return "Android found a conflict with an installed app. Back up your saves before changing an existing installation.";
            case PackageInstaller.STATUS_FAILURE_INCOMPATIBLE: return "Android reports this APK is incompatible. Copy the details below when reporting the problem.";
            case PackageInstaller.STATUS_FAILURE_INVALID: return "Android rejected the APK. Copy the details below when reporting the problem.";
            case PackageInstaller.STATUS_FAILURE_BLOCKED: return "Android or a device policy blocked installation. Check the install permission and copy the details below.";
            default: return "Installation did not complete. Your APK is still in Downloads. Copy the details below when reporting the problem.";
        }
    }
}
