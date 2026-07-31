package org.kapott.hbci.GV;

import org.kapott.hbci.manager.HBCIHandler;
import org.kapott.hbci.manager.MsgGen;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

final class KontoauszugUebersichtSyntax {

	private static final String JOB_NAME = GVKontoauszugUebersicht.LOWLEVEL_NAME;
	private static final String FIRST_SEGMENT_ID = JOB_NAME + "1";
	private static final String SEGMENT_HEADER = "SegHead";
	private static final String SEGMENT_HEADER_CODE = SEGMENT_HEADER + ".code";
	private static final String SEGMENT_HEADER_VERSION = SEGMENT_HEADER + ".version";

	private KontoauszugUebersichtSyntax() {
	}

	static boolean ensureAvailable(HBCIHandler handler) {
		if (handler == null) {
			return false;
		}
		MsgGen msgGen = handler.getMsgGen();
		if (msgGen == null || msgGen.getSyntax() == null) {
			return false;
		}

		Document syntax = msgGen.getSyntax();
		if (findElementById(syntax, FIRST_SEGMENT_ID) != null) {
			return true;
		}

		SyntaxContainers containers = findSyntaxContainers(syntax);
		if (containers == null) {
			return false;
		}

		appendVersion(syntax, containers, 1, "KTV3", false);
		appendVersion(syntax, containers, 2, "KTVInt", false);
		appendVersion(syntax, containers, 3, "KTVInt", true);
		return true;
	}

	private static SyntaxContainers findSyntaxContainers(Document syntax) {
		Element segments = findContainer(syntax, "SEGs");
		Element businessCases = findElementById(syntax, "GV");
		Element businessCaseResults = findElementById(syntax, "GVRes");
		Element parameterSegments = findElementById(syntax, "Params");
		if (segments == null || businessCases == null || businessCaseResults == null || parameterSegments == null) {
			return null;
		}
		return new SyntaxContainers(segments, businessCases, businessCaseResults, parameterSegments);
	}

	private static void appendVersion(Document syntax, SyntaxContainers containers, int version, String accountType, boolean withDocumentId) {
		appendRequestSegment(syntax, containers.segments(), version, accountType);
		appendResultSegment(syntax, containers.segments(), version, withDocumentId);
		appendParameterSegment(syntax, containers.segments(), version);
		appendSegmentReference(syntax, containers.businessCases(), JOB_NAME + version);
		appendSegmentReference(syntax, containers.businessCaseResults(), JOB_NAME + "Res" + version);
		appendSegmentReference(syntax, containers.parameterSegments(), JOB_NAME + "Par" + version);
	}

	private static void appendRequestSegment(Document syntax, Element segments, int version, String accountType) {
		Element segment = createSegmentDefinition(syntax, JOB_NAME + version);
		appendDeg(syntax, segment, "SegHeadUser", SEGMENT_HEADER);
		appendDeg(syntax, segment, accountType, "My");
		appendDe(syntax, segment, "maxentries", "Num", "4", "0");
		appendDe(syntax, segment, "offset", "AN", "35", "0");
		appendValue(syntax, segment, SEGMENT_HEADER_CODE, "HKKAU");
		appendValue(syntax, segment, SEGMENT_HEADER_VERSION, Integer.toString(version));
		segments.appendChild(segment);
	}

	private static void appendResultSegment(Document syntax, Element segments, int version, boolean withDocumentId) {
		Element segment = createSegmentDefinition(syntax, JOB_NAME + "Res" + version);
		appendDeg(syntax, segment, "SegHeadInst", SEGMENT_HEADER);
		appendDe(syntax, segment, "number", "Num", "5", withDocumentId ? "0" : null);
		appendDe(syntax, segment, "acknowledgement", "Code", "1", null);
		appendDe(syntax, segment, "retrievable", "JN", null, null);
		appendDe(syntax, segment, "year", "Num", "4", "0");
		appendDe(syntax, segment, "date", "Date", "0", "0");
		appendDe(syntax, segment, "time", "Time", "0", "0");
		appendDe(syntax, segment, "creationtype", "AN", "30", "0");
		if (withDocumentId) {
			appendDe(syntax, segment, "documentid", "AN", "256", "0");
		}
		appendValue(syntax, segment, SEGMENT_HEADER_CODE, "HIKAU");
		appendValue(syntax, segment, SEGMENT_HEADER_VERSION, Integer.toString(version));
		segments.appendChild(segment);
	}

