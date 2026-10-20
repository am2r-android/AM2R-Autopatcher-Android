#!/usr/bin/env python3
"""Prepare the shared extension-dispatch cache in an isolated pinned runtime."""
import argparse
from pathlib import Path

RUNNER = "android/runner/ProjectFiles/src/main/java/com/yoyogames/runner/RunnerJNILib.java"
ANCHOR = "    public static Object CallExtensionFunction(String classname, String methodname, int argcount,  double [] dblargs,Object []objargs)"
ORIGINAL = "Method[] methods = ReqClass.getMethods();"
CACHED = "Method[] methods = extensionMethods(ReqClass);"
HELPER = """    // BEGIN extension method list cache
    // Extension classes stay loaded for this process. Reuse their public method
    // list; select the signature and invoke the current instance as before.
    private static final java.util.concurrent.ConcurrentHashMap<Class<?>, Method[]> extensionMethodLists =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static Method[] extensionMethods(Class<?> type) {
        Method[] methods = extensionMethodLists.get(type);
        if (methods == null) {
            methods = type.getMethods();
            Method[] previous = extensionMethodLists.putIfAbsent(type, methods);
            if (previous != null) methods = previous;
        }
        return methods;
    }
    // END extension method list cache

"""


def prepare_runtime(runtime):
    path = Path(runtime) / RUNNER
    original = path.read_bytes()
    text = original.decode("utf-8")
    newline = "\r\n" if "\r\n" in text else "\n"
    text = text.replace("\r\n", "\n")
    if text.count(ANCHOR) != 1:
        raise ValueError("Unexpected runner; use an isolated copy of runtime 2026.100.0.1046")
    if "extensionMethodLists" in text or CACHED in text:
        if text.count(HELPER) != 1 or text.count(CACHED) != 1 or ORIGINAL in text:
            raise ValueError("Existing extension cache differs; inspect the runtime copy")
        return False
    if text.count(ORIGINAL) != 1:
        raise ValueError("Unexpected extension dispatch; no file was changed")
    text = text.replace(ANCHOR, HELPER + ANCHOR).replace(ORIGINAL, CACHED)
    path.write_bytes(text.replace("\n", newline).encode("utf-8"))
    return True


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--runtime-copy", type=Path, required=True)
    args = parser.parse_args()
    changed = prepare_runtime(args.runtime_copy)
    print("Extension cache applied." if changed else "Extension cache already matches.")
