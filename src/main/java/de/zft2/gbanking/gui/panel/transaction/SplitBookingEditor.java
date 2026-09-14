package de.zft2.gbanking.gui.panel.transaction;

import static de.zft2.gbanking.util.TextValues.trimToNull;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import de.zft2.gbanking.BaseMessagesDb;
import de.zft2.gbanking.db.dao.BankAccount;
import de.zft2.gbanking.db.dao.Booking;
import de.zft2.gbanking.db.dao.Category;
import de.zft2.gbanking.db.dao.enu.BookingType;
import de.zft2.gbanking.db.dao.enu.Source;
import de.zft2.gbanking.file.BookingCsvFormat;
import de.zft2.gbanking.gui.GuiLayoutState;
import de.zft2.gbanking.gui.dialog.DialogWindowSupport;
import de.zft2.gbanking.gui.util.FormStyleUtils;
import de.zft2.gbanking.gui.util.FxNodeSupport;
import de.zft2.gbanking.gui.util.FxTableUtils;
import de.zft2.gbanking.service.booking.BookingSplitService;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.StringConverter;

final class SplitBookingEditor extends VBox implements BaseMessagesDb {

	private static final String REBOOKING_PARENT_WARNING = "ALERT_SPLIT_BOOKING_REBOOKING_PARENT";
	private static final double TABLE_HEIGHT = 155.0;

	private enum CrossBookingDeleteChoice {
		DELETE_CROSS, KEEP_CROSS, CANCEL
	}

	private final BookingSplitService bookingSplitService;
	private final Runnable reloadParentData;
	private final StringConverter<Category> categoryConverter;

	private final ObservableList<Booking> splitBookings = FXCollections.observableArrayList();
	private final ObservableList<Category> categoryChoices = FXCollections.observableArrayList();
	private final ObservableList<BankAccount> crossAccountChoices = FXCollections.observableArrayList();
	private final Map<Integer, Boolean> deletedSplitBookingActions = new HashMap<>();
	private final TableView<Booking> splitBookingTable = new TableView<>(splitBookings);
	private final Label disabledHintLabel = new Label(getText(REBOOKING_PARENT_WARNING));
	private final Label sumValueLabel = new Label();
	private final Label differenceValueLabel = new Label();
	private final Button newButton = new Button(getText("UI_BUTTON_NEW_SHORT"));
	private final Button deleteButton = new Button(getText("UI_BUTTON_DELETE"));
	private final Button saveButton = new Button(getText("UI_BUTTON_SAVE"));

	private Booking parentBooking;
	private boolean dirty;

	SplitBookingEditor(BookingSplitService bookingSplitService, Runnable reloadParentData, StringConverter<Category> categoryConverter) {
		this.bookingSplitService = Objects.requireNonNull(bookingSplitService);
		this.reloadParentData = Objects.requireNonNull(reloadParentData);
		this.categoryConverter = Objects.requireNonNull(categoryConverter);
		createUi();
		newButton.setOnAction(event -> addSplitBooking());
		deleteButton.setOnAction(event -> deleteSelectedSplitBookings());
		saveButton.setOnAction(event -> saveSplitBookings());
		updateButtons();
	}

	private void createUi() {
		configureTable();
		splitBookingTable.setMinHeight(TABLE_HEIGHT);
		splitBookingTable.setPrefHeight(TABLE_HEIGHT);
		splitBookingTable.setMaxHeight(TABLE_HEIGHT);
		disabledHintLabel.setWrapText(true);
		FxNodeSupport.setVisibleManaged(disabledHintLabel, false);

		HBox buttons = new HBox(10, newButton, deleteButton, saveButton);
		buttons.setAlignment(Pos.CENTER_RIGHT);
		buttons.setPadding(new Insets(6, 0, 0, 0));
		FormStyleUtils.styleButtons(newButton, deleteButton, saveButton);

		setSpacing(8);
		setPadding(new Insets(6));
		setMaxHeight(Region.USE_PREF_SIZE);
		getChildren().addAll(disabledHintLabel, splitBookingTable, createTotalsPane(), buttons);
	}

