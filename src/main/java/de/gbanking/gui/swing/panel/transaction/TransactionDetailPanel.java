package de.gbanking.gui.swing.panel.transaction;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.math.BigDecimal;
import java.util.Calendar;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.border.Border;

import de.gbanking.db.dao.BankAccount;
import de.gbanking.db.dao.Booking;
import de.gbanking.db.dao.Recipient;
import de.gbanking.db.dao.enu.BookingType;
import de.gbanking.db.dao.enu.Source;
import de.gbanking.gui.swing.panel.BasePanelHolder;
import de.gbanking.util.TypeConverter;

public class TransactionDetailPanel extends BasePanelHolder {

	/**
	 * 
	 */
	private static final long serialVersionUID = 4182710529424381830L;

	private Component parentPanel;

	private enum EditContext {
		NEW, EDIT, READONLY;
	}

	private EditContext context;

	private DefaultComboBoxModel<BankAccount> bankAccountItemList;
	private DefaultComboBoxModel<BookingType> bookingTypeItemList;
	private DefaultComboBoxModel<Source> bookingSourceItemList;

	private JTextField accountNameText;
	private JTextField dateBookingText;
	private JTextField dateValueText;
	private JTextArea purposeText;
	private JTextField amountText;
	private JTextField currencyText;
	private JComboBox<BookingType> bookingTypeCombo;
	private JComboBox<Source> bookingSourceCombo;
	private JComboBox<BankAccount> crossAccountCombo;

	private JTextField categoryText;
	
	private JTextField sepaCustomerRefText;
	private JTextField sepaCreditorIdText;
	private JTextField sepaEndToEndText;
	private JTextField sepaMandateText;
	private JTextField sepaPersonIdText;
	private JTextField sepaPurposeText;
	private JTextField sepaTypText;

	private JTextField recipientNameText;
	private JTextField recipientIbanText;
	private JTextField recipientAccountNumberText;
	private JTextField recipientBicText;
	private JTextField recipientBlzText;
	private JTextField recipientBankText;

	private JTextField updatedAtText;

	private JButton newButton;
	private JButton editButton;
	private JButton saveButton;

	private Booking displayedBooking;

	public JTextField getAccountNameText() {
		return accountNameText;
	}

	public JTextField getDateBookingText() {
		return dateBookingText;
	}

	public JTextField getDateValueText() {
		return dateValueText;
	}

	public JTextArea getPurposeText() {
		return purposeText;
	}

	public JTextField getAmountText() {
		return amountText;
	}

	public JTextField getCurrencyText() {
		return currencyText;
	}

	public JComboBox<BookingType> getBookingTypeCombo() {
		return bookingTypeCombo;
	}

	public JComboBox<Source> getBookingSourceCombo() {
		return bookingSourceCombo;
	}

	public JComboBox<BankAccount> getCrossAccountCombo() {
		return crossAccountCombo;
	}

	public JTextField getCategoryText() {
		return categoryText;
	}

	public JTextField getSepaCustomerRefText() {
		return sepaCustomerRefText;
	}

	public JTextField getSepaCreditorIdText() {
		return sepaCreditorIdText;
	}

	public JTextField getSepaEndToEndText() {
		return sepaEndToEndText;
	}

	public JTextField getSepaMandateText() {
		return sepaMandateText;
	}

	public JTextField getSepaPersonIdText() {
		return sepaPersonIdText;
	}

	public JTextField getSepaPurposeText() {
		return sepaPurposeText;
	}

	public JTextField getSepaTypText() {
		return sepaTypText;
	}

	public JTextField getRecipientNameText() {
		return recipientNameText;
	}

	public JTextField getRecipientIbanText() {
		return recipientIbanText;
	}

	public JTextField getRecipientAccountNumberText() {
		return recipientAccountNumberText;
	}

	public JTextField getRecipientBicText() {
		return recipientBicText;
	}

	public JTextField getRecipientBlzText() {
		return recipientBlzText;
	}

	public JTextField getRecipientBankText() {
		return recipientBankText;
	}

	public JTextField getUpdatedAtText() {
		return updatedAtText;
	}

	public TransactionDetailPanel(Container parent) {
		this.parentPanel = parent;
		createInnerTransactionDetailPanel();
	}

