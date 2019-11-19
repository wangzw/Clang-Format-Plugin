package wang.zhanwei.clangformat.plugin;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;
import wang.zhanwei.clangformat.plugin.ClangFormat.Replacement;
import wang.zhanwei.clangformat.plugin.ClangFormat.Replacements;

public class ClangFormatAction extends AnAction {
  @Override
  public void actionPerformed(AnActionEvent event) {
    Project project = event.getData(CommonDataKeys.PROJECT);

    try {
      Editor editor = event.getData(CommonDataKeys.EDITOR);
      VirtualFile file = event.getData(CommonDataKeys.VIRTUAL_FILE);

      // can happen during startup.
      if (editor == null || file == null) {
        return;
      }

      Document document = editor.getDocument();

      Collection<TextRange> ranges = getFormatRanges(project, document, editor, file);

      // Can happen if there are no VCS changes
      if (ranges.isEmpty()) {
        return;
      }

      // convert to byte offset
      ranges = ranges.stream()
                   .map(r
                       -> new TextRange(toByteOffset(document.getText(), r.getStartOffset()),
                           toByteOffset(document.getText(), r.getEndOffset())))
                   .collect(Collectors.toList());

      Caret caret = editor.getCaretModel().getPrimaryCaret();

      Settings settings = Settings.get();

      ClangFormat formatter = new ClangFormat(settings.getClangFormatBinary(), settings.getPath());
      Replacements replacements =
          formatter.format(file.getPath(), getWorkingDirectory(project, file),
              toByteOffset(document.getText(), caret.getOffset()), ranges, document);

      applyChange(replacements, project, document, event.getData(LangDataKeys.PSI_FILE), caret);
    } catch (IOException e) {
      showError(project, e.getMessage());
    }
  }

  File getWorkingDirectory(Project project, VirtualFile file) {
    if (file.getFileSystem() instanceof LocalFileSystem) {
      return Paths.get(file.getPath()).getParent().toFile();
    }

    if (project.getBasePath() != null) {
      return Paths.get(project.getBasePath()).toFile();
    }

    return null;
  }

  void applyChange(
      Replacements replacements, Project project, Document document, PsiFile file, Caret caret) {
    if (replacements.getReplacements() != null) {
      WriteCommandAction.runWriteCommandAction(project, "Format Code", null, () -> {
        byte[] bytes = document.getText().getBytes(StandardCharsets.UTF_8);

        // sorted desc by offset
        for (Replacement r : replacements.getReplacements()) {
          int actualStart = r.getOffset();
          int actualEnd = actualStart + r.getLength();

          document.replaceString(
              toCharOffset(bytes, actualStart), toCharOffset(bytes, actualEnd), r.getValue());
        }

        caret.moveToOffset(replacements.getCursor());
      }, file);
    }
  }

  // https://en.wikipedia.org/wiki/UTF-8
  int toCharOffset(byte[] bytes, int offset) {
    int count = 0;

    for (int i = 0; i < offset && i < bytes.length; ++i) {
      if ((bytes[i] & 0x80) == 0) {
        // ascii
        ++count;
      } else if ((bytes[i] & 0xc0) == 0xc0) {
        // multiply bytes char
        ++count;
      }
    }

    return count;
  }

  int toByteOffset(String text, int offset) {
    return text.substring(0, offset).getBytes(StandardCharsets.UTF_8).length;
  }

  public static void showError(Project project, String errorMsg) {
    Notification notification =
        new Notification("ClangFormat", "Formatting Failed", errorMsg, NotificationType.ERROR);
    Notifications.Bus.notify(notification, project);
  }

  protected Collection<TextRange> getFormatRanges(
      Project project, Document document, Editor editor, VirtualFile file) {
    int selectionStart = editor.getSelectionModel().getSelectionStart();
    int selectionEnd = editor.getSelectionModel().getSelectionEnd();

    return Collections.singletonList(new TextRange(selectionStart, selectionEnd));
  }
}
