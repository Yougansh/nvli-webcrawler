/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.nutch.indexer.more;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.nutch.metadata.Metadata;
import org.apache.nutch.net.protocols.HttpDateFormat;
import org.apache.nutch.net.protocols.Response;
import org.apache.nutch.parse.Parse;
import org.apache.nutch.indexer.IndexingFilter;
import org.apache.nutch.indexer.IndexingException;
import org.apache.nutch.indexer.NutchDocument;
import org.apache.nutch.crawl.CrawlDatum;
import org.apache.nutch.crawl.Inlinks;
import org.apache.nutch.parse.ParseData;
import org.apache.nutch.util.MimeUtil;
import org.apache.tika.Tika;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.Writable;

import java.text.ParseException;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.io.BufferedReader;
import java.io.IOException;
import java.net.URL;
import java.util.Date;
import java.util.regex.*;
import java.util.HashMap;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.DateUtils;

/**
 * Add (or reset) a few metaData properties as respective fields (if they are
 * available), so that they can be accurately used within the search index.
 * 
 * 'lastModifed' is indexed to support query by date, 'contentLength' obtains
 * content length from the HTTP header, 'type' field is indexed to support query
 * by type and finally the 'title' field is an attempt to reset the title if a
 * content-disposition hint exists. The logic is that such a presence is
 * indicative that the content provider wants the filename therein to be used as
 * the title.
 * 
 * Still need to make content-length searchable!
 * 
 * @author John Xing
 */

public class MoreIndexingFilter implements IndexingFilter {
	public static final Logger LOG = LoggerFactory
			.getLogger(MoreIndexingFilter.class);

	/** Get the MimeTypes resolver instance. */
	private MimeUtil MIME;
	private Tika tika = new Tika();

	/** Map for mime-type substitution */
	private HashMap<String, String> mimeMap = null;
	private boolean mapMimes = false;

	public NutchDocument filter(NutchDocument doc, Parse parse, Text url,
			CrawlDatum datum, Inlinks inlinks) throws IndexingException {

		String url_s = url.toString();

		addTime(doc, parse.getData(), url_s, datum);
		addLength(doc, parse.getData(), url_s);
		addType(doc, parse.getData(), url_s, datum);
		resetTitle(doc, parse.getData(), url_s);
		overrideFields(doc, parse.getData(), url_s, datum);
		urlPathDepth(url_s, doc);
		return doc;
	}

	
	/* Code for testing NVLI */
	public NutchDocument overrideFields(NutchDocument doc, ParseData data,
			String url, CrawlDatum datum) {
		String publishdate = "";
		
		if (data.getMeta("publishdate") != null) {
			publishdate = (data.getMeta("publishdate")).trim();

		} else if (data.getMeta("publishdate") == null)// added by vivek
		{

			if (data.getMeta("metatag.publishdate") != null) {
				publishdate = data.getMeta("metatag.publishdate");
			}
		} else {
			publishdate = data.getMeta(Metadata.LAST_MODIFIED);

		}
		String formattedDate = "";
		long time = -1;
		time = getTime(publishdate, url);

		SimpleDateFormat formatter = new SimpleDateFormat(
				"yyyy-MM-dd'T'HH:mm:ss'Z'");
		formattedDate = formatter.format(time);

		LOG.info("Time in override field: " + time + " > formatted >"
				+ formattedDate + " -- for URL: " + url);

		doc.add("publishedDate", formattedDate);

		return doc;
	}

	// Add time related meta info. Add last-modified if present. Index date as
	// last-modified, or, if that's not present, use fetch time.
	private NutchDocument addTime(NutchDocument doc, ParseData data,
			String url, CrawlDatum datum) {
		long time = -1;

		String lastModified = data.getMeta(Metadata.LAST_MODIFIED);
		if (lastModified != null) { // try parse last-modified
			time = getTime(lastModified, url); // use as time
												// store as string
			doc.add("lastModified", new Date(time));
		}

		if (time == -1) { // if no last-modified specified in HTTP header
			time = datum.getModifiedTime(); // use value in CrawlDatum
			if (time <= 0) { // if also unset
				time = datum.getFetchTime(); // use time the fetch took place
												// (fetchTime
												// of fetchDatum)
			}
		}

		// un-stored, indexed and un-tokenized
		doc.add("date", new Date(time));
		return doc;
	}

