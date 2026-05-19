package info.openrocket.swing.gui.util;

import java.awt.Window;
import java.io.File;

import javax.swing.JOptionPane;

import info.openrocket.core.l10n.L10N;
import info.openrocket.core.l10n.Translator;
import info.openrocket.core.startup.Application;

/**
 * Utility that manages overwrite confirmation, including an "overwrite all"
 * option that suppresses repeated prompts within the same export session.
 */
public class OverwritePrompter {

	public enum Choice {
		OVERWRITE,
		OVERWRITE_ALL,
		CANCEL
	}

	@FunctionalInterface
	public interface Prompt {
		Choice request(Window parent, File target);
	}

	private static final Translator trans = Application.getTranslator();

	private final Prompt prompt;
	private boolean overwriteAll;

	public OverwritePrompter(Prompt prompt) {
		this.prompt = prompt != null ? prompt : defaultPrompt();
		this.overwriteAll = false;
	}

	public static Prompt defaultPrompt() {
		return (parent, target) -> {
			String message = L10N.replace(trans.get("error.fileExists.desc"), "{filename}", target.getName());
			Object[] options = new Object[] {
					trans.get("FileChooser.overwrite"),
					trans.get("FileChooser.overwriteAll"),
					trans.get("dlg.but.cancel")
			};
			int result = JOptionPane.showOptionDialog(parent, message, trans.get("error.fileExists.title"),
					JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[0]);
			if (result == 0) {
				return Choice.OVERWRITE;
			} else if (result == 1) {
				return Choice.OVERWRITE_ALL;
			}
			return Choice.CANCEL;
		};
	}

	/**
	 * Returns {@code true} if writing to {@code target} may proceed
	 * (either the file does not exist, overwrite-all was previously chosen, or the
	 * user agrees to overwrite). A {@code false} return means the caller should
	 * abort the entire export.
	 */
	public boolean canOverwrite(Window parent, File target) {
		if (target == null || !target.exists()) {
			return true;
		}
		if (overwriteAll) {
			return true;
		}
		Choice choice = prompt.request(parent, target);
		if (choice == Choice.OVERWRITE_ALL) {
			overwriteAll = true;
			return true;
		}
		return choice == Choice.OVERWRITE;
	}

	public void reset() {
		this.overwriteAll = false;
	}
}
