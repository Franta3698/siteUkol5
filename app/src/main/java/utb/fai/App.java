package utb.fai;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLConnection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.text.html.parser.ParserDelegator;

public class App {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Missing parameter - start URL");
            return;
        }

        LinkedList<URIinfo> foundURIs = new LinkedList<>();
        HashSet<URI> visitedURIs = new HashSet<>();
        ConcurrentHashMap<String, AtomicInteger> wordCounts = new ConcurrentHashMap<>();

        int maxDepth = 2;
        if (args.length > 1) {
            try {
                maxDepth = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Neplatný formát maxDepth, používá se výchozí: 2");
            }
        }

        int debugLevel = 0;
        if (args.length > 2) {
            try {
                debugLevel = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {

            }
        }

        try {
            String startUrlStr = args[0];
            if (!startUrlStr.endsWith("/")) {
                startUrlStr += "/";
            }
            URI uri = new URI(startUrlStr);
            foundURIs.add(new URIinfo(uri, 0));
            visitedURIs.add(uri);

            int poolSize = Runtime.getRuntime().availableProcessors() * 2;
            ExecutorService executor = Executors.newFixedThreadPool(poolSize);
            AtomicInteger activeTasks = new AtomicInteger(0);


            while (true) {
                URIinfo currentInfo = null;
                synchronized (foundURIs) {
                    if (!foundURIs.isEmpty()) {
                        currentInfo = foundURIs.removeFirst();
                    }
                }

                if (currentInfo == null) {
                    if (activeTasks.get() == 0) {
                        break;
                    }
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        break;
                    }
                    continue;
                }

                final URIinfo infoToProcess = currentInfo;
                activeTasks.incrementAndGet();

                final int fMaxDepth = maxDepth;
                final int fDebugLevel = debugLevel;

                executor.submit(() -> {
                    try {
                        if (fDebugLevel > 0) {
                            System.err.println("Analyzing " + infoToProcess.uri);
                        }

                        URLConnection connection = infoToProcess.uri.toURL().openConnection();
                        connection.setConnectTimeout(5000);
                        connection.setReadTimeout(5000);


                        String charset = "UTF-8";
                        String contentType = connection.getHeaderField("Content-Type");
                        if (contentType != null) {
                            for (String param : contentType.replace(" ", "").split(";")) {
                                if (param.startsWith("charset=")) {
                                    charset = param.substring(8);
                                    break;
                                }
                            }
                        }

                        ParserCallback callBack = new ParserCallback(visitedURIs, foundURIs, wordCounts);
                        callBack.depth = infoToProcess.depth;
                        callBack.maxDepth = fMaxDepth;
                        callBack.pageURI = infoToProcess.uri;
                        callBack.debugLevel = fDebugLevel;

                        ParserDelegator parser = new ParserDelegator();
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), charset))) {
                            parser.parse(reader, callBack, true);
                        }

                    } catch (Exception e) {
                        if (fDebugLevel > 0) {
                            System.err.println("Error loading page " + infoToProcess.uri + " - " + e.getMessage());
                        }
                    } finally {
                        activeTasks.decrementAndGet();
                    }
                });
            }

            executor.shutdown();

        } catch (Exception e) {
            System.err.println("Zachycena neošetřená výjimka, končíme...");
            e.printStackTrace();
        }

        wordCounts.entrySet().stream()
                .sorted((e1, e2) -> Integer.compare(e2.getValue().get(), e1.getValue().get()))
                .limit(20)
                .forEach(entry -> System.out.println(entry.getKey() + ";" + entry.getValue().get()));
    }
}