package net.renameo;

import static java.util.Collections.*;
import static net.renameo.Logging.*;
import static net.renameo.util.XPathUtilities.*;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;

public class History {

	private List<Sequence> sequences;

	public History() {
		this.sequences = new ArrayList<Sequence>();
	}

	public History(Collection<Sequence> sequences) {
		this.sequences = new ArrayList<Sequence>(sequences);
	}

	public static class Sequence {

		private Date date;

		private List<Element> elements;

		private Sequence() {
			// hide constructor
		}

		public Date date() {
			return date;
		}

		public List<Element> elements() {
			if (elements == null)
				return emptyList();

			return unmodifiableList(elements);
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof Sequence) {
				Sequence other = (Sequence) obj;
				return date.equals(other.date) && elements.equals(other.elements);
			}

			return false;
		}

		@Override
		public int hashCode() {
			return Objects.hash(elements, date);
		}
	}

	public static class Element {

		private File dir;

		private String from;

		private String to;

		public Element() {
			// keep default constructor
		}

		public Element(String from, String to, File dir) {
			this.from = from;
			this.to = to;
			this.dir = dir;
		}

		public File dir() {
			return dir;
		}

		public String from() {
			return from;
		}

		public String to() {
			return to;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof Element) {
				Element element = (Element) obj;
				return to.equals(element.to) && from.equals(element.from) && dir.getPath().equals(element.dir.getPath());
			}

			return false;
		}

		@Override
		public int hashCode() {
			return Objects.hash(to, from, dir);
		}
	}

	public List<Sequence> sequences() {
		return unmodifiableList(sequences);
	}

	public void add(Collection<Element> elements) {
		Sequence sequence = new Sequence();
		sequence.date = new Date();
		sequence.elements = new ArrayList<Element>(elements);

		add(sequence);
	}

	public void add(Sequence sequence) {
		this.sequences.add(sequence);
	}

	public void addAll(Collection<Sequence> sequences) {
		this.sequences.addAll(sequences);
	}

	public void merge(History history) {
		for (Sequence sequence : history.sequences()) {
			if (!sequences.contains(sequence)) {
				add(sequence);
			}
		}
	}

	public int totalSize() {
		int i = 0;
		for (Sequence it : sequences()) {
			i += it.elements.size();
		}
		return i;
	}

	public void clear() {
		sequences.clear();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof History) {
			History other = (History) obj;
			return sequences.equals(other.sequences);
		}

		return false;
	}

	@Override
	public int hashCode() {
		return sequences.hashCode();
	}

	public Map<File, File> getRenameMap() {
		Map<File, File> map = new LinkedHashMap<File, File>();
		for (History.Sequence seq : this.sequences()) {
			for (History.Element elem : seq.elements()) {
				File to = new File(elem.to());
				if (!to.isAbsolute()) {
					to = new File(elem.dir(), elem.to());
				}
				File from = new File(elem.dir(), elem.from());
				map.put(from, to);
			}
		}
		return map;
	}

	public static void exportHistory(History history, OutputStream output) {
		try {
			// write history as XML document without any external JAXB dependency (required since Java 9+)
			Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
			org.w3c.dom.Element root = document.createElement("history");
			document.appendChild(root);

			for (Sequence sequence : history.sequences()) {
				org.w3c.dom.Element sequenceElement = document.createElement("sequence");
				sequenceElement.setAttribute("date", HISTORY_DATE_FORMAT.format(sequence.date()));

				for (Element element : sequence.elements()) {
					org.w3c.dom.Element renameElement = document.createElement("rename");
					renameElement.setAttribute("from", element.from());
					renameElement.setAttribute("to", element.to());
					renameElement.setAttribute("dir", element.dir().getPath());
					sequenceElement.appendChild(renameElement);
				}

				root.appendChild(sequenceElement);
			}

			TransformerFactory.newInstance().newTransformer().transform(new DOMSource(document), new StreamResult(new OutputStreamWriter(output, StandardCharsets.UTF_8)));
		} catch (Exception e) {
			debug.log(Level.SEVERE, "Failed to write history", e);
		}
	}

	private static Date parseHistoryDate(String value) {
		for (String pattern : new String[] { "yyyy-MM-dd'T'HH:mm:ss.SSSXXXXX", "yyyy-MM-dd'T'HH:mm:ss.SSSZ", "yyyy-MM-dd'T'HH:mm:ss" }) {
			try {
				SimpleDateFormat format = new SimpleDateFormat(pattern);
				return format.parse(value);
			} catch (Exception e) {
				// try next pattern
			}
		}
		return null;
	}

	public static History importHistory(InputStream stream) {
		try {
			Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(stream);

			List<Sequence> sequences = new ArrayList<Sequence>();
			for (org.w3c.dom.Element sequenceElement : streamNodes("history/sequence", document).map(node -> (org.w3c.dom.Element) node).toArray(org.w3c.dom.Element[]::new)) {
				Date date = parseHistoryDate(getAttribute("date", sequenceElement));
				if (date == null) {
					date = new Date();
				}

				List<Element> elements = new ArrayList<Element>();
				for (org.w3c.dom.Element renameElement : streamNodes("rename", sequenceElement).map(node -> (org.w3c.dom.Element) node).toArray(org.w3c.dom.Element[]::new)) {
					elements.add(new Element(getAttribute("from", renameElement), getAttribute("to", renameElement), new File(getAttribute("dir", renameElement))));
				}

				Sequence sequence = new Sequence();
				sequence.date = date;
				sequence.elements = elements;
				sequences.add(sequence);
			}

			return new History(sequences);
		} catch (Exception e) {
			debug.log(Level.SEVERE, "Failed to read history", e);
			return new History();
		}
	}

	private static final SimpleDateFormat HISTORY_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

}
