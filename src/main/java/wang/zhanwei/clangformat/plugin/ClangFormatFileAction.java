package wang.zhanwei.clangformat.plugin;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.Collection;
import java.util.Collections;

/**
 * Runs clang-format on the whole file, and applies the formatting updates to the editor.
 */
public class ClangFormatFileAction extends ClangFormatAction {
  @Override
  protected Collection<TextRange> getFormatRanges(
      Project project, Document document, Editor editor, VirtualFile virtualFile) {
    int docLength = document.getTextLength();

    return Collections.singletonList(new TextRange(0, docLength));
  }
}
