/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use it except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hop.ui.hopgui;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.swing.JPanel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.gui.GuiResource;
import org.apache.hop.ui.core.widget.editor.IContentEditorWidget;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Shell;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.SyntaxScheme;
import org.fife.ui.rsyntaxtextarea.Theme;
import org.fife.ui.rtextarea.RTextScrollPane;

/** Desktop (RCP) implementation of the content editor using RSyntaxTextArea. */
public class ContentEditorFacadeImpl extends ContentEditorFacade {

  private static final String THEMES_DARK_XML = "/org/fife/ui/rsyntaxtextarea/themes/dark.xml";

  @Override
  protected IContentEditorWidget createContentEditorInternal(Composite parent, String languageId) {
    Composite bridge = new Composite(parent, SWT.EMBEDDED | SWT.NO_BACKGROUND);
    PropsUi.setLook(bridge);
    FormData fdBridge = new FormData();
    fdBridge.left = new FormAttachment(0, 0);
    fdBridge.right = new FormAttachment(100, 0);
    fdBridge.top = new FormAttachment(0, 0);
    fdBridge.bottom = new FormAttachment(100, 0);
    bridge.setLayoutData(fdBridge);

    java.awt.Frame awtFrame = org.eclipse.swt.awt.SWT_AWT.new_Frame(bridge);

    RSyntaxTextArea textArea = new RSyntaxTextArea(24, 80);
    textArea.setAntiAliasingEnabled(true);
    textArea.setCodeFoldingEnabled(true);
    textArea.setSyntaxEditingStyle(mapLanguageToStyle(languageId));

    applyThemeFromHop(textArea);
    applyFontFromHop(textArea);
    textArea.setPopupMenu(null);

    forwardShortcutsToSwt(textArea, bridge);

    RTextScrollPane scrollPane = new RTextScrollPane(textArea);
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(scrollPane, BorderLayout.CENTER);
    awtFrame.setLayout(new BorderLayout());
    awtFrame.add(panel, BorderLayout.CENTER);

    bridge.addListener(
        SWT.Resize,
        event -> {
          if (bridge.isDisposed() || awtFrame == null) {
            return;
          }
          try {
            Rectangle rect = bridge.getClientArea();
            java.awt.EventQueue.invokeLater(
                () -> {
                  if (awtFrame != null) {
                    try {
                      awtFrame.setSize(Math.max(1, rect.width), Math.max(1, rect.height));
                      awtFrame.validate();
                    } catch (Exception ignored) {
                      // ignore
                    }
                  }
                });
          } catch (Exception ignored) {
            // ignore
          }
        });

    return new RcpContentEditorWidget(bridge, textArea);
  }

  /**
   * Applies Hop's fixed font (from Options) to the text area. Converts SWT font to AWT and sets it
   * on the base font and on all syntax scheme styles so keywords/comments use the same font.
   */
  private static void applyFontFromHop(RSyntaxTextArea textArea) {
    try {
      org.eclipse.swt.graphics.Font swtFont = GuiResource.getInstance().getFontFixed();
      if (swtFont == null || swtFont.isDisposed()) {
        return;
      }
      FontData[] fdArr = swtFont.getFontData();
      if (fdArr == null || fdArr.length == 0) {
        return;
      }
      FontData fd = fdArr[0];
      int awtStyle = Font.PLAIN;
      int swtStyle = fd.getStyle();
      if ((swtStyle & SWT.BOLD) != 0) {
        awtStyle |= Font.BOLD;
      }
      if ((swtStyle & SWT.ITALIC) != 0) {
        awtStyle |= Font.ITALIC;
      }
      Font awtFont = new Font(fd.getName(), awtStyle, fd.getHeight());
      textArea.setFont(awtFont);
      SyntaxScheme scheme = textArea.getSyntaxScheme();
      if (scheme != null) {
        scheme = (SyntaxScheme) scheme.clone();
        for (int i = 0; i < scheme.getStyleCount(); i++) {
          if (scheme.getStyle(i) != null) {
            scheme.getStyle(i).font = awtFont;
          }
        }
        textArea.setSyntaxScheme(scheme);
      }
    } catch (Exception e) {
      // If GuiResource or font not available (e.g. headless), leave default font
    }
  }

  /** Applies dark or default theme based on Hop's Dark Mode setting. */
  private static void applyThemeFromHop(RSyntaxTextArea textArea) {
    try {
      if (PropsUi.getInstance().isDarkMode()) {
        try (InputStream in = Theme.class.getResourceAsStream(THEMES_DARK_XML)) {
          if (in != null) {
            Theme theme = Theme.load(in);
            theme.apply(textArea);
          }
        }
      }
    } catch (Exception e) {
      // Theme not available or load failed; keep default look
    }
  }

