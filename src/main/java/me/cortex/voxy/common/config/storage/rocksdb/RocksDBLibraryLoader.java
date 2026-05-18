package me.cortex.voxy.common.config.storage.rocksdb;

import org.rocksdb.RocksDB;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

final class RocksDBLibraryLoader {
    private static final String[] ANDROID_LIBRARY_LOAD_ORDER = {
            "libc++_shared.so",
            "libsnappy.so",
            "libbz2.so",
            "liblz4.so",
            "libzstd.so",
            "librocksdb.so",
            "librocksdbjni.so"
    };

    private static boolean loaded;

    private RocksDBLibraryLoader() {
    }

    static synchronized void loadLibrary() {
        if (loaded) {
            return;
        }

        try {
            RocksDB.loadLibrary();
            loaded = true;
            return;
        } catch (RuntimeException | UnsatisfiedLinkError originalFailure) {
            try {
                loadAndroidLibrarySet();
                loaded = true;
                return;
            } catch (RuntimeException androidFailure) {
                androidFailure.addSuppressed(originalFailure);
                throw androidFailure;
            }
        }
    }

    private static void loadAndroidLibrarySet() {
        var abi = getAndroidAbi();
        var resourceBase = "jni/" + abi + "/";

        try {
            var tempDirectory = Files.createTempDirectory("voxy-rocksdb-android-");
            tempDirectory.toFile().deleteOnExit();

            for (var libraryName : ANDROID_LIBRARY_LOAD_ORDER) {
                var libraryPath = extractLibrary(resourceBase, libraryName, tempDirectory);
                System.load(libraryPath.toAbsolutePath().toString());
            }
        } catch (IOException | UnsatisfiedLinkError e) {
            throw new RuntimeException("Unable to load RocksDB Android native libraries for ABI " + abi, e);
        }
    }

    private static Path extractLibrary(String resourceBase, String libraryName, Path tempDirectory) throws IOException {
        var resourcePath = resourceBase + libraryName;
        var outputPath = tempDirectory.resolve(libraryName);
        var classLoader = RocksDBLibraryLoader.class.getClassLoader();

        try (InputStream input = classLoader.getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new RuntimeException(resourcePath + " was not found inside JAR");
            }

            Files.copy(input, outputPath, StandardCopyOption.REPLACE_EXISTING);
        }

        outputPath.toFile().deleteOnExit();
        return outputPath;
    }

    private static String getAndroidAbi() {
        var arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);

        return switch (arch) {
            case "aarch64", "arm64" -> "arm64-v8a";
            case "arm", "arm32" -> "armeabi-v7a";
            case "x86", "i386", "i486", "i586", "i686" -> "x86";
            case "amd64", "x86_64" -> "x86_64";
            default -> throw new RuntimeException("Unsupported RocksDB Android native architecture: " + arch);
        };
    }
}
