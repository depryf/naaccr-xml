/*
 * Copyright (C) 2015 Information Management Services, Inc.
 */
package com.imsweb.naaccrxml;

import java.io.IOException;
import java.io.Writer;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;

import javax.xml.bind.DatatypeConverter;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.ConversionException;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.io.xml.PrettyPrintWriter;

import com.imsweb.naaccrxml.entity.Item;
import com.imsweb.naaccrxml.entity.NaaccrData;
import com.imsweb.naaccrxml.entity.Patient;
import com.imsweb.naaccrxml.entity.dictionary.NaaccrDictionary;
import com.imsweb.naaccrxml.runtime.NaaccrStreamConfiguration;
import com.imsweb.naaccrxml.runtime.NaaccrStreamContext;
import com.imsweb.naaccrxml.runtime.RuntimeNaaccrDictionary;

/**
 * This class can be used to wrap a generic writer into a patient writer handling the NAACCR XML format.
 */
public class PatientXmlWriter implements AutoCloseable {

    // XStream object responsible for reading patient objects
    protected XStream _xstream;

    // the underlined writer
    protected HierarchicalStreamWriter _writer;

    // sometimes we want to finalize the writing operation without closing the writer itself...
    protected boolean _hasBeenFinalized = false;

    /**
     * Constructor.
     * @param writer required underlined writer
     * @param options optional options
     * @param userDictionary optional user-defined dictionary
     * @throws NaaccrIOException if anything goes wrong
     */
    public PatientXmlWriter(Writer writer, NaaccrData rootData, NaaccrOptions options, NaaccrDictionary userDictionary) throws NaaccrIOException {
        this(writer, rootData, options, userDictionary, null);
    }

