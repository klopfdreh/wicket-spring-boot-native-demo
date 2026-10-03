package wicket.tests.components;

import org.apache.wicket.Page;
import org.apache.wicket.protocol.http.WebApplication;

public class DefaultWebApplication extends WebApplication {
    @Override
    public Class<? extends Page> getHomePage() {
        return HomePage.class;
    }
}
