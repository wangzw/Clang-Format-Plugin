package wang.zhanwei.clangformat.plugin;

import com.intellij.codeInsight.actions.FormatChangedTextUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.codeStyle.ChangedRangesInfo;
import java.util.Collection;
import java.util.Collections;

/**
 * Runs clang-format on the current selection or on the whole file if there is no selection.
 * Applies the formatting updates to the editor.
 */
public class ClangFormatAutoAction extends ClangFormatAction {
  Collection<TextRange> getVcsTextRanges(Project project, VirtualFile virtualFile) {
    final PsiManager myPsiManager = PsiManager.getInstance(project);

    PsiFile psiFile = myPsiManager.findFile(virtualFile);
    FormatChangedTextUtil textUtil = FormatChangedTextUtil.getInstance();

    // Check if the file is under VCS. If it is not then we just use the standard code
    if (!textUtil.isChangeNotTrackedForFile(project, psiFile)) {
      ChangedRangesInfo changedRangesInfo = textUtil.getChangedRangesInfo(psiFile);

      // null can be returned if there are no changes
      if (changedRangesInfo != null) {
        return changedRangesInfo.allChangedRanges;
      }

      return Collections.emptyList();
    }
    return null;
  }

  @Override
  protected Collection<TextRange> getFormatRanges(
      Project project, Document document, Editor editor, VirtualFile virtualFile) {
    Settings settings = Settings.get();

    int docLength = document.getTextLength();
    int selectionStart = Math.min(editor.getSelectionModel().getSelectionStart(), docLength);
    int selectionLength =
        Math.min(editor.getSelectionModel().getSelectionEnd(), docLength) - selectionStart;

    // Formatting the VCS changed text will only be done if nothing is selected. Otherwise you can't
    // force a reformat of some code that was not changed from the VCS version
    if (selectionLength <= 0 && settings.isUpdateOnlyChangedText()) {
      // This case only formats the text changed in the VCS. If the diff is too large or we are not
      // in a VCS file then the standard behavior will be used (selection or whole file)
      Collection<TextRange> vcsRanges = getVcsTextRanges(project, virtualFile);

      // getVcsTextRanges returns null if it could return any valid ranges in which case we
      // will use the non-VCS branch
      if (vcsRanges != null) {
        // The VCS has returned some ranges to format
        return vcsRanges;
      }
    }

    if (selectionLength <= 0) {
      return Collections.singletonList(new TextRange(0, docLength));
    } else {
      return Collections.singletonList(
          new TextRange(selectionStart, selectionStart + selectionLength));
    }
  }
}
