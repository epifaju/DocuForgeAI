package ai.docuforge.config;

import java.util.List;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

@Configuration
public class I18nConfig {

    public static final Locale LOCALE_FR = Locale.FRENCH;
    public static final Locale LOCALE_PT = Locale.forLanguageTag("pt");

    @Bean
    MessageSource messageSource() {
        ReloadableResourceBundleMessageSource source = new ReloadableResourceBundleMessageSource();
        source.setBasename("classpath:i18n/messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        source.setDefaultLocale(LOCALE_FR);
        return source;
    }

    @Bean
    LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setDefaultLocale(LOCALE_FR);
        resolver.setSupportedLocales(List.of(LOCALE_FR, LOCALE_PT));
        return resolver;
    }
}
