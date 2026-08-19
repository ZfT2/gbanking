package de.zft2.gbanking.service;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class ServiceRegistry {

	private static final Map<Class<? extends Service>, Service> SERVICES = new HashMap<>();

	private ServiceRegistry() {
	}

	public static synchronized <T extends Service> T getService(Class<T> type) {
		Objects.requireNonNull(type, "type");
		Service service = SERVICES.get(type);

		if (service == null) {
			try {
				service = type.getDeclaredConstructor().newInstance();
				SERVICES.put(type, service);
			} catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException | SecurityException e) {
				throw new IllegalStateException("Could not instantiate service: " + type.getName(), e);
			}
		}

		return type.cast(service);
	}

	public static synchronized <T extends Service> void setService(Class<T> type, T service) {
		SERVICES.put(Objects.requireNonNull(type, "type"), Objects.requireNonNull(service, "service"));
	}

	public static synchronized void removeService(Class<? extends Service> type) {
		SERVICES.remove(Objects.requireNonNull(type, "type"));
	}

	public static synchronized void resetServices() {
		SERVICES.clear();
	}
}