  /**
   * Forwards keyboard shortcuts from the AWT text area to the SWT shell so that Hop's key handler
   * can process them. Only Copy (Ctrl/Cmd+C), Cut (Ctrl/Cmd+X), and Paste (Ctrl/Cmd+V) are left to
   * the text area; all other key combinations are consumed and re-dispatched to SWT.
   */
  private static void forwardShortcutsToSwt(RSyntaxTextArea textArea, Composite bridge) {
    Shell shell = bridge.getShell();
    Display display = bridge.getDisplay();

    KeyEventDispatcher dispatcher =
        e -> {
          if (e.getID() != KeyEvent.KEY_PRESSED) {
            return false;
          }
          if (e.getSource() != textArea && !isDescendant(textArea, e.getSource())) {
            return false;
          }
          int mods =
              e.getModifiersEx()
                  & (InputEvent.CTRL_DOWN_MASK
                      | InputEvent.META_DOWN_MASK
                      | InputEvent.ALT_DOWN_MASK
                      | InputEvent.SHIFT_DOWN_MASK);
          if (mods == 0) {
            return false;
          }
          char keyChar = Character.toLowerCase(e.getKeyChar());
          boolean copyCutPaste =
              (mods == InputEvent.CTRL_DOWN_MASK || mods == InputEvent.META_DOWN_MASK)
                  && (keyChar == 'c' || keyChar == 'x' || keyChar == 'v');
          if (copyCutPaste) {
            return false;
          }

          int swtStateMask = 0;
          if ((e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) != 0) {
            swtStateMask |= SWT.CTRL;
          }
          if ((e.getModifiersEx() & InputEvent.META_DOWN_MASK) != 0) {
            swtStateMask |= SWT.COMMAND;
          }
          if ((e.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) != 0) {
            swtStateMask |= SWT.SHIFT;
          }
          if ((e.getModifiersEx() & InputEvent.ALT_DOWN_MASK) != 0) {
            swtStateMask |= SWT.ALT;
          }

          int swtKeyCode = javaKeyCodeToSwt(e.getKeyCode(), e.getKeyChar());
          char swtChar = e.getKeyChar();

          Shell s = shell;
          Display d = display;
          if (s == null || s.isDisposed() || d == null) {
            return true;
          }
          final int keyCode = swtKeyCode;
          final int stateMask = swtStateMask;
          final char character = swtChar;
          d.asyncExec(
              () -> {
                if (s.isDisposed()) {
                  return;
                }
                Event event = new Event();
                event.type = SWT.KeyDown;
                event.widget = s;
                event.keyCode = keyCode;
                event.stateMask = stateMask;
                event.character = character;
                s.notifyListeners(SWT.KeyDown, event);
              });
          return true;
        };

    KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(dispatcher);

    bridge.addListener(
        SWT.Dispose,
        ev -> {
          java.awt.EventQueue.invokeLater(
              () ->
                  KeyboardFocusManager.getCurrentKeyboardFocusManager()
                      .removeKeyEventDispatcher(dispatcher));
        });
  }

  private static boolean isDescendant(java.awt.Component parent, Object source) {
    if (!(source instanceof java.awt.Component)) {
      return false;
    }
    java.awt.Component c = (java.awt.Component) source;
    do {
      if (c == parent) {
        return true;
      }
      c = c.getParent();
    } while (c != null);
    return false;
  }

  private static String mapLanguageToStyle(String languageId) {
    if (languageId == null) {
      return SyntaxConstants.SYNTAX_STYLE_NONE;
    }
    switch (languageId.toLowerCase(Locale.ROOT)) {
      case "json":
        return SyntaxConstants.SYNTAX_STYLE_JSON;
      case "xml":
        return SyntaxConstants.SYNTAX_STYLE_XML;
      case "javascript":
      case "js":
        return SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT;
      case "html":
        return SyntaxConstants.SYNTAX_STYLE_HTML;
      case "java":
        return SyntaxConstants.SYNTAX_STYLE_JAVA;
      case "sql":
        return SyntaxConstants.SYNTAX_STYLE_SQL;
      case "python":
      case "py":
        return SyntaxConstants.SYNTAX_STYLE_PYTHON;
      case "yaml":
      case "yml":
        return SyntaxConstants.SYNTAX_STYLE_YAML;
      case "properties":
        return SyntaxConstants.SYNTAX_STYLE_PROPERTIES_FILE;
      default:
        return SyntaxConstants.SYNTAX_STYLE_NONE;
    }
  }