	private void createInnerTransactionDetailPanel() {
		Border transactionPanelBorder = BorderFactory.createTitledBorder("Umsatz Details");
		setBorder(transactionPanelBorder);

		JLabel dateBookingLabel = new JLabel("Datum Buchung");
		dateBookingText = new JTextField();
		JLabel dateValueLabel = new JLabel("Wertstellung (Valuta)");
		dateValueText = new JTextField();

		purposeText = new JTextArea(3, 2);
		JLabel amountLabel = new JLabel("Betrag");
		amountText = new JTextField();
		JLabel currencyLabel = new JLabel("Währung");
		currencyText = new JTextField();

		JLabel bookingTypeLabel = new JLabel("Buchung Typ");
		setBookingTypeItems();
		bookingTypeCombo = new JComboBox<>(BookingType.values());

		JLabel bookingSourceLabel = new JLabel("Quelle");
		setBookingSourceItems();
		bookingSourceCombo = new JComboBox<>(Source.values());

		JLabel crossAccountLabel = new JLabel("Gegenkonto");
		setBankAccountItems();
		crossAccountCombo = new JComboBox<>(bankAccountItemList);
		JLabel categoryLabel = new JLabel("Kategorie");
		categoryText = new JTextField();
		
		JLabel sepaCustomerRefLabel = new JLabel("Customer-Ref.");
		sepaCustomerRefText = new JTextField();
		JLabel sepaCreditorIdLabel = new JLabel("Creditor-ID");
		sepaCreditorIdText = new JTextField();
		JLabel sepaEndToEndLabel = new JLabel("EndTEnd-Ref.");
		sepaEndToEndText = new JTextField();
		JLabel sepaMandateLabel = new JLabel("Mandats-Ref.");
		sepaMandateText = new JTextField();
		JLabel sepaPersonIdLabel = new JLabel("Person-ID");
		sepaPersonIdText = new JTextField();
		JLabel sepaPurposeLabel = new JLabel("Purpose");
		sepaPurposeText = new JTextField();
		JLabel sepaTypLabel = new JLabel("Typ");
		sepaTypText = new JTextField();

		JLabel recipientNameLabel = new JLabel("Name");
		recipientNameText = new JTextField();
		JLabel recipientIbanLabel = new JLabel("IBAN / Konto-Nr.");
		recipientIbanText = new JTextField();
		recipientAccountNumberText = new JTextField();
		JLabel recipientBicLabel = new JLabel("BIC / BLZ");
		recipientBicText = new JTextField();
		recipientBlzText = new JTextField();
		JLabel recipientBankLabel = new JLabel("Bank");
		recipientBankText = new JTextField();

		JLabel updatedAtLabel = new JLabel("Stand");
		updatedAtText = new JTextField();

		GridBagLayout detailsMainLayout = new GridBagLayout();
		setLayout(detailsMainLayout);

		GridBagConstraints gbcDetailMain = new GridBagConstraints();
		gbcDetailMain.insets = new Insets(2, 2, 2, 2);
		gbcDetailMain.anchor = GridBagConstraints.NORTHWEST;
		gbcDetailMain.fill = GridBagConstraints.HORIZONTAL;
		gbcDetailMain.gridx = 0;
		gbcDetailMain.gridy = 0;
		gbcDetailMain.gridheight = 2;
		gbcDetailMain.weightx = 0.7;

		JPanel detailsFieldsPanel = new JPanel();
		GridBagLayout detailsPanelLayout = new GridBagLayout();
		detailsFieldsPanel.setLayout(detailsPanelLayout);
		GridBagConstraints gbcDetailFields = new GridBagConstraints();
		gbcDetailFields.insets = new Insets(2, 2, 2, 2);
		gbcDetailFields.weightx = 1;
		gbcDetailFields.anchor = GridBagConstraints.NORTHWEST;
		gbcDetailFields.fill = GridBagConstraints.HORIZONTAL;

		gbcDetailFields.gridx = 0;
		gbcDetailFields.gridy = 0;
		detailsFieldsPanel.add(dateBookingLabel, gbcDetailFields);
		gbcDetailFields.gridx = 1;
		detailsFieldsPanel.add(dateBookingText, gbcDetailFields);
		gbcDetailFields.gridx = 2;
		detailsFieldsPanel.add(dateValueLabel, gbcDetailFields);
		gbcDetailFields.gridx = 3;
		detailsFieldsPanel.add(dateValueText, gbcDetailFields);
		gbcDetailFields.gridx = 0;
		gbcDetailFields.gridy = 2;
		detailsFieldsPanel.add(amountLabel, gbcDetailFields);
		gbcDetailFields.gridx = 1;
		detailsFieldsPanel.add(amountText, gbcDetailFields);
		gbcDetailFields.gridx = 2;
		detailsFieldsPanel.add(currencyLabel, gbcDetailFields);
		gbcDetailFields.gridx = 3;
		detailsFieldsPanel.add(currencyText, gbcDetailFields);
		gbcDetailFields.gridx = 0;
		gbcDetailFields.gridy = 3;
		detailsFieldsPanel.add(bookingTypeLabel, gbcDetailFields);
		gbcDetailFields.gridx = 1;
		detailsFieldsPanel.add(bookingTypeCombo, gbcDetailFields);
		gbcDetailFields.gridx = 2;
		detailsFieldsPanel.add(bookingSourceLabel, gbcDetailFields);
		gbcDetailFields.gridx = 3;
		detailsFieldsPanel.add(bookingSourceCombo, gbcDetailFields);
		gbcDetailFields.gridx = 0;
		gbcDetailFields.gridy = 4;
		detailsFieldsPanel.add(crossAccountLabel, gbcDetailFields);
		gbcDetailFields.gridx = 1;
		detailsFieldsPanel.add(crossAccountCombo, gbcDetailFields);
		gbcDetailFields.gridx = 2;
		detailsFieldsPanel.add(categoryLabel, gbcDetailFields);
		gbcDetailFields.gridx = 3;
		detailsFieldsPanel.add(categoryText, gbcDetailFields);
		gbcDetailFields.gridx = 0;
		gbcDetailFields.gridy = 5;
		gbcDetailFields.gridwidth = 5;
		
		JPanel sepaInfoPanel = new JPanel();
		Border sepaInfoPanelBorder = BorderFactory.createTitledBorder("SEPA Informationen");
		sepaInfoPanel.setBorder(sepaInfoPanelBorder);
		GridBagLayout sepaInfoPanelLayout = new GridBagLayout();
		sepaInfoPanel.setLayout(sepaInfoPanelLayout);
		GridBagConstraints gbcSepaInfo = new GridBagConstraints();
		gbcSepaInfo.insets = new Insets(2, 2, 2, 2);
		gbcSepaInfo.anchor = GridBagConstraints.NORTHWEST;
		gbcSepaInfo.weightx = 1;
		gbcSepaInfo.fill = GridBagConstraints.HORIZONTAL;
		gbcSepaInfo.gridx = 0;
		gbcSepaInfo.gridy = 0;
		sepaInfoPanel.add(sepaCustomerRefLabel, gbcSepaInfo);
		gbcSepaInfo.gridx = 1;
		sepaInfoPanel.add(sepaCustomerRefText, gbcSepaInfo);
		gbcSepaInfo.gridx = 2;
		sepaInfoPanel.add(sepaCreditorIdLabel, gbcSepaInfo);
		gbcSepaInfo.gridx = 3;
		sepaInfoPanel.add(sepaCreditorIdText, gbcSepaInfo);
		gbcSepaInfo.gridx = 4;
		sepaInfoPanel.add(sepaEndToEndLabel, gbcSepaInfo);
		gbcSepaInfo.gridx = 5;
		sepaInfoPanel.add(sepaEndToEndText, gbcSepaInfo);
		gbcSepaInfo.gridx = 0;
		gbcSepaInfo.gridy = 1;
		sepaInfoPanel.add(sepaMandateLabel, gbcSepaInfo);
		gbcSepaInfo.gridx = 1;
		sepaInfoPanel.add(sepaMandateText, gbcSepaInfo);
		gbcSepaInfo.gridx = 2;
		sepaInfoPanel.add(sepaPersonIdLabel, gbcSepaInfo);
		gbcSepaInfo.gridx = 3;
		sepaInfoPanel.add(sepaPersonIdText, gbcSepaInfo);
		gbcSepaInfo.gridx = 4;
		sepaInfoPanel.add(sepaPurposeLabel, gbcSepaInfo);
		gbcSepaInfo.gridx = 5;
		sepaInfoPanel.add(sepaPurposeText, gbcSepaInfo);
		gbcSepaInfo.gridx = 0;
		gbcSepaInfo.gridy = 2;
		sepaInfoPanel.add(sepaTypLabel, gbcSepaInfo);
		gbcSepaInfo.gridx = 1;
		sepaInfoPanel.add(sepaTypText, gbcSepaInfo);
		
		detailsFieldsPanel.add(sepaInfoPanel, gbcDetailFields);
		
		gbcDetailFields.gridwidth = 1;
		gbcDetailFields.gridx = 2;
		gbcDetailFields.gridy = 7;
		detailsFieldsPanel.add(updatedAtLabel, gbcDetailFields);
		gbcDetailFields.gridx = 3;
		detailsFieldsPanel.add(updatedAtText, gbcDetailFields);

		add(detailsFieldsPanel, gbcDetailMain);

		JPanel recipientFieldsPanel = new JPanel();
		Border recipientFieldsPanelBorder = BorderFactory.createTitledBorder("Empfänger/Zahler");
		recipientFieldsPanel.setBorder(recipientFieldsPanelBorder);
		GridBagLayout recipientPanelLayout = new GridBagLayout();
		recipientFieldsPanel.setLayout(recipientPanelLayout);
		GridBagConstraints gbcRecipientFields = new GridBagConstraints();
		gbcRecipientFields.insets = new Insets(2, 2, 2, 2);
		gbcRecipientFields.anchor = GridBagConstraints.NORTHWEST;
		gbcRecipientFields.weightx = 1;
		gbcRecipientFields.fill = GridBagConstraints.HORIZONTAL;

		gbcRecipientFields.gridx = 0;
		gbcRecipientFields.gridy = 0;
		recipientFieldsPanel.add(recipientNameLabel, gbcRecipientFields);
		gbcRecipientFields.gridx = 1;
		gbcRecipientFields.gridwidth = 2;
		recipientFieldsPanel.add(recipientNameText, gbcRecipientFields);
		gbcRecipientFields.gridx = 0;
		gbcRecipientFields.gridy = 1;
		gbcRecipientFields.gridwidth = 1;
		recipientFieldsPanel.add(recipientIbanLabel, gbcRecipientFields);
		gbcRecipientFields.gridx = 1;
		recipientFieldsPanel.add(recipientIbanText, gbcRecipientFields);
		gbcRecipientFields.gridx = 2;
		recipientFieldsPanel.add(recipientAccountNumberText, gbcRecipientFields);
		gbcRecipientFields.gridx = 0;
		gbcRecipientFields.gridy = 2;
		recipientFieldsPanel.add(recipientBicLabel, gbcRecipientFields);
		gbcRecipientFields.gridx = 1;
		recipientFieldsPanel.add(recipientBicText, gbcRecipientFields);
		gbcRecipientFields.gridx = 2;
		recipientFieldsPanel.add(recipientBlzText, gbcRecipientFields);
		gbcRecipientFields.gridx = 0;
		gbcRecipientFields.gridy = 3;
		recipientFieldsPanel.add(recipientBankLabel, gbcRecipientFields);
		gbcRecipientFields.gridx = 1;
		gbcRecipientFields.gridwidth = 2;
		recipientFieldsPanel.add(recipientBankText, gbcRecipientFields);

		gbcDetailMain.gridx = 1;
		gbcDetailMain.gridheight = 1;
		gbcDetailMain.weightx = 0.3;

		add(recipientFieldsPanel, gbcDetailMain);

		gbcDetailMain.gridx = 0;
		gbcDetailMain.gridy = 5;

		JPanel purposeFieldPanel = new JPanel();
		Border purposeFieldPanelBorder = BorderFactory.createTitledBorder("Verwendungszweck");
		purposeFieldPanel.setBorder(purposeFieldPanelBorder);
		purposeFieldPanel.setLayout(new GridBagLayout());
		GridBagConstraints gbcPurpose = new GridBagConstraints();
		gbcPurpose.weightx = 1;
		gbcPurpose.fill = GridBagConstraints.BOTH;
		purposeText.setMaximumSize(new Dimension(250, 50));
		purposeText.setLineWrap(true);
		purposeText.setWrapStyleWord(true);
		purposeFieldPanel.add(purposeText, gbcPurpose);
		add(purposeFieldPanel, gbcDetailMain);

		gbcDetailMain.gridx = 1;

		JPanel buttonsPanel = new JPanel();
		buttonsPanel.setLayout(new GridBagLayout());
		GridBagConstraints gbcButtons = new GridBagConstraints();
		gbcDetailMain.anchor = GridBagConstraints.SOUTH;
		gbcButtons.weightx = 1;
		gbcButtons.fill = GridBagConstraints.BOTH;
		gbcButtons.gridx = 0;
		gbcButtons.gridy = 0;
		newButton = new JButton("neu..");
		editButton = new JButton("bearbeiten");
		saveButton = new JButton("speichern");
		buttonsPanel.add(newButton, gbcButtons);
		gbcButtons.gridx = 1;
		buttonsPanel.add(editButton, gbcButtons);
		gbcButtons.gridx = 2;
		buttonsPanel.add(saveButton, gbcButtons);
		add(buttonsPanel, gbcDetailMain);

		enableTextFields(false);

		newButton.addActionListener(e -> performNew());
		editButton.addActionListener(e -> performEdit());
		saveButton.addActionListener(e -> performSave());

	}

