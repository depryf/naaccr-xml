/*
 * Copyright (C) 2018 Information Management Services, Inc.
 */
package com.imsweb.naaccrxml.sas;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Use this class to convert a given CSV file into a NAACCR XML file.
 * <br/><br/>
 * THIS CLASS IS IMPLEMENTED TO BE COMPATIBLE WITH JAVA 7; BE CAREFUL WHEN MODIFYING IT.
 */
@SuppressWarnings("ALL")
public class SasFlatToXml {

    private File _flatFile;

    private File _xmlFile;

    private File _formatFile;

    private List<File> _dictionaryFiles;

    private String _naaccrVersion;

    private String _recordType;

    private String _dictionaryUris;

    private boolean _writeNumbers;

    private boolean _groupTumors;

    // TODO FD lots to clean up here, review all logs messages

    public SasFlatToXml(String xmlPath) {
        initFiles(xmlPath, false);
    }

    public SasFlatToXml(String xmlPath, String naaccrVersion, String recordType) {
        initFiles(xmlPath, true);

        _dictionaryFiles = new ArrayList<>();

        _naaccrVersion = naaccrVersion;
        if (_naaccrVersion == null || _naaccrVersion.trim().isEmpty())
            SasUtils.logError("NAACCR version needs to be provided");
        if (!"140".equals(naaccrVersion) && !"150".equals(naaccrVersion) && !"160".equals(naaccrVersion) && !"180".equals(naaccrVersion) && !"210".equals(naaccrVersion) && !"220".equals(
                naaccrVersion) && !"230".equals(naaccrVersion))
            SasUtils.logError("NAACCR version must be 140, 150, 160, 180, 210, 220 or 230; got " + _naaccrVersion);

        _recordType = recordType;
        if (_recordType == null || _recordType.trim().isEmpty())
            SasUtils.logError("Record type needs to be provided");
        if (!"A".equals(_recordType) && !"M".equals(_recordType) && !"C".equals(_recordType) && !"I".equals(_recordType))
            SasUtils.logError("Record type must be A, M, C or I; got " + _recordType);

        _writeNumbers = false;
        _groupTumors = true;
    }

    private void initFiles(String xmlPath, boolean logInfo) {
        if (xmlPath == null || xmlPath.trim().isEmpty())
            SasUtils.logError("No target XML path was provided");
        else {
            _flatFile = new File(SasUtils.computeFlatPathFromXmlPath(xmlPath));
            if (logInfo)
                SasUtils.logInfo("Source flat: " + _flatFile.getAbsolutePath());

            _xmlFile = new File(xmlPath);
            if (!new File(_xmlFile.getAbsolutePath()).getParentFile().exists())
                SasUtils.logError("Parent directory for target XML path doesn't exist: " + _xmlFile.getParentFile().getAbsolutePath());
            else if (logInfo)
                SasUtils.logInfo("Target XML: " + _xmlFile.getAbsolutePath());

            _formatFile = new File(SasUtils.computeOutputPathFromXmlPath(xmlPath));
            if (new File(_formatFile.getAbsolutePath()).getParentFile().exists() && logInfo)
                SasUtils.logInfo("Target output format: " + _formatFile.getAbsolutePath());
        }
    }

    public void setDictionary(String dictionaryPath, String dictionaryUri) {
        if (dictionaryPath != null && !dictionaryPath.trim().isEmpty()) {
            for (String path : dictionaryPath.split(";")) {
                File dictionaryFile = new File(path.trim());
                if (!dictionaryFile.exists())
                    SasUtils.logError("Invalid CSV dictionary path: " + path);
                else {
                    try {
                        SasUtils.validateCsvDictionary(dictionaryFile);
                        _dictionaryFiles.add(dictionaryFile);
                        SasUtils.logInfo("Dictionary: " + dictionaryFile.getAbsolutePath());
                    }
                    catch (IOException e) {
                        SasUtils.logError("Invalid CSV dictionary: " + e.getMessage());
                    }
                }
            }
        }

        if (dictionaryUri != null) {
            StringBuilder buf = new StringBuilder();
            for (String uri : dictionaryUri.split(";")) {
                if (buf.length() > 0)
                    buf.append(" ");
                buf.append(uri.trim());
            }
            _dictionaryUris = buf.toString();
        }
    }

    public String getFlatPath() {
        return _flatFile.getAbsolutePath();
    }

    public String getXmlPath() {
        return _xmlFile.getAbsolutePath();
    }

    public String getFormatPath() {
        return _formatFile.getAbsolutePath();
    }

    public String getNaaccrVersion() {
        return _naaccrVersion;
    }

    public String getRecordType() {
        return _recordType;
    }