	private long getTime(String date, String url) {
		long time = -1;
		Date parsedDate = null;
		try {
			time = HttpDateFormat.toLong(date);
		} catch (ParseException e) {
			// try to parse it as date in alternative format
			try {
				SimpleDateFormat formatter = new SimpleDateFormat("MM/dd/yyyy");
				ParsePosition pp = new ParsePosition(0);
				parsedDate = formatter.parse(date, pp);

				if (parsedDate == null) {
					parsedDate = DateUtils.parseDate(date, new String[] {
							"EEE MMM dd HH:mm:ss yyyy",
							"EEE MMM dd HH:mm:ss yyyy zzz",
							"EEE MMM dd HH:mm:ss zzz yyyy",
							"EEE, MMM dd HH:mm:ss yyyy zzz",
							"EEE, dd MMM yyyy HH:mm:ss zzz",
							"EEE,dd MMM yyyy HH:mm:ss zzz", // Thu Feb 20
															// 10:31:00 IST 2014
							"EEE, dd MMM yyyy HH:mm:sszzz",// Thu, 20 Feb 2014
															// 11:34 PM
							"EEE, dd MMM yyyy HH:mm a",// Thu, 20 Feb 2014 11:34
														// PM
							"EEEE, MMM dd, yyyy HH:mm z",// Thu, 20 Feb 2014
															// 11:34 PM
															// jagran//EEEE, MMM
															// dd,
							"EEEE, dd MMM yyyy, hh:mm  z",// yyyy HH:mm:ss a
							"EEEE, dd MMM yyyy, HH:mm z", // Saturday, 14 Dec
															// 2013, 17:49 hrs
															// IST//times of
															// india
							"EEE, dd MMM yyyy HH:mm:ss",
							"EEE, dd-MMM-yy HH:mm:ss zzz",
							"yyyy/MM/dd HH:mm:ss.SSS zzz",
							"yyyy/MM/dd HH:mm:ss.SSS",// Fri, Feb 28th, 2014
							"yyyy/MM/dd HH:mm:ss zzz",
							"yyyy/MM/dd",
							"yyyy.MM.dd HH:mm:ss",
							"yyyy-MM-dd HH:mm",
							"MMM dd yyyy HH:mm:ss. zzz",// 18 Feb, 2014 ,
														// 09.43AM IST
							"MMM dd yyyy HH:mm:ss zzz",
							"dd.MM.yyyy HH:mm:ss zzz",
							"dd MM yyyy HH:mm:ss zzz",// 18 Feb, 2014 ,
							"dd.MM.yyyy; HH:mm:ss",
							"dd MMM, yyyy ,",
							"dd.MM.yyyy HH:mm:ss",// Feb 20, 2014, 07:15AM IST
							"dd.MM.yyyy zzz",
							"dd MMM, yyyy ,  hh.mma z",// 18 Feb, 2014 , 09.43AM
														// IST
							"yyyy-MM-dd'T'HH:mm:ss'Z'",// eg Friday, January 10,
														// 2014 22:22 IST
							"MMM dd, yyyy, HH:mm z",
							"MMM dd, yyyy, hh:mm a",
							"MMM dd, yyyy, hh:mma z", // Feb 20, 2014, 07:15AM
														// IST
							"MMM dd, yyyy, hh.mma z", // Feb 18, 2014, 10.39 AM
														// IST
							"MMM dd, yyyy, hh:mm a z", // Feb 17, 2014, 09.12PM
														// IST
							"MMMM dd, yyyy hh:mm z", // February 20, 2014 10:31
														// IST hindu
							"MMMM dd, yyyy",
							"dd MMMM yyyy hh:mm z", // 4 March 2014 15:09 IST
													// AajTak
							"MMMM dd, yyyy hh:mm a z", // March 4, 2014 12:12 PM
														// IST Khabar
							"MMMM dd, yyyy hh:mm:ss a", // March 5, 2014
														// 03:29:54 PM Freepress
														// Journal
							"MMM dd, yyyy, hh.mm a z", // Mar 5, 2014, 03.12 PM
														// IST Times of india
							"dd MMMM yyyy", // 4 March 2014
							"dd MMM yyyy, hh:mma z", // 05 Mar 2014, 11:59AM IST
							"MMMM dd, yyyy hh:mm a", // April 16, 2014 01:57 am
							"EEE MMM dd hh:mm:ss a yyyy", // Thu Feb 27 00:00:00
															// IST 2014
															// Rajasthan Patrika
							"dd MMM, yyyy, hh.mma z", // 6 Mar, 2014, 12.22PM
														// IST Economic Times
							"MM/dd/yyyy", // 2/27/2014 Rajasthan patrika
							"EEE dd, yyyy, hh:mm a z", // Mar 6, 2014, 12.00 AM
														// IST Mar 11, 2014,
														// 02.49 AM ISTMumbai
														// Mirror
							"EEE, dd MMM yyyy hh:mm:ss", // Thu, 06 Mar 2014
															// 13:39:50
															// Rajasthan Patrika
							"MMM yyyy, hh:mm:ss a", // Feb 2014, 05:27:48 PM
													// Jagran
							"EEE, dd MMM yyyy hh:mm a z", // Thu, 20 Feb 2014
															// 11:34 PM (IST)
															// Jagran
							"EEE, dd MMM yyyy hh:mm a", // Thu, 20 Feb 2014
														// 11:34 PM Jagran
							"MMM dd, yyyy, hh:mma z", // Feb 27, 2014, 08.53PM
														// IST Jagran Apr 11,
														// 2014, 13:50PM IST
							"MMM dd, yyyy, hh.mma z",
							"dd MMM yyyy, hh:mm:ss a", // 28 Feb 2014, 09:37:47
														// AM Jagran
							"MMMM dd, yyyy", // November 24, 2013
												// http://picmp.com
							"MMM dd", // Jun 08 http://picmp.com
							"EEEE, MMMM dd, yyyy, hh:mm z", // Tuesday, March
															// 04, 2014, 00:57
															// IST
															// dailynewsnetwork
							"MMMM-yyyy dd", // March-2014 11 live hindustan
							"MMM dd'th', yyyy", // Feb 27th, 2014
							"MMMM dd, yyyy", // June 22, 2013
												// http://www.digvijayasingh.in
							"dd MMMM, yyyy, hh:mm", // 5 March, 2014, 15:35
													// http://www.nationalherald.net
							"EEEE, dd MMMM yyyy", // Sunday, 02 March 2014
													// http://www.dailypioneer.com
							"MMMM dd, yyyy hh:mm z", // March 06, 2014 15:32 IST
														// http://www.ndtv.com
							"MMMM, yyyy", // February, 2005
							"dd-MM-yy hh:mm a",// 11-03-14 12:28 AM
							"HH:mm z(dd/MM/yyyy)",// 11:58 IST(11/3/2014)
							"dd MMM yyyy, hhmm 'hrs' z",// 18 Mar 2014, 1159 hrs
														// IST
							"EEEE, MMMM dd, yyyy, hh:mm",// Tuesday, March 25,
															// 2014, 19:22
							"dd MMM, yyyy, hhmm 'hrs' z", // 25 Aug, 2014, 0122
															// hrs IST
															// Maharashtra Times
							"MMM dd, yyyy, hh.mma 'hrs' z", // Aug 25, 2014,
															// 03.00AM IST
															// Maharashtra Times
							"dd MMM, yyyy, hh:mm", // 23 Aug, 2014, 00:02
													// Maharashtra Times
							"EEEE, dd MMMM yyyy" // Wednesday, 10 September 2014
													// e-Sakal

					});

				}
				time = parsedDate.getTime();

			} catch (Exception e2) {
				if (LOG.isWarnEnabled()) {
					LOG.warn(url + ": can't parse erroneous date: " + date);
				}
			}
		}
		return time;
	}