	private void setBankAccountItems() {
		if (bankAccountItemList == null) {
			bankAccountItemList = new DefaultComboBoxModel<>();
		}
		for (BankAccount account : dbController.getAll(BankAccount.class)) {
			bankAccountItemList.addElement(account);
		}
	}

	private BankAccount getSelectedAccountItem(int accountId) {
		for (int i = 0; i < bankAccountItemList.getSize(); i++) {
			BankAccount account = bankAccountItemList.getElementAt(i);
			if (account.getId() == accountId) {
				return account;
			}
		}
		return null;
	}

	private void setBookingTypeItems() {
		if (bookingTypeItemList == null) {
			bookingTypeItemList = new DefaultComboBoxModel<>();
		}
		for (BookingType bookingType : BookingType.values()) {
			bookingTypeItemList.addElement(bookingType);
		}
	}

	private void setBookingSourceItems() {
		if (bookingSourceItemList == null) {
			bookingSourceItemList = new DefaultComboBoxModel<>();
		}
		for (Source source : Source.values()) {
			bookingSourceItemList.addElement(source);
		}
	}

	private void enableTextFields(boolean enable) {
		for (Component p : this.getComponents()) {
			if (p instanceof JPanel) {
				for (Component c : ((Container) p).getComponents()) {
					if (c instanceof JTextField || c instanceof JTextArea || c instanceof JComboBox) {
						c.setEnabled(enable);
					}
				}
			}
		}
	}

