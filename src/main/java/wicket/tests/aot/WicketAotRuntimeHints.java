package wicket.tests.aot;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

import java.util.List;

@Configuration
@ImportRuntimeHints(WicketAotRuntimeHints.class)
public class WicketAotRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(@NonNull RuntimeHints hints, @Nullable ClassLoader classLoader) {
        JarScanningRuntimeHintsRegistrarHelper jarScanningRuntimeHintsRegistrarHelper = new JarScanningRuntimeHintsRegistrarHelper();
        jarScanningRuntimeHintsRegistrarHelper.registerClasspathResourcesAndClasses(
                hints,
                classLoader,
                List.of("org/apache/wicket"),
                List.of()
        );
        jarScanningRuntimeHintsRegistrarHelper.registerClasspathDirectoryResourcesAndClasses(
                hints,
                classLoader,
                "target/classes",
                List.of("wicket/tests/components"),
                List.of(".html")
        );
    }
}
