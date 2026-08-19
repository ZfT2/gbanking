package de.zft2.gbanking;

import de.zft2.gbanking.service.GBankingService;
import de.zft2.gbanking.service.ServiceRegistry;

public interface BaseMessagesBean extends BaseMessagesDb {

	default GBankingService getGBankingService() {
		return ServiceRegistry.getService(GBankingService.class);
	}
}