    public void setWriteNumbers(String value) {
        _writeNumbers = value != null && ("true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value));
    }

    public String getWriteNumbers() {
        return _writeNumbers ? "Yes" : "No";
    }

    public void setGroupTumors(String value) {
        _groupTumors = value == null || !("false".equalsIgnoreCase(value) || "no".equalsIgnoreCase(value));
    }

    public String getGroupTumors() {
        return _groupTumors ? "Yes" : "No";
    }

    public List<SasFieldInfo> getFields() {
        return SasUtils.getFields(_naaccrVersion, _recordType, _dictionaryFiles);
    }

    public void createOutputFormat() throws IOException {
        createOutputFormat(null);
    }

    public void createOutputFormat(String fields) throws IOException {
        createOutputFormat(fields, getFields());
    }

    public void createOutputFormat(String fields, List<SasFieldInfo> availableFields) throws IOException {
        try {
            Set<String> requestedFieldIds = SasUtils.extractRequestedFields(fields, availableFields);

            Map<String, String> itemNumbers = new HashMap<>();
            for (SasFieldInfo info : availableFields)
                itemNumbers.put(info.getNaaccrId(), info.getNum().toString());

            Map<String, SasFieldInfo> fieldsToWrite = new HashMap<>();
            for (SasFieldInfo field : availableFields)
                if (requestedFieldIds == null || requestedFieldIds.contains(field.getNaaccrId()))
                    fieldsToWrite.put(field.getTruncatedNaaccrId(), field);
            int expectedLineLength = 0;
            for (SasFieldInfo field : fieldsToWrite.values())
                expectedLineLength += field.getLength();

            SasUtils.logInfo("Generating output format file...");
            BufferedWriter writer = null;
            try {
                writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(_formatFile), StandardCharsets.US_ASCII));
                writer.write("put\n");
                int counter = 1;
                for (Entry<String, SasFieldInfo> entry : fieldsToWrite.entrySet()) {
                    writer.write("@" + counter + " " + entry.getKey() + " $" + entry.getValue().getLength() + ".\n");
                    counter += entry.getValue().getLength();
                }
                writer.write(";");
                SasUtils.logInfo("Successfully created output format file with " + fieldsToWrite.size() + " fields (variables); expected line length is " + expectedLineLength);
            }
            finally {
                if (writer != null)
                    writer.close();
            }
        }
        catch (IOException e) {
            e.printStackTrace();
            throw e;
        }
        catch (RuntimeException e) {
            e.printStackTrace();
            throw new IOException(e);
        }
    }

    public void convert() throws IOException {
        convert(null);
    }

    public void convert(String fields) throws IOException {
        convert(fields, getFields());
    }

    public void convert(String fields, List<SasFieldInfo> availableFields) throws IOException {
        int numPat = 0;
        int numTum = 0;
        try {
            Set<String> requestedFieldIds = SasUtils.extractRequestedFields(fields, availableFields);

            Map<String, String> itemNumbers = new HashMap<>();
            for (SasFieldInfo info : availableFields)
                itemNumbers.put(info.getNaaccrId(), info.getNum().toString());

            Map<String, SasFieldInfo> rootFields = new HashMap<>();
            Map<String, SasFieldInfo> patientFields = new HashMap<>();
            Map<String, SasFieldInfo> tumorFields = new HashMap<>();
            Map<String, SasFieldInfo> fieldsToWrite = new HashMap<>();
            for (SasFieldInfo field : availableFields) {
                if (requestedFieldIds == null || requestedFieldIds.contains(field.getNaaccrId())) {
                    fieldsToWrite.put(field.getTruncatedNaaccrId(), field);
                    if ("NaaccrData".equals(field.getParentTag()))
                        rootFields.put(field.getTruncatedNaaccrId(), field);
                    else if ("Patient".equals(field.getParentTag()))
                        patientFields.put(field.getTruncatedNaaccrId(), field);
                    else if ("Tumor".equals(field.getParentTag()))
                        tumorFields.put(field.getTruncatedNaaccrId(), field);
                }
            }
            int expectedLineLength = 0;
            for (SasFieldInfo field : fieldsToWrite.values())
                expectedLineLength += field.getLength();

            SasUtils.logInfo("Starting converting flat to XML" + (!_groupTumors ? " (tumor grouping disabled) " : "") + "...");
            LineNumberReader reader = null;
            BufferedWriter writer = null;
            try {
                reader = new LineNumberReader(new InputStreamReader(new FileInputStream(_flatFile), StandardCharsets.UTF_8));
                writer = SasUtils.createWriter(_xmlFile);

                String currentPatNum = null;
                String line = reader.readLine();
                boolean wroteRoot = false;
                int numPatientWritten = 0;
                while (line != null) {
                    if (line.length() > expectedLineLength) {
                        SasUtils.logError("Expected line length of " + expectedLineLength + " but line #" + reader.getLineNumber() + " is " + line.length() + "; truncated it...");
                        line = line.substring(0, expectedLineLength);
                    }
                    else if (line.length() < expectedLineLength) {
                        SasUtils.logError("Expected line length of " + expectedLineLength + " but line #" + reader.getLineNumber() + " is " + line.length() + "; padded it...");
                        line = SasUtils.rightPadWithSpaces(line, expectedLineLength);
                    }

                    Map<String, String> values = new HashMap<>();
                    int start = 0;
                    for (SasFieldInfo field : fieldsToWrite.values()) {
                        String value = new String(line.substring(start, start + field.getLength() - 1).trim());
                        if (!value.isEmpty())
                            values.put(field.getTruncatedNaaccrId(), value);
                        start += field.getLength();
                    }

                    String patNum = values.get("patientIdNumber");
                    if (patNum != null && patNum.trim().isEmpty())
                        patNum = null;

                    // do we have to write the root?
                    if (!wroteRoot) {
                        writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
                        writer.write("\n");
                        writer.write("<NaaccrData");
                        writer.write(" baseDictionaryUri=\"http://naaccr.org/naaccrxml/naaccr-dictionary-" + _naaccrVersion + ".xml\"");
                        if (!_dictionaryFiles.isEmpty() && _dictionaryUris != null && !_dictionaryUris.trim().isEmpty())
                            writer.write("\n            userDictionaryUri=\"" + _dictionaryUris + "\"");
                        writer.write("\n            recordType=\"" + _recordType + "\"");
                        writer.write("\n            specificationVersion=\"1.6\"");
                        writer.write("\n            xmlns=\"http://naaccr.org/naaccrxml\"");
                        writer.write(">\n");
                        for (Entry<String, SasFieldInfo> entry : rootFields.entrySet()) {
                            String val = values.get(entry.getKey());
                            if (val != null && !val.trim().isEmpty())
                                writer.write(createItemLine("    ", entry.getKey(), itemNumbers.get(entry.getKey()), val));
                        }
                        wroteRoot = true;
                    }

                    // do we have to write the patient?
                    if (!_groupTumors || patNum == null || currentPatNum == null || !currentPatNum.equals(patNum)) {
                        if (numPatientWritten > 0)
                            writer.write("    </Patient>\n");
                        numPatientWritten++;
                        writer.write("    <Patient>\n");
                        numPat++;
                        for (Entry<String, SasFieldInfo> entry : patientFields.entrySet()) {
                            String val = values.get(entry.getKey());
                            if (val != null && !val.trim().isEmpty())
                                writer.write(createItemLine("        ", entry.getKey(), itemNumbers.get(entry.getKey()), val));
                        }
                    }

                    // we always have to write the tumor!
                    writer.write("        <Tumor>\n");
                    numTum++;
                    for (Entry<String, SasFieldInfo> entry : tumorFields.entrySet()) {
                        String val = values.get(entry.getKey());
                        if (val != null && !val.trim().isEmpty())
                            writer.write(createItemLine("            ", entry.getKey(), itemNumbers.get(entry.getKey()), val));
                    }
                    writer.write("        </Tumor>\n");

                    currentPatNum = patNum;
                    line = reader.readLine();
                }

                writer.write("    </Patient>\n");
                writer.write("</NaaccrData>");
            }
            finally {
                if (writer != null)
                    writer.close();
                if (reader != null)
                    reader.close();
            }
        }
        catch (IOException e) {
            e.printStackTrace();
            throw e;
        }
        catch (RuntimeException e) {
            e.printStackTrace();
            throw new IOException(e);
        }

        SasUtils.logInfo("Successfully created target XML with " + numPat + " Patient" + (numPat > 1 ? "s" : "") + " and " + numTum + " Tumor" + (numTum > 1 ? "?" : ""));
    }

    private String createItemLine(String indentation, String itemId, String itemNumber, String value) {
        StringBuilder buf = new StringBuilder(indentation);
        buf.append("<Item naaccrId=\"").append(itemId).append("\"");
        if (itemNumber != null && _writeNumbers)
            buf.append(" naaccrNum=\"").append(itemNumber).append("\"");
        buf.append(">").append(SasUtils.cleanUpValueToWriteAsXml(value)).append("</Item>\n");
        return buf.toString();
    }

    public void cleanup() {
        cleanup("yes");
    }

    public void cleanup(String option) {
        if ("no".equalsIgnoreCase(option))
            SasUtils.logInfo("Skipping temp files cleanup...");
        else if (!_flatFile.delete() || !_formatFile.delete())
            SasUtils.logError("Unable to cleanup temp files, they will have to be manually deleted...");
        else
            SasUtils.logInfo("Successfully deleted temp files...");
    }

    List<File> getUserDictionaryFiles() {
        return _dictionaryFiles;
    }
}
