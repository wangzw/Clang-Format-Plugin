package wang.zhanwei.clangformat.plugin;

import com.intellij.ide.util.PropertiesComponent;
import lombok.Getter;

@Getter
public class Settings {
  static final String CF_BINARY_PROP =
      ClangFormatConfigurable.class.getName() + ".clangFormatBinary";
  static final String CF_PATH_PROP = ClangFormatConfigurable.class.getName() + ".path";
  static final String CF_VCS_FORMAT_PROP = ClangFormatConfigurable.class.getName() + ".vcs_format";

  final String clangFormatBinary;
  final String path;
  final boolean updateOnlyChangedText;

  static Settings get() {
    return new Settings();
  }

  static Settings update(String clangFormatBinary, String path, boolean updateOnlyChangedText) {
    if ("".equals(clangFormatBinary)) {
      clangFormatBinary = "clang-format";
    }

    if ("".equals(path)) {
      path = null;
    }

    PropertiesComponent props = PropertiesComponent.getInstance();
    props.setValue(CF_BINARY_PROP, clangFormatBinary, "clang-format");
    props.setValue(CF_PATH_PROP, path, null);
    props.setValue(CF_VCS_FORMAT_PROP, updateOnlyChangedText);
    return get();
  }

  Settings() {
    PropertiesComponent props = PropertiesComponent.getInstance();
    clangFormatBinary = props.getValue(CF_BINARY_PROP, "clang-format");
    path = props.getValue(CF_PATH_PROP);
    updateOnlyChangedText = props.getBoolean(CF_VCS_FORMAT_PROP, false);
  }
}