	private void performNew() {
		context = EditContext.NEW;
		editButton.setText("abbrechen");
		editButton.setEnabled(true);
		newButton.setEnabled(false);
		displayedBooking = new Booking();

		enableTextFields(true);

		displayedBooking.setSource(Source.MANUELL);
		getBookingSourceCombo().setEnabled(false);

		dateBookingText.setText(null);
		dateValueText.setText(null);
		purposeText.setText(null);
		amountText.setText(null);
		currencyText.setText(null);
		bookingTypeCombo.setSelectedItem(null);
		bookingSourceCombo.setSelectedItem(Source.MANUELL);
		crossAccountCombo.setSelectedItem(null);

		categoryText.setText(null);

		recipientNameText.setText(null);
		recipientIbanText.setText(null);
		recipientBicText.setText(null);
		recipientBankText.setText(null);
	}

	private void performEdit() {
		if (context == EditContext.EDIT || context == EditContext.NEW) {
			editButton.setText("bearbeiten");
			newButton.setEnabled(true);
			enableTextFields(false);
			context = EditContext.READONLY;
		} else {
			context = EditContext.EDIT;
			editButton.setText("abbrechen");

			enableTextFields(true);
			newButton.setEnabled(false);
		}
	}

	private void performSave() {

		context = EditContext.READONLY;

		if ((bookingTypeCombo.getSelectedItem() == BookingType.REBOOKING_OUT
				|| bookingTypeCombo.getSelectedItem() == BookingType.REBOOKING_IN) && crossAccountCombo.getSelectedItem() == null) {
			JOptionPane.showMessageDialog(parentPanel, getText("ALERT_REBOOKING_CROSS_ACCOUNT_MISSING"));
			return;
		}

		updateBookingFroumUI();

		dbController.insertOrUpdate(displayedBooking);

		enableTextFields(false);

		editButton.setEnabled(true);
		newButton.setEnabled(true);
	}

