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

package org.apache.nutch.indexer.basic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.util.Version;
import org.apache.mahout.classifier.naivebayes.BayesUtils;
import org.apache.mahout.classifier.naivebayes.NaiveBayesModel;
import org.apache.mahout.classifier.naivebayes.StandardNaiveBayesClassifier;
import org.apache.mahout.common.Pair;
import org.apache.mahout.common.iterator.sequencefile.SequenceFileIterable;
import org.apache.mahout.math.RandomAccessSparseVector;
import org.apache.mahout.math.Vector;
import org.apache.mahout.math.Vector.Element;
import org.apache.mahout.vectorizer.TFIDF;
import org.apache.nutch.metadata.Nutch;
import org.apache.nutch.parse.Parse;

import org.apache.nutch.indexer.IndexingFilter;
import org.apache.nutch.indexer.IndexingException;
import org.apache.nutch.indexer.NutchDocument;
import org.apache.nutch.util.StringUtil;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;

import org.apache.nutch.crawl.CrawlDatum;
import org.apache.nutch.crawl.Inlinks;

import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.hadoop.conf.Configuration;

import com.google.common.collect.ConcurrentHashMultiset;
import com.google.common.collect.Multiset;

/**
 * Adds basic searchable fields to a document. The fields added are : domain,
 * host, url, content, title, cache, tstamp domain is included depending on
 * {@code indexer.add.domain} in nutch-default.xml. title is truncated as per
 * {@code indexer.max.title.length} in nutch-default.xml. (As per NUTCH-1004, a
 * zero-length title is not added) content is truncated as per
 * {@code indexer.max.content.length} in nutch-default.xml.
 */
public class BasicIndexingFilter implements IndexingFilter {
	public static final Logger LOG = LoggerFactory
			.getLogger(BasicIndexingFilter.class);

	private static String MAHOUT_DOMAIN_DATA_PATH;
	private int MAX_TITLE_LENGTH;
	private int MAX_CONTENT_LENGTH;
	private boolean addDomain = false;
	private Configuration conf;

	/**
	 * The {@link BasicIndexingFilter} filter object which supports few
	 * configuration settings for adding basic searchable fields. See
	 * {@code indexer.add.domain}, {@code indexer.max.title.length},
	 * {@code indexer.max.content.length} in nutch-default.xml.
	 * 
	 * @param doc
	 *            The {@link NutchDocument} object
	 * @param parse
	 *            The relevant {@link Parse} object passing through the filter
	 * @param url
	 *            URL to be filtered for anchor text
	 * @param datum
	 *            The {@link CrawlDatum} entry
	 * @param inlinks
	 *            The {@link Inlinks} containing anchor text
	 * @return filtered NutchDocument
	 */
	public NutchDocument filter(NutchDocument doc, Parse parse, Text url,
			CrawlDatum datum, Inlinks inlinks) throws IndexingException {

		Text reprUrl = (Text) datum.getMetaData().get(
				Nutch.WRITABLE_REPR_URL_KEY);
		String reprUrlString = reprUrl != null ? reprUrl.toString() : null;
		String urlString = url.toString();

		String host = null;
		try {
			URL u;
			if (reprUrlString != null) {
				u = new URL(reprUrlString);
			} else {
				u = new URL(urlString);
			}
			/*
			 * if (addDomain) { doc.add("domain", URLUtil.getDomainName(u)); }
			 */

			/* For Domain Identification -- Added by LS Date: 27June2016 */
			String domain = getDomain(parse.getData().getTitle(),
					parse.getText());
			LOG.info("Domain Identified by ---- at Basic IndexingFilter: "
							+ domain);
			doc.add("domain", domain);
			host = u.getHost();
		} catch (MalformedURLException e) {
			throw new IndexingException(e);
		}

		if (host != null) {
			doc.add("host", host);
		}

		doc.add("url", reprUrlString == null ? urlString : reprUrlString);

		// content
		String content = parse.getText();
		if (MAX_CONTENT_LENGTH > -1 && content.length() > MAX_CONTENT_LENGTH) {
			content = content.substring(0, MAX_CONTENT_LENGTH);
		}
		doc.add("content", StringUtil.cleanField(content));

		// title
		String title = parse.getData().getTitle();
		if (MAX_TITLE_LENGTH > -1 && title.length() > MAX_TITLE_LENGTH) { // truncate
																			// title
																			// if
																			// needed
			title = title.substring(0, MAX_TITLE_LENGTH);
		}

		if (title.length() > 0) {
			// NUTCH-1004 Do not index empty values for title field
			doc.add("title", StringUtil.cleanField(title));
		}

		// add cached content/summary display policy, if available
		String caching = parse.getData().getMeta(Nutch.CACHING_FORBIDDEN_KEY);
		if (caching != null && !caching.equals(Nutch.CACHING_FORBIDDEN_NONE)) {
			doc.add("cache", caching);
		}

		// add timestamp when fetched, for deduplication
		doc.add("tstamp", new Date(datum.getFetchTime()));

		return doc;
	}

	/**
	 * Set the {@link Configuration} object
	 */
	public void setConf(Configuration conf) {
		this.conf = conf;
		this.MAX_TITLE_LENGTH = conf.getInt("indexer.max.title.length", 100);
		this.addDomain = conf.getBoolean("indexer.add.domain", false);
		this.MAX_CONTENT_LENGTH = conf.getInt("indexer.max.content.length", -1);
		MAHOUT_DOMAIN_DATA_PATH = conf.get("mahout.dir");
	}

	/**
	 * Get the {@link Configuration} object
	 */
	public Configuration getConf() {
		return this.conf;
	}

