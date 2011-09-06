package jrds.webapp;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;

import jrds.Tools;
import jrds.factories.NodeListIterator;
import jrds.standalone.JettyLogger;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.BeforeClass;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

public class TestListServlet {
    static final private Logger logger = Logger.getLogger(TestListServlet.class);

    @BeforeClass
    static public void configure() throws Exception {
        Tools.configure();
        Tools.prepareXml();
        logger.setLevel(Level.TRACE);
        System.setProperty("org.mortbay.log.class", jrds.standalone.JettyLogger.class.getName());
        Tools.setLevel(new String[] {JettyLogger.class.getName(), Status.class.getName()}, logger.getLevel());

    }

    @Test 
    public void testListServlet() throws Exception
    {
        File cwd = new File (".");
        ClassLoader cl = new URLClassLoader(new URL[] {cwd.toURI().toURL()});

        InputStream webxmlStream = cl.getResourceAsStream("web/WEB-INF/web.xml");
        Document webxml = Tools.parseRessource(webxmlStream);
        for(Node n: new NodeListIterator(webxml, Tools.xpather.compile("/web-app/servlet/servlet-class"))) {
            String servletClassName = n.getTextContent().trim();
            getClass().getClassLoader().loadClass(servletClassName);
        }
        for(Node n: new NodeListIterator(webxml, Tools.xpather.compile("/web-app/listener/listener-class"))) {
            String className = n.getTextContent().trim();
            getClass().getClassLoader().loadClass(className);
        }
   }

}