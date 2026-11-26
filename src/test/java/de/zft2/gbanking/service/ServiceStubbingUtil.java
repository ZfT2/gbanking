package de.zft2.gbanking.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.spy;

import java.util.Collection;
import java.util.function.Supplier;

import de.zft2.gbanking.db.DBController;

public final class ServiceStubbingUtil {

	private ServiceStubbingUtil() {
	}

	public static void initStubbedServicesInContext(Collection<Class<? extends Service>> serviceTypes) {
		for (Class<? extends Service> serviceType : serviceTypes) {
			registerMock(serviceType);
		}
	}

	public static void unloadStubbedServicesInContext(Collection<Class<? extends Service>> serviceTypes) {
		for (Class<? extends Service> serviceType : serviceTypes) {
			ServiceRegistry.removeService(serviceType);
		}
	}

	public static <T extends Service> T spyService(Class<T> type) {
		T spy = spy(ServiceRegistry.getService(type));
		ServiceRegistry.setService(type, spy);
		return spy;
	}

	/** Registers dependencies until the registry is reset after the test. */
	public static <T> T createWithServices(Supplier<T> factory, Service... services) {
		for (Service service : services) {
			var details = mockingDetails(service);
			Class<? extends Service> type = details.isMock()
					? details.getMockCreationSettings().getTypeToMock().asSubclass(Service.class) : service.getClass();
			registerService(type, service);
		}
		return factory.get();
	}

	public static <T> T createWithDatabase(Supplier<T> factory, DBController database) {
		try (var controller = mockStatic(DBController.class)) {
			controller.when(() -> DBController.getInstance(".")).thenReturn(database);
			return factory.get();
		}
	}

	private static <T extends Service> void registerService(Class<T> type, Service service) {
		ServiceRegistry.setService(type, type.cast(service));
	}

	private static <T extends Service> void registerMock(Class<T> serviceType) {
		ServiceRegistry.setService(serviceType, mock(serviceType));
	}
}
