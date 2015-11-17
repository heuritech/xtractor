/*
 *  
 *  TL;DRzr - A simple algorithmic summarizer
 *  Website: http://tldrzr.com
 *  Author: Saurav Mohapatra (mohaps@gmail.com)
 *  
 *  Copyright (c) 2013, Saurav Mohapatra
 *  All rights reserved.
 *  
 *  
 *  
 *  Redistribution and use in source and binary forms, with or without modification, are permitted 
 *  provided that the following conditions are met:
 *  
 *  	a)  Redistributions of source code must retain the above copyright notice, 
 *  		this list of conditions and the following disclaimer.
 *  
 *  	b)  Redistributions in binary form must reproduce the above copyright notice, 
 *  		this list of conditions and the following disclaimer in the documentation 
 *  		and/or other materials provided with the distribution.
 *  	
 *  	c)  Neither the name of TL;DRzr nor the names of its contributors may be used 
 *  		to endorse or promote products derived from this software without specific 
 *  		prior written permission.
 *  
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, 
 *  BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT 
 *  SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL 
 *  DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) 
 *  HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) 
 *  ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.mohaps.tldr.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;

import com.mohaps.tldr.summarize.Defaults;
import com.mohaps.tldr.summarize.IStopWords;
import com.mohaps.tldr.summarize.ITokenizer;

import java.io.*;

import org.tartarus.snowball.SnowballStemmer;
import org.tartarus.snowball.ext.englishStemmer;
/**
 * Utility methods to use for word operations
 * Will try to use OpenNLP by default, failing that will fall back to regex based manipulation/extraction
 * @author mohaps
 *
 */
public final class Words {
	private static SentenceModel SENTENCE_MODEL;
	static {
		try {
			InputStream inputFile = Words.class.getClassLoader()
					.getResourceAsStream("en-sent.bin");
			if (inputFile != null) {
				try {
					SENTENCE_MODEL = new SentenceModel(inputFile);
					System.out.println(">> OpenNLP Sentence Model loaded!");
				} finally {
					if (inputFile != null) {
						try {
							inputFile.close();
						} catch (Throwable t) {
						}
					}
				}
			}
		} catch (IOException ex) {
			System.err
					.println("Failed to load sentence model for OpenNLP. error = "
							+ ex.getLocalizedMessage()
							+ ". Will fall back to regex based sentence parsing");
			ex.printStackTrace();
		}
	}

	private static class Word {
		private String word;
		private int frequency;

		public Word(String word) {
			this.word = word.toLowerCase();
			this.frequency = 1;
		}

		public String getWord() {
			return word;
		}

		public int getFrequency() {
			return frequency;
		}

		public int increment() {
			return ++frequency;
		}

		public int hashCode() {
			return word.hashCode();
		}

		public String toString() {
			return new StringBuilder(word).append("(").append(frequency)
					.append(")").toString();
		}
	}
	public static final Set<String> getMostFrequent(String input,
			ITokenizer tokenizer, IStopWords stopWords, int maxCount,
			int minimumOccurences) throws Exception {

		HashMap<String, Word> words = new HashMap<String, Word>();
		ArrayList<Word> wordList = new ArrayList<Word>();
		String[] wordTokens = tokenizer.tokenize(input);
		SnowballStemmer stemmer = new englishStemmer();
		for (int i = 0; i < wordTokens.length; i++) {
			if(isWord(wordTokens[i]) && wordTokens[i].length() > 4) {
				stemmer.setCurrent(wordTokens[i]);
				stemmer.stem();
				String wordToken = stemmer.getCurrent();
				if (isWord(wordToken) && !stopWords.isStopWord(wordToken) && wordToken.length() > 4) {
					Word w = words.get(wordToken);
					if (w != null) {
						w.increment();
					} else {
						w = new Word(wordToken);
						words.put(wordToken, w);
						wordList.add(w);
					}
				}
			}
		}
		Collections.sort(wordList, new Comparator<Word>() {

			public int compare(Word w1, Word w2) {
				if (w1.getFrequency() > w2.getFrequency()) {
					return -1;
				} else if (w1.getFrequency() < w2.getFrequency()) {
					return 1;
				} else {
					String s1 = w1.getWord();
					String s2 = w2.getWord();

					for (int i = 0; i < s1.length() && i < s2.length(); i++) {
						if (s1.charAt(i) > s2.charAt(i)) {
							return -1;
						} else if (s1.charAt(i) < s2.charAt(i)) {
							return 1;
						}
					}

					if (s1.length() > s2.length()) {
						return -1;
					} else if (s1.length() < s2.length()) {
						return 1;
					} else {
						return 0;
					}
				}

			}

		});
		HashSet<String> ret = new HashSet<String>();
		Iterator<Word> iter = wordList.iterator();
		while (iter.hasNext() && ret.size() <= maxCount) {
			Word w = iter.next();
			if(w.getFrequency() >= minimumOccurences) {
				ret.add(w.getWord());
			}
		}
		return ret;
	}