	private static void appendParameterSegment(Document syntax, Element segments, int version) {
		Element segment = createSegmentDefinition(syntax, JOB_NAME + "Par" + version);
		appendDeg(syntax, segment, "SegHeadInst", SEGMENT_HEADER);
		appendDe(syntax, segment, "maxnum", "Num", "3", null);
		appendDe(syntax, segment, "minsigs", "Num", "1", null);
		appendDe(syntax, segment, "secclass", "Code", "1", null);
		appendSecurityClassValues(syntax, segment);
		appendValue(syntax, segment, SEGMENT_HEADER_CODE, "HIKAUS");
		appendValue(syntax, segment, SEGMENT_HEADER_VERSION, Integer.toString(version));
		segments.appendChild(segment);
	}

	private static Element createSegmentDefinition(Document syntax, String id) {
		Element segment = syntax.createElement("SEGdef");
		segment.setAttribute("id", id);
		segment.setIdAttribute("id", true);
		return segment;
	}

	private static void appendSegmentReference(Document syntax, Element parent, String type) {
		Element segment = syntax.createElement("SEG");
		segment.setAttribute("type", type);
		segment.setAttribute("minnum", "0");
		parent.appendChild(segment);
	}

	private static void appendDeg(Document syntax, Element parent, String type, String name) {
		Element deg = syntax.createElement("DEG");
		deg.setAttribute("type", type);
		if (name != null) {
			deg.setAttribute("name", name);
		}
		parent.appendChild(deg);
	}

	private static void appendDe(Document syntax, Element parent, String name, String type, String maxSize, String minNum) {
		Element de = syntax.createElement("DE");
		de.setAttribute("name", name);
		de.setAttribute("type", type);
		if (maxSize != null) {
			de.setAttribute("maxsize", maxSize);
		}
		if (minNum != null) {
			de.setAttribute("minnum", minNum);
		}
		parent.appendChild(de);
	}

	private static void appendSecurityClassValues(Document syntax, Element parent) {
		Element valids = syntax.createElement("valids");
		valids.setAttribute("path", "secclass");
		for (String value : new String[] { "0", "1", "2", "3", "4" }) {
			Element validValue = syntax.createElement("validvalue");
			validValue.appendChild(syntax.createTextNode(value));
			valids.appendChild(validValue);
		}
		parent.appendChild(valids);
	}

	private static void appendValue(Document syntax, Element parent, String path, String text) {
		Element value = syntax.createElement("value");
		value.setAttribute("path", path);
		value.appendChild(syntax.createTextNode(text));
		parent.appendChild(value);
	}

	private static Element findContainer(Document syntax, String tagName) {
		NodeList nodes = syntax.getElementsByTagName(tagName);
		if (nodes.getLength() == 0) {
			return null;
		}
		Node node = nodes.item(0);
		return node instanceof Element element ? element : null;
	}

	private static Element findElementById(Document syntax, String id) {
		Element element = syntax.getElementById(id);
		if (element != null) {
			return element;
		}
		NodeList nodes = syntax.getElementsByTagName("*");
		for (int i = 0; i < nodes.getLength(); i++) {
			Node node = nodes.item(i);
			if (node instanceof Element candidate && id.equals(candidate.getAttribute("id"))) {
				return candidate;
			}
		}
		return null;
	}

	private record SyntaxContainers(Element segments, Element businessCases, Element businessCaseResults, Element parameterSegments) {
	}
}
