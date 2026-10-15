package com.community.am2r.patcher;

public final class InstallReportTest {
    public static void main(String[] args) {
        String detail = InstallReport.failureCode("INSTALL_FAILED_NO_MATCHING_ABIS: path /private/example.apk");
        check(detail.equals("INSTALL_FAILED_NO_MATCHING_ABIS"));
        check(InstallReport.failureCode("unrecognized message with a private path").equals("Not provided"));
        check(InstallReport.failureCode(null).equals("Not provided"));
        check(InstallReport.summary(7).contains("incompatible"));
        check(InstallReport.summary(6).contains("storage"));
        check(InstallReport.summary(5).contains("Back up"));
        check(!InstallReport.summary(5).contains("Uninstall"));
        check(InstallReport.summary(3).contains("cancelled"));
        check(InstallReport.summary(0).equals("AM2R installed successfully."));
        check(InstallReport.summary(12345).contains("did not complete"));
        System.out.println("Installer result checks passed.");
    }
    private static void check(boolean result) { if (!result) throw new AssertionError(); }
}