	public String getDomain(String title,String content)
  {
	// Domain Identification

		String mahoutDomainData = MAHOUT_DOMAIN_DATA_PATH;
		String modelDomainPath = mahoutDomainData + "/model";
		String labelDomainIndexPath = modelDomainPath + "/labelindex";
		String dictionaryDomainPath = mahoutDomainData
				+ "/data-vectors/dictionary.file-0";
		String documentDomainFrequencyPath = mahoutDomainData
				+ "/data-vectors/df-count/part-r-00000";
		String docsDomainPath = "lovey";
		String nbCategoryFromTitle = "";
		String nbCategoryFromContent = "";
		if (title != null
				&& title.equals("") == false) {
			nbCategoryFromTitle = classifyDoc(modelDomainPath,
					labelDomainIndexPath, dictionaryDomainPath,
					documentDomainFrequencyPath, docsDomainPath,
					title);
		}
		if (content != null
				&& content.equals("") == false) {
			nbCategoryFromContent = classifyDoc(modelDomainPath,
					labelDomainIndexPath, dictionaryDomainPath,
					documentDomainFrequencyPath, docsDomainPath,
					content);
		}
		if(nbCategoryFromTitle.equals(nbCategoryFromContent)){
			return nbCategoryFromContent; //Return anything
		}
		else
			return nbCategoryFromContent; //Return content domain only
		
  }

	// CATEGORIZATION FILTER Code Modules starts here!!
	// --date:Aug7,2015

	// domain identification using Naive bayes model....

	public static String classifyDoc(String modelPath, String labelIndexPath,
			String dictionaryPath, String documentFrequencyPath,
			String docsPath, String doc) {
		int bestCategoryId = -1;
		Map<Integer, String> labels = null;
		try {
			Configuration configuration = new Configuration();
			// model is a matrix (wordId, labelId) => probability score
			NaiveBayesModel model = NaiveBayesModel.materialize(new Path(
					modelPath), configuration);
			StandardNaiveBayesClassifier classifier = new StandardNaiveBayesClassifier(
					model);

			// labels is a map label => classId
			labels = BayesUtils.readLabelIndex(configuration, new Path(
					labelIndexPath));
			Map<String, Integer> dictionary = readDictionnary(configuration,
					new Path(dictionaryPath));
			Map<Integer, Long> documentFrequency = readDocumentFrequency(
					configuration, new Path(documentFrequencyPath));

			// analyzer used to extract word from docs
			Analyzer analyzer = new StandardAnalyzer(Version.LUCENE_34);

			int labelCount = labels.size();
			int documentCount = documentFrequency.get(-1).intValue();

			// Log.info("Number of labels: " + labelCount);
			/*
			 * for (int i = 0; i < labelCount; i++) Log.info("label " + i + ": "
			 * + labels.get(i));
			 */
			/*
			 * Log.info("Number of documents in training set: " +
			 * documentCount);
			 */
			// get string content to be classified

			Multiset<String> words = ConcurrentHashMultiset.create();

			// extract words from document
			TokenStream ts = analyzer
					.tokenStream("text", new StringReader(doc));
			CharTermAttribute termAtt = ts
					.addAttribute(CharTermAttribute.class);
			ts.reset();
			int wordCount = 0;

			while (ts.incrementToken()) {
				if (termAtt.length() > 0) {
					String word = ts.getAttribute(CharTermAttribute.class)
							.toString();
					Integer wordId = dictionary.get(word);
					// if the word is not in the dictionary, skip it
					if (wordId != null) {
						words.add(word);
						wordCount++;
					}
				}
			}

			// create vector wordId => weight using tfidf
			RandomAccessSparseVector vector = new RandomAccessSparseVector(
					10000);
			TFIDF tfidf = new TFIDF();
			for (Multiset.Entry<String> entry : words.entrySet()) {
				String word = entry.getElement();
				int count = entry.getCount();
				Integer wordId = dictionary.get(word);
				Long freq = documentFrequency.get(wordId);
				double tfIdfValue = tfidf.calculate(count, freq.intValue(),
						wordCount, documentCount);
				vector.setQuick(wordId, tfIdfValue);
			}

			// With the classifier, we get one score for each label
			// The label with the highest score is the one the tweet is more
			// likely to
			// be associated to
			Vector resultVector = classifier.classifyFull(vector);
			double bestScore = -Double.MAX_VALUE;

			// for(Element element:resultVector)
			for (int i = 0; i < resultVector.size(); i++) {
				Element element = resultVector.getElement(i);
				int categoryId = element.index();
				double score = element.get();
				if (score > bestScore) {
					bestScore = score;
					bestCategoryId = categoryId;
				}
				//LOG.info("scores  " + labels.get(categoryId) + ": " + score);
			}
			//LOG.info("category => " + labels.get(bestCategoryId));

		} catch (Exception e) {
			e.printStackTrace();
		}

		return labels.get(bestCategoryId);
	}

	public static Map<Integer, Long> readDocumentFrequency(Configuration conf,
			Path documentFrequencyPath) {
		Map<Integer, Long> documentFrequency = new HashMap<Integer, Long>();
		for (Pair<IntWritable, LongWritable> pair : new SequenceFileIterable<IntWritable, LongWritable>(
				documentFrequencyPath, true, conf)) {
			documentFrequency
					.put(pair.getFirst().get(), pair.getSecond().get());
		}
		return documentFrequency;
	}

	public static Map<String, Integer> readDictionnary(Configuration conf,
			Path dictionnaryPath) {
		Map<String, Integer> dictionnary = new HashMap<String, Integer>();
		for (Pair<Text, IntWritable> pair : new SequenceFileIterable<Text, IntWritable>(
				dictionnaryPath, true, conf)) {
			dictionnary.put(pair.getFirst().toString(), pair.getSecond().get());
		}
		return dictionnary;
	}
	// ENDS HERE
}
