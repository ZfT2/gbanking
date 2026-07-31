package de.zft2.gbanking;

import de.zft2.gbanking.gui.GBankingContext;
import de.zft2.gbanking.service.GBankingBean;

public interface BaseMessagesBean extends BaseMessagesDb {

	public static final GBankingBean bean = GBankingContext.getBean();

	default GBankingBean getGBankingBean() {
		return bean;
	}
}
