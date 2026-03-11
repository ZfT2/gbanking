package de.gbanking.gui.fx.panel.overview;

import de.gbanking.gui.fx.enu.PageContext;
import de.gbanking.gui.fx.panel.BasePanelHolder;

public abstract class OverviewBasePanel extends BasePanelHolder {

	private PageContext pageContext;

	public PageContext getPageContext() {
		return pageContext;
	}

	public void setPageContext(PageContext pageContext) {
		this.pageContext = pageContext;
	}

	public abstract void createOverallPanel(boolean show);
}