	private void updateBookingFroumUI() {

		displayedBooking.setDateBooking(TypeConverter.toCalendarFromDateStrShort(formatInput(dateBookingText.getText())));
		displayedBooking.setDateValue(TypeConverter.toCalendarFromDateStrShort(formatInput(dateValueText.getText())));
		displayedBooking.setPurpose(formatInput(purposeText.getText()));
		displayedBooking.setAmount(new BigDecimal(formatInput(amountText.getText())));
		displayedBooking.setCurrency(formatInput(currencyText.getText()));
		displayedBooking.setBookingType((BookingType) bookingTypeCombo.getSelectedItem());
		displayedBooking.setSource((Source) bookingSourceCombo.getSelectedItem());
		displayedBooking.setCrossAccountId(((BankAccount) crossAccountCombo.getSelectedItem()).getId());

		de.gbanking.db.dao.Category category = displayedBooking.getCategory();
		if (category == null) {
			category = new de.gbanking.db.dao.Category(formatInput(categoryText.getText()));
		} else {
			category.setName(formatInput(categoryText.getText()));
		}

		displayedBooking.setCategory(category);

		Recipient recipient = displayedBooking.getRecipient();
		if (recipient == null) {
			recipient = new Recipient();
		}

		recipient.setName(formatInput(recipientNameText.getText()));
		recipient.setIban(formatInput(recipientIbanText.getText()));
		recipient.setBic(formatInput(recipientBicText.getText()));
		recipient.setBank(formatInput(recipientBankText.getText()));

		displayedBooking.setUpdatedAt(Calendar.getInstance());
	}

