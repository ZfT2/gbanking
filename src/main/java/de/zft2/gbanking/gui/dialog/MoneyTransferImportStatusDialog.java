package de.zft2.gbanking.gui.dialog;

import java.util.Optional;

import de.zft2.gbanking.BaseMessages;
import de.zft2.gbanking.db.dao.enu.MoneyTransferStatus;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.stage.Window;

public final class MoneyTransferImportStatusDialog {

	private MoneyTransferImportStatusDialog() {
	}

	public static Optional<MoneyTransferStatus> show(Window owner) {
		ButtonType archiveButton = new ButtonType(BaseMessages.getTextStatic("UI_MONEYTRANSFER_IMPORT_TARGET_ARCHIVE"),
				ButtonBar.ButtonData.OK_DONE);
		ButtonType openButton = new ButtonType(BaseMessages.getTextStatic("UI_MONEYTRANSFER_IMPORT_TARGET_OPEN"), ButtonBar.ButtonData.OTHER);
		Optional<ButtonType> selection = DialogWindowSupport.showChoice(owner, Alert.AlertType.CONFIRMATION,
				BaseMessages.getTextStatic("UI_MONEYTRANSFER_IMPORT_TARGET_TITLE"),
				BaseMessages.getTextStatic("UI_MONEYTRANSFER_IMPORT_TARGET_HEADER"),
				BaseMessages.getTextStatic("UI_MONEYTRANSFER_IMPORT_TARGET_TEXT"), archiveButton, openButton, ButtonType.CANCEL);
		return selection.filter(button -> button != ButtonType.CANCEL)
				.map(button -> button.equals(openButton) ? MoneyTransferStatus.NEW : MoneyTransferStatus.IMPORTED);
	}
}
