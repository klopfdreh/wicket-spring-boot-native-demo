package wicket.tests.aot;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;
import org.springframework.util.Assert;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

/**
 * The JarScanningRuntimeHintsRegistrarHelper is used to scan all JAR files in the classpath and register relevant resources and classes for native image compilation.
 */
public class JarScanningRuntimeHintsRegistrarHelper {

    private static final Logger log = LoggerFactory.getLogger(JarScanningRuntimeHintsRegistrarHelper.class);

    /**
     * Scans all JAR files in the classpath and registers relevant resources and classes for native image compilation.
     *
     * @param hints              the RuntimeHints to register the hints into
     * @param classLoader        the ClassLoader for loading classes
     * @param packagesToScan     the list of packages to scan
     * @param resourceExtensions the list of resource file extensions to register
     */
    public void registerClasspathResourcesAndClasses(@NonNull RuntimeHints hints, ClassLoader classLoader, Collection<String> packagesToScan, Collection<String> resourceExtensions) {
        Assert.state(!packagesToScan.isEmpty(), "packagesToScan must be not empty");

        String classPath = System.getProperty("java.class.path");
        String[] classpathEntries = classPath.split(File.pathSeparator);

        Arrays.stream(classpathEntries)
                .filter(entry -> entry.endsWith(".jar"))
                .forEach(jarPath -> processJarFile(jarPath, hints, classLoader, packagesToScan, resourceExtensions));
    }

    /**
     * Scans a classpath directory recursively and registers all relevant resources and classes for native image compilation.
     *
     * @param hints              the RuntimeHints to register the hints into
     * @param classLoader        the ClassLoader for loading classes
     * @param classPath          the classpath directory to scan recursively
     * @param packagesToScan     the list of packages to scan
     * @param resourceExtensions the list of resource file extensions to register
     */
    public void registerClasspathDirectoryResourcesAndClasses(@NonNull RuntimeHints hints, ClassLoader classLoader, String classPath, Collection<String> packagesToScan, Collection<String> resourceExtensions) {
        Assert.state(!packagesToScan.isEmpty(), "packagesToScan must be not empty");

        Path classPathDirectory = Path.of(classPath);

        if (!Files.isDirectory(classPathDirectory)) {
            log.warn("Classpath directory does not exist or is not a directory: {}", classPath);
            return;
        }

        try (var pathToWalk = Files.walk(classPathDirectory)) {
            pathToWalk
                    .filter(Files::isRegularFile)
                    .forEach(path -> processClasspathFile(path, classPathDirectory, hints, classLoader, packagesToScan, resourceExtensions));
        } catch (IOException e) {
            log.warn("Error while reading classpath directory: {}", classPath, e);
        }
    }

    /**
     * Processes a single JAR file and registers all relevant resources and classes.
     *
     * @param jarPath            the path to the JAR file
     * @param hints              the RuntimeHints for registration
     * @param classLoader        the ClassLoader for loading classes
     * @param packagesToScan     the list of packages to scan
     * @param resourceExtensions the list of resource file extensions to register
     */
    private void processJarFile(String jarPath, @NonNull RuntimeHints hints, ClassLoader classLoader, Collection<String> packagesToScan, Collection<String> resourceExtensions) {
        try (JarInputStream jarInputStream = new JarInputStream(Files.newInputStream(Path.of(jarPath)))) {
            JarEntry entry;
            while ((entry = jarInputStream.getNextJarEntry()) != null) {
                String entryName = entry.getName();

                if (shouldRegisterAsResource(entryName, packagesToScan, resourceExtensions)) {
                    registerResource(hints, entryName);
                }

                if (shouldRegisterAsClass(entryName, packagesToScan)) {
                    registerClassForReflection(hints, classLoader, entryName, packagesToScan);
                }
            }
        } catch (IOException e) {
            log.warn("Error while reading jar file: {}", jarPath, e);
        }
    }

    /**
     * Processes a single file from a classpath directory and registers all relevant resources and classes.
     *
     * @param path               the path to the file
     * @param classPathDirectory the root directory of the classpath
     * @param hints              the RuntimeHints for registration
     * @param classLoader        the ClassLoader for loading classes
     * @param packagesToScan     the list of packages to scan
     * @param resourceExtensions the list of resource file extensions to register
     */
    private void processClasspathFile(Path path, Path classPathDirectory, @NonNull RuntimeHints hints, ClassLoader classLoader, Collection<String> packagesToScan, Collection<String> resourceExtensions) {
        String entryName = classPathDirectory.relativize(path).toString().replace(File.separatorChar, '/');

        if (shouldRegisterAsResource(entryName, packagesToScan, resourceExtensions)) {
            registerResource(hints, entryName);
        }

        if (shouldRegisterAsClass(entryName, packagesToScan)) {
            registerClassForReflection(hints, classLoader, entryName, packagesToScan);
        }
    }

