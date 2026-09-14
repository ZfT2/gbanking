package de.zft2.gbanking.gui.panel.overview;

import de.zft2.gbanking.gui.enu.PageContext;
import de.zft2.gbanking.gui.panel.institute.InstituteDetailPanel;
import de.zft2.gbanking.gui.panel.institute.InstituteListPanel;
import de.zft2.gbanking.gui.panel.layout.DetailListPane;

public class InstituteOverviewPanel extends OverviewBasePanel {

	private final InstituteDetailPanel detailPanel = new InstituteDetailPanel();
	private final InstituteListPanel listPanel = new InstituteListPanel(this);

	public InstituteOverviewPanel() {
		initializePanel();
	}

	private void initializePanel() {
		setPageContext(PageContext.INSTITUTES);
		setOverviewContent("UI_PANEL_INSTITUTES", new DetailListPane(detailPanel, listPanel));
	}

	public InstituteDetailPanel getDetailPanel() {
		return detailPanel;
	}

	@Override
	public void refreshOnShow() {
		listPanel.reload();
	}
}
