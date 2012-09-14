/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2010 Kenny Root, Jeffrey Sharkey
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.connectbot.service;

import org.connectbot.TerminalView;
import org.connectbot.bean.SelectionArea;
import org.connectbot.keyboard.IKeyboard;
import org.connectbot.keyboard.StandardKeyboard;
import org.connectbot.keyboard.TF300TKeyboard;
import org.connectbot.util.PreferenceConstants;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Configuration;
import android.os.Message;
import android.preference.PreferenceManager;
import android.text.ClipboardManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnKeyListener;
import de.mud.terminal.VDUBuffer;
import de.mud.terminal.vt320;

/**
 * @author kenny
 *
 */
@SuppressWarnings("deprecation") // for ClipboardManager
public class TerminalKeyListener implements OnKeyListener, OnSharedPreferenceChangeListener {
	public static final String TAG = "ConnectBot.OnKeyListener";

	public final static int META_CTRL_ON = 0x01;
	public final static int META_CTRL_LOCK = 0x02;
	public final static int META_ALT_ON = 0x04;
	public final static int META_ALT_LOCK = 0x08;
	public final static int META_SHIFT_ON = 0x10;
	public final static int META_SHIFT_LOCK = 0x20;
	public final static int META_SLASH = 0x40;
	public final static int META_TAB = 0x80;

	// The bit mask of momentary and lock states for each
	public final static int META_CTRL_MASK = META_CTRL_ON | META_CTRL_LOCK;
	public final static int META_ALT_MASK = META_ALT_ON | META_ALT_LOCK;
	public final static int META_SHIFT_MASK = META_SHIFT_ON | META_SHIFT_LOCK;

	// backport constants from api level 11
	public final static int KEYCODE_ESCAPE = 111;
	public final static int HC_META_CTRL_ON = 4096;

	// All the transient key codes
	public final static int META_TRANSIENT = META_CTRL_ON | META_ALT_ON
			| META_SHIFT_ON;

	private final TerminalManager manager;
	private final TerminalBridge bridge;
	private final VDUBuffer buffer;

	private String keymode = null;
	private boolean hardKeyboard = false;

	public int metaState = 0;

	public int mDeadKey = 0;

	// TODO add support for the new API.
	private ClipboardManager clipboard = null;

	private boolean selectingForCopy = false;
	private final SelectionArea selectionArea;

	private String encoding;

	private final SharedPreferences prefs;

	private IKeyboard lastKeyboard = null;

	public TerminalKeyListener(TerminalManager manager,
			TerminalBridge bridge,
			VDUBuffer buffer,
			String encoding) {
		this.manager = manager;
		this.bridge = bridge;
		this.buffer = buffer;
		this.encoding = encoding;

		selectionArea = new SelectionArea();

		prefs = PreferenceManager.getDefaultSharedPreferences(manager);
		prefs.registerOnSharedPreferenceChangeListener(this);

		setHardKeyboard((manager.res.getConfiguration().keyboard
				== Configuration.KEYBOARD_QWERTY));

		updateKeymode();
	}

	static public IKeyboard getKeyboard(String name)
	{
		if ("org.connectbot.keyboard.TF300TKeyboard".equals(name))
			return new TF300TKeyboard();
		else // s == "org.connectbot.keyboard.StandardKeyboard"
			return new StandardKeyboard();
	}

	/**
	 * Handle onKey() events coming down from a {@link TerminalView} above us.
	 * Modify the keys to make more sense to a host then pass it to the transport.
	 */
	public boolean onKey(View v, int keyCode, KeyEvent event) {
		String s = prefs.getString("keyboardLayout", "org.connectbot.keyboard.StandardKeyboard");
		if (lastKeyboard == null || !s.equals(lastKeyboard.getClass().getName()))
		{
			Log.i(TAG, "Switching keyboard preference to " + s + ".");
			lastKeyboard = TerminalKeyListener.getKeyboard(s);
		}

		return lastKeyboard.onKey(this, v, keyCode, event);
	}

	public int keyAsControl(int key) {
		// Support CTRL-a through CTRL-z
		if (key >= 0x61 && key <= 0x7A)
			key -= 0x60;
		// Support CTRL-A through CTRL-_
		else if (key >= 0x41 && key <= 0x5F)
			key -= 0x40;
		// CTRL-space sends NULL
		else if (key == 0x20)
			key = 0x00;
		// CTRL-? sends DEL
		else if (key == 0x3F)
			key = 0x7F;
		return key;
	}

	public void sendEscape() {
		((vt320)getBuffer()).keyTyped(vt320.KEY_ESCAPE, ' ', 0);
	}

