package de.zft2.gbanking.service;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ServiceRegistryTest {

	@AfterEach
	void tearDown() {
		ServiceRegistry.resetServices();
	}

	@Test
	void getServiceShouldReuseCreatedInstance() {
		TestService first = ServiceRegistry.getService(TestService.class);

		assertSame(first, ServiceRegistry.getService(TestService.class));
	}

	@Test
	void setServiceShouldReplaceCreatedInstance() {
		TestService replacement = new TestService();

		ServiceRegistry.setService(TestService.class, replacement);

		assertSame(replacement, ServiceRegistry.getService(TestService.class));
	}

	@Test
	void resetServicesShouldDiscardCreatedInstances() {
		TestService first = ServiceRegistry.getService(TestService.class);

		ServiceRegistry.resetServices();

		assertNotSame(first, ServiceRegistry.getService(TestService.class));
	}

	@Test
	void getServiceShouldFailFastWithoutDefaultConstructor() {
		assertThrows(IllegalStateException.class, () -> ServiceRegistry.getService(ServiceWithoutDefaultConstructor.class));
	}

	static final class TestService implements Service {
	}

	static final class ServiceWithoutDefaultConstructor implements Service {
		ServiceWithoutDefaultConstructor(String value) {
		}
	}
}