	private Node createTotalsPane() {
		GridPane grid = new GridPane();
		grid.setHgap(8);
		grid.setVgap(4);
		grid.setAlignment(Pos.CENTER_RIGHT);
		grid.add(new Label(getText("UI_LABEL_SPLIT_SUM")), 0, 0);
		grid.add(sumValueLabel, 1, 0);
		grid.add(new Label(getText("UI_LABEL_SPLIT_DIFFERENCE")), 0, 1);
		grid.add(differenceValueLabel, 1, 1);
		sumValueLabel.setAlignment(Pos.CENTER_RIGHT);
		differenceValueLabel.setAlignment(Pos.CENTER_RIGHT);

		Region spacer = new Region();
		spacer.prefWidthProperty().bind(splitBookingTable.getColumns().get(0).widthProperty()
				.add(splitBookingTable.getColumns().get(1).widthProperty())
				.add(splitBookingTable.getColumns().get(2).widthProperty()));
		HBox footer = new HBox(spacer, grid);
		footer.setAlignment(Pos.CENTER_LEFT);
		return footer;
	}

	private void configureTable() {
		splitBookingTable.setEditable(true);
		splitBookingTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

		TableColumn<Booking, Boolean> selectedColumn = FxTableUtils.createSelectAllSelectionColumn(getText("UI_TABLE_SELECT_ALL"), splitBookings,
				booking -> booking.isSelected(), (booking, selected) -> booking.setSelected(selected));
		splitBookingTable.getColumns().addAll(List.of(selectedColumn, createPurposeColumn(), createCategoryColumn(), createAmountColumn(),
				createCrossAccountColumn()));
		GuiLayoutState.configureTable(splitBookingTable, "transactionDetails.splitBookings");
	}