    /**
     * Checks whether a JAR entry should be registered as a resource.
     *
     * @param entryName          the name of the JAR entry
     * @param packagesToScan     the list of packages to scan
     * @param resourceExtensions the list of resource file extensions to register
     * @return true if the resource should be registered
     */
    private boolean shouldRegisterAsResource(String entryName, Collection<String> packagesToScan, Collection<String> resourceExtensions) {
        return !entryName.contains("package-info")
                && !resourceExtensions.isEmpty()
                && resourceExtensions.stream().anyMatch(entryName::endsWith)
                && packagesToScan.stream().anyMatch(entryName::startsWith);
    }

    /**
     * Registers a resource in the RuntimeHints.
     *
     * @param hints     the RuntimeHints for registration
     * @param entryName the name of the resource to register
     */
    public void registerResource(@NonNull RuntimeHints hints, String entryName) {
        log.info("Registered resource {}.", entryName);
        hints.resources().registerPattern(entryName);
    }

    /**
     * Checks whether a JAR entry should be registered as a class for reflection and serialization.
     *
     * @param entryName      the name of the JAR entry
     * @param packagesToScan the list of packages to scan
     * @return true if the class should be registered
     */
    private boolean shouldRegisterAsClass(String entryName, Collection<String> packagesToScan) {
        return !entryName.contains("package-info")
                && entryName.endsWith(".class")
                && packagesToScan.stream().anyMatch(entryName::startsWith);
    }

    /**
     * Registers a class for reflection and serialization.
     * For Hibernate classes (without inner classes), the array variants are additionally registered.
     *
     * @param hints          the RuntimeHints for registration
     * @param classLoader    the ClassLoader for loading classes
     * @param entryName      the name of the class entry in the JAR
     * @param packagesToScan the list of packages to scan
     */
    private void registerClassForReflection(@NonNull RuntimeHints hints, ClassLoader classLoader, String entryName, Collection<String> packagesToScan) {
        String className = convertEntryNameToClassName(entryName);
        log.info("Registered class {} for reflections and serialization.", className);
        hints.reflection().registerType(TypeReference.of(className), MemberCategory.values());
        if (shouldRegisterArrayClass(entryName, packagesToScan)) {
            registerArrayClass(hints, classLoader, className);
        }
    }

    /**
     * Converts a JAR entry name into a fully qualified class name.
     *
     * @param entryName the JAR entry name (e.g., "org/hibernate/Foo.class")
     * @return the fully qualified class name (e.g., "org.hibernate.Foo")
     */
    private String convertEntryNameToClassName(String entryName) {
        String className = entryName.replace('/', '.');
        return className.substring(0, className.length() - 6); // remove ".class"
    }

    /**
     * Checks whether the array variant should be additionally registered for a class.
     *
     * @param entryName      the name of the JAR entry
     * @param packagesToScan the list of packages to scan
     * @return true if the array class should be registered
     */
    private boolean shouldRegisterArrayClass(String entryName, Collection<String> packagesToScan) {
        return !entryName.contains("$") && packagesToScan.stream().anyMatch(entryName::startsWith);
    }

    /**
     * Registers the array variant of a class for reflection and serialization.
     *
     * @param hints       the RuntimeHints for registration
     * @param classLoader the ClassLoader for loading classes
     * @param className   the fully qualified class name
     */
    private void registerArrayClass(@NonNull RuntimeHints hints, ClassLoader classLoader, String className) {
        try {
            log.info("Registered array class {} for reflections and serialization.", className);
            Class<?> elementClass = Class.forName(className, false, classLoader);
            Class<?> arrayClass = Array.newInstance(elementClass, 0).getClass();
            hints.reflection().registerType(TypeReference.of(arrayClass), MemberCategory.values());
        } catch (NoClassDefFoundError | ClassNotFoundException e) {
            log.warn("Class not found for array class registration: {}", className, e);
        }
    }

    /**
     * Registers a model class for reflection and serialization.
     *
     * @param hints the RuntimeHints for registration
     * @param clazz the model class to register
     */
    public void registerSingleClassForReflection(@NonNull RuntimeHints hints, Class<?> clazz) {
        hints.reflection().registerType(TypeReference.of(clazz), hint -> hint.withMembers(MemberCategory.values()));
    }
}