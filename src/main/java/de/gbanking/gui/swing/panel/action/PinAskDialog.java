package de.gbanking.gui.swing.panel.action;

import java.awt.Container;
import java.awt.Dialog;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.border.Border;

public class PinAskDialog {

	JFrame parentFrame;
	
	private JDialog modelDialog;
	
	JButton okButton;
	JButton cancelButton;
	
	ActionListener actionListenerStep1;

	private char[] pin;
	
	public PinAskDialog(JFrame parent) {
		this.parentFrame = parent;
	}
	
	public JDialog createNewPinAskDialog() {
		modelDialog = new JDialog(parentFrame, "PIN", Dialog.ModalityType.DOCUMENT_MODAL);
		modelDialog.setBounds(132, 132, 300, 200);
		
		createPinAskPanel();

		return modelDialog;
	}
	
	private void createPinAskPanel() {
		
		Container dialogContainer = modelDialog.getContentPane();
		
		GridBagLayout dialogPanelLayout = new GridBagLayout();
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(2, 2, 2, 2);

		JPanel panel1 = new JPanel();
		panel1.setLayout(dialogPanelLayout);
		Border panel1Border = BorderFactory.createTitledBorder("PIN Eingabe"); 
		panel1.setBorder(panel1Border);
		
		JLabel pinLabel = new JLabel("PIN");
		JPasswordField pinText = new JPasswordField("");
		
		gbc.anchor = GridBagConstraints.NORTHWEST;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.weightx = 1;
		gbc.gridx = 0;
		gbc.gridy = 0;
		panel1.add(pinLabel, gbc);
		gbc.gridx = 1;
		panel1.add(pinText, gbc);
				
		okButton = new JButton("OK");
		actionListenerStep1 = new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				pin = pinText.getPassword();
				modelDialog.setVisible(false);
			}
		};
		okButton.addActionListener(actionListenerStep1);
		
		cancelButton = new JButton("Abbrechen");
		cancelButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				pin = null;
				modelDialog.setVisible(false);
			}
		});

		gbc.gridx = 0;
		gbc.gridy = 4;
		panel1.add(okButton, gbc);
		gbc.gridx = 1;
		panel1.add(cancelButton, gbc);
		dialogContainer.add(panel1);
	}

	public char[] getPin() {
		return pin;
	}

}