	private boolean isBookingEditable() {
		return !(displayedBooking.getSource() == Source.ONLINE || displayedBooking.getSource() == Source.ONLINE_PRENO);
	}

	private String formatInput(String input) {
		if (input != null) {
			input = input.trim();
		}

		return input;
	}

	public void updatePanelFieldValues(Booking booking) {

		this.displayedBooking = booking;

		getDateBookingText().setText(TypeConverter.toDateStringShort(booking.getDateBooking()));
		getDateValueText().setText(TypeConverter.toDateStringShort(booking.getDateValue()));
		getPurposeText().setText(booking.getPurpose());
		getAmountText().setText(booking.getAmountStr());
		getCurrencyText().setText(booking.getCurrency());
		getBookingTypeCombo().setSelectedItem(booking.getBookingType() != null ? booking.getBookingType() : null);
		getBookingSourceCombo().setSelectedItem(booking.getSource());
		getCrossAccountCombo().setSelectedItem(getSelectedAccountItem(booking.getCrossAccountId()));
		getCategoryText().setText(booking.getCategory() != null ? booking.getCategory().toString() : null);
		Recipient recipient = booking.getRecipient();
		if (recipient != null) {
			getRecipientNameText().setText(recipient.getName());
			getRecipientIbanText().setText(recipient.getIban());
			getRecipientAccountNumberText().setText(recipient.getAccountNumber());
			getRecipientBicText().setText(recipient.getBic());
			getRecipientBlzText().setText(recipient.getBlz());
			getRecipientBankText().setText(recipient.getBank());
		}
		
		getSepaCustomerRefText().setText(booking.getSepaCustomerRef());
		getSepaCreditorIdText().setText(booking.getSepaCreditorId());
		getSepaEndToEndText().setText(booking.getSepaEndToEnd());
		getSepaMandateText().setText(booking.getSepaMandate());
		getSepaPersonIdText().setText(booking.getSepaPersonId());
		getSepaPurposeText().setText(booking.getSepaPurpose());
		getSepaTypText().setText(booking.getSepaTyp() != null ? booking.getSepaTyp().toString() : null);

		getUpdatedAtText().setText(TypeConverter.toDateStringLong(booking.getUpdatedAt()));

		editButton.setEnabled(isBookingEditable());
	}

}
