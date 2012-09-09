/**
 *
 */
package org.connectbot.keyboard;

import org.connectbot.service.TerminalKeyListener;

import android.view.KeyEvent;
import android.view.View;

/**
 * @author James
 *
 */
public interface IKeyboard {
	/**
	 * Receives a key press and sends it to the SSH session.
	 * @param listener The terminal key listener than owns this keyboard.
	 * @param v The view the key press occurred in.
	 * @param keyCode The key code that was pressed.
	 * @param event The key event information.
	 * @return Whether the key press was handled.
	 */
	public boolean onKey(TerminalKeyListener listener, View v, int keyCode, KeyEvent event);
}