	public int urlPathDepth(String url, NutchDocument doc) {
		int lastIndex = 0;
		int count = 1;
		// int defaultPathDepth = getConf().getInt(DEFAULTURLPATHDEPTH, 10);
		URL urlForDepth;

		try {
			urlForDepth = new URL(url);
			String path = urlForDepth.getPath();
			if (path != null) {
				path = path.trim();
				if (path.isEmpty() || path.equalsIgnoreCase("/")) {
					// here count is one as for seed page we are considering
					// depth as one
					count = 1;
				} else {
					// here the count is 2 as we are considering urls after seed
					// on depth 2
					count = 2;
					while (lastIndex != -1) {
						lastIndex = path.indexOf("/", lastIndex + 1);
						if (lastIndex != -1) {
							count++;
						}
					}
				}
			}
		} catch (Exception exception) {

		}
		doc.add("depth", count - 1);
		return count - 1;
	}
	
	
	
	// Add Content-Length
	private NutchDocument addLength(NutchDocument doc, ParseData data,
			String url) {
		String contentLength = data.getMeta(Response.CONTENT_LENGTH);

		if (contentLength != null) {
			// NUTCH-1010 ContentLength not trimmed
			String trimmed = contentLength.toString().trim();
			if (!trimmed.isEmpty())
				doc.add("contentLength", trimmed);
		}
		return doc;
	}

