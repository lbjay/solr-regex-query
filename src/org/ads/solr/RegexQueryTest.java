package org.ads.solr;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.*;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.regex.JavaUtilRegexCapabilities;
import org.apache.lucene.search.regex.RegexQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

public class RegexQueryTest {
	
	private JavaUtilRegexCapabilities capabilities = new JavaUtilRegexCapabilities();
	private IndexSearcher searcher;
	private static final int MAX_HITS = 1000;

	public RegexQueryTest(String indexPath) {
		super();
		Directory dir;
		IndexReader reader;
		try {
			dir = FSDirectory.open(new File(indexPath));
			reader = IndexReader.open(dir, true);
			this.searcher = new IndexSearcher(reader);
		} catch (CorruptIndexException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public String[] getTextFrag(Document doc, String pattern) {
		String[] frags = new String[10];
		String body = doc.get("body");
		Pattern p = Pattern.compile(pattern, Pattern.MULTILINE);
		Matcher m = p.matcher(body);
		int i = 0;
		while (m.find()) {
			int start = m.start();
			int end = m.end();
			String match = body.substring(start, end);
			int prefixStart = (start - 30) <= 0 ? 0 : start - 30;
			int postfixEnd = (end + 30) >= body.length() ? body.length() : end + 30;
			String prefix = body.substring(prefixStart, start);
			String postfix = body.substring(end, postfixEnd);
			String frag = prefix + " <em>" + match + "</em> " + postfix;
			if (i < frags.length) { 
				frags[i++] = frag;
			}
		}
		return frags;
	}
	
	public RegexQueryResult regexQuery(String pattern) throws IOException {
		// create the query
		Term t = new Term("body", pattern);
		RegexQuery q = new RegexQuery(t);
		q.setRegexImplementation(this.capabilities);
		System.out.println("query object: " + q);
		System.out.println("stats: " + this.searcher.getIndexReader().numDocs());
		
		// do the search
		long startTime = System.currentTimeMillis();
		TopDocs docs = this.searcher.search(q, MAX_HITS);
		long stopTime = System.currentTimeMillis();
		System.out.println("total: " + docs.totalHits);
		System.out.println("scoredocs: " + docs.scoreDocs.length);
		
		// calc the runtime
		long totalTime = stopTime - startTime;
		String runTime = String.format("%d min, %d sec", 
		    TimeUnit.MILLISECONDS.toMinutes(totalTime),
		    TimeUnit.MILLISECONDS.toSeconds(totalTime) - 
		    TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(totalTime))
		);
		System.out.println("Run time: " + runTime);
		
		// build the snippets
		HashMap<String,String[]> hits = new HashMap<String,String[]>();
		for (ScoreDoc match : docs.scoreDocs) {
			Document doc = searcher.doc(match.doc);
			String bibcode = doc.get("bibcode");
			hits.put(bibcode, getTextFrag(doc, pattern));
		}
		
		// build the result object
		RegexQueryResult results = new RegexQueryResult();
		results.setResults(hits);
		results.setDuration(runTime);
		results.setTotalHits(docs.totalHits);
		return results;
	}
		
	public class RegexQueryResult {
		public String duration;
		public int totalHits;
		public HashMap<String,String[]> results;
		public String getDuration() {
			return duration;
		}
		public void setDuration(String duration) {
			this.duration = duration;
		}
		public int getTotalHits() {
			return totalHits;
		}
		public void setTotalHits(int totalHits) {
			this.totalHits = totalHits;
		}
		public HashMap<String, String[]> getResults() {
			return results;
		}
		public void setResults(HashMap<String, String[]> results) {
			this.results = results;
		}
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			System.out.println("Query: " + args[0]);
			RegexQueryTest rqt = new RegexQueryTest(args[1]);
			RegexQueryResult results = rqt.regexQuery(args[0]);
			for (String k : results.results.keySet()) {
				String[] hits = results.results.get(k);
				System.out.println(k + ": " + hits.length);
//				for (String h : hits) {
//					if (h != null) 
//						System.out.println(h);
//				}
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
