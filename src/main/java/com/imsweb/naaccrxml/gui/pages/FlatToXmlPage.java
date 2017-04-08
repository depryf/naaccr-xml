/*
 * Copyright (C) 2015 Information Management Services, Inc.
 */
package com.imsweb.naaccrxml.gui.pages;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.List;

import com.imsweb.naaccrxml.NaaccrFormat;
import com.imsweb.naaccrxml.NaaccrIOException;
import com.imsweb.naaccrxml.NaaccrObserver;
import com.imsweb.naaccrxml.NaaccrOptions;
import com.imsweb.naaccrxml.NaaccrXmlUtils;
import com.imsweb.naaccrxml.entity.dictionary.NaaccrDictionary;
import com.imsweb.naaccrxml.gui.StandaloneOptions;

public class FlatToXmlPage extends AbstractProcessingPage {

    @Override
    protected String getSourceLabelText() {
        return "Source Flat File:";
    }

    @Override
    protected String getTargetLabelText() {
        return "Target XML File:";
    }

    @Override
    protected StandaloneOptions createOptions() {
        return new StandaloneOptions(true, false, false, true);
    }

    @Override
    protected void runProcessing(File source, File target, NaaccrOptions options, List<NaaccrDictionary> dictionaries, NaaccrObserver observer) throws NaaccrIOException {
        NaaccrXmlUtils.flatToXml(source, target, options, dictionaries, observer);
    }

    @Override
    protected NaaccrFormat getFormatForInputFile(File file) {

        // make sure the file exists
        if (file == null || !file.exists()) {
            reportAnalysisError("unable to find selected file");
            return null;
        }

        // make sure the first line is available
        String firstLine = null;
        try (BufferedReader reader = new BufferedReader(NaaccrXmlUtils.createReader(file))) {
            firstLine = reader.readLine();
        }
        catch (IOException e) {
            // ignored
        }
        if (firstLine == null || firstLine.isEmpty()) {
            reportAnalysisError("selected file is empty");
            return null;
        }

        // make sure the NAACCR version is valid
        String version = firstLine.length() < 19 ? "" : firstLine.substring(16, 19).trim();
        if (version.isEmpty()) {
            reportAnalysisError("unable to get NAACCR version from first record");
            return null;
        }
        if (!NaaccrFormat.isVersionSupported(version)) {
            reportAnalysisError("invalid/unsupported NAACCR version on first record: " + version);
            return null;
        }

        // make sure the record type is valid
        String type = firstLine.substring(0, 1).trim();
        if (type.isEmpty()) {
            reportAnalysisError("unable to get record type from first record");
            return null;
        }
        if (!NaaccrFormat.isRecordTypeSupported(type)) {
            reportAnalysisError("invalid/unsupported record type on first record: " + type);
            return null;
        }

        // make sure the format is valid (it should at this point)
        NaaccrFormat format;
        try {
            format = NaaccrFormat.getInstance(version, type);
        }
        catch (RuntimeException ex) {
            reportAnalysisError(ex.getMessage());
            return null;
        }

        // make sure first line has the correct length
        if (firstLine.length() != format.getLineLength()) {
            reportAnalysisError("invalid line length for first record, expected " + format.getLineLength() + " but got " + firstLine.length());
            return null;
        }

        return format;
    }
}
