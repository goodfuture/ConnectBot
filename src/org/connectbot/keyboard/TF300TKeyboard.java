/**
 *
 */
package org.connectbot.keyboard;

import java.io.IOException;

import org.connectbot.service.TerminalKeyListener;

import android.annotation.TargetApi;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import de.mud.terminal.vt320;

/**
 * @author James
 *
 */
public class TF300TKeyboard implements IKeyboard {

	private Boolean m_DeleteWithVolumeUp = false;

	// To prevent the CTRL/SHIFT/ALT status modifiers appearing
	// on screen (which can get confusing since we ensure they are
	// correct in the logic anyway), we store our state independently
	// and then restore it / save it on each onKey call.
	private int m_MetaState = 0;

	private void restoreState(TerminalKeyListener listener) {
		listener.metaState = m_MetaState;
	}
	private void saveState(TerminalKeyListener listener) {
		m_MetaState = listener.metaState;
		listener.metaState &= ~(TerminalKeyListener.META_SHIFT_ON |
				TerminalKeyListener.META_ALT_ON |
				TerminalKeyListener.META_CTRL_ON);
	}

	/* (non-Javadoc)
	 * @see org.connectbot.keyboard.IKeyboard#onKey(org.connectbot.service.TerminalKeyListener, android.view.View, int, android.view.KeyEvent)
	 */
	@TargetApi(11)
	public boolean onKey(TerminalKeyListener listener, View v, int keyCode,
			KeyEvent event) {
		try {
			this.restoreState(listener);

			// We only care about key down events.
			if (event.getAction() == KeyEvent.ACTION_UP)
			{
				switch (keyCode)
				{
					case KeyEvent.KEYCODE_SHIFT_LEFT:
					case KeyEvent.KEYCODE_SHIFT_RIGHT:
						listener.metaState &= ~(TerminalKeyListener.META_SHIFT_ON);
					case KeyEvent.KEYCODE_ALT_LEFT:
					case KeyEvent.KEYCODE_ALT_RIGHT:
						listener.metaState &= ~(TerminalKeyListener.META_ALT_ON);
					case KeyEvent.KEYCODE_CTRL_LEFT:
					case KeyEvent.KEYCODE_CTRL_RIGHT:
						listener.metaState &= ~(TerminalKeyListener.META_CTRL_ON);
				}
				this.saveState(listener);
				return true;
			}

			// Toggle states.
			if (event.isCtrlPressed())
				listener.metaState |= TerminalKeyListener.META_CTRL_ON;
			if (event.isAltPressed())
				listener.metaState |= TerminalKeyListener.META_ALT_ON;
			if (event.isShiftPressed())
				listener.metaState |= TerminalKeyListener.META_SHIFT_ON;

			// If the key press has CTRL or ALT, then there is no printable
			// key.  However, for the purposes of sending data, we actually
			// want to send the printable key since the CTRL / ALT status
			// is held as meta-data.
			int key = event.getUnicodeChar();
			if ((listener.metaState & TerminalKeyListener.META_CTRL_ON) != 0)
				key = new KeyEvent(KeyEvent.ACTION_DOWN, keyCode).getUnicodeChar();

			// Send key data.
			final boolean printing = (key != 0);
			if (printing && keyCode != KeyEvent.KEYCODE_ENTER)
			{
				if ((listener.metaState & TerminalKeyListener.META_CTRL_ON) != 0)
					key = listener.keyAsControl(key);
				if (key < 0x80)
					listener.getBridge().transport.write(key);
				else
					// TODO write encoding routine that doesn't allocate each time
					listener.getBridge().transport.write(new String(Character.toChars(key))
							.getBytes(listener.getEncoding()));
			}
			else
			{
				// Handle special characters.
				switch(keyCode) {
					case KeyEvent.KEYCODE_BACK:
						// Check to see whether this is the back button on the
						// screen (-1) or whether it's the back button on the
						// keyboard.  We only want to treat the keyboard back
						// as ESC.
						if (event.getDeviceId() == -1)
						{
							// Simulate the back key press.
							this.saveState(listener);
							listener.finish();
							return true;
						}
						listener.sendEscape();
						break;
					case KeyEvent.KEYCODE_TAB:
						listener.getBridge().transport.write(0x09);
						break;
					case KeyEvent.KEYCODE_VOLUME_UP:
						if (m_DeleteWithVolumeUp)
							((vt320) listener.getBuffer()).keyPressed(vt320.KEY_DELETE, ' ',
									listener.getStateForBuffer());
						else
						{
							// Pretend we didn't handle it so that it still does volume up.
							this.saveState(listener);
							return false;
						}
						break;
					case KeyEvent.KEYCODE_DEL:
						if ((listener.metaState & TerminalKeyListener.META_CTRL_ON) != 0)
						{
							m_DeleteWithVolumeUp = !m_DeleteWithVolumeUp;
							if (m_DeleteWithVolumeUp)
								listener.notifyUser("Volume up is now the delete key.");
							else
								listener.notifyUser("Shift-Backspace is now the delete key.");
						}
						else if ((listener.metaState & TerminalKeyListener.META_SHIFT_ON) != 0 && !m_DeleteWithVolumeUp)
							((vt320) listener.getBuffer()).keyPressed(vt320.KEY_DELETE, ' ',
									listener.getStateForBuffer());
						else if ((listener.metaState & TerminalKeyListener.META_SHIFT_ON) != 0 && m_DeleteWithVolumeUp)
							((vt320) listener.getBuffer()).keyPressed(vt320.KEY_BACK_SPACE, ' ',
									0 /* prevent treatment as delete */);
						else
							((vt320) listener.getBuffer()).keyPressed(vt320.KEY_BACK_SPACE, ' ',
									listener.getStateForBuffer());
						break;
					case KeyEvent.KEYCODE_ENTER:
						((vt320)listener.getBuffer()).keyTyped(vt320.KEY_ENTER, ' ',
								listener.getStateForBuffer());
						break;
					case KeyEvent.KEYCODE_DPAD_LEFT:
						((vt320) listener.getBuffer()).keyPressed(vt320.KEY_LEFT, ' ',
								listener.getStateForBuffer());
						break;
					case KeyEvent.KEYCODE_DPAD_UP:
						((vt320) listener.getBuffer()).keyPressed(vt320.KEY_UP, ' ',
								listener.getStateForBuffer());
						break;
					case KeyEvent.KEYCODE_DPAD_DOWN:
						((vt320) listener.getBuffer()).keyPressed(vt320.KEY_DOWN, ' ',
								listener.getStateForBuffer());
						break;
					case KeyEvent.KEYCODE_DPAD_RIGHT:
						((vt320) listener.getBuffer()).keyPressed(vt320.KEY_RIGHT, ' ',
								listener.getStateForBuffer());
						break;
					case KeyEvent.KEYCODE_MOVE_HOME:
						((vt320) listener.getBuffer()).keyPressed(vt320.KEY_HOME, ' ',
								listener.getStateForBuffer());
						break;
					case KeyEvent.KEYCODE_PAGE_UP:
						((vt320) listener.getBuffer()).keyPressed(vt320.KEY_PAGE_UP, ' ',
								listener.getStateForBuffer());
						break;
					case KeyEvent.KEYCODE_PAGE_DOWN:
						((vt320) listener.getBuffer()).keyPressed(vt320.KEY_PAGE_DOWN, ' ',
								listener.getStateForBuffer());
						break;
					case KeyEvent.KEYCODE_MOVE_END:
						((vt320) listener.getBuffer()).keyPressed(vt320.KEY_END, ' ',
								listener.getStateForBuffer());
						break;
					case KeyEvent.KEYCODE_SHIFT_LEFT:
					case KeyEvent.KEYCODE_SHIFT_RIGHT:
					case KeyEvent.KEYCODE_ALT_LEFT:
					case KeyEvent.KEYCODE_ALT_RIGHT:
					case KeyEvent.KEYCODE_CTRL_LEFT:
					case KeyEvent.KEYCODE_CTRL_RIGHT:
						// These are handled above.
						break;
					default:
						Log.d(TerminalKeyListener.TAG, "UNHANDLED KEY CODE:" + keyCode);
						this.saveState(listener);
						return false;
				}
			}

			this.saveState(listener);
			return true;

		} catch (IOException e) {
			Log.e(TerminalKeyListener.TAG, "Problem while trying to handle an onKey() event", e);
			try {
				listener.getBridge().transport.flush();
			} catch (IOException ioe) {
				Log.d(TerminalKeyListener.TAG, "Our transport was closed, dispatching disconnect event");
				listener.getBridge().dispatchDisconnect(false);
			}
		} catch (NullPointerException npe) {
			Log.d(TerminalKeyListener.TAG, "Input before connection established ignored.");
			this.saveState(listener);
			return true;
		}

		this.saveState(listener);
		return false;
	}

	/* (non-Javadoc)
	 * @see org.connectbot.keyboard.IKeyboard#permitsTouch()
	 */
	public boolean permitsTouch() {
		return false;
	}

}