  /** Map Java KeyEvent key code / character to SWT keyCode for shortcut matching. */
  private static int javaKeyCodeToSwt(int javaKeyCode, char keyChar) {
    if (keyChar != KeyEvent.CHAR_UNDEFINED && keyChar >= 32 && keyChar < 127) {
      return Character.toLowerCase(keyChar);
    }
    switch (javaKeyCode) {
      case KeyEvent.VK_ENTER:
        return SWT.CR;
      case KeyEvent.VK_BACK_SPACE:
        return SWT.BS;
      case KeyEvent.VK_DELETE:
        return SWT.DEL;
      case KeyEvent.VK_ESCAPE:
        return SWT.ESC;
      case KeyEvent.VK_TAB:
        return SWT.TAB;
      case KeyEvent.VK_ADD:
        return SWT.KEYPAD_ADD;
      case KeyEvent.VK_SUBTRACT:
        return SWT.KEYPAD_SUBTRACT;
      case KeyEvent.VK_MULTIPLY:
        return SWT.KEYPAD_MULTIPLY;
      case KeyEvent.VK_DIVIDE:
        return SWT.KEYPAD_DIVIDE;
      case KeyEvent.VK_EQUALS:
        return SWT.KEYPAD_EQUAL;
      default:
        return javaKeyCode;
    }
  }

  private static class RcpContentEditorWidget implements IContentEditorWidget {

    private final Composite control;
    private final RSyntaxTextArea textArea;
    private final List<ModifyListener> modifyListeners = new CopyOnWriteArrayList<>();
    private volatile boolean suppressModify;

    RcpContentEditorWidget(Composite control, RSyntaxTextArea textArea) {
      this.control = control;
      this.textArea = textArea;
      textArea
          .getDocument()
          .addDocumentListener(
              new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                  fireModify();
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                  fireModify();
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                  fireModify();
                }

                private void fireModify() {
                  if (suppressModify) {
                    return;
                  }
                  Display display = control.getDisplay();
                  if (display == null || control.isDisposed()) {
                    return;
                  }
                  Runnable run =
                      () -> {
                        if (control.isDisposed() || suppressModify) {
                          return;
                        }
                        for (ModifyListener listener : new ArrayList<>(modifyListeners)) {
                          try {
                            listener.modifyText(null);
                          } catch (Exception ignored) {
                            // ignore
                          }
                        }
                      };
                  if (display.getThread() == Thread.currentThread()) {
                    run.run();
                  } else {
                    display.asyncExec(run);
                  }
                }
              });
    }

    @Override
    public Control getControl() {
      return control;
    }

    @Override
    public String getText() {
      final String[] out = new String[1];
      if (control.getDisplay().getThread() == Thread.currentThread()) {
        out[0] = textArea.getText();
      } else {
        control
            .getDisplay()
            .syncExec(
                () -> {
                  if (!control.isDisposed()) {
                    out[0] = textArea.getText();
                  } else {
                    out[0] = "";
                  }
                });
      }
      return out[0] != null ? out[0] : "";
    }

    @Override
    public void setText(String text) {
      doSetText(text != null ? text : "");
    }

    @Override
    public void setTextSuppressModify(String text) {
      suppressModify = true;
      try {
        doSetText(text != null ? text : "");
      } finally {
        suppressModify = false;
      }
    }

    private void doSetText(String text) {
      if (control.getDisplay().getThread() == Thread.currentThread()) {
        textArea.setText(text);
      } else {
        control
            .getDisplay()
            .asyncExec(
                () -> {
                  if (!control.isDisposed()) {
                    textArea.setText(text);
                  }
                });
      }
    }

    @Override
    public void setLanguage(String languageId) {
      String style = ContentEditorFacadeImpl.mapLanguageToStyle(languageId);
      if (control.getDisplay().getThread() == Thread.currentThread()) {
        textArea.setSyntaxEditingStyle(style);
      } else {
        control
            .getDisplay()
            .asyncExec(
                () -> {
                  if (!control.isDisposed()) {
                    textArea.setSyntaxEditingStyle(style);
                  }
                });
      }
    }

    @Override
    public void setReadOnly(boolean readOnly) {
      boolean editable = !readOnly;
      java.awt.EventQueue.invokeLater(
          () -> {
            if (!control.isDisposed()) {
              textArea.setEditable(editable);
            }
          });
    }

    @Override
    public void addModifyListener(ModifyListener listener) {
      if (listener != null) {
        modifyListeners.add(listener);
      }
    }

    @Override
    public void removeModifyListener(ModifyListener listener) {
      if (listener != null) {
        modifyListeners.remove(listener);
      }
    }

    @Override
    public void selectAll() {
      control
          .getDisplay()
          .asyncExec(
              () -> {
                if (!control.isDisposed()) {
                  textArea.selectAll();
                }
              });
    }

    @Override
    public void unselectAll() {
      control
          .getDisplay()
          .asyncExec(
              () -> {
                if (!control.isDisposed()) {
                  textArea.setCaretPosition(0);
                  textArea.moveCaretPosition(0);
                }
              });
    }

    @Override
    public void copy() {
      control
          .getDisplay()
          .asyncExec(
              () -> {
                if (!control.isDisposed()) {
                  textArea.copy();
                }
              });
    }
  }
}
