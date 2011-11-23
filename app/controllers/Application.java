package controllers;

import play.*;
import play.mvc.*;

import java.util.*;
import org.ads.solr.RegexQueryTest;

import models.*;

public class Application extends Controller {

    public static void index() {
        String pattern = params.get("q");
        if (pattern != null) {
            String indexPath = "/proj/adszee/solr4ads/solr/fulltext-regex/data/index/";
            RegexQueryTest rqt = new RegexQueryTest(indexPath);
            RegexQueryTest.RegexQueryResult results = null;
            try {
				results = rqt.regexQuery(pattern);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
            renderArgs.put("q", pattern);
            renderArgs.put("results", results.results);
            renderArgs.put("duration", results.duration);
            renderArgs.put("hits", results.totalHits);
        } 
        render();
    }

}
