package info.openrocket.swing.utils;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.inject.Injector;
import info.openrocket.core.startup.Application;
import info.openrocket.core.formatting.RocketDescriptor;
import info.openrocket.core.l10n.Translator;
import info.openrocket.core.preferences.ApplicationPreferences;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BasicApplicationTest {

    private Injector originalInjector;

    @BeforeEach
    void captureOriginalInjector() {
        originalInjector = Application.getInjector();
    }

    @AfterEach
    void restoreOriginalInjector() {
        Application.setInjector(originalInjector);
    }

    @Test
	void initializeApplicationProvidesInjectorAndPreferences() {
		Application.setInjector(null);
		BasicApplication app = new BasicApplication();

		app.initializeApplication();

		assertNotNull(Application.getInjector(), "Injector should be initialized");
		assertNotNull(Application.getPreferences(), "Preferences should be accessible");
	}

	@Test
	void reinitializingApplicationKeepsInjectorValid() {
		BasicApplication app = new BasicApplication();
		app.initializeApplication();
		Injector first = Application.getInjector();

		app.initializeApplication();
		Injector second = Application.getInjector();

		assertNotNull(first);
		assertNotNull(second);
	}

	@Test
	void coreServicesModuleProvidesExpectedBindings() {
		Application.setInjector(null);
		BasicApplication app = new BasicApplication();
		app.initializeApplication();

		Injector injector = Application.getInjector();

		assertNotNull(injector.getInstance(ApplicationPreferences.class));
		assertNotNull(injector.getInstance(Translator.class));
		assertNotNull(injector.getInstance(RocketDescriptor.class));
	}
}
