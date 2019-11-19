package wang.zhanwei.clangformat.plugin;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlValue;
import lombok.Getter;
import org.apache.commons.io.IOUtils;

public class ClangFormat {
  static final List<Path> DEFAULT_PATHS =
      Arrays.asList(Paths.get("/usr/bin"), Paths.get("/usr/local/bin"));
  static final int FORMAT_TIMEOUT_IN_SECONDS = 10;
  final Path clangformat;

  public ClangFormat(String binary, String paths) throws IOException {
    clangformat = searchClangFormat(binary, paths);
  }

  Path searchClangFormat(String binary, String paths) throws IOException {
    binary = StringUtil.isEmpty(binary) ? "clang-format" : binary;

    File candidate = new File(binary);

    if (candidate.canExecute()) {
      return candidate.toPath();
    }

    if (candidate.toPath().isAbsolute()) {
      throw new IOException(candidate.toString() + " is not executable");
    }

    paths = StringUtil.isEmpty(paths) ? "" : paths;

    Set<Path> parents = Stream.of(paths.split(":"))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .map(Paths::get)
                            .collect(Collectors.toSet());
    parents.addAll(DEFAULT_PATHS);

    for (Path parent : parents) {
      Path path = Paths.get(parent.toString(), binary);

      if (path.toFile().canExecute()) {
        return path;
      }
    }

    throw new IOException("No executable \"" + binary + "\" found in given path");
  }

  public Replacements format(String file, File directory, int cursor, Collection<TextRange> ranges,
      Document document) throws IOException {
    try {
      // run clang-format
      Process process = new ProcessBuilder()
                            .command(getCommandArguments(file, cursor, ranges))
                            .redirectErrorStream(true)
                            .directory(directory)
                            .start();

      ExecutorService executorService = Executors.newFixedThreadPool(2);

      // write file content out
      executorService.submit(() -> {
        try {
          OutputStream output = process.getOutputStream();
          IOUtils.write(document.getText(), output, StandardCharsets.UTF_8);
          output.flush();
          output.close();
        } catch (IOException ignored) {
        }
      });

      // read result in
      Future<String> result = executorService.submit(
          () -> IOUtils.toString(process.getInputStream(), StandardCharsets.UTF_8));

      // wait for complete or timeout
      executorService.shutdown();
      executorService.awaitTermination(FORMAT_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS);

      // timeout of formatting
      if (process.isAlive()) {
        process.destroyForcibly();
      }

      // wait for clang-format exit or killed
      if (0 == process.waitFor()) {
        Replacements replacements =
            Replacements.parse(IOUtils.toInputStream(result.get(), StandardCharsets.UTF_8));

        if (replacements.getReplacements() != null) {
          // sort replacement desc based on offset
          replacements.getReplacements().sort(
              Comparator.comparingInt(Replacement::getOffset).reversed());
        }

        return replacements;
      }

      throw new IOException(result.get());
    } catch (ExecutionException | InterruptedException e) {
      throw new IOException(e.getMessage());
    }
  }

  List<String> getCommandArguments(String file, int cursor, Collection<TextRange> ranges) {
    List<String> args = new ArrayList<>(Arrays.asList(clangformat.toString(), "-style=file",
        "-output-replacements-xml", "-assume-filename=" + file, "-cursor=" + cursor));

    // Add all the format ranges as individual -offset/-length parameters
    for (TextRange range : ranges) {
      args.add("-offset=" + range.getStartOffset());
      args.add("-length=" + range.getLength());
    }

    return args;
  }

  @XmlRootElement
  static class Replacements {
    static final JAXBContext JAXB_CTX;

    @Getter @XmlElement int cursor;
    @Getter @XmlElement(name = "replacement") List<Replacement> replacements;

    static {
      try {
        JAXB_CTX = JAXBContext.newInstance(Replacements.class);
      } catch (JAXBException e) {
        throw new RuntimeException("Failed to load JAXB context", e);
      }
    }

    static Replacements parse(InputStream inputStream) {
      try {
        // JAXB closes the InputStream.
        return (Replacements) JAXB_CTX.createUnmarshaller().unmarshal(inputStream);
      } catch (JAXBException e) {
        throw new RuntimeException("Failed to parse clang-format XML replacements", e);
      }
    }
  }

  @Getter
  static class Replacement {
    @XmlAttribute int offset;
    @XmlAttribute int length;
    @XmlValue String value;
  }
}
