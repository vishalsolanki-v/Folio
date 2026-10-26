/*
 * Modified copy of SimpleSax from mammoth-java 1.13.0 (https://github.com/mwilliamson/java-mammoth).
 * Copyright (c) 2015, Michael Williamson. All rights reserved.
 * Licensed under the BSD 2-Clause License (see the open-source licenses screen in the app).
 *
 * Folio change: Android's SAX parser rejects the "disallow-doctype-decl" and secure-processing features,
 * so mammoth failed on every DOCX. This copy keeps external entities off and instead rejects any DOCTYPE
 * through a LexicalHandler, which gives the same protection (no XXE, no entity expansion bombs).
 * The original class is stripped from the mammoth jar by the StripMammothSimpleSax transform in app/build.gradle.kts.
 */
package org.zwobble.mammoth.internal.xml.parsing;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.DefaultHandler2;
import org.zwobble.mammoth.internal.util.PassThroughException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.Map;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toMap;

class SimpleSax {
    static void parseStream(InputStream input, SimpleSaxHandler handler) {
        parseInputSource(new InputSource(input), handler);
    }

    static void parseString(String value, SimpleSaxHandler handler) {
        parseInputSource(new InputSource(new StringReader(value)), handler);
    }

    private static void parseInputSource(InputSource inputSource, SimpleSaxHandler handler) {
        SAXParserFactory parserFactory = SAXParserFactory.newInstance();
        parserFactory.setNamespaceAware(true);
        try {
            parserFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            SAXParser saxParser = parserFactory.newSAXParser();
            XMLReader xmlReader = saxParser.getXMLReader();
            DefaultHandler2 saxHandler = new DefaultHandler2() {
                @Override
                public void startDTD(String name, String publicId, String systemId) throws SAXException {
                    throw new SAXException("DOCTYPE is not allowed in Office XML");
                }

                @Override
                public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
                    ElementName name = new ElementName(uri, localName);
                    Map<ElementName, String> attributesMap = IntStream.range(0, attributes.getLength())
                        .boxed()
                        .collect(toMap(
                            index -> new ElementName(attributes.getURI(index), attributes.getLocalName(index)),
                            attributes::getValue
                        ));
                    handler.startElement(name, attributesMap);
                }

                @Override
                public void endElement(String uri, String localName, String qName) throws SAXException {
                    handler.endElement();
                }

                @Override
                public void characters(char[] ch, int start, int length) throws SAXException {
                    handler.characters(new String(ch, start, length));
                }
            };
            xmlReader.setContentHandler(saxHandler);
            xmlReader.setProperty("http://xml.org/sax/properties/lexical-handler", saxHandler);
            xmlReader.parse(inputSource);
        } catch (IOException exception) {
            throw new PassThroughException(exception);
        } catch (ParserConfigurationException | SAXException exception) {
            throw new RuntimeException(exception);
        }
    }
}
