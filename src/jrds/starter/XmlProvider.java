package jrds.starter;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import jrds.RdsHost;

import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * @author bacchell
 * A provider is used for XML to solve multi thread problems
 * 
 * As each one is parsed by one and only one thread, it we used one provider by host,
 * we can simply solved the concurency problem and reuse factory and parser without too many risks
 * 
 */
public class XmlProvider extends Starter {
	static final private Logger logger = Logger.getLogger(XmlProvider.class);

	private DocumentBuilder dbuilder = null;
	private XPath xpather = null;
	private String hostname = null;

	public XmlProvider(RdsHost monitoredHost) {
		hostname = monitoredHost.getName();
	}

	/* (non-Javadoc)
	 * @see jrds.Starter#getKey()
	 */
	@Override
	public Object getKey() {
		return "xmlprovider:" + hostname;
	}

	/* (non-Javadoc)
	 * @see jrds.Starter#start()
	 */
	@Override
	public boolean start() {
		xpather = XPathFactory.newInstance().newXPath();
		DocumentBuilderFactory instance = DocumentBuilderFactory.newInstance();
		instance.setIgnoringComments(true);
		instance.setValidating(false);
		try {
			dbuilder = instance.newDocumentBuilder();
		} catch (ParserConfigurationException e) {
			logger.fatal("No Document builder available");
			return false;
		}
		if(logger.isTraceEnabled())
			logger.trace("starting XmlProvider " + getClass().getName() + '@' + Integer.toHexString(hashCode()) + " " + xpather + " "+ dbuilder);
		return dbuilder != null && xpather != null;
	}

	/* (non-Javadoc)
	 * @see jrds.Starter#stop()
	 */
	@Override
	public void stop() {
		xpather = null;
		dbuilder = null;
		if(logger.isTraceEnabled())
			logger.trace("stopping XmlProvider " + getClass().getName() + '@' + Integer.toHexString(hashCode()));
	}

	public long findUptimeByDate(Document d, String startTimePath, String currentTimePath, DateFormat pattern) {
		try {
			Node startTimeNode = (Node) xpather.evaluate(startTimePath, d, XPathConstants.NODE);
			String startTimeString = startTimeNode.getTextContent();
			Date startTime = pattern.parse(startTimeString);
			Node currentTimeNode = (Node) xpather.evaluate(currentTimePath, d, XPathConstants.NODE);
			String currentTimeString = currentTimeNode.getTextContent();
			Date currentTime = pattern.parse(currentTimeString);
			return (currentTime.getTime() - startTime.getTime()) / 1000;
		} catch (XPathExpressionException e) {
			logger.error("Time not found: " + e);
		} catch (ParseException e) {
			logger.error("Date not parsed with pattern " + ((SimpleDateFormat) pattern).toPattern() + ": " + e);
		}
		return 0;
	}

	public long findUptime(Document d, String upTimePath) {
		long uptime = 0;
		if(upTimePath == null) {
			logger.error("No xpath for the uptime for " + this);
			return 0;
		}
		try {
			Node upTimeNode = (Node) xpather.evaluate(upTimePath, d, XPathConstants.NODE);
			if(upTimeNode != null) {
				if(logger.isTraceEnabled())
					logger.trace("Will parse uptime: " + upTimeNode.getTextContent());
				String dateString = upTimeNode.getTextContent();
				uptime = jrds.Util.parseStringNumber(dateString, Long.class, 0).longValue();
			}
			logger.debug("uptime for " + this + " is " + uptime);
		} catch (XPathExpressionException e) {
			logger.error("Uptime not found" + e);
		}
		return uptime;
	}

	public void fileFromXpaths(Document d, Set<String> xpaths, Map<String, Number> oldMap) {
		for(String xpath: xpaths) {
			try {
				if(logger.isTraceEnabled())
					logger.trace("Will search the xpath \"" + xpath + "\" for " + this);
				if(xpath == null || "".equals(xpath))
					continue;
				Node n = (Node)xpather.evaluate(xpath, d, XPathConstants.NODE);
				double value = 0;
				if(n != null) {
					if(logger.isTraceEnabled())
						logger.trace(n);
					value = Double.parseDouble(n.getTextContent());
					oldMap.put(xpath, Double.valueOf(value));
				}
			} catch (XPathExpressionException e) {
				logger.error("Invalid XPATH : " + xpath + " for " + this);
			} catch (NumberFormatException e) {
				logger.warn("value read from " + xpath + "  not parsable for " + this + ": " + e);
			}
		}
		logger.trace("Values found: " + oldMap);
		return;
	}

	public Document getDocument(InputSource stream) {
		Document d = null;
		if(logger.isTraceEnabled())
			logger.trace("" + stream + " " + dbuilder + " " + isStarted() + " " + getClass().getName() + '@' + Integer.toHexString(hashCode()));
		try {
			try {
				dbuilder.reset();
			} catch (UnsupportedOperationException e) {
			}
			d = dbuilder.parse(stream);
			if(logger.isTraceEnabled())
				logger.trace("just parsed a " + d.getDocumentElement().getTagName() + " from " + this);
		} catch (SAXException e) {
			logger.error("Invalid XML for the probe " + this + ":" + e, e);
		} catch (IOException e) {
			logger.error("IO Exception getting values for " + this + ":" + e);
		}
		return d;
	}

	public Document getDocument(InputStream stream) {
		return getDocument(new InputSource(stream));
	}

	public Document getDocument(Reader stream) {
		return getDocument(new InputSource(stream));
	}

	/**
	 * Used to get an empty document
	 * @return an empty document
	 */
	public Document getDocument() {
		try {
			dbuilder.reset();
		} catch (UnsupportedOperationException e) {
		}
		return dbuilder.newDocument();
	}

	/**
	 * @return the xpather
	 */
	public XPath getXpather() {
		return xpather;
	}

	public NodeList getNodeList(Document d, String xpath) throws XPathExpressionException {
		return  (NodeList) xpather.evaluate(xpath, d, XPathConstants.NODESET);
	}

	public Node getNode(Document d, String xpath) throws XPathExpressionException {
		return  (Node) xpather.evaluate(xpath, d, XPathConstants.NODE);
	}

}
