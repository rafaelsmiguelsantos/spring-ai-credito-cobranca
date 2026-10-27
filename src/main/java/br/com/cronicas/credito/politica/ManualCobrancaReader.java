package br.com.cronicas.credito.politica;

import br.com.cronicas.credito.dominio.Carteira;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.core.io.Resource;

/**
 * Reads a policy manual written in Markdown and emits one {@link Document} per
 * section, carrying the metadata the retriever will later filter on.
 *
 * <p>Why not {@code TokenTextSplitter}? Because a fixed-size window has no idea
 * what a section is. Split "ate 45% do saldo atualizado" away from the heading
 * "Alcada da carteira CARTAO" and the chunk is worse than useless: it is a
 * confident percentage with no carteira attached, and the model will happily
 * attach the wrong one.
 *
 * <p>A policy manual is not prose. It is a tree of rules, and the heading is half
 * of each rule's meaning. Chunking strategy is where most of the quality of a RAG
 * pipeline is won or lost, and it is domain work, not framework work.
 */
public class ManualCobrancaReader implements DocumentReader {

    private static final Pattern FRONTMATTER = Pattern.compile("\\A---\\R(.*?)\\R---\\R", Pattern.DOTALL);
    private static final Pattern FRONTMATTER_LINE = Pattern.compile("^([a-z_]+):\\s*\"?([^\"]*)\"?\\s*$");
    private static final Pattern HEADING = Pattern.compile("^(#{1,3})\\s+(.*)$");

    /** Built from the enum, so a new carteira cannot be added without being tagged. */
    private static final Pattern CARTEIRA = Pattern.compile("\\b("
            + Arrays.stream(Carteira.values()).map(Enum::name).collect(Collectors.joining("|"))
            + ")\\b");

    private final Resource resource;

    public ManualCobrancaReader(Resource resource) {
        this.resource = resource;
    }

    @Override
    public List<Document> get() {
        String raw = read(resource);

        Map<String, Object> fileMetadata = new LinkedHashMap<>();
        fileMetadata.put(MetadadosPolitica.FONTE, fonte());

        Matcher frontmatter = FRONTMATTER.matcher(raw);
        String body = raw;
        if (frontmatter.find()) {
            fileMetadata.putAll(parseFrontmatter(frontmatter.group(1)));
            body = raw.substring(frontmatter.end());
        }

        return splitBySection(body, fileMetadata);
    }

    /**
     * The value every chunk of this file is tagged with, and the handle an
     * ingestion uses to delete what a previous run of itself wrote.
     */
    public String fonte() {
        String filename = resource.getFilename();
        return filename == null ? resource.getDescription() : filename;
    }

    private List<Document> splitBySection(String body, Map<String, Object> fileMetadata) {
        List<Document> documents = new ArrayList<>();

        String currentTitle = null;
        String currentParent = null;
        StringBuilder buffer = new StringBuilder();

        for (String line : body.split("\\R", -1)) {
            Matcher heading = HEADING.matcher(line);

            if (heading.matches()) {
                int level = heading.group(1).length();
                String title = heading.group(2).trim();

                if (level == 1) {
                    continue; // document title, not a section
                }

                flush(documents, currentTitle, currentParent, buffer, fileMetadata);

                if (level == 2) {
                    currentParent = title;
                }
                currentTitle = title;
                continue;
            }

            buffer.append(line).append('\n');
        }

        flush(documents, currentTitle, currentParent, buffer, fileMetadata);
        return documents;
    }

    private void flush(List<Document> documents, String title, String parent,
                       StringBuilder buffer, Map<String, Object> fileMetadata) {

        String text = buffer.toString().trim();
        buffer.setLength(0);

        if (title == null || text.isEmpty()) {
            return;
        }

        Map<String, Object> metadata = new LinkedHashMap<>(fileMetadata);
        metadata.put(MetadadosPolitica.SECAO, title);
        if (parent != null && !parent.equals(title)) {
            metadata.put(MetadadosPolitica.SECAO_PAI, parent);
        }

        // Every chunk is tagged, so the retriever can filter instead of hoping
        // the embedding carries the distinction. A section that names no carteira
        // gets the GERAL sentinel rather than no tag at all -- see
        // MetadadosPolitica.CARTEIRA_GERAL for why the absence of a tag is worse
        // than it looks.
        metadata.put(MetadadosPolitica.CARTEIRA, carteiraDe(title, parent));

        // The heading is prepended to the text as well as stored as metadata:
        // the embedding should carry it, not just the filter.
        documents.add(new Document(title + "\n\n" + text, metadata));
    }

    /**
     * A subsection inherits its parent's carteira: "4.3 Alcada da carteira
     * CDC_VEICULO" names one, and anything nested under it is about the same one.
     */
    private String carteiraDe(String title, String parent) {
        Matcher noTitulo = CARTEIRA.matcher(title);
        if (noTitulo.find()) {
            return noTitulo.group(1);
        }
        if (parent != null) {
            Matcher noPai = CARTEIRA.matcher(parent);
            if (noPai.find()) {
                return noPai.group(1);
            }
        }
        return MetadadosPolitica.CARTEIRA_GERAL;
    }

    private Map<String, Object> parseFrontmatter(String block) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        for (String line : block.split("\\R")) {
            Matcher matcher = FRONTMATTER_LINE.matcher(line.trim());
            if (matcher.matches()) {
                metadata.put(matcher.group(1), matcher.group(2).trim());
            }
        }
        return metadata;
    }

    private String read(Resource resource) {
        try (var in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        catch (IOException e) {
            throw new UncheckedIOException("Nao foi possivel ler " + resource, e);
        }
    }
}
