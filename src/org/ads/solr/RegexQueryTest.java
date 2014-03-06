package org.ads.solr;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.*;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.NumericRangeFilter;
import org.apache.lucene.search.NumericRangeQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.RegexpQuery;
import org.apache.lucene.search.vectorhighlight.*;
import org.apache.lucene.store.*;
import org.apache.lucene.util.Version;
import org.apache.solr.core.SolrConfig;
import org.apache.solr.core.StandardIndexReaderFactory;
import org.apache.solr.schema.FieldType;
import org.apache.solr.schema.IndexSchema;
import org.apache.solr.schema.SchemaField;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class RegexQueryTest {
	
	private IndexSearcher searcher;
	private static final int MAX_HITS = 1000;
	private IndexSchema schema;

	public RegexQueryTest(String indexPath) {
		super();
		System.setProperty("solr.solr.home", "/proj/adszee/solr4ads/solr/fulltext");
		
		try {
			Directory dir = FSDirectory.open(new File(indexPath));
			StandardIndexReaderFactory rfact = new StandardIndexReaderFactory();
			IndexReader reader = rfact.newReader(dir);
			this.searcher = new IndexSearcher(reader);
		} catch (CorruptIndexException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		try {
			FileInputStream fis = new FileInputStream(new File("/proj/adszee/solr4ads/solr/conf/solrconfig.xml"));
			InputSource is = new InputSource(fis);
			SolrConfig conf = new SolrConfig("fulltext", is);
			fis.close();
			fis = new FileInputStream(new File("/proj/adszee/solr4ads/solr/conf/schema.xml"));
			is = new InputSource(fis);
			this.schema = new IndexSchema(conf, "fulltext", is);
			fis.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ParserConfigurationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (SAXException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	

	public String[] getTextFrag(Document doc, String pattern) {
		String[] frags = new String[10];
		String body = doc.get("body");
		body = body.replaceAll("-?\\s+-?", "-");
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
	
	public String calcRunTime(long start, long stop) {
		// calc the runtime
		long totalTime = stop - start;
		return String.format("%d min, %d sec", 
		    TimeUnit.MILLISECONDS.toMinutes(totalTime),
		    TimeUnit.MILLISECONDS.toSeconds(totalTime) - 
		    TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(totalTime)));
	}
		
	public RegexQueryResult regexQuery(String pattern) throws IOException {
		// create the query
		Term t = new Term("body", pattern);
		RegexpQuery q = new RegexpQuery(t);
		System.out.println("query object: " + q);
		System.out.println("stats: " + this.searcher.getIndexReader().numDocs());
		
		// combine with a numeric id range to help performance
		NumericRangeFilter idFilter = NumericRangeFilter.newIntRange("id", 32, 8000000, 10000000, true, true);
//		FieldCacheRangeFilter fcrf = FieldCacheRangeFilter.newIntRange("id", 8000000, 10000000, true, true);
		System.out.println("filter: " + idFilter);
		
        FieldType ft = this.schema.getFieldType("id");
        SchemaField idField = schema.getField("id");
        System.out.println("field type: " + ft);
        System.out.println("id field: " + idField);
	       
		// do the search
		long startTime = System.currentTimeMillis();
//		TopDocs docs = this.searcher.search(q, idFilter, MAX_HITS);
//		TopDocs docs = this.searcher.search(q, fcrf, MAX_HITS);
		TopDocs docs = this.searcher.search(q, MAX_HITS);
		long stopTime = System.currentTimeMillis();
		System.out.println("total: " + docs.totalHits);
		System.out.println("scoredocs: " + docs.scoreDocs.length);
		
		String runTime = calcRunTime(startTime, stopTime);
		System.out.println("Run time: " + runTime);
		
		FastVectorHighlighter fvh = new FastVectorHighlighter();
		FieldQuery fq = fvh.getFieldQuery(q);
		System.out.println("FieldQuery: " + fq);
		
		// build the snippets
		HashMap<String,String[]> hits = new HashMap<String,String[]>();
		for (ScoreDoc match : docs.scoreDocs) {
			Document doc = searcher.doc(match.doc);
			String bibcode = doc.get("bibcode");
//			hits.put(bibcode, getTextFrag(doc, pattern));
			hits.put(bibcode, new String[0]);
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
