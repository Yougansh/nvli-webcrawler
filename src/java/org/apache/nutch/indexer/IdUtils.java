/**
 * 
 */
package org.apache.nutch.indexer;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import org.apache.commons.httpclient.URIException;
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
	private static String ENEWS_WEBSITES_SPECIFIC_LIST;
	private static ArrayList<String> eNewsSpecificWebsites = new ArrayList<String>();
	private static String GOVW_WEBSITES_SPECIFIC_LIST;
	private static ArrayList<String> govwSpecificWebsites = new ArrayList<String>();

	public static String IDENTIFIER;
	public static String RESOURCETYPE;
	public static String IDENTIFIED_HOSTNAME;

	static {
		sdf = new SimpleDateFormat("yyyyddMMhhmmss");

	}

	public static void getIdentifier(String cleanedurl, Configuration conf) {
		if (IdUtils.isEnewsWebsite(cleanedurl, conf)) {

			IDENTIFIER = "ENEW_" + cleanedurl;RESOURCETYPE="ENEW";

		} else if (IdUtils.isGovtWebsite(cleanedurl, conf)) {

			IDENTIFIER = "GOVW_" + cleanedurl;RESOURCETYPE="GOVW";

		} else {

			IDENTIFIER = "UDEF_" + cleanedurl;RESOURCETYPE="UDEF";

		}
		IDENTIFIER+="_"+sdf.format(new Date());
	}

	public static void getHostname(String resourcetype, URL host, Configuration conf) throws URISyntaxException {
		if (resourcetype.equals("ENEW")) {
			if (eNewsSpecificWebsites.isEmpty()) {
				ENEWS_WEBSITES_SPECIFIC_LIST = conf.get("enewswebsites.list.specificenews");
				System.out.println("ENEWS_WEBSITES_SPECIFIC_LIST:  " + ENEWS_WEBSITES_SPECIFIC_LIST);
				String[] spiltstring = ENEWS_WEBSITES_SPECIFIC_LIST.split(":");

				for (String s : spiltstring) {
					System.out.println("Indexing Filterchecker spiltstring: " + s);
					eNewsSpecificWebsites.add(s.toUpperCase());
				}
			}

			boolean flag = false;
			for (String s : eNewsSpecificWebsites) {
				System.out.println("eNewsSpecificWebsites in IF 316" + s);

				if (host.getHost().toUpperCase().contains("."+s+".")) // Select Hostname
																// from //
																// predefined
																// list of//
																// urls (from
																// nutch-site.xml)
				{
					System.out.println("HOST URL in IF" + host.toURI().toString());

					flag = true;
					IDENTIFIED_HOSTNAME = s.toUpperCase();
					break;
				}
			}
			if (!flag) {
				System.out
						.println("for url" + host.toURI().toString() + " flag is still false, hence 2nd if is called");
				IDENTIFIED_HOSTNAME = host.getHost().substring(0, host.getHost().indexOf('.')).toUpperCase();
			}
		}
		else
			if (resourcetype.equals("GOVW")) {
				if (govwSpecificWebsites.isEmpty()) {
					GOVW_WEBSITES_SPECIFIC_LIST = conf.get("govwebsites.list.specificgovt");
					System.out.println("GOVW_WEBSITES_SPECIFIC_LIST:  " + GOVW_WEBSITES_SPECIFIC_LIST);
					String[] spiltstring = GOVW_WEBSITES_SPECIFIC_LIST.split(":");

					for (String s : spiltstring) {
						System.out.println("Indexing Filterchecker spiltstring: " + s);
						govwSpecificWebsites.add(s.toUpperCase());
					}
				}

				boolean flag = false;
				for (String s : govwSpecificWebsites) {
					System.out.println("govwSpecificWebsites in IF 316" + s);

					if (host.getHost().toUpperCase().contains("."+s+".")) // Select Hostname
																	// from //
																	// predefined
																	// list of//
																	// urls (from
																	// nutch-site.xml)
					{
						System.out.println("HOST URL in IF" + host.toURI().toString());

						flag = true;
						IDENTIFIED_HOSTNAME = s.toUpperCase();
						break;
					}
				}
				if (!flag) {
					System.out
							.println("for url" + host.toURI().toString() + " flag is still false, hence 2nd if is called");
					IDENTIFIED_HOSTNAME = host.getHost().substring(0, host.getHost().indexOf('.')).toUpperCase();
				}
			}
			else
			{
				//No hostname for UDEF
				IDENTIFIED_HOSTNAME="UNDEFINED";
			}

	}

	public static boolean isGovtWebsite(String url_in, Configuration conf) {
		if (govtWebsiteDomains.isEmpty()) {
			GOVT_WEBSITES_LIST_LOCATION = conf.get("govtwebsites.list");
			//System.out.println("GOVT_WEBSITES_LIST_LOCATION: " + GOVT_WEBSITES_LIST_LOCATION);
			String[] spiltstring = GOVT_WEBSITES_LIST_LOCATION.split(":");
			for (String s : spiltstring) {
				System.out.println("IDUtils GOVT_LIST spiltstring: " + s);
				govtWebsiteDomains.add(s);
			}
		}
		for (String domain : govtWebsiteDomains) {
			if (url_in.toLowerCase().contains("_" + domain.toLowerCase()))
				return true;
		}
		return false;
	}

	public static boolean isEnewsWebsite(String url_in, Configuration conf) {
		if (eNewsWebsite.isEmpty()) {
			ENEWS_WEBSITES_LIST_LOCATION = conf.get("enewswebsites.list");
			//System.out.println("ENEWS_WEBSITES_LIST_LOCATION: " + ENEWS_WEBSITES_LIST_LOCATION);
			String[] spiltstring = ENEWS_WEBSITES_LIST_LOCATION.split(":");
			for (String s : spiltstring) {
				System.out.println("IDUtils ENEWS_LIST spiltstring: " + s);
				eNewsWebsite.add(s);
			}
		}

		for (String domain : eNewsWebsite) {
			if (url_in.toLowerCase().contains(domain.toLowerCase()))
				return true;
		}
		return false;
	}

	public static String cleanUrls(URL url) {

		String cleanedurl = url.getHost() + url.getFile();
		// http://timesofindia.indiatimes.com/enews/article/121224ss12.htm
		cleanedurl = cleanedurl.replaceAll("http://", "").replaceAll("https://", "").replaceAll("www", "")
				.replaceAll("/", ".").replaceAll("_", "").replaceAll(".html", "").replaceAll(".htm", "")
				.replaceAll(".php", "").replaceAll(".jsp", "").replaceAll(".aspx", "");
		cleanedurl = cleanedurl.replace('.', '_').replaceAll("\\?", "_").replaceAll("=", "_");
		if (cleanedurl.startsWith("_")) {
			cleanedurl = cleanedurl.replaceFirst("_", "");
		}
		if (cleanedurl.endsWith("_")) {
			cleanedurl = cleanedurl.substring(0, cleanedurl.length() - 1);
		}
		// System.out.println("Cleaned Url: " + cleanedurl);
		// cleanedurl += "_" + sdf.format(new Date());
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

		System.out.println("Clean URL=" + IdUtils.cleanUrls(u));
	}

}