	private TableColumn<Booking, String> createPurposeColumn() {
		TableColumn<Booking, String> column = new TableColumn<>(getText("UI_TABLE_PURPOSE"));
		column.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getPurpose()));
		column.setCellFactory(TextFieldTableCell.forTableColumn());
		column.setOnEditCommit(event -> {
			event.getRowValue().setPurpose(trimToNull(event.getNewValue()));
			markDirty();
			splitBookingTable.refresh();
		});
		FxTableUtils.setPreferredWidth(column, 180, 260);
		return column;
	}

	private TableColumn<Booking, Category> createCategoryColumn() {
		TableColumn<Booking, Category> column = new TableColumn<>(getText("UI_LABEL_CATEGORY"));
		column.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getCategory()));
		column.setCellFactory(ComboBoxTableCell.forTableColumn(categoryConverter, categoryChoices));
		column.setOnEditCommit(event -> {
			event.getRowValue().setCategory(event.getNewValue());
			clearCategoryRuleAssignment(event.getRowValue());
			markDirty();
			splitBookingTable.refresh();
		});
		FxTableUtils.setPreferredWidth(column, 140, 220);
		return column;
	}

	private TableColumn<Booking, BigDecimal> createAmountColumn() {
		TableColumn<Booking, BigDecimal> column = new TableColumn<>(getText("UI_TABLE_AMOUNT"));
		column.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().getAmount()));
		column.setCellFactory(TextFieldTableCell.forTableColumn(createAmountConverter()));
		column.setOnEditCommit(event -> {
			event.getRowValue().setAmount(event.getNewValue());
			markDirty();
			updateTotals();
			splitBookingTable.refresh();
		});
		FxTableUtils.setPreferredWidth(column, 90, 120);
		return column;
	}

	private TableColumn<Booking, BankAccount> createCrossAccountColumn() {
		TableColumn<Booking, BankAccount> column = new TableColumn<>(getText("UI_TABLE_CROSS_ACCOUNT"));
		column.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(findCrossAccount(data.getValue().getCrossAccountId())));
		column.setCellFactory(ComboBoxTableCell.forTableColumn(createAccountConverter(), crossAccountChoices));
		column.setOnEditCommit(event -> {
			BankAccount account = event.getNewValue();
			event.getRowValue().setCrossAccountId(account != null ? account.getId() : null);
			markDirty();
			splitBookingTable.refresh();
		});
		FxTableUtils.setPreferredWidth(column, 140, 220);
		return column;
	}

	private StringConverter<BankAccount> createAccountConverter() {
		return new StringConverter<>() {
			@Override
			public String toString(BankAccount account) {
				return account != null ? account.getAccountName() : "";
			}

			@Override
			public BankAccount fromString(String value) {
				return null;
			}
		};
	}

	private StringConverter<BigDecimal> createAmountConverter() {
		DecimalFormat format = FxTableUtils.createGermanDecimalFormat();
		return new StringConverter<>() {
			@Override
			public String toString(BigDecimal value) {
				return value != null ? format.format(value) : "";
			}

			@Override
			public BigDecimal fromString(String value) {
				String normalizedValue = trimToNull(value);
				return normalizedValue != null ? BookingCsvFormat.parseAmount(normalizedValue) : null;
			}
		};
	}

	void setReferenceChoices(List<Category> categories, List<BankAccount> crossAccounts) {
		categoryChoices.setAll(categories != null ? categories : List.of());
		crossAccountChoices.setAll(crossAccounts != null ? crossAccounts : List.of());
	}

	void load(Booking booking) {
		parentBooking = booking;
		deletedSplitBookingActions.clear();
		dirty = false;
		splitBookings.setAll(booking != null ? bookingSplitService.getSplitBookings(booking) : List.of());
		updateTotals();
		updateButtons();
	}

	void clear() {
		load(null);
	}

	void discardChanges() {
		load(parentBooking);
	}

	boolean hasUnsavedChanges() {
		return dirty || !deletedSplitBookingActions.isEmpty();
	}

	Button getSaveButton() {
		return saveButton;
	}

	private void addSplitBooking() {
		if (!hasPersistedParent()) {
			showWarning("ALERT_SPLIT_BOOKING_PARENT_MISSING");
			return;
		}
		if (isParentRebooking()) {
			showWarning(REBOOKING_PARENT_WARNING);
			return;
		}

		Booking splitBooking = new Booking();
		splitBooking.setParentBookingId(parentBooking.getId());
		splitBooking.setAccountId(parentBooking.getAccountId());
		splitBooking.setDateBooking(parentBooking.getDateBooking());
		splitBooking.setDateValue(parentBooking.getDateValue());
		splitBooking.setDate(parentBooking.getDate());
		splitBooking.setPurpose(parentBooking.getPurpose());
		splitBooking.setAmount(calculateDifference());
		splitBooking.setSource(Source.MANUELL);
		splitBooking.setCategory(parentBooking.getCategory());
		clearCategoryRuleAssignment(splitBooking);
		splitBookings.add(splitBooking);
		markDirty();
		updateTotals();
		updateButtons();
	}

	private void deleteSelectedSplitBookings() {
		List<Booking> selectedBookings = splitBookings.stream().filter(Booking::isSelected).toList();
		if (selectedBookings.isEmpty() && splitBookingTable.getSelectionModel().getSelectedItem() != null) {
			selectedBookings = List.of(splitBookingTable.getSelectionModel().getSelectedItem());
		}
		if (selectedBookings.isEmpty()) {
			return;
		}

		Map<Integer, Boolean> selectedDeleteActions = new HashMap<>();
		for (Booking splitBooking : selectedBookings) {
			if (splitBooking.getId() > 0) {
				CrossBookingDeleteChoice choice = chooseCrossBookingDeleteAction(splitBooking);
				if (choice == CrossBookingDeleteChoice.CANCEL) {
					return;
				}
				selectedDeleteActions.put(splitBooking.getId(), choice == CrossBookingDeleteChoice.DELETE_CROSS);
			}
		}
		deletedSplitBookingActions.putAll(selectedDeleteActions);
		splitBookings.removeAll(selectedBookings);
		markDirty();
		updateTotals();
		updateButtons();
	}

	private CrossBookingDeleteChoice chooseCrossBookingDeleteAction(Booking splitBooking) {
		Integer crossBookingId = splitBooking.getCrossBookingId();
		if (crossBookingId == null || crossBookingId <= 0) {
			return CrossBookingDeleteChoice.DELETE_CROSS;
		}

		ButtonType deleteCrossButton = new ButtonType(getText("UI_BUTTON_YES"), ButtonBar.ButtonData.YES);
		ButtonType keepCrossButton = new ButtonType(getText("UI_BUTTON_NO"), ButtonBar.ButtonData.NO);
		ButtonType result = DialogWindowSupport.showChoice(getOwnerWindow(), Alert.AlertType.CONFIRMATION,
				getText("ALERT_SPLIT_BOOKING_DELETE_CROSS_TITLE"), getText("ALERT_SPLIT_BOOKING_DELETE_CROSS_HEADER"),
				getText("ALERT_SPLIT_BOOKING_DELETE_CROSS_TEXT"), deleteCrossButton, keepCrossButton, ButtonType.CANCEL)
				.orElse(ButtonType.CANCEL);
		if (result == deleteCrossButton) {
			return CrossBookingDeleteChoice.DELETE_CROSS;
		}
		return result == keepCrossButton ? CrossBookingDeleteChoice.KEEP_CROSS : CrossBookingDeleteChoice.CANCEL;
	}

	private void saveSplitBookings() {
		if (!hasPersistedParent()) {
			showWarning("ALERT_SPLIT_BOOKING_PARENT_MISSING");
			return;
		}
		if (isParentRebooking()) {
			showWarning(REBOOKING_PARENT_WARNING);
			return;
		}

		try {
			List<Booking> savedSplitBookings = bookingSplitService.saveSplitBookings(parentBooking, new ArrayList<>(splitBookings),
					deletedSplitBookingActions);
			deletedSplitBookingActions.clear();
			dirty = false;
			splitBookings.setAll(savedSplitBookings);
			updateTotals();
			updateButtons();
			reloadParentData.run();
		} catch (Exception e) {
			DialogWindowSupport.showAlert(getOwnerWindow(), Alert.AlertType.WARNING, e.getMessage());
		}
	}

	private void updateButtons() {
		boolean hasSavedParentBooking = hasPersistedParent();
		boolean splitEditingAllowed = hasSavedParentBooking && !isParentRebooking();
		FxNodeSupport.setVisibleManaged(disabledHintLabel, hasSavedParentBooking && isParentRebooking());
		newButton.setDisable(!splitEditingAllowed);
		deleteButton.setDisable(!splitEditingAllowed || splitBookings.isEmpty());
		saveButton.setDisable(!splitEditingAllowed);
		splitBookingTable.setDisable(!splitEditingAllowed);
	}

	private void updateTotals() {
		if (parentBooking == null || parentBooking.getAmount() == null) {
			sumValueLabel.setText("");
			differenceValueLabel.setText("");
			return;
		}

		BigDecimal splitSum = calculateSum();
		DecimalFormat format = FxTableUtils.createGermanDecimalFormat();
		sumValueLabel.setText(format.format(splitSum));
		differenceValueLabel.setText(format.format(parentBooking.getAmount().subtract(splitSum)));
	}

	private void markDirty() {
		dirty = true;
	}

	private BigDecimal calculateDifference() {
		return parentBooking != null && parentBooking.getAmount() != null
				? parentBooking.getAmount().subtract(calculateSum())
				: BigDecimal.ZERO;
	}

	private BigDecimal calculateSum() {
		return splitBookings.stream().map(Booking::getAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	private boolean hasPersistedParent() {
		return parentBooking != null && parentBooking.getId() > 0;
	}

	private boolean isParentRebooking() {
		return parentBooking != null && BookingType.isRebooking(parentBooking.getBookingType());
	}

	private BankAccount findCrossAccount(Integer accountId) {
		if (accountId == null || accountId <= 0) {
			return null;
		}
		return crossAccountChoices.stream().filter(account -> account.getId() == accountId).findFirst().orElse(null);
	}

	private void clearCategoryRuleAssignment(Booking booking) {
		booking.setCategoryRuleId(null);
		booking.setCategoryRuleName(null);
	}

	private void showWarning(String key) {
		DialogWindowSupport.showAlert(getOwnerWindow(), Alert.AlertType.WARNING, getText(key));
	}

	private Window getOwnerWindow() {
		return getScene() != null ? getScene().getWindow() : null;
	}
}
