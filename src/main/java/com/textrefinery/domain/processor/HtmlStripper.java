package com.textrefinery.domain.processor;

import com.textrefinery.domain.model.ProcessingContext;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

/** Si el texto es HTML, lo reduce a texto plano conservando párrafos y saltos. */
public final class HtmlStripper implements TextProcessor {

    private static final Pattern HTML_TAG = Pattern.compile(
            "</?(?:html|body|div|span|p|br|a|b|i|strong|em|ul|ol|li|table|tr|td|h[1-6])\\b[^>]*>",
            Pattern.CASE_INSENSITIVE);

    public static boolean looksLikeHtml(String text) {
        return HTML_TAG.matcher(text).find();
    }

    @Override
    public String process(String input, ProcessingContext ctx) {
        if (!looksLikeHtml(input)) {
            return input;
        }
        Document doc = Jsoup.parse(input);
        doc.outputSettings().prettyPrint(false);
        doc.select("br, div, li, tr").after("\\n");
        doc.select("p, h1, h2, h3, h4, h5, h6").after("\\n\\n");
        return doc.body().text().replace("\\n", "\n");
    }
}
