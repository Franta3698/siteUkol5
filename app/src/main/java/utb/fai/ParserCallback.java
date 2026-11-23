package utb.fai;

import java.net.URI;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;

class ParserCallback extends HTMLEditorKit.ParserCallback {

    URI pageURI;
    int depth = 0, maxDepth = 2;
    HashSet<URI> visitedURIs;
    LinkedList<URIinfo> foundURIs;
    int debugLevel = 0;


    ConcurrentHashMap<String, AtomicInteger> wordCounts;

    ParserCallback(HashSet<URI> visitedURIs, LinkedList<URIinfo> foundURIs, ConcurrentHashMap<String, AtomicInteger> wordCounts) {
        this.foundURIs = foundURIs;
        this.visitedURIs = visitedURIs;
        this.wordCounts = wordCounts;
    }

    public void handleSimpleTag(HTML.Tag t, MutableAttributeSet a, int pos) {
        handleStartTag(t, a, pos);
    }

    public void handleStartTag(HTML.Tag t, MutableAttributeSet a, int pos) {
        URI uri;
        String href = null;
        if (debugLevel > 1)
            System.err.println("handleStartTag: " + t.toString() + ", pos=" + pos + ", attribs=" + a.toString());
        
        if (depth < maxDepth) {
            if (t == HTML.Tag.A)
                href = (String) a.getAttribute(HTML.Attribute.HREF);
            else if (t == HTML.Tag.FRAME)
                href = (String) a.getAttribute(HTML.Attribute.SRC);
        }

        if (href != null) {
            try {
                uri = pageURI.resolve(href);

                URI cleanUri = new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), uri.getQuery(), null);

                synchronized (visitedURIs) {
                    if (!cleanUri.isOpaque() && !visitedURIs.contains(cleanUri)) {
                        visitedURIs.add(cleanUri);
                        synchronized (foundURIs) {
                            foundURIs.add(new URIinfo(cleanUri, depth + 1));
                        }
                        if (debugLevel > 0)
                            System.err.println("Adding URI: " + cleanUri.toString());
                    }
                }
            } catch (Exception e) {
                if (debugLevel > 1) {
                    System.err.println("Nalezeno nekorektní URI: " + href);
                }
            }
        }
    }

    @Override
    public void handleText(char[] data, int pos) {
        if (debugLevel > 1) {
            System.out.println("handleText: " + String.valueOf(data) + ", pos=" + pos);
        }

        String text = new String(data).toLowerCase();

        String[] words = text.split("[^a-zA-ZáčďéěíňóřšťúůýžÁČĎÉĚÍŇÓŘŠŤÚŮÝŽ]+");

        for (String word : words) {
            if (!word.isEmpty()) {
                wordCounts.computeIfAbsent(word, k -> new AtomicInteger(0)).incrementAndGet();
            }
        }
    }
}