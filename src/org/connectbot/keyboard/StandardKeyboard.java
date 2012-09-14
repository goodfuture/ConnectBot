/**
 *
 */
package org.connectbot.keyboard;

import java.io.IOException;

import org.connectbot.service.TerminalKeyListener;
import org.connectbot.util.PreferenceConstants;

import android.util.Log;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.View;
import de.mud.terminal.vt320;

/**
 * @author James
 *
 */
public class StandardKeyboard implements IKeyboard {
	/*
	 * (non-Javadoc)
	 * @see org.connectbot.keyboard.IKeyboard#onKey(org.connectbot.service.TerminalKeyListener, android.view.View, int, android.view.KeyEvent)
	 */
	public boolean onKey(TerminalKeyListener listener, View v, int keyCode, KeyEvent event)
	{
		try {
			final boolean hardKeyboardHidden = listener.getManager().hardKeyboardHidden;

			// Ignore all key-up events except for the special keys
			if (event.getAction() == KeyEvent.ACTION_UP) {
				// There's nothing here for virtual keyboard users.
				if (!listener.isHardKeyboard() || (listener.isHardKeyboard() && hardKeyboardHidden))
					return false;

				// skip keys if we aren't connected yet or have been disconnected
				if (listener.getBridge().isDisconnected() || listener.getBridge().transport == null)
					return false;

				if (PreferenceConstants.KEYMODE_RIGHT.equals(listener.getKeymode())) {
					if (keyCode == KeyEvent.KEYCODE_ALT_RIGHT
							&& (listener.metaState & TerminalKeyListener.META_SLASH) != 0) {
						listener.metaState &= ~(TerminalKeyListener.META_SLASH | TerminalKeyListener.META_TRANSIENT);
						listener.getBridge().transport.write('/');
						return true;
					} else if (keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT
							&& (listener.metaState & TerminalKeyListener.META_TAB) != 0) {
						listener.metaState &= ~(TerminalKeyListener.META_TAB | TerminalKeyListener.META_TRANSIENT);
						listener.getBridge().transport.write(0x09);
						return true;
					}
				} else if (PreferenceConstants.KEYMODE_LEFT.equals(listener.getKeymode())) {
					if (keyCode == KeyEvent.KEYCODE_ALT_LEFT
							&& (listener.metaState & TerminalKeyListener.META_SLASH) != 0) {
						listener.metaState &= ~(TerminalKeyListener.META_SLASH | TerminalKeyListener.META_TRANSIENT);
						listener.getBridge().transport.write('/');
						return true;
					} else if (keyCode == KeyEvent.KEYCODE_SHIFT_LEFT
							&& (listener.metaState & TerminalKeyListener.META_TAB) != 0) {
						listener.metaState &= ~(TerminalKeyListener.META_TAB | TerminalKeyListener.META_TRANSIENT);
						listener.getBridge().transport.write(0x09);
						return true;
					}
				}

				return false;
			}

			// check for terminal resizing keys
			if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
				listener.getBridge().increaseFontSize();
				return true;
			} else if(keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
				listener.getBridge().decreaseFontSize();
				return true;
			}

			// skip keys if we aren't connected yet or have been disconnected
			if (listener.getBridge().isDisconnected() || listener.getBridge().transport == null)
				return false;

			listener.getBridge().resetScrollPosition();

			if (keyCode == KeyEvent.KEYCODE_UNKNOWN &&
					event.getAction() == KeyEvent.ACTION_MULTIPLE) {
				byte[] input = event.getCharacters().getBytes(listener.getEncoding());
				listener.getBridge().transport.write(input);
				return true;
			}

			int curMetaState = event.getMetaState();
			final int orgMetaState = curMetaState;

			if ((listener.metaState & TerminalKeyListener.META_SHIFT_MASK) != 0) {
				curMetaState |= KeyEvent.META_SHIFT_ON;
			}

			if ((listener.metaState & TerminalKeyListener.META_ALT_MASK) != 0) {
				curMetaState |= KeyEvent.META_ALT_ON;
			}

			int key = event.getUnicodeChar(curMetaState);
			// no hard keyboard?  ALT-k should pass through to below
			if ((orgMetaState & KeyEvent.META_ALT_ON) != 0 &&
					(!listener.isHardKeyboard() || hardKeyboardHidden)) {
				key = 0;
			}

			if ((key & KeyCharacterMap.COMBINING_ACCENT) != 0) {
				listener.mDeadKey = key & KeyCharacterMap.COMBINING_ACCENT_MASK;
				return true;
			}

			if (listener.mDeadKey != 0) {
				key = KeyCharacterMap.getDeadChar(listener.mDeadKey, keyCode);
				listener.mDeadKey = 0;
			}

			final boolean printing = (key != 0);

			// otherwise pass through to existing session
			// print normal keys
			if (printing) {
				listener.metaState &= ~(TerminalKeyListener.META_SLASH | TerminalKeyListener.META_TAB);

				// Remove shift and alt modifiers
				final int lastMetaState = listener.metaState;
				listener.metaState &= ~(TerminalKeyListener.META_SHIFT_ON | TerminalKeyListener.META_ALT_ON);
				if (listener.metaState != lastMetaState) {
					listener.getBridge().redraw();
				}

				if ((listener.metaState & TerminalKeyListener.META_CTRL_MASK) != 0) {
					listener.metaState &= ~TerminalKeyListener.META_CTRL_ON;
					listener.getBridge().redraw();

					// If there is no hard keyboard or there is a hard keyboard currently hidden,
					// CTRL-1 through CTRL-9 will send F1 through F9
					if ((!listener.isHardKeyboard() || (listener.isHardKeyboard() && hardKeyboardHidden))
							&& listener.sendFunctionKey(keyCode))
						return true;

					key = listener.keyAsControl(key);
				}

				// handle pressing f-keys
				//if ((hardKeyboard && !hardKeyboardHidden)
				//		&& (curMetaState & KeyEvent.META_SHIFT_ON) != 0
				//		&& sendFunctionKey(keyCode))
				//	return true;

				if (key < 0x80)
					listener.getBridge().transport.write(key);
				else
					// TODO write encoding routine that doesn't allocate each time
					listener.getBridge().transport.write(new String(Character.toChars(key))
							.getBytes(listener.getEncoding()));

				return true;
			}

			// send ctrl and meta-keys as appropriate
			if (!listener.isHardKeyboard() || hardKeyboardHidden) {
				int k = event.getUnicodeChar(0);
				int k0 = k;
				boolean sendCtrl = false;
				boolean sendMeta = false;
				if (k != 0) {
					if ((orgMetaState & TerminalKeyListener.HC_META_CTRL_ON) != 0) {
						k = listener.keyAsControl(k);
						if (k != k0)
							sendCtrl = true;
						// send F1-F10 via CTRL-1 through CTRL-0
						if (!sendCtrl && listener.sendFunctionKey(keyCode))
							return true;
					} else if ((orgMetaState & KeyEvent.META_ALT_ON) != 0) {
						sendMeta = true;
						listener.sendEscape();
					}
					if (sendMeta || sendCtrl) {
						listener.getBridge().transport.write(k);
						return true;
					}
				}
			}
			// try handling keymode shortcuts
			if (listener.isHardKeyboard() && !hardKeyboardHidden &&
					event.getRepeatCount() == 0) {
				if (PreferenceConstants.KEYMODE_RIGHT.equals(listener.getKeymode())) {
					switch (keyCode) {
					case KeyEvent.KEYCODE_ALT_RIGHT:
						listener.metaState |= TerminalKeyListener.META_SLASH;
						return true;
					case KeyEvent.KEYCODE_SHIFT_RIGHT:
						listener.metaState |= TerminalKeyListener.META_TAB;
						return true;
					case KeyEvent.KEYCODE_SHIFT_LEFT:
						listener.metaPress(TerminalKeyListener.META_SHIFT_ON);
						return true;
					case KeyEvent.KEYCODE_ALT_LEFT:
						listener.metaPress(TerminalKeyListener.META_ALT_ON);
						return true;
					}
				} else if (PreferenceConstants.KEYMODE_LEFT.equals(listener.getKeymode())) {
					switch (keyCode) {
					case KeyEvent.KEYCODE_ALT_LEFT:
						listener.metaState |= TerminalKeyListener.META_SLASH;
						return true;
					case KeyEvent.KEYCODE_SHIFT_LEFT:
						listener.metaState |= TerminalKeyListener.META_TAB;
						return true;
					case KeyEvent.KEYCODE_SHIFT_RIGHT:
						listener.metaPress(TerminalKeyListener.META_SHIFT_ON);
						return true;
					case KeyEvent.KEYCODE_ALT_RIGHT:
						listener.metaPress(TerminalKeyListener.META_ALT_ON);
						return true;
					}
				} else {
					switch (keyCode) {
					case KeyEvent.KEYCODE_ALT_LEFT:
					case KeyEvent.KEYCODE_ALT_RIGHT:
						listener.metaPress(TerminalKeyListener.META_ALT_ON);
						return true;
					case KeyEvent.KEYCODE_SHIFT_LEFT:
					case KeyEvent.KEYCODE_SHIFT_RIGHT:
						listener.metaPress(TerminalKeyListener.META_SHIFT_ON);
						return true;
					}
				}
			}

			// look for special chars
			switch(keyCode) {
			case KeyEvent.KEYCODE_CTRL_LEFT:
			case KeyEvent.KEYCODE_CTRL_RIGHT:
				listener.metaPress(TerminalKeyListener.META_CTRL_ON);
				return true;
			case TerminalKeyListener.KEYCODE_ESCAPE:
				listener.sendEscape();
				return true;
			case KeyEvent.KEYCODE_TAB:
				listener.getBridge().transport.write(0x09);
				return true;
			case KeyEvent.KEYCODE_CAMERA:

				// check to see which shortcut the camera button triggers
				String camera = listener.getManager().getPrefs().getString(
						PreferenceConstants.CAMERA,
						PreferenceConstants.CAMERA_CTRLA_SPACE);
				if(PreferenceConstants.CAMERA_CTRLA_SPACE.equals(camera)) {
					listener.getBridge().transport.write(0x01);
					listener.getBridge().transport.write(' ');
				} else if(PreferenceConstants.CAMERA_CTRLA.equals(camera)) {
					listener.getBridge().transport.write(0x01);
				} else if(PreferenceConstants.CAMERA_ESC.equals(camera)) {
					((vt320)listener.getBuffer()).keyTyped(vt320.KEY_ESCAPE, ' ', 0);
				} else if(PreferenceConstants.CAMERA_ESC_A.equals(camera)) {
					((vt320)listener.getBuffer()).keyTyped(vt320.KEY_ESCAPE, ' ', 0);
					listener.getBridge().transport.write('a');
				}

				break;

			case KeyEvent.KEYCODE_DEL:
				((vt320) listener.getBuffer()).keyPressed(vt320.KEY_BACK_SPACE, ' ',
						listener.getStateForBuffer());
				listener.metaState &= ~TerminalKeyListener.META_TRANSIENT;
				return true;
			case KeyEvent.KEYCODE_ENTER:
				((vt320)listener.getBuffer()).keyTyped(vt320.KEY_ENTER, ' ', 0);
				listener.metaState &= ~TerminalKeyListener.META_TRANSIENT;
				return true;

			case KeyEvent.KEYCODE_DPAD_LEFT:
				if (listener.isSelectingForCopy()) {
					listener.getSelectionArea().decrementColumn();
					listener.getBridge().redraw();
				} else {
					((vt320) listener.getBuffer()).keyPressed(vt320.KEY_LEFT, ' ',
							listener.getStateForBuffer());
					listener.metaState &= ~TerminalKeyListener.META_TRANSIENT;
					listener.getBridge().tryKeyVibrate();
				}
				return true;

			case KeyEvent.KEYCODE_DPAD_UP:
				if (listener.isSelectingForCopy()) {
					listener.getSelectionArea().decrementRow();
					listener.getBridge().redraw();
				} else {
					((vt320) listener.getBuffer()).keyPressed(vt320.KEY_UP, ' ',
							listener.getStateForBuffer());
					listener.metaState &= ~TerminalKeyListener.META_TRANSIENT;
					listener.getBridge().tryKeyVibrate();
				}
				return true;

			case KeyEvent.KEYCODE_DPAD_DOWN:
				if (listener.isSelectingForCopy()) {
					listener.getSelectionArea().incrementRow();
					listener.getBridge().redraw();
				} else {
					((vt320) listener.getBuffer()).keyPressed(vt320.KEY_DOWN, ' ',
							listener.getStateForBuffer());
					listener.metaState &= ~TerminalKeyListener.META_TRANSIENT;
					listener.getBridge().tryKeyVibrate();
				}
				return true;

			case KeyEvent.KEYCODE_DPAD_RIGHT:
				if (listener.isSelectingForCopy()) {
					listener.getSelectionArea().incrementColumn();
					listener.getBridge().redraw();
				} else {
					((vt320) listener.getBuffer()).keyPressed(vt320.KEY_RIGHT, ' ',
							listener.getStateForBuffer());
					listener.metaState &= ~TerminalKeyListener.META_TRANSIENT;
					listener.getBridge().tryKeyVibrate();
				}
				return true;

			case KeyEvent.KEYCODE_DPAD_CENTER:
				if (listener.isSelectingForCopy()) {
					if (listener.getSelectionArea().isSelectingOrigin())
						listener.getSelectionArea().finishSelectingOrigin();
					else {
						if (listener.getClipboard() != null) {
							// copy selected area to clipboard
							String copiedText = listener.getSelectionArea().copyFrom(listener.getBuffer());

							listener.getClipboard().setText(copiedText);
							// XXX STOPSHIP
//							manager.notifyUser(manager.getString(
//									R.string.console_copy_done,
//									copiedText.length()));

							listener.setSelectingForCopy(false);
							listener.getSelectionArea().reset();
						}
					}
				} else {
					if ((listener.metaState & TerminalKeyListener.META_CTRL_ON) != 0) {
						listener.sendEscape();
						listener.metaState &= ~TerminalKeyListener.META_CTRL_ON;
					} else
						listener.metaPress(TerminalKeyListener.META_CTRL_ON);
				}

				listener.getBridge().redraw();

				return true;
			}

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
			return true;
		}

		return false;
	}

	/* (non-Javadoc)
	 * @see org.connectbot.keyboard.IKeyboard#permitsTouch()
	 */
	public boolean permitsTouch() {
		return true;
	}
}
