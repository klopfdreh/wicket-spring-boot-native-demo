package wicket.tests.config;

import org.apache.wicket.RuntimeConfigurationType;
import org.apache.wicket.protocol.http.WicketFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingFilterBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import wicket.tests.components.DefaultWebApplication;

@Configuration
public class WicketAutoConfiguration {

    @Bean
    @ConditionalOnMissingFilterBean(WicketFilter.class)
    public FilterRegistrationBean<WicketFilter> wicketFilterRegistration() {
        return configureWicketFilter(new WicketFilter(new DefaultWebApplication()));
    }

    private static FilterRegistrationBean<WicketFilter> configureWicketFilter(WicketFilter filter) {
        FilterRegistrationBean<WicketFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.addUrlPatterns("/*");
        registration.setName("wicket-filter");
        registration.setOrder(Ordered.LOWEST_PRECEDENCE);
        registration.addInitParameter(WicketFilter.FILTER_MAPPING_PARAM, "/*");
        registration.addInitParameter("configuration", RuntimeConfigurationType.DEVELOPMENT.name());
        return registration;
    }
}