	public static final boolean isWord(String word) {
		return (word != null && word.trim().length() > 0);
	}

	public static Set<String> parseSentences(String input,
			ITokenizer tokenizer, int minimumWordsInASentence) throws Exception {
		if (SENTENCE_MODEL != null) {
			return parseSentencesNLP(input, tokenizer, minimumWordsInASentence);
		} else {
			return parseSentencesRegEx(input, tokenizer,
					minimumWordsInASentence);
		}
	}

	public static Set<String> parseSentencesNLP(String input,
			ITokenizer tokenizer, int minimumWordsInASentence) throws Exception {
		SentenceDetectorME sentenceDetector = new SentenceDetectorME(
				SENTENCE_MODEL);
		String[] rawSentences = sentenceDetector.sentDetect(input);
		HashSet<String> sentences = new HashSet<String>();
		for (int i = 0; i < rawSentences.length; i++) {
			String rawSentence = rawSentences[i];
			String[] words = tokenizer.tokenize(rawSentence);
			if (words.length >= minimumWordsInASentence) {
				sentences.add(rawSentence);
			}
		}
		return sentences;
	}

	public static Set<String> parseSentencesRegEx(String input,
			ITokenizer tokenizer, int minimumWordsInASentence) throws Exception {
		String[] rawSentences = input.split(Defaults.REGEX_SENTENCES);
		HashSet<String> sentences = new HashSet<String>();
		for (int i = 0; i < rawSentences.length; i++) {
			String rawSentence = rawSentences[i];
			String[] words = tokenizer.tokenize(rawSentence);
			if (words.length >= minimumWordsInASentence) {
				sentences.add(rawSentence);
			}
		}
		return sentences;
		
	}

	public static final String replaceSmartQuotes(String s) {
		return s.replace('\u2018', '\'')
				.replace('\u2019', '\'')
				.replace('\u201c', '\"')
				.replace('\u201b', '\'')
				.replace('\u201d', '\"')
				.replace('\u2026', '-')
				.replace('\u2013', '-')
				.replace('\u2014', '-')
				.replaceAll("&#8211;", "-")				
				.replaceAll("&#8220;", "\"")
				.replaceAll("&#8221;", "\"")
				.replaceAll("&#8216;", "\'")
				.replaceAll("&#8217;", "\'")
				.replaceAll("&#8219;", "\'")
				.replaceAll("&#039;", "\'")
				.replaceAll("&#8230;", "...")				
				.replaceAll("&#8212;", "-");
	}
	
	public static void main(String[] args) {
		String s = "-than estimated by Umeng-";
		for(int i = 0; i < s.length(); i++){
			System.out.println(">> Char Code "+(short)s.charAt(i)+" (0x"+Integer.toHexString((short)s.charAt(i))+") - {"+s.charAt(i)+"}");
		}
	}

	//TODO: ugly hack. find something more efficient and elegant to replace well-known contractions with longer synonyms
	public static String dotCorrection(String inputRaw) {
		return inputRaw.replace("U.S.", "US").replace("U.K.", "UK").replace("Mass.", "Massachusetts").replace("Mr.", "Mr");
	}
}
