package de.zft2.gbanking.gui.panel.moneytransfer;

import java.util.List;

import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.MoneyTransfer;
import de.zft2.gbanking.db.dao.enu.OrderType;
import javafx.collections.FXCollections;
import javafx.css.PseudoClass;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;

public class MoneyTransferInputPanel extends MoneyTransferInputBasePanel {

	private static final List<OrderType> TRANSFER_ORDER_TYPES = List.of(OrderType.TRANSFER, OrderType.REALTIME_TRANSFER, OrderType.URGENT_TRANSFER);
	private static final PseudoClass UNAVAILABLE_PSEUDO_CLASS = PseudoClass.getPseudoClass("unavailable");

	private final ComboBox<OrderType> transferTypeCombo = new ComboBox<>(FXCollections.observableArrayList(TRANSFER_ORDER_TYPES));
	private BankAccount currentAccount;
	private boolean showingExistingTransfer;

	public MoneyTransferInputPanel(MoneyTransferDetailListTabPanel parent) {
		super(parent);
		transferTypeCombo.setValue(OrderType.TRANSFER);
		transferTypeCombo.setCellFactory(listView -> createTransferTypeCell());
		transferTypeCombo.setButtonCell(createTransferTypeCell());
		transferTypeCombo.valueProperty().addListener((obs, oldValue, newValue) -> {
			if (newValue != null) {
				refreshCapabilityState(currentAccount);
				getParentPanel().getMoneyTransferListPanel().reload();
				if (getParentPanel().getSelectedAccount() != null) {
					getParentPanel().getMoneyTransferListPanel().updatePanelBorder(getParentPanel().getSelectedAccount().getAccountName());
				}
			}
		});
		initializeSpecificFields();
		buttonSubmit.setText(getText("UI_BUTTON_MONEYTRANSFER_SAVE"));
	}

	@Override
	protected void addSpecificFields() {
		addFieldAbove("UI_LABEL_TRANSFER_TYPE", transferTypeCombo, 0, 5, 2);
	}

	@Override
	public OrderType getOrderType() {
		return transferTypeCombo.getValue();
	}

	@Override
	protected void resetSpecificFields() {
		transferTypeCombo.setValue(OrderType.TRANSFER);
	}

	@Override
	protected void updateSpecificFieldValues(MoneyTransfer selectedMoneytransfer) {
		showingExistingTransfer = true;
		try {
			transferTypeCombo.setValue(selectedMoneytransfer.getOrderType());
		} finally {
			showingExistingTransfer = false;
		}
	}

	@Override
	public void refreshCapabilityState(BankAccount selectedAccount) {
		currentAccount = selectedAccount;
		selectSupportedTransferTypeIfNeeded(selectedAccount);
		setCapabilityEnabled(isTransferTypeSupported(selectedAccount, getOrderType()));
		transferTypeCombo.setDisable(TRANSFER_ORDER_TYPES.stream().noneMatch(type -> isTransferTypeSupported(selectedAccount, type)));
		transferTypeCombo.setButtonCell(createTransferTypeCell());
	}

	private void selectSupportedTransferTypeIfNeeded(BankAccount selectedAccount) {
		if (showingExistingTransfer) {
			return;
		}
		OrderType selectedType = getOrderType();
		if (selectedType != null && isTransferTypeSupported(selectedAccount, selectedType)) {
			return;
		}
		OrderType transferType = TRANSFER_ORDER_TYPES.stream()
				.filter(type -> isTransferTypeSupported(selectedAccount, type))
				.findFirst()
				.orElse(null);
		if (transferType != null) {
			transferTypeCombo.setValue(transferType);
		}
	}

	private boolean isTransferTypeSupported(BankAccount selectedAccount, OrderType orderType) {
		return bean.supportsTransferOrderType(selectedAccount, orderType);
	}

	static List<OrderType> getTransferOrderTypes() {
		return TRANSFER_ORDER_TYPES;
	}

	public static boolean isTransferOrderType(OrderType orderType) {
		return TRANSFER_ORDER_TYPES.contains(orderType);
	}

	private ListCell<OrderType> createTransferTypeCell() {
		return new ListCell<>() {
			@Override
			protected void updateItem(OrderType item, boolean empty) {
				super.updateItem(item, empty);
				setText(empty || item == null ? null : item.toString());
				boolean unavailable = !empty && item != null && !isTransferTypeSupported(currentAccount, item);
				setDisable(unavailable);
				pseudoClassStateChanged(UNAVAILABLE_PSEUDO_CLASS, unavailable);
			}
		};
	}
}