    /**
     * Constructor.
     * @param writer required underlined writer
     * @param options optional options
     * @param userDictionary optional user-defined dictionary
     * @param configuration optional stream configuration
     * @throws NaaccrIOException if anything goes wrong
     */
    public PatientXmlWriter(Writer writer, NaaccrData rootData, NaaccrOptions options, NaaccrDictionary userDictionary, NaaccrStreamConfiguration configuration) throws NaaccrIOException {

        try {
            // we always need options
            if (options == null)
                options = new NaaccrOptions();

            // we always need a configuration
            if (configuration == null)
                configuration = new NaaccrStreamConfiguration();

            // create the context
            NaaccrStreamContext context = new NaaccrStreamContext();
            context.setOptions(options);
            context.setConfiguration(configuration);

            NaaccrDictionary baseDictionary = NaaccrXmlDictionaryUtils.getBaseDictionaryByUri(rootData.getBaseDictionaryUri());

            // create the writer
            _writer = new PrettyPrintWriter(writer, new char[] {' ', ' ', ' ', ' '});

            // would be better to use a "header writer", I think XStream has one actually; that would be better...
            try {
                writer.write("<?xml version=\"1.0\"?>\n\n");
            }
            catch (IOException e) {
                throw new NaaccrIOException(e.getMessage());
            }

            // write standard attributes
            _writer.startNode(NaaccrXmlUtils.NAACCR_XML_TAG_ROOT);
            if (rootData.getBaseDictionaryUri() == null)
                throw new NaaccrIOException("base dictionary URI is required");
            _writer.addAttribute(NaaccrXmlUtils.NAACCR_XML_ROOT_ATT_BASE_DICT, rootData.getBaseDictionaryUri());
            if (userDictionary != null) {
                if (rootData.getUserDictionaryUri() != null && !rootData.getUserDictionaryUri().equals(userDictionary.getDictionaryUri()))
                    throw new NaaccrIOException("Provided dictionary has a different URI than the one in the rootData");
                _writer.addAttribute(NaaccrXmlUtils.NAACCR_XML_ROOT_ATT_USER_DICT, userDictionary.getDictionaryUri());
            }
            if (rootData.getRecordType() == null)
                throw new NaaccrIOException("record type is required");
            _writer.addAttribute(NaaccrXmlUtils.NAACCR_XML_ROOT_ATT_REC_TYPE, rootData.getRecordType());
            if (rootData.getTimeGenerated() != null) {
                Calendar cal = Calendar.getInstance();
                cal.setTime(rootData.getTimeGenerated());
                _writer.addAttribute(NaaccrXmlUtils.NAACCR_XML_ROOT_ATT_TIME_GENERATED, DatatypeConverter.printDateTime(cal));
            }
            else
                _writer.addAttribute(NaaccrXmlUtils.NAACCR_XML_ROOT_ATT_TIME_GENERATED, DatatypeConverter.printDateTime(Calendar.getInstance()));
            // always use the current specs; doesn't matter the value on the root object...
            _writer.addAttribute(NaaccrXmlUtils.NAACCR_XML_ROOT_ATT_SPEC_VERSION, NaaccrXmlUtils.CURRENT_SPECIFICATION_VERSION);
            // same for the default namespace, always use the library value...
            _writer.addAttribute("xmlns", NaaccrXmlUtils.NAACCR_XML_NAMESPACE);

            // write non-standard attributes
            Set<String> standardAttributes = new HashSet<>();
            standardAttributes.add(NaaccrXmlUtils.NAACCR_XML_ROOT_ATT_BASE_DICT);
            standardAttributes.add(NaaccrXmlUtils.NAACCR_XML_ROOT_ATT_USER_DICT);
            standardAttributes.add(NaaccrXmlUtils.NAACCR_XML_ROOT_ATT_REC_TYPE);
            standardAttributes.add(NaaccrXmlUtils.NAACCR_XML_ROOT_ATT_TIME_GENERATED);
            standardAttributes.add(NaaccrXmlUtils.NAACCR_XML_ROOT_ATT_SPEC_VERSION);
            for (Entry<String, String> entry : rootData.getExtraRootParameters().entrySet())
                if (!standardAttributes.contains(entry.getKey()) && !"xmlns".equals(entry.getKey()))
                    _writer.addAttribute(entry.getKey(), entry.getValue());

            // now we are ready to create our reading context and make it available to the patient converter
            context.setDictionary(new RuntimeNaaccrDictionary(rootData.getRecordType(), baseDictionary, userDictionary));
            configuration.getPatientConverter().setContext(context);

            // write the root items
            for (Item item : rootData.getItems())
                configuration.getPatientConverter().writeItem(rootData, item, _writer);

            // for now, ignore the root extension...
            // TODO [EXTENSIONS] this would be the place to write the root extension...

            // need to expose xstream so the other methods can use it...
            _xstream = configuration.getXstream();
        }
        catch (ConversionException ex) {
            throw convertSyntaxException(ex);
        }
    }

    /**
     * Writes the given patient on this stream.
     * @throws NaaccrIOException if anything goes wrong
     */
    public void writePatient(Patient patient) throws NaaccrIOException {
        try {
            _xstream.marshal(patient, _writer);
        }
        catch (ConversionException ex) {
            throw convertSyntaxException(ex);
        }
    }

    /**
     * Write the final node of the document, without closing the stream.
     */
    public void closeAndKeepAlive() {
        if (!_hasBeenFinalized) {
            _writer.endNode();
            _hasBeenFinalized = true;
        }
    }

    @Override
    public void close() {
        closeAndKeepAlive();
        _writer.close();
    }

    /**
     * We don't want to expose the conversion exceptions, so let's translate them into our own exceptions...
     */
    protected NaaccrIOException convertSyntaxException(ConversionException ex) {
        String msg = ex.get("message");
        if (msg == null)
            msg = ex.getMessage();
        NaaccrIOException e = new NaaccrIOException(msg);
        if (ex.get("lineNumber") != null)
            e.setLineNumber(Integer.valueOf(ex.get("lineNumber")));
        e.setPath(ex.get("path"));
        return e;
    }
}
