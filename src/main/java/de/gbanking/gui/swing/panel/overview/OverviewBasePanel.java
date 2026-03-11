package de.gbanking.gui.swing.panel.overview;

import de.gbanking.gui.swing.enu.PageContext;
import de.gbanking.gui.swing.panel.BasePanelHolder;

public abstract class OverviewBasePanel extends BasePanelHolder {

	/**
	 * 
	 */
	private static final long serialVersionUID = -160925069741147557L;

	private PageContext pageContext;

	public PageContext getPageContext() {
		return pageContext;
	}

	public void setPageContext(PageContext pageContext) {
		this.pageContext = pageContext;
	}

	public abstract void createOverallPanel(boolean show);

}