	/**
	 * @param key
	 * @return successful
	 */
	public boolean sendFunctionKey(int keyCode) {
		switch (keyCode) {
		case KeyEvent.KEYCODE_1:
			((vt320) getBuffer()).keyPressed(vt320.KEY_F1, ' ', 0);
			return true;
		case KeyEvent.KEYCODE_2:
			((vt320) getBuffer()).keyPressed(vt320.KEY_F2, ' ', 0);
			return true;
		case KeyEvent.KEYCODE_3:
			((vt320) getBuffer()).keyPressed(vt320.KEY_F3, ' ', 0);
			return true;
		case KeyEvent.KEYCODE_4:
			((vt320) getBuffer()).keyPressed(vt320.KEY_F4, ' ', 0);
			return true;
		case KeyEvent.KEYCODE_5:
			((vt320) getBuffer()).keyPressed(vt320.KEY_F5, ' ', 0);
			return true;
		case KeyEvent.KEYCODE_6:
			((vt320) getBuffer()).keyPressed(vt320.KEY_F6, ' ', 0);
			return true;
		case KeyEvent.KEYCODE_7:
			((vt320) getBuffer()).keyPressed(vt320.KEY_F7, ' ', 0);
			return true;
		case KeyEvent.KEYCODE_8:
			((vt320) getBuffer()).keyPressed(vt320.KEY_F8, ' ', 0);
			return true;
		case KeyEvent.KEYCODE_9:
			((vt320) getBuffer()).keyPressed(vt320.KEY_F9, ' ', 0);
			return true;
		case KeyEvent.KEYCODE_0:
			((vt320) getBuffer()).keyPressed(vt320.KEY_F10, ' ', 0);
			return true;
		default:
			return false;
		}
	}

	/**
	 * Handle meta key presses where the key can be locked on.
	 * <p>
	 * 1st press: next key to have meta state<br />
	 * 2nd press: meta state is locked on<br />
	 * 3rd press: disable meta state
	 *
	 * @param code
	 */
	public void metaPress(int code) {
		if ((metaState & (code << 1)) != 0) {
			metaState &= ~(code << 1);
		} else if ((metaState & code) != 0) {
			metaState &= ~code;
			metaState |= code << 1;
		} else
			metaState |= code;
		getBridge().redraw();
	}

	public void setTerminalKeyMode(String keymode) {
		this.keymode = keymode;
	}

	public int getStateForBuffer() {
		int bufferState = 0;

		if ((metaState & META_CTRL_MASK) != 0)
			bufferState |= vt320.KEY_CONTROL;
		if ((metaState & META_SHIFT_MASK) != 0)
			bufferState |= vt320.KEY_SHIFT;
		if ((metaState & META_ALT_MASK) != 0)
			bufferState |= vt320.KEY_ALT;

		return bufferState;
	}

	public int getMetaState() {
		return metaState;
	}

	public int getDeadKey() {
		return mDeadKey;
	}

	public void setClipboardManager(ClipboardManager clipboard) {
		this.setClipboard(clipboard);
	}

	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
		if (PreferenceConstants.KEYMODE.equals(key)) {
			updateKeymode();
		}
	}

	private void updateKeymode() {
		keymode = prefs.getString(PreferenceConstants.KEYMODE, PreferenceConstants.KEYMODE_RIGHT);
	}

	public void setCharset(String encoding) {
		this.encoding = encoding;
	}

	/**
	 * @return the hardKeyboard
	 */
	public boolean isHardKeyboard() {
		return hardKeyboard;
	}

	/**
	 * @param hardKeyboard the hardKeyboard to set
	 */
	public void setHardKeyboard(boolean hardKeyboard) {
		this.hardKeyboard = hardKeyboard;
	}

	/**
	 * @return the manager
	 */
	public TerminalManager getManager() {
		return manager;
	}

	/**
	 * @return the bridge
	 */
	public TerminalBridge getBridge() {
		return bridge;
	}

	/**
	 * @return the keymode
	 */
	public String getKeymode() {
		return keymode;
	}

	/**
	 * @return the encoding
	 */
	public String getEncoding() {
		return encoding;
	}

	/**
	 * @return the buffer
	 */
	public VDUBuffer getBuffer() {
		return buffer;
	}

	/**
	 * @return the selectingForCopy
	 */
	public boolean isSelectingForCopy() {
		return selectingForCopy;
	}

	/**
	 * @param selectingForCopy the selectingForCopy to set
	 */
	public void setSelectingForCopy(boolean selectingForCopy) {
		this.selectingForCopy = selectingForCopy;
	}

	/**
	 * @return the selectionArea
	 */
	public SelectionArea getSelectionArea() {
		return selectionArea;
	}

	/**
	 * @return the clipboard
	 */
	public ClipboardManager getClipboard() {
		return clipboard;
	}

	/**
	 * @param clipboard the clipboard to set
	 */
	public void setClipboard(ClipboardManager clipboard) {
		this.clipboard = clipboard;
	}

	/**
	 * @param string
	 */
	public void notifyUser(String message) {
		bridge.notifyUser(message);
	}

	/**
	 *
	 */
	public void finish() {
		if (manager.finishHandler != null)
			Message.obtain(manager.finishHandler, -1, bridge).sendToTarget();
	}
}
