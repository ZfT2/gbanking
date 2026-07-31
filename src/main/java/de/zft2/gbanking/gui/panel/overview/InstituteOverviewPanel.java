package de.zft2.gbanking.gui.panel.overview;

import de.zft2.gbanking.gui.enu.PageContext;
import de.zft2.gbanking.gui.panel.institute.InstituteDetailPanel;
import de.zft2.gbanking.gui.panel.institute.InstituteListPanel;
import de.zft2.gbanking.gui.panel.layout.DetailListPane;

public class InstituteOverviewPanel extends OverviewBasePanel {

	private final InstituteDetailPanel detailPanel = new InstituteDetailPanel();
	private final InstituteListPanel listPanel = new InstituteListPanel(this);

	@Override
	public void createOverallPanel(boolean show) {
		setPageContext(PageContext.INSTITUTES);
		setOverviewContent("UI_PANEL_INSTITUTES", new DetailListPane(detailPanel, listPanel), show);
	}

	public InstituteDetailPanel getDetailPanel() {
		return detailPanel;
	}

	@Override
	public void refreshOnShow() {
		listPanel.reload();
	}
}