	/**
	 * <p>
	 * Add Content-Type and its primaryType and subType add contentType,
	 * primaryType and subType to field "type" as un-stored, indexed and
	 * un-tokenized, so that search results can be confined by contentType or
	 * its primaryType or its subType.
	 * </p>
	 * <p>
	 * For example, if contentType is application/vnd.ms-powerpoint, search can
	 * be done with one of the following qualifiers
	 * type:application/vnd.ms-powerpoint type:application
	 * type:vnd.ms-powerpoint all case insensitive. The query filter is
	 * implemented in {@link TypeQueryFilter}.
	 * </p>
	 * 
	 * @param doc
	 * @param data
	 * @param url
	 * @return
	 */
	private NutchDocument addType(NutchDocument doc, ParseData data,
			String url, CrawlDatum datum) {
		String mimeType = null;
		String contentType = null;

		Writable tcontentType = datum.getMetaData().get(
				new Text(Response.CONTENT_TYPE));
		if (tcontentType != null) {
			contentType = tcontentType.toString();
		} else
			contentType = data.getMeta(Response.CONTENT_TYPE);
		if (contentType == null) {
			// Note by Jerome Charron on 20050415:
			// Content Type not solved by a previous plugin
			// Or unable to solve it... Trying to find it
			// Should be better to use the doc content too
			// (using MimeTypes.getMimeType(byte[], String), but I don't know
			// which field it is?
			// if (MAGIC) {
			// contentType = MIME.getMimeType(url, content);
			// } else {
			// contentType = MIME.getMimeType(url);
			// }

			mimeType = tika.detect(url);
		} else {
			mimeType = MIME.forName(MimeUtil.cleanMimeType(contentType));
		}

		// Checks if we solved the content-type.
		if (mimeType == null) {
			return doc;
		}

		// Check if we have to map mime types
		if (mapMimes) {
			// Check if the current mime is mapped
			if (mimeMap.containsKey(mimeType)) {
				// It's mapped, let's replace it
				mimeType = mimeMap.get(mimeType);
			}
		}

		contentType = mimeType;
		doc.add("type", contentType);

		// Check if we need to split the content type in sub parts
		if (conf.getBoolean("moreIndexingFilter.indexMimeTypeParts", true)) {
			String[] parts = getParts(contentType);

			for (String part : parts) {
				doc.add("type", part);
			}
		}

		// leave this for future improvement
		// MimeTypeParameterList parameterList = mimeType.getParameters()

		return doc;
	}

	/**
	 * Utility method for splitting mime type into type and subtype.
	 * 
	 * @param mimeType
	 * @return
	 */
	static String[] getParts(String mimeType) {
		return mimeType.split("/");
	}

	// Reset title if we see non-standard HTTP header "Content-Disposition".
	// It's a good indication that content provider wants filename therein
	// be used as the title of this url.

	// Patterns used to extract filename from possible non-standard
	// HTTP header "Content-Disposition". Typically it looks like:
	// Content-Disposition: inline; filename="foo.ppt"
	private Configuration conf;

	static Pattern patterns[] = { null, null };

	static {
		try {
			// order here is important
			patterns[0] = Pattern.compile("\\bfilename=['\"](.+)['\"]");
			patterns[1] = Pattern.compile("\\bfilename=(\\S+)\\b");
		} catch (PatternSyntaxException e) {
			// just ignore
		}
	}

	private NutchDocument resetTitle(NutchDocument doc, ParseData data,
			String url) {
		String contentDisposition = data.getMeta(Metadata.CONTENT_DISPOSITION);
		if (contentDisposition == null || doc.getFieldValue("title") != null)
			return doc;

		for (int i = 0; i < patterns.length; i++) {
			Matcher matcher = patterns[i].matcher(contentDisposition);
			if (matcher.find()) {
				doc.add("title", matcher.group(1));
				break;
			}
		}

		return doc;
	}

	public void setConf(Configuration conf) {
		this.conf = conf;
		MIME = new MimeUtil(conf);

		if (conf.getBoolean("moreIndexingFilter.mapMimeTypes", false) == true) {
			mapMimes = true;

			// Load the mapping
			try {
				readConfiguration();
			} catch (Exception e) {
				LOG.error(org.apache.hadoop.util.StringUtils
						.stringifyException(e));
			}
		}
	}

	public Configuration getConf() {
		return this.conf;
	}

	private void readConfiguration() throws IOException {
		BufferedReader reader = new BufferedReader(
				conf.getConfResourceAsReader("contenttype-mapping.txt"));
		String line;
		String parts[];

		mimeMap = new HashMap<String, String>();

		while ((line = reader.readLine()) != null) {
			if (StringUtils.isNotBlank(line) && !line.startsWith("#")) {
				line.trim();
				parts = line.split("\t");

				// Must be at least two parts
				if (parts.length > 1) {
					for (int i = 1; i < parts.length; i++) {
						mimeMap.put(parts[i].trim(), parts[0].trim());
					}
				}
			}
		}
	}
}
