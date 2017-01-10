/**
 * 
 */
package org.apache.nutch.indexer;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import org.apache.hadoop.conf.Configuration;

/**
 * @author Lovey
 * @Date June 22, 2016
 * @Description --Utility class for a URL
 * @LastModifications -- NaN
 * 
 */
public class IdUtils {

	/* List of URLS based on 2 documents in package Class */
	private static ArrayList<String> govtWebsiteDomains = new ArrayList<>();
	private static ArrayList<String> eNewsWebsite = new ArrayList<String>();
	private static SimpleDateFormat sdf;
	private static String GOVT_WEBSITES_LIST_LOCATION;
	private static String ENEWS_WEBSITES_LIST_LOCATION;

	static {
		sdf = new SimpleDateFormat("yyyyddMMhhmm");

	}

	public static boolean isGovtWebsite(String url, Configuration conf) {
		if (govtWebsiteDomains.isEmpty()) {
			GOVT_WEBSITES_LIST_LOCATION = conf.get("govtwebsites.list");
			String[] spiltstring = GOVT_WEBSITES_LIST_LOCATION.split("|");
			for (String s : spiltstring)
				govtWebsiteDomains.add(s);
		}
		for (String domain : govtWebsiteDomains) {
			if (url.toLowerCase().contains(domain.toLowerCase()))
				return true;
		}
		return false;
	}

	public static boolean isEnewsWebsite(String url, Configuration conf) {
		if (eNewsWebsite.isEmpty()) {
			ENEWS_WEBSITES_LIST_LOCATION = conf.get("enewswebsites.list");
			String[] spiltstring = ENEWS_WEBSITES_LIST_LOCATION.split("|");
			for (String s : spiltstring)
				govtWebsiteDomains.add(s);
		}

		for (String domain : eNewsWebsite) {
			if (url.toLowerCase().contains(domain.toLowerCase()))
				return true;
		}
		return false;
	}

	public static String cleanUrls(String urlstring) {

		String cleanedurl = urlstring;
		// http://timesofindia.indiatimes.com/enews/article/121224ss12.htm
		cleanedurl = cleanedurl.replaceAll("http://", "").replaceAll("https://", "").replaceAll("www", "")
				.replaceAll("/", ".").replaceAll("_", "").replaceAll(".html", "").replaceAll(".htm", "")
				.replaceAll(".php", "").replaceAll(".jsp", "").replaceAll(".aspx", "");
		cleanedurl = cleanedurl.replace('.', '_');
		if (cleanedurl.startsWith("_")) {
			cleanedurl = cleanedurl.replaceFirst("_", "");
		}
		if (cleanedurl.endsWith("_")) {
			cleanedurl = cleanedurl.substring(0, cleanedurl.length() - 1);
		}
		// System.out.println("Cleaned Url: " + cleanedurl);
		cleanedurl += "_" + sdf.format(new Date());
		cleanedurl = cleanedurl.toUpperCase();
		return cleanedurl;
	}

	public static URL getUrl(String url) {
		try {
			return new URL(url);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public static void main(String[] args) {

		String URL = "http://www.arunachalfront.info/columnist.php?topic=109";
		URL u = getUrl(URL);
		System.out.println("Passed url: " + URL);
		System.out.println("protocol = " + u.getProtocol()); // http
		System.out.println("authority = " + u.getAuthority()); // example.com:80
		System.out.println("host = " + u.getHost()); // example.com
		System.out.println("port = " + u.getPort()); // 80
		System.out.println("path = " + u.getPath()); // /docs/books/tutorial/index.html
		System.out.println("query = " + u.getQuery()); // name=networking
		System.out.println("filename = " + u.getFile()); /// docs/books/tutorial/index.html?name=networking
		System.out.println("ref = " + u.getRef()); // DOWNLOADING

		System.out.println("Clean URL=" + IdUtils.cleanUrls(URL));
	}

}
