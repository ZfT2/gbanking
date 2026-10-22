package de.zft2.gbanking.gui.dialog.stock;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import de.zft2.gbanking.BaseMessages;
import de.zft2.gbanking.gui.dialog.DialogWindowSupport;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Base class of the stock dialogs.
 *
 * <p>
 * The stock dialogs share the same structure: a modal stage with a header, the content of the
 * dialog and a button bar. The action button applies the entered values and closes the dialog,
 * the cancel button closes the dialog without any change. The base class provides this common
 * part and the handling of the runtime exceptions of the actions.
 * </p>
 */
public abstract class AbstractStockDialog implements BaseMessages {

	private final Stage dialog;

	/**
	 * Creates the modal stage of the dialog.
	 *
	 * @param owner   the owner window of the dialog
	 * @param titleKey the key of the title of the dialog
	 */
	protected AbstractStockDialog(Window owner, String titleKey) {
		dialog = DialogWindowSupport.createModalStage(owner, titleKey);
	}

	/**
	 * Shows the dialog and waits until it is closed.
	 */
	public void show() {
		dialog.showAndWait();
	}

	/**
	 * Returns the stage of the dialog.
	 *
	 * @return the stage of the dialog
	 */
	protected Stage dialog() {
		return dialog;
	}

	/**
	 * Creates the button that applies the entered values.
	 *
	 * @param textKey the key of the button text
	 * @param action  the action executed when the button is pressed
	 * @return the created button
	 */
	protected Button createActionButton(String textKey, Runnable action) {
		Button button = createButton(textKey, action);
		button.setDefaultButton(true);
		return button;
	}

	/**
	 * Creates the button that closes the dialog without applying the entered values.
	 *
	 * @return the created button
	 */
	protected Button createCancelButton() {
		return createCloseButton("UI_BUTTON_CANCEL");
	}

	/**
	 * Creates the button that closes the dialog without applying the entered values.
	 *
	 * @param textKey the key of the button text
	 * @return the created button
	 */
	protected Button createCloseButton(String textKey) {
		Button button = createButton(textKey, () -> dialog.close());
		button.setCancelButton(true);
		return button;
	}

	/**
	 * Creates a button with the given text.
	 *
	 * @param textKey the key of the button text
	 * @param action  the action executed when the button is pressed
	 * @return the created button
	 */
	protected Button createButton(String textKey, Runnable action) {
		Button button = new Button(getText(textKey));
		button.setOnAction(event -> action.run());
		return button;
	}

	/**
	 * Creates the scene of the dialog with a header, the given content and the given buttons.
	 *
	 * @param headerText   the text of the header
	 * @param content      the content of the dialog
	 * @param actionButton the button that applies the entered values
	 * @param cancelButton the button that closes the dialog
	 * @param width        the width of the dialog
	 * @param height       the height of the dialog
	 */
	protected void buildScene(String headerText, Node content, Button actionButton, Button cancelButton, int width,
			int height) {
		buildScene(headerText, List.of(content), List.of(actionButton, cancelButton), width, height);
	}

	/**
	 * Creates the scene of the dialog with a header, the given content and the given buttons.
	 *
	 * @param headerText the text of the header
	 * @param content    the content of the dialog
	 * @param buttons    the buttons of the dialog
	 * @param width      the width of the dialog
	 * @param height     the height of the dialog
	 */
	protected void buildScene(String headerText, List<Node> content, List<Button> buttons, int width, int height) {
		Label header = new Label(headerText);
		header.getStyleClass().add("gbanking-form-section-title");
		List<Node> nodes = new ArrayList<>();
		nodes.add(header);
		nodes.addAll(content);
		nodes.add(DialogWindowSupport.createButtonBar(buttons.toArray(new Button[0])));
		VBox root = DialogWindowSupport.createDialogRoot(nodes.toArray(new Node[0]));
		dialog.setScene(DialogWindowSupport.createScene(root, width, height));
	}

	/**
	 * Executes the given action and shows runtime exceptions as error message.
	 *
	 * @param action the action to execute
	 */
	protected void runAction(Runnable action) {
		try {
			action.run();
		} catch (RuntimeException exception) {
			StockDialogSupport.showError(dialog, exception);
		}
	}

	/**
	 * Executes the given action and closes the dialog if the action was applied. Runtime exceptions
	 * are shown as error message, the dialog stays open in this case.
	 *
	 * @param action the action that applies the entered values, it returns false if the dialog has
	 *               to stay open
	 */
	protected void applyAndClose(BooleanSupplier action) {
		runAction(() -> {
			if (action.getAsBoolean()) {
				dialog.close();
			}
		});
	}
}
