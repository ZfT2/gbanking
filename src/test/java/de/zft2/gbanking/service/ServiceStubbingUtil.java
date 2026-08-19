package de.zft2.gbanking.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import java.util.Collection;


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

	private static <T extends Service> void registerMock(Class<T> serviceType) {
		ServiceRegistry.setService(serviceType, mock(serviceType));
	}
}
