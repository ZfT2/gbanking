package de.zft2.gbanking.gui.util;

import de.zft2.gbanking.gui.util.FormStyleUtils.FieldWidth;
import javafx.collections.ObservableList;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

public final class FormFields {

	private FormFields() {
	}

	public static TextField text(FieldWidth width) {
		return FormStyleUtils.applyWidth(new TextField(), width);
	}

	public static TextField textXs() {
		return text(FieldWidth.XS);
	}

	public static TextField textS() {
		return text(FieldWidth.S);
	}

	public static TextField textM() {
		return text(FieldWidth.M);
	}

	public static TextField textL() {
		return text(FieldWidth.L);
	}

	public static TextArea textAreaLarge() {
		return FormStyleUtils.prepareLargeTextArea(new TextArea(), 3);
	}

	public static TextArea textAreaLarge(int rowCount) {
		return FormStyleUtils.prepareLargeTextArea(new TextArea(), rowCount);
	}

	public static CheckBox checkBox() {
		return new CheckBox();
	}

	public static <T> ComboBox<T> combo(FieldWidth width) {
		return FormStyleUtils.applyWidth(new ComboBox<>(), width);
	}

	public static <T> ComboBox<T> combo(FieldWidth width, ObservableList<T> items) {
		ComboBox<T> comboBox = combo(width);
		comboBox.setItems(items);
		return comboBox;
	}
	public static <T> ComboBox<T> comboXs() {
		return combo(FieldWidth.XS);
	}

	public static <T> ComboBox<T> comboS() {
		return combo(FieldWidth.S);
	}

	public static <T> ComboBox<T> comboM() {
		return combo(FieldWidth.M);
	}

	public static <T> ComboBox<T> comboL() {
		return combo(FieldWidth.L);
	}

	public static <T> ComboBox<T> comboXs(ObservableList<T> items) {
		return combo(FieldWidth.XS, items);
	}

	public static <T> ComboBox<T> comboS(ObservableList<T> items) {
		return combo(FieldWidth.S, items);
	}

	public static <T> ComboBox<T> comboM(ObservableList<T> items) {
		return combo(FieldWidth.M, items);
	}

	public static <T> ComboBox<T> comboL(ObservableList<T> items) {
		return combo(FieldWidth.L, items);
	}
}