package wicket.tests;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

@SpringBootApplication
public class WicketNativeApplication {

    static void main(String[] args) {
        new SpringApplicationBuilder().sources(WicketNativeApplication.class).run(args);
    }
}
